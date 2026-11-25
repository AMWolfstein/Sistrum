import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/screens/settings/appearance_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

import 'support/tooltip_finder.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';

void main() {
  test(
    'old themes retain outlines and new themes serialize the preference',
    () {
      expect(ThemeSettings.fromJson({}).mornyeGlassOutlines, isTrue);
      const settings = ThemeSettings(mornyeGlassOutlines: false);
      expect(ThemeSettings.fromJson(settings.toJson()), settings);
      expect(
        settings
            .copyWith(mornyeGlassLevel: MornyeGlassLevel.flat)
            .mornyeGlassOutlines,
        isFalse,
      );
      expect(settings, isNot(const ThemeSettings()));
    },
  );
  test('glass mode restores safely from saved and invalid settings', () {
    expect(
      ThemeSettings.fromJson({}).mornyeGlassLevel,
      MornyeGlassLevel.liquid,
    );
    for (final (saved, expected) in [
      ('flat', MornyeGlassLevel.flat),
      ('frosted', MornyeGlassLevel.liquid),
      ('liquid', MornyeGlassLevel.liquid),
      ('unknown', MornyeGlassLevel.liquid),
    ]) {
      final settings = ThemeSettings.fromJson({kMornyeGlassModeKey: saved});
      expect(settings.mornyeGlassLevel, expected, reason: saved);
      expect(ThemeSettings.fromJson(settings.toJson()), settings);
    }
  });
  test('custom accents keep text readable in both appearances', () {
    double contrast(Color first, Color second) {
      final a = first.computeLuminance();
      final b = second.computeLuminance();
      return a > b ? (a + 0.05) / (b + 0.05) : (b + 0.05) / (a + 0.05);
    }

    for (final brightness in Brightness.values) {
      // Grouped settings cells, where tinted action rows sit.
      final cell = brightness == Brightness.dark
          ? const Color(0xff1c1c1e)
          : Colors.white;
      for (final accent in MornyeAccent.values) {
        final scheme = MornyeTheme.build(
          brightness,
          accent: accent,
        ).colorScheme;
        expect(
          contrast(scheme.primary, scheme.surface),
          greaterThanOrEqualTo(4.5),
          reason: '$accent on $brightness',
        );
        expect(
          contrast(scheme.primary, cell),
          greaterThanOrEqualTo(4.5),
          reason: '$accent on a $brightness grouped cell',
        );
        expect(
          contrast(scheme.onPrimary, scheme.primary),
          greaterThanOrEqualTo(4.5),
          reason: 'Button text for $accent on $brightness',
        );
      }
    }
  });

  Future<void> openSettings(
    WidgetTester tester,
    SharedPreferences prefs, {
    bool lowEnd = false,
  }) async {
    tester.view.physicalSize = const Size(390, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          initialThemeSettingsProvider.overrideWithValue(
            loadBootstrapThemeSettings(prefs),
          ),
          // Low-end devices render flat, opaque glass whatever the mode.
          if (lowEnd) lowEndDeviceProvider.overrideWithValue(true),
        ],
        child: Consumer(
          builder: (context, ref, _) {
            final settings = ref.watch(themeProvider);
            ThemeData themeFor(Brightness brightness) => MornyeTheme.build(
              brightness,
              accent: settings.mornyeAccent,
              glassOutlines: settings.mornyeGlassOutlines,
            );
            return MaterialApp(
              theme: themeFor(Brightness.light),
              darkTheme: themeFor(Brightness.dark),
              themeMode: settings.themeMode,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: const MornyeSettingsTheme(child: AppearanceSettingsPage()),
            );
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  for (final mode in [ThemeMode.light, ThemeMode.dark]) {
    testWidgets(
      'glass outlines toggle survives restart and local themes ($mode)',
      (tester) async {
        SharedPreferences.setMockInitialValues({
          kThemeModeKey: mode.name,
          kRetiredThemePrefsRemovedKey: true,
        });
        final prefs = await SharedPreferences.getInstance();
        await openSettings(tester, prefs);
        final toggle = find.byKey(const ValueKey('mornye-glass-outlines'));
        await tester.ensureVisible(toggle);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Glass outlines'));
        await tester.pumpAndSettle();
        expect(prefs.getBool(kMornyeGlassOutlinesKey), isFalse);
        final context = tester.element(toggle);
        expect(MornyeTheme.glassOutlinesOf(context), isFalse);
        final local = MornyeTheme.fromContext(
          context,
          brightness: Brightness.dark,
        );
        expect(local.extension<MornyeTheme>()!.glassOutlines, isFalse);
        await tester.pumpWidget(const SizedBox());
        await openSettings(tester, prefs);
        expect(MornyeTheme.glassOutlinesOf(tester.element(toggle)), isFalse);
        await tester.ensureVisible(toggle);
        await tester.pumpAndSettle();
        await tester.tap(find.text('Glass outlines'));
        await tester.pumpAndSettle();
        expect(prefs.getBool(kMornyeGlassOutlinesKey), isTrue);
        // The theme picker follows the glass mode selector on the page.
        final picker = find.byType(MornyeSegmentedControl).last;
        await tester.scrollUntilVisible(
          picker,
          200,
          scrollable: find.byType(Scrollable).first,
        );
        // The theme picker sits on an opaque group card.
        expect(
          find.descendant(of: picker, matching: find.byType(LiquidGlassLens)),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('glass mode previews live and survives restart ($mode)', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({
        kThemeModeKey: mode.name,
        kRetiredThemePrefsRemovedKey: true,
      });
      final prefs = await SharedPreferences.getInstance();
      await openSettings(tester, prefs);
      final selector = find.byKey(const ValueKey('mornye-glass-mode'));
      await tester.ensureVisible(selector);
      await tester.pumpAndSettle();
      expect(
        ProviderScope.containerOf(
          tester.element(selector),
        ).read(mornyeGlassModeProvider),
        MornyeGlassLevel.liquid,
      );

      await tester.tap(
        find.descendant(of: selector, matching: find.text('None')),
      );
      await tester.pumpAndSettle();
      expect(prefs.getString(kMornyeGlassModeKey), 'flat');
      expect(
        ProviderScope.containerOf(
          tester.element(selector),
        ).read(mornyeGlassLevelProvider),
        MornyeGlassLevel.flat,
      );

      await tester.pumpWidget(const SizedBox());
      await openSettings(tester, prefs);
      final page = find.byType(AppearanceSettingsPage);
      expect(
        ProviderScope.containerOf(
          tester.element(page),
        ).read(mornyeGlassModeProvider),
        MornyeGlassLevel.flat,
      );

      await tester.ensureVisible(selector);
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(of: selector, matching: find.text('Liquid Glass')),
      );
      await tester.pumpAndSettle();
      expect(prefs.getString(kMornyeGlassModeKey), 'liquid');
      expect(
        ProviderScope.containerOf(
          tester.element(page),
        ).read(mornyeGlassLevelProvider),
        MornyeGlassLevel.liquid,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'glass mode keeps the choice on devices that render flat ($mode)',
      (tester) async {
        SharedPreferences.setMockInitialValues({
          kThemeModeKey: mode.name,
          kRetiredThemePrefsRemovedKey: true,
        });
        final prefs = await SharedPreferences.getInstance();
        await openSettings(tester, prefs, lowEnd: true);
        final selector = find.byKey(const ValueKey('mornye-glass-mode'));
        await tester.ensureVisible(selector);
        await tester.pumpAndSettle();
        final container = ProviderScope.containerOf(tester.element(selector));
        expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.flat);
        expect(
          container.read(mornyeGlassModeProvider),
          MornyeGlassLevel.liquid,
        );

        await tester.tap(
          find.descendant(of: selector, matching: find.text('None')),
        );
        await tester.pumpAndSettle();
        expect(prefs.getString(kMornyeGlassModeKey), 'flat');
        expect(container.read(mornyeGlassModeProvider), MornyeGlassLevel.flat);
        expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.flat);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'glass mode explains high contrast without changing the choice ($mode)',
      (tester) async {
        tester.platformDispatcher.accessibilityFeaturesTestValue =
            const FakeAccessibilityFeatures(highContrast: true);
        addTearDown(
          tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
        );
        SharedPreferences.setMockInitialValues({
          kThemeModeKey: mode.name,
          kRetiredThemePrefsRemovedKey: true,
        });
        final prefs = await SharedPreferences.getInstance();
        await openSettings(tester, prefs);
        final selector = find.byKey(const ValueKey('mornye-glass-mode'));
        await tester.ensureVisible(selector);
        await tester.pumpAndSettle();
        final note = find.byKey(const ValueKey('mornye-glass-high-contrast'));
        expect(
          tester.widget<Text>(note).data,
          'High contrast is on, so glass is turned off.',
        );
        expect(MediaQuery.highContrastOf(tester.element(selector)), isTrue);

        await tester.tap(
          find.descendant(of: selector, matching: find.text('None')),
        );
        await tester.pumpAndSettle();
        expect(prefs.getString(kMornyeGlassModeKey), 'flat');
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('Mornye accent updates live and survives restart ($mode)', (
      tester,
    ) async {
      SharedPreferences.setMockInitialValues({
        kThemeModeKey: mode.name,
        kMornyeAccentKey: 'red',
        kMornyeAccentMovedToBlueKey: true,
        kRetiredThemePrefsRemovedKey: true,
      });
      final prefs = await SharedPreferences.getInstance();
      await openSettings(tester, prefs);
      final page = find.byType(AppearanceSettingsPage);
      final initial = Theme.of(tester.element(page));
      expect(
        initial.colorScheme.primary,
        MornyeTheme.accentColor(MornyeAccent.red, initial.brightness),
      );

      final blue = findByTooltip('Blue');
      await tester.ensureVisible(blue);
      await tester.tap(blue);
      await tester.pumpAndSettle();
      final theme = Theme.of(tester.element(page));
      expect(
        theme.colorScheme.primary,
        MornyeTheme.accentColor(MornyeAccent.blue, theme.brightness),
      );
      expect(prefs.getString(kMornyeAccentKey), 'blue');
      final overlay = MornyeTheme.fromContext(
        tester.element(page),
        brightness: Brightness.dark,
      );
      expect(overlay.extension<MornyeTheme>()!.accent, MornyeAccent.blue);
      expect(
        overlay.colorScheme.primary,
        MornyeTheme.accentColor(MornyeAccent.blue, Brightness.dark),
      );

      await tester.pumpWidget(const SizedBox());
      await openSettings(tester, prefs);
      expect(
        Theme.of(tester.element(page)).colorScheme.primary,
        theme.colorScheme.primary,
      );
      await tester.ensureVisible(findByTooltip('Red (default)'));
      await tester.tap(findByTooltip('Red (default)'));
      await tester.pumpAndSettle();
      expect(
        Theme.of(tester.element(page)).colorScheme.primary,
        initial.colorScheme.primary,
      );
      expect(tester.takeException(), isNull);
    });
  }
}
