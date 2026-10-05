package me.misa198.airmedy.player

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization (FR-073): pins the current engine's policy results before the engine
 * seam. Do not change expected values without an owner-approved spec reason.
 */
class PlaybackPolicyCharacterizationTest {

    @Test
    fun `crossfade starts at the remaining-equals-upper-fade edge`() {
        assertTrue(shouldStartCrossfade(5, positionMs = 55_000, durationMs = 60_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade does not start one millisecond past the upper-fade edge`() {
        assertFalse(shouldStartCrossfade(5, positionMs = 54_999, durationMs = 60_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade starts at the remaining-401 lower edge`() {
        assertTrue(shouldStartCrossfade(5, positionMs = 59_599, durationMs = 60_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade does not start at the remaining-400 exclusion edge`() {
        assertFalse(shouldStartCrossfade(5, positionMs = 59_600, durationMs = 60_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade does not start without a preloaded next track`() {
        assertFalse(shouldStartCrossfade(5, positionMs = 57_000, durationMs = 60_000, hasPreloadedNext = false))
    }

    @Test
    fun `crossfade does not start for a non-positive configured duration`() {
        assertFalse(shouldStartCrossfade(0, positionMs = 57_000, durationMs = 60_000, hasPreloadedNext = true))
        assertFalse(shouldStartCrossfade(-1, positionMs = 57_000, durationMs = 60_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade does not start for a track shorter than two seconds`() {
        assertFalse(shouldStartCrossfade(5, positionMs = 500, durationMs = 1_999, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade starts on a two-second track with the half-track clamp`() {
        assertTrue(shouldStartCrossfade(5, positionMs = 1_000, durationMs = 2_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade half-track clamp excludes the millisecond before the window`() {
        assertFalse(shouldStartCrossfade(12, positionMs = 3_999, durationMs = 8_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade half-track clamp includes the window lower edge`() {
        assertTrue(shouldStartCrossfade(12, positionMs = 4_000, durationMs = 8_000, hasPreloadedNext = true))
    }

    @Test
    fun `crossfade duration is capped by the configured fade`() {
        assertEquals(5_000L, crossfadeDurationMs(5, positionMs = 0, durationMs = 60_000))
    }

    @Test
    fun `crossfade duration is capped by half the track`() {
        assertEquals(4_000L, crossfadeDurationMs(12, positionMs = 0, durationMs = 8_000))
    }

    @Test
    fun `crossfade duration is capped by the audible remainder`() {
        assertEquals(2_000L, crossfadeDurationMs(5, positionMs = 58_000, durationMs = 60_000))
    }

    @Test
    fun `crossfade duration floors a negative configured fade to zero`() {
        assertEquals(0L, crossfadeDurationMs(-3, positionMs = 0, durationMs = 60_000))
    }

    @Test
    fun `crossfade duration floors a negative track duration to zero`() {
        assertEquals(0L, crossfadeDurationMs(5, positionMs = 0, durationMs = -10))
    }

    @Test
    fun `crossfade duration floors a past-the-end position to zero`() {
        assertEquals(0L, crossfadeDurationMs(5, positionMs = 70_000, durationMs = 60_000))
    }

    @Test
    fun `next preload is blocked while a crossfade is running`() {
        assertFalse(canPreloadNext(isCrossfading = true))
    }

    @Test
    fun `next preload is allowed when no crossfade is running`() {
        assertTrue(canPreloadNext(isCrossfading = false))
    }

    @Test
    fun `resume at the exact track end without a decoder restarts the queue`() {
        assertTrue(shouldRestartQueueOnResume(pausedPositionMs = 60_000, durationMs = 60_000, hasDecoder = false))
    }

    @Test
    fun `resume past the track end without a decoder restarts the queue`() {
        assertTrue(shouldRestartQueueOnResume(pausedPositionMs = 61_000, durationMs = 60_000, hasDecoder = false))
    }

    @Test
    fun `resume before the track end without a decoder does not restart the queue`() {
        assertFalse(shouldRestartQueueOnResume(pausedPositionMs = 59_999, durationMs = 60_000, hasDecoder = false))
    }

    @Test
    fun `resume at the track end with a decoder does not restart the queue`() {
        assertFalse(shouldRestartQueueOnResume(pausedPositionMs = 60_000, durationMs = 60_000, hasDecoder = true))
    }

    @Test
    fun `resume on a zero-length track without a decoder does not restart the queue`() {
        assertFalse(shouldRestartQueueOnResume(pausedPositionMs = 0, durationMs = 0, hasDecoder = false))
    }

    @Test
    fun `completed playback retains the final position`() {
        assertEquals(60_000L, stoppedCurrentPosition(PlaybackEndReason.COMPLETED, 60_000))
    }

    @Test
    fun `skipped playback rewinds to the start`() {
        assertEquals(0L, stoppedCurrentPosition(PlaybackEndReason.SKIPPED, 60_000))
    }

    @Test
    fun `stopped playback rewinds to the start`() {
        assertEquals(0L, stoppedCurrentPosition(PlaybackEndReason.STOPPED, 60_000))
    }

    @Test
    fun `play and shuffle actions replace the restored queue`() {
        assertTrue(playbackActionReplacesRestoredQueue(PlaybackService.ActionPlay))
        assertTrue(playbackActionReplacesRestoredQueue(PlaybackService.ActionShuffle))
    }

    @Test
    fun `non-queue-starting actions do not replace the restored queue`() {
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionPause))
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionResume))
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionStop))
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionClearQueue))
        assertFalse(playbackActionReplacesRestoredQueue(PlaybackService.ActionNext))
        assertFalse(playbackActionReplacesRestoredQueue(null))
        assertFalse(playbackActionReplacesRestoredQueue(""))
    }

    @Test
    fun `disconnected audio output requires recovery`() {
        assertTrue(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = true))
        assertFalse(audioOutputDisconnectRequiresRecovery(isOutputDisconnected = false))
    }

    @Test
    fun `audio becoming noisy action requires pause`() {
        assertTrue(audioBecomingNoisyRequiresPause(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    @Test
    fun `non-noisy actions do not require pause`() {
        assertFalse(audioBecomingNoisyRequiresPause(null))
        assertFalse(audioBecomingNoisyRequiresPause(""))
        assertFalse(audioBecomingNoisyRequiresPause("android.intent.action.HEADSET_PLUG"))
    }

    @Test
    fun `permanent audio focus loss pauses playback`() {
        assertEquals(AudioFocusChangeAction.Pause, audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS))
    }

    @Test
    fun `transient audio focus loss pauses and resumes on gain`() {
        assertEquals(
            AudioFocusChangeAction.PauseAndResumeOnGain,
            audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT),
        )
    }

    @Test
    fun `transient duckable audio focus loss ducks playback`() {
        assertEquals(
            AudioFocusChangeAction.Duck,
            audioFocusChangeAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK),
        )
    }

    @Test
    fun `audio focus gain restores playback`() {
        assertEquals(AudioFocusChangeAction.Restore, audioFocusChangeAction(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun `unmapped audio focus changes are ignored`() {
        assertEquals(AudioFocusChangeAction.Ignore, audioFocusChangeAction(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT))
        assertEquals(AudioFocusChangeAction.Ignore, audioFocusChangeAction(AudioManager.AUDIOFOCUS_NONE))
        assertEquals(AudioFocusChangeAction.Ignore, audioFocusChangeAction(12345))
    }
}
