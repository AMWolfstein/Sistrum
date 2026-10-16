package com.amwolfstein.sistrum.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import com.amwolfstein.sistrum.ui.components.AnchoredPopupMenuHost
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryPlaylist
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class PlaylistDetailsContentTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun heroArtworkMatchesAlbumDetailsSize() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                Box(Modifier.width(280.dp)) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(
                            playlist = LibraryPlaylist("mix", "Night drive", emptyList(), "{}"),
                            artworkPaths = listOf("one", "two", "three", "four"),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("playlist-artwork-mosaic").assertWidthIsEqualTo(280.dp)
    }

    @Test
    fun displaysTrackCountAndDesktopStyleDurationWithoutArtistOrCopyright() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(
                        playlist = LibraryPlaylist("mix", "Night drive", listOf("one", "two"), "{}"),
                        tracks = listOf(
                            LibraryTrack("one", "One", "Artist", metadataJson = """{"duration":60}"""),
                            LibraryTrack("two", "Two", "Artist", metadataJson = """{"duration":120}"""),
                        ),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Night drive").assertExists()
        composeTestRule.onNodeWithText("2 songs").assertExists()
        composeTestRule.onNodeWithTag("mornye-list").performScrollToNode(hasText("2 songs · 3 min"))
        composeTestRule.onNodeWithText("2 songs · 3 min").assertExists()
    }

    @Test
    fun trackMenuEndsWithDestructiveRemoveFromPlaylistAction() {
        val track = LibraryTrack("one", "One", "Artist")
        var removedTrackId: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(
                            playlist = LibraryPlaylist("mix", "Night drive", listOf(track.id), "{}"),
                            tracks = listOf(track),
                        ),
                        playbackQueue = PlaybackQueueSnapshot(activeTrackIds = listOf(track.id)),
                        onTrackRemoveFromPlaylist = { removedTrackId = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("One").performScrollTo()
        composeTestRule.onNode(hasContentDescription("Track options")).performClick()
        composeTestRule.onNodeWithText("Remove from playlist").performClick()
        assertEquals(track.id, removedTrackId)
    }

    @Test
    fun heroMoreShowsPlaylistPlaybackAndEditingActions() {
        val track = LibraryTrack("one", "One", "Artist")
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(
                            playlist = LibraryPlaylist("mix", "Night drive", listOf(track.id), "{}"),
                            tracks = listOf(track),
                        ),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("More options")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Play next").assertExists()
        composeTestRule.onNodeWithText("Add to queue").assertExists()
        composeTestRule.onNodeWithText("Edit playlist").assertExists()
        composeTestRule.onNodeWithText("Delete playlist").assertExists()
    }

    @Test
    fun reorderModeReplacesTrackOptionsWithDragHandlesAndDoneAction() {
        val tracks = listOf(LibraryTrack("one", "One", "Artist"), LibraryTrack("two", "Two", "Artist"))
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(PlaylistDetailsUiState(LibraryPlaylist("mix", "Night drive", tracks.map { it.id }, "{}"), tracks))
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("More options")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Reorder playlist").performClick()
        composeTestRule.onNodeWithText("One").performScrollTo()
        composeTestRule.onAllNodes(hasContentDescription("Drag to reorder track")).assertCountEquals(2)
        composeTestRule.onNode(hasContentDescription("Done reordering")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("One").performScrollTo()
        composeTestRule.onAllNodes(hasContentDescription("Track options")).assertCountEquals(2)
    }

    @Test
    fun favoritesMenuOffersEditButNotDelete() {
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                AnchoredPopupMenuHost(hazeState = null) {
                    PlaylistDetailsContent(
                        PlaylistDetailsUiState(playlist = LibraryPlaylist(FavoritesPlaylistId, "", emptyList(), "{}")),
                    )
                }
            }
        }

        composeTestRule.onNode(hasContentDescription("More options")).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Edit playlist").assertExists()
        composeTestRule.onAllNodesWithText("Delete playlist").assertCountEquals(0)
    }

    @Test
    fun emptyPlaylistKeepsActionsBackAndZeroFooter() {
        var plays = 0
        var shuffles = 0
        var backs = 0
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Light) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(playlist = LibraryPlaylist("empty", "Empty mix", emptyList(), "{}")),
                    onPlay = { plays++ }, onShuffle = { shuffles++ }, onBack = { backs++ },
                )
            }
        }
        composeTestRule.onNodeWithTag("mornye-header-title").assertTextEquals("Empty mix")
        composeTestRule.onNode(hasContentDescription("Play")).performScrollTo().performClick()
        composeTestRule.onNode(hasContentDescription("Shuffle")).performClick()
        composeTestRule.onNode(hasContentDescription("Go back")).performClick()
        composeTestRule.onNodeWithText("0 songs · 0 min").performScrollTo().assertExists()
        composeTestRule.onAllNodes(hasContentDescription("Track options")).assertCountEquals(0)
        assertEquals(1, plays)
        assertEquals(1, shuffles)
        assertEquals(1, backs)
    }

    @Test
    fun favoritesCustomCoverReplacesGridAndSongTapKeepsTrackId() {
        val track = LibraryTrack("favorite", "Favorite song", "Artist")
        var selected: String? = null
        composeTestRule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                PlaylistDetailsContent(
                    PlaylistDetailsUiState(
                        playlist = LibraryPlaylist(FavoritesPlaylistId, "", listOf(track.id), "{}"),
                        tracks = listOf(track), customArtworkPath = "custom",
                        artworkPaths = listOf("one", "two", "three", "four"),
                    ), onTrackClick = { selected = it },
                )
            }
        }
        composeTestRule.onNodeWithTag("mornye-header-title").assertTextEquals("Favorites")
        composeTestRule.onAllNodesWithTag("playlist-artwork-mosaic").assertCountEquals(0)
        composeTestRule.onNodeWithText(track.title).performScrollTo().performClick()
        assertEquals(track.id, selected)
        composeTestRule.onNodeWithText("1 song · 0 min").performScrollTo().assertExists()
    }
}
