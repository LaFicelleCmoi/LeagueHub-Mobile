package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.ZoneTone

// « Qu'est-ce qu'il nous faut ? » : points nécessaires pour le titre, la LDC, l'Europe ou le maintien.

enum class ObjectiveKind { TITLE, UCL, EUROPE, SAFETY }

enum class ObjectiveStatus {
    /** Plus personne ne peut repasser devant. */
    SECURED,
    /** Atteignable par ses seuls résultats : il faut [Objective.pointsNeeded] points. */
    IN_HAND,
    /** Encore possible, mais seulement si des rivaux perdent des points. */
    DEPENDS_ON_OTHERS,
    IMPOSSIBLE,
}

data class Objective(
    val kind: ObjectiveKind,
    /** Place à atteindre ou à conserver (1 pour le titre, 4 pour la LDC…). */
    val position: Int,
    val status: ObjectiveStatus,
    /** Points à prendre pour être sûr (statut IN_HAND), sinon null. */
    val pointsNeeded: Int?,
    /** Points disponibles d'ici la fin (3 × matchs restants). */
    val pointsAvailable: Int,
    /** Écart avec l'équipe qui occupe aujourd'hui la place visée : > 0 = retard, < 0 = avance. */
    val gap: Int,
)

/**
 * Objectifs d'une équipe d'après le classement (en direct si fourni) et les zones ESPN.
 *
 * Calcul prudent : pour « être sûr », on suppose que chaque rival peut encore prendre tous ses points
 * (on ignore qu'ils s'affrontent entre eux), donc le chiffre donné est un maximum, jamais trop bas.
 *
 * @param totalMatches nombre de matchs d'une saison (2 × (équipes − 1) par défaut).
 */
fun objectives(rows: List<StandingRow>, teamId: String, totalMatches: Int = 2 * (rows.size - 1)): List<Objective> {
    val us = rows.firstOrNull { it.team.id == teamId } ?: return emptyList()
    val others = rows.filter { it.team.id != teamId }
    val remaining = { r: StandingRow -> (totalMatches - r.played).coerceAtLeast(0) }
    val available = 3 * remaining(us)
    val ourMax = us.points + available
    val othersMax = others.map { it.points + 3 * remaining(it) }
    val othersByPoints = others.sortedByDescending { it.points }

    return targets(rows).map { (kind, position) ->
        // Rivaux qui finiraient devant (ou à égalité, départage inconnu) si on termine à [final] points.
        fun threats(final: Int) = othersMax.count { it >= final }
        val needed = (0..available).firstOrNull { k -> threats(us.points + k) <= position - 1 }
        val uncatchable = others.count { it.points > ourMax }
        val status = when {
            needed == 0 -> ObjectiveStatus.SECURED
            needed != null -> ObjectiveStatus.IN_HAND
            uncatchable <= position - 1 -> ObjectiveStatus.DEPENDS_ON_OTHERS
            else -> ObjectiveStatus.IMPOSSIBLE
        }
        val holder = othersByPoints.getOrNull(position - 1)
        Objective(
            kind = kind,
            position = position,
            status = status,
            pointsNeeded = needed?.takeIf { it > 0 },
            pointsAvailable = available,
            gap = (holder?.points ?: 0) - us.points,
        )
    }
}

/** Places visées, déduites des zones du classement ; une même place n'apparaît qu'une fois. */
private fun targets(rows: List<StandingRow>): List<Pair<ObjectiveKind, Int>> {
    val n = rows.size
    val europeTones = setOf(ZoneTone.UCL, ZoneTone.UEL, ZoneTone.UECL)
    val ucl = rows.filter { it.zone?.tone == ZoneTone.UCL }.maxOfOrNull { it.rank }
    val europe = rows.filter { it.zone?.tone in europeTones }.maxOfOrNull { it.rank }
    // Barrage et relégation en bas de tableau : le maintien direct est la place juste au-dessus.
    val firstDrop = rows.filter {
        it.zone?.tone == ZoneTone.RELEGATION || (it.zone?.tone == ZoneTone.PLAYOFF && it.rank > n / 2)
    }.minOfOrNull { it.rank }
    val list = mutableListOf(ObjectiveKind.TITLE to 1)
    ucl?.let { list += ObjectiveKind.UCL to it }
    europe?.let { list += ObjectiveKind.EUROPE to it }
    firstDrop?.let { list += ObjectiveKind.SAFETY to it - 1 }
    return list.distinctBy { it.second }
}
