import 'package:sqflite/sqflite.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sistrum/utils/ios_container_paths.dart';

/// Restored history can contain an older container even after this install's
/// startup migration ran. Update stored paths and their match keys together.
/// The version marker reruns the repair for installations without that marker,
/// even if their recorded container path already matches.
Future<int?> migrateIosHistoryPaths(
  Database db,
  SharedPreferences prefs, {
  required String containerPath,
  bool force = false,
}) async {
  const version = 1;
  const versionKey = 'ios_history_path_migration_version';
  if (!force &&
      prefs.getString('ios_last_container_path') == containerPath &&
      prefs.getInt(versionKey) == version) {
    return null;
  }

  final updated = await sqlite.transactionWithBusyRetry(db, (txn) async {
    var count = 0;
    String? afterId;
    while (true) {
      final rows = await txn.query(
        'history',
        columns: ['id', 'file_path'],
        where: afterId == null ? null : 'id > ?',
        whereArgs: afterId == null ? null : [afterId],
        orderBy: 'id',
        limit: 256,
      );
      if (rows.isEmpty) break;
      final batch = txn.batch();
      for (final row in rows) {
        final previous = row['file_path'] as String?;
        if (previous == null) continue;
        final current = rebaseIosSandboxPath(
          previous,
          '$containerPath/Documents',
        );
        if (current == previous) continue;
        final id = row['id'] as String;
        batch.update(
          'history',
          {'file_path': current},
          where: 'id = ?',
          whereArgs: [id],
        );
        sqlite.putPathKeysInBatch(batch, 'history_path_keys', id, current);
        count++;
      }
      await batch.commit(noResult: true);
      afterId = rows.last['id'] as String;
      if (rows.length < 256) break;
    }
    return count;
  });
  await prefs.setString('ios_last_container_path', containerPath);
  await prefs.setInt(versionKey, version);
  return updated;
}

/// Called inside a transaction. Delayed metadata/SAF probes must never insert
/// rows deleted by orphan cleanup while the probe was running.
Future<Set<String>> updateExistingHistoryRows(
  DatabaseExecutor db,
  Iterable<Map<String, dynamic>> rows,
) async {
  final updatedIds = <String>{};
  final batch = db.batch();
  for (final row in rows) {
    final id = row['id'] as String;
    final changed = await db.update(
      'history',
      row,
      where: 'id = ?',
      whereArgs: [id],
    );
    if (changed == 0) continue;
    updatedIds.add(id);
    sqlite.putPathKeysInBatch(
      batch,
      'history_path_keys',
      id,
      row['file_path'] as String?,
    );
  }
  if (updatedIds.isNotEmpty) await batch.commit(noResult: true);
  return updatedIds;
}
