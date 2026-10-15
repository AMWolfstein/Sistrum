package com.amwolfstein.sistrum.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SistrumSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val extraExtraLarge: Dp = 32.dp,
)

@Immutable
data class SistrumShapes(
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(16.dp),
    val large: RoundedCornerShape = RoundedCornerShape(24.dp),
    val extraLarge: RoundedCornerShape = RoundedCornerShape(28.dp),
)

/**
 * Text and mutedText are readable on page, opaqueFill and glassTint over opaqueFill.
 * Accent is suitable for large text (3:1); use text or mutedText for normal-size labels.
 * Scrim is an overlay, divider/focusRing are strokes, and disabled is for inactive content.
 */
@Immutable
data class SistrumTokenColors(
    val page: Color,
    val glassTint: Color,
    val opaqueFill: Color,
    val scrim: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    val divider: Color,
    val disabled: Color,
    val focusRing: Color,
)

/** Foundation durations in milliseconds; reduced motion resolves every duration to zero. */
@Immutable
data class SistrumMotion(
    val shortDurationMillis: Int = 150,
    val mediumDurationMillis: Int = 300,
    val longDurationMillis: Int = 500,
)

@Immutable
data class SistrumTokens(
    val colors: SistrumTokenColors,
    val spacing: SistrumSpacing = SistrumSpacing(),
    val shapes: SistrumShapes = SistrumShapes(),
    val motion: SistrumMotion = SistrumMotion(),
)

/** Reuses the theme palette without changing any existing color or theme consumer. */
fun sistrumTokens(colors: SistrumColors, reducedMotion: Boolean = false): SistrumTokens = SistrumTokens(
    motion = if (reducedMotion) SistrumMotion(0, 0, 0) else SistrumMotion(),
    colors = SistrumTokenColors(
        page = colors.background,
        glassTint = colors.glass,
        opaqueFill = colors.glassOpaque,
        scrim = colors.playerBackdrop,
        text = colors.textMain,
        mutedText = colors.textMuted,
        accent = colors.primary,
        divider = colors.borderGlass,
        disabled = colors.textMuted.copy(alpha = 0.38f),
        focusRing = colors.primary,
    ),
)

// Matches LocalSistrumColors' light default outside SistrumTheme.
val LocalSistrumTokens = staticCompositionLocalOf { sistrumTokens(LightColors) }
