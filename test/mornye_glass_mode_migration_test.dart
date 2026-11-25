import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/settings_provider.dart' show kAppSettingsKey;
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';

Future<SharedPreferences> _prefs(Map<String, Object> values) async {
  SharedPreferences.setMockInitialValues(values);
  return SharedPreferences.getInstance();
}

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

  group('retired clarity maps to one mode', () {
    final cases = <(num?, MornyeGlassLevel)>[
      (0, MornyeGlassLevel.flat),
      (-1, MornyeGlassLevel.flat),
      (0.01, MornyeGlassLevel.liquid),
      (0.25, MornyeGlassLevel.liquid),
      (0.49, MornyeGlassLevel.liquid),
      (0.5, MornyeGlassLevel.liquid),
      (0.75, MornyeGlassLevel.liquid),
      (1, MornyeGlassLevel.liquid),
      (2, MornyeGlassLevel.liquid),
      (double.nan, MornyeGlassLevel.liquid),
      (null, MornyeGlassLevel.liquid),
    ];
    for (final (clarity, expected) in cases) {
      test('clarity $clarity becomes ${expected.name}', () {
        expect(mornyeGlassLevelFromLegacyClarity(clarity), expected);
      });
    }

    test('a stored retired Frosted mode reads as Liquid', () {
      expect(mornyeGlassLevelFromString('frosted'), MornyeGlassLevel.liquid);
      expect(mornyeGlassLevelFromString('liquid'), MornyeGlassLevel.liquid);
      expect(mornyeGlassLevelFromString('flat'), MornyeGlassLevel.flat);
      expect(mornyeGlassLevelFromString('bogus'), MornyeGlassLevel.liquid);
    });
  });

  test(
    'bootstrap reads the migrated mode without writing preferences',
    () async {
      final prefs = await _prefs({kLegacyMornyeGlassClarityKey: 0.25});
      expect(
        loadBootstrapThemeSettings(prefs).mornyeGlassLevel,
        MornyeGlassLevel.liquid,
      );
      expect(prefs.containsKey(kMornyeGlassModeKey), isFalse);
    },
  );

  test('the one-time migration writes the mode and removes clarity', () async {
    final prefs = await _prefs({kLegacyMornyeGlassClarityKey: 0.25});
    final container = await _loadTheme(prefs);
    expect(
      container.read(themeProvider).mornyeGlassLevel,
      MornyeGlassLevel.liquid,
    );
    expect(prefs.getString(kMornyeGlassModeKey), 'liquid');
    expect(prefs.containsKey(kLegacyMornyeGlassClarityKey), isFalse);
    expect(prefs.getBool(kMornyeGlassModeMigratedKey), isTrue);

    // A second start reads the same mode and changes nothing.
    final again = await _loadTheme(prefs);
    expect(again.read(themeProvider).mornyeGlassLevel, MornyeGlassLevel.liquid);
    expect(prefs.getString(kMornyeGlassModeKey), 'liquid');
  });

  test('a fresh install without clarity starts in Liquid Glass', () async {
    final prefs = await _prefs({});
    final container = await _loadTheme(prefs);
    expect(
      container.read(themeProvider).mornyeGlassLevel,
      MornyeGlassLevel.liquid,
    );
    expect(prefs.getBool(kMornyeGlassModeMigratedKey), isTrue);
  });

  test('a mode saved after the migration is never overwritten', () async {
    final prefs = await _prefs({
      kMornyeGlassModeMigratedKey: true,
      kMornyeGlassModeKey: 'flat',
      kLegacyMornyeGlassClarityKey: 1.0,
    });
    final container = await _loadTheme(prefs);
    expect(
      container.read(themeProvider).mornyeGlassLevel,
      MornyeGlassLevel.flat,
    );
    expect(prefs.getString(kMornyeGlassModeKey), 'flat');
  });

  test('the retired force blur never changes the migrated mode', () async {
    final forced = jsonEncode({'forceBackdropBlur': true});
    final glassPrefs = await _prefs({
      kLegacyMornyeGlassClarityKey: 0.25,
      kAppSettingsKey: forced,
    });
    final glass = await _loadTheme(glassPrefs);
    expect(glass.read(themeProvider).mornyeGlassLevel, MornyeGlassLevel.liquid);

    final nonePrefs = await _prefs({
      kLegacyMornyeGlassClarityKey: 0.0,
      kAppSettingsKey: forced,
    });
    final none = await _loadTheme(nonePrefs);
    expect(none.read(themeProvider).mornyeGlassLevel, MornyeGlassLevel.flat);
    expect(nonePrefs.getString(kMornyeGlassModeKey), 'flat');
  });

  test('stale force-blur settings are ignored and never written back', () {
    final settings = AppSettings.fromJson({'forceBackdropBlur': true});
    expect(settings.toJson().containsKey('forceBackdropBlur'), isFalse);
  });

  test('one preset table sets blur per mode: Liquid and None only', () {
    expect(MornyeGlassPreset.of(MornyeGlassLevel.liquid).blurSigma, 2);
    expect(MornyeGlassLevel.values, [
      MornyeGlassLevel.flat,
      MornyeGlassLevel.liquid,
    ]);
    final none = MornyeGlassPreset.of(MornyeGlassLevel.flat);
    expect(none.blurSigma, 0);
    expect(none.tintStrength, 0);
    expect(none.dispersion, 0);
  });
}
