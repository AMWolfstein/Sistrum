import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/custom_font_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';

/// Builds the light and dark Mornye themes from the stored theme settings.
class MornyeThemeWrapper extends ConsumerWidget {
  final Widget Function(ThemeData light, ThemeData dark, ThemeMode mode)
  builder;

  const MornyeThemeWrapper({super.key, required this.builder});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Lyrics size and font names live in ThemeSettings but not in ThemeData.
    final themeSettings = ref.watch(
      themeProvider.select(
        (s) => (
          themeMode: s.themeMode,
          mornyeAccent: s.mornyeAccent,
          useSystemFont: s.useSystemFont,
          mornyeGlassOutlines: s.mornyeGlassOutlines,
        ),
      ),
    );
    // Only the family: a rebuild while it loads would animate an equal theme.
    final fontFamily = ref.watch(
      customFontFamilyProvider.select((font) => font.asData?.value),
    );

    return builder(
      MornyeTheme.build(
        Brightness.light,
        accent: themeSettings.mornyeAccent,
        useSystemFont: themeSettings.useSystemFont,
        fontFamily: fontFamily,
        glassOutlines: themeSettings.mornyeGlassOutlines,
      ),
      MornyeTheme.build(
        Brightness.dark,
        accent: themeSettings.mornyeAccent,
        useSystemFont: themeSettings.useSystemFont,
        fontFamily: fontFamily,
        glassOutlines: themeSettings.mornyeGlassOutlines,
      ),
      themeSettings.themeMode,
    );
  }
}
