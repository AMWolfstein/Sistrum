import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/listening_statistics_screen.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/player_artwork.dart';

import 'support/fixed_settings.dart';

void main() {
  Widget app(Widget page) => MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: page,
  );
  testWidgets('listening recap shows totals and supports annual view', (
    tester,
  ) async {
    final requested = <({int days, int year})>[];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(FixedSettingsNotifier.new),
          listeningSummaryProvider.overrideWith((ref, period) async {
            requested.add(period);
            return ListeningSummary.fromRows([
              {
                'day': '2026-10-01',
                'track_key': 'one',
                'title': 'One',
                'artist': 'Artist',
                'album': 'Album',
                'milliseconds': 120000,
                'plays': 1,
                'artwork': '/covers/album.jpg',
              },
            ]);
          }),
        ],
        child: app(const ListeningStatisticsScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('1 play · 1 track · 1 active day'), findsOneWidget);
    await tester.tap(find.text('${DateTime.now().year}'));
    await tester.pumpAndSettle();
    expect(requested.last.days, 0);
    expect(requested.last.year, DateTime.now().year);
    final artistTile = find.byWidgetPredicate(
      (widget) =>
          widget is ListTile &&
          widget.title is Text &&
          (widget.title! as Text).data == 'Artist',
    );
    await tester.scrollUntilVisible(artistTile, 150);
    await tester.pumpAndSettle();
    final artistArtwork = find.descendant(
      of: artistTile,
      matching: find.byType(PlayerArtwork),
    );
    expect(artistArtwork, findsOneWidget);
    expect(
      tester.widget<PlayerArtwork>(artistArtwork).artUri,
      '/covers/album.jpg',
    );
    expect(tester.takeException(), isNull);
  });
}
