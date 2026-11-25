import 'dart:async';

import 'package:material_ui/material_ui.dart';
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

  test(
    'a glass mode stored with another type loads as if it were absent',
    () async {
      SharedPreferences.setMockInitialValues({});
      final empty = await SharedPreferences.getInstance();
      final fallback = loadBootstrapThemeSettings(empty).mornyeGlassLevel;

      for (final wrongType in <Object>[
        3,
        true,
        1.5,
        ['liquid'],
      ]) {
        SharedPreferences.setMockInitialValues({
          kMornyeGlassModeKey: wrongType,
        });
        final prefs = await SharedPreferences.getInstance();
        expect(
          loadBootstrapThemeSettings(prefs).mornyeGlassLevel,
          fallback,
          reason: 'stored ${wrongType.runtimeType}',
        );
      }
    },
  );

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
        kMornyeAccentKey: 'blue',
        kMornyeGlassModeKey: 'liquid',
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
      final savingLevel = notifier.setMornyeGlassLevel(MornyeGlassLevel.liquid);
      var saved = false;
      unawaited(savingAccent.then((_) => saved = true));
      await Future<void>.delayed(Duration.zero);
      expect(saved, isFalse);
      expect(
        container.read(themeProvider).mornyeGlassLevel,
        MornyeGlassLevel.liquid,
      );
      expect(prefs.getString(kThemeModeKey), 'dark');

      releaseLoad.complete(prefs);
      await Future.wait([savingMode, savingAccent, savingLevel]);
      final state = container.read(themeProvider);
      expect(state.themeMode, ThemeMode.system);
      expect(state.mornyeAccent, MornyeAccent.red);
      expect(state.mornyeGlassLevel, MornyeGlassLevel.liquid);
      expect(loadBootstrapThemeSettings(prefs), state);
    },
  );

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
          .setMornyeGlassOutlines(false);
      releaseLoad.completeError(StateError('theme_load_failed'));
      await saving;
      expect(container.read(themeProvider).mornyeGlassOutlines, isFalse);
    },
  );

  test(
    'a glass mode write is ignored while a comparison renders in memory',
    () async {
      SharedPreferences.setMockInitialValues({kMornyeGlassModeKey: 'liquid'});
      final prefs = await SharedPreferences.getInstance();
      final container = ProviderContainer(
        overrides: [
          themeProvider.overrideWith(
            () => ThemeNotifier(preferences: Future.value(prefs)),
          ),
        ],
      );
      addTearDown(container.dispose);
      // Reading builds the notifier, which starts loading the saved mode.
      container.read(themeProvider);
      await pumpEventQueue();
      final compare = container.read(mornyeGlassCompareModeProvider.notifier);

      compare.setMode(MornyeGlassLevel.liquid);
      await container
          .read(themeProvider.notifier)
          .setMornyeGlassLevel(MornyeGlassLevel.flat);
      expect(prefs.getString(kMornyeGlassModeKey), 'liquid');
      expect(
        container.read(themeProvider).mornyeGlassLevel,
        MornyeGlassLevel.liquid,
      );

      compare.setMode(null);
      await container
          .read(themeProvider.notifier)
          .setMornyeGlassLevel(MornyeGlassLevel.flat);
      expect(prefs.getString(kMornyeGlassModeKey), 'flat');
    },
  );

  test(
    'disposed theme does not read state or save after delayed loading',
    () async {
      SharedPreferences.setMockInitialValues({kThemeModeKey: 'system'});
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
          .setThemeMode(ThemeMode.dark);
      container.dispose();
      releaseLoad.complete(prefs);
      await saving;
      expect(prefs.getString(kThemeModeKey), 'system');
    },
  );
}
