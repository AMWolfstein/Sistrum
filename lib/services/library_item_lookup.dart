import 'package:mornye/models/track.dart';
import 'package:mornye/models/unified_library_item.dart';
import 'package:mornye/providers/download_history_provider.dart';
import 'package:mornye/services/history_database.dart';
import 'package:mornye/services/library_database.dart';

/// The Library song each of [tracks] (a playlist's) plays from, matched as
/// playing them matches (`resolveTrackFiles`): a scanned Library row first
/// when [includeLocal], else a download. Tracks without one are left out.
Future<List<UnifiedLibraryItem>> libraryItemsFor(
  List<Track> tracks, {
  required bool includeLocal,
  LibraryDatabase? library,
  HistoryDatabase? history,
}) async {
  if (tracks.isEmpty) return const [];
  final historyDb = history ?? HistoryDatabase.instance;
  final local = includeLocal
      ? await (library ?? LibraryDatabase.instance).findExistingBatch([
          for (final track in tracks)
            LocalLibraryBatchLookupRequest(
              id: (track.source ?? '').toLowerCase() == 'local'
                  ? track.id
                  : null,
              isrc: track.isrc,
              trackName: track.name,
              artistName: track.artistName,
            ),
        ])
      : List<Map<String, dynamic>?>.filled(tracks.length, null);
  final matches = await historyDb.findExistingTracks([
    for (final track in tracks)
      HistoryLookupRequest(
        spotifyId: track.id,
        isrc: track.isrc,
        trackName: track.name,
        artistName: track.artistName,
      ),
  ]);
  final downloads = await historyDb.getByIds({
    for (var i = 0; i < tracks.length; i++)
      if (local[i] == null)
        if (matches[i]?['id'] case final String id) id,
  });
  return [
    for (var i = 0; i < tracks.length; i++)
      if (local[i] case final row?)
        UnifiedLibraryItem.fromLocalLibrary(LocalLibraryItem.fromJson(row))
      else if (downloads[matches[i]?['id']] case final row?)
        UnifiedLibraryItem.fromDownloadHistory(
          DownloadHistoryItem.fromJson(row),
        ),
  ];
}
