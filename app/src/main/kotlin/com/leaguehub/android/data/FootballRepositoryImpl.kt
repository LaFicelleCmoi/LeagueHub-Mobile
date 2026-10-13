package com.leaguehub.android.data

import android.content.Context
import com.leaguehub.android.data.db.AppDatabase
import com.leaguehub.android.data.db.BlobEntity
import com.leaguehub.android.data.db.MatchEntity
import com.leaguehub.android.data.db.StandingEntity
import com.leaguehub.android.data.db.SyncEntity
import com.leaguehub.android.data.espn.EspnApi
import com.leaguehub.android.data.espn.EspnJson
import com.leaguehub.android.data.espn.toArticles
import com.leaguehub.android.data.espn.toClubMatches
import com.leaguehub.android.data.espn.toLeaders
import com.leaguehub.android.data.espn.toMatchDetail
import com.leaguehub.android.data.espn.toMatches
import com.leaguehub.android.data.espn.toStandings
import com.leaguehub.android.data.espn.toTeam
import com.leaguehub.android.data.ldc.Draw2026
import com.leaguehub.android.domain.knockout.Round
import com.leaguehub.android.domain.knockout.roundOf
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.Article
import com.leaguehub.android.domain.model.Cached
import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.ClubPalmares
import com.leaguehub.android.domain.model.Leaders
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.SeasonTotals
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.repository.ClubHit
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.schedule.PARIS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository unique : l'API ESPN alimente Room, l'UI observe Room.
 * Si ESPN ne répond pas, le dernier état valide en base reste affiché — jamais d'écran vide.
 */
@Singleton
class FootballRepositoryImpl @Inject constructor(
    private val api: EspnApi,
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
) : FootballRepository {

    private val json: Json = EspnJson
    private val clock: () -> Long = System::currentTimeMillis

    // --- Classements ---

    override fun standings(league: LeagueConfig): Flow<Cached<Standings>?> =
        db.standings().observe(league.slug).combine(db.sync().observe(standingsKey(league))) { rows, sync ->
            if (rows.isEmpty() || sync == null) return@combine null
            val decoded = rows.mapNotNull { runCatching { json.decodeFromString(StandingRow.serializer(), it.payload) }.getOrNull() }
            Cached(Standings(sync.extra, decoded), sync.updatedAt, fromCache = true)
        }

    override suspend fun refreshStandings(league: LeagueConfig) {
        val standings = api.standings(league.espnCode).toStandings()
        if (standings.rows.isEmpty()) return // réponse vide : on garde la base
        db.standings().replace(
            league.slug,
            standings.rows.map { StandingEntity(league.slug, it.team.id, it.rank, json.encodeToString(StandingRow.serializer(), it)) },
        )
        db.sync().put(SyncEntity(standingsKey(league), clock(), standings.season))
    }

    // --- Calendrier ---

    private fun calendarWindow(): Pair<Long, Long> {
        val today = LocalDate.now(PARIS)
        val from = today.minusDays(7).atStartOfDay(PARIS).toInstant().toEpochMilli()
        val to = today.plusDays(15).atStartOfDay(PARIS).toInstant().toEpochMilli() - 1
        return from to to
    }

    override fun calendar(league: LeagueConfig): Flow<List<Match>> {
        val (from, to) = calendarWindow()
        return db.matches().observe(league.espnCode, from, to).map(::decodeMatches)
    }

    override suspend fun refreshCalendar(league: LeagueConfig) {
        val (from, to) = calendarWindow()
        val matches = monthsBetween(from, to).let { months -> scoreboardMonths(league.espnCode, months) }
            .filter { it.kickoff in from..to }
        db.matches().replaceWindow(league.espnCode, from, to, matches.map(::toEntity))
        db.sync().put(SyncEntity(calendarKey(league), clock()))
    }

    override suspend fun liveMatches(league: LeagueConfig): List<Match> = coroutineScope {
        // Hier et aujourd'hui : un match du soir peut finir après minuit.
        val today = LocalDate.now(PARIS)
        val days = listOf(today.minusDays(1), today).map { it.format(DateTimeFormatter.BASIC_ISO_DATE) }
        val matches = days.map { day -> async { api.scoreboard(league.espnCode, day).toMatches(league.espnCode) } }
            .awaitAll().flatten().distinctBy { it.id }.sortedBy { it.kickoff }
        db.matches().upsert(matches.map(::toEntity))
        matches
    }

    // --- Buteurs, actualités (cache JSON) ---

    override suspend fun leaders(league: LeagueConfig): Cached<Leaders> =
        cachedBlob("leaders:${league.slug}", Leaders.serializer()) { api.statistics(league.espnCode).toLeaders() }

    override suspend fun news(league: LeagueConfig): Cached<List<Article>> =
        cachedBlob("news:${league.slug}", ListSerializer(Article.serializer())) { api.news(league.espnCode).toArticles() }

    private suspend fun <T> cachedBlob(key: String, serializer: kotlinx.serialization.KSerializer<T>, fetch: suspend () -> T): Cached<T> {
        return try {
            val fresh = fetch()
            val now = clock()
            db.blobs().put(BlobEntity(key, json.encodeToString(serializer, fresh), now))
            Cached(fresh, now, fromCache = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val blob = db.blobs().get(key) ?: throw e
            Cached(json.decodeFromString(serializer, blob.json), blob.updatedAt, fromCache = true)
        }
    }

    // --- Ligue des champions ---

    /** Saison en cours : de juillet de l'année de début à juin de l'année suivante. */
    private fun uclSeasonWindow(): Pair<YearMonth, YearMonth> {
        val now = YearMonth.now(PARIS)
        val startYear = if (now.monthValue >= 7) now.year else now.year - 1
        return YearMonth.of(startYear, 7) to YearMonth.of(startYear + 1, 6)
    }

    override fun uclSeason(): Flow<Cached<List<Match>>?> {
        val (start, end) = uclSeasonWindow()
        val from = start.atDay(1).atStartOfDay(PARIS).toInstant().toEpochMilli()
        val to = end.plusMonths(1).atDay(1).atStartOfDay(PARIS).toInstant().toEpochMilli() - 1
        val code = Competitions.UCL.espnCode
        return db.matches().observe(code, from, to).combine(db.sync().observe(UCL_KEY)) { rows, sync ->
            if (sync == null && rows.isEmpty()) null
            else Cached(decodeMatches(rows), sync?.updatedAt ?: 0L, fromCache = true)
        }
    }

    override suspend fun refreshUclSeason() {
        val (start, end) = uclSeasonWindow()
        val months = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.toList()
        val code = Competitions.UCL.espnCode
        val matches = scoreboardMonths(code, months).filter {
            roundOf(it.roundSlug, monthOf(it.kickoff)) != Round.QUALIFYING
        }
        db.matches().upsert(matches.map(::toEntity))
        db.sync().put(SyncEntity(UCL_KEY, clock()))
    }

    // --- Club ---

    override suspend fun teamForm(teamId: String, league: LeagueConfig): TeamForm = coroutineScope {
        val current = async { api.teamSchedule(teamId) }
        val fixtures = async { runCatching { api.teamSchedule(teamId, fixture = true) }.getOrNull() }
        val played = current.await()
        val upcoming = fixtures.await()

        val team = (played.team ?: upcoming?.team)?.toTeam() ?: Team(teamId, "", "", "", null)
        val all = (played.toClubMatches(teamId) + upcoming?.toClubMatches(teamId).orEmpty())
            .associateBy { it.id }.values.sortedBy { it.kickoff }

        val finished = all.filter { it.state == MatchState.POST && it.outcome != null }
        TeamForm(
            team = team,
            season = seasonTotals(finished),
            results = finished.takeLast(5).reversed(),
            live = all.firstOrNull { it.state == MatchState.IN },
            next = all.firstOrNull { it.state == MatchState.PRE && it.kickoff > clock() - KICKOFF_GRACE },
            seasonMatches = all,
        )
    }

    private fun seasonTotals(matches: List<ClubMatch>) = SeasonTotals(
        played = matches.size,
        wins = matches.count { it.outcome == MatchOutcome.WIN },
        draws = matches.count { it.outcome == MatchOutcome.DRAW },
        losses = matches.count { it.outcome == MatchOutcome.LOSS },
        goalsFor = matches.sumOf { it.goalsFor ?: 0 },
        goalsAgainst = matches.sumOf { it.goalsAgainst ?: 0 },
    )

    override suspend fun matchDetail(competitionCode: String, eventId: String): MatchDetail =
        api.summary(competitionCode, eventId).toMatchDetail(competitionCode, eventId)
            ?: throw IllegalStateException("Match $eventId introuvable")

    override suspend fun searchClubs(query: String): List<ClubHit> {
        val needle = normalize(query)
        if (needle.length < 2) return emptyList()
        val hits = LinkedHashMap<String, ClubHit>()
        for (entity in db.standings().all()) {
            val league = Competitions.bySlug(entity.league) ?: continue
            val row = runCatching { json.decodeFromString(StandingRow.serializer(), entity.payload) }.getOrNull() ?: continue
            if (normalize(row.team.name).contains(needle) || normalize(row.team.abbreviation) == needle) {
                hits.putIfAbsent(row.team.id, ClubHit(row.team, league))
            }
        }
        for (club in Draw2026.QUALIFIED) {
            if (normalize(club.name).contains(needle)) {
                hits.putIfAbsent(club.id, ClubHit(Team(club.id, club.name, club.name, club.abbr, club.logo, club.color), Competitions.UCL))
            }
        }
        return hits.values.sortedBy { it.team.name }
    }

    private val palmaresCache = ConcurrentHashMap<String, Map<String, ClubPalmares>>()

    override suspend fun palmares(league: LeagueConfig, teamId: String): ClubPalmares? {
        if (league !in Competitions.LEAGUES) {
            // Club de LDC : on cherche dans les 5 championnats.
            return Competitions.LEAGUES.firstNotNullOfOrNull { palmares(it, teamId) }
        }
        val clubs = palmaresCache.getOrPut(league.slug) {
            runCatching {
                val text = context.assets.open("palmares/${league.slug}.json").bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(text).jsonObject
                (root["clubs"] as? JsonObject).orEmpty().mapValues { json.decodeFromJsonElement(ClubPalmares.serializer(), it.value) }
            }.getOrDefault(emptyMap())
        }
        return clubs[teamId]
    }

    // --- Utilitaires ---

    /**
     * ESPN refuse régulièrement les plages de plusieurs jours mais accepte toujours un mois entier :
     * on demande mois par mois, en parallèle.
     */
    private suspend fun scoreboardMonths(code: String, months: List<YearMonth>): List<Match> = coroutineScope {
        months.map { m -> async { api.scoreboard(code, m.format(MONTH)).toMatches(code) } }
            .awaitAll().flatten().distinctBy { it.id }
    }

    private fun monthsBetween(from: Long, to: Long): List<YearMonth> {
        // Un jour de marge de chaque côté : ESPN range les matchs par mois dans son propre fuseau.
        val first = YearMonth.from(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(from - DAY), PARIS))
        val last = YearMonth.from(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(to + DAY), PARIS))
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }

    private fun monthOf(time: Long): Int = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(time), PARIS).monthValue

    private fun toEntity(m: Match) = MatchEntity(m.id, m.competition, m.kickoff, m.state.name, json.encodeToString(Match.serializer(), m))

    private fun decodeMatches(rows: List<MatchEntity>): List<Match> =
        rows.mapNotNull { runCatching { json.decodeFromString(Match.serializer(), it.payload) }.getOrNull() }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    private companion object {
        const val UCL_KEY = "ucl-season"
        const val DAY = 86_400_000L
        /** Un match encore « à venir » après son heure reste le prochain tant qu'ESPN ne l'a pas lancé. */
        const val KICKOFF_GRACE = 3 * 60 * 60_000L
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
        fun standingsKey(league: LeagueConfig) = "standings:${league.slug}"
        fun calendarKey(league: LeagueConfig) = "calendar:${league.slug}"
    }
}
