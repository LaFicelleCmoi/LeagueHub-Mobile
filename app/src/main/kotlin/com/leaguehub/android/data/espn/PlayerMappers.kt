package com.leaguehub.android.data.espn

import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.PlayerGame
import com.leaguehub.android.domain.model.PlayerPosition
import com.leaguehub.android.domain.model.PlayerProfile
import com.leaguehub.android.domain.model.PlayerSeason
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

// Conversion de la fiche joueur ESPN (bio + stats match par match) en modèle du domaine.

/** Assemble la bio et l'historique ; les totaux officiels d'ESPN priment sur nos sommes. */
fun toPlayerProfile(bio: EspnAthleteResponse, gamelog: EspnGamelogResponse?): PlayerProfile {
    val a = bio.athlete
    val games = gamelog?.toPlayerGames().orEmpty()
    val summary = a.statsSummary?.statistics.orEmpty()
    val (starts, subIns) = startsAndSubs(summary.firstOrNull { it.name == "starts-subIns" }?.displayValue)
        ?: (games.size to 0)
    val lines = gamelog?.statsByEvent()?.values.orEmpty()
    fun total(name: String) = lines.sumOf { it[name] ?: 0 }
    fun official(name: String) = summary.firstOrNull { it.name == name }?.let { it.value?.toInt() ?: it.displayValue.toIntOrNull() }
    return PlayerProfile(
        id = a.id,
        name = a.displayName,
        jersey = a.jersey?.takeIf { it.isNotBlank() },
        position = positionOf(a.position?.abbreviation),
        age = a.age,
        birthEpochDay = parseBirthDate(a.displayDOB)?.toEpochDay(),
        nationality = a.citizenship?.takeIf { it.isNotBlank() },
        flag = a.flag?.href,
        heightCm = parseHeightCm(a.displayHeight),
        weightKg = parseWeightKg(a.displayWeight),
        team = a.team?.takeIf { it.id.isNotEmpty() }?.toTeam(),
        leagueCode = bio.league?.slug,
        leagueName = bio.league?.name,
        season = PlayerSeason(
            starts = starts,
            subIns = subIns,
            goals = official("totalGoals") ?: total("totalGoals"),
            assists = official("goalAssists") ?: total("goalAssists"),
            shots = official("totalShots") ?: total("totalShots"),
            shotsOnTarget = total("shotsOnTarget"),
            yellowCards = total("yellowCards"),
            redCards = total("redCards"),
        ),
        games = games,
    )
}

/** Stats du joueur par match : id du match → (nom de colonne → valeur). */
private fun EspnGamelogResponse.statsByEvent(): Map<String, Map<String, Int>> =
    seasonTypes.flatMap { it.categories }.flatMap { it.events }.distinctBy { it.eventId }
        .associate { line -> line.eventId to names.zip(line.stats) { name, value -> name to (value.toIntOrNull() ?: 0) }.toMap() }

internal fun EspnGamelogResponse.toPlayerGames(): List<PlayerGame> {
    val stats = statsByEvent()
    return events.values.mapNotNull { e ->
        val kickoff = e.gameDate?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: return@mapNotNull null
        val opponent = e.opponent?.takeIf { it.id.isNotEmpty() }?.toTeam() ?: return@mapNotNull null
        val home = e.homeTeamId?.let { it == e.team?.id } ?: (e.atVs == "vs")
        val homeScore = e.homeTeamScore?.toIntOrNull()
        val awayScore = e.awayTeamScore?.toIntOrNull()
        val s = stats[e.id].orEmpty()
        PlayerGame(
            eventId = e.id,
            kickoff = kickoff,
            opponent = opponent,
            home = home,
            goalsFor = if (home) homeScore else awayScore,
            goalsAgainst = if (home) awayScore else homeScore,
            outcome = when (e.gameResult?.uppercase()) {
                "W" -> MatchOutcome.WIN
                "D", "T" -> MatchOutcome.DRAW
                "L" -> MatchOutcome.LOSS
                else -> null
            },
            goals = s["totalGoals"] ?: 0,
            assists = s["goalAssists"] ?: 0,
            yellowCards = s["yellowCards"] ?: 0,
            redCards = s["redCards"] ?: 0,
        )
    }.sortedByDescending { it.kickoff }
}

internal fun positionOf(abbreviation: String?): PlayerPosition? = when (abbreviation?.uppercase()) {
    "G", "GK" -> PlayerPosition.GOALKEEPER
    "D", "CB", "LB", "RB" -> PlayerPosition.DEFENDER
    "M", "CM", "DM", "AM" -> PlayerPosition.MIDFIELDER
    "F", "FW", "ST" -> PlayerPosition.FORWARD
    else -> null
}

/** « 9 (2) » → 9 titularisations, 2 entrées en jeu. */
internal fun startsAndSubs(value: String?): Pair<Int, Int>? {
    val m = Regex("""(\d+)\s*\((\d+)\)""").find(value ?: return null) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}

/** ESPN écrit jour/mois/année : « 9/2/1993 » = 9 février 1993. */
internal fun parseBirthDate(value: String?): LocalDate? {
    val parts = value?.trim()?.split('/')?.map { it.toIntOrNull() } ?: return null
    if (parts.size != 3 || parts.any { it == null }) return null
    return runCatching { LocalDate.of(parts[2]!!, parts[1]!!, parts[0]!!) }.getOrNull()
}

/** « 6' 1" » → 185 ; accepte aussi « 1.85 m » et « 185 cm ». */
internal fun parseHeightCm(value: String?): Int? {
    val v = value?.trim().orEmpty()
    Regex("""(\d+)\s*'\s*(\d+)?""").find(v)?.let { m ->
        val inches = m.groupValues[1].toInt() * 12 + (m.groupValues[2].toIntOrNull() ?: 0)
        return (inches * 2.54).roundToInt().takeIf { it > 0 }
    }
    Regex("""(\d+)\s*cm""").find(v)?.let { return it.groupValues[1].toInt() }
    Regex("""(\d+[.,]\d+)\s*m""").find(v)?.let { return (it.groupValues[1].replace(',', '.').toDouble() * 100).roundToInt() }
    return null
}

/** « 179 lbs » → 81 ; accepte aussi « 81 kg ». */
internal fun parseWeightKg(value: String?): Int? {
    val v = value?.trim().orEmpty()
    Regex("""(\d+(?:\.\d+)?)\s*lbs?""").find(v)?.let { return (it.groupValues[1].toDouble() * 0.45359237).roundToInt().takeIf { kg -> kg > 0 } }
    Regex("""(\d+(?:\.\d+)?)\s*kg""").find(v)?.let { return it.groupValues[1].toDouble().roundToInt() }
    return null
}
