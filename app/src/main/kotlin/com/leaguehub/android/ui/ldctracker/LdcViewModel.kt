package com.leaguehub.android.ui.ldctracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.data.ldc.Coefficients2026
import com.leaguehub.android.data.ldc.Draw2026
import com.leaguehub.android.data.espn.teamLogo
import com.leaguehub.android.domain.knockout.KnockoutRound
import com.leaguehub.android.domain.knockout.Round
import com.leaguehub.android.domain.knockout.buildBracket
import com.leaguehub.android.domain.knockout.findChampion
import com.leaguehub.android.domain.knockout.roundOf
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.live.PollResult
import com.leaguehub.android.domain.live.nextPollDelay
import com.leaguehub.android.domain.live.pollingFlow
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.schedule.NextKickoff
import com.leaguehub.android.domain.schedule.getNextKickoff
import com.leaguehub.android.domain.simulation.SimScore
import com.leaguehub.android.domain.simulation.SimulatedRow
import com.leaguehub.android.domain.simulation.simulateStandings
import com.leaguehub.android.domain.standings.LeaguePhaseTable
import com.leaguehub.android.domain.standings.PhaseMatch
import com.leaguehub.android.domain.standings.PhaseSide
import com.leaguehub.android.domain.standings.RankOptions
import com.leaguehub.android.domain.standings.rankLeaguePhase
import com.leaguehub.android.ui.common.GoalWatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

/** Un adversaire du tirage, avec le match ESPN correspondant s'il est publié. */
data class DrawFixture(val opponent: Team, val home: Boolean, val match: Match?)

data class LdcUiState(
    val loaded: Boolean = false,
    val offline: Boolean = false,
    val refreshing: Boolean = false,
    val updatedAt: Long = 0,
    val table: LeaguePhaseTable? = null,
    val bracket: List<KnockoutRound> = emptyList(),
    val champion: Team? = null,
    val leagueMatches: List<Match> = emptyList(),
    val next: NextKickoff? = null,
    val liveMatches: List<Match> = emptyList(),
    /** Matchs de phase de ligue simulables (à venir), ESPN ou tirage en repli. */
    val simulable: List<Match> = emptyList(),
    val sims: Map<String, SimScore> = emptyMap(),
    val simulated: List<SimulatedRow> = emptyList(),
    val favoriteId: String? = null,
)

@HiltViewModel
class LdcViewModel @Inject constructor(
    private val repo: FootballRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val ucl = Competitions.UCL
    private val refreshing = MutableStateFlow(false)
    private val offline = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)
    private val sims = MutableStateFlow<Map<String, SimScore>>(emptyMap())

    private val season = repo.uclSeason().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val goalWatcher = GoalWatcher()
    val goals: SharedFlow<GoalWatcher.Goal> = goalWatcher.events

    /** Direct : les scores lus sont recopiés en base, la saison observée se met à jour d'elle-même. */
    private val live: StateFlow<List<Match>> = pollingFlow(
        delayFor = { fetched, now -> nextPollDelay(season.value?.data.orEmpty() + fetched, now) },
        fetch = { repo.liveMatches(ucl) },
    ).map { r -> if (r is PollResult.Success) r.value.also(goalWatcher::observe) else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val favorite = settings.settings.map { it.favorite?.teamId }
        .onEach { goalWatcher.favoriteId = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val engine = season.map { cached ->
        val matches = cached?.data.orEmpty()
        val league = matches.filter { roundOf(it.roundSlug, monthOf(it.kickoff)) == Round.LEAGUE }
        val knockout = matches.filter { roundOf(it.roundSlug, monthOf(it.kickoff)).isKnockout }
        val phase = league.map(::toPhaseMatch)
        val table = rank(phase)
        val bracket = buildBracket(knockout) { roundOf(it.roundSlug, monthOf(it.kickoff)) }
        Engine(cached?.updatedAt ?: 0, matches, league, phase, table, bracket, simulable(league))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LdcUiState> = combine(
        engine, sims, live, favorite,
        combine(refreshing, offline, loaded) { r, o, l -> Triple(r, o, l) },
    ) { e, s, liveMatches, fav, (r, o, l) ->
        val now = System.currentTimeMillis()
        LdcUiState(
            loaded = l || e?.matches?.isNotEmpty() == true,
            offline = o,
            refreshing = r,
            updatedAt = e?.updatedAt ?: 0,
            table = e?.table,
            bracket = e?.bracket.orEmpty(),
            champion = e?.bracket?.let(::findChampion),
            leagueMatches = e?.league.orEmpty(),
            next = e?.matches?.let { getNextKickoff(it, now, graceMs = 2 * 60_000) },
            liveMatches = e?.matches.orEmpty().filter { it.state == MatchState.IN }.ifEmpty { liveMatches.filter { it.state == MatchState.IN } },
            simulable = e?.simulable.orEmpty(),
            sims = s,
            simulated = if (e == null || s.isEmpty()) emptyList() else {
                val all = e.phase + e.simulable.filter { m -> e.phase.none { it.id == m.id } }.map(::toPhaseMatch)
                simulateStandings(e.table.rows, s.values.toList(), all) { rank(it).rows }
            },
            favoriteId = fav,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LdcUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            offline.value = runCatching { repo.refreshUclSeason() }.isFailure
            refreshing.value = false
            loaded.value = true
        }
    }

    fun setScore(matchId: String, home: Int, away: Int) =
        sims.update { it + (matchId to SimScore(matchId, home.coerceIn(0, 20), away.coerceIn(0, 20))) }

    fun clearScore(matchId: String) = sims.update { it - matchId }
    fun resetSimulation() { sims.value = emptyMap() }

    /** Les 8 adversaires d'un club (tirage officiel), complétés par le calendrier ESPN. */
    fun drawFor(clubId: String, leagueMatches: List<Match>): List<DrawFixture> {
        val opponents = Draw2026.OPPONENTS[clubId] ?: return emptyList()
        fun fixture(oppId: String, home: Boolean): DrawFixture {
            val club = Draw2026.club(oppId)
            val team = Team(oppId, club?.name ?: oppId, club?.name ?: oppId, club?.abbr ?: "", teamLogo(oppId), club?.color)
            val match = leagueMatches.firstOrNull {
                if (home) it.home.team.id == clubId && it.away.team.id == oppId else it.home.team.id == oppId && it.away.team.id == clubId
            }
            return DrawFixture(team, home, match)
        }
        return (opponents.home.map { fixture(it, true) } + opponents.away.map { fixture(it, false) })
            .sortedWith(compareBy<DrawFixture> { it.match?.kickoff ?: Long.MAX_VALUE }.thenBy { !it.home })
    }

    // --- Moteur ---

    private fun rank(matches: List<PhaseMatch>): LeaguePhaseTable = rankLeaguePhase(
        matches,
        RankOptions(
            seed = Draw2026.SEED,
            coefficients = Coefficients2026.ENGINE,
            coefficientSeasons = Coefficients2026.SEASONS,
        ),
    )

    /**
     * Matchs simulables : ceux d'ESPN encore à venir ; si ESPN n'a pas encore publié le calendrier,
     * les 144 affiches du tirage officiel, en repli.
     */
    private fun simulable(league: List<Match>): List<Match> {
        if (league.isNotEmpty()) return league.filter { it.state == MatchState.PRE }
        return Draw2026.OPPONENTS.flatMap { (clubId, o) -> o.home.map { opp -> drawMatch(clubId, opp) } }
    }

    private fun drawMatch(homeId: String, awayId: String): Match {
        fun side(id: String): MatchSide {
            val c = Draw2026.club(id)
            return MatchSide(Team(id, c?.name ?: id, c?.name ?: id, c?.abbr ?: "", teamLogo(id), c?.color), null, false, null, null)
        }
        return Match(
            id = "draw-$homeId-$awayId", competition = ucl.espnCode, kickoff = 0, state = MatchState.PRE,
            statusName = "", clock = "", venue = null, home = side(homeId), away = side(awayId), roundSlug = "league-phase",
        )
    }

    private fun toPhaseMatch(m: Match) = PhaseMatch(
        id = m.id,
        state = m.state,
        time = m.kickoff,
        home = PhaseSide(m.home.team.id, m.home.team.name, m.home.score, m.home.team.abbreviation, m.home.team.logo.orEmpty()),
        away = PhaseSide(m.away.team.id, m.away.team.name, m.away.score, m.away.team.abbreviation, m.away.team.logo.orEmpty()),
    )

    private fun monthOf(time: Long): Int = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).monthValue

    private class Engine(
        val updatedAt: Long,
        val matches: List<Match>,
        val league: List<Match>,
        val phase: List<PhaseMatch>,
        val table: LeaguePhaseTable,
        val bracket: List<KnockoutRound>,
        val simulable: List<Match>,
    )
}
