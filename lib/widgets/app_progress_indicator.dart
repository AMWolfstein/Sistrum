import 'package:flutter/material.dart';

/// Task progress (downloads, scans, updates), determinate or not.
///
/// Flutter's flat indicators; [value] is clamped to `0..1`.
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

  final double? minHeight;
  final BorderRadiusGeometry? borderRadius;
  final double? strokeWidth;

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
