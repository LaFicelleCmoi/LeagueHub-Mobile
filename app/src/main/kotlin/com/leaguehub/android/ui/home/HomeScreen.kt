package com.leaguehub.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.parseHex
import com.leaguehub.android.core.util.formatDay
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.schedule.PARIS
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
import java.time.LocalDate

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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Header() }
            // Affiche du club favori : le cœur de l'accueil.
            item { FavoriteHero(state, onTeam, onMatch, onPickFavorite) }
            // Clubs suivis : une carte par club, prochain match ou score en direct.
            if (state.followed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.followed_clubs)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(state.followed, key = { it.first.teamId }) { (club, form) -> FollowedCard(club, form, onTeam, onMatch) }
                    }
                }
            }
            item { Figures(state.figures) }
            // Accès rapide aux compétitions.
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    items(Competitions.ALL, key = { it.slug }) { league ->
                        LeagueChip(league, selected = false, onClick = { onLeague(league) })
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.today_matches)) }
            if (state.today.isEmpty()) item { EmptyState(stringResource(R.string.no_match_today)) }
            // Une carte par compétition, une ligne compacte par match.
            items(state.today, key = { it.first.slug }) { (league, matches) ->
                LeagueMatchesCard(league, matches, state.favorite?.teamId, onLeague, onMatch)
            }
        }
        GoalCelebration(viewModel.goals)
    }
}

/** Grand en-tête : date du jour en accent, nom de l'app en très grand. */
@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
        Text(
            formatDay(LocalDate.now(PARIS)).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
    }
}

/** Les trois chiffres du jour dans une seule carte, séparés par de fins traits verticaux. */
@Composable
private fun Figures(figures: KeyFigures) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            Figure(stringResource(R.string.figure_matches), figures.matchesToday.toString(), Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Figure(stringResource(R.string.figure_live), figures.liveNow.toString(), Modifier.weight(1f), live = figures.liveNow > 0)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Figure(stringResource(R.string.figure_goals), figures.goalsToday.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier, live: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) { LiveDot(); Spacer(Modifier.width(6.dp)) }
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                color = if (live) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Matchs du jour d'une compétition : en-tête marqué de la couleur de la ligue, puis une ligne par match. */
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
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { onLeague(league) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color(league.accent)))
            Row(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(league.logo(), size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(league.name.uppercase(), style = MaterialTheme.typography.labelLarge, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        matches.forEach { m ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompactMatchRow(m, favoriteId = favoriteId, onClick = { onMatch(m.competition, m.id) })
        }
    }
}

/**
 * Affiche du club favori : dégradé à ses couleurs, les deux logos face à face
 * et l'heure (ou le score en direct) en très grand au centre.
 */
@Composable
private fun FavoriteHero(
    state: HomeUiState,
    onTeam: (String, String) -> Unit,
    onMatch: (String, String) -> Unit,
    onPickFavorite: () -> Unit,
) {
    val fav = state.favorite
    if (fav == null) {
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.StarOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.no_favorite_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPickFavorite) { Text(stringResource(R.string.choose_club)) }
            }
        }
        return
    }
    val form = state.favoriteForm
    val live = form?.live
    val next = form?.next
    val match = live ?: next
    val clubColor = parseHex(fav.color) ?: MaterialTheme.colorScheme.primary
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(clubColor.copy(alpha = 0.45f), Color.Transparent)))
                .clickable { if (match != null) onMatch(match.competitionCode, match.id) else onTeam(fav.competition, fav.teamId) }
                .padding(20.dp),
        ) {
            // Bandeau : « Mon club » + forme récente.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.my_club).uppercase(),
                    style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    form?.results?.reversed()?.forEach { r -> r.outcome?.let { FormBadge(it) } }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (match == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(fav.logo, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(fav.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.widget_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@AppCard
            }
            // Le favori à gauche, l'adversaire à droite, quel que soit le terrain.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroSide(fav.logo, fav.name, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    if (live != null) {
                        Text(
                            "${live.goalsFor ?: 0} – ${live.goalsAgainst ?: 0}",
                            style = MaterialTheme.typography.displayMedium, color = LocalSportColors.current.live,
                        )
                        Pill(statusLabel(live.statusName, live.state, live.kickoff, live.clock), LocalSportColors.current.live, dot = true)
                    } else if (next != null) {
                        Text(formatTime(next.kickoff), style = MaterialTheme.typography.displayMedium, color = LocalSportColors.current.accent)
                        Text(formatShortDate(next.kickoff), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Countdown(next.kickoff)
                    }
                }
                HeroSide(match.opponent.logo, match.opponent.shortName, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                (if (match.home) "⌂ " else "✈ ") + match.competition,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HeroSide(logo: String?, name: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(logo, size = 64.dp)
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Pill(text: String, color: Color, dot: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) { LiveDot(); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Carte d'un club suivi : logo et nom, puis son match en direct ou le prochain. */
@Composable
private fun FollowedCard(
    club: FavoriteClub,
    form: TeamForm?,
    onTeam: (String, String) -> Unit,
    onMatch: (String, String) -> Unit,
) {
    val match = form?.live ?: form?.next
    AppCard(
        Modifier.width(220.dp).clickable {
            if (match != null) onMatch(match.competitionCode, match.id) else onTeam(club.competition, club.teamId)
        },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(club.logo, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(club.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            when {
                form == null -> Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                match == null -> Text(stringResource(R.string.widget_no_match), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(match.opponent.logo, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (match.home) "vs " else "@ ") + match.opponent.shortName,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (form.live != null) statusLabel(match.statusName, match.state, match.kickoff, match.clock) else formatShortDate(match.kickoff),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (form.live != null) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (form.live != null) "${match.goalsFor ?: 0}-${match.goalsAgainst ?: 0}" else formatTime(match.kickoff),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (form.live != null) LocalSportColors.current.live else LocalSportColors.current.accent,
                    )
                }
            }
        }
    }
}
