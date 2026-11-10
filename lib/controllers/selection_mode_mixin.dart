import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mornye/widgets/selection_bottom_bar.dart';

/// Shared multi-select state for list screens: enter/exit selection mode,
/// toggle individual items, select all, and prune ids that fell out of the
/// current item list. Mix into a `State<T>` alongside the screen's own
/// per-item lookups. Used by the album, artist, playlist, downloaded album,
/// local album and library tracks folder screens; queue_tab keeps its own
/// selection state.
mixin SelectionModeMixin<T extends StatefulWidget> on State<T> {
  bool isSelectionMode = false;
  final Set<String> selectedIds = {};

  /// Whether the screen shows its selection bar.
  bool get showsSelectionBar => isSelectionMode;

  void enterSelectionMode(String itemId) {
    HapticFeedback.mediumImpact();
    setState(() {
      isSelectionMode = true;
      selectedIds.add(itemId);
    });
  }

  void exitSelectionMode() {
    setState(() {
      isSelectionMode = false;
      selectedIds.clear();
    });
  }

  void toggleSelection(String itemId) {
    setState(() {
      if (selectedIds.contains(itemId)) {
        selectedIds.remove(itemId);
        if (selectedIds.isEmpty) {
          isSelectionMode = false;
        }
      } else {
        selectedIds.add(itemId);
      }
    });
  }

  /// Selects every supplied item.
  void selectAll(Iterable<String> ids) {
    setState(() {
      selectedIds.addAll(ids);
      if (selectedIds.isNotEmpty) isSelectionMode = true;
    });
  }

  /// The selection bar over [ids], every selectable item in display order.
  /// Toggling with everything selected calls [onDeselectAll], which exits
  /// selection mode by default.
  Widget buildSelectionBar({
    required List<String> ids,
    required double bottomPadding,
    required List<Widget> children,
    VoidCallback? onDeselectAll,
    String? allSelectedLabel,
    String? tapToSelectLabel,
  }) {
    final allSelected = ids.isNotEmpty && selectedIds.length == ids.length;
    return SelectionBottomBar(
      selectedCount: selectedIds.length,
      allSelected: allSelected,
      onClose: exitSelectionMode,
      onToggleSelectAll: allSelected
          ? onDeselectAll ?? exitSelectionMode
          : () => selectAll(ids),
      bottomPadding: bottomPadding,
      allSelectedLabel: allSelectedLabel,
      tapToSelectLabel: tapToSelectLabel,
      children: children,
    );
  }

  /// Drops ids no longer present in [validIds]; call from build() with the
  /// current item ids. Exits selection mode next frame if that empties it.
  void pruneSelection(Set<String> validIds) {
    selectedIds.removeWhere((id) => !validIds.contains(id));
    if (selectedIds.isEmpty && isSelectionMode) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() => isSelectionMode = false);
      });
    }
  }
}
