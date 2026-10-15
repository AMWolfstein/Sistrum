package com.amwolfstein.sistrum.player.media3

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every song re-measures the mixer block; only a new route or a real change is stored and logged. */
class MixerBlockChangeTest {
    @Test fun firstMeasurementOfARouteIsAChange() = assertTrue(mixerBlockChanged(null, 40f))

    @Test fun sameValueAgainIsNotAChange() {
        assertFalse(mixerBlockChanged(40f, 40f))
        assertFalse(mixerBlockChanged(40f, 40.4f))
        assertFalse(mixerBlockChanged(40f, 39.6f))
    }

    @Test fun movingByHalfAMillisecondOrMoreIsAChange() {
        assertTrue(mixerBlockChanged(40f, 40.5f))
        assertTrue(mixerBlockChanged(40f, 20f))
        assertTrue(mixerBlockChanged(4f, 40f))
    }
}
