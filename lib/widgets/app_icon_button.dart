import 'package:material_ui/material_ui.dart';

enum _AppIconButtonVariant { standard, filled, tonal }

/// Drop-in [IconButton] with the shared variants and layout.
class AppIconButton extends StatelessWidget {
  const AppIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.color,
    this.iconSize,
    this.isSelected,
    this.selectedIcon,
    this.style,
    this.padding,
    this.constraints,
    this.visualDensity,
  }) : _variant = _AppIconButtonVariant.standard;

  const AppIconButton.filled({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.color,
    this.iconSize,
    this.isSelected,
    this.selectedIcon,
    this.style,
    this.padding,
    this.constraints,
    this.visualDensity,
  }) : _variant = _AppIconButtonVariant.filled;

  const AppIconButton.filledTonal({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.color,
    this.iconSize,
    this.isSelected,
    this.selectedIcon,
    this.style,
    this.padding,
    this.constraints,
    this.visualDensity,
  }) : _variant = _AppIconButtonVariant.tonal;

  final Widget icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final Color? color;
  final double? iconSize;
  final bool? isSelected;
  final Widget? selectedIcon;
  final ButtonStyle? style;
  final EdgeInsetsGeometry? padding;
  final BoxConstraints? constraints;
  final VisualDensity? visualDensity;
  final _AppIconButtonVariant _variant;

  @override
  Widget build(BuildContext context) {
    return switch (_variant) {
      _AppIconButtonVariant.standard => IconButton(
        icon: icon,
        selectedIcon: selectedIcon,
        onPressed: onPressed,
        tooltip: tooltip,
        color: color,
        iconSize: iconSize,
        isSelected: isSelected,
        style: style,
        padding: padding,
        constraints: constraints,
        visualDensity: visualDensity,
      ),
      _AppIconButtonVariant.filled => IconButton.filled(
        icon: icon,
        selectedIcon: selectedIcon,
        onPressed: onPressed,
        tooltip: tooltip,
        color: color,
        iconSize: iconSize,
        isSelected: isSelected,
        style: style,
        padding: padding,
        constraints: constraints,
        visualDensity: visualDensity,
      ),
      _AppIconButtonVariant.tonal => IconButton.filledTonal(
        icon: icon,
        selectedIcon: selectedIcon,
        onPressed: onPressed,
        tooltip: tooltip,
        color: color,
        iconSize: iconSize,
        isSelected: isSelected,
        style: style,
        padding: padding,
        constraints: constraints,
        visualDensity: visualDensity,
      ),
    };
  }
}
