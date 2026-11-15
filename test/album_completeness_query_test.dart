import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/services/album_completeness.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';

String _definition(String file, String table) => RegExp(
  'CREATE TABLE $table\\s*\\([\\s\\S]*?\\n\\s*\\)',
).firstMatch(File(file).readAsStringSync())!.group(0)!;

// Execute the actual page/count queries with their bound parameters in SQLite.
// Schema/bootstrap calls are isolated from this fixed inventory fixture.
class _QueryFixture {
  Future<List<Map<String, Object?>>> rawQuery(
    String sql, [
    List<Object?>? arguments,
  ]) async {
    final result = await Process.run('python3', [
      '-c',
      r'''
import json, sqlite3, sys
data = json.loads(sys.argv[1])
db = sqlite3.connect(':memory:')
db.row_factory = sqlite3.Row
db.execute("ATTACH DATABASE ':memory:' AS history_db")
db.execute(data['history'].replace('CREATE TABLE history', 'CREATE TABLE history_db.history', 1))
db.execute(data['library'])
db.execute('CREATE TABLE library_sources (id TEXT PRIMARY KEY, enabled INTEGER, available INTEGER)')
db.execute("INSERT INTO library_sources VALUES ('internal',1,1)")
db.execute('CREATE VIEW library_visible AS SELECT * FROM library')
for table in ['library_path_keys', 'history_db.history_path_keys']:
    db.execute('CREATE TABLE '+table+' (item_id TEXT, path_key TEXT)')
def add(local, id, album, number, total, depth=16):
    table = 'library' if local else 'history_db.history'
    fields = dict(id=id, track_name='Song '+str(number), artist_name='Artist',
      album_name=album, album_artist='Artist', album_key=album+'|artist',
      file_path='/'+id+'.flac', track_number=number, total_tracks=total,
      disc_number=1, total_discs=1, bit_depth=depth,
      search_text=(album+' song '+str(number)+' artist').lower(),
      sort_genre='', sort_release='', sort_added=1)
    fields.update(dict(scanned_at='2026-01-01', source_id='internal',
                       track_name_norm='song '+str(number), artist_name_norm='artist', album_name_norm=album,
                       album_artist_norm='artist') if local else
                  dict(downloaded_at='2026-01-01', service='example-provider',
                       sort_track='song '+str(number), sort_artist='artist', sort_album=album,
                       sort_album_artist='artist'))
    db.execute('INSERT INTO '+table+' ('+','.join(fields)+') VALUES ('+','.join('?' for _ in fields)+')', list(fields.values()))
    keys = 'library_path_keys' if local else 'history_db.history_path_keys'
    db.execute('INSERT INTO '+keys+' VALUES (?,?)', (id, fields['file_path']))
add(False, 'missing1', 'missing', 1, 4, depth=24)
add(False, 'missing2', 'missing', 2, 4)
add(False, 'complete1', 'complete', 1, 2, depth=24)
add(False, 'complete2', 'complete', 2, 2)
add(False, 'unknown1', 'unknown', 1, None)
add(False, 'one1', 'one-track', 1, 12)
add(False, 'mixed1', 'mixed', 1, 2)
add(True, 'mixed2', 'mixed', 2, 2)
add(True, 'local1', 'local-only', 1, 3)
add(True, 'local3', 'local-only', 3, 3)
print(json.dumps([dict(row) for row in db.execute(data['sql'], data['args'])]))
''',
      jsonEncode({
        'history': _definition('lib/services/history_schema.dart', 'history'),
        'library': _definition('lib/services/library_schema.dart', 'library'),
        'sql': sql,
        'args': arguments ?? [],
      }),
    ]);
    if (result.exitCode != 0) {
      throw StateError('${result.stderr}\nSQL: $sql\nArgs: $arguments');
    }
    return (jsonDecode(result.stdout as String) as List)
        .map((row) => Map<String, Object?>.from(row as Map))
        .toList();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    final font = FontLoader('Google Sans Flex')
      ..addFont(rootBundle.load('assets/fonts/GoogleSansFlex.ttf'));
    await font.load();
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    const channel = MethodChannel('plugins.flutter.io/path_provider');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    const sqliteChannel = MethodChannel('com.tekartik.sqflite');
    final versions = <int, int>{};
    final executor = _QueryFixture();
    messenger.setMockMethodCallHandler(sqliteChannel, (call) async {
      final args = (call.arguments as Map?) ?? {};
      if (call.method == 'openDatabase') {
        final id = versions.length + 1;
        versions[id] = (args['path'] as String).endsWith('history.db')
            ? HistoryDatabase.schemaVersion
            : LibraryDatabase.schemaVersion;
        return {'id': id};
      }
      final sql = args['sql'] as String? ?? '';
      if (call.method == 'execute') {
        if (sql.contains('CREATE VIRTUAL TABLE')) {
          throw PlatformException(
            code: 'sqlite_error',
            message: 'no such module: fts5',
          );
        }
        return null;
      }
      if (call.method != 'query') return null;
      final rows = sql.toLowerCase().contains('pragma user_version')
          ? <Map<String, Object?>>[
              {'user_version': versions[args['id']]},
            ]
          : await executor.rawQuery(
              sql,
              (args['arguments'] as List?)?.cast<Object?>(),
            );
      final columns = rows.isEmpty ? <String>[] : rows.first.keys.toList();
      return {
        'columns': columns,
        'rows': [
          for (final row in rows) [for (final column in columns) row[column]],
        ],
      };
    });
    addTearDown(() => messenger.setMockMethodCallHandler(sqliteChannel, null));
  });
  test(
    'production album/track/count queries filter full inventory and paginate consistently',
    () async {
      final db = LibraryDatabase.instance;
      const query = QueueLibraryDbQuery(
        filterMode: 'albums',
        metadata: incompleteAlbumFilter,
        sortMode: 'album-asc',
      );
      final albums = await db.getQueueAlbumPage(query);
      expect(albums.map((row) => row['album_name']), [
        'local-only',
        'missing',
        'one-track',
      ]);
      expect(albums.map((row) => row['album_present_tracks']), [2, 2, 1]);
      expect(albums.map((row) => row['album_expected_tracks']), [3, 4, 12]);
      final counts = await db.getQueueCounts(query);
      expect(counts.albumCount, 3);
      expect(counts.allTrackCount, 5);
      final tracks = await db.getQueueTrackPage(
        const QueueLibraryDbQuery(metadata: incompleteAlbumFilter),
      );
      expect(tracks, hasLength(5));
      expect(tracks.any((row) => row['albumName'] == 'mixed'), isFalse);
      final first = await db.getQueueAlbumPageResult(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          sortMode: 'album-asc',
          limit: 1,
        ),
      );
      final second = await db.getQueueAlbumPageResult(
        QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          sortMode: 'album-asc',
          limit: 1,
          cursor: first.nextCursor,
        ),
      );
      expect(first.rows.single['album_name'], 'local-only');
      expect(second.rows.single['album_name'], 'missing');
      final unknown = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: unknownAlbumCompletenessFilter,
        ),
      );
      expect(unknown.single['album_name'], 'unknown');
      expect(AlbumCompleteness.fromRow(unknown.single)!.badge, '?');
      final hiRes = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          quality: 'hires',
        ),
      );
      expect(hiRes.single['album_name'], 'missing');
      expect(hiRes.single['album_present_tracks'], 2);
      final searched = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          searchQuery: 'one-track',
        ),
      );
      expect(searched.single['album_name'], 'one-track');
      final local = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          source: 'local',
        ),
      );
      expect(local.single['album_name'], 'local-only');
      final noLocal = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(
          filterMode: 'albums',
          metadata: incompleteAlbumFilter,
          source: 'local',
          includeLocal: false,
        ),
      );
      expect(noLocal, isEmpty);
      final normal = await db.getQueueAlbumPage(
        const QueueLibraryDbQuery(filterMode: 'albums'),
      );
      expect(normal.every((row) => row['album_completeness'] == null), isTrue);
      expect(normal.any((row) => row['album_name'] == 'one-track'), isFalse);
    },
  );
}
