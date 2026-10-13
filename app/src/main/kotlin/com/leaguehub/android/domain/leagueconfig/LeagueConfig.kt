package com.leaguehub.android.domain.leagueconfig

// Configuration des compétitions (portage de `src/lib/leagues.ts`).
// Comme sur le web, le seul paramètre qui change la compétition est le slug ESPN :
// ajouter un championnat = ajouter UNE entrée ici (ex. `ned.1` pour l'Eredivisie).

enum class CompetitionKind {
    /** Championnat national : classement ESPN, journées. */
    LEAGUE,

    /** Ligue des champions : phase de ligue à 36 classée par le moteur UEFA. */
    UCL,
}

data class LeagueConfig(
    /** Identifiant stable dans l'app (routes, cache, favoris). */
    val slug: String,
    /** Identifiant de la compétition dans l'API ESPN. */
    val espnCode: String,
    val name: String,
    /** Code pays ISO (pour l'affichage). */
    val country: String,
    /** Identifiant du logo sur le CDN d'ESPN. */
    val logoId: Int,
    /** Couleur d'accent (ARGB). */
    val accent: Long,
    val kind: CompetitionKind = CompetitionKind.LEAGUE,
) {
    fun logo(dark: Boolean = false): String {
        val folder = if (dark) "500-dark" else "500"
        return "https://a.espncdn.com/i/leaguelogos/soccer/$folder/$logoId.png"
    }
}

object Competitions {
    val PREMIER_LEAGUE = LeagueConfig("premier-league", "eng.1", "Premier League", "GB", 23, 0xFF7C3AED)
    val LA_LIGA = LeagueConfig("la-liga", "esp.1", "La Liga", "ES", 15, 0xFFEA580C)
    val SERIE_A = LeagueConfig("serie-a", "ita.1", "Serie A", "IT", 12, 0xFF0284C7)
    val BUNDESLIGA = LeagueConfig("bundesliga", "ger.1", "Bundesliga", "DE", 10, 0xFFDC2626)
    val LIGUE_1 = LeagueConfig("ligue-1", "fra.1", "Ligue 1", "FR", 9, 0xFF16A34A)
    val UCL = LeagueConfig("ldc", "uefa.champions", "Ligue des champions", "EU", 2, 0xFF1E3A8A, CompetitionKind.UCL)

    /** Les 5 grands championnats, dans l'ordre d'affichage. */
    val LEAGUES: List<LeagueConfig> = listOf(PREMIER_LEAGUE, LA_LIGA, SERIE_A, BUNDESLIGA, LIGUE_1)

    /** Les 6 compétitions de l'app. */
    val ALL: List<LeagueConfig> = LEAGUES + UCL

    fun bySlug(slug: String): LeagueConfig? = ALL.firstOrNull { it.slug == slug }
    fun byEspnCode(code: String): LeagueConfig? = ALL.firstOrNull { it.espnCode == code }
}
