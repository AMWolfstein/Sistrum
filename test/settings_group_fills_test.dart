import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/settings_group.dart';

Widget _host(Widget child, {ThemeData? theme}) {
  return MaterialApp(
    theme: theme ?? MornyeTheme.build(Brightness.light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
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
