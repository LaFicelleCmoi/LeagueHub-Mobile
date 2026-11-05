package com.leaguehub.android.ui.matchdetail

import com.leaguehub.android.ui.components.segmentPosition
import com.leaguehub.android.ui.components.listSegment
import com.leaguehub.android.ui.components.LiveDot
import com.leaguehub.android.ui.components.EmptyState
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.core.util.formatTime
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LeagueHubTheme
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.util.formatDateTime
import com.leaguehub.android.domain.model.Lineup
import com.leaguehub.android.domain.model.LineupPlayer
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.MatchStat
import com.leaguehub.android.domain.model.Side
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.domain.model.TimelineEvent
import com.leaguehub.android.domain.model.TimelineKind
import com.leaguehub.android.domain.repository.ThemeMode
import com.leaguehub.android.ui.components.ErrorState
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.statusLabel

/** Point d'entrée de la navigation : relie l'écran à son ViewModel. */
@Composable
fun MatchDetailRoute(viewModel: MatchDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchDetailScreen(state, onRetry = viewModel::retry)
}

/** Écran sans état : n'affiche que ce que [state] décrit. */
@Composable
fun MatchDetailScreen(state: MatchDetailUiState, onRetry: () -> Unit) {
    when (state) {
        is MatchDetailUiState.Content -> DetailContent(state.detail)
        MatchDetailUiState.Error -> ErrorState(onRetry = onRetry)
        MatchDetailUiState.Loading -> LoadingState()
    }
}

@Composable
private fun DetailContent(d: MatchDetail) {
    val m = d.match
    val live = m.state == MatchState.IN
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        // Tableau de score.
        item {
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(vertical = 20.dp, horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ScoreSide(m.home.team.name, m.home.team.logo, Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (m.state == MatchState.PRE) formatTime(m.kickoff) else "${m.home.score ?: 0} – ${m.away.score ?: 0}",
                                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black,
                                color = if (m.state == MatchState.PRE) LocalSportColors.current.accent else MaterialTheme.colorScheme.onSurface,
                            )
                            if (m.home.shootout != null) {
                                Text(stringResource(R.string.penalties_short, "${m.home.shootout}-${m.away.shootout}"), style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            StatusPill(statusLabel(m), live)
                        }
                        ScoreSide(m.away.team.name, m.away.team.logo, Modifier.weight(1f))
                    }
                    val info = listOfNotNull(
                        formatDateTime(m.kickoff), m.venue, d.referee?.let { stringResource(R.string.referee, it) },
                        d.attendance?.let { stringResource(R.string.attendance, it) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        info.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        }
        val timeline = d.timeline.filter { it.kind != TimelineKind.OTHER }
        if (timeline.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.timeline)) }
            itemsIndexed(timeline, key = { _, it -> "t" + it.id + it.minute }) { i, e ->
                Column(Modifier.listSegment(segmentPosition(i, timeline.size))) { TimelineRow(e) }
            }
        }
        if (d.stats.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) { d.stats.forEach { StatBar(it) } }
                }
            }
        }
        if (d.lineups.size == 2) {
            item { SectionTitle(stringResource(R.string.lineups)) }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        d.lineups.sortedBy { it.side }.forEach { LineupColumn(it, Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (timeline.isEmpty() && d.stats.isEmpty() && d.lineups.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_details)) }
        }
    }
}

@Composable
private fun ScoreSide(name: String, logo: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(logo, size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Statut du match dans une pilule : rouge en direct, neutre sinon. */
@Composable
private fun StatusPill(text: String, live: Boolean) {
    val color = if (live) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) { LiveDot(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun icon(kind: TimelineKind) = when (kind) {
    TimelineKind.GOAL, TimelineKind.PENALTY_GOAL, TimelineKind.OWN_GOAL -> "⚽"
    TimelineKind.PENALTY_MISSED -> "✖"
    TimelineKind.YELLOW_CARD -> "🟨"
    TimelineKind.SECOND_YELLOW -> "🟨🟥"
    TimelineKind.RED_CARD -> "🟥"
    TimelineKind.SUBSTITUTION -> "⇄"
    TimelineKind.VAR -> "📺"
    TimelineKind.PERIOD -> "⏱"
    TimelineKind.OTHER -> "•"
}

@Composable
private fun TimelineRow(e: TimelineEvent) {
    val text = "${icon(e.kind)} ${e.players.joinToString(" / ").ifEmpty { e.label }}"
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (e.side != Side.AWAY) text else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        // Minute au centre, dans une pastille.
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            Text(
                e.minute,
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            )
        }
        Text(if (e.side == Side.AWAY) text else "", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatBar(s: MatchStat) {
    val homeColor = MaterialTheme.colorScheme.primary
    val awayColor = LocalSportColors.current.accent
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.home, fontWeight = FontWeight.Bold, color = homeColor)
            Text(
                statLabel(s), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(s.away, fontWeight = FontWeight.Bold, color = awayColor)
        }
        // Deux barres séparées par un petit espace : domicile à gauche, extérieur à droite.
        s.share?.let { share ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().clip(RoundedCornerShape(50)).background(homeColor))
                if (share < 1f) Box(Modifier.weight(1f - share).fillMaxHeight().clip(RoundedCornerShape(50)).background(awayColor))
            }
        }
    }
}

@Composable
private fun statLabel(s: MatchStat): String = when (s.key) {
    "possessionPct" -> stringResource(R.string.stat_possession)
    "totalShots" -> stringResource(R.string.stat_shots)
    "shotsOnTarget" -> stringResource(R.string.stat_shots_on_target)
    "wonCorners" -> stringResource(R.string.stat_corners)
    "foulsCommitted" -> stringResource(R.string.stat_fouls)
    "yellowCards" -> stringResource(R.string.stat_yellow)
    "redCards" -> stringResource(R.string.stat_red)
    "offsides" -> stringResource(R.string.stat_offsides)
    "saves" -> stringResource(R.string.stat_saves)
    "accuratePasses" -> stringResource(R.string.stat_passes)
    else -> s.label
}

@Composable
private fun LineupColumn(lineup: Lineup, modifier: Modifier) {
    Column(modifier) {
        Text(lineup.team.shortName, style = MaterialTheme.typography.titleSmall)
        lineup.formation?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        lineup.players.forEachIndexed { i, p ->
            if (i > 0 && !p.starter && lineup.players[i - 1].starter) {
                Text(
                    stringResource(R.string.substitutes).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            Text(
                "${p.jersey ?: ""} ${p.name}".trim(),
                style = MaterialTheme.typography.bodySmall,
                color = if (p.starter) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Aperçus (Android Studio) ---

private fun previewTeam(id: String, name: String) = Team(id, name, name, name.take(3).uppercase(), logo = null)

private val previewDetail: MatchDetail = run {
    val home = previewTeam("132", "Bayern Munich")
    val away = previewTeam("2950", "Mainz")
    MatchDetail(
        match = Match(
            id = "preview", competition = "ger.1", kickoff = 1_794_150_000_000L, state = MatchState.IN,
            statusName = "STATUS_SECOND_HALF", clock = "67'", venue = "Allianz Arena",
            home = MatchSide(home, score = 2, winner = false, played = 10, shootout = null),
            away = MatchSide(away, score = 1, winner = false, played = 10, shootout = null),
        ),
        timeline = listOf(
            TimelineEvent("1", "12'", TimelineKind.GOAL, "Goal", Side.HOME, listOf("H. Kane")),
            TimelineEvent("2", "34'", TimelineKind.YELLOW_CARD, "Yellow Card", Side.AWAY, listOf("D. da Costa")),
            TimelineEvent("3", "51'", TimelineKind.GOAL, "Goal", Side.AWAY, listOf("J. Burkardt")),
            TimelineEvent("4", "60'", TimelineKind.PENALTY_GOAL, "Penalty - Scored", Side.HOME, listOf("H. Kane")),
        ),
        stats = listOf(
            MatchStat("possessionPct", "Possession", "64", "36", share = 0.64f),
            MatchStat("totalShots", "Shots", "14", "6", share = 0.7f),
        ),
        lineups = listOf(Side.HOME to home, Side.AWAY to away).map { (side, team) ->
            Lineup(
                side, team, formation = "4-2-3-1",
                players = listOf(
                    LineupPlayer("${team.id}-1", "Gardien", "1", "G", starter = true),
                    LineupPlayer("${team.id}-9", "Attaquant", "9", "F", starter = true),
                    LineupPlayer("${team.id}-20", "Remplaçant", "20", "M", starter = false),
                ),
            )
        },
        attendance = 75_000,
        referee = "F. Zwayer",
    )
}

@Preview(name = "Contenu", showBackground = true)
@Composable
private fun MatchDetailContentPreview() {
    LeagueHubTheme(mode = ThemeMode.DARK) { MatchDetailScreen(MatchDetailUiState.Content(previewDetail), onRetry = {}) }
}

@Preview(name = "Chargement", showBackground = true)
@Composable
private fun MatchDetailLoadingPreview() {
    LeagueHubTheme(mode = ThemeMode.DARK) { MatchDetailScreen(MatchDetailUiState.Loading, onRetry = {}) }
}

@Preview(name = "Erreur", showBackground = true)
@Composable
private fun MatchDetailErrorPreview() {
    LeagueHubTheme(mode = ThemeMode.DARK) { MatchDetailScreen(MatchDetailUiState.Error, onRetry = {}) }
}
