import 'package:liquid_glass_easy/liquid_glass_easy.dart'
    show LiquidGlassBlurMethod;
import 'package:sistrum/models/theme_settings.dart';

/// What a glass surface is, which decides whether it reads the backdrop.
enum MornyeGlassKind {
  /// Navigation chrome that floats over content: the tab bar, the mini player,
  /// the search field, the menus and popovers while presented. Samples in
  /// Liquid.
  chrome,

  /// A small control: header circles, action capsules, the profile avatar,
  /// the side capsules of the bottom bar. Liquid keeps its lens in a batch.
  control,

  /// Content: settings groups, sheets, dialogs and the desktop inspector.
  /// Always a plain opaque fill, the same as None.
  content,
}

/// The one table of fixed glass values per mode: Liquid or None. Surfaces read
/// it through [MornyeGlassPreset.of]; no clarity, per-surface or third-mode
/// values remain.
class MornyeGlassPreset {
  const MornyeGlassPreset._({
    required this.blurSigma,
    required this.tintStrength,
    required this.clearMix,
    required this.dispersion,
    required this.controlsSample,
    required this.batchBlurMethod,
    required this.pillBlurSigma,
    required this.pillRefractionIndex,
    required this.pillRefractionWidth,
  });

  /// Gaussian sigma of the backdrop blur. 0 for None.
  final double blurSigma;

  /// Veil of the surface colour over a tinted fill.
  final double tintStrength;

  /// How far a lens fill moves towards the light clear fill (0 to 1).
  final double clearMix;

  /// Colour split of the liquid lens, in logical pixels.
  final double dispersion;

  /// Blur of the selection pill (the moving thumb of the tab bar and the
  /// segmented controls). 0 is the package's tab pill: a clear lens that only
  /// refracts, so it adds no blur pass.
  final double pillBlurSigma;

  /// Refraction of the selection pill, as the package's tab pill sets it.
  final double pillRefractionIndex;
  final double pillRefractionWidth;

  /// Whether a [MornyeGlassKind.control] reads the backdrop in this mode.
  final bool controlsSample;

  /// How a Liquid lens in a [LiquidGlassBatch] blurs. [LiquidGlassBlurMethod.shader]
  /// folds the blur into the lens shader, so it adds no engine blur pass per
  /// batch (package `LiquidGlassConfiguration.batchBlurMethod`). Measure it with
  /// the Performance hub: no device was available when this was chosen.
  final LiquidGlassBlurMethod batchBlurMethod;

  /// Whether a surface of [kind] reads the backdrop in this mode. Content
  /// never does; controls do only where [controlsSample] is set.
  bool samplesFor(MornyeGlassKind kind) => switch (kind) {
    MornyeGlassKind.chrome => true,
    MornyeGlassKind.control => controlsSample,
    MornyeGlassKind.content => false,
  };

  static const liquid = MornyeGlassPreset._(
    blurSigma: 2,
    tintStrength: 0.0625,
    clearMix: 0.6875,
    // No colour split: any non-zero dispersion adds a backdrop read.
    dispersion: 0,
    controlsSample: true,
    batchBlurMethod: LiquidGlassBlurMethod.shader,
    pillBlurSigma: 0,
    pillRefractionIndex: 1.015,
    pillRefractionWidth: 24,
  );

  static const none = MornyeGlassPreset._(
    blurSigma: 0,
    tintStrength: 0,
    clearMix: 0,
    dispersion: 0,
    controlsSample: false,
    batchBlurMethod: LiquidGlassBlurMethod.engine,
    pillBlurSigma: 0,
    pillRefractionIndex: 1.015,
    pillRefractionWidth: 24,
  );

  /// Dark dropdown and context menu tint, shared by every glass mode.
  static const double menuTintDark = 0.65;

  static MornyeGlassPreset of(MornyeGlassLevel level) => switch (level) {
    MornyeGlassLevel.liquid => liquid,
    MornyeGlassLevel.flat => none,
  };
}
