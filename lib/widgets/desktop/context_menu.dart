import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart' show kPrimaryButton;
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';

/// One row of a desktop context menu. Use `Icons.*`; Mornye swaps in the
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

/// Mornye menus are as wide as the iOS popover, minus its touch padding.
const double _mornyeMenuWidth = 280;

/// Shows [groups] as a context menu at [position], in global coordinates.
///
/// Opens the dense [MornyeContextMenu]. Empty groups are dropped. The chosen
/// action runs once the menu has been dismissed, so it can open its own
/// dialog or sheet. The future completes after that action has started, with
/// whether a row was chosen.
///
/// The Mornye menu opens out of [origin], the control that asked for it,
/// or else out of [position].
Future<bool> showContextMenu(
  BuildContext context, {
  required Offset position,
  required List<List<ContextMenuAction>> groups,
  Offset? origin,
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
    // That anchor is as wide as the menu, so its center is not the control.
    origin: origin ?? position,
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

/// Opens [onContextMenu] on desktop on a right-click, a Control-click on
/// macOS, or Shift+F10 and the Menu key while the item or its
/// [DesktopListScope] has focus. Phones and tablets get [child] unchanged;
/// their long press keeps the same actions.
class DesktopContextMenuRegion extends StatefulWidget {
  const DesktopContextMenuRegion({
    super.key,
    required this.onContextMenu,
    required this.child,
  });

  /// Opens the item's menu at a global position. Null turns the region off.
  final ValueChanged<Offset>? onContextMenu;
  final Widget child;

  @override
  State<DesktopContextMenuRegion> createState() =>
      _DesktopContextMenuRegionState();
}

class _DesktopContextMenuRegionState extends State<DesktopContextMenuRegion>
    implements DesktopContextMenuTarget {
  @override
  void openContextMenuFromKeyboard() {
    final origin = contextMenuOriginOf(context);
    if (origin != null) widget.onContextMenu?.call(origin);
  }

  void _handlePointerDown(PointerDownEvent event) {
    DesktopListScope.markCurrent(context, this);
    final controlClick =
        defaultTargetPlatform == TargetPlatform.macOS &&
        event.buttons == kPrimaryButton &&
        HardwareKeyboard.instance.isControlPressed;
    if (controlClick) widget.onContextMenu?.call(event.position);
  }

  @override
  Widget build(BuildContext context) {
    final onContextMenu = widget.onContextMenu;
    if ((!isDesktopPlatform && !isAndroidTv) || onContextMenu == null) {
      return widget.child;
    }
    return Focus(
      canRequestFocus: false,
      skipTraversal: true,
      onKeyEvent: (_, event) {
        if (!isContextMenuKey(event)) return KeyEventResult.ignored;
        openContextMenuFromKeyboard();
        return KeyEventResult.handled;
      },
      child: Listener(
        onPointerDown: _handlePointerDown,
        child: GestureDetector(
          onSecondaryTapUp: (details) => onContextMenu(details.globalPosition),
          child: widget.child,
        ),
      ),
    );
  }
}
