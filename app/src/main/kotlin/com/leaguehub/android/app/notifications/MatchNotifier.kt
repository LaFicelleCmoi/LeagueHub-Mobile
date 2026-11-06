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

    /** Buts déjà annoncés pour ce club dans ce match (clé par club : deux clubs suivis peuvent s'affronter). */
    private fun goalsKey(matchId: String, teamId: String) = "goals:$matchId:$teamId"

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
        // Score de départ noté avant le coup d'envoi : tout but marqué ensuite sera annoncé.
        if (next != null) {
            val key = goalsKey(next.id, club.teamId)
            if (!prefs.contains(key)) prefs.edit().putInt(key, 0).commit()
        }
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
            val key = goalsKey(live.id, club.teamId)
            // Score inconnu (réponse ESPN incomplète) : on attend le prochain battement.
            val scored = liveFor ?: live.goalsFor ?: return@synchronized
            val against = liveAgainst ?: live.goalsAgainst ?: 0
            when {
                // Club ajouté en plein match (score d'avant-match jamais noté) : point de départ, sans annoncer les buts déjà marqués.
                !prefs.contains(key) -> prefs.edit().putInt(key, scored).commit()
                // Le compteur ne redescend jamais : un cache ESPN en retard (3-1 puis 2-1 puis 3-1) n'annonce pas deux fois le même but.
                scored > prefs.getInt(key, 0) -> {
                    prefs.edit().putInt(key, scored).commit()
                    Notifications.goal(
                        context, live.id + club.teamId, "$scored-$against",
                        context.getString(R.string.notif_goal_title, club.name),
                        "${club.name} $scored – $against ${live.opponent.name} (${clock?.takeIf { it.isNotEmpty() } ?: live.clock})",
                    )
                }
            }
        }
    }
}
