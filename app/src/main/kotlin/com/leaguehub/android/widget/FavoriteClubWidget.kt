package com.leaguehub.android.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.leaguehub.android.R
import com.leaguehub.android.app.MainActivity
import com.leaguehub.android.app.work.SyncWorker
import com.leaguehub.android.core.util.formatShortDate
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.schedule.PARIS
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Ce que le widget affiche, préparé par le SyncWorker : le widget ne fait aucun appel réseau,
 * les logos sont donc téléchargés ici et stockés en petits PNG dans le stockage de l'app.
 */
object WidgetState {
    private const val PREFS = "widget"
    private const val LOGO_PX = 128 // assez net pour 44 dp, assez léger pour les RemoteViews
    /** À incrémenter quand le format change : une donnée d'un autre format déclenche une resynchro. */
    private const val FORMAT = 3

    private fun logoFile(context: Context, name: String) = File(File(context.filesDir, "widget").apply { mkdirs() }, "$name.png")

    /** Forme récente « VVN… » de la plus ancienne à la plus récente, encodée W/D/L. */
    private fun TeamForm.encodedForm(): String =
        results.reversed().mapNotNull { it.outcome?.name?.first() }.joinToString("")

    suspend fun save(context: Context, favorite: FavoriteClub, form: TeamForm, opponentForm: TeamForm?) {
        val match = form.live ?: form.next
        val competition = match?.let { m -> Competitions.ALL.firstOrNull { it.espnCode == m.competitionCode } }

        downloadLogo(context, favorite.logo ?: form.team.logo, "club")
        downloadLogo(context, match?.opponent?.logo, "opponent")
        downloadLogo(context, competition?.logo(dark = true), "competition")

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            clear()
            putInt("format", FORMAT)
            putString("club", favorite.name)
            putString("clubForm", form.encodedForm())
            if (match != null) {
                putBoolean("hasMatch", true)
                putString("opponent", match.opponent.name)
                putString("opponentShort", match.opponent.shortName)
                putString("opponentForm", opponentForm?.encodedForm().orEmpty())
                putBoolean("home", match.home)
                putString("competition", match.competition)
                putString("time", formatTime(match.kickoff))
                putString("date", formatShortDate(match.kickoff))
                putLong("kickoff", match.kickoff)
                match.venue?.let { putString("venue", it) }
                putBoolean("live", match == form.live)
                putString("clock", match.clock)
                putInt("goalsFor", match.goalsFor ?: 0)
                putInt("goalsAgainst", match.goalsAgainst ?: 0)
            }
        }.apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putInt("format", FORMAT).apply()
        listOf("club", "opponent", "competition").forEach { logoFile(context, it).delete() }
    }

    /** Données absentes ou d'une ancienne version de l'app : il faut resynchroniser. */
    fun isOutdated(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("format", 0) != FORMAT

    /** Télécharge (via le cache de Coil) et réduit un logo ; supprime l'ancien s'il n'y en a plus. */
    private suspend fun downloadLogo(context: Context, url: String?, name: String) {
        val file = logoFile(context, name)
        if (url == null) { file.delete(); return }
        val request = ImageRequest.Builder(context).data(url).size(LOGO_PX).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return // on garde l'ancien logo
        runCatching {
            file.outputStream().use { result.drawable.toBitmap(LOGO_PX, LOGO_PX).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    data class Snapshot(
        val club: String?,
        val clubForm: String,
        val clubLogo: Bitmap?,
        val hasMatch: Boolean,
        val opponent: String,
        val opponentShort: String,
        val opponentForm: String,
        val opponentLogo: Bitmap?,
        val home: Boolean,
        val competition: String,
        val competitionLogo: Bitmap?,
        val time: String,
        val date: String,
        val kickoff: Long,
        val venue: String?,
        val live: Boolean,
        val clock: String,
        val goalsFor: Int,
        val goalsAgainst: Int,
    )

    fun read(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun logo(name: String) = logoFile(context, name).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        val hasMatch = p.getBoolean("hasMatch", false)
        return Snapshot(
            club = p.getString("club", null),
            clubForm = p.getString("clubForm", "").orEmpty(),
            clubLogo = logo("club"),
            hasMatch = hasMatch,
            opponent = p.getString("opponent", "").orEmpty(),
            opponentShort = p.getString("opponentShort", "").orEmpty(),
            opponentForm = p.getString("opponentForm", "").orEmpty(),
            opponentLogo = if (hasMatch) logo("opponent") else null,
            home = p.getBoolean("home", true),
            competition = p.getString("competition", "").orEmpty(),
            competitionLogo = if (hasMatch) logo("competition") else null,
            time = p.getString("time", "").orEmpty(),
            date = p.getString("date", "").orEmpty(),
            kickoff = p.getLong("kickoff", 0),
            venue = p.getString("venue", null),
            live = p.getBoolean("live", false),
            clock = p.getString("clock", "").orEmpty(),
            goalsFor = p.getInt("goalsFor", 0),
            goalsAgainst = p.getInt("goalsAgainst", 0),
        )
    }
}

// --- Couleurs (jour / nuit) ---

/** Accent jaune de l'app ; ambre foncé le jour pour rester lisible sur fond clair. */
private val Accent = ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFACC15))
private val Live = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
/** Fin liseré autour du widget pour le détacher du fond d'écran. */
private val Border = ColorProvider(day = Color.Black.copy(alpha = 0.08f), night = Color.White.copy(alpha = 0.1f))
/** Texte secondaire : blanc à 80 % la nuit plutôt qu'une couleur primaire peu lisible. */
private val Secondary = ColorProvider(day = Color(0xFF475569), night = Color.White.copy(alpha = 0.8f))

/** Pastilles de forme désaturées (même logique que dans l'app). */
private fun formColors(code: Char): Pair<ColorProvider, ColorProvider> = when (code) {
    'W' -> ColorProvider(Color(0xFFBBF7D0), Color(0xFF1F4D33)) to ColorProvider(Color(0xFF14532D), Color(0xFFDCFCE7))
    'L' -> ColorProvider(Color(0xFFFECACA), Color(0xFF5B2328)) to ColorProvider(Color(0xFF7F1D1D), Color(0xFFFEE2E2))
    else -> ColorProvider(Color(0xFFE2E8F0), Color(0xFF334155)) to ColorProvider(Color(0xFF334155), Color(0xFFE2E8F0))
}

/**
 * Échelle du contenu selon la taille réelle du widget : tout grandit avec lui
 * (1.0 pour un 4×2 standard, jusqu'à 1.6 pour un widget très haut).
 */
private class Metrics(size: DpSize) {
    val scale: Float = (size.height.value / 120f).coerceIn(1f, 1.6f)
        .coerceAtMost(size.width.value / 200f).coerceAtLeast(1f)
    fun dp(v: Float) = (v * scale).dp
    fun sp(v: Float) = (v * scale).sp
    /** Assez de hauteur pour un pied de widget (stade, jour). */
    val footer = size.height >= 150.dp
    /** Assez de largeur pour les 5 pastilles de forme. */
    val fullForm = size.width >= 260.dp
    /** Mise en page choisie selon la forme du widget. */
    val layout: WidgetLayout = when {
        size.width < 130.dp -> WidgetLayout.TINY
        size.height < 100.dp && size.width >= 250.dp -> WidgetLayout.ROW
        size.height < 100.dp -> WidgetLayout.COMPACT
        size.width < 220.dp -> WidgetLayout.SQUARE
        else -> WidgetLayout.DETAILED
    }
    val compact = layout != WidgetLayout.DETAILED
}

/**
 * Formats gérés :
 * - TINY (1×1, 1×2) : logos et heure empilés ;
 * - COMPACT (2×1, 3×1) : une ligne, adversaire et heure ;
 * - ROW (4×1 et plus) : une ligne complète, les deux clubs, la compétition, l'heure et le jour ;
 * - SQUARE (2×2, 2×3) : affiche vertical, logos face à face au centre ;
 * - DETAILED (3×2, 4×2 et plus) : la maquette complète, avec pied si assez haut.
 */
private enum class WidgetLayout { TINY, COMPACT, ROW, SQUARE, DETAILED }

/** Widget d'écran d'accueil : prochain match (ou match en cours) du club favori. */
class FavoriteClubWidget : GlanceAppWidget() {

    // Taille exacte : la mise en page s'adapte en continu au redimensionnement.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Données d'un ancien format (mise à jour de l'app) : on les remplace sans attendre la synchro périodique.
        if (WidgetState.isOutdated(context)) SyncWorker.refreshNow(context)
        val snapshot = WidgetState.read(context)
        provideContent {
            GlanceTheme { Content(context, snapshot) }
        }
    }

    @Composable
    private fun Content(context: Context, s: WidgetState.Snapshot) {
        val m = Metrics(LocalSize.current)
        // Liseré : un fond « bordure » arrondi, puis le fond du widget 1 dp plus petit.
        Box(
            GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(Border).padding(1.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Box(
                GlanceModifier.fillMaxSize().cornerRadius(19.dp).background(GlanceTheme.colors.widgetBackground)
                    .padding(
                        horizontal = if (m.layout == WidgetLayout.TINY) 6.dp else 16.dp,
                        vertical = if (m.compact) 8.dp else 14.dp,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                when {
                    s.club == null -> Message(context.getString(R.string.widget_no_favorite))
                    !s.hasMatch -> NoMatch(context, s, m)
                    else -> when (m.layout) {
                        WidgetLayout.TINY -> Tiny(s)
                        WidgetLayout.COMPACT -> Compact(s)
                        WidgetLayout.ROW -> WideRow(context, s)
                        WidgetLayout.SQUARE -> Square(s)
                        WidgetLayout.DETAILED -> Detailed(context, s, m)
                    }
                }
            }
        }
    }

    /**
     * Mise en page détaillée (4×2 et plus) :
     * ```
     * [LDC] Ligue des champions          21:00
     *                                   13 oct.
     * ─────────────────────────────────────────
     * [logo] Bayern Munich   [V][V][N]
     * [logo] @ Viking FK     [N]
     * ─────────────────────────────────────────   (si assez haut)
     * Allianz Arena                   Aujourd'hui
     * ```
     */
    @Composable
    private fun Detailed(context: Context, s: WidgetState.Snapshot, m: Metrics) {
        Column(GlanceModifier.fillMaxSize()) {
            // En-tête : compétition à gauche, heure (ou minute de jeu) en haut à droite.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    s.competitionLogo?.let {
                        Logo(it, m.dp(18f))
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Text(
                        s.competition,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = m.sp(12f), fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (s.live) {
                        Text(
                            "● " + s.clock.ifEmpty { context.getString(R.string.live) },
                            style = TextStyle(color = Live, fontSize = m.sp(20f), fontWeight = FontWeight.Bold),
                        )
                    } else {
                        Text(s.time, style = TextStyle(color = Accent, fontSize = m.sp(20f), fontWeight = FontWeight.Bold))
                        Text(s.date, style = TextStyle(color = Secondary, fontSize = m.sp(11f)))
                    }
                }
            }
            Divider(top = 8.dp, bottom = 0.dp)
            // Les équipes occupent tout l'espace restant, centrées verticalement : pas de trou.
            Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                val formSize = if (m.fullForm) 5 else 3
                TeamRow(context, m, s.clubLogo, s.club.orEmpty(), s.clubForm.takeLast(formSize), s.goalsFor.takeIf { s.live })
                Spacer(GlanceModifier.height(m.dp(8f)))
                TeamRow(
                    context, m, s.opponentLogo, (if (s.home) "" else "@ ") + s.opponent,
                    s.opponentForm.takeLast(formSize), s.goalsAgainst.takeIf { s.live },
                )
            }
            if (m.footer) {
                Divider(top = 0.dp, bottom = 8.dp)
                Footer(context, s, m)
            }
        }
    }

    /** Pied : stade (ou domicile / extérieur) et jour relatif du match. */
    @Composable
    private fun Footer(context: Context, s: WidgetState.Snapshot, m: Metrics) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.venue ?: context.getString(if (s.home) R.string.widget_home else R.string.widget_away),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = Secondary, fontSize = m.sp(12f)),
                maxLines = 1,
            )
            if (!s.live) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    relativeDay(context, s.kickoff),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = m.sp(12f), fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
    }

    /** « Aujourd'hui », « Demain », « Dans 3 jours » : calculé à l'affichage, jamais périmé d'un jour. */
    private fun relativeDay(context: Context, kickoff: Long): String {
        val days = ChronoUnit.DAYS.between(LocalDate.now(PARIS), Instant.ofEpochMilli(kickoff).atZone(PARIS).toLocalDate())
        return when {
            days <= 0 -> context.getString(R.string.widget_today)
            days == 1L -> context.getString(R.string.widget_tomorrow)
            else -> context.getString(R.string.widget_in_days, days.toInt())
        }
    }

    /** 1×1 : les deux logos et l'heure (ou le score), centrés. */
    @Composable
    private fun Tiny(s: WidgetState.Snapshot) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                s.clubLogo?.let { Logo(it, 22.dp) }
                Spacer(GlanceModifier.width(4.dp))
                s.opponentLogo?.let { Logo(it, 22.dp) }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                if (s.live) "${s.goalsFor}-${s.goalsAgainst}" else s.time,
                style = TextStyle(color = if (s.live) Live else Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }

    /** 4×1 : une ligne complète, compétition à gauche, les deux clubs, heure et jour à droite. */
    @Composable
    private fun WideRow(context: Context, s: WidgetState.Snapshot) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            s.clubLogo?.let { Logo(it, 28.dp); Spacer(GlanceModifier.width(6.dp)) }
            s.opponentLogo?.let { Logo(it, 28.dp); Spacer(GlanceModifier.width(10.dp)) }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    s.club.orEmpty() + (if (s.home) " vs " else " @ ") + s.opponentShort,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    s.competitionLogo?.let { Logo(it, 12.dp); Spacer(GlanceModifier.width(4.dp)) }
                    Text(
                        if (s.live) "${s.goalsFor} – ${s.goalsAgainst}" else s.competition,
                        style = TextStyle(color = if (s.live) Live else Secondary, fontSize = 11.sp, fontWeight = if (s.live) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (s.live) "● " + s.clock else s.time,
                    style = TextStyle(color = if (s.live) Live else Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (!s.live) Text(relativeDay(context, s.kickoff), style = TextStyle(color = Secondary, fontSize = 11.sp), maxLines = 1)
            }
        }
    }

    /** 2×2 : affiche vertical, heure en haut, logos face à face au centre, adversaire et date en bas. */
    @Composable
    private fun Square(s: WidgetState.Snapshot) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                s.competitionLogo?.let { Logo(it, 16.dp) }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    if (s.live) "● " + s.clock else s.time,
                    style = TextStyle(color = if (s.live) Live else Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                s.clubLogo?.let { Logo(it, 36.dp) }
                Text(
                    if (s.live) "  ${s.goalsFor}-${s.goalsAgainst}  " else "  vs  ",
                    style = TextStyle(
                        color = if (s.live) Live else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = if (s.live) 16.sp else 12.sp, fontWeight = FontWeight.Bold,
                    ),
                )
                s.opponentLogo?.let { Logo(it, 36.dp) }
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                (if (s.home) "vs " else "@ ") + s.opponentShort,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (!s.live) Text(s.date, style = TextStyle(color = Secondary, fontSize = 11.sp), maxLines = 1)
        }
    }

    /** Mise en page compacte (2×1) : logos, adversaire, heure à droite. */
    @Composable
    private fun Compact(s: WidgetState.Snapshot) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            s.clubLogo?.let { Logo(it, 24.dp); Spacer(GlanceModifier.width(4.dp)) }
            s.opponentLogo?.let { Logo(it, 24.dp); Spacer(GlanceModifier.width(8.dp)) }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    (if (s.home) "vs " else "@ ") + s.opponentShort,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    if (s.live) "${s.goalsFor} – ${s.goalsAgainst}" else s.date,
                    style = TextStyle(color = Secondary, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            Text(
                if (s.live) s.clock else s.time,
                style = TextStyle(color = if (s.live) Live else Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
    }

    @Composable
    private fun TeamRow(context: Context, m: Metrics, logo: Bitmap?, name: String, form: String, score: Int?) {
        val logoSize = m.dp(28f)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) Logo(logo, logoSize) else Spacer(GlanceModifier.size(logoSize))
            Spacer(GlanceModifier.width(m.dp(10f)))
            Text(
                name,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = m.sp(15f), fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            form.forEach { code ->
                Spacer(GlanceModifier.width(3.dp))
                FormPill(context, code, m.dp(17f))
            }
            if (score != null) {
                Spacer(GlanceModifier.width(10.dp))
                Text(score.toString(), style = TextStyle(color = Live, fontSize = m.sp(18f), fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    private fun FormPill(context: Context, code: Char, size: Dp = 17.dp) {
        val (bg, fg) = formColors(code)
        val label = context.getString(
            when (code) { 'W' -> R.string.form_w; 'L' -> R.string.form_l; else -> R.string.form_d },
        )
        Box(GlanceModifier.size(size).cornerRadius(4.dp).background(bg), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = fg, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold))
        }
    }

    /** Trait fin de séparation. */
    @Composable
    private fun Divider(top: Dp, bottom: Dp) {
        Spacer(GlanceModifier.height(top))
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Border)) {}
        Spacer(GlanceModifier.height(bottom))
    }

    @Composable
    private fun Logo(bitmap: Bitmap, size: Dp) {
        // Fit plutôt que Crop : un rognage circulaire couperait les blasons.
        Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(size), contentScale = ContentScale.Fit)
    }

    /** Pas de match à venir : identité du club et forme récente, centrées. */
    @Composable
    private fun NoMatch(context: Context, s: WidgetState.Snapshot, m: Metrics) {
        // Formats étroits : logo centré, texte dessous.
        if (m.layout == WidgetLayout.TINY || m.layout == WidgetLayout.SQUARE) {
            val tiny = m.layout == WidgetLayout.TINY
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                s.clubLogo?.let { Logo(it, if (tiny) 32.dp else 48.dp) }
                if (!tiny) {
                    Spacer(GlanceModifier.height(8.dp))
                    Text(s.club.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(context.getString(R.string.widget_no_match), style = TextStyle(color = Secondary, fontSize = 11.sp), maxLines = 2)
                }
            }
            return
        }
        val big = !m.compact
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            s.clubLogo?.let { Logo(it, if (big) m.dp(48f) else 28.dp); Spacer(GlanceModifier.width(14.dp)) }
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    s.club.orEmpty(),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (big) m.sp(18f) else 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(context.getString(R.string.widget_no_match), style = TextStyle(color = Secondary, fontSize = 12.sp), maxLines = 1)
                if (big && s.clubForm.isNotEmpty()) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row {
                        s.clubForm.takeLast(5).forEachIndexed { i, code ->
                            if (i > 0) Spacer(GlanceModifier.width(3.dp))
                            FormPill(context, code, m.dp(17f))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

class FavoriteClubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoriteClubWidget()

    /** Premier widget posé sur l'écran d'accueil : on le remplit tout de suite. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.refreshNow(context)
    }
}
