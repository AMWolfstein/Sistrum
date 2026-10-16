package com.amwolfstein.sistrum.ui.navigation

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.App
import com.amwolfstein.sistrum.AppDestination
import com.amwolfstein.sistrum.AppIntent
import com.amwolfstein.sistrum.AppStackPage
import com.amwolfstein.sistrum.MainViewModel
import com.amwolfstein.sistrum.PlaybackModel
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.player.PlaybackState
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.settings.ThemeModeStore
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** T002: shipped Compose contracts. Device execution and the unchanged suite baseline belong to T008. */
class UiContractCharacterizationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun circleCollapseRelocatesMiniPlayerBesideNavigationAndExpansionRestoresBounds() {
        var compact by mutableStateOf(false)
        rule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    NavigationChrome(
                        selectedDestination = AppDestination.Home,
                        playbackState = playing,
                        hazeState = null,
                        compact = compact,
                        onExpandClick = { compact = false },
                        onDestinationSelected = {},
                        onPreviousClick = {}, onPlayPauseClick = {}, onNextClick = {},
                        onMiniPlayerDismiss = {},
                    )
                }
            }
        }
        val miniBefore = rule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot
        val homeBefore = rule.onNodeWithContentDescription(label(R.string.destination_home))
            .fetchSemanticsNode().boundsInRoot
        // Exercise the same consumed-scroll policy App uses to drive the paired transition.
        rule.runOnIdle {
            val scroll = NavigationChromeScrollAccumulator()
            scroll.update(ContentScrollDelta(ContentScrollDirection.Up, 24f), 24f)
            compact = scroll.compact
        }
        rule.onNodeWithContentDescription(label(R.string.destination_library)).assertDoesNotExist()
        val circle = rule.onNodeWithContentDescription(label(R.string.destination_home))
        circle.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val miniAfter = rule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot
        val homeAfter = circle.fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertEquals(64f * density, miniAfter.left - miniBefore.left, 1f)
        assertEquals(80f * density, miniAfter.top - miniBefore.top, 1f)
        assertEquals(homeBefore.left, homeAfter.left, 1f)
        assertTrue(homeAfter.top > homeBefore.top)
        assertTrue(homeAfter.width < homeBefore.width)
        assertTrue(homeAfter.right < miniAfter.left)
        assertEquals(homeAfter.center.y, miniAfter.center.y, 1f)

        circle.performClick()
        val miniRestored = rule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot
        val homeRestored = rule.onNodeWithContentDescription(label(R.string.destination_home))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(miniBefore, miniRestored)
        assertEquals(homeBefore, homeRestored)
        rule.onNodeWithContentDescription(label(R.string.destination_library)).assertIsDisplayed()
    }

    @Test
    fun destinationAndChildPageSurviveActivityRecreationAndTabRoundTrip() {
        val originalActivity = rule.activity
        val originalModel = attachApp()
        rule.onNodeWithContentDescription(label(R.string.destination_library)).performClick()
        rule.onNodeWithContentDescription(label(R.string.library_artists)).performClick()
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertIsDisplayed()

        rule.activityRule.scenario.recreate()
        val restoredModel = attachApp()
        assertNotSame(originalActivity, rule.activity)
        assertSame(originalModel, restoredModel)
        rule.onNodeWithContentDescription(label(R.string.destination_library)).assertIsSelected()
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(label(R.string.destination_settings)).performClick()
        rule.onNodeWithContentDescription(label(R.string.destination_library)).performClick()
        rule.onNodeWithText(label(R.string.artists_empty_title)).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(listOf(AppStackPage.Root, AppStackPage.LibraryArtists), restoredModel.uiState.value.stackFor(AppDestination.Library))
        }
    }

    @Test
    fun backFromDetailPopsToItsListBeforeLibraryRoot() {
        val model = attachApp()
        rule.runOnIdle {
            model.dispatch(AppIntent.OpenPage(AppStackPage.LibraryAlbums))
            model.dispatch(AppIntent.OpenAlbumDetails("missing-album"))
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(AppStackPage.AlbumDetails, model.uiState.value.currentPage) }
        pressBack()
        rule.runOnIdle {
            assertEquals(AppStackPage.LibraryAlbums, model.uiState.value.currentPage)
            assertEquals(null, model.uiState.value.selectedAlbumId)
        }
        pressBack()
        rule.onNodeWithContentDescription(label(R.string.destination_library)).assertIsSelected()
        rule.runOnIdle { assertEquals(listOf(AppStackPage.Root), model.uiState.value.stackFor(AppDestination.Library)) }
    }

    @Test
    fun backFromFullPlayerClosesItBeforePoppingUnderlyingDetail() {
        val model = attachApp()
        rule.runOnIdle {
            model.dispatch(AppIntent.OpenPage(AppStackPage.LibraryAlbums))
            model.dispatch(AppIntent.OpenAlbumDetails("missing-album"))
        }
        rule.onNodeWithText(item.title).performClick()
        rule.onNodeWithContentDescription(label(R.string.full_screen_player)).assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription(label(R.string.full_screen_player)).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(AppStackPage.AlbumDetails, model.uiState.value.currentPage)
            assertEquals("missing-album", model.uiState.value.selectedAlbumId)
        }
        pressBack()
        rule.runOnIdle { assertEquals(AppStackPage.LibraryAlbums, model.uiState.value.currentPage) }
    }

    @Test
    fun defaultPlayerKeepsTransportSeekQueueLyricsAndBothFavoriteTransitions() {
        var state by mutableStateOf<PlaybackState>(playing)
        var favorite by mutableStateOf(false)
        val calls = mutableListOf<String>()
        val favorites = mutableListOf<Pair<String, Boolean>>()
        rule.setContent {
            SistrumTheme(themeMode = ThemeMode.Dark) {
                FullScreenPlayer(
                    visible = true, dragProgress = 0f, isDragging = false,
                    openingFromMiniPlayerSwipe = false,
                    playbackState = state,
                    queue = PlaybackQueueSnapshot(activeTrackIds = listOf(item.trackId), currentIndex = 0),
                    queueTracks = listOf(LibraryTrack(item.trackId, item.title, item.artist)),
                    lyrics = "Characterization lyric",
                    volume = 0.5f, onSeek = {}, onVolumeChange = {},
                    onPrevious = { calls += "previous" },
                    onPlayPause = {
                        if (state is PlaybackState.Playing) {
                            calls += "pause"
                            state = PlaybackState.Paused(item, 0L, 120_000L)
                        } else {
                            calls += "play"
                            state = playing
                        }
                    },
                    onNext = { calls += "next" },
                    isFavorite = favorite,
                    onFavoriteToggle = { id, enabled -> favorites += id to enabled; favorite = enabled },
                    onOpenMediaOutputSwitcher = {}, onDismiss = {},
                )
            }
        }
        rule.onNodeWithContentDescription(label(R.string.player_seek)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Play").performClick()
        rule.onNodeWithContentDescription("Previous").performClick()
        rule.onNodeWithContentDescription("Next").performClick()
        rule.onNodeWithContentDescription(label(R.string.player_heart)).performClick()
        rule.onNodeWithContentDescription(label(R.string.player_heart)).performClick()
        rule.runOnIdle {
            assertEquals(listOf("pause", "play", "previous", "next"), calls)
            assertEquals(listOf(item.trackId to true, item.trackId to false), favorites)
        }
        rule.onNodeWithContentDescription(label(R.string.player_queue)).performClick()
        rule.onNodeWithTag("full_screen_queue_panel_header").assertIsDisplayed()
        rule.onNodeWithContentDescription(label(R.string.player_lyrics)).performClick()
        rule.onNodeWithText("Characterization lyric").assertIsDisplayed()
        rule.onNodeWithTag("full_screen_queue_panel_header").assertDoesNotExist()
    }

    // Real destination owner retained by the Activity's ViewModelStore; no fake save/restore reducer.
    // Reattach the same App content after recreation, as a host Activity's onCreate would do.
    private fun attachApp(): MainViewModel {
        lateinit var model: MainViewModel
        rule.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity, MainViewModel.Factory(object : ThemeModeStore {
                override val themeMode = MutableStateFlow(ThemeMode.Dark)
                override val reduceTransparency = MutableStateFlow(true)
                override suspend fun setThemeMode(themeMode: ThemeMode) { this.themeMode.value = themeMode }
                override suspend fun setReduceTransparency(enabled: Boolean) { reduceTransparency.value = enabled }
            }, com.amwolfstein.sistrum.settings.IntegrationPreferences(activity)))[MainViewModel::class.java]
            activity.setContent {
                val state by model.uiState.collectAsState()
                App(uiState = state, playback = PlaybackModel(state = playing), onIntent = model::dispatch)
            }
        }
        rule.waitForIdle()
        return model
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        val item = PlaybackItem("characterization-track", "Characterization track", "Characterization artist", "/audio/fake.flac")
        val playing = PlaybackState.Playing(item, 0L, 120_000L)
    }
}
