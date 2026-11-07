package com.leaguehub.android.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.PlayerProfile
import com.leaguehub.android.domain.repository.FootballRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran Fiche joueur doit afficher. */
sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data object Error : PlayerUiState
    /** [offlineSince] : ESPN n'a pas répondu, c'est la version gardée à cette date. */
    data class Content(val player: PlayerProfile, val offlineSince: Long? = null) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: FootballRepository,
) : ViewModel() {
    private val athleteId = savedState.get<String>("athleteId").orEmpty()
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repo.player(athleteId) }.fold(
                { c -> PlayerUiState.Content(c.data, offlineSince = c.updatedAt.takeIf { c.fromCache }) },
                { PlayerUiState.Error },
            )
        }
    }
}

/** Slug de la page club à ouvrir pour son équipe ; un club hors des 5 championnats passe par la LDC. */
internal fun clubLeagueSlug(player: PlayerProfile): String =
    player.leagueCode?.let(Competitions::byEspnCode)?.slug ?: Competitions.UCL.slug
