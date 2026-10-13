package com.leaguehub.android.domain.schedule

import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

/** Portage de `tests/schedule.test.js`. */
class ScheduleTest {
    private fun at(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `getNextMatchDate - le jour strictement suivant, pas un match du soir meme`() {
        val now = at("2026-10-21T14:00+02:00[Europe/Paris]")
        val schedule = listOf(
            match("tonight", "A", "B", at("2026-10-21T21:00+02:00[Europe/Paris]")),
            match("tomorrow1", "C", "D", at("2026-10-22T18:45+02:00[Europe/Paris]")),
            match("tomorrow2", "E", "F", at("2026-10-22T21:00+02:00[Europe/Paris]")),
            match("later", "G", "H", at("2026-11-04T21:00+01:00[Europe/Paris]")),
        )
        val next = getNextMatchDate(schedule, now)!!
        assertEquals(LocalDate.of(2026, 10, 22), next.date)
        assertEquals(listOf("tomorrow1", "tomorrow2"), next.matches.map { it.id })
    }

    @Test
    fun `getNextMatchDate - plus rien a venir`() {
        assertNull(getNextMatchDate(listOf(match("old", "A", "B", 0)), at("2026-10-21T14:00+02:00[Europe/Paris]")))
    }

    @Test
    fun `getNextKickoff - un instant, y compris plus tard dans la journee`() {
        val now = at("2026-10-21T14:00+02:00[Europe/Paris]")
        val schedule = listOf(
            match("live", "A", "B", now - 3_600_000, MatchState.IN),
            match("tonight", "C", "D", at("2026-10-21T21:00+02:00[Europe/Paris]")),
            match("early", "E", "F", at("2026-10-21T18:45+02:00[Europe/Paris]")),
        )
        val next = getNextKickoff(schedule, now)!!
        assertEquals("early", next.match.id)
        assertEquals(listOf("early", "tonight"), next.sameDay.map { it.id })
    }

    @Test
    fun `getNextKickoff - tolerance apres l heure officielle`() {
        val kickoff = at("2026-10-21T21:00+02:00[Europe/Paris]")
        val schedule = listOf(match("m", "A", "B", kickoff))
        assertNull(getNextKickoff(schedule, kickoff + 60_000))
        assertEquals("m", getNextKickoff(schedule, kickoff + 60_000, graceMs = 120_000)?.match?.id)
    }

    @Test
    fun `countdownParts - minutes arrondies au-dessus`() {
        assertEquals(CountdownParts(0, 0, 1, 1), countdownParts(30_000))
        assertEquals(CountdownParts(0, 0, 0, 0), countdownParts(0))
        assertEquals(CountdownParts(0, 0, 0, 0), countdownParts(-5))
        assertEquals(CountdownParts(1, 2, 3, 1440 + 123), countdownParts(((1440 + 123) * 60_000L)))
    }

    @Test
    fun `urgence - ambre a 24 h, rouge dans la derniere heure`() {
        assertEquals(CountdownUrgency.NORMAL, countdownUrgency(25 * 3_600_000L))
        assertEquals(CountdownUrgency.SOON, countdownUrgency(23 * 3_600_000L))
        assertEquals(CountdownUrgency.IMMINENT, countdownUrgency(59 * 60_000L))
    }
}
