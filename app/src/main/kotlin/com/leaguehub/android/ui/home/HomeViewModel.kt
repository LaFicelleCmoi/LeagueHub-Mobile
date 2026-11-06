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
import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Standings
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
    /** Clubs suivis (hors favori) et leur fiche ; null tant qu'elle n'est pas chargée. */
    val followed: List<Pair<FavoriteClub, TeamForm?>> = emptyList(),
    /** Prochain match du favori au stade, s'il n'est pas déjà celui de la carte « Mon club ». */
    val nextHome: ClubMatch? = null,
    /** Résumé de la ligue favorite choisie dans les Réglages. */
    val league: LeagueSummary? = null,
)

/** Carte « Ma ligue » : tête du classement et matchs à suivre. */
data class LeagueSummary(
    val league: LeagueConfig,
    /** Les 3 premiers, puis le club favori s'il est plus bas. */
    val rows: List<StandingRow>,
    val favoriteTeamId: String?,
    /** Matchs en cours, sinon ceux du prochain jour de match (3 au plus). */
    val matches: List<Match>,
    val live: Boolean,
)

internal const val LEAGUE_TOP = 3
private const val LEAGUE_MATCHES = 3

internal fun leagueSummary(
    league: LeagueConfig,
    standings: Standings?,
    calendar: List<Match>,
    favoriteTeamId: String?,
): LeagueSummary? {
    val all = standings?.rows.orEmpty()
    val top = all.take(LEAGUE_TOP)
    val mine = all.drop(LEAGUE_TOP).firstOrNull { it.team.id == favoriteTeamId }
    val live = calendar.filter { it.state == MatchState.IN }
    val matches = live.ifEmpty {
        val upcoming = calendar.filter { it.state == MatchState.PRE }.sortedBy { it.kickoff }
        val day = upcoming.firstOrNull()?.let { parisDay(it.kickoff) }
        upcoming.filter { parisDay(it.kickoff) == day }
    }
        // Le match du favori d'abord, puis l'ordre du coup d'envoi.
        .sortedWith(compareByDescending<Match> { favoriteTeamId != null && it.involves(favoriteTeamId) }.thenBy { it.kickoff })
        .take(LEAGUE_MATCHES)
    if (top.isEmpty() && matches.isEmpty()) return null
    return LeagueSummary(league, top + listOfNotNull(mine), favoriteTeamId, matches, live = live.isNotEmpty())
}

/**
 * Prochain match à domicile d'un club, ou null s'il est déjà affiché dans la carte « Mon club »
 * (le match en cours, sinon le prochain) : pas de doublon à l'écran.
 */
internal fun nextHomeMatch(form: TeamForm?): ClubMatch? {
    val home = form?.seasonMatches?.filter { it.home && it.state == MatchState.PRE }?.minByOrNull { it.kickoff } ?: return null
    val shown = form.live ?: form.next
    return home.takeIf { it.id != shown?.id }
}

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

    /** Fiches des clubs suivis, relues toutes les minutes pendant leurs matchs, toutes les 10 min sinon. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val followedForms = settings.settings
        .map { s -> s.followed.map { it.club }.filter { it.teamId != s.favorite?.teamId } }
        .distinctUntilChanged()
        .flatMapLatest { clubs ->
            if (clubs.isEmpty()) flowOf(emptyList())
            else pollingFlow(
                delayFor = { forms: List<Pair<FavoriteClub, TeamForm?>>, now ->
                    val busy = forms.any { (_, f) -> f?.live != null || (f?.next != null && f.next.kickoff - now <= 15 * 60_000) }
                    if (busy) 60_000L else 10 * 60_000L
                },
                fetch = {
                    coroutineScope {
                        clubs.map { c ->
                            async { c to runCatching { repo.teamForm(c.teamId, Competitions.bySlug(c.competition) ?: Competitions.UCL) }.getOrNull() }
                        }.awaitAll()
                    }
                },
            ).map { (it as? PollResult.Success)?.value ?: clubs.map { c -> c to null } }
        }

    /** Ligue favorite : classement en base (rafraîchi à chaque choix) et calendrier déjà chargé. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val leagueCard = settings.settings
        .map { s -> s.favoriteLeague?.let(Competitions::bySlug) to s.favorite?.teamId }
        .distinctUntilChanged()
        .flatMapLatest { (league, teamId) ->
            if (league == null) flowOf(null)
            else {
                viewModelScope.launch { runCatching { repo.refreshStandings(league) } }
                combine(repo.standings(league), calendars) { standings, cals ->
                    leagueSummary(league, standings?.data, cals.firstOrNull { it.first == league }?.second.orEmpty(), teamId)
                }
            }
        }

    private val base = combine(calendars, favorite, favoriteForm, live, followedForms) { cals, fav, form, _, followed ->
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
            followed = followed,
            nextHome = nextHomeMatch(form?.takeIf { fav != null && it.team.id == fav.teamId }),
        )
    }

    val state: StateFlow<HomeUiState> = combine(base, leagueCard) { s, league -> s.copy(league = league) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Calendriers vides au premier lancement : on les remplit (le SyncWorker prend ensuite le relais).
        viewModelScope.launch {
            Competitions.LEAGUES.forEach { league -> launch { runCatching { repo.refreshCalendar(league) } } }
        }
    }
}
