import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/theme/mornye_icons.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';

class TrackCollectionQuickActions extends ConsumerWidget {
  final Track track;
  final bool hasLocalPlaybackCandidate;

  /// A 32dp pointer target for desktop table rows.
  final bool dense;

  const TrackCollectionQuickActions({
    super.key,
    required this.track,
    this.hasLocalPlaybackCandidate = false,
    this.dense = false,
  });

  /// Track options: a bottom sheet (Material) or popover (Mornye) on phones
  /// and tablets, a context menu at [position] or under [context] on
  /// desktop.
  static void showTrackOptionsSheet(
    BuildContext context,
    WidgetRef ref,
    Track track, {
    bool hasLocalPlaybackCandidate = false,
    Offset? position,
  }) {
    if (isDesktopPlatform) {
      final origin =
          position ??
          contextMenuOriginOf(context) ??
          MediaQuery.sizeOf(context).center(Offset.zero);
      showContextMenu(
        context,
        position: origin,
        groups: trackOptionMenuGroups(
          context,
          ref,
          track,
          hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
        ),
      );
      return;
    }
    if (context.isMornye) {
      showMornyeContextMenu<void>(
        context: context,
        builder: (_) => _TrackOptionsSheet(
          track: track,
          hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
        ),
      );
      return;
    }
    showAppModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      isScrollControlled: true,
      builder: (_) => _TrackOptionsSheet(
        track: track,
        hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;

    return AppIconButton(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: AppIcon(
        context.isMornye ? CupertinoIcons.ellipsis : Icons.more_vert,
        color: colorScheme.onSurfaceVariant,
        size: 20,
      ),
      onPressed: () => showTrackOptionsSheet(
        context,
        ref,
        track,
        hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
        position: contextMenuPositionBelow(context),
      ),
      padding: EdgeInsets.zero,
      constraints: dense
          ? const BoxConstraints(minWidth: 32, minHeight: 32)
          : const BoxConstraints(minWidth: 48, minHeight: 48),
      style: dense
          ? IconButton.styleFrom(
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            )
          : null,
    );
  }
}

/// The track options as context menu groups: playback, collections,
/// navigation, then copying. Every item works on the local library.
List<List<ContextMenuAction>> trackOptionMenuGroups(
  BuildContext context,
  WidgetRef ref,
  Track track, {
  bool hasLocalPlaybackCandidate = false,
}) {
  final l10n = context.l10n;
  final root = Navigator.of(context, rootNavigator: true).context;
  final collections = ref.read(libraryCollectionsProvider);
  final isLoved = collections.isLoved(track);
  final isInWishlist = collections.isInWishlist(track);

  void notify(String message) {
    if (!root.mounted) return;
    ScaffoldMessenger.of(root).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> toggle({required bool wishlist}) async {
    final notifier = ref.read(libraryCollectionsProvider.notifier);
    final added = wishlist
        ? await notifier.toggleWishlist(track)
        : await notifier.toggleLoved(track);
    if (!root.mounted) return;
    final l10n = root.l10n;
    notify(
      wishlist
          ? (added
                ? l10n.collectionAddedToWishlist(track.name)
                : l10n.collectionRemovedFromWishlist(track.name))
          : (added
                ? l10n.collectionAddedToLoved(track.name)
                : l10n.collectionRemovedFromLoved(track.name)),
    );
  }

  void copy(String text) {
    Clipboard.setData(ClipboardData(text: text));
    if (root.mounted) notify(root.l10n.trackCopiedToClipboard);
  }

  return [
    [
      if (hasLocalPlaybackCandidate)
        ContextMenuAction(
          icon: Icons.play_arrow_rounded,
          label: l10n.trackMetadataPlay,
          onSelected: () async {
            final played = await playLocalIfAvailable(root, ref, track);
            if (!played && root.mounted) {
              notify(root.l10n.snackbarFileNotFound);
            }
          },
        ),
    ],
    [
      ContextMenuAction(
        icon: isLoved ? Icons.favorite : Icons.favorite_border,
        label: isLoved
            ? l10n.trackOptionRemoveFromLoved
            : l10n.trackOptionAddToLoved,
        onSelected: () => toggle(wishlist: false),
      ),
      ContextMenuAction(
        icon: isInWishlist
            ? Icons.playlist_add_check_circle
            : Icons.add_circle_outline,
        label: isInWishlist
            ? l10n.trackOptionRemoveFromWishlist
            : l10n.trackOptionAddToWishlist,
        onSelected: () => toggle(wishlist: true),
      ),
      ContextMenuAction(
        icon: Icons.playlist_add,
        label: l10n.collectionAddToPlaylist,
        onSelected: () => showAddTrackToPlaylistSheet(root, ref, track),
      ),
    ],
    [
      ContextMenuAction(
        icon: Icons.album_outlined,
        label: l10n.homeGoToAlbum,
        onSelected: () => navigateToAlbum(
          root,
          albumName: track.albumName,
          artistName: track.artistName,
        ),
      ),
    ],
    [
      ContextMenuAction(
        icon: Icons.content_copy_rounded,
        label: l10n.trackOptionCopyTrackName,
        onSelected: () => copy(track.name),
      ),
      ContextMenuAction(
        icon: Icons.person_outline_rounded,
        label: l10n.trackOptionCopyArtist,
        onSelected: () => copy(track.artistName),
      ),
      ContextMenuAction(
        icon: Icons.copy_all_rounded,
        label: l10n.trackOptionCopyTrackAndArtist,
        onSelected: () => copy('${track.name} - ${track.artistName}'),
      ),
    ],
  ];
}

/// The track options on phones and tablets, in the shell's sheet style.
class _TrackOptionsSheet extends ConsumerWidget {
  const _TrackOptionsSheet({
    required this.track,
    required this.hasLocalPlaybackCandidate,
  });

  final Track track;
  final bool hasLocalPlaybackCandidate;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final groups = trackOptionMenuGroups(
      context,
      ref,
      track,
      hasLocalPlaybackCandidate: hasLocalPlaybackCandidate,
    );
    if (context.isMornye) {
      return MornyeContextMenu(
        groups: [
          for (final group in groups)
            [
              for (final action in group)
                MornyeMenuAction(
                  icon: mornyeIconFor(action.icon),
                  label: action.label,
                  onPressed: () {
                    Navigator.of(context).pop();
                    action.onSelected?.call();
                  },
                ),
            ],
        ],
      );
    }
    final colorScheme = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.82,
        ),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const AppSheetHandle(),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                child: Row(
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        color: colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: AppIcon(
                        Icons.music_note,
                        color: colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            track.name,
                            style: Theme.of(context).textTheme.titleMedium
                                ?.copyWith(fontWeight: FontWeight.w600),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          ClickableArtistName(artistName: track.artistName),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              for (final group in groups) ...[
                const Divider(height: 1),
                for (final action in group)
                  ListTile(
                    leading: AppIcon(action.icon),
                    title: Text(action.label),
                    onTap: action.onSelected == null
                        ? null
                        : () {
                            Navigator.of(context).pop();
                            action.onSelected!();
                          },
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
