package com.leaguehub.android.app.work

import android.content.Context

/**
 * Trace du suivi en arrière-plan, affichée dans les réglages : permet de voir d'un coup d'œil
 * si le battement tourne encore (dernière vérification) et s'il a échoué récemment.
 */
object BackgroundHealth {
    private const val PREFS = "background-health"
    private const val LAST_CHECK = "last_check"
    private const val LAST_FAILURE = "last_failure"

    data class Snapshot(
        /** Dernière vérification terminée (epoch ms), 0 si aucune. */
        val lastCheck: Long,
        /** Dernière vérification dont une lecture ESPN a échoué (epoch ms), 0 si aucune. */
        val lastFailure: Long,
    )

    fun record(context: Context, failed: Boolean, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(LAST_CHECK, now)
            .apply { if (failed) putLong(LAST_FAILURE, now) }
            .apply()
    }

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(prefs.getLong(LAST_CHECK, 0), prefs.getLong(LAST_FAILURE, 0))
    }
}
