package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState

// Évolution au classement, journée par journée, reconstruite à partir des résultats de la saison.

/** Positions de chaque équipe : `positions[teamId][k - 1]` = rang après sa k-ième journée. */
data class PositionHistory(
    val matchdays: Int,
    val positions: Map<String, List<Int>>,
)

private data class Totals(val points: Int = 0, val goalDiff: Int = 0, val goalsFor: Int = 0) {
    operator fun plus(r: Totals) = Totals(points + r.points, goalDiff + r.goalDiff, goalsFor + r.goalsFor)
}

/**
 * Journée k = les k premiers matchs de chaque équipe (un match reporté est rattrapé dans l'ordre
 * de l'équipe, comme sur les sites de stats). Départage : points, différence de buts, buts marqués ;
 * les confrontations directes, propres à chaque championnat, ne sont pas reconstituées.
 *
 * @param teamIds les équipes du championnat (les autres, ex. un match de coupe, sont ignorées).
 */
fun positionHistory(matches: List<Match>, teamIds: List<String>): PositionHistory {
    val known = teamIds.toSet()
    val results = teamIds.associateWith { mutableListOf<Totals>() }
    matches
        .filter { it.state == MatchState.POST && it.home.score != null && it.away.score != null }
        .filter { it.home.team.id in known && it.away.team.id in known }
        .distinctBy { it.id }
        .sortedBy { it.kickoff }
        .forEach { m ->
            val hs = m.home.score!!
            val aws = m.away.score!!
            results.getValue(m.home.team.id) += Totals(points(hs, aws), hs - aws, hs)
            results.getValue(m.away.team.id) += Totals(points(aws, hs), aws - hs, aws)
        }

    val matchdays = results.values.maxOfOrNull { it.size } ?: 0
    // Cumuls après chaque match, par équipe.
    val cumulative = results.mapValues { (_, list) -> list.runningFold(Totals()) { acc, r -> acc + r } }
    val positions = teamIds.associateWith { ArrayList<Int>(matchdays) }
    for (k in 1..matchdays) {
        // Une équipe en retard (match reporté) garde son dernier total.
        val ranked = teamIds.sortedWith(
            compareByDescending<String> { at(cumulative, it, k).points }
                .thenByDescending { at(cumulative, it, k).goalDiff }
                .thenByDescending { at(cumulative, it, k).goalsFor }
                .thenBy { it },
        )
        ranked.forEachIndexed { i, id -> positions.getValue(id) += i + 1 }
    }
    return PositionHistory(matchdays, positions)
}

private fun at(cumulative: Map<String, List<Totals>>, id: String, k: Int): Totals {
    val list = cumulative.getValue(id)
    return list[minOf(k, list.lastIndex)]
}

private fun points(gf: Int, ga: Int) = if (gf > ga) 3 else if (gf == ga) 1 else 0
