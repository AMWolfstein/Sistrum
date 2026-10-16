package com.amwolfstein.sistrum.ui.screens

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class AlbumDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysPublishedYearTrackCountAndTotalDurationInHeroMetadata() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", year = 2003),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Muse", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Muse", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("album-mornye-artist").assertTextEquals("Muse")
        composeTestRule.onNodeWithTag("album-mornye-metadata").assertTextEquals("2003 · Unknown")
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("2 songs · 3 min"))
        composeTestRule.onNodeWithText("2 songs · 3 min").assertExists()
    }

    @Test
    fun displaysCopyrightBelowTheTrackListWhenPresent() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(
                    AlbumDetailsUiState(
                        album = LibraryAlbum("album", "Absolution", "Muse", copyright = "© 2003 Taste Media"),
                        tracks = listOf(LibraryTrack("one", "One", "Muse")),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("© 2003 Taste Media"))
        composeTestRule.onNodeWithText("© 2003 Taste Media").assertExists()
    }

    @Test
    fun trackOverflowShowsContextMenuWithoutGoToAlbum() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onAllNodesWithText("Go to album").assertCountEquals(0)
    }

    @Test
    fun holdingTrackOpensContextMenu() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("One").performScrollTo().performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertExists()
    }

    @Test
    fun heroOverflowShowsAlbumActionsAndAddsOnlyNonFavoriteTracks() {
        var favoriteIds = emptyList<String>()
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(
                                LibraryTrack("one", "One", "Muse"),
                                LibraryTrack("two", "Two", "Muse", metadataJson = """{"is_favorite":true}"""),
                            ),
                        ),
                        onAlbumAddToFavorites = { favoriteIds = it },
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Album options")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Add to favourites").performClick()

        org.junit.Assert.assertEquals(listOf("one"), favoriteIds)
    }

    @Test
    fun heroOverflowMenuIsAnchoredToAlbumOptions() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                    )
                }
            }
        }

        val options = composeTestRule.onNode(hasContentDescription("Album options"))
        options.performScrollTo()
        val optionsBounds = options.fetchSemanticsNode().boundsInRoot
        options.performClick()
        val menuBounds = composeTestRule.onNodeWithText("Play next").fetchSemanticsNode().boundsInRoot

        assertTrue(menuBounds.top >= optionsBounds.bottom)
    }

    @Test
    fun heroOverflowHidesAddToQueueAndRequestsPlaylistPicker() {
        var request: TrackContextBottomSheetRequest? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    AlbumDetailsContent(
                        uiState = AlbumDetailsUiState(
                            album = LibraryAlbum("album", "Absolution", "Muse"),
                            tracks = listOf(LibraryTrack("one", "One", "Muse")),
                        ),
                        playbackQueue = PlaybackQueueSnapshot(activeTrackIds = listOf("one")),
                        onTrackContextBottomSheet = { request = it },
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Album options")).performScrollTo().performClick()
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
        composeTestRule.onNodeWithText("Add to playlist").performClick()
                org.junit.Assert.assertEquals(TrackContextBottomSheetRequest.Playlist(listOf("one"), addOnly = true), request)
    }

    @Test
    fun metadataUsesGenreYearAndExistingQualityLabel() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(
                    album = LibraryAlbum("album", "Absolution", "Muse", year = 2003),
                    tracks = listOf(LibraryTrack("one", "One", "Muse", metadataJson =
                        """{"genres":[{"name":"Rock"}],"format":"flac","bit_depth":24,"sample_rate":96000}""")),
                ))
            }
        }
        composeTestRule.onNodeWithTag("album-mornye-metadata").assertTextEquals("Rock · 2003 · Hi-Res")
    }

    @Test
    fun numberedMultiDiscSongsKeepSelectionAndFooter() {
        var selected = ""
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(
                    album = LibraryAlbum("album", "Double", "Artist"),
                    tracks = listOf(
                        LibraryTrack("a", "First disc song", "Artist", discNumber = 1, trackNumber = 7,
                            metadataJson = """{"duration":60}"""),
                        LibraryTrack("b", "Second disc song", "Artist", discNumber = 2, trackNumber = 1,
                            metadataJson = """{"duration":120}"""),
                    ),
                ), onTrackClick = { selected = it })
            }
        }
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("Disc 1"))
        composeTestRule.onNodeWithText("Disc 1").assertExists()
        composeTestRule.onNodeWithText("7", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("Disc 2"))
        composeTestRule.onNodeWithText("Disc 2").assertExists()
        composeTestRule.onNodeWithText("1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Second disc song").performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals("b", selected) }
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("2 songs · 3 min"))
        composeTestRule.onNodeWithText("2 songs · 3 min").assertExists()
    }

    @Test
    fun missingTagsUseSequentialNumbersWithoutDiscHeader() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(
                    album = LibraryAlbum("album", "Album", "Artist"),
                    tracks = listOf(LibraryTrack("a", "Song A", "Artist"), LibraryTrack("b", "Song B", "Artist")),
                ))
            }
        }
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("Song B"))
        composeTestRule.onNodeWithText("Song B").performScrollTo()
        composeTestRule.onNodeWithText("1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("2", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Disc 1").assertDoesNotExist()
    }

    @Test
    fun emptyAlbumOmitsUnknownMetadataAndHasZeroFooter() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(album = LibraryAlbum("album", "Empty")))
            }
        }
        composeTestRule.onNodeWithTag("album-mornye-metadata").assertTextEquals("")
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("0 songs · 0 min"))
        composeTestRule.onNodeWithText("0 songs · 0 min").assertExists()
    }

    @Test
    fun longArabicTitleAtFontTwoWrapsAndPlaybackAndBackActionsWork() {
        var plays = 0
        var shuffles = 0
        var backs = 0
        val title = "عنوان ألبوم عربي طويل جدا ".repeat(8)
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                SistrumTheme(themeMode = ThemeMode.Light) {
                    AlbumDetailsContent(AlbumDetailsUiState(album = LibraryAlbum("album", title, "Artist")),
                        onPlay = { plays++ }, onShuffle = { shuffles++ }, onBack = { backs++ })
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithTag("mornye-header-title")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        assertTrue(!layouts.single().didOverflowHeight)
        assertTrue(!layouts.single().didOverflowWidth)
        assertTrue(!layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
        composeTestRule.onNode(hasContentDescription("Play")).performScrollTo().performClick()
        composeTestRule.onNode(hasContentDescription("Shuffle")).performScrollTo().performClick()
        composeTestRule.onNode(hasContentDescription("Go back")).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, shuffles)
            assertEquals(1, backs)
        }
    }


    @Test
    fun rawGenreWithoutYearOmitsEmptySeparators() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(
                    album = LibraryAlbum("album", "Album", "Artist"),
                    tracks = listOf(LibraryTrack("one", "Song", "Artist",
                        metadataJson = """{"raw_genre_names":"Jazz","format":"mp3"}""")),
                ))
            }
        }
        composeTestRule.onNodeWithTag("album-mornye-metadata").assertTextEquals("Jazz · Lossy")
    }

    @Test
    fun unavailableAlbumKeepsDescriptionAndBackAction() {
        var backs = 0
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AlbumDetailsContent(AlbumDetailsUiState(), onBack = { backs++ })
            }
        }
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("This album is no longer in your library."))
        composeTestRule.onNodeWithText("This album is no longer in your library.").assertExists()
        composeTestRule.onNode(hasContentDescription("Go back")).performClick()
        composeTestRule.runOnIdle { assertEquals(1, backs) }
    }

}
