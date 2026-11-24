import 'dart:async';
import 'dart:io';
import 'package:sistrum/services/audio_file_open_service.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart'
    show SystemChrome, SystemUiMode, rootBundle;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/app.dart';
import 'package:sistrum/constants/app_info.dart';
import 'package:sistrum/l10n/supported_locales.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/services/notification_service.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/share_intent_service.dart';
import 'package:sistrum/services/app_state_database.dart';
import 'package:sistrum/services/app_orientation.dart';
import 'package:sistrum/services/activity_attachment.dart';
import 'package:sistrum/services/app_crash_service.dart';
import 'package:sistrum/services/background_favorites.dart';
import 'package:sistrum/services/desktop_diagnostics.dart';
import 'package:sistrum/services/desktop_library_watcher.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/services/playback_notification.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/local_library_scan_prefs.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart'
    show restoreDesktopSidebar;
import 'package:sistrum/widgets/desktop/desktop_inspector.dart'
    show bindDesktopFullScreenLyrics;
import 'package:sistrum/widgets/desktop/desktop_wheel_smoother.dart';
import 'package:sistrum/widgets/desktop/desktop_window.dart'
    show listenDesktopWindowFullScreen;

final _log = AppLogger('Main');

/// Desktop binding: eases mouse wheel notches before they reach the
/// scrollables (see [DesktopWheelSmoother]).
class _DesktopBinding extends WidgetsFlutterBinding {
  static _DesktopBinding? _instance;

  static WidgetsBinding ensureInitialized() => _instance ??= _DesktopBinding();

  late final _wheel = DesktopWheelSmoother.forBinding(
    this,
    dispatch: _handlePointerEvent,
  );

  void _handlePointerEvent(PointerEvent event) =>
      super.handlePointerEvent(event);

  @override
  void handlePointerEvent(PointerEvent event) {
    if (_wheel.handlePointerEvent(event)) return;
    super.handlePointerEvent(event);
  }
}

void main() {
  // Catch uncaught Dart errors so a failing async path is logged, not fatal.
  // Native backend crashes still can't be caught here.
  runZonedGuarded(
    () async {
      final binding = isDesktopPlatform && kDesktopWheelSmoothing
          ? _DesktopBinding.ensureInitialized()
          : WidgetsFlutterBinding.ensureInitialized();
      // The preferences plugin answers without an Activity, so on Android
      // the load overlaps the Activity's creation.
      final preferences = SharedPreferences.getInstance();
      if (Platform.isAndroid) {
        // A media service can start the engine before MainActivity registers
        // the backend and platform channels. Calls made earlier would fail or
        // never be answered, leaving the backend uninitialized. Playback has
        // its channels on the engine, so a car's media browser that started
        // the app can browse and play at once.
        if (!isActivityAttached(binding)) {
          unawaited(_startBackgroundPlayback());
        }
        await waitForActivityAttachment(binding);
        // Android 15 and later enforce this; older versions stop the app
        // above the navigation bar, so no screen could reach the bottom edge.
        unawaited(SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge));
      }
      // Awaited before runApp, which reports a failure once.
      final orientation = _initializeOrientation()..ignore();
      // Desktop: log files and the unclean-exit marker, before the error
      // hooks below start writing to them.
      await DesktopDiagnostics.initialize();
      LicenseRegistry.addLicense(() async* {
        yield LicenseEntryWithLineBreaks(
          const [AppInfo.appName],
          '${AppInfo.copyright}\n\n${AppInfo.license}\n\n'
          '${await rootBundle.loadString('LICENSE')}\n\n'
          '${await rootBundle.loadString('COPYING')}',
        );
        yield LicenseEntryWithLineBreaks(const [
          'Inter',
        ], await rootBundle.loadString('assets/fonts/Inter-LICENSE.txt'));
        yield LicenseEntryWithLineBreaks(const [
          'Material Color Utilities (native palette)',
          'Dart SDK (native Random compatibility)',
        ], await rootBundle.loadString('assets/licenses/native_palette.txt'));
        yield LicenseEntryWithLineBreaks(const [
          'rusqlite',
          'roxmltree',
        ], await rootBundle.loadString('assets/licenses/native_data_jobs.txt'));
        yield LicenseEntryWithLineBreaks(const [
          'Unihan (Unicode Han Database)',
          'phrase-pinyin-data',
        ], await rootBundle.loadString('assets/licenses/romanization.txt'));
        yield LicenseEntryWithLineBreaks(const [
          'FFmpeg',
        ], await rootBundle.loadString('assets/licenses/ffmpeg.txt'));
        // FFmpegKit (mobile) and the Linux and Windows FFmpeg builds.
        yield LicenseEntryWithLineBreaks(const [
          'FFmpeg',
          'FFmpegKit',
        ], await rootBundle.loadString('assets/licenses/lgpl-3.0.txt'));
        if (Platform.isAndroid) {
          yield LicenseEntryWithLineBreaks(const [
            'libusb',
            'libusb1-sys',
          ], await rootBundle.loadString('assets/licenses/usb.txt'));
          yield LicenseEntryWithLineBreaks(const [
            'Oboe',
          ], await rootBundle.loadString('assets/licenses/oboe.txt'));
          yield LicenseEntryWithLineBreaks(const [
            'WavPack',
          ], await rootBundle.loadString('assets/licenses/wavpack.txt'));
          yield LicenseEntryWithLineBreaks(const [
            'libFLAC',
          ], await rootBundle.loadString('assets/licenses/flac.txt'));
        }
      });

      final previousOnError = FlutterError.onError;
      FlutterError.onError = (details) {
        previousOnError?.call(details);
        _log.e('Uncaught Flutter error: ${details.exceptionAsString()}');
        unawaited(
          AppCrashService.recordRuntimeError(details.exception, details.stack),
        );
      };
      WidgetsBinding.instance.platformDispatcher.onError = (error, stack) {
        _log.e('Uncaught platform error: $error');
        unawaited(AppCrashService.recordRuntimeError(error, stack));
        return true;
      };

      final prefs = await preferences;
      var bootstrapSettings = loadBootstrapSettings(prefs);
      if (await _prepareAndroidInstallationState(prefs)) {
        bootstrapSettings = loadBootstrapSettings(prefs);
      }
      musicPlayerRuntime.configure(bootstrapSettings);
      if (isDesktopPlatform) restoreDesktopSidebar(prefs);
      final bootstrapTheme = loadBootstrapThemeSettings(prefs);
      final runtimeProfile = await _resolveRuntimeProfile(prefs);
      _configureImageCache(runtimeProfile);
      await orientation;
      runApp(
        ProviderScope(
          overrides: [
            lowEndDeviceProvider.overrideWithValue(
              runtimeProfile.disableOverscrollEffects,
            ),
            deviceSupportsBackdropBlurProvider.overrideWithValue(
              runtimeProfile.enableBackdropBlur,
            ),
            // The RAM tiers describe Android hardware. Other platforms keep
            // the existing full material by default.
            deviceSupportsLiquidGlassProvider.overrideWithValue(
              !Platform.isAndroid || runtimeProfile.enableBackdropBlur,
            ),
            initialSettingsProvider.overrideWithValue(bootstrapSettings),
            initialThemeSettingsProvider.overrideWithValue(bootstrapTheme),
          ],
          child: EagerInitialization(
            child: SistrumApp(
              disableOverscrollEffects: runtimeProfile.disableOverscrollEffects,
            ),
          ),
        ),
      );
      // Waits until startup settles, then analyzes how songs sound for
      // Autoplay. Media browsers that start without the app shell skip it.
      musicPlayerRuntime.startSongAnalysis();
    },
    (error, stack) {
      _log.e('Uncaught zone error: $error');
      unawaited(AppCrashService.recordRuntimeError(error, stack));
    },
  );
}

/// The player, its saved session and the media controls, from the stored
/// settings, for a media browser such as Android Auto that starts the app
/// without an Activity. The app shell takes the controls over once it opens.
Future<void> _startBackgroundPlayback() async {
  try {
    final prefs = await SharedPreferences.getInstance();
    // An Activity that attached meanwhile runs the full startup instead.
    if (isActivityAttached(WidgetsBinding.instance)) return;
    final settings = loadBootstrapSettings(prefs);
    final runtime = musicPlayerRuntime..configure(settings);
    final l10n = appLocalizationsFor(settings.locale);
    runtime.updateStrings(
      unknownTitle: l10n.unknownTitle,
      unknownArtist: l10n.unknownArtist,
    );
    final dependencies = runtime.dependencies;
    runtime.configureBackgroundNotification(
      presentation: PlaybackNotification(
        favoriteLabel: l10n.trackOptionAddToLoved,
        unfavoriteLabel: l10n.trackOptionRemoveFromLoved,
        shuffleOnLabel: l10n.nowPlayingShuffleOn,
        shuffleOffLabel: l10n.nowPlayingPlayInOrder,
      ),
      favorites: BackgroundFavorites(
        library: dependencies.libraryDatabase,
        collections: dependencies.collectionsDatabase,
      ),
    );
    await runtime.restoreSession();
    await runtime.initialize();
  } catch (error, stack) {
    _log.e('Failed to start playback without the app open', error, stack);
  }
}

/// Resolves Android TV, then the orientation, alongside the other startup
/// calls.
Future<void> _initializeOrientation() async {
  await initializeAndroidTv();
  // MainActivity.onCreate already asks phones for portrait and lets tablets
  // follow the device, as the request below would, and the router observer
  // repeats it on the first page. TV gets fixed landscape there and the user
  // landscape here.
  if (Platform.isAndroid && !isAndroidTv) return;
  await setAppOrientation();
}

const _runtimeProfileTierKey = 'runtime_profile_tier_v1';

/// Returns whether a restored-installation reset started, which may have
/// rewritten the stored settings.
Future<bool> _prepareAndroidInstallationState(SharedPreferences prefs) async {
  if (!Platform.isAndroid) return false;

  var resetStarted = false;
  try {
    final hasPersistedSettings = hasPersistedAppSettings(prefs);
    final installState = await PlatformBridge.ensureInstallMarker();
    final shouldReset = shouldResetRestoredInstallation(
      hasPersistedSettings: hasPersistedSettings,
      installState: installState,
    );
    if (!shouldReset) return false;

    resetStarted = true;
    _log.w(
      'Android restored state into a fresh installation; resetting '
      'installation-bound settings',
    );
    await resetRestoredInstallationSettings(prefs);
    await prefs.remove(_runtimeProfileTierKey);
    await prefs.remove(localLibraryLastScannedAtKey);
    await AppStateDatabase.instance.clearAfterInstallationRestore();
  } catch (e) {
    _log.w('Failed to inspect restored installation state: $e');
  }
  return resetStarted;
}

/// The runtime profile: the tier stored by an earlier launch, or one picked
/// from the device when none is stored yet.
Future<_RuntimeProfile> _resolveRuntimeProfile(SharedPreferences prefs) async {
  final cachedTier = prefs.getString(_runtimeProfileTierKey);
  if (cachedTier != null) {
    final cached = _RuntimeProfile.fromTier(cachedTier);
    if (cached != null) return cached;
  }

  const defaults = _RuntimeProfile.standard();

  if (!Platform.isAndroid) {
    await prefs.setString(_runtimeProfileTierKey, defaults.tier);
    return defaults;
  }

  try {
    final androidInfo = await DeviceInfoPlugin().androidInfo;
    final isArm32Only = androidInfo.supported64BitAbis.isEmpty;
    final isLowRamDevice =
        androidInfo.isLowRamDevice || androidInfo.physicalRamSize <= 2500;

    final profile = (isArm32Only || isLowRamDevice)
        ? const _RuntimeProfile.low()
        : androidInfo.physicalRamSize >= 6000
        ? const _RuntimeProfile.high()
        : defaults;
    await prefs.setString(_runtimeProfileTierKey, profile.tier);
    return profile;
  } catch (e) {
    debugPrint('Failed to resolve runtime profile: $e');
    return defaults;
  }
}

void _configureImageCache(_RuntimeProfile runtimeProfile) {
  final imageCache = PaintingBinding.instance.imageCache;
  // Keep memory cache bounded so cover-heavy pages don't retain too many
  // full-resolution images simultaneously.
  imageCache.maximumSize = runtimeProfile.imageCacheMaximumSize;
  imageCache.maximumSizeBytes = runtimeProfile.imageCacheMaximumSizeBytes;
}

class _RuntimeProfile {
  final String tier;
  final int imageCacheMaximumSize;
  final int imageCacheMaximumSizeBytes;
  final bool disableOverscrollEffects;
  final bool enableBackdropBlur;

  const _RuntimeProfile._({
    required this.tier,
    required this.imageCacheMaximumSize,
    required this.imageCacheMaximumSizeBytes,
    required this.disableOverscrollEffects,
    required this.enableBackdropBlur,
  });

  const _RuntimeProfile.low()
    : this._(
        tier: 'low',
        imageCacheMaximumSize: 120,
        imageCacheMaximumSizeBytes: 24 << 20,
        disableOverscrollEffects: true,
        enableBackdropBlur: false,
      );

  const _RuntimeProfile.standard()
    : this._(
        tier: 'standard',
        imageCacheMaximumSize: 240,
        imageCacheMaximumSizeBytes: 60 << 20,
        disableOverscrollEffects: false,
        enableBackdropBlur: false,
      );

  const _RuntimeProfile.high()
    : this._(
        tier: 'high',
        imageCacheMaximumSize: 320,
        imageCacheMaximumSizeBytes: 80 << 20,
        disableOverscrollEffects: false,
        enableBackdropBlur: true,
      );

  static _RuntimeProfile? fromTier(String tier) => switch (tier) {
    'low' => const _RuntimeProfile.low(),
    'standard' => const _RuntimeProfile.standard(),
    'high' => const _RuntimeProfile.high(),
    _ => null,
  };
}

class EagerInitialization extends ConsumerStatefulWidget {
  const EagerInitialization({super.key, required this.child});
  final Widget child;

  @override
  ConsumerState<EagerInitialization> createState() =>
      _EagerInitializationState();
}

class _EagerInitializationState extends ConsumerState<EagerInitialization>
    with WidgetsBindingObserver {
  ProviderSubscription<bool>? _localLibraryEnabledSub;
  Timer? _localLibraryWarmupTimer;
  bool _localLibraryWarmupScheduled = false;
  bool _autoScanTriggeredOnLaunch = false;
  DesktopLibraryWatcher? _desktopLibraryWatcher;
  ProviderSubscription<bool>? _desktopLibraryWatchSub;
  ProviderSubscription<List<LocalLibrarySource>>? _desktopLibrarySourcesSub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _logRefreshRate();
      if (isDesktopPlatform) {
        listenDesktopWindowFullScreen();
        bindDesktopFullScreenLyrics();
      }
      _initializeAppServices();
      _initializeDeferredProviders();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _localLibraryEnabledSub?.close();
    _localLibraryWarmupTimer?.cancel();
    _desktopLibraryWatchSub?.close();
    _stopDesktopLibraryWatcher();
    super.dispose();
  }

  @override
  void didChangeMetrics() => _logRefreshRate();

  double? _loggedRefreshRate;

  /// Notes the refresh rate the device gives the app, each time it changes,
  /// so a report of choppy motion shows whether the phone held the app at
  /// 60 Hz.
  void _logRefreshRate() {
    if (!mounted) return;
    final rate = View.maybeOf(context)?.display.refreshRate;
    if (rate == null || rate == _loggedRefreshRate) return;
    _loggedRefreshRate = rate;
    _log.i('Display refresh rate: ${rate.toStringAsFixed(0)} Hz');
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _logRefreshRate();
      _maybeAutoScanLocalLibrary();
      if (ref.read(localLibraryProvider.exists)) {
        unawaited(
          ref
              .read(localLibraryProvider.notifier)
              .refreshSourceAvailability(scanReconnected: true),
        );
      }
    } else if (state == AppLifecycleState.paused) {
      unawaited(PlatformBridge.releaseNativeMemory());
    }
  }

  @override
  void didHaveMemoryPressure() {
    // OS memory pressure: drop decoded bitmaps (disk caches stay intact) and
    // ask the native backend to release disposable runtimes and caches.
    final imageCache = PaintingBinding.instance.imageCache;
    imageCache.clear();
    imageCache.clearLiveImages();
    unawaited(PlatformBridge.releaseNativeMemory(underPressure: true));
  }

  void _initializeDeferredProviders() {
    _maybeScheduleLocalLibraryWarmup(
      ref.read(
        settingsProvider.select((settings) => settings.localLibraryEnabled),
      ),
    );

    _localLibraryEnabledSub = ref.listenManual<bool>(
      settingsProvider.select((settings) => settings.localLibraryEnabled),
      (previous, next) {
        if (next == true) {
          _maybeScheduleLocalLibraryWarmup(true);
        }
      },
    );

    if (isDesktopPlatform) {
      _desktopLibraryWatchSub = ref.listenManual<bool>(
        settingsProvider.select(_desktopLibraryWatchEnabled),
        (_, _) => _updateDesktopLibraryWatcher(),
      );
    }
  }

  /// Desktop watches library folders unless automatic scans are off.
  static bool _desktopLibraryWatchEnabled(AppSettings settings) =>
      settings.localLibraryEnabled && settings.localLibraryAutoScan != 'off';

  /// Starts or stops the desktop folder watcher. Starts only once the
  /// library provider is warm, so watching never delays startup.
  void _updateDesktopLibraryWatcher() {
    if (!isDesktopPlatform || !mounted) return;
    if (!_desktopLibraryWatchEnabled(ref.read(settingsProvider)) ||
        !ref.read(localLibraryProvider.exists)) {
      _stopDesktopLibraryWatcher();
      return;
    }
    if (_desktopLibraryWatcher != null) return;
    final watcher = _desktopLibraryWatcher = DesktopLibraryWatcher(
      scanSource: (sourceId) => ref
          .read(localLibraryProvider.notifier)
          .startSourceScan(sourceId, notify: false, waitForCurrentScan: true),
      isScanning: () => ref.read(localLibraryProvider).isScanning,
    );
    _desktopLibrarySourcesSub = ref.listenManual<List<LocalLibrarySource>>(
      localLibraryProvider.select((state) => state.sources),
      (_, sources) => unawaited(watcher.updateSources(sources)),
      fireImmediately: true,
    );
  }

  void _stopDesktopLibraryWatcher() {
    _desktopLibrarySourcesSub?.close();
    _desktopLibrarySourcesSub = null;
    unawaited(_desktopLibraryWatcher?.dispose());
    _desktopLibraryWatcher = null;
  }

  Timer _scheduleProviderWarmup(Duration delay, VoidCallback action) {
    return Timer(delay, () {
      if (!mounted) return;
      action();
    });
  }

  void _maybeScheduleLocalLibraryWarmup(bool enabled) {
    if (!enabled || _localLibraryWarmupScheduled) return;
    _localLibraryWarmupScheduled = true;
    _localLibraryWarmupTimer = _scheduleProviderWarmup(
      const Duration(milliseconds: 1600),
      () {
        if (!ref.read(settingsProvider).localLibraryEnabled) {
          _localLibraryWarmupScheduled = false;
          return;
        }
        ref.read(localLibraryProvider);
        _updateDesktopLibraryWatcher();
        if (!_autoScanTriggeredOnLaunch) {
          _autoScanTriggeredOnLaunch = true;
          Future.delayed(const Duration(milliseconds: 500), () {
            if (mounted) _maybeAutoScanLocalLibrary();
          });
        }
      },
    );
  }

  Future<void> _maybeAutoScanLocalLibrary() async {
    if (!mounted) return;

    final settings = ref.read(settingsProvider);
    if (!settings.localLibraryEnabled) return;
    if (settings.localLibraryAutoScan == 'off') return;

    final libraryState = ref.read(localLibraryProvider);
    if (libraryState.isScanning) return;

    final now = DateTime.now();
    final prefs = await SharedPreferences.getInstance();
    final pendingSourceId = prefs.getString(localLibraryActiveScanSourceKey);
    if (pendingSourceId != null && pendingSourceId.trim().isNotEmpty) {
      await ref
          .read(localLibraryProvider.notifier)
          .startSourceScan(
            pendingSourceId.trim(),
            notify: prefs.getBool(localLibraryActiveScanNotifyKey) ?? true,
          );
      return;
    }
    final lastScanned = readLocalLibraryLastScannedAt(prefs);

    if (lastScanned != null) {
      final elapsed = now.difference(lastScanned);

      switch (settings.localLibraryAutoScan) {
        case 'on_open':
          if (elapsed.inMinutes < 10) return;
          break;
        case 'daily':
          if (elapsed.inHours < 24) return;
          break;
        case 'weekly':
          if (elapsed.inDays < 7) return;
          break;
        default:
          return;
      }
    }

    await ref.read(localLibraryProvider.notifier).scanAllSources(notify: false);
  }

  Future<void> _initializeAppServices() async {
    try {
      await Future.wait([
        NotificationService().initialize(),
        ShareIntentService().initialize(),
        AudioFileOpenService.instance.initialize(),
      ]);
    } catch (e) {
      debugPrint('Failed to initialize app services: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
