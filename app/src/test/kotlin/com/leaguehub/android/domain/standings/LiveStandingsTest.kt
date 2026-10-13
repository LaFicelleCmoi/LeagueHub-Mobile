package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.match
import com.leaguehub.android.standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStandingsTest {

    private val table = Standings(
        "2026-27",
        listOf(standing(1, "PSG", 8, 20, 12, 20), standing(2, "OM", 8, 18, 8, 15), standing(3, "OL", 8, 17, 5, 12)),
    )

    @Test
    fun `aucun match - ordre officiel conserve, pas de provisoire`() {
        val live = computeLiveStandings(table, emptyList())
        assertFalse(live.provisional)
        assertEquals(listOf("PSG", "OM", "OL"), live.rows.map { it.row.team.id })
    }

    @Test
    fun `match en cours - points provisoires et mouvement`() {
        // L'OL mène l'OM : 20 pts contre 18.
        val m = match("1", "OL", "OM", state = MatchState.IN, hs = 2, aws = 0)
        val live = computeLiveStandings(table, listOf(m))
        assertTrue(live.provisional)
        assertEquals(listOf("PSG", "OL", "OM"), live.rows.map { it.row.team.id })
        val ol = live.rows.first { it.row.team.id == "OL" }
        assertEquals(20, ol.row.points)
        assertEquals(9, ol.row.played)
        assertEquals(1, ol.movement)
        assertEquals(-1, live.rows.first { it.row.team.id == "OM" }.movement)
    }

    @Test
    fun `match termine deja compte par le bilan ESPN - pas de double comptage`() {
        // Le bilan V-N-D du match dit 8 matchs : le classement (8 joués) l'inclut déjà.
        val m = match("1", "OL", "OM", state = MatchState.POST, hs = 2, aws = 0, homePlayed = 8, awayPlayed = 8)
        val live = computeLiveStandings(table, listOf(m))
        assertFalse(live.provisional)
        assertNull(live.rows.first { it.row.team.id == "OL" }.live)
    }

    @Test
    fun `match termine que le classement n a pas encore integre`() {
        val m = match("1", "OL", "OM", state = MatchState.POST, hs = 2, aws = 0, homePlayed = 9, awayPlayed = 9)
        val live = computeLiveStandings(table, listOf(m))
        assertTrue(live.provisional)
        assertEquals(20, live.rows.first { it.row.team.id == "OL" }.row.points)
    }

    @Test
    fun `match vu en cours - compte jusqu a ce que le classement ait plus de matchs`() {
        val m = match("1", "OL", "OM", state = MatchState.POST, hs = 1, aws = 1)
        val pre = mapOf(preMatchKey("1", "OL") to 8, preMatchKey("1", "OM") to 8)
        assertTrue(computeLiveStandings(table, listOf(m), pre).provisional)

        val updated = Standings("2026-27", table.rows.map { it.copy(played = 9) })
        assertFalse(computeLiveStandings(updated, listOf(m), pre).provisional)
    }

    @Test
    fun `les zones restent attachees aux places`() {
        val zoned = Standings("x", table.rows.mapIndexed { i, r -> r.copy(zone = if (i == 0) com.leaguehub.android.domain.model.Zone("ucl", com.leaguehub.android.domain.model.ZoneTone.UCL, false) else null) })
        val m = match("1", "OM", "PSG", state = MatchState.IN, hs = 3, aws = 0)
        val live = computeLiveStandings(zoned, listOf(m))
        assertEquals("OM", live.rows[0].row.team.id)
        assertEquals(com.leaguehub.android.domain.model.ZoneTone.UCL, live.rows[0].row.zone?.tone)
        assertNull(live.rows[1].row.zone)
    }
}
