import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_queue_store.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/library_row_mapper.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;

const _artists = ['Aurora Lane', 'Night Shift', 'Paper Moons'];
const _formats = ['flac', 'm4a', 'mp3'];

Future<void> _addScanned(
  Database db,
  String id,
  String path, {
  required String album,
  required String artist,
  required int number,
  required String source,
  required int day,
}) async {
  final json = LocalLibraryItem(
    id: id,
    sourceId: source,
    trackName: number.isEven ? 'Night Song $number' : 'Day Song $number',
    artistName: artist,
    albumName: album,
    albumArtist: artist,
    filePath: path,
    coverPath: number == 2 ? '/Users/me/Covers/$day.jpg' : null,
    scannedAt: DateTime.utc(2026, 9, day, number),
    fileModTime: day * 100 + number,
    isrc: number == 3 ? 'BAD' : 'USAAA26${'$day$number'.padLeft(5, '0')}',
    trackNumber: number,
    totalTracks: day % 3 == 0 ? null : 4,
    discNumber: 1,
    totalDiscs: 1,
    bitDepth: switch ((day + number) % 3) {
      0 => 24,
      1 => 16,
      _ => null,
    },
    genre: day.isEven || number == 1 ? 'Pop' : null,
    label: day % 3 == 1 ? null : 'Label',
    releaseDate: '${2000 + day}',
    format: _formats[(day + number) % 3],
  ).toJson();
  await db.insert('library', LibraryRowMapper.encode(json));
  final batch = db.batch();
  sqlite.putPathKeysInBatch(batch, 'library_path_keys', id, path);
  await batch.commit(noResult: true);
}

Map<String, dynamic> _download(
  String id,
  String path, {
  required String album,
  required String artist,
  required int number,
  required int day,
}) => {
  'id': id,
  'trackName': number.isEven ? 'Night Track $number' : 'Track $number',
  'artistName': artist,
  'albumName': album,
  'albumArtist': day % 4 == 0 ? null : artist,
  'coverUrl': 'https://covers.example/$day.jpg',
  'filePath': path,
  'service': 'example-provider',
  'downloadedAt': DateTime.utc(2026, 8, day, number).toIso8601String(),
  'isrc': number == 2 ? 'DL$day' : 'USBBB26${'$day$number'.padLeft(5, '0')}',
  'trackNumber': number,
  'totalTracks': 3,
  'discNumber': 1,
  'totalDiscs': 1,
  'quality': switch (number) {
    1 => '24-bit/96kHz',
    2 => '16-bit/44.1kHz',
    _ => '320 kbps',
  },
  'bitDepth': switch (number) {
    1 => 24,
    2 => 16,
    _ => null,
  },
  'genre': day.isOdd || number == 1 ? 'Rock' : null,
  'label': day.isEven ? 'Label' : null,
  'releaseDate': '${2010 + day}',
  'format': _formats[(day + number + 1) % 3],
};

/// Albums of one to four songs by three album artists in every source
/// state; some scanned songs are also downloads, which hides them.
Future<void> _seed() async {
  final library = await LibraryDatabase.instance.database;
  for (final (id, available, enabled) in [
    ('music', 1, 1),
    ('offline', 0, 1),
    ('disabled', 1, 0),
  ]) {
    await library.insert('library_sources', {
      'id': id,
      'path': '/Volumes/$id',
      'display_name': id,
      'available': available,
      'enabled': enabled,
    });
  }
  const sources = ['legacy', 'music', 'music', 'offline', 'disabled'];
  final downloads = <Map<String, dynamic>>[];
  for (var day = 1; day <= 18; day++) {
    final artist = _artists[day % 3];
    final album = 'Album $day';
    for (var number = 1; number <= day % 4 + 1; number++) {
      final path =
          '/Users/me/Music/A$day/$number.${_formats[(day + number) % 3]}';
      await _addScanned(
        library,
        'l-$day-$number',
        path,
        album: album,
        artist: artist,
        number: number,
        source: sources[(day + number) % sources.length],
        day: day,
      );
      if (day % 5 == 0 && number == 1) {
        downloads.add(
          _download(
            'h-shadow-$day',
            path,
            album: album,
            artist: artist,
            number: number,
            day: day,
          ),
        );
      }
    }
  }
  for (var day = 1; day <= 12; day++) {
    for (var number = 1; number <= day % 3 + 1; number++) {
      downloads.add(
        _download(
          'h-$day-$number',
          '/Users/me/Downloads/D$day/$number.${_formats[(day + number + 1) % 3]}',
          album: 'Download $day',
          artist: _artists[(day + 1) % 3],
          number: number,
          day: day,
        ),
      );
    }
  }
  await HistoryDatabase.instance.upsertBatch(downloads);
}

final _filters = <String, QueueLibraryDbQuery Function(String, bool)>{
  'unfiltered': (sort, singles) =>
      QueueLibraryDbQuery(sortMode: sort, includeSingleTrackAlbums: singles),
  for (final artist in [..._artists, ' NIGHT Shift '])
    'artist $artist': (sort, singles) => QueueLibraryDbQuery(
      sortMode: sort,
      includeSingleTrackAlbums: singles,
      albumArtist: artist,
    ),
  'search night': (sort, singles) => QueueLibraryDbQuery(
    sortMode: sort,
    includeSingleTrackAlbums: singles,
    searchQuery: 'night',
  ),
  'search moons': (sort, singles) => QueueLibraryDbQuery(
    sortMode: sort,
    includeSingleTrackAlbums: singles,
    searchQuery: 'paper moons',
  ),
  for (final quality in ['hires', 'cd', 'lossy'])
    'quality $quality': (sort, singles) => QueueLibraryDbQuery(
      sortMode: sort,
      includeSingleTrackAlbums: singles,
      quality: quality,
    ),
  for (final format in ['flac', 'm4a'])
    'format $format': (sort, singles) => QueueLibraryDbQuery(
      sortMode: sort,
      includeSingleTrackAlbums: singles,
      format: format,
    ),
  for (final metadata in [
    'missing-genre',
    'complete',
    'incorrect-isrc-format',
    'incomplete-album',
    'unknown-album-completeness',
  ])
    'metadata $metadata': (sort, singles) => QueueLibraryDbQuery(
      sortMode: sort,
      includeSingleTrackAlbums: singles,
      metadata: metadata,
    ),
  'artist and search': (sort, singles) => QueueLibraryDbQuery(
    sortMode: sort,
    includeSingleTrackAlbums: singles,
    albumArtist: 'Night Shift',
    searchQuery: 'song',
  ),
  'artist and completeness': (sort, singles) => QueueLibraryDbQuery(
    sortMode: sort,
    includeSingleTrackAlbums: singles,
    albumArtist: 'Aurora Lane',
    metadata: 'incomplete-album',
  ),
};

QueueLibraryDbQuery _scoped(
  QueueLibraryDbQuery request, {
  String? source,
  bool includeLocal = true,
  int limit = 4,
  int offset = 0,
  QueueLibraryDbCursor? cursor,
}) => QueueLibraryDbQuery(
  includeSingleTrackAlbums: request.includeSingleTrackAlbums,
  albumArtist: request.albumArtist,
  searchQuery: request.searchQuery,
  quality: request.quality,
  format: request.format,
  metadata: request.metadata,
  sortMode: request.sortMode,
  source: source,
  includeLocal: includeLocal,
  limit: limit,
  offset: offset,
  cursor: cursor,
);

/// Every album page of [request]: cursor pages of four, then one offset
/// page.
Future<List<Object?>> _pages(
  LibraryQueueStore store,
  QueueLibraryDbQuery request,
) async {
  final pages = <Object?>[];
  QueueLibraryDbCursor? cursor;
  for (var page = 0; page < 20; page++) {
    final result = await store.albumPage(
      _scoped(
        request,
        source: request.source,
        includeLocal: request.includeLocal,
        cursor: cursor,
      ),
    );
    pages.add(result.rows);
    cursor = result.nextCursor;
    if (result.rows.length < 4 || cursor == null) break;
  }
  pages.add(
    (await store.albumPage(
      _scoped(
        request,
        source: request.source,
        includeLocal: request.includeLocal,
        offset: 4,
      ),
    )).rows,
  );
  return pages;
}

/// SHA-256 of every album page per filter, captured from the album page SQL
/// of 0331d5ec, which aggregated every album for a filtered page.
const _expectedDigests = <String, String>{
  'unfiltered':
      '97ab635a928bdfd8c2062f7e6648a38db8f697a161d941c8f81aeb5cb19fa69c',
  'artist Aurora Lane':
      '3be5b1a2daf47cc72c2520d2d9339e44f47c40213f319645ef5b0c5e7630f0a7',
  'artist Night Shift':
      '8a68113880849df209c714ff4213030f09525b2097520573567fa4603f9f39da',
  'artist Paper Moons':
      '3454a85a70b6d57efe06449d1f0de857af4778f5ddfcee54dbc2ca758aee1782',
  'artist  NIGHT Shift ':
      '8a68113880849df209c714ff4213030f09525b2097520573567fa4603f9f39da',
  'search night':
      '3d9e87c618a7fdb6b273ae7a963760e539b5bdaa3c331704119727417681cbc6',
  'search moons':
      '3454a85a70b6d57efe06449d1f0de857af4778f5ddfcee54dbc2ca758aee1782',
  'quality hires':
      '5a652e13184594fc7fcadc293e1e3073843e7b35718565830d75645073fd9f64',
  'quality cd':
      'ee452e730d15cd8202eb510a0e48b702f9c1bd606aef1be2945aaab83445d64e',
  'quality lossy':
      'c5f3f2112f63a50f9954e17cdb087040445019291d345c6273ffcb06faa1e8f7',
  'format flac':
      'fd54633fec45a10bab5ca5d23ececb9846fe5781f1b944a1717c1b580db5d346',
  'format m4a':
      'fe90f44b048c3eae120ada80624d154d185cdbb7f9de6eb97a4ac5951eac4997',
  'metadata missing-genre':
      'a133962baa15a6e250981697c347c31c8a8ea2a287fed69014de06d94d800f7d',
  'metadata complete':
      '8df07c5d401fbb006d352deb877c012862911be48b598ec441def411206e1c89',
  'metadata incorrect-isrc-format':
      '3788fe1b3e4a0ce9bc43493764994cb58d51a2a5b917974fe36307ac583f772d',
  'metadata incomplete-album':
      '46d3899a617240bf0b20056b89bc791d1a5aa2bc7bf200fb7dc930d98d8a517b',
  'metadata unknown-album-completeness':
      'd6103617d467f45d98ec181e4e98ea2fa91fed76f2d01e0da680d0d5818b4ad9',
  'artist and search':
      '07fe0c6a52d430792dfd4bcdbf2bb40f720c20eeca48c28e4ed139f3d80147ee',
  'artist and completeness':
      '5e937fb67a9f819719f50edefc24075b6cb2a8b967301d82d2cdf633df0c63db',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const paths = MethodChannel('plugins.flutter.io/path_provider');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory root;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('library-album-filter-');
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
  });

  test(
    'filtered album pages keep their rows, counts, sort keys and order',
    () async {
      await _seed();
      final db = await LibraryDatabase.instance.database;
      // Creates the size tables the size sort joins and attaches History.
      await LibraryDatabase.instance.getQueueAlbumPageResult(
        const QueueLibraryDbQuery(sortMode: 'size-desc'),
      );
      // The album-artist filters read the credits table that queries build.
      await tagCreditsCacheFor(db).ensure(db, const TagCreditScope());
      final snapshot = sqlite.LibraryShadowSnapshot();
      final actual = <String, String>{};
      for (final MapEntry(key: name, value: build) in _filters.entries) {
        final results = <Object?>[];
        for (final fts in [false, true]) {
          LibraryQueueStore store({bool shadow = false}) => LibraryQueueStore(
            db,
            historyFts: fts && HistoryDatabase.instance.searchFtsAvailable,
            localFts: fts && LibraryDatabase.instance.searchFtsAvailable,
            shadowSnapshot: shadow,
          );
          for (final sort in [
            'latest',
            'album-asc',
            'artist-desc',
            'release-newest',
            'size-desc',
          ]) {
            for (final singles in [false, true]) {
              for (final (source, includeLocal) in [
                (null, true),
                ('downloaded', true),
                ('local', true),
                (null, false),
              ]) {
                final request = _scoped(
                  build(sort, singles),
                  source: source,
                  includeLocal: includeLocal,
                );
                final rowCheck = await _pages(store(), request);
                final viaSnapshot = await snapshot.read(
                  db,
                  (shadow) => _pages(store(shadow: shadow), request),
                );
                expect(
                  viaSnapshot,
                  rowCheck,
                  reason: '$name $sort $singles $source $includeLocal',
                );
                results.add(rowCheck);
              }
            }
          }
        }
        actual[name] = sha256
            .convert(utf8.encode(jsonEncode(results)))
            .toString();
      }
      final mismatches = [
        for (final MapEntry(:key, :value) in actual.entries)
          if (_expectedDigests[key] != value) "  '$key': '$value',",
      ];
      expect(
        mismatches,
        isEmpty,
        reason: 'Changed album pages:\n${mismatches.join('\n')}',
      );
      expect(_expectedDigests.keys, unorderedEquals(actual.keys));
    },
  );
}
