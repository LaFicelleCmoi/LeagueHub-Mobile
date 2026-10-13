package com.leaguehub.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.urgencyColor
import com.leaguehub.android.core.util.formatDateTime
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.MatchOutcome
import com.leaguehub.android.domain.schedule.countdownParts
import com.leaguehub.android.domain.schedule.countdownUrgency
import com.leaguehub.android.domain.standings.FormResult
import kotlinx.coroutines.delay

@Composable
fun TeamLogo(url: String?, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    if (url == null) {
        Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, message: String = stringResource(R.string.error_generic)) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Données servies depuis la base locale (réseau indisponible). */
@Composable
fun OfflineBanner(updatedAt: Long, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.offline_since, formatDateTime(updatedAt)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Carte standard de l'app : fine bordure + légère élévation pour se détacher du fond. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content,
    )
}

/** Filtre de compétition : logo en tête, fond teinté de la couleur de la ligue quand il est actif. */
@Composable
fun LeagueChip(league: LeagueConfig, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Color(league.accent)
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(league.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = { TeamLogo(league.logo(), size = 18.dp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = accent.copy(alpha = 0.6f),
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(LocalSportColors.current.live))
}

/**
 * Pastille V/N/D, volontairement discrète : la couleur est mélangée à la surface
 * (vert pastel, gris neutre…) pour ne pas voler la vedette au nom du club.
 */
@Composable
fun FormBadge(outcome: MatchOutcome, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val sport = LocalSportColors.current
    val (label, color) = when (outcome) {
        MatchOutcome.WIN -> stringResource(R.string.form_w) to sport.win
        MatchOutcome.DRAW -> stringResource(R.string.form_d) to sport.draw
        MatchOutcome.LOSS -> stringResource(R.string.form_l) to sport.loss
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(5.dp)).background(lerp(color, MaterialTheme.colorScheme.surface, 0.55f)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

fun FormResult.toOutcome(): MatchOutcome = when (this) {
    FormResult.W -> MatchOutcome.WIN
    FormResult.D -> MatchOutcome.DRAW
    FormResult.L -> MatchOutcome.LOSS
}

/** Heure courante, rafraîchie toutes les [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Compte à rebours avant un coup d'envoi : ambre à 24 h, rouge dans la dernière heure. */
@Composable
fun Countdown(kickoff: Long, modifier: Modifier = Modifier) {
    val now = rememberNow(15_000)
    val remaining = kickoff - now
    val parts = countdownParts(remaining)
    val text = when {
        remaining <= 0 -> stringResource(R.string.kickoff_now)
        parts.days > 0 -> stringResource(R.string.countdown_days, parts.days, parts.hours)
        parts.hours > 0 -> stringResource(R.string.countdown_hours, parts.hours, parts.minutes)
        else -> stringResource(R.string.countdown_minutes, parts.minutes)
    }
    Text(
        text,
        modifier = modifier,
        color = urgencyColor(countdownUrgency(remaining)),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
