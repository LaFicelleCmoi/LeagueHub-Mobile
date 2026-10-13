package com.leaguehub.android.domain.standings

import com.leaguehub.android.data.ldc.Coefficients2026
import com.leaguehub.android.domain.model.MatchState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portage de `tests/standings-engine.test.js` (Tracker LDC 2026-2027). */
class RankLeaguePhaseTest {

    private var seq = 0L

    /** m("A", 2, "X", 0) : A reçoit X et gagne 2-0. */
    private fun m(home: String, hg: Int, away: String, ag: Int, state: MatchState = MatchState.POST): PhaseMatch {
        seq++
        return PhaseMatch("m$seq", state, seq, PhaseSide(home, home, hg), PhaseSide(away, away, ag))
    }

    private fun LeaguePhaseTable.r(id: String) = row(id)!!
    private fun LeaguePhaseTable.above(a: String, b: String) = r(a).pos < r(b).pos

    @Test
    fun `points - victoire 3, nul 1, defaite 0`() {
        val t = rankLeaguePhase(listOf(m("A", 2, "B", 0), m("C", 1, "D", 1)))
        assertEquals(3, t.r("A").points)
        assertEquals(0, t.r("B").points)
        assertEquals(1, t.r("C").points)
        assertEquals(1, t.r("D").points)
    }

    @Test
    fun `statistiques - buts et victoires a l exterieur attribues au visiteur`() {
        val t = rankLeaguePhase(listOf(m("X", 1, "A", 3)))
        val a = t.r("A")
        assertEquals(listOf(3, 1, 2, 3, 1, 1), listOf(a.goalsFor, a.goalsAgainst, a.goalDiff, a.awayGoalsFor, a.wins, a.awayWins))
        val x = t.r("X")
        assertEquals(listOf(0, 0), listOf(x.awayGoalsFor, x.awayWins))
    }

    @Test
    fun `cascade 1 - difference de buts`() {
        val t = rankLeaguePhase(listOf(m("A", 2, "X", 0), m("B", 1, "Y", 0)))
        assertTrue(t.above("A", "B"))
        assertEquals("gd", t.r("B").decidedBy)
    }

    @Test
    fun `cascade 2 - buts marques`() {
        val t = rankLeaguePhase(listOf(m("A", 3, "X", 1), m("B", 2, "Y", 0)))
        assertTrue(t.above("A", "B"))
        assertEquals("gf", t.r("B").decidedBy)
    }

    @Test
    fun `cascade 3 - buts marques a l exterieur`() {
        val t = rankLeaguePhase(listOf(m("X", 1, "A", 2), m("B", 2, "Y", 1)))
        assertTrue(t.above("A", "B"))
        assertEquals("awayGf", t.r("B").decidedBy)
    }

    @Test
    fun `cascade 4 - victoires`() {
        val t = rankLeaguePhase(
            listOf(m("A", 1, "X", 0), m("A", 0, "Y", 1), m("B", 0, "Z", 0), m("B", 0, "W", 0), m("B", 1, "V", 1)),
        )
        assertEquals(t.r("A").points, t.r("B").points)
        assertTrue(t.above("A", "B"))
        assertEquals("w", t.r("B").decidedBy)
    }

    @Test
    fun `cascade 5 - victoires a l exterieur`() {
        val t = rankLeaguePhase(listOf(m("X", 0, "A", 1), m("A", 1, "Y", 2), m("B", 1, "Z", 0), m("W", 2, "B", 1)))
        val a = t.r("A")
        val b = t.r("B")
        assertEquals(
            listOf(a.points, a.goalDiff, a.goalsFor, a.awayGoalsFor, a.wins),
            listOf(b.points, b.goalDiff, b.goalsFor, b.awayGoalsFor, b.wins),
        )
        assertTrue(t.above("A", "B"))
        assertEquals("awayW", b.decidedBy)
    }

    @Test
    fun `phase en cours, toujours a egalite - rang partage et ordre alphabetique`() {
        val t = rankLeaguePhase(listOf(m("Zeta", 1, "X", 0), m("Alpha", 1, "Y", 0)))
        assertTrue(t.above("Alpha", "Zeta"))
        assertEquals("alphabetical", t.r("Zeta").decidedBy)
        assertEquals(t.r("Alpha").rank, t.r("Zeta").rank)
        assertFalse(t.r("Zeta").unresolved)
    }

    @Test
    fun `phase en cours - les criteres 6 a 10 ne sont pas appliques`() {
        val t = rankLeaguePhase(listOf(m("A", 1, "X", 0), m("B", 1, "Y", 0), m("X", 5, "Z", 0)))
        assertFalse(t.complete)
        assertEquals(listOf("pts", "gd", "gf", "awayGf", "w", "awayW"), t.criteria)
        assertEquals("alphabetical", t.r("B").decidedBy)
    }

    private fun h2hFixture() = listOf(m("A", 1, "B", 0), m("X", 1, "A", 0), m("B", 4, "Y", 0))

    @Test
    fun `regles UEFA - le match direct ne departage pas`() {
        val t = rankLeaguePhase(h2hFixture())
        assertEquals(t.r("A").points, t.r("B").points)
        assertTrue(t.above("B", "A"))
        assertEquals("gd", t.r("A").decidedBy)
        assertFalse("h2h" in t.criteria)
    }

    @Test
    fun `option headToHead hors reglement - A passe devant`() {
        val t = rankLeaguePhase(h2hFixture(), RankOptions(headToHead = true))
        assertTrue(t.above("A", "B"))
        assertEquals("h2h", t.r("B").decidedBy)
    }

    @Test
    fun `option headToHead sans rencontre - critere sans objet`() {
        val t = rankLeaguePhase(listOf(m("A", 2, "X", 0), m("B", 1, "Y", 0)), RankOptions(headToHead = true))
        assertEquals("gd", t.r("B").decidedBy)
    }

    @Test
    fun `option headToHead a trois - reapplique au sous-groupe`() {
        val t = rankLeaguePhase(
            listOf(m("A", 1, "B", 0), m("X", 1, "A", 0), m("B", 2, "Y", 0), m("C", 3, "Z", 0)),
            RankOptions(headToHead = true),
        )
        assertTrue(t.above("A", "B"))
    }

    @Test
    fun `6 - points cumules des adversaires`() {
        val t = rankLeaguePhase(listOf(m("A", 1, "X", 0), m("B", 1, "Y", 0), m("X", 2, "Z", 0)), RankOptions(complete = true))
        assertEquals(3, t.r("A").oppPoints)
        assertEquals(0, t.r("B").oppPoints)
        assertTrue(t.above("A", "B"))
        assertEquals("oppPts", t.r("B").decidedBy)
    }

    @Test
    fun `7 - difference de buts cumulee des adversaires`() {
        val t = rankLeaguePhase(
            listOf(m("A", 1, "X", 0), m("B", 1, "Y", 0), m("X", 3, "Z", 0), m("Y", 1, "W", 0)),
            RankOptions(complete = true),
        )
        assertEquals(t.r("A").oppPoints, t.r("B").oppPoints)
        assertTrue(t.r("A").oppGoalDiff > t.r("B").oppGoalDiff)
        assertEquals("oppGd", t.r("B").decidedBy)
    }

    @Test
    fun `8 - buts marques cumules des adversaires`() {
        val t = rankLeaguePhase(
            listOf(m("A", 1, "X", 0), m("B", 1, "Y", 0), m("X", 3, "Z", 1), m("Y", 2, "W", 0)),
            RankOptions(complete = true),
        )
        assertTrue(t.r("A").oppGoalsFor > t.r("B").oppGoalsFor)
        assertTrue(t.above("A", "B"))
        assertEquals("oppGf", t.r("B").decidedBy)
    }

    private fun twins() = listOf(m("A", 1, "X", 0), m("B", 1, "Y", 0))
    private val disc33 = mapOf("A" to 3, "B" to 3, "X" to 0, "Y" to 0)

    @Test
    fun `9 - le total disciplinaire le plus bas passe devant`() {
        val t = rankLeaguePhase(twins(), RankOptions(complete = true, disciplinary = mapOf("A" to 5, "B" to 3, "X" to 0, "Y" to 0)))
        assertTrue(t.above("B", "A"))
        assertEquals("disc", t.r("A").decidedBy)
    }

    @Test
    fun `9 - donnee manquante - on s arrete, on ne saute pas au coefficient`() {
        val t = rankLeaguePhase(
            twins(),
            RankOptions(
                complete = true,
                disciplinary = mapOf("A" to 5),
                coefficients = mapOf("A" to ClubCoefficient(99.0), "B" to ClubCoefficient(1.0)),
            ),
        )
        assertTrue(t.r("A").unresolved)
        assertEquals("disc", t.r("A").missing)
        assertEquals("disc", t.r("B").missing)
    }

    @Test
    fun `10 - coefficient club le plus eleve`() {
        val t = rankLeaguePhase(
            twins(),
            RankOptions(complete = true, disciplinary = disc33, coefficients = mapOf("A" to ClubCoefficient(50.0), "B" to ClubCoefficient(60.0))),
        )
        assertTrue(t.above("B", "A"))
        assertEquals("coef", t.r("A").decidedBy)
    }

    @Test
    fun `10 - annexe D8 - la saison la plus recente departage`() {
        val t = rankLeaguePhase(
            twins(),
            RankOptions(
                complete = true,
                disciplinary = disc33,
                coefficientSeasons = listOf("2024/25", "2025/26"),
                coefficients = mapOf(
                    "A" to ClubCoefficient(40.0, 10.0, mapOf("2024/25" to 25.0, "2025/26" to 15.0)),
                    "B" to ClubCoefficient(40.0, 10.0, mapOf("2024/25" to 20.0, "2025/26" to 20.0)),
                ),
            ),
        )
        assertTrue(t.above("B", "A"))
        assertEquals("coef:2025/26", t.r("A").decidedBy)
    }

    @Test
    fun `tous criteres epuises - signale, jamais invente`() {
        val same = ClubCoefficient(40.0, 10.0, mapOf("2025/26" to 20.0))
        val t = rankLeaguePhase(
            twins(),
            RankOptions(complete = true, disciplinary = disc33, coefficientSeasons = listOf("2025/26"), coefficients = mapOf("A" to same, "B" to same)),
        )
        assertTrue(t.r("A").unresolved)
        assertEquals("domestic-position", t.r("A").missing)
    }

    @Test
    fun `zones - 1-8 huitiemes, 9-24 barrages, 25-36 elimines`() {
        assertEquals(
            listOf(PhaseZone.Q, PhaseZone.Q, PhaseZone.PO, PhaseZone.PO, PhaseZone.OUT, PhaseZone.OUT),
            listOf(1, 8, 9, 24, 25, 36).map { zoneOf(it) },
        )
        val seed = (0 until 36).map { SeedClub("T" + it.toString().padStart(2, '0'), "T$it") }
        val zones = toZones(rankLeaguePhase(emptyList(), RankOptions(seed = seed)).rows)
        assertEquals(listOf(8, 16, 12), PhaseZone.entries.map { zones.getValue(it).size })
    }

    @Test
    fun `match en cours compte provisoirement, match a venir ignore`() {
        val t = rankLeaguePhase(listOf(m("A", 1, "B", 0, MatchState.IN), m("C", 5, "D", 0, MatchState.PRE)))
        assertEquals(3, t.r("A").points)
        assertTrue(t.r("A").live)
        assertNull(t.row("C"))
    }

    @Test
    fun `donnees reelles 2024-25 - ordre final officiel reproduit a l identique`() {
        val text = javaClass.classLoader!!.getResource("league-phase-2024-25.json")!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val matches = root.getValue("matches").jsonArray.map { e ->
            val o = e.jsonObject
            fun side(key: String) = o.getValue(key).jsonObject.let {
                PhaseSide(it.getValue("teamId").jsonPrimitive.content, it.getValue("name").jsonPrimitive.content, it.getValue("score").jsonPrimitive.int)
            }
            PhaseMatch(
                o.getValue("id").jsonPrimitive.content,
                MatchState.valueOf(o.getValue("state").jsonPrimitive.content.uppercase()),
                o.getValue("time").jsonPrimitive.long,
                side("home"),
                side("away"),
            )
        }
        val official = root.getValue("officialOrder").jsonArray.map { it.jsonObject.getValue("teamId").jsonPrimitive.content }

        val t = rankLeaguePhase(matches)
        assertEquals(144, matches.size)
        assertTrue("les 144 matchs sont joués : liste complète des critères", t.complete)
        assertEquals(official, t.rows.map { it.teamId })
        val real = t.rows.first { it.name == "Real Madrid" }
        val bayern = t.rows.first { "Bayern" in it.name }
        assertEquals(11, real.pos)
        assertEquals(12, bayern.pos)
        assertEquals("awayW", bayern.decidedBy)
        assertEquals(0, t.rows.count { it.unresolved })
    }

    @Test
    fun `coefficients 2026-27 - 36 clubs, regle de l annexe D4 respectee`() {
        assertEquals(36, Coefficients2026.CLUBS.size)
        assertEquals(listOf("2021/22", "2022/23", "2023/24", "2024/25", "2025/26"), Coefficients2026.SEASONS)
        Coefficients2026.CLUBS.values.forEach { c ->
            assertEquals(c.name, maxOf(c.sum, c.countryPart), c.coefficient, 0.0)
            assertEquals(c.name, c.sum, c.seasons.values.sumOf { it ?: 0.0 }, 1e-9)
        }
    }
}
