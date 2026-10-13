package com.leaguehub.android.ui.league

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.zoneColor
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.standings.Objective
import com.leaguehub.android.domain.standings.ObjectiveKind
import com.leaguehub.android.domain.standings.ObjectiveStatus
import com.leaguehub.android.domain.standings.PositionHistory
import com.leaguehub.android.domain.standings.objectives
import com.leaguehub.android.domain.standings.positionHistory
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.ui.components.ErrorState
import com.leaguehub.android.ui.components.LoadingState
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.TeamLogo
import kotlin.math.roundToInt

/** Couleurs des courbes (palette catégorielle validée), une par club, dans un ordre fixe. */
private val SeriesLight = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
private val SeriesDark = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
private const val MAX_SERIES = 5

/** Onglet Saison : « Qu'est-ce qu'il faut ? » puis l'évolution au classement des clubs choisis. */
@Composable
fun SeasonTab(state: LeagueUiState, viewModel: LeagueViewModel) {
    LaunchedEffect(Unit) { viewModel.loadSeason() }
    val season by viewModel.season.collectAsStateWithLifecycle()
    // Classement en direct s'il y a des matchs en cours, sinon officiel.
    val rows = state.live?.rows?.map { it.row } ?: state.standings?.data?.rows.orEmpty()
    if (rows.isEmpty()) {
        if (!state.loaded) LoadingState() else ErrorState(onRetry = viewModel::refresh)
        return
    }
    val favoriteInLeague = state.favoriteId?.takeIf { id -> rows.any { it.team.id == id } }

    // Sélection « id:emplacement » : la couleur reste attachée au club quand on en retire un autre.
    var selection by rememberSaveable { mutableStateOf("") }
    if (selection.isEmpty()) {
        val defaults = listOfNotNull(favoriteInLeague, rows.first().team.id).distinct()
        selection = defaults.mapIndexed { i, id -> "$id:$i" }.joinToString(",")
    }
    val known = rows.map { it.team.id }.toSet()
    val slots: Map<String, Int> = selection.split(",").filter { it.contains(':') }
        .associate { it.substringBefore(':') to (it.substringAfter(':').toIntOrNull() ?: 0) }
        .filterKeys { it in known }
        .ifEmpty { mapOf(rows.first().team.id to 0) }
    fun toggle(id: String) {
        val next = slots.toMutableMap()
        if (id in next) {
            if (next.size > 1) next.remove(id)
        } else if (next.size < MAX_SERIES) {
            next[id] = (0 until MAX_SERIES).first { it !in next.values }
        }
        selection = next.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
    // Suit le thème choisi dans l'app (pas seulement celui du système).
    val palette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) SeriesDark else SeriesLight
    val focus = favoriteInLeague ?: slots.keys.first()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                stringResource(R.string.season_pick),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
            )
            ClubPicker(rows, slots, palette, ::toggle)
        }
        item {
            val team = rows.first { it.team.id == focus }
            SectionTitle(stringResource(R.string.objectives_title, team.team.shortName))
            ObjectivesCard(rows, team)
        }
        item { SectionTitle(stringResource(R.string.season_chart)) }
        item {
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                when (val s = season) {
                    Remote.Loading -> Box(Modifier.fillMaxWidth().height(280.dp)) { LoadingState() }
                    Remote.Failed -> Box(Modifier.fillMaxWidth().height(280.dp)) { ErrorState(onRetry = { viewModel.loadSeason(force = true) }) }
                    is Remote.Ready -> {
                        val history = remember(s.value, rows.size) { positionHistory(s.value.data, rows.map { it.team.id }) }
                        if (history.matchdays == 0) {
                            Text(stringResource(R.string.season_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            PositionChart(history, rows, slots, palette)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.season_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Choix des clubs : la puce d'un club suivi prend la couleur de sa courbe (c'est aussi la légende). */
@Composable
private fun ClubPicker(rows: List<StandingRow>, slots: Map<String, Int>, palette: List<Color>, onToggle: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(rows, key = { it.team.id }) { r ->
            val slot = slots[r.team.id]
            val color = slot?.let { palette[it] }
            FilterChip(
                selected = slot != null,
                onClick = { onToggle(r.team.id) },
                enabled = slot != null || slots.size < MAX_SERIES,
                label = { Text(r.team.shortName, maxLines = 1) },
                leadingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (color != null) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                        }
                        TeamLogo(r.team.logo, size = 18.dp)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (color ?: Color.Transparent).copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = slot != null,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = color ?: MaterialTheme.colorScheme.outline,
                    selectedBorderWidth = 1.5.dp,
                ),
            )
        }
    }
}

/** Une ligne par objectif : statut (icône + texte, jamais la couleur seule), points nécessaires, écart. */
@Composable
private fun ObjectivesCard(rows: List<StandingRow>, team: StandingRow) {
    val list = remember(rows, team.team.id) { objectives(rows, team.team.id) }
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        list.forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ObjectiveRow(o)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val remaining = list.firstOrNull()?.pointsAvailable?.div(3) ?: 0
        Text(
            stringResource(R.string.objectives_note, remaining),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ObjectiveRow(o: Objective) {
    val sport = LocalSportColors.current
    val (icon, tint) = when (o.status) {
        ObjectiveStatus.SECURED -> Icons.Default.CheckCircle to sport.positive
        ObjectiveStatus.IN_HAND -> Icons.Default.Flag to sport.accent
        ObjectiveStatus.DEPENDS_ON_OTHERS -> Icons.Default.HourglassTop to sport.soon
        ObjectiveStatus.IMPOSSIBLE -> Icons.Default.Block to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(objectiveLabel(o.kind)), style = MaterialTheme.typography.titleSmall)
            Text(
                when (o.status) {
                    ObjectiveStatus.SECURED -> stringResource(R.string.obj_secured)
                    ObjectiveStatus.IN_HAND -> stringResource(R.string.obj_needed, o.pointsNeeded ?: 0, o.pointsAvailable)
                    ObjectiveStatus.DEPENDS_ON_OTHERS -> stringResource(R.string.obj_depends)
                    ObjectiveStatus.IMPOSSIBLE -> stringResource(R.string.obj_impossible)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when {
                    o.gap > 0 -> stringResource(R.string.obj_behind, o.gap, o.position)
                    o.gap < 0 -> stringResource(R.string.obj_ahead, -o.gap, o.position)
                    else -> stringResource(R.string.obj_level, o.position)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Le chiffre clé en grand : points à prendre.
        if (o.status == ObjectiveStatus.IN_HAND) {
            Spacer(Modifier.width(12.dp))
            Text("${o.pointsNeeded}", style = MaterialTheme.typography.headlineMedium, color = sport.accent)
        }
    }
}

private fun objectiveLabel(kind: ObjectiveKind) = when (kind) {
    ObjectiveKind.TITLE -> R.string.obj_title
    ObjectiveKind.UCL -> R.string.obj_ucl
    ObjectiveKind.EUROPE -> R.string.obj_europe
    ObjectiveKind.SAFETY -> R.string.obj_safety
}

/**
 * Courbes de position (1 en haut), une par club choisi, sur fond de zones (LDC, relégation…).
 * Toucher ou glisser sélectionne une journée : trait vertical et relevé texte des positions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionChart(history: PositionHistory, rows: List<StandingRow>, slots: Map<String, Int>, palette: List<Color>) {
    val n = rows.size
    val days = history.matchdays
    var picked by remember(days) { mutableIntStateOf(days) }
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    val endStyle = TextStyle(fontSize = 11.sp, color = ink, fontWeight = FontWeight.Bold)
    val teams = rows.associateBy { it.team.id }
    val series = slots.entries.sortedBy { it.value }.mapNotNull { (id, slot) ->
        history.positions[id]?.let { Triple(id, palette[slot], it) }
    }

    // Relevé de la journée choisie (tient lieu d'infobulle et de vue tableau).
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.season_matchday, picked), style = MaterialTheme.typography.labelLarge)
        series.forEach { (id, color, pos) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${teams[id]?.team?.abbreviation} #${pos[picked - 1]}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    val padLeft = 28.dp
    val padRight = 44.dp
    val padTop = 12.dp
    val padBottom = 24.dp
    val dayTicks = (listOf(1) + (5..days step 5) + days).distinct()
    val dayLabels = dayTicks.map { stringResource(R.string.season_matchday, it) }
    val description = series.joinToString { (id, _, pos) -> "${teams[id]?.team?.name}: ${pos.last()}" }
    Canvas(
        Modifier.fillMaxWidth().height(280.dp)
            .semantics { contentDescription = description }
            .pointerInput(days) {
                detectTapGestures { o -> picked = xToDay(o.x, size.width.toFloat(), padLeft.toPx(), padRight.toPx(), days) }
            }
            .pointerInput(days) {
                detectHorizontalDragGestures { change, _ ->
                    picked = xToDay(change.position.x, size.width.toFloat(), padLeft.toPx(), padRight.toPx(), days)
                }
            },
    ) {
        val left = padLeft.toPx()
        val right = size.width - padRight.toPx()
        val top = padTop.toPx()
        val bottom = size.height - padBottom.toPx()
        fun x(day: Int) = if (days <= 1) (left + right) / 2 else left + (right - left) * (day - 1) / (days - 1)
        fun y(pos: Float) = top + (bottom - top) * (pos - 1) / (n - 1).coerceAtLeast(1)

        // Zones en fond très léger, par bandes de rangs consécutifs de même zone.
        rows.forEach { r ->
            val tone = r.zone?.tone ?: return@forEach
            drawRect(
                zoneColor(tone).copy(alpha = 0.10f),
                topLeft = Offset(left, y(r.rank - 0.5f).coerceAtLeast(top)),
                size = Size(right - left, (y(r.rank + 0.5f).coerceAtMost(bottom) - y(r.rank - 0.5f).coerceAtLeast(top))),
            )
        }
        // Grille discrète + rangs 1, 5, 10…
        val ticks = (listOf(1) + (5..n step 5) + n).distinct()
        ticks.forEach { p ->
            drawLine(grid, Offset(left, y(p.toFloat())), Offset(right, y(p.toFloat())), strokeWidth = 1f)
            val t = measurer.measure(p.toString(), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y(p.toFloat()) - t.size.height / 2))
        }
        // Journées : J1, toutes les 5, la dernière.
        dayTicks.forEachIndexed { i, d ->
            val t = measurer.measure(dayLabels[i], labelStyle)
            drawText(t, topLeft = Offset(x(d) - t.size.width / 2, bottom + 6.dp.toPx()))
        }
        // Journée choisie.
        drawLine(muted.copy(alpha = 0.6f), Offset(x(picked), top), Offset(x(picked), bottom), strokeWidth = 1.dp.toPx())

        // Courbes : trait de 2 dp, jonctions arrondies.
        series.forEach { (_, color, pos) ->
            val path = Path()
            pos.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(1), y(p.toFloat())) else path.lineTo(x(i + 1), y(p.toFloat()))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Repères sur la journée choisie : point de 8 dp entouré d'un anneau de la couleur du fond.
        series.forEach { (_, color, pos) ->
            val c = Offset(x(picked), y(pos[picked - 1].toFloat()))
            drawCircle(surface, radius = 6.dp.toPx(), center = c)
            drawCircle(color, radius = 4.dp.toPx(), center = c)
        }
        // Étiquettes directes en bout de courbe (texte neutre, la pastille porte la couleur), écartées si elles se chevauchent.
        var lastY = Float.NEGATIVE_INFINITY
        series.map { (id, color, pos) -> Triple(id, color, y(pos.last().toFloat())) }.sortedBy { it.third }.forEach { (id, color, yy) ->
            val t = measurer.measure(teams[id]?.team?.abbreviation.orEmpty(), endStyle)
            val ly = maxOf(yy, lastY + t.size.height)
            lastY = ly
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(right + 8.dp.toPx(), ly))
            drawText(t, topLeft = Offset(right + 14.dp.toPx(), ly - t.size.height / 2))
        }
    }
}

private fun xToDay(x: Float, width: Float, padLeft: Float, padRight: Float, days: Int): Int {
    if (days <= 1) return 1
    val f = ((x - padLeft) / (width - padLeft - padRight)).coerceIn(0f, 1f)
    return (1 + f * (days - 1)).roundToInt()
}
