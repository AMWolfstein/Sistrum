package com.amwolfstein.sistrum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens

/** Shared decision for glass surfaces; reduceTransparency comes from ThemePreferences. */
enum class GlassPolicy {
    Blur,
    Opaque,
    ;

    companion object {
        fun resolve(
            reduceTransparency: Boolean,
            hazeAvailable: Boolean,
            performanceFallback: Boolean,
        ): GlassPolicy = if (reduceTransparency || !hazeAvailable || performanceFallback) Opaque else Blur
    }
}

/** Downsampling makes small glass surfaces look pixelated after upscaling, so sample at full resolution. */
internal val LiquidGlassPerformanceMode: HazePerformanceMode = HazePerformanceMode.Quality

/** App-level policy inputs; a missing Haze state independently forces the opaque fallback. */
val LocalReduceTransparency = staticCompositionLocalOf { false }
val LocalGlassPerformanceFallback = staticCompositionLocalOf { false }

/** Shared surface without extra padding, clipping or sizing, so callers retain their geometry. */
@Composable
fun GlassSurface(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    borderShape: Shape? = null,
    scrimAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.liquidGlassBackground(hazeState, borderShape = borderShape, scrimAlpha = scrimAlpha), content = content)
}

/**
 * The same treatment for surfaces whose background sits inside an existing modifier chain.
 * A scrim and border are opt-in so existing overlay and clipping geometry stays with the caller.
 */
@Composable
fun Modifier.liquidGlassBackground(
    hazeState: HazeState?,
    hazeBlurRadius: Dp = 16.dp,
    glassTint: Color? = null,
    borderShape: Shape? = null,
    scrimAlpha: Float = 0f,
): Modifier {
    val colors = LocalSistrumTokens.current.colors
    val policy = GlassPolicy.resolve(
        reduceTransparency = LocalReduceTransparency.current,
        hazeAvailable = hazeState != null,
        performanceFallback = LocalGlassPerformanceFallback.current,
    )
    val surface = if (policy == GlassPolicy.Opaque) {
        background(colors.opaqueFill)
    } else {
        hazeBlur(
            input = HazeInput.Sources(requireNotNull(hazeState)),
            style = HazeBlurStyle {
                blurRadius(hazeBlurRadius)
                colorEffects(listOf(HazeColorEffect.tint(glassTint ?: colors.glassTint)))
            },
            performanceMode = LiquidGlassPerformanceMode,
        ).background(glassTint ?: colors.glassTint)
    }
    return surface
        .then(if (scrimAlpha > 0f) Modifier.background(colors.scrim.copy(alpha = scrimAlpha)) else Modifier)
        .then(if (borderShape != null) Modifier.border(1.dp, colors.divider, borderShape) else Modifier)
}
