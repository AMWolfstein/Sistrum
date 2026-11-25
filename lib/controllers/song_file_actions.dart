import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:share_plus/share_plus.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/song_leftovers_provider.dart';
import 'package:sistrum/screens/track_metadata_edit_sheet.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/music_player_service.dart'
    show readPlaybackFileMetadataWithRetry;
import 'package:sistrum/services/replaygain_service.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/int_utils.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/string_utils.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';

final _log = AppLogger('SongFileActions');

/// Songs whose file is being deleted, by path, so a second tap cannot start
/// another deletion of the same file.
final _deleting = <String>{};

/// Plays the song's file on this device, as a local track.
Future<void> playSongFile(
  BuildContext context,
  WidgetRef ref,
  SongFile song,
) async {
  if (song.isCueVirtual) {
    showCueVirtualTrackSnackBar(context);
    return;
  }
  final item = song.localItem;
  final track = song.track;
  final coverPath = normalizeOptionalString(item?.coverPath) ?? '';
  try {
    await ref
        .read(playbackProvider.notifier)
        .playLocalPath(
          path: song.path!,
          title: track.name,
          artist: track.artistName,
          album: track.albumName,
          genre: track.genre ?? item?.genre,
          coverUrl: coverPath,
          duration: track.duration > 0
              ? Duration(seconds: track.duration)
              : null,
          bitDepth: item?.bitDepth,
          sampleRate: item?.sampleRate,
          bitrate: item?.bitrate,
          format: item?.format,
          explicit: track.isExplicit,
        );
  } catch (e) {
    if (context.mounted) showCannotOpenFileSnackBar(context, e);
  }
}

/// Puts the song next in the queue, or at its end. Needs a library row.
Future<void> enqueueSong(
  BuildContext context,
  WidgetRef ref,
  SongFile song, {
  required bool playNext,
}) async {
  final item = song.localItem;
  if (item == null) return;
  final controller = ref.read(musicPlayerControllerProvider);
  if (playNext) {
    await controller.playNextLocal(item);
  } else {
    await controller.addToQueueLocal(item);
  }
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        playNext
            ? context.l10n.snackbarPlayingNext
            : context.l10n.snackbarAddedToQueueGeneric,
      ),
    ),
  );
}

/// Adds [track] to Loved, or removes it when it is already loved, and confirms.
Future<void> toggleSongLoved(
  BuildContext context,
  WidgetRef ref,
  Track track,
) async {
  final l10n = context.l10n;
  final added = await ref
      .read(libraryCollectionsProvider.notifier)
      .toggleLoved(track);
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        added
            ? l10n.collectionAddedToLoved(track.name)
            : l10n.collectionRemovedFromLoved(track.name),
      ),
    ),
  );
}

void openSongAlbum(BuildContext context, Track track) {
  navigateToAlbum(
    context,
    albumName: track.albumName,
    artistName: track.albumArtist ?? track.artistName,
  );
}

Future<void> openSongArtist(BuildContext context, Track track) =>
    navigateToArtistCredits(context, artistNames: track.artistName);

/// Opens the metadata editor for the song's file. [onSaved] runs after a save.
Future<void> editSongMetadata(
  BuildContext context,
  SongFile song, {
  VoidCallback? onSaved,
}) async {
  final sourcePath = song.cleanPath;
  if (sourcePath == null) return;
  final track = song.track;
  Map<String, dynamic>? fileMetadata;
  try {
    final result = await readPlaybackFileMetadataWithRetry(sourcePath);
    if (result['error'] == null) {
      fileMetadata = result;
    }
  } catch (e) {
    if (sourcePath.startsWith('network://')) {
      if (context.mounted) {
        showAppSnackBar(context, content: Text(context.friendlyError(e)));
      }
      return;
    }
    debugPrint('readFileMetadata failed, using item data: $e');
  }

  if (!context.mounted) return;
  final initialDurationSeconds =
      readPositiveInt(fileMetadata?['duration']) ?? track.duration;
  final colorScheme = Theme.of(context).colorScheme;

  final saved = await showAppModalBottomSheet<bool>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    dialogSize: AppDialogSize.wide,
    builder: (sheetContext) => EditMetadataSheet(
      colorScheme: colorScheme,
      initialValues: _metadataEditorValues(track, fileMetadata),
      filePath: sourcePath,
      durationMs: initialDurationSeconds > 0
          ? initialDurationSeconds * 1000
          : 0,
    ),
  );

  if (saved == true && context.mounted) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(context.l10n.snackbarMetadataSaved)));
    onSaved?.call();
  }
}

Map<String, String> _metadataEditorValues(
  Track track,
  Map<String, dynamic>? metadata,
) {
  String val(String key, String? fallback) =>
      metadata?[key]?.toString() ?? fallback ?? '';

  return {
    'title': val('title', track.name),
    'artist': val('artist', track.artistName),
    'album': val('album', track.albumName),
    'album_artist': val('album_artist', track.albumArtist),
    'date': val('date', track.releaseDate),
    'track_number': val('track_number', track.trackNumber?.toString()),
    'total_tracks': val('total_tracks', track.totalTracks?.toString()),
    'disc_number': val('disc_number', track.discNumber?.toString()),
    'total_discs': val('total_discs', track.totalDiscs?.toString()),
    'genre': val('genre', track.genre),
    'isrc': val('isrc', track.isrc),
    'label': val('label', track.label),
    'copyright': val('copyright', track.copyright),
    'composer': val('composer', track.composer),
    'comment': val('comment', null),
    'album_type': val('album_type', track.albumType),
    'explicit': (parseExplicitFlag(metadata?['explicit']) ?? track.isExplicit)
        ? '1'
        : '0',
    'upc': val('upc', track.upc),
    'lyrics': val('lyrics', null),
  };
}

/// Writes ReplayGain into the file, or removes it. [onChanged] runs after a
/// successful change so the caller can reload the file's tags.
Future<void> updateSongReplayGain(
  BuildContext context,
  SongFile song, {
  bool remove = false,
  VoidCallback? onChanged,
}) async {
  final sourcePath = song.cleanPath;
  if (sourcePath == null || !song.fileExists) return;
  final messenger = ScaffoldMessenger.of(context);
  messenger.clearSnackBars();
  messenger.showSnackBar(
    SnackBar(
      content: Text(
        remove
            ? context.l10n.replayGainRemoving
            : context.l10n.trackReplayGainScanning,
      ),
      duration: const Duration(seconds: 30),
    ),
  );
  bool ok = false;
  try {
    ok = remove
        ? await ReplayGainService.removeFromFile(sourcePath)
        : await ReplayGainService.applyToFile(sourcePath);
  } catch (e) {
    _log.w('ReplayGain update failed: $e');
  }
  if (!context.mounted) return;
  if (ok) onChanged?.call();
  messenger.hideCurrentSnackBar();
  messenger.showSnackBar(
    SnackBar(
      content: Text(
        remove
            ? (ok
                  ? context.l10n.trackRemoveReplayGainSuccess
                  : context.l10n.trackRemoveReplayGainFailed)
            : (ok
                  ? context.l10n.trackReplayGainSuccess
                  : context.l10n.trackReplayGainFailed),
      ),
    ),
  );
}

/// Asks first, then removes the ReplayGain tags from the song's file.
Future<void> removeSongReplayGain(
  BuildContext context,
  SongFile song, {
  VoidCallback? onChanged,
}) async {
  if (!song.fileExists) return;
  final confirmed = await showAppConfirmDialog(
    context,
    title: context.l10n.trackRemoveReplayGain,
    message: context.l10n.replayGainRemoveConfirmMessage(1),
    confirmLabel: context.l10n.trackRemoveReplayGain,
  );
  if (!confirmed || !context.mounted) return;
  await updateSongReplayGain(context, song, remove: true, onChanged: onChanged);
}

void showCueVirtualTrackSnackBar(BuildContext context) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        'This CUE track is virtual. Use ${context.l10n.cueSplitButton} first.',
      ),
    ),
  );
}

Future<void> shareSongFile(BuildContext context, SongFile song) async {
  if (song.isCueVirtual) {
    showCueVirtualTrackSnackBar(context);
    return;
  }

  final sharePath = song.cleanPath;
  if (sharePath == null || !await fileExists(sharePath)) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.snackbarFileNotFound)),
      );
    }
    return;
  }

  final shareTitle = '${song.track.name} - ${song.track.artistName}';

  // For SAF content URIs, use native share intent directly (zero-copy).
  if (isContentUri(sharePath)) {
    try {
      await PlatformBridge.shareContentUri(sharePath, title: shareTitle);
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              context.l10n.snackbarCannotOpenFile('Failed to share file'),
            ),
          ),
        );
      }
    }
    return;
  }

  await SharePlus.instance.share(
    ShareParams(files: [XFile(sharePath)], text: shareTitle),
  );
}

/// Confirms, then deletes the song's file and its lyrics files. Removes the
/// library row, so playback drops the song through the deletion events.
/// [onDeleted] runs after the file is gone.
Future<void> deleteSongFromDevice(
  BuildContext context,
  SongFile song, {
  VoidCallback? onDeleted,
}) async {
  final filePath = song.cleanPath;
  if (filePath == null || _deleting.contains(filePath)) return;
  final l10n = context.l10n;
  final confirmed = await showAppConfirmDialog(
    context,
    title: l10n.trackDeleteConfirmTitle,
    message: l10n.trackDeleteConfirmMessage,
    confirmLabel: l10n.dialogDelete,
    destructive: true,
    filled: false,
  );
  if (!confirmed || !context.mounted || _deleting.contains(filePath)) return;
  _deleting.add(filePath);
  try {
    final container = ProviderScope.containerOf(context, listen: false);
    final cueItem = song.isCueVirtual ? song.localItem : null;
    if (cueItem != null) {
      await container
          .read(localLibraryProvider.notifier)
          .removeItem(cueItem.id);
    } else {
      if (!await container.read(songLeftoversProvider).delete(filePath)) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(context.l10n.snackbarDeleteTracksFailed(1))),
          );
        }
        return;
      }
      await removeDeletedLibraryFileEntries(container, [filePath]);
    }
    if (!context.mounted) return;
    onDeleted?.call();
  } finally {
    _deleting.remove(filePath);
  }
}

/// Whether the song playing from [item] is a file on this device.
bool isLocalPlayingSong(MediaItem item) {
  final source = item.extras?['source']?.toString() ?? '';
  return source.isNotEmpty && !source.startsWith('network://');
}

/// Deletes the file of the song playing from [item], with the same confirmation
/// as every other song. Playback reacts to the deletion itself.
Future<void> deletePlayingSongFromDevice(
  BuildContext context,
  WidgetRef ref,
  MediaItem item,
) async {
  if (!isLocalPlayingSong(item)) return;
  final track = await ref.read(playerCollectionTrackProvider(item).future);
  if (!context.mounted) return;
  final song = await resolveSongFile(
    ref,
    track: track,
    path: item.extras?['source']?.toString(),
  );
  if (!context.mounted) return;
  await deleteSongFromDevice(context, song);
}
