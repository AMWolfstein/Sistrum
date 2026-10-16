package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryAlbum
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.ui.components.TrackContextBottomSheetRequest
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryAlbumsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoAlbums() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) { LibraryAlbumsContent(LibraryAlbumsUiState()) }
        }
        composeTestRule.onNodeWithText("No albums in library").assertExists()
    }

    @Test
    fun displaysAlbumsInVirtualizedRowsAndUsesUnknownArtistFallback() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(
                        albums = listOf(
                            LibraryAlbum(id = "a", title = "Album A", artist = "Artist A"),
                            LibraryAlbum(id = "b", title = "Album B"),
                        ),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Album A").assertExists()
        composeTestRule.onNodeWithText("Artist A").assertExists()
        composeTestRule.onNodeWithText("Unknown artist").assertExists()
        composeTestRule.onAllNodesWithTag("album-row-divider").assertCountEquals(1)
    }

    @Test
    fun playbackActionsAppearForAlbumsAndDispatchTheirMode() {
        var playMode: Boolean? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(albums = listOf(LibraryAlbum(id = "album", title = "Album"))),
                    onPlayAll = { playMode = it },
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Play")).performClick()
        assertEquals(false, playMode)
        composeTestRule.onNode(hasContentDescription("Shuffle")).performClick()
        assertEquals(true, playMode)
    }

    @Test
    fun holdingAnAlbumOpensItsContextMenu() {
        var request: TrackContextBottomSheetRequest? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryAlbumsContent(
                        LibraryAlbumsUiState(
                            albums = listOf(LibraryAlbum(id = "album", title = "Album A", artist = "Artist A")),
                            tracks = listOf(LibraryTrack("track", "Track", "Artist A", albumId = "album")),
                        ),
                        onTrackContextBottomSheet = { request = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Album A").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNode(hasContentDescription("Add to favourites")).assertExists()
        composeTestRule.onNodeWithText("Add to playlist").performClick()
        assertEquals(
            TrackContextBottomSheetRequest.Playlist(listOf("track"), addOnly = true),
            request,
        )
    }

    @Test
    fun displaysAlbumsInTwoColumnGridWithoutListDividers() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryAlbumsContent(
                    LibraryAlbumsUiState(
                        layoutMode = AlbumLayoutMode.Grid,
                        albums = listOf(
                            LibraryAlbum(id = "a", title = "Album A", artist = "Artist A"),
                            LibraryAlbum(id = "b", title = "Album B"),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Album A").assertExists()
        composeTestRule.onNodeWithText("Album B").assertExists()
        composeTestRule.onNodeWithText("Unknown artist").assertExists()
        composeTestRule.onAllNodesWithTag("album-row-divider").assertCountEquals(0)
    }

    @Test
    fun loadingAndFilteredEmptyStatesStayDistinct() {
        var state by mutableStateOf(LibraryAlbumsUiState(isLoaded = false))
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) { LibraryAlbumsContent(state) }
        }
        composeTestRule.onNodeWithText("No albums in library").assertDoesNotExist()
        composeTestRule.runOnIdle { state = LibraryAlbumsUiState(filterQuery = "missing") }
        composeTestRule.onNodeWithText("No matching albums").assertIsDisplayed()
        composeTestRule.onNodeWithText("No albums in library").assertDoesNotExist()
    }

    @Test
    fun reorderedGridKeepsAlbumSelectionAndCollectionActions() {
        val alpha = LibraryAlbum("alpha", "Alpha", "Artist")
        val zulu = LibraryAlbum("zulu", "Zulu", "Artist")
        var albums by mutableStateOf(listOf(alpha, zulu))
        val actions = mutableListOf<Pair<String, List<String>>>()
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryAlbumsContent(
                        uiState = LibraryAlbumsUiState(
                            layoutMode = AlbumLayoutMode.Grid, albums = albums,
                            tracks = listOf(
                                LibraryTrack("second", "Second", "Artist", albumId = "zulu", trackNumber = 2),
                                LibraryTrack("first", "First", "Artist", albumId = "zulu", trackNumber = 1),
                                LibraryTrack("other", "Other", "Artist", albumId = "alpha"),
                            ),
                        ),
                        onAlbumClick = { actions += "select" to listOf(it.id) },
                        onAlbumPlay = { id, shuffle -> actions += "play:$shuffle" to listOf(id) },
                        onAlbumPlayNext = { actions += "next" to it },
                        onAlbumAddToQueue = { actions += "queue" to it },
                        onAlbumAddToFavorites = { actions += "favorite" to it },
                    )
                }
            }
        }
        composeTestRule.runOnIdle { albums = listOf(zulu, alpha) }
        composeTestRule.onNodeWithText("Zulu").performClick()
        for (action in listOf("Play", "Shuffle", "Play next", "Add to queue", "Add to favorites")) {
            composeTestRule.onNodeWithText("Zulu").performTouchInput { longClick() }
            composeTestRule.onAllNodesWithText(action).onLast().performClick()
        }
        assertEquals(
            listOf(
                "select" to listOf("zulu"), "play:false" to listOf("zulu"), "play:true" to listOf("zulu"),
                "next" to listOf("first", "second"), "queue" to listOf("first", "second"),
                "favorite" to listOf("first", "second"),
            ), actions,
        )
    }

    @Test
    fun tenThousandAlbumsKeepListAndGridCompositionBounded() {
        val albums = List(10_000) { LibraryAlbum("album-$it", "Large album $it", "Artist") }
        var layout by mutableStateOf(AlbumLayoutMode.List)
        lateinit var listState: LazyListState
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            SistrumTheme(ThemeMode.Dark) {
                LibraryAlbumsContent(
                    uiState = LibraryAlbumsUiState(albums = albums, layoutMode = layout, sortOption = AlbumSortOption.DateAdded),
                    listState = rememberLazyListState().also { listState = it },
                    modifier = Modifier.height(320.dp).testTag("large-albums"),
                )
            }
        }
        fun assertBounded() {
            val count = composeTestRule.onAllNodes(hasText("Large album ", substring = true)).fetchSemanticsNodes().size
            assertTrue("Composed album cards/rows: $count", count in 1..40)
        }
        assertBounded()
        composeTestRule.onNodeWithTag("large-albums").performScrollToIndex(5_002)
        composeTestRule.onNodeWithText("Large album 5000").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("album-5000", listState.layoutInfo.visibleItemsInfo.first().key) }
        composeTestRule.onNodeWithText("Large album 0").assertDoesNotExist()
        assertBounded()
        composeTestRule.runOnIdle { layout = AlbumLayoutMode.Grid }
        composeTestRule.onNodeWithTag("large-albums").performScrollToIndex(2_502)
        composeTestRule.onNodeWithText("Large album 5000").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("album-5000", listState.layoutInfo.visibleItemsInfo.first().key) }
        composeTestRule.onNodeWithText("Large album 0").assertDoesNotExist()
        assertBounded()
        val before = composeTestRule.onNodeWithText("Large album 5000").fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("Large album 5000").assertIsDisplayed()
        assertEquals(before, composeTestRule.onNodeWithText("Large album 5000").fetchSemanticsNode().boundsInRoot)
        assertBounded()
    }

    @Test
    fun longArabicTitleUsesTwoLinesAndKeepsTheAlbumActionVisibleAtLargeFont() {
        val title = "عنوان طويل للأغنية والألبوم مع تفاصيل كثيرة ".repeat(4)
        val album = LibraryAlbum("album", title, "Artist")
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                SistrumTheme(ThemeMode.Dark) {
                    LibraryAlbumsContent(
                        LibraryAlbumsUiState(albums = listOf(album)),
                        modifier = Modifier.width(360.dp).height(320.dp),
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        composeTestRule.onNode(hasContentDescription("Open album")).assertIsDisplayed()
    }
}
