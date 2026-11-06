package com.leaguehub.android.ui.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.BuildConfig
import com.leaguehub.android.app.work.BackgroundHealth
import com.leaguehub.android.core.i18n.Language
import com.leaguehub.android.domain.repository.AppLanguage
import com.leaguehub.android.domain.repository.AppSettings
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État du système, relu à chaque affichage de l'écran (il change hors de l'app). */
data class SystemStatus(
    /** Android autorise-t-il les notifications de l'app ? */
    val notificationsAllowed: Boolean,
    val health: BackgroundHealth.Snapshot,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val version: String = BuildConfig.VERSION_NAME

    fun systemStatus() = SystemStatus(
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        health = BackgroundHealth.read(context),
    )

    fun theme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }
    fun language(language: AppLanguage) = viewModelScope.launch {
        repo.setLanguage(language)
        Language.apply(language)
    }
    fun clearFavorite() = viewModelScope.launch { repo.setFavorite(null) }
    fun clubTheme(enabled: Boolean) = viewModelScope.launch { repo.setClubTheme(enabled) }
    fun notifications(kickoff: Boolean, goals: Boolean) = viewModelScope.launch { repo.setNotifications(kickoff, goals) }
    fun favoriteLeague(slug: String?) = viewModelScope.launch { repo.setFavoriteLeague(slug) }
    fun unfollow(teamId: String) = viewModelScope.launch { repo.unfollow(teamId) }
    fun followedNotifications(teamId: String, kickoff: Boolean, goals: Boolean) =
        viewModelScope.launch { repo.setFollowedNotifications(teamId, kickoff, goals) }
}
