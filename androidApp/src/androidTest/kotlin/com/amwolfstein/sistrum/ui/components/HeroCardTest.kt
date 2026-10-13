package com.amwolfstein.sistrum.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Rule
import org.junit.Test

class HeroCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysItsTitleAndDescription() {
        val title = "No connected devices"
        val description = "Connect a device to keep your listening in sync."

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Light) {
                HeroCard(
                    symbol = MaterialSymbols.Power,
                    title = title,
                    description = description,
                )
            }
        }

        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
    }
}
