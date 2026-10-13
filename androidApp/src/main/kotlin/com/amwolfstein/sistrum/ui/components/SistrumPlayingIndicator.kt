package com.amwolfstein.sistrum.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors

/** Three-bar now-playing indicator, animated only while playback is active. */
@Composable
fun SistrumPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSistrumColors.current
    val playbackProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "playing-indicator-playback-progress",
    )
    // Once paused and the 220 ms settle has finished, the bars are flat (scaleY 0.3 whatever
    // the scale), so drop the infinite transition instead of ticking it every frame.
    val scales = if (isPlaying || playbackProgress > 0f) animatedBarScales() else StaticBarScales

    Row(
        modifier = modifier.testTag("playing_indicator").height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        scales.forEach { scale ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = 0.3f + (scale() - 0.3f) * playbackProgress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(colors.onPrimary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private val StaticBarScales: List<() -> Float> = List(3) { { 0.3f } }

@Composable
private fun animatedBarScales(): List<() -> Float> {
    val transition = rememberInfiniteTransition(label = "playing-indicator")
    val first = transition.animateScale(0.3f, 0.8f, 800, "playing-indicator-first")
    val second = transition.animateScale(1f, 0.4f, 600, "playing-indicator-second")
    val third = transition.animateScale(0.6f, 0.9f, 700, "playing-indicator-third")
    return listOf({ first.value }, { second.value }, { third.value })
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateScale(
    start: Float,
    middle: Float,
    duration: Int,
    label: String,
) = animateFloat(
    initialValue = start,
    targetValue = middle,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = duration
            start at 0 using FastOutSlowInEasing
            middle at duration / 2 using FastOutSlowInEasing
            start at duration
        },
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)
