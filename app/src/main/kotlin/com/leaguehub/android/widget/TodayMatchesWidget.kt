package com.leaguehub.android.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.leaguehub.android.R
import com.leaguehub.android.app.MainActivity
import com.leaguehub.android.app.work.SyncWorker
import com.leaguehub.android.core.util.formatTime
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Un match tel que le widget l'affiche (préparé par le SyncWorker, sans réseau côté widget). */
@Serializable
data class TodayItem(
    val id: String,
    val competition: String,
    val leagueSlug: String,
    val kickoff: Long,
    val state: MatchState,
    val clock: String,
    val homeId: String,
    val homeName: String,
    val homeAbbr: String,
    val homeScore: Int?,
    val awayId: String,
    val awayName: String,
    val awayAbbr: String,
    val awayScore: Int?,
)

/** Données du widget « Matchs du jour » : liste JSON + logos en petits PNG (téléchargés une fois). */
object TodayWidgetState {
    private const val PREFS = "widget_today"
    private const val LOGO_PX = 64
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TodayItem.serializer())

    private fun dir(context: Context, kind: String) = File(File(context.filesDir, "widget"), kind).apply { mkdirs() }
    fun teamLogo(context: Context, id: String) = File(dir(context, "teams"), "$id.png")
    fun leagueLogo(context: Context, slug: String) = File(dir(context, "leagues"), "$slug.png")

    suspend fun save(context: Context, matches: List<Pair<LeagueConfig, Match>>) {
        // En direct d'abord, puis à venir, puis terminés (les plus récents en tête).
        val ordered = matches.sortedWith(
            compareBy<Pair<LeagueConfig, Match>> { (_, m) -> when (m.state) { MatchState.IN -> 0; MatchState.PRE -> 1; MatchState.POST -> 2 } }
                .thenBy { (_, m) -> if (m.state == MatchState.POST) -m.kickoff else m.kickoff },
        ).take(MAX_ITEMS)
        for ((league, m) in ordered) {
            download(context, league.logo(dark = true), leagueLogo(context, league.slug))
            download(context, m.home.team.logo, teamLogo(context, m.home.team.id))
            download(context, m.away.team.logo, teamLogo(context, m.away.team.id))
        }
        val items = ordered.map { (league, m) ->
            TodayItem(
                id = m.id, competition = m.competition, leagueSlug = league.slug, kickoff = m.kickoff, state = m.state, clock = m.clock,
                homeId = m.home.team.id, homeName = m.home.team.shortName, homeAbbr = m.home.team.abbreviation, homeScore = m.home.score,
                awayId = m.away.team.id, awayName = m.away.team.shortName, awayAbbr = m.away.team.abbreviation, awayScore = m.away.score,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("items", json.encodeToString(serializer, items))
            .putLong("updatedAt", System.currentTimeMillis())
            .apply()
    }

    fun read(context: Context): List<TodayItem>? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    /** Un logo d'équipe change rarement : on ne le retélécharge pas s'il est déjà là. */
    private suspend fun download(context: Context, url: String?, file: File) {
        if (url == null || file.exists()) return
        val request = ImageRequest.Builder(context).data(url).size(LOGO_PX).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return
        runCatching { file.outputStream().use { result.drawable.toBitmap(LOGO_PX, LOGO_PX).compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private const val MAX_ITEMS = 30
}

// Couleurs jour / nuit, les mêmes que le widget du club favori.
private val Accent = ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFACC15))
private val Live = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
private val Border = ColorProvider(day = Color.Black.copy(alpha = 0.08f), night = Color.White.copy(alpha = 0.1f))
private val Secondary = ColorProvider(day = Color(0xFF475569), night = Color.White.copy(alpha = 0.8f))
private val LivePill = ColorProvider(day = Color(0x1FDC2626), night = Color(0x33F87171))

/** Relance la synchro depuis le bouton du widget. */
class RefreshTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.refreshNow(context)
    }
}

/** Widget « Matchs du jour » : direct, à venir et résultats des 6 compétitions. */
class TodayMatchesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = TodayWidgetState.read(context)
        // Jamais rempli (widget tout juste posé) : on lance la synchro.
        if (items == null) SyncWorker.refreshNow(context)
        // Logos chargés une fois par affichage, partagés entre les lignes.
        val logos = HashMap<String, Bitmap?>()
        fun logo(file: File): Bitmap? = logos.getOrPut(file.path) { file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
        val rows = items.orEmpty().map { item ->
            Row3(
                item,
                logo(TodayWidgetState.leagueLogo(context, item.leagueSlug)),
                logo(TodayWidgetState.teamLogo(context, item.homeId)),
                logo(TodayWidgetState.teamLogo(context, item.awayId)),
            )
        }
        provideContent { GlanceTheme { Content(context, rows, loaded = items != null) } }
    }

    /** Une ligne prête à dessiner : le match et ses trois logos. */
    private data class Row3(val item: TodayItem, val league: Bitmap?, val home: Bitmap?, val away: Bitmap?)

    @Composable
    private fun Content(context: Context, rows: List<Row3>, loaded: Boolean) {
        val size = LocalSize.current
        val fontScale = context.resources.configuration.fontScale
        // Une seule ligne de haut (4×1, 2×1) : uniquement le match le plus important.
        val singleLine = size.height.value - 2f - 16f < (13f + 14f) * 1.3f * fontScale + 24f
        val wide = size.width >= 300.dp
        val narrow = size.width < 200.dp
        Box(GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(Border).padding(1.dp)) {
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(19.dp).background(GlanceTheme.colors.widgetBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (!singleLine) Header(context, rows.count { it.item.state == MatchState.IN })
                when {
                    !loaded -> Message(context.getString(R.string.loading_short))
                    rows.isEmpty() -> Message(context.getString(R.string.widget_today_empty))
                    singleLine -> MatchRow(context, rows.first(), wide = wide, narrow = narrow, extra = rows.size - 1)
                    else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(rows, itemId = { it.item.id.toLongOrNull() ?: it.item.id.hashCode().toLong() }) { row ->
                            Column {
                                Spacer(GlanceModifier.height(6.dp))
                                MatchRow(context, row, wide = wide, narrow = narrow, extra = 0)
                            }
                        }
                    }
                }
            }
        }
    }

    /** En-tête : titre, pastille du nombre de matchs en direct, bouton de rafraîchissement. */
    @Composable
    private fun Header(context: Context, live: Int) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                context.getString(R.string.widget_today_title),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (live > 0) {
                Box(GlanceModifier.cornerRadius(10.dp).background(LivePill).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(
                        "● " + context.getString(R.string.widget_live_count, live),
                        style = TextStyle(color = Live, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
            }
            Image(
                ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = context.getString(R.string.retry),
                modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshTodayAction>()),
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }

    /**
     * Ligne de match : [ligue] logo + nom domicile | score ou heure | nom + logo extérieur.
     * Étroit : abréviations ; large : noms courts et logo de la compétition.
     */
    @Composable
    private fun MatchRow(context: Context, row: Row3, wide: Boolean, narrow: Boolean, extra: Int) {
        val m = row.item
        val logoSize: Dp = if (narrow) 16.dp else 20.dp
        val nameStyle = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (narrow) 12.sp else 13.sp, fontWeight = FontWeight.Medium)
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("leaguehub://match/${m.competition}/${m.id}"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(deepLink)), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                row.league?.let { Logo(it, 14.dp) } ?: Spacer(GlanceModifier.size(14.dp))
                Spacer(GlanceModifier.width(8.dp))
            }
            // Domicile, aligné vers le score.
            Text(
                if (narrow) m.homeAbbr else m.homeName,
                modifier = GlanceModifier.defaultWeight(),
                style = nameStyle.copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            row.home?.let { Logo(it, logoSize) } ?: Spacer(GlanceModifier.size(logoSize))
            Center(context, m)
            row.away?.let { Logo(it, logoSize) } ?: Spacer(GlanceModifier.size(logoSize))
            Spacer(GlanceModifier.width(6.dp))
            Text(if (narrow) m.awayAbbr else m.awayName, modifier = GlanceModifier.defaultWeight(), style = nameStyle, maxLines = 1)
            if (extra > 0) {
                Spacer(GlanceModifier.width(6.dp))
                Text("+$extra", style = TextStyle(color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    /** Centre de la ligne : score (rouge en direct, avec la minute) ou heure du coup d'envoi. */
    @Composable
    private fun Center(context: Context, m: TodayItem) {
        Column(GlanceModifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (m.state) {
                MatchState.PRE -> Text(formatTime(m.kickoff), style = TextStyle(color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                MatchState.IN -> {
                    Text("${m.homeScore ?: 0} - ${m.awayScore ?: 0}", style = TextStyle(color = Live, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(m.clock.ifEmpty { context.getString(R.string.live) }, style = TextStyle(color = Live, fontSize = 10.sp), maxLines = 1)
                }
                MatchState.POST -> Text(
                    "${m.homeScore ?: 0} - ${m.awayScore ?: 0}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    @Composable
    private fun Logo(bitmap: Bitmap, size: Dp) {
        Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(size), contentScale = ContentScale.Fit)
    }

    @Composable
    private fun Message(text: String) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = TextStyle(color = Secondary, fontSize = 13.sp), maxLines = 2)
        }
    }
}

class TodayMatchesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayMatchesWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.refreshNow(context)
    }
}
