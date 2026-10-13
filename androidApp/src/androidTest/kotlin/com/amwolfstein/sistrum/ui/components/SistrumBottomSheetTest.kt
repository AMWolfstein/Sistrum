package com.amwolfstein.sistrum.ui.components

import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class SistrumBottomSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun systemBackDismissesSheet() {
        var visible by mutableStateOf(true)
        var dismissed = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                if (visible) SistrumBottomSheet(
                    title = { Text("Sheet") },
                    onDismiss = { dismissed = true; visible = false },
                ) {}
            }
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitUntil(1_000) { dismissed }
        assertTrue(dismissed)
    }

    @Test
    fun tappingHeaderDoesNotDismissSheet() {
        var visible by mutableStateOf(true)
        var dismissed = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                if (visible) SistrumBottomSheet(
                    title = { Text("Sheet") },
                    onDismiss = { dismissed = true; visible = false },
                ) {}
            }
        }

        composeTestRule.onNodeWithText("Sheet").performTouchInput { click() }
        composeTestRule.waitForIdle()

        assertFalse(dismissed)
    }

    @Test
    fun draggingHeaderDownDismissesSheet() {
        var visible by mutableStateOf(true)
        var dismissed = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                if (visible) SistrumBottomSheet(
                    title = { Text("Sheet") },
                    onDismiss = { dismissed = true; visible = false },
                ) {}
            }
        }

        composeTestRule.onNodeWithTag("bottom-sheet-header").performTouchInput { swipeDown() }
        composeTestRule.waitUntil(1_000) { dismissed }

        assertTrue(dismissed)
    }

    @Test
    fun shortHeaderDragKeepsSheetOpen() {
        var visible by mutableStateOf(true)
        var dismissed = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                if (visible) SistrumBottomSheet(
                    title = { Text("Sheet") },
                    onDismiss = { dismissed = true; visible = false },
                ) {}
            }
        }

        composeTestRule.onNodeWithTag("bottom-sheet-header").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, 24f))
            up()
        }
        composeTestRule.waitForIdle()

        assertFalse(dismissed)
    }
}
