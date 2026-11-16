import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_row_mapper.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;

const _scope = TagCreditScope();

Future<void> _scanned(
  Database db, {
  required String id,
  required String path,
  String? genre,
}) async {
  final json = LocalLibraryItem(
    id: id,
    sourceId: 'music',
    trackName: 'Scanned $id',
    artistName: 'Artist',
    albumName: 'Album',
    filePath: path,
    scannedAt: DateTime.utc(2026, 9, 1),
    format: 'flac',
    genre: genre,
  ).toJson();
  await db.insert('library', LibraryRowMapper.encode(json));
  final batch = db.batch();
  sqlite.putPathKeysInBatch(batch, 'library_path_keys', id, path);
  await batch.commit(noResult: true);
}

Map<String, dynamic> _download(
  String id, {
  required String path,
  String? genre,
}) => {
  'id': id,
  'trackName': 'Downloaded $id',
  'artistName': 'Artist',
  'albumName': 'Album',
  'filePath': path,
  'service': 'example-provider',
  'downloadedAt': DateTime.utc(2026, 8, 1).toIso8601String(),
  'trackNumber': 1,
  'discNumber': 1,
  'totalDiscs': 1,
  'genre': genre,
  'quality': '320 kbps',
};

/// The library's genres as name key -> song count.
Future<Map<String, int>> _counts({
  TagCreditScope credits = _scope,
  bool includeLocal = true,
}) async {
  final genres = await loadLibraryGenres(
    credits: credits,
    includeLocal: includeLocal,
  );
  return {for (final genre in genres) genre.key: genre.trackCount};
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory root;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('library-genres-');
    sqfliteFfiInit();
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactoryFfi;
    messenger.setMockMethodCallHandler(paths, (_) async => root.path);
    addTearDown(() async {
      await LibraryDatabase.instance.close();
      await HistoryDatabase.instance.close();
      messenger.setMockMethodCallHandler(paths, null);
      databaseFactory = previousFactory;
      await root.delete(recursive: true);
    });
    final library = await LibraryDatabase.instance.database;
    await library.insert('library_sources', {
      'id': 'music',
      'path': '/Volumes/music',
      'display_name': 'music',
      'available': 1,
      'enabled': 1,
    });
  });

  test('a tag of several genres lists its song under each genre', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('rock-pop', path: '/d/rock-pop.mp3', genre: 'Rock; Pop'),
    ]);

    expect(await _counts(), {'pop': 1, 'rock': 1});
    final rock = await loadLibraryGenreItems(
      'rock',
      credits: _scope,
      includeLocal: true,
    );
    final pop = await loadLibraryGenreItems(
      'pop',
      credits: _scope,
      includeLocal: true,
    );
    expect(rock.map((item) => item.filePath), ['/d/rock-pop.mp3']);
    expect(pop.map((item) => item.filePath), ['/d/rock-pop.mp3']);
  });

  test('genres are sorted by name and carry their display name', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('a', path: '/d/a.mp3', genre: 'Zydeco'),
      _download('b', path: '/d/b.mp3', genre: 'Afrobeats; Blues'),
    ]);

    final genres = await loadLibraryGenres(credits: _scope, includeLocal: true);
    expect(genres.map((genre) => genre.key), ['afrobeats', 'blues', 'zydeco']);
    expect(genres.map((genre) => genre.name), ['Afrobeats', 'Blues', 'Zydeco']);
  });

  test('a custom Genre delimiter splits the tag', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('jazz', path: '/d/jazz.mp3', genre: 'Jazz | Soul'),
    ]);
    // Only the custom bar splits: the default ; and ، are not delimiters.
    const credits = TagCreditScope(
      rules: TagCreditRules(genreDelimiters: ['|']),
    );

    expect(await _counts(credits: credits), {'jazz': 1, 'soul': 1});
    expect(await _counts(), hasLength(1));
  });

  test('Keep together does not apply to genres', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('hh', path: '/d/hh.mp3', genre: 'Hip; Hop'),
    ]);
    const credits = TagCreditScope(
      rules: TagCreditRules(keepTogether: ['Hip; Hop']),
    );

    expect(await _counts(credits: credits), {'hip': 1, 'hop': 1});
  });

  test('empty genres, blank names and delimiters alone are skipped', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('none', path: '/d/none.mp3'),
      _download('blank', path: '/d/blank.mp3', genre: '   '),
      _download('bare', path: '/d/bare.mp3', genre: ' ; ; '),
    ]);

    expect(await _counts(), isEmpty);
  });

  test('counts are songs per genre, and one key matches any case', () async {
    await HistoryDatabase.instance.upsertBatch([
      _download('r1', path: '/d/r1.mp3', genre: 'Rock'),
      _download('r2', path: '/d/r2.mp3', genre: 'Rock'),
      _download('r3', path: '/d/r3.mp3', genre: 'Rock; Pop'),
      _download('r4', path: '/d/r4.mp3', genre: 'rock'),
    ]);

    final genres = await loadLibraryGenres(credits: _scope, includeLocal: true);
    expect(
      {for (final genre in genres) genre.key: genre.trackCount},
      {'pop': 1, 'rock': 4},
    );
    expect(genres.last.name, 'Rock');
  });

  test('with the local library off, scanned songs are not listed', () async {
    final library = await LibraryDatabase.instance.database;
    await _scanned(
      library,
      id: 'scan-1',
      path: '/music/scan-1.flac',
      genre: 'Jazz',
    );
    await HistoryDatabase.instance.upsertBatch([
      _download('dl-1', path: '/d/dl-1.mp3', genre: 'Jazz'),
    ]);

    expect(await _counts(includeLocal: false), {'jazz': 1});
    expect(await _counts(), {'jazz': 2});
    final songs = await loadLibraryGenreItems(
      'jazz',
      credits: _scope,
      includeLocal: false,
    );
    expect(songs.map((item) => item.filePath), ['/d/dl-1.mp3']);
  });

  test('a downloaded and a scanned song both count', () async {
    final library = await LibraryDatabase.instance.database;
    await _scanned(
      library,
      id: 'soul-s',
      path: '/music/soul.flac',
      genre: 'Soul',
    );
    await HistoryDatabase.instance.upsertBatch([
      _download('soul-d', path: '/d/soul.mp3', genre: 'Soul'),
    ]);

    expect(await _counts(), {'soul': 2});
    final songs = await loadLibraryGenreItems(
      'soul',
      credits: _scope,
      includeLocal: true,
    );
    expect(songs.map((item) => item.filePath).toSet(), {
      '/music/soul.flac',
      '/d/soul.mp3',
    });
  });

  test('a file that is downloaded and scanned is listed once', () async {
    final library = await LibraryDatabase.instance.database;
    await _scanned(
      library,
      id: 'folk-s',
      path: '/music/folk.flac',
      genre: 'Folk',
    );
    await HistoryDatabase.instance.upsertBatch([
      _download('folk-d', path: '/music/folk.flac', genre: 'Folk'),
    ]);

    expect(await _counts(), {'folk': 1});
    final songs = await loadLibraryGenreItems(
      'folk',
      credits: _scope,
      includeLocal: true,
    );
    expect(songs.map((item) => item.filePath), ['/music/folk.flac']);
  });
}
