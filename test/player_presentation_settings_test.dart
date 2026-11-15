import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/playback_settings_page.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('cover style and music haptics keep their defaults', () {
    final restored = AppSettings.fromJson(const {});
    // Unchosen, the player shows a card.
    expect(restored.playerCoverStyle, isEmpty);
    expect(restored.playerCoverStyleFor(), 'default');
    // A choice is kept.
    final card = restored.copyWith(playerCoverStyle: 'default');
    expect(card.playerCoverStyleFor(), 'default');
    final full = restored.copyWith(playerCoverStyle: 'fullscreen');
    expect(full.playerCoverStyleFor(), 'fullscreen');
    expect(restored.musicHaptics, isFalse);
    expect(restored.autoHideLyricsControls, isTrue);
  });

  test('cover style and music haptics survive a round trip', () {
    final settings = const AppSettings().copyWith(
      playerCoverStyle: 'default',
      musicHaptics: true,
      autoHideLyricsControls: false,
    );
    final restored = AppSettings.fromJson(settings.toJson());
    expect(restored.playerCoverStyle, 'default');
    expect(restored.musicHaptics, isTrue);
    expect(restored.autoHideLyricsControls, isFalse);
    expect(
      restored.copyWith(autoHideLyricsControls: true).autoHideLyricsControls,
      isTrue,
    );
  });

  test('invalid cover styles preserve the rest of the saved settings', () {
    for (final value in [null, '', 'unknown', 42, <String, String>{}]) {
      final restored = AppSettings.fromJson({
        'playerCoverStyle': value,
        'musicHaptics': true,
      });
      expect(restored.playerCoverStyle, isEmpty);
      expect(restored.musicHaptics, isTrue);
    }
  });

  for (final mornye in const [true]) {
    testWidgets(
      'automatic fullscreen lyrics is saved and searchable (Mornye: $mornye)',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final container = ProviderContainer(
          overrides: [
            initialSettingsProvider.overrideWithValue(const AppSettings()),
          ],
        );
        addTearDown(container.dispose);
        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: MaterialApp(
              theme: mornye
                  ? MornyeTheme.build(Brightness.dark)
                  : AppTheme.light(),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: const PlaybackSettingsPage(),
            ),
          ),
        );
        final option = find.byKey(const ValueKey('auto-hide-lyrics-controls'));
        await tester.scrollUntilVisible(
          option,
          200,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();
        expect(container.read(settingsProvider).autoHideLyricsControls, isTrue);
        await tester.tap(option);
        await tester.pumpAndSettle();
        expect(
          container.read(settingsProvider).autoHideLyricsControls,
          isFalse,
        );
        final prefs = await SharedPreferences.getInstance();
        final restored = AppSettings.fromJson(
          jsonDecode(prefs.getString('app_settings')!) as Map<String, dynamic>,
        );
        expect(restored.autoHideLyricsControls, isFalse);
        final l10n = AppLocalizations.of(tester.element(option));
        expect(
          SettingsSearchCatalog(l10n).playback.where(
            (entry) =>
                entry.title == l10n.autoHideLyricsControls &&
                entry.matches(SettingsSearchQuery('fullscreen lyrics')),
          ),
          hasLength(1),
        );
        expect(tester.takeException(), isNull);
      },
    );
  }
}
