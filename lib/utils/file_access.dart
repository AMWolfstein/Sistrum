import 'dart:io';

import 'package:open_filex/open_filex.dart';
import 'package:mornye/services/network_storage_service.dart';
import 'package:mornye/services/platform_bridge.dart';
import 'package:mornye/services/source_deletion_events.dart';
import 'package:mornye/utils/mime_utils.dart';

class FileAccessStat {
  final int? size;
  final DateTime? modified;

  const FileAccessStat({this.size, this.modified});
}

/// Deletes [file] when it exists and swallows any error.
Future<void> deleteFileQuietly(File file) async {
  try {
    if (await file.exists()) await file.delete();
  } catch (_) {}
}

bool isContentUri(String? path) {
  return path != null && path.startsWith('content://');
}

bool isSameContentUri(String? first, String? second) {
  if (first == null || second == null) return false;
  if (first == second) return true;
  if (!isContentUri(first) || !isContentUri(second)) return false;

  String decode(String value) {
    try {
      return Uri.decodeFull(value);
    } catch (_) {
      return value;
    }
  }

  return decode(first) == decode(second);
}

/// Pattern matching CUE virtual path suffixes like #track01, #track12, etc.
final _cueTrackSuffix = RegExp(r'#track\d+$');

const cueVirtualTrackRequiresSplitMessage =
    'This CUE track is virtual. Use Split into Tracks first.';

/// Whether the path is a CUE virtual path (contains #trackNN suffix).
bool isCueVirtualPath(String? path) {
  return path != null && _cueTrackSuffix.hasMatch(path);
}

/// Strip the #trackNN suffix from a CUE virtual path to get the base .cue path.
/// Returns the path unchanged if it's not a CUE virtual path.
String stripCueTrackSuffix(String path) {
  return path.replaceFirst(_cueTrackSuffix, '');
}

Future<bool> fileExists(String? path) async {
  if (path == null || path.isEmpty) return false;
  // Network identities are resolved by the built-in player, not File.exists.
  if (path.startsWith('network://')) return true;
  final realPath = isCueVirtualPath(path) ? stripCueTrackSuffix(path) : path;
  if (isContentUri(realPath)) {
    // A phone's content URI can arrive with synced statistics; a computer has
    // no way to open one.
    return PlatformBridge.safExists(realPath);
  }
  return File(realPath).exists();
}

/// Bounded, tri-state checks for destructive library cleanup. Unknown results
/// retain their database rows. CUE tracks are checked against the backing file.
Future<Map<String, bool?>> fileExistenceByPath(List<String> paths) async {
  final realPaths = {for (final path in paths) path: stripCueTrackSuffix(path)};
  final results = <String, bool?>{};
  final unique = realPaths.values.toSet();
  final saf = unique.where(isContentUri).toList(growable: false);
  const batchSize = 64;
  for (var start = 0; start < saf.length; start += batchSize) {
    final end = start + batchSize < saf.length ? start + batchSize : saf.length;
    final batch = saf.sublist(start, end);
    try {
      results.addAll(await PlatformBridge.safExistsBatch(batch));
    } catch (_) {
      // Missing native method on an older build is also inconclusive.
    }
  }
  // Offline network paths remain inconclusive during destructive cleanup.
  final local = unique
      .where((path) => !isContentUri(path) && !path.startsWith('network://'))
      .toList();
  const concurrency = 16;
  for (var start = 0; start < local.length; start += concurrency) {
    final end = start + concurrency < local.length
        ? start + concurrency
        : local.length;
    await Future.wait(
      local.sublist(start, end).map((path) async {
        if (path.isEmpty) return;
        try {
          final stat = await File(path).stat();
          if (stat.type != FileSystemEntityType.notFound) {
            results[path] = true;
          } else {
            // Dart's stat also returns notFound for permission errors. Opening
            // exposes the OS error without reading the audio file's contents.
            final handle = await File(path).open();
            await handle.close();
            results[path] = true;
          }
        } on FileSystemException catch (error) {
          final code = error.osError?.errorCode;
          final absent = code == 2 || code == (20);
          results[path] = absent ? false : null;
        } catch (_) {
          results[path] = null;
        }
      }),
    );
  }
  return {for (final item in realPaths.entries) item.key: results[item.value]};
}

/// Deletes [path] and reports whether the file is confirmed absent afterward.
///
/// SAF providers are allowed to reject a delete request by returning `false`.
/// Callers that also remove a Library row must only do so when this returns
/// `true`, otherwise the app would hide a file that still exists on storage.
Future<bool> deleteFile(
  String? path, {
  SourceDeletionEvents? deletionEvents,
  NetworkStorageService? networkStorage,
}) async {
  if (path == null || path.isEmpty) return false;
  if (path.startsWith('EXISTS:')) {
    path = path.substring(7).trim();
    if (path.isEmpty) return false;
  }
  // CUE virtual paths should NOT be deleted through this function —
  // deleting album.cue would remove ALL tracks. Callers should handle
  // CUE deletion specially (e.g. only delete when all tracks are removed).
  if (isCueVirtualPath(path)) return false;
  bool confirmedAbsent;
  try {
    if (path.startsWith('network://')) {
      confirmedAbsent = await (networkStorage ?? NetworkStorageService.instance)
          .deleteFile(path);
    } else if (isContentUri(path)) {
      final deleted = await PlatformBridge.safDelete(path);
      confirmedAbsent = deleted || !await PlatformBridge.safExists(path);
    } else {
      final source = path;
      return await (deletionEvents ?? SourceDeletionEvents.instance).delete(
        source,
        () async {
          final file = File(source);
          if (await file.exists()) await file.delete();
          return !await file.exists();
        },
      );
    }
  } catch (_) {
    return false;
  }
  if (confirmedAbsent) {
    await (deletionEvents ?? SourceDeletionEvents.instance).publish(path);
  }
  return confirmedAbsent;
}

Future<FileAccessStat?> fileStat(String? path) async {
  if (path == null || path.isEmpty) return null;
  if (path.startsWith('network://')) return const FileAccessStat();
  final realPath = isCueVirtualPath(path) ? stripCueTrackSuffix(path) : path;
  if (isContentUri(realPath)) {
    final stat = await PlatformBridge.safStat(realPath);
    final exists = stat['exists'] as bool? ?? true;
    if (!exists) return null;
    return FileAccessStat(
      size: stat['size'] as int?,
      modified: stat['modified'] != null
          ? DateTime.fromMillisecondsSinceEpoch(stat['modified'] as int)
          : null,
    );
  }

  final stat = await FileStat.stat(realPath);
  if (stat.type == FileSystemEntityType.notFound) {
    // Dart stat hides every OS error behind notFound. A metadata-only lookup
    // exposes the errno without opening or copying audio on the normal path.
    try {
      return FileAccessStat(size: await File(realPath).length());
    } on FileSystemException catch (error) {
      final code = error.osError?.errorCode;
      if (code == 2 || code == (20)) return null;
      rethrow;
    }
  }
  return FileAccessStat(size: stat.size, modified: stat.modified);
}

Future<void> openFile(String path) async {
  if (isCueVirtualPath(path)) {
    throw Exception(cueVirtualTrackRequiresSplitMessage);
  }

  final realPath = path;
  if (isContentUri(realPath)) {
    await PlatformBridge.openContentUri(realPath, mimeType: '');
    return;
  }
  final mimeType = audioMimeTypeForPath(realPath);
  final result = await OpenFilex.open(realPath, type: mimeType);
  if (result.type != ResultType.done) {
    throw Exception(result.message);
  }
}
