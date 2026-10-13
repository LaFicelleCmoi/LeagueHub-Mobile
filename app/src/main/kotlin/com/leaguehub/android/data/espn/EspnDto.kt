package com.leaguehub.android.data.espn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Typage (partiel) des réponses JSON de l'API ESPN (portage de `src/lib/espn/types.ts`).
// Seuls les champs réellement exploités sont déclarés ; tout le reste est ignoré.

@Serializable
data class EspnLogo(val href: String, val rel: List<String> = emptyList())

@Serializable
data class EspnTeam(
    val id: String = "",
    val displayName: String = "",
    val shortDisplayName: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val color: String? = null,
    val logo: String? = null,
    val logos: List<EspnLogo> = emptyList(),
)

@Serializable
data class EspnStat(
    val name: String = "",
    val label: String? = null,
    val value: Double? = null,
    val displayValue: String = "",
)

// --- Classement : /apis/v2/sports/soccer/{league}/standings ---

@Serializable
data class EspnNote(val color: String? = null, val description: String? = null, val rank: Int? = null)

@Serializable
data class EspnStandingEntry(val team: EspnTeam, val stats: List<EspnStat> = emptyList(), val note: EspnNote? = null)

@Serializable
data class EspnStandingTable(val seasonDisplayName: String? = null, val entries: List<EspnStandingEntry> = emptyList())

@Serializable
data class EspnStandingChild(val name: String = "", val standings: EspnStandingTable = EspnStandingTable())

@Serializable
data class EspnStandingsResponse(val name: String = "", val children: List<EspnStandingChild> = emptyList())

// --- Matchs : /apis/site/v2/sports/soccer/{league}/scoreboard ---

@Serializable
data class EspnStatusType(
    val name: String = "",
    val state: String = "pre",
    val completed: Boolean = false,
    val description: String = "",
    val detail: String = "",
    val shortDetail: String = "",
)

@Serializable
data class EspnStatus(val displayClock: String? = null, val period: Int? = null, val type: EspnStatusType = EspnStatusType())

@Serializable
data class EspnRecord(val type: String? = null, val summary: String? = null)

@Serializable
data class EspnCompetitor(
    val id: String = "",
    val homeAway: String = "",
    val winner: Boolean? = null,
    /** Chaîne dans un scoreboard, objet { value, displayValue } dans un calendrier d'équipe. */
    val score: JsonElement? = null,
    val shootoutScore: Double? = null,
    val team: EspnTeam = EspnTeam(),
    /** Bilan « V-N-D » ; un match terminé y est inclus, un match en cours non. */
    val records: List<EspnRecord> = emptyList(),
)

@Serializable
data class EspnClock(val value: Double? = null, val displayValue: String = "")

@Serializable
data class EspnAthleteRef(val id: String = "", val displayName: String = "", val shortName: String? = null)

@Serializable
data class EspnTeamRef(val id: String = "")

@Serializable
data class EspnDetail(
    val clock: EspnClock? = null,
    val team: EspnTeamRef? = null,
    val scoringPlay: Boolean = false,
    val redCard: Boolean = false,
    val yellowCard: Boolean = false,
    val penaltyKick: Boolean = false,
    val ownGoal: Boolean = false,
    val shootout: Boolean = false,
    val athletesInvolved: List<EspnAthleteRef> = emptyList(),
)

@Serializable
data class EspnVenue(val fullName: String? = null)

@Serializable
data class EspnCompetition(
    val id: String = "",
    val date: String? = null,
    val status: EspnStatus? = null,
    val venue: EspnVenue? = null,
    val competitors: List<EspnCompetitor> = emptyList(),
    val details: List<EspnDetail> = emptyList(),
)

@Serializable
data class EspnSeasonRef(val slug: String? = null, val year: Int? = null)

@Serializable
data class EspnLeagueRef(val name: String? = null, val abbreviation: String? = null, val slug: String? = null)

@Serializable
data class EspnEvent(
    val id: String,
    val date: String,
    val name: String = "",
    val status: EspnStatus? = null,
    val competitions: List<EspnCompetition> = emptyList(),
    val season: EspnSeasonRef? = null,
    /** Présent dans le calendrier d'une équipe (toutes compétitions). */
    val league: EspnLeagueRef? = null,
)

/** Les événements restent bruts : chacun est décodé séparément, un événement malformé est filtré. */
@Serializable
data class EspnScoreboardResponse(val events: List<JsonElement> = emptyList())

// --- Statistiques : /apis/site/v2/sports/soccer/{league}/statistics ---

@Serializable
data class EspnLeaderAthlete(
    val id: String = "",
    val displayName: String = "",
    val team: EspnTeam? = null,
    val statistics: List<EspnStat> = emptyList(),
)

@Serializable
data class EspnLeader(val value: Double = 0.0, val athlete: EspnLeaderAthlete = EspnLeaderAthlete())

@Serializable
data class EspnLeaderCategory(val name: String = "", val leaders: List<EspnLeader> = emptyList())

@Serializable
data class EspnStatisticsResponse(val stats: List<EspnLeaderCategory> = emptyList())

// --- Actualités : /apis/site/v2/sports/soccer/{league}/news ---

@Serializable
data class EspnImage(val url: String = "", val type: String? = null)

@Serializable
data class EspnWebLink(val href: String = "")

@Serializable
data class EspnLinks(val web: EspnWebLink? = null)

@Serializable
data class EspnArticle(
    val id: Long? = null,
    val headline: String = "",
    val description: String? = null,
    val published: String = "",
    val images: List<EspnImage> = emptyList(),
    val links: EspnLinks? = null,
)

@Serializable
data class EspnNewsResponse(val articles: List<EspnArticle> = emptyList())

// --- Calendrier d'une équipe : /apis/site/v2/sports/soccer/all/teams/{id}/schedule ---

@Serializable
data class EspnScheduleResponse(val team: EspnTeam? = null, val events: List<JsonElement> = emptyList())

// --- Détail d'un match : /apis/site/v2/sports/soccer/{league}/summary?event={id} ---

@Serializable
data class EspnKeyEventType(val id: String? = null, val text: String? = null, val type: String? = null)

@Serializable
data class EspnParticipant(val athlete: EspnAthleteRef? = null)

@Serializable
data class EspnKeyEvent(
    val id: String = "",
    val type: EspnKeyEventType? = null,
    val text: String? = null,
    val clock: EspnClock? = null,
    val team: EspnTeamRef? = null,
    val participants: List<EspnParticipant> = emptyList(),
)

@Serializable
data class EspnBoxscoreTeam(val team: EspnTeam = EspnTeam(), val homeAway: String? = null, val statistics: List<EspnStat> = emptyList())

@Serializable
data class EspnBoxscore(val teams: List<EspnBoxscoreTeam> = emptyList())

@Serializable
data class EspnPosition(val abbreviation: String? = null)

@Serializable
data class EspnRosterPlayer(
    val starter: Boolean = false,
    val jersey: String? = null,
    val athlete: EspnAthleteRef = EspnAthleteRef(),
    val position: EspnPosition? = null,
)

@Serializable
data class EspnRoster(
    val homeAway: String = "",
    val team: EspnTeam = EspnTeam(),
    val formation: String? = null,
    val roster: List<EspnRosterPlayer> = emptyList(),
)

@Serializable
data class EspnOfficial(val displayName: String? = null, val fullName: String? = null)

@Serializable
data class EspnGameInfo(val attendance: Int? = null, val officials: List<EspnOfficial> = emptyList())

@Serializable
data class EspnHeader(val competitions: List<EspnCompetition> = emptyList(), val season: EspnSeasonRef? = null)

@Serializable
data class EspnSummaryResponse(
    val header: EspnHeader? = null,
    val keyEvents: List<EspnKeyEvent> = emptyList(),
    val boxscore: EspnBoxscore? = null,
    val rosters: List<EspnRoster> = emptyList(),
    val gameInfo: EspnGameInfo? = null,
)
