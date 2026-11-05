package com.leaguehub.android.ui.matchdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.domain.live.LIVE_POLL_INTERVAL
import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.live.pollingFlow
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.repository.FootballRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Ce que l'écran Détail du match doit afficher. */
sealed interface MatchDetailUiState {
    data object Loading : MatchDetailUiState
    data object Error : MatchDetailUiState
    data class Content(val detail: MatchDetail) : MatchDetailUiState
}

/**
 * Traduit une relecture en état d'écran : un détail déjà affiché ([last])
 * reste visible si la relecture suivante échoue.
 */
internal fun matchDetailUiState(last: MatchDetail?, result: PollResult<MatchDetail>): MatchDetailUiState = when (result) {
    is PollResult.Success -> MatchDetailUiState.Content(result.value)
    is PollResult.Failure -> last?.let(MatchDetailUiState::Content) ?: MatchDetailUiState.Error
}

@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: FootballRepository,
) : ViewModel() {
    private val competition = savedState.get<String>("competition").orEmpty()
    private val eventId = savedState.get<String>("eventId").orEmpty()
    private val attempt = MutableStateFlow(0)

    /** Dernier détail reçu ; lu et écrit uniquement par la collecte de [state]. */
    private var lastDetail: MatchDetail? = null

    /** Relu toutes les 20 s pendant le match, toutes les 10 min sinon. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MatchDetailUiState> = attempt.flatMapLatest {
        pollingFlow(
            delayFor = { d: MatchDetail, _ -> if (d.match.state == MatchState.IN) LIVE_POLL_INTERVAL else 10 * 60_000L },
            fetch = { repo.matchDetail(competition, eventId) },
        )
    }.map { result ->
        matchDetailUiState(lastDetail, result).also { if (it is MatchDetailUiState.Content) lastDetail = it.detail }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchDetailUiState.Loading)

    fun retry() { attempt.value++ }
}
