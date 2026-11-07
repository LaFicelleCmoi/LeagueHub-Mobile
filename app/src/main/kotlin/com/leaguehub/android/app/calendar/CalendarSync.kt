package com.leaguehub.android.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import com.leaguehub.android.R
import com.leaguehub.android.data.espn.EspnJson
import com.leaguehub.android.domain.calendar.CalendarEntry
import com.leaguehub.android.domain.calendar.MATCH_DURATION_MS
import com.leaguehub.android.domain.calendar.SyncedEvent
import com.leaguehub.android.domain.calendar.clearPlan
import com.leaguehub.android.domain.calendar.planCalendar
import com.leaguehub.android.domain.calendar.upcomingEntries
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Un agenda du téléphone dans lequel l'app peut écrire. */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int, val primary: Boolean)

/**
 * Recopie les matchs à venir du favori et des clubs suivis dans l'agenda choisi, puis les tient à jour :
 * horaire fixé ou décalé, match reporté, club plus suivi. Les matchs passés restent dans l'agenda.
 * Ce que l'app a créé est mémorisé (match → événement) pour ne jamais toucher aux autres événements.
 */
@Singleton
class CalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val football: FootballRepository,
    private val settings: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val knownSerializer = MapSerializer(String.serializer(), SyncedEvent.serializer())

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Agendas modifiables, l'agenda principal du compte en premier. */
    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY,
        )
        val writable = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.VISIBLE} = 1"
        val result = mutableListOf<DeviceCalendar>()
        runCatching {
            context.contentResolver.query(Calendars.CONTENT_URI, projection, writable, null, null)?.use { c ->
                while (c.moveToNext()) {
                    result += DeviceCalendar(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        account = c.getString(2).orEmpty(),
                        color = c.getInt(3),
                        primary = !c.isNull(4) && c.getInt(4) == 1,
                    )
                }
            }
        }
        result.sortedWith(compareByDescending<DeviceCalendar> { it.primary }.thenBy { it.account }.thenBy { it.name })
    }

    /** Appelé par la synchro d'arrière-plan : au plus toutes les [EVERY_MS], ou dès que les clubs changent. */
    suspend fun syncIfDue(now: Long = System.currentTimeMillis()) {
        val s = settings.settings.first()
        if (!s.calendarSync) return
        val clubsChanged = prefs.getString(LAST_CLUBS, null) != clubsKey(s.allClubs.map { it.teamId })
        if (clubsChanged || now - prefs.getLong(LAST_SYNC, 0) > EVERY_MS) sync(now)
    }

    /** Met l'agenda à jour tout de suite. false si la synchro est coupée ou impossible. */
    suspend fun sync(now: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val s = settings.settings.first()
            val calendarId = s.calendarId
            if (!s.calendarSync || calendarId == null || !hasPermission()) return@withContext false
            val clubs = s.allClubs
            val seasons = coroutineScope {
                clubs.map { club ->
                    async {
                        val league = Competitions.bySlug(club.competition) ?: Competitions.UCL
                        runCatching { club to football.teamForm(club.teamId, league).seasonMatches }.getOrNull()
                    }
                }.awaitAll()
            }
            val desired = upcomingEntries(seasons.filterNotNull(), now)
            val known = load().toMutableMap()
            val plan = planCalendar(desired, known, calendarId, now, complete = seasons.none { it == null })

            val resolver = context.contentResolver
            plan.delete.forEach { id -> runCatching { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) } }
            known.values.removeAll { it.eventId in plan.delete }
            plan.forget.forEach(known::remove)
            for ((eventId, entry) in plan.update) {
                val updated = runCatching {
                    resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values(entry, calendarId), null, null)
                }.getOrDefault(0)
                // Événement supprimé à la main entre-temps : on le recrée avec le nouvel horaire.
                val id = if (updated > 0) eventId else insert(entry, calendarId)
                if (id != null) known[entry.matchId] = SyncedEvent(id, calendarId, entry.start, entry.fingerprint)
            }
            for (entry in plan.insert) {
                insert(entry, calendarId)?.let { known[entry.matchId] = SyncedEvent(it, calendarId, entry.start, entry.fingerprint) }
            }
            save(known)
            prefs.edit()
                .putLong(LAST_SYNC, now)
                .putString(LAST_CLUBS, clubsKey(clubs.map { it.teamId }))
                .apply()
            true
        }
    }

    /** Nombre de matchs à venir que l'app a mis dans l'agenda. */
    suspend fun upcomingCount(now: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) { load().values.count { it.start > now } }

    /** Synchro coupée : retire les matchs à venir créés par l'app et oublie tout. */
    suspend fun clear(now: Long = System.currentTimeMillis()) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (hasPermission()) {
                clearPlan(load(), now).forEach { id ->
                    runCatching { context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) }
                }
            }
            prefs.edit().clear().apply()
        }
    }

    private fun insert(entry: CalendarEntry, calendarId: Long): Long? = runCatching {
        context.contentResolver.insert(Events.CONTENT_URI, values(entry, calendarId))?.let(ContentUris::parseId)
    }.getOrNull()

    private fun values(e: CalendarEntry, calendarId: Long) = ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, if (e.allDay) context.getString(R.string.calendar_title_tbd, e.title) else e.title)
        put(Events.DTSTART, e.start)
        put(Events.DTEND, e.end)
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        // Une journée entière se cale sur UTC, sinon elle déborde sur la veille ou le lendemain.
        put(Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else TimeZone.getDefault().id)
        put(Events.EVENT_LOCATION, e.location)
        put(Events.DESCRIPTION, context.getString(R.string.calendar_description, e.competition))
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
    }

    private fun load(): Map<String, SyncedEvent> =
        prefs.getString(KNOWN, null)?.let { runCatching { EspnJson.decodeFromString(knownSerializer, it) }.getOrNull() }.orEmpty()

    private fun save(known: Map<String, SyncedEvent>) {
        prefs.edit().putString(KNOWN, EspnJson.encodeToString(knownSerializer, known)).apply()
    }

    private fun clubsKey(ids: List<String>) = ids.sorted().joinToString(",")

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        private const val STATE = "calendar-sync"
        private const val KNOWN = "events"
        private const val LAST_SYNC = "last_sync"
        private const val LAST_CLUBS = "last_clubs"
        /** Les horaires sont fixés quelques semaines avant : toutes les 3 h, c'est largement assez. */
        private const val EVERY_MS = 3 * 60 * 60_000L

        /**
         * Ouvre l'appli d'agenda sur un événement pré-rempli pour [match] (sans permission : l'utilisateur valide).
         * Heure provisoire : on propose la journée entière.
         */
        fun addMatchIntent(context: Context, match: Match): Intent {
            val title = "${match.home.team.name} – ${match.away.team.name}"
            val competition = Competitions.byEspnCode(match.competition)?.name
            val intent = Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
                .putExtra(Events.TITLE, if (match.timeConfirmed) title else context.getString(R.string.calendar_title_tbd, title))
                .putExtra(Events.EVENT_LOCATION, match.venue)
                .putExtra(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            competition?.let { intent.putExtra(Events.DESCRIPTION, context.getString(R.string.calendar_description, it)) }
            return if (match.timeConfirmed) {
                intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, match.kickoff)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, match.kickoff + MATCH_DURATION_MS)
            } else {
                intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, match.kickoff)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, match.kickoff)
            }
        }
    }
}
