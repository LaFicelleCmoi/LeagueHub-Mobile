package com.leaguehub.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.core.util.parisDay
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.live.needsLiveUpdate
import com.leaguehub.android.domain.live.nextPollDelay
import com.leaguehub.android.domain.live.pollingFlow
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.ui.common.GoalWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class KeyFigures(val matchesToday: Int, val liveNow: Int, val goalsToday: Int)

data class HomeUiState(
    val today: List<Pair<LeagueConfig, List<Match>>> = emptyList(),
    val figures: KeyFigures = KeyFigures(0, 0, 0),
    val favorite: FavoriteClub? = null,
    val favoriteForm: TeamForm? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: FootballRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val goalWatcher = GoalWatcher()
    val goals: SharedFlow<GoalWatcher.Goal> = goalWatcher.events

    /** Calendriers en base des 6 compétitions (scores du direct recopiés par le polling). */
    private val calendars = combine(Competitions.ALL.map { league -> repo.calendar(league).map { league to it } }) { it.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var firstPoll = true

    /** Direct de toutes les compétitions qui ont un match en cours ou imminent ; la base fait le reste. */
    private val live = pollingFlow(
        delayFor = { _: Unit, now -> nextPollDelay(calendars.value.flatMap { it.second }, now) },
        fetch = {
            val now = System.currentTimeMillis()
            val targets = if (firstPoll) Competitions.ALL
            else calendars.value.filter { (_, matches) -> matches.any { needsLiveUpdate(it, now) } }.map { it.first }
            firstPoll = false
            coroutineScope {
                targets.map { async { runCatching { repo.liveMatches(it) }.getOrDefault(emptyList()) } }.awaitAll()
                    .flatten().also(goalWatcher::observe)
            }
            Unit
        },
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val favorite = settings.settings.map { it.favorite }.distinctUntilChanged()
        .onEach { goalWatcher.favoriteId = it?.teamId }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val favoriteForm = favorite.flatMapLatest { fav ->
        if (fav == null) flowOf(null)
        else pollingFlow(
            delayFor = { f: TeamForm, now -> if (f.live != null || (f.next != null && f.next.kickoff - now <= 15 * 60_000)) 30_000L else 10 * 60_000L },
            fetch = { repo.teamForm(fav.teamId, Competitions.bySlug(fav.competition) ?: Competitions.UCL) },
        ).map { (it as? PollResult.Success)?.value }
    }

    val state: StateFlow<HomeUiState> = combine(calendars, favorite, favoriteForm, live) { cals, fav, form, _ ->
        val today = LocalDate.now(com.leaguehub.android.domain.schedule.PARIS)
        val todays = cals.map { (league, matches) -> league to matches.filter { parisDay(it.kickoff) == today } }
            .filter { it.second.isNotEmpty() }
        val all = todays.flatMap { it.second }
        HomeUiState(
            today = todays,
            figures = KeyFigures(
                matchesToday = all.size,
                liveNow = all.count { it.state == MatchState.IN },
                goalsToday = all.sumOf { (it.home.score ?: 0) + (it.away.score ?: 0) },
            ),
            favorite = fav,
            favoriteForm = form?.takeIf { fav != null && it.team.id == fav.teamId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Calendriers vides au premier lancement : on les remplit (le SyncWorker prend ensuite le relais).
        viewModelScope.launch {
            Competitions.LEAGUES.forEach { league -> launch { runCatching { repo.refreshCalendar(league) } } }
        }
    }
}
