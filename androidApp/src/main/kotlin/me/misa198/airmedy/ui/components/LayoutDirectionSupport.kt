package me.misa198.airmedy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection

/** Whether the current layout runs right to left. */
@Composable
@ReadOnlyComposable
internal fun isLayoutRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl

/**
 * Lays [content] out left to right regardless of the app's direction. Media transport
 * controls, the seek bar and the volume slider stay left to right in RTL locales, as in
 * standard music players, because they describe time and level rather than reading order.
 */
@Composable
internal fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/**
 * Converts a physical horizontal distance (positive = rightwards, as drag deltas and
 * graphics-layer translations are) into a direction-relative one (positive = towards the
 * layout's end), and back: the conversion is its own inverse.
 */
internal fun Float.towardsEnd(layoutDirection: LayoutDirection): Float =
    if (layoutDirection == LayoutDirection.Rtl) -this else this

/**
 * Whether [text] reads right to left on its own: its first strong character decides, and text
 * without one (digits, punctuation) follows [layoutDirection]. This matches how Compose resolves
 * the default content text direction, so it predicts where a line's start and end land.
 */
internal fun isRtlText(text: CharSequence, layoutDirection: LayoutDirection): Boolean {
    val heuristic = if (layoutDirection == LayoutDirection.Rtl) {
        android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL
    } else {
        android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR
    }
    return heuristic.isRtl(text, 0, text.length)
}

/**
 * Text alignment to the layout's end edge. `TextAlign.End` follows each string's own direction,
 * which would scatter a column of mixed Arabic and Latin values across both edges.
 */
@Composable
@ReadOnlyComposable
internal fun layoutEndTextAlign(): TextAlign = if (isLayoutRtl()) TextAlign.Left else TextAlign.Right
