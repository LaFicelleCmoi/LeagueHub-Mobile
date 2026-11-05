package com.leaguehub.android.ui.matchdetail

import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.match
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class MatchDetailUiStateTest {
    private fun detail(id: String) = MatchDetail(
        match = match(id, "A", "B"), timeline = emptyList(), stats = emptyList(),
        lineups = emptyList(), attendance = null, referee = null,
    )

    private val failure = PollResult.Failure(IOException("réseau"), failures = 1)

    @Test
    fun `une lecture réussie affiche le détail reçu`() {
        val fresh = detail("2")
        assertEquals(MatchDetailUiState.Content(fresh), matchDetailUiState(detail("1"), PollResult.Success(fresh)))
        assertEquals(MatchDetailUiState.Content(fresh), matchDetailUiState(null, PollResult.Success(fresh)))
    }

    @Test
    fun `un échec garde le dernier détail affiché`() {
        val last = detail("1")
        assertEquals(MatchDetailUiState.Content(last), matchDetailUiState(last, failure))
    }

    @Test
    fun `un échec sans détail déjà reçu affiche l erreur`() {
        assertEquals(MatchDetailUiState.Error, matchDetailUiState(null, failure))
    }
}
