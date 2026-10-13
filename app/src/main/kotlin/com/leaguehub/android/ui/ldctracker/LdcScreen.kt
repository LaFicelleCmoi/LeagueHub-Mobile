package com.leaguehub.android.ui.ldctracker

import com.leaguehub.android.ui.components.segmentPosition
import com.leaguehub.android.ui.components.listSegment
import com.leaguehub.android.ui.components.SegmentPosition
import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.GroupLabel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.phaseZoneColor
import com.leaguehub.android.core.util.formatDateTime
import com.leaguehub.android.core.util.formatDay
import com.leaguehub.android.data.ldc.Draw2026
import com.leaguehub.android.domain.knockout.Round
import com.leaguehub.android.domain.knockout.Tie
import com.leaguehub.android.domain.knockout.TieWinner
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.simulation.SimulatedRow
import com.leaguehub.android.domain.standings.PhaseZone
import com.leaguehub.android.domain.standings.zoneOf
import com.leaguehub.android.ui.components.Countdown
import com.leaguehub.android.ui.components.EmptyState
import com.leaguehub.android.ui.components.FormBadge
import com.leaguehub.android.ui.components.GoalCelebration
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.MatchCard
import com.leaguehub.android.ui.components.OfflineBanner
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.StandingLine
import com.leaguehub.android.ui.components.StandingsHeader
import com.leaguehub.android.ui.components.TableLine
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.statusLabel
import com.leaguehub.android.ui.components.toOutcome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LdcScreen(
    onTeam: (String) -> Unit,
    onMatch: (Match) -> Unit,
    viewModel: LdcViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.ldc_tab_league, R.string.ldc_tab_knockout, R.string.ldc_tab_simulator, R.string.ldc_tab_draw)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) }) }
            }
            if (state.offline && state.updatedAt > 0) OfflineBanner(state.updatedAt)
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> LeaguePhaseTab(state, onTeam, onMatch)
                    1 -> KnockoutTab(state, onMatch)
                    2 -> SimulatorTab(state, viewModel)
                    else -> DrawTab(state, viewModel, onMatch)
                }
            }
        }
        GoalCelebration(viewModel.goals)
    }
}

@Composable
private fun NextKickoffCard(state: LdcUiState) {
    val next = state.next ?: return
    AppCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.next_kickoff).uppercase(), style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(next.match.home.team.logo, size = 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${next.match.home.team.shortName} – ${next.match.away.team.shortName}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                TeamLogo(next.match.away.team.logo, size = 24.dp)
                Spacer(Modifier.width(12.dp))
                Countdown(next.time)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatDateTime(next.time) + if (next.sameDay.size > 1) " · " + stringResource(R.string.matches_that_day, next.sameDay.size) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LeaguePhaseTab(state: LdcUiState, onTeam: (String) -> Unit, onMatch: (Match) -> Unit) {
    val table = state.table
    if (table == null) {
        LoadingState(); return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { NextKickoffCard(state) }
        if (state.liveMatches.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.live_now)) }
            items(state.liveMatches, key = { "live-" + it.id }) { m -> MatchCard(m, favoriteId = state.favoriteId, onClick = { onMatch(m) }) }
        }
        item {
            Text(
                stringResource(if (table.complete) R.string.ldc_rules_complete else R.string.ldc_rules_in_progress),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PhaseZone.entries.forEach { zone ->
            val rows = table.rows.filter { zoneOf(it.pos) == zone }
            if (rows.isEmpty()) return@forEach
            item(key = "zone-$zone") {
                SectionTitle(stringResource(zoneTitle(zone)) + " (${zone.from}-${zone.to})")
                StandingsHeader(Modifier.listSegment(SegmentPosition.First))
            }
            itemsIndexed(rows, key = { _, it -> it.teamId }) { i, r ->
                Column(Modifier.listSegment(if (i == rows.lastIndex) SegmentPosition.Last else SegmentPosition.Middle)) {
                    SegmentDivider()
                    StandingLine(
                        TableLine(
                            teamId = r.teamId, rank = if (r.sharedRank) "=" + r.rank else r.rank.toString(), name = r.name,
                            logo = r.logo, played = r.played, goalDiff = r.goalDiff, points = r.points,
                            zoneColor = phaseZoneColor(zone), live = r.live,
                        ),
                        favoriteId = state.favoriteId,
                        onClick = { onTeam(r.teamId) },
                    )
                    if (r.form.isNotEmpty() || r.decidedBy != null) {
                        Row(Modifier.padding(start = 76.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            r.form.forEach { FormBadge(it.toOutcome(), Modifier.padding(end = 3.dp)) }
                            r.decidedBy?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.decided_by, criterionLabel(it)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (r.unresolved) LocalSportColors.current.soon else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun zoneTitle(zone: PhaseZone) = when (zone) {
    PhaseZone.Q -> R.string.ldc_zone_q
    PhaseZone.PO -> R.string.ldc_zone_po
    PhaseZone.OUT -> R.string.ldc_zone_out
}

/** Libellé lisible d'un critère de départage de l'article 18.01. */
@Composable
fun criterionLabel(key: String): String = when {
    key == "pts" -> stringResource(R.string.crit_pts)
    key == "gd" -> stringResource(R.string.crit_gd)
    key == "gf" -> stringResource(R.string.crit_gf)
    key == "awayGf" -> stringResource(R.string.crit_away_gf)
    key == "w" -> stringResource(R.string.crit_w)
    key == "awayW" -> stringResource(R.string.crit_away_w)
    key == "oppPts" -> stringResource(R.string.crit_opp_pts)
    key == "oppGd" -> stringResource(R.string.crit_opp_gd)
    key == "oppGf" -> stringResource(R.string.crit_opp_gf)
    key == "disc" -> stringResource(R.string.crit_disc)
    key == "coef" -> stringResource(R.string.crit_coef)
    key == "h2h" -> stringResource(R.string.crit_h2h)
    key == "alphabetical" -> stringResource(R.string.crit_alphabetical)
    key == "unresolved" -> stringResource(R.string.crit_unresolved)
    key == "coef:association" -> stringResource(R.string.crit_coef_association)
    key.startsWith("coef:") -> stringResource(R.string.crit_coef_season, key.removePrefix("coef:"))
    else -> key
}

@Composable
private fun KnockoutTab(state: LdcUiState, onMatch: (Match) -> Unit) {
    if (state.bracket.isEmpty()) {
        EmptyState(stringResource(R.string.knockout_not_started)); return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        state.champion?.let { champ ->
            item {
                AppCard(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, tint = LocalSportColors.current.favorite)
                        Spacer(Modifier.width(12.dp))
                        TeamLogo(champ.logo, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.champion, champ.name), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        state.bracket.forEach { round ->
            item(key = "round-${round.round}") { SectionTitle(stringResource(roundTitle(round.round))) }
            items(round.ties, key = { round.round.name + it.teamA.id + it.teamB.id }) { tie -> TieCard(tie, onMatch) }
        }
    }
}

fun roundTitle(round: Round) = when (round) {
    Round.PLAYOFF -> R.string.round_playoff
    Round.R16 -> R.string.round_r16
    Round.QF -> R.string.round_qf
    Round.SF -> R.string.round_sf
    Round.FINAL -> R.string.round_final
    Round.LEAGUE -> R.string.ldc_tab_league
    Round.QUALIFYING -> R.string.round_qualifying
}

@Composable
private fun TieCard(tie: Tie, onMatch: (Match) -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            TieSide(tie.teamA.name, tie.teamA.logo, tie.aggA, tie.penA, tie.winner == TieWinner.A, tie.started)
            TieSide(tie.teamB.name, tie.teamB.logo, tie.aggB, tie.penB, tie.winner == TieWinner.B, tie.started)
            Spacer(Modifier.size(4.dp))
            tie.legs.forEachIndexed { i, leg ->
                Text(
                    (if (tie.twoLegs) stringResource(if (i == 0) R.string.first_leg else R.string.second_leg) + " · " else "") +
                        "${leg.home.team.abbreviation} ${leg.home.score ?: "–"}-${leg.away.score ?: "–"} ${leg.away.team.abbreviation} · " +
                        if (leg.state == MatchState.PRE) formatDateTime(leg.kickoff) else statusLabel(leg),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (leg.state == MatchState.IN) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onMatch(leg) }.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TieSide(name: String, logo: String?, agg: Int, pen: Int?, winner: Boolean, started: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        TeamLogo(logo, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, Modifier.weight(1f), fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        if (started) Text(agg.toString() + (pen?.let { " ($it)" } ?: ""), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SimulatorTab(state: LdcUiState, viewModel: LdcViewModel) {
    if (state.simulable.isEmpty()) {
        EmptyState(stringResource(R.string.simulator_nothing)); return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.simulator_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (state.sims.isNotEmpty()) TextButton(onClick = viewModel::resetSimulation) { Text(stringResource(R.string.reset)) }
            }
        }
        if (state.simulated.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.simulated_table)) }
            item { StandingsHeader(Modifier.listSegment(SegmentPosition.First)) }
            itemsIndexed(state.simulated, key = { _, it -> "sim-" + it.row.teamId }) { i, row ->
                Column(Modifier.listSegment(if (i == state.simulated.lastIndex) SegmentPosition.Last else SegmentPosition.Middle)) {
                    SegmentDivider()
                    SimRow(row, state.favoriteId)
                }
            }
        }
        item { SectionTitle(stringResource(R.string.simulator_matches)) }
        val grouped = state.simulable.groupBy { if (it.kickoff > 0) formatDay(it.kickoff) else "" }
        grouped.forEach { (day, matches) ->
            if (day.isNotEmpty()) item(key = "simday-$day") { GroupLabel(day) }
            itemsIndexed(matches, key = { _, it -> "simm-" + it.id }) { i, m ->
                val sim = state.sims[m.id]
                Column(Modifier.listSegment(segmentPosition(i, matches.size))) {
                if (i > 0) SegmentDivider()
                SimMatchRow(
                    m, sim?.homeGoals, sim?.awayGoals,
                    onChange = { h, a -> viewModel.setScore(m.id, h, a) },
                    onClear = { viewModel.clearScore(m.id) },
                )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SimRow(row: SimulatedRow, favoriteId: String?) {
    StandingLine(
        TableLine(
            teamId = row.row.teamId, rank = row.row.pos.toString(), name = row.row.name, logo = row.row.logo,
            played = row.row.played, goalDiff = row.row.goalDiff, points = row.row.points,
            zoneColor = phaseZoneColor(row.zone), movement = row.delta,
        ),
        favoriteId = favoriteId,
        onClick = {},
        // Changement de zone : le saut visuellement notable.
        modifier = if (row.changedZone) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
    )
}

@Composable
private fun SimMatchRow(match: Match, home: Int?, away: Int?, onChange: (Int, Int) -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(match.home.team.shortName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Stepper(home) { onChange(it, away ?: 0) }
        Text(" – ")
        Stepper(away) { onChange(home ?: 0, it) }
        Text(
            match.away.team.shortName, Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
        )
        if (home != null) IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) { Text("✕") }
    }
}

@Composable
private fun Stepper(value: Int?, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValue(((value ?: 0) - 1).coerceAtLeast(0)) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, stringResource(R.string.minus), Modifier.size(16.dp))
        }
        Text(value?.toString() ?: "–", fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        IconButton(onClick = { onValue((value ?: -1) + 1) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.plus), Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DrawTab(state: LdcUiState, viewModel: LdcViewModel, onMatch: (Match) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(state.favoriteId?.takeIf { Draw2026.club(it) != null } ?: Draw2026.QUALIFIED.first().id) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Spacer(Modifier.width(6.dp)) }
            items(Draw2026.QUALIFIED, key = { it.id }) { club ->
                FilterChip(
                    selected = club.id == selected,
                    onClick = { selected = club.id },
                    label = { Text(club.abbr) },
                    leadingIcon = { TeamLogo(club.logo, size = 18.dp) },
                )
            }
        }
        val club = Draw2026.club(selected)
        Text(
            stringResource(R.string.draw_title, club?.name.orEmpty(), club?.pot ?: 0),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.draw_source, Draw2026.PUBLISHED),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val fixtures = viewModel.drawFor(selected, state.leagueMatches)
        LazyColumn(contentPadding = PaddingValues(top = 8.dp)) {
            itemsIndexed(fixtures, key = { _, it -> it.opponent.id + it.home }) { i, f ->
                Column(Modifier.listSegment(segmentPosition(i, fixtures.size))) {
                    if (i > 0) SegmentDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = f.match != null) { f.match?.let(onMatch) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (f.home) "⌂" else "✈", Modifier.width(24.dp))
                        TeamLogo(f.opponent.logo, size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(f.opponent.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        val m = f.match
                        when {
                            m == null -> Text(stringResource(R.string.date_tbd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            m.state == MatchState.PRE -> Text(formatDateTime(m.kickoff), style = MaterialTheme.typography.bodySmall)
                            else -> Text("${m.home.score}-${m.away.score}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.draw_legend),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
