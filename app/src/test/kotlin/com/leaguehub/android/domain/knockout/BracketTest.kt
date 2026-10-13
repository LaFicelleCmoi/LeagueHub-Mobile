package com.leaguehub.android.domain.knockout

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BracketTest {

    @Test
    fun `classement des tours depuis le slug ESPN`() {
        assertEquals(Round.LEAGUE, roundOf("league-phase"))
        assertEquals(Round.PLAYOFF, roundOf("knockout-round-playoffs"))
        assertEquals(Round.R16, roundOf("round-of-16"))
        assertEquals(Round.QUALIFYING, roundOf("third-qualifying-round"))
        assertEquals(Round.LEAGUE, roundOf(null, kickoffMonth = 10))
        assertEquals(Round.PLAYOFF, roundOf(null, kickoffMonth = 3))
    }

    @Test
    fun `confrontation aller-retour - score cumule`() {
        val legs = listOf(
            match("1", "A", "B", 1, MatchState.POST, 2, 1),
            match("2", "B", "A", 2, MatchState.POST, 1, 1),
        )
        val tie = aggregateTie(legs)
        assertEquals(3, tie.aggA)
        assertEquals(2, tie.aggB)
        assertEquals(TieWinner.A, tie.winner)
        assertEquals(true, tie.twoLegs)
    }

    @Test
    fun `egalite cumulee - tirs au but`() {
        val second = match("2", "B", "A", 2, MatchState.POST, 1, 0)
        val legs = listOf(
            match("1", "A", "B", 1, MatchState.POST, 1, 0),
            second.copy(home = second.home.copy(shootout = 5), away = second.away.copy(shootout = 4)),
        )
        assertEquals(TieWinner.B, aggregateTie(legs).winner)
    }

    @Test
    fun `match retour a venir - pas encore de vainqueur`() {
        val legs = listOf(match("1", "A", "B", 1, MatchState.POST, 3, 0), match("2", "B", "A", 2))
        val tie = aggregateTie(legs)
        assertNull(tie.winner)
        assertEquals(false, tie.decided)
    }

    @Test
    fun `bracket - tours ordonnes et champion`() {
        val matches = listOf(
            match("f", "A", "C", 10, MatchState.POST, 1, 0).copy(roundSlug = "final"),
            match("s1", "A", "B", 5, MatchState.POST, 2, 0).copy(roundSlug = "semifinals"),
            match("s2", "C", "D", 5, MatchState.POST, 1, 0).copy(roundSlug = "semifinals"),
        )
        val rounds = buildBracket(matches) { roundOf(it.roundSlug) }
        assertEquals(listOf(Round.SF, Round.FINAL), rounds.map { it.round })
        assertEquals("A", findChampion(rounds)?.id)
    }
}
