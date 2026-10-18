package com.leaguehub.android.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.widget.TodayWidgetState
import com.leaguehub.android.widget.WidgetState
import com.leaguehub.android.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Suivi en direct des widgets, léger (pas de recalage des classements) :
 * - pendant un match (club favori ou widget Matchs du jour) : toutes les minutes ;
 * - sinon, réveil à l'heure du prochain coup d'envoi (dans les 12 h) ;
 * - sinon, il s'arrête : la synchro périodique le relancera.
 */
@HiltWorker
class LiveWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val football: FootballRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val favorite = settings.settings.first().favorite
        val club = runCatching { WidgetUpdater.refreshFavorite(applicationContext, football, favorite) }.getOrNull()
        val hasToday = WidgetUpdater.hasTodayWidget(applicationContext)
        val todayLive = runCatching { WidgetUpdater.refreshToday(applicationContext, football) }.getOrNull()

        val failed = (favorite != null && club == null) || (hasToday && todayLive == null)
        val live = club?.live == true || todayLive == true ||
            // Réseau en échec pendant un match affiché : on réessaie vite quand même.
            (failed && (WidgetState.read(applicationContext).live || TodayWidgetState.hasLive(applicationContext)))
        val nextKickoff = listOfNotNull(
            club?.nextKickoff,
            if (hasToday) TodayWidgetState.nextKickoff(applicationContext) else null,
        ).filter { it > now }.minOrNull()

        val delay = when {
            live -> LIVE_EVERY_MS
            failed -> RETRY_MS
            nextKickoff != null && nextKickoff - now < WAKE_WINDOW_MS -> (nextKickoff - now).coerceAtLeast(LIVE_EVERY_MS)
            else -> null
        }
        if (delay != null) enqueue(applicationContext, delay, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val NAME = "leaguehub-live-widgets"
        private const val LIVE_EVERY_MS = 60_000L
        private const val RETRY_MS = 2 * 60_000L
        private const val WAKE_WINDOW_MS = 12 * 60 * 60_000L

        /** Mise à jour immédiate des widgets (nouveau favori, widget posé, données périmées). */
        fun start(context: Context) = enqueue(context, 0, ExistingWorkPolicy.REPLACE)

        /** Lance le suivi s'il ne tourne pas déjà (synchro périodique). */
        fun ensureRunning(context: Context) = enqueue(context, 0, ExistingWorkPolicy.KEEP)

        private fun enqueue(context: Context, delayMs: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<LiveWidgetWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}
