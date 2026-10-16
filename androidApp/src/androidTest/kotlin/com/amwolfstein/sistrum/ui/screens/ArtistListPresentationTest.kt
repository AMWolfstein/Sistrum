package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.ui.components.ArtistRow
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** No Haze source or error state exists here; transparency variants use the opaque portrait surface. */
@RunWith(Parameterized::class)
class ArtistListPresentationTest(private val case: UiMatrix.Case) {
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
    fun loadingEmptyFilteredAndContentStatesKeepArtistIdentity() {
        val state = mutableStateOf(LibraryArtistsUiState(isLoaded = false))
        var selected: String? = null
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("ArtistList"))
        matrix.setContent {
            LibraryArtistsContent(state.value, onArtistClick = { selected = it.id })
        }
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertDoesNotExist()
        rule.onNodeWithText(label(R.string.artists_no_match_title)).assertDoesNotExist()
        rule.runOnIdle { state.value = LibraryArtistsUiState() }
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertIsDisplayed()
        rule.runOnIdle { state.value = LibraryArtistsUiState(filterQuery = "absent") }
        rule.onNodeWithText(label(R.string.artists_no_match_title)).assertIsDisplayed()
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertDoesNotExist()
        rule.runOnIdle {
            state.value = LibraryArtistsUiState(artists = listOf(
                LibraryArtist("second", "Second"), LibraryArtist("first", "First"),
            ))
        }
        rule.onNodeWithText(label(R.string.artists_no_match_title)).assertDoesNotExist()
        rule.onAllNodesWithTag("artist-row-divider").assertCountEquals(1)
        rule.onNodeWithText("First").performClick()
        assertEquals("first", selected)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.artist_row_open)))
        matrix.captureToImage("content")
    }

    @Test
    fun suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible() {
        val name = if (case.language == "ar") "اسم فنان عربي طويل للغاية للموسيقى الكلاسيكية" else "An exceptionally long classical music artist name"
        var clicks = 0
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("ArtistPortrait"))
        matrix.setContent {
            Box(Modifier.width(280.dp).background(Color.White)) {
                ArtistRow(name, onClick = { clicks++ }, portrait = {
                    Box(Modifier.fillMaxSize().background(Color.Red).testTag("portrait"))
                })
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(2, layouts.single().layoutInput.maxLines)
        if (case.fontScale == 2f) assertEquals(2, layouts.single().lineCount)
        matrix.assertNoClippedActions(hasContentDescription(label(R.string.artist_row_open)))
        rule.onNodeWithText(name).performClick()
        rule.onNode(hasContentDescription(label(R.string.artist_row_open))).performClick()
        assertEquals(2, clicks)
        val image = rule.onNodeWithTag("portrait", useUnmergedTree = true).captureToImage()
        assertEquals(image.width, image.height)
        val pixels = image.toPixelMap()
        assertTrue(pixels[image.width / 2, image.height / 2].red > 0.95f)
        assertTrue(pixels[image.width / 2, image.height / 2].green < 0.05f)
        // A square slot would paint the corner red; the row must clip its supplied content.
        assertTrue(pixels[1, 1].green > 0.95f)
        matrix.captureToImage("round-long-name")
    }

    @Test
    fun portraitOverridesLocalArtworkAndEmptySlotRetainsOfflineFallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("artist-local-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val path = mutableStateOf<String?>(file.absolutePath)
            val supplied = mutableStateOf(false)
            UiMatrix(rule, case, UiMatrix.outputDirectory("ArtistFallback")).setContent {
                ArtistRow(
                    "Local artist", modifier = Modifier.testTag("row"), artworkPath = path.value,
                    portrait = if (supplied.value) {
                        { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                    } else null,
                )
            }
            fun centerPixel(): Color {
                val image = rule.onNodeWithTag("row").captureToImage()
                val inset = with(rule.density) { 48.dp.toPx() }.toInt()
                val x = if (case.language == "ar") image.width - inset else inset
                return image.toPixelMap()[x, image.height / 2]
            }
            fun centerIsRed(): Boolean = centerPixel().let {
                it.red > 0.95f && it.green < 0.05f && it.blue < 0.05f
            }
            rule.waitUntil(5_000) { centerIsRed() }
            rule.runOnIdle { supplied.value = true }
            val suppliedPixel = centerPixel()
            assertTrue(suppliedPixel.blue > 0.95f && suppliedPixel.red < 0.05f)
            rule.runOnIdle { supplied.value = false }
            rule.waitUntil(5_000) { centerIsRed() }
            rule.runOnIdle { path.value = null }
            assertTrue(!centerIsRed())
            rule.onNodeWithText("Local artist").assertIsDisplayed()
        } finally {
            file.delete()
        }
    }

    @Test
    fun listForwardsEachArtistToPortraitWithoutComposingTheWholeLibrary() {
        val artists = List(1_000) { LibraryArtist("artist-$it", "Artist $it") }
        val active = mutableSetOf<String>()
        var selected: String? = null
        UiMatrix(rule, case, UiMatrix.outputDirectory("ArtistVirtualList")).setContent {
            LibraryArtistsContent(
                LibraryArtistsUiState(artists = artists),
                onArtistClick = { selected = it.id },
                portrait = { artist ->
                    DisposableEffect(artist.id) {
                        active.add(artist.id)
                        onDispose { active.remove(artist.id) }
                    }
                    Box(Modifier.fillMaxSize().testTag("portrait-${artist.id}"))
                },
            )
        }
        rule.onNodeWithTag("portrait-artist-0", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { assertTrue(active.size in 1..50) }
        // Item zero is the filter, so artist 500 is at lazy index 501.
        rule.onNode(hasScrollAction()).performScrollToIndex(501)
        rule.onNodeWithTag("portrait-artist-500", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Artist 500").performClick()
        assertEquals("artist-500", selected)
        rule.runOnIdle { assertTrue(active.size in 1..50) }
    }
}
