import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/playback_metadata.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/string_utils.dart';

/// Why a saved file's fresh tags did not reach the library index.
enum MetadataIndexFailure { read, database, virtualTrack }

/// Whether a saved edit is browsable yet, and the refreshed row when it is.
class MetadataIndexResult {
  final MetadataIndexFailure? failure;
  final Object? error;
  final LocalLibraryItem? item;

  const MetadataIndexResult.indexed(LocalLibraryItem this.item)
    : failure = null,
      error = null;
  const MetadataIndexResult.stale(MetadataIndexFailure this.failure, this.error)
    : item = null;
}

/// Publishes a saved file's fresh tags and artwork to the library index.
///
/// The metadata writer only stores the file. This rereads its tags, maps them
/// onto the scanned row, refreshes the extracted cover and upserts the row, so
/// the caller only reloads the library and reports [MetadataIndexResult].
class MetadataIndexReconciler {
  final Future<Map<String, dynamic>> Function(String) read;
  final Future<Map<String, dynamic>> Function(String, String) extractCover;
  final Future<Directory> Function() supportDirectory;

  /// Upserts the row and returns how many rows Download History skipped.
  final Future<int> Function(
    Map<String, dynamic> json, {
    required String sourceId,
  })
  upsert;

  const MetadataIndexReconciler({
    this.read = readPlaybackFileMetadataWithRetry,
    this.extractCover = PlatformBridge.extractCoverToFile,
    this.supportDirectory = getApplicationSupportDirectory,
    this.upsert = _upsert,
  });

  static Future<int> _upsert(
    Map<String, dynamic> json, {
    required String sourceId,
  }) async {
    final result = await LibraryDatabase.instance.upsertBatchExcludingHistory([
      json,
    ], sourceId: sourceId);
    return result.skipped;
  }

  /// [tags] is the caller's fresh read of the file, reused instead of rereading.
  Future<MetadataIndexResult> reconcile(
    LocalLibraryItem item, {
    Map<String, dynamic>? tags,
  }) async {
    // A CUE track has no file of its own to reread.
    if (isCueVirtualPath(item.filePath)) {
      return const MetadataIndexResult.stale(
        MetadataIndexFailure.virtualTrack,
        null,
      );
    }
    final Map<String, dynamic> fresh;
    try {
      fresh = tags ?? await read(item.filePath);
    } catch (error) {
      return MetadataIndexResult.stale(MetadataIndexFailure.read, error);
    }
    if (fresh['error'] != null) {
      return MetadataIndexResult.stale(
        MetadataIndexFailure.read,
        fresh['error'],
      );
    }
    final json = _rowAfterEdit(item, fresh);
    final coverPath = await _refreshCover(item.filePath);
    // A failed extraction keeps the previous cover reference; the old image is
    // pruned only once the index no longer refers to it.
    if (coverPath != null) json['coverPath'] = coverPath;
    try {
      final skipped = await upsert(json, sourceId: item.sourceId);
      // A shadowed row was not written, so the library still shows the old tags.
      if (skipped > 0) {
        return MetadataIndexResult.stale(
          MetadataIndexFailure.database,
          StateError('Download History shadows ${item.filePath}'),
        );
      }
    } catch (error) {
      return MetadataIndexResult.stale(MetadataIndexFailure.database, error);
    }
    return MetadataIndexResult.indexed(LocalLibraryItem.fromJson(json));
  }

  /// Refreshes the editable tags and keeps the scanned identity and audio facts.
  static Map<String, dynamic> _rowAfterEdit(
    LocalLibraryItem item,
    Map<String, dynamic> tags,
  ) {
    final json = item.toJson();
    const text = {
      'title': 'trackName',
      'artist': 'artistName',
      'album': 'albumName',
      'album_artist': 'albumArtist',
      'date': 'releaseDate',
      'genre': 'genre',
      'isrc': 'isrc',
      'composer': 'composer',
      'label': 'label',
      'copyright': 'copyright',
    };
    const numbers = {
      'track_number': 'trackNumber',
      'total_tracks': 'totalTracks',
      'disc_number': 'discNumber',
      'total_discs': 'totalDiscs',
    };
    for (final field in text.entries) {
      if (tags.containsKey(field.key)) {
        json[field.value] = tags[field.key]?.toString() ?? '';
      }
    }
    for (final field in numbers.entries) {
      if (tags.containsKey(field.key)) {
        json[field.value] = int.tryParse(tags[field.key]?.toString() ?? '');
      }
    }
    json['explicit'] = parseExplicitFlag(tags['explicit']) ?? item.explicit;
    if (tags.containsKey('lyrics')) {
      json['hasLyrics'] = tags['lyrics']?.toString().trim().isNotEmpty ?? false;
    }
    return json;
  }

  Future<String?> _refreshCover(String filePath) async {
    try {
      final directory = Directory(
        '${(await supportDirectory()).path}/library_covers',
      );
      await directory.create(recursive: true);
      final path =
          '${directory.path}/edited_${DateTime.now().microsecondsSinceEpoch}.jpg';
      final cover = await extractCover(filePath, path);
      final file = File(path);
      if (cover['error'] == null &&
          await file.exists() &&
          await file.length() > 0) {
        return path;
      }
      if (await file.exists()) await file.delete();
    } catch (error) {
      AppLogger(
        'MetadataIndex',
      ).w('Saved tags but could not refresh artwork: $error');
    }
    return null;
  }
}
