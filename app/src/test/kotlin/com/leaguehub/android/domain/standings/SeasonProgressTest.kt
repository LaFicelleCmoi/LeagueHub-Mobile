package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.match
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonProgressTest {

    private fun played(id: String, home: String, away: String, hs: Int, aws: Int, kickoff: Long) =
        match(id, home, away, kickoff = kickoff, state = MatchState.POST, hs = hs, aws = aws)

    @Test
    fun `positions follow points after each matchday`() {
        val matches = listOf(
            played("1", "A", "B", 2, 0, kickoff = 1), // J1 : A gagne
            played("2", "C", "D", 1, 1, kickoff = 2),
            played("3", "B", "C", 3, 0, kickoff = 3), // J2 : B gagne, A et D font nul
            played("4", "D", "A", 0, 0, kickoff = 4),
        )
        val h = positionHistory(matches, listOf("A", "B", "C", "D"))
        assertEquals(2, h.matchdays)
        assertEquals(listOf(1, 1), h.positions["A"]) // 3 pts puis 4 pts
        assertEquals(listOf(4, 2), h.positions["B"]) // 0 pt (-2) puis 3 pts
        assertEquals(listOf(2, 4), h.positions["C"]) // C et D à 1 pt en J1 : départagés par l'id
        assertEquals(listOf(3, 3), h.positions["D"])
    }

    @Test
    fun `team with a postponed match keeps its last total`() {
        val matches = listOf(
            played("1", "A", "B", 1, 0, kickoff = 1),
            played("2", "A", "C", 1, 0, kickoff = 2), // C n'a joué qu'une fois, B aussi
        )
        val h = positionHistory(matches, listOf("A", "B", "C"))
        assertEquals(2, h.matchdays)
        assertEquals(listOf(1, 1), h.positions["A"])
        assertEquals(2, h.positions.getValue("B").size)
    }

    @Test
    fun `unfinished and foreign matches are ignored`() {
        val matches = listOf(
            match("1", "A", "B", state = MatchState.IN, hs = 5, aws = 0),
            played("2", "A", "Z", 9, 0, kickoff = 1),
        )
        val h = positionHistory(matches, listOf("A", "B"))
        assertEquals(0, h.matchdays)
    }
}
