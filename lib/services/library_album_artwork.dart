import 'dart:async';
import 'dart:io';

import 'package:sqflite/sqflite.dart';
import 'package:sistrum/services/library_schema.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sistrum/utils/album_artwork_match.dart';
import 'package:sistrum/utils/file_access.dart';

/// What was found for one Library album, kept so a later run neither looks
/// it up again nor puts back artwork the user removed.
class AlbumArtworkRecord {
  const AlbumArtworkRecord({
    required this.albumKey,
    required this.matched,
    required this.checkedAt,
    this.providerId,
    this.albumId,
    this.coverUrl,
    this.motionUrl,
    this.coverFile,
    this.coverRemoved = false,
    this.motionSaved = false,
    this.motionRemoved = false,
  });

  final String albumKey;

  /// Whether a provider album matched. A record without a match stops the
  /// album from being looked up again.
  final bool matched;
  final DateTime checkedAt;
  final String? providerId;
  final String? albumId;
  final String? coverUrl;
  final String? motionUrl;

  /// The cover saved for the album's songs that have none. Clearing the
  /// cover cache deletes it; [coverUrl] fetches it again without a search.
  final String? coverFile;

  /// The user removed the cover as wrong; runs leave the album alone.
  final bool coverRemoved;
  final bool motionSaved;
  final bool motionRemoved;

  AlbumArtworkRecord copyWith({
    String? coverFile,
    bool clearCoverFile = false,
    bool? coverRemoved,
    bool? motionSaved,
    bool? motionRemoved,
  }) => AlbumArtworkRecord(
    albumKey: albumKey,
    matched: matched,
    checkedAt: checkedAt,
    providerId: providerId,
    albumId: albumId,
    coverUrl: coverUrl,
    motionUrl: motionUrl,
    coverFile: clearCoverFile ? null : coverFile ?? this.coverFile,
    coverRemoved: coverRemoved ?? this.coverRemoved,
    motionSaved: motionSaved ?? this.motionSaved,
    motionRemoved: motionRemoved ?? this.motionRemoved,
  );

  Map<String, Object?> toRow() => {
    'album_key': albumKey,
    'matched': matched ? 1 : 0,
    'provider_id': providerId,
    'album_id': albumId,
    'cover_url': coverUrl,
    'motion_url': motionUrl,
    'cover_file': coverFile,
    'cover_removed': coverRemoved ? 1 : 0,
    'motion_saved': motionSaved ? 1 : 0,
    'motion_removed': motionRemoved ? 1 : 0,
    'checked_at': checkedAt.millisecondsSinceEpoch,
  };

  static AlbumArtworkRecord fromRow(Map<String, Object?> row) =>
      AlbumArtworkRecord(
        albumKey: row['album_key'] as String,
        matched: row['matched'] == 1,
        checkedAt: DateTime.fromMillisecondsSinceEpoch(
          (row['checked_at'] as num?)?.toInt() ?? 0,
        ),
        providerId: row['provider_id'] as String?,
        albumId: row['album_id'] as String?,
        coverUrl: row['cover_url'] as String?,
        motionUrl: row['motion_url'] as String?,
        coverFile: row['cover_file'] as String?,
        coverRemoved: row['cover_removed'] == 1,
        motionSaved: row['motion_saved'] == 1,
        motionRemoved: row['motion_removed'] == 1,
      );
}

class LibraryArtworkAlbum {
  const LibraryArtworkAlbum({
    required this.album,
    required this.hasCover,
    required this.artists,
  });

  final ArtworkAlbum album;

  /// Whether any of its songs has a cover, its own or a found one.
  final bool hasCover;

  /// Each song artist on the album. Players find an animated cover by the
  /// album and the playing song's artist.
  final List<String> artists;
}

/// Artwork found online for Library albums, kept in the app and never
/// written to the music files.
///
/// Covers are saved beside the scan's covers and set on the album's songs
/// that have no cover, so every screen, the player and Link show them as
/// they show embedded art. A scan clears a song's cover when the file has
/// none, so [apply] runs after each scan to set them again.
class LibraryAlbumArtworkStore {
  LibraryAlbumArtworkStore({required Future<Database> Function() database})
    : _database = database;

  static const table = 'library_album_artwork';

  final Future<Database> Function() _database;
  Future<void>? _ready;

  Future<Database> _open() async {
    final db = await _database();
    await (_ready ??= db
        .execute('''
          CREATE TABLE IF NOT EXISTS $table (
            album_key TEXT PRIMARY KEY,
            matched INTEGER NOT NULL DEFAULT 0,
            provider_id TEXT,
            album_id TEXT,
            cover_url TEXT,
            motion_url TEXT,
            cover_file TEXT,
            cover_removed INTEGER NOT NULL DEFAULT 0,
            motion_saved INTEGER NOT NULL DEFAULT 0,
            motion_removed INTEGER NOT NULL DEFAULT 0,
            checked_at INTEGER NOT NULL DEFAULT 0
          )''')
        .catchError((Object error) {
          _ready = null;
          throw error;
        }));
    return db;
  }

  /// The visible local albums, or only the one with [albumKey]. Network
  /// songs keep the covers their server gives them.
  Future<List<LibraryArtworkAlbum>> albums({String? albumKey}) async {
    final db = await _open();
    final rows = await db.rawQuery(
      'SELECT album_key, MAX(album_name) AS album_name, '
      "MAX(COALESCE(NULLIF(TRIM(album_artist), ''), artist_name)) AS artist, "
      'COUNT(*) AS tracks, '
      "MIN(NULLIF(release_date, '')) AS release_date, "
      "MAX(CASE WHEN cover_path IS NOT NULL AND cover_path != '' "
      'THEN 1 ELSE 0 END) AS has_cover '
      'FROM ${LibrarySchema.visibleView} '
      "WHERE file_path NOT LIKE 'network://%' AND album_key IS NOT NULL "
      "AND TRIM(COALESCE(album_name, '')) != '' "
      '${albumKey == null ? '' : 'AND album_key = ? '}'
      'GROUP BY album_key',
      [?albumKey],
    );
    final artists = <String, List<String>>{};
    for (final row in await db.rawQuery(
      'SELECT DISTINCT album_key, artist_name '
      'FROM ${LibrarySchema.visibleView} '
      "WHERE file_path NOT LIKE 'network://%' AND album_key IS NOT NULL "
      '${albumKey == null ? '' : 'AND album_key = ? '}',
      [?albumKey],
    )) {
      final artist = (row['artist_name'] as String? ?? '').trim();
      if (artist.isEmpty) continue;
      artists.putIfAbsent(row['album_key'] as String, () => []).add(artist);
    }
    final albums = <LibraryArtworkAlbum>[];
    for (final row in rows) {
      final name = (row['album_name'] as String? ?? '').trim();
      final artist = (row['artist'] as String? ?? '').trim();
      if (_unknown.contains(artworkMatchText(name)) ||
          _unknown.contains(artworkMatchText(artist))) {
        continue;
      }
      albums.add(
        LibraryArtworkAlbum(
          album: ArtworkAlbum(
            key: row['album_key'] as String,
            album: name,
            artist: artist,
            trackCount: (row['tracks'] as num?)?.toInt() ?? 0,
            year: artworkYear(row['release_date'] as String?),
          ),
          hasCover: row['has_cover'] == 1,
          artists: artists[row['album_key']] ?? const [],
        ),
      );
    }
    return albums;
  }

  // Tags read from file names fall back to these; nothing can match them.
  static const _unknown = {'', 'unknown', 'unknown album', 'unknown artist'};

  Future<String?> albumKeyOf(String filePath) async {
    final db = await _open();
    final rows = await db.query(
      'library',
      columns: ['album_key'],
      where: 'file_path = ?',
      whereArgs: [filePath],
      limit: 1,
    );
    return rows.firstOrNull?['album_key'] as String?;
  }

  Future<Map<String, AlbumArtworkRecord>> records({String? albumKey}) async {
    final db = await _open();
    final rows = await db.query(
      table,
      where: albumKey == null ? null : 'album_key = ?',
      whereArgs: [?albumKey],
    );
    return {
      for (final row in rows)
        row['album_key'] as String: AlbumArtworkRecord.fromRow(row),
    };
  }

  /// Sets each found cover on its album's songs that have none. A cover
  /// whose file is gone is dropped from its record and from those songs.
  /// Returns the number of
  /// songs that changed.
  Future<int> apply() async {
    final db = await _open();
    final rows = await db.query(
      table,
      columns: ['album_key', 'cover_file'],
      where: 'cover_file IS NOT NULL AND cover_removed = 0',
    );
    final gone = <String, String>{};
    for (final row in rows) {
      final path = row['cover_file'] as String;
      if (!await File(path).exists()) gone[row['album_key'] as String] = path;
    }
    return sqlite.transactionWithBusyRetry(db, (txn) async {
      var changed = 0;
      for (final MapEntry(key: albumKey, value: path) in gone.entries) {
        await txn.update(
          table,
          {'cover_file': null},
          where: 'album_key = ?',
          whereArgs: [albumKey],
        );
        changed += await txn.update(
          'library',
          {'cover_path': null},
          where: 'cover_path = ?',
          whereArgs: [path],
        );
      }
      changed += await txn.rawUpdate(
        'UPDATE library SET cover_path = ('
        'SELECT a.cover_file FROM $table a '
        'WHERE a.album_key = library.album_key) '
        "WHERE (cover_path IS NULL OR cover_path = '') "
        "AND file_path NOT LIKE 'network://%' "
        'AND album_key IN (SELECT album_key FROM $table '
        'WHERE cover_file IS NOT NULL AND cover_removed = 0)',
      );
      return changed;
    });
  }

  /// Takes the found cover off [albumKey]'s songs and keeps runs from
  /// putting it back. Returns the record as it was.
  Future<AlbumArtworkRecord?> removeCover(String albumKey) async {
    final db = await _open();
    final record = (await records(albumKey: albumKey))[albumKey];
    if (record == null) return null;
    final file = record.coverFile;
    await sqlite.transactionWithBusyRetry(db, (txn) async {
      if (file != null) {
        await txn.update(
          'library',
          {'cover_path': null},
          where: 'cover_path = ?',
          whereArgs: [file],
        );
      }
      await txn.insert(
        table,
        record
            .copyWith(
              clearCoverFile: true,
              coverRemoved: true,
              motionSaved: false,
              motionRemoved: record.motionSaved || record.motionRemoved,
            )
            .toRow(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    });
    if (file != null) await deleteFileQuietly(File(file));
    return record;
  }
}
