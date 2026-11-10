import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:mornye/l10n/app_localizations.dart';
import 'package:mornye/models/theme_settings.dart';
import 'package:mornye/providers/theme_provider.dart';
import 'package:mornye/theme/app_theme.dart';
import 'package:mornye/theme/dynamic_color_wrapper.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/settings_group.dart';
import 'package:shared_preferences/shared_preferences.dart';


double _contrast(Color first, Color second) {
  final a = first.computeLuminance();
  final b = second.computeLuminance();
  return a > b ? (a + 0.05) / (b + 0.05) : (b + 0.05) / (a + 0.05);
}

Widget _host(Widget child, {ThemeData? theme, bool reduceMotion = false}) {
  return MaterialApp(
    theme: theme ?? AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reduceMotion),
      child: child!,
    ),
    home: Scaffold(body: child),
  );
}

/// The group fill as a widget below [theme] sees it.
Future<Color> _groupColor(WidgetTester tester, ThemeData theme) async {
  late Color color;
  await tester.pumpWidget(
    _host(
      Builder(
        builder: (context) {
          color = settingsGroupColor(context);
          return const SizedBox();
        },
      ),
      theme: theme,
    ),
  );
  // MaterialApp animates between themes; read the settled one.
  await tester.pumpAndSettle();
  return color;
}

BorderRadius? _radiusOf(ShapeBorder? shape) =>
    shape is RoundedRectangleBorder ? shape.borderRadius as BorderRadius : null;

void main() {
  group('settingsGroupColor', () {
    testWidgets('Material groups keep the 5.2.1 neutral fill', (tester) async {
      for (final theme in [AppTheme.light(), AppTheme.dark()]) {
        final scheme = theme.colorScheme;
        final color = await _groupColor(tester, theme);
        final expected = Color.alphaBlend(
          scheme.brightness == Brightness.dark
              ? Colors.white.withValues(alpha: 0.08)
              : Colors.black.withValues(alpha: 0.04),
          scheme.surface,
        );
        expect(color, expected, reason: '${scheme.brightness}');
        expect(color, isNot(scheme.surface));
      }
    });

    testWidgets('Mornye keeps its grouped surface', (tester) async {
      final theme = MornyeTheme.build(Brightness.light);
      expect(
        await _groupColor(tester, theme),
        theme.colorScheme.surfaceContainer,
      );
    });

    testWidgets('AMOLED groups and menus stay apart from the black page', (
      tester,
    ) async {
      const settings = ThemeSettings(
        themeMode: ThemeMode.dark,
        useDynamicColor: false,
        useAmoled: true,
        style: AppThemeStyle.material,
      );
      SharedPreferences.setMockInitialValues({
        kThemeModeKey: 'dark',
        kUseDynamicColorKey: false,
        kUseAmoledKey: true,
        kThemeStyleKey: 'material',
      });
      late ThemeData theme;
      late Color group;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [initialThemeSettingsProvider.overrideWithValue(settings)],
          child: DynamicColorWrapper(
            builder: (light, dark, mode) => MaterialApp(
              theme: light,
              darkTheme: dark,
              themeMode: mode,
              home: Builder(
                builder: (context) {
                  theme = Theme.of(context);
                  group = settingsGroupColor(context);
                  return const SizedBox();
                },
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final scheme = theme.colorScheme;
      expect(scheme.brightness, Brightness.dark);
      expect(scheme.surface, Colors.black);
      expect(theme.scaffoldBackgroundColor, Colors.black);
      expect(
        group,
        Color.alphaBlend(Colors.white.withValues(alpha: 0.08), Colors.black),
      );
      // At least as far from black as the old blended group fill (#141414).
      expect(
        _contrast(group, Colors.black),
        greaterThanOrEqualTo(_contrast(const Color(0xFF141414), Colors.black)),
      );
      // Menus sit on the low tier, and shadows do not show on black.
      expect(
        _contrast(scheme.surfaceContainerLow, Colors.black),
        greaterThan(1.1),
      );
      final ladder = [
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
      ];
      expect(ladder.first, Colors.black);
      for (var i = 1; i < ladder.length; i++) {
        expect(
          _contrast(ladder[i], ladder[i - 1]),
          greaterThan(1.03),
          reason: 'tier $i must stay above tier ${i - 1}',
        );
        expect(
          ladder[i].computeLuminance(),
          greaterThan(ladder[i - 1].computeLuminance()),
        );
      }
      expect(theme.popupMenuTheme.color, scheme.surfaceContainerLow);
      expect(theme.navigationBarTheme.backgroundColor, scheme.surfaceContainer);
    });

    testWidgets('a reduced motion Material card keeps the 5.2.1 outline', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(
          SettingsGroup(
            children: [
              SettingsItem(title: 'Field', onTap: () {}),
              const SizedBox(height: 12),
            ],
          ),
          reduceMotion: true,
        ),
      );
      final scheme = AppTheme.light().colorScheme;
      final card = tester.widget<Container>(
        find
            .descendant(
              of: find.byType(SettingsGroup),
              matching: find.byWidgetPredicate(
                (widget) =>
                    widget is Container && widget.decoration is BoxDecoration,
              ),
            )
            .first,
      );
      final decoration = card.decoration! as BoxDecoration;
      expect(
        decoration.color,
        Color.alphaBlend(Colors.black.withValues(alpha: 0.04), scheme.surface),
      );
      expect(
        decoration.border,
        Border.all(color: scheme.outlineVariant.withValues(alpha: 0.5)),
      );
    });
  });

  group('menus', () {
    test('theme menus share the M3 Expressive standard menu skin', () {
      const expressive = M3EMenuTheme.defaults;
      for (final theme in [AppTheme.light(), AppTheme.dark()]) {
        final scheme = theme.colorScheme;
        final container = BorderRadius.circular(expressive.containerRadius);

        expect(theme.popupMenuTheme.color, scheme.surfaceContainerLow);
        expect(_radiusOf(theme.popupMenuTheme.shape), container);

        final style = theme.menuTheme.style!;
        expect(style.backgroundColor!.resolve({}), scheme.surfaceContainerLow);
        expect(style.elevation!.resolve({}), theme.popupMenuTheme.elevation);
        expect(_radiusOf(style.shape!.resolve({})), container);
        expect(
          style.padding!.resolve({}),
          EdgeInsets.all(expressive.stateLayerInset),
        );

        final item = theme.menuButtonTheme.style!.shape!.resolve({});
        expect(_radiusOf(item), BorderRadius.circular(expressive.itemRadius));
      }
    });

  });

  group('settings fills', () {
    Widget fills() => Column(
      children: [
        for (final tone in SettingsInfoTone.values)
          SettingsInfoCard(key: ValueKey(tone), tone: tone, message: tone.name),
        SettingsChoiceChip(label: 'Off', isSelected: false, onTap: () {}),
      ],
    );

    Color? cardFill(WidgetTester tester, SettingsInfoTone tone) {
      final box = tester.widget<DecoratedBox>(
        find
            .descendant(
              of: find.byKey(ValueKey(tone)),
              matching: find.byType(DecoratedBox),
            )
            .first,
      );
      return (box.decoration as BoxDecoration).color;
    }

    testWidgets('Material info cards and chips use solid container tiers', (
      tester,
    ) async {
      for (final theme in [AppTheme.light(), AppTheme.dark()]) {
        final scheme = theme.colorScheme;
        await tester.pumpWidget(_host(fills(), theme: theme));
        await tester.pumpAndSettle();

        expect(
          cardFill(tester, SettingsInfoTone.neutral),
          scheme.surfaceContainerHigh,
        );
        expect(
          cardFill(tester, SettingsInfoTone.warning),
          scheme.tertiaryContainer,
        );
        expect(cardFill(tester, SettingsInfoTone.error), scheme.errorContainer);

        final chip = tester.widget<Material>(
          find
              .descendant(
                of: find.byType(SettingsChoiceChip),
                matching: find.byType(Material),
              )
              .first,
        );
        expect(chip.color, scheme.surfaceContainerHigh);
      }
    });

    testWidgets('Mornye info cards keep their soft fills', (tester) async {
      final theme = MornyeTheme.build(Brightness.light);
      final scheme = theme.colorScheme;
      await tester.pumpWidget(_host(fills(), theme: theme));

      expect(
        cardFill(tester, SettingsInfoTone.error),
        scheme.errorContainer.withValues(alpha: 0.6),
      );
      expect(
        cardFill(tester, SettingsInfoTone.neutral),
        scheme.surfaceContainerHighest.withValues(alpha: 0.5),
      );
    });
  });
}
