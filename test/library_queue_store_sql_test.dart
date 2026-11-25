import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/services/album_completeness.dart';
import 'package:sistrum/services/library_queue_store.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;

// Whitespace outside literals is insignificant, and no literal in the queue
// SQL contains a whitespace run.
String _normalized(String sql) => sql.replaceAll(RegExp(r'\s+'), ' ').trim();

/// Records every query and returns no rows, so the generated SQL can be pinned
/// without a database.
class _RecordingDatabase implements Database {
  final queries = <String>[];

  @override
  Future<List<Map<String, Object?>>> rawQuery(
    String sql, [
    List<Object?>? arguments,
  ]) async {
    queries.add('${_normalized(sql)}\n${jsonEncode(arguments)}');
    return const [];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _sortModes = [
  'latest',
  'oldest',
  'a-z',
  'z-a',
  'artist-asc',
  'artist-desc',
  'album-asc',
  'album-desc',
  'release-oldest',
  'release-newest',
  'genre-asc',
  'genre-desc',
  'size-asc',
  'size-desc',
  'unknown-sort',
];

const _cursor4 = QueueLibraryDbCursor(['c0', 'c1', 2, 'c3']);
const _cursor5 = QueueLibraryDbCursor(['c0', 1, 'c2', 'c3', 'c4']);

final _filters =
    <String, QueueLibraryDbQuery Function(String, QueueLibraryDbCursor?)>{
      'default': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor),
      'source-downloaded': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        source: 'downloaded',
      ),
      'source-local': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, source: 'local'),
      'source-other': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, source: 'other'),
      'no-local': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        includeLocal: false,
      ),
      'no-local-source-local': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        includeLocal: false,
        source: 'local',
      ),
      'singles': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        filterMode: 'singles',
      ),
      'singles-downloaded': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        filterMode: 'singles',
        source: 'downloaded',
      ),
      'search': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        searchQuery: ' Hello_50% \\ World ',
      ),
      'album-artist': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        albumArtist: 'Some Artist',
      ),
      'quality-hires': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        quality: ' HiRes ',
      ),
      'quality-cd': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, quality: 'cd'),
      'quality-lossy': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, quality: 'lossy'),
      'format-m4a': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, format: 'M4A'),
      'format-unknown': (sort, cursor) =>
          QueueLibraryDbQuery(sortMode: sort, cursor: cursor, format: 'x_y%'),
      for (final metadata in [
        'complete',
        'missing-any',
        'missing-year',
        'missing-genre',
        'missing-album-artist',
        'missing-track-number',
        'missing-disc-number',
        'missing-artist',
        'incorrect-isrc-format',
        'missing-isrc',
        'missing-label',
        'missing-lyrics',
        'has-replaygain',
        'missing-replaygain',
        'incomplete-album',
        'unknown-album-completeness',
        'unknown-metadata',
      ])
        'metadata-$metadata': (sort, cursor) => QueueLibraryDbQuery(
          sortMode: sort,
          cursor: cursor,
          metadata: metadata,
        ),
      'single-track-albums': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        includeSingleTrackAlbums: true,
      ),
      'single-track-albums-incomplete': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        includeSingleTrackAlbums: true,
        metadata: 'incomplete-album',
      ),
      'combined': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        source: 'downloaded',
        filterMode: 'singles',
        searchQuery: 'hello',
        albumArtist: 'Some Artist',
        quality: 'hires',
        format: 'flac',
        metadata: 'missing-genre',
        limit: 7,
        offset: 21,
      ),
      'combined-local': (sort, cursor) => QueueLibraryDbQuery(
        sortMode: sort,
        cursor: cursor,
        source: 'local',
        filterMode: 'singles',
        searchQuery: 'hello',
        albumArtist: 'Some Artist',
        quality: 'cd',
        format: 'ogg',
        metadata: 'unknown-album-completeness',
        limit: 7,
        offset: 21,
      ),
    };

/// SHA-256 of the normalized SQL and bound arguments of every query each case
/// issues. Captured from the reference implementation; a mismatch means the
/// Library order, filter or paging SQL changed.
const _expectedDigests = <String, String>{
  'sort:latest':
      '55e9ccb461bfb2b2b8ab4352c17a05e3303c2b736859f084d8e9444ba9d675ff',
  'sort:oldest':
      '79064d1532c72446e6766ef9b6d7ad23b0b15282173fbfdafeedae0a75709f08',
  'sort:a-z':
      'fe3f64fccf904a35f44b458b75f17aee22889e2aeb41efc714bcdad49352d9c6',
  'sort:z-a':
      'ec593f16de444b224546e3c90741ec8efa3431d726342a30a31499e7ba0b9da3',
  'sort:artist-asc':
      '099bfcaaed9053ce942b6a0d7b4f85aadf7503752ce4e88da3b478df50b75da8',
  'sort:artist-desc':
      'd57b829a725115bb29bd27eb2d3e3508a778fc9be9025c4580c9842e82e6fe7b',
  'sort:album-asc':
      'bdecb38dede28a7652646f99250676317211acac86f9f6135bcc5f780f210975',
  'sort:album-desc':
      '65d4e6ab6e56be1240abe775693f68f3f1b09f1b2405f83cf43583f9e63e915b',
  'sort:release-oldest':
      'fd070205c20fd710e19411d96339e7a73a492936620af01406c5691cc3970e72',
  'sort:release-newest':
      '7a7ab61f7f75030b608e45d2c4e11a8b4b1ade211f6d114d74dd13effa76e9a8',
  'sort:genre-asc':
      'f10d9e96658cdd0cba82af877d3c99ec96ae8858bd0ef49c7fec59733676a9cd',
  'sort:genre-desc':
      'ddec75ac180ed6233c3ed7183e8a7409f5f5e741168b347277a4c7f05b76577d',
  'sort:size-asc':
      'be9332daa6a307196835d051c5fc217012c26fd87050dcd76a97624ff6ac18b7',
  'sort:size-desc':
      'aeca82515ad5c285398d67ee51d8df1f3f251eaa09836e1e1b5fe96bc8757491',
  'sort:unknown-sort':
      '55e9ccb461bfb2b2b8ab4352c17a05e3303c2b736859f084d8e9444ba9d675ff',
  'filter:default':
      '4f125b1ffb91400b8ca1300b148c9ae190a0b11bbe470e23afad7a965f6d1d05',
  'filter:source-downloaded':
      '07ea809ebcc784d86c6ec4e93c4bb3af9e237751cf9c9799d9a77425105d6ce8',
  'filter:source-local':
      '047af9930aed0252170b843f21d05d6aa5311ed432bdc64bac32406112896bac',
  'filter:source-other':
      '40a7bcb73bb022d161910b12fde93f1ba8f8ecbe4f56b12150a8713054b28980',
  'filter:no-local':
      'f8bc84c866a7436a5017558e4e6429069481efad1fa2e3ab07af05f93eaaf4bb',
  'filter:no-local-source-local':
      '31cafb933841a2b2d1d76e68bcd09b6bd4647bbe2166482429bac484ba3f7a3b',
  'filter:singles':
      'f3a4186c45c62d25bff587df1200c62ba6f690c003e586c22e01075aa42498a3',
  'filter:singles-downloaded':
      '9edcdc501b37dd3cc129b1a506d5ed91f61edaa68bf8ee7c72971951a96a9e66',
  'filter:search':
      'd863c3d3d6ef525c85cd6e24f61f13627c9c7de43534e6446f1c906dcdf3edff',
  'filter:album-artist':
      'eccce51f9e43711be9e20aaafffbcc32e855129727541b1b7df2dd61f454d808',
  'filter:quality-hires':
      '63d5dc078de7d1a667674c67126f8f46bf88747a5439a02dd1cc17e29f3372df',
  'filter:quality-cd':
      'ccd165a4dacad99378bdff7b4aa9541afcc0737fdfd11a17b40fd5a7f8d79edc',
  'filter:quality-lossy':
      '71820675d04de74d7a15bdd7e220d7c71397101dbfffbccca863f8d73a181a20',
  'filter:format-m4a':
      '18ac70c1694fc3fe6434e90781afaa657ba9636d64f6197288ab37f7f07c4297',
  'filter:format-unknown':
      '20a581174aba73a607529f95e9ac64c7bab8ae4b734123857943904911063498',
  'filter:metadata-complete':
      'c1e7b94fec41154451f432e6ec4fee45cc328d99dc14870daccd32f3fb95cf42',
  'filter:metadata-missing-any':
      '77c3380be71f3a85f8b7db56f9f5c4e0137bd103cd26b395599c929af0186b4f',
  'filter:metadata-missing-year':
      'f93bad36d375f36093db6ea71a729530ff7134af7f9f9d523fd4464d94e118c0',
  'filter:metadata-missing-genre':
      '72abfdec1bcc0519108b3b7a4848820c1c92ad8a0492659628734d85031d5979',
  'filter:metadata-missing-album-artist':
      '3b5743fbf4f4d13b5d57a65bd8af21da4d30952497b7cb9e7fbd3acde11c0b75',
  'filter:metadata-missing-track-number':
      '158abc213e9582c8b44c4a47f8a20b1ab725c29305c487ef9453b07cf4ef0049',
  'filter:metadata-missing-disc-number':
      '094fa8ee99dfeec526dec30579c843a8e744934d300ab9731fe34fbaa19ca14c',
  'filter:metadata-missing-artist':
      'c8ff629bee740faeaf60ee838a6f90e6288cd9e6676cf275b70d752762ea4223',
  'filter:metadata-incorrect-isrc-format':
      '5c2511afbacf5db25131475e31953c35d23689e5a8b927d3e24eea6201edcac1',
  'filter:metadata-missing-isrc':
      'bc88095a3e8763ab611aa75c4ac229e1e9d960e0e185a8e4b37213a8e0fa1712',
  'filter:metadata-missing-label':
      'e90aaaf4b44f7ea329cec9e12a8322f86cf32e55eb179a7a70e72335c3ed9d9a',
  'filter:metadata-missing-lyrics':
      'd694fe7268e5fe84a765d7207c0838e23297239fc32db2ea7df2355d49b44470',
  'filter:metadata-has-replaygain':
      'b21c4cb2a516c9af18a9466d2aa53b79bf1ebf229b125ff9817e0590cf36c08d',
  'filter:metadata-missing-replaygain':
      '06f3dd161c0e72d4cb97caf6770702424bb4cf44ddf5fc1e58ff8e0632f14e93',
  'filter:metadata-incomplete-album':
      '19e338547f0a3f69b0b6581f907d282d6def80f29f75eb517a920e10e6ca50d1',
  'filter:metadata-unknown-album-completeness':
      'f104d2fe68c37e1f72a369c1892a0ff4a8ae171026e456c3122c1e3c17d447e4',
  'filter:metadata-unknown-metadata':
      '3d1db2e41af6561b91d240dd95bd038c547f081e9a0de8eb3218e7d5bd47c915',
  'filter:single-track-albums':
      'fd100b534d73b81bc7d1755251cfc36cae29e537b0a4bc87825158373348fbe5',
  'filter:single-track-albums-incomplete':
      '19e338547f0a3f69b0b6581f907d282d6def80f29f75eb517a920e10e6ca50d1',
  'filter:combined':
      '88fb5f8b9587bdeefab9ef98f4eb0026a70422ee8332bb808b54839f74d3de50',
  'filter:combined-local':
      'ce12ad63bdd22f98ddc8ddf7eb7b34baacad3a0ca4bd25b7023135fedf227b5d',
};

/// Each shadow check of the snapshot SQL, with the row check it replaces.
final _rowChecks = {
  for (final includeLocal in [true, false])
    _normalized(
      albumInventorySql(includeLocal: includeLocal, shadowSnapshot: true),
    ): _normalized(
      albumInventorySql(includeLocal: includeLocal),
    ),
  for (final alias in ['l', 'candidate'])
    _normalized(sqlite.libraryNotShadowedSql(alias, snapshot: true)):
        _normalized(sqlite.libraryNotShadowedSql(alias)),
};
final _snapshotSql = <String>[];

/// Also issues the shadow-snapshot SQL, which must equal the pinned SQL once
/// each shadow check is put back to its row check.
Future<String> _digest(
  Future<void> Function(LibraryQueueStore store) issue,
) async {
  final queries = <String>[];
  for (final fts in [false, true]) {
    final db = _RecordingDatabase();
    await issue(LibraryQueueStore(db, historyFts: fts, localFts: fts));
    queries.addAll(db.queries);
    final snapshot = _RecordingDatabase();
    await issue(
      LibraryQueueStore(
        snapshot,
        historyFts: fts,
        localFts: fts,
        shadowSnapshot: true,
      ),
    );
    _snapshotSql.addAll(snapshot.queries);
    expect([
      for (final sql in snapshot.queries)
        _rowChecks.entries.fold(
          sql,
          (sql, check) => sql.replaceAll(check.key, check.value),
        ),
    ], db.queries);
  }
  return sha256.convert(utf8.encode(queries.join('\n---\n'))).toString();
}

void main() {
  test(
    'queue SQL is pinned for every sort mode, filter and page shape',
    () async {
      final actual = <String, String>{};
      for (final sort in _sortModes) {
        actual['sort:$sort'] = await _digest((store) async {
          for (final cursor in [null, _cursor4, _cursor5]) {
            final request = QueueLibraryDbQuery(sortMode: sort, cursor: cursor);
            await store.trackPage(request);
            await store.albumPage(request);
          }
          final paged = QueueLibraryDbQuery(
            sortMode: sort,
            limit: 20,
            offset: 40,
          );
          await store.trackPage(paged);
          await store.albumPage(paged);
        });
      }
      for (final MapEntry(key: name, value: build) in _filters.entries) {
        actual['filter:$name'] = await _digest((store) async {
          for (final (sort, cursor) in [
            ('latest', null),
            ('latest', _cursor4),
            ('size-desc', null),
            ('size-desc', _cursor5),
            ('album-asc', _cursor4),
          ]) {
            final request = build(sort, cursor);
            await store.trackPage(request);
            await store.albumPage(request);
            await store.counts(request);
            await store.artistPage(request);
          }
        });
      }
      final mismatches = [
        for (final MapEntry(:key, :value) in actual.entries)
          if (_expectedDigests[key] != value) "    '$key': '$value',",
      ];
      expect(
        mismatches,
        isEmpty,
        reason: 'Changed SQL digests:\n${mismatches.join('\n')}',
      );
      expect(_expectedDigests.keys, unorderedEquals(actual.keys));
      // No snapshot query probes a row's path keys against History.
      expect(
        _snapshotSql.where((sql) => sql.contains('history_path_keys')),
        isEmpty,
      );
      expect(
        _snapshotSql.where((sql) => sql.contains('library_shadowed')),
        isNotEmpty,
      );
    },
  );
}
