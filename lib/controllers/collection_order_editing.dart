import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_collections.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/discard_changes_dialog.dart';

/// Drag-to-reorder for a stored track list: the draft order, the discard
/// prompt and the save. The screen says how its list is stored.
mixin CollectionOrderEditing<T extends ConsumerStatefulWidget>
    on ConsumerState<T> {
  List<CollectionTrackEntry>? orderDraft;
  List<String> _orderBefore = const [];
  bool savingOrder = false;
  bool draggingOrder = false;
  bool _confirmingDiscard = false;

  bool get editingOrder => orderDraft != null;

  bool get orderChanged =>
      orderDraft?.indexed.any((item) => item.$2.key != _orderBefore[item.$1]) ??
      false;

  /// Stores [orderedKeys] in order; false when the store kept the order.
  Future<bool> saveOrderKeys(List<String> orderedKeys);

  void editOrder(List<CollectionTrackEntry> entries) {
    setState(() {
      orderDraft = [...entries];
      _orderBefore = [for (final entry in entries) entry.key];
      draggingOrder = false;
    });
  }

  void setDraggingOrder(bool dragging) {
    if (mounted) setState(() => draggingOrder = dragging);
  }

  void moveDraftEntry(int oldIndex, int newIndex) {
    final draft = orderDraft;
    if (draft == null || savingOrder) return;
    setState(() {
      final next = [...draft];
      next.insert(newIndex, next.removeAt(oldIndex));
      orderDraft = next;
    });
  }

  Future<void> cancelOrder() async {
    if (savingOrder || _confirmingDiscard) return;
    _confirmingDiscard = true;
    try {
      if (orderChanged && !await showDiscardChangesDialog(context)) return;
      if (mounted) {
        setState(() {
          orderDraft = null;
          draggingOrder = false;
        });
      }
    } finally {
      _confirmingDiscard = false;
    }
  }

  Future<void> saveOrder() async {
    final draft = orderDraft;
    if (draft == null || savingOrder || draggingOrder) return;
    setState(() => savingOrder = true);
    try {
      final saved = await saveOrderKeys([for (final entry in draft) entry.key]);
      if (!mounted) return;
      setState(() => orderDraft = null);
      showAppSnackBar(
        context,
        content: Text(
          saved
              ? context.l10n.collectionOrderSaved
              : context.l10n.collectionOrderChanged,
        ),
      );
    } on Object {
      if (mounted) {
        showAppSnackBar(
          context,
          content: Text(context.l10n.collectionOrderSaveFailed),
        );
      }
    } finally {
      if (mounted) setState(() => savingOrder = false);
    }
  }
}
