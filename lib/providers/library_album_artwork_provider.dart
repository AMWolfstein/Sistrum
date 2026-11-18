import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/player_motion_artwork_provider.dart';
import 'package:sistrum/services/library_album_artwork.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/motion_artwork_store.dart';

final libraryAlbumArtworkStoreProvider = Provider(
  (ref) => LibraryAlbumArtworkStore(
    database: () => LibraryDatabase.instance.database,
  ),
);

/// The album of the Library song at a path, and what was found for it, for
/// the album's page to show a found cover and offer removing it.
final libraryAlbumArtworkForSongProvider = FutureProvider.autoDispose
    .family<({String albumKey, AlbumArtworkRecord? record})?, String>((
      ref,
      filePath,
    ) async {
      final store = ref.watch(libraryAlbumArtworkStoreProvider);
      final albumKey = await store.albumKeyOf(filePath);
      if (albumKey == null) return null;
      final records = await store.records(albumKey: albumKey);
      return (albumKey: albumKey, record: records[albumKey]);
    });

/// Removes artwork that was found for a Library album.
class LibraryAlbumArtworkNotifier extends Notifier<void> {
  @override
  void build() {}

  MotionArtworkStore get _motion => ref.read(motionArtworkStoreProvider);

  static List<MotionArtworkAlbum> _motionKeys(LibraryArtworkAlbum album) => [
    for (final artist in {...album.artists, album.album.artist})
      (album: album.album.album, artist: artist),
  ];

  /// Takes the found cover and animated cover off [albumKey] as wrong, and
  /// keeps later runs from bringing them back.
  Future<void> removeForAlbum(String albumKey) async {
    final store = ref.read(libraryAlbumArtworkStoreProvider);
    final album = (await store.albums(albumKey: albumKey)).firstOrNull;
    final record = await store.removeCover(albumKey);
    if (record == null) return;
    if (record.motionSaved && album != null) {
      for (final key in _motionKeys(album)) {
        await _motion.remove(key);
      }
    }
    await _changed(
      covers: record.coverFile != null,
      motion: record.motionSaved,
    );
  }

  Future<void> _changed({required bool covers, required bool motion}) async {
    if (!ref.mounted) return;
    ref.invalidate(libraryAlbumArtworkForSongProvider);
    if (motion) ref.invalidate(playerMotionArtworkProvider);
    if (covers) {
      await ref.read(localLibraryProvider.notifier).reloadFromStorage();
    }
  }
}

final libraryAlbumArtworkProvider =
    NotifierProvider<LibraryAlbumArtworkNotifier, void>(
      LibraryAlbumArtworkNotifier.new,
    );
