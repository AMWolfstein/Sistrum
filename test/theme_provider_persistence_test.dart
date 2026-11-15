import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/theme_provider.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('a stored red moves to blue once, and red picked later stays', () async {
    SharedPreferences.setMockInitialValues({kMornyeAccentKey: 'red'});
    final prefs = await SharedPreferences.getInstance();
    expect(loadBootstrapThemeSettings(prefs).mornyeAccent, MornyeAccent.blue);
    expect(prefs.getString(kMornyeAccentKey), 'blue');

    await prefs.setString(kMornyeAccentKey, 'red');
    expect(loadBootstrapThemeSettings(prefs).mornyeAccent, MornyeAccent.red);
    expect(prefs.getString(kMornyeAccentKey), 'red');
  });

  test('the move leaves any other accent alone', () async {
    SharedPreferences.setMockInitialValues({kMornyeAccentKey: 'pink'});
    final prefs = await SharedPreferences.getInstance();
    expect(loadBootstrapThemeSettings(prefs).mornyeAccent, MornyeAccent.pink);
    expect(prefs.getString(kMornyeAccentKey), 'pink');
    expect(prefs.getBool(kMornyeAccentMovedToBlueKey), isTrue);
  });

  test(
    'early theme edits preserve untouched saved settings and await saving',
    () async {
      SharedPreferences.setMockInitialValues({
        kThemeModeKey: 'dark',
        kUseSystemFontKey: true,
        kMornyeAccentKey: 'blue',
        kLyricsTextScaleKey: 0.9,
      });
      final prefs = await SharedPreferences.getInstance();
      final releaseLoad = Completer<SharedPreferences>();
      final container = ProviderContainer(
        overrides: [
          themeProvider.overrideWith(
            () => ThemeNotifier(preferences: releaseLoad.future),
          ),
        ],
      );
      addTearDown(container.dispose);
      final notifier = container.read(themeProvider.notifier);
      // An explicit edit equal to the bootstrap default still owns this field.
      final savingMode = notifier.setThemeMode(ThemeMode.system);
      final savingAccent = notifier.setMornyeAccent(MornyeAccent.red);
      await notifier.setLyricsTextScale(1.2, persist: false);
      var saved = false;
      unawaited(savingAccent.then((_) => saved = true));
      await Future<void>.delayed(Duration.zero);
      expect(saved, isFalse);
      expect(container.read(themeProvider).lyricsTextScale, 1.2);
      expect(prefs.getString(kThemeModeKey), 'dark');

      releaseLoad.complete(prefs);
      await Future.wait([savingMode, savingAccent]);
      final state = container.read(themeProvider);
      expect(state.themeMode, ThemeMode.system);
      expect(state.mornyeAccent, MornyeAccent.red);
      expect(state.lyricsTextScale, 1.2);
      expect(state.useSystemFont, isTrue);
      expect(loadBootstrapThemeSettings(prefs), state);
    },
  );

  test('early preview remains immediate without writing preferences', () async {
    SharedPreferences.setMockInitialValues({
      kLyricsTextScaleKey: 0.9,
      kMornyeAccentKey: 'blue',
    });
    final prefs = await SharedPreferences.getInstance();
    final releaseLoad = Completer<SharedPreferences>();
    final loaded = Completer<void>();
    final container = ProviderContainer(
      overrides: [
        themeProvider.overrideWith(
          () => ThemeNotifier(preferences: releaseLoad.future),
        ),
      ],
    );
    addTearDown(container.dispose);
    container.listen(themeProvider, (_, next) {
      if (next.mornyeAccent == MornyeAccent.blue) loaded.complete();
    });
    await container
        .read(themeProvider.notifier)
        .setLyricsTextScale(1.2, persist: false);
    expect(container.read(themeProvider).lyricsTextScale, 1.2);
    releaseLoad.complete(prefs);
    await loaded.future;
    expect(container.read(themeProvider).lyricsTextScale, 1.2);
    expect(prefs.getDouble(kLyricsTextScaleKey), 0.9);
  });

  test(
    'failed preference loading keeps early edits without leaking errors',
    () async {
      final releaseLoad = Completer<SharedPreferences>();
      final container = ProviderContainer(
        overrides: [
          themeProvider.overrideWith(
            () => ThemeNotifier(preferences: releaseLoad.future),
          ),
        ],
      );
      addTearDown(container.dispose);
      final saving = container
          .read(themeProvider.notifier)
          .setUseSystemFont(true);
      releaseLoad.completeError(StateError('theme_load_failed'));
      await saving;
      expect(container.read(themeProvider).useSystemFont, isTrue);
    },
  );

  test(
    'disposed theme does not read state or save after delayed loading',
    () async {
      SharedPreferences.setMockInitialValues({kUseSystemFontKey: false});
      final prefs = await SharedPreferences.getInstance();
      final releaseLoad = Completer<SharedPreferences>();
      final container = ProviderContainer(
        overrides: [
          themeProvider.overrideWith(
            () => ThemeNotifier(preferences: releaseLoad.future),
          ),
        ],
      );
      final saving = container
          .read(themeProvider.notifier)
          .setUseSystemFont(true);
      container.dispose();
      releaseLoad.complete(prefs);
      await saving;
      expect(prefs.getBool(kUseSystemFontKey), isFalse);
    },
  );

  test(
    'the retired glass clarity preference is removed once at startup',
    () async {
      SharedPreferences.setMockInitialValues({'mornye_glass_clarity': 0.4});
      final prefs = await SharedPreferences.getInstance();
      loadBootstrapThemeSettings(prefs);
      await Future<void>.delayed(Duration.zero);
      expect(prefs.containsKey('mornye_glass_clarity'), isFalse);
    },
  );

  test(
    'the retired Material keys are removed once and the rest is kept',
    () async {
      SharedPreferences.setMockInitialValues({
        'theme_style': 'material',
        'use_dynamic_color': false,
        'seed_color': 0xff123456,
        'use_amoled': true,
        kThemeModeKey: 'dark',
        kMornyeAccentKey: 'pink',
        kUseSystemFontKey: true,
        kMornyeGlassOutlinesKey: false,
        kLyricsTextScaleKey: 1.2,
      });
      final prefs = await SharedPreferences.getInstance();
      final settings = loadBootstrapThemeSettings(prefs);
      await Future<void>.delayed(Duration.zero);
      for (final key in [
        'theme_style',
        'use_dynamic_color',
        'seed_color',
        'use_amoled',
      ]) {
        expect(prefs.containsKey(key), isFalse, reason: key);
      }
      expect(settings.themeMode, ThemeMode.dark);
      expect(settings.mornyeAccent, MornyeAccent.pink);
      expect(settings.useSystemFont, isTrue);
      expect(settings.mornyeGlassOutlines, isFalse);
      expect(settings.lyricsTextScale, 1.2);
      expect(loadBootstrapThemeSettings(prefs), settings);
    },
  );

  test(
    'retired keys with unexpected types are removed without failing',
    () async {
      SharedPreferences.setMockInitialValues({
        'theme_style': 3,
        'use_dynamic_color': 'yes',
        'seed_color': 'green',
        'use_amoled': 1.5,
      });
      final prefs = await SharedPreferences.getInstance();
      expect(loadBootstrapThemeSettings(prefs), const ThemeSettings());
      await Future<void>.delayed(Duration.zero);
      expect(prefs.getKeys().where((k) => k.startsWith('use_')), isEmpty);
      expect(prefs.containsKey('theme_style'), isFalse);
      expect(prefs.containsKey('seed_color'), isFalse);
    },
  );
}
