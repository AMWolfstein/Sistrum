import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/utils/artist_utils.dart';

void main() {
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
      expect(restored.copyWith().historySortMode, sort);
    }
  });

  test(
    'settings saved before the Home feed and search tab were removed load',
    () {
      final restored = AppSettings.fromJson({
        'homeFeedProvider': '__off__',
        'defaultSearchTab': 'artist',
        'hasSearchedBefore': true,
        'historySortMode': 'oldest',
      });
      expect(restored.historySortMode, 'oldest');
      final saved = restored.toJson();
      for (final key in [
        'homeFeedProvider',
        'defaultSearchTab',
        'hasSearchedBefore',
      ]) {
        expect(saved.containsKey(key), isFalse, reason: key);
      }
    },
  );

  test('retired Material-only settings in saved JSON are ignored', () {
    final restored = AppSettings.fromJson({
      'landscapeLyricsArtwork': true,
      'compactPlayer': true,
      'historyViewMode': 'list',
      'historyFilterMode': 'albums',
      'defaultLibraryView': 'folders',
      'historySortMode': 'oldest',
    });
    expect(restored.historySortMode, 'oldest');
    final saved = restored.toJson();
    for (final key in [
      'landscapeLyricsArtwork',
      'compactPlayer',
      'historyViewMode',
      'historyFilterMode',
      'defaultLibraryView',
    ]) {
      expect(saved.containsKey(key), isFalse, reason: key);
    }
  });

  test('retired quality conversion settings are dropped from saved JSON', () {
    for (final format in ['mp3_320', 'aac_320', 'opus_256', 'opus_128']) {
      final settings = AppSettings.fromJson({'tidalHighFormat': format});
      expect(settings.toJson().containsKey('tidalHighFormat'), isFalse);
    }
  });

  group('AppSettings', () {
    test('provides stable defaults', () {
      const settings = AppSettings();

      expect(settings.artistTagMode, artistTagModeJoined);
      expect(settings.autoFallback, isTrue);
      expect(settings.lyricsProviders, [
        'apple_music',
        'lrcred',
        'lyricsplus',
        'lrclib',
      ]);
      expect(settings.playerMode, 'internal');
      expect(
        settings.libraryQualityLabelMode,
        AppSettings.libraryQualityLabelBitDepthOnly,
      );
    });

    test('copyWith updates values and can clear nullable provider fields', () {
      const settings = AppSettings(
        downloadFallbackExtensionIds: ['fallback.ext'],
        searchProvider: 'search.ext',
      );

      final updated = settings.copyWith(
        lyricsProviders: ['apple_music'],
        libraryQualityLabelMode: AppSettings.libraryQualityLabelBitDepthOnly,
        clearDownloadFallbackExtensionIds: true,
        clearSearchProvider: true,
      );

      expect(updated.lyricsProviders, ['apple_music']);
      expect(
        updated.libraryQualityLabelMode,
        AppSettings.libraryQualityLabelBitDepthOnly,
      );
      expect(updated.downloadFallbackExtensionIds, isNull);
      expect(updated.searchProvider, isNull);
    });

    test('round-trips json including recently added settings', () {
      const settings = AppSettings(
        storageMode: 'saf',
        downloadTreeUri: 'content://tree/music',
        downloadFallbackExtensionIds: ['ext.a', 'ext.b'],
        searchProvider: 'search.ext',
        useAllFilesAccess: true,
        networkCompatibilityMode: true,
        songLinkRegion: 'ID',
        localLibraryEnabled: true,
        localLibraryPath: '/music',
        hasCompletedTutorial: true,
        libraryQualityLabelMode: AppSettings.libraryQualityLabelBitDepthOnly,
      );

      final decoded = AppSettings.fromJson(settings.toJson());

      expect(decoded.storageMode, 'saf');
      expect(decoded.downloadTreeUri, 'content://tree/music');
      expect(decoded.downloadFallbackExtensionIds, ['ext.a', 'ext.b']);
      expect(decoded.searchProvider, 'search.ext');
      expect(decoded.useAllFilesAccess, isTrue);
      expect(decoded.networkCompatibilityMode, isTrue);
      expect(decoded.songLinkRegion, 'ID');
      expect(decoded.localLibraryEnabled, isTrue);
      expect(decoded.localLibraryPath, '/music');
      expect(decoded.hasCompletedTutorial, isTrue);
      expect(
        decoded.libraryQualityLabelMode,
        AppSettings.libraryQualityLabelBitDepthOnly,
      );
    });
  });

  group('ThemeSettings', () {
    test('serializes, deserializes, copies, and compares values', () {
      const settings = ThemeSettings(
        themeMode: ThemeMode.dark,
        mornyeAccent: MornyeAccent.blue,
        useSystemFont: true,
      );

      final decoded = ThemeSettings.fromJson(settings.toJson());
      final copied = decoded.copyWith(themeMode: ThemeMode.light);

      expect(decoded, settings);
      expect(decoded.hashCode, settings.hashCode);
      expect(copied.themeMode, ThemeMode.light);
      expect(decoded.mornyeAccent, MornyeAccent.blue);
      expect(copied.mornyeAccent, MornyeAccent.blue);
      expect(decoded.useSystemFont, isTrue);
      expect(copied.useSystemFont, isTrue);
      expect(ThemeSettings.fromJson({}).useSystemFont, isFalse);
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
