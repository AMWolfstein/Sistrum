import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/tag_credit_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/tag_separator_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';

import 'support/fixed_settings.dart';

Finder _chip(String label) =>
    find.descendant(of: find.byType(AppChoiceChip), matching: find.text(label));

final _pageScrollable = find
    .descendant(
      of: find.byType(CustomScrollView),
      matching: find.byType(Scrollable),
    )
    .first;

/// Moves [element] to [alignment] of its scroll view, clear of the pinned
/// header at the top and the bottom edge in a short landscape window.
Future<void> _center(
  WidgetTester tester,
  Element element, [
  double alignment = 0.5,
]) async {
  await Scrollable.ensureVisible(element, alignment: alignment);
  await tester.pumpAndSettle();
}

/// Scrolls the page back to the top, then down to the section [header], and
/// leaves the header a third of the way down so the section's rows are built.
/// The page is a lazy sliver list: rows far from view do not exist yet.
Future<void> _showSection(WidgetTester tester, String header) async {
  tester.state<ScrollableState>(_pageScrollable).position.jumpTo(0);
  await tester.pumpAndSettle();
  final target = find.text(header);
  await tester.scrollUntilVisible(target, 120, scrollable: _pageScrollable);
  await _center(tester, target.evaluate().first, 0.3);
}

/// Taps the first match of [finder], or the last one with [last], after
/// centring it. A page row not built yet is scrolled to first.
Future<void> _tap(
  WidgetTester tester,
  Finder finder, {
  bool last = false,
}) async {
  if (finder.evaluate().isEmpty) {
    await tester.scrollUntilVisible(finder, 120, scrollable: _pageScrollable);
  }
  final matches = finder.evaluate().toList();
  final element = last ? matches.last : matches.first;
  await _center(tester, element);
  await tester.tap(find.byElementPredicate((e) => identical(e, element)));
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final size in [const Size(390, 844), const Size(844, 390)]) {
    testWidgets('section sheets and Keep together ($size)', (tester) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final container = ProviderContainer(
        overrides: [settingsProvider.overrideWith(FixedSettingsNotifier.new)],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: ThemeData(extensions: const [MornyeTheme()]),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: const TagSeparatorSettingsPage(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      TagCreditSettings credits() =>
          container.read(settingsProvider).tagCredits;

      // Defaults are selected from the start.
      expect(credits().artistDelimiters, kArtistDelimiterDefaults);
      expect(credits().genreDelimiters, kGenreDelimiterDefaults);

      final sheet = find.byType(AppBottomSheet);
      Finder inSheet(Finder finder) =>
          find.descendant(of: sheet, matching: finder);

      // The page lists one row per section; Artists opens its chip sheet.
      await _tap(tester, find.text('Artists'));
      expect(sheet, findsOneWidget);
      expect(inSheet(find.text('Artist and album artist')), findsOneWidget);

      // Every artist chip starts selected: All is the default.
      expect(kArtistDelimiterDefaults, kArtistDelimiterChoices);

      // A selected chip turns off; tapping it again turns it back on.
      await _tap(tester, inSheet(_chip(',')));
      expect(credits().artistDelimiters, isNot(contains(',')));
      await _tap(tester, inSheet(_chip(',')));
      expect(credits().artistDelimiters, contains(','));

      // All selects every offered artist delimiter again.
      await _tap(tester, inSheet(_chip('/')));
      expect(credits().artistDelimiters, isNot(contains('/')));
      await _tap(tester, inSheet(_chip('All')));
      expect(credits().artistDelimiters, kArtistDelimiterChoices);

      // Add separator: Save adds the typed separator, selected, and closes.
      final sheetField = inSheet(find.byType(TextField));
      await _tap(tester, sheetField);
      await tester.enterText(sheetField, '|');
      await tester.pump();
      await _tap(tester, inSheet(find.text('Save')));
      expect(sheet, findsNothing);
      expect(credits().customArtistDelimiters, ['|']);
      expect(credits().artistDelimiters, contains('|'));

      // The last selected genre delimiter cannot be turned off.
      await _tap(tester, find.text('Genre'));
      await _tap(tester, inSheet(_chip('،')));
      expect(credits().genreDelimiters, [';']);
      await _tap(tester, inSheet(_chip(';')));
      expect(credits().genreDelimiters, [';']);
      await _tap(tester, inSheet(find.text('Save')));
      expect(sheet, findsNothing);
      expect(credits().customGenreDelimiters, isEmpty);

      // Keep together: a name is added, then removed.
      await _showSection(tester, 'Keep together');
      await _tap(tester, find.text('Keep together'));
      expect(sheet, findsOneWidget);
      final keepField = inSheet(find.byType(TextField));
      await _tap(tester, keepField);
      await tester.enterText(keepField, 'Tyler, The Creator');
      await tester.pump();
      await _tap(tester, inSheet(find.byTooltip('Add name')));
      expect(credits().keepTogether, ['Tyler, The Creator']);
      await _tap(tester, inSheet(find.byTooltip('Remove name')));
      expect(credits().keepTogether, isEmpty);
      expect(tester.takeException(), isNull);
    });
  }
}
