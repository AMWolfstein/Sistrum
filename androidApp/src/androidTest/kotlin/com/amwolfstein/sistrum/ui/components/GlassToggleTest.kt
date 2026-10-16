package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.amwolfstein.sistrum.ui.theme.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlassToggleTest {
    @get:Rule val rule = createComposeRule()

    private val checked = mutableStateOf(false)
    private var calls = 0
    private val reduce = mutableStateOf(false)

    private fun content(
        enabled: Boolean = true,
        rtl: Boolean = false,
        reduceTransparency: Boolean = false,
        reducedMotion: Boolean = false,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        reduce.value = reduceTransparency
        rule.setContent {
            val density = LocalDensity.current
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color.Blue) else lightColorScheme(primary = Color.Blue)) {
                CompositionLocalProvider(
                    LocalReduceTransparency provides reduce.value,
                    LocalReducedMotion provides reducedMotion,
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    Box(Modifier.background(Color.Black)) {
                        GlassToggle(checked.value, { calls++; checked.value = it }, Modifier.testTag("toggle"), enabled)
                    }
                }
            }
        }
    }

    @Test fun tapTogglesAndAnnouncesSwitchState() {
        content()
        rule.onNodeWithTag("toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
            .performTouchInput { click() }
        rule.onNodeWithTag("toggle").assertIsOn()
        rule.runOnIdle { assertEquals(1, calls) }
        rule.onNodeWithTag("toggle").performClick().assertIsOff()
        rule.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun dragTogglesInBothDirections() {
        content()
        rule.onNodeWithTag("toggle").performTouchInput { swipeRight() }.assertIsOn()
        rule.onNodeWithTag("toggle").performTouchInput { swipeLeft() }.assertIsOff()
        rule.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun rtlDragUsesMirroredDirection() {
        content(rtl = true)
        rule.onNodeWithTag("toggle").performTouchInput { swipeLeft() }.assertIsOn()
        rule.onNodeWithTag("toggle").performTouchInput { swipeRight() }.assertIsOff()
        rule.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun disabledIgnoresTapAndDrag() {
        content(enabled = false)
        rule.onNodeWithTag("toggle").assertIsNotEnabled().performTouchInput { click(); swipeRight() }.assertIsOff()
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun cancelledDragDoesNotToggle() {
        content()
        rule.onNodeWithTag("toggle").performTouchInput {
            down(centerLeft)
            moveTo(centerRight)
            cancel()
        }.assertIsOff()
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun reduceTransparencyRendersStaticWhiteThumbAndPrimaryTrack() {
        content(reduceTransparency = true)
        assertThumb(onRight = false)
        rule.onNodeWithTag("toggle").performClick().assertIsOn()
        assertThumb(onRight = true)
        val pixels = rule.onNodeWithTag("toggle").captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[(pixels.width * 0.1f).toInt(), pixels.height / 2])
        assertNoBackdrop()
    }

    @Test fun rtlThumbSideIsMirrored() {
        content(rtl = true, reduceTransparency = true)
        assertThumb(onRight = true)
        rule.onNodeWithTag("toggle").performClick().assertIsOn()
        assertThumb(onRight = false)
    }

    @Test fun reducedMotionUsesStaticFallbackInDarkTheme() {
        content(reducedMotion = true, dark = true)
        assertThumb(onRight = false)
        rule.onNodeWithTag("toggle").performClick()
        assertThumb(onRight = true)
        assertNoBackdrop()
    }

    @Test fun fontScaleTwoKeepsFullSizeThumbAndTouchTarget() {
        content(reduceTransparency = true, fontScale = 2f)
        rule.onNodeWithTag("toggle").assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(64f))
            .assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
        assertThumb(onRight = false)
        rule.onNodeWithTag("toggle").performClick()
        assertThumb(onRight = true)
    }

    @Test fun externalStateChangesMoveThumbWithoutCallback() {
        content(reduceTransparency = true)
        rule.runOnIdle { checked.value = true }
        rule.onNodeWithTag("toggle").assertIsOn()
        assertThumb(onRight = true)
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun transparencyChangeRemovesLensAndRestoresOnlyOnSupportedApis() {
        content()
        assertBackdrop(android.os.Build.VERSION.SDK_INT >= 33)
        rule.runOnIdle { reduce.value = true }
        assertNoBackdrop()
        assertThumb(onRight = false)
        rule.runOnIdle { reduce.value = false }
        assertBackdrop(android.os.Build.VERSION.SDK_INT >= 33)
    }

    @Test fun disabledThumbHasSpecifiedAlpha() {
        content(enabled = false, reduceTransparency = true)
        val pixels = rule.onNodeWithTag("toggle").captureToImage().toPixelMap()
        val thumb = pixels[(pixels.width * 0.15f).toInt(), pixels.height / 2]
        assertEquals(0.38f, thumb.red, 0.01f)
        assertEquals(0.38f, thumb.green, 0.01f)
        assertEquals(0.38f, thumb.blue, 0.01f)
    }

    private fun assertThumb(onRight: Boolean) {
        val pixels = rule.onNodeWithTag("toggle").captureToImage().toPixelMap()
        // The 40 x 24 dp thumb occupies x=2..42 or x=22..62 on the 64 dp track.
        val whiteSide = if (onRight) 0.85f else 0.15f
        val trackSide = if (onRight) 0.15f else 0.85f
        assertEquals(Color.White, pixels[(pixels.width * whiteSide).toInt(), pixels.height / 2])
        assertTrue(Color.White != pixels[(pixels.width * trackSide).toInt(), pixels.height / 2])
        // Full thumb height remains visible inside the 48 dp input target.
        assertEquals(Color.White, pixels[pixels.width / 2, (pixels.height * 0.3f).toInt()])
        assertEquals(Color.White, pixels[pixels.width / 2, (pixels.height * 0.7f).toInt()])
    }

    private fun assertNoBackdrop() = assertBackdrop(false)

    private fun assertBackdrop(expected: Boolean) {
        val modifiers = rule.onNodeWithTag("glass-toggle-thumb", useUnmergedTree = true)
            .fetchSemanticsNode().layoutInfo.getModifierInfo()
        assertEquals(modifiers.map { it.modifier.javaClass.name }.toString(), expected,
            modifiers.any { it.modifier.javaClass.name.startsWith("com.kyant.backdrop") })
    }
}
