package com.leaguehub.android.data.espn

import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.Article
import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.EuropeanCup
import com.leaguehub.android.domain.model.Leader
import com.leaguehub.android.domain.model.Leaders
import com.leaguehub.android.domain.model.Lineup
import com.leaguehub.android.domain.model.LineupPlayer
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.MatchEvent
import com.leaguehub.android.domain.model.MatchEventKind
import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchStat
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.domain.model.TimelineEvent
import com.leaguehub.android.domain.model.TimelineKind
import com.leaguehub.android.domain.model.Zone
import com.leaguehub.android.domain.model.ZoneTone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Mappers JSON ESPN -> modèles du domaine (portage de `src/lib/espn/api.ts` et du parsing
// défensif d'espn.js). Fonctions pures : testées sans Android ni réseau.

val EspnJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

fun teamLogo(id: String): String = "https://a.espncdn.com/i/teamlogos/soccer/500/$id.png"

fun EspnTeam.toTeam(): Team {
    val logo = logo ?: logos.firstOrNull { "default" in it.rel }?.href ?: logos.firstOrNull()?.href
        ?: id.takeIf { it.isNotEmpty() }?.let(::teamLogo)
    val display = displayName.ifEmpty { name ?: shortDisplayName ?: "—" }
    return Team(
        id = id,
        name = display,
        shortName = shortDisplayName ?: display,
        abbreviation = abbreviation ?: display.take(3).uppercase(),
        logo = logo,
        color = color?.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) },
    )
}

/** Date ISO d'ESPN (« 2026-10-14T19:00Z », secondes facultatives) -> ms epoch, ou null. */
fun parseEspnDate(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value.replace("Z", ":00Z")).toInstant().toEpochMilli() }
        .getOrNull()
}

/** Score : chaîne (scoreboard) ou objet { value, displayValue } (calendrier d'équipe). */
fun scoreOf(element: JsonElement?): Int? = when (element) {
    is JsonPrimitive -> element.intOrNull ?: element.doubleOrNull?.toInt() ?: element.content.toIntOrNull()
    is JsonObject -> (element["value"] as? JsonPrimitive)?.doubleOrNull?.toInt()
        ?: (element["displayValue"] as? JsonPrimitive)?.content?.toIntOrNull()
    else -> null
}

private fun stateOf(value: String): MatchState = when (value) {
    "in" -> MatchState.IN
    "post" -> MatchState.POST
    else -> MatchState.PRE
}

private fun statValue(stats: List<EspnStat>, name: String): Double? {
    val stat = stats.firstOrNull { it.name == name } ?: return null
    return stat.value ?: stat.displayValue.toDoubleOrNull()
}

// --- Classement ---

fun toZone(description: String?): Zone? {
    if (description.isNullOrBlank()) return null
    val text = description.lowercase()
    val qualifying = "qualifying" in text
    return when {
        "champions league" in text -> Zone("ucl", ZoneTone.UCL, qualifying)
        "europa league" in text -> Zone("uel", ZoneTone.UEL, qualifying)
        "conference league" in text -> Zone("uecl", ZoneTone.UECL, qualifying)
        Regex("relegation play-?off").containsMatchIn(text) -> Zone("playoff", ZoneTone.PLAYOFF, qualifying)
        "relegat" in text -> Zone("relegation", ZoneTone.RELEGATION, qualifying)
        else -> Zone(description, ZoneTone.OTHER, qualifying)
    }
}

fun EspnStandingsResponse.toStandings(): Standings {
    val table = children.firstOrNull()?.standings
    val rows = table?.entries.orEmpty().map { entry ->
        fun stat(name: String) = statValue(entry.stats, name)?.toInt() ?: 0
        StandingRow(
            rank = stat("rank"),
            rankChange = stat("rankChange"),
            team = entry.team.toTeam(),
            played = stat("gamesPlayed"),
            wins = stat("wins"),
            draws = stat("ties"),
            losses = stat("losses"),
            goalsFor = stat("pointsFor"),
            goalsAgainst = stat("pointsAgainst"),
            goalDiff = stat("pointDifferential"),
            points = stat("points"),
            zone = toZone(entry.note?.description),
        )
    }.sortedBy { it.rank }
    val season = table?.seasonDisplayName?.let { Regex("\\d{4}-\\d{2}").find(it)?.value }.orEmpty()
    return Standings(season, rows)
}

// --- Matchs ---

/** Statuts sans score affichable. */
private val STATUSES_WITHOUT_SCORE = setOf("STATUS_POSTPONED", "STATUS_CANCELED")

private fun EspnDetail.toMatchEvent(homeTeamId: String): MatchEvent? {
    val athlete = athletesInvolved.firstOrNull() ?: return null
    if (shootout) return null
    val kind = when {
        redCard -> MatchEventKind.RED_CARD
        yellowCard -> MatchEventKind.YELLOW_CARD
        !scoringPlay -> return null
        ownGoal -> MatchEventKind.OWN_GOAL
        penaltyKick -> MatchEventKind.PENALTY
        else -> MatchEventKind.GOAL
    }
    return MatchEvent(
        minute = clock?.displayValue.orEmpty(),
        player = athlete.shortName ?: athlete.displayName,
        kind = kind,
        side = if (team?.id == homeTeamId) Side.HOME else Side.AWAY,
    )
}

/** Nombre de matchs du bilan « V-N-D » fourni avec chaque match (anti double comptage). */
private fun EspnCompetitor.recordGames(): Int? {
    val summary = records.firstOrNull { it.type == "total" }?.summary ?: return null
    val counts = summary.split("-").map { it.trim().toIntOrNull() }
    return if (counts.size == 3 && counts.all { it != null }) counts.sumOf { it!! } else null
}

fun EspnEvent.toMatch(competitionCode: String): Match? {
    val competition = competitions.firstOrNull() ?: return null
    val home = competition.competitors.firstOrNull { it.homeAway == "home" } ?: competition.competitors.getOrNull(0) ?: return null
    val away = competition.competitors.firstOrNull { it.homeAway == "away" } ?: competition.competitors.getOrNull(1) ?: return null
    if (home === away) return null
    val kickoff = parseEspnDate(date) ?: parseEspnDate(competition.date) ?: return null
    val status = competition.status ?: status ?: return null
    val state = stateOf(status.type.state)
    val showScore = state != MatchState.PRE && status.type.name !in STATUSES_WITHOUT_SCORE

    fun side(c: EspnCompetitor) = MatchSide(
        team = c.team.toTeam(),
        score = if (showScore) scoreOf(c.score) ?: 0 else null,
        winner = c.winner == true,
        played = c.recordGames(),
        shootout = if (showScore) c.shootoutScore?.toInt() else null,
    )

    return Match(
        id = id,
        competition = competitionCode,
        kickoff = kickoff,
        state = state,
        statusName = status.type.name,
        clock = if (state == MatchState.IN) status.displayClock ?: status.type.shortDetail else "",
        venue = competition.venue?.fullName,
        home = side(home),
        away = side(away),
        events = competition.details.mapNotNull { it.toMatchEvent(home.team.id) },
        roundSlug = season?.slug,
    )
}

/** Décode chaque événement séparément : un événement malformé est filtré, jamais crashant. */
fun decodeEvents(events: List<JsonElement>): List<EspnEvent> =
    events.mapNotNull { runCatching { EspnJson.decodeFromJsonElement(EspnEvent.serializer(), it) }.getOrNull() }

fun EspnScoreboardResponse.toMatches(competitionCode: String): List<Match> =
    decodeEvents(events).mapNotNull { runCatching { it.toMatch(competitionCode) }.getOrNull() }

// --- Buteurs ---

fun EspnStatisticsResponse.toLeaders(limit: Int = 20): Leaders {
    fun pick(category: String): List<Leader> {
        val result = mutableListOf<Leader>()
        stats.firstOrNull { it.name == category }?.leaders.orEmpty()
            .filter { it.value > 0 }.take(limit)
            .forEach { l ->
                val value = l.value.toInt()
                val previous = result.lastOrNull()
                result += Leader(
                    id = l.athlete.id,
                    // Classement « sportif » : les ex æquo partagent le même rang.
                    rank = if (previous != null && previous.value == value) previous.rank else result.size + 1,
                    player = l.athlete.displayName,
                    team = l.athlete.team?.toTeam(),
                    value = value,
                    appearances = statValue(l.athlete.statistics, "appearances")?.toInt(),
                )
            }
        return result
    }
    return Leaders(goals = pick("goalsLeaders"), assists = pick("assistsLeaders"))
}

// --- Actualités ---

fun EspnNewsResponse.toArticles(): List<Article> = articles.mapIndexed { i, a ->
    val image = a.images.firstOrNull { it.type == "header" } ?: a.images.firstOrNull()
    Article(
        id = (a.id ?: i.toLong()).toString(),
        headline = a.headline,
        description = a.description.orEmpty(),
        published = parseEspnDate(a.published),
        image = image?.url?.takeIf { it.isNotEmpty() },
        url = a.links?.web?.href?.takeIf { it.isNotEmpty() },
    )
}

// --- Matchs d'un club (toutes compétitions) ---

/** Compétitions officielles retenues ; amicaux et tournois de préparation sont ignorés. */
val COMPETITION_LABELS: Map<String, String> = Competitions.ALL.associate { it.espnCode to it.name } + mapOf(
    "eng.2" to "Championship",
    "eng.fa" to "FA Cup",
    "eng.league_cup" to "Carabao Cup",
    "eng.charity" to "Community Shield",
    "esp.copa_del_rey" to "Copa del Rey",
    "esp.super_cup" to "Supercopa",
    "ita.coppa_italia" to "Coppa Italia",
    "ita.super_cup" to "Supercoppa",
    "ger.dfb_pokal" to "DFB-Pokal",
    "ger.super_cup" to "Supercup",
    "fra.coupe_de_france" to "Coupe de France",
    "fra.super_cup" to "Trophée des champions",
    "uefa.europa" to "Ligue Europa",
    "uefa.europa.conf" to "Ligue Conférence",
    "uefa.champions_qual" to "Ligue des champions (qualif.)",
    "uefa.europa_qual" to "Ligue Europa (qualif.)",
    "uefa.europa.conf_qual" to "Ligue Conférence (qualif.)",
    "uefa.super_cup" to "Supercoupe de l'UEFA",
    "fifa.cwc" to "Coupe du monde des clubs",
    "fifa.intercontinental_cup" to "Coupe intercontinentale",
)

private val NATIONAL_LEAGUE = Regex("^[a-z]{3}\\.\\d$")

fun isOfficial(slug: String): Boolean = slug in COMPETITION_LABELS || NATIONAL_LEAGUE.matches(slug)

fun europeanCup(slug: String): EuropeanCup? = when (slug) {
    "uefa.champions", "uefa.champions_qual" -> EuropeanCup.UCL
    "uefa.europa", "uefa.europa_qual" -> EuropeanCup.UEL
    "uefa.europa.conf", "uefa.europa.conf_qual" -> EuropeanCup.UECL
    else -> null
}

/** Un match du calendrier d'un club, vu depuis ce club ; null si non officiel ou malformé. */
fun EspnEvent.toClubMatch(teamId: String): ClubMatch? {
    val slug = league?.slug.orEmpty()
    if (!isOfficial(slug)) return null
    val competition = competitions.firstOrNull() ?: return null
    val us = competition.competitors.firstOrNull { it.team.id == teamId } ?: return null
    val them = competition.competitors.firstOrNull { it.team.id != teamId } ?: return null
    val kickoff = parseEspnDate(date) ?: return null
    val status = competition.status ?: status ?: return null
    val state = stateOf(status.type.state)
    val scored = state != MatchState.PRE && status.type.name !in STATUSES_WITHOUT_SCORE
    val gf = if (scored) scoreOf(us.score) ?: 0 else null
    val ga = if (scored) scoreOf(them.score) ?: 0 else null

    val outcome = if (state == MatchState.POST && gf != null && ga != null) {
        when {
            us.winner == true -> MatchOutcome.WIN
            them.winner == true -> MatchOutcome.LOSS
            gf > ga -> MatchOutcome.WIN
            gf < ga -> MatchOutcome.LOSS
            else -> MatchOutcome.DRAW
        }
    } else null

    val detail = when {
        us.shootoutScore != null && them.shootoutScore != null -> "${us.shootoutScore.toInt()}-${them.shootoutScore.toInt()}"
        status.type.name == "STATUS_FINAL_AET" -> "aet"
        else -> null
    }

    return ClubMatch(
        id = id,
        kickoff = kickoff,
        competitionCode = slug,
        competition = COMPETITION_LABELS[slug] ?: league?.abbreviation ?: league?.name.orEmpty(),
        home = us.homeAway == "home",
        opponent = them.team.toTeam(),
        goalsFor = gf,
        goalsAgainst = ga,
        state = state,
        statusName = status.type.name,
        clock = if (state == MatchState.IN) status.displayClock ?: status.type.shortDetail else "",
        outcome = outcome,
        detail = detail,
        venue = competition.venue?.fullName,
        europeanCup = europeanCup(slug),
    )
}

fun EspnScheduleResponse.toClubMatches(teamId: String): List<ClubMatch> =
    decodeEvents(events).mapNotNull { runCatching { it.toClubMatch(teamId) }.getOrNull() }

// --- Détail d'un match ---

private fun timelineKind(slug: String, text: String): TimelineKind {
    val s = "$slug ${text.lowercase()}"
    return when {
        "own-goal" in s || "own goal" in s -> TimelineKind.OWN_GOAL
        "penalty---scored" in s || ("penalty" in s && "goal" in s && "missed" !in s && "saved" !in s) -> TimelineKind.PENALTY_GOAL
        "penalty" in s && ("missed" in s || "saved" in s) -> TimelineKind.PENALTY_MISSED
        "goal" in s -> TimelineKind.GOAL
        "second-yellow" in s || "second yellow" in s -> TimelineKind.SECOND_YELLOW
        "yellow" in s -> TimelineKind.YELLOW_CARD
        "red" in s -> TimelineKind.RED_CARD
        "substitution" in s -> TimelineKind.SUBSTITUTION
        "var" in slug -> TimelineKind.VAR
        "half" in s || "kickoff" in s || "end" in s || "start" in s -> TimelineKind.PERIOD
        else -> TimelineKind.OTHER
    }
}

/** Statistiques retenues pour la comparaison, dans l'ordre d'affichage. */
val MATCH_STAT_KEYS = listOf(
    "possessionPct", "totalShots", "shotsOnTarget", "wonCorners", "foulsCommitted",
    "yellowCards", "redCards", "offsides", "saves", "accuratePasses",
)

fun EspnSummaryResponse.toMatchDetail(competitionCode: String, eventId: String): MatchDetail? {
    val competition = header?.competitions?.firstOrNull() ?: return null
    val event = EspnEvent(
        id = eventId,
        date = competition.date ?: return null,
        status = competition.status,
        competitions = listOf(competition),
        season = header.season,
    )
    val match = event.toMatch(competitionCode) ?: return null
    val homeId = match.home.team.id

    val timeline = keyEvents.mapNotNull { e ->
        val slug = e.type?.type.orEmpty()
        val label = e.type?.text ?: e.text ?: return@mapNotNull null
        val kind = timelineKind(slug, label)
        TimelineEvent(
            id = e.id,
            minute = e.clock?.displayValue.orEmpty(),
            kind = kind,
            label = label,
            side = e.team?.id?.let { if (it == homeId) Side.HOME else Side.AWAY },
            players = e.participants.mapNotNull { it.athlete?.displayName },
        )
    }

    val home = boxscore?.teams?.firstOrNull { it.homeAway == "home" || it.team.id == homeId }
    val away = boxscore?.teams?.firstOrNull { it != home }
    val stats = if (home == null || away == null) emptyList() else MATCH_STAT_KEYS.mapNotNull { key ->
        val h = home.statistics.firstOrNull { it.name == key } ?: return@mapNotNull null
        val a = away.statistics.firstOrNull { it.name == key } ?: return@mapNotNull null
        val hv = h.displayValue.toFloatOrNull()
        val av = a.displayValue.toFloatOrNull()
        val share = if (hv != null && av != null && hv + av > 0) hv / (hv + av) else null
        MatchStat(key, h.label ?: key, h.displayValue, a.displayValue, share)
    }

    val lineups = rosters.map { r ->
        Lineup(
            side = if (r.homeAway == "home") Side.HOME else Side.AWAY,
            team = r.team.toTeam(),
            formation = r.formation,
            players = r.roster.map {
                LineupPlayer(it.athlete.id, it.athlete.displayName, it.jersey, it.position?.abbreviation, it.starter)
            }.sortedByDescending { it.starter },
        )
    }

    return MatchDetail(
        match = match,
        timeline = timeline,
        stats = stats,
        lineups = lineups,
        attendance = gameInfo?.attendance?.takeIf { it > 0 },
        referee = gameInfo?.officials?.firstOrNull()?.let { it.displayName ?: it.fullName },
    )
}
