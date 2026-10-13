package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Standings

// Classement en direct (portage de `src/lib/live-standings.ts`) : on part du classement
// officiel ESPN et on y ajoute le résultat provisoire des matchs qu'il ne compte pas encore.

data class LiveResult(
    val match: Match,
    val side: Side,
    /** Points ajoutés au classement officiel : 3, 1 ou 0. */
    val points: Int,
)

data class LiveStandingRow(
    val row: StandingRow,
    /** Rang dans le classement officiel. */
    val officialRank: Int,
    val live: LiveResult?,
) {
    /** > 0 : l'équipe monte par rapport au classement officiel. */
    val movement: Int get() = officialRank - row.rank
}

data class LiveStandings(
    val rows: List<LiveStandingRow>,
    /** Au moins un résultat provisoire est pris en compte. */
    val provisional: Boolean,
)

fun preMatchKey(matchId: String, teamId: String): String = "$matchId:$teamId"

/**
 * Ce match manque-t-il au classement officiel pour cette équipe ? (pas de double comptage)
 * - En cours : ESPN ne le compte jamais avant la fin.
 * - Terminé : si on l'a vu en cours, le classement le compte dès qu'il a plus de matchs qu'avant le
 *   coup d'envoi. Sinon, le bilan V-N-D du match l'inclut déjà : notre copie du classement est en
 *   retard tant qu'elle compte moins de matchs que ce bilan.
 */
private fun isMissing(row: StandingRow, match: Match, played: Int?, preMatchPlayed: Int?): Boolean = when (match.state) {
    MatchState.IN -> true
    MatchState.POST -> if (preMatchPlayed != null) row.played <= preMatchPlayed else played != null && row.played < played
    MatchState.PRE -> false
}

fun computeLiveStandings(
    standings: Standings,
    matches: List<Match>,
    preMatchPlayed: Map<String, Int> = emptyMap(),
): LiveStandings {
    val results = HashMap<String, LiveResult>()
    val byTeam = standings.rows.associateBy { it.team.id }

    for (match in matches) {
        if (match.home.score == null || match.away.score == null) continue
        for (side in Side.entries) {
            val us = match.side(side)
            val them = match.side(if (side == Side.HOME) Side.AWAY else Side.HOME)
            val row = byTeam[us.team.id] ?: continue
            val pre = preMatchPlayed[preMatchKey(match.id, us.team.id)]
            if (!isMissing(row, match, us.played, pre)) continue
            val gf = us.score ?: 0
            val ga = them.score ?: 0
            results[us.team.id] = LiveResult(match, side, if (gf > ga) 3 else if (gf == ga) 1 else 0)
        }
    }

    val rows = standings.rows.map { row ->
        val live = results[row.team.id] ?: return@map LiveStandingRow(row, row.rank, null)
        val other = if (live.side == Side.HOME) Side.AWAY else Side.HOME
        val gf = live.match.side(live.side).score ?: 0
        val ga = live.match.side(other).score ?: 0
        LiveStandingRow(
            row = row.copy(
                played = row.played + 1,
                wins = row.wins + if (live.points == 3) 1 else 0,
                draws = row.draws + if (live.points == 1) 1 else 0,
                losses = row.losses + if (live.points == 0) 1 else 0,
                goalsFor = row.goalsFor + gf,
                goalsAgainst = row.goalsAgainst + ga,
                goalDiff = row.goalDiff + gf - ga,
                points = row.points + live.points,
            ),
            officialRank = row.rank,
            live = live,
        )
    }

    // Aucun résultat provisoire : on garde l'ordre officiel et ses critères de départage.
    if (results.isEmpty()) return LiveStandings(rows, provisional = false)

    val sorted = rows
        .sortedWith(
            compareByDescending<LiveStandingRow> { it.row.points }
                .thenByDescending { it.row.goalDiff }
                .thenByDescending { it.row.goalsFor }
                .thenBy { it.officialRank },
        )
        // Les zones (Ligue des champions, relégation…) dépendent de la place, pas de l'équipe.
        .mapIndexed { i, r -> r.copy(row = r.row.copy(rank = i + 1, zone = standings.rows.getOrNull(i)?.zone)) }
    return LiveStandings(sorted, provisional = true)
}
