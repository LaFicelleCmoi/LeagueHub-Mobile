package com.leaguehub.android.ui.club

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.live.pollingFlow
import com.leaguehub.android.domain.model.ClubPalmares
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubUiState(
    val form: TeamForm? = null,
    val failed: Boolean = false,
    val palmares: ClubPalmares? = null,
    val isFavorite: Boolean = false,
)

@HiltViewModel
class ClubViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: FootballRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val teamId: String = savedState.get<String>("teamId").orEmpty()
    val league = Competitions.bySlug(savedState.get<String>("league").orEmpty()) ?: Competitions.LIGUE_1

    private val attempt = MutableStateFlow(0)
    private val palmares = MutableStateFlow<ClubPalmares?>(null)

    /** Une fiche déjà affichée reste visible en cas d'erreur ponctuelle. */
    private var lastForm: TeamForm? = null

    /** Pendant un match du club, ou dans le quart d'heure qui précède, la fiche est relue toutes les 30 s. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val form = attempt.flatMapLatest {
        pollingFlow(
            delayFor = { f: TeamForm, now -> if (f.live != null || (f.next != null && f.next.kickoff - now <= 15 * 60_000)) 30_000L else 10 * 60_000L },
            fetch = { repo.teamForm(teamId, league) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ClubUiState> = combine(form, palmares, settings.settings.map { it.favorite?.teamId }) { f, p, fav ->
        val success = (f as? PollResult.Success)?.value
        ClubUiState(
            form = success ?: lastForm,
            failed = f is PollResult.Failure && lastForm == null,
            palmares = p,
            isFavorite = fav == teamId,
        ).also { if (success != null) lastForm = success }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClubUiState())

    init {
        viewModelScope.launch { palmares.value = repo.palmares(league, teamId) }
    }

    fun retry() { attempt.value++ }

    fun toggleFavorite() {
        val team = state.value.form?.team ?: return
        viewModelScope.launch {
            settings.setFavorite(
                if (state.value.isFavorite) null
                else FavoriteClub(team.id, team.name, team.logo, league.slug, team.color),
            )
        }
    }
}
