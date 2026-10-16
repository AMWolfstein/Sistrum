package com.amwolfstein.sistrum.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryArtist
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtistDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysArtistSummaryAndOpensSelectedAlbum() {
        var selectedAlbumId: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        albums = listOf(LibraryAlbum("album", "Absolution", "Muse"), LibraryAlbum("album-2", "Origin of Symmetry", "Muse")),
                        tracks = List(3) { index -> com.amwolfstein.sistrum.sync.LibraryTrack("$index", "Track $index", "Muse") },
                    ),
                    listState = rememberLazyListState(),
                    onAlbumClick = { selectedAlbumId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Muse").assertExists()
        composeTestRule.onNodeWithText("2 albums · 3 tracks").assertExists()
        composeTestRule.onNodeWithText("Absolution").performClick()
        composeTestRule.onAllNodesWithTag("artist-detail-album-divider").assertCountEquals(3)
        assertEquals("album", selectedAlbumId)
    }

    @Test
    fun displaysUnavailableStateWhenArtistIsMissing() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(ArtistDetailsUiState(), listState = rememberLazyListState())
            }
        }

        composeTestRule.onNodeWithText("Artist unavailable").assertExists()
    }

    @Test
    fun doesNotDisplayAnAlbumDividerForOneAlbum() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        albums = listOf(LibraryAlbum("album", "Absolution", "Muse")),
                    ),
                    listState = rememberLazyListState(),
                )
            }
        }

        composeTestRule.onAllNodesWithTag("artist-detail-album-divider").assertCountEquals(2)
    }

    @Test
    fun artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist() {
        var nextIds: List<String>? = null
        var playlistIds: List<String>? = null
        var addOnly = false
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                ArtistDetailsContent(
                    uiState = ArtistDetailsUiState(
                        artist = LibraryArtist("artist", "Muse"),
                        tracks = listOf(
                            com.amwolfstein.sistrum.sync.LibraryTrack("first", "First", "Muse"),
                            com.amwolfstein.sistrum.sync.LibraryTrack("second", "Second", "Muse"),
                        ),
                    ),
                    listState = rememberLazyListState(),
                    onPlayNext = { nextIds = it },
                    onTrackContextBottomSheet = { request ->
                        val playlistRequest = request as TrackContextBottomSheetRequest.Playlist
                        playlistIds = playlistRequest.trackIds
                        addOnly = playlistRequest.addOnly
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Artist options").performClick()
        composeTestRule.onNodeWithText("Play next").performClick()
        assertEquals(listOf("first", "second"), nextIds)

        composeTestRule.onNodeWithText("Artist options").performClick()
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf("first", "second"), playlistIds)
        assertEquals(true, addOnly)
    }

    @Test
    fun optionalSlotsKeepLocalAlbumAndOrderedQueueActions() {
        var queued: List<String>? = null
        var selected: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    ArtistDetailsContent(
                        ArtistDetailsUiState(
                            artist = LibraryArtist("artist", "Muse"),
                            albums = listOf(LibraryAlbum("local-album", "Local album", "Muse")),
                            tracks = listOf(
                                com.amwolfstein.sistrum.sync.LibraryTrack("b", "Second", "Muse"),
                                com.amwolfstein.sistrum.sync.LibraryTrack("a", "First", "Muse"),
                            ),
                        ),
                        listState = rememberLazyListState(),
                        portrait = { Box(Modifier.fillMaxSize().background(Color.Blue).testTag("portrait")) },
                        headerBackground = { Box(Modifier.fillMaxSize().testTag("header-background")) },
                        about = { Text("Local artist biography") },
                        onAddToQueue = { queued = it },
                        onAlbumClick = { selected = it.id },
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("portrait").assertIsDisplayed()
        composeTestRule.onNodeWithTag("header-background").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Artist options").performClick()
        composeTestRule.onNodeWithText("Add to queue").performClick()
        assertEquals(listOf("b", "a"), queued)
        composeTestRule.onNodeWithText("Local artist biography").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Local album").performScrollTo().performClick()
        assertEquals("local-album", selected)
    }

    @Test
    fun manualImageWinsAndRemovingItRestoresPortraitSlot() {
        val file = File.createTempFile("artist-manual-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val artist = mutableStateOf(LibraryArtist("artist", "Muse", artworkPath = file.absolutePath, hasCustomArtwork = true))
            var additions = 0
            var removals = 0
            composeTestRule.setContent {
                SistrumTheme(themeMode = ThemeMode.Dark) {
                    AnchoredPopupMenuHost(hazeState = null) {
                        ArtistDetailsContent(
                            ArtistDetailsUiState(artist = artist.value),
                            listState = rememberLazyListState(),
                            portrait = { Box(Modifier.fillMaxSize().background(Color.Blue).testTag("portrait")) },
                            onAddArtistImage = { additions++ },
                            onRemoveArtistImage = { removals++; artist.value = LibraryArtist("artist", "Muse") },
                        )
                    }
                }
            }
            composeTestRule.onNodeWithTag("portrait").assertDoesNotExist()
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithTag("detail-hero-artwork-image").fetchSemanticsNodes().size == 1
            }
            val image = composeTestRule.onNodeWithTag("detail-hero-artwork-image").captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertTrue(pixel.red > 0.95f && pixel.green < 0.05f && pixel.blue < 0.05f)
            composeTestRule.onNodeWithContentDescription("Artist options").performClick()
            composeTestRule.onNodeWithText("Add artist image").performClick()
            assertEquals(1, additions)
            composeTestRule.onNodeWithContentDescription("Artist options").performClick()
            composeTestRule.onNodeWithText("Remove artist image").performClick()
            assertEquals(1, removals)
            composeTestRule.onNodeWithTag("portrait").assertIsDisplayed()
            composeTestRule.onNodeWithTag("detail-hero-artwork-image").assertDoesNotExist()
            composeTestRule.onNodeWithContentDescription("Artist options").performClick()
            composeTestRule.onNodeWithText("Remove artist image").assertDoesNotExist()
        } finally {
            file.delete()
        }
    }

    @Test
    fun nullPortraitUsesProjectedNewestTrackCover() {
        val file = File.createTempFile("artist-default-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            composeTestRule.setContent {
                SistrumTheme(themeMode = ThemeMode.Dark) {
                    ArtistDetailsContent(
                        ArtistDetailsUiState(artist = LibraryArtist("artist", "Muse", artworkPath = file.absolutePath)),
                        listState = rememberLazyListState(),
                    )
                }
            }
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithTag("detail-hero-artwork-image").fetchSemanticsNodes().size == 1
            }
            val image = composeTestRule.onNodeWithTag("detail-hero-artwork-image").captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertTrue(pixel.green > 0.95f && pixel.red < 0.05f && pixel.blue < 0.05f)
        } finally {
            file.delete()
        }
    }
}
