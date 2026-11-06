package com.leaguehub.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.leaguehub.android.domain.leagueconfig.Competitions
import com.leaguehub.android.domain.model.FavoriteClub
import com.leaguehub.android.domain.model.MatchDetail
import com.leaguehub.android.domain.model.MatchState
import com.leaguehub.android.domain.model.Side
import com.leaguehub.android.domain.model.TeamForm
import com.leaguehub.android.domain.repository.FootballRepository
import com.leaguehub.android.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Dépôts accessibles hors des écrans Hilt (widgets, actions de widget). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun football(): FootballRepository
    fun settings(): SettingsRepository
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Résultat d'une mise à jour du widget du club. */
data class FavoriteRefresh(
    /** Fiche du club (null si le réseau a échoué ou s'il n'y a pas de favori). */
    val form: TeamForm?,
    val live: Boolean,
    /** Prochain coup d'envoi du club, pour réveiller le widget à l'heure. */
    val nextKickoff: Long?,
    /** Score et minute du résumé du match en cours (plus frais que la fiche du club), pour les notifications. */
    val liveFor: Int? = null,
    val liveAgainst: Int? = null,
    val clock: String? = null,
)

/** Mise à jour des deux widgets, partagée par la synchro périodique et le suivi en direct. */
object WidgetUpdater {

    /** Données du club favori, lues sur ESPN avant tout affichage. */
    class FavoriteData(
        val form: TeamForm,
        val opponentForm: TeamForm?,
        val detail: MatchDetail?,
        val refresh: FavoriteRefresh,
    )

    /**
     * Lit la fiche du club et, pendant / juste après un match, le résumé ESPN (score à jour, minute,
     * buteurs). Ne touche pas au widget : les notifications peuvent partir avant le dessin.
     * @return null si le réseau a échoué.
     */
    suspend fun fetchFavorite(football: FootballRepository, favorite: FavoriteClub): FavoriteData? {
        val league = Competitions.bySlug(favorite.competition) ?: Competitions.UCL
        val form = runCatching { football.teamForm(favorite.teamId, league) }.getOrNull() ?: return null
        val shown = WidgetState.shownMatch(form)
        val detail = shown?.takeIf { it.state != MatchState.PRE }?.let {
            runCatching { football.matchDetail(it.competitionCode, it.id) }.getOrNull()
        }
        // Forme de l'adversaire : utile seulement avant le match (pastilles).
        val opponentForm = shown?.takeIf { it.state == MatchState.PRE }?.let {
            runCatching { football.teamForm(it.opponent.id, league) }.getOrNull()
        }
        val ours = if (shown?.home == true) Side.HOME else Side.AWAY
        val fresh = detail?.match?.takeIf { form.live != null && it.id == form.live.id }
        val refresh = FavoriteRefresh(
            form, live = form.live != null, nextKickoff = form.next?.kickoff,
            liveFor = fresh?.side(ours)?.score,
            liveAgainst = fresh?.side(if (ours == Side.HOME) Side.AWAY else Side.HOME)?.score,
            clock = fresh?.clock,
        )
        return FavoriteData(form, opponentForm, detail, refresh)
    }

    /** Dessine le widget du club avec des données déjà lues (logos téléchargés au passage). */
    suspend fun renderFavorite(context: Context, favorite: FavoriteClub, data: FavoriteData) {
        WidgetState.save(context, favorite, data.form, data.opponentForm, data.detail)
        FavoriteClubWidget().updateAll(context)
    }

    /** Plus de club favori : le widget ne doit pas continuer d'afficher l'ancien. */
    suspend fun clearFavorite(context: Context) {
        WidgetState.clear(context)
        FavoriteClubWidget().updateAll(context)
    }

    /**
     * Widget du club favori : lecture puis affichage.
     * @return null si le réseau a échoué (l'affichage précédent est conservé).
     */
    suspend fun refreshFavorite(context: Context, football: FootballRepository, favorite: FavoriteClub?): FavoriteRefresh? {
        if (favorite == null) {
            clearFavorite(context)
            return FavoriteRefresh(null, live = false, nextKickoff = null)
        }
        val data = fetchFavorite(football, favorite) ?: return null
        renderFavorite(context, favorite, data)
        return data.refresh
    }

    /** Le widget « Matchs du jour » est-il posé sur l'écran d'accueil ? */
    suspend fun hasTodayWidget(context: Context): Boolean =
        GlanceAppWidgetManager(context).getGlanceIds(TodayMatchesWidget::class.java).isNotEmpty()

    /**
     * Widget « Matchs du jour », s'il est posé.
     * @return true si un match est en cours, false sinon, null en cas d'échec réseau ou s'il n'est pas posé.
     */
    suspend fun refreshToday(context: Context, football: FootballRepository): Boolean? {
        if (!hasTodayWidget(context)) return null
        val live = TodayWidgetState.refresh(context, football) ?: return null
        TodayMatchesWidget().updateAll(context)
        return live
    }
}
