import 'package:flutter/material.dart';

/// One row of an [AppMenuButton].
class AppMenuItem<T extends Object> {
  const AppMenuItem({
    required this.value,
    required this.label,
    this.leading,
    this.selected,
    this.destructive = false,
  });

  final T value;
  final String label;
  final Widget? leading;

  /// Non-null for single-select menus: the row shows a check when true.
  final bool? selected;
  final bool destructive;
}

/// Overflow and picker menus as a popup menu.
class AppMenuButton<T extends Object> extends StatelessWidget {
  const AppMenuButton({
    super.key,
    required this.icon,
    required this.items,
    required this.onSelected,
    this.tooltip,
    this.offset = Offset.zero,
  });

  final Widget icon;
  final List<AppMenuItem<T>> items;
  final ValueChanged<T> onSelected;
  final String? tooltip;

  final Offset offset;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return PopupMenuButton<T>(
      icon: icon,
      tooltip: tooltip,
      offset: offset,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      onSelected: onSelected,
      itemBuilder: (context) => [
        for (final item in items)
          PopupMenuItem<T>(
            value: item.value,
            child: Row(
              children: [
                if (item.leading != null) ...[
                  IconTheme.merge(
                    data: IconThemeData(
                      size: 20,
                      color: item.destructive
                          ? colorScheme.error
                          : item.selected == true
                          ? colorScheme.primary
                          : colorScheme.onSurfaceVariant,
                    ),
                    child: item.leading!,
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: Text(
                    item.label,
                    style: TextStyle(
                      color: item.destructive ? colorScheme.error : null,
                      fontWeight: item.selected == true
                          ? FontWeight.w600
                          : null,
                    ),
                  ),
                ),
                if (item.selected == true)
                  Icon(Icons.check, size: 18, color: colorScheme.primary),
              ],
            ),
          ),
      ],
    );
  }
}
