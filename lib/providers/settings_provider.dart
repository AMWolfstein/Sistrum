import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/home_shortcut.dart';
import 'package:sistrum/models/automix_options.dart';
import 'package:sistrum/services/secure_storage_options.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';

const _settingsKey = 'app_settings';
const _settingsCorruptBackupKey = 'app_settings_corrupt_backup';
const _migrationVersionKey = 'settings_migration_version';
const _currentMigrationVersion = 16;

/// Marked the removed rebrand tour as seen.
const _retiredUpgradeIntroSeenKey = 'upgrade_intro_sistrum_seen';

/// The per-artist Albums sort, removed with the artist page's sort control.
const _retiredArtistReleaseSortsKey = 'artist_release_sorts';
const _spotifyClientSecretKey = 'spotify_client_secret';
final _log = AppLogger('SettingsProvider');

/// Startup override used to render the correct route/locale on the first
/// frame. The notifier still performs migrations and full validation after it
/// mounts.
final initialSettingsProvider = Provider<AppSettings>(
  (ref) => const AppSettings(),
);

/// Set during bootstrap when the saved Android SAF tree no longer has a valid
/// persisted grant. MainShell consumes this once to block downloads until the
/// user explicitly repairs or changes the destination.

bool hasPersistedAppSettings(SharedPreferences prefs) {
  final rawSettings = prefs.getString(_settingsKey);
  return rawSettings != null && rawSettings.isNotEmpty;
}

AppSettings loadBootstrapSettings(SharedPreferences prefs) {
  final rawSettings = prefs.getString(_settingsKey);
  if (rawSettings == null || rawSettings.isEmpty) return const AppSettings();
  try {
    final decoded = jsonDecode(rawSettings);
    if (decoded is! Map) return const AppSettings();
    return AppSettings.fromJson(Map<String, dynamic>.from(decoded));
  } catch (_) {
    return const AppSettings();
  }
}

/// Removes values that are only valid for one OS installation while keeping
/// portable user preferences. This is used when Android restores app data into
/// a fresh package install despite backup being disabled (for example an OEM
/// device-to-device transfer).
AppSettings resetInstallationBoundSettings(AppSettings settings) {
  return settings.copyWith(
    downloadDirectory: '',
    downloadDirectoryBookmark: '',
    storageMode: 'app',
    downloadTreeUri: '',
    isFirstLaunch: true,
    localLibraryEnabled: false,
    localLibraryPath: '',
    localLibraryBookmark: '',
    localLibraryAutoScan: 'off',
    hasCompletedTutorial: false,
  );
}

Future<void> resetRestoredInstallationSettings(SharedPreferences prefs) async {
  final rawSettings = prefs.getString(_settingsKey);
  if (rawSettings == null || rawSettings.isEmpty) return;

  try {
    final decoded = jsonDecode(rawSettings);
    if (decoded is! Map) {
      await prefs.remove(_settingsKey);
      return;
    }
    final restored = AppSettings.fromJson(Map<String, dynamic>.from(decoded));
    final sanitized = resetInstallationBoundSettings(restored);
    await prefs.setString(_settingsKey, jsonEncode(sanitized.toJson()));
  } catch (e) {
    _log.w('Failed to sanitize restored settings; resetting to defaults: $e');
    await prefs.remove(_settingsKey);
  }
}

class SettingsNotifier extends Notifier<AppSettings> {
  SettingsNotifier({Future<SharedPreferences> Function()? preferences})
    : _loadPreferences = preferences ?? SharedPreferences.getInstance;

  final Future<SharedPreferences> Function() _loadPreferences;
  Future<SharedPreferences> get _prefs => _loadPreferences();
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage(
    aOptions: secureStorageAndroidOptions,
    mOptions: secureStorageMacOsOptions,
  );
  Future<void>? _saveSettingsFuture;
  bool _saveQueued = false;
  String? _pendingSettingsJson;
  Future<void>? _loadSettingsFuture;

  @override
  AppSettings build() {
    unawaited(
      ensureLoaded().catchError((Object error, StackTrace stack) {
        _log.e('Failed to load settings', error, stack);
      }),
    );
    return ref.read(initialSettingsProvider);
  }

  /// Startup and extension actions share loading, including preference migrations.
  Future<void> ensureLoaded() {
    return _loadSettingsFuture ??= _loadSettings().catchError((
      Object error,
      StackTrace stack,
    ) {
      _loadSettingsFuture = null;
      Error.throwWithStackTrace(error, stack);
    });
  }

  Future<void> _loadSettings() async {
    final prefs = await _prefs;
    final rawSettings = prefs.getString(_settingsKey);
    if (rawSettings != null) {
      AppSettings? loaded;
      try {
        final decoded = jsonDecode(rawSettings);
        if (decoded is! Map) {
          throw const FormatException('settings root must be a JSON object');
        }
        loaded = AppSettings.fromJson(Map<String, dynamic>.from(decoded));
      } catch (e, stack) {
        _log.e('Failed to load settings, resetting to defaults: $e', e, stack);
        try {
          await prefs.setString(_settingsCorruptBackupKey, rawSettings);
          await prefs.remove(_settingsKey);
        } catch (backupError) {
          _log.w('Failed to backup corrupt settings: $backupError');
        }
      }

      if (loaded != null) {
        state = loaded;

        await _runMigrations(prefs);
        await _normalizeIosDownloadDirectoryIfNeeded();
      }
    }

    await _cleanupRetiredSpotifySettings();
  }

  Future<void> _runMigrations(SharedPreferences prefs) async {
    final lastMigration = prefs.getInt(_migrationVersionKey) ?? 0;

    if (lastMigration < _currentMigrationVersion) {
      if (state.downloadTreeUri.isNotEmpty && state.storageMode != 'saf') {
        state = state.copyWith(storageMode: 'saf');
      }
      // Migration 2: existing users who already completed setup should skip tutorial
      if (!state.isFirstLaunch && !state.hasCompletedTutorial) {
        state = state.copyWith(hasCompletedTutorial: true);
      }
      // Migration 15: the rebrand tour is gone, and so is its flag.
      if (lastMigration < 15) {
        await prefs.remove(_retiredUpgradeIntroSeenKey);
      }
      // Migration 16: the artist Albums sort is gone, and so is its stored choice.
      if (lastMigration < 16) {
        await prefs.remove(_retiredArtistReleaseSortsKey);
      }
      await prefs.setInt(_migrationVersionKey, _currentMigrationVersion);
      await _saveSettings();
    }
  }

  Future<void> _commit(AppSettings next) {
    state = next;
    return _saveSettings();
  }

  Future<void> _saveSettings() {
    _pendingSettingsJson = jsonEncode(state.toJson());

    final activeSave = _saveSettingsFuture;
    if (activeSave != null) {
      _saveQueued = true;
      return activeSave;
    }

    final saved = Completer<void>();
    _saveSettingsFuture = saved.future;
    unawaited(_drainSettingsSave(saved));
    return saved.future;
  }

  Future<void> _drainSettingsSave(Completer<void> saved) async {
    try {
      final prefs = await _prefs;
      do {
        final jsonToWrite = _pendingSettingsJson;
        _saveQueued = false;
        if (jsonToWrite != null) {
          await prefs.setString(_settingsKey, jsonToWrite);
        }
      } while (_saveQueued);
    } catch (e) {
      _log.e('Failed to save settings: $e');
    } finally {
      _saveSettingsFuture = null;
      saved.complete();
    }
  }

  /// Restores settings from a backup payload (the map produced by
  /// [AppSettings.toJson]). Device-specific storage location fields
  /// (download directory and SAF tree URI) are intentionally preserved from the
  /// current device, because a SAF tree URI from another phone is not valid
  /// here and would break downloads.
  Future<void> restoreFromBackup(Map<String, dynamic> json) async {
    final current = state;
    AppSettings restored;
    try {
      restored = AppSettings.fromJson(Map<String, dynamic>.from(json));
    } catch (e, stack) {
      _log.e('Failed to parse settings from backup: $e', e, stack);
      rethrow;
    }

    state = restored.copyWith(
      // Preserve this device's storage location; the backup's values point at
      // the original device and would not resolve here.
      downloadDirectory: current.downloadDirectory,
      downloadDirectoryBookmark: current.downloadDirectoryBookmark,
      storageMode: current.storageMode,
      downloadTreeUri: current.downloadTreeUri,
    );

    await _saveSettings();
  }

  Future<void> _normalizeIosDownloadDirectoryIfNeeded() async {
    if (!Platform.isIOS) return;

    // A security-scoped bookmark is the source of truth for folders picked
    // from Files: its stored path may legitimately point outside the app
    // container, and the download flow re-resolves the real path from the
    // bookmark. "Fixing" the path here would also drop the bookmark and
    // silently revert the user's chosen folder.
    if (state.downloadDirectoryBookmark.isNotEmpty) return;

    final currentDir = state.downloadDirectory.trim();
    if (currentDir.isEmpty) return;

    final normalizedDir = await validateOrFixIosPath(currentDir);
    if (normalizedDir == currentDir) return;

    _log.i('Normalized iOS download directory: $currentDir -> $normalizedDir');
    state = state.copyWith(
      downloadDirectory: normalizedDir,
      downloadDirectoryBookmark: '',
    );
    await _saveSettings();
  }

  Future<void> _cleanupRetiredSpotifySettings() async {
    try {
      final storedSecret = await _secureStorage.read(
        key: _spotifyClientSecretKey,
      );
      if (storedSecret != null && storedSecret.isNotEmpty) {
        await _secureStorage.delete(key: _spotifyClientSecretKey);
      }
    } catch (e) {
      // The retired secret is optional; active credentials share this vault.
      _log.w(
        'Could not clean up retired credentials; continuing settings load: $e',
      );
    }
  }

  void setAudioQuality(String quality) =>
      _commit(state.copyWith(audioQuality: quality));

  void setDownloadDirectory(String directory, {String? iosBookmark}) => _commit(
    state.copyWith(
      downloadDirectory: directory,
      downloadDirectoryBookmark: iosBookmark ?? '',
    ),
  );

  void setStorageMode(String mode) =>
      _commit(state.copyWith(storageMode: mode == 'saf' ? 'saf' : 'app'));

  /// Atomically leaves SAF and persists a writable app-managed destination.
  ///
  /// Keeping these fields in one update avoids an intermediate saved state
  /// where app-folder mode still points at the SAF display name, or where an
  /// invalid tree URI can switch the mode back to SAF.
  Future<void> useAppFolderStorage(String directory) async {
    await _commit(
      state.copyWith(
        storageMode: 'app',
        downloadDirectory: directory,
        downloadDirectoryBookmark: '',
        downloadTreeUri: '',
      ),
    );
  }

  void setDownloadTreeUri(String uri, {String? displayName}) => _commit(
    state.copyWith(
      downloadTreeUri: uri,
      storageMode: uri.isNotEmpty ? 'saf' : state.storageMode,
      downloadDirectory: displayName ?? state.downloadDirectory,
      downloadDirectoryBookmark: uri.isNotEmpty
          ? ''
          : state.downloadDirectoryBookmark,
    ),
  );

  void setEmbedLyrics(bool enabled) =>
      _commit(state.copyWith(embedLyrics: enabled));

  void setPlaybackNormalization(bool enabled) =>
      _commit(state.copyWith(playbackNormalization: enabled));

  void setPlaybackNormalizationPreamp(double db) => _commit(
    state.copyWith(
      playbackNormalizationPreamp: playbackNormalizationPreampFromJson(db),
    ),
  );

  void setPlaybackNormalizationMode(String mode) => _commit(
    state.copyWith(
      playbackNormalizationMode: playbackNormalizationModeFromJson(mode),
    ),
  );

  void setConfirmLoudVolume(bool enabled) =>
      _commit(state.copyWith(confirmLoudVolume: enabled));

  void setLoudVolumeLevel(int percent) {
    if (!loudVolumeLevels.contains(percent)) return;
    _commit(state.copyWith(loudVolumeLevel: percent));
  }

  void setPauseOnMute(bool enabled) =>
      _commit(state.copyWith(pauseOnMute: enabled));

  void setHeadphoneResume(String mode) {
    if (!headphoneResumeModes.contains(mode)) return;
    _commit(state.copyWith(headphoneResume: mode));
  }

  void setGaplessPlayback(bool enabled) =>
      _commit(state.copyWith(gaplessPlayback: enabled));

  void setAutoMix(bool enabled) => _commit(state.copyWith(autoMix: enabled));

  void setAutoMixDuration(int seconds) => _commit(
    state.copyWith(autoMixDuration: autoMixDurationFromJson(seconds)),
  );

  void setAutoMixEffect(AutoMixEffect effect) => _commit(
    state.copyWith(
      autoMixEffect: effect,
      autoMixPitch: effect == AutoMixEffect.pitch && state.autoMixPitch == 0
          ? 2
          : state.autoMixPitch,
    ),
  );

  void setAutoMixSpeed(double speed) =>
      _commit(state.copyWith(autoMixSpeed: autoMixSpeedFromJson(speed)));

  void setAutoMixPitch(double semitones) =>
      _commit(state.copyWith(autoMixPitch: autoMixPitchFromJson(semitones)));

  void setAutoMixEcho(bool enabled) =>
      _commit(state.copyWith(autoMixEcho: enabled));

  void setAutoMixLowPass(bool enabled) =>
      _commit(state.copyWith(autoMixLowPass: enabled));

  void setPlaybackSpeed(double speed) =>
      _commit(state.copyWith(playbackSpeed: playbackSpeedFromJson(speed)));

  void setPlaybackKeepPitch(bool keep) =>
      _commit(state.copyWith(playbackKeepPitch: keep));

  void setPlaybackPitch(double semitones) =>
      _commit(state.copyWith(playbackPitch: playbackPitchFromJson(semitones)));

  void resetPlaybackTempo() => _commit(
    state.copyWith(playbackSpeed: 1, playbackKeepPitch: true, playbackPitch: 0),
  );

  void setAutoplay(bool enabled) => _commit(state.copyWith(autoplay: enabled));

  void setAutoplayPersonalization(bool enabled) =>
      _commit(state.copyWith(autoplayPersonalization: enabled));

  void setAutoplayRandomness(double value) => _commit(
    state.copyWith(autoplayRandomness: autoplayRandomnessFromJson(value)),
  );

  void setSongAnalysis(bool enabled) =>
      _commit(state.copyWith(songAnalysis: enabled));

  void setSongAnalysisOnlyWhileCharging(bool enabled) =>
      _commit(state.copyWith(songAnalysisOnlyWhileCharging: enabled));

  void setMusicHaptics(bool enabled) =>
      _commit(state.copyWith(musicHaptics: enabled));

  void setMusicHapticsSensitivity(double value) => _commit(
    state.copyWith(musicHapticsSensitivity: musicHapticsLevelFromJson(value)),
  );

  void setMusicHapticsStrength(double value) => _commit(
    state.copyWith(musicHapticsStrength: musicHapticsLevelFromJson(value)),
  );

  void setPlayerCoverStyle(String style) => _commit(
    state.copyWith(playerCoverStyle: playerCoverStyleFromJson(style)),
  );

  void setAutoHideLyricsControls(bool enabled) => _commit(
    state.copyWith(
      autoHideLyricsControls: enabled,
      autoHideLyricsControlsWithAccessibility: enabled,
    ),
  );

  void setUsbBitPerfect(bool enabled) =>
      _commit(state.copyWith(usbBitPerfect: enabled));

  void setUsbDirect(bool enabled) => _commit(
    state.copyWith(usbDirect: enabled, dapExclusive: enabled ? false : null),
  );

  void setUsbDsdOverPcm(bool enabled) =>
      _commit(state.copyWith(usbDsdOverPcm: enabled));

  void setUsbAllowFixedVolume(bool enabled) =>
      _commit(state.copyWith(usbAllowFixedVolume: enabled));

  void setDapExclusive(bool enabled) => _commit(
    state.copyWith(dapExclusive: enabled, usbDirect: enabled ? false : null),
  );

  void setMedia3Engine(bool enabled) =>
      _commit(state.copyWith(media3Engine: enabled));

  void setSkipSilence(bool enabled) =>
      _commit(state.copyWith(skipSilence: enabled));

  void setSpatialAudio(bool enabled) =>
      _commit(state.copyWith(spatialAudio: enabled));

  void setQueueNoDuplicates(bool enabled) =>
      _commit(state.copyWith(queueNoDuplicates: enabled));

  void setQueueLocked(bool locked) =>
      _commit(state.copyWith(queueLocked: locked));

  void setExclusiveOutput(bool enabled) =>
      _commit(state.copyWith(exclusiveOutput: enabled));

  void setExclusiveOutputDevice(String device) =>
      _commit(state.copyWith(exclusiveOutputDevice: device));

  void setKeepScreenOnLyrics(bool enabled) =>
      _commit(state.copyWith(keepScreenOnLyrics: enabled));

  void setMotionArtworkEnabled(bool enabled) =>
      _commit(state.copyWith(motionArtworkEnabled: enabled));

  void setLyricsMode(String mode) {
    if (mode == 'embed' || mode == 'external' || mode == 'both') {
      _commit(state.copyWith(lyricsMode: mode));
    }
  }

  /// Switching provider resets the model to the new provider's default.
  void setLyricsRomanize(bool enabled) =>
      _commit(state.copyWith(lyricsRomanize: enabled));

  void setLyricsRomanizedMainLine(bool enabled) =>
      _commit(state.copyWith(lyricsRomanizedMainLine: enabled));

  Future<void> setFirstLaunchComplete() async {
    // The router leaves setup as soon as this flag changes. Persist the final
    // folder settings first so a restart cannot send the user back to setup.
    await ensureLoaded();
    final prefs = await _prefs;
    while (_saveSettingsFuture != null) {
      await _saveSettingsFuture;
    }
    final saved = Completer<void>();
    _saveSettingsFuture = saved.future;
    try {
      do {
        _saveQueued = false;
        final completed = state.copyWith(isFirstLaunch: false);
        if (!await prefs.setString(
          _settingsKey,
          jsonEncode(completed.toJson()),
        )) {
          throw StateError('Could not save completed setup');
        }
        // Include settings changed during the write before publishing the flag.
        if (!_saveQueued && ref.mounted) state = completed;
      } while (_saveQueued);
    } finally {
      _saveSettingsFuture = null;
      saved.complete();
    }
  }

  void setHistorySortMode(String mode) =>
      _commit(state.copyWith(historySortMode: librarySortModeFromJson(mode)));

  void setLibraryAlbumSortMode(String mode) => _commit(
    state.copyWith(libraryAlbumSortMode: libraryAlbumSortModeFromJson(mode)),
  );

  void setSearchViewMode(String mode) =>
      _commit(state.copyWith(searchViewMode: mode));

  void setHomeShortcuts(List<HomeShortcut> shortcuts) => _commit(
    state.copyWith(homeShortcuts: [for (final s in shortcuts) s.name]),
  );

  void setSwipeToQueue(bool enabled) =>
      _commit(state.copyWith(swipeToQueue: enabled));

  void setForceBackdropBlur(bool enabled) =>
      _commit(state.copyWith(forceBackdropBlur: enabled));

  void setInterfaceScale(double scale) =>
      _commit(state.copyWith(interfaceScale: interfaceScaleFromJson(scale)));

  void setLocale(String locale) => _commit(state.copyWith(locale: locale));

  void setLocalLibraryEnabled(bool enabled) =>
      _commit(state.copyWith(localLibraryEnabled: enabled));

  void setLocalLibraryPathAndBookmark(String path, String bookmark) => _commit(
    state.copyWith(localLibraryPath: path, localLibraryBookmark: bookmark),
  );

  void setLocalLibraryShowDuplicates(bool show) =>
      _commit(state.copyWith(localLibraryShowDuplicates: show));

  void setLocalLibraryAutoScan(String mode) =>
      _commit(state.copyWith(localLibraryAutoScan: mode));

  void setTutorialComplete() =>
      _commit(state.copyWith(hasCompletedTutorial: true));

  void setPlayerShowPronunciation(bool visible) =>
      _commit(state.copyWith(playerShowPronunciation: visible));

  void setPlayerShowTranslation(bool visible) =>
      _commit(state.copyWith(playerShowTranslation: visible));

  void setPlayerShowVolume(bool visible) =>
      _commit(state.copyWith(playerShowVolume: visible));

  void setPlayerShowLyricSnippet(bool visible) =>
      _commit(state.copyWith(playerShowLyricSnippet: visible));
}

final settingsProvider = NotifierProvider<SettingsNotifier, AppSettings>(
  SettingsNotifier.new,
);
