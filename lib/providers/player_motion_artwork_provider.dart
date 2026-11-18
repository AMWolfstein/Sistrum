import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/motion_artwork_folder.dart';
import 'package:sistrum/services/motion_artwork_store.dart';

typedef PlayerArtworkAlbum = ({String album, String artist});

final motionArtworkStoreProvider = Provider((ref) => MotionArtworkStore());

final motionArtworkFolderProvider = Provider((ref) => MotionArtworkFolder());

/// An album and the song, or one of its songs, whose folder may hold the
/// album's `cover.mp4`.
typedef PlayerArtworkSong = ({String album, String artist, String source});

/// Player and local album pages read artwork saved by downloads or re-enrich.
/// Opening either must never search extensions or download more artwork.
final playerMotionArtworkProvider = FutureProvider.autoDispose
    .family<MotionArtwork?, PlayerArtworkAlbum>((ref, album) async {
      if (!ref.watch(settingsProvider.select((s) => s.motionArtworkEnabled))) {
        return null;
      }
      if (album.album.trim().isEmpty || album.artist.trim().isEmpty) {
        return null;
      }
      return ref.watch(motionArtworkStoreProvider).find(album);
    });

/// The artwork for a song: a `cover.mp4` in its folder first, since that
/// one belongs to exactly this album and is the user's own file, then the
/// artwork saved for the album.
final songMotionArtworkProvider = FutureProvider.autoDispose
    .family<MotionArtwork?, PlayerArtworkSong>((ref, song) async {
      if (!ref.watch(settingsProvider.select((s) => s.motionArtworkEnabled))) {
        return null;
      }
      final (beside, saved) = await (
        ref.watch(motionArtworkFolderProvider).find(song.source),
        ref.watch(
          playerMotionArtworkProvider((
            album: song.album,
            artist: song.artist,
          )).future,
        ),
      ).wait;
      return beside ?? saved;
    });
