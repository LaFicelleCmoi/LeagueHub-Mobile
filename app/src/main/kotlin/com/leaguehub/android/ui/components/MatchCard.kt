package com.leaguehub.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.Condensed
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchEventKind
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side

/** Libellé du statut : heure du coup d'envoi, minute de jeu, « Terminé »… */
@Composable
fun statusLabel(statusName: String, state: MatchState, kickoff: Long, clock: String): String = when (statusName) {
    "STATUS_HALFTIME" -> stringResource(R.string.status_halftime)
    "STATUS_FULL_TIME", "STATUS_FINAL" -> stringResource(R.string.status_final)
    "STATUS_FINAL_AET" -> stringResource(R.string.status_final_aet)
    "STATUS_FINAL_PEN" -> stringResource(R.string.status_final_pen)
    "STATUS_POSTPONED" -> stringResource(R.string.status_postponed)
    "STATUS_CANCELED" -> stringResource(R.string.status_canceled)
    "STATUS_ABANDONED" -> stringResource(R.string.status_abandoned)
    "STATUS_SUSPENDED" -> stringResource(R.string.status_suspended)
    "STATUS_DELAYED" -> stringResource(R.string.status_delayed)
    else -> when (state) {
        MatchState.PRE -> formatTime(kickoff)
        MatchState.IN -> clock.ifEmpty { stringResource(R.string.live) }
        MatchState.POST -> stringResource(R.string.status_final)
    }
}

@Composable
fun statusLabel(match: Match): String = statusLabel(match.statusName, match.state, match.kickoff, match.clock)

@Composable
fun MatchCard(
    match: Match,
    modifier: Modifier = Modifier,
    favoriteId: String? = null,
    showCountdown: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.state == MatchState.IN) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    statusLabel(match),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (match.state == MatchState.IN) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (match.state == MatchState.IN) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.weight(1f))
                if (showCountdown && match.state == MatchState.PRE) Countdown(match.kickoff)
            }
            Spacer(Modifier.size(6.dp))
            SideRow(match.home, favoriteId, match.state)
            Spacer(Modifier.size(4.dp))
            SideRow(match.away, favoriteId, match.state)

            val notable = match.events.filter { it.kind != MatchEventKind.YELLOW_CARD }
            if (notable.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EventColumn(notable.filter { it.side == Side.HOME }.map { eventText(it.kind, it.player, it.minute) }, TextAlign.Start)
                    EventColumn(notable.filter { it.side == Side.AWAY }.map { eventText(it.kind, it.player, it.minute) }, TextAlign.End)
                }
            }
        }
    }
}

private fun eventText(kind: MatchEventKind, player: String, minute: String): String {
    val icon = when (kind) {
        MatchEventKind.GOAL -> "⚽"
        MatchEventKind.PENALTY -> "⚽ (p)"
        MatchEventKind.OWN_GOAL -> "⚽ (csc)"
        MatchEventKind.RED_CARD -> "🟥"
        MatchEventKind.YELLOW_CARD -> "🟨"
    }
    return "$icon $player $minute"
}

@Composable
private fun EventColumn(lines: List<String>, align: TextAlign) {
    Column(Modifier.widthIn(max = 170.dp)) {
        lines.forEach {
            Text(
                it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SideRow(side: MatchSide, favoriteId: String?, state: MatchState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(side.team.logo, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            side.team.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (side.winner) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (side.team.id == favoriteId) {
            Icon(Icons.Default.Star, contentDescription = null, tint = LocalSportColors.current.favorite, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        if (side.score != null) {
            Text(
                side.score.toString() + (side.shootout?.let { " ($it)" } ?: ""),
                fontWeight = FontWeight.Bold,
                fontFamily = Condensed,
                fontSize = 18.sp,
                color = if (state == MatchState.IN) LocalSportColors.current.live else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Version compacte d'un match pour les listes denses (accueil) : les deux équipes
 * (logo à gauche, nom au centre, score éventuel) et, à droite, l'heure ou le statut.
 */
@Composable
fun CompactMatchRow(
    match: Match,
    modifier: Modifier = Modifier,
    favoriteId: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val live = match.state == MatchState.IN
    Row(
        modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SideRow(match.home, favoriteId, match.state)
            SideRow(match.away, favoriteId, match.state)
        }
        Spacer(Modifier.width(16.dp))
        // Colonne d'heure à largeur fixe : toutes les heures restent alignées d'une ligne à l'autre.
        Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) { LiveDot(); Spacer(Modifier.width(4.dp)) }
                Text(
                    statusLabel(match),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = Condensed,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        live -> LocalSportColors.current.live
                        match.state == MatchState.PRE -> LocalSportColors.current.accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}
