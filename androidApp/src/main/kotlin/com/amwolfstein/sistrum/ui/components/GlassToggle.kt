// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from Exhale (modified): ui/component/liquid/LiquidToggle.kt
// Source: https://github.com/ozyern/Exhale, commit 166e2cc7c6a3bcfb944999571e1bc5e0e3c1a066.
// Modified: Sistrum policy fallback, switch semantics, enabled state and controlled drag input.
package com.amwolfstein.sistrum.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.amwolfstein.sistrum.ui.theme.LocalReducedMotion
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Exhale's local-track glass lens, with controlled switch state and accessible input. */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)
    val glass = GlassPolicy.resolve(
        reduceTransparency = LocalReduceTransparency.current,
        hazeAvailable = Build.VERSION.SDK_INT >= 33,
        performanceFallback = LocalGlassPerformanceFallback.current || LocalReducedMotion.current,
    ) == GlassPolicy.Blur
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val value = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked, dragging, fraction, glass) {
        val target = if (dragging) fraction else if (checked) 1f else 0f
        if (glass) value.animateTo(target, spring(1f, 1000f, 0.001f))
        else value.snapTo(target)
    }
    val pressProgress by animateFloatAsState(
        if (glass && enabled && (pressed || dragging)) 1f else 0f,
        spring(1f, 1000f, 0.001f),
    )
    val scaleX by animateFloatAsState(if (glass && enabled && (pressed || dragging)) 1.5f else 1f, spring(0.6f, 250f))
    val scaleY by animateFloatAsState(if (glass && enabled && (pressed || dragging)) 1.5f else 1f, spring(0.7f, 250f))
    val trackBackdrop = rememberLayerBackdrop()
    val input = Modifier
        .pointerInput(enabled, isLtr, dragWidth) {
            if (enabled) detectHorizontalDragGestures(
                onDragStart = {
                    fraction = if (currentChecked) 1f else 0f
                    dragging = true
                },
                onDragEnd = {
                    currentOnChange(fraction >= 0.5f)
                    dragging = false
                },
                onDragCancel = { dragging = false },
            ) { change, delta ->
                change.consume()
                fraction = (fraction + (if (isLtr) delta else -delta) / dragWidth).coerceIn(0f, 1f)
            }
        }
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interaction,
            indication = null,
            onValueChange = onCheckedChange,
        )
    Box(
        modifier.requiredSize(64.dp, 48.dp).alpha(if (enabled) 1f else 0.38f).then(input),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track background
        Box(
            Modifier
                .then(if (glass) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(RoundedCornerShape(50)) // Capsule
                .drawBehind {
                    val fraction = if (glass) value.value else if (checked) 1f else 0f
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(64f.dp, 28f.dp)
        )

        // Thumb
        Box(
            Modifier.testTag("glass-toggle-thumb")
                .graphicsLayer {
                    val fraction = if (glass) value.value else if (checked) 1f else 0f
                    val padding = 2f.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, fraction)
                    else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .then(if (!glass) Modifier.background(Color.White, RoundedCornerShape(50)) else Modifier.drawBackdrop(
                    // Sample the sibling track only; an ancestor recording would draw itself.
                    backdrop = rememberBackdrop(trackBackdrop) { drawBackdrop ->
                        val progress = pressProgress
                        val scaleX = lerp(2f / 3f, 0.75f, progress)
                        val scaleY = lerp(0f, 0.75f, progress)
                        scale(scaleX, scaleY) {
                            drawBackdrop()
                        }
                    },
                    shape = { RoundedCornerShape(50) }, // Capsule
                    effects = {
                        val progress = pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        val velocity = value.velocity / 50f
                        this.scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        this.scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                ))
                .size(40f.dp, 24f.dp)
        )
    }
}
