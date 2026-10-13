package com.leaguehub.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leaguehub.android.data.espn.EspnJson
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.repository.AppLanguage
import com.leaguehub.android.domain.repository.AppSettings
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.repository.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Thème, club favori, langue : mémorisés sur l'appareil (DataStore), sans compte. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val FAVORITE = stringPreferencesKey("favorite")
        val CLUB_THEME = booleanPreferencesKey("club_theme")
        val NOTIFY_KICKOFF = booleanPreferencesKey("notify_kickoff")
        val NOTIFY_GOALS = booleanPreferencesKey("notify_goals")
    }

    override val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = p[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            favorite = p[Keys.FAVORITE]?.let { runCatching { EspnJson.decodeFromString(FavoriteClub.serializer(), it) }.getOrNull() },
            clubTheme = p[Keys.CLUB_THEME] ?: false,
            notifyKickoff = p[Keys.NOTIFY_KICKOFF] ?: true,
            notifyGoals = p[Keys.NOTIFY_GOALS] ?: true,
        )
    }

    override suspend fun setTheme(mode: ThemeMode) {
        context.settingsStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        context.settingsStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setFavorite(club: FavoriteClub?) {
        context.settingsStore.edit {
            if (club == null) it.remove(Keys.FAVORITE)
            else it[Keys.FAVORITE] = EspnJson.encodeToString(FavoriteClub.serializer(), club)
        }
    }

    override suspend fun setClubTheme(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.CLUB_THEME] = enabled }
    }

    override suspend fun setNotifications(kickoff: Boolean, goals: Boolean) {
        context.settingsStore.edit {
            it[Keys.NOTIFY_KICKOFF] = kickoff
            it[Keys.NOTIFY_GOALS] = goals
        }
    }
}
