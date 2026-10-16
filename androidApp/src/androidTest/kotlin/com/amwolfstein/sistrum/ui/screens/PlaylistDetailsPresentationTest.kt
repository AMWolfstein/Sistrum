package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.ui.components.LocalReduceTransparency
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** No separate loading/error state exists; null playlist is the existing unavailable state. */
@RunWith(Parameterized::class)
class PlaylistDetailsPresentationTest(private val case: UiMatrix.Case) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = UiMatrix.cases.map { arrayOf(it) }
    }

    private fun label(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(case.language))
        }).getString(id)
    }

    @Test
    fun narrowLongTitleKeepsSquareMosaicAndPrimaryActions() {
        val title = if (case.language == "ar") "قائمة تشغيل عربية طويلة للغاية للموسيقى المسائية" else "An exceptionally long playlist title for evening music"
        var plays = 0
        var shuffles = 0
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistDetails"))
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(
                        playlist = LibraryPlaylist("mix", title, emptyList(), "{}"),
                        artworkPaths = listOf("one", "two", "three", "four"),
                    ), onPlay = { plays++ }, onShuffle = { shuffles++ },
                )
            }
        }
        rule.onNodeWithText(title).assertExists()
        val cover = rule.onAllNodesWithTag("playlist-artwork-mosaic").fetchSemanticsNodes().single().boundsInRoot
        assertEquals(cover.width, cover.height, 0.5f)
        with(rule.density) { assertEquals(280.dp.toPx(), cover.width, 0.5f) }
        val play = hasContentDescription(label(R.string.player_play))
        val shuffle = hasContentDescription(label(R.string.player_shuffle))
        rule.onNode(play).performScrollTo().assertIsDisplayed()
        matrix.assertNoClippedActions(play)
        matrix.assertNoClippedActions(shuffle)
        rule.onNode(play).performClick()
        rule.onNode(shuffle).performClick()
        assertEquals(1, plays)
        assertEquals(1, shuffles)
        matrix.captureToImage("narrow-hero")
    }

    @Test
    fun unavailableThenEmptyPlaylistKeepsItsIdentityAndMenu() {
        val state = mutableStateOf(PlaylistDetailsUiState())
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistDetailsEmpty"))
        matrix.setContent {
            AnchoredPopupMenuHost(hazeState = null) { PlaylistDetailsContent(state.value) }
        }
        rule.onNodeWithTag("mornye-header-title").assertTextEquals(label(R.string.playlist_details_empty_title)).assertIsDisplayed()
        rule.onNode(hasContentDescription(label(R.string.player_play))).assertDoesNotExist()
        rule.runOnIdle {
            state.value = PlaylistDetailsUiState(playlist = LibraryPlaylist("empty", "Empty mix", emptyList(), "{}"))
        }
        rule.onNodeWithText(label(R.string.playlist_details_empty_title)).assertDoesNotExist()
        rule.onNodeWithText("Empty mix").assertExists()
        rule.onAllNodesWithTag("playlist-detail-track-divider").assertCountEquals(0)
        rule.onNode(hasContentDescription(label(R.string.album_row_more_options))).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.playlist_reorder)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.playlist_reorder)).performClick()
        rule.onAllNodes(hasContentDescription(label(R.string.playlist_reorder_drag_handle))).assertCountEquals(0)
        rule.onNode(hasContentDescription(label(R.string.playlist_reorder_done))).performClick()
        rule.onNode(hasContentDescription(label(R.string.album_row_more_options))).assertIsDisplayed()
    }

    @Test
    fun favoritesKeepLocalizedIdentityAndProtectedActions() {
        val track = LibraryTrack("fav", "Favorite song", "Artist", metadataJson = """{"is_favorite":true}""")
        var favorite: Pair<String, Boolean>? = null
        UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistDetailsFavorites")).setContent {
            AnchoredPopupMenuHost(hazeState = null) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(LibraryPlaylist(FavoritesPlaylistId, "", listOf(track.id), "{}"), listOf(track)),
                    onTrackFavoriteToggle = { id, value -> favorite = id to value },
                )
            }
        }
        rule.onNodeWithText(label(R.string.library_favorites)).assertExists()
        rule.onNode(hasContentDescription(label(R.string.album_row_more_options))).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.playlist_delete)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.playlist_reorder)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.playlist_edit)).performClick()
        // Dismiss the editor through its existing cancel action before opening the track menu.
        rule.onNode(hasContentDescription(label(R.string.cancel))).performClick()
        rule.onNodeWithText(track.title).performScrollTo()
        rule.onNode(hasContentDescription(label(R.string.track_row_more_options))).performClick()
        rule.onNodeWithText(label(R.string.track_context_remove_from_playlist)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.track_context_remove_from_favorites)).performClick()
        assertEquals(track.id to false, favorite)
    }

    @Test
    fun dragReorderReportsDropAnchorsAndDoneRestoresTrackActions() {
        val tracks = listOf(LibraryTrack("a", "First", "Artist"), LibraryTrack("b", "Second", "Artist"))
        val moves = mutableListOf<Triple<String, String?, String?>>()
        var selected: String? = null
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistDetailsReorder"))
        matrix.setContent { reduce ->
            CompositionLocalProvider(LocalReduceTransparency provides reduce) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(LibraryPlaylist("mix", "Mix", tracks.map { it.id }, "{}"), tracks),
                        onTrackMove = { id, previous, next -> moves.add(Triple(id, previous, next)) },
                        onTrackClick = { selected = it },
                    )
                }
            }
        }
        rule.onNode(hasContentDescription(label(R.string.album_row_more_options))).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.playlist_reorder)).performClick()
        rule.onNode(hasScrollAction()).performScrollToIndex(1)
        val handle = hasContentDescription(label(R.string.playlist_reorder_drag_handle))
        rule.onAllNodes(handle).assertCountEquals(2)
        rule.onAllNodes(handle)[0].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        matrix.assertNoClippedActions(handle)
        val from = rule.onAllNodes(handle)[0].fetchSemanticsNode().boundsInRoot.center
        val to = rule.onAllNodes(handle)[1].fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(to, delayMillis = 500)
            advanceEventTime(300)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Triple("a", "b", null)), moves)
        rule.onNode(hasContentDescription(label(R.string.playlist_reorder_done))).performScrollTo().performClick()
        rule.onNodeWithText("First").performScrollTo().performClick()
        assertEquals("a", selected)
        rule.onAllNodes(handle).assertCountEquals(0)
    }
}
