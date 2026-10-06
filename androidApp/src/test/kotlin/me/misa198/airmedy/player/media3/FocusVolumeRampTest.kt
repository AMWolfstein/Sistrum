package me.misa198.airmedy.player.media3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusVolumeRampTest {

    @Test
    fun `down ramp reaches the target after 96 ms and clamps`() {
        val ramp = FocusVolumeRamp(initial = 1f)
        ramp.setTarget(0.2f, nowMs = 0L)

        assertEquals(0.6f, ramp.valueAt(48L), 0.001f)
        assertEquals(0.2f, ramp.valueAt(96L), 0f)
        assertEquals(0.2f, ramp.valueAt(500L), 0f)
        assertTrue(ramp.isSettled(96L))
        assertFalse(ramp.isSettled(95L))
    }

    @Test
    fun `up ramp reaches the target after 192 ms and clamps`() {
        val ramp = FocusVolumeRamp(initial = 0.2f)
        ramp.setTarget(1f, nowMs = 0L)

        assertEquals(0.7f, ramp.valueAt(120L), 0.001f)
        assertEquals(1f, ramp.valueAt(192L), 0f)
        assertEquals(1f, ramp.valueAt(900L), 0f)
    }

    @Test
    fun `down ramp is monotonically non-increasing`() {
        val ramp = FocusVolumeRamp(initial = 1f)
        ramp.setTarget(0.2f, nowMs = 0L)

        var previous = ramp.valueAt(0L)
        for (i in 1..100) {
            val value = ramp.valueAt(i.toLong())
            assertTrue("value rose at sample $i: $previous -> $value", value <= previous)
            previous = value
        }
    }

    @Test
    fun `up ramp is monotonically non-decreasing`() {
        val ramp = FocusVolumeRamp(initial = 0.2f)
        ramp.setTarget(1f, nowMs = 0L)

        var previous = ramp.valueAt(0L)
        for (i in 1..100) {
            val value = ramp.valueAt(i.toLong())
            assertTrue("value fell at sample $i: $previous -> $value", value >= previous)
            previous = value
        }
    }

    @Test
    fun `retarget mid-ramp continues from the current value`() {
        val ramp = FocusVolumeRamp(initial = 1f)
        ramp.setTarget(0.2f, nowMs = 0L)
        val midValue = ramp.valueAt(48L)
        assertEquals(0.6f, midValue, 0.001f)

        ramp.setTarget(1f, nowMs = 48L)

        assertEquals(midValue, ramp.valueAt(48L), 0f)
        assertEquals(1f, ramp.valueAt(48L + 96L), 0f)
        assertTrue(ramp.isSettled(48L + 96L))
    }

    @Test
    fun `target equal to the current value settles immediately`() {
        val ramp = FocusVolumeRamp(initial = 0.5f)
        ramp.setTarget(0.5f, nowMs = 1_000L)

        assertTrue(ramp.isSettled(1_000L))
        assertEquals(0.5f, ramp.valueAt(1_000L), 0f)
    }

    @Test
    fun `targets are clamped to zero and one`() {
        val ducked = FocusVolumeRamp(initial = 1f)
        ducked.setTarget(-0.5f, nowMs = 0L)
        assertEquals(0f, ducked.valueAt(120L), 0f)

        val restored = FocusVolumeRamp(initial = 0f)
        restored.setTarget(2f, nowMs = 0L)
        assertEquals(1f, restored.valueAt(240L), 0f)
    }
}
