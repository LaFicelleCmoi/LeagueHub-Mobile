package com.leaguehub.android.domain.live

import app.cash.turbine.test
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.match
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRulesTest {
    private val now = 1_000_000_000L

    @Test
    fun `suivi - en cours, ou dans les 10 minutes avant le coup d envoi`() {
        assertTrue(needsLiveUpdate(match("1", "A", "B", now - 3_600_000, MatchState.IN), now))
        assertTrue(needsLiveUpdate(match("2", "A", "B", now + 5 * 60_000), now))
        assertFalse(needsLiveUpdate(match("3", "A", "B", now + 30 * 60_000), now))
        assertFalse(needsLiveUpdate(match("4", "A", "B", now - 60_000, MatchState.POST), now))
        // Toujours « à venir » après l'heure : ESPN bascule parfois en retard.
        assertTrue(needsLiveUpdate(match("5", "A", "B", now - 2 * 3_600_000), now))
    }

    @Test
    fun `delai - 20 s pendant un match, sinon jusqu a 10 min avant le prochain`() {
        assertEquals(LIVE_POLL_INTERVAL, nextPollDelay(listOf(match("1", "A", "B", now, MatchState.IN)), now))
        assertEquals(20 * 60_000L, nextPollDelay(listOf(match("2", "A", "B", now + 30 * 60_000)), now))
        assertEquals(IDLE_RECHECK, nextPollDelay(listOf(match("3", "A", "B", now + 10 * 3_600_000)), now))
        assertEquals(IDLE_RECHECK, nextPollDelay(emptyList(), now))
    }

    @Test
    fun `backoff - les tentatives s espacent jusqu a 5 minutes`() {
        assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L), (1..6).map(::backoffDelay))
    }

    @Test
    fun `pollingFlow - emet, attend, espace apres erreur puis reprend`() = runTest {
        var calls = 0
        val flow = pollingFlow(clock = { currentTime }, delayFor = { _: Int, _ -> 20_000L }) {
            calls++
            if (calls in 2..3) throw IOException("ESPN indisponible")
            calls
        }
        flow.test {
            assertEquals(PollResult.Success(1), awaitItem())
            assertEquals(0L, currentTime)
            val first = awaitItem() as PollResult.Failure
            assertEquals(1, first.failures)
            assertEquals(20_000L, currentTime)
            assertEquals(2, (awaitItem() as PollResult.Failure).failures)
            assertEquals(40_000L, currentTime)
            assertEquals(PollResult.Success(4), awaitItem())
            assertEquals(80_000L, currentTime) // 20 s + 20 s + 40 s de backoff
            cancelAndIgnoreRemainingEvents()
        }
    }
}
