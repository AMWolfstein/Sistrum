import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/mornye_icons.dart';

/// [Icon] drawn with Mornye's glyphs.
///
/// [icon] is used unchanged, or its Cupertino counterpart with
/// [AppIcon.adaptive]. Icons without a counterpart fall back to [icon].
class AppIcon extends StatelessWidget {
  const AppIcon(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.semanticLabel,
    this.shadows,
    this.textDirection,
  }) : _adaptive = false;

  /// Uses [mornyeIconFor] for the glyph.
  const AppIcon.adaptive(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.semanticLabel,
    this.shadows,
    this.textDirection,
  }) : _adaptive = true;

  final IconData? icon;
  final double? size;
  final Color? color;
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
