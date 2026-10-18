package com.leaguehub.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battement des widgets, réglé par alarme système (WorkManager regroupe et retarde trop les tâches
 * courtes, surtout sur Samsung) :
 * - pendant un match (club favori ou widget Matchs du jour) : toutes les minutes ;
 * - sinon, réveil à l'heure du prochain coup d'envoi (dans les 12 h) ;
 * - sinon, plus d'alarme : la synchro périodique relancera le battement.
 */
object WidgetTicker {
    private const val LIVE_EVERY_MS = 60_000L
    private const val RETRY_MS = 2 * 60_000L
    private const val WAKE_WINDOW_MS = 12 * 60 * 60_000L

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

    /** Met à jour les deux widgets puis programme le prochain battement. */
    suspend fun tick(context: Context) {
        // Filet de sécurité d'abord : si on est interrompu (réseau lent, délai dépassé), la chaîne continue.
        schedule(context, LIVE_EVERY_MS)
        val entry = widgetEntryPoint(context)
        val football = entry.football()
        val now = System.currentTimeMillis()
        val favorite = entry.settings().settings.first().favorite
        val club = runCatching { WidgetUpdater.refreshFavorite(context, football, favorite) }.getOrNull()
        val hasToday = WidgetUpdater.hasTodayWidget(context)
        val todayLive = runCatching { WidgetUpdater.refreshToday(context, football) }.getOrNull()

        val failed = (favorite != null && club == null) || (hasToday && todayLive == null)
        val live = club?.live == true || todayLive == true ||
            // Réseau en échec pendant un match affiché : on réessaie quand même.
            (failed && (WidgetState.read(context).live || TodayWidgetState.hasLive(context)))
        val nextKickoff = listOfNotNull(club?.nextKickoff, if (hasToday) TodayWidgetState.nextKickoff(context) else null)
            .filter { it > now }.minOrNull()

        val delay = when {
            live -> LIVE_EVERY_MS
            failed -> RETRY_MS
            nextKickoff != null && nextKickoff - now < WAKE_WINDOW_MS -> (nextKickoff - now).coerceAtLeast(LIVE_EVERY_MS)
            else -> null
        }
        if (delay != null) schedule(context, delay) else cancel(context)
    }
}

/** Reçoit l'alarme du battement et met les widgets à jour (moins de 10 s, limite d'un récepteur). */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000) { WidgetTicker.tick(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
