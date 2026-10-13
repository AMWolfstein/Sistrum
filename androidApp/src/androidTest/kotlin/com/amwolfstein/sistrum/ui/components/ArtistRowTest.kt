package com.amwolfstein.sistrum.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArtistRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysArtistAndHandlesOptionalCallbacks() {
        var rowClicked by mutableStateOf(false)
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                ArtistRow(
                    name = "Muse",
                    onClick = { rowClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").performClick()
        assertTrue(rowClicked)
        rowClicked = false
        composeTestRule.onNode(hasContentDescription("Open artist")).performClick()
        assertTrue(rowClicked)
    }
}
