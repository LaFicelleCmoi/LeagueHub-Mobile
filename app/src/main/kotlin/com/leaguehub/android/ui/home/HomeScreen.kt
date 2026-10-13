package com.leaguehub.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.ui.components.CompactMatchRow
import com.leaguehub.android.ui.components.Countdown
import com.leaguehub.android.ui.components.EmptyState
import com.leaguehub.android.ui.components.FormBadge
import com.leaguehub.android.ui.components.GoalCelebration
import com.leaguehub.android.ui.components.LeagueChip
import com.leaguehub.android.ui.components.LiveDot
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.statusLabel

@Composable
fun HomeScreen(
    onLeague: (LeagueConfig) -> Unit,
    onTeam: (league: String, teamId: String) -> Unit,
    onMatch: (competition: String, eventId: String) -> Unit,
    onPickFavorite: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            // Chiffres clés façon « dashboard ».
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Figure(stringResource(R.string.figure_matches), state.figures.matchesToday.toString(), Modifier.weight(1f))
                    Figure(stringResource(R.string.figure_live), state.figures.liveNow.toString(), Modifier.weight(1f), live = state.figures.liveNow > 0)
                    Figure(stringResource(R.string.figure_goals), state.figures.goalsToday.toString(), Modifier.weight(1f))
                }
            }
            // Accès rapide aux compétitions.
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    items(Competitions.ALL, key = { it.slug }) { league ->
                        LeagueChip(league, selected = false, onClick = { onLeague(league) })
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.my_club)) }
            item { FavoriteCard(state, onTeam, onMatch, onPickFavorite) }
            item { SectionTitle(stringResource(R.string.today_matches)) }
            if (state.today.isEmpty()) item { EmptyState(stringResource(R.string.no_match_today)) }
            // Une carte par compétition, une ligne compacte par match.
            items(state.today, key = { it.first.slug }) { (league, matches) ->
                LeagueMatchesCard(league, matches, state.favorite?.teamId, onLeague, onMatch)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        GoalCelebration(viewModel.goals)
    }
}

/** Carte de chiffre clé : grand nombre, libellé en petites capitales espacées. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier, live: Boolean = false) {
    AppCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) { LiveDot(); Spacer(Modifier.width(8.dp)) }
                Text(
                    value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (live) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Matchs du jour d'une compétition : en-tête cliquable puis une ligne par match. */
@Composable
private fun LeagueMatchesCard(
    league: LeagueConfig,
    matches: List<Match>,
    favoriteId: String?,
    onLeague: (LeagueConfig) -> Unit,
    onMatch: (String, String) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { onLeague(league) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamLogo(league.logo(), size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                league.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        matches.forEach { m ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompactMatchRow(m, favoriteId = favoriteId, onClick = { onMatch(m.competition, m.id) })
        }
    }
}

/**
 * Carte « Mon club » en deux blocs :
 * 1. logo + nom + pastilles de forme ;
 * 2. après un séparateur, le match en cours ou le prochain match, heure en accent.
 */
@Composable
private fun FavoriteCard(
    state: HomeUiState,
    onTeam: (String, String) -> Unit,
    onMatch: (String, String) -> Unit,
    onPickFavorite: () -> Unit,
) {
    val fav = state.favorite
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (fav == null) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.no_favorite_yet))
                TextButton(onClick = onPickFavorite) { Text(stringResource(R.string.choose_club)) }
            }
            return@AppCard
        }
        val form = state.favoriteForm
        // Bloc 1 : identité du club et forme récente.
        Row(
            Modifier.fillMaxWidth().clickable { onTeam(fav.competition, fav.teamId) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamLogo(fav.logo, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                fav.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                form?.results?.reversed()?.forEach { r -> r.outcome?.let { FormBadge(it) } }
            }
        }
        // Bloc 2 : match en direct, sinon prochain match.
        val live = form?.live
        val next = form?.next
        if (live != null || next != null) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (live != null) {
            Row(
                Modifier.fillMaxWidth().clickable { onMatch(live.competitionCode, live.id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(); Spacer(Modifier.width(8.dp))
                Text(
                    "${live.goalsFor ?: 0} – ${live.goalsAgainst ?: 0} ${if (live.home) "vs" else "@"} ${live.opponent.shortName}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    statusLabel(live.statusName, live.state, live.kickoff, live.clock),
                    color = LocalSportColors.current.live,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (next != null) {
            Row(
                Modifier.fillMaxWidth().clickable { onMatch(next.competitionCode, next.id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamLogo(next.opponent.logo, size = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (next.home) "vs" else "@"} ${next.opponent.name}",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${next.competition} · ${formatShortDate(next.kickoff)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Heure mise en évidence dans le coin, compte à rebours dessous.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatTime(next.kickoff),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalSportColors.current.accent,
                    )
                    Countdown(next.kickoff)
                }
            }
        }
    }
}
