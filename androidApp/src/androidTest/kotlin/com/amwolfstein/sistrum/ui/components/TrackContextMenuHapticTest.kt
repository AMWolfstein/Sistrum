package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrackContextMenuHapticTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun favoriteActionConfirmsOnlyWhenAdding() {
        val haptics = mutableListOf<HapticFeedbackType>()
        var change: Boolean? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
                }) {
                    TrackContextMenu(
                        track = LibraryTrack(id = "track", title = "Track", artists = "Artist"),
                        expanded = true,
                        onDismiss = {},
                        onFavoriteChange = { _, favorite -> change = favorite },
                        anchor = { Box(Modifier) },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Add to favorites").performClick()
        composeTestRule.runOnIdle {
            assertEquals(true, change)
            assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
        }
    }

    @Test
    fun unfavoriteActionDoesNotConfirm() {
        val haptics = mutableListOf<HapticFeedbackType>()
        var change: Boolean? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { haptics += hapticFeedbackType }
                }) {
                    TrackContextMenu(
                        track = LibraryTrack(id = "track", title = "Track", artists = "Artist", metadataJson = "{\"is_favorite\":true}"),
                        expanded = true,
                        onDismiss = {},
                        onFavoriteChange = { _, favorite -> change = favorite },
                        anchor = { Box(Modifier) },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Remove from favorites").performClick()
        composeTestRule.runOnIdle {
            assertEquals(false, change)
            assertEquals(emptyList<HapticFeedbackType>(), haptics)
        }
    }

    @Test
    fun findLyricsOpensTheTrackContextSheet() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                TrackContextMenu(
                    track = LibraryTrack(id = "track", title = "Track", artists = "Artist"),
                    expanded = true,
                    onDismiss = {},
                    anchor = { Box(Modifier) },
                )
            }
        }

        composeTestRule.onNodeWithText("Find lyrics").performClick()

        composeTestRule.onNodeWithText("Find Lyrics").assertIsDisplayed()
    }
}
