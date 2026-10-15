package com.amwolfstein.sistrum.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SistrumTokensTest(private val theme: String, private val palette: SistrumColors) {
    private val tokens = sistrumTokens(palette)

    @Test
    fun semanticColorsPreserveTheExistingPalette() {
        val colors = tokens.colors
        assertEquals(palette.background, colors.page)
        assertEquals(palette.glass, colors.glassTint)
        assertEquals(palette.glassOpaque, colors.opaqueFill)
        assertEquals(palette.playerBackdrop, colors.scrim)
        assertEquals(palette.textMain, colors.text)
        assertEquals(palette.textMuted, colors.mutedText)
        assertEquals(palette.primary, colors.accent)
        assertEquals(palette.borderGlass, colors.divider)
        assertEquals(palette.textMuted.copy(alpha = 0.38f), colors.disabled)
        assertEquals(palette.primary, colors.focusRing)
        assertEquals(1f, colors.page.alpha)
        assertEquals(1f, colors.opaqueFill.alpha)
    }

    @Test
    fun normalTextPairsMeetWcagAA() {
        val colors = tokens.colors
        val backgrounds = listOf(
            colors.page,
            colors.opaqueFill,
            colors.glassTint.compositeOver(colors.opaqueFill),
        )
        for (background in backgrounds) {
            for (text in listOf(colors.text, colors.mutedText)) {
                assertContrastAtLeast(text, background, 4.5)
            }
        }
    }

    @Test
    fun largeAccentTextPairsMeetWcagAA() {
        val colors = tokens.colors
        for (background in listOf(
            colors.page,
            colors.opaqueFill,
            colors.glassTint.compositeOver(colors.opaqueFill),
        )) {
            assertContrastAtLeast(colors.accent, background, 3.0)
        }
    }

    @Test
    fun glassCompositingPreservesTheOpaqueFallbackColor() {
        // Both shipped tints share their fallback's RGB, so compositing must preserve it.
        assertEquals(
            tokens.colors.opaqueFill,
            tokens.colors.glassTint.compositeOver(tokens.colors.opaqueFill),
        )
    }

    @Test
    fun spacingMatchesTheSpecifiedDpScale() {
        assertEquals(
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp),
            tokens.spacing.run { listOf(extraSmall, small, medium, large, extraLarge, extraExtraLarge) },
        )
    }

    @Test
    fun cornersMatchTheSpecifiedDpScale() {
        assertEquals(RoundedCornerShape(12.dp), tokens.shapes.small)
        assertEquals(RoundedCornerShape(16.dp), tokens.shapes.medium)
        assertEquals(RoundedCornerShape(24.dp), tokens.shapes.large)
        assertEquals(RoundedCornerShape(28.dp), tokens.shapes.extraLarge)
    }

    @Test
    fun contrastOracleMatchesWcagReferenceRatios() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.00001)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.00001)
        // WCAG sRGB examples: #767676 passes normal text; #777777 falls just below 4.5.
        assertEquals(4.5422, contrast(Color(0xFF767676), Color.White), 0.0001)
        assertEquals(4.4781, contrast(Color(0xFF777777), Color.White), 0.0001)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground.compositeOver(background), background)
        assertTrue("$theme: $foreground on $background has contrast $ratio < $minimum", ratio >= minimum)
    }

    // WCAG relative luminance: linearize sRGB before applying the luminance coefficients.
    private fun contrast(foreground: Color, background: Color): Double {
        fun luminance(color: Color): Double {
            fun linear(channel: Float): Double = channel.toDouble().let {
                if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }
        val foregroundLuminance = luminance(foreground)
        val backgroundLuminance = luminance(background)
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun themes(): Collection<Array<Any>> = listOf(
            arrayOf("Light", LightColors),
            arrayOf("Dark", DarkColors),
        )
    }
}
