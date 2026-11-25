import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/theme_provider.dart';

export 'package:sistrum/models/theme_settings.dart' show MornyeGlassLevel;

/// True on low-end hardware (arm32-only or low-RAM, resolved at startup in
/// main.dart via a ProviderScope override). Low-end devices render no glass.
final lowEndDeviceProvider = Provider<bool>((ref) => false);

/// Raw device capability decided by the startup runtime profile (overridden
/// in main.dart). Only the `high` tier enables blur by default.
final deviceSupportsBackdropBlurProvider = Provider<bool>((ref) => false);

/// Whether the GPU supports the Liquid Glass lens. Overridden in main.dart.
final deviceSupportsLiquidGlassProvider = Provider<bool>((ref) => true);

/// The glass mode the user selected (persisted, not backed up). Never changed
/// by device fallback; read [mornyeGlassLevelProvider] for rendering.
final mornyeGlassModeProvider = Provider<MornyeGlassLevel>(
  (ref) => ref.watch(themeProvider.select((s) => s.mornyeGlassLevel)),
);

/// The mode being rendered: a comparison's mode while one runs, else the
/// selected mode. Rendering reads this; the saved choice is never changed.
final mornyeGlassActiveModeProvider = Provider<MornyeGlassLevel>(
  (ref) =>
      ref.watch(mornyeGlassCompareModeProvider) ??
      ref.watch(mornyeGlassModeProvider),
);

/// Flutter also reports Android's medium contrast as high contrast. Glass is
/// decorative, so a high-contrast request turns it off. This only controls
/// decorative surfaces, never the inherited text or reduced-motion settings.
final mornyeGlassContrastBlockedProvider = Provider.family<bool, bool>(
  (ref, highContrast) => highContrast,
);

/// The glass the device renders for the selected mode. Device fallback can
/// downgrade a selection, never upgrade it. None is never changed.
final mornyeGlassLevelProvider = Provider<MornyeGlassLevel>((ref) {
  final selected = ref.watch(mornyeGlassActiveModeProvider);
  if (selected == MornyeGlassLevel.flat) return selected;
  if (ref.watch(lowEndDeviceProvider)) return MornyeGlassLevel.flat;
  // An unsupported lens renders as None. Only the rendered level changes;
  // the saved choice stays Liquid.
  if (selected == MornyeGlassLevel.liquid &&
      !ref.watch(deviceSupportsLiquidGlassProvider)) {
    return MornyeGlassLevel.flat;
  }
  return selected;
});

/// Backdrop blur for Mornye surfaces (Liquid only).
final mornyeBlurEnabledProvider = Provider<bool>(
  (ref) => ref.watch(mornyeGlassLevelProvider) != MornyeGlassLevel.flat,
);

/// Higher-quality glass and continuously filtered artwork / lyric effects.
final mornyeLiquidGlassProvider = Provider<bool>(
  (ref) => ref.watch(mornyeGlassLevelProvider) == MornyeGlassLevel.liquid,
);
