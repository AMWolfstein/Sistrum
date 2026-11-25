import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sistrum/services/backend/backend_channel.dart';
import 'package:sistrum/services/desktop_diagnostics.dart';
import 'package:sistrum/utils/audio_format_utils.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/hash_utils.dart';
import 'package:sistrum/utils/logger.dart';

part 'platform_bridge/bridge_codec.dart';
part 'platform_bridge/bridge_events.dart';
part 'platform_bridge/bridge_library_scan.dart';
part 'platform_bridge/bridge_native_worker.dart';
part 'platform_bridge/bridge_saf.dart';

final _log = AppLogger('PlatformBridge');

/// The marker a forced rescan writes into the Library cover directory
/// (`LIBRARY_COVER_REGENERATE_MARKER` in the Rust backend).
const libraryCoverRegenerateMarker = '.regenerate';

class InstallationState {
  final bool markerExisted;
  final bool markerCreated;
  final bool freshPackageInstall;

  const InstallationState({
    required this.markerExisted,
    required this.markerCreated,
    required this.freshPackageInstall,
  });

  const InstallationState.unsupported()
    : markerExisted = true,
      markerCreated = true,
      freshPackageInstall = false;

  factory InstallationState.fromMap(Map<String, dynamic> map) {
    return InstallationState(
      markerExisted: map['marker_existed'] == true,
      markerCreated: map['marker_created'] == true,
      freshPackageInstall: map['fresh_package_install'] == true,
    );
  }
}

bool shouldResetRestoredInstallation({
  required bool hasPersistedSettings,
  required InstallationState installState,
}) {
  return hasPersistedSettings &&
      !installState.markerExisted &&
      installState.markerCreated &&
      installState.freshPackageInstall;
}

class PlatformBridge {
  /// MethodChannel on Android and iOS, the Rust library on desktop.
  static BackendChannel get _channel => backendChannel;

  static int _nativeDataJobSerial = 0;
  static final _nativeDataJobs = <String, Set<_NativeDataJobState>>{};

  /// A single background native operation. Binary inputs never become base64
  /// JSON, and large request/result codecs stay off the UI isolate.
  static Future<Map<String, dynamic>> runNativeDataJob(
    Map<String, dynamic> request, {
    Uint8List? bytes,
    String? requestId,
  }) async {
    final id =
        requestId ??
        'data_${DateTime.now().microsecondsSinceEpoch}_${_nativeDataJobSerial++}';
    final state = _NativeDataJobState();
    final states = _nativeDataJobs.putIfAbsent(id, () => {});
    states.add(state);
    try {
      final encoded = await _encodeNativeDataJobRequest(request);
      if (state.cancelled) {
        throw PlatformException(
          code: 'CANCELLED',
          message: 'Native data job cancelled',
        );
      }
      state.dispatched = true;
      Object? result;
      try {
        result = await _channel.invokeMethod('runNativeDataJob', {
          'request_json': encoded,
          'request_id': id,
          'bytes': ?bytes,
        });
      } finally {
        state.dispatched = false;
      }
      return await _decodeRequiredMapResultAsync(result, 'runNativeDataJob');
    } finally {
      states.remove(state);
      if (states.isEmpty) _nativeDataJobs.remove(id);
    }
  }

  static Future<void> cancelNativeDataJob(String requestId) async {
    final states = _nativeDataJobs[requestId];
    if (states == null) return;
    for (final state in states) {
      state.cancelled = true;
    }
    if (states.any((state) => state.dispatched)) {
      await _channel.invokeMethod<void>('cancelNativeDataJob', {
        'request_id': requestId,
      });
    }
  }

  static Future<void> setScreenAwake(bool enabled) async {
    if (defaultTargetPlatform != TargetPlatform.android &&
        defaultTargetPlatform != TargetPlatform.iOS) {
      return;
    }
    await _channel.invokeMethod<void>('setScreenAwake', {'enabled': enabled});
  }

  /// Whether this Android device is on external power; false elsewhere.
  static Future<bool> isCharging() async {
    if (!Platform.isAndroid) return false;
    return await _channel.invokeMethod<bool>('isCharging') ?? false;
  }

  /// Android API level without collecting unrelated device information.
  static Future<int?> getAndroidSdkVersion() async {
    try {
      final sdk = await _channel.invokeMethod<int>('getAndroidSdkVersion');
      return sdk != null && sdk > 0 ? sdk : null;
    } on MissingPluginException {
      return null;
    } on PlatformException catch (e) {
      _log.w('Android SDK version unavailable: $e');
      return null;
    }
  }

  static const _jsonResultFileKey = '__json_file';
  static const _backgroundJsonDecodeThresholdBytes = 128 * 1024;
  static final StreamController<void> _libraryStorageEvents =
      StreamController<void>.broadcast();
  static final StreamController<void> _verificationNotificationEvents =
      StreamController<void>.broadcast();
  static final StreamController<void> _setupFolderSelectionEvents =
      StreamController<void>.broadcast();
  static final StreamController<void> _extensionAuthCallbackEvents =
      StreamController<void>.broadcast();
  static final StreamController<List<String>>
  _iosBackgroundDownloadExpirationEvents =
      StreamController<List<String>>.broadcast();
  static final StreamController<String> _discordPresenceStatusEvents =
      StreamController<String>.broadcast();
  static final StreamController<String> _libraryFolderChangeEvents =
      StreamController<String>.broadcast();
  static final StreamController<Map<String, dynamic>> _linkRequestEvents =
      StreamController<Map<String, dynamic>>.broadcast();
  static final StreamController<Map<String, dynamic>> _linkMessageEvents =
      StreamController<Map<String, dynamic>>.broadcast();
  static final StreamController<Map<String, dynamic>> _linkClientEvents =
      StreamController<Map<String, dynamic>>.broadcast();
  static final StreamController<Map<String, dynamic>> _linkPairedEvents =
      StreamController<Map<String, dynamic>>.broadcast();
  static final StreamController<String> _linkPairingEndedEvents =
      StreamController<String>.broadcast();
  static final StreamController<Map<Object?, Object?>> _castEvents =
      StreamController<Map<Object?, Object?>>.broadcast();
  static bool _backendEventHandlerInstalled = false;

  /// The Rust backend answers on Android and iOS through the MethodChannel
  /// and on desktop through dart:ffi, with plain filesystem paths.
  static bool get supportsCoreBackend =>
      Platform.isAndroid || Platform.isIOS || isDesktopPlatform;

  static Stream<T> _events<T>(StreamController<T> controller) {
    _ensureBackendEventHandler();
    return controller.stream;
  }

  static Stream<void> libraryStorageEvents() => _events(_libraryStorageEvents);

  static Stream<void> setupFolderSelectionEvents() =>
      _events(_setupFolderSelectionEvents);

  /// Desktop only: replaces the watched library folders. Returns the folders
  /// that are being watched.
  static Future<List<String>> watchLibraryFolders(List<String> paths) async {
    if (!isDesktopPlatform) return const [];
    final watched = await _channel.invokeMethod<List<dynamic>>(
      'watchLibraryFolders',
      {'paths': paths},
    );
    return [for (final path in watched ?? const []) path.toString()];
  }

  static Future<void> unwatchLibraryFolders() async {
    if (!isDesktopPlatform) return;
    await _channel.invokeMethod<void>('unwatchLibraryFolders');
  }

  /// Desktop watched library roots whose content changed (throttled).
  static Stream<String> libraryFolderChangeEvents() =>
      _events(_libraryFolderChangeEvents);

  static Map<String, dynamic> _linkMap(Object? value) => value is Map
      ? value.map((key, value) => MapEntry(key.toString(), value))
      : <String, dynamic>{};

  static void _ensureBackendEventHandler() {
    if (_backendEventHandlerInstalled) return;
    _backendEventHandlerInstalled = true;
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'extensionAuthCallbackReceived':
          _extensionAuthCallbackEvents.add(null);
          return null;
        case 'setupFolderSelectionChanged':
          _setupFolderSelectionEvents.add(null);
          return null;
        case 'libraryScanError':
          final args = call.arguments;
          if (args is Map) {
            final path = args['path']?.toString() ?? '';
            final operation = args['operation']?.toString() ?? 'scan';
            final message = args['message']?.toString() ?? 'Unknown error';
            AppLogger('LocalLibrary').e('$operation [$path]: $message');
          }
          return null;
        case 'libraryStorageChanged':
          _libraryStorageEvents.add(null);
          return null;
        case 'discordPresenceStatus':
          final status = call.arguments;
          if (status is String) _discordPresenceStatusEvents.add(status);
          return null;
        case 'libraryFolderChanged':
          final args = call.arguments;
          final root = args is Map ? args['root']?.toString() ?? '' : '';
          if (root.isNotEmpty) _libraryFolderChangeEvents.add(root);
          return null;
        case 'linkRequest':
          if (call.arguments is Map) {
            _linkRequestEvents.add(_linkMap(call.arguments));
          }
          return null;
        case 'linkMessage':
          if (call.arguments is Map) {
            _linkMessageEvents.add(_linkMap(call.arguments));
          }
          return null;
        case 'linkClients':
          if (call.arguments is Map) {
            _linkClientEvents.add(_linkMap(call.arguments));
          }
          return null;
        case 'linkPaired':
          if (call.arguments is Map) {
            _linkPairedEvents.add(_linkMap(call.arguments));
          }
          return null;
        case 'linkPairingEnded':
          final args = call.arguments;
          final reason = args is Map ? args['reason']?.toString() : null;
          _linkPairingEndedEvents.add(reason ?? 'cancelled');
          return null;
        case 'castStatus' || 'castDevices':
          if (call.arguments case final Map<Object?, Object?> payload) {
            _castEvents.add(payload);
          }
          return null;
        case 'extensionVerificationNotificationTapped':
          _verificationNotificationEvents.add(null);
          return null;
        case 'iosBackgroundDownloadExpired':
          final raw = call.arguments;
          var itemIds = const <String>[];
          try {
            final decoded = raw is String ? jsonDecode(raw) : raw;
            if (decoded is List) {
              itemIds = decoded
                  .map((value) => value.toString().trim())
                  .where((value) => value.isNotEmpty)
                  .toSet()
                  .toList(growable: false);
            }
          } catch (_) {
            // Older/native-mismatched builds may send no payload. The queue
            // still pauses its currently visible active items below.
          }
          _iosBackgroundDownloadExpirationEvents.add(itemIds);
          return null;
        default:
          return null;
      }
    });
  }

  static Future<Map<String, dynamic>> _invokeMap(
    String method, [
    dynamic args,
  ]) async {
    final result = await _channel.invokeMethod(method, args);
    return _decodeRequiredMapResultAsync(result, method);
  }

  static Future<void> _invokeVoid(String method, [dynamic args]) async {
    await _channel.invokeMethod(method, args);
  }

  static Future<T> _invokeAs<T>(String method, [dynamic args]) async =>
      (await _channel.invokeMethod(method, args)) as T;

  static Future<T> _invokeDecoded<T>(
    FutureOr<T> Function(dynamic result, String method) decode,
    String method, [
    dynamic args,
  ]) async => decode(await _channel.invokeMethod(method, args), method);

  static Future<InstallationState> ensureInstallMarker() async {
    if (!Platform.isAndroid) return const InstallationState.unsupported();
    final result = await _invokeMap('ensureInstallMarker');
    return InstallationState.fromMap(result);
  }

  static Future<void> exitApp() => _invokeVoid('exitApp');

  /// Holds backend access through download and finalization, including folder changes.
  static Future<String> acquireDownloadDirectory(String path) async {
    if (!supportsCoreBackend) return '';
    return await _channel.invokeMethod<String>('acquireDownloadDirectory', {
          'path': path,
        }) ??
        '';
  }

  static Future<void> releaseDownloadDirectory(String token) async {
    if (!supportsCoreBackend || token.isEmpty) return;
    await _invokeVoid('releaseDownloadDirectory', {'token': token});
  }

  static Future<Map<String, dynamic>?> pickSafTree({bool forSetup = false}) =>
      _invokeDecoded(
        _decodeNullableMapResult,
        'pickSafTree',
        forSetup ? {'for_setup': true} : null,
      );

  static Future<String?> readSetupFolderSelection() =>
      _channel.invokeMethod<String>('readSetupFolderSelection');

  static Future<void> acknowledgeSetupFolderSelection(String payload) =>
      _channel.invokeMethod<void>('acknowledgeSetupFolderSelection', {
        'payload': payload,
      });

  static Future<bool> safExists(String uri) async =>
      (await safExistsState(uri)) ?? false;

  /// Null when the provider cannot tell (a lost grant or a failed query), so
  /// a destructive caller keeps the document.
  static Future<bool?> safExistsState(String uri) =>
      _invokeAs<bool?>('safExists', {'uri': uri});

  /// Lists playable documents for a temporary queue, without indexing a source.
  static Future<List<dynamic>> listSafAudioFiles(String treeUri) async =>
      await _channel.invokeMethod<List<dynamic>>('listSafAudioFiles', {
        'tree_uri': treeUri,
      }) ??
      const [];

  /// Exact-path checks. Only explicit `missing` results authorize cleanup;
  /// absent responses and permission/provider failures remain unknown.
  static Future<Map<String, bool?>> safExistsBatch(List<String> uris) async {
    if (uris.isEmpty) return const {};
    final result = await _invokeMap('safExistsBatch', {
      'uris_json': jsonEncode(uris),
    });
    return {
      for (final uri in uris)
        uri: switch (result[uri]) {
          'found' => true,
          'missing' => false,
          _ => null,
        },
    };
  }

  /// Read access for a Library source, independent of download write access.
  /// Null means the provider could not confirm access (loading/query failure).
  static Future<bool?> probeSafTreeReadAccess(String treeUri) async {
    try {
      Future<bool?> probe() => _channel.invokeMethod<bool>(
        'probeSafTreeReadAccess',
        {'tree_uri': treeUri},
      );
      final readable = await probe();
      if (readable != false) return readable;
      // A busy provider can briefly return an empty root after a write.
      // Confirm a negative before hiding all tracks from this source.
      await Future<void>.delayed(const Duration(milliseconds: 200));
      return await probe();
    } catch (e) {
      _log.w('Failed to probe SAF library read access: $e');
      return null;
    }
  }

  static Future<bool> safDelete(String uri) =>
      _invokeAs('safDelete', {'uri': uri});

  static Future<Map<String, dynamic>> safStat(String uri) {
    return _invokeMap('safStat', {'uri': uri});
  }

  /// Null when the copy fails. With [reportFailure] a failure throws a
  /// [PlatformException] that says why, instead of returning null.
  static Future<String?> copyContentUriToTemp(
    String uri, {
    bool reportFailure = false,
  }) => _invokeAs('safCopyToTemp', {
    'uri': uri,
    if (reportFailure) 'report_failure': true,
  });

  static Future<ContentUriPlaybackLease?> openContentUriPlaybackLease(
    String uri,
  ) async {
    if (!Platform.isAndroid || !isContentUri(uri)) return null;
    final result = await _channel.invokeMethod('safOpenPlaybackLease', {
      'uri': uri,
    });
    if (result is! Map) return null;
    final map = Map<String, dynamic>.from(result);
    final path = map['path']?.toString() ?? '';
    final token = map['token']?.toString() ?? '';
    if (path.isEmpty || token.isEmpty) return null;
    return ContentUriPlaybackLease(
      path: path,
      token: token,
      displayName: result['display_name'] as String?,
    );
  }

  static Future<void> closeContentUriPlaybackLease(String token) async {
    if (!Platform.isAndroid || token.isEmpty) return;
    try {
      await _channel.invokeMethod('safClosePlaybackLease', {'token': token});
    } catch (e) {
      _log.w('Failed to close SAF playback lease: $e');
    }
  }

  static Future<String?> createSafFileFromPath({
    required String treeUri,
    required String relativeDir,
    required String fileName,
    required String mimeType,
    required String srcPath,
  }) => _invokeAs('safCreateFromPath', {
    'tree_uri': treeUri,
    'relative_dir': relativeDir,
    'file_name': fileName,
    'mime_type': mimeType,
    'src_path': srcPath,
  });

  static Future<String?> writeSafSidecarFromPath({
    required String audioUri,
    required String fileName,
    required String mimeType,
    required String srcPath,
  }) => _invokeAs('safWriteSidecarFromPath', {
    'audio_uri': audioUri,
    'file_name': fileName,
    'mime_type': mimeType,
    'src_path': srcPath,
  });

  static Future<Map<String, dynamic>> createSafFileIfAbsentFromPath({
    required String treeUri,
    required String relativeDir,
    required String fileName,
    required String mimeType,
    required String srcPath,
  }) {
    return _invokeMap('safCreateIfAbsentFromPath', {
      'tree_uri': treeUri,
      'relative_dir': relativeDir,
      'file_name': fileName,
      'mime_type': mimeType,
      'src_path': srcPath,
    });
  }

  static Future<bool> shareContentUri(String uri, {String title = ''}) async =>
      await _invokeAs<bool?>('shareContentUri', {'uri': uri, 'title': title}) ??
      false;

  /// Reads the embedded or sidecar lyrics of a local file. Never fetches online.
  static Future<Map<String, dynamic>> getLyricsLRCWithSource(String filePath) {
    return _invokeMap('getLyricsLRCWithSource', {'file_path': filePath});
  }

  static Future<Map<String, dynamic>> embedLyricsToFile(
    String filePath,
    String lyrics,
  ) {
    return _invokeMap('embedLyricsToFile', {
      'file_path': filePath,
      'lyrics': lyrics,
    });
  }

  static Future<Map<String, dynamic>> extractCoverToFile(
    String audioPath,
    String outputPath,
  ) {
    return _withMediaFileAccess([audioPath, outputPath], () {
      return _invokeMap('extractCoverToFile', {
        'audio_path': audioPath,
        'output_path': outputPath,
      });
    });
  }

  static Future<Map<String, dynamic>> readFileMetadata(
    String filePath, {
    String? displayName,
  }) {
    return _invokeMap('readFileMetadata', {
      'file_path': filePath,
      'display_name': ?displayName,
    });
  }

  /// Reads the tags and quality fields used for automatic Library display.
  ///
  /// Android's readAudioMetadata implementation can inspect most SAF files
  /// through /proc/self/fd, avoiding the full-file cache copy required by tag
  /// editing. Keep the complete reader as a compatibility fallback for
  /// malformed or incorrectly named files that the lightweight scanner could
  /// only identify from their filename.
  static Future<Map<String, dynamic>> readDisplayAudioMetadata(
    String filePath,
  ) async {
    final scanned = await readAudioMetadata(filePath);
    if (scanned != null &&
        scanned['error'] == null &&
        scanned['metadataFromFilename'] != true) {
      return normalizeScannedAudioMetadata(scanned);
    }
    return readFileMetadata(filePath);
  }

  static Future<Map<String, dynamic>> editFileMetadata(
    String filePath,
    Map<String, String> metadata,
  ) {
    return _withMediaFileAccess([filePath, metadata['cover_path'] ?? ''], () {
      return _invokeMap('editFileMetadata', {
        'file_path': filePath,
        'metadata_json': jsonEncode(metadata),
      });
    });
  }

  static Future<T> _withMediaFileAccess<T>(
    Iterable<String> paths,
    Future<T> Function() operation,
  ) async {
    // A user-selected library file or cover can live outside the download
    // folder. Hold only this operation's backend grants; SAF is staged natively.
    final scopes = <String>[];
    try {
      final directories = <String>{
        for (final path in paths)
          if (path.startsWith('/'))
            p.posix.dirname(path)
          else if (p.windows.isAbsolute(path) &&
              !p.windows.isRootRelative(path))
            p.windows.dirname(path),
      };
      for (final directory in directories) {
        scopes.add(await acquireDownloadDirectory(directory));
      }
      return await operation();
    } finally {
      for (final scope in scopes.reversed) {
        try {
          await releaseDownloadDirectory(scope);
        } catch (e) {
          _log.w('Failed to release media file access: $e');
        }
      }
    }
  }

  /// Writes ISRC and label into an M4A/MP4 file as iTunes freeform atoms.
  /// FFmpeg's MP4 muxer drops these keys, so they must be written natively
  /// after the FFmpeg metadata pass. [filePath] must be a local file path.
  /// Only the keys present in [fields] are touched; an empty value clears it.
  static Future<Map<String, dynamic>> writeM4AFreeformTags(
    String filePath,
    Map<String, String> fields,
  ) {
    return _withMediaFileAccess([filePath], () {
      return _invokeMap('writeM4AFreeformTags', {
        'file_path': filePath,
        'metadata_json': jsonEncode(fields),
      });
    });
  }

  static Future<bool> writeTempToSaf(String tempPath, String safUri) async {
    final map = await _invokeMap('writeTempToSaf', {
      'temp_path': tempPath,
      'saf_uri': safUri,
    });
    return map['success'] == true;
  }

  static Future<bool> writeSafSidecarLrc(String safUri, String lyrics) async {
    final map = await _invokeMap('writeSafSidecarLrc', {
      'saf_uri': safUri,
      'lyrics': lyrics,
    });
    return map['success'] == true;
  }

  /// Writes [content] as the sidecar with [extension] next to the Android
  /// document [safUri], in its folder. Returns whether it was written.
  static Future<bool> writeSafSidecar(
    String safUri,
    String content,
    String extension,
  ) async =>
      await _invokeAs<bool?>('writeSafSidecar', {
        'saf_uri': safUri,
        'content': content,
        'extension': extension,
      }) ??
      false;

  /// The sidecar with [extension] next to the Android document [safUri], or
  /// null when it has none.
  static Future<String?> readSafSidecar(String safUri, String extension) =>
      _invokeAs<String?>('readSafSidecar', {
        'saf_uri': safUri,
        'extension': extension,
      });

  /// The file named [name] in the folder of the SAF document [safUri], or
  /// null when the folder has none.
  static Future<({String uri, int size, int modified})?> findSafSibling(
    String safUri,
    String name,
  ) async {
    final result = await _channel.invokeMethod<Map<Object?, Object?>>(
      'findSafSibling',
      {'saf_uri': safUri, 'name': name},
    );
    final uri = result?['uri'];
    final size = result?['size'];
    final modified = result?['modified'];
    if (uri is! String || size is! int || modified is! int) return null;
    return (uri: uri, size: size, modified: modified);
  }

  /// The sidecars with [extensions] and the files named [names] in the
  /// folder of the SAF document [safUri], found in one listing of that
  /// folder: sidecars by extension and files by name, leaving out those the
  /// folder does not have. Null when the folder cannot be read.
  static Future<
    ({
      Map<String, ({String uri, int size, int modified})> sidecars,
      Map<String, ({String uri, int size, int modified})> files,
    })?
  >
  findSafSiblings(
    String safUri, {
    required List<String> extensions,
    required List<String> names,
  }) async {
    final result = await _channel.invokeMethod<Map<Object?, Object?>>(
      'findSafSiblings',
      {'saf_uri': safUri, 'extensions': extensions, 'names': names},
    );
    if (result == null) return null;
    Map<String, ({String uri, int size, int modified})> entries(Object? map) =>
        {
          if (map is Map)
            for (final MapEntry(:key, :value) in map.entries)
              if (key is String && value is Map)
                if (value case {
                  'uri': final String uri,
                  'size': final int size,
                  'modified': final int modified,
                })
                  key: (uri: uri, size: size, modified: modified),
        };
    return (
      sidecars: entries(result['sidecars']),
      files: entries(result['files']),
    );
  }

  /// The text of the SAF document [uri], or null when it cannot be read or
  /// is larger than [maxBytes].
  static Future<String?> readSafText(String uri, int maxBytes) =>
      _invokeAs<String?>('readSafText', {'uri': uri, 'max_bytes': maxBytes});

  /// The file name of the SAF document [safUri] and the folders holding it,
  /// nearest first, each with whether it is the granted tree's own folder,
  /// or null when the provider cannot tell. Ask before deleting the
  /// document.
  static Future<({String name, List<({String uri, bool root})> folders})?>
  safSongFolders(String safUri) async {
    final result = await _channel.invokeMethod<Map<Object?, Object?>>(
      'safSongFolders',
      {'saf_uri': safUri},
    );
    if (result case {
      'name': final String name,
      'folders': final List<Object?> folders,
    }) {
      return (
        name: name,
        folders: [
          for (final folder in folders)
            if (folder case {'uri': final String uri, 'root': final bool root})
              (uri: uri, root: root),
        ],
      );
    }
    return null;
  }

  /// The entries of the SAF folder [folderUri], or null when it cannot be
  /// listed.
  static Future<List<({String name, String uri, bool folder})>?> listSafFolder(
    String folderUri,
  ) async {
    final result = await _channel.invokeMethod<List<Object?>>('listSafFolder', {
      'folder_uri': folderUri,
    });
    if (result == null) return null;
    final entries = <({String name, String uri, bool folder})>[];
    for (final entry in result) {
      if (entry case {
        'name': final String name,
        'uri': final String uri,
        'folder': final bool folder,
      }) {
        entries.add((name: name, uri: uri, folder: folder));
      } else {
        return null;
      }
    }
    return entries;
  }

  /// Keeps user-started work of [kind] alive with the screen off: an Android
  /// foreground service, or an iOS background-task grace period. Returns
  /// whether the platform accepted it.
  static Future<bool> startBackgroundWork(
    String kind, {
    required String title,
    String text = '',
  }) async {
    final started = await _channel.invokeMethod<bool>('startBackgroundWork', {
      'kind': kind,
      'title': title,
      'text': text,
    });
    return started ?? false;
  }

  /// Updates the Android work notification; [progress] is a percentage, or
  /// negative while indeterminate.
  static Future<void> updateBackgroundWork(
    String kind, {
    required String title,
    String text = '',
    int progress = -1,
  }) => _invokeVoid('updateBackgroundWork', {
    'kind': kind,
    'title': title,
    'text': text,
    'progress': progress,
  });

  static Future<void> stopBackgroundWork(String kind) =>
      _invokeVoid('stopBackgroundWork', {'kind': kind});

  /// Ask the native backend to release unused memory. Best-effort:
  /// safe to call on memory pressure or when the app is backgrounded.
  static Future<void> releaseNativeMemory({bool underPressure = false}) async {
    try {
      await _channel.invokeMethod(
        underPressure ? 'releaseMemoryUnderPressure' : 'releaseMemory',
      );
    } catch (_) {}
  }

  static Future<void> initExtensionSystem(
    String extensionsDir,
    String dataDir, {
    required String masterKey,
    List<String> allowedDirectories = const [],
  }) async {
    _log.d('initExtensionSystem: $extensionsDir, $dataDir');
    await _channel.invokeMethod('initExtensionSystem', {
      'extensions_dir': extensionsDir,
      'data_dir': dataDir,
      'master_key': masterKey,
      'allowed_directories': allowedDirectories,
    });
  }

  static Future<Map<String, dynamic>?> getPendingAppCrash() async {
    if (isDesktopPlatform) return DesktopDiagnostics.instance?.pendingCrash();
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    return _invokeDecoded(_decodeNullableMapResult, 'getPendingAppCrash');
  }

  static Future<void> dismissAppCrash(String id) async {
    if (isDesktopPlatform) return DesktopDiagnostics.instance?.dismiss(id);
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    await _channel.invokeMethod<void>('dismissAppCrash', {'id': id});
  }

  static Future<Map<String, dynamic>?> getLatestAppCrash() async {
    if (isDesktopPlatform) return DesktopDiagnostics.instance?.latestCrash();
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    return _invokeDecoded(_decodeNullableMapResult, 'getLatestAppCrash');
  }

  static Future<void> markAppCrashShown(String id) async {
    if (isDesktopPlatform) return DesktopDiagnostics.instance?.markShown(id);
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    await _channel.invokeMethod<void>('markAppCrashShown', {'id': id});
  }

  static Future<void> recordAppRuntimeError({
    required String error,
    required String stack,
    required String logs,
  }) async {
    if (isDesktopPlatform) {
      return DesktopDiagnostics.instance?.recordRuntimeError(
        error: error,
        stack: stack,
        logs: logs,
      );
    }
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    await _channel.invokeMethod<void>('recordAppRuntimeError', {
      'error': error,
      'stack': stack,
      'logs': logs,
    });
  }

  static Future<Map<String, dynamic>?> getLibraryScanExitInfo({
    required int startedAfter,
  }) async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    return _invokeDecoded(_decodeNullableMapResult, 'getLibraryScanExitInfo', {
      'started_after': startedAfter,
    });
  }

  static Future<void> setLibraryCoverCacheDir(String cacheDir) =>
      _invokeVoid('setLibraryCoverCacheDir', {'cache_dir': cacheDir});

  static Future<LibraryScanNDJSONFile> scanLibraryFolderToNDJSONFile(
    String folderPath, {
    bool forceFullScan = false,
    bool Function()? isCancelled,
  }) {
    return _scanToNDJSONFile(
      method: 'scanLibraryFolderToNDJSONFile',
      arguments: {'folder_path': folderPath},
      forceFullScan: forceFullScan,
      isCancelled: isCancelled,
    );
  }

  static Future<Map<String, dynamic>> scanLibraryFolderIncremental(
    String folderPath,
    Map<String, int> existingFiles,
  ) async => _invokeMap('scanLibraryFolderIncremental', {
    'folder_path': folderPath,
    'existing_files': jsonEncode(existingFiles),
  });

  static Future<LibraryScanNDJSONFile> scanSafTreeToNDJSONFile(
    String treeUri, {
    bool forceFullScan = false,
    bool Function()? isCancelled,
  }) {
    return _scanToNDJSONFile(
      method: 'scanSafTreeToNDJSONFile',
      arguments: {'tree_uri': treeUri},
      forceFullScan: forceFullScan,
      isCancelled: isCancelled,
    );
  }

  static Future<LibraryScanNDJSONFile> _scanToNDJSONFile({
    required String method,
    required Map<String, dynamic> arguments,
    required bool forceFullScan,
    bool Function()? isCancelled,
  }) async {
    // Stable support-directory path lets native SAF scans resume after process death.
    _ensureBackendEventHandler();
    final scanDir = await getApplicationSupportDirectory();
    await scanDir.create(recursive: true);
    final identity = jsonEncode(<String, dynamic>{
      'method': method,
      'arguments': arguments,
    });
    final hash = fnv1a32(utf8.encode(identity));
    final output = File(
      '${scanDir.path}${Platform.pathSeparator}'
      'library_scan_${hash.toRadixString(16).padLeft(8, '0')}.ndjson',
    );
    // Partial output is kept on failure; native SAF scan resumes from its sidecar.
    if (isCancelled?.call() == true) {
      throw StateError('Library scan cancelled before native scan');
    }
    if (forceFullScan) {
      // An explicit rescan must not resume metadata from an earlier attempt.
      for (final file in [output, File('${output.path}.state')]) {
        if (await file.exists()) await file.delete();
      }
      // Native scans extract thumbnails older than this marker again under
      // their stable names, so playlists and sessions keep valid paths.
      final covers = Directory('${scanDir.path}/library_covers');
      await covers.create(recursive: true);
      await File(
        '${covers.path}/$libraryCoverRegenerateMarker',
      ).writeAsString(DateTime.now().toIso8601String(), flush: true);
      await setLibraryCoverCacheDir(covers.path);
    }
    if (isCancelled?.call() == true) {
      throw StateError('Library scan cancelled before native scan');
    }
    final result = await _channel.invokeMethod(method, {
      ...arguments,
      'output_path': output.path,
    });
    if (result is! Map) {
      throw FormatException('$method returned ${result.runtimeType}');
    }
    if (result['cancelled'] == true) {
      throw FormatException('$method returned a cancelled partial scan');
    }
    final errors = result['error_count'];
    if (errors is! num ||
        !errors.isFinite ||
        errors < 0 ||
        errors != errors.toInt()) {
      throw FormatException('$method returned an invalid error count');
    }
    final pathValue = result['path'];
    final countValue = result['count'];
    if (pathValue is! String || pathValue.trim().isEmpty) {
      throw FormatException('$method returned an invalid output path');
    }
    if (countValue is! num ||
        !countValue.isFinite ||
        countValue < 0 ||
        countValue != countValue.toInt()) {
      throw FormatException('$method returned an invalid row count');
    }
    final path = pathValue;
    final count = countValue.toInt();
    final file = File(path);
    if (!await file.exists()) {
      throw FormatException('$method did not create its output file');
    }
    return LibraryScanNDJSONFile(
      file: file,
      expectedCount: count,
      errorCount: errors.toInt(),
    );
  }

  static Future<Map<String, dynamic>> scanSafTreeIncremental(
    String treeUri,
    Map<String, int> existingFiles,
  ) async {
    _ensureBackendEventHandler();
    return _invokeMap('scanSafTreeIncremental', {
      'tree_uri': treeUri,
      'existing_files': jsonEncode(existingFiles),
    });
  }

  static Future<Map<String, int>> getSafFileModTimes(List<String> uris) async {
    final map = await _invokeMap('getSafFileModTimes', {
      'uris': jsonEncode(uris),
    });
    return map.map((key, value) => MapEntry(key, (value as num).toInt()));
  }

  static Future<Map<String, dynamic>> getLibraryScanProgress() async =>
      _decodeMapResult(await _channel.invokeMethod('getLibraryScanProgress'));

  static Stream<Map<String, dynamic>> libraryScanProgressStream() {
    return _channel.libraryScanProgressEvents().map(_decodeMapResult);
  }

  static Future<void> cancelLibraryScan() => _invokeVoid('cancelLibraryScan');

  /// Holds the running native scan at its next file; it keeps its position.
  static Future<void> pauseLibraryScan() => _invokeVoid('pauseLibraryScan');

  static Future<void> resumeLibraryScan() => _invokeVoid('resumeLibraryScan');

  static Object? _decodeJsonResult(dynamic result) {
    if (result is String) {
      if (result.isEmpty) return null;
      return jsonDecode(result);
    }
    return result;
  }

  static Future<Object?> _decodeJsonResultAsync(dynamic result) async {
    if (result is Map && result[_jsonResultFileKey] is String) {
      final file = File(result[_jsonResultFileKey] as String);
      try {
        // Read and decode where the result is built, so the UI isolate never
        // retains a second copy of the entire spill file's text.
        return await compute(_decodeJsonFileInBackground, file.path);
      } finally {
        try {
          await file.delete();
        } catch (_) {}
      }
    }
    if (result is String &&
        result.length >= _backgroundJsonDecodeThresholdBytes) {
      return _decodeJsonStringAsync(result);
    }
    return _decodeJsonResult(result);
  }

  static Future<Object?> _decodeJsonStringAsync(String json) {
    if (json.length < _backgroundJsonDecodeThresholdBytes) {
      return Future<Object?>.value(jsonDecode(json));
    }
    return compute(_decodeJsonInBackground, json);
  }

  static Map<String, dynamic>? _decodeNullableMapResult(
    dynamic result,
    String method,
  ) {
    final decoded = _decodeJsonResult(result);
    if (decoded == null) return null;
    if (decoded is Map) {
      return decoded.cast<String, dynamic>();
    }
    throw FormatException(
      'Expected nullable map result from $method, got ${decoded.runtimeType}',
    );
  }

  static Future<Map<String, dynamic>> _decodeRequiredMapResultAsync(
    dynamic result,
    String method,
  ) async {
    final decoded = await _decodeJsonResultAsync(result);
    if (decoded is Map) {
      return decoded.cast<String, dynamic>();
    }
    throw FormatException(
      'Expected map result from $method, got ${decoded.runtimeType}',
    );
  }

  static Map<String, dynamic> _decodeMapResult(dynamic result) {
    if (result is Map) {
      return result.cast<String, dynamic>();
    }
    if (result is String) {
      if (result.isEmpty) return const <String, dynamic>{};
      final decoded = jsonDecode(result);
      if (decoded is Map) {
        return decoded.cast<String, dynamic>();
      }
    }
    return const <String, dynamic>{};
  }

  /// Present the native iOS folder picker. The security-scoped bookmark is
  /// created inside the picker callback while its access grant is still
  /// active; creating it later from a bare path loses the grant.
  /// Returns null when the user cancels; throws on picker/bookmark failure.
  static Future<IosPickedDirectory?> pickIosDirectory() async {
    _log.i('Opening iOS folder picker');
    try {
      final result = await _channel.invokeMethod('pickIosDirectory');
      final map = _decodeNullableMapResult(result, 'pickIosDirectory');
      if (map == null) {
        _log.i('iOS folder selection cancelled');
        return null;
      }
      final path = map['path'] as String? ?? '';
      final bookmark = map['bookmark'] as String? ?? '';
      if (!path.startsWith('/') || bookmark.trim().isEmpty) {
        throw const FormatException('Invalid iOS folder selection');
      }
      _log.i('iOS folder selected with an access bookmark');
      return IosPickedDirectory(path: path, bookmark: bookmark);
    } catch (error, stack) {
      _log.e('iOS folder selection failed', error, stack);
      rethrow;
    }
  }

  /// Resolve a base64-encoded iOS security-scoped bookmark and start accessing
  /// the resource. The returned lease must be passed to
  /// [stopAccessingIosBookmark] by the operation that acquired it.
  static Future<IosSecurityScopedAccess?> startAccessingIosBookmark(
    String bookmark,
  ) async {
    try {
      final result = await _channel.invokeMethod('startAccessingIosBookmark', {
        'bookmark': bookmark,
      });
      if (result is! Map) {
        throw FormatException(
          'startAccessingIosBookmark returned ${result.runtimeType}',
        );
      }
      final path = result['path'];
      final token = result['token'];
      if (path is! String ||
          path.trim().isEmpty ||
          token is! String ||
          token.trim().isEmpty) {
        throw const FormatException('Invalid iOS bookmark access lease');
      }
      return IosSecurityScopedAccess(path: path, token: token);
    } catch (e) {
      _log.w('Failed to start accessing iOS bookmark: $e');
      return null;
    }
  }

  /// Releases exactly the security-scoped lease acquired by the caller.
  static Future<void> stopAccessingIosBookmark(
    IosSecurityScopedAccess access,
  ) async {
    try {
      await _channel.invokeMethod('stopAccessingIosBookmark', {
        'token': access.token,
      });
    } catch (e) {
      _log.w('Failed to stop accessing iOS bookmark: $e');
    }
  }

  static Future<Map<String, dynamic>?> readAudioMetadata(
    String filePath,
  ) async {
    try {
      final result = await _channel.invokeMethod('readAudioMetadata', {
        'file_path': filePath,
      });
      return _decodeNullableMapResult(result, 'readAudioMetadata');
    } catch (e) {
      _log.w('Failed to read audio metadata: $e');
      return null;
    }
  }

  static Future<Map<String, dynamic>> parseCueSheet(
    String cuePath, {
    String audioDir = '',
  }) {
    _log.i('parseCueSheet: $cuePath (audioDir: $audioDir)');
    return _invokeMap('parseCueSheet', {
      'cue_path': cuePath,
      'audio_dir': audioDir,
    });
  }
}
