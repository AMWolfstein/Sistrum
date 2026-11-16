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
      '32ffe03bb221e2fc726157498930566e85940a10bf20aceba69514b433a0e6d5',
  'sort:oldest':
      'ba51c4e62b17d822161346ee6ced68794b782fcb5cbafe4fb0e4c08c5f18a4ee',
  'sort:a-z':
      '6dc7e3f325b8a9f2ae9444234f275fb0567fdb9ff2fcc65dc102645b2bcfe72e',
  'sort:z-a':
      '8d1361fc81213bf98e8ee6c1375771173f7b3a6f1cc7bc680ae204c0f72c520c',
  'sort:artist-asc':
      'e535993aec4175f1e00d6e74d0d4ee5d29e8b968d50c0c71299f4261ad0805a2',
  'sort:artist-desc':
      '60fc7f31e3f897fbccea4a00e41440e2dc5f32b02f54f7af3b5af9ee443912b1',
  'sort:album-asc':
      'a987ba7eb7473f3b1548fcae81b762ec2944443fadd3663a5eec23dc262481fc',
  'sort:album-desc':
      'b990d8b5ef7bf71f731469caf3d7018ac4c023f9c5711e2ea118b34313032c4a',
  'sort:release-oldest':
      '04a5e44291f4f04e2a2c4f1b942d48173fbf7cacf71ffe12dd5d1e3ef9dd1d7d',
  'sort:release-newest':
      '63d219f44e5a2c3c67347adbc3635b74c6025c3dd6f35b26bda3612cadcc3417',
  'sort:genre-asc':
      'aaae49c02f9eca25ee01161414e899aca00b3a184c1f6cce67c1f431ed89ecd6',
  'sort:genre-desc':
      'a8658b4c0661dd131b9ba5fad68817d4163772453ca727b43506a1a3d91d8675',
  'sort:size-asc':
      '85218343e8e68e7c05d84bff284463fee4501e3b804f547b1957b24434dee79e',
  'sort:size-desc':
      '00ef42759ab1af305bda3c4a1875038b946b181185b1bb4a46fa043460e3c771',
  'sort:unknown-sort':
      '32ffe03bb221e2fc726157498930566e85940a10bf20aceba69514b433a0e6d5',
  'filter:default':
      '588fbdfe505f8e2e28eb49c0c8eb9601c113b56711acc827d5be62eec261e110',
  'filter:source-downloaded':
      'bcf0687b3fb8a4345935596127156c48702f86d79e57d707e79223d09fe9e45a',
  'filter:source-local':
      '05bf19117ef43e986e092eed6b17021d1eac845f547bf4032563234fa92bdd6f',
  'filter:source-other':
      '783acd72fc23be72a0ca6393994089dbfa849eb1b7473c8ad06db4926aa37dba',
  'filter:no-local':
      '96a532054acdff17e9c0fcd48c29607446648ab8bb260132a86c80c05e87d997',
  'filter:no-local-source-local':
      'f21523a21af020542ee3bfb4f4221caa1c76254807dd44ce84ed8a23f8d03f93',
  'filter:singles':
      'd6852e9e0fd84f0a6b7cf8f4bd275961d3eec58a66cb8b6923fe38e0f6d49a27',
  'filter:singles-downloaded':
      '9c3ce8225c4286e4030b2fe08c1b0eaf01f70499405e3ebb042921da184c8890',
  'filter:search':
      '0f8692f517d9500cdf118f2befd974ff4adce4e937965b9895d648cc424e394d',
  'filter:album-artist':
      'e7a8ad23f7a2ef3a104e71a1f6c8ee9b2339fe7dab3cfcffbf8abf61ecc51ba9',
  'filter:quality-hires':
      '46a8cb87e87ee62d7b6c66dc3923e1e73485546a2cab087282f713c5b6e8497a',
  'filter:quality-cd':
      '9fdf0e47e6bbd36d8b52ac41b5e34fb3630ff6439e9ddf7e9f39f90dabcaae39',
  'filter:quality-lossy':
      '3cd033b57e9070395fcab55c91baee6fb71ac7705e149949ca9471ac02dd8b1b',
  'filter:format-m4a':
      'cc0834da2178c5292eec12066684da0091f60e432a802d6d4fcdf71b94375c3d',
  'filter:format-unknown':
      '62224678a922f14eb44be37198377e8600fc6504c9c3cda2ddeca4214a1ff54a',
  'filter:metadata-complete':
      '30eefb58cca3ffbfbc9739e7c21200666358dc38f28c56d4c0c7df198f7a45cf',
  'filter:metadata-missing-any':
      'fca5b77d85db26807f8e754de4eb1051fd32977803e49f2a8d22ba4c81eed0b3',
  'filter:metadata-missing-year':
      '537fdf96ff6d2855b54708e96b9582f28ab44efd3ea0392f6277b75647ac950d',
  'filter:metadata-missing-genre':
      '892912bb0ee18a04c5626c517e52b998b81961d0285faf1aab12d7e17eeb618d',
  'filter:metadata-missing-album-artist':
      '3b16c825374b2794368b2c2fb5dea4fed36610bb7129cb5faf605430f9e703e8',
  'filter:metadata-missing-track-number':
      'a30ac80991de2f5dd01f7d51893a7becf0e55aabcfcfe99ec37ea0eb6a8b3e0b',
  'filter:metadata-missing-disc-number':
      '8af211ee809163dda4f573ca73c306418b7d0bfbca48bb57de94cf1bc555f4a0',
  'filter:metadata-missing-artist':
      '2c5f438384811ac2688495a441d4e327fb62bd7f745d1a7a2d99fc2513a44bbe',
  'filter:metadata-incorrect-isrc-format':
      '5587aa9fc740386bab925aba63a7279a06bd63dd695c91bf441fd2d518b3ff35',
  'filter:metadata-missing-isrc':
      'cffe985e03149038be776cda5fc8b66ad67faf336912002c4b5da9981cde6c6b',
  'filter:metadata-missing-label':
      'e0ccf58e56fb3c389e77d24d6ed9b5d0dbd8fb5b65b1affff4a2a1bb222ba9cb',
  'filter:metadata-missing-lyrics':
      'eaeed025f63fbf471bd3bee3ee605d839035ab76a9da602828e15da5bbecb029',
  'filter:metadata-has-replaygain':
      '1c6414ef775ee02fcddd82d9ed9e58be033864333a2f2126f93ed60a91a4c836',
  'filter:metadata-missing-replaygain':
      'fa4beb256c70e92c0db69cf291e9dc1d95305b3dad546e961c7056b016ae879c',
  'filter:metadata-incomplete-album':
      'dbe5b04dd8db92ec4179b828f8372aa1923c38e33eaf08447bacf82f03c53e32',
  'filter:metadata-unknown-album-completeness':
      'f774eb5d629f9e40d3e91d50cc4e16524c375de28bce3318d711819f36cc79ba',
  'filter:metadata-unknown-metadata':
      'adcc6c742e3e6003d23425bc3617a72c1db949223a825e761627ec6e3543f976',
  'filter:single-track-albums':
      'f7c4131a9762c5cd6dab93fad84a4577220036df832555bebcff43dee227f7ce',
  'filter:single-track-albums-incomplete':
      'dbe5b04dd8db92ec4179b828f8372aa1923c38e33eaf08447bacf82f03c53e32',
  'filter:combined':
      '23a99ebca4c6b1e0b6e03fdeb9e97e9b8d0a0098c207e335e2ed2c2e46725c8b',
  'filter:combined-local':
      'd7c9aa378ceae1cca072ef96602a5e54695736bc90e9d2c4a7cd91dfd938761e',
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
