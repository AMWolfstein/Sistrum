import 'dart:math';

import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/models/download_history.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/track_provider.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/utils/release_type_utils.dart';

/// Songs the library artist page lists under Popular, as the online page
/// shows them in one page. shortcut: the extension's full top list is not
/// read here, upgrade if the library page should list more.
const libraryPopularTrackCount = 5;

const _pageSize = 500;

/// A library artist's albums and singles: the albums the artist is the album
/// artist of, one entry per album key.
class LibraryArtistOverview {
  const LibraryArtistOverview(this.albums);

  final List<ArtistAlbum> albums;

  /// The newest album by release date. Undated albums never qualify.
  ArtistAlbum? get latestRelease {
    ArtistAlbum? latest;
    for (final album in albums) {
      if (album.releaseDate.trim().isEmpty) continue;
      if (latest == null ||
          album.releaseDate.compareTo(latest.releaseDate) > 0) {
        latest = album;
      }
    }
    return latest;
  }

  /// The artist image: the cover of [latestRelease].
  String? get imageCover => latestRelease?.coverUrl;
}

/// The albums and singles the artist is the album artist of.
Future<LibraryArtistOverview> loadLibraryArtistOverview(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
}) async {
  final rows = await _allRows(
    (cursor) => LibraryDatabase.instance.getQueueAlbumPageResult(
      _artistQuery(
        artist,
        credits: credits,
        includeLocal: includeLocal,
        role: LibraryArtistCredit.albumArtist,
        cursor: cursor,
      ),
    ),
  );
  final byKey = <String, List<Map<String, dynamic>>>{};
  for (final row in rows) {
    byKey.putIfAbsent(row['album_key'] as String? ?? '', () => []).add(row);
  }
  return LibraryArtistOverview([
    for (final copies in byKey.values) _libraryAlbum(copies),
  ]);
}

/// The tracks where the artist is a track artist but not the album artist.
Future<List<Track>> loadLibraryArtistFeaturedTracks(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
}) async {
  final rows = await _allRows(
    (cursor) => LibraryDatabase.instance.getQueueTrackPageResult(
      _artistQuery(
        artist,
        credits: credits,
        includeLocal: includeLocal,
        role: LibraryArtistCredit.featured,
        cursor: cursor,
      ),
    ),
  );
  return [for (final row in rows) ?_libraryItem(row)?.toTrack()];
}

/// Every song that credits the artist, in library order: the play-all list.
Future<List<Track>> loadLibraryArtistTracks(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
}) async {
  final songs = await _creditedSongs(
    artist,
    credits: credits,
    includeLocal: includeLocal,
  );
  return [for (final song in songs) song.toTrack()];
}

/// The artist's most-played songs, filled up with random songs of the artist
/// to [libraryPopularTrackCount]. Plays are all-time and matched by file path,
/// the key the statistics record; with [useStatistics] off, only random songs.
Future<List<Track>> loadLibraryArtistPopularTracks(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
  required bool useStatistics,
  Random? random,
}) async {
  final songs = await _creditedSongs(
    artist,
    credits: credits,
    includeLocal: includeLocal,
  );
  final plays = useStatistics && songs.isNotEmpty
      ? await ListeningStatisticsStore.instance.read(
          (db) => readPlaysForTracks(db, songs.map((song) => song.filePath)),
        )
      : const <String, int>{};
  final chosen =
      songs.where((song) => plays.containsKey(song.filePath)).toList()
        ..sort((a, b) {
          final byPlays = plays[b.filePath]!.compareTo(plays[a.filePath]!);
          return byPlays != 0 ? byPlays : a.filePath.compareTo(b.filePath);
        });
  final picked = chosen.take(libraryPopularTrackCount).toList();
  final fill = libraryPopularTrackCount - picked.length;
  if (fill > 0) {
    final pickedPaths = {for (final song in picked) song.filePath};
    final others = [
      for (final song in songs)
        if (!pickedPaths.contains(song.filePath)) song,
    ]..shuffle(random ?? Random());
    picked.addAll(others.take(fill));
  }
  return [for (final song in picked) song.toTrack()];
}

/// The songs of the album with [albumKey], downloaded and scanned alike, in
/// album order. Items keep their files, for the offline row actions.
Future<List<UnifiedLibraryItem>> loadLibraryAlbumItems(
  String albumKey, {
  required bool includeLocal,
}) async {
  final downloaded = await HistoryDatabase.instance.getAlbumTracksByKey(
    albumKey,
  );
  final scanned = includeLocal
      ? await LibraryDatabase.instance.getQueueLocalAlbumTracksByKey(albumKey)
      : const <Map<String, dynamic>>[];
  final items = [
    for (final row in downloaded)
      UnifiedLibraryItem.fromDownloadHistory(DownloadHistoryItem.fromJson(row)),
    for (final row in scanned)
      UnifiedLibraryItem.fromLocalLibrary(LocalLibraryItem.fromJson(row)),
  ];
  return AlbumTrackOrder(
    items,
    discNumber: (item) =>
        item.historyItem?.discNumber ?? item.localItem?.discNumber,
    trackNumber: (item) =>
        item.historyItem?.trackNumber ?? item.localItem?.trackNumber,
    trackName: (item) => item.trackName,
  ).tracks.toList(growable: false);
}

/// The songs that credit [artist] in any role, read page by page.
Future<List<UnifiedLibraryItem>> _creditedSongs(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
}) async {
  final rows = await _allRows(
    (cursor) => LibraryDatabase.instance.getQueueTrackPageResult(
      _artistQuery(
        artist,
        credits: credits,
        includeLocal: includeLocal,
        role: LibraryArtistCredit.credited,
        cursor: cursor,
      ),
    ),
  );
  return [for (final row in rows) ?_libraryItem(row)];
}

QueueLibraryDbQuery _artistQuery(
  String artist, {
  required TagCreditScope credits,
  required bool includeLocal,
  required LibraryArtistCredit role,
  QueueLibraryDbCursor? cursor,
}) => QueueLibraryDbQuery(
  albumArtist: artist,
  artistCredit: role,
  includeSingleTrackAlbums: true,
  includeLocal: includeLocal,
  limit: _pageSize,
  cursor: cursor,
  tagCredits: credits,
);

/// Every row of a paged read, following the seek cursor until a short page.
Future<List<Map<String, dynamic>>> _allRows(
  Future<QueueLibraryDbPage> Function(QueueLibraryDbCursor? cursor) readPage,
) async {
  final rows = <Map<String, dynamic>>[];
  QueueLibraryDbCursor? cursor;
  while (true) {
    final page = await readPage(cursor);
    rows.addAll(page.rows);
    cursor = page.nextCursor;
    if (page.rows.length < _pageSize || cursor == null) return rows;
  }
}

/// A library row decoded as the item it was read from; null for a source the
/// queue does not produce.
UnifiedLibraryItem? _libraryItem(Map<String, dynamic> row) {
  final json = Map<String, dynamic>.from(row['item'] as Map);
  return switch (row['source']) {
    'local' => UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem.fromJson(json),
    ),
    'downloaded' => UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem.fromJson(json),
    ),
    _ => null,
  };
}

/// One album from its copies: a downloaded and a scanned copy of the same key
/// are one album. Its size and kind come from the track total tag, not the
/// tracks owned; a missing tag gives no count.
ArtistAlbum _libraryAlbum(List<Map<String, dynamic>> copies) {
  final first = copies.first;
  final name = first['album_name'] as String? ?? '';
  final totalTracks = copies
      .map((row) => (row['total_tracks'] as num?)?.toInt() ?? 0)
      .fold<int>(0, max);
  final cover = copies
      .expand((row) => [row['cover_path'], row['cover_url']])
      .whereType<String>()
      .where((cover) => cover.isNotEmpty)
      .firstOrNull;
  return ArtistAlbum(
    id: first['album_key'] as String? ?? '',
    name: name,
    releaseDate: copies
        .map((row) => row['sort_release'] as String? ?? '')
        .reduce((a, b) => a.compareTo(b) >= 0 ? a : b),
    totalTracks: totalTracks,
    coverUrl: cover,
    albumType: libraryReleaseKind(
      albumName: name,
      totalTracks: totalTracks,
    ).name,
    artists: first['artist_name'] as String? ?? '',
  );
}
