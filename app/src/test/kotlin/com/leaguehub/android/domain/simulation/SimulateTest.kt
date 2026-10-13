package com.leaguehub.android.domain.simulation

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.standings.PhaseMatch
import com.leaguehub.android.domain.standings.PhaseSide
import com.leaguehub.android.domain.standings.RankOptions
import com.leaguehub.android.domain.standings.rankLeaguePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulateTest {
    private fun pm(id: String, home: String, away: String, hs: Int? = null, aws: Int? = null, state: MatchState = MatchState.PRE) =
        PhaseMatch(id, state, id.hashCode().toLong(), PhaseSide(home, home, hs), PhaseSide(away, away, aws))

    private val played = listOf(pm("1", "A", "B", 2, 0, MatchState.POST), pm("2", "C", "D", 1, 0, MatchState.POST))
    private val upcoming = listOf(pm("3", "B", "C"), pm("4", "D", "A"))
    private val all = played + upcoming
    private val rank = { ms: List<PhaseMatch> -> rankLeaguePhase(ms, RankOptions(complete = false)).rows }

    @Test
    fun `le score saisi est injecte comme match termine, sans toucher a l original`() {
        val injected = injectScores(all, listOf(SimScore("3", 3, 1)))
        assertEquals(MatchState.POST, injected.first { it.id == "3" }.state)
        assertEquals(3, injected.first { it.id == "3" }.home.score)
        assertEquals(MatchState.PRE, all.first { it.id == "3" }.state)
    }

    @Test
    fun `classement recalcule par le moteur, deltas et equipes simulees`() {
        val base = rank(all)
        val sim = simulateStandings(base, listOf(SimScore("3", 5, 0)), all, rank)
        val b = sim.first { it.row.teamId == "B" }
        assertTrue(b.simulated)
        assertEquals(base.first { it.teamId == "B" }.pos, b.basePos)
        assertTrue(b.delta > 0)
        assertFalse(sim.first { it.row.teamId == "A" }.simulated)
        // Le classement réel n'est jamais muté.
        assertEquals(0, base.first { it.teamId == "B" }.points)
    }

    @Test
    fun `score negatif ramene a zero`() {
        val injected = injectScores(all, listOf(SimScore("4", -2, 1)))
        assertEquals(0, injected.first { it.id == "4" }.home.score)
    }
}
