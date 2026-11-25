import 'dart:io';

import 'package:sistrum/services/ffmpeg_metadata.dart';
import 'package:sistrum/services/metadata_cover_resources.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/source_metadata_events.dart';

enum MetadataSaveFailure { backend, ffmpeg, storage, unexpected }

class MetadataSaveResult {
  final MetadataSaveFailure? failure;
  final Object? error;

  const MetadataSaveResult.saved() : failure = null, error = null;
  const MetadataSaveResult.failed(
    MetadataSaveFailure this.failure, [
    this.error,
  ]);
}

class MetadataSaveRequest {
  final String filePath;
  final Map<String, String> metadata;
  final String? selectedCoverPath;
  final String? currentCoverPath;
  final bool preserveUnspecifiedTags;

  MetadataSaveRequest({
    required this.filePath,
    required Map<String, String> metadata,
    this.selectedCoverPath,
    this.currentCoverPath,
    this.preserveUnspecifiedTags = false,
  }) : metadata = Map.unmodifiable(metadata);
}

enum MetadataSaveCodec { mp3, m4a, opus }

typedef MetadataEmbed =
    Future<String?> Function(
      MetadataSaveCodec codec,
      String path,
      String? coverPath,
      Map<String, String> metadata,
    );

/// Writes tags without widget state, and owns resources until every writer ends.
class MetadataPersistenceService {
  final MetadataCoverResources covers;
  final Future<Map<String, dynamic>> Function(String, Map<String, String>) edit;
  final Future<Map<String, dynamic>> Function(String) read;
  final MetadataEmbed embed;
  final Future<bool> Function(String, String) writeBack;

  const MetadataPersistenceService({
    required this.covers,
    this.edit = PlatformBridge.editFileMetadata,
    this.read = PlatformBridge.readFileMetadata,
    this.embed = _embed,
    this.writeBack = PlatformBridge.writeTempToSaf,
  });

  Future<MetadataSaveResult> save(MetadataSaveRequest request) async {
    final releaseLease = covers.retain();
    try {
      final saved = await _saveLocal(request);
      if (saved.failure == null && request.metadata.containsKey('lyrics')) {
        await SourceMetadataEvents.instance.publish(request.filePath);
      }
      return saved;
    } catch (error) {
      return MetadataSaveResult.failed(MetadataSaveFailure.unexpected, error);
    } finally {
      await releaseLease();
    }
  }

  Future<MetadataSaveResult> _saveLocal(MetadataSaveRequest request) async {
    String? extractedCoverDir;
    String? stagedPath;
    try {
      final coverPath = request.selectedCoverPath;
      final metadata = {...request.metadata, 'cover_path': coverPath ?? ''};
      final result = await edit(request.filePath, metadata);
      stagedPath = result['temp_path'] as String?;
      if (result['error'] case final error?) {
        return MetadataSaveResult.failed(MetadataSaveFailure.backend, error);
      }
      if (result['success'] == false) {
        return const MetadataSaveResult.failed(MetadataSaveFailure.backend);
      }
      if (result['method'] != 'ffmpeg') {
        return const MetadataSaveResult.saved();
      }

      final target = stagedPath ?? request.filePath;
      final tags = _fallbackTags(
        metadata,
        preserveUnspecified: request.preserveUnspecifiedTags,
      );
      try {
        final existing = await read(target);
        for (final key in const [
          'REPLAYGAIN_TRACK_GAIN',
          'REPLAYGAIN_TRACK_PEAK',
          'REPLAYGAIN_ALBUM_GAIN',
          'REPLAYGAIN_ALBUM_PEAK',
        ]) {
          final value = existing[key.toLowerCase()]?.toString() ?? '';
          if (value.isNotEmpty) tags[key] = value;
        }
      } catch (_) {
        // Computed ReplayGain tags are preserved when the reader is available.
      }
      var existingCover = coverPath ?? request.currentCoverPath;
      if (existingCover == null || existingCover.isEmpty) {
        final extracted = await covers.extract(target);
        existingCover = extracted?.path;
        extractedCoverDir = extracted?.tempDir;
      }

      final codec = _codec(request.filePath);
      final output = codec == null
          ? null
          : await embed(codec, target, existingCover, tags);
      if (output == null) {
        return const MetadataSaveResult.failed(MetadataSaveFailure.ffmpeg);
      }
      final safUri = result['saf_uri'] as String?;
      if (stagedPath != null &&
          safUri != null &&
          !await writeBack(output, safUri)) {
        return const MetadataSaveResult.failed(MetadataSaveFailure.storage);
      }
      return const MetadataSaveResult.saved();
    } catch (error) {
      return MetadataSaveResult.failed(MetadataSaveFailure.unexpected, error);
    } finally {
      // Android also deletes on writeback; failed FFmpeg never reaches it.
      if (stagedPath != null && stagedPath != request.filePath) {
        try {
          await File(stagedPath).delete();
        } catch (_) {}
      }
      await covers.release(extractedCoverDir);
    }
  }

  static MetadataSaveCodec? _codec(String path) {
    final lower = path.toLowerCase();
    if (lower.endsWith('.mp3')) return MetadataSaveCodec.mp3;
    if (lower.endsWith('.m4a') || lower.endsWith('.aac')) {
      return MetadataSaveCodec.m4a;
    }
    if (lower.endsWith('.opus') || lower.endsWith('.ogg')) {
      return MetadataSaveCodec.opus;
    }
    return null;
  }

  static Map<String, String> _fallbackTags(
    Map<String, String> metadata, {
    required bool preserveUnspecified,
  }) {
    String index(String key) {
      final number = metadata['${key}_number'] ?? '';
      final total = metadata['${key}_total'] ?? '';
      if (number.isEmpty || number == '0') return '';
      return total.isEmpty || total == '0' ? number : '$number/$total';
    }

    // Full editor saves clear empty fields; partial saves keep unspecified tags.
    return {
      for (final entry in const {
        'TITLE': 'title',
        'ARTIST': 'artist',
        'ALBUM': 'album',
        'ALBUMARTIST': 'album_artist',
        'DATE': 'date',
        'GENRE': 'genre',
        'ISRC': 'isrc',
        'LYRICS': 'lyrics',
        'UNSYNCEDLYRICS': 'lyrics',
        'ORGANIZATION': 'label',
        'COPYRIGHT': 'copyright',
        'COMPOSER': 'composer',
        'COMMENT': 'comment',
        'ITUNESADVISORY': 'explicit',
        'RELEASETYPE': 'album_type',
        'BARCODE': 'upc',
        'COMPILATION': 'compilation',
      }.entries)
        if (!preserveUnspecified || metadata.containsKey(entry.value))
          entry.key: metadata[entry.value] ?? '',
      if (!preserveUnspecified || metadata.containsKey('track_number'))
        'TRACKNUMBER': index('track'),
      if (!preserveUnspecified || metadata.containsKey('disc_number'))
        'DISCNUMBER': index('disc'),
    };
  }

  static Future<String?> _embed(
    MetadataSaveCodec codec,
    String path,
    String? coverPath,
    Map<String, String> metadata,
  ) => switch (codec) {
    MetadataSaveCodec.mp3 => FFmpegMetadata.embedMetadataToMp3(
      mp3Path: path,
      coverPath: coverPath,
      metadata: metadata,
      preserveMetadata: true,
    ),
    MetadataSaveCodec.m4a => FFmpegMetadata.embedMetadataToM4a(
      m4aPath: path,
      coverPath: coverPath,
      metadata: metadata,
      preserveMetadata: true,
    ),
    MetadataSaveCodec.opus => FFmpegMetadata.embedMetadataToOpus(
      opusPath: path,
      coverPath: coverPath,
      metadata: metadata,
      preserveMetadata: true,
    ),
  };
}
