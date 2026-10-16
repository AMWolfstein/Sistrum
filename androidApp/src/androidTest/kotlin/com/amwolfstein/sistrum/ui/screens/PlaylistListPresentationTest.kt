package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.ui.components.PlaylistRow
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** No separate error state or Haze source: both transparency settings use opaque artwork/editor fills. */
@RunWith(Parameterized::class)
class PlaylistListPresentationTest(private val case: UiMatrix.Case) {
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
    fun loadingEmptyAndContentKeepFavoritesAndClickIdentity() {
        val state = mutableStateOf(LibraryPlaylistsUiState(isLoaded = false))
        var selected: String? = null
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistStates"))
        matrix.setContent {
            LibraryPlaylistsContent(state.value, listState = LazyListState(), onPlaylistClick = { selected = it })
        }
        rule.onNodeWithText(label(R.string.playlists_empty_title)).assertDoesNotExist()
        rule.runOnIdle { state.value = LibraryPlaylistsUiState() }
        rule.onNodeWithText(label(R.string.playlists_empty_title)).assertIsDisplayed()
        rule.runOnIdle {
            state.value = LibraryPlaylistsUiState(listOf(PlaylistListItem("favorites", ""), PlaylistListItem("p", "Night")))
        }
        rule.onNodeWithText(label(R.string.playlists_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.library_favorites)).performClick()
        assertEquals("favorites", selected)
        rule.onNodeWithText("Night").performClick()
        assertEquals("p", selected)
        rule.onAllNodesWithTag("playlist-row-divider").assertCountEquals(1)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.playlist_row_open)))
        matrix.captureToImage()
    }

    @Test
    fun narrowLongNameUsesTwoLinesAndKeepsOpenTarget() {
        val name = if (case.language == "ar") "قائمة تشغيل عربية طويلة للغاية للموسيقى الكلاسيكية" else "An exceptionally long classical music playlist name"
        var clicks = 0
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistLongName"))
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                PlaylistRow("p", name, emptyList(), onClick = { clicks++ })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(2, layouts.single().layoutInput.maxLines)
        assertEquals(2, layouts.single().lineCount)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.playlist_row_open)))
        rule.onNodeWithContentDescription(label(R.string.playlist_row_open)).performClick()
        rule.onNodeWithText(name).performClick()
        assertEquals(2, clicks)
        matrix.captureToImage()
    }

    @Test
    fun createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover() {
        var created: String? = null
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistCreate"))
        matrix.setContent {
            CreatePlaylistBottomSheet(onDismiss = {}, onCreate = { name, uri ->
                created = name
                assertNull(uri)
            })
        }
        rule.onNodeWithTag("playlist-create-button").assertIsNotEnabled()
        rule.onNodeWithTag("playlist-name-input").performTextInput("   ")
        rule.onNodeWithTag("playlist-create-button").assertIsNotEnabled()
        val name = if (case.language == "ar") "موسيقى المساء" else "Evening music"
        rule.onNodeWithTag("playlist-name-input").performTextInput("$name  ")
        rule.onNodeWithTag("playlist-create-button").assertIsEnabled()
        rule.onNodeWithTag("playlist-create-button").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("playlist-create-button").performClick()
        assertEquals(name, created)
    }

    @Test
    fun favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover() {
        var saved = false
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistFavoritesEditor"))
        matrix.setContent {
            EditPlaylistBottomSheet("", "local-cover.png", showNameInput = false, onDismiss = {},
                onSave = { name, uri, clear ->
                    assertEquals("", name)
                    assertNull(uri)
                    assertTrue(clear)
                    saved = true
                })
        }
        rule.onAllNodesWithTag("playlist-name-input").assertCountEquals(0)
        rule.onNodeWithTag("playlist-create-button").assertIsEnabled()
        rule.onNodeWithContentDescription(label(R.string.playlist_clear_artwork)).performClick()
        rule.onNodeWithContentDescription(label(R.string.playlist_clear_artwork)).assertDoesNotExist()
        rule.onNodeWithTag("playlist-create-button").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("playlist-create-button").performClick()
        assertTrue(saved)
    }

    @Test
    fun contextActionsKeepQueueOrderRenameAndConfirmedDelete() {
        var next: List<String>? = null
        var queue: List<String>? = null
        var updated: String? = null
        var deleted: String? = null
        UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistActions")).setContent {
            AnchoredPopupMenuHost(hazeState = null) {
                LibraryPlaylistsContent(
                    LibraryPlaylistsUiState(listOf(PlaylistListItem("p", "Night", trackIds = listOf("b", "a")))),
                    listState = LazyListState(), onPlaylistPlayNext = { next = it }, onPlaylistAddToQueue = { queue = it },
                    onPlaylistUpdate = { id, name, uri, clear ->
                        assertEquals("p", id)
                        assertNull(uri)
                        assertEquals(false, clear)
                        updated = name
                    }, onPlaylistDelete = { deleted = it },
                )
            }
        }
        fun openMenu() = rule.onNodeWithText("Night").performSemanticsAction(SemanticsActions.OnLongClick)
        openMenu()
        rule.onNodeWithText(label(R.string.track_context_play_next)).performClick()
        assertEquals(listOf("b", "a"), next)
        openMenu()
        rule.onNodeWithText(label(R.string.track_context_add_to_queue)).performClick()
        assertEquals(listOf("b", "a"), queue)
        openMenu()
        rule.onNodeWithText(label(R.string.playlist_edit)).performClick()
        rule.onNodeWithTag("playlist-name-input").performTextReplacement("  Renamed  ")
        rule.onNodeWithTag("playlist-create-button").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertEquals("Renamed", updated)
        openMenu()
        rule.onNodeWithText(label(R.string.playlist_delete)).performClick()
        assertNull(deleted)
        rule.onNodeWithText(label(R.string.playlist_delete)).performClick()
        assertEquals("p", deleted)
    }

    @Test
    fun manualCoverRendersAndLargeListKeepsScrollStateAndBoundedRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("playlist-cover-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val showRow = mutableStateOf(true)
            val listState = LazyListState(500, 0)
            var selected: String? = null
            UiMatrix(rule, case, UiMatrix.outputDirectory("PlaylistCoverAndScroll")).setContent {
                if (showRow.value) PlaylistRow("p", "Manual", listOf(file.absolutePath), Modifier.testTag("row"))
                else LibraryPlaylistsContent(
                    LibraryPlaylistsUiState(List(1_000) { PlaylistListItem("p$it", "Playlist $it") }),
                    listState = listState, onPlaylistClick = { selected = it },
                )
            }
            rule.waitUntil(5_000) {
                val image = rule.onNodeWithTag("row").captureToImage()
                val inset = with(rule.density) { 79.dp.toPx() }.toInt()
                val x = if (case.language == "ar") image.width - inset else inset
                image.toPixelMap()[x, image.height / 2].let { it.red > 0.95f && it.green < 0.05f && it.blue < 0.05f }
            }
            rule.runOnIdle { showRow.value = false }
            rule.onNodeWithText("Playlist 500").assertIsDisplayed()
            rule.runOnIdle { assertEquals(500, listState.firstVisibleItemIndex) }
            assertTrue(rule.onAllNodes(hasContentDescription(label(R.string.playlist_row_open))).fetchSemanticsNodes().size in 1..30)
            rule.onNode(hasScrollAction()).performScrollToIndex(900)
            rule.onNodeWithText("Playlist 900").performClick()
            assertEquals("p900", selected)
            assertTrue(rule.onAllNodes(hasContentDescription(label(R.string.playlist_row_open))).fetchSemanticsNodes().size in 1..30)
        } finally {
            file.delete()
        }
    }
}
