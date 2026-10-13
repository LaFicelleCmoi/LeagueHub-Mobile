package com.leaguehub.android.data.ldc

import com.leaguehub.android.data.espn.teamLogo
import com.leaguehub.android.domain.standings.SeedClub

/* Tirage officiel UEFA de la phase de ligue 2026-2027 — 27 août 2026 (portage de `draw2026.js`).
   Les identifiants sont ceux des clubs ESPN. La qualification reste une donnée UEFA : l'API ESPN
   ne publie pas toujours la saison au moment du tirage, d'où ce repli. */

data class QualifiedClub(
    val pot: Int,
    val id: String,
    val name: String,
    val abbr: String,
    /** Couleur principale (hex sans #). */
    val color: String,
    val country: String,
) {
    val logo: String get() = teamLogo(id)
    fun toSeed() = SeedClub(id, name, abbr, logo)
}

data class DrawOpponents(val home: List<String>, val away: List<String>)

object Draw2026 {
    const val PUBLISHED = "2026-08-27"
    const val SOURCE_URL =
        "https://www.uefa.com/uefachampionsleague/news/02a8-216cd740d41f-fd3b45ac4a0f-1000--champions-league-league-phase-draw-all-36-teams-learn-their/"

    val QUALIFIED: List<QualifiedClub> = listOf(
        QualifiedClub(1, "160", "Paris Saint-Germain", "PSG", "011F68", "FRA"),
        QualifiedClub(1, "132", "Bayern München", "MUN", "dc052d", "GER"),
        QualifiedClub(1, "86", "Real Madrid", "RMA", "ffffff", "ESP"),
        QualifiedClub(1, "364", "Liverpool", "LIV", "d11317", "ENG"),
        QualifiedClub(1, "110", "Inter", "INT", "00239c", "ITA"),
        QualifiedClub(1, "382", "Manchester City", "MNC", "99c5ea", "ENG"),
        QualifiedClub(1, "359", "Arsenal", "ARS", "e20520", "ENG"),
        QualifiedClub(1, "83", "Barcelona", "BAR", "990000", "ESP"),
        QualifiedClub(1, "1068", "Atlético de Madrid", "ATM", "ca3624", "ESP"),
        QualifiedClub(2, "124", "Borussia Dortmund", "DOR", "ffee00", "GER"),
        QualifiedClub(2, "104", "AS Roma", "ROMA", "990a2c", "ITA"),
        QualifiedClub(2, "2250", "Sporting CP", "SCP", "008127", "POR"),
        QualifiedClub(2, "362", "Aston Villa", "AVL", "660e36", "ENG"),
        QualifiedClub(2, "437", "FC Porto", "FCP", "0000dd", "POR"),
        QualifiedClub(2, "360", "Manchester United", "MAN", "da020e", "ENG"),
        QualifiedClub(2, "570", "Club Brugge", "BRU", "0081ff", "BEL"),
        QualifiedClub(2, "244", "Real Betis", "BET", "288A00", "ESP"),
        QualifiedClub(2, "148", "PSV Eindhoven", "PSV", "ef2f24", "NED"),
        QualifiedClub(3, "142", "Feyenoord", "FEY", "ef2f24", "NED"),
        QualifiedClub(3, "166", "Lille", "LILL", "c2051b", "FRA"),
        QualifiedClub(3, "2980", "Bodø/Glimt", "BODO", "FCEE33", "NOR"),
        QualifiedClub(3, "114", "Napoli", "NAP", "0677d2", "ITA"),
        QualifiedClub(3, "11420", "RB Leipzig", "RBL", "ffffff", "GER"),
        QualifiedClub(3, "102", "Villarreal", "VIL", "ffff00", "ESP"),
        QualifiedClub(3, "436", "Fenerbahçe", "FEN", "ffff00", "TUR"),
        QualifiedClub(3, "493", "Shakhtar Donetsk", "SHK", "ff5900", "UKR"),
        QualifiedClub(3, "432", "Galatasaray", "GAL", "aa0031", "TUR"),
        QualifiedClub(4, "494", "Slavia Prague", "SLP", "dc1f26", "CZE"),
        QualifiedClub(4, "521", "Slovan Bratislava", "SLB", "81c0ff", "SVK"),
        QualifiedClub(4, "134", "VfB Stuttgart", "VFB", "ffffff", "GER"),
        QualifiedClub(4, "887", "AEK Athens", "AEK", "ffff00", "GRE"),
        QualifiedClub(4, "4411", "LASK", "LAS", "ffffff", "AUT"),
        QualifiedClub(4, "2572", "Como", "COMO", "4169E1", "ITA"),
        QualifiedClub(4, "175", "Lens", "RCL", "E91514", "FRA"),
        QualifiedClub(4, "510", "Viking", "VIK", "000080", "NOR"),
        QualifiedClub(4, "21922", "Sabah", "SAB", "000000", "AZE"),
    )

    private val byId = QUALIFIED.associateBy { it.id }
    fun club(id: String): QualifiedClub? = byId[id]

    val SEED: List<SeedClub> = QUALIFIED.map { it.toSeed() }

    /** Les 8 adversaires de chaque club : 4 à domicile, 4 à l'extérieur. */
    val OPPONENTS: Map<String, DrawOpponents> = mapOf(
        "887" to o(listOf(86, 104, 432, 4411), listOf(382, 124, 493, 2572)),
        "359" to o(listOf(86, 124, 166, 21922), listOf(132, 244, 114, 494)),
        "362" to o(listOf(160, 124, 436, 510), listOf(83, 570, 432, 494)),
        "1068" to o(listOf(132, 360, 436, 510), listOf(364, 148, 2980, 134)),
        "124" to o(listOf(110, 244, 102, 887), listOf(359, 362, 2980, 21922)),
        "83" to o(listOf(382, 362, 142, 2572), listOf(160, 2250, 432, 21922)),
        "132" to o(listOf(359, 244, 2980, 494), listOf(1068, 360, 166, 510)),
        "2980" to o(listOf(1068, 124, 166, 4411), listOf(132, 570, 114, 175)),
        "570" to o(listOf(364, 362, 2980, 175), listOf(110, 148, 114, 134)),
        "2572" to o(listOf(160, 360, 11420, 887), listOf(83, 244, 142, 175)),
        "436" to o(listOf(364, 104, 102, 494), listOf(1068, 362, 493, 4411)),
        "142" to o(listOf(110, 437, 11420, 2572), listOf(83, 244, 432, 510)),
        "432" to o(listOf(83, 362, 142, 134), listOf(160, 2250, 166, 887)),
        "110" to o(listOf(364, 570, 493, 134), listOf(86, 124, 142, 521)),
        "4411" to o(listOf(364, 437, 436, 521), listOf(86, 2250, 2980, 887)),
        "11420" to o(listOf(382, 148, 493, 175), listOf(86, 360, 142, 2572)),
        "175" to o(listOf(382, 2250, 2980, 2572), listOf(364, 570, 11420, 494)),
        "166" to o(listOf(132, 244, 432, 521), listOf(359, 104, 2980, 134)),
        "364" to o(listOf(1068, 437, 102, 175), listOf(110, 570, 436, 4411)),
        "382" to o(listOf(160, 2250, 114, 887), listOf(83, 437, 11420, 175)),
        "360" to o(listOf(132, 104, 11420, 21922), listOf(1068, 2250, 102, 2572)),
        "114" to o(listOf(359, 570, 2980, 510), listOf(382, 437, 102, 21922)),
        "160" to o(listOf(83, 104, 432, 521), listOf(382, 362, 102, 2572)),
        "437" to o(listOf(382, 148, 114, 494), listOf(364, 244, 142, 4411)),
        "148" to o(listOf(1068, 570, 493, 134), listOf(86, 437, 11420, 510)),
        "244" to o(listOf(359, 437, 142, 2572), listOf(132, 124, 166, 521)),
        "86" to o(listOf(110, 148, 11420, 4411), listOf(359, 104, 493, 887)),
        "104" to o(listOf(86, 2250, 166, 521), listOf(160, 360, 436, 887)),
        "521" to o(listOf(110, 244, 493, 134), listOf(160, 104, 166, 4411)),
        "21922" to o(listOf(83, 124, 114, 494), listOf(359, 360, 102, 510)),
        "493" to o(listOf(86, 2250, 436, 887), listOf(110, 148, 11420, 521)),
        "494" to o(listOf(359, 362, 102, 175), listOf(132, 437, 436, 21922)),
        "2250" to o(listOf(83, 360, 432, 4411), listOf(382, 104, 493, 175)),
        "134" to o(listOf(1068, 570, 166, 510), listOf(110, 148, 432, 521)),
        "510" to o(listOf(132, 148, 142, 21922), listOf(1068, 362, 114, 134)),
        "102" to o(listOf(160, 360, 114, 21922), listOf(364, 124, 436, 494)),
    )

    private fun o(home: List<Int>, away: List<Int>) = DrawOpponents(home.map(Int::toString), away.map(Int::toString))
}
