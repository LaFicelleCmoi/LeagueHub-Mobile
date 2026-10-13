package com.leaguehub.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Position d'une ligne dans une liste « en carte » : chaque élément d'une LazyColumn
 * dessine sa part de la carte, ce qui garde la liste paresseuse (pas une seule grosse carte).
 */
enum class SegmentPosition { Single, First, Middle, Last }

fun segmentPosition(index: Int, count: Int): SegmentPosition = when {
    count <= 1 -> SegmentPosition.Single
    index == 0 -> SegmentPosition.First
    index == count - 1 -> SegmentPosition.Last
    else -> SegmentPosition.Middle
}

private val SegmentRadius = 16.dp

/**
 * Fond et bordure de carte pour une ligne de liste : coins arrondis en haut pour la première,
 * en bas pour la dernière, côtés seuls au milieu. Même rendu qu'[AppCard].
 */
@Composable
fun Modifier.listSegment(position: SegmentPosition): Modifier {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val border = MaterialTheme.colorScheme.outlineVariant
    val top = position == SegmentPosition.First || position == SegmentPosition.Single
    val bottom = position == SegmentPosition.Last || position == SegmentPosition.Single
    val shape = RoundedCornerShape(
        topStart = if (top) SegmentRadius else 0.dp, topEnd = if (top) SegmentRadius else 0.dp,
        bottomStart = if (bottom) SegmentRadius else 0.dp, bottomEnd = if (bottom) SegmentRadius else 0.dp,
    )
    return this
        .padding(horizontal = 12.dp)
        .clip(shape) // l'effet de pression reste dans la carte
        .drawBehind {
            val r = SegmentRadius.toPx()
            val stroke = 1.dp.toPx()
            // On dessine un rectangle arrondi qui déborde là où la carte continue, puis on rogne.
            val y0 = if (top) 0f else -2 * r
            val y1 = if (bottom) size.height else size.height + 2 * r
            clipRect {
                drawRoundRect(container, Offset(0f, y0), Size(size.width, y1 - y0), CornerRadius(r))
                drawRoundRect(
                    border, Offset(stroke / 2, y0 + stroke / 2), Size(size.width - stroke, y1 - y0 - stroke),
                    CornerRadius(r), style = Stroke(stroke),
                )
            }
        }
}

/** Séparateur interne d'une liste en carte (à placer en tête des lignes non premières). */
@Composable
fun SegmentDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Petit intitulé au-dessus d'un groupe (jour, mois, zone…). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Tuile de chiffre clé : grand nombre, libellé discret dessous. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
