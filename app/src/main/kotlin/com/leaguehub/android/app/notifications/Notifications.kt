package com.leaguehub.android.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.leaguehub.android.R
import com.leaguehub.android.app.MainActivity

/** Notifications : coup d'envoi et but du club favori. */
object Notifications {
    private const val CHANNEL_KICKOFF = "kickoff"
    private const val CHANNEL_GOALS = "goals"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_KICKOFF, context.getString(R.string.channel_kickoff), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_GOALS, context.getString(R.string.channel_goals), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun kickoff(context: Context, matchId: String, title: String, text: String) =
        post(context, CHANNEL_KICKOFF, ("k$matchId").hashCode(), title, text)

    fun goal(context: Context, matchId: String, score: String, title: String, text: String) =
        post(context, CHANNEL_GOALS, ("g$matchId$score").hashCode(), title, text)

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission retirée entre-temps : rien à faire.
        }
    }
}
