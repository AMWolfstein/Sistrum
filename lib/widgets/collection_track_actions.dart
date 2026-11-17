import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart' show ShareParams, SharePlus, XFile;
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/collection_track_files_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/services/m3u_playlist_service.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/destructive_selection_button.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/track_card.dart';

/// Play and delete for the selected songs of a saved list. The screen mixes
/// this in so the flow keeps the screen's context: the selection bar leaves
/// the tree while a dialog is open.
mixin CollectionTrackActions<T extends ConsumerStatefulWidget>
    on ConsumerState<T> {
  bool _trackActionInProgress = false;

  bool get trackActionInProgress => _trackActionInProgress;

  /// Leaves selection mode after a play or delete that went through.
  void leaveSelectionAfterTrackAction();

  /// The playable files of [tracks]; call from build.
  List<PlayableMedia> selectedMedia(List<Track> tracks) => tracks.isEmpty
      ? const []
      : ref
                .watch(
                  collectionTrackFilesProvider(
                    CollectionTrackFilesRequest(tracks),
                  ),
                )
                .value ??
            const [];

  Future<void> playSelectedMedia(
    List<PlayableMedia> media, {
    bool? playNext,
  }) async {
    if (media.isEmpty || _trackActionInProgress) return;
    setState(() => _trackActionInProgress = true);
    try {
      if (playNext == null) {
        await ref
            .read(playbackProvider.notifier)
            .playMediaQueue(
              media,
              startIndex: 0,
              externalPath: media.first.source,
            );
      } else {
        await ref
            .read(musicPlayerControllerProvider)
            .enqueueMedia(media, playNext: playNext);
      }
      if (mounted) leaveSelectionAfterTrackAction();
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    } finally {
      if (mounted) setState(() => _trackActionInProgress = false);
    }
  }

  Future<void> deleteSelectedMedia(List<PlayableMedia> media) async {
    final paths = {
      for (final item in media)
        if (!isCueVirtualPath(item.source)) item.source,
    }.toList();
    if (paths.isEmpty || _trackActionInProgress) return;
    final container = ProviderScope.containerOf(context, listen: false);
    setState(() => _trackActionInProgress = true);
    try {
      await confirmAndDeleteTracks(
        context: context,
        ids: paths,
        message: context.l10n.collectionDeleteFilesMessage(paths.length),
        deleteItem: deleteFile,
        persistDeletedItems: (deleted) =>
            removeDeletedLibraryFileEntries(container, deleted),
        onExitSelectionMode: () {
          if (mounted) leaveSelectionAfterTrackAction();
        },
      );
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    } finally {
      if (mounted) setState(() => _trackActionInProgress = false);
    }
  }
}

/// The selection bar actions of a saved list: play, play next, queue, add to
/// playlist, remove from the list and delete files. [media] are the selected
/// songs' files.
class CollectionTrackSelectionActions extends StatelessWidget {
  const CollectionTrackSelectionActions({
    super.key,
    required this.media,
    required this.selectedCount,
    required this.showPlayer,
    required this.inProgress,
    required this.onPlay,
    required this.onAddToPlaylist,
    required this.removeLabel,
    required this.onRemove,
    required this.onDelete,
  });

  final List<PlayableMedia> media;
  final int selectedCount;
  final bool showPlayer;
  final bool inProgress;
  final void Function(List<PlayableMedia> media, {bool? playNext}) onPlay;
  final VoidCallback onAddToPlaylist;

  /// The remove label without its count.
  final String removeLabel;
  final VoidCallback onRemove;
  final void Function(List<PlayableMedia> media) onDelete;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final l10n = context.l10n;
    final hasSelection = selectedCount > 0;
    final localCount = media.map((item) => item.source).toSet().length;
    final canPlay = media.isNotEmpty && !inProgress;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SelectionActionGrid(
          children: [
            SelectionActionButton(
              icon: Icons.play_arrow,
              label: l10n.tooltipPlay,
              onPressed: canPlay ? () => onPlay(media) : null,
            ),
            if (showPlayer)
              for (final next in [true, false])
                SelectionActionButton(
                  icon: next ? Icons.playlist_play : Icons.queue_music,
                  label: next ? l10n.trackPlayNext : l10n.trackAddToQueue,
                  onPressed: canPlay
                      ? () => onPlay(media, playNext: next)
                      : null,
                ),
            SelectionActionButton(
              icon: Icons.playlist_add,
              label: '${l10n.collectionAddToPlaylist} ($selectedCount)',
              onPressed: hasSelection ? onAddToPlaylist : null,
            ),
            // Removing entries keeps the files, so it stays a plain action.
            SelectionActionButton(
              onPressed: hasSelection ? onRemove : null,
              icon: Icons.remove_circle_outline,
              label: hasSelection
                  ? '$removeLabel ($selectedCount)'
                  : removeLabel,
            ),
          ],
        ),
        if (localCount > 0) ...[
          const SizedBox(height: 8),
          DestructiveSelectionButton(
            count: inProgress ? 0 : localCount,
            colorScheme: colorScheme,
            label: l10n.selectionDeleteTracksCount(localCount),
            onPressed: () => onDelete(media),
          ),
        ],
      ],
    );
  }
}

/// Exports the tracks as an .m3u8 shared via the system sheet. Tracks without
/// a resolvable local file are skipped; SAF entries are written relative to
/// the download tree root.
Future<void> exportCollectionAsM3u8(
  BuildContext context,
  WidgetRef ref,
  String title,
  List<Track> tracks,
) async {
  final l10n = context.l10n;
  final messenger = ScaffoldMessenger.of(context);
  try {
    final paths = await ref
        .read(playbackProvider.notifier)
        .resolveTrackFilePaths(tracks);

    final exportEntries = <M3uExportEntry>[];
    for (var i = 0; i < tracks.length; i++) {
      final path = paths[i];
      if (path == null || isCueVirtualPath(path)) continue;
      final entryPath = M3uPlaylistService.exportPathFor(path);
      if (entryPath == null) continue;
      exportEntries.add(M3uExportEntry(track: tracks[i], path: entryPath));
    }

    if (exportEntries.isEmpty) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.collectionExportM3uNone)),
      );
      return;
    }

    final file = await M3uPlaylistService.writeExportEntries(
      title,
      exportEntries,
    );
    messenger.showSnackBar(
      SnackBar(
        content: Text(
          l10n.collectionExportM3uDone(exportEntries.length, tracks.length),
        ),
      ),
    );
    await SharePlus.instance.share(
      ShareParams(files: [XFile(file.path)], text: title),
    );
  } catch (_) {
    messenger.showSnackBar(
      SnackBar(content: Text(l10n.collectionExportM3uFailed)),
    );
  }
}

/// Plays [tracks] from the start, or from a random song with shuffle on.
Future<void> playCollectionTracks(
  BuildContext context,
  WidgetRef ref,
  List<Track> tracks, {
  required bool shuffle,
}) async {
  if (tracks.isEmpty) return;
  try {
    await ref.read(musicPlayerControllerProvider).setShuffle(shuffle);
    await ref
        .read(playbackProvider.notifier)
        .playTrackList(
          tracks,
          startIndex: shuffle ? Random().nextInt(tracks.length) : 0,
        );
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.snackbarCannotOpenFile('$e'))),
    );
  }
}

/// Opens a saved track's metadata from its scanned copy. A saved track without
/// one has lost its file, so it says so.
Future<void> openCollectionTrackMetadata(
  BuildContext context,
  WidgetRef ref,
  Track track,
) async {
  final historyNotifier = ref.read(downloadHistoryProvider.notifier);

  var historyItem = await historyNotifier.getBySpotifyIdAsync(track.id);
  if (!context.mounted) return;

  if (historyItem == null && track.isrc != null && track.isrc!.isNotEmpty) {
    historyItem = await historyNotifier.getByIsrcAsync(track.isrc!);
    if (!context.mounted) return;
  }

  historyItem ??= await historyNotifier.findByTrackAndArtistAsync(
    track.name,
    track.artistName,
  );
  if (!context.mounted) return;

  if (historyItem != null) {
    final embeddedCoverPath =
        await DownloadedEmbeddedCoverResolver.resolveOrExtract(
          historyItem.filePath,
        );
    if (!context.mounted) return;
    await precacheMetadataBackdrop(
      context,
      embeddedCoverPath ?? historyItem.coverUrl,
    );
    if (!context.mounted) return;
    await Navigator.of(
      context,
    ).push(slidePageRoute<void>(page: TrackMetadataScreen(item: historyItem)));
    return;
  }

  final localItem = ref.read(settingsProvider).localLibraryEnabled
      ? await ref
            .read(localLibraryProvider.notifier)
            .findExistingAsync(
              isrc: track.isrc,
              trackName: track.name,
              artistName: track.artistName,
            )
      : null;
  if (!context.mounted) return;

  if (localItem != null) {
    await precacheMetadataBackdrop(context, localItem.coverPath);
    if (!context.mounted) return;
    await Navigator.of(context).push(
      slidePageRoute<void>(page: TrackMetadataScreen(localItem: localItem)),
    );
    return;
  }

  showAppSnackBar(context, content: Text(context.l10n.trackFileUnavailable));
}

/// The empty state of a saved-track list.
class CollectionEmptyState extends StatelessWidget {
  const CollectionEmptyState({
    super.key,
    required this.title,
    required this.subtitle,
  });

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.folder_open,
              size: 60,
              color: colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The song's own cover URL, unless it is a device content URI.
String? rawCollectionCoverUrl(Track track) {
  final rawCover = track.coverUrl?.trim();
  if (rawCover != null &&
      rawCover.isNotEmpty &&
      !rawCover.startsWith('content://')) {
    return rawCover;
  }
  return null;
}

/// The first cover URL among [tracks], or null.
String? firstRawCollectionCover(Iterable<Track> tracks) {
  for (final track in tracks) {
    final cover = rawCollectionCoverUrl(track);
    if (cover != null && cover.isNotEmpty) return cover;
  }
  return null;
}

/// The library lookup of the covers of [tracks] that have none of their own.
LocalLibraryCoverBatchRequest collectionCoverBatchRequest(
  Iterable<Track> tracks,
) => LocalLibraryCoverBatchRequest([
  for (final track in tracks)
    LocalLibraryCoverRequest(
      isrc: track.isrc?.trim(),
      trackName: track.name,
      artistName: track.artistName,
    ),
]);

/// A song's cover in a collection row: its own art, else the library's art for
/// it, else a placeholder.
class CollectionTrackCover extends ConsumerWidget {
  const CollectionTrackCover({
    super.key,
    required this.track,
    required this.size,
  });

  final Track track;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rawCoverUrl = rawCollectionCoverUrl(track);
    final localCoverUrl = rawCoverUrl == null
        ? ref
              .watch(
                localLibraryCoverProvider(
                  LocalLibraryCoverRequest(
                    isrc: track.isrc?.trim(),
                    trackName: track.name,
                    artistName: track.artistName,
                  ),
                ),
              )
              .maybeWhen(data: (cover) => cover, orElse: () => null)
        : null;
    final coverUrl = rawCoverUrl ?? localCoverUrl;
    return ClipRRect(
      borderRadius: context.tokens.borderRadiusThumb,
      child: coverUrl != null && coverUrl.isNotEmpty
          ? LocalOrNetworkCoverImage(
              url: coverUrl,
              width: size,
              height: size,
              networkCacheWidth: (size * 2).toInt(),
              fadeInDuration: const Duration(milliseconds: 180),
              fadeOutDuration: const Duration(milliseconds: 90),
              placeholder: (_) => TrackCoverPlaceholder(size: size),
            )
          : TrackCoverPlaceholder(size: size),
    );
  }
}
