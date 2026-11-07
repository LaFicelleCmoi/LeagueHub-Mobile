package com.leaguehub.android.domain.calendar

import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarPlanTest {

    private val now = ZonedDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val hour = 60 * 60_000L
    private val bayern = FavoriteClub("132", "Bayern Munich", null, "bundesliga")
    private val psg = FavoriteClub("160", "Paris Saint-Germain", null, "ligue-1")

    private fun match(
        id: String,
        kickoff: Long,
        opponent: String = "Cologne",
        home: Boolean = true,
        state: MatchState = MatchState.PRE,
        confirmed: Boolean = true,
    ) = ClubMatch(
        id = id, kickoff = kickoff, competitionCode = "ger.1", competition = "Bundesliga", home = home,
        opponent = Team(opponent, opponent, opponent, opponent.take(3), null), goalsFor = null, goalsAgainst = null,
        state = state, statusName = "STATUS_SCHEDULED", clock = "", outcome = null, detail = null,
        venue = "Allianz Arena", europeanCup = null, timeConfirmed = confirmed,
    )

    private fun synced(entry: CalendarEntry, eventId: Long, calendarId: Long = 1) =
        SyncedEvent(eventId, calendarId, entry.start, entry.fingerprint)

    @Test
    fun `le titre met l'équipe à domicile en premier`() {
        assertEquals("Bayern Munich – Cologne", calendarEntry(bayern, match("1", now + hour)).title)
        assertEquals("Mainz – Bayern Munich", calendarEntry(bayern, match("1", now + hour, "Mainz", home = false)).title)
    }

    @Test
    fun `heure provisoire = journée entière du jour du match à Paris`() {
        // 30 janvier 23:30 UTC = 31 janvier 00:30 à Paris.
        val kickoff = ZonedDateTime.of(2027, 1, 30, 23, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val e = calendarEntry(bayern, match("1", kickoff, confirmed = false))
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2027, 1, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), e.start)
        assertEquals(e.start + 24 * hour, e.end)
    }

    @Test
    fun `seuls les matchs à venir comptent, une fois même entre deux clubs suivis`() {
        val entries = upcomingEntries(
            listOf(
                bayern to listOf(match("past", now - hour, state = MatchState.POST), match("1", now + hour), match("cl", now + 5 * hour, "PSG")),
                psg to listOf(match("cl", now + 5 * hour, "Bayern Munich", home = false)),
            ),
            now,
        )
        assertEquals(listOf("1", "cl"), entries.map { it.matchId })
    }

    @Test
    fun `nouveau match créé, horaire changé mis à jour, rien d'autre`() {
        val kept = calendarEntry(bayern, match("kept", now + hour))
        val moved = calendarEntry(bayern, match("moved", now + 48 * hour))
        val known = mapOf("kept" to synced(kept, 10), "moved" to synced(moved, 11))
        val movedNow = calendarEntry(bayern, match("moved", now + 50 * hour))
        val fresh = calendarEntry(bayern, match("new", now + 72 * hour))

        val plan = planCalendar(listOf(kept, movedNow, fresh), known, calendarId = 1, now = now, complete = true)

        assertEquals(listOf("new"), plan.insert.map { it.matchId })
        assertEquals(listOf(11L to movedNow), plan.update)
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.forget.isEmpty())
    }

    @Test
    fun `heure confirmée après coup, l'événement journée entière devient un créneau`() {
        val tbd = calendarEntry(bayern, match("1", now + 30 * 24 * hour, confirmed = false))
        val fixed = calendarEntry(bayern, match("1", now + 30 * 24 * hour + 2 * hour))
        val plan = planCalendar(listOf(fixed), mapOf("1" to synced(tbd, 5)), 1, now, complete = true)
        assertEquals(listOf(5L to fixed), plan.update)
    }

    @Test
    fun `match joué oublié sans le supprimer, match disparu supprimé`() {
        val played = calendarEntry(bayern, match("played", now - 3 * hour))
        val postponed = calendarEntry(bayern, match("postponed", now + 24 * hour))
        val known = mapOf("played" to synced(played, 1), "postponed" to synced(postponed, 2))

        val plan = planCalendar(emptyList(), known, 1, now, complete = true)

        assertEquals(listOf("played"), plan.forget)
        assertEquals(listOf(2L), plan.delete)
    }

    @Test
    fun `calendrier d'un club illisible, on ne supprime rien`() {
        val upcoming = calendarEntry(bayern, match("1", now + 24 * hour))
        val plan = planCalendar(emptyList(), mapOf("1" to synced(upcoming, 1)), 1, now, complete = false)
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.forget.isEmpty())
    }

    @Test
    fun `nouvel agenda choisi, les matchs à venir déménagent`() {
        val stays = calendarEntry(bayern, match("1", now + 24 * hour))
        val gone = calendarEntry(bayern, match("2", now + 48 * hour))
        val known = mapOf("1" to synced(stays, 1, calendarId = 7), "2" to synced(gone, 2, calendarId = 7))

        // Lecture incomplète : l'ancien agenda est quand même vidé de ce qu'on y avait mis.
        val plan = planCalendar(listOf(stays), known, calendarId = 9, now = now, complete = false)

        assertEquals(listOf("1"), plan.insert.map { it.matchId })
        assertEquals(setOf(1L, 2L), plan.delete.toSet())
    }

    @Test
    fun `couper la synchro retire seulement les matchs à venir`() {
        val played = calendarEntry(bayern, match("played", now - 3 * hour))
        val upcoming = calendarEntry(bayern, match("next", now + 3 * hour))
        val known = mapOf("played" to synced(played, 1), "next" to synced(upcoming, 2))
        assertEquals(listOf(2L), clearPlan(known, now))
    }
}
