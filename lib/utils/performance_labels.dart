import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/theme_settings.dart';

/// Localized name of a glass mode, as the Appearance page shows it.
String glassModeLabel(AppLocalizations l10n, MornyeGlassLevel mode) =>
    switch (mode) {
      MornyeGlassLevel.liquid => l10n.appearanceGlassLiquid,
      MornyeGlassLevel.flat => l10n.appearanceGlassNone,
    };

/// mm:ss for a recording's elapsed time.
String performanceClock(Duration elapsed) {
  String two(int value) => value.toString().padLeft(2, '0');
  return '${two(elapsed.inMinutes)}:${two(elapsed.inSeconds % 60)}';
}
