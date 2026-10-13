package com.leaguehub.android.domain.standings

import com.leaguehub.android.domain.model.Zone
import com.leaguehub.android.domain.model.ZoneTone
import com.leaguehub.android.standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObjectivesTest {

    // Mini-championnat de 4 équipes, 6 matchs chacune : 1re = LDC, 4e = relégation.
    private fun table(vararg rows: Triple<String, Int, Int>) = rows.mapIndexed { i, (id, played, pts) ->
        val zone = when (i) {
            0 -> Zone("UCL", ZoneTone.UCL, false)
            3 -> Zone("Rel", ZoneTone.RELEGATION, false)
            else -> null
        }
        standing(i + 1, id, played, pts).copy(zone = zone)
    }

    @Test
    fun `title secured when nobody can catch up`() {
        val rows = table(Triple("A", 5, 15), Triple("B", 5, 9), Triple("C", 5, 3), Triple("D", 5, 0))
        val title = objectives(rows, "A").first { it.kind == ObjectiveKind.TITLE }
        assertEquals(ObjectiveStatus.SECURED, title.status)
        assertNull(title.pointsNeeded)
        assertEquals(-6, title.gap) // 6 pts d'avance sur B
    }

    @Test
    fun `points needed to be sure of the title`() {
        // A 10 pts (2 matchs restants), B 9 pts (2 restants, max 15) : il faut 6 pts pour finir à 16.
        val rows = table(Triple("A", 4, 10), Triple("B", 4, 9), Triple("C", 4, 3), Triple("D", 4, 0))
        val title = objectives(rows, "A").first { it.kind == ObjectiveKind.TITLE }
        assertEquals(ObjectiveStatus.IN_HAND, title.status)
        assertEquals(6, title.pointsNeeded)
        assertEquals(6, title.pointsAvailable)
    }

    @Test
    fun `depends on others then impossible`() {
        // C : 3 pts, 1 match restant (max 6).
        val rows = table(Triple("A", 5, 13), Triple("B", 5, 5), Triple("C", 5, 3), Triple("D", 5, 1))
        val title = objectives(rows, "C").first { it.kind == ObjectiveKind.TITLE }
        assertEquals(ObjectiveStatus.IMPOSSIBLE, title.status) // A a déjà 13 > 6
        val safety = objectives(rows, "C").first { it.kind == ObjectiveKind.SAFETY }
        assertEquals(3, safety.position)
        // D peut finir à 4 (égalité = risque) : il faut 5 pts au total, soit 2 de plus.
        assertEquals(ObjectiveStatus.IN_HAND, safety.status)
        assertEquals(2, safety.pointsNeeded)
    }

    @Test
    fun `objective possible only with help`() {
        // B : 6 pts, 1 match restant (max 9) ; A à 7 pts peut finir à 10 : B ne peut être sûr du titre.
        val rows = table(Triple("A", 5, 7), Triple("B", 5, 6), Triple("C", 5, 3), Triple("D", 5, 0))
        val title = objectives(rows, "B").first { it.kind == ObjectiveKind.TITLE }
        assertEquals(ObjectiveStatus.DEPENDS_ON_OTHERS, title.status)
        assertEquals(1, title.gap)
    }
}
