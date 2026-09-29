package me.misa198.airmedy.ui.components

import dev.chrisbanes.haze.HazePerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassTest {
    @Test
    fun backdropKeepsFullInputResolution() {
        // Haze samples a Fixed mode at full resolution only for the highest quality fraction.
        assertEquals(HazePerformanceMode.Fixed(1f), LiquidGlassPerformanceMode)
    }
}
