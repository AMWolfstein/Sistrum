import 'package:audio_service/audio_service.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/cover_store.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('PlayerTrackNavigation');

Future<void> openPlayingArtist(
  BuildContext context,
  WidgetRef ref,
  MediaItem mediaItem,
) async {
  await navigateToArtist(context, artistName: mediaItem.artist ?? '');
}

Future<void> openPlayingAlbum(
  BuildContext context,
  WidgetRef ref, {
  required MediaItem mediaItem,
}) async {
  final albumName = (mediaItem.album ?? '').trim();
  if (albumName.isEmpty) return;

  // Prefer the stored collection so this action opens the exact files the
  // user is currently playing.
  try {
    final album = await ref
        .read(musicPlayerControllerProvider)
        .localAlbumFor(mediaItem.id);
    if (!context.mounted) return;
    if (album != null) {
      final (:item, :tracks) = album;
      final albumArtist = (item.albumArtist ?? '').trim();
      pushViaPreferredNavigator(
        context,
        (_) => LocalAlbumScreen(
          albumName: item.albumName,
          artistName: albumArtist.isNotEmpty ? albumArtist : item.artistName,
          coverPath: item.coverPath,
          tracks: tracks,
        ),
      );
      return;
    }
  } catch (e) {
    _log.w('Failed to resolve local album: $e');
  }

  if (!context.mounted) return;
  navigateToAlbum(
    context,
    albumName: albumName,
    artistName: mediaItem.artist,
    coverPath: localCoverPath(mediaItem.artUri?.toString()),
  );
}
