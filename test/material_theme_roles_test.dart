import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/settings_group.dart';

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
    testWidgets('Sistrum keeps its grouped surface', (tester) async {
      final theme = MornyeTheme.build(Brightness.light);
      expect(
        await _groupColor(tester, theme),
        theme.colorScheme.surfaceContainer,
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

    testWidgets('Sistrum info cards keep their soft fills', (tester) async {
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
