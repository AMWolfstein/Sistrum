import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/destructive_selection_button.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

/// Shared Library toolbar for playlist/queue actions and local audio file
/// operations.
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

  /// Album pages keep delete the full-width button on desktop too, with the
  /// downloaded album labels.
  final bool albumDelete;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final inlineDelete = isDesktopPlatform && !albumDelete;
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
      colorScheme: colorScheme,
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
          children: [
            replayGain(remove: false),
            // Desktop keeps delete a toolbar button. It shares a row with
            // the other removal, delete first, so neither wraps onto a row
            // of its own.
            if (inlineDelete)
              Row(
                mainAxisSize: MainAxisSize.min,
                spacing: 8,
                children: [
                  SelectionActionButton(
                    icon: Icons.delete_outline,
                    label: deleteLabel,
                    onPressed: hasSelection ? onDelete : null,
                    colorScheme: colorScheme,
                    destructive: true,
                  ),
                  replayGain(remove: true),
                ],
              )
            else
              replayGain(remove: true),
          ],
        ),
        if (!inlineDelete) ...[
          const SizedBox(height: 8),
          DestructiveSelectionButton(
            count: selectedCount,
            onPressed: onDelete,
            label: albumDelete ? null : deleteLabel,
          ),
        ],
      ],
    );
  }
}
