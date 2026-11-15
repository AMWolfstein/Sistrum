import 'package:flutter/material.dart';
import 'package:sistrum/theme/mornye_icons.dart';

/// [widget] as an [AppIcon] when it is a plain [Icon], so shared components
/// can adopt Material Symbols without changing every caller.
Widget symbolIconWidget(Widget widget) {
  if (widget case Icon(:final icon?)) {
    return AppIcon(
      icon,
      key: widget.key,
      size: widget.size,
      color: widget.color,
      fill: widget.fill,
      semanticLabel: widget.semanticLabel,
      shadows: widget.shadows,
      textDirection: widget.textDirection,
    );
  }
  return widget;
}

/// [Icon] that draws the Material theme with rounded Material Symbols.
///
/// Mornye keeps [icon] unchanged, or its Cupertino counterpart with
/// [AppIcon.adaptive]. Icons without a symbol fall back to [icon].
class AppIcon extends StatelessWidget {
  const AppIcon(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.fill,
    this.semanticLabel,
    this.shadows,
    this.textDirection,
  }) : _adaptive = false;

  /// Uses [mornyeIconFor] when the Mornye theme is on.
  const AppIcon.adaptive(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.fill,
    this.semanticLabel,
    this.shadows,
    this.textDirection,
  }) : _adaptive = true;

  final IconData? icon;
  final double? size;
  final Color? color;

  /// Overrides the symbol fill; null keeps the legacy glyph's filled state.
  final double? fill;
  final String? semanticLabel;
  final List<Shadow>? shadows;
  final TextDirection? textDirection;
  final bool _adaptive;

  @override
  Widget build(BuildContext context) {
    final icon = this.icon;
    return Icon(
      _adaptive && icon != null ? mornyeIconFor(icon) : icon,
      size: size,
      color: color,
      semanticLabel: semanticLabel,
      shadows: shadows,
      textDirection: textDirection,
    );
  }
}
