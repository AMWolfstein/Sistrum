package com.amwolfstein.sistrum.ui.components

import dev.chrisbanes.haze.HazePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassTest {
    @Test
    fun backdropKeepsFullInputResolution() {
        // Haze samples a Fixed mode at full resolution only for the highest quality fraction.
        assertEquals(HazePerformanceMode.Fixed(1f), LiquidGlassPerformanceMode)
    }

    @Test
    fun blurRequiresAvailableHazeWithoutEitherFallback() {
        assertEquals(GlassPolicy.Blur, GlassPolicy.resolve(false, true, false))
    }

    @Test
    fun reduceTransparencyAlwaysUsesOpaque() {
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(true, true, false))
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(true, true, true))
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(true, false, false))
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(true, false, true))
    }

    @Test
    fun unavailableHazeUsesOpaque() {
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(false, false, false))
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(false, false, true))
    }

    @Test
    fun performanceFallbackUsesOpaqueEvenWithAvailableHaze() {
        assertEquals(GlassPolicy.Opaque, GlassPolicy.resolve(false, true, true))
    }
}
