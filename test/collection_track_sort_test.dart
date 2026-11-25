import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/collection_row.dart';
import 'package:sistrum/providers/collection_track_sort_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/collection_detail_screen.dart';
import 'package:sistrum/services/listening_statistics.dart' show playCountKey;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';
import 'package:sistrum/widgets/desktop/track_list_table.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fixed_settings.dart';
import 'support/tooltip_finder.dart';

CollectionTrackEntry _entry(
  String key,
  String name,
  String artist,
  String album,
  int day, {
  int? number,
}) => CollectionTrackEntry(
  key: key,
  addedAt: DateTime(2026, 1, day),
  track: Track(
    id: key,
    name: name,
    artistName: artist,
    albumName: album,
    trackNumber: number,
    duration: 180,
  ),
);

/// Saved order a, b, c. By title c, b, a; by artist b, c, a; by album
/// b, a, c; newest first c, a, b.
final _entries = [
  _entry('a', 'Zebra', 'Cara', 'Blue', 2, number: 2),
  _entry('b', 'apple 10', 'Ana', 'Blue', 1, number: 1),
  _entry('c', 'Apple 2', 'Ben', 'Red', 3),
];

List<CollectionRow> _rows(Iterable<CollectionTrackEntry> entries) => [
  for (final entry in entries)
    CollectionRow(key: entry.key, track: entry.track),
];

List<String> _keys(Iterable<CollectionRow> rows) => [
  for (final row in rows) row.key,
];

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    loved: _entries,
    playlists: [
      UserPlaylistCollection(
        id: 'mix',
        name: 'My mix',
        createdAt: DateTime(2026),
        updatedAt: DateTime(2026),
        tracks: _entries,
      ),
    ],
  );

  @override
  Future<void> ensurePlaylistLoaded(String id) async {}
}

class _Library extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

Future<void> _open(
  WidgetTester tester, {
  bool desktop = false,
  CollectionDetailKind kind = CollectionDetailKind.playlist,
}) async {
  debugDesktopPlatformOverride = desktop;
  tester.view.physicalSize = desktop
      ? const Size(1200, 900)
      : const Size(430, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(FixedSettingsNotifier.new),
        libraryCollectionsProvider.overrideWith(_Collections.new),
        localLibraryProvider.overrideWith(_Library.new),
        localLibraryCoverProvider.overrideWith((ref, request) async => null),
        localLibraryFirstCoverProvider.overrideWith(
          (ref, request) async => null,
        ),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: kind == CollectionDetailKind.playlist
            ? CollectionDetailScreen.playlist(playlistId: 'mix')
            : const CollectionDetailScreen.loved(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// The song titles from the top of the page down.
List<String> _shown(WidgetTester tester, {bool desktop = false}) {
  if (desktop) {
    return [
      for (final track
          in tester.widget<TrackListTable>(find.byType(TrackListTable)).tracks)
        track.name,
    ];
  }
  final names = ['Zebra', 'apple 10', 'Apple 2'];
  return names..sort(
    (a, b) => tester
        .getTopLeft(find.text(a))
        .dy
        .compareTo(tester.getTopLeft(find.text(b)).dy),
  );
}

Future<void> _pick(WidgetTester tester, String label) async {
  await tester.tap(findByTooltip('Sort Songs').hitTestable().first);
  await tester.pumpAndSettle();
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(() => debugDesktopPlatformOverride = null);

  test('each sort orders the songs and ties keep the saved order', () {
    // Plays by song: "Apple 2" twice as often as "Zebra", "apple 10" unplayed.
    final plays = {
      playCountKey('Apple 2', 'Ben'): 5,
      playCountKey('Zebra', 'Cara'): 1,
    };
    List<String> sorted(CollectionTrackSort sort) =>
        _keys(sortCollectionRows(_rows(_entries), sort, plays));

    expect(sorted(CollectionTrackSort.custom), ['a', 'b', 'c']);
    // Case is ignored and "2" comes before "10".
    expect(sorted(CollectionTrackSort.title), ['c', 'b', 'a']);
    expect(sorted(CollectionTrackSort.artist), ['b', 'c', 'a']);
    // An album lists its songs by number.
    expect(sorted(CollectionTrackSort.album), ['b', 'a', 'c']);
    expect(sorted(CollectionTrackSort.mostPlayed), ['c', 'a', 'b']);

    final same = _rows([
      _entry('x', 'Same', 'One', 'Album', 1),
      _entry('y', 'Same', 'One', 'Album', 1),
      _entry('z', 'Same', 'One', 'Album', 1),
    ]);
    for (final sort in CollectionTrackSort.values) {
      expect(_keys(sortCollectionRows(same, sort, const {})), ['x', 'y', 'z']);
    }
    // The saved list itself is never reordered.
    expect(_keys(_rows(_entries)), ['a', 'b', 'c']);
  });

  for (final desktop in [false, true]) {
    testWidgets('a playlist lists its songs by the chosen sort and keeps '
        'the choice (desktop: $desktop)', (tester) async {
      await _open(tester, desktop: desktop);
      expect(_shown(tester, desktop: desktop), [
        'Zebra',
        'apple 10',
        'Apple 2',
      ]);

      await _pick(tester, 'Title (A-Z)');
      expect(_shown(tester, desktop: desktop), [
        'Apple 2',
        'apple 10',
        'Zebra',
      ]);
      final prefs = await SharedPreferences.getInstance();
      expect(jsonDecode(prefs.getString('collection_track_sorts')!), {
        'playlist:mix': 'title',
      });

      // Custom Order opens the drag order, which starts from the saved order.
      await _pick(tester, 'Custom Order');
      expect(
        _keys(
          tester
              .widget<CollectionReorderSliver>(
                find.byType(CollectionReorderSliver),
              )
              .entries,
        ),
        ['a', 'b', 'c'],
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a saved choice applies when the playlist opens, and to that '
      'playlist only', (tester) async {
    SharedPreferences.setMockInitialValues({
      'collection_track_sorts': jsonEncode({'playlist:mix': 'artist'}),
    });
    await _open(tester);
    expect(_shown(tester), ['apple 10', 'Apple 2', 'Zebra']);

    await _open(tester, kind: CollectionDetailKind.loved);
    expect(_shown(tester), ['Zebra', 'apple 10', 'Apple 2']);
  });

  testWidgets('Edit order works on the saved order whatever the page shows', (
    tester,
  ) async {
    await _open(tester);
    await _pick(tester, 'Title (A-Z)');

    await _pick(tester, 'Custom Order');

    expect(
      _keys(
        tester
            .widget<CollectionReorderSliver>(
              find.byType(CollectionReorderSliver),
            )
            .entries,
      ),
      ['a', 'b', 'c'],
    );
    // Sorting waits until the order is saved or the edit is cancelled.
    expect(findByTooltip('Sort Songs'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
