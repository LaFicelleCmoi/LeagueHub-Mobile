package com.leaguehub.android.ui.settings

import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.AppCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.leaguehub.android.R
import com.leaguehub.android.core.i18n.Language
import com.leaguehub.android.domain.repository.AppLanguage
import com.leaguehub.android.domain.repository.AppSettings
import com.leaguehub.android.domain.repository.SettingsRepository
import com.leaguehub.android.domain.repository.ThemeMode
import com.leaguehub.android.ui.components.SectionTitle
import com.leaguehub.android.ui.components.TeamLogo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun theme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }
    fun language(language: AppLanguage) = viewModelScope.launch {
        repo.setLanguage(language)
        Language.apply(language)
    }
    fun clearFavorite() = viewModelScope.launch { repo.setFavorite(null) }
    fun clubTheme(enabled: Boolean) = viewModelScope.launch { repo.setClubTheme(enabled) }
    fun notifications(kickoff: Boolean, goals: Boolean) = viewModelScope.launch { repo.setNotifications(kickoff, goals) }
}

@Composable
fun SettingsScreen(onPickFavorite: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // Club favori en tête : c'est le réglage qui change le plus l'app.
        SectionTitle(stringResource(R.string.favorite_club))
        SettingsGroup {
            val fav = s.favorite
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (fav != null) {
                    TeamLogo(fav.logo, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(fav.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.clearFavorite() }) { Text(stringResource(R.string.remove)) }
                } else {
                    Text(stringResource(R.string.no_favorite_yet), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onPickFavorite) { Text(stringResource(R.string.choose_club)) }
            }
            SegmentDivider()
            Toggle(stringResource(R.string.club_theme), s.clubTheme, enabled = s.favorite?.color != null) { viewModel.clubTheme(it) }
        }

        SectionTitle(stringResource(R.string.settings_appearance))
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingLabel(stringResource(R.string.settings_theme))
                Choice(
                    options = ThemeMode.entries,
                    selected = s.theme,
                    label = { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) },
                    onSelect = { viewModel.theme(it) },
                )
                Spacer(Modifier.height(8.dp))
                SettingLabel(stringResource(R.string.settings_language))
                Choice(
                    options = AppLanguage.entries,
                    selected = s.language,
                    label = { stringResource(when (it) { AppLanguage.SYSTEM -> R.string.theme_system; AppLanguage.FR -> R.string.lang_fr; AppLanguage.EN -> R.string.lang_en }) },
                    onSelect = { viewModel.language(it) },
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_notifications))
        SettingsGroup {
            Toggle(stringResource(R.string.notify_kickoff), s.notifyKickoff) {
                if (it) askPermission(permission::launch)
                viewModel.notifications(it, s.notifyGoals)
            }
            SegmentDivider()
            Toggle(stringResource(R.string.notify_goals), s.notifyGoals) {
                if (it) askPermission(permission::launch)
                viewModel.notifications(s.notifyKickoff, it)
            }
        }

        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        )
    }
}

/** Groupe de réglages : une carte. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), content = content)
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun askPermission(launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launch(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
