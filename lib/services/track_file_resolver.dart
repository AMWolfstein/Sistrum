import 'package:sistrum/models/track.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/file_access.dart';

/// The Library file each of [tracks] plays from, or null when it has none.
/// Matches are read only while [includeLocal] (Local Library is on).
Future<List<String?>> resolveTrackFiles(
  List<Track> tracks, {
  required LibraryDatabase library,
  required bool includeLocal,
}) async {
  if (tracks.isEmpty) return const <String?>[];
  final localMatches = includeLocal
      ? await library.findExistingBatch([
          for (final track in tracks)
            LocalLibraryBatchLookupRequest(
              id: (track.source ?? '').toLowerCase() == 'local'
                  ? track.id
                  : null,
              isrc: track.isrc,
              trackName: track.name,
              artistName: track.artistName,
            ),
        ])
      : List<Map<String, dynamic>?>.filled(tracks.length, null);

  final results = List<String?>.filled(tracks.length, null);
  final existsChecks = <String, Future<bool>>{};
  final safPaths = <String>{
    for (final row in localMatches)
      if (isContentUri(stripCueTrackSuffix(row?['filePath']?.toString() ?? '')))
        stripCueTrackSuffix(row!['filePath'].toString()),
  }.toList(growable: false);
  for (var start = 0; start < safPaths.length; start += 64) {
    final batch = safPaths.sublist(
      start,
      (start + 64).clamp(0, safPaths.length),
    );
    try {
      final checks = await PlatformBridge.safExistsBatch(batch);
      for (final path in batch) {
        final exists = checks[path];
        if (exists != null) existsChecks[path] = Future.value(exists);
      }
    } catch (_) {
      // Older bindings may lack the batch API; the per-file check still runs.
    }
  }
  var next = 0;
  final workerCount = tracks.length < 8 ? tracks.length : 8;

  Future<bool> pathExists(String path) {
    final realPath = stripCueTrackSuffix(path);
    return existsChecks.putIfAbsent(realPath, () => fileExists(path));
  }

  Future<void> worker() async {
    while (true) {
      final index = next++;
      if (index >= tracks.length) return;
      final localPath = localMatches[index]?['filePath']?.toString() ?? '';
      if (localPath.isNotEmpty && await pathExists(localPath)) {
        results[index] = localPath;
      }
    }
  }

  await Future.wait(List.generate(workerCount, (_) => worker()));
  return results;
}
