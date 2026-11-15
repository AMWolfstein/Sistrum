import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_action_button.dart';

/// Responsive rows shared by selection-mode action buttons.
class SelectionActionGrid extends StatelessWidget {
  const SelectionActionGrid({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const spacing = 8.0;
        final columns = (constraints.maxWidth / 320).floor().clamp(2, 4);
        final itemWidth =
            (constraints.maxWidth - spacing * (columns - 1)) / columns;
        return Wrap(
          spacing: spacing,
          runSpacing: spacing,
          children: [
            for (final child in children)
              SizedBox(width: itemWidth, child: child),
          ],
        );
      },
    );
  }
}

/// Icon+label pill used in selection-mode bottom bars, with the active theme's
/// disabled tones and room for a two-line label.
class SelectionActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  /// Fills the button as the destructive action, such as delete.
  final bool destructive;

  const SelectionActionButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.destructive = false,
  });

  @override
  Widget build(BuildContext context) {
    return AppActionButton(
      outlined: !destructive,
      tonal: !destructive,
      isDestructive: destructive,
      onPressed: onPressed,
      icon: Icon(icon),
      label: Text(label, maxLines: 2, overflow: TextOverflow.ellipsis),
      fontSize: 15,
    );
  }
}
