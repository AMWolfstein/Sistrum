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

class SistrumDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirmActionUsesTheProvidedCallback() {
        var confirmed by mutableStateOf(false)
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                SistrumDialog(
                    title = "Disconnect desktop?",
                    description = "The desktop remains authorized until revoked there.",
                    dismissLabel = "Cancel",
                    onDismiss = {},
                    confirmLabel = "Revoke",
                    onConfirm = { confirmed = true },
                    confirmVariant = SistrumPillButtonVariant.Destructive,
                )
            }
        }

        composeTestRule.onNodeWithText("Revoke").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun alertUsesItsSingleDismissAction() {
        var dismissed by mutableStateOf(false)
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                SistrumDialog(
                    title = "Not enough storage",
                    description = "Needs 2 GB, 1 GB available.",
                    dismissLabel = "Close",
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }
}
