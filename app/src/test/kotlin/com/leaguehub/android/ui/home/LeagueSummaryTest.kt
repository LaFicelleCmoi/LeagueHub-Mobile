package com.leaguehub.android.ui.home

import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueSummaryTest {
    private val league = Competitions.BUNDESLIGA
    private val day = 24 * 3_600_000L

    private fun row(rank: Int, id: String) = StandingRow(
        rank = rank, rankChange = 0, team = team(id), played = 8, wins = 0, draws = 0, losses = 0,
        goalsFor = 0, goalsAgainst = 0, goalDiff = 0, points = 30 - rank, zone = null,
    )

    private fun match(id: String, kickoff: Long, home: String, away: String, state: MatchState = MatchState.PRE) = Match(
        id = id, competition = "ger.1", kickoff = kickoff, state = state, statusName = "", clock = "", venue = null,
        home = MatchSide(team(home), null, false, null, null), away = MatchSide(team(away), null, false, null, null),
    )

    private val table = Standings("2026", (1..10).map { row(it, "t$it") })

    @Test
    fun `top 3 puis le favori classé plus bas`() {
        val s = leagueSummary(league, table, emptyList(), favoriteTeamId = "t7")!!
        assertEquals(listOf("t1", "t2", "t3", "t7"), s.rows.map { it.team.id })
    }

    @Test
    fun `favori déjà dans le top 3 - pas de doublon`() {
        val s = leagueSummary(league, table, emptyList(), favoriteTeamId = "t2")!!
        assertEquals(listOf("t1", "t2", "t3"), s.rows.map { it.team.id })
    }

    @Test
    fun `prochain jour de match seulement, match du favori en premier`() {
        val base = 1_800_000_000_000L
        val calendar = listOf(
            match("a", base, "t1", "t2"),
            match("b", base + 3_600_000, "t7", "t8"),
            match("c", base + 3 * day, "t3", "t4"),
            match("old", base - day, "t5", "t6", MatchState.POST),
        )
        val s = leagueSummary(league, table, calendar, favoriteTeamId = "t7")!!
        assertEquals(listOf("b", "a"), s.matches.map { it.id })
        assertFalse(s.live)
    }

    @Test
    fun `matchs en cours prioritaires`() {
        val calendar = listOf(match("live", 0, "t1", "t2", MatchState.IN), match("next", day, "t3", "t4"))
        val s = leagueSummary(league, table, calendar, favoriteTeamId = null)!!
        assertEquals(listOf("live"), s.matches.map { it.id })
        assertTrue(s.live)
    }

    @Test
    fun `rien à afficher - carte masquée`() {
        assertNull(leagueSummary(league, null, emptyList(), favoriteTeamId = null))
    }
}
