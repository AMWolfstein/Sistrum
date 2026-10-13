package com.amwolfstein.sistrum.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryComposer
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryComposersContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoComposers() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryComposersContent(LibraryComposersUiState())
            }
        }

        composeTestRule.onNodeWithText("No composers in library").assertExists()
    }

    @Test
    fun displaysComposersInVirtualizedRows() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryComposersContent(
                    LibraryComposersUiState(
                        composers = listOf(
                            LibraryComposer(id = "c1", name = "Beethoven"),
                            LibraryComposer(id = "c2", name = "Mozart"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Beethoven").assertExists()
        composeTestRule.onNodeWithText("Mozart").assertExists()
        composeTestRule.onAllNodesWithTag("composer-row-divider").assertCountEquals(1)
    }

    @Test
    fun composerOverflowUsesProvidedOrderedTrackIds() {
        var nextIds: List<String>? = null
        var addOnly = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryComposersContent(
                    LibraryComposersUiState(composers = listOf(LibraryComposer("c", "Bach"))),
                    orderedTrackIdsForComposer = { listOf("track-1", "track-2") },
                    onComposerPlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        addOnly = (request as TrackContextBottomSheetRequest.Playlist).addOnly
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Bach").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("track-1", "track-2"), nextIds)

        composeTestRule.onNodeWithText("Bach").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(true, addOnly)
    }
}
