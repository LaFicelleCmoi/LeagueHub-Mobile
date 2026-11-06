package com.leaguehub.android.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leaguehub.android.app.notifications.Notifications
import com.leaguehub.android.widget.WidgetTicker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Battement des widgets et des notifications ([WidgetTicker.tick]), en travail prioritaire :
 * lancé tout de suite, même en veille, avec plusieurs minutes de budget.
 * Le rythme pendant les matchs est tenu par l'alarme de [WidgetTicker], qui relance ce travail.
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

    /** Avant Android 12, un travail prioritaire tourne au premier plan : notification discrète obligatoire. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifications.BACKGROUND_ID, Notifications.background(applicationContext))

    companion object {
        private const val NAME = "leaguehub-live-widgets"

        /** Mise à jour immédiate (nouveau favori, widget posé, données périmées) : remplace un battement en attente. */
        fun start(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

        /** Lance le suivi s'il ne tourne pas déjà (synchro périodique). */
        fun ensureRunning(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        /** Battement demandé par l'alarme : ignoré si le précédent n'est pas fini. */
        fun tickNow(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<LiveWidgetWorker>()
                // Quota épuisé (app non exemptée de l'optimisation batterie) : simple travail normal.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }
    }
}
