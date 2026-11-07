package com.leaguehub.android.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LeagueHubTheme
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.parseHex
import com.leaguehub.android.core.util.formatDateTime
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.model.PlayerGame
import com.leaguehub.android.domain.model.PlayerPosition
import com.leaguehub.android.domain.model.PlayerProfile
import com.leaguehub.android.domain.model.PlayerSeason
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.ui.components.EmptyState
import com.leaguehub.android.ui.components.ErrorState
import com.leaguehub.android.ui.components.FormBadge
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.SegmentPosition
import com.leaguehub.android.ui.components.StatTile
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.listSegment
import com.leaguehub.android.ui.components.segmentPosition
import com.leaguehub.android.domain.leagueconfig.Competitions
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Point d'entrée de la navigation : relie l'écran à son ViewModel. */
@Composable
fun PlayerRoute(
    onTeam: (league: String, teamId: String) -> Unit,
    onMatch: (competition: String, eventId: String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayerScreen(state, onRetry = viewModel::retry, onTeam = onTeam, onMatch = onMatch)
}

/** Écran sans état : n'affiche que ce que [state] décrit. */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onRetry: () -> Unit,
    onTeam: (league: String, teamId: String) -> Unit,
    onMatch: (competition: String, eventId: String) -> Unit,
) {
    when (state) {
        PlayerUiState.Loading -> LoadingState()
        PlayerUiState.Error -> ErrorState(onRetry = onRetry)
        is PlayerUiState.Content -> PlayerContent(state.player, state.offlineSince, onTeam, onMatch)
    }
}

@Composable
private fun PlayerContent(
    p: PlayerProfile,
    offlineSince: Long?,
    onTeam: (league: String, teamId: String) -> Unit,
    onMatch: (competition: String, eventId: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        offlineSince?.let { since ->
            item {
                Text(
                    stringResource(R.string.offline_since, formatDateTime(since)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        }
        item { PlayerHero(p, onTeam = { team -> onTeam(clubLeagueSlug(p), team.id) }) }
        item {
            // Nom de la compétition tel que l'app l'affiche partout ailleurs, sinon celui d'ESPN.
            val league = p.leagueCode?.let(Competitions::byEspnCode)?.name ?: p.leagueName
            SectionTitle(league?.let { stringResource(R.string.player_season_in, it) } ?: stringResource(R.string.player_season))
            SeasonTiles(p.season)
        }
        item { SectionTitle(stringResource(R.string.player_games)) }
        if (p.games.isEmpty()) {
            item { EmptyState(stringResource(R.string.player_no_games)) }
        }
        val code = p.leagueCode
        itemsIndexed(p.games, key = { _, it -> it.eventId }) { i, g ->
            PlayerGameRow(g, segmentPosition(i, p.games.size), onClick = code?.let { { onMatch(it, g.eventId) } })
        }
    }
}

/** En-tête : dégradé aux couleurs du club, numéro, nom, poste, club et identité. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerHero(p: PlayerProfile, onTeam: (Team) -> Unit) {
    val clubColor = parseHex(p.team?.color) ?: MaterialTheme.colorScheme.primary
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(clubColor.copy(alpha = 0.35f), Color.Transparent)))) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JerseyBadge(p.jersey)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p.position?.let {
                            Text(stringResource(positionLabel(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                p.team?.let { team ->
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTeam(team) }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamLogo(team.logo, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(team.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.club))
                    }
                }
                val facts = listOfNotNull(
                    p.age?.let { age ->
                        val born = p.birthEpochDay?.let { formatBirth(LocalDate.ofEpochDay(it)) }
                        born?.let { stringResource(R.string.player_age_born, age, it) } ?: stringResource(R.string.player_age, age)
                    },
                    p.heightCm?.let { stringResource(R.string.player_height, it) },
                    p.weightKg?.let { stringResource(R.string.player_weight, it) },
                )
                if (facts.isNotEmpty() || p.nationality != null) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.nationality?.let { FactPill(localizedCountry(it), flag = p.flag) }
                        facts.forEach { FactPill(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun JerseyBadge(jersey: String?) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(LocalSportColors.current.accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            jersey ?: "–", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
            color = LocalSportColors.current.accent,
        )
    }
}

@Composable
private fun FactPill(text: String, flag: String? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        flag?.let {
            TeamLogo(it, size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SeasonTiles(s: PlayerSeason) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(s.appearances.toString(), stringResource(R.string.club_played), Modifier.weight(1f))
            StatTile(s.goals.toString(), stringResource(R.string.player_goals), Modifier.weight(1f), LocalSportColors.current.accent)
            StatTile(s.assists.toString(), stringResource(R.string.player_assists), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.player_starts_value, s.starts, s.subIns), stringResource(R.string.player_starts), Modifier.weight(1f))
            StatTile("${s.shots} (${s.shotsOnTarget})", stringResource(R.string.player_shots), Modifier.weight(1f))
            StatTile("${s.yellowCards} / ${s.redCards}", stringResource(R.string.player_cards), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerGameRow(g: PlayerGame, position: SegmentPosition, onClick: (() -> Unit)?) {
    Column(Modifier.listSegment(position)) {
        if (position == SegmentPosition.Middle || position == SegmentPosition.Last) SegmentDivider()
        Row(
            Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (g.outcome != null) FormBadge(g.outcome) else Spacer(Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(52.dp)) {
                Text(formatShortDate(g.kickoff), style = MaterialTheme.typography.labelSmall)
                Text(if (g.home) "⌂" else "✈", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TeamLogo(g.opponent.logo, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(g.opponent.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                contributions(g)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = LocalSportColors.current.accent, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("${g.goalsFor ?: "–"}-${g.goalsAgainst ?: "–"}", fontWeight = FontWeight.Bold)
        }
    }
}

/** « ⚽ 2 · 🅰 1 · 🟨 » : ce que le joueur a fait dans le match, rien s'il n'a rien fait. */
internal fun contributions(g: PlayerGame): String? = listOfNotNull(
    g.goals.takeIf { it > 0 }?.let { "⚽ $it" },
    g.assists.takeIf { it > 0 }?.let { "🅰 $it" },
    "🟨".repeat(g.yellowCards.coerceAtMost(2)).ifEmpty { null },
    "🟥".takeIf { g.redCards > 0 },
).joinToString(" · ").ifEmpty { null }

private fun positionLabel(p: PlayerPosition) = when (p) {
    PlayerPosition.GOALKEEPER -> R.string.position_goalkeeper
    PlayerPosition.DEFENDER -> R.string.position_defender
    PlayerPosition.MIDFIELDER -> R.string.position_midfielder
    PlayerPosition.FORWARD -> R.string.position_forward
}

/** ESPN donne le pays en anglais (« Germany ») : on le traduit quand Java le connaît. */
internal fun localizedCountry(english: String, locale: Locale = Locale.getDefault()): String =
    Locale.getISOCountries().asSequence().map { Locale("", it) }
        .firstOrNull { it.getDisplayCountry(Locale.ENGLISH).equals(english, ignoreCase = true) }
        ?.getDisplayCountry(locale) ?: english

private fun formatBirth(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

// --- Aperçus (Android Studio) ---

private val previewPlayer = PlayerProfile(
    id = "271137", name = "Lassine Sinayoko", jersey = "9", position = PlayerPosition.FORWARD, age = 26,
    birthEpochDay = LocalDate.of(1999, 12, 8).toEpochDay(), nationality = "Mali", flag = null,
    heightCm = 185, weightKg = 81,
    team = Team("6851", "Paris FC", "Paris FC", "PAR", logo = null, color = "1B3F8B"),
    leagueCode = "fra.1", leagueName = "French Ligue 1",
    season = PlayerSeason(starts = 9, subIns = 0, goals = 8, assists = 3, shots = 21, shotsOnTarget = 12, yellowCards = 1, redCards = 0),
    games = listOf(
        PlayerGame("1", 1_793_550_000_000L, Team("174", "AS Monaco", "Monaco", "MON", null), true, 3, 1, MatchOutcome.WIN, 2, 1, 0, 0),
        PlayerGame("2", 1_792_950_000_000L, Team("160", "Paris Saint-Germain", "PSG", "PSG", null), false, 0, 2, MatchOutcome.LOSS, 0, 0, 1, 0),
    ),
)

@Preview(showBackground = true)
@Composable
private fun PlayerContentPreview() {
    LeagueHubTheme { PlayerScreen(PlayerUiState.Content(previewPlayer), onRetry = {}, onTeam = { _, _ -> }, onMatch = { _, _ -> }) }
}

@Preview(showBackground = true)
@Composable
private fun PlayerErrorPreview() {
    LeagueHubTheme { PlayerScreen(PlayerUiState.Error, onRetry = {}, onTeam = { _, _ -> }, onMatch = { _, _ -> }) }
}
