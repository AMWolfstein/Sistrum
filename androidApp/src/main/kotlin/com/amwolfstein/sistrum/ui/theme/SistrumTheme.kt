package com.amwolfstein.sistrum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.isSystemInDarkTheme
import com.amwolfstein.sistrum.settings.ThemeMode

data class SistrumColors(
    val background: Color,
    val playerBackdrop: Color,
    val card: Color,
    val glass: Color,
    val glassOpaque: Color,
    val glassElevated: Color,
    val sliderInactive: Color,
    val buttonSecondary: Color,
    val textFieldClear: Color,
    val borderGlass: Color,
    val textMain: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val foregroundSubtle: Color,
    val success: Color,
    val navigationActive: Color,
    // Audio quality badge accents mirror the desktop quality palette.
    val qualityHiRes: Color = Color(0xFFF59E0B),
    val qualityDsd: Color = Color(0xFFE879F9),
    val qualityDsdSurface: Color = Color(0xFFD946EF),
)

private val LightColors = SistrumColors(
    background = Color(0xFFF6F3EE),
    playerBackdrop = Color(0xFF19140F),
    card = Color.White,
    glass = Color.White.copy(alpha = 0.4f),
    glassOpaque = Color.White,
    glassElevated = Color.White.copy(alpha = 0.90f),
    // The fullscreen player sits over artwork, so this remains translucent to
    // let that backdrop show through instead of reading as a solid gray bar.
    sliderInactive = Color.White.copy(alpha = 0.10f),
    buttonSecondary = Color(0xFFEAE3D7),
    textFieldClear = Color(0xFFDCD2C2),
    borderGlass = Color.Black.copy(alpha = 0.10f),
    textMain = Color(0xFF0A0A0A),
    textMuted = Color(0xFF5C5246),
    primary = Color(0xFF8A5A1E),
    onPrimary = Color.White,
    foregroundSubtle = Color.White.copy(alpha = 0.46f),
    success = Color(0xFF16A34A),
    navigationActive = Color.Black.copy(alpha = 0.08f),
    qualityHiRes = Color(0xFFF59E0B),
    qualityDsd = Color(0xFFE879F9),
    qualityDsdSurface = Color(0xFFD946EF),
)

private val DarkColors = SistrumColors(
    background = Color(0xFF19140F),
    playerBackdrop = Color(0xFF0F0C09),
    card = Color(0xFF241D16),
    glass = Color(0xFF211B14).copy(alpha = 0.4f),
    glassOpaque = Color(0xFF211B14),
    glassElevated = Color(0xFF352B20).copy(alpha = 0.40f),
    sliderInactive = Color.White.copy(alpha = 0.10f),
    buttonSecondary = Color(0xFF4A3C2C),
    textFieldClear = Color(0xFF3A2F23),
    borderGlass = Color.White.copy(alpha = 0.10f),
    textMain = Color.White,
    textMuted = Color(0xFFB3A58E),
    primary = Color(0xFFDAAA57),
    onPrimary = Color(0xFF19140F),
    foregroundSubtle = Color.White.copy(alpha = 0.46f),
    success = Color(0xFF4ADE80),
    navigationActive = Color.Black.copy(alpha = 0.40f),
    qualityHiRes = Color(0xFFF59E0B),
    qualityDsd = Color(0xFFE879F9),
    qualityDsdSurface = Color(0xFFD946EF),
)

val LocalSistrumColors = staticCompositionLocalOf { LightColors }

@Composable
fun SistrumTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            surface = colors.glass,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            surface = colors.glass,
            onBackground = colors.textMain,
            onSurface = colors.textMain,
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    val baseTypography = MaterialTheme.typography
    val typography = remember(baseTypography, layoutDirection) { baseTypography.withNaturalTextDirection(layoutDirection) }
    CompositionLocalProvider(LocalSistrumColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

/**
 * Every string takes its own direction (TextDirection.Content): an Arabic title inside the
 * English UI is laid out right to left and a Latin title or folder path inside the Arabic UI
 * left to right, so punctuation and inline badges land correctly. Compose would otherwise lay
 * out all text in the layout direction. Alignment stays at the layout's start edge, so lists
 * keep one edge whatever the script of each title; text that needs its own alignment (lyrics)
 * sets it explicitly.
 */
private fun Typography.withNaturalTextDirection(layoutDirection: LayoutDirection): Typography {
    val align = if (layoutDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
    fun TextStyle.natural() = copy(textDirection = TextDirection.Content, textAlign = align)
    return copy(
        displayLarge = displayLarge.natural(),
        displayMedium = displayMedium.natural(),
        displaySmall = displaySmall.natural(),
        headlineLarge = headlineLarge.natural(),
        headlineMedium = headlineMedium.natural(),
        headlineSmall = headlineSmall.natural(),
        titleLarge = titleLarge.natural(),
        titleMedium = titleMedium.natural(),
        titleSmall = titleSmall.natural(),
        bodyLarge = bodyLarge.natural(),
        bodyMedium = bodyMedium.natural(),
        bodySmall = bodySmall.natural(),
        labelLarge = labelLarge.natural(),
        labelMedium = labelMedium.natural(),
        labelSmall = labelSmall.natural(),
    )
}
