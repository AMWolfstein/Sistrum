package com.amwolfstein.sistrum.ui.testing

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.settings.ThemeMode
import com.amwolfstein.sistrum.ui.theme.SistrumTheme
import java.io.File

/** One instance per test/rule: setContent is called once, so JUnit parameterization supplies the matrix. */
class UiMatrix(
    private val rule: ComposeContentTestRule,
    val case: Case,
    private val outputDir: File,
) {
    data class Case(val theme: ThemeMode, val reduceTransparency: Boolean, val language: String, val fontScale: Float) {
        val id: String get() = "${theme.name}-reduce$reduceTransparency-$language-font$fontScale"
    }

    companion object {
        val cases: List<Case> = listOf(ThemeMode.Light, ThemeMode.Dark).flatMap { theme ->
            listOf(false, true).flatMap { reduce ->
                listOf("en", "ar").flatMap { language ->
                    listOf(1f, 1.3f, 2f).map { scale -> Case(theme, reduce, language, scale) }
                }
            }
        }

        /** Instrumentation's additionalTestOutputDir is used by Gradle; otherwise use test-owned external files. */
        fun outputDirectory(testName: String): File {
            require(testName.matches(Regex("[A-Za-z0-9_.-]+")))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val supplied = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            val base = if (supplied != null) File(supplied) else
                File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "ui-matrix")
            return File(base, testName)
        }
    }

    /** Fixtures must forward reduceTransparency into the screen's existing parameter; no preferences are written. */
    fun setContent(content: @Composable (reduceTransparency: Boolean) -> Unit) {
        rule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList.forLanguageTags(case.language))
                fontScale = case.fontScale
            }
            val context = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (case.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(LocalDensity.current.density, case.fontScale),
            ) {
                SistrumTheme(themeMode = case.theme) { content(case.reduceTransparency) }
            }
        }
    }

    /** Visible primary actions must be fully in the root, including their labels (no ellipsis/overflow). */
    fun assertNoClippedActions(actions: SemanticsMatcher = hasClickAction()) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = rule.onAllNodes(actions, useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "${case.id}: no actions matched" }
        for (node in nodes) {
            val raw = Rect(node.positionInRoot.x, node.positionInRoot.y,
                node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height)
            check(raw.width > 0 && raw.height > 0 && root.containsRect(raw) && node.boundsInRoot.containsRect(raw)) {
                "${case.id}: action ${node.id} clipped: raw=$raw visible=${node.boundsInRoot} root=$root"
            }
            assertLabelsFit(node)
        }
    }

    private fun assertLabelsFit(node: SemanticsNode) {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        check(results.none(::labelOverflows)) { "${case.id}: action label ${node.id} overflows" }
        node.children.forEach(::assertLabelsFit)
    }

    /** SC-002: normal text >=4.5:1; callers explicitly opt into the large-text >=3:1 threshold. */
    fun assertContrast(fg: Color, bg: Color, largeText: Boolean = false) {
        require(bg.alpha == 1f) { "Supply the final opaque background (composite glass/artwork first)" }
        val ratio = ColorUtils.calculateContrast(fg.compositeOver(bg).toArgb(), bg.toArgb())
        val minimum = if (largeText) 3.0 else 4.5
        check(ratio >= minimum) { "${case.id}: contrast $ratio < $minimum (fg=$fg, bg=$bg)" }
    }

    fun captureToImage(name: String = "screen", node: SemanticsNodeInteraction = rule.onRoot()): File {
        require(name.matches(Regex("[A-Za-z0-9_.-]+")))
        check(outputDir.isDirectory || outputDir.mkdirs()) { "Cannot create $outputDir" }
        val file = File(outputDir, "${case.id}-$name.png")
        file.outputStream().use { stream ->
            check(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Cannot encode $file"
            }
        }
        return file
    }
}

/**
 * Real truncation only. `hasVisualOverflow` is not usable here: a short label in a wrap-content button is laid out on a
 * paragraph as wide as the incoming max width (936 px for "Play" on the CPH2307) while the visible text box is its
 * measured size (83 px), so `didOverflowWidth` reports overflow for every fitting label. A label overflows when lines
 * were cut, a line is ellipsized, or a line's own text width (start-alignment independent, so RTL too) exceeds the box.
 */
private fun labelOverflows(layout: TextLayoutResult): Boolean =
    layout.multiParagraph.didExceedMaxLines || layout.size.height < layout.multiParagraph.height ||
        (0 until layout.lineCount).any { line ->
            layout.isLineEllipsized(line) || layout.getLineRight(line) - layout.getLineLeft(line) > layout.size.width + 0.5f
        }

private fun Rect.containsRect(other: Rect): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
