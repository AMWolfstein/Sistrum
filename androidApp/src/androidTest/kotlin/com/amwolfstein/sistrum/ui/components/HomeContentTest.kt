package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.player.PlaybackQueueSnapshot
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun homeTracks(count: Int) = (0 until count).map {
    LibraryTrack(id = "t$it", title = "Song $it", artists = "Artist $it", album = "Album $it", albumId = "a$it",
        metadataJson = """{"explicit":true,"format":"flac","year":2026}""")
}

@Composable
private fun HomeFixture(
    recent: List<LibraryTrack> = emptyList(),
    shelf: List<LibraryTrack> = emptyList(),
    loaded: Boolean = true,
    state: LazyListState = rememberLazyListState(),
    click: (List<LibraryTrack>, String) -> Unit = { _, _ -> },
    playNext: (LibraryTrack) -> Unit = {},
) {
    AnchoredPopupMenuHost(hazeState = null) {
        HomeContent(isLoaded = loaded, recentTracks = recent, keepListeningTracks = shelf,
            mostPlayedTracks = shelf, forgottenTracks = shelf, onTrackClick = click,
            playbackQueue = PlaybackQueueSnapshot(), onTrackPlayNext = playNext,
            onTrackAddToQueue = {}, onTrackFavoriteToggle = { _, _ -> }, onTrackAlbumClick = {},
            onTrackArtistClick = {}, onTrackContextBottomSheet = {}, listState = state,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 100.dp),
            modifier = Modifier.fillMaxSize())
    }
}

class HomeContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyLibraryShowsEmptyStateAfterLoading() {
        rule.setContent { SistrumTheme(ThemeMode.Dark) { HomeFixture() } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.library_empty_title)).assertIsDisplayed()
        rule.onNodeWithTag("home-recent-carousel").assertDoesNotExist()
    }

    @Test fun loadingDoesNotClaimLibraryIsEmpty() {
        rule.setContent { SistrumTheme(ThemeMode.Dark) { HomeFixture(loaded = false) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.library_empty_title)).assertDoesNotExist()
    }

    @Test fun recentHeroPlaysItsRepresentativeFromTheUnchangedRecentQueue() {
        val tracks = homeTracks(3).let { listOf(it[0], it[1], it[2].copy(albumId = "a0")) }
        var queue = emptyList<LibraryTrack>()
        var selected = ""
        rule.setContent { SistrumTheme(ThemeMode.Dark) {
            HomeFixture(recent = tracks, click = { q, id -> queue = q; selected = id })
        } }
        rule.onNodeWithTag("home-hero-t0").assertIsDisplayed().performClick()
        assertEquals(tracks, queue)
        assertEquals("t0", selected)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.resources.getQuantityString(R.plurals.album_details_track_count, 2, 2) + " · 2026")
            .assertIsDisplayed()
        rule.onNodeWithTag("home-hero-t2").assertDoesNotExist()
    }

    @Test fun largeLibraryIsVirtualizedAndTitleCollapsesOnScroll() {
        rule.setContent { SistrumTheme(ThemeMode.Dark) { HomeFixture(recent = homeTracks(1000), shelf = homeTracks(1000)) } }
        rule.onNodeWithTag("home-large-title").assertIsDisplayed()
        rule.onNodeWithTag("home-hero-t999").assertDoesNotExist()
        rule.onNodeWithTag("home-list").performTouchInput { swipeUp() }
        rule.onNodeWithTag("home-collapsed-title").assertIsDisplayed()
    }

    @Test fun shelfClickAndLongPressKeepTrackActions() {
        val tracks = homeTracks(4)
        var selected = ""
        var queue = emptyList<LibraryTrack>()
        var next: LibraryTrack? = null
        rule.setContent { SistrumTheme(ThemeMode.Dark) {
            HomeFixture(shelf = tracks, click = { q, id -> queue = q; selected = id }, playNext = { next = it })
        } }
        rule.onAllNodesWithText("Song 0")[0].performClick()
        assertEquals("t0", selected)
        assertEquals(tracks, queue)
        rule.onAllNodesWithText("Song 0")[0].performTouchInput { longClick() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.track_context_play_next)).performClick()
        assertEquals(tracks[0], next)
    }

    @Test fun shelfKeepsExplicitAndQualityBadges() {
        rule.setContent { SistrumTheme(ThemeMode.Dark) { HomeFixture(shelf = homeTracks(1)) } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onAllNodesWithText(context.getString(R.string.track_explicit))[0].assertIsDisplayed()
        rule.onAllNodesWithText(context.getString(R.string.track_info_quality_lossless))[0].assertIsDisplayed()
    }

    @Test fun suppliedScrollStateIsUsedWhenHomeReturns() {
        rule.setContent { SistrumTheme(ThemeMode.Dark) {
            Box(Modifier.height(320.dp)) {
                HomeFixture(shelf = homeTracks(20), state = rememberLazyListState(initialFirstVisibleItemIndex = 2))
            }
        } }
        rule.onNodeWithTag("home-collapsed-title").assertIsDisplayed()
        rule.onNodeWithTag("home-large-title").assertDoesNotExist()
    }

    @Test fun heroLongPressKeepsTrackActions() {
        var next: LibraryTrack? = null
        val tracks = homeTracks(1)
        rule.setContent { SistrumTheme(ThemeMode.Dark) { HomeFixture(recent = tracks, playNext = { next = it }) } }
        rule.onNodeWithTag("home-hero-t0").performTouchInput { longClick() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithText(context.getString(R.string.track_context_play_next)).performClick()
        assertEquals(tracks[0], next)
    }
    @Test fun fullRecentListIncludesEveryTrackAndPlaysTheOriginalQueue() {
        val tracks = homeTracks(30)
        var queue = emptyList<LibraryTrack>()
        var selected = ""
        rule.setContent { SistrumTheme(ThemeMode.Dark) {
            HomeFixture(recent = tracks, click = { q, id -> queue = q; selected = id })
        } }
        rule.onNodeWithTag("home-recent-full-list").performClick()
        rule.onNodeWithTag("home-recent-list").performScrollToIndex(29)
        rule.onNodeWithText("Song 29").performClick()
        assertEquals(tracks, queue)
        assertEquals("t29", selected)
        rule.onNodeWithTag("home-recent-list").assertDoesNotExist()
    }

}

@RunWith(Parameterized::class)
class HomeContentMatrixTest(private val case: UiMatrix.Case) {
    @get:Rule val rule = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases() = UiMatrix.cases.map { arrayOf(it) }
    }
    @Test fun heroRemainsClickableAtEveryFontAndCarouselsMirror() {
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("HomeContent"))
        var clicked = false
        matrix.setContent {
            HomeFixture(recent = homeTracks(5), shelf = homeTracks(5), click = { _, _ -> clicked = true })
        }
        val first = rule.onNodeWithTag("home-hero-t0").assertIsDisplayed()
        first.performClick()
        assertTrue(clicked)
        val a = first.fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithTag("home-hero-t1").fetchSemanticsNode().boundsInRoot
        if (case.language == "ar") assertTrue(b.left < a.left) else assertTrue(b.left > a.left)
        matrix.assertNoClippedActions(hasTestTag("home-hero-t0"))
        matrix.captureToImage()
        rule.onNodeWithTag("home-list").performScrollToIndex(2)
        val shelfFirst = rule.onAllNodesWithTag("home-track-t0")[0].assertIsDisplayed()
        val shelfSecond = rule.onAllNodesWithTag("home-track-t1")[0]
        val firstBounds = shelfFirst.fetchSemanticsNode().boundsInRoot
        val nextBounds = shelfSecond.fetchSemanticsNode().boundsInRoot
        if (case.language == "ar") assertTrue(nextBounds.left < firstBounds.left)
        else assertTrue(nextBounds.left > firstBounds.left)
        clicked = false
        shelfFirst.performClick()
        assertTrue(clicked)
        matrix.assertNoClippedActions(hasTestTag("home-track-t0") and
            hasAnyAncestor(hasTestTag("home-shelf-${R.string.home_keep_listening}")))
        matrix.captureToImage("shelf")
    }
}

/** Literal task dimensions across viewport widths, font scales and layout directions. */
@RunWith(Parameterized::class)
class HomeRecentPanelTest(private val width: Int, private val scale: Float, private val rtl: Boolean) {
    @get:Rule val rule = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "width={0},font={1},rtl={2}")
        fun cases() = listOf(320, 840).flatMap { width ->
            listOf(1f, 1.3f, 2f).flatMap { scale ->
                listOf(false, true).map { rtl -> arrayOf<Any>(width, scale, rtl) }
            }
        }
    }

    @Test fun cardsStayFixedAndTitlesEllipsize() {
        val tracks = homeTracks(8).map { it.copy(album = "A very long album title ".repeat(12)) }
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 900.dp))) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, scale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    SistrumTheme(ThemeMode.Dark) {
                        HomeFixture(recent = tracks)
                    }
                }
            }
        }
        val first = rule.onNodeWithTag("home-hero-t0")
        val node = first.fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        assertEquals(280f * density, node.size.width.toFloat(), 1f)
        assertEquals(300f * density, node.size.height.toFloat(), 1f)
        val a = node.boundsInRoot
        val b = rule.onNodeWithTag("home-hero-t1").fetchSemanticsNode().boundsInRoot
        if (rtl) assertTrue(b.left < a.left) else assertTrue(b.left > a.left)
        assertTrue("The next card must peek into the viewport", b.width > 0f && b.height > 0f)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("home-recent-title-t0", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0))
    }

    @Test fun dragSettlesAtACardStartInEitherDirection() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 900.dp))) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, scale),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    SistrumTheme(ThemeMode.Dark) {
                        HomeFixture(recent = homeTracks(20))
                    }
                }
            }
        }
        val firstNode = rule.onNodeWithTag("home-hero-t0").fetchSemanticsNode()
        val first = firstNode.boundsInRoot
        val panelDensity = firstNode.layoutInfo.density.density
        rule.onNodeWithTag("home-recent-carousel").performTouchInput {
            val start = center
            swipe(start, start.copy(x = start.x + if (rtl) 180f * panelDensity else -180f * panelDensity), 1000)
        }
        rule.waitForIdle()
        val cards = rule.onAllNodes(hasTestTag("home-hero-t0") or hasTestTag("home-hero-t1") or
            hasTestTag("home-hero-t2") or hasTestTag("home-hero-t3")).fetchSemanticsNodes()
        val anchor = if (rtl) first.right else first.left
        assertTrue("A card must settle at the initial start edge", cards.any {
            val edge = if (rtl) it.boundsInRoot.right else it.boundsInRoot.left
            kotlin.math.abs(edge - anchor) < 2f
        })
        // The slow drag crosses a card boundary, rather than simply returning to the first card.
        val moved = rule.onNodeWithTag("home-hero-t1").fetchSemanticsNode().boundsInRoot
        assertEquals(if (rtl) first.right else first.left,
            if (rtl) moved.right else moved.left, 1f)
    }
}
