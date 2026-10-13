package com.leaguehub.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leaguehub.android.core.theme.LeagueHubTheme
import com.leaguehub.android.ui.components.StandingLine
import com.leaguehub.android.ui.components.TableLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandingLineTest {
    @get:Rule
    val compose = createComposeRule()

    private val line = TableLine(
        teamId = "160", rank = "1", name = "Paris Saint-Germain", logo = null,
        played = 8, goalDiff = 12, points = 20, zoneColor = Color.Blue, movement = 1,
    )

    @Test
    fun ligneAvecEtoileDuFavoriEtClic() {
        var clicked = 0
        compose.setContent {
            LeagueHubTheme { StandingLine(line, favoriteId = "160", onClick = { clicked++ }) }
        }
        compose.onNodeWithText("Paris Saint-Germain").assertIsDisplayed()
        compose.onNodeWithText("+12").assertIsDisplayed()
        compose.onNodeWithText("20").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity().getString(R.string.favorite_club)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity().getString(R.string.moves_up)).assertIsDisplayed()
        compose.onNodeWithTag("standing-160").performClick()
        assertEquals(1, clicked)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activity() =
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
