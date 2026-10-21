package com.leaguehub.android.app.notifications

import android.content.Context
import com.leaguehub.android.R
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.TeamForm

/**
 * Notifications d'un club (coup d'envoi dans la demi-heure, chaque but), chacune envoyée une seule fois.
 * Appelé par la synchro périodique et par le battement des widgets (chaque minute pendant les matchs).
 */
object MatchNotifier {
    const val KICKOFF_WARNING_MS = 30 * 60_000L
    private const val STATE = "sync-state"
    private val lock = Any()

    /**
     * @param liveFor / liveAgainst score le plus frais connu (résumé du match), sinon celui de la fiche du club.
     */
    fun notify(
        context: Context,
        club: FavoriteClub,
        form: TeamForm,
        kickoff: Boolean,
        goals: Boolean,
        now: Long = System.currentTimeMillis(),
        liveFor: Int? = null,
        liveAgainst: Int? = null,
        clock: String? = null,
    ) = synchronized(lock) { // synchro et battement peuvent tourner en même temps : pas de doublon
        val prefs = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        val next = form.next
        if (kickoff && next != null && next.kickoff - now in 0..KICKOFF_WARNING_MS) {
            val key = "kickoff:${next.id}"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).commit()
                Notifications.kickoff(
                    context, next.id,
                    context.getString(R.string.notif_kickoff_title, club.name),
                    context.getString(R.string.notif_kickoff_text, next.opponent.name, next.competition),
                )
            }
        }

        val live = form.live
        if (goals && live != null && live.state == MatchState.IN) {
            // Clé par club : deux clubs suivis peuvent s'affronter.
            val key = "goals:${live.id}:${club.teamId}"
            val scored = liveFor ?: live.goalsFor ?: 0
            val against = liveAgainst ?: live.goalsAgainst ?: 0
            val known = prefs.getInt(key, 0)
            if (scored > known) {
                Notifications.goal(
                    context, live.id + club.teamId, "$scored-$against",
                    context.getString(R.string.notif_goal_title, club.name),
                    "${club.name} $scored – $against ${live.opponent.name} (${clock?.takeIf { it.isNotEmpty() } ?: live.clock})",
                )
            }
            if (scored != known) prefs.edit().putInt(key, scored).commit()
        }
    }
}
