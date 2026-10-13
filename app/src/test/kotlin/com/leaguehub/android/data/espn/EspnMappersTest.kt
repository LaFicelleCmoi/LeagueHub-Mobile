package com.leaguehub.android.data.espn

import com.leaguehub.android.domain.model.MatchEventKind
import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side
import com.leaguehub.android.domain.model.ZoneTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EspnMappersTest {

    private val scoreboard = """
    {
      "events": [
        {
          "id": "401", "date": "2026-10-18T19:00Z", "name": "OM at PSG",
          "season": { "slug": "regular-season" },
          "competitions": [{
            "id": "401", "date": "2026-10-18T19:00Z",
            "status": { "displayClock": "67'", "type": { "name": "STATUS_SECOND_HALF", "state": "in", "completed": false, "shortDetail": "67'" } },
            "venue": { "fullName": "Parc des Princes" },
            "competitors": [
              { "id": "160", "homeAway": "home", "score": "2", "team": { "id": "160", "displayName": "Paris Saint-Germain", "abbreviation": "PSG", "logo": "https://x/psg.png", "color": "011F68" },
                "records": [{ "type": "total", "summary": "6-1-1" }] },
              { "id": "176", "homeAway": "away", "score": "1", "team": { "id": "176", "displayName": "Marseille", "abbreviation": "MAR" },
                "records": [{ "type": "total", "summary": "5-2-1" }] }
            ],
            "details": [
              { "clock": { "displayValue": "12'" }, "team": { "id": "160" }, "scoringPlay": true, "athletesInvolved": [{ "id": "1", "displayName": "Ousmane Dembélé", "shortName": "O. Dembélé" }] },
              { "clock": { "displayValue": "30'" }, "team": { "id": "176" }, "scoringPlay": true, "penaltyKick": true, "athletesInvolved": [{ "id": "2", "displayName": "M. Greenwood" }] },
              { "clock": { "displayValue": "50'" }, "team": { "id": "176" }, "redCard": true, "athletesInvolved": [{ "id": "3", "displayName": "Joueur" }] },
              { "clock": { "displayValue": "55'" }, "team": { "id": "160" }, "scoringPlay": true, "athletesInvolved": [] }
            ]
          }]
        },
        { "id": "broken", "competitions": "pas une liste" },
        { "id": "402", "date": "2026-10-25T15:00Z",
          "competitions": [{ "status": { "type": { "name": "STATUS_SCHEDULED", "state": "pre" } },
            "competitors": [
              { "homeAway": "home", "team": { "id": "1", "displayName": "Lens" } },
              { "homeAway": "away", "team": { "id": "2", "displayName": "Lille" } } ] }] }
      ]
    }
    """.trimIndent()

    @Test
    fun `scoreboard - match en direct normalise, evenement malforme filtre`() {
        val response = EspnJson.decodeFromString(EspnScoreboardResponse.serializer(), scoreboard)
        val matches = response.toMatches("fra.1")
        assertEquals(listOf("401", "402"), matches.map { it.id })

        val live = matches[0]
        assertEquals(MatchState.IN, live.state)
        assertEquals("67'", live.clock)
        assertEquals(2, live.home.score)
        assertEquals(1, live.away.score)
        assertEquals(8, live.home.played)
        assertEquals("011F68", live.home.team.color)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/176.png", live.away.team.logo)
        assertEquals(listOf(MatchEventKind.GOAL, MatchEventKind.PENALTY, MatchEventKind.RED_CARD), live.events.map { it.kind })
        assertEquals(Side.AWAY, live.events[1].side)
        assertEquals("O. Dembélé", live.events[0].player)

        val upcoming = matches[1]
        assertEquals(MatchState.PRE, upcoming.state)
        assertNull(upcoming.home.score)
    }

    @Test
    fun `dates ESPN sans secondes`() {
        val expected = java.time.Instant.parse("2026-10-18T19:00:00Z").toEpochMilli()
        assertEquals(expected, parseEspnDate("2026-10-18T19:00Z"))
        assertEquals(expected, parseEspnDate("2026-10-18T19:00:00Z"))
        assertEquals(expected, parseEspnDate("2026-10-18T21:00+02:00"))
        assertNull(parseEspnDate("n'importe quoi"))
    }

    @Test
    fun `classement - stats et zones`() {
        val json = """
        { "children": [{ "standings": { "seasonDisplayName": "2026-27 Ligue 1", "entries": [
          { "team": { "id": "160", "displayName": "Paris Saint-Germain" }, "note": { "description": "Champions League" },
            "stats": [ { "name": "rank", "value": 1 }, { "name": "points", "value": 20 }, { "name": "gamesPlayed", "value": 8 },
                       { "name": "pointDifferential", "displayValue": "+12" }, { "name": "ties", "value": 2 } ] },
          { "team": { "id": "9", "displayName": "Relegué" }, "note": { "description": "Relegation" },
            "stats": [ { "name": "rank", "value": 18 }, { "name": "points", "value": 3 } ] }
        ]}}]}
        """.trimIndent()
        val standings = EspnJson.decodeFromString(EspnStandingsResponse.serializer(), json).toStandings()
        assertEquals("2026-27", standings.season)
        assertEquals(20, standings.rows[0].points)
        assertEquals(12, standings.rows[0].goalDiff)
        assertEquals(ZoneTone.UCL, standings.rows[0].zone?.tone)
        assertEquals(ZoneTone.RELEGATION, standings.rows[1].zone?.tone)
    }

    @Test
    fun `zones - qualif et barrages`() {
        assertEquals(true, toZone("Champions League Qualifying")?.qualifying)
        assertEquals(ZoneTone.PLAYOFF, toZone("Relegation Playoff")?.tone)
        assertEquals(ZoneTone.UECL, toZone("Conference League")?.tone)
        assertNull(toZone(null))
    }

    @Test
    fun `calendrier d un club - score objet, amicaux ignores, tirs au but`() {
        val json = """
        { "team": { "id": "160", "displayName": "PSG" }, "events": [
          { "id": "1", "date": "2026-09-01T19:00Z", "league": { "slug": "fra.coupe_de_france" },
            "competitions": [{ "status": { "type": { "name": "STATUS_FINAL_PEN", "state": "post", "completed": true } },
              "competitors": [
                { "homeAway": "home", "score": { "value": 1, "displayValue": "1" }, "shootoutScore": 4, "winner": true, "team": { "id": "160", "displayName": "PSG" } },
                { "homeAway": "away", "score": { "value": 1, "displayValue": "1" }, "shootoutScore": 3, "team": { "id": "5", "displayName": "Nantes" } } ] }] },
          { "id": "2", "date": "2026-07-20T19:00Z", "league": { "slug": "club.friendly" },
            "competitions": [{ "status": { "type": { "state": "post" } }, "competitors": [
                { "homeAway": "home", "team": { "id": "160" } }, { "homeAway": "away", "team": { "id": "6" } } ] }] }
        ]}
        """.trimIndent()
        val matches = EspnJson.decodeFromString(EspnScheduleResponse.serializer(), json).toClubMatches("160")
        assertEquals(1, matches.size)
        assertEquals(MatchOutcome.WIN, matches[0].outcome)
        assertEquals("4-3", matches[0].detail)
        assertEquals("Coupe de France", matches[0].competition)
    }

    @Test
    fun `buteurs - ex aequo au meme rang`() {
        val json = """
        { "stats": [ { "name": "goalsLeaders", "leaders": [
          { "value": 9, "athlete": { "id": "1", "displayName": "A" } },
          { "value": 9, "athlete": { "id": "2", "displayName": "B" } },
          { "value": 7, "athlete": { "id": "3", "displayName": "C" } },
          { "value": 0, "athlete": { "id": "4", "displayName": "D" } } ] } ] }
        """.trimIndent()
        val leaders = EspnJson.decodeFromString(EspnStatisticsResponse.serializer(), json).toLeaders()
        assertEquals(listOf(1, 1, 3), leaders.goals.map { it.rank })
        assertEquals(0, leaders.assists.size)
    }
}
