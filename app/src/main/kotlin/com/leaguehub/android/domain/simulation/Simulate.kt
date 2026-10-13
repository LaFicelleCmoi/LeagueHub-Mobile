package com.leaguehub.android.domain.simulation

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.standings.LeaguePhaseRow
import com.leaguehub.android.domain.standings.PhaseMatch
import com.leaguehub.android.domain.standings.PhaseZone
import com.leaguehub.android.domain.standings.zoneOf

/* =========================================================================
   Simulate.kt — Moteur « What-If » de la phase de ligue (portage de `simulate.js`)
   -------------------------------------------------------------------------
   On saisit le score exact d'un match à venir. Le classement simulé est
   recalculé par le MÊME moteur réglementaire que le classement réel, à partir
   de la liste complète des matchs dans laquelle les scores ont été injectés.

   Pourquoi ne pas ajouter des points ligne par ligne : les critères 6 à 8
   (force du calendrier) dépendent des résultats de TOUTES les équipes.
   ========================================================================= */

data class SimScore(val matchId: String, val homeGoals: Int, val awayGoals: Int)

data class SimulatedRow(
    val row: LeaguePhaseRow,
    /** Rang réel avant simulation. */
    val basePos: Int,
    /** basePos - pos : > 0 = monte, < 0 = descend. */
    val delta: Int,
    /** L'équipe joue au moins un des matchs simulés. */
    val simulated: Boolean,
) {
    /** La simulation fait changer de zone : le saut visuellement notable. */
    val changedZone: Boolean get() = zoneOf(basePos) != zoneOf(row.pos)
    val zone: PhaseZone? get() = zoneOf(row.pos)
}

/** Injecte les scores saisis dans une COPIE de la liste : le match simulé devient « terminé ». */
fun injectScores(matches: List<PhaseMatch>, sims: List<SimScore>): List<PhaseMatch> {
    val byId = sims.associateBy { it.matchId }
    return matches.map { m ->
        val s = byId[m.id] ?: return@map m
        m.copy(
            state = MatchState.POST,
            home = m.home.copy(score = s.homeGoals.coerceAtLeast(0)),
            away = m.away.copy(score = s.awayGoals.coerceAtLeast(0)),
        )
    }
}

/**
 * @param current classement réel (référence des deltas) — jamais modifié
 * @param matches tous les matchs de la phase
 * @param rank moteur réglementaire (matchs -> lignes classées)
 */
fun simulateStandings(
    current: List<LeaguePhaseRow>,
    sims: List<SimScore>,
    matches: List<PhaseMatch>,
    rank: (List<PhaseMatch>) -> List<LeaguePhaseRow>,
): List<SimulatedRow> {
    val basePos = current.associate { it.teamId to it.pos }
    val byId = matches.associateBy { it.id }
    val touched = sims.mapNotNull { byId[it.matchId] }.flatMap { listOf(it.home.teamId, it.away.teamId) }.toSet()
    return rank(injectScores(matches, sims)).map { t ->
        val base = basePos[t.teamId] ?: t.pos
        SimulatedRow(t, base, base - t.pos, t.teamId in touched)
    }
}
