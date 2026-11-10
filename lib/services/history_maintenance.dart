import 'package:sqflite/sqflite.dart';
import 'package:mornye/models/download_history.dart';
import 'package:mornye/services/sqlite_helpers.dart' as sqlite;

class HistoryMaintenanceUpdate {
  const HistoryMaintenanceUpdate({
    required this.original,
    required this.updated,
  });

  final DownloadHistoryItem original;
  final DownloadHistoryItem updated;

  DownloadHistoryItem? mergeInto(DownloadHistoryItem current) {
    if (current.id != original.id ||
        current.filePath != original.filePath ||
        !current.downloadedAt.isAtSameMomentAs(original.downloadedAt)) {
      return null;
    }
    final before = original.toJson();
    final next = current.toJson();
    final keepLyrics =
        current.lyricsMetadataScanVersion != original.lyricsMetadataScanVersion;
    final keepReplayGain =
        current.replayGainMetadataScanVersion !=
        original.replayGainMetadataScanVersion;
    for (final field in updated.toJson().entries) {
      if (field.value == before[field.key] ||
          next[field.key] != before[field.key]) {
        continue;
      }
      if (keepLyrics &&
          (field.key == 'hasLyrics' ||
              field.key == 'lyricsMetadataScanVersion')) {
        continue;
      }
      if (keepReplayGain &&
          (field.key == 'hasReplayGain' ||
              field.key == 'replayGainMetadataScanVersion')) {
        continue;
      }
      next[field.key] = field.value;
    }
    return DownloadHistoryItem.fromJson(next);
  }
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
