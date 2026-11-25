import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/ffmpeg_models.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/services/cue_split_library_service.dart';
import 'package:sistrum/services/ffmpeg_cue_split.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/mime_utils.dart';
import 'package:sistrum/utils/saf_display_path.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';

final _log = AppLogger('SongCueSplit');

/// CUE sheets with a split in progress, so a second tap cannot start another.
final _splitting = <String>{};

/// Opens the CUE sheet of [song] and offers to split it into tracks. Needs the
/// library row of the CUE sheet.
Future<void> showCueSplitSheet(
  BuildContext context,
  WidgetRef ref,
  SongFile song,
) async {
  final item = song.localItem;
  final rawCuePath = song.cleanPath;
  if (item == null || rawCuePath == null) return;
  var cuePath = rawCuePath;
  final unknownAlbum = context.l10n.unknownAlbum;
  final unknownArtist = context.l10n.unknownArtist;
  final trackSuffix = RegExp(r'#track\d+$');
  if (trackSuffix.hasMatch(cuePath)) {
    cuePath = cuePath.replaceFirst(trackSuffix, '');
  }

  ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(context.l10n.snackbarLoadingCueSheet)));

  try {
    final cueInfo = await PlatformBridge.parseCueSheet(cuePath);

    if (!context.mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();

    if (cueInfo.containsKey('error')) {
      _showSnackBarMessage(context, context.l10n.cueSplitNoAudioFile);
      return;
    }

    final album = cueInfo['album'] as String? ?? unknownAlbum;
    final artist = cueInfo['artist'] as String? ?? unknownArtist;
    final audioPath = cueInfo['audio_path'] as String? ?? '';
    final genre = cueInfo['genre'] as String? ?? '';
    final date = cueInfo['date'] as String? ?? '';
    final tracksRaw = cueInfo['tracks'] as List<dynamic>? ?? [];

    if (audioPath.isEmpty) {
      _showSnackBarMessage(context, context.l10n.cueSplitNoAudioFile);
      return;
    }

    final tracks = tracksRaw
        .map((t) => CueSplitTrackInfo.fromJson(t as Map<String, dynamic>))
        .toList();

    if (tracks.isEmpty) {
      _showSnackBarMessage(context, context.l10n.cueSplitFailed);
      return;
    }

    if (!context.mounted) return;

    showAppModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      isScrollControlled: true,
      builder: (sheetContext) {
        final colorScheme = Theme.of(sheetContext).colorScheme;
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const AppSheetHandle(),
                const SizedBox(height: 16),
                Text(
                  sheetContext.l10n.cueSplitTitle,
                  style: Theme.of(
                    sheetContext,
                  ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 12),
                Text(
                  sheetContext.l10n.cueSplitAlbum(album),
                  style: Theme.of(sheetContext).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  sheetContext.l10n.cueSplitArtist(artist),
                  style: Theme.of(sheetContext).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  sheetContext.l10n.cueSplitTrackCount(tracks.length),
                  style: Theme.of(sheetContext).textTheme.bodyMedium?.copyWith(
                    color: colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 16),
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 200),
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: tracks.length,
                    itemBuilder: (context, index) {
                      final track = tracks[index];
                      final duration = track.endSec > 0
                          ? track.endSec - track.startSec
                          : 0.0;
                      final durationStr = duration > 0
                          ? '${(duration ~/ 60).toString().padLeft(2, '0')}:${(duration.toInt() % 60).toString().padLeft(2, '0')}'
                          : '';
                      return ListTile(
                        dense: true,
                        contentPadding: EdgeInsets.zero,
                        leading: CircleAvatar(
                          radius: 14,
                          backgroundColor: colorScheme.primaryContainer,
                          child: Text(
                            '${track.number}',
                            style: TextStyle(
                              fontSize: 11,
                              color: colorScheme.onPrimaryContainer,
                            ),
                          ),
                        ),
                        title: Text(
                          track.title,
                          style: const TextStyle(fontSize: 13),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: track.artist.isNotEmpty
                            ? Text(
                                track.artist,
                                style: TextStyle(
                                  fontSize: 11,
                                  color: colorScheme.onSurfaceVariant,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              )
                            : null,
                        trailing: durationStr.isNotEmpty
                            ? Text(
                                durationStr,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: colorScheme.onSurfaceVariant,
                                ),
                              )
                            : null,
                      );
                    },
                  ),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: () {
                      Navigator.pop(sheetContext);
                      _confirmAndSplitCue(
                        context: context,
                        ref: ref,
                        cuePath: cuePath,
                        audioPath: audioPath,
                        cleanPath: rawCuePath,
                        item: item,
                        album: album,
                        artist: artist,
                        genre: genre,
                        date: date,
                        tracks: tracks,
                      );
                    },
                    icon: const Icon(Icons.call_split),
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    label: Text(sheetContext.l10n.cueSplitButton),
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        );
      },
    );
  } catch (e) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    _showSnackBarMessage(context, context.l10n.cueSplitFailed);
    _log.e('Failed to parse CUE sheet: $e');
  }
}

void _confirmAndSplitCue({
  required BuildContext context,
  required WidgetRef ref,
  required String cuePath,
  required String audioPath,
  required String cleanPath,
  required LocalLibraryItem item,
  required String album,
  required String artist,
  required String genre,
  required String date,
  required List<CueSplitTrackInfo> tracks,
}) {
  showAppDialog<void>(
    context: context,
    builder: (dialogContext) {
      return AppAlertDialog(
        title: Text(dialogContext.l10n.cueSplitConfirmTitle),
        content: Text(
          dialogContext.l10n.cueSplitConfirmMessage(album, tracks.length),
        ),
        actions: [
          AppDialogAction(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(dialogContext.l10n.dialogCancel),
          ),
          AppDialogAction(
            filled: true,
            onPressed: () {
              Navigator.pop(dialogContext);
              _performCueSplit(
                context: context,
                ref: ref,
                cuePath: cuePath,
                audioPath: audioPath,
                cleanPath: cleanPath,
                item: item,
                album: album,
                artist: artist,
                genre: genre,
                date: date,
                tracks: tracks,
              );
            },
            child: Text(dialogContext.l10n.cueSplitButton),
          ),
        ],
      );
    },
  );
}

Future<Directory> _resolvePersistentCueSplitOutputDir() async {
  if (Platform.isAndroid) {
    final externalDir = await getExternalStorageDirectory();
    if (externalDir != null) {
      final musicDir = Directory(
        '${externalDir.parent.parent.parent.parent.path}'
        '${Platform.pathSeparator}Music'
        '${Platform.pathSeparator}Sistrum',
      );
      await musicDir.create(recursive: true);
      return musicDir;
    }
  }

  final docsDir = await getApplicationDocumentsDirectory();
  final fallbackDir = Directory(
    '${docsDir.path}${Platform.pathSeparator}Sistrum',
  );
  await fallbackDir.create(recursive: true);
  return fallbackDir;
}

Future<List<String>?> _exportCueSplitOutputsToSaf({
  required List<String> outputPaths,
  required String treeUri,
  required String relativeDir,
}) async {
  final exportedUris = <String>[];
  for (final path in outputPaths) {
    final fileName = path.split(Platform.pathSeparator).last;
    final safUri = await PlatformBridge.createSafFileFromPath(
      treeUri: treeUri,
      relativeDir: relativeDir,
      fileName: fileName,
      mimeType: audioMimeTypeForPath(path),
      srcPath: path,
    );
    if (safUri != null && safUri.isNotEmpty) {
      exportedUris.add(safUri);
    }
  }
  return exportedUris.isEmpty ? null : exportedUris;
}

Future<void> _performCueSplit({
  required BuildContext context,
  required WidgetRef ref,
  required String cuePath,
  required String audioPath,
  required String cleanPath,
  required LocalLibraryItem item,
  required String album,
  required String artist,
  required String genre,
  required String date,
  required List<CueSplitTrackInfo> tracks,
}) async {
  if (!_splitting.add(cuePath)) return;

  String? safTempAudioPath;
  Directory? tempSplitDir;
  try {
    // For SAF content:// audio paths, copy to temp for FFmpeg processing.
    String workingAudioPath = audioPath;
    final isSafSource = isContentUri(audioPath);
    if (isSafSource) {
      final tempPath = await PlatformBridge.copyContentUriToTemp(audioPath);
      if (tempPath == null || tempPath.isEmpty) {
        throw Exception('Failed to copy SAF audio file to temp');
      }
      safTempAudioPath = tempPath;
      workingAudioPath = tempPath;
    }

    final String outputDir;
    final safLocation = isSafSource
        ? resolveSafDocumentLocation(cleanPath)
        : null;
    final treeUri = safLocation?.treeUri ?? '';
    final relativeDir = safLocation?.relativeDir ?? '';
    final writeBackToSaf = isSafSource && treeUri.isNotEmpty;
    if (writeBackToSaf) {
      final tempDir = await getTemporaryDirectory();
      tempSplitDir = Directory(
        '${tempDir.path}${Platform.pathSeparator}'
        'cue_split_${DateTime.now().millisecondsSinceEpoch}',
      );
      await tempSplitDir.create(recursive: true);
      outputDir = tempSplitDir.path;
    } else if (isSafSource) {
      final persistentDir = await _resolvePersistentCueSplitOutputDir();
      outputDir = persistentDir.path;
    } else {
      outputDir = File(audioPath).parent.path;
    }

    if (!context.mounted) return;
    _showLongSnackBarMessage(
      context,
      context.l10n.cueSplitSplitting(1, tracks.length),
    );

    String? coverPath;
    try {
      final tempDir = await getTemporaryDirectory();
      final coverOutput =
          '${tempDir.path}${Platform.pathSeparator}cue_cover_${DateTime.now().millisecondsSinceEpoch}.jpg';
      final coverResult = await PlatformBridge.extractCoverToFile(
        workingAudioPath,
        coverOutput,
      );
      if (coverResult['error'] == null) {
        coverPath = coverOutput;
      }
    } catch (_) {}

    final albumMetadata = <String, String>{
      'artist': artist,
      'album': album,
      'genre': genre,
      'date': date,
    };

    final outputPaths = await FFmpegCueSplit.splitCueToTracks(
      audioPath: workingAudioPath,
      outputDir: outputDir,
      tracks: tracks,
      albumMetadata: albumMetadata,
      coverPath: coverPath,
      onProgress: (current, total) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).hideCurrentSnackBar();
          _showLongSnackBarMessage(
            context,
            context.l10n.cueSplitSplitting(current, total),
          );
        }
      },
    );

    var finalOutputPaths = outputPaths;

    if (coverPath != null && finalOutputPaths != null) {
      for (final path in finalOutputPaths) {
        if (path.toLowerCase().endsWith('.flac')) {
          try {
            // Only send the cover_path field: EditFlacFields uses
            // field-presence semantics, so omitting artist/album_artist
            // means those keys won't be rewritten. This preserves any
            // existing split artist Vorbis Comments.
            await PlatformBridge.editFileMetadata(path, {
              'cover_path': coverPath,
            });
          } catch (e) {
            _log.w('Failed to embed cover to split track: $e');
          }
        }
      }
    }

    if (writeBackToSaf && finalOutputPaths != null) {
      final exportedUris = await _exportCueSplitOutputsToSaf(
        outputPaths: finalOutputPaths,
        treeUri: treeUri,
        relativeDir: relativeDir,
      );
      finalOutputPaths = exportedUris;
    }

    if (coverPath != null) {
      try {
        await File(coverPath).delete();
      } catch (_) {}
    }

    final complete =
        finalOutputPaths != null && finalOutputPaths.length == tracks.length;
    if (complete) {
      await CueSplitLibraryService().publish(
        cuePath: cuePath,
        audioPath: audioPath,
        tracks: tracks,
        outputPaths: finalOutputPaths,
        sourceId: !isSafSource || writeBackToSaf
            ? item.sourceId
            : LocalLibraryItem.legacySourceId,
        sourceItemId: !isSafSource || writeBackToSaf ? item.id : null,
      );
      if (context.mounted) {
        await ref.read(localLibraryProvider.notifier).reloadFromStorage();
      }
    }

    if (context.mounted) {
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      if (complete) {
        _showSnackBarMessage(
          context,
          context.l10n.cueSplitSuccess(finalOutputPaths.length),
        );
      } else {
        _showSnackBarMessage(context, context.l10n.cueSplitFailed);
      }
    }
  } catch (e) {
    _log.e('CUE split failed: $e');
    if (context.mounted) {
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
      _showSnackBarMessage(context, context.l10n.cueSplitFailed);
    }
  } finally {
    if (safTempAudioPath != null) {
      try {
        await File(safTempAudioPath).delete();
      } catch (_) {}
    }
    if (tempSplitDir != null) {
      try {
        await tempSplitDir.delete(recursive: true);
      } catch (_) {}
    }
    _splitting.remove(cuePath);
  }
}

void _showSnackBarMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}

void _showLongSnackBarMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(message), duration: const Duration(seconds: 60)),
  );
}
