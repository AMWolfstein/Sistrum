import 'dart:io';

import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';

import 'support/re_enrich_settings.dart';

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

final _albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

List<UnifiedLibraryItem> _items() => [
  for (final (index, id) in ['first', 'second'].indexed)
    UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem(
        id: id,
        trackName: id == 'first' ? 'First' : 'Second',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/$id.flac',
        service: 'example-provider',
        downloadedAt: DateTime(2026),
        trackNumber: index + 1,
        duration: 180,
      ),
    ),
];

Future<void> _pump(WidgetTester tester, List<UnifiedLibraryItem> items) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(430, 1200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(ReEnrichTestSettings.new),
        downloadHistoryProvider.overrideWith(_History.new),
        libraryAlbumItemsProvider(_albumKey).overrideWith((ref) async => items),
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream.value(null),
        ),
        playbackPlayingProvider.overrideWith((ref) => false),
        playbackLoadingProvider.overrideWith((ref) => false),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SelectionOverlayHost(
          child: AlbumScreen.library(albumKey: _albumKey),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  test('album delete leaves server copies to the server options', () {
    final local = _items().first;
    final server = UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem(
        id: 'server',
        trackName: 'Server',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: 'network://server/server.flac',
        service: 'example-provider',
        downloadedAt: DateTime(2026),
        duration: 180,
      ),
    );

    final picked = deletableLibraryItems(
      [local, server],
      {local.id, server.id},
    );

    expect(picked.map((item) => item.id), [local.id]);
    expect(deletableLibraryItems([server], {server.id}), isEmpty);
  });

  testWidgets(
    'library album header offers compact Play and Shuffle beside Love and Add to playlist, no Download',
    (tester) async {
      await _pump(tester, _items());
      final l10n = AppLocalizations.of(
        tester.element(find.byType(AlbumScreen)),
      );
      expect(
        find.byWidgetPredicate((w) => w is AlbumPlayActions && w.compact),
        findsOneWidget,
      );
      expect(find.text(l10n.tooltipPlay), findsOneWidget);
      expect(find.text(l10n.actionShuffle), findsOneWidget);
      expect(find.byTooltip(l10n.tooltipLoveAll), findsOneWidget);
      expect(find.byTooltip(l10n.tooltipAddToPlaylist), findsOneWidget);
      expect(find.text(l10n.dialogDownload), findsNothing);
      expect(find.text('Album'), findsWidgets);
      expect(find.text('Artist'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'a row ends with the outlined play circle and three dots, and the '
    'dots open only offline actions with Delete in red',
    (tester) async {
      await _pump(tester, _items());
      final row = find.byWidgetPredicate(
        (w) => w is AlbumTrackTile && w.trackName == 'First',
      );
      expect(
        find.descendant(
          of: row,
          matching: find.byIcon(CupertinoIcons.play_circle),
        ),
        findsOneWidget,
      );
      await tester.tap(
        find.descendant(
          of: row,
          matching: find.byIcon(CupertinoIcons.ellipsis),
        ),
      );
      await tester.pumpAndSettle();
      final l10n = AppLocalizations.of(
        tester.element(find.byType(AlbumScreen)),
      );
      expect(find.text(l10n.trackMetadataPlay), findsNWidgets(2));
      expect(find.text(l10n.collectionAddToPlaylist), findsOneWidget);
      expect(find.text(l10n.dialogDownload), findsNothing);
      final delete = find.text(l10n.selectionDeleteTracksCount(1));
      expect(delete, findsOneWidget);
      final style = tester.widget<Text>(delete).style;
      final scheme = Theme.of(tester.element(delete)).colorScheme;
      expect(style?.color, scheme.error);
    },
  );

  testWidgets('library rows show the track number, title and artist', (
    tester,
  ) async {
    await _pump(tester, _items());
    final row = find.byWidgetPredicate(
      (w) => w is AlbumTrackTile && w.trackName == 'First',
    );
    expect(row, findsOneWidget);
    expect(find.descendant(of: row, matching: find.text('1')), findsOneWidget);
    expect(
      find.descendant(of: row, matching: find.text('Artist')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('library selection offers offline actions only', (tester) async {
    await _pump(tester, _items());
    final l10n = AppLocalizations.of(tester.element(find.byType(AlbumScreen)));
    await tester.longPress(find.text('First'));
    await tester.pumpAndSettle();
    final bar = find.byType(LibraryTrackSelectionBar);
    expect(bar, findsOneWidget);
    expect(find.byType(LibrarySelectionPlaybackActions), findsOneWidget);
    expect(
      find.descendant(
        of: bar,
        matching: find.text(l10n.collectionAddToPlaylist),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: bar, matching: find.text(l10n.dialogDownload)),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  test('album entry points open the library album page', () {
    // Each entry file opens the page by key; the old pages are gone.
    const entries = [
      'lib/screens/mornye_library_screen.dart',
      'lib/screens/queue_tab_navigation.dart',
      'lib/controllers/player_track_navigation.dart',
      'lib/screens/home_tab_intro.dart',
      'lib/widgets/library_search_results.dart',
      'lib/screens/artist_screen_library.dart',
    ];
    for (final path in entries) {
      expect(
        File(path).readAsStringSync(),
        contains('AlbumScreen.library('),
        reason: path,
      );
    }
    for (final path in [...entries, 'lib/screens/artist_screen.dart']) {
      final source = File(path).readAsStringSync();
      expect(source, isNot(contains('DownloadedAlbumScreen')), reason: path);
      expect(source, isNot(contains('LocalAlbumScreen')), reason: path);
      expect(source, isNot(contains('downloadedAlbumKeys')), reason: path);
    }
  });
}
