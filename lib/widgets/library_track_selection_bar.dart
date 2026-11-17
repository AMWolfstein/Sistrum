import 'package:flutter/material.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/widgets/destructive_selection_button.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

/// Shared Library toolbar for playlist/queue actions and audio file operations.
class LibraryTrackSelectionBar extends StatelessWidget {
  const LibraryTrackSelectionBar({
    super.key,
    required this.selectedCount,
    required this.allSelected,
    required this.onClose,
    required this.onToggleSelectAll,
    required this.bottomPadding,
    required this.onReplayGain,
    required this.onDelete,
    this.playbackActions,
    this.albumDelete = false,
  });

  final int selectedCount;
  final bool allSelected;
  final VoidCallback onClose;
  final VoidCallback onToggleSelectAll;
  final double bottomPadding;
  final void Function({required bool remove}) onReplayGain;
  final VoidCallback onDelete;

  final Widget? playbackActions;

  /// downloaded album labels.
  final bool albumDelete;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final hasSelection = selectedCount > 0;
    final deleteLabel = hasSelection
        ? context.l10n.selectionDeleteTracksCount(selectedCount)
        : context.l10n.selectionSelectToDelete;
    Widget replayGain({required bool remove}) => SelectionActionButton(
      icon: remove ? Icons.remove_circle_outline : Icons.graphic_eq,
      label: remove
          ? context.l10n.selectionRemoveReplayGainCount(selectedCount)
          : context.l10n.selectionReplayGainCount(selectedCount),
      onPressed: hasSelection ? () => onReplayGain(remove: remove) : null,
    );
    return SelectionBottomBar(
      selectedCount: selectedCount,
      allSelected: allSelected,
      onClose: onClose,
      onToggleSelectAll: onToggleSelectAll,
      bottomPadding: bottomPadding,
      children: [
        ?playbackActions,
        if (playbackActions != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(context.l10n.trackEditMetadata),
          ),
        SelectionActionGrid(
          children: [replayGain(remove: false), replayGain(remove: true)],
        ),
        const SizedBox(height: 8),
        DestructiveSelectionButton(
          count: selectedCount,
          colorScheme: colorScheme,
          onPressed: onDelete,
          label: albumDelete ? null : deleteLabel,
        ),
      ],
    );
  }
}
