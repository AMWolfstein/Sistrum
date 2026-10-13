package com.amwolfstein.sistrum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.screens.InsightPeriod
import com.amwolfstein.sistrum.ui.screens.InsightLineChart
import com.amwolfstein.sistrum.ui.screens.InsightDonut
import com.amwolfstein.sistrum.ui.screens.InsightPoint
import com.amwolfstein.sistrum.ui.screens.InsightTopTrack
import com.amwolfstein.sistrum.ui.screens.InsightTopArtist
import com.amwolfstein.sistrum.ui.screens.InsightUiState
import com.amwolfstein.sistrum.ui.screens.LibraryInsightState
import com.amwolfstein.sistrum.ui.screens.ListeningInsightState
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InsightContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun insightReplacesPlaceholderAndExposesFiltersAndExpandableTracks() {
        var state by mutableStateOf(
            InsightUiState(
                library = LibraryInsightState(tracks = 6),
                listening = ListeningInsightState(
                    listenedSeconds = 3_600,
                    topArtists = listOf(InsightTopArtist("artist-1", "Artist name", null, 60)),
                    topTracks = (1..6).map { index ->
                        InsightTopTrack(LibraryTrack("track-$index", "Track $index", "Artist"), index, index * 60)
                    },
                ),
            ),
        )
        composeTestRule.setContent {
            App(
                uiState = AppUiState(selectedDestination = AppDestination.Insight),
                destinations = AppDestinationModels(
                    insight = InsightDestinationModel(
                        state = state,
                        onListeningPeriodSelected = { state = state.copy(listeningPeriod = it) },
                    ),
                ),
            )
        }

        composeTestRule.onNodeWithTag("insight-page").assertIsDisplayed()
        val libraryWidth = composeTestRule.onNodeWithTag("insight-library-size").fetchSemanticsNode().boundsInRoot.width
        val playlistWidth = composeTestRule.onNodeWithTag("insight-playlists").fetchSemanticsNode().boundsInRoot.width
        assertTrue(libraryWidth > playlistWidth)
        composeTestRule.onAllNodesWithText("Insight will appear here.").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("7 Days").onLast().performClick()
        composeTestRule.onNodeWithText("30 Days").performClick()
        assertEquals(InsightPeriod.ThirtyDays, state.listeningPeriod)
        composeTestRule.onAllNodesWithText("All devices").assertCountEquals(0)
        composeTestRule.onNodeWithText("Artist name").performScrollTo()
        composeTestRule.onNodeWithText("Artist name").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Track 6").assertCountEquals(0)
        composeTestRule.onNodeWithTag("insight-top-tracks-toggle").performScrollTo()
        composeTestRule.onNodeWithText("1 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 plays").assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Track options")).assertDoesNotExist()
        composeTestRule.onAllNodesWithTag("insight-track-divider").assertCountEquals(4)
        composeTestRule.onNodeWithTag("insight-top-tracks-toggle").performClick()
        composeTestRule.onNodeWithText("Track 6").assertIsDisplayed()
    }

    @Test
    fun libraryGrowthChartDoesNotScrollForLongRanges() {
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) { InsightLineChart((1..31).map { InsightPoint(it.toString(), it) }, "Library growth") }
        }

        composeTestRule.onNodeWithContentDescription("Library growth").assert(!hasScrollAction())
    }

    @Test
    fun libraryGrowthChartDisplaysASingleDataPoint() {
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) { InsightLineChart(listOf(InsightPoint("2026", 1)), "Single library growth point") }
        }

        composeTestRule.onNodeWithContentDescription("Single library growth point").assertIsDisplayed()
    }

    @Test
    fun donutChartDisplaysASingleValue() {
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) { InsightDonut(listOf(1 to LocalSistrumColors.current.primary), "1", "Single donut value") }
        }

        composeTestRule.onNodeWithContentDescription("Single donut value").assertIsDisplayed()
    }
}
