import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';

void main() {
  test('Autoplay randomness defaults to least recent and restores safely', () {
    expect(const AppSettings().autoplayRandomness, 0);
    expect(AppSettings.fromJson({}).autoplayRandomness, 0);
    for (final value in [0.0, 0.5, 1.0]) {
      final settings = const AppSettings().copyWith(autoplayRandomness: value);
      final restored = AppSettings.fromJson(settings.toJson());
      expect(restored.autoplayRandomness, value);
      expect(restored.copyWith(locale: 'id').autoplayRandomness, value);
    }
    for (final invalid in [null, '', double.nan, double.infinity]) {
      expect(
        AppSettings.fromJson({
          'autoplayRandomness': invalid,
        }).autoplayRandomness,
        0,
      );
    }
    expect(
      AppSettings.fromJson({'autoplayRandomness': -5}).autoplayRandomness,
      0,
    );
    expect(
      AppSettings.fromJson({'autoplayRandomness': 5}).autoplayRandomness,
      1,
    );
  });

  test('Interface size defaults to normal and restores within range', () {
    expect(const AppSettings().interfaceScale, 1);
    expect(AppSettings.fromJson({}).interfaceScale, 1);
    for (final value in [0.8, 0.9, 1.25, 1.3]) {
      final settings = const AppSettings().copyWith(interfaceScale: value);
      final restored = AppSettings.fromJson(settings.toJson());
      expect(restored.interfaceScale, value);
      expect(restored.copyWith(locale: 'id').interfaceScale, value);
    }
    for (final invalid in ['', double.nan, double.infinity]) {
      expect(
        AppSettings.fromJson({'interfaceScale': invalid}).interfaceScale,
        1,
      );
    }
    expect(AppSettings.fromJson({'interfaceScale': 0.2}).interfaceScale, 0.8);
    expect(AppSettings.fromJson({'interfaceScale': 3}).interfaceScale, 1.3);
  });

  test('Playback speed and pitch default to normal and restore in range', () {
    const defaults = AppSettings();
    expect(defaults.playbackSpeed, 1);
    expect(defaults.playbackKeepPitch, isTrue);
    expect(defaults.playbackPitch, 0);
    final empty = AppSettings.fromJson({});
    expect(empty.playbackSpeed, 1);
    expect(empty.playbackKeepPitch, isTrue);
    expect(empty.playbackPitch, 0);

    final changed = defaults.copyWith(
      playbackSpeed: 1.25,
      playbackKeepPitch: false,
      playbackPitch: -3,
    );
    final restored = AppSettings.fromJson(changed.toJson());
    expect(restored.playbackSpeed, 1.25);
    expect(restored.playbackKeepPitch, isFalse);
    expect(restored.playbackPitch, -3);
    // Half semitones from earlier builds restore as whole ones.
    expect(AppSettings.fromJson({'playbackPitch': 2.5}).playbackPitch, 3);
    expect(AppSettings.fromJson({'playbackPitch': -1.4}).playbackPitch, -1);
    expect(restored.copyWith(locale: 'id').playbackSpeed, 1.25);

    for (final invalid in ['', double.nan, double.infinity]) {
      final settings = AppSettings.fromJson({
        'playbackSpeed': invalid,
        'playbackPitch': invalid,
      });
      expect(settings.playbackSpeed, 1);
      expect(settings.playbackPitch, 0);
    }
    final clamped = AppSettings.fromJson({
      'playbackSpeed': 9,
      'playbackPitch': -40,
    });
    expect(clamped.playbackSpeed, maxPlaybackSpeed);
    expect(clamped.playbackPitch, -maxPlaybackPitch);
  });

  test('Swipe to queue is on by default and remembers off', () {
    expect(const AppSettings().swipeToQueue, isTrue);
    expect(AppSettings.fromJson({}).swipeToQueue, isTrue);
    final off = const AppSettings().copyWith(swipeToQueue: false);
    expect(AppSettings.fromJson(off.toJson()).swipeToQueue, isFalse);
    expect(off.copyWith(locale: 'id').swipeToQueue, isFalse);
  });

  test('Library sort restores every choice and tolerates old settings', () {
    expect(AppSettings.fromJson({}).historySortMode, 'latest');
    for (final invalid in [null, '', 'unsupported', 42]) {
      expect(
        AppSettings.fromJson({'historySortMode': invalid}).historySortMode,
        'latest',
      );
    }
    for (final sort in librarySortModes) {
      final settings = const AppSettings().copyWith(historySortMode: sort);
      final restored = AppSettings.fromJson(settings.toJson());
      expect(restored.historySortMode, sort);
    }
  });

  test('album sort restores either year order and tolerates old settings', () {
    expect(AppSettings.fromJson({}).libraryAlbumSortMode, 'a-z');
    for (final invalid in [null, '', 'unsupported', 42]) {
      expect(
        AppSettings.fromJson({
          'libraryAlbumSortMode': invalid,
        }).libraryAlbumSortMode,
        'a-z',
      );
    }
    for (final mode in ['year-asc', 'year-desc']) {
      final settings = const AppSettings().copyWith(libraryAlbumSortMode: mode);
      expect(
        AppSettings.fromJson(settings.toJson()).libraryAlbumSortMode,
        mode,
      );
    }
  });

  group('AppSettings', () {
    test('provides stable defaults', () {
      const settings = AppSettings();

      expect(settings.audioQuality, 'LOSSLESS');
    });

    test('copyWith updates values', () {
      const settings = AppSettings();

      final updated = settings.copyWith(locale: 'id');

      expect(updated.locale, 'id');
      expect(updated.audioQuality, settings.audioQuality);
    });

    test('round-trips json including recently added settings', () {
      const settings = AppSettings(
        storageMode: 'saf',
        downloadTreeUri: 'content://tree/music',
        localLibraryEnabled: true,
        localLibraryPath: '/music',
        hasCompletedTutorial: true,
      );

      final decoded = AppSettings.fromJson(settings.toJson());

      expect(decoded.storageMode, 'saf');
      expect(decoded.downloadTreeUri, 'content://tree/music');
      expect(decoded.localLibraryEnabled, isTrue);
      expect(decoded.localLibraryPath, '/music');
      expect(decoded.hasCompletedTutorial, isTrue);
    });
  });

  group('ThemeSettings', () {
    test('serializes, deserializes, copies, and compares values', () {
      const settings = ThemeSettings(
        themeMode: ThemeMode.dark,
        mornyeAccent: MornyeAccent.blue,
      );

      final decoded = ThemeSettings.fromJson(settings.toJson());
      final copied = decoded.copyWith(themeMode: ThemeMode.light);

      expect(decoded, settings);
      expect(decoded.hashCode, settings.hashCode);
      expect(copied.themeMode, ThemeMode.light);
      expect(decoded.mornyeAccent, MornyeAccent.blue);
      expect(copied.mornyeAccent, MornyeAccent.blue);
      expect(ThemeSettings.fromJson({}).mornyeAccent, MornyeAccent.blue);
      expect(
        ThemeSettings.fromJson({kMornyeAccentKey: 'unknown'}).mornyeAccent,
        MornyeAccent.blue,
      );
      expect(
        ThemeSettings.fromJson({kMornyeAccentKey: 'red'}).mornyeAccent,
        MornyeAccent.red,
        reason: 'a saved choice stays',
      );
      expect(
        ThemeSettings.fromJson({'theme_mode': 'invalid'}).themeMode,
        ThemeMode.system,
      );
    });
  });
}
