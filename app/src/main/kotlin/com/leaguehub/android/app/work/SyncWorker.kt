package com.leaguehub.android.app.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.leaguehub.android.R
import com.leaguehub.android.app.notifications.Notifications
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.widget.FavoriteClubWidget
import com.leaguehub.android.widget.WidgetState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Recalage en arrière-plan (WorkManager) : classements et calendriers rafraîchis pour le hors-ligne,
 * suivi du club favori (notifications coup d'envoi / but) et mise à jour du widget.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val football: FootballRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Recalage complet au plus une fois par heure.
        if (now - prefs.getLong(LAST_FULL_SYNC, 0) > FULL_SYNC_EVERY) {
            var ok = true
            for (league in Competitions.LEAGUES) {
                ok = runCatching { football.refreshStandings(league); football.refreshCalendar(league) }.isSuccess && ok
            }
            ok = runCatching { football.refreshUclSeason() }.isSuccess && ok
            if (ok) prefs.edit().putLong(LAST_FULL_SYNC, now).apply()
        }

        val current = settings.settings.first()
        val favorite = current.favorite
        if (favorite == null) {
            // Plus de club favori : le widget ne doit pas continuer d'afficher l'ancien.
            WidgetState.clear(applicationContext)
            FavoriteClubWidget().updateAll(applicationContext)
            return Result.success()
        }
        val league = Competitions.bySlug(favorite.competition) ?: Competitions.UCL
        val form = runCatching { football.teamForm(favorite.teamId, league) }.getOrElse { return Result.retry() }

        // Forme de l'adversaire pour les pastilles du widget (facultative : un échec ne bloque rien).
        val opponentForm = (form.live ?: form.next)?.opponent?.let { opponent ->
            runCatching { football.teamForm(opponent.id, league) }.getOrNull()
        }
        WidgetState.save(applicationContext, favorite, form, opponentForm)
        FavoriteClubWidget().updateAll(applicationContext)

        val next = form.next
        if (current.notifyKickoff && next != null && next.kickoff - now in 0..KICKOFF_WARNING) {
            val key = "kickoff:${next.id}"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).apply()
                Notifications.kickoff(
                    applicationContext, next.id,
                    applicationContext.getString(R.string.notif_kickoff_title, favorite.name),
                    applicationContext.getString(R.string.notif_kickoff_text, next.opponent.name, next.competition),
                )
            }
        }

        val live = form.live
        if (current.notifyGoals && live != null && live.state == MatchState.IN) {
            val key = "goals:${live.id}"
            val goals = live.goalsFor ?: 0
            val known = prefs.getInt(key, 0)
            if (goals > known) {
                Notifications.goal(
                    applicationContext, live.id, "$goals-${live.goalsAgainst ?: 0}",
                    applicationContext.getString(R.string.notif_goal_title, favorite.name),
                    "${favorite.name} $goals – ${live.goalsAgainst ?: 0} ${live.opponent.name} (${live.clock})",
                )
            }
            prefs.edit().putInt(key, goals).apply()
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "leaguehub-sync"
        private const val STATE = "sync-state"
        private const val LAST_FULL_SYNC = "last_full_sync"
        private const val FULL_SYNC_EVERY = 60 * 60_000L
        private const val KICKOFF_WARNING = 30 * 60_000L

        private const val NAME_NOW = "leaguehub-sync-now"

        /** Synchronisation immédiate (ouverture de l'app, nouveau favori, widget ajouté). */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
