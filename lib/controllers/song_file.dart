import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/services/file_access_check.dart';
import 'package:sistrum/utils/file_access.dart';

/// A song's file, resolved from the song's identity: its [track], the path it
/// plays from and its library row, when there is one. Song menus and the Now
/// Playing actions act on this instead of on a screen's own state.
class SongFile {
  const SongFile({
    required this.track,
    required this.path,
    required this.localItem,
    required this.access,
  });

  final Track track;

  /// The stored path or URI, without the `EXISTS:` prefix. A CUE track keeps
  /// its `#trackNN` suffix. Null for a song with no local file.
  final String? path;
  final LocalLibraryItem? localItem;

  /// Null when there is no [path] to check.
  final FileAccessCheck? access;

  bool get fileExists => access is FileAccessFound;

  /// The file itself: a CUE track's sheet, without its `#trackNN` suffix.
  String? get cleanPath => cleanSongPath(path);

  bool get isCueVirtual => isCueVirtualPath(path);

  bool get isCueFile {
    if (isCueVirtual) return true;
    final file = cleanPath;
    if (file != null && file.toLowerCase().endsWith('.cue')) return true;
    return (localItem?.format ?? '').startsWith('cue+');
  }

  bool get isSafFile => isContentUri(cleanPath);

  /// Editing tags, ReplayGain and analysis need the audio file on hand.
  bool get canEditFile => fileExists;

  /// Sidecar files are written beside the song, so they need its path.
  bool get canSaveSidecars => path != null;

  bool get canDelete => path != null;
}

/// The file [path] names, without a CUE track's `#trackNN` suffix.
String? cleanSongPath(String? path) {
  if (path == null) return null;
  final file = isCueVirtualPath(path) ? stripCueTrackSuffix(path) : path;
  return file.isEmpty ? null : file;
}

/// Resolves the file of [track]: the given [path] or [localItem], else the
/// library copy that matches the track. Checks that the file is still there.
Future<SongFile> resolveSongFile(
  WidgetRef ref, {
  required Track track,
  String? path,
  LocalLibraryItem? localItem,
}) async {
  var item = localItem;
  var filePath = _withoutExistsPrefix(path ?? item?.filePath);
  if (filePath == null && item == null) {
    item = await ref
        .read(localLibraryProvider.notifier)
        .findExistingAsync(
          isrc: track.isrc?.trim(),
          trackName: track.name,
          artistName: track.artistName,
        );
    filePath = _withoutExistsPrefix(item?.filePath);
  }
  final file = cleanSongPath(filePath);
  final access = file == null ? null : await checkFileAccess(file);
  return SongFile(
    track: track,
    path: filePath,
    localItem: item,
    access: access,
  );
}

/// Whether the song at [path] can play now. A CUE virtual track plays only
/// from its sheet's split, and a song whose file is gone cannot play, so a
/// row tap on either opens the song menu instead. A file that cannot be
/// checked is left to playback, which reports its own error.
Future<bool> songFileCanPlay(String? path) async {
  final stored = _withoutExistsPrefix(path);
  if (stored == null || isCueVirtualPath(stored)) return false;
  // A network source is streamed, not a file on this device.
  if (stored.startsWith('network://')) return true;
  return await checkFileAccess(stored) is! FileAccessMissing;
}

String? _withoutExistsPrefix(String? path) {
  if (path == null) return null;
  final trimmed = path.startsWith('EXISTS:') ? path.substring(7).trim() : path;
  return trimmed.isEmpty ? null : trimmed;
}
