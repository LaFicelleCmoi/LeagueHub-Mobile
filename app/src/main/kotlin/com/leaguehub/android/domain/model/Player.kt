package com.leaguehub.android.domain.model

import kotlinx.serialization.Serializable

// --- Fiche joueur ---
// @Serializable : la fiche est gardée en cache pour rester consultable hors ligne.

@Serializable
enum class PlayerPosition { GOALKEEPER, DEFENDER, MIDFIELDER, FORWARD }

@Serializable
data class PlayerProfile(
    val id: String,
    val name: String,
    val jersey: String?,
    val position: PlayerPosition?,
    val age: Int?,
    /** Date de naissance en jours depuis l'epoch (fuseau neutre), si ESPN la donne. */
    val birthEpochDay: Long?,
    val nationality: String?,
    val flag: String?,
    val heightCm: Int?,
    val weightKg: Int?,
    val team: Team?,
    /** Code ESPN du championnat du joueur (« fra.1 »), pour ouvrir ses matchs. */
    val leagueCode: String?,
    /** Nom du championnat, en clair. */
    val leagueName: String?,
    val season: PlayerSeason,
    /** Matchs du championnat joués cette saison, du plus récent au plus ancien. */
    val games: List<PlayerGame>,
)

@Serializable
data class PlayerSeason(
    val starts: Int,
    val subIns: Int,
    val goals: Int,
    val assists: Int,
    val shots: Int,
    val shotsOnTarget: Int,
    val yellowCards: Int,
    val redCards: Int,
) {
    val appearances: Int get() = starts + subIns
}

/** Un match du joueur, vu depuis son équipe. */
@Serializable
data class PlayerGame(
    val eventId: String,
    val kickoff: Long,
    val opponent: Team,
    val home: Boolean,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val outcome: MatchOutcome?,
    val goals: Int,
    val assists: Int,
    val yellowCards: Int,
    val redCards: Int,
)
