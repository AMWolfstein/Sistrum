package com.amwolfstein.sistrum.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.sync.LibraryComposer
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun queryAndResultsScrollRestoreAndBackRemainsWithTheHost() {
        val restoration = StateRestorationTester(rule)
        lateinit var listState: LazyListState
        var backCount = 0
        val showingSearch = mutableStateOf(true)
        val results = LibrarySearchUiState(
            tracks = listOf(LibraryTrack("t", "Track result", "Artist")),
            albums = listOf(LibraryAlbum("a", "Album result", "Artist")),
            artists = listOf(LibraryArtist("r", "Artist result")),
            playlists = listOf(LibraryPlaylist("p", "Playlist result", emptyList(), "{}")),
            composers = listOf(LibraryComposer("c", "Composer result")),
        )
        restoration.setContent {
            SistrumTheme(themeMode = com.amwolfstein.sistrum.settings.ThemeMode.Dark) {
                var query by rememberSaveable { mutableStateOf("") }
                val list = rememberLazyListState()
                BackHandler { backCount++ }
                if (showingSearch.value) {
                    LibrarySearchContent(results.copy(query = query), listState = list, onQueryChange = { query = it })
                }
                androidx.compose.runtime.SideEffect {
                    listState = list
                }
            }
        }
        rule.onNode(hasSetTextAction()).performClick().performTextReplacement("local query")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.onNodeWithTag("search-results").performScrollToIndex(8)
        rule.onNodeWithText("Composer result").assertIsDisplayed()
        val before = rule.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
        assertTrue("Search results must be scrolled before restoration", before.first > 0 || before.second > 0)
        rule.runOnIdle { showingSearch.value = false }
        rule.onNodeWithTag("search-results").assertDoesNotExist()
        rule.runOnIdle { showingSearch.value = true }
        rule.onNode(hasSetTextAction()).assertTextEquals("local query")
        rule.runOnIdle { assertEquals(before, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNode(hasSetTextAction()).assertTextEquals("local query")
        rule.runOnIdle { assertEquals(before, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset) }
        rule.onNodeWithText("Composer result").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.runOnIdle { assertEquals(1, backCount) }
    }
}
