package com.zarz.mornye

import org.junit.Assert.*
import org.junit.Test

class PreferredDisplayControllerTest {
    private val current = PreferredMode(1, 1080, 2400, 120f)
    @Test fun selects60AtCurrentResolution() {
        val target = PreferredMode(3, 1080, 2400, 60f)
        assertEquals(target, preferred60Mode(listOf(current, PreferredMode(2, 720, 1600, 60f), target), current))
    }
    @Test fun unsupportedDoesNotSelectDifferentResolutionOr90Hz() {
        assertNull(preferred60Mode(listOf(current, PreferredMode(2, 720, 1600, 60f), PreferredMode(3, 1080, 2400, 90f)), current))
    }
    @Test fun prefersClosestRateAndSupportsFractional60() {
        val fractional = PreferredMode(2, 1080, 2400, 59.94f)
        assertEquals(fractional, preferred60Mode(listOf(fractional), current))
        val exact = PreferredMode(3, 1080, 2400, 60f)
        assertEquals(exact, preferred60Mode(listOf(fractional, exact), current))
    }
}
