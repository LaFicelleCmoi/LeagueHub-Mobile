package com.leaguehub.android.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.app.navigation.AppNavHost
import com.leaguehub.android.core.theme.LeagueHubTheme
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Thème appliqué avant la première frame : une seule lecture (locale, quelques octets) du DataStore.
        val initial = runBlocking { settingsRepository.settings.first() }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initial)
            LeagueHubTheme(
                mode = settings.theme,
                clubColor = settings.favorite?.color?.takeIf { settings.clubTheme },
            ) {
                AppNavHost()
            }
        }
    }
}
