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
      '7535da954cd81ccea0d6d17ff94880c6d7e72531179167088ed23a8e72ccc38c',
  'sort:oldest':
      '22c297b6a7ba7614d57992b8ca4172c8deba1f4113c8747fe2ff96748c14df67',
  'sort:a-z':
      '21d93e8359d7730b4597ab75d4b7780772ca4579258c965c96135592328f737e',
  'sort:z-a':
      'df42d781c554c4370bea21d44635cb32bb23fd1a6faedddad789d46eb7531775',
  'sort:artist-asc':
      'ae3bf3ccc74a5712bc5c4e9ff9d4b38fee40bf55d396d52f812ebe9d6ead7478',
  'sort:artist-desc':
      '92a512806cf4f1e34b53708c5c670f007658d7b8d62ffcba888dbcd49e08f981',
  'sort:album-asc':
      'f27c15d1107b1511d1a462f1b9f3685460c7c5cdd7e4ddcd60d3d92ddaa4e220',
  'sort:album-desc':
      '4bb703f48cdc48969c3ff0032783f97f2b81bfe0305390d64f056d19ef882576',
  'sort:release-oldest':
      'a1bb20850d0718b93bfce03173acbcb17ec2727cb4351c7b04135fd77ee68c02',
  'sort:release-newest':
      '8eaabfa8dc546e7932681a078a35e52e8aef703df915d9839eb570f32d5a07bd',
  'sort:genre-asc':
      'be4ba0ed4a707d0390af7145f210cfba2f81e4f62eb286af032aff5a73d1cc21',
  'sort:genre-desc':
      'e6140c76fbd03598c19972c37fa285e7cfddffade5e6b33b0e9eb841ccb8b804',
  'sort:size-asc':
      '7954a6e8293d60b83fd1b90289e731c97d3022624f9246176a5eeccac0428058',
  'sort:size-desc':
      'ed87459cc57a7dca5a85065535cf3e4a1166c6917b0ddc025a4e79c7906ccd0f',
  'sort:unknown-sort':
      '7535da954cd81ccea0d6d17ff94880c6d7e72531179167088ed23a8e72ccc38c',
  'filter:default':
      '309e13237177b645f725eb37105912368df3fcbe357615907f081d03a647df79',
  'filter:source-downloaded':
      '7adfb01d33ae6cfecaaad1fe96298a4ecc3acb3d3cbaed734e6ea5355955192d',
  'filter:source-local':
      '09a8aa5726079700497de04b799115a1c23ab8032c48c647694cb8c35cb21e12',
  'filter:source-other':
      '31eef3a5e4bacaca2596ab0f672368f595111ae4a53eaa8892561633244c4338',
  'filter:no-local':
      '6d901b42bd8b2661e6a0a39394ab7762168eddb746c8e2553c6ef37bad2aeb58',
  'filter:no-local-source-local':
      '38c5cb26d76e078cbc78f029f2521956a68473ae8f4da6d43f83294bd286004b',
  'filter:singles':
      'dbd45dd08707a25fbc55c9229089a2dbc4fbee00b0909c6a0065214a3fef4f02',
  'filter:singles-downloaded':
      '2221aaa71e640659817f47f2a908f1a43f9e9d5437873a7714e54ce13a569596',
  'filter:search':
      'b92fe53c99199e2ea0f022586aa9344ccef8e08543394a70344437a9bca6bdaf',
  'filter:album-artist':
      'a5346f40a1f99cc3ac5575c1407edcc61e2b64b51f1477351dd0fcafa33f3ba9',
  'filter:quality-hires':
      'c4dcb77247ed52d7d944d6a04933d37096173b13111e8f2892e578ff3cb080c8',
  'filter:quality-cd':
      'fc4662dacf83f27af75062c78e201efe80d33891cc22d64b4cf53036f06b538f',
  'filter:quality-lossy':
      '6535b00da8f9895583bc5a8384c4643ba936593737019abffe0de692bda3bcc8',
  'filter:format-m4a':
      'eef578c038e683fdfbc9cb0324533a0d8b4313058bacd3e09d2ead3404b16211',
  'filter:format-unknown':
      'e75d6bb004f6fdf06a81e1c4b2fa1ae3776572d933691001b2309da26fe82e71',
  'filter:metadata-complete':
      '9203f42a75055c2377471efd132e4b54df9d50a3a8a8988552fb9151e1d87a17',
  'filter:metadata-missing-any':
      '41c18f31185c4abf6713a201d34900d7bb664b459a07596b85c093e75def6cfa',
  'filter:metadata-missing-year':
      'e4e4679e8f23dd1c6b1069c5547447843ee8c94160aed971667eef882af6018f',
  'filter:metadata-missing-genre':
      '9e8e075a60d10bebe53534725ec89ece8b489faedbc24679564e8fd58f68c744',
  'filter:metadata-missing-album-artist':
      '21a3e941f66205f98e19861ba595041d87d7ae0f3206e098be51a19d74a7f64b',
  'filter:metadata-missing-track-number':
      '4f804efac24d231a74411e5ebd443b94bfd1d8e887c6d3ede08c0fa222841b00',
  'filter:metadata-missing-disc-number':
      'b9d823040b119e1eaefe7286c20e7dfe8603cd86a3addb1f4ca49a3b029dd8b3',
  'filter:metadata-missing-artist':
      '3b045fc01250c945ab8c946d73d885caf1f66ee73dd6bd02a99d06e2d8150185',
  'filter:metadata-incorrect-isrc-format':
      '1563653935fda1035b90a726b32dcdd8ec51134b6a205211f5e9434c85ba8984',
  'filter:metadata-missing-isrc':
      '4e464cb0ada2c9efbccc334b1f5dc18ed35a1bfe6b6d5c9caa3539e8f532c2c8',
  'filter:metadata-missing-label':
      '0d3652a2c9a7c8edff1723fd29a7de101fbfe177f91ff201821bdcc8fc380789',
  'filter:metadata-missing-lyrics':
      '0e76050130a59cea2c1c2b4e849691c32b5e4cb8268d81a120c6098a64179579',
  'filter:metadata-has-replaygain':
      '0fdf365e12db280479da8b9336e076bec1d006a8ad329800fe8e6e4b3afadb55',
  'filter:metadata-missing-replaygain':
      'e24d42d25c43de9100668916ce5ec23141efb7763147cf9971436bd4fa82497e',
  'filter:metadata-incomplete-album':
      '5cb0d8b716b5720763682c976d1f7abf932a16b9a5f04d98c4cbb2e47db7e66d',
  'filter:metadata-unknown-album-completeness':
      '389a2dda95f5e8fbac20c3f2e5db113590a6d77d0c2ccf32ec80748d95d5e811',
  'filter:metadata-unknown-metadata':
      '25828b8c42cad08f4099a920c4d9ce34cd7fb8096457cc111b089963d97f37fb',
  'filter:single-track-albums':
      '804dea09bfbc56c890ee3efe25e0ba635fe65dd54353a0c8e847e5d022b0a223',
  'filter:single-track-albums-incomplete':
      '5cb0d8b716b5720763682c976d1f7abf932a16b9a5f04d98c4cbb2e47db7e66d',
  'filter:combined':
      'c4fb57b7f221cb6f990b656ce52a73cc7d6653eb1a4f72260695e813207cd5b1',
  'filter:combined-local':
      'ba298ea7de986eab3c030b83e5476a5d356e82cec095c600c08427e5069e6118',
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
