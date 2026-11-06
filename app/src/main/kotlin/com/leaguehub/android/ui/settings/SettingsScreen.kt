package com.leaguehub.android.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaguehub.android.R
import com.leaguehub.android.core.theme.LocalSportColors
import com.leaguehub.android.core.theme.parseHex
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.FollowedClub
import com.leaguehub.android.domain.repository.AppLanguage
import com.leaguehub.android.domain.repository.ThemeMode
import com.leaguehub.android.ui.components.AppCard
import com.leaguehub.android.ui.components.SegmentDivider
import com.leaguehub.android.ui.components.TeamLogo
import com.leaguehub.android.ui.components.rememberNow

/** Au-delà, la dernière vérification est jugée ancienne (la synchro tourne au moins toutes les 15 min hors veille). */
private const val STALE_CHECK_MS = 60 * 60_000L

@Composable
fun SettingsScreen(onPickFavorite: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Permission et santé de l'arrière-plan changent hors de l'app : relues à chaque retour sur l'écran.
    var status by remember { mutableStateOf(viewModel.systemStatus()) }
    LifecycleResumeEffect(Unit) {
        status = viewModel.systemStatus()
        onPauseOrDispose { }
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 32.dp)) {
        // Club favori en tête : c'est le réglage qui change le plus l'app.
        FavoriteCard(s.favorite, onPickFavorite, onRemove = { viewModel.clearFavorite() })
        SettingsGroup {
            SettingSwitch(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.club_theme),
                subtitle = stringResource(if (s.favorite?.color != null) R.string.club_theme_hint else R.string.club_theme_unavailable),
                checked = s.clubTheme,
                enabled = s.favorite?.color != null,
                onChange = { viewModel.clubTheme(it) },
            )
        }

        // Ligue favorite : ouverte par défaut dans l'onglet Ligues.
        SettingsGroup {
            SettingChoice(
                Icons.Default.SportsSoccer,
                stringResource(R.string.favorite_league),
                stringResource(R.string.favorite_league_hint),
            ) {
                LeaguePicker(s.favoriteLeague) { slug -> viewModel.favoriteLeague(slug) }
            }
        }

        // Notifications : alerte d'abord si Android les bloque, puis les interrupteurs et la santé du suivi.
        SectionHeader(stringResource(R.string.settings_notifications))
        SettingsGroup {
            if (!status.notificationsAllowed) {
                BlockedNotifications(onAllow = openNotificationSettings)
                SegmentDivider()
            }
            SettingSwitch(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.notify_kickoff),
                subtitle = stringResource(R.string.notify_kickoff_hint),
                checked = s.notifyKickoff,
                onChange = {
                    if (it) askPermission(permission::launch)
                    viewModel.notifications(it, s.notifyGoals)
                },
            )
            SegmentDivider()
            SettingSwitch(
                icon = Icons.Default.SportsSoccer,
                title = stringResource(R.string.notify_goals),
                subtitle = stringResource(R.string.notify_goals_hint),
                checked = s.notifyGoals,
                onChange = {
                    if (it) askPermission(permission::launch)
                    viewModel.notifications(s.notifyKickoff, it)
                },
            )
            SegmentDivider()
            BackgroundHealthRow(status)
        }

        // Clubs suivis : chacun ses notifications.
        SectionHeader(stringResource(R.string.followed_clubs))
        SettingsGroup {
            val followed = s.followed.filter { it.club.teamId != s.favorite?.teamId }
            if (followed.isEmpty()) {
                Text(
                    stringResource(R.string.followed_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            followed.forEachIndexed { i, f ->
                if (i > 0) SegmentDivider()
                FollowedRow(
                    f,
                    onKickoff = { on ->
                        if (on) askPermission(permission::launch)
                        viewModel.followedNotifications(f.club.teamId, on, f.notifyGoals)
                    },
                    onGoals = { on ->
                        if (on) askPermission(permission::launch)
                        viewModel.followedNotifications(f.club.teamId, f.notifyKickoff, on)
                    },
                    onRemove = { viewModel.unfollow(f.club.teamId) },
                )
            }
            SegmentDivider()
            TextButton(onClick = onPickFavorite, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_club))
            }
        }

        SectionHeader(stringResource(R.string.settings_appearance))
        SettingsGroup {
            SettingChoice(Icons.Default.Brightness6, stringResource(R.string.settings_theme)) {
                Choice(
                    options = ThemeMode.entries,
                    selected = s.theme,
                    label = { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) },
                    onSelect = { viewModel.theme(it) },
                )
            }
            SegmentDivider()
            SettingChoice(Icons.Default.Language, stringResource(R.string.settings_language)) {
                Choice(
                    options = AppLanguage.entries,
                    selected = s.language,
                    label = { stringResource(when (it) { AppLanguage.SYSTEM -> R.string.theme_system; AppLanguage.FR -> R.string.lang_fr; AppLanguage.EN -> R.string.lang_en }) },
                    onSelect = { viewModel.language(it) },
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_about))
        SettingsGroup {
            SettingRow(Icons.Default.Info, stringResource(R.string.app_name), stringResource(R.string.app_version, viewModel.version))
            SegmentDivider()
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** Carte du club favori, teintée de sa couleur ; invite à en choisir un s'il n'y en a pas. */
@Composable
private fun FavoriteCard(fav: FavoriteClub?, onPick: () -> Unit, onRemove: () -> Unit) {
    val tint = parseHex(fav?.color) ?: MaterialTheme.colorScheme.primary
    AppCard(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent)))
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.favorite_club).uppercase(),
                style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fav != null) {
                    TeamLogo(fav.logo, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fav.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.favorite_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        stringResource(R.string.no_favorite_yet), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPick) {
                    Text(stringResource(if (fav != null) R.string.change_club else R.string.choose_club))
                }
                if (fav != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

/** Bandeau d'alerte : Android bloque les notifications de l'app. */
@Composable
private fun BlockedNotifications(onAllow: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().background(error.copy(alpha = 0.10f)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Default.NotificationsOff, error)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notifications_blocked), style = MaterialTheme.typography.titleSmall, color = error)
            Text(stringResource(R.string.notifications_blocked_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onAllow) { Text(stringResource(R.string.open_settings)) }
    }
}

/** Santé du suivi en arrière-plan : dernière vérification (verte si récente) et dernier échec réseau. */
@Composable
private fun BackgroundHealthRow(status: SystemStatus) {
    val now = rememberNow(30_000)
    val health = status.health
    val fresh = health.lastCheck > 0 && now - health.lastCheck < STALE_CHECK_MS
    val color = if (fresh) LocalSportColors.current.positive else LocalSportColors.current.imminent
    val subtitle = buildList {
        add(
            if (health.lastCheck == 0L) stringResource(R.string.background_never)
            else stringResource(R.string.background_last_check, relativeOrNow(health.lastCheck, now)),
        )
        // Échec récent seulement : un vieil incident n'inquiète personne.
        if (health.lastFailure > 0 && now - health.lastFailure < STALE_CHECK_MS) {
            add(stringResource(R.string.background_failure, relativeOrNow(health.lastFailure, now)))
        }
        add(stringResource(R.string.background_hint))
    }.joinToString("\n")
    SettingRow(Icons.Default.Sync, stringResource(R.string.background_tracking), subtitle, iconTint = color) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
}

/** « à l'instant » sous une minute, sinon « il y a 5 minutes », dans la langue de l'app. */
@Composable
private fun relativeOrNow(time: Long, now: Long): String =
    if (now - time < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.background_just_now)
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

/** Un club suivi : logo, nom, interrupteurs de notifications (coup d'envoi, buts) et retrait. */
@Composable
private fun FollowedRow(f: FollowedClub, onKickoff: (Boolean) -> Unit, onGoals: (Boolean) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(f.club.logo, size = 36.dp)
            Spacer(Modifier.width(14.dp))
            Text(f.club.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.unfollow_club), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(start = 50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = f.notifyKickoff, onClick = { onKickoff(!f.notifyKickoff) },
                label = { Text(stringResource(R.string.notif_kickoff_short)) },
                leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = f.notifyGoals, onClick = { onGoals(!f.notifyGoals) },
                label = { Text(stringResource(R.string.notif_goals_short)) },
                leadingIcon = { Icon(Icons.Default.NotificationsActive, null, Modifier.size(16.dp)) },
            )
        }
    }
}

/** Intitulé de section : petit, en capitales, pour laisser la vedette aux réglages. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Groupe de réglages : une carte. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), content = content)
}

/** Pastille d'icône : fond teinté arrondi, comme les réglages système récents. */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Ligne de réglage : icône, titre, sous-titre et élément de fin (interrupteur, pastille…). */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(icon, title, subtitle, enabled = enabled) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Réglage à choix multiple : icône et titre, puis les options en boutons segmentés. */
@Composable
private fun SettingChoice(icon: ImageVector, title: String, subtitle: String? = null, choice: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        SettingRow(icon, title, subtitle)
        Box(Modifier.padding(horizontal = 16.dp)) { choice() }
    }
}

/** Les 5 championnats en pastilles ; toucher la ligue choisie la retire. */
@Composable
private fun LeaguePicker(selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Competitions.LEAGUES.forEach { league ->
            val isSelected = league.slug == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else league.slug) },
                label = { Text(league.name) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
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
