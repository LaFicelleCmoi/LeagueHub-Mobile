package com.leaguehub.android.app.work

import com.leaguehub.android.widget.TodayWidgetState
import com.leaguehub.android.widget.TodayMatchesWidget
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.content.SharedPreferences
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
 * suivi du club favori et des clubs suivis (notifications coup d'envoi / but) et mise à jour des widgets.
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

        // Widget « Matchs du jour » (seulement s'il est posé sur l'écran d'accueil).
        var anyLive = runCatching { refreshTodayWidget() }.getOrDefault(false)

        val current = settings.settings.first()
        val favorite = current.favorite
        var ok = true
        if (favorite == null) {
            // Plus de club favori : le widget ne doit pas continuer d'afficher l'ancien.
            WidgetState.clear(applicationContext)
            FavoriteClubWidget().updateAll(applicationContext)
        } else {
            val form = runCatching { football.teamForm(favorite.teamId, leagueOf(favorite)) }.getOrNull()
            if (form == null) {
                ok = false
            } else {
                // Forme de l'adversaire pour les pastilles du widget (facultative : un échec ne bloque rien).
                val opponentForm = (form.live ?: form.next)?.opponent?.let { opponent ->
                    runCatching { football.teamForm(opponent.id, leagueOf(favorite)) }.getOrNull()
                }
                WidgetState.save(applicationContext, favorite, form, opponentForm)
                FavoriteClubWidget().updateAll(applicationContext)
                notifyClub(prefs, favorite, form, current.notifyKickoff, current.notifyGoals, now)
                anyLive = anyLive || form.live != null
            }
        }

        // Clubs suivis : chacun avec ses propres notifications.
        for (followed in current.followed) {
            if (followed.club.teamId == favorite?.teamId) continue
            if (!followed.notifyKickoff && !followed.notifyGoals) continue
            val form = runCatching { football.teamForm(followed.club.teamId, leagueOf(followed.club)) }.getOrNull() ?: continue
            notifyClub(prefs, followed.club, form, followed.notifyKickoff, followed.notifyGoals, now)
            anyLive = anyLive || form.live != null
        }

        // Pendant un match, on repasse toutes les 5 min (la synchro périodique, elle, est au quart d'heure).
        if (anyLive) scheduleLive(applicationContext, fromLiveRun = TAG_LIVE in tags)
        return if (ok) Result.success() else Result.retry()
    }

    private fun leagueOf(club: FavoriteClub): LeagueConfig = Competitions.bySlug(club.competition) ?: Competitions.UCL

    /** Coup d'envoi dans la demi-heure et buts d'un club, chacun notifié une seule fois. */
    private fun notifyClub(
        prefs: SharedPreferences,
        club: FavoriteClub,
        form: TeamForm,
        kickoff: Boolean,
        goals: Boolean,
        now: Long,
    ) {
        val next = form.next
        if (kickoff && next != null && next.kickoff - now in 0..KICKOFF_WARNING) {
            val key = "kickoff:${next.id}"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).apply()
                Notifications.kickoff(
                    applicationContext, next.id,
                    applicationContext.getString(R.string.notif_kickoff_title, club.name),
                    applicationContext.getString(R.string.notif_kickoff_text, next.opponent.name, next.competition),
                )
            }
        }

        val live = form.live
        if (goals && live != null && live.state == MatchState.IN) {
            // Clé par club : deux clubs suivis peuvent s'affronter.
            val key = "goals:${live.id}:${club.teamId}"
            val scored = live.goalsFor ?: 0
            val known = prefs.getInt(key, 0)
            if (scored > known) {
                Notifications.goal(
                    applicationContext, live.id + club.teamId, "$scored-${live.goalsAgainst ?: 0}",
                    applicationContext.getString(R.string.notif_goal_title, club.name),
                    "${club.name} $scored – ${live.goalsAgainst ?: 0} ${live.opponent.name} (${live.clock})",
                )
            }
            prefs.edit().putInt(key, scored).apply()
        }
    }

    /**
     * Matchs du jour des 6 compétitions pour le widget dédié.
     * @return true si au moins un match est en cours.
     */
    private suspend fun refreshTodayWidget(): Boolean {
        val ids = GlanceAppWidgetManager(applicationContext).getGlanceIds(TodayMatchesWidget::class.java)
        if (ids.isEmpty()) return false
        val live = TodayWidgetState.refresh(applicationContext, football) ?: return false
        TodayMatchesWidget().updateAll(applicationContext)
        return live
    }

    companion object {
        private const val NAME = "leaguehub-sync"
        private const val STATE = "sync-state"
        private const val LAST_FULL_SYNC = "last_full_sync"
        private const val FULL_SYNC_EVERY = 60 * 60_000L
        private const val KICKOFF_WARNING = 30 * 60_000L

        private const val NAME_NOW = "leaguehub-sync-now"
        private const val NAME_LIVE = "leaguehub-sync-live"
        private const val TAG_LIVE = "live"
        private const val LIVE_EVERY_MINUTES = 5L

        /**
         * Synchro rapprochée pendant les matchs. Depuis une synchro « live », on enchaîne la suivante ;
         * depuis une autre, on n'en ajoute pas si une est déjà prévue.
         */
        private fun scheduleLive(context: Context, fromLiveRun: Boolean) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(LIVE_EVERY_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG_LIVE)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME_LIVE,
                if (fromLiveRun) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Synchronisation immédiate (ouverture de l'app, nouveau favori, widget ajouté). */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // REPLACE : une demande immédiate ne doit jamais attendre derrière une synchro en échec (backoff).
            WorkManager.getInstance(context).enqueueUniqueWork(NAME_NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
