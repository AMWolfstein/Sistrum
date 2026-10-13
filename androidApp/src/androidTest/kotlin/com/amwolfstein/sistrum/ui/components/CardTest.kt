package com.amwolfstein.sistrum.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun invokesItsActionWhenTapped() {
        var clicked by mutableStateOf(false)
        val title = "Open collection"

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                Card(
                    title = title,
                    description = "See every saved track",
                    onClick = { clicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText(title).performClick()

        assertTrue(clicked)
    }
}
