package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.ui.components.HomeContent
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import android.os.LocaleList
import android.content.res.Configuration

class LibraryContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun libraryContentDisplaysActionListItems() {
        var searchClicked = false
        var artistsClicked = false
        var albumsClicked = false
        var tracksClicked = false
        var genresClicked = false
        var composersClicked = false
        var playlistsClicked = false

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryContent(
                    onSearchSelected = { searchClicked = true },
                    onArtistsSelected = { artistsClicked = true },
                    onAlbumsSelected = { albumsClicked = true },
                    onTracksSelected = { tracksClicked = true },
                    onGenresSelected = { genresClicked = true },
                    onComposersSelected = { composersClicked = true },
                    onPlaylistsSelected = { playlistsClicked = true },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.library_search)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(searchClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_artists)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(artistsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_albums)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(albumsClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_tracks)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(tracksClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_genres)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(genresClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_composers)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(composersClicked)

        composeTestRule.onNodeWithText(context.getString(R.string.library_playlists)).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(playlistsClicked)
    }

    // D-22: the shelf and its existing action coverage move to Home in T010.
    @Test
    fun libraryContentRendersRecentTracksGridAndTriggersTrackClick() {
        var clickedTrackId: String? = null
        val recentTracks = listOf(
            LibraryTrack(id = "r1", title = "Recent Song 1", artists = "Artist 1"),
            LibraryTrack(id = "r2", title = "Recent Song 2", artists = "Artist 2"),
        )

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                MovedRecentShelf(
                    recentTracks = recentTracks,
                    onTrackClick = { clickedTrackId = it },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText(context.getString(R.string.library_recently_added)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent Song 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home-recent-carousel").performScrollToIndex(1)
        composeTestRule.onNodeWithText("Recent Song 2").assertIsDisplayed().performClick()

        assertEquals("r2", clickedTrackId)
    }

    @Test
    fun holdingRecentlyAddedTrackOpensContextMenu() {
        val track = LibraryTrack(id = "r1", title = "Recent Song", artists = "Artist")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    MovedRecentShelf(recentTracks = listOf(track))
                }
            }
        }

        composeTestRule.onNodeWithText("Recent Song").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertIsDisplayed()
    }

    @Test
    fun trackInfoSheetOpensFromRecentlyAddedGrid() {
        var request: TrackContextBottomSheetRequest? = null
        val track = LibraryTrack(id = "r1", title = "Recent Song", artists = "Artist")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    MovedRecentShelf(recentTracks = listOf(track), onBottomSheet = { request = it })
                }
            }
        }

        composeTestRule.onNodeWithText("Recent Song").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("Track info").performClick()

        // D-22: Home forwards the real sheet request to the app host.
        assertEquals(TrackContextBottomSheetRequest.Info(track), request)
    }

    @Test
    fun emptyLibraryKeepsDestinationsWithoutRecentlyAdded() {
        composeTestRule.setContent { SistrumTheme(ThemeMode.Dark) { LibraryContent() } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule.onNodeWithText(context.getString(R.string.library_tracks)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.library_recently_added)).assertDoesNotExist()
    }

    @Test
    fun libraryScrollRestoresAfterRecreation() {
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            SistrumTheme(ThemeMode.Dark) {
                LibraryContent(modifier = Modifier.height(200.dp), listState = rememberLazyListState())
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val playlists = context.getString(R.string.library_playlists)
        composeTestRule.onNodeWithText(playlists).performScrollTo().assertIsDisplayed()
        val before = composeTestRule.onNodeWithText(playlists).fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText(playlists).assertIsDisplayed()
        assertEquals(before, composeTestRule.onNodeWithText(playlists).fetchSemanticsNode().boundsInRoot)
    }

}

// D-22: keep the original recent-track click and menu assertions at their new Home destination.
@Composable
private fun MovedRecentShelf(
    recentTracks: List<LibraryTrack>,
    onTrackClick: (String) -> Unit = {},
    onBottomSheet: (TrackContextBottomSheetRequest) -> Unit = {},
) {
    HomeContent(
        isLoaded = true, recentTracks = recentTracks, keepListeningTracks = emptyList(),
        mostPlayedTracks = emptyList(), forgottenTracks = emptyList(),
        onTrackClick = { _, id -> onTrackClick(id) }, playbackQueue = PlaybackQueueSnapshot(),
        onTrackPlayNext = {}, onTrackAddToQueue = {}, onTrackFavoriteToggle = { _, _ -> },
        onTrackAlbumClick = {}, onTrackArtistClick = {}, onTrackContextBottomSheet = onBottomSheet,
        listState = rememberLazyListState(), contentPadding = PaddingValues(),
    )
}

@RunWith(Parameterized::class)
class LibraryContentMatrixTest(private val case: UiMatrix.Case) {
    @get:Rule val rule = createComposeRule()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = UiMatrix.cases.map { arrayOf(it) }
    }

    @Test
    fun everyDestinationRemainsReachableWithoutClipping() {
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("LibraryContent"))
        val selected = mutableListOf<Int>()
        matrix.setContent {
            LibraryContent(
                onSearchSelected = { selected += R.string.library_search },
                onArtistsSelected = { selected += R.string.library_artists },
                onAlbumsSelected = { selected += R.string.library_albums },
                onTracksSelected = { selected += R.string.library_tracks },
                onGenresSelected = { selected += R.string.library_genres },
                onComposersSelected = { selected += R.string.library_composers },
                onPlaylistsSelected = { selected += R.string.library_playlists },
            )
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(case.language))
        })
        val destinations = listOf(R.string.library_search, R.string.library_artists, R.string.library_albums,
            R.string.library_tracks, R.string.library_genres, R.string.library_composers, R.string.library_playlists)
        destinations.forEach { id ->
            val label = localized.getString(id)
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            matrix.assertNoClippedActions(hasClickAction() and hasAnyDescendant(hasText(label)))
        }
        assertEquals(destinations, selected)
        matrix.captureToImage()
    }
}
