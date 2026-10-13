package com.leaguehub.android.domain.knockout

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Team

// Phase finale (portage de `buildBracket` / `aggregateTie` d'espn.js) : les matchs à élimination
// directe sont regroupés par tour, puis par paire d'équipes (une ou deux manches).

enum class Round {
    QUALIFYING, LEAGUE, PLAYOFF, R16, QF, SF, FINAL;

    val isKnockout: Boolean get() = this != QUALIFYING && this != LEAGUE

    companion object {
        val KNOCKOUT_ORDER = listOf(PLAYOFF, R16, QF, SF, FINAL)
    }
}

private val SLUG_ROUND = mapOf(
    "league-phase" to Round.LEAGUE,
    "group-stage" to Round.LEAGUE,
    "knockout-round-playoffs" to Round.PLAYOFF,
    "round-of-16" to Round.R16,
    "quarterfinals" to Round.QF,
    "quarter-finals" to Round.QF,
    "semifinals" to Round.SF,
    "semi-finals" to Round.SF,
    "final" to Round.FINAL,
)

/** Classe un match d'après le slug de tour ESPN (signal officiel), avec replis par motif puis par mois. */
fun roundOf(slug: String?, kickoffMonth: Int? = null): Round {
    val s = slug?.lowercase().orEmpty()
    SLUG_ROUND[s]?.let { return it }
    if (s.isNotEmpty()) {
        when {
            Regex("qualif|play-?off-round|preliminary").containsMatchIn(s) -> return Round.QUALIFYING
            Regex("knockout.*play|knockout-round").containsMatchIn(s) -> return Round.PLAYOFF
            Regex("league-phase|group|first-phase|second-phase|league").containsMatchIn(s) -> return Round.LEAGUE
            Regex("round-of-16|round-of-32|eighth").containsMatchIn(s) -> return Round.R16
            "quarter" in s -> return Round.QF
            "semi" in s -> return Round.SF
            "final" in s -> return Round.FINAL
        }
    }
    // Dernier repli : par date (phase de ligue ~ août -> fin janvier). Mois 1..12.
    return if (kickoffMonth == null || kickoffMonth >= 8 || kickoffMonth == 1) Round.LEAGUE else Round.PLAYOFF
}

enum class TieWinner { A, B }

data class Tie(
    val teamA: Team,
    val teamB: Team,
    val aggA: Int,
    val aggB: Int,
    val penA: Int?,
    val penB: Int?,
    val legs: List<Match>,
    val started: Boolean,
    val live: Boolean,
    val decided: Boolean,
    val winner: TieWinner?,
) {
    val twoLegs: Boolean get() = legs.size > 1
    val winnerTeam: Team? get() = when (winner) { TieWinner.A -> teamA; TieWinner.B -> teamB; null -> null }
}

data class KnockoutRound(val round: Round, val ties: List<Tie>)

fun buildBracket(matches: List<Match>, roundOfMatch: (Match) -> Round): List<KnockoutRound> {
    val byRound = matches.groupBy(roundOfMatch)
    return Round.KNOCKOUT_ORDER.mapNotNull { r ->
        val list = byRound[r].orEmpty()
        if (list.isEmpty()) null else KnockoutRound(r, groupTies(list))
    }
}

fun groupTies(matches: List<Match>): List<Tie> =
    matches.sortedBy { it.kickoff }
        .groupBy { listOf(it.home.team.id, it.away.team.id).sorted().joinToString("::") }
        .values.map(::aggregateTie)

/** Agrège 1 ou 2 manches en une confrontation avec vainqueur. */
fun aggregateTie(legs: List<Match>): Tie {
    val first = legs.first()
    val aId = first.home.team.id
    var teamA = first.home.team
    var teamB = first.away.team
    var aggA = 0; var aggB = 0
    var penA: Int? = null; var penB: Int? = null
    var live = false; var anyPost = false; var started = false

    for (leg in legs) {
        val aHome = leg.home.team.id == aId
        val a = if (aHome) leg.home else leg.away
        val b = if (aHome) leg.away else leg.home
        if (leg.state != MatchState.PRE) { aggA += a.score ?: 0; aggB += b.score ?: 0; started = true }
        if (leg.state == MatchState.IN) live = true
        if (leg.state == MatchState.POST) anyPost = true
        if (a.shootout != null || b.shootout != null) { penA = a.shootout; penB = b.shootout }
        if (a.team.logo != null) teamA = a.team
        if (b.team.logo != null) teamB = b.team
    }

    val allPost = anyPost && legs.all { it.state == MatchState.POST }
    var winner: TieWinner? = null
    if (allPost) {
        winner = when {
            aggA != aggB -> if (aggA > aggB) TieWinner.A else TieWinner.B
            penA != null && penB != null && penA != penB -> if (penA > penB) TieWinner.A else TieWinner.B
            else -> {
                val last = legs.last()
                val lastAHome = last.home.team.id == aId
                when {
                    last.home.winner -> if (lastAHome) TieWinner.A else TieWinner.B
                    last.away.winner -> if (lastAHome) TieWinner.B else TieWinner.A
                    else -> null
                }
            }
        }
    }
    return Tie(teamA, teamB, aggA, aggB, penA, penB, legs, started, live, allPost, winner)
}

/** Le champion : vainqueur de la finale, si décidée. */
fun findChampion(rounds: List<KnockoutRound>): Team? =
    rounds.firstOrNull { it.round == Round.FINAL }?.ties?.firstOrNull()?.takeIf { it.decided }?.winnerTeam
