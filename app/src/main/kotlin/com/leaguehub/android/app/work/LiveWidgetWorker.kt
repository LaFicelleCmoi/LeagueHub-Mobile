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
import com.leaguehub.android.widget.WidgetTicker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Mise à jour immédiate des widgets (nouveau favori, widget posé, données périmées) ;
 * le rythme pendant les matchs est ensuite tenu par [WidgetTicker] (alarme système).
 */
@HiltWorker
class LiveWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WidgetTicker.tick(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "leaguehub-live-widgets"

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
