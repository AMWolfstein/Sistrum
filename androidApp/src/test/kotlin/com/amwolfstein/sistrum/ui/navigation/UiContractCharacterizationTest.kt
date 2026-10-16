package com.amwolfstein.sistrum.ui.navigation

import com.amwolfstein.sistrum.AppDestination
import com.amwolfstein.sistrum.AppIntent
import com.amwolfstein.sistrum.AppStackPage
import com.amwolfstein.sistrum.AppUiState
import com.amwolfstein.sistrum.MainViewModel
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.StackPageEntry
import com.amwolfstein.sistrum.currentStackPage
import com.amwolfstein.sistrum.player.PlaybackItem
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.player.PlaybackState
import com.amwolfstein.sistrum.player.RepeatMode
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.settings.ThemeModeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.amwolfstein.sistrum.settings.IntegrationPreferences
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T001 host baseline: literal expectations characterize the shipped UI, before restyling.
 *
 * Existing coverage (kept unchanged):
 * - MainViewModelTest: retained destination stacks, reselect-to-root, back pops and detail cleanup.
 *   AppNavigationMetadataTest: destination order and symbols. Here we also pin root/detail titles,
 *   stack indices and transition order. Saved destination stacks mean switching tabs within the
 *   current ViewModel; recreation and full-player-before-page back priority belong to device
 *   AppNavigationTest.systemBackClosesFullScreenPlayerBeforeNavigating and T002.
 * - ContentScrollDirectionTest: consumed-delta sign, accumulation and reversal. Here we pin exact
 *   collapse/expand thresholds, interruption and reset; paired chrome geometry belongs to T002.
 * - Device MiniPlayerTest.hidesWhenPlaybackIsIdleOrFailed: visibility; device
 *   QueueTransportAvailabilityTest: boundary transport and swipe gating. Here both policies run
 *   on the host, including empty queues, missing selections and every repeat mode.
 * - FullScreenPlayerSwipeTest: vertical jitter, short flick rejection and deliberate swipe math;
 *   FullScreenPlayerSeekTest: preview retained until seek confirmation. Those existing tests are
 *   the DEFAULT gesture baseline; do not duplicate their math or assertions here.
 * - PlayerLyricsParserTest: browse entry, timed/plain bilingual text, metadata and positive offset;
 *   LyricsBrowseModeTest: idle timeout/restart and current-line prominence. Here we pin the browse
 *   truth table, timeout constant, empty input, negative/clamped and malformed offset entry points.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiContractCharacterizationTest {
    @get:Rule val temporary = TemporaryFolder()

    private var preferenceScope: CoroutineScope? = null

    @After
    fun resetMainDispatcher() {
        preferenceScope?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun destinationStacksResumeAndBackUnwindsOnlyTheSelectedStack() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = object : ThemeModeStore {
            override val themeMode = MutableStateFlow(ThemeMode.System)
            override val reduceTransparency = MutableStateFlow(false)
            override suspend fun setThemeMode(themeMode: ThemeMode) { this.themeMode.value = themeMode }
            override suspend fun setReduceTransparency(enabled: Boolean) { reduceTransparency.value = enabled }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        preferenceScope = scope
        val viewModel = MainViewModel(store, IntegrationPreferences(
            PreferenceDataStoreFactory.create(scope = scope) {
                temporary.root.resolve("integrations.preferences_pb")
            },
        ))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.LibraryAlbums))
        viewModel.dispatch(AppIntent.OpenAlbumDetails("album-1"))
        viewModel.dispatch(AppIntent.OpenPage(AppStackPage.SettingsLyrics))
        viewModel.dispatch(AppIntent.SelectDestination(AppDestination.Library))
        advanceUntilIdle()
        assertEquals(AppDestination.Library, viewModel.uiState.value.selectedDestination)
        assertEquals(AppStackPage.AlbumDetails, viewModel.uiState.value.currentPage)
        assertEquals("album-1", viewModel.uiState.value.selectedAlbumId)

        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()
        assertEquals(AppStackPage.LibraryAlbums, viewModel.uiState.value.currentPage)
        assertEquals(null, viewModel.uiState.value.selectedAlbumId)
        assertEquals(
            listOf(AppStackPage.Root, AppStackPage.SettingsLyrics),
            viewModel.uiState.value.stackFor(AppDestination.Settings),
        )
        viewModel.dispatch(AppIntent.NavigateBack)
        viewModel.dispatch(AppIntent.NavigateBack)
        advanceUntilIdle()
        assertEquals(AppDestination.Library, viewModel.uiState.value.selectedDestination)
        assertEquals(listOf(AppStackPage.Root), viewModel.uiState.value.stackFor(AppDestination.Library))

        viewModel.dispatch(AppIntent.SelectDestination(AppDestination.Settings))
        advanceUntilIdle()
        assertEquals(AppStackPage.SettingsLyrics, viewModel.uiState.value.currentPage)
        viewModel.dispatch(AppIntent.SelectDestination(AppDestination.Settings))
        advanceUntilIdle()
        assertEquals(AppStackPage.Root, viewModel.uiState.value.currentPage)
    }

    @Test
    fun rootAndDetailMetadataFollowTheSelectedDestination() {
        val roots = AppUiState()
        for ((destination, title) in listOf(
            AppDestination.Home to R.string.destination_home,
            AppDestination.Insight to R.string.destination_insight,
            AppDestination.Library to R.string.destination_library,
            AppDestination.Settings to R.string.destination_settings,
        )) {
            assertEquals(listOf(AppStackPage.Root), roots.stackFor(destination))
            assertEquals(StackPageEntry(destination, AppStackPage.Root, 0), roots.stackFor(destination).currentStackPage(destination))
            assertEquals(title, destination.titleRes)
            assertEquals(title, AppStackPage.Root.titleRes(destination))
        }
        assertEquals(R.string.album_details_title, AppStackPage.AlbumDetails.titleRes(AppDestination.Library))
        assertEquals(R.string.lyrics_title, AppStackPage.SettingsLyrics.titleRes(AppDestination.Settings))
    }

    @Test
    fun transitionDirectionUsesActualStackDepthRatherThanPageEnumOrder() {
        val root = StackPageEntry(AppDestination.Library, AppStackPage.Root, 0)
        val album = StackPageEntry(AppDestination.Library, AppStackPage.AlbumDetails, 1)
        val artist = StackPageEntry(AppDestination.Library, AppStackPage.ArtistDetails, 2)
        assertEquals(artist, listOf(AppStackPage.Root, AppStackPage.AlbumDetails, AppStackPage.ArtistDetails).currentStackPage(AppDestination.Library))
        assertTrue(isForwardTransition(album, root))
        assertTrue(isForwardTransition(artist, album))
        assertFalse(isForwardTransition(album, artist))
        assertFalse(isForwardTransition(root, album))
        assertFalse(isForwardTransition(album, album))
        assertFalse(isForwardTransition(album.copy(destination = AppDestination.Settings), album))
    }

    @Test
    fun circleCollapsesAndExpandsAtTheExactAccumulatedThreshold() {
        val accumulator = NavigationChromeScrollAccumulator()
        assertFalse(accumulator.compact)
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 23f), 24f))
        assertTrue(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 1f), 24f))
        assertTrue(accumulator.compact)
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 100f), 24f))
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 23f), 24f))
        assertTrue(accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 1f), 24f))
        assertFalse(accumulator.compact)
    }

    @Test
    fun scrollingInTheCurrentModeDiscardsAPendingModeChange() {
        val accumulator = NavigationChromeScrollAccumulator()
        accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 24f), 24f)
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 23f), 24f))
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 1f), 24f))
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Down, 1f), 24f))
        assertTrue(accumulator.compact)
        accumulator.reset()
        assertFalse(accumulator.compact)
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 23f), 24f))
        accumulator.reset()
        assertFalse(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 1f), 24f))
        assertTrue(accumulator.update(ContentScrollDelta(ContentScrollDirection.Up, 23f), 24f))
    }

    @Test
    fun miniPlayerVisibilityDependsOnPlaybackState() {
        val item = PlaybackItem("one", "Track", "Artist", "/synthetic/one.flac")
        assertFalse(PlaybackState.Idle.showsMiniPlayer())
        assertFalse(PlaybackState.Failed("one", "synthetic failure").showsMiniPlayer())
        assertTrue(PlaybackState.Preparing(item).showsMiniPlayer())
        assertTrue(PlaybackState.Playing(item, 0L, 60_000L).showsMiniPlayer())
        assertTrue(PlaybackState.Paused(item, 60_000L, 60_000L).showsMiniPlayer())
    }

    @Test
    fun queueTransportStaysEnabledAtBothEndsAndForAnEmptyQueue() {
        assertTrue(PlaybackQueueSnapshot().canNavigatePrevious())
        assertTrue(PlaybackQueueSnapshot().canNavigateNext())
        for (repeat in RepeatMode.entries) {
            for (index in 0..1) {
                val queue = PlaybackQueueSnapshot(activeTrackIds = listOf("one", "two"), currentIndex = index, repeatMode = repeat)
                assertTrue("previous: $repeat/$index", queue.canNavigatePrevious())
                assertTrue("next: $repeat/$index", queue.canNavigateNext())
            }
            val single = PlaybackQueueSnapshot(activeTrackIds = listOf("one"), currentIndex = 0, repeatMode = repeat)
            assertTrue(single.canNavigatePrevious())
            assertTrue(single.canNavigateNext())
        }
        for (index in listOf(-1, 2)) {
            val unselected = PlaybackQueueSnapshot(activeTrackIds = listOf("one", "two"), currentIndex = index)
            assertFalse(unselected.canNavigatePrevious())
            assertFalse(unselected.canNavigateNext())
        }
    }

    @Test
    fun swipeSignSelectsTheCorrespondingAvailableTransport() {
        for (previous in listOf(false, true)) {
            for (next in listOf(false, true)) {
                assertEquals(previous, canDispatchQueueSwipe(1, previous, next))
                assertEquals(next, canDispatchQueueSwipe(-1, previous, next))
                assertFalse(canDispatchQueueSwipe(0, previous, next))
            }
        }
    }

    @Test
    fun lyricsBrowsingRequiresUserDraggingWithoutProgrammaticFollowing() {
        assertEquals(4_000L, LyricsBrowseIdleTimeoutMs)
        assertFalse(shouldEnterLyricsBrowseMode(false, false))
        assertFalse(shouldEnterLyricsBrowseMode(false, true))
        assertTrue(shouldEnterLyricsBrowseMode(true, false))
        assertFalse(shouldEnterLyricsBrowseMode(true, true))
    }

    @Test
    fun lyricsOffsetParsingClampsAtZeroAndAppliesOnlyToFollowingLines() {
        assertEquals(emptyList<PlayerLyricLine>(), parsePlayerLyrics("\n[ti:Title]\n"))
        assertEquals(
            listOf(
                PlayerLyricLine("Before", timestampSeconds = 1f),
                PlayerLyricLine("Clamped", "Translation", 0f),
                PlayerLyricLine("After", timestampSeconds = 1.5f),
                PlayerLyricLine("Plain", "Translation"),
            ),
            parsePlayerLyrics("[00:01.00]Before\n[offset:-1500]\n[00:01.00]Clamped ^ Translation\n[00:03.00]After\nPlain / Translation"),
        )
        assertEquals(
            listOf(PlayerLyricLine("Reset", timestampSeconds = 2f)),
            parsePlayerLyrics("[offset:500]\n[offset:invalid]\n[00:02.00]Reset"),
        )
    }
}
