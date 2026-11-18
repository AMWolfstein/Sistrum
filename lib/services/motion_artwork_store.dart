import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sistrum/services/ffmpeg_support.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';

typedef MotionArtworkAlbum = ({String album, String artist});

class MotionArtwork {
  const MotionArtwork(this.source, {this.aspectRatio});

  final String source;
  final double? aspectRatio;
}

/// Album-scoped artwork saved on this device. Relative filenames survive iOS
/// reinstalls that relocate the app container; temporary cache cleanup does
/// not remove it. Videos are not downloaded: the store only keeps local files.
class MotionArtworkStore {
  MotionArtworkStore({
    Future<Directory> Function()? directory,
    Future<bool> Function(String path)? validate,
  }) : _directory = directory ?? defaultDirectory,
       _validate = validate ?? _validateVideo;

  final Future<Directory> Function() _directory;
  final Future<bool> Function(String path) _validate;
  final _legacyChecks = <String, Future<bool>>{};
  final _invalid = <String>{};
  bool _clearing = false;
  static final _log = AppLogger('MotionArtwork');
  static const _cacheVersion = 2;

  static Future<Directory> defaultDirectory() async => Directory(
    '${(await getApplicationSupportDirectory()).path}/motion_artwork',
  );

  String _key(MotionArtworkAlbum album) {
    String normalize(String value) =>
        value.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');
    return sha256
        .convert(
          utf8.encode(
            jsonEncode([normalize(album.album), normalize(album.artist)]),
          ),
        )
        .toString();
  }

  static final _storedKey = RegExp(r'^[0-9a-f]{64}$');

  /// The key whose video [key] shows: its own, or the one its alias names.
  Future<String> _resolve(Directory root, String key) async {
    if (await File('${root.path}/$key.mp4').exists()) return key;
    final alias = File('${root.path}/$key.alias');
    if (!await alias.exists()) return key;
    final target = (await alias.readAsString()).trim();
    return _storedKey.hasMatch(target) ? target : key;
  }

  /// Lets [album] show the video saved for [target] without a second copy.
  /// Players find a video by the album and the playing song's artist, so
  /// each song artist of one album needs a name for the same video.
  Future<void> alias(
    MotionArtworkAlbum album,
    MotionArtworkAlbum target,
  ) async {
    final key = _key(album);
    final targetKey = _key(target);
    if (_clearing || key == targetKey) return;
    final root = await _directory();
    await root.create(recursive: true);
    await File('${root.path}/$key.alias').writeAsString(targetKey, flush: true);
  }

  /// Deletes the video saved for [album], or its alias.
  Future<void> remove(MotionArtworkAlbum album) async {
    if (_clearing) return;
    final key = _key(album);
    final root = await _directory();
    for (final name in ['$key.mp4', '$key.json', '$key.alias']) {
      await deleteFileQuietly(File('${root.path}/$name'));
    }
    _invalid.remove(key);
    _legacyChecks.remove(key);
  }

  Future<MotionArtwork?> find(MotionArtworkAlbum album) async {
    if (_clearing) return null;
    try {
      final root = await _directory();
      final key = await _resolve(root, _key(album));
      if (_invalid.contains(key)) return null;
      final file = File('${root.path}/$key.mp4');
      if (!await file.exists() || await file.length() == 0) return null;
      double? ratio;
      var version = 0;
      final info = File('${root.path}/$key.json');
      if (await info.exists()) {
        final data = jsonDecode(await info.readAsString());
        if (data is Map) {
          if (data['version'] is int) version = data['version'] as int;
          if (data['aspectRatio'] is num) {
            final value = (data['aspectRatio'] as num).toDouble();
            if (value.isFinite && value > 0) ratio = value;
          }
        }
      }
      // Older downloads could report success with corrupt HLS byte ranges.
      // Check those once per session; retain healthy covers for offline use.
      if (version != _cacheVersion &&
          !await _legacyChecks.putIfAbsent(key, () async {
            try {
              return await _validate(file.path);
            } catch (_) {
              return false;
            }
          })) {
        _invalid.add(key);
        _log.w('Saved motion artwork failed validation; repair required');
        return null;
      }
      return MotionArtwork(file.uri.toString(), aspectRatio: ratio);
    } catch (_) {
      return null;
    }
  }

  Future<void> clear() async {
    _clearing = true;
    try {
      await Future.wait(_legacyChecks.values.toList());
      final root = await _directory();
      if (await root.exists()) await root.delete(recursive: true);
      _legacyChecks.clear();
      _invalid.clear();
    } finally {
      _clearing = false;
    }
  }

  static Future<bool> _validateVideo(String path) => _run([
    '-v',
    'error',
    '-xerror',
    '-err_detect',
    'explode',
    '-i',
    path,
    '-map',
    '0:v:0',
    '-an',
    '-sn',
    '-dn',
    '-f',
    'null',
    '-',
  ], timeout: const Duration(seconds: 20));

  static Future<bool> _run(
    List<String> arguments, {
    required Duration timeout,
  }) async {
    final session = await FFmpegExecution.start(arguments);
    final completed = session.completed.then((result) => result.success);
    return completed.timeout(
      timeout,
      onTimeout: () async {
        await FFmpegExecution.cancel(session.id);
        // Wait for this session to release its file before removing partials.
        await completed.timeout(
          const Duration(seconds: 5),
          onTimeout: () => false,
        );
        return false;
      },
    );
  }
}
