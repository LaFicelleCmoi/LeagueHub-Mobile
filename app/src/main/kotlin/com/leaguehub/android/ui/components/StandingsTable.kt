package com.leaguehub.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.util.formatGoalDiff

/** Ligne de classement générique, partagée par les 5 championnats et la LDC. */
data class TableLine(
    val teamId: String,
    val rank: String,
    val name: String,
    val logo: String?,
    val played: Int,
    val goalDiff: Int,
    val points: Int,
    val zoneColor: Color?,
    /** Mouvement par rapport au classement officiel (> 0 : monte). */
    val movement: Int = 0,
    val live: Boolean = false,
    /** Adversaire du match en cours : son logo remplace le simple point rouge. */
    val liveOpponent: LiveOpponent? = null,
)

/** Match en cours vu du classement : logo adverse et score de notre point de vue (« 2-1 »). */
data class LiveOpponent(val logo: String?, val score: String)

@Composable
fun StandingsHeader(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.col_club), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HeaderCell(stringResource(R.string.col_played))
        HeaderCell(stringResource(R.string.col_gd))
        HeaderCell(stringResource(R.string.col_points))
    }
}

@Composable
private fun HeaderCell(text: String) {
    Text(text, Modifier.width(36.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StandingLine(line: TableLine, favoriteId: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sport = LocalSportColors.current
    val favorite = line.teamId == favoriteId
    Row(
        modifier.fillMaxWidth()
            .testTag("standing-${line.teamId}")
            .background(if (favorite) sport.favorite.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(4.dp).height(24.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(line.zoneColor ?: Color.Transparent),
        )
        Spacer(Modifier.width(8.dp))
        Row(Modifier.width(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(line.rank, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            when {
                line.movement > 0 -> Icon(Icons.Default.ArrowDropUp, stringResource(R.string.moves_up), tint = sport.positive, modifier = Modifier.size(16.dp))
                line.movement < 0 -> Icon(Icons.Default.ArrowDropDown, stringResource(R.string.moves_down), tint = sport.negative, modifier = Modifier.size(16.dp))
            }
        }
        TeamLogo(line.logo, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                fontWeight = if (favorite) FontWeight.Bold else FontWeight.Medium,
            )
            if (favorite) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Star, stringResource(R.string.favorite_club), tint = sport.favorite, modifier = Modifier.size(14.dp))
            }
            if (line.live) {
                Spacer(Modifier.width(6.dp))
                val opp = line.liveOpponent
                if (opp == null) {
                    LiveDot()
                } else {
                    // Pastille « 2-1 [logo] » rouge : on voit contre qui et où en est le match.
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(sport.live.copy(alpha = 0.14f))
                            .padding(start = 6.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(opp.score, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = sport.live)
                        Spacer(Modifier.width(4.dp))
                        TeamLogo(opp.logo, size = 16.dp)
                    }
                }
            }
        }
        Cell(line.played.toString())
        Cell(
            formatGoalDiff(line.goalDiff),
            color = when {
                line.goalDiff > 0 -> sport.positive
                line.goalDiff < 0 -> sport.negative
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Cell(line.points.toString(), bold = true)
    }
}

@Composable
private fun Cell(text: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Text(
        text, Modifier.width(36.dp), textAlign = TextAlign.End, color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodyMedium,
    )
}
