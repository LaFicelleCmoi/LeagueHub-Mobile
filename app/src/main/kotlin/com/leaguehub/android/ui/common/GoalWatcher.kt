package com.leaguehub.android.ui.common

import com.leaguehub.android.domain.model.Match
import com.leaguehub.android.domain.model.MatchEventKind
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Détecte un but du club favori entre deux lectures du direct, pour la célébration à l'écran. */
class GoalWatcher {
    data class Goal(val match: Match, val scorer: String?)

    var favoriteId: String? = null
    private val lastScores = HashMap<String, Int>()
    private val _events = MutableSharedFlow<Goal>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Goal> = _events

    fun observe(matches: List<Match>) {
        val fav = favoriteId ?: return
        for (m in matches) {
            if (!m.involves(fav) || m.state == MatchState.PRE) continue
            val side = if (m.home.team.id == fav) Side.HOME else Side.AWAY
            val score = m.side(side).score ?: continue
            val previous = lastScores.put(m.id, score)
            // Première lecture : simple référence, pas de célébration rétroactive.
            if (previous != null && score > previous && m.state == MatchState.IN) {
                val scorer = m.events.lastOrNull {
                    it.side == side && it.kind != MatchEventKind.YELLOW_CARD && it.kind != MatchEventKind.RED_CARD
                }?.player
                _events.tryEmit(Goal(m, scorer))
            }
        }
    }
}
