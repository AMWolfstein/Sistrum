import 'package:material_ui/material_ui.dart';

/// Task progress (downloads, scans, updates), determinate or not.
class AppProgressIndicator extends StatelessWidget {
  const AppProgressIndicator.linear({
    super.key,
    this.value,
    this.color,
    this.trackColor,
    this.minHeight,
    this.borderRadius,
    this.semanticsLabel,
    this.semanticsValue,
  }) : strokeWidth = null,
       strokeCap = null,
       _circular = false;

  /// Fills the smaller side of its parent, or 48dp when unbounded.
  const AppProgressIndicator.circular({
    super.key,
    this.value,
    this.color,
    this.trackColor,
    this.strokeWidth,
    this.strokeCap,
    this.semanticsLabel,
    this.semanticsValue,
  }) : minHeight = null,
       borderRadius = null,
       _circular = true;

  /// Progress in `0..1`, or null for indeterminate.
  final double? value;
  final Color? color;
  final Color? trackColor;

  /// Height of the flat linear fallback; the wavy track keeps its own height.
  final double? minHeight;
  final BorderRadiusGeometry? borderRadius;
  final double? strokeWidth;

  /// Cap of the flat circular fallback; the wavy stroke is always round.
  final StrokeCap? strokeCap;
  final String? semanticsLabel;
  final String? semanticsValue;
  final bool _circular;

  @override
  Widget build(BuildContext context) {
    final value = this.value?.clamp(0.0, 1.0);
    return _circular
        ? CircularProgressIndicator(
            value: value,
            color: color,
            backgroundColor: trackColor,
            strokeWidth: strokeWidth,
            strokeCap: strokeCap,
            semanticsLabel: semanticsLabel,
            semanticsValue: semanticsValue,
          )
        : LinearProgressIndicator(
            value: value,
            color: color,
            backgroundColor: trackColor,
            minHeight: minHeight,
            borderRadius: borderRadius,
            semanticsLabel: semanticsLabel,
            semanticsValue: semanticsValue,
          );
  }
}
