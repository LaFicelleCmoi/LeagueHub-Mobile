package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.MatchState
import java.text.Collator
import java.util.Locale

/* =========================================================================
   RankLeaguePhase.kt — Classement de la phase de ligue (UEFA Champions League)
   -------------------------------------------------------------------------
   Portage Kotlin de `standings-engine.js` (Tracker LDC 2026-2027).
   LOGIQUE PURE : ni Android, ni réseau, ni ESPN. Des matchs entrent, un
   classement sort.

   Référence : Règlement de l'UEFA Champions League 2026/27
     - article 18.01 « Égalité de points – phase de ligue »
     - annexe D.4 (coefficient club) et D.8 (coefficients égaux)

   ▸ PHASE EN COURS (avant la fin de la dernière journée)
       points, puis 1. différence de buts · 2. buts marqués · 3. buts marqués à
       l'extérieur · 4. victoires · 5. victoires à l'extérieur.
       Toujours à égalité : même rang, ordre alphabétique.

   ▸ PHASE TERMINÉE (tous les matchs de la phase joués)
       les cinq ci-dessus, puis
        6. points cumulés par les adversaires affrontés
        7. différence de buts cumulée des adversaires
        8. buts marqués cumulés par les adversaires
        9. total de points disciplinaires le plus BAS
       10. coefficient club le plus élevé (annexe D.4), puis annexe D.8 :
           saison la plus récente, puis les précédentes, puis l'association.

   La confrontation directe n'est PAS dans l'article 18.01 : elle n'existe ici
   qu'en option explicitement NON UEFA (`headToHead = true`).

   « On ne saute jamais un critère » : si une donnée manque pour départager un
   groupe, le classement de ce groupe s'arrête là et ses lignes sont marquées
   `unresolved`.
   ========================================================================= */

const val MATCHES_PER_TEAM = 8

data class PhaseSide(
    val teamId: String,
    val name: String,
    val score: Int?,
    val abbr: String = "",
    val logo: String = "",
)

/** Un match de phase de ligue (le filtrage du tour appartient à la couche de données). */
data class PhaseMatch(
    val id: String,
    val state: MatchState,
    /** Coup d'envoi (ms), sert à l'ordre de la forme. */
    val time: Long,
    val home: PhaseSide,
    val away: PhaseSide,
)

/** Club à faire figurer même sans match joué. */
data class SeedClub(val id: String, val name: String, val abbr: String = "", val logo: String = "")

data class ClubCoefficient(
    val coefficient: Double,
    val countryPart: Double? = null,
    /** Points par saison ; une saison sans participation vaut 0 (annexe D.4). */
    val seasons: Map<String, Double?> = emptyMap(),
)

data class RankOptions(
    val seed: List<SeedClub> = emptyList(),
    /** Force l'état de la phase ; sinon déduit des matchs. */
    val complete: Boolean? = null,
    val teamsCount: Int? = null,
    /** Critère 9 : points disciplinaires par équipe. */
    val disciplinary: Map<String, Int>? = null,
    /** Critère 10. */
    val coefficients: Map<String, ClubCoefficient>? = null,
    /** Saisons de référence, de la plus ancienne à la plus récente (annexe D.8). */
    val coefficientSeasons: List<String> = emptyList(),
    /** Option NON UEFA. */
    val headToHead: Boolean = false,
)

enum class FormResult { W, D, L }

data class TeamResultLine(
    val matchId: String,
    val time: Long,
    val oppId: String,
    val home: Boolean,
    val gf: Int,
    val ga: Int,
    val result: FormResult,
)

/** Statistiques d'une équipe, enrichies de sa place après classement. */
class LeaguePhaseRow internal constructor(
    val teamId: String,
    var name: String,
    var abbr: String,
    var logo: String,
) {
    internal var sortName: String = ""
    var played = 0; internal set
    var wins = 0; internal set
    var draws = 0; internal set
    var losses = 0; internal set
    var goalsFor = 0; internal set
    var goalsAgainst = 0; internal set
    var goalDiff = 0; internal set
    var points = 0; internal set
    var awayGoalsFor = 0; internal set
    var awayWins = 0; internal set
    var oppPoints = 0; internal set
    var oppGoalDiff = 0; internal set
    var oppGoalsFor = 0; internal set
    /** Au moins un match en cours compté provisoirement. */
    var live = false; internal set
    val results = mutableListOf<TeamResultLine>()
    /** 5 derniers résultats, du plus ancien au plus récent. */
    var form: List<FormResult> = emptyList(); internal set

    /** Rang de 1 à n (ordre d'affichage). */
    var pos = 0; internal set
    /** Rang officiel : PARTAGÉ en cas d'égalité persistante. */
    var rank = 0; internal set
    /** Critère qui l'a séparée de l'équipe juste au-dessus. */
    var decidedBy: String? = null; internal set
    var sharedRank = false; internal set
    var unresolved = false; internal set
    var missing: String? = null; internal set
}

data class LeaguePhaseTable(
    val rows: List<LeaguePhaseRow>,
    val complete: Boolean,
    val criteria: List<String>,
    val headToHead: Boolean,
) {
    fun row(teamId: String): LeaguePhaseRow? = rows.firstOrNull { it.teamId == teamId }
}

/* =======================================================================
   1. STATISTIQUES PAR ÉQUIPE
   ======================================================================= */

/** Un match compte dès qu'il a commencé : un match EN COURS compte de façon provisoire. */
private fun counted(m: PhaseMatch): Boolean =
    (m.state == MatchState.POST || m.state == MatchState.IN) && m.home.score != null && m.away.score != null

fun buildTeamStats(matches: List<PhaseMatch>, seed: List<SeedClub> = emptyList()): LinkedHashMap<String, LeaguePhaseRow> {
    val map = LinkedHashMap<String, LeaguePhaseRow>()
    fun row(id: String, name: String, abbr: String, logo: String): LeaguePhaseRow =
        map.getOrPut(id) { LeaguePhaseRow(id, name, abbr, logo) }.also { if (it.logo.isEmpty() && logo.isNotEmpty()) it.logo = logo }

    seed.forEach { if (it.id.isNotEmpty()) row(it.id, it.name, it.abbr, it.logo) }

    for (m in matches) {
        if (!counted(m)) continue
        val h = row(m.home.teamId, m.home.name, m.home.abbr, m.home.logo)
        val a = row(m.away.teamId, m.away.name, m.away.abbr, m.away.logo)
        // Le nom affiché reste celui de l'amorce ; le nom ESPN (anglais) sert à l'ordre alphabétique.
        if (m.home.name.isNotEmpty()) h.sortName = m.home.name
        if (m.away.name.isNotEmpty()) a.sortName = m.away.name

        val hg = m.home.score ?: 0
        val ag = m.away.score ?: 0
        h.played++; a.played++
        h.goalsFor += hg; h.goalsAgainst += ag
        a.goalsFor += ag; a.goalsAgainst += hg
        a.awayGoalsFor += ag // critère 3

        val (rh, ra) = when {
            hg > ag -> { h.wins++; a.losses++; h.points += 3; FormResult.W to FormResult.L }
            hg < ag -> { a.wins++; a.awayWins++; h.losses++; a.points += 3; FormResult.L to FormResult.W } // critère 5
            else -> { h.draws++; a.draws++; h.points += 1; a.points += 1; FormResult.D to FormResult.D }
        }
        if (m.state == MatchState.IN) { h.live = true; a.live = true }

        h.results += TeamResultLine(m.id, m.time, m.away.teamId, true, hg, ag, rh)
        a.results += TeamResultLine(m.id, m.time, m.home.teamId, false, ag, hg, ra)
    }

    for (t in map.values) {
        t.goalDiff = t.goalsFor - t.goalsAgainst
        if (t.sortName.isEmpty()) t.sortName = t.name
        t.results.sortBy { it.time }
        t.form = t.results.takeLast(5).map { it.result }
    }

    // Critères 6 à 8 : totaux de CHAQUE adversaire affronté, une fois tous les totaux connus.
    for (t in map.values) {
        t.oppPoints = 0; t.oppGoalDiff = 0; t.oppGoalsFor = 0
        val seen = HashSet<String>()
        for (r in t.results) {
            if (!seen.add(r.oppId)) continue
            val o = map[r.oppId] ?: continue
            t.oppPoints += o.points; t.oppGoalDiff += o.goalDiff; t.oppGoalsFor += o.goalsFor
        }
    }
    return map
}

/* =======================================================================
   2. CASCADE DE DÉPARTAGE
   ======================================================================= */

private class Ctx(
    val complete: Boolean,
    val headToHead: Boolean,
    val disciplinary: Map<String, Int>?,
    val coefficients: Map<String, ClubCoefficient>?,
    val coefficientSeasons: List<String>,
)

private class Criterion(
    val key: String,
    val betterHigh: Boolean = true,
    /** La valeur dépend du groupe (confrontation directe). */
    val subset: Boolean = false,
    val get: (LeaguePhaseRow, Ctx) -> Double? = { _, _ -> null },
)

private val CRIT: Map<String, Criterion> = listOf(
    Criterion("pts", get = { t, _ -> t.points.toDouble() }),
    Criterion("h2h", subset = true),
    Criterion("gd", get = { t, _ -> t.goalDiff.toDouble() }),
    Criterion("gf", get = { t, _ -> t.goalsFor.toDouble() }),
    Criterion("awayGf", get = { t, _ -> t.awayGoalsFor.toDouble() }),
    Criterion("w", get = { t, _ -> t.wins.toDouble() }),
    Criterion("awayW", get = { t, _ -> t.awayWins.toDouble() }),
    Criterion("oppPts", get = { t, _ -> t.oppPoints.toDouble() }),
    Criterion("oppGd", get = { t, _ -> t.oppGoalDiff.toDouble() }),
    Criterion("oppGf", get = { t, _ -> t.oppGoalsFor.toDouble() }),
    Criterion("disc", betterHigh = false, get = { t, c -> c.disciplinary?.get(t.teamId)?.toDouble() }),
    Criterion("coef", get = { t, c -> c.coefficients?.get(t.teamId)?.coefficient }),
).associateBy { it.key }

val CRITERIA_IN_PROGRESS = listOf("pts", "gd", "gf", "awayGf", "w", "awayW")
val CRITERIA_COMPLETE = CRITERIA_IN_PROGRESS + listOf("oppPts", "oppGd", "oppGf", "disc", "coef")

/** Annexe D.8 — coefficients égaux : saisons de la plus récente à la plus ancienne, puis l'association. */
private fun d8Criteria(ctx: Ctx): List<Criterion> =
    ctx.coefficientSeasons.reversed().map { season ->
        Criterion("coef:$season") { t, c ->
            val r = c.coefficients?.get(t.teamId) ?: return@Criterion null
            r.seasons[season] ?: 0.0
        }
    } + Criterion("coef:association") { t, c -> c.coefficients?.get(t.teamId)?.countryPart }

private fun criteriaFor(ctx: Ctx): List<Criterion> {
    val list = (if (ctx.complete) CRITERIA_COMPLETE else CRITERIA_IN_PROGRESS).map { CRIT.getValue(it) }.toMutableList()
    if (ctx.headToHead) list.add(1, CRIT.getValue("h2h"))
    if (ctx.complete) list += d8Criteria(ctx)
    return list
}

/** Option NON UEFA : points pris dans les matchs joués ENTRE les équipes du groupe. */
private fun h2hPoints(t: LeaguePhaseRow, group: List<LeaguePhaseRow>): Int {
    val ids = group.mapTo(HashSet()) { it.teamId }
    return t.results.filter { it.oppId in ids }.sumOf { r ->
        val pts: Int = when (r.result) { FormResult.W -> 3; FormResult.D -> 1; FormResult.L -> 0 }
        pts
    }
}

private fun metWithin(group: List<LeaguePhaseRow>): Boolean {
    val ids = group.mapTo(HashSet()) { it.teamId }
    return group.any { t -> t.results.any { it.oppId in ids } }
}

private fun valueOf(crit: Criterion, t: LeaguePhaseRow, group: List<LeaguePhaseRow>, ctx: Ctx): Double? {
    val v = if (crit.subset) h2hPoints(t, group).toDouble() else crit.get(t, ctx)
    return v?.takeUnless { it.isNaN() }
}

/** Répartit un groupe à égalité en paquets ordonnés selon UN critère ; null si une valeur manque. */
private fun partition(group: List<LeaguePhaseRow>, crit: Criterion, ctx: Ctx): List<List<LeaguePhaseRow>>? {
    val vals = group.map { t -> t to (valueOf(crit, t, group, ctx) ?: return null) }
    val sorted = if (crit.betterHigh) vals.sortedByDescending { it.second } else vals.sortedBy { it.second }
    val buckets = mutableListOf<Pair<Double, MutableList<LeaguePhaseRow>>>()
    for ((t, v) in sorted) {
        if (buckets.isEmpty() || buckets.last().first != v) buckets += v to mutableListOf()
        buckets.last().second += t
    }
    return buckets.map { it.second }
}

private val nameCollator: Collator = Collator.getInstance(Locale.ENGLISH).apply { strength = Collator.PRIMARY }

private class Entry(
    val team: LeaguePhaseRow,
    var sep: String?,
    val shared: Boolean = false,
    val unresolved: Boolean = false,
    val missing: String? = null,
)

/** Phase en cours, égalité persistante : rang partagé, ordre alphabétique. */
private fun equalRank(group: List<LeaguePhaseRow>): List<Entry> =
    group.sortedWith { a, b -> nameCollator.compare(a.sortName, b.sortName) }
        .mapIndexed { i, t -> Entry(t, if (i > 0) "alphabetical" else null, shared = i > 0) }

/** Donnée manquante ou critères épuisés : ordre déterministe par identifiant, lignes signalées. */
private fun unresolved(group: List<LeaguePhaseRow>, missing: String): List<Entry> =
    group.sortedBy { it.teamId }
        .mapIndexed { i, t -> Entry(t, if (i > 0) "unresolved" else null, shared = i > 0, unresolved = true, missing = missing) }

/**
 * Cascade : pour chaque critère, dans l'ordre, s'il sépare le groupe on obtient des paquets
 * ordonnés, et chaque paquet encore à égalité est départagé à partir du critère SUIVANT.
 * La confrontation directe (option) est réappliquée au sous-groupe restant.
 */
private fun rankGroup(group: List<LeaguePhaseRow>, list: List<Criterion>, from: Int, ctx: Ctx): List<Entry> {
    if (group.size == 1) return listOf(Entry(group[0], null))
    for (j in from until list.size) {
        val crit = list[j]
        if (crit.subset && !metWithin(group)) continue // jamais affrontées : critère sans objet
        val buckets = partition(group, crit, ctx) ?: return unresolved(group, crit.key)
        if (buckets.size == 1) continue
        val out = mutableListOf<Entry>()
        buckets.forEachIndexed { b, sub ->
            val next = if (crit.subset && sub.size > 1) j else j + 1
            val ranked = rankGroup(sub, list, next, ctx)
            if (b > 0) ranked[0].sep = crit.key
            out += ranked
        }
        return out
    }
    return if (ctx.complete) unresolved(group, "domestic-position") else equalRank(group)
}

fun isPhaseComplete(matches: List<PhaseMatch>, teamsCount: Int = 36): Boolean {
    val expected = teamsCount * MATCHES_PER_TEAM / 2
    if (matches.size < expected) return false
    return matches.all { it.state == MatchState.POST }
}

fun rankLeaguePhase(matches: List<PhaseMatch>, opts: RankOptions = RankOptions()): LeaguePhaseTable {
    val map = buildTeamStats(matches, opts.seed)
    val teamsCount = opts.teamsCount ?: opts.seed.size.takeIf { it > 0 } ?: 36
    val complete = opts.complete ?: isPhaseComplete(matches, teamsCount)
    val ctx = Ctx(complete, opts.headToHead, opts.disciplinary, opts.coefficients, opts.coefficientSeasons)

    val criteria = criteriaFor(ctx)
    val entries = rankGroup(map.values.toList(), criteria, 0, ctx)
    val rows = entries.mapIndexed { i, e ->
        e.team.apply {
            pos = i + 1
            decidedBy = e.sep
            sharedRank = e.shared
            unresolved = e.unresolved
            missing = e.missing
        }
    }
    rows.forEachIndexed { i, t -> t.rank = if (t.sharedRank && i > 0) rows[i - 1].rank else t.pos }
    return LeaguePhaseTable(rows, complete, criteria.map { it.key }, ctx.headToHead)
}

/* =======================================================================
   3. ZONES D'AFFICHAGE — séparé du tri : le moteur classe, ceci découpe.
   ======================================================================= */

enum class PhaseZone(val from: Int, val to: Int) {
    /** Huitièmes directs. */
    Q(1, 8),

    /** Barrages. */
    PO(9, 24),

    /** Éliminés. */
    OUT(25, 36),
}

fun zoneOf(pos: Int): PhaseZone? = PhaseZone.entries.firstOrNull { pos in it.from..it.to }

fun toZones(rows: List<LeaguePhaseRow>): Map<PhaseZone, List<LeaguePhaseRow>> =
    PhaseZone.entries.associateWith { z -> rows.filter { it.pos in z.from..z.to } }
