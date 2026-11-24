import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/mornye_theme.dart';

/// One inline surface, matching the metadata groups and dialog controls.
class AppContentCard extends StatelessWidget {
  const AppContentCard({
    super.key,
    required this.child,
    this.color,
    this.shape,
    this.elevation,
    this.clipBehavior = Clip.none,
    this.preserveColor = false,
    this.margin,
    this.onTap,
    this.onLongPress,
    this.mornyeSurface = true,
  });

  final Widget child;
  final Color? color;
  final ShapeBorder? shape;
  final double? elevation;
  final Clip clipBehavior;
  final bool preserveColor;

  /// Outer margin; zero by default.
  final EdgeInsetsGeometry? margin;

  /// Makes the whole card pressable.
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// False keeps a plain [Card] under Mornye for surfaces that carry their
  /// own tinted color (errors, notices) rather than the Mornye control fill.
  final bool mornyeSurface;

  @override
  Widget build(BuildContext context) {
    if (!mornyeSurface) {
      final interactive = onTap != null || onLongPress != null;
      return Card(
        color: color,
        shape: shape,
        elevation: elevation,
        margin: margin,
        clipBehavior: interactive ? Clip.antiAlias : clipBehavior,
        child: interactive
            ? InkWell(onTap: onTap, onLongPress: onLongPress, child: child)
            : child,
      );
    }
    final content = DividerTheme(
      data: DividerTheme.of(
        context,
      ).copyWith(color: MornyeTheme.metadataDividerColor(context)),
      child: child,
    );
    final surface = Material(
      color: preserveColor ? color : MornyeTheme.controlFill(context),
      borderRadius: BorderRadius.circular(28),
      clipBehavior: Clip.antiAlias,
      child: onTap != null || onLongPress != null
          ? InkWell(onTap: onTap, onLongPress: onLongPress, child: content)
          : content,
    );
    return margin == null ? surface : Padding(padding: margin!, child: surface);
  }
}
