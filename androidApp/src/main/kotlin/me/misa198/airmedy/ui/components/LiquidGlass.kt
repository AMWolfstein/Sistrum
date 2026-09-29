package me.misa198.airmedy.ui.components

import androidx.compose.foundation.background
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
import me.misa198.airmedy.ui.theme.AirmedyColors

/** Downsampling makes small glass surfaces look pixelated after upscaling, so sample at full resolution. */
internal val LiquidGlassPerformanceMode: HazePerformanceMode = HazePerformanceMode.Quality

/** The shared backdrop treatment for the persistent navigation and page header. */
fun Modifier.liquidGlassBackground(
    hazeState: HazeState?,
    colors: AirmedyColors,
    hazeBlurRadius: Dp = 16.dp,
    glassTint: Color? = null,
): Modifier = if (hazeState == null) {
    background(colors.glassOpaque)
} else {
    hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeBlurStyle {
            blurRadius(hazeBlurRadius)
            colorEffects(listOf(HazeColorEffect.tint(glassTint ?: colors.glass)))
        },
        performanceMode = LiquidGlassPerformanceMode,
    ).background(glassTint ?: colors.glass)
}
