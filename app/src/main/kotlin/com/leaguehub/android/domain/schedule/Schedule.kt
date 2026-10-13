package com.leaguehub.android.domain.schedule

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* =========================================================================
   Schedule.kt — Calendrier (portage de `schedule.js`)
   LOGIQUE PURE : ni Android, ni réseau. Le fuseau est un paramètre.
   ========================================================================= */

/** Fuseau d'affichage par défaut : toutes les dates sont en heure de Paris. */
val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** Jour civil d'un instant dans un fuseau (un match à 21 h appartient au jour où on le regarde). */
fun localDay(time: Long, zone: ZoneId = PARIS): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

data class MatchDay(val date: LocalDate, val matches: List<Match>)

/**
 * Bloc de matchs de la prochaine date STRICTEMENT postérieure au jour courant (jugé au jour, pas à
 * la milliseconde : les matchs de 21 h appartiennent à « Aujourd'hui »), ou null.
 */
fun getNextMatchDate(schedule: List<Match>, now: Long, zone: ZoneId = PARIS): MatchDay? {
    val today = localDay(now, zone)
    val future = schedule.filter { localDay(it.kickoff, zone) > today }
        .sortedWith(compareBy<Match> { it.kickoff }.thenBy { it.id })
    if (future.isEmpty()) return null
    val day = localDay(future.first().kickoff, zone)
    return MatchDay(day, future.takeWhile { localDay(it.kickoff, zone) == day })
}

data class NextKickoff(
    val match: Match,
    val time: Long,
    val day: LocalDate,
    /** Matchs encore à venir ce jour-là. */
    val sameDay: List<Match>,
)

/**
 * Prochain coup d'envoi postérieur à maintenant (un INSTANT, pour un compte à rebours).
 * Seuls les matchs « à venir » comptent. [graceMs] : tolérance après l'heure officielle — ESPN met
 * souvent une minute ou deux à passer un match « en cours ».
 */
fun getNextKickoff(schedule: List<Match>, now: Long, graceMs: Long = 0, zone: ZoneId = PARIS): NextKickoff? {
    val future = schedule.filter { it.state == MatchState.PRE && it.kickoff > now - graceMs.coerceAtLeast(0) }
        .sortedWith(compareBy<Match> { it.kickoff }.thenBy { it.id })
    val first = future.firstOrNull() ?: return null
    val day = localDay(first.kickoff, zone)
    return NextKickoff(first, first.kickoff, day, future.filter { localDay(it.kickoff, zone) == day })
}

/** Matchs en cours, par ordre de coup d'envoi. */
fun getLiveMatches(schedule: List<Match>): List<Match> =
    schedule.filter { it.state == MatchState.IN }.sortedBy { it.kickoff }

data class CountdownParts(val days: Long, val hours: Long, val minutes: Long, val total: Long)

/**
 * Durée -> jours/heures/minutes, en minutes ARRONDIES AU-DESSUS : à 30 secondes du coup d'envoi
 * on affiche « 1 min », jamais « 0 min ». Durée nulle ou négative : tout à zéro.
 */
fun countdownParts(ms: Long): CountdownParts {
    if (ms <= 0) return CountdownParts(0, 0, 0, 0)
    val total = (ms + 59_999) / 60_000
    return CountdownParts(total / 1440, (total % 1440) / 60, total % 60, total)
}

enum class CountdownUrgency {
    NORMAL,

    /** Moins de 24 h : ambre. */
    SOON,

    /** Dernière heure : rouge. */
    IMMINENT,
}

fun countdownUrgency(ms: Long): CountdownUrgency = when {
    ms <= 60 * 60_000L -> CountdownUrgency.IMMINENT
    ms <= 24 * 60 * 60_000L -> CountdownUrgency.SOON
    else -> CountdownUrgency.NORMAL
}
