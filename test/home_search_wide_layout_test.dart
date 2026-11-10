import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/utils/adaptive_layout.dart';

import 'support/search_results_fixture.dart';

// Tall enough for every section of the All view.
const _tablet = Size(1180, 1800);

const _grid = AppSettings(
  searchProvider: 'example',
  hasSearchedBefore: true,
  searchViewMode: 'grid',
);

/// The cover save started: its progress notice, or the failure a test
/// binding reports in its place.
void _expectCoverSaveStarted() {
  expect(
    find.byWidgetPredicate(
      (widget) =>
          widget is Text &&
          ((widget.data ?? '').startsWith('Downloading') ||
              (widget.data ?? '').startsWith('Failed')),
    ),
    findsOneWidget,
  );
}

Future<void> _dismissSnackBars(WidgetTester tester) async {
  ScaffoldMessenger.of(
    tester.element(find.byType(Scaffold).first),
  ).clearSnackBars();
  await tester.pumpAndSettle();
}

void main() {


  for (final mornye in [false, true]) {
    final style = mornye ? 'Mornye' : 'Material';


    for (final (platform, size) in [
      ('tablet', _tablet),
    ]) {
      testWidgets('$style $platform album filter shows a grid of covers', (
        tester,
      ) async {
        final results = SearchFixtureResults(
          count: 6,
          coverUrl: 'https://example.com/cover.jpg',
        );
        await pumpSearchFixture(
          tester,
          size: size,
          mornye: mornye,
          settings: _grid,
          results: results,
          filters: searchFixtureAllFilters,
        );
        await tester.tap(
          find.text('Albums filter'),
        );
        await tester.pumpAndSettle();
        expect(results.requestedFilters.last, 'album');

        expect(find.byType(SliverGrid), findsOneWidget);
        final cells = [
          for (var i = 0; i < 6; i++) find.byKey(ValueKey('album-album-$i')),
        ];
        for (final cell in cells) {
          expect(cell, findsOneWidget);
        }
        // One row of adaptive cells across the pane, as wide as the cover
        // geometry the skeleton shares.
        final first = tester.getRect(cells.first);
        final second = tester.getRect(cells[1]);
        expect(second.top, first.top);
        expect(second.left - first.right, searchCoverGap);
        expect(first.width, inInclusiveRange(searchCoverMinExtent, 210));
        expect(find.text('Example artist 0'), findsOneWidget);
        expect(find.byIcon(Icons.chevron_right), findsNothing);

        // A tap opens the album through the same route as the phone row.
        await tester.tap(find.text('Found album 0'));
        await tester.pump();
        expect(
          find.text('Cannot load album: missing extension source'),
          findsOneWidget,
        );
        await _dismissSnackBars(tester);

        // Long press on the cover opens the save-cover action.
        await tester.longPress(cells.first);
        await tester.pump();
        _expectCoverSaveStarted();
        await _dismissSnackBars(tester);

        // Sort orders the grid and each cell keeps its key.
        await tester.tap(find.text('Sort').hitTestable());
        await tester.pumpAndSettle();
        await tester.tap(find.text('Title (Z-A)'));
        await tester.tap(find.text('Apply'));
        await tester.pumpAndSettle();
        expect(
          tester.getRect(find.byKey(const ValueKey('album-album-5'))).left,
          first.left,
        );
        expect(
          tester.getCenter(find.text('Found album 5')).dx,
          lessThan(tester.getCenter(find.text('Found album 0')).dx),
        );
        expect(tester.takeException(), isNull);
      });
    }


    testWidgets('$style tablet All view shelves songs without a filter', (
      tester,
    ) async {
      final results = SearchFixtureResults(count: 2, songCount: 8);
      await pumpSearchFixture(
        tester,
        size: _tablet,
        mornye: mornye,
        settings: _grid,
        results: results,
        filters: searchFixtureCollectionFilters,
      );
      // Without a songs filter to switch to, the shelf holds every song.
      expect(find.byKey(const ValueKey('search-shelf-track')), findsOneWidget);
      expect(find.byKey(const ValueKey('search-shelf-album')), findsOneWidget);
      expect(find.bySemanticsLabel('Show all Songs'), findsNothing);
      expect(find.bySemanticsLabel('Show all Albums'), findsOneWidget);
      expect(results.requestedFilters, [null]);
      expect(tester.takeException(), isNull);
    });

  }
}
