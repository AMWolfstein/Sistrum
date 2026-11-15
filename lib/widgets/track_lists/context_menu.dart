import 'package:flutter/material.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';

/// Cupertino glyph.
class ContextMenuAction {
  const ContextMenuAction({
    required this.icon,
    required this.label,
    required this.onSelected,
    this.destructive = false,
    this.checked = false,
  });

  final IconData icon;
  final String label;

  /// Runs after the menu has closed. Null shows the row disabled.
  final VoidCallback? onSelected;

  /// Draws the row in the error color. Put destructive rows in the last
  /// group, where the divider sets them apart.
  final bool destructive;

  /// Marks the active choice of a toggle, such as Favorite.
  final bool checked;
}

const double _mornyeMenuWidth = 280;

/// Shows [groups] as a context menu at [position], in global coordinates.
///
/// Opens the dense [MornyeContextMenu]. Empty groups are dropped and
/// the remaining ones are separated by dividers. The chosen action runs after
/// the menu closes, so it can open its own dialog or sheet. The future
/// completes after that action has started, with whether a row was chosen.
Future<bool> showContextMenu(
  BuildContext context, {
  required Offset position,
  required List<List<ContextMenuAction>> groups,
}) async {
  final visible = [
    for (final group in groups)
      if (group.isNotEmpty) group,
  ];
  if (visible.isEmpty) return false;
  final chosen = await showMornyeContextMenu<ContextMenuAction>(
    context: context,
    // The layout aligns the menu's trailing edge with the anchor's, so a
    // menu-wide anchor puts its leading edge at the pointer.
    anchor: Rect.fromLTWH(position.dx, position.dy - 8, _mornyeMenuWidth, 0),
    maxWidth: _mornyeMenuWidth,
    builder: (menuContext) => MornyeContextMenu(
      dense: true,
      groups: [
        for (final group in visible)
          [
            for (final action in group)
              MornyeMenuAction(
                icon: mornyeIconFor(action.icon),
                label: action.label,
                selected: action.checked,
                destructive: action.destructive,
                onPressed: action.onSelected == null
                    ? null
                    : () => Navigator.pop(menuContext, action),
              ),
          ],
      ],
    ),
  );
  chosen?.onSelected?.call();
  return chosen != null;
}

/// The center of [context]'s render box, where keyboard-opened menus appear.
Offset? contextMenuOriginOf(BuildContext context) {
  final box = context.findRenderObject();
  if (box is! RenderBox || !box.attached || !box.hasSize) return null;
  return box.localToGlobal(box.size.center(Offset.zero));
}

/// The bottom-left corner of [context]'s render box, where a menu opened
/// from a button appears.
Offset? contextMenuPositionBelow(BuildContext context) {
  final box = context.findRenderObject();
  if (box is! RenderBox || !box.attached || !box.hasSize) return null;
  return box.localToGlobal(box.size.bottomLeft(Offset.zero));
}
