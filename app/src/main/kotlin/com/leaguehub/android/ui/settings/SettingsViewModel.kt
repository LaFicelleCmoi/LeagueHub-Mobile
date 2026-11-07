package com.leaguehub.android.ui.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.BuildConfig
import com.leaguehub.android.app.calendar.CalendarSync
import com.leaguehub.android.app.calendar.DeviceCalendar
import com.leaguehub.android.app.work.BackgroundHealth
import com.leaguehub.android.core.i18n.Language
import com.leaguehub.android.domain.repository.AppLanguage
import com.leaguehub.android.domain.repository.AppSettings
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.repository.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État du système, relu à chaque affichage de l'écran (il change hors de l'app). */
data class SystemStatus(
    /** Android autorise-t-il les notifications de l'app ? */
    val notificationsAllowed: Boolean,
    val health: BackgroundHealth.Snapshot,
)

/** Agenda du téléphone : agendas disponibles et état de la recopie des matchs. */
data class CalendarUi(
    val calendars: List<DeviceCalendar> = emptyList(),
    val permitted: Boolean = false,
    /** L'utilisateur a refusé l'accès : il faut passer par les réglages Android. */
    val denied: Boolean = false,
    val syncing: Boolean = false,
    /** Matchs à venir actuellement dans l'agenda. */
    val upcoming: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val calendarSync: CalendarSync,
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

    private val _calendar = MutableStateFlow(CalendarUi())
    val calendar: StateFlow<CalendarUi> = _calendar.asStateFlow()

    /** Relu à chaque retour sur l'écran : la permission et les agendas changent hors de l'app. */
    fun refreshCalendars() = viewModelScope.launch {
        _calendar.update {
            it.copy(
                calendars = calendarSync.calendars(),
                permitted = calendarSync.hasPermission(),
                denied = it.denied && !calendarSync.hasPermission(),
                upcoming = calendarSync.upcomingCount(),
            )
        }
    }

    /** Réponse à la demande de permission : on active si elle est accordée. */
    fun onCalendarPermission(granted: Boolean) {
        if (granted) enableCalendar() else _calendar.update { it.copy(denied = true) }
    }

    /** Active la recopie dans l'agenda déjà choisi, sinon dans l'agenda principal. */
    fun enableCalendar() = viewModelScope.launch {
        val calendars = calendarSync.calendars()
        val id = settings.value.calendarId?.takeIf { id -> calendars.any { it.id == id } } ?: calendars.firstOrNull()?.id
        _calendar.update { it.copy(calendars = calendars, permitted = calendarSync.hasPermission()) }
        if (id == null) return@launch
        repo.setCalendarSync(true, id)
        syncCalendar()
    }

    fun chooseCalendar(id: Long) = viewModelScope.launch {
        repo.setCalendarSync(true, id)
        syncCalendar()
    }

    fun disableCalendar() = viewModelScope.launch {
        repo.setCalendarSync(false, settings.value.calendarId)
        calendarSync.clear()
        _calendar.update { it.copy(upcoming = 0) }
    }

    private suspend fun syncCalendar() {
        _calendar.update { it.copy(syncing = true) }
        runCatching { calendarSync.sync() }
        _calendar.update { it.copy(syncing = false, upcoming = calendarSync.upcomingCount()) }
    }
}
