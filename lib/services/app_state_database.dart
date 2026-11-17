import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sistrum/services/playback_resume_store.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sistrum/services/sqlite_long_text.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('AppStateDb');

const _dbFileName = 'app_state.db';
const _dbVersion = 6;

const _playbackSessionTable = 'playback_session';

class AppStateDatabase {
  static final AppStateDatabase instance = AppStateDatabase._init();
  static final sqlite.SingleFlightInitializer<Database> _database =
      sqlite.SingleFlightInitializer<Database>();

  final Database? _databaseOverride;

  AppStateDatabase._init() : _databaseOverride = null;

  /// Keeps scoped playback fixtures on their own native SQLite connection.
  @visibleForTesting
  AppStateDatabase.withDatabase(Database database)
    : _databaseOverride = database;

  /// Opens a private file with the same state and resume schemas as the app.
  @visibleForTesting
  static Future<Database> openWithFactory(
    DatabaseFactory factory,
    String path,
  ) => factory.openDatabase(
    path,
    options: OpenDatabaseOptions(
      version: _dbVersion,
      onCreate: _createDb,
      onUpgrade: _upgradeDb,
    ),
  );

  Future<Database> get database {
    final override = _databaseOverride;
    if (override != null) return Future.value(override);
    return _database.getOrCreate(_initDb);
  }

  Future<Database> _initDb() {
    return sqlite.openAppDatabase(
      _dbFileName,
      version: _dbVersion,
      incrementalAutoVacuum: false,
      onCreate: _createDb,
      onUpgrade: _upgradeDb,
    );
  }

  static Future<void> _createDb(Database db, int version) async {
    _log.i('Creating app state database schema v$version');

    await _createPlaybackSessionTable(db);
    await PlaybackResumeStore.createSchema(db);
  }

  static Future<void> _upgradeDb(
    Database db,
    int oldVersion,
    int newVersion,
  ) async {
    _log.i('Upgrading app state database from v$oldVersion to v$newVersion');
    if (oldVersion < 4) {
      await _migratePlaybackSessionToV4(db);
    }
    if (oldVersion < 5) {
      await PlaybackResumeStore.createSchema(db);
    }
    if (oldVersion < 6) {
      // The download queue table left with the download engine.
      await db.execute('DROP TABLE IF EXISTS download_queue_items');
    }
  }

  static Future<void> _createPlaybackSessionTable(Database db) {
    return db.execute('''
      CREATE TABLE IF NOT EXISTS $_playbackSessionTable (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        media_json TEXT NOT NULL,
        current_index INTEGER NOT NULL DEFAULT 0,
        position_ms INTEGER NOT NULL DEFAULT 0,
        shuffle INTEGER NOT NULL DEFAULT 0,
        repeat_mode TEXT NOT NULL DEFAULT 'none',
        updated_at TEXT NOT NULL
      )
    ''');
  }

  static Future<void> _migratePlaybackSessionToV4(Database db) async {
    final table = await db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
      [_playbackSessionTable],
    );
    if (table.isEmpty) {
      await _createPlaybackSessionTable(db);
      return;
    }

    final columns = await db.rawQuery(
      'PRAGMA table_info($_playbackSessionTable)',
    );
    if (columns.any((column) => column['name'] == 'media_json')) return;

    Map<String, dynamic>? legacySession;
    final rows = await db.query(_playbackSessionTable, limit: 1);
    final raw = rows.isEmpty ? null : rows.first['session_json'] as String?;
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map) {
          legacySession = Map<String, dynamic>.from(decoded);
        }
      } catch (e) {
        _log.w('Discarding unreadable legacy playback session: $e');
      }
    }

    const migratedTable = '${_playbackSessionTable}_v4';
    await db.execute('DROP TABLE IF EXISTS $migratedTable');
    await db.execute('''
      CREATE TABLE $migratedTable (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        media_json TEXT NOT NULL,
        current_index INTEGER NOT NULL DEFAULT 0,
        position_ms INTEGER NOT NULL DEFAULT 0,
        shuffle INTEGER NOT NULL DEFAULT 0,
        repeat_mode TEXT NOT NULL DEFAULT 'none',
        updated_at TEXT NOT NULL
      )
    ''');
    if (legacySession != null) {
      await db.insert(migratedTable, _playbackSessionRow(legacySession));
    }
    await db.execute('DROP TABLE $_playbackSessionTable');
    await db.execute(
      'ALTER TABLE $migratedTable RENAME TO $_playbackSessionTable',
    );
  }

  static Map<String, Object?> _playbackSessionRow(
    Map<String, dynamic> session,
  ) {
    final media = session['media'];
    final currentIndex = session['index'];
    final positionMs = session['positionMs'];
    final repeatMode = session['repeat'];
    return {
      'id': 1,
      'media_json': jsonEncode(media is List ? media : const []),
      'current_index': currentIndex is num ? currentIndex.toInt() : 0,
      'position_ms': positionMs is num ? positionMs.toInt() : 0,
      'shuffle': session['shuffle'] == true ? 1 : 0,
      'repeat_mode': repeatMode is String ? repeatMode : 'none',
      'updated_at': DateTime.now().toIso8601String(),
    };
  }

  /// The playback session is tied to the installation's grants: its sources
  /// may be content URIs granted to the old installation, so it must not
  /// resume after Android restores data into a newly installed package.
  Future<Map<String, dynamic>?> getPlaybackSession() async {
    final stored = await readJsonTextRow(
      await database,
      table: _playbackSessionTable,
      jsonColumn: 'media_json',
      columns: const ['current_index', 'position_ms', 'shuffle', 'repeat_mode'],
    );
    if (stored == null) return null;
    final row = stored.row;
    if ((row['media_json_bytes'] as int? ?? 0) == 0) return null;
    try {
      final decoded = stored.json();
      if (decoded is! List) return null;
      return {
        'version': 2,
        'media': decoded,
        'index': (row['current_index'] as num?)?.toInt() ?? 0,
        'positionMs': (row['position_ms'] as num?)?.toInt() ?? 0,
        'shuffle': (row['shuffle'] as num?)?.toInt() == 1,
        'repeat': row['repeat_mode'] as String? ?? 'none',
      };
    } catch (e) {
      _log.w('Discarding unreadable playback session: $e');
    }
    return null;
  }

  /// Replaces the saved session in one statement. [mediaJson] is the UTF-8
  /// JSON of its media list, stored as the same `media_json` text.
  Future<void> savePlaybackSession({
    required Uint8List mediaJson,
    required int index,
    required int positionMs,
    required bool shuffle,
    required String repeatMode,
  }) async => writePlaybackSession(
    await database,
    mediaJson: mediaJson,
    index: index,
    positionMs: positionMs,
    shuffle: shuffle,
    repeatMode: repeatMode,
  );

  @visibleForTesting
  static Future<void> writePlaybackSession(
    Database db, {
    required Uint8List mediaJson,
    required int index,
    required int positionMs,
    required bool shuffle,
    required String repeatMode,
  }) async {
    // Bytes cross the platform channel as one copy; SQLite reads them as
    // text in the database encoding, which only UTF-8 matches.
    final utf8Text = _utf8Databases[db] ??=
        (await db.rawQuery('PRAGMA encoding')).single.values.single == 'UTF-8';
    await db.rawInsert(
      'INSERT OR REPLACE INTO $_playbackSessionTable '
      '(id, media_json, current_index, position_ms, shuffle, repeat_mode, '
      'updated_at) '
      'VALUES (1, ${utf8Text ? 'CAST(? AS TEXT)' : '?'}, ?, ?, ?, ?, ?)',
      [
        utf8Text ? mediaJson : utf8.decode(mediaJson),
        index,
        positionMs,
        shuffle ? 1 : 0,
        repeatMode,
        DateTime.now().toIso8601String(),
      ],
    );
  }

  static final _utf8Databases = Expando<bool>('UTF-8 database');

  Future<bool> updatePlaybackSessionState({
    required int index,
    required int positionMs,
    required bool shuffle,
    required String repeatMode,
  }) async {
    final db = await database;
    final changed = await db.update(_playbackSessionTable, {
      'current_index': index,
      'position_ms': positionMs,
      'shuffle': shuffle ? 1 : 0,
      'repeat_mode': repeatMode,
      'updated_at': DateTime.now().toIso8601String(),
    }, where: 'id = 1');
    return changed > 0;
  }

  Future<void> clearPlaybackSession() async {
    final db = await database;
    await db.delete(_playbackSessionTable);
  }

  Future<void> archivePlaybackSession(Map<String, dynamic> media) async =>
      PlaybackResumeStore(await database).archive(media);

  Future<Map<String, dynamic>?> getPlaybackResumePreview() async =>
      PlaybackResumeStore(await database).preview();

  Future<Map<String, dynamic>?> getPlaybackResumeSession() async =>
      PlaybackResumeStore(await database).session();

  Future<void> clearPlaybackResume() async =>
      PlaybackResumeStore(await database).clear();

  Future<void> relocatePlaybackSource(
    String previous,
    String replacement,
  ) async =>
      PlaybackResumeStore(await database).relocateSource(previous, replacement);

}
