package me.misa198.airmedy.ui.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import me.misa198.airmedy.AppDestination
import me.misa198.airmedy.player.PlaybackItem
import me.misa198.airmedy.player.PlaybackQueueSnapshot
import me.misa198.airmedy.player.PlaybackState
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.components.isRtlText
import me.misa198.airmedy.ui.screens.CrossfadeDurationSlider
import me.misa198.airmedy.ui.screens.CrossfadeDurationSliderTrackTag
import me.misa198.airmedy.ui.theme.AirmedyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Right-to-left behavior of the components that were made direction-aware. */
class RtlLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun miniPlayerTransportControlsStayLeftToRightInRtl() {
        composeTestRule.setContent { Rtl { MiniPlayerChrome() } }

        val previous = composeTestRule.onNodeWithContentDescription("Previous").fetchSemanticsNode().boundsInRoot
        val pause = composeTestRule.onNodeWithContentDescription("Pause").fetchSemanticsNode().boundsInRoot
        val next = composeTestRule.onNodeWithContentDescription("Next").fetchSemanticsNode().boundsInRoot
        assertTrue("previous left of pause", previous.center.x < pause.center.x)
        assertTrue("pause left of next", pause.center.x < next.center.x)
        // The rest of the pill mirrors: the title sits to the right of the controls.
        val title = composeTestRule.onNodeWithText(item.title).fetchSemanticsNode().boundsInRoot
        assertTrue("title on the start (right) side", title.left > next.right)
    }

    @Test
    fun swipingMetadataRightInRtlGoesToTheNextTrack() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent { Rtl { MiniPlayerChrome(onPrevious = { calls += "previous" }, onNext = { calls += "next" }) } }

        composeTestRule.onNodeWithText(item.title).performTouchInput { swipeRight() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { calls.isNotEmpty() }

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun swipingMetadataLeftInRtlReturnsToThePreviousTrack() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent { Rtl { MiniPlayerChrome(onPrevious = { calls += "previous" }, onNext = { calls += "next" }) } }

        composeTestRule.onNodeWithText(item.artist).performTouchInput { swipeLeft() }
        composeTestRule.waitUntil(timeoutMillis = 2_000) { calls.isNotEmpty() }

        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun crossfadeSliderRunsRightToLeftInRtl() {
        var selectedSeconds = 4
        composeTestRule.setContent {
            Rtl {
                AirmedyTheme(themeMode = ThemeMode.Dark) {
                    CrossfadeDurationSlider(
                        seconds = selectedSeconds,
                        enabled = true,
                        onSecondsChanged = { selectedSeconds = it },
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }

        // The maximum sits at the layout's end, which is the left edge in RTL.
        composeTestRule.onNodeWithTag(CrossfadeDurationSliderTrackTag)
            .performTouchInput { click(topLeft + Offset(2f, 24f)) }

        assertEquals(12, selectedSeconds)
    }

    @Test
    fun textDirectionFollowsTheFirstStrongCharacter() {
        assertTrue(isRtlText("قدام مرايتها", LayoutDirection.Ltr))
        assertFalse(isRtlText("Hero / ليلى", LayoutDirection.Rtl))
        assertFalse(isRtlText("/storage/emulated/0/Music", LayoutDirection.Rtl))
        // Without a strong character (digits only) the layout direction decides.
        assertTrue(isRtlText("2024", LayoutDirection.Rtl))
        assertFalse(isRtlText("2024", LayoutDirection.Ltr))
    }

    @Composable
    private fun Rtl(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }

    @Composable
    private fun MiniPlayerChrome(onPrevious: () -> Unit = {}, onNext: () -> Unit = {}) {
        AirmedyTheme(themeMode = ThemeMode.Dark) {
            NavigationChrome(
                selectedDestination = AppDestination.Home,
                playbackState = PlaybackState.Playing(item, positionMs = 0L, durationMs = 120_000L),
                playbackQueue = PlaybackQueueSnapshot(activeTrackIds = listOf("track-0", item.trackId, "track-2"), currentIndex = 1),
                hazeState = rememberHazeState(),
                onDestinationSelected = {},
                onPreviousClick = onPrevious,
                onPlayPauseClick = {},
                onNextClick = onNext,
                onMiniPlayerDismiss = {},
            )
        }
    }

    private companion object {
        val item = PlaybackItem(
            trackId = "track-1",
            title = "Title",
            artist = "Artist",
            audioPath = "/audio/track-1.flac",
        )
    }
}
