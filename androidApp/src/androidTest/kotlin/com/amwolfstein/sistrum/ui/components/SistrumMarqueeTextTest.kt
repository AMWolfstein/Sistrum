package com.amwolfstein.sistrum.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Rule
import org.junit.Test

class SistrumMarqueeTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTextContentCorrectly() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                SistrumMarqueeText(
                    text = "Very Long Track Title That Will Overflow The Container Width",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        composeTestRule.onNodeWithText("Very Long Track Title That Will Overflow The Container Width")
            .assertIsDisplayed()
    }
}
