package com.leaguehub.android.ui.club

import androidx.compose.foundation.clickable
import com.leaguehub.android.ui.components.segmentPosition
import com.leaguehub.android.ui.components.listSegment
import com.leaguehub.android.ui.components.StatTile
import com.leaguehub.android.ui.components.SegmentPosition
import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.GroupLabel
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.core.theme.parseHex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.leaguehub.android.core.util.formatMonth
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.domain.model.ClubMatch
import com.leaguehub.android.domain.model.EuropeanCup
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.TrophyScope
import com.leaguehub.android.ui.components.Countdown
import com.leaguehub.android.ui.components.ErrorState
import com.leaguehub.android.ui.components.FormBadge
import com.leaguehub.android.ui.components.LiveDot
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.statusLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClubScreen(
    onMatch: (competition: String, eventId: String) -> Unit,
    viewModel: ClubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    when {
        form == null && state.failed -> ErrorState(onRetry = viewModel::retry)
        form == null -> LoadingState()
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item {
                ClubHero(form, viewModel.league.name, state.isFavorite, viewModel::toggleFavorite)
            }
            // Chiffres de la saison en tuiles.
            item {
                val s = form.season
                Column(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(s.played.toString(), stringResource(R.string.club_played), Modifier.weight(1f))
                        StatTile(s.wins.toString(), stringResource(R.string.club_wins), Modifier.weight(1f), LocalSportColors.current.positive)
                        StatTile(s.draws.toString(), stringResource(R.string.club_draws), Modifier.weight(1f))
                        StatTile(s.losses.toString(), stringResource(R.string.club_losses), Modifier.weight(1f), LocalSportColors.current.negative)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(s.goalsFor.toString(), stringResource(R.string.club_goals_for), Modifier.weight(1f))
                        StatTile(s.goalsAgainst.toString(), stringResource(R.string.club_goals_against), Modifier.weight(1f))
                    }
                }
            }
            form.live?.let { live ->
                item {
                    SectionTitle(stringResource(R.string.live_now))
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onMatch(live.competitionCode, live.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(); Spacer(Modifier.width(10.dp))
                            Text("${live.goalsFor ?: 0} – ${live.goalsAgainst ?: 0}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text((if (live.home) "vs " else "@ ") + live.opponent.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${live.competition} · ${statusLabel(live.statusName, live.state, live.kickoff, live.clock)}",
                                    style = MaterialTheme.typography.bodySmall, color = LocalSportColors.current.live,
                                )
                            }
                        }
                    }
                }
            }
            form.next?.let { next ->
                item {
                    SectionTitle(stringResource(R.string.next_match))
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onMatch(next.competitionCode, next.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamLogo(next.opponent.logo, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text((if (next.home) "vs " else "@ ") + next.opponent.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${next.competition} · ${formatShortDate(next.kickoff)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatTime(next.kickoff), style = MaterialTheme.typography.titleLarge, color = LocalSportColors.current.accent)
                                Countdown(next.kickoff)
                            }
                        }
                    }
                }
            }
            if (form.results.isNotEmpty()) item { SectionTitle(stringResource(R.string.form_last5)) }
            itemsIndexed(form.results, key = { _, it -> "r" + it.id }) { i, r ->
                ClubMatchRow(r, onMatch, segmentPosition(i, form.results.size))
            }

            state.palmares?.let { p ->
                item {
                    SectionTitle(stringResource(R.string.palmares, p.total))
                    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(TrophyScope.NATIONAL, TrophyScope.EUROPE, TrophyScope.MONDE, TrophyScope.AUTRE).forEach { scope ->
                                val trophies = p.trophies.filter { it.trophyScope == scope }
                                if (trophies.isEmpty()) return@forEach
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        stringResource(scopeTitle(scope)).uppercase(),
                                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        trophies.forEach { t -> TrophyPill(t.label, t.count) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Déroulé de la saison, mois par mois.
            val byMonth = form.seasonMatches.groupBy { formatMonth(it.kickoff) }
            if (byMonth.isNotEmpty()) item { SectionTitle(stringResource(R.string.season_by_month)) }
            byMonth.forEach { (month, matches) ->
                item(key = "m-$month") { GroupLabel(month.replaceFirstChar { it.titlecase() }) }
                itemsIndexed(matches, key = { _, it -> "s" + it.id }) { i, m -> ClubMatchRow(m, onMatch, segmentPosition(i, matches.size)) }
            }
        }
    }
}

/** En-tête du club : dégradé à ses couleurs, logo, nom, compétition, forme et étoile favori. */
@Composable
private fun ClubHero(form: TeamForm, league: String, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    val clubColor = parseHex(form.team.color) ?: MaterialTheme.colorScheme.primary
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(clubColor.copy(alpha = 0.35f), Color.Transparent)),
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(form.team.logo, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(form.team.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(league, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(if (isFavorite) R.string.unset_favorite else R.string.set_favorite),
                            tint = if (isFavorite) LocalSportColors.current.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val outcomes = form.results.reversed().mapNotNull { it.outcome }
                if (outcomes.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        outcomes.forEach { FormBadge(it, size = 22.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyPill(label: String, count: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.EmojiEvents, null, tint = LocalSportColors.current.favorite, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(6.dp))
        Text("×$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LocalSportColors.current.accent)
    }
}

private fun scopeTitle(scope: TrophyScope) = when (scope) {
    TrophyScope.NATIONAL -> R.string.trophies_national
    TrophyScope.EUROPE -> R.string.trophies_europe
    TrophyScope.MONDE -> R.string.trophies_world
    TrophyScope.AUTRE -> R.string.trophies_other
}

@Composable
private fun ClubMatchRow(m: ClubMatch, onMatch: (String, String) -> Unit, position: SegmentPosition) {
    Column(Modifier.listSegment(position)) {
    if (position == SegmentPosition.Middle || position == SegmentPosition.Last) SegmentDivider()
    Row(
        Modifier.fillMaxWidth().clickable { onMatch(m.competitionCode, m.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.outcome != null) FormBadge(m.outcome) else Spacer(Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(52.dp)) {
            Text(formatShortDate(m.kickoff), style = MaterialTheme.typography.labelSmall)
            Text(if (m.home) "⌂" else "✈", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TeamLogo(m.opponent.logo, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(m.opponent.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                m.competition + (m.europeanCup?.let { " " + cupBadge(it) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (m.europeanCup != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when (m.state) {
            MatchState.PRE -> Text(statusLabel(m.statusName, m.state, m.kickoff, m.clock), style = MaterialTheme.typography.bodySmall)
            else -> Column(horizontalAlignment = Alignment.End) {
                Text("${m.goalsFor ?: "–"}-${m.goalsAgainst ?: "–"}", fontWeight = FontWeight.Bold,
                    color = if (m.state == MatchState.IN) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurface)
                m.detail?.let {
                    Text(if (it == "aet") stringResource(R.string.after_extra_time) else stringResource(R.string.penalties_short, it), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    }
}

private fun cupBadge(cup: EuropeanCup) = when (cup) {
    EuropeanCup.UCL -> "★"
    EuropeanCup.UEL -> "◆"
    EuropeanCup.UECL -> "●"
}
