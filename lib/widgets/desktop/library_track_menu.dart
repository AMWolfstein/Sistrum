import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';

/// Context menu groups for library files (downloaded or scanned), acting on
/// [items]: the selection the menu was opened on.
///
/// The playback and playlist actions match [LibrarySelectionPlaybackActions];
/// the screen passes its own selection-bar callbacks for the rest, and omits
/// those it does not offer. Sistrum Link adds "Send to (phone)" and "Save to
/// this computer" while a phone is connected. Actions that finish with the
/// menu (play, queue, playlist, transfers) call [onDone] to end the
/// selection; the bulk actions keep it and end it themselves.
List<List<ContextMenuAction>> libraryTrackMenuGroups(
  BuildContext context,
  WidgetRef ref, {
  required List<UnifiedLibraryItem> items,
  VoidCallback? onPlay,
  VoidCallback? onDone,
  VoidCallback? onDelete,
}) {
  if (items.isEmpty) return const [];
  final l10n = context.l10n;
  final root = Navigator.of(context, rootNavigator: true).context;
  final count = items.length;

  Future<void> enqueue({required bool playNext}) async {
    await ref
        .read(musicPlayerControllerProvider)
        .enqueueLibraryItems(items, playNext: playNext);
    onDone?.call();
  }

  return [
    [
      if (onPlay != null)
        ContextMenuAction(
          icon: Icons.play_arrow_rounded,
          label: l10n.trackMetadataPlay,
          onSelected: () {
            onDone?.call();
            onPlay();
          },
        ),
      ContextMenuAction(
        icon: Icons.playlist_play,
        label: l10n.trackPlayNext,
        onSelected: () => enqueue(playNext: true),
      ),
      ContextMenuAction(
        icon: Icons.queue_music,
        label: l10n.trackAddToQueue,
        onSelected: () => enqueue(playNext: false),
      ),
    ],
    [
      ContextMenuAction(
        icon: Icons.playlist_add,
        label: l10n.collectionAddToPlaylist,
        onSelected: () {
          final tracks = [for (final item in items) item.toTrack()];
          onDone?.call();
          showAddTracksToPlaylistSheet(root, ref, tracks);
        },
      ),
    ],
    [
      if (onDelete != null)
        ContextMenuAction(
          icon: Icons.delete_outline,
          label: l10n.selectionDeleteTracksCount(count),
          destructive: true,
          onSelected: onDelete,
        ),
    ],
  ];
}
