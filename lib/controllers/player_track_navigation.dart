import 'package:audio_service/audio_service.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('PlayerTrackNavigation');

/// Opens the artist of the song playing as [mediaItem].
Future<void> openPlayingArtist(
  BuildContext context,
  WidgetRef ref,
  MediaItem mediaItem,
) async {
  final track = ref.read(playerCollectionTrackProvider(mediaItem)).value;
  // A downloaded track's source is the service its audio came from, not a
  // metadata catalog; the lookup uses the user's search provider instead.
  await navigateToArtistCredits(
    context,
    artistNames: mediaItem.artist ?? '',
    artistIds: track?.artistId,
  );
}

/// Opens the album of the song playing as [mediaItem] from [source].
Future<void> openPlayingAlbum(
  BuildContext context,
  WidgetRef ref, {
  required MediaItem mediaItem,
  required String source,
}) async {
  final albumName = (mediaItem.album ?? '').trim();
  if (albumName.isEmpty) return;

  // Prefer the stored collection so this action remains useful offline and
  // opens the exact files the user is currently playing.
  try {
    final historyItem = source.isEmpty
        ? null
        : await ref
              .read(downloadHistoryProvider.notifier)
              .getByFilePathAsync(source);
    if (!context.mounted) return;
    if (historyItem != null) {
      pushViaPreferredNavigator(
        context,
        (_) => AlbumScreen.library(
          albumKey: LibraryDatabase.albumKeyFor(
            historyItem.albumName,
            historyItem.albumArtist,
            historyItem.artistName,
          ),
        ),
      );
      return;
    }
  } catch (e) {
    _log.w('Failed to resolve downloaded album: $e');
  }

  try {
    // The row's album key still finds the album when every scanned song of it
    // is shadowed by a download.
    final item = await ref
        .read(musicPlayerControllerProvider)
        .localAlbumFor(mediaItem.id);
    if (!context.mounted) return;
    if (item != null) {
      pushViaPreferredNavigator(
        context,
        (_) => AlbumScreen.library(albumKey: item.albumKey),
      );
      return;
    }
  } catch (e) {
    _log.w('Failed to resolve local album: $e');
  }

  if (!context.mounted) return;
  await navigateToAlbum(
    context,
    albumName: albumName,
    artistName: mediaItem.artist,
    coverUrl: mediaItem.artUri?.toString(),
  );
}
