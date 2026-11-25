import 'package:flutter/physics.dart';
import 'package:flutter/widgets.dart';

/// A duration and curve pair for implicit animations.
@immutable
class AppMotionSpec {
  const AppMotionSpec(this.duration, this.curve);

  final Duration duration;
  final Curve curve;

  /// [curve] for the child a switcher takes away (`switchOutCurve`, or a
  /// `reverseCurve`). Flutter runs that curve backwards, which would hold a
  /// leaving child in place for most of a spring and drop it at the end;
  /// flipped, the child leaves the way the spec moves, fastest first.
  Curve get leaving => curve.flipped;
}

/// A [Curve] that follows a [SpringDescription] released from rest at 0 toward
/// 1.
///
/// [duration] is the spring's settling time, so implicit animations driven by
/// this curve end exactly when the physical motion does.
class SpringCurve extends Curve {
  SpringCurve.of(SpringDescription spring)
    : this._(SpringSimulation(spring, 0, 1, 0));

  SpringCurve._(this._simulation) : duration = _settleTime(_simulation);

  final SpringSimulation _simulation;
  final Duration duration;

  static Duration _settleTime(SpringSimulation simulation) {
    const step = 1 / 1000;
    var settledSince = 0.0;
    for (var t = 0.0; t < 2; t += step) {
      final settled =
          (simulation.x(t) - 1).abs() < 0.002 && simulation.dx(t).abs() < 0.02;
      if (!settled) {
        settledSince = t + step;
      } else if (t - settledSince >= 0.05) {
        break;
      }
    }
    return Duration(microseconds: (settledSince * 1e6).round());
  }

  @override
  double transformInternal(double t) =>
      _simulation.x(t * duration.inMicroseconds / 1e6);
}

/// Apple-style springs for the Mornye theme.
///
/// A spring is named the way SwiftUI's `spring(duration:bounce:)` names it:
/// by the time its motion reads as done and by its bounce. The system's
/// default spring (`smooth`) has no bounce, and so do these, except
/// [snappy]. A spring moves most of the way early and settles in a long,
/// quiet tail, so its [AppMotionSpec.duration] is longer than its name.
abstract final class MornyeMotion {
  static SpringDescription _spring(int milliseconds, [double bounce = 0]) =>
      SpringDescription.withDurationAndBounce(
        duration: Duration(milliseconds: milliseconds),
        bounce: bounce,
      );

  /// Small controls: a press, a check, a chip, a hover.
  static final SpringDescription fastSpring = _spring(250);

  /// Content that moves or resizes: rows, panels, a selection.
  static final SpringDescription standardSpring = _spring(350);

  /// Full-screen surfaces, at the pace of the system's default spring.
  static final SpringDescription slowSpring = _spring(500);

  /// A control with a physical feel that lands with a slight bounce, as the
  /// system's `snappy` spring: a selection thumb, a switch thumb. Not for a
  /// surface whose size resizes other content.
  static final SpringDescription snappySpring = _spring(350, 0.15);

  /// Opacity and color of small controls.
  static final SpringDescription effectsFastSpring = _spring(150);

  /// Opacity and color of content.
  static final SpringDescription effectsSpring = _spring(250);

  static AppMotionSpec _spec(SpringDescription spring) {
    final curve = SpringCurve.of(spring);
    return AppMotionSpec(curve.duration, curve);
  }

  static final AppMotionSpec fast = _spec(fastSpring);
  static final AppMotionSpec standard = _spec(standardSpring);
  static final AppMotionSpec slow = _spec(slowSpring);
  static final AppMotionSpec snappy = _spec(snappySpring);
  static final AppMotionSpec effectsFast = _spec(effectsFastSpring);
  static final AppMotionSpec effects = _spec(effectsSpring);
}

/// The app's motion presets. Each call site passes its `fallback`, used when
/// reduced motion is on or the fallback has no length. A site that needs its
/// own Mornye motion passes `mornye`.
abstract final class AppMotion {
  static const _still = AppMotionSpec(Duration.zero, Curves.linear);

  /// A move that must stop at its end: a section that opens above other
  /// content, a card that unfolds, a scroll most of a page long. Mornye's
  /// standard spring, which does not pass its target; nothing with reduced
  /// motion.
  static AppMotionSpec surface(BuildContext context) {
    if (MediaQuery.disableAnimationsOf(context)) return _still;
    return MornyeMotion.standard;
  }

  static AppMotionSpec _resolve(
    BuildContext context,
    AppMotionSpec fallback,
    AppMotionSpec apple,
    AppMotionSpec? mornye,
  ) {
    if (MediaQuery.disableAnimationsOf(context)) return fallback;
    if (mornye != null) return mornye;
    return fallback.duration == Duration.zero ? fallback : apple;
  }

  /// Fast spatial spring for small components: chips, checks, buttons.
  static AppMotionSpec spatialFast(
    BuildContext context,
    AppMotionSpec fallback, {
    AppMotionSpec? mornye,
  }) => _resolve(context, fallback, MornyeMotion.fast, mornye);

  /// Default spatial spring for list items, panels and content changes.
  static AppMotionSpec spatial(
    BuildContext context,
    AppMotionSpec fallback, {
    AppMotionSpec? mornye,
  }) => _resolve(context, fallback, MornyeMotion.standard, mornye);

  /// Slow spatial spring for full-screen surfaces such as the player.
  static AppMotionSpec spatialSlow(
    BuildContext context,
    AppMotionSpec fallback, {
    AppMotionSpec? mornye,
  }) => _resolve(context, fallback, MornyeMotion.slow, mornye);

  /// Fast effects spring for opacity and color.
  static AppMotionSpec effectsFast(
    BuildContext context,
    AppMotionSpec fallback, {
    AppMotionSpec? mornye,
  }) => _resolve(context, fallback, MornyeMotion.effectsFast, mornye);

  /// Default effects spring for opacity and color.
  static AppMotionSpec effects(
    BuildContext context,
    AppMotionSpec fallback, {
    AppMotionSpec? mornye,
  }) => _resolve(context, fallback, MornyeMotion.effects, mornye);
}
