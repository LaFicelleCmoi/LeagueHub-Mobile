package com.leaguehub.android.domain.model

import kotlinx.serialization.Serializable

// Modèles normalisés utilisés par l'interface (portage de `src/lib/types.ts`).
// Les réponses brutes d'ESPN sont typées dans `data/espn/EspnDto.kt`.
// @Serializable : les modèles sont aussi le format du cache Room (single source of truth).

@Serializable
data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logo: String?,
    /** Couleur principale du club (hex sans #), si ESPN la donne. */
    val color: String? = null,
)

@Serializable
enum class ZoneTone { UCL, UEL, UECL, PLAYOFF, RELEGATION, OTHER }

@Serializable
data class Zone(
    val label: String,
    val tone: ZoneTone,
    /** Place qualificative pour un tour préliminaire. */
    val qualifying: Boolean,
)

@Serializable
data class StandingRow(
    val rank: Int,
    val rankChange: Int,
    val team: Team,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int,
    val zone: Zone?,
)

@Serializable
data class Standings(val season: String, val rows: List<StandingRow>)

@Serializable
enum class MatchState { PRE, IN, POST }

@Serializable
data class MatchSide(
    val team: Team,
    val score: Int?,
    val winner: Boolean,
    /** Matchs de championnat joués selon le bilan ESPN (un match en cours n'y est pas encore compté). */
    val played: Int?,
    /** Tirs au but réussis, en cas de séance. */
    val shootout: Int?,
)

@Serializable
enum class MatchEventKind { GOAL, PENALTY, OWN_GOAL, YELLOW_CARD, RED_CARD }

@Serializable
enum class Side { HOME, AWAY }

@Serializable
data class MatchEvent(
    val minute: String,
    val player: String,
    val kind: MatchEventKind,
    val side: Side,
)

@Serializable
data class Match(
    val id: String,
    /** Code ESPN de la compétition (« esp.1 »), pour charger le détail du match. */
    val competition: String,
    /** Coup d'envoi, en millisecondes epoch. */
    val kickoff: Long,
    val state: MatchState,
    /** Code ESPN du statut (« STATUS_HALFTIME »…), traduit par l'interface. */
    val statusName: String,
    /** Minute de jeu affichée par ESPN (« 67' »), vide hors direct. */
    val clock: String,
    val venue: String?,
    val home: MatchSide,
    val away: MatchSide,
    val events: List<MatchEvent> = emptyList(),
    /** Tour d'une coupe (« league-phase », « round-of-16 »…), slug ESPN. */
    val roundSlug: String? = null,
) {
    fun side(side: Side): MatchSide = if (side == Side.HOME) home else away
    fun involves(teamId: String): Boolean = home.team.id == teamId || away.team.id == teamId
}

// --- Détail d'un match ---

enum class TimelineKind {
    GOAL, PENALTY_GOAL, OWN_GOAL, PENALTY_MISSED, YELLOW_CARD, SECOND_YELLOW, RED_CARD,
    SUBSTITUTION, VAR, PERIOD, OTHER,
}

data class TimelineEvent(
    val id: String,
    val minute: String,
    val kind: TimelineKind,
    /** Libellé ESPN (anglais) de l'action. */
    val label: String,
    val side: Side?,
    val players: List<String>,
)

data class MatchStat(
    val key: String,
    val label: String,
    val home: String,
    val away: String,
    /** Part de l'équipe à domicile (0..1) pour la barre de comparaison. */
    val share: Float?,
)

data class LineupPlayer(
    val id: String,
    val name: String,
    val jersey: String?,
    val position: String?,
    val starter: Boolean,
)

data class Lineup(
    val side: Side,
    val team: Team,
    val formation: String?,
    val players: List<LineupPlayer>,
)

data class MatchDetail(
    val match: Match,
    val timeline: List<TimelineEvent>,
    val stats: List<MatchStat>,
    val lineups: List<Lineup>,
    val attendance: Int?,
    val referee: String?,
)

// --- Buteurs, actualités ---

@Serializable
data class Leader(
    val id: String,
    val rank: Int,
    val player: String,
    val team: Team?,
    val value: Int,
    val appearances: Int?,
)

@Serializable
data class Leaders(val goals: List<Leader>, val assists: List<Leader>)

@Serializable
data class Article(
    val id: String,
    val headline: String,
    val description: String,
    /** Date de publication (ms epoch), si ESPN la donne. */
    val published: Long?,
    val image: String?,
    val url: String?,
)

// --- Club ---

/** Coupes d'Europe : Ligue des champions, Ligue Europa, Ligue Conférence. */
enum class EuropeanCup { UCL, UEL, UECL }

enum class MatchOutcome { WIN, DRAW, LOSS }

/** Un match de la saison d'un club, vu depuis ce club. */
data class ClubMatch(
    val id: String,
    val kickoff: Long,
    val competitionCode: String,
    val competition: String,
    val home: Boolean,
    val opponent: Team,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val state: MatchState,
    val statusName: String,
    val clock: String,
    val outcome: MatchOutcome?,
    /** Précision éventuelle : tirs au but « 4-3 », prolongation. */
    val detail: String?,
    val venue: String?,
    val europeanCup: EuropeanCup?,
)

data class SeasonTotals(
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
)

data class TeamForm(
    val team: Team,
    val season: SeasonTotals,
    /** Les 5 derniers matchs officiels, du plus récent au plus ancien. */
    val results: List<ClubMatch>,
    val live: ClubMatch?,
    val next: ClubMatch?,
    /** Toute la saison, chronologique (déroulé mois par mois). */
    val seasonMatches: List<ClubMatch>,
)

enum class TrophyScope { NATIONAL, EUROPE, MONDE, AUTRE }

@Serializable
data class Trophy(
    val key: String,
    val label: String,
    val count: Int,
    val years: List<String>,
    val scope: String,
) {
    val trophyScope: TrophyScope
        get() = when (scope) {
            "national" -> TrophyScope.NATIONAL
            "europe" -> TrophyScope.EUROPE
            "monde" -> TrophyScope.MONDE
            else -> TrophyScope.AUTRE
        }
}

@Serializable
data class ClubPalmares(
    val qid: String,
    val name: String,
    val total: Int,
    val trophies: List<Trophy>,
)

/** Club favori, mémorisé sur l'appareil. */
@Serializable
data class FavoriteClub(
    val teamId: String,
    val name: String,
    val logo: String?,
    /** Slug de la compétition d'origine (« ligue-1 », « ldc »). */
    val competition: String,
    /** Couleur du club (hex sans #), pour le thème aux couleurs du club. */
    val color: String? = null,
)

/** Résultat d'un appel : la donnée et son origine (réseau frais ou cache hors ligne). */
data class Cached<T>(
    val data: T,
    val updatedAt: Long,
    val fromCache: Boolean,
)
