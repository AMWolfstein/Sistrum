import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_collections_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_row_mapper.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;

import 'support/fixed_settings.dart';

const _scope = TagCreditScope();

Future<void> _scanned(
  Database db, {
  required String id,
  required String name,
  required String path,
  required String album,
  required String artist,
  String? albumArtist,
  int? number,
  String? releaseDate,
  String? cover,
  int? totalTracks,
}) async {
  final json = LocalLibraryItem(
    id: id,
    sourceId: 'music',
    trackName: name,
    artistName: artist,
    albumName: album,
    albumArtist: albumArtist,
    filePath: path,
    coverPath: cover,
    scannedAt: DateTime.utc(2026, 9, 1),
    trackNumber: number,
    discNumber: 1,
    releaseDate: releaseDate,
    totalTracks: totalTracks,
    format: 'flac',
  ).toJson();
  await db.insert('library', LibraryRowMapper.encode(json));
  final batch = db.batch();
  sqlite.putPathKeysInBatch(batch, 'library_path_keys', id, path);
  await batch.commit(noResult: true);
}

Map<String, dynamic> _download(
  String id, {
  required String name,
  required String path,
  required String album,
  required String artist,
  String? albumArtist,
  required int number,
  String? releaseDate,
  int? totalTracks,
  String cover = 'https://covers.example/downloads.jpg',
}) => {
  'id': id,
  'trackName': name,
  'artistName': artist,
  'albumName': album,
  'albumArtist': albumArtist,
  'coverUrl': cover,
  'filePath': path,
  'service': 'example-provider',
  'downloadedAt': DateTime.utc(2026, 8, 1, number).toIso8601String(),
  'isrc': 'US${id.replaceAll('-', '').toUpperCase()}',
  'trackNumber': number,
  'discNumber': 1,
  'totalDiscs': 1,
  'releaseDate': releaseDate,
  'totalTracks': totalTracks,
  'quality': '320 kbps',
};

/// Dana Vale's albums and features, one Keep-together artist, a song library
/// for Popular, and an album whose songs are both downloaded and scanned.
Future<void> _seed() async {
  final library = await LibraryDatabase.instance.database;
  await library.insert('library_sources', {
    'id': 'music',
    'path': '/Volumes/music',
    'display_name': 'music',
    'available': 1,
    'enabled': 1,
  });
  const dana = 'Dana Vale';
  await _scanned(
    library,
    id: 'long-1',
    name: 'Long One',
    path: '/music/long/1.flac',
    album: 'Long Play',
    artist: dana,
    number: 1,
    releaseDate: '2020-05-01',
    cover: '/covers/long.jpg',
    totalTracks: 12,
  );
  await _scanned(
    library,
    id: 'long-2',
    name: 'Long Two',
    path: '/music/long/2.flac',
    album: 'Long Play',
    artist: 'Dana Vale, Kit',
    albumArtist: dana,
    number: 2,
    releaseDate: '2020-05-01',
  );
  await _scanned(
    library,
    id: 'long-3',
    name: 'Long Three',
    path: '/music/long/3.flac',
    album: 'Long Play',
    artist: dana,
    number: 3,
    releaseDate: '2020-05-01',
  );
  await _scanned(
    library,
    id: 'single',
    name: 'Lone Song',
    path: '/music/single/1.flac',
    album: 'Lone Single',
    artist: dana,
    number: 1,
    releaseDate: '2021-01-01',
    totalTracks: 1,
  );
  // Two of twelve owned: the tag makes it an album.
  for (var n = 1; n <= 2; n++) {
    await _scanned(
      library,
      id: 'partial-$n',
      name: 'Partial $n',
      path: '/music/partial/$n.flac',
      album: 'Partial Album',
      artist: dana,
      number: n,
      releaseDate: '2016-04-04',
      totalTracks: 12,
    );
  }
  await _scanned(
    library,
    id: 'ep-1',
    name: 'Night Track',
    path: '/music/night/1.flac',
    album: 'Night Drive - EP',
    artist: dana,
    number: 1,
    releaseDate: '2015-01-01',
  );
  await _scanned(
    library,
    id: 'demo-1',
    name: 'Demo One',
    path: '/music/demo/1.flac',
    album: 'Undated Demo',
    artist: dana,
    number: 1,
  );
  await _scanned(
    library,
    id: 'demo-2',
    name: 'Demo Two',
    path: '/music/demo/2.flac',
    album: 'Undated Demo',
    artist: dana,
    number: 2,
  );
  await _scanned(
    library,
    id: 'duet',
    name: 'Duet',
    path: '/music/kit/1.flac',
    album: 'Kit Album',
    artist: 'Dana Vale, Kit',
    albumArtist: 'Kit',
    number: 1,
    releaseDate: '2018-01-01',
  );
  await _scanned(
    library,
    id: 'fallback-1',
    name: 'Fallback One',
    path: '/music/fallback/1.flac',
    album: 'Fallback Album',
    artist: 'Dana Vale, Kit',
    number: 1,
    releaseDate: '2019-01-01',
  );
  await _scanned(
    library,
    id: 'fallback-2',
    name: 'Fallback Two',
    path: '/music/fallback/2.flac',
    album: 'Fallback Album',
    artist: 'Dana Vale, Kit',
    number: 2,
    releaseDate: '2019-01-01',
  );
  for (var n = 1; n <= 7; n++) {
    await _scanned(
      library,
      id: 'pop-$n',
      name: 'Pop $n',
      path: '/music/pop/$n.flac',
      album: 'Pop Album',
      artist: 'Pop Act',
      number: n,
    );
  }
  await _scanned(
    library,
    id: 'duo-1',
    name: 'Duo One',
    path: '/music/duo/1.flac',
    album: 'Duo Album',
    artist: 'Duo',
    number: 1,
  );
  await _scanned(
    library,
    id: 'duo-2',
    name: 'Duo Two',
    path: '/music/duo/2.flac',
    album: 'Duo Album',
    artist: 'Duo',
    number: 2,
  );
  await _scanned(
    library,
    id: 'tyler',
    name: 'Tyler Song',
    path: '/music/tyler/1.flac',
    album: 'Flower Boy',
    artist: 'Tyler, The Creator',
    number: 1,
    releaseDate: '2017-07-21',
  );
  await HistoryDatabase.instance.upsertBatch([
    _download(
      'mix-1',
      name: 'Mix One',
      path: '/downloads/mix/1.mp3',
      album: 'Mixtape',
      artist: dana,
      number: 1,
      releaseDate: '2022-03-03',
      totalTracks: 9,
      cover: 'https://covers.example/mixtape.jpg',
    ),
    _download(
      'mix-2',
      name: 'Mix Two',
      path: '/downloads/mix/2.mp3',
      album: 'Mixtape',
      artist: dana,
      number: 2,
      releaseDate: '2022-03-03',
      totalTracks: 9,
      cover: 'https://covers.example/mixtape.jpg',
    ),
    _download(
      'hits-1',
      name: 'Hits Feature',
      path: '/downloads/hits/1.mp3',
      album: 'Various Hits',
      artist: 'Dana Vale; Other',
      albumArtist: 'Various',
      number: 1,
    ),
    _download(
      'shared-3',
      name: 'Shared Three',
      path: '/downloads/shared/3.mp3',
      album: 'Shared Album',
      artist: dana,
      number: 3,
    ),
    _download(
      'shared-4',
      name: 'Shared Four',
      path: '/downloads/shared/4.mp3',
      album: 'Shared Album',
      artist: dana,
      number: 4,
    ),
  ]);
  await _scanned(
    library,
    id: 'shared-1',
    name: 'Shared One',
    path: '/music/shared/1.flac',
    album: 'Shared Album',
    artist: dana,
    number: 1,
  );
  await _scanned(
    library,
    id: 'shared-2',
    name: 'Shared Two',
    path: '/music/shared/2.flac',
    album: 'Shared Album',
    artist: dana,
    number: 2,
  );
}

Future<void> _play(String path, int plays) async {
  await writeListeningDeltas(await ListeningStatisticsStore.instance.database, [
    ListeningDelta(
      '2026-10-01',
      ListeningTrack(key: path, title: path, artist: '', album: ''),
      plays * 1000,
      plays,
    ),
  ]);
}

List<String> _itemNames(Iterable<UnifiedLibraryItem> items) => [
  for (final item in items) item.trackName,
];

List<String> _names(Iterable<Track> tracks) => [
  for (final track in tracks) track.name,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory root;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('library-detail-data-');
    sqfliteFfiInit();
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactoryFfi;
    messenger.setMockMethodCallHandler(paths, (_) async => root.path);
    addTearDown(() async {
      await LibraryDatabase.instance.close();
      await HistoryDatabase.instance.close();
      await ListeningStatisticsStore.instance.clear();
      messenger.setMockMethodCallHandler(paths, null);
      databaseFactory = previousFactory;
      await root.delete(recursive: true);
    });
    await _seed();
  });

  group('artist albums and singles', () {
    test('albums are album-artist credits, one per album key, kind and '
        'size from the total tag', () async {
      final overview = await loadLibraryArtistOverview(
        'Dana Vale',
        credits: _scope,
        includeLocal: true,
      );
      final byName = {for (final album in overview.albums) album.name: album};
      expect(
        byName.keys,
        unorderedEquals([
          'Long Play',
          'Lone Single',
          'Undated Demo',
          'Fallback Album',
          'Mixtape',
          'Shared Album',
          'Partial Album',
          'Night Drive - EP',
        ]),
      );
      expect(byName['Long Play']!.totalTracks, 12);
      expect(byName['Long Play']!.albumType, 'album');
      expect(byName['Lone Single']!.totalTracks, 1);
      expect(byName['Lone Single']!.albumType, 'single');
      expect(byName['Mixtape']!.totalTracks, 9);
      expect(byName['Mixtape']!.albumType, 'album');
      expect(byName['Partial Album']!.totalTracks, 12);
      expect(
        byName['Partial Album']!.albumType,
        'album',
        reason: 'two of twelve owned stays an album',
      );
      expect(byName['Night Drive - EP']!.totalTracks, 0);
      expect(byName['Night Drive - EP']!.albumType, 'ep');
      expect(byName['Undated Demo']!.albumType, 'album');
      expect(byName['Undated Demo']!.totalTracks, 0);
      expect(byName['Mixtape']!.coverUrl, 'https://covers.example/mixtape.jpg');
      expect(byName['Long Play']!.coverUrl, '/covers/long.jpg');
      expect(
        overview.albums.where((album) => album.name == 'Kit Album'),
        isEmpty,
        reason: 'Dana Vale is only a featured artist on Kit Album',
      );
    });

    test('the latest release is the newest dated album and undated '
        'albums never qualify', () async {
      final overview = await loadLibraryArtistOverview(
        'Dana Vale',
        credits: _scope,
        includeLocal: true,
      );
      expect(overview.latestRelease!.name, 'Mixtape');
      expect(overview.imageCover, 'https://covers.example/mixtape.jpg');
      expect(
        overview.albums.firstWhere((a) => a.name == 'Undated Demo').releaseDate,
        isEmpty,
      );
    });

    test('an artist whose albums are all undated has no latest release or '
        'image', () async {
      final library = await LibraryDatabase.instance.database;
      await _scanned(
        library,
        id: 'undated-only',
        name: 'Plain',
        path: '/music/plain/1.flac',
        album: 'No Date',
        artist: 'Plain Artist',
        number: 1,
      );
      final overview = await loadLibraryArtistOverview(
        'Plain Artist',
        credits: _scope,
        includeLocal: true,
      );
      expect(overview.albums.map((album) => album.name), ['No Date']);
      expect(overview.latestRelease, isNull);
      expect(overview.imageCover, isNull);
    });

    test('a Keep together name stays one artist', () async {
      const kept = TagCreditScope(
        rules: TagCreditRules(keepTogether: ['Tyler, The Creator']),
      );
      final split = await loadLibraryArtistOverview(
        'Tyler, The Creator',
        credits: _scope,
        includeLocal: true,
      );
      final together = await loadLibraryArtistOverview(
        'Tyler, The Creator',
        credits: kept,
        includeLocal: true,
      );
      expect(split.albums, isEmpty);
      expect(together.albums.map((album) => album.name), ['Flower Boy']);
      expect(together.latestRelease!.name, 'Flower Boy');
    });
  });

  group('featured tracks', () {
    test('a track whose album artist is the artist is not featured, and '
        'a missing album artist falls back to the track artist', () async {
      final featured = await loadLibraryArtistFeaturedTracks(
        'Dana Vale',
        credits: _scope,
        includeLocal: true,
      );
      // Long Two is album artist Dana Vale, and the Fallback Album songs have
      // Dana Vale as album artist through the track-artist fallback.
      expect(_names(featured), unorderedEquals(['Duet', 'Hits Feature']));
    });

    test(
      'an artist featured on someone else\'s album gets those songs',
      () async {
        final kit = await loadLibraryArtistFeaturedTracks(
          'Kit',
          credits: _scope,
          includeLocal: true,
        );
        expect(_names(kit), ['Long Two']);
        final other = await loadLibraryArtistFeaturedTracks(
          'Other',
          credits: _scope,
          includeLocal: true,
        );
        expect(_names(other), ['Hits Feature']);
      },
    );
  });

  group('popular', () {
    setUp(() async {
      await _play('/music/pop/3.flac', 9);
      await _play('/music/pop/6.flac', 4);
    });

    test('with statistics on, the most played come first and random songs '
        'fill up to five', () async {
      for (var seed = 1; seed <= 20; seed++) {
        final popular = await loadLibraryArtistPopularTracks(
          'Pop Act',
          credits: _scope,
          includeLocal: true,
          useStatistics: true,
          random: Random(seed),
        );
        expect(popular, hasLength(libraryPopularTrackCount));
        expect(_names(popular.take(2)), ['Pop 3', 'Pop 6']);
        final rest = _names(popular.skip(2));
        expect(rest.toSet(), hasLength(3), reason: 'no song twice');
        expect(
          rest.every((name) => name != 'Pop 3' && name != 'Pop 6'),
          isTrue,
        );
      }
    });

    test('with statistics off, the played songs get no order', () async {
      var firstIsMostPlayed = true;
      for (var seed = 1; seed <= 20; seed++) {
        final popular = await loadLibraryArtistPopularTracks(
          'Pop Act',
          credits: _scope,
          includeLocal: true,
          useStatistics: false,
          random: Random(seed),
        );
        expect(popular, hasLength(libraryPopularTrackCount));
        if (popular.first.name != 'Pop 3') firstIsMostPlayed = false;
      }
      expect(firstIsMostPlayed, isFalse);
    });

    test('with statistics on but no plays, random songs only', () async {
      final popular = await loadLibraryArtistPopularTracks(
        'Duo',
        credits: _scope,
        includeLocal: true,
        useStatistics: true,
        random: Random(3),
      );
      expect(_names(popular), unorderedEquals(['Duo One', 'Duo Two']));
    });

    test('fewer songs than the section size stay as they are', () async {
      await _play('/music/duo/2.flac', 2);
      final popular = await loadLibraryArtistPopularTracks(
        'Duo',
        credits: _scope,
        includeLocal: true,
        useStatistics: true,
        random: Random(1),
      );
      expect(_names(popular), ['Duo Two', 'Duo One']);
    });

    test('plays are read for any number of songs in chunks', () async {
      final keys = [
        for (var n = 0; n < 900; n++) '/music/none/$n.flac',
        '/music/pop/3.flac',
        '/music/pop/6.flac',
      ];
      final plays = await ListeningStatisticsStore.instance.read(
        (db) => readPlaysForTracks(db, keys),
      );
      expect(plays, {'/music/pop/3.flac': 9, '/music/pop/6.flac': 4});
    });
  });

  group('album tracks by key', () {
    final shared = LibraryDatabase.albumKeyFor(
      'Shared Album',
      null,
      'Dana Vale',
    );
    final mixtape = LibraryDatabase.albumKeyFor('Mixtape', null, 'Dana Vale');
    final single = LibraryDatabase.albumKeyFor(
      'Lone Single',
      null,
      'Dana Vale',
    );

    test('downloaded and scanned copies of one album key are both listed '
        'in album order', () async {
      final tracks = await loadLibraryAlbumItems(shared, includeLocal: true);
      expect(_itemNames(tracks), [
        'Shared One',
        'Shared Two',
        'Shared Three',
        'Shared Four',
      ]);
    });

    test(
      'an album only downloaded or only scanned is found by its key',
      () async {
        final downloaded = await loadLibraryAlbumItems(
          mixtape,
          includeLocal: true,
        );
        final scanned = await loadLibraryAlbumItems(single, includeLocal: true);
        expect(_itemNames(downloaded), ['Mix One', 'Mix Two']);
        expect(_itemNames(scanned), ['Lone Song']);
      },
    );

    test('a downloaded file that is also scanned is listed once', () async {
      final library = await LibraryDatabase.instance.database;
      await _scanned(
        library,
        id: 'mix-scan-1',
        name: 'Mix One Scanned',
        path: '/downloads/mix/1.mp3',
        album: 'Mixtape',
        artist: 'Dana Vale',
        number: 1,
      );
      final tracks = await loadLibraryAlbumItems(mixtape, includeLocal: true);
      expect(_itemNames(tracks), ['Mix One', 'Mix Two']);
      final overview = await loadLibraryArtistOverview(
        'Dana Vale',
        credits: _scope,
        includeLocal: true,
      );
      expect(
        overview.albums
            .firstWhere((album) => album.name == 'Mixtape')
            .totalTracks,
        9,
        reason: 'the total tag, not the copies that are owned',
      );
    });

    test(
      'with the local library off, only downloaded songs are listed',
      () async {
        final tracks = await loadLibraryAlbumItems(shared, includeLocal: false);
        expect(_itemNames(tracks), ['Shared Three', 'Shared Four']);
        final scanned = await loadLibraryAlbumItems(
          single,
          includeLocal: false,
        );
        expect(scanned, isEmpty);
      },
    );
  });

  group('playlist and Loved tracks', () {
    test('playlist and Loved rows come back as Track', () async {
      SharedPreferences.setMockInitialValues({});
      const track = Track(
        id: 'loved-1',
        name: 'Saved Song',
        artistName: 'Dana Vale',
        albumName: 'Long Play',
        duration: 200,
      );
      final json = jsonEncode(track.toJson());
      final collections = LibraryCollectionsDatabase.instance;
      await collections.upsertPlaylist(
        id: 'playlist-1',
        name: 'Mix',
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z',
      );
      await collections.upsertPlaylistTrack(
        playlistId: 'playlist-1',
        trackKey: 'loved-1',
        trackJson: json,
        addedAt: '2026-01-02T00:00:00.000Z',
        playlistUpdatedAt: '2026-01-02T00:00:00.000Z',
      );
      await collections.upsertLovedEntry(
        trackKey: 'loved-1',
        trackJson: json,
        addedAt: '2026-01-03T00:00:00.000Z',
      );
      final container = ProviderContainer(
        overrides: [settingsProvider.overrideWith(FixedSettingsNotifier.new)],
      );
      addTearDown(container.dispose);
      final playlist = await container.read(
        libraryPlaylistTracksProvider('playlist-1').future,
      );
      final loved = await container.read(libraryLovedTracksProvider.future);
      expect(_names(playlist), ['Saved Song']);
      expect(_names(loved), ['Saved Song']);
    });
  });

  test('the providers expose the library data to the screens', () async {
    final container = ProviderContainer(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(
            const AppSettings(
              localLibraryEnabled: true,
              listeningStatisticsEnabled: true,
            ),
          ),
        ),
        tagCreditScopeProvider.overrideWithValue(_scope),
      ],
    );
    addTearDown(container.dispose);
    final overview = await container.read(
      libraryArtistOverviewProvider('Dana Vale').future,
    );
    expect(overview.latestRelease!.name, 'Mixtape');
    final featured = await container.read(
      libraryArtistFeaturedTracksProvider('Dana Vale').future,
    );
    expect(_names(featured), unorderedEquals(['Duet', 'Hits Feature']));
    final popular = await container.read(
      libraryArtistPopularTracksProvider('Pop Act').future,
    );
    expect(popular, hasLength(libraryPopularTrackCount));
    final album = await container.read(
      libraryAlbumTracksProvider(
        LibraryDatabase.albumKeyFor('Mixtape', null, 'Dana Vale'),
      ).future,
    );
    expect(_names(album), ['Mix One', 'Mix Two']);

    final localOff = ProviderContainer(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(
            const AppSettings(localLibraryEnabled: false),
          ),
        ),
        tagCreditScopeProvider.overrideWithValue(_scope),
      ],
    );
    addTearDown(localOff.dispose);
    final sharedWithoutScans = await localOff.read(
      libraryAlbumTracksProvider(
        LibraryDatabase.albumKeyFor('Shared Album', null, 'Dana Vale'),
      ).future,
    );
    expect(_names(sharedWithoutScans), ['Shared Three', 'Shared Four']);
  });
}
