package com.amwolfstein.sistrum.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MornyeTintTest {
    @Test fun missingAndTransparentArtworkUseTheSpecifiedNeutralTint() {
        assertEquals(Color(0xFF242428), mornyePageTint(null))
        assertEquals(Color(0xFF242428), mornyePageTint(Color.Transparent))
    }

    @Test fun darkeningRetainsDominantHue() {
        assertEquals(Color(0.32f, 0f, 0f), mornyePageTint(Color.Red))
        assertEquals(Color(0f, 0.32f, 0f), mornyePageTint(Color.Green))
        assertEquals(Color(0f, 0f, 0.32f), mornyePageTint(Color.Blue))
    }

    @Test fun whiteTextMeetsContrastAcrossTheEntireRgbCube() {
        for (red in 0..10) for (green in 0..10) for (blue in 0..10) {
            val page = mornyePageTint(Color(red / 10f, green / 10f, blue / 10f))
            // WCAG 2 contrast of opaque white against the page's relative luminance.
            assertTrue(1.05 / (page.luminance() + 0.05) >= 4.5)
            assertEquals(1f, page.alpha)
        }
    }
}
