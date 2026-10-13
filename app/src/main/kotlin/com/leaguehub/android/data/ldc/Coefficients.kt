package com.leaguehub.android.data.ldc

import com.leaguehub.android.domain.standings.ClubCoefficient

/* =========================================================================
   Coefficients clubs UEFA pour la saison 2026/27 (portage de `coefficients2026.js`).
   Sert UNIQUEMENT au dernier critère de départage de l'article 18.01 (10. coefficient
   club le plus élevé) et à l'annexe D.8.

   Annexe D.4 : le coefficient est la somme des 5 saisons (2021/22 à 2025/26) ou 20 % du
   coefficient de l'association, le plus élevé des deux.

   Source : kassiesa.net « UEFA 5-year Club Ranking 2026 », relevé le 2026-09-10.
   ⚠ Source NON officielle, non recoupée avec uefa.com.
   ========================================================================= */

data class ClubCoefficientEntry(
    val name: String,
    val country: String,
    val sum: Double,
    val countryPart: Double,
    val seasons: Map<String, Double?>,
) {
    /** Annexe D.4 : max(somme des saisons, part de l'association). */
    val coefficient: Double get() = maxOf(sum, countryPart)
    fun toEngine() = ClubCoefficient(coefficient, countryPart, seasons)
}

object Coefficients2026 {
    const val SEASON = "2026/27"
    const val RETRIEVED = "2026-09-10"
    const val SOURCE = "https://kassiesa.net/uefa/data/method5/trank2026.html"
    const val OFFICIAL = false

    val SEASONS = listOf("2021/22", "2022/23", "2023/24", "2024/25", "2025/26")

    private fun c(name: String, country: String, sum: Double, countryPart: Double, vararg s: Double?) =
        ClubCoefficientEntry(name, country, sum, countryPart, SEASONS.zip(s.toList()).toMap())

    val CLUBS: Map<String, ClubCoefficientEntry> = mapOf(
        "83" to c("FC Barcelona", "Esp", 113.25, 19.409, 15.0, 9.0, 23.0, 36.25, 30.0),
        "86" to c("Real Madrid", "Esp", 144.5, 19.409, 30.0, 29.0, 34.0, 24.5, 27.0),
        "102" to c("Villarreal", "Esp", 59.0, 19.409, 24.0, 12.0, 16.0, null, 7.0),
        "104" to c("AS Roma", "Ita", 97.75, 19.989, 23.0, 22.0, 21.0, 14.5, 17.25),
        "110" to c("Internazionale", "Ita", 127.0, 19.989, 18.0, 29.0, 20.0, 40.25, 19.75),
        "114" to c("Napoli", "Ita", 63.0, 19.989, 9.0, 25.0, 17.0, null, 12.0),
        "124" to c("Borussia Dortmund", "Ger", 100.75, 18.58, 10.0, 18.0, 29.0, 27.75, 16.0),
        "132" to c("Bayern München", "Ger", 147.5, 18.58, 26.0, 27.0, 28.0, 27.25, 39.25),
        "134" to c("VfB Stuttgart", "Ger", 27.5, 18.58, null, null, null, 13.0, 14.5),
        "142" to c("Feyenoord", "Ned", 71.0, 13.585, 24.0, 17.0, 8.0, 18.0, 4.0),
        "148" to c("PSV Eindhoven", "Ned", 71.25, 13.585, 10.0, 11.0, 17.0, 21.25, 12.0),
        "160" to c("Paris Saint-Germain", "Fra", 132.0, 16.699, 19.0, 19.0, 23.0, 33.5, 37.5),
        "166" to c("Lille OSC", "Fra", 68.75, 16.699, 17.0, null, 17.0, 24.0, 10.75),
        "175" to c("RC Lens", "Fra", 12.5, 16.699, null, null, 10.0, 2.5, null),
        "244" to c("Real Betis", "Esp", 74.5, 19.409, 11.0, 16.0, 6.0, 19.25, 22.25),
        "359" to c("Arsenal", "Eng", 119.0, 23.903, null, 17.0, 22.0, 36.0, 44.0),
        "360" to c("Manchester United", "Eng", 76.5, 23.903, 18.0, 19.0, 7.0, 32.5, null),
        "362" to c("Aston Villa", "Eng", 83.0, 23.903, null, null, 17.0, 30.25, 35.75),
        "364" to c("Liverpool", "Eng", 130.0, 23.903, 33.0, 19.0, 20.0, 29.5, 28.5),
        "382" to c("Manchester City", "Eng", 125.5, 23.903, 27.0, 33.0, 28.0, 14.75, 22.75),
        "432" to c("Galatasaray", "Tur", 53.5, 10.375, 15.0, null, 8.0, 12.75, 17.75),
        "436" to c("Fenerbahçe", "Tur", 57.75, 10.375, 5.0, 17.0, 14.0, 11.25, 10.5),
        "437" to c("FC Porto", "Por", 80.75, 14.633, 10.0, 18.0, 19.0, 9.75, 24.0),
        "493" to c("Shakhtar Donetsk", "Ukr", 56.25, 5.182, 6.0, 11.0, 10.0, 11.0, 18.25),
        "494" to c("Slavia Praha", "Cze", 44.0, 9.705, 10.0, 6.0, 15.0, 4.0, 9.0),
        "510" to c("Viking Stavanger", "Nor", 4.5, 8.247, null, 2.5, null, null, 2.0),
        "521" to c("Slovan Bratislava", "Svk", 36.0, 4.475, 6.0, 12.0, 8.0, 6.0, 4.0),
        "570" to c("Club Brugge", "Bel", 75.25, 12.45, 7.0, 17.0, 21.0, 15.75, 14.5),
        "887" to c("AEK Athens", "Gre", 24.0, 9.682, 1.5, null, 3.0, 2.0, 17.5),
        "1068" to c("Atlético Madrid", "Esp", 104.75, 19.409, 19.0, 8.0, 24.0, 26.5, 27.25),
        "2250" to c("Sporting CP Lisbon", "Por", 84.0, 14.633, 16.0, 14.0, 12.0, 14.5, 27.5),
        "2572" to c("Como", "Ita", 0.0, 19.989, null, null, null, null, null),
        "2980" to c("Bodø/Glimt", "Nor", 64.0, 8.247, 15.0, 3.0, 8.0, 21.0, 17.0),
        "4411" to c("LASK", "Aut", 21.0, 6.77, 15.0, null, 3.0, 3.0, null),
        "11420" to c("RB Leipzig", "Ger", 61.0, 18.58, 17.0, 18.0, 18.0, 8.0, null),
        "21922" to c("Sabah FK", "Azb", 6.0, 4.587, null, null, 2.0, 2.0, 2.0),
    )

    val ENGINE: Map<String, ClubCoefficient> = CLUBS.mapValues { it.value.toEngine() }
}
