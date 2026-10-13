package com.leaguehub.android.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.leaguehub.android.app.notifications.Notifications
import com.leaguehub.android.app.work.SyncWorker
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LeagueHubApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SyncWorker.schedule(this)
        // Au lancement puis à chaque changement de club favori : widget et notifications à jour tout de suite.
        appScope.launch {
            settings.settings.map { it.favorite?.teamId }.distinctUntilChanged().collect {
                SyncWorker.refreshNow(this@LeagueHubApp)
            }
        }
    }
}
