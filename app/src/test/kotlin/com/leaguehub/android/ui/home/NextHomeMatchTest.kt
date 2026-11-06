package com.leaguehub.android.ui.home

import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.SeasonTotals
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextHomeMatchTest {
    private fun clubMatch(id: String, kickoff: Long, home: Boolean, state: MatchState = MatchState.PRE) = ClubMatch(
        id = id, kickoff = kickoff, competitionCode = "ger.1", competition = "Bundesliga", home = home,
        opponent = team("opp$id"), goalsFor = null, goalsAgainst = null, state = state, statusName = "", clock = "",
        outcome = null, detail = null, venue = null, europeanCup = null,
    )

    private fun form(matches: List<ClubMatch>, live: ClubMatch? = null) = TeamForm(
        team = team("bayern"), season = SeasonTotals(0, 0, 0, 0, 0, 0), results = emptyList(),
        live = live, next = matches.firstOrNull { it.state == MatchState.PRE }, seasonMatches = matches,
    )

    @Test
    fun `prochain match à l'extérieur - affiche le match à domicile suivant`() {
        val away = clubMatch("1", 100, home = false)
        val home = clubMatch("2", 200, home = true)
        assertEquals(home, nextHomeMatch(form(listOf(away, home))))
    }

    @Test
    fun `prochain match déjà à domicile - masqué pour éviter le doublon`() {
        val home = clubMatch("1", 100, home = true)
        val later = clubMatch("2", 200, home = true)
        assertNull(nextHomeMatch(form(listOf(home, later))))
    }

    @Test
    fun `match en cours - le prochain à domicile n'est pas un doublon`() {
        val live = clubMatch("1", 50, home = true, state = MatchState.IN)
        val home = clubMatch("2", 200, home = true)
        assertEquals(home, nextHomeMatch(form(listOf(live, home), live = live)))
    }

    @Test
    fun `matchs joués ignorés, aucun match à domicile à venir`() {
        val played = clubMatch("1", 10, home = true, state = MatchState.POST)
        val away = clubMatch("2", 100, home = false)
        assertNull(nextHomeMatch(form(listOf(played, away))))
        assertNull(nextHomeMatch(null))
    }
}
