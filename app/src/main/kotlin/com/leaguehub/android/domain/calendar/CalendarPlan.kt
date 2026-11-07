package com.leaguehub.android.domain.calendar

import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.schedule.PARIS
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

// Recopie des matchs des clubs suivis dans l'agenda du téléphone : la partie « décision »,
// sans Android, pour pouvoir la tester. L'écriture dans l'agenda est faite par `CalendarSync`.

/** Durée réservée dans l'agenda : 90 min, mi-temps et arrêts de jeu. */
const val MATCH_DURATION_MS = 2 * 60 * 60_000L
private const val DAY_MS = 24 * 60 * 60_000L

/** Un match tel qu'il doit apparaître dans l'agenda. */
data class CalendarEntry(
    val matchId: String,
    /** « Domicile – Extérieur ». */
    val title: String,
    /** Journée entière : minuit UTC du jour du match (convention Android). */
    val start: Long,
    val end: Long,
    /** Heure pas encore fixée : on réserve la journée. */
    val allDay: Boolean,
    val location: String?,
    val competition: String,
) {
    /** Change dès qu'un détail visible de l'événement change. */
    val fingerprint: String get() = listOf(title, start, end, allDay, location, competition).joinToString("|")
}

/** Ce qu'on sait d'un événement déjà créé dans l'agenda. */
@Serializable
data class SyncedEvent(val eventId: Long, val calendarId: Long, val start: Long, val fingerprint: String)

data class CalendarPlan(
    val insert: List<CalendarEntry>,
    /** Événements existants à réécrire (identifiant Android → nouveau contenu). */
    val update: List<Pair<Long, CalendarEntry>>,
    val delete: List<Long>,
    /** Matchs passés : l'événement reste dans l'agenda comme souvenir, on arrête juste de le suivre. */
    val forget: List<String>,
)

fun calendarEntry(club: FavoriteClub, m: ClubMatch): CalendarEntry {
    val title = if (m.home) "${club.name} – ${m.opponent.name}" else "${m.opponent.name} – ${club.name}"
    val (start, end) = if (m.timeConfirmed) {
        m.kickoff to m.kickoff + MATCH_DURATION_MS
    } else {
        val day = Instant.ofEpochMilli(m.kickoff).atZone(PARIS).toLocalDate()
        val midnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        midnight to midnight + DAY_MS
    }
    return CalendarEntry(m.id, title, start, end, allDay = !m.timeConfirmed, location = m.venue, competition = m.competition)
}

/** Matchs à venir de chaque club, une seule fois si deux clubs suivis s'affrontent. */
fun upcomingEntries(clubs: List<Pair<FavoriteClub, List<ClubMatch>>>, now: Long): List<CalendarEntry> =
    clubs.flatMap { (club, matches) ->
        matches.filter { it.state == MatchState.PRE && it.kickoff > now }.map { calendarEntry(club, it) }
    }.distinctBy { it.matchId }.sortedBy { it.start }

/**
 * Compare ce qui doit être dans l'agenda ([desired]) à ce qu'on y a déjà mis ([known]).
 * [complete] = false si le calendrier d'un club n'a pas pu être lu : on ne supprime alors rien,
 * faute de savoir si un match a vraiment disparu.
 */
fun planCalendar(
    desired: List<CalendarEntry>,
    known: Map<String, SyncedEvent>,
    calendarId: Long,
    now: Long,
    complete: Boolean,
): CalendarPlan {
    val insert = mutableListOf<CalendarEntry>()
    val update = mutableListOf<Pair<Long, CalendarEntry>>()
    val delete = mutableListOf<Long>()
    val forget = mutableListOf<String>()
    for (entry in desired) {
        val k = known[entry.matchId]
        when {
            k == null -> insert += entry
            // Autre agenda choisi : on déménage l'événement.
            k.calendarId != calendarId -> { delete += k.eventId; insert += entry }
            k.fingerprint != entry.fingerprint -> update += k.eventId to entry
        }
    }
    val wanted = desired.mapTo(HashSet()) { it.matchId }
    for ((matchId, k) in known) {
        if (matchId in wanted) continue
        when {
            k.start <= now -> forget += matchId
            // Reporté sans date, club plus suivi, ou ancien agenda : l'événement à venir est retiré.
            complete || k.calendarId != calendarId -> delete += k.eventId
        }
    }
    return CalendarPlan(insert, update, delete, forget)
}

/** Synchro coupée : on retire les matchs à venir, les matchs passés restent dans l'agenda. */
fun clearPlan(known: Map<String, SyncedEvent>, now: Long): List<Long> =
    known.values.filter { it.start > now }.map { it.eventId }
