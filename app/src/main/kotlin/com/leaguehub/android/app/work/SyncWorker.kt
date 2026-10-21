package com.leaguehub.android.app.work

import com.leaguehub.android.widget.WidgetUpdater
import com.leaguehub.android.widget.TodayWidgetState
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import android.content.Context
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
import com.leaguehub.android.app.notifications.MatchNotifier
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Recalage en arrière-plan (WorkManager) : classements et calendriers rafraîchis pour le hors-ligne,
 * suivi du club favori et des clubs suivis (notifications coup d'envoi / but) et mise à jour des widgets
 * (relayée par [LiveWidgetWorker] pendant les matchs).
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

        // Widgets : même mise à jour que le suivi en direct.
        val todayLive = runCatching { WidgetUpdater.refreshToday(applicationContext, football) }.getOrNull() == true
        val current = settings.settings.first()
        val favorite = current.favorite
        val club = runCatching { WidgetUpdater.refreshFavorite(applicationContext, football, favorite) }.getOrNull()
        club?.form?.let { form ->
            favorite?.let {
                MatchNotifier.notify(applicationContext, it, form, current.notifyKickoff, current.notifyGoals, now, club.liveFor, club.liveAgainst, club.clock)
            }
        }
        var anyLive = todayLive || club?.live == true

        // Clubs suivis : chacun avec ses propres notifications.
        val followedKickoffs = mutableListOf<Long>()
        for (followed in current.followed) {
            if (followed.club.teamId == favorite?.teamId) continue
            if (!followed.notifyKickoff && !followed.notifyGoals) continue
            val form = runCatching { football.teamForm(followed.club.teamId, leagueOf(followed.club)) }.getOrNull() ?: continue
            MatchNotifier.notify(applicationContext, followed.club, form, followed.notifyKickoff, followed.notifyGoals, now)
            form.next?.kickoff?.let { followedKickoffs += it }
            anyLive = anyLive || form.live != null
        }

        // Match en cours ou coup d'envoi dans la journée : le suivi en direct des widgets prend le relais (toutes les minutes).
        val soon = (listOfNotNull(club?.nextKickoff, TodayWidgetState.nextKickoff(applicationContext)) + followedKickoffs)
            .any { it - now in 0..WIDGET_WAKE_MS }
        if (anyLive || soon) LiveWidgetWorker.ensureRunning(applicationContext)
        // Toujours « succès » : un échec réseau ponctuel ne doit pas repousser la synchro suivante (backoff).
        return Result.success()
    }

    private fun leagueOf(club: FavoriteClub): LeagueConfig = Competitions.bySlug(club.competition) ?: Competitions.UCL

    companion object {
        private const val NAME = "leaguehub-sync"
        private const val STATE = "sync-state"
        private const val LAST_FULL_SYNC = "last_full_sync"
        private const val FULL_SYNC_EVERY = 60 * 60_000L

        private const val NAME_NOW = "leaguehub-sync-now"
        private const val WIDGET_WAKE_MS = 12 * 60 * 60_000L

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
