package com.leaguehub.android.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Redémarrage du téléphone ou mise à jour de l'app : les alarmes du battement ont disparu.
 * On relance aussitôt la synchro et le battement, sans attendre la synchro périodique
 * (qui peut tarder de longues minutes, voire des heures en veille).
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        SyncWorker.refreshNow(app)
        LiveWidgetWorker.ensureRunning(app)
    }
}
