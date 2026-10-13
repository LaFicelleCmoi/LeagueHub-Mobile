package com.leaguehub.android.domain.live

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Règles du suivi en direct (portage de `src/lib/live.ts`) : quels matchs suivre, et quand.

/** Intervalle entre deux actualisations des scores pendant un match. */
const val LIVE_POLL_INTERVAL = 20_000L

/** Plafond de l'espacement des tentatives après une erreur. */
const val MAX_BACKOFF = 5 * 60_000L

/** Hors match, on revient vérifier au plus tard après ce délai. */
const val IDLE_RECHECK = 30 * 60_000L

/** On commence à suivre un match 10 minutes avant le coup d'envoi… */
const val BEFORE_KICKOFF = 10 * 60_000L

/** …et on continue tant qu'il est « à venir » après l'heure prévue : ESPN le bascule parfois en retard. */
const val AFTER_KICKOFF = 12 * 60 * 60_000L

/** Le match est-il en cours, ou sur le point de commencer ? */
fun needsLiveUpdate(match: Match, now: Long): Boolean = when (match.state) {
    MatchState.IN -> true
    MatchState.PRE -> match.kickoff - now <= BEFORE_KICKOFF && now - match.kickoff <= AFTER_KICKOFF
    MatchState.POST -> false
}

/**
 * Délai avant la prochaine lecture : 20 s pendant un match, sinon jusqu'à 10 min avant le prochain
 * coup d'envoi (borné à [IDLE_RECHECK]). Polling UNIQUEMENT pendant les matchs.
 */
fun nextPollDelay(matches: List<Match>, now: Long): Long {
    if (matches.any { needsLiveUpdate(it, now) }) return LIVE_POLL_INTERVAL
    val next = matches.filter { it.state == MatchState.PRE && it.kickoff > now }.minOfOrNull { it.kickoff }
        ?: return IDLE_RECHECK
    return (next - BEFORE_KICKOFF - now).coerceIn(LIVE_POLL_INTERVAL, IDLE_RECHECK)
}

/** Espacement après [failures] erreurs consécutives : 20 s, 40 s, 80 s… jusqu'à 5 minutes. */
fun backoffDelay(failures: Int): Long {
    if (failures <= 0) return LIVE_POLL_INTERVAL
    val shift = (failures - 1).coerceAtMost(10)
    return (LIVE_POLL_INTERVAL shl shift).coerceAtMost(MAX_BACKOFF)
}

sealed interface PollResult<out T> {
    data class Success<T>(val value: T) : PollResult<T>
    data class Failure(val error: Throwable, val failures: Int) : PollResult<Nothing>
}

/**
 * Boucle de polling : émet chaque lecture, puis attend le délai calculé par [delayFor] (ou le
 * backoff après une erreur). Collectée avec `WhileSubscribed`, elle s'arrête d'elle-même quand
 * l'app passe en arrière-plan.
 */
fun <T> pollingFlow(
    clock: () -> Long = System::currentTimeMillis,
    delayFor: (T, Long) -> Long,
    fetch: suspend () -> T,
): Flow<PollResult<T>> = flow {
    var failures = 0
    while (true) {
        val wait = try {
            val value = fetch()
            failures = 0
            emit(PollResult.Success(value))
            delayFor(value, clock())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failures++
            emit(PollResult.Failure(e, failures))
            backoffDelay(failures)
        }
        delay(wait)
    }
}
