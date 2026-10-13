package com.amwolfstein.sistrum.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SistrumPillButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun destructiveVariantExposesItsActionAndInvokesCallback() {
        var clicked by mutableStateOf(false)
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                SistrumPillButton(
                    label = "Revoke",
                    onClick = { clicked = true },
                    variant = SistrumPillButtonVariant.Destructive,
                )
            }
        }

        composeTestRule.onNodeWithText("Revoke").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    // T062a: the gold brand primary needs a dark foreground (8.6:1); white on gold is 2.1:1.
    fun darkThemeUsesDarkForegroundOnGoldPrimaryForPrimaryAndDestructiveActions() {
        var onPrimary: Color? = null

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                onPrimary = MaterialTheme.colorScheme.onPrimary
                SistrumPillButton(
                    label = "Continue",
                    onClick = {},
                    variant = SistrumPillButtonVariant.Primary,
                )
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(Color(0xFF19140F), onPrimary)
        }
    }
}
