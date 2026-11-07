package com.leaguehub.android.data.espn

import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.PlayerPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlayerMappersTest {

    private val bio = EspnJson.decodeFromString(
        EspnAthleteResponse.serializer(),
        """
        {
          "athlete": {
            "id": "271137", "displayName": "Lassine Sinayoko", "jersey": "9",
            "position": { "abbreviation": "F" }, "age": 26, "displayDOB": "8/12/1999",
            "citizenship": "Mali", "flag": { "href": "https://x/mli.png" },
            "displayHeight": "6' 1\"", "displayWeight": "179 lbs",
            "team": { "id": "6851", "displayName": "Paris FC", "abbreviation": "PAR" },
            "statsSummary": { "statistics": [
              { "name": "starts-subIns", "displayValue": "9 (1)" },
              { "name": "totalGoals", "value": 8.0, "displayValue": "8" },
              { "name": "goalAssists", "value": 3.0, "displayValue": "3" }
            ] },
            "inconnu": { "ignoré": true }
          },
          "league": { "slug": "fra.1", "name": "French Ligue 1" }
        }
        """,
    )

    private val gamelog = EspnJson.decodeFromString(
        EspnGamelogResponse.serializer(),
        """
        {
          "names": ["totalGoals", "goalAssists", "totalShots", "shotsOnTarget", "yellowCards", "redCards"],
          "events": {
            "1": { "id": "1", "gameDate": "2026-11-01T16:15:00.000+00:00", "atVs": "vs", "homeTeamId": "6851",
                   "homeTeamScore": "3", "awayTeamScore": "1", "gameResult": "W",
                   "team": { "id": "6851" }, "opponent": { "id": "174", "displayName": "AS Monaco", "logo": "https://x/174.png" } },
            "2": { "id": "2", "gameDate": "2026-10-25T19:00:00.000+00:00", "atVs": "@", "homeTeamId": "160",
                   "homeTeamScore": "2", "awayTeamScore": "0", "gameResult": "L",
                   "team": { "id": "6851" }, "opponent": { "id": "160", "displayName": "Paris Saint-Germain" } },
            "3": { "id": "3", "gameDate": "pas une date", "opponent": { "id": "1" } }
          },
          "seasonTypes": [{ "categories": [{ "events": [
            { "eventId": "1", "stats": ["2", "1", "4", "3", "0", "0"] },
            { "eventId": "2", "stats": ["0", "0", "1", "0", "1", "0"] }
          ] }] }]
        }
        """,
    )

    @Test
    fun `la bio est convertie en unités et en positions françaises`() {
        val p = toPlayerProfile(bio, gamelog)
        assertEquals("Lassine Sinayoko", p.name)
        assertEquals(PlayerPosition.FORWARD, p.position)
        assertEquals(185, p.heightCm)
        assertEquals(81, p.weightKg)
        assertEquals(LocalDate.of(1999, 12, 8).toEpochDay(), p.birthEpochDay)
        assertEquals("6851", p.team?.id)
        assertEquals("fra.1", p.leagueCode)
    }

    @Test
    fun `les totaux officiels priment, les cartons viennent des matchs`() {
        val s = toPlayerProfile(bio, gamelog).season
        assertEquals(9, s.starts)
        assertEquals(1, s.subIns)
        assertEquals(10, s.appearances)
        assertEquals(8, s.goals) // officiel, pas la somme des matchs (2)
        assertEquals(3, s.assists)
        assertEquals(5, s.shots) // pas de total officiel : somme des matchs
        assertEquals(3, s.shotsOnTarget)
        assertEquals(1, s.yellowCards)
        assertEquals(0, s.redCards)
    }

    @Test
    fun `les matchs sont vus depuis l'équipe du joueur, du plus récent au plus ancien`() {
        val games = toPlayerProfile(bio, gamelog).games
        assertEquals(listOf("1", "2"), games.map { it.eventId }) // le match à la date illisible est écarté
        val home = games[0]
        assertTrue(home.home)
        assertEquals(3, home.goalsFor)
        assertEquals(1, home.goalsAgainst)
        assertEquals(MatchOutcome.WIN, home.outcome)
        assertEquals(2, home.goals)
        val away = games[1]
        assertEquals(false, away.home)
        assertEquals(0, away.goalsFor)
        assertEquals(2, away.goalsAgainst)
        assertEquals(1, away.yellowCards)
    }

    @Test
    fun `sans historique, la fiche reste utilisable`() {
        val p = toPlayerProfile(bio, gamelog = null)
        assertTrue(p.games.isEmpty())
        assertEquals(8, p.season.goals)
        assertEquals(0, p.season.yellowCards)
    }

    @Test
    fun `mesures et dates illisibles donnent null`() {
        assertEquals(185, parseHeightCm("1.85 m"))
        assertEquals(178, parseHeightCm("178 cm"))
        assertNull(parseHeightCm("--"))
        assertEquals(75, parseWeightKg("75 kg"))
        assertNull(parseWeightKg(null))
        assertNull(parseBirthDate("31/2/1999"))
        assertNull(parseBirthDate("1999-02-09"))
        assertNull(startsAndSubs("--"))
        assertNull(positionOf("?"))
    }
}
