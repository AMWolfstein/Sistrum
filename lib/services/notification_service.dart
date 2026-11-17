import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:sistrum/constants/app_info.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/utils/string_utils.dart';

class NotificationService {
  static final NotificationService _instance = NotificationService._internal();
  factory NotificationService() => _instance;
  NotificationService._internal()
    : _notifications = FlutterLocalNotificationsPlugin();

  @visibleForTesting
  NotificationService.forTesting(FlutterLocalNotificationsPlugin notifications)
    : _notifications = notifications;

  final FlutterLocalNotificationsPlugin _notifications;
  bool _isInitialized = false;
  Future<void>? _initialization;
  AppLocalizations? _l10n;

  /// Call this from the widget tree (e.g. didChangeDependencies) whenever the
  /// app locale changes so that notification strings stay in sync.
  void updateStrings(AppLocalizations l10n) {
    _l10n = l10n;
  }

  String get embeddingMetadataLabel =>
      _l10n?.notifEmbeddingMetadata ?? 'Embedding metadata...';

  String get libraryScanInterruptedMessage =>
      _l10n?.notifLibraryScanInterruptedBody ??
      'The previous library scan was interrupted. Scan the folder again to continue. Your indexed tracks are still available.';

  static const String libraryScanWorkKind = 'library_scan';
  static const String updateDownloadWorkKind = 'app_update';

  // Kinds whose progress the Android foreground-service notification shows,
  // replacing the plugin's progress notification so only one is visible.
  final Set<String> _serviceNotificationKinds = {};

  /// Keeps user-started work running when the screen turns off (Android
  /// foreground service). Pair with [endBackgroundWork].
  Future<void> beginBackgroundWork(String kind, {required String title}) async {
    if (!Platform.isAndroid) return;
    try {
      final started = await PlatformBridge.startBackgroundWork(
        kind,
        title: title,
      );
      if (started && Platform.isAndroid) _serviceNotificationKinds.add(kind);
    } catch (e) {
      debugPrint('Background work $kind could not start: $e');
    }
  }

  Future<void> endBackgroundWork(String kind) async {
    if (!Platform.isAndroid) return;
    _serviceNotificationKinds.remove(kind);
    try {
      await PlatformBridge.stopBackgroundWork(kind);
    } catch (e) {
      debugPrint('Background work $kind could not stop: $e');
    }
  }

  Future<void> beginLibraryScanWork() => beginBackgroundWork(
    libraryScanWorkKind,
    title: _l10n?.notifScanningLibrary ?? 'Scanning local library',
  );

  Future<void> beginUpdateDownloadWork({required String version}) =>
      beginBackgroundWork(
        updateDownloadWorkKind,
        title:
            _l10n?.notifDownloadingUpdate(version) ??
            'Downloading ${AppInfo.appName} v$version',
      );

  Future<bool> _showInBackgroundWork(
    String kind, {
    required String title,
    required String body,
    required int progress,
  }) async {
    if (!_serviceNotificationKinds.contains(kind)) return false;
    try {
      await PlatformBridge.updateBackgroundWork(
        kind,
        title: title,
        text: body,
        progress: progress,
      );
      return true;
    } catch (e) {
      debugPrint('Background work $kind notification failed: $e');
      return false;
    }
  }

  static const int updateDownloadId = 2;
  static const int libraryScanId = 3;
  static const String channelId = 'download_progress';
  static const String channelName = 'Download Progress';
  static const String channelDescription = 'Shows download progress for tracks';
  static const String alertChannelId = 'download_alerts_v1';
  static const String alertChannelName = 'Download Alerts';
  static const String alertChannelDescription =
      'Important download status and actions that need attention';
  static const String libraryChannelId = 'library_scan';
  static const String libraryChannelName = 'Library Scan';
  static const String libraryChannelDescription =
      'Shows local library scan progress';

  Future<void> initialize() {
    if (_isInitialized) return Future.value();
    return _initialization ??= _initialize().whenComplete(() {
      _initialization = null;
    });
  }

  Future<void> _initialize() async {
    // Use the notification mark without the adaptive launcher's padding.
    const androidSettings = AndroidInitializationSettings(
      '@drawable/ic_stat_sistrum',
    );

    const initSettings = InitializationSettings(android: androidSettings);

    await _notifications.initialize(
      settings: initSettings,
      onDidReceiveNotificationResponse: (_) {},
    );

    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      final androidImpl = _notifications
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >();
      await androidImpl?.createNotificationChannel(
        const AndroidNotificationChannel(
          channelId,
          channelName,
          description: channelDescription,
          importance: Importance.low,
          showBadge: false,
          playSound: false,
          enableVibration: false,
        ),
      );
      await androidImpl?.createNotificationChannel(
        const AndroidNotificationChannel(
          alertChannelId,
          alertChannelName,
          description: alertChannelDescription,
          importance: Importance.defaultImportance,
          playSound: true,
          enableVibration: true,
        ),
      );
      await androidImpl?.createNotificationChannel(
        const AndroidNotificationChannel(
          libraryChannelId,
          libraryChannelName,
          description: libraryChannelDescription,
          importance: Importance.low,
          showBadge: false,
          playSound: false,
          enableVibration: false,
        ),
      );
    }

    _isInitialized = true;
  }

  Future<void> _showSafely({
    required int id,
    required String title,
    required String body,
    required NotificationDetails details,
    String? payload,
  }) async {
    await _notifications.show(
      id: id,
      title: title,
      body: body,
      notificationDetails: details,
      payload: payload,
    );
  }

  /// Builds [NotificationDetails]. A non-null [progress] switches to the
  /// low-importance ongoing progress style; a negative value is indeterminate.
  /// Otherwise builds a dismissible alert.
  NotificationDetails _details({
    bool library = false,
    int? progress,
    bool playSound = false,
    bool presentBadge = false,
    bool presentSound = false,
  }) {
    assert(!library || !playSound);
    final inProgress = progress != null;
    assert(!inProgress || !playSound);
    final androidChannelId = library
        ? libraryChannelId
        : playSound
        ? alertChannelId
        : channelId;
    final androidChannelName = library
        ? libraryChannelName
        : playSound
        ? alertChannelName
        : channelName;
    final androidChannelDescription = library
        ? libraryChannelDescription
        : playSound
        ? alertChannelDescription
        : channelDescription;

    return NotificationDetails(
      android: AndroidNotificationDetails(
        androidChannelId,
        androidChannelName,
        channelDescription: androidChannelDescription,
        importance: inProgress ? Importance.low : Importance.defaultImportance,
        priority: inProgress ? Priority.low : Priority.defaultPriority,
        showProgress: inProgress,
        maxProgress: inProgress ? 100 : 0,
        progress: progress?.clamp(0, 100) ?? 0,
        indeterminate: (progress ?? 0) < 0,
        ongoing: inProgress,
        autoCancel: !inProgress,
        playSound: playSound,
        enableVibration: !inProgress,
        onlyAlertOnce: inProgress,
        icon: '@drawable/ic_stat_sistrum',
      ),
    );
  }

  Future<void> showLibraryScanProgress({
    required double progress,
    required int scannedFiles,
    required int totalFiles,
    String? currentFile,
  }) async {
    if (!_isInitialized) await initialize();

    // Network scans read tags while discovering folders, so their total is
    // unknown. Zero percent would falsely imply that no files were processed.
    // Use the same indeterminate value for the foreground service and plugin.
    final hasTotal = totalFiles > 0;
    final percentage = hasTotal ? progress.clamp(0.0, 100.0).round() : -1;
    final progressBody = hasTotal
        ? (_l10n?.notifLibraryScanProgressWithTotal(
                scannedFiles,
                totalFiles,
                percentage,
              ) ??
              '$scannedFiles/$totalFiles files • $percentage%')
        : (_l10n?.notifLibraryScanProgressNoTotal(scannedFiles) ??
              '$scannedFiles files scanned');
    final body = (currentFile != null && currentFile.isNotEmpty)
        ? '$progressBody\n$currentFile'
        : progressBody;
    final title = _l10n?.notifScanningLibrary ?? 'Scanning local library';
    if (await _showInBackgroundWork(
      libraryScanWorkKind,
      title: title,
      body: body,
      progress: percentage,
    )) {
      return;
    }

    await _showSafely(
      id: libraryScanId,
      title: title,
      body: body,
      details: _details(library: true, progress: percentage),
    );
  }

  Future<void> showLibraryScanComplete({
    required int totalTracks,
    int excludedDownloadedCount = 0,
    int errorCount = 0,
  }) async {
    if (!_isInitialized) await initialize();

    final extras = <String>[];
    if (excludedDownloadedCount > 0) {
      extras.add(
        _l10n?.notifLibraryScanExcluded(excludedDownloadedCount) ??
            '$excludedDownloadedCount excluded',
      );
    }
    if (errorCount > 0) {
      extras.add(
        _l10n?.notifLibraryScanErrors(errorCount) ?? '$errorCount errors',
      );
    }
    final suffix = extras.isEmpty ? '' : ' (${extras.join(', ')})';

    await _showSafely(
      id: libraryScanId,
      title: _l10n?.notifLibraryScanComplete ?? 'Library scan complete',
      body:
          '${_l10n?.notifLibraryScanCompleteBody(totalTracks) ?? '$totalTracks tracks indexed'}$suffix',
      details: _details(library: true),
    );
  }

  Future<void> showLibraryScanFailed(String message) async {
    if (!_isInitialized) await initialize();

    await _showSafely(
      id: libraryScanId,
      title: _l10n?.notifLibraryScanFailed ?? 'Library scan failed',
      body: message,
      details: _details(library: true),
    );
  }

  Future<void> showLibraryScanCancelled() async {
    if (!_isInitialized) await initialize();

    await _showSafely(
      id: libraryScanId,
      title: _l10n?.notifLibraryScanCancelled ?? 'Library scan cancelled',
      body: _l10n?.notifLibraryScanStopped ?? 'Scan stopped before completion.',
      details: _details(library: true),
    );
  }

  Future<void> showLibraryScanPaused({required int scannedFiles}) async {
    if (!_isInitialized) await initialize();

    await _showSafely(
      id: libraryScanId,
      title: _l10n?.notifLibraryScanPaused ?? 'Library scan paused',
      body:
          _l10n?.notifLibraryScanPausedBody(scannedFiles) ??
          '$scannedFiles files scanned. Resume in Local Library settings to '
              'continue from here.',
      details: _details(library: true),
    );
  }

  /// Clears a paused-scan notice once the foreground service shows progress.
  Future<void> cancelLibraryScanNotification() async {
    await _notifications.cancel(id: libraryScanId);
  }

  Future<void> showUpdateDownloadProgress({
    required String version,
    required int received,
    required int total,
  }) async {
    if (!_isInitialized) await initialize();

    final percentage = total > 0 ? (received * 100 ~/ total) : 0;
    final receivedMB = formatMegabytes(received);
    final totalMB = formatMegabytes(total);
    final title =
        _l10n?.notifDownloadingUpdate(version) ??
        'Downloading ${AppInfo.appName} v$version';
    final body =
        _l10n?.notifUpdateProgress(receivedMB, totalMB, percentage) ??
        '$receivedMB / $totalMB MB • $percentage%';
    if (await _showInBackgroundWork(
      updateDownloadWorkKind,
      title: title,
      body: body,
      progress: total > 0 ? percentage : -1,
    )) {
      return;
    }

    await _showSafely(
      id: updateDownloadId,
      title: title,
      body: body,
      details: _details(progress: percentage),
    );
  }

  Future<void> showUpdateDownloadComplete({required String version}) async {
    if (!_isInitialized) await initialize();

    await _showSafely(
      id: updateDownloadId,
      title: _l10n?.notifUpdateReady ?? 'Update Ready',
      body:
          _l10n?.notifUpdateReadyBody(version) ??
          '${AppInfo.appName} v$version downloaded. Tap to install.',
      details: _details(
        playSound: true,
        presentBadge: true,
        presentSound: true,
      ),
    );
  }

  Future<void> showUpdateDownloadFailed() async {
    if (!_isInitialized) await initialize();

    await _showSafely(
      id: updateDownloadId,
      title: _l10n?.notifUpdateFailed ?? 'Update Failed',
      body:
          _l10n?.notifUpdateFailedBody ??
          'Could not download update. Try again later.',
      details: _details(playSound: true),
    );
  }

  Future<void> cancelUpdateNotification() async {
    await _notifications.cancel(id: updateDownloadId);
  }
}
