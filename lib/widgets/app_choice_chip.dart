import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/mornye_theme.dart';

/// A single tonal selection inside a sheet, without a second glass layer.
class AppChoiceChip extends StatelessWidget {
  const AppChoiceChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onSelected,
    this.icon,
    this.count,
  });

  final Widget label;
  final bool selected;
  final ValueChanged<bool>? onSelected;
  final Widget? icon;
  final int? count;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final foreground = onSelected == null
        ? scheme.onSurfaceVariant
        : selected
        ? scheme.primary
        : scheme.onSurface;
    return Semantics(
      selected: selected,
      child: CupertinoButton(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        borderRadius: BorderRadius.circular(24),
        color: selected
            ? scheme.primary.withValues(alpha: 0.12)
            : MornyeTheme.controlFill(context),
        disabledColor: MornyeTheme.controlFill(context, enabled: false),
        onPressed: onSelected == null ? null : () => onSelected!(!selected),
        child: DefaultTextStyle(
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(
            color: foreground,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[icon!, const SizedBox(width: 8)],
              if (selected) ...[
                Icon(CupertinoIcons.check_mark, size: 18, color: foreground),
                const SizedBox(width: 8),
              ],
              Flexible(child: label),
              if (count != null) ...[const SizedBox(width: 6), Text('$count')],
            ],
          ),
        ),
      ),
    );
  }
}
