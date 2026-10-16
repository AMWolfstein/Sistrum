package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.sync.LibraryComposer
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Local fixtures only; no error state or Haze source exists in the search contract. */
@RunWith(Parameterized::class)
class SearchPresentationTest(private val case: UiMatrix.Case) {
    @get:Rule val rule = createComposeRule()

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

    @Test fun controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone() {
        val state = mutableStateOf(LibrarySearchUiState(query = "restored"))
        UiMatrix(rule, case, UiMatrix.outputDirectory("SearchStates")).setContent {
            LibrarySearchContent(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
        }
        val input = rule.onNode(hasSetTextAction())
        input.assertTextEquals("restored")
        input.performClick().assertIsFocused()
        input.performTextReplacement("الموسيقى")
        rule.runOnIdle { assertEquals("الموسيقى", state.value.query) }
        rule.onNode(hasContentDescription(label(R.string.text_field_clear))).performClick()
        input.assertTextEquals("")
        rule.onNodeWithText(label(R.string.library_search_empty_description)).assertIsDisplayed()
        rule.runOnIdle { state.value = LibrarySearchUiState(query = "pending", isLoaded = false) }
        input.assertTextEquals("pending")
        rule.onNodeWithTag("search-loading").assertIsDisplayed()
        rule.onNodeWithText(label(R.string.library_search_no_results_title)).assertDoesNotExist()
        rule.runOnIdle { state.value = state.value.copy(isLoaded = true) }
        rule.onNodeWithTag("search-loading").assertDoesNotExist()
        rule.onNodeWithText(label(R.string.library_search_no_results_title)).assertIsDisplayed()
        input.performClick().performImeAction()
        input.assertIsNotFocused()
    }

    @Test fun categorizedResultsKeepEveryDestinationCallback() {
        val clicked = mutableListOf<String>()
        val state = LibrarySearchUiState(
            query = "local", tracks = listOf(LibraryTrack("t", "Track result", "Artist")),
            albums = listOf(LibraryAlbum("a", "Album result", "Artist")),
            artists = listOf(LibraryArtist("r", "Artist result")),
            playlists = listOf(LibraryPlaylist("p", "Playlist result", emptyList(), "{}")),
            composers = listOf(LibraryComposer("c", "Composer result")),
        )
        UiMatrix(rule, case, UiMatrix.outputDirectory("SearchCategories")).setContent {
            LibrarySearchContent(state, onTrackClick = { clicked += it }, onAlbumClick = { clicked += it },
                onArtistClick = { clicked += it }, onPlaylistClick = { clicked += it }, onComposerClick = { clicked += it })
        }
        listOf("Track result", "Album result", "Artist result", "Playlist result", "Composer result").forEachIndexed { index, title ->
            rule.onNodeWithTag("search-results").performScrollToIndex(index * 2)
            rule.onNodeWithText(title).assertIsDisplayed().performClick()
        }
        assertEquals(listOf("t", "a", "r", "p", "c"), clicked)
    }

    @Test fun narrowTrackColumnsGrowForLongEnglishAndArabicNames() {
        val title = if (case.language == "ar") "اسم مقطوعة موسيقية عربية طويل للغاية" else "An exceptionally long local music track title"
        val tracks = List(3) { LibraryTrack("$it", "$title $it", "Artist") }
        var clicked: String? = null
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("SearchTracks"))
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                LibrarySearchContent(LibrarySearchUiState(query = "local", tracks = tracks), onTrackClick = { clicked = it })
            }
        }
        // A fixed-height grid used to clip the last row at larger font scales.
        rule.onNodeWithText("$title 2").assertIsDisplayed().performClick()
        assertEquals("2", clicked)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.track_row_more_options)))
        matrix.captureToImage("three-tracks")
    }
}
