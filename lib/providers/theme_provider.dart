import 'dart:async';
import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/theme_settings.dart';

final initialThemeSettingsProvider = Provider<ThemeSettings>(
  (ref) => const ThemeSettings(),
);

/// The accent to load, moving an install off the old default once. Every
/// theme save wrote the accent, so a stored red usually only means red was
/// the default. Red picked after this stays.
MornyeAccent _loadMornyeAccent(SharedPreferences prefs) {
  final stored = prefs.getString(kMornyeAccentKey);
  if (prefs.getBool(kMornyeAccentMovedToBlueKey) ?? false) {
    return mornyeAccentFromString(stored);
  }
  unawaited(prefs.setBool(kMornyeAccentMovedToBlueKey, true));
  if (stored == MornyeAccent.red.name) {
    unawaited(prefs.setString(kMornyeAccentKey, MornyeAccent.blue.name));
    return MornyeAccent.blue;
  }
  return mornyeAccentFromString(stored);
}

/// Deletes the font files the removed custom font setting imported. Missing
/// files are fine, and a failure must not block the preference cleanup.
Future<void> _removeRetiredFontFiles() async {
  try {
    final support = await getApplicationSupportDirectory();
    await Directory('${support.path}/custom_fonts').delete(recursive: true);
  } catch (_) {
    // Nothing was imported, or this platform has no support directory.
  }
}

/// Removes the retired font preferences and imported font files once. It has
/// its own marker, so installs whose theme marker was already written still
/// get it.
Future<void> _removeRetiredFontPrefs(SharedPreferences prefs) async {
  try {
    if (prefs.get(kRetiredFontPrefsRemovedKey) == true) return;
    final keys = [
      kRetiredUseSystemFontKey,
      kRetiredCustomFontIdKey,
      kRetiredCustomFontNameKey,
    ];
    // Font files exist only after a custom font was imported, which stored a key.
    final hadFont = keys.any(prefs.containsKey);
    for (final key in keys) {
      await prefs.remove(key);
    }
    if (hadFont) await _removeRetiredFontFiles();
    await prefs.setBool(kRetiredFontPrefsRemovedKey, true);
  } catch (e) {
    // Leaves the marker unset, so the next start retries.
    debugPrint('Error removing retired font settings: $e');
  }
}

/// Removes the retired preferences once. The marker is written last, so an
/// interrupted run repeats the removal. Reads through `get`, so a stored value
/// of any type cannot break the load.
Future<void> _removeRetiredThemePrefs(SharedPreferences prefs) async {
  try {
    if (prefs.get(kRetiredThemePrefsRemovedKey) == true) return;
    for (final key in [
      kRetiredUseDynamicColorKey,
      kRetiredSeedColorKey,
      kRetiredUseAmoledKey,
      kRetiredThemeStyleKey,
    ]) {
      await prefs.remove(key);
    }
    await prefs.setBool(kRetiredThemePrefsRemovedKey, true);
  } catch (e) {
    // Leaves the marker unset, so the next start retries.
    debugPrint('Error removing retired theme settings: $e');
  }
}

/// The stored mode. Until the one-time migration writes it, the mode the
/// retired clarity implied, so both paths agree. A value of another type is
/// ignored, because this runs before the first frame and must not throw.
MornyeGlassLevel _loadMornyeGlassLevel(SharedPreferences prefs) {
  final stored = prefs.get(kMornyeGlassModeKey);
  if (stored is String) return mornyeGlassLevelFromString(stored);
  final clarity = prefs.get(kLegacyMornyeGlassClarityKey);
  return mornyeGlassLevelFromLegacyClarity(clarity is num ? clarity : null);
}

/// Writes the mode once from the retired clarity and force-blur values, then
/// removes the clarity key. The marker is written last, so an interrupted run
/// repeats it, and a repeat writes the same mode.
Future<void> _migrateMornyeGlassMode(SharedPreferences prefs) async {
  try {
    if (prefs.get(kMornyeGlassModeMigratedKey) == true) return;
    await prefs.setString(
      kMornyeGlassModeKey,
      _loadMornyeGlassLevel(prefs).name,
    );
    await prefs.remove(kLegacyMornyeGlassClarityKey);
    await prefs.setBool(kMornyeGlassModeMigratedKey, true);
  } catch (e) {
    // Leaves the marker unset, so the next start retries.
    debugPrint('Error migrating the glass mode: $e');
  }
}

ThemeSettings loadBootstrapThemeSettings(SharedPreferences prefs) {
  return ThemeSettings(
    themeMode: themeModeFromString(prefs.getString(kThemeModeKey)),
    mornyeAccent: _loadMornyeAccent(prefs),
    lyricsTextScale: normalizeLyricsTextScale(
      prefs.getDouble(kLyricsTextScaleKey),
    ),
    lyricsBlur: normalizeLyricsBlur(prefs.getDouble(kLyricsBlurKey)),
    lyricsOffsetMs: normalizeLyricsOffsetMs(prefs.getInt(kLyricsOffsetKey)),
    mornyeGlassLevel: _loadMornyeGlassLevel(prefs),
    mornyeGlassOutlines: prefs.getBool(kMornyeGlassOutlinesKey) ?? true,
  );
}

final themeProvider = NotifierProvider<ThemeNotifier, ThemeSettings>(() {
  return ThemeNotifier();
});

/// The mode a Performance hub comparison renders. It lives in memory only: it
/// replaces the selected mode for rendering while set, so a crash mid-compare
/// cannot leave a changed saved choice. Null when no comparison runs.
final mornyeGlassCompareModeProvider =
    NotifierProvider<MornyeGlassCompareModeNotifier, MornyeGlassLevel?>(
      MornyeGlassCompareModeNotifier.new,
    );

class MornyeGlassCompareModeNotifier extends Notifier<MornyeGlassLevel?> {
  @override
  MornyeGlassLevel? build() => null;

  void setMode(MornyeGlassLevel? mode) => state = mode;
}

class ThemeNotifier extends Notifier<ThemeSettings> {
  ThemeNotifier({Future<SharedPreferences>? preferences})
    : _prefs = preferences ?? SharedPreferences.getInstance();

  final Future<SharedPreferences> _prefs;
  late Future<void> _loadFuture;
  Future<void> _saveChain = Future<void>.value();
  final _loadingEdits = <ThemeSettings Function(ThemeSettings)>[];
  bool _loaded = false;

  @override
  ThemeSettings build() {
    _loadFuture = _loadFromStorage();
    return ref.read(initialThemeSettingsProvider);
  }

  Future<void> _loadFromStorage() async {
    try {
      final prefs = await _prefs;
      await _removeRetiredThemePrefs(prefs);
      await _removeRetiredFontPrefs(prefs);
      await _migrateMornyeGlassMode(prefs);
      var loaded = loadBootstrapThemeSettings(prefs);
      for (final edit in _loadingEdits) {
        loaded = edit(loaded);
      }
      if (ref.mounted) state = loaded;
    } catch (e) {
      debugPrint('Error loading theme settings: $e');
    } finally {
      _loaded = true;
      _loadingEdits.clear();
    }
  }

  void _editState(ThemeSettings Function(ThemeSettings) edit) {
    if (!_loaded) _loadingEdits.add(edit);
    state = edit(state);
  }

  Future<void> _saveToStorage() => _saveChain = _saveChain.then((_) async {
    try {
      await _loadFuture;
      final prefs = await _prefs;
      if (!ref.mounted) return;
      final snapshot = state;
      await prefs.setString(kThemeModeKey, snapshot.themeMode.name);
      await prefs.setString(kMornyeAccentKey, snapshot.mornyeAccent.name);
      await prefs.setDouble(kLyricsTextScaleKey, snapshot.lyricsTextScale);
      await prefs.setDouble(kLyricsBlurKey, snapshot.lyricsBlur);
      await prefs.setInt(kLyricsOffsetKey, snapshot.lyricsOffsetMs);
      await prefs.setString(
        kMornyeGlassModeKey,
        snapshot.mornyeGlassLevel.name,
      );
      await prefs.setBool(
        kMornyeGlassOutlinesKey,
        snapshot.mornyeGlassOutlines,
      );
    } catch (e) {
      debugPrint('Error saving theme settings: $e');
    }
  });

  Future<void> setThemeMode(ThemeMode mode) async {
    _editState((current) => current.copyWith(themeMode: mode));
    await _saveToStorage();
  }

  Future<void> setMornyeAccent(MornyeAccent accent) async {
    _editState((current) => current.copyWith(mornyeAccent: accent));
    await _saveToStorage();
  }

  Future<void> setLyricsTextScale(double value, {bool persist = true}) async {
    _editState((current) => current.copyWith(lyricsTextScale: value));
    if (persist) await _saveToStorage();
  }

  Future<void> setLyricsBlur(double value, {bool persist = true}) async {
    _editState((current) => current.copyWith(lyricsBlur: value));
    if (persist) await _saveToStorage();
  }

  Future<void> setLyricsOffsetMs(int value, {bool persist = true}) async {
    _editState((current) => current.copyWith(lyricsOffsetMs: value));
    if (persist) await _saveToStorage();
  }

  Future<void> setMornyeGlassLevel(MornyeGlassLevel value) async {
    // A comparison owns the rendered mode until it ends, so no choice is saved.
    if (ref.read(mornyeGlassCompareModeProvider) != null) return;
    _editState((current) => current.copyWith(mornyeGlassLevel: value));
    await _saveToStorage();
  }

  Future<void> setMornyeGlassOutlines(bool value) async {
    _editState((current) => current.copyWith(mornyeGlassOutlines: value));
    await _saveToStorage();
  }
}
