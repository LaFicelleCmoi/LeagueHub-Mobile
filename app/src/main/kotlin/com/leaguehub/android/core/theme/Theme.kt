package com.leaguehub.android.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.DeviceFontFamilyName
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

/** Vert citron « jour de match » : l'accent de toute l'app. */
private val Lime = Color(0xFFC6F432)
private val LimeDeep = Color(0xFF4D7C0F) // version lisible sur fond clair

// Palette M3 complète : les cartes utilisent surfaceContainer, un cran au-dessus du fond.
private val LightColors = lightColorScheme(
    primary = LimeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F7B5),
    onPrimaryContainer = Color(0xFF2D4A06),
    secondary = Color(0xFF0F766E),
    secondaryContainer = Color(0xFFE4F7B5),
    onSecondaryContainer = Color(0xFF2D4A06),
    background = Color(0xFFF4F5F0),
    onBackground = Color(0xFF111312),
    surface = Color(0xFFF4F5F0),
    onSurface = Color(0xFF111312),
    surfaceVariant = Color(0xFFE9EBE3),
    onSurfaceVariant = Color(0xFF5B6157),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBF7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDEFE7),
    surfaceContainerHighest = Color(0xFFE2E5DA),
    outline = Color(0xFF9AA093),
    outlineVariant = Color(0xFFE2E5DA),
)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF0A0B0F),
    primaryContainer = Color(0xFF2C3A10),
    onPrimaryContainer = Color(0xFFE4FBA0),
    secondary = Color(0xFF2DD4BF),
    secondaryContainer = Color(0xFF2C3A10),
    onSecondaryContainer = Color(0xFFE4FBA0),
    background = Color(0xFF0A0B0F),
    onBackground = Color(0xFFF1F2F4),
    surface = Color(0xFF0A0B0F),
    onSurface = Color(0xFFF1F2F4),
    surfaceVariant = Color(0xFF1C1E26),
    onSurfaceVariant = Color(0xFF9097A3),
    surfaceContainerLowest = Color(0xFF050608),
    surfaceContainerLow = Color(0xFF101117),
    surfaceContainer = Color(0xFF14161D),
    surfaceContainerHigh = Color(0xFF1C1E27),
    surfaceContainerHighest = Color(0xFF252833),
    outline = Color(0xFF4A4F5C),
    // Bordure des cartes : visible sur le fond quasi noir sans être appuyée.
    outlineVariant = Color(0xFF23262F),
)

/**
 * Police condensée du système (Roboto Condensed sur Android), pour les scores, heures et titres :
 * le côté « tableau d'affichage » des apps de sport, sans embarquer de fichier de police.
 */
val Condensed: FontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Black),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold, FontStyle.Italic),
)

/** Typographie : titres plus gras et plus serrés, corps inchangé. */
private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Black),
        displayMedium = displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Black),
        displaySmall = displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Black),
        headlineLarge = headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Black, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp),
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
    accent = Color(0xFF4D7C0F), // vert citron foncé : la version vive serait illisible sur fond clair
)

private val DarkSport = LightSport.copy(
    positive = Color(0xFF34D399), negative = Color(0xFFF87171), soon = Color(0xFFFBBF24), imminent = Color(0xFFF87171),
    accent = Color(0xFFC6F432),
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
