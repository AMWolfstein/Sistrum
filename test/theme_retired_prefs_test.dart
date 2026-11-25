import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/theme_provider.dart';

Future<ProviderContainer> _loadTheme(SharedPreferences prefs) async {
  final container = ProviderContainer(
    overrides: [
      themeProvider.overrideWith(
        () => ThemeNotifier(preferences: Future.value(prefs)),
      ),
    ],
  );
  addTearDown(container.dispose);
  // Saving waits for the load to finish, so this returns once it has run.
  await container.read(themeProvider.notifier).setThemeMode(ThemeMode.system);
  return container;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('retired style, color and AMOLED keys are removed once', () async {
    SharedPreferences.setMockInitialValues({
      kRetiredThemeStyleKey: 'mornye',
      kRetiredSeedColorKey: 0xff123456,
      kRetiredUseDynamicColorKey: false,
      kRetiredUseAmoledKey: true,
      kThemeModeKey: 'dark',
      kMornyeAccentKey: 'pink',
      kRetiredUseSystemFontKey: true,
      kMornyeGlassClarityKey: 0.3,
    });
    final prefs = await SharedPreferences.getInstance();
    final container = await _loadTheme(prefs);

    for (final key in [
      kRetiredThemeStyleKey,
      kRetiredSeedColorKey,
      kRetiredUseDynamicColorKey,
      kRetiredUseAmoledKey,
      kRetiredUseSystemFontKey,
    ]) {
      expect(prefs.containsKey(key), isFalse, reason: key);
    }
    expect(prefs.getBool(kRetiredThemePrefsRemovedKey), isTrue);
    final state = container.read(themeProvider);
    expect(state.mornyeAccent, MornyeAccent.pink);
    expect(state.mornyeGlassClarity, closeTo(0.3, 1e-9));
    expect(prefs.getString(kThemeModeKey), 'system');
  });

  test('a retired key written after the removal is left alone', () async {
    SharedPreferences.setMockInitialValues({kRetiredThemeStyleKey: 'mornye'});
    final prefs = await SharedPreferences.getInstance();
    await _loadTheme(prefs);
    expect(prefs.containsKey(kRetiredThemeStyleKey), isFalse);

    await prefs.setString(kRetiredThemeStyleKey, 'material');
    await _loadTheme(prefs);
    expect(prefs.getString(kRetiredThemeStyleKey), 'material');
  });

  test('retired keys of unexpected types do not break loading', () async {
    SharedPreferences.setMockInitialValues({
      kRetiredThemeStyleKey: 7,
      kRetiredSeedColorKey: 'not a color',
      kRetiredUseDynamicColorKey: 'yes',
      kRetiredUseAmoledKey: 2.5,
      kMornyeAccentKey: 'teal',
    });
    final prefs = await SharedPreferences.getInstance();
    final container = await _loadTheme(prefs);

    expect(prefs.containsKey(kRetiredThemeStyleKey), isFalse);
    expect(prefs.containsKey(kRetiredSeedColorKey), isFalse);
    expect(prefs.containsKey(kRetiredUseDynamicColorKey), isFalse);
    expect(prefs.containsKey(kRetiredUseAmoledKey), isFalse);
    expect(container.read(themeProvider).mornyeAccent, MornyeAccent.teal);
  });

  test(
    'retired font keys are removed when only the theme marker is set',
    () async {
      SharedPreferences.setMockInitialValues({
        kRetiredThemePrefsRemovedKey: true,
        kRetiredUseSystemFontKey: true,
        kRetiredCustomFontIdKey: 'font-1',
        kRetiredCustomFontNameKey: 'Imported',
        kThemeModeKey: 'dark',
        kMornyeAccentKey: 'pink',
      });
      final prefs = await SharedPreferences.getInstance();
      final container = await _loadTheme(prefs);

      expect(prefs.containsKey(kRetiredUseSystemFontKey), isFalse);
      expect(prefs.containsKey(kRetiredCustomFontIdKey), isFalse);
      expect(prefs.containsKey(kRetiredCustomFontNameKey), isFalse);
      expect(prefs.getBool(kRetiredFontPrefsRemovedKey), isTrue);
      expect(container.read(themeProvider).mornyeAccent, MornyeAccent.pink);
    },
  );

  test(
    'a retired font key written after the font removal is left alone',
    () async {
      SharedPreferences.setMockInitialValues({
        kRetiredThemePrefsRemovedKey: true,
        kRetiredCustomFontIdKey: 'font-1',
      });
      final prefs = await SharedPreferences.getInstance();
      await _loadTheme(prefs);
      expect(prefs.containsKey(kRetiredCustomFontIdKey), isFalse);

      await prefs.setString(kRetiredCustomFontIdKey, 'font-2');
      await _loadTheme(prefs);
      expect(prefs.getString(kRetiredCustomFontIdKey), 'font-2');
    },
  );
}
