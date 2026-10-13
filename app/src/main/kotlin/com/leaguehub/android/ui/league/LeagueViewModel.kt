package com.leaguehub.android.ui.league

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.live.nextPollDelay
import com.leaguehub.android.domain.live.pollingFlow
import com.leaguehub.android.domain.model.Article
import com.leaguehub.android.domain.model.Cached
import com.leaguehub.android.domain.model.Leaders
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.standings.LiveStandings
import com.leaguehub.android.domain.standings.computeLiveStandings
import com.leaguehub.android.domain.standings.preMatchKey
import com.leaguehub.android.ui.common.GoalWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeagueUiState(
    val league: LeagueConfig,
    val standings: Cached<Standings>? = null,
    val live: LiveStandings? = null,
    /** Calendrier en base, scores du direct fusionnés. */
    val matches: List<Match> = emptyList(),
    val favoriteId: String? = null,
    val refreshing: Boolean = false,
    /** Le réseau a échoué : on sert la base (ou rien, si elle est vide). */
    val offline: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface Remote<out T> {
    data object Loading : Remote<Nothing>
    data class Ready<T>(val value: Cached<T>) : Remote<T>
    data object Failed : Remote<Nothing>
}

@HiltViewModel
class LeagueViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: FootballRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val league: LeagueConfig = Competitions.bySlug(savedState.get<String>("slug").orEmpty()) ?: Competitions.LIGUE_1

    private val refreshing = MutableStateFlow(false)
    private val offline = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)

    /** Matchs joués par chaque équipe au moment où on a vu le match commencer (anti double comptage). */
    private val preMatchPlayed = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val calendar = repo.calendar(league).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val standings = repo.standings(league).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val goalWatcher = GoalWatcher()
    val goals: SharedFlow<GoalWatcher.Goal> = goalWatcher.events

    /** Direct : polling pendant les matchs seulement, suspendu dès que l'écran n'est plus visible. */
    private val live: StateFlow<List<Match>> = pollingFlow(
        delayFor = { fetched, now -> nextPollDelay(calendar.value + fetched, now) },
        fetch = { repo.liveMatches(league) },
    ).map { result ->
        when (result) {
            is PollResult.Success -> result.value.also(::onLive)
            is PollResult.Failure -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val favorite = settings.settings.map { it.favorite?.teamId }
        .onEach { goalWatcher.favoriteId = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LeagueUiState> = combine(
        combine(standings, calendar, live, preMatchPlayed) { s, cal, liveMatches, pre -> Quad(s, cal, liveMatches, pre) },
        favorite,
        combine(refreshing, offline, loaded) { r, o, l -> Triple(r, o, l) },
    ) { (s, cal, liveMatches, pre), fav, (r, o, l) ->
        val merged = (cal.associateBy { it.id } + liveMatches.associateBy { it.id }).values.sortedBy { it.kickoff }
        LeagueUiState(
            league = league,
            standings = s,
            live = s?.data?.let { computeLiveStandings(it, liveMatches, pre) },
            matches = merged,
            favoriteId = fav,
            refreshing = r,
            offline = o,
            loaded = l,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeagueUiState(league))

    private val _leaders = MutableStateFlow<Remote<Leaders>>(Remote.Loading)
    val leaders = _leaders.asStateFlow()
    private val _season = MutableStateFlow<Remote<List<Match>>>(Remote.Loading)
    /** Résultats de toute la saison (onglet Saison), chargés à la demande. */
    val season = _season.asStateFlow()
    private val _news = MutableStateFlow<Remote<List<Article>>>(Remote.Loading)
    val news = _news.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val ok = runCatching { repo.refreshStandings(league) }.isSuccess and
                runCatching { repo.refreshCalendar(league) }.isSuccess
            offline.value = !ok
            refreshing.value = false
            loaded.value = true
        }
    }

    fun loadLeaders(force: Boolean = false) {
        if (!force && _leaders.value is Remote.Ready) return
        viewModelScope.launch {
            _leaders.value = Remote.Loading
            _leaders.value = runCatching { repo.leaders(league) }.fold({ Remote.Ready(it) }, { Remote.Failed })
        }
    }

    fun loadSeason(force: Boolean = false) {
        if (!force && _season.value is Remote.Ready) return
        viewModelScope.launch {
            _season.value = Remote.Loading
            _season.value = runCatching { repo.seasonMatches(league) }.fold({ Remote.Ready(it) }, { Remote.Failed })
        }
    }

    fun loadNews(force: Boolean = false) {
        if (!force && _news.value is Remote.Ready) return
        viewModelScope.launch {
            _news.value = Remote.Loading
            _news.value = runCatching { repo.news(league) }.fold({ Remote.Ready(it) }, { Remote.Failed })
        }
    }

    private fun onLive(matches: List<Match>) {
        val rows = standings.value?.data?.rows.orEmpty().associateBy { it.team.id }
        val pre = preMatchPlayed.value.toMutableMap()
        var finished = false
        for (m in matches) {
            if (m.state == MatchState.IN) {
                for (side in listOf(m.home, m.away)) {
                    val key = preMatchKey(m.id, side.team.id)
                    if (key !in pre) rows[side.team.id]?.let { pre[key] = it.played }
                }
            }
            val before = calendar.value.firstOrNull { it.id == m.id }
            if (before?.state == MatchState.IN && m.state == MatchState.POST) finished = true
        }
        preMatchPlayed.value = pre
        goalWatcher.observe(matches)
        // Un match vient de finir : le classement officiel ESPN va l'intégrer.
        if (finished) viewModelScope.launch { runCatching { repo.refreshStandings(league) } }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
