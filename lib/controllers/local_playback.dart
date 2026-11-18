import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';

/// Plays [track] from the local library when a matching file exists on disk.
///
/// Returns true when playback started (or when an error was surfaced to the
/// user), and false when no local copy is available.
Future<bool> playLocalIfAvailable(
  BuildContext context,
  WidgetRef ref,
  Track track,
) async {
  if (!ref.read(settingsProvider).localLibraryEnabled) return false;
  try {
    final isrc = track.isrc?.trim();
    final localItem = await ref
        .read(localLibraryProvider.notifier)
        .findExistingAsync(
          isrc: isrc,
          trackName: track.name,
          artistName: track.artistName,
        );
    if (localItem == null || !await fileExists(localItem.filePath)) {
      return false;
    }
    await ref
        .read(playbackProvider.notifier)
        .playLocalPath(
          path: localItem.filePath,
          title: localItem.trackName,
          artist: localItem.artistName,
          album: localItem.albumName,
          genre: localItem.genre ?? track.genre,
          coverUrl: localItem.coverPath ?? track.coverUrl ?? '',
        );
    return true;
  } catch (e) {
    if (context.mounted) showCannotOpenFileSnackBar(context, e);
    return true;
  }
}
