package com.leaguehub.android.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import com.leaguehub.android.ui.components.LeagueChip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.leaguehub.android.R
import com.leaguehub.android.domain.leagueconfig.CompetitionKind
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.ui.club.ClubScreen
import com.leaguehub.android.ui.home.HomeScreen
import com.leaguehub.android.ui.ldctracker.LdcScreen
import com.leaguehub.android.ui.league.LeagueScreen
import com.leaguehub.android.ui.matchdetail.MatchDetailScreen
import com.leaguehub.android.ui.search.SearchScreen
import com.leaguehub.android.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LEAGUE = "league/{slug}"
    const val LDC = "ldc"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val CLUB = "club/{league}/{teamId}"
    const val MATCH = "match/{competition}/{eventId}"

    fun league(slug: String) = "league/$slug"
    fun club(league: String, teamId: String) = "club/$league/$teamId"
    fun match(competition: String, eventId: String) = "match/$competition/$eventId"
}

private data class TopLevel(val route: String, val pattern: String, val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    var lastLeague by rememberSaveable { mutableStateOf(Competitions.LEAGUES.first().slug) }
    val topLevel = listOf(
        TopLevel(Routes.HOME, Routes.HOME, R.string.nav_home, Icons.Default.Home),
        TopLevel(Routes.league(lastLeague), Routes.LEAGUE, R.string.nav_leagues, Icons.Default.SportsSoccer),
        TopLevel(Routes.LDC, Routes.LDC, R.string.nav_ucl, Icons.Default.EmojiEvents),
        TopLevel(Routes.SEARCH, Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
        TopLevel(Routes.SETTINGS, Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
    )
    val entry by navController.currentBackStackEntryAsState()
    val pattern = entry?.destination?.route
    val isTopLevel = topLevel.any { it.pattern == pattern }

    fun openLeague(league: LeagueConfig) {
        if (league.kind == CompetitionKind.UCL) navController.navigate(Routes.LDC) { launchSingleTop = true }
        else navController.navigate(Routes.league(league.slug)) { launchSingleTop = true }
    }
    fun openTeam(league: String, teamId: String) = navController.navigate(Routes.club(league, teamId))
    fun openMatch(competition: String, eventId: String) = navController.navigate(Routes.match(competition, eventId))

    Scaffold(
        topBar = {
            // L'accueil dessine son propre grand en-tête.
            if (pattern != Routes.HOME) TopAppBar(
                title = {
                    val slug = entry?.arguments?.getString("slug")
                    Text(
                        when (pattern) {
                            Routes.LEAGUE -> Competitions.bySlug(slug.orEmpty())?.name ?: stringResource(R.string.nav_leagues)
                            Routes.LDC -> stringResource(R.string.ldc_title)
                            Routes.SEARCH -> stringResource(R.string.nav_search)
                            Routes.SETTINGS -> stringResource(R.string.nav_settings)
                            Routes.CLUB -> stringResource(R.string.club)
                            Routes.MATCH -> stringResource(R.string.match)
                            else -> stringResource(R.string.app_name)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (!isTopLevel) IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) FloatingNavBar {
                topLevel.forEach { item ->
                    PillNavItem(
                        item = item,
                        selected = pattern == item.pattern,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    onLeague = ::openLeague,
                    onTeam = ::openTeam,
                    onMatch = ::openMatch,
                    onPickFavorite = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.LEAGUE, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { backStack ->
                val slug = backStack.arguments?.getString("slug").orEmpty()
                LaunchedEffect(slug) { lastLeague = slug }
                Column(Modifier.fillMaxSize()) {
                    LeagueChips(slug) { league ->
                        navController.navigate(Routes.league(league.slug)) {
                            popUpTo(Routes.LEAGUE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    LeagueScreen(
                        onTeam = { teamId -> openTeam(slug, teamId) },
                        onMatch = { m: Match -> openMatch(m.competition, m.id) },
                    )
                }
            }
            composable(Routes.LDC) {
                LdcScreen(
                    onTeam = { teamId -> openTeam(Competitions.UCL.slug, teamId) },
                    onMatch = { m -> openMatch(m.competition, m.id) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onClub = { hit -> openTeam(hit.league.slug, hit.team.id) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onPickFavorite = { navController.navigate(Routes.SEARCH) })
            }
            composable(Routes.CLUB) {
                ClubScreen(onMatch = ::openMatch)
            }
            composable(Routes.MATCH, deepLinks = listOf(navDeepLink { uriPattern = "leaguehub://match/{competition}/{eventId}" })) {
                MatchDetailScreen()
            }
        }
    }
}

@Composable
private fun LeagueChips(selected: String, onSelect: (LeagueConfig) -> Unit) {
    LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Spacer(Modifier.width(6.dp)) }
        items(Competitions.LEAGUES, key = { it.slug }) { league ->
            LeagueChip(league, selected = league.slug == selected, onClick = { onSelect(league) })
        }
    }
}

/**
 * Onglet de la barre de navigation : quand il est actif, une pilule englobe
 * l'icône ET le libellé (l'indicateur M3 par défaut n'entoure que l'icône).
 */
@Composable
private fun RowScope.PillNavItem(item: TopLevel, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Box(
        // Hauteur fixe : un fillMaxHeight étirerait la barre sur tout l'écran.
        Modifier.weight(1f).height(60.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) colors.primary else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(item.icon, contentDescription = null, tint = content)
            Text(
                stringResource(item.label),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.sp, // « Championnats » doit tenir sur un cinquième d'écran
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Barre de navigation flottante : pilule arrondie détachée des bords, au-dessus de la barre système. */
@Composable
private fun FloatingNavBar(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}
