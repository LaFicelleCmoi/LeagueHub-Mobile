package com.leaguehub.android.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.leaguehub.android.domain.model.ZoneTone
import com.leaguehub.android.domain.repository.ThemeMode
import com.leaguehub.android.domain.schedule.CountdownUrgency
import com.leaguehub.android.domain.standings.PhaseZone

// Couleurs partagées par toute l'app (portage de `standings-style.ts`).

private val Brand = Color(0xFF2563EB)

// Palette M3 complète : les cartes utilisent surfaceContainer, un cran au-dessus du fond.
private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF3F5F9),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF526073),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F7),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF2DD4BF),
    secondaryContainer = Color(0xFF1F3354),
    onSecondaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE5E9F0),
    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFE5E9F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF070B16),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = Color(0xFF131C2E),
    surfaceContainerHigh = Color(0xFF1A2438),
    surfaceContainerHighest = Color(0xFF222D44),
    outline = Color(0xFF475569),
    // Bordure des cartes : visible sur le fond sombre sans être appuyée.
    outlineVariant = Color(0xFF243049),
)

/** Typographie : titres plus gras et plus serrés, corps inchangé. */
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Formes : cartes à 16 dp, chips à 10 dp. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class SportColors(
    val win: Color,
    val draw: Color,
    val loss: Color,
    val live: Color,
    val favorite: Color,
    val soon: Color,
    val imminent: Color,
    val positive: Color,
    val negative: Color,
    /** Accent « jaune » pour mettre en avant une heure de coup d'envoi. */
    val accent: Color,
)

private val LightSport = SportColors(
    win = Color(0xFF16A34A), draw = Color(0xFF94A3B8), loss = Color(0xFFDC2626), live = Color(0xFFDC2626),
    favorite = Color(0xFFEAB308), soon = Color(0xFFD97706), imminent = Color(0xFFDC2626),
    positive = Color(0xFF059669), negative = Color(0xFFDC2626),
    accent = Color(0xFFB45309), // ambre foncé : un jaune pur serait illisible sur fond clair
)

private val DarkSport = LightSport.copy(
    positive = Color(0xFF34D399), negative = Color(0xFFF87171), soon = Color(0xFFFBBF24), imminent = Color(0xFFF87171),
    accent = Color(0xFFFACC15),
)

val LocalSportColors = staticCompositionLocalOf { LightSport }

fun zoneColor(tone: ZoneTone): Color = when (tone) {
    ZoneTone.UCL -> Color(0xFF2563EB)
    ZoneTone.UEL -> Color(0xFFF97316)
    ZoneTone.UECL -> Color(0xFF10B981)
    ZoneTone.PLAYOFF -> Color(0xFFFBBF24)
    ZoneTone.RELEGATION -> Color(0xFFDC2626)
    ZoneTone.OTHER -> Color(0xFF94A3B8)
}

fun phaseZoneColor(zone: PhaseZone?): Color = when (zone) {
    PhaseZone.Q -> Color(0xFF16A34A)
    PhaseZone.PO -> Color(0xFF2563EB)
    PhaseZone.OUT, null -> Color(0xFF94A3B8)
}

@Composable
fun urgencyColor(urgency: CountdownUrgency): Color = when (urgency) {
    CountdownUrgency.IMMINENT -> LocalSportColors.current.imminent
    CountdownUrgency.SOON -> LocalSportColors.current.soon
    CountdownUrgency.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun parseHex(hex: String?): Color? =
    hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** Couleur du club utilisable comme couleur primaire : ni trop claire ni trop sombre pour le thème. */
private fun clubPrimary(color: Color?, dark: Boolean): Color? {
    color ?: return null
    val l = color.luminance()
    return if ((dark && l < 0.08f) || (!dark && l > 0.75f)) null else color
}

private fun ColorScheme.withPrimary(primary: Color?): ColorScheme {
    primary ?: return this
    val on = if (primary.luminance() > 0.5f) Color.Black else Color.White
    return copy(primary = primary, onPrimary = on)
}

@Composable
fun LeagueHubTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    clubColor: String? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = (if (dark) DarkColors else LightColors).withPrimary(clubPrimary(parseHex(clubColor), dark))
    androidx.compose.runtime.CompositionLocalProvider(LocalSportColors provides if (dark) DarkSport else LightSport) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
