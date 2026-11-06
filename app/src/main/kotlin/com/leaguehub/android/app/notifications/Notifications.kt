package com.leaguehub.android.app.notifications

import android.Manifest
import android.app.Notification
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
    private const val CHANNEL_BACKGROUND = "background"

    /** Identifiant de la notification discrète du suivi en arrière-plan (Android 11 et moins). */
    const val BACKGROUND_ID = 0x1EA6

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_KICKOFF, context.getString(R.string.channel_kickoff), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_GOALS, context.getString(R.string.channel_goals), NotificationManager.IMPORTANCE_HIGH),
                // Sans son ni bandeau : n'apparaît qu'avant Android 12, le temps d'une vérification.
                NotificationChannel(CHANNEL_BACKGROUND, context.getString(R.string.channel_background), NotificationManager.IMPORTANCE_MIN),
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

    /** Notification discrète affichée pendant une vérification en arrière-plan (avant Android 12). */
    fun background(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_BACKGROUND)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle(context.getString(R.string.notif_background))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

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
