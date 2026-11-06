package com.leaguehub.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.leaguehub.android.app.notifications.MatchNotifier
import com.leaguehub.android.app.work.BackgroundHealth
import com.leaguehub.android.app.work.LiveWidgetWorker
import com.leaguehub.android.domain.leagueconfig.Competitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battement des widgets et des notifications de match, réglé par alarme système (WorkManager regroupe et retarde trop les tâches
 * périodiques, surtout sur Samsung) :
 * - pendant un match (club favori ou widget Matchs du jour) : toutes les minutes ;
 * - sinon, réveil à l'heure du prochain coup d'envoi (dans les 12 h) ;
 * - sinon, plus d'alarme : la synchro périodique relancera le battement.
 *
 * L'alarme ne fait que confier le battement à [LiveWidgetWorker] (travail prioritaire, plusieurs minutes de budget),
 * au lieu des moins de 10 s d'un récepteur.
 */
object WidgetTicker {
    private const val LIVE_EVERY_MS = 60_000L
    private const val RETRY_MS = 2 * 60_000L
    private const val WAKE_WINDOW_MS = 12 * 60 * 60_000L
    private const val STARTING_GRACE_MS = 15 * 60_000L

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(context: Context, delayMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // Sans permission d'alarme exacte ; se déclenche aussi en veille légère.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent(context))
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /**
     * Lit les scores, envoie les notifications, puis met à jour les widgets et programme le prochain battement.
     * Les notifications passent avant le dessin des widgets (téléchargement de logos compris) :
     * un réseau lent retarde l'affichage, jamais l'annonce d'un but.
     */
    suspend fun tick(context: Context) {
        // Filet de sécurité d'abord : si on est interrompu (réseau lent, délai dépassé), la chaîne continue.
        schedule(context, LIVE_EVERY_MS)
        val entry = widgetEntryPoint(context)
        val football = entry.football()
        val now = System.currentTimeMillis()
        val settings = entry.settings().settings.first()
        val favorite = settings.favorite
        // Clubs suivis avec au moins une notification activée.
        val followed = settings.followed.filter { it.club.teamId != favorite?.teamId && (it.notifyKickoff || it.notifyGoals) }

        // 1. Toutes les lectures ESPN en parallèle.
        val (favoriteData, followedForms) = coroutineScope {
            val fav = async { favorite?.let { WidgetUpdater.fetchFavorite(football, it) } }
            val others = followed.map { f ->
                async {
                    val league = Competitions.bySlug(f.club.competition) ?: Competitions.UCL
                    f to runCatching { football.teamForm(f.club.teamId, league) }.getOrNull()
                }
            }
            fav.await() to others.awaitAll()
        }
        val club = if (favorite == null) null else favoriteData?.refresh

        // 2. Notifications, dans la minute, avec le score le plus frais (résumé du match).
        if (favorite != null && club?.form != null) {
            MatchNotifier.notify(
                context, favorite, club.form, settings.notifyKickoff, settings.notifyGoals, now,
                club.liveFor, club.liveAgainst, club.clock,
            )
        }
        followedForms.forEach { (f, form) ->
            if (form != null) MatchNotifier.notify(context, f.club, form, f.notifyKickoff, f.notifyGoals, now)
        }

        // 3. Widgets.
        if (favorite == null) WidgetUpdater.clearFavorite(context)
        else if (favoriteData != null) runCatching { WidgetUpdater.renderFavorite(context, favorite, favoriteData) }
        val hasToday = WidgetUpdater.hasTodayWidget(context)
        val todayLive = runCatching { WidgetUpdater.refreshToday(context, football) }.getOrNull()

        val failed = (favorite != null && club == null) || (hasToday && todayLive == null)
        // Un club suivi en échec compte pour le diagnostic, pas pour le rythme (pas de réveils sans fin).
        BackgroundHealth.record(context, failed || followedForms.any { it.second == null }, now)
        val live = club?.live == true || todayLive == true || followedForms.any { it.second?.live != null } ||
            // Réseau en échec pendant un match affiché : on réessaie quand même.
            (failed && (WidgetState.read(context).live || TodayWidgetState.hasLive(context)))

        // Coups d'envoi à surveiller ; pour les clubs notifiés, on se réveille aussi 30 min avant.
        val kickoffs = buildList {
            club?.nextKickoff?.let { add(it); if (settings.notifyKickoff) add(it - MatchNotifier.KICKOFF_WARNING_MS) }
            if (hasToday) TodayWidgetState.nextKickoff(context)?.let(::add)
            followedForms.forEach { (f, form) ->
                form?.next?.kickoff?.let { add(it); if (f.notifyKickoff) add(it - MatchNotifier.KICKOFF_WARNING_MS) }
            }
        }
        // Coup d'envoi passé il y a peu mais match pas encore « en cours » chez ESPN : on surveille de près.
        val starting = kickoffs.any { it <= now && now - it < STARTING_GRACE_MS }
        val nextWake = kickoffs.filter { it > now }.minOrNull()

        val delay = when {
            live || starting -> LIVE_EVERY_MS
            failed -> RETRY_MS
            nextWake != null && nextWake - now < WAKE_WINDOW_MS -> (nextWake - now).coerceAtLeast(LIVE_EVERY_MS)
            else -> null
        }
        if (delay != null) schedule(context, delay) else cancel(context)
    }
}

/** Reçoit l'alarme du battement et le confie à WorkManager (travail prioritaire, sans limite de 10 s). */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val queued = runCatching { LiveWidgetWorker.tickNow(app) }.isSuccess
        if (queued) return
        // Secours : WorkManager indisponible, battement fait ici dans le temps imparti au récepteur.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { WidgetTicker.tick(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
