import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import 'package:sistrum/services/lyrics_sidecar.dart' show mlrcExtension;
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('SongLeftovers');

/// An entry of a folder holding songs. [id] is its path, or its uri for an
/// Android document.
typedef SongFolderEntry = ({String name, String id, bool folder});

abstract interface class SongFolderStore {
  /// The entries of [folder], or null when it cannot be listed.
  Future<List<SongFolderEntry>?> list(String folder);

  /// Deletes the file [id]; false when it stays.
  Future<bool> delete(String id);
}

/// Folders and files on a plain path.
class LocalSongFolderStore implements SongFolderStore {
  const LocalSongFolderStore();

  @override
  Future<List<SongFolderEntry>?> list(String folder) async {
    try {
      final entries = <SongFolderEntry>[];
      await for (final entity in Directory(folder).list(followLinks: false)) {
        entries.add((
          name: p.basename(entity.path),
          id: entity.path,
          // A link counts as a folder, so it always keeps its folder.
          folder: entity is! File,
        ));
      }
      return entries;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<bool> delete(String id) async {
    try {
      await File(id).delete();
      return true;
    } catch (_) {
      return false;
    }
  }
}

/// Folders and files in an Android storage access tree.
class DocumentSongFolderStore implements SongFolderStore {
  const DocumentSongFolderStore();

  @override
  Future<List<SongFolderEntry>?> list(String folder) async {
    final entries = await PlatformBridge.listSafFolder(folder);
    return entries
        ?.map(
          (entry) => (name: entry.name, id: entry.uri, folder: entry.folder),
        )
        .toList();
  }

  @override
  Future<bool> delete(String id) async {
    try {
      return await PlatformBridge.safDelete(id);
    } catch (_) {
      return false;
    }
  }
}

/// Where a song sits, found before it is deleted.
class _SongPlace {
  const _SongPlace({
    required this.store,
    required this.name,
    required this.folder,
  });

  final SongFolderStore store;

  final String name;

  final String folder;
}

final songLeftoversProvider = Provider<SongLeftovers>(SongLeftovers.new);

/// Deletes the songs the user removes together with their `.lrc` and `.mlrc`
/// lyrics files. Folders are never removed. CUE tracks are only deleted.
class SongLeftovers {
  SongLeftovers(
    this._ref, {
    Future<bool> Function(String? source)? deleteSong,
    SongFolderStore files = const LocalSongFolderStore(),
    SongFolderStore documents = const DocumentSongFolderStore(),
    Future<({String name, List<({String uri, bool root})> folders})?> Function(
      String source,
    )?
    documentPlace,
  }) : _deleteSong = deleteSong ?? deleteFile,
       _files = files,
       _documents = documents,
       _documentPlace = documentPlace ?? PlatformBridge.safSongFolders {
    _ref.onDispose(dispose);
  }

  final Ref _ref;
  final Future<bool> Function(String? source) _deleteSong;
  final SongFolderStore _files;
  final SongFolderStore _documents;
  final Future<({String name, List<({String uri, bool root})> folders})?>
  Function(String source)
  _documentPlace;

  var _disposed = false;

  /// Deletes the song at [source] as [deleteFile] does, then its lyrics
  /// files. Returns whether the song is gone.
  Future<bool> delete(String? source) async {
    final place = await _placeOf(source);
    if (!await _deleteSong(source)) return false;
    if (place == null || _disposed) return true;
    try {
      await _deleteLyricsFiles(place);
    } catch (error) {
      _log.w('Could not delete the lyrics files of ${place.name}: $error');
    }
    return true;
  }

  Future<_SongPlace?> _placeOf(String? source) async {
    if (source == null) return null;
    final path = source.startsWith('EXISTS:')
        ? source.substring(7).trim()
        : source;
    if (path.isEmpty || isCueVirtualPath(path)) {
      return null;
    }
    try {
      if (isContentUri(path)) {
        final place = await _documentPlace(path);
        if (place == null || place.folders.isEmpty) return null;
        return _SongPlace(
          store: _documents,
          name: place.name,
          folder: place.folders.first.uri,
        );
      }
      final uri = Uri.tryParse(path);
      final file = uri?.scheme == 'file' ? uri!.toFilePath() : path;
      if (!p.isAbsolute(file)) return null;
      final folder = p.dirname(p.normalize(file));
      return _SongPlace(store: _files, name: p.basename(file), folder: folder);
    } catch (error) {
      _log.w('Could not find the folder of $path: $error');
      return null;
    }
  }

  /// The song's own `.lrc` and `.mlrc` files, unless another song in the
  /// folder has the same name.
  Future<void> _deleteLyricsFiles(_SongPlace place) async {
    final entries = await place.store.list(place.folder);
    if (entries == null) return;
    final stem = _stem(place.name);
    final files = entries.where((entry) => !entry.folder);
    if (files.any((entry) => _stem(entry.name) == stem && !_isLyrics(entry))) {
      return;
    }
    for (final entry in files) {
      if (_stem(entry.name) == stem && _isLyrics(entry)) {
        await place.store.delete(entry.id);
      }
    }
  }

  static String _stem(String name) => p.posix.basenameWithoutExtension(name);

  static bool _isLyrics(SongFolderEntry entry) => const {
    '.lrc',
    '.$mlrcExtension',
  }.contains(p.posix.extension(entry.name).toLowerCase());

  void dispose() {
    _disposed = true;
  }
}
