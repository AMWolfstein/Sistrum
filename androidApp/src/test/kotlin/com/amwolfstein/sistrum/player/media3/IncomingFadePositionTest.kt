package com.amwolfstein.sistrum.player.media3

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingFadePositionTest {
    @Test fun skippedLeadMustLeaveTheEntireFadeBeforeIncomingEof() {
        assertEquals(0L, incomingFadePositionMs(9_000L, 10_000L, 4_000L))
        assertEquals(0L, incomingFadePositionMs(10_000L, 10_000L, 4_000L))
        assertEquals(0L, incomingFadePositionMs(12_000L, 10_000L, 4_000L))
        assertEquals(0L, incomingFadePositionMs(6_001L, 10_000L, 4_000L))
        assertEquals(6_000L, incomingFadePositionMs(6_000L, 10_000L, 4_000L))
        assertEquals(2_000L, incomingFadePositionMs(2_000L, 10_000L, 4_000L))
    }

    @Test fun noSeekAndUnknownOrEmptyDurationUseTheOriginalTimeline() {
        assertEquals(0L, incomingFadePositionMs(0L, 10_000L, 4_000L))
        assertEquals(0L, incomingFadePositionMs(2_000L, C.TIME_UNSET, 4_000L))
        assertEquals(0L, incomingFadePositionMs(2_000L, 0L, 4_000L))
    }
}
