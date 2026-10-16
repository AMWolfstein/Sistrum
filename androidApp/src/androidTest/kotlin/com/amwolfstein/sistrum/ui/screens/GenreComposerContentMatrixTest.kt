package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryComposer
import com.amwolfstein.sistrum.sync.LibraryGenre
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Existing behavior tests stay unchanged; these cover presentation and state transitions across M1's matrix.
 * These screens have no separate error state or transparency setting; haze is absent in these local fixtures.
 */
@RunWith(Parameterized::class)
class GenreComposerContentMatrixTest(private val case: UiMatrix.Case) {
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

    @Test
    fun genreListLoadingEmptyFilteredAndContentStates() {
        val state = mutableStateOf(LibraryGenresUiState(isLoaded = false))
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("LibraryGenres"))
        var selected: String? = null
        matrix.setContent {
            LibraryGenresContent(state.value, onGenreClick = { selected = it.id })
        }
        rule.onNodeWithText(label(R.string.genres_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.genres_no_match_title)).assertDoesNotExist()
        rule.runOnIdle { state.value = LibraryGenresUiState() }
        rule.onNodeWithText(label(R.string.genres_empty_title)).assertIsDisplayed()
        rule.runOnIdle { state.value = LibraryGenresUiState(filterQuery = "absent") }
        rule.onNodeWithText(label(R.string.genres_no_match_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.genres_empty_title)).assertDoesNotExist()
        rule.runOnIdle {
            state.value = LibraryGenresUiState(genres = listOf(
                LibraryGenre("second", "Second"), LibraryGenre("first", "First"),
            ))
        }
        rule.onNodeWithText(label(R.string.genres_no_match_title)).assertDoesNotExist()
        rule.onAllNodesWithTag("genre-row-divider").assertCountEquals(1)
        rule.onNodeWithText("First").performScrollTo().performClick()
        assertEquals("first", selected)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.genre_row_open)))
        matrix.captureToImage("content")
    }

    @Test
    fun genreLongNameWrapsAndKeepsOpenActionVisible() {
        val name = if (case.language == "ar") "موسيقى عربية كلاسيكية طويلة الاسم" else "A long classical collection name"
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("GenreRow"))
        var selected: String? = null
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                LibraryGenresContent(
                    LibraryGenresUiState(genres = listOf(LibraryGenre("long", name))),
                    onGenreClick = { selected = it.id },
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val text = rule.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        text.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(2, layouts.single().layoutInput.maxLines)
        if (case.fontScale == 2f) assertEquals(2, layouts.single().lineCount)
        assertTrue(layouts.single().size.height >= layouts.single().multiParagraph.height)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.genre_row_open)))
        rule.onNode(hasContentDescription(label(R.string.genre_row_open))).performClick()
        assertEquals("long", selected)
        matrix.captureToImage("long-name")
    }

    @Test
    fun genreDetailsMissingEmptyAndAlbumStates() {
        val state = mutableStateOf(GenreDetailsUiState())
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("GenreDetails"))
        var selected: String? = null
        matrix.setContent {
            GenreDetailsContent(state.value, listState = rememberLazyListState(),
                onAlbumClick = { selected = it.id })
        }
        rule.onNodeWithText(label(R.string.genre_details_empty_title)).assertIsDisplayed()
        rule.runOnIdle { state.value = GenreDetailsUiState(genre = LibraryGenre("entity", "Collection")) }
        rule.onNodeWithText(label(R.string.genre_details_empty_title)).assertDoesNotExist()
        rule.onNodeWithText("Collection").assertIsDisplayed()
        rule.onAllNodesWithTag("genre-detail-album-divider").assertCountEquals(0)
        rule.runOnIdle {
            state.value = state.value.copy(albums = listOf(LibraryAlbum("album", "Album", "Artist")))
        }
        rule.onNodeWithText("Album").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("album", selected)
        rule.onAllNodesWithTag("genre-detail-album-divider").assertCountEquals(2)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.album_row_open)))
        matrix.captureToImage("album")
    }

    @Test
    fun composerListLoadingEmptyFilteredAndContentStates() {
        val state = mutableStateOf(LibraryComposersUiState(isLoaded = false))
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("LibraryComposers"))
        var selected: String? = null
        matrix.setContent {
            LibraryComposersContent(state.value, onComposerClick = { selected = it.id })
        }
        rule.onNodeWithText(label(R.string.composers_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.composers_no_match_title)).assertDoesNotExist()
        rule.runOnIdle { state.value = LibraryComposersUiState() }
        rule.onNodeWithText(label(R.string.composers_empty_title)).assertIsDisplayed()
        rule.runOnIdle { state.value = LibraryComposersUiState(filterQuery = "absent") }
        rule.onNodeWithText(label(R.string.composers_no_match_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.composers_empty_title)).assertDoesNotExist()
        rule.runOnIdle {
            state.value = LibraryComposersUiState(composers = listOf(
                LibraryComposer("second", "Second"), LibraryComposer("first", "First"),
            ))
        }
        rule.onNodeWithText(label(R.string.composers_no_match_title)).assertDoesNotExist()
        rule.onAllNodesWithTag("composer-row-divider").assertCountEquals(1)
        rule.onNodeWithText("First").performScrollTo().performClick()
        assertEquals("first", selected)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.composer_row_open)))
        matrix.captureToImage("content")
    }

    @Test
    fun composerLongNameWrapsAndKeepsOpenActionVisible() {
        val name = if (case.language == "ar") "موسيقى عربية كلاسيكية طويلة الاسم" else "A long classical collection name"
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("ComposerRow"))
        var selected: String? = null
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                LibraryComposersContent(
                    LibraryComposersUiState(composers = listOf(LibraryComposer("long", name))),
                    onComposerClick = { selected = it.id },
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val text = rule.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        text.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(2, layouts.single().layoutInput.maxLines)
        if (case.fontScale == 2f) assertEquals(2, layouts.single().lineCount)
        assertTrue(layouts.single().size.height >= layouts.single().multiParagraph.height)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.composer_row_open)))
        rule.onNode(hasContentDescription(label(R.string.composer_row_open))).performClick()
        assertEquals("long", selected)
        matrix.captureToImage("long-name")
    }

    @Test
    fun composerDetailsMissingEmptyAndAlbumStates() {
        val state = mutableStateOf(ComposerDetailsUiState())
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("ComposerDetails"))
        var selected: String? = null
        matrix.setContent {
            ComposerDetailsContent(state.value, listState = rememberLazyListState(),
                onAlbumClick = { selected = it.id })
        }
        rule.onNodeWithText(label(R.string.composer_details_empty_title)).assertIsDisplayed()
        rule.runOnIdle { state.value = ComposerDetailsUiState(composer = LibraryComposer("entity", "Collection")) }
        rule.onNodeWithText(label(R.string.composer_details_empty_title)).assertDoesNotExist()
        rule.onNodeWithText("Collection").assertIsDisplayed()
        rule.onAllNodesWithTag("composer-detail-album-divider").assertCountEquals(0)
        rule.runOnIdle {
            state.value = state.value.copy(albums = listOf(LibraryAlbum("album", "Album", "Artist")))
        }
        rule.onNodeWithText("Album").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("album", selected)
        rule.onAllNodesWithTag("composer-detail-album-divider").assertCountEquals(2)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.album_row_open)))
        matrix.captureToImage("album")
    }
}
