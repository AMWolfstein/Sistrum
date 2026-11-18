import 'dart:convert';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:sistrum/models/library_collections.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/services/library_collections_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_media_identity.dart';

/// Loved songs for the media controls while the app shell is closed, such as
/// when a car starts playback. It reads and writes the same Loved collection
/// with the same Library identity as the shell, which takes over once open.
class BackgroundFavorites {
  BackgroundFavorites({required this.library, required this.collections});

  final LibraryDatabase library;
  final LibraryCollectionsDatabase collections;

  Future<Track> _track(MediaItem item) async => collectionTrackForMedia(
    item,
    await libraryItemForMedia(
      id: item.id,
      source: item.extras?['source']?.toString() ?? '',
      title: item.title,
      artist: item.artist ?? '',
      library: library,
    ),
  );

  Future<bool> isLoved(MediaItem item) async =>
      collections.containsLovedTrack(trackCollectionKey(await _track(item)));

  /// Loves [item], or stops loving it, and returns whether it is now loved.
  Future<bool> toggle(MediaItem item) async {
    final track = await _track(item);
    final key = trackCollectionKey(track);
    if (await collections.containsLovedTrack(key)) {
      await collections.deleteLovedEntry(key);
      return false;
    }
    await collections.upsertLovedEntry(
      trackKey: key,
      trackJson: jsonEncode(track.toJson()),
      addedAt: DateTime.now().toIso8601String(),
    );
    return true;
  }
}
