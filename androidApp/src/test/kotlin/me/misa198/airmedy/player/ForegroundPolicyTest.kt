package me.misa198.airmedy.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPolicyTest {

    private fun item(id: String) = PlaybackItem(
        trackId = id,
        title = "Title $id",
        artist = "Artist",
        audioPath = "/music/$id.flac",
    )

    // ------------------------------------------------------------------ FR-080
    // Every action that can begin audio must be started in the foreground; the rest must not.

    @Test
    fun `actions that can begin audio require a foreground start`() {
        assertTrue(requiresForegroundStart(PlaybackService.ActionPlay))
        assertTrue(requiresForegroundStart(PlaybackService.ActionShuffle))
        assertTrue(requiresForegroundStart(PlaybackService.ActionResume))
        assertTrue(requiresForegroundStart(PlaybackService.ActionNext))
        assertTrue(requiresForegroundStart(PlaybackService.ActionPrevious))
        assertTrue(requiresForegroundStart(PlaybackService.ActionSelect))
        assertTrue(requiresForegroundStart(PlaybackService.ActionStartMoodRadio))
    }

    @Test
    fun `commands that do not begin audio do not require a foreground start`() {
        assertFalse(requiresForegroundStart(PlaybackService.ActionPause))
        assertFalse(requiresForegroundStart(PlaybackService.ActionPauseForTransientFocusLoss))
        assertFalse(requiresForegroundStart(PlaybackService.ActionDuck))
        assertFalse(requiresForegroundStart(PlaybackService.ActionRestoreFocus))
        assertFalse(requiresForegroundStart(PlaybackService.ActionStop))
        assertFalse(requiresForegroundStart(PlaybackService.ActionClearQueue))
        assertFalse(requiresForegroundStart(PlaybackService.ActionSeek))
        assertFalse(requiresForegroundStart(PlaybackService.ActionSetShuffle))
        assertFalse(requiresForegroundStart(PlaybackService.ActionSetRepeat))
        assertFalse(requiresForegroundStart(PlaybackService.ActionPlayNext))
        assertFalse(requiresForegroundStart(PlaybackService.ActionAppend))
        assertFalse(requiresForegroundStart(PlaybackService.ActionRemove))
        assertFalse(requiresForegroundStart(PlaybackService.ActionReorder))
        assertFalse(requiresForegroundStart(PlaybackService.ActionSetCrossfade))
    }

    @Test
    fun `null and unknown actions do not require a foreground start`() {
        assertFalse(requiresForegroundStart(null))
        assertFalse(requiresForegroundStart("unknown"))
    }

    @Test
    fun `the service stops only when the state has settled with nothing playing`() {
        assertTrue(serviceShouldStopWhenSettled(PlaybackState.Idle))
        assertTrue(serviceShouldStopWhenSettled(PlaybackState.Failed(trackId = "a", reason = "boom")))
        assertFalse(serviceShouldStopWhenSettled(PlaybackState.Preparing(item("a"))))
        assertFalse(serviceShouldStopWhenSettled(PlaybackState.Playing(item("a"), positionMs = 1_000L, durationMs = 180_000L)))
        assertFalse(serviceShouldStopWhenSettled(PlaybackState.Paused(item("a"), positionMs = 1_000L, durationMs = 180_000L)))
    }
}
