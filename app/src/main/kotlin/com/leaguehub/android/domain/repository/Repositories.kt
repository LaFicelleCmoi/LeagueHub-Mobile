package com.leaguehub.android.domain.repository

import com.leaguehub.android.domain.leagueconfig.LeagueConfig
import com.leaguehub.android.domain.model.Article
import com.leaguehub.android.domain.model.Cached
import com.leaguehub.android.domain.model.ClubPalmares
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.Leaders
import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.Standings
import com.leaguehub.android.domain.model.Team
import com.leaguehub.android.domain.model.TeamForm
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux données de football. L'UI ne connaît que les modèles du domaine.
 * Offline-first : les flux observent la base locale (single source of truth), les `refresh*`
 * la mettent à jour depuis le réseau. Une erreur réseau ne vide jamais la base.
 */
interface FootballRepository {
    fun standings(league: LeagueConfig): Flow<Cached<Standings>?>
    suspend fun refreshStandings(league: LeagueConfig)

    /** Derniers résultats et 14 prochains jours, consultables hors ligne. */
    fun calendar(league: LeagueConfig): Flow<List<Match>>
    suspend fun refreshCalendar(league: LeagueConfig)

    /** Matchs d'hier et d'aujourd'hui, lus en direct (et recopiés en base). */
    suspend fun liveMatches(league: LeagueConfig): List<Match>

    suspend fun leaders(league: LeagueConfig): Cached<Leaders>
    suspend fun news(league: LeagueConfig): Cached<List<Article>>

    /** Toute la saison de Ligue des champions (phase de ligue + phase finale), en base. */
    fun uclSeason(): Flow<Cached<List<Match>>?>
    suspend fun refreshUclSeason()

    suspend fun teamForm(teamId: String, league: LeagueConfig): TeamForm
    suspend fun matchDetail(competitionCode: String, eventId: String): MatchDetail

    /** Clubs connus (classements en base + qualifiés LDC) dont le nom contient [query]. */
    suspend fun searchClubs(query: String): List<ClubHit>
    suspend fun palmares(league: LeagueConfig, teamId: String): ClubPalmares?
}

data class ClubHit(val team: Team, val league: LeagueConfig)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) { SYSTEM(""), FR("fr"), EN("en") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val favorite: FavoriteClub? = null,
    /** Couleurs de l'app aux couleurs du club favori. */
    val clubTheme: Boolean = false,
    val notifyKickoff: Boolean = true,
    val notifyGoals: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setFavorite(club: FavoriteClub?)
    suspend fun setClubTheme(enabled: Boolean)
    suspend fun setNotifications(kickoff: Boolean, goals: Boolean)
}
