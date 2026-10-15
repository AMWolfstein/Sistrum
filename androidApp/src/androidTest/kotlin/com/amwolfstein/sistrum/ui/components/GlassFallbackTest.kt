package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GlassFallbackTest(private val case: UiMatrix.Case) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = UiMatrix.cases.map { arrayOf<Any>(it) }
    }

    @get:Rule val rule = createComposeRule()
    private val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("glass-fallback"))

    @Test fun reduceTransparencyRemovesBlurAndKeepsTextReadable() {
        val reduce = mutableStateOf(case.reduceTransparency)
        var text = Color.Unspecified
        var mutedText = Color.Unspecified
        var fill = Color.Unspecified
        matrix.setContent {
            val colors = LocalSistrumTokens.current.colors
            text = colors.text
            mutedText = colors.mutedText
            fill = colors.opaqueFill
            // Keep Haze available even when reduced: policy must remove the real blur element.
            val haze = rememberHazeState()
            CompositionLocalProvider(LocalReduceTransparency provides reduce.value) {
                GlassSurface(haze, Modifier.size(240.dp).testTag("surface")) {
                    Text(if (case.language == "ar") "تشغيل" else "Play",
                        color = colors.text, modifier = Modifier.padding(24.dp))
                }
            }
        }
        assertBlurPresent(!case.reduceTransparency)
        rule.runOnIdle { reduce.value = true }
        assertBlurPresent(false)
        val pixels = rule.onNodeWithTag("surface").captureToImage().toPixelMap()
        val renderedFill = pixels[4, pixels.height / 2]
        assertEquals(fill, renderedFill)
        matrix.assertContrast(text, renderedFill)
        matrix.assertContrast(mutedText, renderedFill)
        matrix.captureToImage()
        // Switching the setting back restores blur on the same surface.
        rule.runOnIdle { reduce.value = false }
        assertBlurPresent(true)
    }

    @Test fun performanceFallbackRemovesBlurAndCanRecover() {
        val fallback = mutableStateOf(false)
        matrix.setContent {
            CompositionLocalProvider(LocalGlassPerformanceFallback provides fallback.value) {
                GlassSurface(rememberHazeState(), Modifier.size(120.dp).testTag("surface"))
            }
        }
        assertBlurPresent(true)
        rule.runOnIdle { fallback.value = true }
        assertBlurPresent(false)
        rule.runOnIdle { fallback.value = false }
        assertBlurPresent(true)
    }

    @Test fun missingHazeUsesOpaqueForModifierConsumersToo() {
        var fill = Color.Unspecified
        matrix.setContent {
            fill = LocalSistrumTokens.current.colors.opaqueFill
            Box(Modifier.size(120.dp).testTag("surface").liquidGlassBackground(null))
        }
        assertBlurPresent(false)
        val pixels = rule.onNodeWithTag("surface").captureToImage().toPixelMap()
        assertEquals(fill, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test fun libraryActionLabelsHaveNormalTextContrastOnOpaqueGlass() {
        val play = if (case.language == "ar") "تشغيل" else "Play"
        val shuffle = if (case.language == "ar") "عشوائي" else "Shuffle"
        matrix.setContent {
            CompositionLocalProvider(LocalReduceTransparency provides true) {
                LibraryPlaybackActions(play, shuffle, {}, {}, hazeState = rememberHazeState())
            }
        }
        for (label in listOf(play, shuffle)) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val pixels = rule.onNodeWithContentDescription(label).captureToImage().toPixelMap()
            matrix.assertContrast(layouts.single().layoutInput.style.color, pixels[4, pixels.height / 2])
        }
    }

    private fun assertBlurPresent(expected: Boolean) {
        rule.waitForIdle()
        // Inspect attached modifiers, not a policy semantics flag. The positive assertions also
        // ensure a Haze upgrade/rename cannot make the absence checks pass vacuously.
        val modifiers = rule.onNodeWithTag("surface").fetchSemanticsNode().layoutInfo.getModifierInfo()
        val hazeElements = modifiers.filter {
            it.modifier.javaClass.name == "dev.chrisbanes.haze.TypedHazeEffectNodeElement"
        }
        assertTrue("${case.id}: expected blur=$expected; modifiers=${modifiers.map { it.modifier.javaClass.name }}",
            hazeElements.isNotEmpty() == expected)
    }
}
