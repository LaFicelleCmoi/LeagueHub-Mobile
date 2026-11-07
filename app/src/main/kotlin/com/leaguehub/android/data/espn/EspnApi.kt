package com.leaguehub.android.data.espn

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API publique (non officielle) d'ESPN. Base : https://site.api.espn.com/apis/ */
interface EspnApi {
    @GET("v2/sports/soccer/{code}/standings")
    suspend fun standings(@Path("code") code: String): EspnStandingsResponse

    /** [dates] : un jour « AAAAMMJJ » ou un mois « AAAAMM ». */
    @GET("site/v2/sports/soccer/{code}/scoreboard")
    suspend fun scoreboard(
        @Path("code") code: String,
        @Query("dates") dates: String,
        @Query("limit") limit: Int = 500,
    ): EspnScoreboardResponse

    @GET("site/v2/sports/soccer/{code}/statistics")
    suspend fun statistics(@Path("code") code: String): EspnStatisticsResponse

    @GET("site/v2/sports/soccer/{code}/news")
    suspend fun news(@Path("code") code: String, @Query("limit") limit: Int = 12): EspnNewsResponse

    /** Calendrier d'un club toutes compétitions ; [fixture] = true pour les matchs à venir. */
    @GET("site/v2/sports/soccer/all/teams/{teamId}/schedule")
    suspend fun teamSchedule(
        @Path("teamId") teamId: String,
        @Query("fixture") fixture: Boolean? = null,
        @Query("season") season: Int? = null,
    ): EspnScheduleResponse

    @GET("site/v2/sports/soccer/{code}/summary")
    suspend fun summary(@Path("code") code: String, @Query("event") eventId: String): EspnSummaryResponse

    /** Fiche d'un joueur : servie par un autre hôte ESPN, d'où l'URL complète. */
    @GET("$ATHLETES_URL{id}")
    suspend fun athlete(@Path("id") athleteId: String): EspnAthleteResponse

    /** Ses matchs de championnat de la saison en cours, avec ses stats dans chacun. */
    @GET("$ATHLETES_URL{id}/gamelog")
    suspend fun athleteGamelog(@Path("id") athleteId: String): EspnGamelogResponse

    companion object {
        const val BASE_URL = "https://site.api.espn.com/apis/"
        const val ATHLETES_URL = "https://site.web.api.espn.com/apis/common/v3/sports/soccer/athletes/"
    }
}
