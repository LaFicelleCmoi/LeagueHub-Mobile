package com.leaguehub.android

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchSide
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.StandingRow
import com.leaguehub.android.domain.model.Team

fun team(id: String) = Team(id, id, id, id.take(3).uppercase(), null)

fun match(
    id: String,
    home: String,
    away: String,
    kickoff: Long = 0,
    state: MatchState = MatchState.PRE,
    hs: Int? = null,
    aws: Int? = null,
    homePlayed: Int? = null,
    awayPlayed: Int? = null,
) = Match(
    id = id, competition = "fra.1", kickoff = kickoff, state = state, statusName = "", clock = "", venue = null,
    home = MatchSide(team(home), hs, false, homePlayed, null),
    away = MatchSide(team(away), aws, false, awayPlayed, null),
)

fun standing(rank: Int, id: String, played: Int, points: Int, gd: Int = 0, gf: Int = 0) = StandingRow(
    rank = rank, rankChange = 0, team = team(id), played = played, wins = 0, draws = 0, losses = 0,
    goalsFor = gf, goalsAgainst = gf - gd, goalDiff = gd, points = points, zone = null,
)
