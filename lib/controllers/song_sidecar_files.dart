import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show defaultTargetPlatform;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/services/lyrics_sidecar.dart';
import 'package:sistrum/services/music_player_service.dart'
    show readPlaybackFileMetadataWithRetry;
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/lyrics_metadata_helper.dart';

final _log = AppLogger('SongSidecarFiles');

final _invalidFileNameChars = RegExp(r'[<>:"/\\|?*\x00-\x1f]');
final _multiUnderscore = RegExp(r'_+');
final _leadingOrTrailingDots = RegExp(r'^\.+|\.+$');

/// "Artist - Title" as a file name, with the characters a file name cannot hold
/// replaced.
String songSaveBaseName(Track track) =>
    '${_fileNameSegment(track.artistName)} - ${_fileNameSegment(track.name)}';

String _fileNameSegment(String value) {
  var sanitized = value.replaceAll(_invalidFileNameChars, '_').trim();
  sanitized = sanitized.replaceAll(_leadingOrTrailingDots, '');
  sanitized = sanitized.replaceAll(_multiUnderscore, '_');
  return sanitized.isEmpty ? 'untitled' : sanitized;
}

/// A folder the user chose, as a SAF tree on Android and otherwise a path,
/// with a security-scoped bookmark on iOS. Null when they cancel.
Future<({String? treeUri, String? path, String? bookmark})?>
pickSidecarFolder() async {
  switch (defaultTargetPlatform) {
    case TargetPlatform.android:
      final picked = await PlatformBridge.pickSafTree();
      final treeUri = picked?['tree_uri'] as String? ?? '';
      if (treeUri.isEmpty) return null;
      return (treeUri: treeUri, path: null, bookmark: null);
    case TargetPlatform.iOS:
      final picked = await PlatformBridge.pickIosDirectory();
      if (picked == null) return null;
      return (treeUri: null, path: picked.path, bookmark: picked.bookmark);
    default:
      final path = await FilePicker.getDirectoryPath();
      if (path == null) return null;
      return (treeUri: null, path: path, bookmark: null);
  }
}

/// Reads the lyrics of the file at [sourcePath]: its lyrics tag, then its
/// `.lrc` sidecar. Neither call fetches online lyrics.
Future<Map<String, dynamic>> readSongLyrics(String sourcePath) async {
  if (sourcePath.startsWith('network://')) {
    final metadata = await readPlaybackFileMetadataWithRetry(sourcePath);
    return {'lyrics': metadata['lyrics']?.toString() ?? '', 'source': ''};
  }
  try {
    final result = await PlatformBridge.getLyricsLRCWithSource(
      sourcePath,
    ).timeout(const Duration(seconds: 5));
    if (hasUsableLyricsContent(result['lyrics']?.toString() ?? '')) {
      return result;
    }
  } catch (e) {
    _log.d('Local lyrics reader failed; trying playback metadata: $e');
  }
  final metadata = await readPlaybackFileMetadataWithRetry(
    sourcePath,
  ).timeout(const Duration(seconds: 5));
  final lyrics = metadata['lyrics']?.toString() ?? '';
  if (hasUsableLyricsContent(lyrics)) return {'lyrics': lyrics, 'source': ''};
  // An Android document's .lrc file is out of the lyrics reader's reach.
  String? lrc;
  try {
    lrc = await const LyricsSidecarStore(
      extension: lrcExtension,
    ).read(sourcePath).timeout(const Duration(seconds: 5));
  } catch (e) {
    _log.d('Could not read the .lrc lyrics: $e');
  }
  return {
    'lyrics': lrc != null && hasUsableLyricsContent(lrc) ? lrc : lyrics,
    'source': '',
  };
}

/// Writes `baseName.extension` as a sidecar of [song].
///
/// `writeTo` renders the file at the given path and returns the bridge
/// result, or null when it has already told the user why nothing was
/// written. SAF tracks are rendered into a temp directory and published to
/// the song's own SAF folder. Filesystem tracks are written next to the
/// audio file. Once the audio file is gone, its old folder says nothing about
/// where the song went, so the user picks the folder instead.
/// Exceptions propagate to the caller.
Future<void> publishSongSidecar(
  BuildContext context,
  SongFile song, {
  required String baseName,
  required String tempDirPrefix,
  required String extension,
  required String mimeType,
  required String Function() successMessage,
  required bool hideCurrentSnackBar,
  required Future<Map<String, dynamic>?> Function(String outputPath) writeTo,
}) async {
  void showMessage(String message) {
    final messenger = ScaffoldMessenger.of(context);
    if (hideCurrentSnackBar) {
      messenger.hideCurrentSnackBar();
    }
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  final fileName = '$baseName.$extension';
  final hasFile = song.fileExists;
  final folder = hasFile ? null : await pickSidecarFolder();
  if (!context.mounted) return;
  if (!hasFile && folder == null) {
    if (hideCurrentSnackBar) {
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
    }
    return;
  }

  // Renders the file in a temp directory for `publish`, which says whether
  // it reached its place.
  Future<void> publishFromTemp(
    Future<bool> Function(String tempOutput) publish,
  ) async {
    final tempDir = await Directory.systemTemp.createTemp(tempDirPrefix);
    final tempOutput = '${tempDir.path}${Platform.pathSeparator}$fileName';
    try {
      final result = await writeTo(tempOutput);
      if (result == null) return;
      if (result['error'] != null) {
        if (context.mounted) {
          showMessage(
            context.l10n.trackSaveFailed(
              context.friendlyError(result['error']),
            ),
          );
        }
        return;
      }
      final published = await publish(tempOutput);
      if (context.mounted) {
        showMessage(
          published
              ? successMessage()
              : context.l10n.trackSaveFailed('No storage access'),
        );
      }
    } finally {
      try {
        await tempDir.delete(recursive: true);
      } catch (_) {}
    }
  }

  if (folder == null ? song.isSafFile : folder.treeUri != null) {
    final audioUri = song.cleanPath!;
    // A song beside its file takes the file's own SAF folder, as before.
    final treeUri = folder?.treeUri;
    const relativeDir = '';
    await publishFromTemp((tempOutput) async {
      final safUri = treeUri != null && treeUri.isNotEmpty
          ? await PlatformBridge.createSafFileFromPath(
              treeUri: treeUri,
              relativeDir: relativeDir,
              fileName: fileName,
              mimeType: mimeType,
              srcPath: tempOutput,
            )
          : await PlatformBridge.writeSafSidecarFromPath(
              audioUri: audioUri,
              fileName: fileName,
              mimeType: mimeType,
              srcPath: tempOutput,
            );
      return safUri != null && safUri.isNotEmpty;
    });
    return;
  }

  final bookmark = folder?.bookmark;
  final access = bookmark == null
      ? null
      : await PlatformBridge.startAccessingIosBookmark(bookmark);
  if (bookmark != null && access == null) {
    if (context.mounted) {
      showMessage(context.l10n.trackSaveFailed('No storage access'));
    }
    return;
  }
  final dir = access?.path ?? folder?.path ?? File(song.cleanPath!).parent.path;
  final outputPath = '$dir${Platform.pathSeparator}$fileName';

  final Map<String, dynamic>? result;
  try {
    result = await writeTo(outputPath);
  } finally {
    if (access != null) {
      await PlatformBridge.stopAccessingIosBookmark(access);
    }
  }
  if (result == null) {
    return;
  }

  if (context.mounted) {
    if (result['error'] != null) {
      showMessage(
        context.l10n.trackSaveFailed(context.friendlyError(result['error'])),
      );
    } else {
      showMessage(successMessage());
    }
  }
}

/// Saves the cover of [song] as a `.jpg` beside it. The embedded artwork comes
/// first, then the cover the library keeps for the song.
Future<void> saveSongCoverArt(BuildContext context, SongFile song) async {
  try {
    final baseName = songSaveBaseName(song.track);
    final audioPath = song.cleanPath!;
    final cachedCover = song.localItem?.coverPath;
    Future<bool> copyCover(String? path, String outputPath) async {
      if (path == null || path.isEmpty) return false;
      final uri = Uri.tryParse(path);
      final file = uri?.scheme == 'file' ? File.fromUri(uri!) : File(path);
      if (!await file.exists() || await file.length() == 0) return false;
      if (file.absolute.path != File(outputPath).absolute.path) {
        await file.copy(outputPath);
      }
      return true;
    }

    await publishSongSidecar(
      context,
      song,
      baseName: baseName,
      tempDirPrefix: 'cover_',
      extension: 'jpg',
      mimeType: 'image/jpeg',
      successMessage: () => context.l10n.trackCoverSaved(baseName),
      hideCurrentSnackBar: false,
      writeTo: (outputPath) async {
        Map<String, dynamic>? result;
        if (song.fileExists) {
          // Imported files carry their own embedded artwork.
          result = await PlatformBridge.extractCoverToFile(
            audioPath,
            outputPath,
          );
        }
        if ((result == null || result['error'] != null) &&
            await copyCover(cachedCover, outputPath)) {
          return {'success': true};
        }
        if (result == null && context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(context.l10n.trackCoverNoSource)),
          );
        }
        return result;
      },
    );
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(context.l10n.trackSaveFailed(context.friendlyError(e))),
        ),
      );
    }
  }
}

/// Saves the lyrics of [song] as an `.lrc` beside it. [displayedLyrics] are
/// the lyrics a screen already shows and are saved as they are; otherwise the
/// song's own lyrics are read.
Future<void> saveSongLyrics(
  BuildContext context,
  SongFile song, {
  String? displayedLyrics,
}) async {
  try {
    final baseName = songSaveBaseName(song.track);
    if (context.mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text(context.l10n.trackSaveLyricsProgress)),
        );
    }

    await publishSongSidecar(
      context,
      song,
      baseName: baseName,
      tempDirPrefix: 'lyrics_',
      extension: 'lrc',
      mimeType: 'application/octet-stream',
      successMessage: () => context.l10n.trackLyricsSaved(baseName),
      hideCurrentSnackBar: true,
      writeTo: (outputPath) async {
        var lyrics = displayedLyrics ?? '';
        if (!hasUsableLyricsContent(lyrics)) {
          lyrics =
              (await readSongLyrics(song.cleanPath!))['lyrics']?.toString() ??
              '';
        }
        if (!hasUsableLyricsContent(lyrics)) return null;
        await File(outputPath).writeAsString(lyrics);
        return {'success': true};
      },
    );
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              context.l10n.trackSaveFailed(context.friendlyError(e)),
            ),
          ),
        );
    }
  }
}
