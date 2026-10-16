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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.longClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTracksContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysEmptyStateWhenNoTracks() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = emptyList()),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No tracks in library").assertExists()
    }

    @Test
    fun doesNotDisplayEmptyStateBeforeInitialLoad() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(isLoaded = false),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No tracks in library").assertDoesNotExist()
    }

    @Test
    fun displaysTracksList() {
        val sampleTracks = listOf(
            LibraryTrack(id = "1", title = "Song A", artists = "Artist A", album = "Album A"),
            LibraryTrack(id = "2", title = "Song B", artists = "Artist B", album = "Album B"),
        )

        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(
                        tracks = sampleTracks,
                        sortOption = TrackSortOption.Name,
                        sortOrder = SortOrder.Ascending,
                    ),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").assertExists()
        composeTestRule.onNodeWithText("Song B").assertExists()
        composeTestRule.onAllNodesWithTag("track-row-divider").assertCountEquals(1)
    }

    @Test
    fun keepsTheFilterVisibleAndReportsChangesWhenAQueryIsActive() {
        var changedQuery: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(
                        tracks = listOf(LibraryTrack(id = "1", title = "Song A", artists = "Artist A")),
                        filterQuery = "song",
                    ),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onFilterQueryChange = { changedQuery = it },
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Search...")).assertExists().performTextInput(" a")
        assertEquals("song a", changedQuery)
    }

    @Test
    fun showsTheMatchingEmptyStateForAnActiveFilter() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(filterQuery = "missing"),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No matching tracks").assertExists()
    }

    @Test
    fun keepsFilterFocusWhenItsQueryLeavesNoMatches() {
        var state by mutableStateOf(
            LibraryTracksUiState(
                tracks = listOf(LibraryTrack(id = "1", title = "Song A", artists = "Artist A")),
                filterQuery = "s",
            ),
        )
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = state,
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onFilterQueryChange = { query ->
                        state = state.copy(
                            tracks = emptyList(),
                            filterQuery = query,
                        )
                    },
                )
            }
        }

        composeTestRule.onNode(hasContentDescription("Search..."))
            .performClick()
            .assertIsFocused()
            .performTextInput("missing")

        composeTestRule.onNode(hasContentDescription("Search...")).assertIsFocused()
        composeTestRule.onNodeWithText("No matching tracks").assertExists()
    }

    @Test
    fun playbackActionsAppearForTracksAndDispatchTheirMode() {
        var playMode: Boolean? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = listOf(LibraryTrack("track", "Song", "Artist"))),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
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
    fun clickingTrackEmitsTheSelectedTrack() {
        val track = LibraryTrack(id = "selected", title = "Song A", artists = "Artist A", album = "Album A")
        var selectedId: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = listOf(track)),
                    onSortOptionSelected = {},
                    onToggleSortOrder = {},
                    onTrackClick = { selectedId = it.id },
                )
            }
        }

        composeTestRule.onNodeWithText("Song A").performClick()
        assertEquals("selected", selectedId)
    }

    @Test
    fun moreOptionsOpensTrackContextMenu() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Add to favorites").assertExists()
        composeTestRule.onNodeWithText("Add to playlist").assertExists()
    }

    @Test
    fun holdingTrackOpensTrackContextMenu() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Song A").performTouchInput { longClick() }

        composeTestRule.onNodeWithText("Track info").assertExists()
    }

    @Test
    fun contextMenuHidesMissingAlbumAndArtistNavigation() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()

        composeTestRule.onAllNodesWithText("Go to album").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Go to artist").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Go to artists").assertCountEquals(0)
    }

    @Test
    fun trackRemainsVisibleWhileContextBottomSheetIsOpen() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Track info").performClick()

        composeTestRule.onNodeWithText("Track info").assertExists()
        composeTestRule.onNodeWithText("Song A").assertExists()
    }

    @Test
    fun currentTrackHidesQueueActions() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                        playbackQueue = PlaybackQueueSnapshot(
                            activeTrackIds = listOf(track.id),
                            currentIndex = 0,
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onAllNodesWithText("Play next").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
    }

    @Test
    fun alreadyQueuedTrackHidesAddToQueueAction() {
        val track = LibraryTrack(id = "track-1", title = "Song A", artists = "Artist A")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {},
                        onToggleSortOrder = {},
                        playbackQueue = PlaybackQueueSnapshot(
                            activeTrackIds = listOf("current", track.id),
                            currentIndex = 0,
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("Track options")).performClick()

        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onAllNodesWithText("Add to queue").assertCountEquals(0)
    }

    @Test
    fun reorderedTracksKeepTheirIdsAndDispatchQueueAndFavoriteActions() {
        val alpha = LibraryTrack("alpha", "Alpha", "Artist")
        val zulu = LibraryTrack("zulu", "Zulu", "Artist")
        var tracks by mutableStateOf(listOf(alpha, zulu))
        val actions = mutableListOf<Pair<String, String>>()
        composeTestRule.setContent {
            SistrumTheme(ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    LibraryTracksContent(
                        uiState = LibraryTracksUiState(tracks = tracks),
                        onSortOptionSelected = {}, onToggleSortOrder = {},
                        onTrackClick = { actions += "select" to it.id },
                        onTrackPlayNext = { actions += "next" to it.id },
                        onTrackAddToQueue = { actions += "queue" to it.id },
                        onTrackFavoriteToggle = { track, favorite -> actions += "favorite:$favorite" to track.id },
                    )
                }
            }
        }
        composeTestRule.runOnIdle { tracks = listOf(zulu, alpha) }
        val zuluBounds = composeTestRule.onNodeWithText("Zulu").fetchSemanticsNode().boundsInRoot
        val alphaBounds = composeTestRule.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot
        assertTrue(zuluBounds.top < alphaBounds.top)
        composeTestRule.onNodeWithText("Zulu").performClick()
        for (action in listOf("Play next", "Add to queue", "Add to favorites")) {
            composeTestRule.onNodeWithText("Zulu").performTouchInput { longClick() }
            composeTestRule.onNodeWithText(action).performClick()
        }
        assertEquals(listOf("select" to "zulu", "next" to "zulu", "queue" to "zulu", "favorite:true" to "zulu"), actions)
    }

    @Test
    fun tenThousandTracksKeepCompositionBoundedAndRestoreScrolledPosition() {
        val tracks = List(10_000) { LibraryTrack("track-$it", "Large song $it", "Artist") }
        lateinit var listState: LazyListState
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            SistrumTheme(ThemeMode.Dark) {
                LibraryTracksContent(
                    uiState = LibraryTracksUiState(tracks = tracks, sortOption = TrackSortOption.DateAdded),
                    onSortOptionSelected = {}, onToggleSortOrder = {},
                    listState = rememberLazyListState().also { listState = it },
                    modifier = Modifier.height(320.dp).testTag("large-tracks"),
                )
            }
        }
        fun assertBounded() {
            val count = composeTestRule.onAllNodes(hasText("Large song ", substring = true)).fetchSemanticsNodes().size
            assertTrue("Composed track rows: $count", count in 1..40)
        }
        assertBounded()
        // Filter and playback controls are the first two lazy items.
        composeTestRule.onNodeWithTag("large-tracks").performScrollToIndex(5_002)
        composeTestRule.onNodeWithText("Large song 5000").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("track-5000", listState.layoutInfo.visibleItemsInfo.first().key) }
        composeTestRule.onNodeWithText("Large song 0").assertDoesNotExist()
        assertBounded()
        val before = composeTestRule.onNodeWithText("Large song 5000").fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText("Large song 5000").assertIsDisplayed()
        assertEquals(before, composeTestRule.onNodeWithText("Large song 5000").fetchSemanticsNode().boundsInRoot)
        assertBounded()
    }

    @Test
    fun longArabicTitleUsesTwoLinesAndKeepsTheTrackActionVisibleAtLargeFont() {
        val title = "عنوان طويل للأغنية والألبوم مع تفاصيل كثيرة ".repeat(4)
        val track = LibraryTrack("track", title, "Artist")
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                SistrumTheme(ThemeMode.Dark) {
                    LibraryTracksContent(
                        LibraryTracksUiState(tracks = listOf(track)),
                        onSortOptionSelected = {}, onToggleSortOrder = {},
                        modifier = Modifier.width(360.dp).height(320.dp),
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        composeTestRule.onNode(hasContentDescription("Track options")).assertIsDisplayed()
    }
}
