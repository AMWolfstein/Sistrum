import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/models/download_history.dart';

class _Playback extends PlaybackController {
  final played = <String>[];

  @override
  Future<void> playLibraryQueue(
    List<UnifiedLibraryItem> items, {
    required UnifiedLibraryItem startItem,
  }) async {
    played.add(startItem.localItem!.id);
  }
}

void main() {
  final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

  testWidgets('downloaded album order survives empty and reload', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(430, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final loads = <Completer<List<UnifiedLibraryItem>>>[];
    final provider = libraryAlbumItemsProvider(albumKey);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          provider.overrideWith((ref) {
            final load = Completer<List<UnifiedLibraryItem>>();
            loads.add(load);
            return load.future;
          }),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AlbumScreen.library(albumKey: albumKey),
        ),
      ),
    );
    expect(find.byType(AppLoadingIndicator), findsOneWidget);
    loads.single.complete([]);
    await tester.pumpAndSettle();
    expect(find.byType(AlbumTrackTile), findsNothing);
    final container = ProviderScope.containerOf(
      tester.element(find.byType(AlbumScreen)),
    );
    container.invalidate(provider);
    await tester.pump();
    expect(find.byType(AppLoadingIndicator), findsOneWidget);
    loads.last.complete([
      for (final disc in [2, 1])
        UnifiedLibraryItem.fromDownloadHistory(
          DownloadHistoryItem(
            id: '$disc',
            trackName: 'Disc $disc',
            artistName: 'Artist',
            albumName: 'Album',
            filePath: '/missing/song-$disc.flac',
            service: 'example',
            downloadedAt: DateTime.utc(2026),
            discNumber: disc,
            trackNumber: 1,
          ),
        ),
    ]);
    await tester.pumpAndSettle();
    expect(
      tester
          .widgetList<AlbumTrackTile>(find.byType(AlbumTrackTile))
          .map((tile) => tile.trackName),
      ['Disc 1', 'Disc 2'],
    );
    container.invalidate(provider);
    await tester.pump();
    loads.last.complete([]);
    await tester.pumpAndSettle();
    expect(find.byType(AlbumTrackTile), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('album row opens metadata, Play plays', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(430, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final playback = _Playback();
    final navigator = GlobalKey<NavigatorState>();
    final tracks = [
      for (final number in [2, 1])
        UnifiedLibraryItem.fromLocalLibrary(
          LocalLibraryItem(
            id: '$number',
            trackName: 'Song $number',
            artistName: 'Artist',
            albumName: 'Album',
            filePath: '/missing/song-$number.flac',
            trackNumber: number,
            scannedAt: DateTime(2026),
          ),
        ),
    ];
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          playbackProvider.overrideWith(() => playback),
          libraryAlbumItemsProvider(
            albumKey,
          ).overrideWith((ref) async => tracks),
        ],
        child: MaterialApp(
          navigatorKey: navigator,
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AlbumScreen.library(albumKey: albumKey),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Song 2'), 150);
    await tester.tap(find.text('Song 2'));
    await tester.pumpAndSettle();
    final metadata = tester.widget<TrackMetadataScreen>(
      find.byType(TrackMetadataScreen),
    );
    expect(metadata.localItem!.id, '2');
    expect(metadata.localNavigationItems!.map((track) => track.id), ['1', '2']);
    expect(metadata.navigationIndex, 1);
    expect(playback.played, isEmpty);
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
    final row = find.byWidgetPredicate(
      (widget) => widget is AlbumTrackTile && widget.trackName == 'Song 2',
    );
    await tester.tap(
      find.descendant(of: row, matching: find.byType(IconButton)).first,
    );
    await tester.pumpAndSettle();
    expect(playback.played, ['2']);
    expect(find.byType(TrackMetadataScreen), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
