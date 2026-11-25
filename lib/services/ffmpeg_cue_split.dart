import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:sistrum/services/audio_metadata_mapper.dart';
import 'package:sistrum/services/ffmpeg_models.dart';
import 'package:sistrum/services/ffmpeg_support.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('FFmpeg');

/// Splits one CUE-referenced image into per-track files.
abstract final class FFmpegCueSplit {
  // FFmpeg's balanced FLAC preset keeps PCM unchanged with less encoder work.
  static const _flacCompressionLevel = '5';

  static Future<List<String>?> splitCueToTracks({
    required String audioPath,
    required String outputDir,
    required List<CueSplitTrackInfo> tracks,
    required Map<String, String> albumMetadata,
    String? coverPath,
    void Function(int current, int total)? onProgress,
    @visibleForTesting Future<FFmpegResult> Function(List<String>)? execute,
  }) async {
    if (tracks.isEmpty) {
      _log.e('No tracks to split');
      return null;
    }
    final outputPaths = <String>[];
    final inputExt = audioPath.toLowerCase().split('.').last;
    final outputExt = const {'flac', 'wav', 'ape', 'wv'}.contains(inputExt)
        ? 'flac'
        : inputExt;
    for (var i = 0; i < tracks.length; i++) {
      final track = tracks[i];
      onProgress?.call(i + 1, tracks.length);
      final title = track.title
          .replaceAll(RegExp(r'[<>:"/\\|?*]'), '_')
          .replaceAll(RegExp(r'\s+'), ' ')
          .trim();
      final outputName =
          '${track.number.toString().padLeft(2, '0')} - $title.$outputExt';
      final arguments = <String>[
        '-v',
        'error',
        '-hide_banner',
        '-i',
        audioPath,
        '-ss',
        _formatSeconds(track.startSec),
      ];
      if (track.endSec > 0) {
        arguments.addAll(['-to', _formatSeconds(track.endSec)]);
      }
      arguments.addAll(
        outputExt == 'flac'
            ? ['-c:a', 'flac', '-compression_level', _flacCompressionLevel]
            : ['-c:a', 'copy'],
      );
      final metadata = <String, String>{
        'TITLE': track.title,
        'ARTIST': track.artist.isNotEmpty
            ? track.artist
            : (albumMetadata['artist'] ?? ''),
        'ALBUM': albumMetadata['album'] ?? '',
        'ALBUMARTIST': albumMetadata['artist'] ?? '',
        'TRACKNUMBER': track.number.toString(),
        'GENRE': albumMetadata['genre'] ?? '',
        'DATE': albumMetadata['date'] ?? '',
        'ISRC': track.isrc,
        'COMPOSER': track.composer,
      }..removeWhere((_, value) => value.isEmpty);
      AudioMetadataMapper.appendMappedMetadataArguments(arguments, metadata);
      String? outputPath;
      try {
        outputPath = await FFmpegOutput.reserve(
          '$outputDir${Platform.pathSeparator}$outputName',
        );
        arguments.addAll([outputPath, '-y']);
        _log.d('CUE split track ${track.number}');
        final result = await (execute ?? FFmpegExecution.arguments)(arguments);
        if (!result.success) {
          _log.e(
            'CUE split failed for track ${track.number}: ${result.output}',
          );
          continue;
        }
        final finalized = await FFmpegOutput.finalize(
          plan: FFmpegOutputPlan(
            workingPath: outputPath,
            finalPath: outputPath,
          ),
          inputPath: audioPath,
          deleteOriginal: false,
        );
        if (finalized == null) continue;
        outputPaths.add(finalized);
        outputPath = null;
        _log.i(
          'CUE split: track ${track.number} -> ${finalized.split(Platform.pathSeparator).last}',
        );
      } catch (error) {
        _log.e('CUE split failed for track ${track.number}: $error');
      } finally {
        if (outputPath != null) await FFmpegOutput.cleanup(outputPath);
      }
    }
    if (outputPaths.isEmpty) {
      _log.e('CUE split: no tracks were successfully extracted');
      return null;
    }
    _log.i('CUE split complete: ${outputPaths.length}/${tracks.length} tracks');
    return outputPaths;
  }

  static String _formatSeconds(double seconds) {
    if (seconds < 0) return '0';
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    final secs = seconds - hours * 3600 - minutes * 60;
    return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${secs.toStringAsFixed(3).padLeft(6, '0')}';
  }
}
