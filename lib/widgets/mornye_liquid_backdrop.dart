import 'dart:ui' show ImageFilter;
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:mornye/providers/runtime_profile_provider.dart';
import 'package:mornye/theme/mornye_theme.dart';

/// The one place that decides how Mornye glass renders. Glass is fixed at the
/// Clear end of the retired Glass Quality slider, so every surface reads the
/// same blur budget and every eligible surface samples its backdrop.
abstract final class MornyeGlassRules {
  /// Blur of the Clear liquid lens, and of the frosted fallback.
  static const clearSigma = 2.0;
  static const frostedSigma = 3.0;

  /// Shared, immutable filters so a rebuild never creates one.
  static final _clearFilter = ImageFilter.blur(
    sigmaX: clearSigma,
    sigmaY: clearSigma,
  );
  static final _frostedFilter = ImageFilter.blur(
    sigmaX: frostedSigma,
    sigmaY: frostedSigma,
  );

  /// Host tests cannot run shader filters; this lets them build the lens.
  @visibleForTesting
  static bool? debugShaderFilterSupport;

  /// Whether the shader lens can render here: Impeller only, and never in a
  /// test host unless [debugShaderFilterSupport] says so.
  static bool get shaderLensSupported =>
      debugShaderFilterSupport ?? ImageFilter.isShaderFilterSupported;

  /// Blur sigma for a glass level: frosted keeps its small budget.
  static double blurSigma(MornyeGlassLevel level) =>
      level == MornyeGlassLevel.frosted ? frostedSigma : clearSigma;

  /// The shared backdrop filter for a glass level.
  static ImageFilter blurFilter(MornyeGlassLevel level) =>
      level == MornyeGlassLevel.frosted ? _frostedFilter : _clearFilter;

  /// Widest colour split in logical pixels, for an effect amount (`progress`
  /// of a held lens, `1` at rest).
  static double dispersion(double effect) => 1.32 * effect;
}

/// A bounded package lens without a captured-screen view or a touch ticker.
class MornyeLiquidBackdrop extends StatelessWidget {
  const MornyeLiquidBackdrop({
    super.key,
    required this.borderRadius,
    required this.blurSigma,
    required this.child,
    this.interaction = false,
    this.progress = 1,
    this.refractionDepth = 0.30,
    this.refractionWidth = 20,
    this.smoothCorners = false,
    this.highlightIntensity = 1,
    this.opacity = 1,
  });

  final BorderRadius borderRadius;
  final double blurSigma;
  final Widget child;
  final bool interaction;
  final double progress;
  final double refractionDepth;
  final double refractionWidth;
  final bool smoothCorners;
  final double highlightIntensity;

  /// Fades the whole lens (refraction, tint, rim and its blur); `0` costs no
  /// backdrop pass and leaves the child.
  final double opacity;

  @override
  Widget build(BuildContext context) {
    if (interaction && progress <= 0) return child;
    final effect = interaction ? progress : 1.0;
    final outlines = MornyeTheme.glassOutlinesOf(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    // Match the old optical lens's edge displacement. Version 5 fixes travel
    // at 70 logical pixels and controls bending through the index alone.
    final edgeBend =
        math.sqrt(1 - 1 / (1.7 * 1.7)) *
        refractionWidth *
        refractionDepth *
        effect *
        5 /
        70;
    final index = 1 / math.sqrt(1 - math.pow(edgeBend.clamp(0, 0.95), 2));
    return LiquidGlassLens(
      opacity: opacity,
      style: LiquidGlassStyle(
        // The package's named constructors set the corner style and clip
        // quality together, so the two ternaries become one choice.
        shape: smoothCorners
            ? LiquidGlassShape.squircle(
                cornerRadius: borderRadius.topLeft.x,
                clipQuality: LiquidGlassClipQuality.exact,
              )
            : LiquidGlassShape.roundedRectangle(
                cornerRadius: borderRadius.topLeft.x,
              ),
        specular: LiquidGlassLight.specular(
          thickness: outlines ? 0.75 * 5.4 : 0,
          angle: 0,
          spread: 0.2,
          intensity: outlines
              ? (dark ? 0.25 : 0.12) * effect * highlightIntensity
              : 0,
          color: dark ? Colors.white : const Color(0x88ffffff),
        ),
        glow: LiquidGlassLight.glow(
          thickness: outlines ? 0.75 * 29.4 : 0,
          intensity: outlines ? 0.05 * highlightIntensity : 0,
        ),
        blur: blurSigma,
        refractionIndex: index,
        refractionWidth: refractionWidth,
        // Version 5 expresses the widest colour split in logical pixels.
        dispersion: MornyeGlassRules.dispersion(effect),
      ),
      child: child,
    );
  }
}
