package com.leaguehub.android.widget

import com.leaguehub.android.core.util.formatDay
import android.content.res.Configuration
import java.time.LocalDate
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoint
import com.leaguehub.android.domain.schedule.PARIS
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.core.util.parisDay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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

/** Un match tel que le widget l'affiche. */
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

/** Accès au dépôt depuis le widget (hors écrans Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun football(): FootballRepository
}

/**
 * Données du widget « Matchs du jour » : liste JSON + logos en petits PNG (téléchargés une fois).
 * Le widget se charge lui-même ([refresh]) au lieu d'attendre la synchro en arrière-plan.
 */
object TodayWidgetState {
    private const val PREFS = "widget_today"
    private const val LOGO_PX = 64
    private const val MAX_ITEMS = 30
    private const val FETCH_TIMEOUT_MS = 20_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TodayItem.serializer())
    private val mutex = Mutex()

    /** Incrémenté à chaque nouvelle donnée (matchs, puis logos) : le widget affiché se redessine aussitôt. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun dir(context: Context, kind: String) = File(File(context.filesDir, "widget"), kind).apply { mkdirs() }
    fun teamLogo(context: Context, id: String) = File(dir(context, "teams"), "$id.png")
    /** Logo de compétition : version pour fond sombre ([dark]) ou clair. */
    fun leagueLogo(context: Context, slug: String, dark: Boolean = true) =
        File(dir(context, "leagues"), if (dark) "$slug.png" else "$slug-light.png")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): List<TodayItem>? =
        prefs(context).getString("items", null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    fun updatedAt(context: Context): Long = prefs(context).getLong("updatedAt", 0)

    /** À recharger : jamais chargé, d'un autre jour, ou trop vieux (2 min en direct, 10 min sinon). */
    fun isStale(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val items = read(context) ?: return true
        val at = updatedAt(context)
        if (parisDay(at) != parisDay(now)) return true
        val maxAge = if (items.any { it.state == MatchState.IN }) 2 * 60_000L else 10 * 60_000L
        return now - at > maxAge
    }

    /**
     * Charge les matchs du jour des 6 compétitions, les enregistre, puis complète les logos manquants.
     * @return true si au moins un match est en cours, null si rien n'a pu être chargé (réseau).
     */
    suspend fun refresh(context: Context, football: FootballRepository): Boolean? = mutex.withLock {
        val today = LocalDate.now(PARIS)
        val fetched = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            coroutineScope {
                Competitions.ALL.map { league -> async { league to runCatching { football.liveMatches(league) }.getOrNull() } }.awaitAll()
            }
        } ?: return@withLock null
        // Tout a échoué : on garde l'affichage précédent plutôt qu'une liste vide trompeuse.
        if (fetched.all { it.second == null }) return@withLock null
        val matches = fetched.flatMap { (league, list) -> list.orEmpty().filter { parisDay(it.kickoff) == today }.map { league to it } }
        val ordered = save(context, matches)
        _version.value++
        // Les matchs sont déjà affichés ; les logos arrivent ensuite (une seule fois par équipe).
        withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            coroutineScope {
                ordered.flatMap { (league, m) ->
                    listOf(
                        league.logo(dark = true) to leagueLogo(context, league.slug, dark = true),
                        league.logo(dark = false) to leagueLogo(context, league.slug, dark = false),
                        m.home.team.logo to teamLogo(context, m.home.team.id),
                        m.away.team.logo to teamLogo(context, m.away.team.id),
                    )
                }.distinctBy { it.second.path }.filter { !it.second.exists() }
                    .map { (url, file) -> async { download(context, url, file) } }.awaitAll()
            }
        }
        _version.value++
        matches.any { it.second.state == MatchState.IN }
    }

    private fun save(context: Context, matches: List<Pair<LeagueConfig, Match>>): List<Pair<LeagueConfig, Match>> {
        // En direct d'abord, puis à venir, puis terminés (les plus récents en tête).
        val ordered = matches.sortedWith(
            compareBy<Pair<LeagueConfig, Match>> { (_, m) -> when (m.state) { MatchState.IN -> 0; MatchState.PRE -> 1; MatchState.POST -> 2 } }
                .thenBy { (_, m) -> if (m.state == MatchState.POST) -m.kickoff else m.kickoff },
        ).take(MAX_ITEMS)
        val items = ordered.map { (league, m) ->
            TodayItem(
                id = m.id, competition = m.competition, leagueSlug = league.slug, kickoff = m.kickoff, state = m.state, clock = m.clock,
                homeId = m.home.team.id, homeName = m.home.team.shortName, homeAbbr = m.home.team.abbreviation, homeScore = m.home.score,
                awayId = m.away.team.id, awayName = m.away.team.shortName, awayAbbr = m.away.team.abbreviation, awayScore = m.away.score,
            )
        }
        // commit() et non apply() : le widget relit juste après.
        prefs(context).edit()
            .putString("items", json.encodeToString(serializer, items))
            .putLong("updatedAt", System.currentTimeMillis())
            .commit()
        return ordered
    }

    private suspend fun download(context: Context, url: String?, file: File) {
        if (url == null) return
        val request = ImageRequest.Builder(context).data(url).size(LOGO_PX).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return
        runCatching { file.outputStream().use { result.drawable.toBitmap(LOGO_PX, LOGO_PX).compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
}

private fun football(context: Context): FootballRepository =
    EntryPointAccessors.fromApplication(context.applicationContext, TodayWidgetEntryPoint::class.java).football()

// Couleurs jour / nuit, les mêmes que le widget du club favori.
private val Accent = ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFACC15))
private val Live = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFF87171))
private val Border = ColorProvider(day = Color.Black.copy(alpha = 0.08f), night = Color.White.copy(alpha = 0.1f))
private val Secondary = ColorProvider(day = Color(0xFF475569), night = Color.White.copy(alpha = 0.8f))
private val LivePill = ColorProvider(day = Color(0x1FDC2626), night = Color(0x33F87171))
/** Fond des cartes de match : à peine plus clair (nuit) ou plus sombre (jour) que le widget. */
private val Card = ColorProvider(day = Color(0x0A000000), night = Color(0x0FFFFFFF))
private val LiveCard = ColorProvider(day = Color(0x14DC2626), night = Color(0x1FF87171))

/** Bouton ↻ : recharge tout de suite, sans passer par la file de la synchro. */
class RefreshTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TodayWidgetState.refresh(context, football(context))
        TodayMatchesWidget().update(context, glanceId)
    }
}

/** Widget « Matchs du jour » : direct, à venir et résultats des 6 compétitions. */
class TodayMatchesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Relu à chaque nouvelle donnée : pas besoin d'attendre la fin de session du widget.
            val version by TodayWidgetState.version.collectAsState()
            val items = remember(version) { TodayWidgetState.read(context) }
            val rows = remember(version) { buildRows(context, items.orEmpty()) }
            var failed by remember { mutableStateOf(false) }
            // Le widget se charge lui-même dès qu'il s'affiche si ses données sont absentes ou vieilles.
            LaunchedEffect(Unit) {
                if (TodayWidgetState.isStale(context)) {
                    failed = TodayWidgetState.refresh(context, football(context)) == null
                }
            }
            GlanceTheme {
                Content(context, rows, loaded = items != null, failed = failed && items == null, updatedAt = TodayWidgetState.updatedAt(context))
            }
        }
    }

    /** Logos lus une fois par affichage et partagés entre les lignes. */
    private fun buildRows(context: Context, items: List<TodayItem>): List<Row3> {
        val logos = HashMap<String, Bitmap?>()
        fun logo(file: File): Bitmap? = logos.getOrPut(file.path) { file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return items.map { item ->
            Row3(
                item,
                logo(TodayWidgetState.leagueLogo(context, item.leagueSlug, dark = night)),
                logo(TodayWidgetState.teamLogo(context, item.homeId)),
                logo(TodayWidgetState.teamLogo(context, item.awayId)),
            )
        }
    }

    /** Une ligne prête à dessiner : le match et ses trois logos. */
    private data class Row3(val item: TodayItem, val league: Bitmap?, val home: Bitmap?, val away: Bitmap?)

    /** Ce que la liste affiche : un en-tête de compétition ou un match. */
    private sealed interface Entry {
        val key: Long
        data class Section(val slug: String, val logo: Bitmap?, val count: Int, val live: Int) : Entry {
            override val key: Long get() = ("s$slug").hashCode().toLong()
        }
        data class Game(val row: Row3) : Entry {
            override val key: Long get() = row.item.id.toLongOrNull() ?: row.item.id.hashCode().toLong()
        }
    }

    /**
     * Tailles calculées pour remplir le widget : peu de matchs = grandes lignes aérées,
     * beaucoup = lignes compactes et liste qui défile. Tient compte de la police système.
     */
    private class ListMetrics(height: Float, private val fontScale: Float, sections: Int, games: Int) {
        private fun line(sp: Float) = sp * 1.3f * fontScale
        val headerH = line(16f) + line(11f) + 10f
        val sectionH = maxOf(20f, line(12f)) + 14f
        private val avail = height - 2f - 16f - headerH
        val rowH: Float = if (games == 0) 48f else ((avail - sections * sectionH - games * GAP) / games).coerceIn(48f, 72f)
        /** 0 pour la plus petite ligne, 1 pour la plus grande. */
        private val t = (rowH - 48f) / 24f
        val logo = (22f + 10f * t).dp
        val nameSp = (13f + 2f * t).sp
        val centerSp = (15f + 3f * t).sp

        companion object { const val GAP = 4f }
    }

    @Composable
    private fun Content(context: Context, rows: List<Row3>, loaded: Boolean, failed: Boolean, updatedAt: Long) {
        val size = LocalSize.current
        val fontScale = context.resources.configuration.fontScale
        // Une seule ligne de haut (4×1, 2×1) : uniquement le match le plus important.
        val singleLine = size.height.value - 2f - 16f < (13f + 14f) * 1.3f * fontScale + 24f
        val wide = size.width >= 300.dp
        val narrow = size.width < 200.dp
        // Groupes par compétition, dans l'ordre d'importance déjà calculé (direct, à venir, terminés).
        val groups = rows.groupBy { it.item.leagueSlug }
        val entries = groups.flatMap { (slug, list) ->
            listOf(Entry.Section(slug, list.first().league, list.size, list.count { it.item.state == MatchState.IN })) + list.map { Entry.Game(it) }
        }
        val metrics = ListMetrics(size.height.value, fontScale, groups.size, rows.size)
        Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Border).padding(1.dp)) {
            Column(
                GlanceModifier.fillMaxSize().cornerRadius(23.dp).background(GlanceTheme.colors.widgetBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (!singleLine) Header(context, rows.count { it.item.state == MatchState.IN }, updatedAt.takeIf { loaded })
                when {
                    failed -> Message(context.getString(R.string.widget_today_error))
                    !loaded -> Message(context.getString(R.string.loading_short))
                    rows.isEmpty() -> Message(context.getString(R.string.widget_today_empty))
                    singleLine -> MatchRow(context, rows.first(), ListMetrics(48f + 60f, fontScale, 0, 1), wide = wide, narrow = narrow, extra = rows.size - 1)
                    else -> LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(entries, itemId = { it.key }) { entry ->
                            when (entry) {
                                is Entry.Section -> SectionHeader(context, entry, metrics)
                                is Entry.Game -> Column {
                                    MatchRow(context, entry.row, metrics, wide = wide, narrow = narrow, extra = 0)
                                    Spacer(GlanceModifier.height(ListMetrics.GAP.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** En-tête : titre et date, pastille du direct, heure de mise à jour, bouton ↻. */
    @Composable
    private fun Header(context: Context, live: Int, updatedAt: Long?) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp).clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    context.getString(R.string.widget_today_title),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(formatDay(LocalDate.now(PARIS)), style = TextStyle(color = Secondary, fontSize = 11.sp), maxLines = 1)
            }
            if (live > 0) {
                Box(GlanceModifier.cornerRadius(10.dp).background(LivePill).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(
                        "● " + context.getString(R.string.widget_live_count, live),
                        style = TextStyle(color = Live, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
            }
            // Heure de la dernière mise à jour : on voit tout de suite si c'est frais.
            updatedAt?.let {
                Text(formatTime(it), style = TextStyle(color = Secondary, fontSize = 10.sp))
                Spacer(GlanceModifier.width(4.dp))
            }
            Box(
                GlanceModifier.size(32.dp).cornerRadius(16.dp).clickable(actionRunCallback<RefreshTodayAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = context.getString(R.string.retry),
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }

    /** En-tête de compétition : logo lisible, nom, nombre de matchs (et en direct). */
    @Composable
    private fun SectionHeader(context: Context, section: Entry.Section, m: ListMetrics) {
        val name = Competitions.bySlug(section.slug)?.name ?: section.slug
        Row(
            GlanceModifier.fillMaxWidth().height(m.sectionH.dp).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            section.logo?.let { Logo(it, 20.dp); Spacer(GlanceModifier.width(8.dp)) }
            Text(
                name.uppercase(),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (section.live > 0) {
                Text("● ${section.live}  ", style = TextStyle(color = Live, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Text(section.count.toString(), style = TextStyle(color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }

    /**
     * Carte de match : nom + logo domicile | score ou heure | logo + nom extérieur.
     * Étroit : abréviations ; sinon noms courts. En direct, la carte prend une teinte rouge.
     */
    @Composable
    private fun MatchRow(context: Context, row: Row3, m: ListMetrics, wide: Boolean, narrow: Boolean, extra: Int) {
        val g = row.item
        val homeWins = g.state == MatchState.POST && (g.homeScore ?: 0) > (g.awayScore ?: 0)
        val awayWins = g.state == MatchState.POST && (g.awayScore ?: 0) > (g.homeScore ?: 0)
        val ink = GlanceTheme.colors.onSurface
        fun nameStyle(winner: Boolean, align: TextAlign) = TextStyle(
            color = if (g.state == MatchState.POST && !winner) Secondary else ink,
            fontSize = m.nameSp,
            fontWeight = if (winner || g.state != MatchState.POST) FontWeight.Bold else FontWeight.Medium,
            textAlign = align,
        )
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("leaguehub://match/${g.competition}/${g.id}"))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        Row(
            GlanceModifier.fillMaxWidth().height(m.rowH.dp).cornerRadius(14.dp)
                .background(if (g.state == MatchState.IN) LiveCard else Card)
                .padding(horizontal = if (narrow) 6.dp else 10.dp)
                .clickable(actionStartActivity(deepLink)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Domicile, aligné vers le score.
            Text(
                if (narrow || !wide && g.homeName.length > 12) g.homeAbbr else g.homeName,
                modifier = GlanceModifier.defaultWeight(),
                style = nameStyle(homeWins, TextAlign.End),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            row.home?.let { Logo(it, m.logo) } ?: Spacer(GlanceModifier.size(m.logo))
            Center(context, g, m)
            row.away?.let { Logo(it, m.logo) } ?: Spacer(GlanceModifier.size(m.logo))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (narrow || !wide && g.awayName.length > 12) g.awayAbbr else g.awayName,
                modifier = GlanceModifier.defaultWeight(),
                style = nameStyle(awayWins, TextAlign.Start),
                maxLines = 1,
            )
            if (extra > 0) {
                Spacer(GlanceModifier.width(6.dp))
                Text("+$extra", style = TextStyle(color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    /** Centre : heure (jaune) avant le match, score et minute (rouge) pendant, score et « Terminé » après. */
    @Composable
    private fun Center(context: Context, g: TodayItem, m: ListMetrics) {
        Column(GlanceModifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (g.state) {
                MatchState.PRE -> Text(formatTime(g.kickoff), style = TextStyle(color = Accent, fontSize = m.centerSp, fontWeight = FontWeight.Bold))
                MatchState.IN -> {
                    Text("${g.homeScore ?: 0} - ${g.awayScore ?: 0}", style = TextStyle(color = Live, fontSize = m.centerSp, fontWeight = FontWeight.Bold))
                    Text("● " + g.clock.ifEmpty { context.getString(R.string.live) }, style = TextStyle(color = Live, fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                MatchState.POST -> {
                    Text("${g.homeScore ?: 0} - ${g.awayScore ?: 0}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = m.centerSp, fontWeight = FontWeight.Bold))
                    Text(context.getString(R.string.status_final), style = TextStyle(color = Secondary, fontSize = 9.sp), maxLines = 1)
                }
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
