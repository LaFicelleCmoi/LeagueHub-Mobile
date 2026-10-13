package com.leaguehub.android.ui.league

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import com.leaguehub.android.ui.components.segmentPosition
import com.leaguehub.android.ui.components.listSegment
import com.leaguehub.android.ui.components.SegmentPosition
import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.GroupLabel
import com.leaguehub.android.ui.components.CompactMatchRow
import com.leaguehub.android.core.theme.LocalSportColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.leaguehub.android.R
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.core.theme.zoneColor
import com.leaguehub.android.core.util.formatDay
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.core.util.parisDay
import com.leaguehub.android.domain.model.Leader
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.ZoneTone
import com.leaguehub.android.ui.components.EmptyState
import com.leaguehub.android.ui.components.ErrorState
import com.leaguehub.android.ui.components.GoalCelebration
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.OfflineBanner
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.StandingLine
import com.leaguehub.android.ui.components.StandingsHeader
import com.leaguehub.android.ui.components.TableLine
import com.leaguehub.android.ui.components.TeamLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    onTeam: (teamId: String) -> Unit,
    onMatch: (Match) -> Unit,
    viewModel: LeagueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_standings, R.string.tab_matches, R.string.tab_scorers, R.string.tab_news)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
            val standings = state.standings
            if (state.offline && standings != null) OfflineBanner(standings.updatedAt)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = {
                    viewModel.refresh()
                    if (tab == 2) viewModel.loadLeaders(force = true)
                    if (tab == 3) viewModel.loadNews(force = true)
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (tab) {
                    0 -> StandingsTab(state, onTeam, viewModel::refresh)
                    1 -> MatchesTab(state, onMatch)
                    2 -> ScorersTab(viewModel, onTeam)
                    else -> NewsTab(viewModel)
                }
            }
        }
        GoalCelebration(viewModel.goals)
    }
}

@Composable
private fun StandingsTab(state: LeagueUiState, onTeam: (String) -> Unit, retry: () -> Unit) {
    val live = state.live
    when {
        live == null && !state.loaded -> LoadingState()
        live == null && state.offline -> ErrorState(onRetry = retry)
        live == null -> EmptyState(stringResource(R.string.no_standings))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (live.provisional) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    ) {
                        Text(
                            stringResource(R.string.live_standings_banner),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            item { StandingsHeader(Modifier.padding(top = 12.dp).listSegment(SegmentPosition.First)) }
            itemsIndexed(live.rows, key = { _, r -> r.row.team.id }) { i, r ->
                // L'en-tête ouvre la carte : les lignes ne sont donc jamais « premières ».
                val pos = if (i == live.rows.lastIndex) SegmentPosition.Last else SegmentPosition.Middle
                Column(Modifier.listSegment(pos)) {
                SegmentDivider()
                StandingLine(
                    TableLine(
                        teamId = r.row.team.id,
                        rank = r.row.rank.toString(),
                        name = r.row.team.name,
                        logo = r.row.team.logo,
                        played = r.row.played,
                        goalDiff = r.row.goalDiff,
                        points = r.row.points,
                        zoneColor = r.row.zone?.let { zoneColor(it.tone).copy(alpha = if (it.qualifying) 0.5f else 1f) },
                        movement = if (live.provisional) r.movement else 0,
                        live = r.live?.match?.state == MatchState.IN,
                    ),
                    favoriteId = state.favoriteId,
                    onClick = { onTeam(r.row.team.id) },
                )
                }
            }
            item { ZoneLegend(live.rows.mapNotNull { it.row.zone?.tone }.distinct()) }
        }
    }
}

/** Légende des zones, en pastilles qui passent à la ligne. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneLegend(tones: List<ZoneTone>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tones.forEach { tone ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(zoneColor(tone)))
                Spacer(Modifier.width(6.dp))
                Text(zoneLabel(tone), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun zoneLabel(tone: ZoneTone): String = stringResource(
    when (tone) {
        ZoneTone.UCL -> R.string.zone_ucl
        ZoneTone.UEL -> R.string.zone_uel
        ZoneTone.UECL -> R.string.zone_uecl
        ZoneTone.PLAYOFF -> R.string.zone_playoff
        ZoneTone.RELEGATION -> R.string.zone_relegation
        ZoneTone.OTHER -> R.string.zone_other
    },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchesTab(state: LeagueUiState, onMatch: (Match) -> Unit) {
    if (state.matches.isEmpty()) {
        if (!state.loaded) LoadingState() else EmptyState(stringResource(R.string.no_matches))
        return
    }
    val upcoming = state.matches.filter { it.state != MatchState.POST }
    val results = state.matches.filter { it.state == MatchState.POST }.sortedByDescending { it.kickoff }
    LazyColumn(Modifier.fillMaxSize()) {
        if (upcoming.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.upcoming_and_live)) }
            dayGroups(upcoming, state.favoriteId, onMatch, countdown = true)
        }
        if (results.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.latest_results)) }
            dayGroups(results, state.favoriteId, onMatch, countdown = false)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Matchs groupés par jour : un intitulé puis une carte, une ligne compacte par match. */
private fun LazyListScope.dayGroups(matches: List<Match>, favoriteId: String?, onMatch: (Match) -> Unit, countdown: Boolean) {
    matches.groupBy { parisDay(it.kickoff) }.forEach { (day, list) ->
        item(key = "day-$day-$countdown") { GroupLabel(formatDay(day)) }
        itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
            Column(Modifier.listSegment(segmentPosition(i, list.size))) {
                if (i > 0) SegmentDivider()
                CompactMatchRow(m, favoriteId = favoriteId, onClick = { onMatch(m) })
            }
        }
    }
}

@Composable
private fun ScorersTab(viewModel: LeagueViewModel, onTeam: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.loadLeaders() }
    val leaders by viewModel.leaders.collectAsStateWithLifecycle()
    when (val l = leaders) {
        Remote.Loading -> LoadingState()
        Remote.Failed -> ErrorState(onRetry = { viewModel.loadLeaders(force = true) })
        is Remote.Ready -> LazyColumn(Modifier.fillMaxSize()) {
            val goals = l.value.data.goals
            val assists = l.value.data.assists
            if (goals.isNotEmpty()) item { SectionTitle(stringResource(R.string.top_scorers)) }
            itemsIndexed(goals, key = { _, it -> "g" + it.id }) { i, it -> LeaderRow(it, onTeam, segmentPosition(i, goals.size)) }
            if (assists.isNotEmpty()) item { SectionTitle(stringResource(R.string.top_assists)) }
            itemsIndexed(assists, key = { _, it -> "a" + it.id }) { i, it -> LeaderRow(it, onTeam, segmentPosition(i, assists.size)) }
            item { Spacer(Modifier.height(24.dp)) }
            if (l.value.data.goals.isEmpty() && l.value.data.assists.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_scorers)) }
            }
        }
    }
}

@Composable
private fun LeaderRow(leader: Leader, onTeam: (String) -> Unit, position: SegmentPosition) {
    Column(Modifier.listSegment(position)) {
    if (position == SegmentPosition.Middle || position == SegmentPosition.Last) SegmentDivider()
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = leader.team != null) { leader.team?.let { onTeam(it.id) } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(leader.rank)
        Spacer(Modifier.width(12.dp))
        TeamLogo(leader.team?.logo, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(leader.player, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                leader.team?.name.orEmpty() + (leader.appearances?.let { " · " + stringResource(R.string.appearances, it) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(leader.value.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
    }
}

/** Rang dans une pastille ; podium mis en avant avec l'accent. */
@Composable
private fun RankBadge(rank: Int) {
    val podium = rank in 1..3
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(if (podium) LocalSportColors.current.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rank.toString(), style = MaterialTheme.typography.labelLarge,
            color = if (podium) LocalSportColors.current.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewsTab(viewModel: LeagueViewModel) {
    LaunchedEffect(Unit) { viewModel.loadNews() }
    val news by viewModel.news.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val n = news) {
        Remote.Loading -> LoadingState()
        Remote.Failed -> ErrorState(onRetry = { viewModel.loadNews(force = true) })
        is Remote.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (n.value.data.isEmpty()) item { EmptyState(stringResource(R.string.no_news)) }
            items(n.value.data, key = { it.id }) { article ->
                AppCard(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(enabled = article.url != null) {
                        article.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    },
                ) {
                    article.image?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(article.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (article.description.isNotEmpty()) {
                            Text(
                                article.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        article.published?.let {
                            Text(formatShortDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
