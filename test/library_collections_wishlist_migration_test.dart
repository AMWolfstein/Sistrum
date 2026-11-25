import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:sistrum/services/library_collections_database.dart';

/// The v3 collections schema, with the wishlist table this migration drops.
Future<void> _createV3(Database db, int version) async {
  await db.execute(
    'CREATE TABLE wishlist_tracks(track_key TEXT PRIMARY KEY,track_json TEXT NOT NULL,added_at TEXT NOT NULL)',
  );
  await db.execute(
    'CREATE INDEX idx_wishlist_tracks_added_at ON wishlist_tracks(added_at DESC)',
  );
  await db.execute(
    'CREATE TABLE loved_tracks(track_key TEXT PRIMARY KEY,track_json TEXT NOT NULL,added_at TEXT NOT NULL)',
  );
  await db.execute(
    'CREATE TABLE favorite_artists(artist_key TEXT PRIMARY KEY,artist_json TEXT NOT NULL,added_at TEXT NOT NULL)',
  );
  await db.execute(
    'CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,cover_image_path TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)',
  );
  await db.execute(
    'CREATE TABLE playlist_tracks(playlist_id TEXT NOT NULL,track_key TEXT NOT NULL,track_json TEXT NOT NULL,added_at TEXT NOT NULL,PRIMARY KEY(playlist_id,track_key),FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)',
  );
}

Future<List<String>> _tables(Database db) async => [
  for (final row in await db.rawQuery(
    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
  ))
    row['name'] as String,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory root;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    sqfliteFfiInit();
    root = await Directory.systemTemp.createTemp('collections-v4-');
  });
  tearDown(() => root.delete(recursive: true));

  test(
    'v3 to v4 drops only the wishlist table and keeps loved and playlists',
    () async {
      final path = '${root.path}/library_collections.db';
      final legacy = await databaseFactoryFfi.openDatabase(
        path,
        options: OpenDatabaseOptions(version: 3, onCreate: _createV3),
      );
      await legacy.insert('wishlist_tracks', {
        'track_key': 'example:wish',
        'track_json': '{}',
        'added_at': '2026-01-01',
      });
      await legacy.insert('loved_tracks', {
        'track_key': 'example:loved',
        'track_json': '{}',
        'added_at': '2026-01-01',
      });
      await legacy.insert('playlists', {
        'id': 'p',
        'name': 'Playlist',
        'created_at': '2026-01-01',
        'updated_at': '2026-01-01',
      });
      await legacy.insert('playlist_tracks', {
        'playlist_id': 'p',
        'track_key': 'example:member',
        'track_json': '{}',
        'added_at': '2026-01-01',
      });
      await legacy.close();

      final db = await LibraryCollectionsDatabase.openWithFactory(
        databaseFactoryFfi,
        path,
      );
      addTearDown(db.close);

      expect(await db.rawQuery('PRAGMA user_version'), [
        {'user_version': 4},
      ]);
      expect(await _tables(db), isNot(contains('wishlist_tracks')));
      expect(
        await db.rawQuery(
          "SELECT name FROM sqlite_master WHERE name='idx_wishlist_tracks_added_at'",
        ),
        isEmpty,
      );
      expect(await db.query('loved_tracks'), [
        {
          'track_key': 'example:loved',
          'track_json': '{}',
          'added_at': '2026-01-01',
        },
      ]);
      expect(await db.query('playlists'), hasLength(1));
      expect(await db.query('playlist_tracks'), hasLength(1));
    },
  );

  test('a fresh database has no wishlist table', () async {
    final db = await LibraryCollectionsDatabase.openWithFactory(
      databaseFactoryFfi,
      '${root.path}/fresh.db',
    );
    addTearDown(db.close);
    expect(await _tables(db), isNot(contains('wishlist_tracks')));
    expect(await _tables(db), contains('loved_tracks'));
  });
}
