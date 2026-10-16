package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class AlbumDetailsPresentationTest(private val case: UiMatrix.Case) {
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
    fun longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth() {
        val title = if (case.language == "ar") "عنوان ألبوم عربي طويل للغاية مع موسيقى كلاسيكية" else "An exceptionally long album title with classical music"
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("AlbumDetails"))
        var plays = 0
        var shuffles = 0
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(album = LibraryAlbum("album", title, "Artist", year = 2026)),
                    onPlay = { plays++ }, onShuffle = { shuffles++ },
                )
            }
        }
        rule.onNodeWithText(title).assertExists()
        rule.onNode(hasTestTag("detail-hero-artwork-image")).assertDoesNotExist()
        val play = hasContentDescription(label(R.string.player_play))
        val shuffle = hasContentDescription(label(R.string.player_shuffle))
        rule.onNode(play).performScrollTo().assertIsDisplayed()
        matrix.assertNoClippedActions(play)
        matrix.assertNoClippedActions(shuffle)
        rule.onNode(play).performClick()
        rule.onNode(shuffle).performClick()
        assertEquals(1, plays)
        assertEquals(1, shuffles)
        matrix.captureToImage("long-title-missing-art")
    }

    @Test
    fun localArtworkWinsOverUnavailableEmbeddedArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("album-manual-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            UiMatrix(rule, case, UiMatrix.outputDirectory("AlbumArtwork")).setContent {
                AlbumDetailsContent(AlbumDetailsUiState(album = LibraryAlbum(
                    "album", "Local cover", "Artist", artworkPath = file.absolutePath,
                    audioPath = File(context.cacheDir, "unavailable-audio.flac").absolutePath,
                )))
            }
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("detail-hero-artwork-image")).fetchSemanticsNodes().size == 1 }
            val image = rule.onNode(hasTestTag("detail-hero-artwork-image")).assertIsDisplayed().captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertTrue(pixel.red > 0.95f && pixel.green < 0.05f && pixel.blue < 0.05f)
        } finally {
            file.delete()
        }
    }

    @Test
    fun multipleDiscsKeepTheirOrderAndTrackClickIdentity() {
        val tracks = listOf(
            LibraryTrack("d2", "Disc two first", "Artist", albumId = "album", discNumber = 2, trackNumber = 1),
            LibraryTrack("d1b", "Disc one second", "Artist", albumId = "album", discNumber = 1, trackNumber = 2),
            LibraryTrack("d1a", "Disc one first", "Artist", albumId = "album", discNumber = 1, trackNumber = 1),
        )
        val state = albumDetailsUiStateFor(AlbumDetailsUiState(
            albums = listOf(LibraryAlbum("album", "Multi-disc", "Artist")), tracks = tracks,
        ), "album")
        var selected: String? = null
        UiMatrix(rule, case, UiMatrix.outputDirectory("AlbumDiscs")).setContent {
            AlbumDetailsContent(state, onTrackClick = { selected = it })
        }
        assertEquals(listOf("d1a", "d1b", "d2"), state.tracks.map { it.id })
        rule.onNodeWithText("Disc two first").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("d2", selected)
    }
}
