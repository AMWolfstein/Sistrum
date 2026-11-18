import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/services/library_collections_database.dart';

/// A fresh directory removed after the test.
Future<Directory> _tempDirectory() async {
  final directory = await Directory.systemTemp.createTemp('playlist-reorder-');
  addTearDown(() => directory.delete(recursive: true));
  return directory;
}

/// A collections database with the app schema, opened through FFI.
Future<Database> _openDatabase(Directory directory, String name) async {
  sqfliteFfiInit();
  final db = await LibraryCollectionsDatabase.openWithFactory(
    databaseFactoryFfi,
    '${directory.path}/$name.db',
  );
  addTearDown(db.close);
  return db;
}

Track _track(String id) => Track(
  id: id,
  name: 'Song $id',
  artistName: 'Artist',
  albumName: 'Album',
  duration: 200,
);

ProviderContainer _container(LibraryCollectionsDatabase database) {
  final container = ProviderContainer(
    overrides: [
      libraryCollectionsProvider.overrideWith(
        () => LibraryCollectionsNotifier(database: database),
      ),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

List<String> _order(ProviderContainer container, String playlistId) => [
  for (final entry
      in container
          .read(libraryCollectionsProvider)
          .playlistById(playlistId)!
          .tracks)
    entry.track.id,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('a reorder persists and keeps later additions last', () async {
    final directory = await _tempDirectory();
    final db = await _openDatabase(directory, 'reorder');
    final database = LibraryCollectionsDatabase.withDatabase(db);
    final container = _container(database);
    final notifier = container.read(libraryCollectionsProvider.notifier);

    final id = await notifier.createPlaylist('Mix');
    // One batch shares a single stamp, so only rowid orders these tracks.
    await notifier.addTracksToPlaylist(id, [
      for (final id in ['a', 'b', 'c', 'd']) _track(id),
    ]);
    final keys = [
      for (final entry
          in container
              .read(libraryCollectionsProvider)
              .playlistById(id)!
              .tracks)
        entry.key,
    ];

    expect(
      await notifier.reorderPlaylistTracks(id, [
        keys[2],
        keys[0],
        keys[3],
        keys[1],
      ]),
      isTrue,
    );
    expect(_order(container, id), ['c', 'a', 'd', 'b']);
    await notifier.addTrackToPlaylist(id, _track('e'));
    expect(_order(container, id), ['c', 'a', 'd', 'b', 'e']);

    final reloaded = _container(database);
    await reloaded
        .read(libraryCollectionsProvider.notifier)
        .ensurePlaylistLoaded(id);
    expect(_order(reloaded, id), ['c', 'a', 'd', 'b', 'e']);

    final stamps = (await db.rawQuery(
      'SELECT added_at FROM playlist_tracks WHERE playlist_id = ? '
      'ORDER BY added_at, rowid',
      [id],
    )).map((row) => row['added_at']! as String).toList();
    expect(stamps.toSet(), hasLength(5));
  });

  test('a reorder must name every track exactly once', () async {
    final directory = await _tempDirectory();
    final db = await _openDatabase(directory, 'invalid');
    final container = _container(LibraryCollectionsDatabase.withDatabase(db));
    final notifier = container.read(libraryCollectionsProvider.notifier);
    final id = await notifier.createPlaylist('Mix');
    await notifier.addTracksToPlaylist(id, [
      _track('a'),
      _track('b'),
    ]);
    final keys = [
      for (final entry
          in container
              .read(libraryCollectionsProvider)
              .playlistById(id)!
              .tracks)
        entry.key,
    ];

    for (final order in [
      [keys[1]],
      [keys[1], keys[1]],
      [keys[1], 'missing'],
      keys,
    ]) {
      expect(await notifier.reorderPlaylistTracks(id, order), isFalse);
    }
    expect(_order(container, id), ['a', 'b']);
  });

  for (final mode in [
    LibraryTracksFolderMode.loved,
    LibraryTracksFolderMode.wishlist,
  ]) {
    test(
      '${mode.name} order survives reload and keeps new songs first',
      () async {
        final directory = await _tempDirectory();
        final db = await _openDatabase(directory, mode.name);
        final database = LibraryCollectionsDatabase.withDatabase(db);
        final container = _container(database);
        final notifier = container.read(libraryCollectionsProvider.notifier);
        final tracks = [
          for (final id in ['a', 'b', 'c', 'd']) _track(id),
        ];
        if (mode == LibraryTracksFolderMode.loved) {
          await notifier.toggleLovedTracks(tracks);
        } else {
          for (final track in tracks) {
            await notifier.toggleWishlist(track);
          }
        }
        List<CollectionTrackEntry> entries(ProviderContainer c) =>
            mode == LibraryTracksFolderMode.loved
            ? c.read(libraryCollectionsProvider).loved
            : c.read(libraryCollectionsProvider).wishlist;
        final byId = {
          for (final entry in entries(container)) entry.track.id: entry,
        };
        expect(
          await notifier.reorderSavedTracks(mode, [
            for (final id in ['c', 'a', 'd', 'b']) byId[id]!.key,
          ]),
          isTrue,
        );
        expect(entries(container).map((e) => e.track.id), ['c', 'a', 'd', 'b']);
        if (mode == LibraryTracksFolderMode.loved) {
          await notifier.toggleLovedTracks([_track('e')]);
        } else {
          await notifier.toggleWishlist(_track('e'));
        }
        final reloaded = _container(database);
        await reloaded
            .read(libraryCollectionsProvider.notifier)
            .ensurePlaylistLoaded('unused');
        expect(entries(reloaded).map((e) => e.track.id), [
          'e',
          'c',
          'a',
          'd',
          'b',
        ]);
        final stable = entries(reloaded).map((e) => e.key).toList();
        final restored = reloaded.read(libraryCollectionsProvider.notifier);
        expect(
          await restored.reorderSavedTracks(mode, [stable.first]),
          isFalse,
        );
        expect(
          await restored.reorderSavedTracks(mode, [
            for (final _ in stable) stable.first,
          ]),
          isFalse,
        );
        expect(await restored.reorderSavedTracks(mode, stable), isFalse);
        expect(entries(reloaded).map((e) => e.track.id), [
          'e',
          'c',
          'a',
          'd',
          'b',
        ]);
      },
    );
  }
}
