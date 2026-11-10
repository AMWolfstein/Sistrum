import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:mornye/models/download_history.dart';
import 'package:mornye/models/library_database_models.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/services/cover_download_service.dart';
import 'package:mornye/services/folder_path_template.dart';
import 'package:mornye/services/network_storage_service.dart';
import 'package:mornye/services/platform_bridge.dart';
import 'package:mornye/utils/file_access.dart' show deleteFile;

/// A Library original from network storage, which this device can save.
class OfflineSong {
  const OfflineSong({
    required this.item,
    required this.songId,
    this.relativeDir,
    this.fileName,
  });

  final LocalLibraryItem item;

  final String songId;
  final String? relativeDir;
  final String? fileName;
}

abstract interface class OfflineTransport {
  /// Writes the original file of [songId] to [output]. [checkpoint] throws
  /// to cancel between chunks.
  Future<void> download(
    String songId,
    File output, {
    required void Function() checkpoint,
    required void Function(int received, int? total) progress,
  });

  /// Ends any download in flight and releases the connection.
  void close();
}

/// Where a saved file landed.
class OfflineStored {
  const OfflineStored({
    required this.filePath,
    required this.storageMode,
    this.treeUri,
    this.relativeDir,
    this.fileName,
  });

  /// A path, or a `content://` URI under Android's storage access.
  final String filePath;

  /// `app` or `saf`, as download history records it.
  final String storageMode;
  final String? treeUri;
  final String? relativeDir;
  final String? fileName;
}

/// The phone's download folder.
abstract interface class OfflineDestination {
  /// Copies [file] to [relativeDir]/[fileName] in the download folder,
  /// renaming it when the name is taken. Throws [OfflineNoFolder] when
  /// the folder is unavailable.
  Future<OfflineStored> store(
    File file, {
    required String relativeDir,
    required String fileName,
    required String mimeType,
  });
}

abstract interface class OfflineRollbackDestination
    implements OfflineDestination {
  Future<void> discard(OfflineStored stored);
}

/// The download folder is unavailable (no storage access granted), so no
/// song can be saved.
class OfflineNoFolder implements Exception {
  const OfflineNoFolder();

  @override
  String toString() => 'OfflineNoFolder: no storage access';
}

class OfflineProgress {
  const OfflineProgress({
    required this.done,
    required this.total,
    required this.title,
    this.received = 0,
    this.size,
  });

  /// Songs finished (saved, skipped or failed) before the current one.
  final int done;
  final int total;

  /// The song being saved.
  final String title;
  final int received;
  final int? size;

  /// The whole job, 0 to 1.
  double get fraction {
    if (total <= 0) return 1;
    final size = this.size;
    final part = size == null || size <= 0
        ? 0.0
        : (received / size).clamp(0.0, 1.0);
    return ((done + part) / total).clamp(0.0, 1.0);
  }
}

/// How one song of a [OfflineSaver.save] run ended.
enum OfflineOutcome {
  saved,

  /// This phone already had it.
  skipped,
  failed,
}

class OfflineResult {
  const OfflineResult({
    required this.saved,
    required this.failed,
    required this.cancelled,
    this.skipped = 0,
  });

  final int saved;
  final int skipped;
  final int failed;
  final bool cancelled;
}

/// Cancels a [OfflineSaver.save] run.
class OfflineCancel {
  bool _cancelled = false;
  void Function()? _onCancel;

  bool get cancelled => _cancelled;

  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    _onCancel?.call();
  }
}

class _Cancelled implements Exception {
  const _Cancelled();
}

/// Saves Library originals to this device as downloads: the original file goes
/// into the download folder and a history record with the song's tags makes
/// it a Library download straight away.
class OfflineSaver {
  OfflineSaver({
    required this.serverId,
    required this.openTransport,
    required this.destination,
    required this.record,
    required this.temporaryDirectory,
    this.exists,
    this.idPrefix = 'offline',
    this.serviceName = service,
    this.prepareSong,
    this.historyIdForSong,
    this.existsOrigin,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  /// The source namespace for the saved songs.
  final String serverId;

  /// A transport for one [save] run, closed when the run ends.
  final OfflineTransport Function() openTransport;
  final OfflineDestination destination;

  /// Adds the history record of a saved song.
  final Future<void> Function(DownloadHistoryItem item) record;
  final Future<Directory> Function() temporaryDirectory;

  /// Whether the history record with this id exists and its file is still
  /// there; such songs are skipped. Without it every song is fetched.
  final Future<bool> Function(String historyId)? exists;
  final String idPrefix;
  final String serviceName;
  final Future<OfflineSong> Function(OfflineSong song)? prepareSong;
  final String Function(OfflineSong song)? historyIdForSong;
  final Future<bool> Function(String origin)? existsOrigin;
  final DateTime Function() _now;

  static const service = 'Mornye';

  /// The history id of [songId] (contract section 13.2): the same song
  /// saved again replaces its record.
  String historyId(String songId) =>
      '${idPrefix}_${sha256.convert(utf8.encode('$serverId/$songId'))}';

  Future<bool> _has(OfflineSong song) async {
    final exists = this.exists;
    try {
      if (await existsOrigin?.call(song.item.filePath) ?? false) return true;
    } on Object {
      // Older records can still be found by their stable transfer id.
    }
    try {
      return await exists?.call(_songHistoryId(song)) ?? false;
    } on Object {
      return false;
    }
  }

  String _songHistoryId(OfflineSong song) =>
      historyIdForSong?.call(song) ?? historyId(song.songId);

  static String _extension(LocalLibraryItem item) {
    final format = item.format?.trim().toLowerCase() ?? '';
    if (RegExp(r'^[a-z0-9]{1,10}$').hasMatch(format)) return format;
    final suffix = p.extension(item.filePath).replaceFirst('.', '');
    return suffix.isEmpty || suffix == 'raw' ? 'audio' : suffix.toLowerCase();
  }

  /// `Album artist/Album`, or the artist alone without an album.
  static String relativeDirFor(LocalLibraryItem item) {
    final artist = (item.albumArtist?.trim().isNotEmpty ?? false)
        ? item.albumArtist!
        : item.artistName;
    final album = item.albumName.trim();
    return [
      FolderPathTemplate.sanitizeComponent(artist),
      if (album.isNotEmpty) FolderPathTemplate.sanitizeComponent(album),
    ].join('/');
  }

  /// `Title - Artist.ext`, like the default download file name.
  static String fileNameFor(LocalLibraryItem item) {
    final stem = FolderPathTemplate.sanitizeComponent(
      item.artistName.trim().isEmpty
          ? item.trackName
          : '${item.trackName} - ${item.artistName}',
    );
    return '$stem.${_extension(item)}';
  }

  DownloadHistoryItem _history(OfflineSong song, OfflineStored stored) {
    final item = song.item;
    final saf = stored.storageMode == 'saf';
    return DownloadHistoryItem(
      id: _songHistoryId(song),
      trackName: item.trackName,
      artistName: item.artistName,
      albumName: item.albumName,
      albumArtist: item.albumArtist,
      coverUrl: item.coverPath,
      filePath: stored.filePath,
      originFilePath: item.filePath,
      storageMode: stored.storageMode,
      downloadTreeUri: saf ? stored.treeUri : null,
      safRelativeDir: saf ? stored.relativeDir : null,
      safFileName: saf ? stored.fileName : null,
      service: serviceName,
      downloadedAt: _now(),
      isrc: item.isrc,
      spotifyId: item.id,
      trackNumber: item.trackNumber,
      totalTracks: item.totalTracks,
      discNumber: item.discNumber,
      totalDiscs: item.totalDiscs,
      duration: item.duration,
      releaseDate: item.releaseDate,
      bitDepth: item.bitDepth,
      sampleRate: item.sampleRate,
      bitrate: item.bitrate,
      format: item.format,
      genre: item.genre,
      composer: item.composer,
      label: item.label,
      copyright: item.copyright,
      explicit: item.explicit,
      hasLyrics: item.hasLyrics,
      lyricsMetadataScanVersion: item.hasLyrics ? 1 : 0,
      hasReplayGain: item.hasReplayGain,
    );
  }

  /// Saves [songs] one by one, skipping those this phone already has
  /// ([exists]). A song that fails is counted and the run goes on; [onSong]
  /// learns how each one ended, by its index. [cancel] stops the run and
  /// discards the song in flight. Throws [OfflineNoFolder] when the
  /// download folder is unavailable.
  Future<OfflineResult> save(
    List<OfflineSong> songs, {
    void Function(OfflineProgress progress)? onProgress,
    void Function(int index, OfflineOutcome outcome)? onSong,
    OfflineCancel? cancel,
  }) async {
    var saved = 0;
    var skipped = 0;
    var failed = 0;
    final transport = openTransport();
    cancel?._onCancel = transport.close;
    void checkpoint() {
      if (cancel?.cancelled ?? false) throw const _Cancelled();
    }

    try {
      final temp = await temporaryDirectory();
      for (var index = 0; index < songs.length; index++) {
        if (cancel?.cancelled ?? false) break;
        final song = songs[index];
        final done = index;
        final title = song.item.trackName;
        onProgress?.call(
          OfflineProgress(done: done, total: songs.length, title: title),
        );
        if (await _has(song)) {
          if (cancel?.cancelled ?? false) break;
          skipped++;
          onSong?.call(index, OfflineOutcome.skipped);
          continue;
        }
        final file = File(
          p.join(
            temp.path,
            'offline_${Random.secure().nextInt(1 << 32)}'
            '.${_extension(song.item)}',
          ),
        );
        OfflineOutcome outcome;
        OfflineStored? published;
        var recorded = false;
        try {
          final prepared = await prepareSong?.call(song) ?? song;
          checkpoint();
          await transport.download(
            song.songId,
            file,
            checkpoint: checkpoint,
            progress: (received, size) => onProgress?.call(
              OfflineProgress(
                done: done,
                total: songs.length,
                title: title,
                received: received,
                size: size,
              ),
            ),
          );
          checkpoint();
          final fileName = prepared.fileName ?? fileNameFor(song.item);
          final stored = await destination.store(
            file,
            relativeDir: prepared.relativeDir ?? relativeDirFor(song.item),
            fileName: fileName,
            mimeType: networkContentType(fileName),
          );
          published = stored;
          await record(_history(song, stored));
          recorded = true;
          saved++;
          outcome = OfflineOutcome.saved;
        } on OfflineNoFolder {
          rethrow;
        } on OfflineCopyAlreadySaved {
          skipped++;
          outcome = OfflineOutcome.skipped;
        } on Object {
          if (cancel?.cancelled ?? false) break;
          failed++;
          outcome = OfflineOutcome.failed;
        } finally {
          if (!recorded &&
              published != null &&
              destination is OfflineRollbackDestination) {
            try {
              await (destination as OfflineRollbackDestination).discard(
                published,
              );
            } catch (_) {
              // Cleanup must not hide the original persistence failure.
            }
          }
          try {
            if (await file.exists()) await file.delete();
          } on FileSystemException {
            // A leftover temporary file is cleaned with the cache.
          }
        }
        onSong?.call(index, outcome);
      }
    } finally {
      cancel?._onCancel = null;
      transport.close();
    }
    onProgress?.call(
      OfflineProgress(
        done: saved + skipped + failed,
        total: songs.length,
        title: '',
      ),
    );
    return OfflineResult(
      saved: saved,
      skipped: skipped,
      failed: failed,
      cancelled: cancel?.cancelled ?? false,
    );
  }
}

/// [OfflineDestination] for the download folder the settings choose:
/// Android's storage access tree in `saf` mode, else the download directory
class DownloadFolderDestination implements OfflineRollbackDestination {
  const DownloadFolderDestination(this.settings);

  final AppSettings settings;

  @override
  Future<void> discard(OfflineStored stored) async {
    try {
      await deleteFile(stored.filePath);
    } finally {
      
    }
  }

  @override
  Future<OfflineStored> store(
    File file, {
    required String relativeDir,
    required String fileName,
    required String mimeType,
  }) async {
    if (Platform.isAndroid && settings.storageMode == 'saf') {
      final treeUri = settings.downloadTreeUri.trim();
      if (treeUri.isEmpty) throw const OfflineNoFolder();
      final result = await PlatformBridge.createUniqueSafFileFromPath(
        treeUri: treeUri,
        relativeDir: relativeDir,
        fileName: fileName,
        mimeType: mimeType,
        srcPath: file.path,
      );
      final error = result['error']?.toString().trim() ?? '';
      if (error.isNotEmpty) throw FileSystemException(error);
      final uri = result['uri']?.toString().trim() ?? '';
      final name = result['file_name']?.toString().trim() ?? '';
      if (uri.isEmpty || name.isEmpty) {
        throw const FileSystemException('Failed to write to storage');
      }
      return OfflineStored(
        filePath: uri,
        storageMode: 'saf',
        treeUri: treeUri,
        relativeDir: relativeDir,
        fileName: name,
      );
    }
    try {
      var root = settings.downloadDirectory.trim();
      if (root.isEmpty) {
        root = p.join(
          (await getApplicationDocumentsDirectory()).path,
          'Mornye',
        );
      }
      final directory = Directory(p.joinAll([root, ...relativeDir.split('/')]));
      await directory.create(recursive: true);
      final target = await CoverDownloadService.uniqueFilePath(
        directory.path,
        fileName,
      );
      await file.copy(target);
      return OfflineStored(filePath: target, storageMode: 'app');
    } finally {
      
    }
  }
}
