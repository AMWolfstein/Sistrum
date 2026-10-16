package com.amwolfstein.sistrum.ui.components

import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.amwolfstein.sistrum.ui.theme.LocalReducedMotion
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MornyeDetailScaffoldTest(private val case: UiMatrix.Case) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = UiMatrix.cases.map { arrayOf<Any>(it) }
    }
    @get:Rule val rule = createComposeRule()
    private val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("mornye-detail"))
    private var play = 0
    private var shuffle = 0
    private var more = 0
    private var back = 0
    private var song = 0
    private var page = Color.Unspecified

    private fun show(playShape: MornyePlayShape = MornyePlayShape.Pill) {
        matrix.setContent { reduce ->
            CompositionLocalProvider(LocalReduceTransparency provides reduce) {
                MornyeDetailScaffold("A long album title for the detail page", { back++ },
                    header = {
                        page = LocalSistrumTokens.current.colors.page
                        MornyeActionRow({ shuffle++ }, { play++ }, { it() }, playShape = playShape, onMore = { more++ })
                    }) {
                    items(40) { index ->
                        MornyeSongRow(LibraryTrack(id = "$index", title = "Song $index", artists = "Artist"),
                            { song++ }, { more++ }, number = index + 1)
                    }
                    item { MornyeDetailFooter(40, 120) }
                }
            }
        }
    }
    private fun label(english: String, arabic: String) = if (case.language == "ar") arabic else english

    @Test fun headerCollapsesToCenteredTitleAndBackStillWorks() {
        show()
        rule.onNodeWithTag("mornye-collapsed-title").assertDoesNotExist()
        rule.onNodeWithTag("mornye-list").performScrollToIndex(8)
        rule.onNodeWithTag("mornye-collapsed-title").assertIsDisplayed()
        val title = rule.onNodeWithTag("mornye-collapsed-title").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, title.center.x, 1f)
        rule.onNodeWithContentDescription(label("Go back", "رجوع")).performClick()
        rule.runOnIdle { assertEquals(1, back) }
    }

    @Test fun actionsAnnounceTheirPurposeAndInvokeCallbacks() {
        show()
        rule.onNodeWithContentDescription(label("Shuffle", "ترتيب عشوائي")).performScrollTo().performClick()
        rule.onNodeWithContentDescription(label("Play", "تشغيل")).performScrollTo().performClick()
        rule.onNodeWithContentDescription(label("More options", "مزيد من الخيارات")).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, shuffle); assertEquals(1, play); assertEquals(1, more) }
    }

    @Test fun actionsMirrorInRtlAndFitAtLargeFonts() {
        show()
        rule.onNodeWithContentDescription(label("Play", "تشغيل")).performScrollTo()
        val titleLayouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("mornye-header-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertEquals(1, titleLayouts.size)
        assertTrue(titleLayouts.none { it.didOverflowHeight || it.isLineEllipsized(it.lineCount - 1) })
        val shuffleBounds = rule.onNodeWithContentDescription(label("Shuffle", "ترتيب عشوائي")).fetchSemanticsNode().boundsInRoot
        val moreBounds = rule.onNodeWithContentDescription(label("More options", "مزيد من الخيارات")).fetchSemanticsNode().boundsInRoot
        assertEquals(case.language == "ar", shuffleBounds.center.x > moreBounds.center.x)
        matrix.assertNoClippedActions(hasContentDescription(label("Shuffle", "ترتيب عشوائي")) or
            hasContentDescription(label("Play", "تشغيل")) or hasContentDescription(label("More options", "مزيد من الخيارات")))
    }

    @Test fun glassPolicyPreservesGeometryAndCanRecover() {
        val reduce = mutableStateOf(case.reduceTransparency)
        val motion = mutableStateOf(false)
        val performance = mutableStateOf(false)
        matrix.setContent {
            CompositionLocalProvider(LocalReduceTransparency provides reduce.value,
                LocalReducedMotion provides motion.value, LocalGlassPerformanceFallback provides performance.value) {
                MornyeDetailScaffold("Album", {}, header = {
                    MornyeGlassSurface(Modifier.size(120.dp).testTag("mornye-glass")) {}
                }) {}
            }
        }
        fun assertBlur(expected: Boolean) {
            val modifiers = rule.onNodeWithTag("mornye-glass").fetchSemanticsNode().layoutInfo.getModifierInfo()
            assertEquals(expected, modifiers.any { it.modifier.javaClass.name == "dev.chrisbanes.haze.TypedHazeEffectNodeElement" })
        }
        val bounds = rule.onNodeWithTag("mornye-glass").fetchSemanticsNode().boundsInRoot
        assertBlur(Build.VERSION.SDK_INT >= 33 && !case.reduceTransparency)
        rule.runOnIdle { reduce.value = true }
        assertBlur(false)
        rule.runOnIdle { reduce.value = false; motion.value = true }
        assertBlur(false)
        rule.runOnIdle { motion.value = false; performance.value = true }
        assertBlur(false)
        rule.runOnIdle { performance.value = false }
        assertBlur(Build.VERSION.SDK_INT >= 33)
        assertEquals(bounds, rule.onNodeWithTag("mornye-glass").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun footerUsesLocalizedSongAndMinutePlurals() {
        show()
        rule.onNodeWithTag("mornye-list").performScrollToIndex(41)
        rule.onNodeWithText(label("40 songs · 120 min", "40 أغنية · 120 دقيقة")).assertIsDisplayed()
    }

    @Test fun missingArtworkKeepsReadableTintInBothThemes() {
        show()
        rule.onNodeWithTag("mornye-artwork-fallback").assertExists()
        rule.runOnIdle { assertEquals(Color(0xFF242428), page); matrix.assertContrast(Color.White, page) }
    }

    @Test fun circularPlayAndSongSelectionKeepIndependentCallbacks() {
        show(MornyePlayShape.Circle)
        rule.onNodeWithContentDescription(label("Play", "تشغيل")).performScrollTo().performClick()
        rule.onNodeWithTag("mornye-list").performScrollToIndex(3)
        rule.onNodeWithText("Song 2").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, play); assertEquals(1, song); assertEquals(0, more) }
    }
}
