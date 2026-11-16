import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';

import 'support/fixed_settings.dart';

// The library's own track, so the Loved keys match the album page's rows.
final _item = UnifiedLibraryItem.fromDownloadHistory(
  DownloadHistoryItem(
    id: 'example-track',
    trackName: 'Example song',
    artistName: 'Example artist',
    albumName: 'Example album',
    filePath: '/music/example-track.flac',
    service: 'example-provider',
    downloadedAt: DateTime.utc(2026),
    trackNumber: 1,
    duration: 180,
  ),
);
final _track = _item.toTrack();

class _Collections extends LibraryCollectionsNotifier {
  var _batchCalls = 0;
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);

  CollectionTrackEntry get _entry => CollectionTrackEntry(
    key: trackCollectionKey(_track),
    track: _track,
    addedAt: DateTime.utc(2026),
  );

  void publishWishlist() => state = state.copyWith(wishlist: [_entry]);
  void publishLoved() => state = state.copyWith(loved: [_entry]);

  @override
  Future<({bool removed, int count})> toggleLovedTracks(
    Iterable<Track> tracks,
  ) async {
    _batchCalls++;
    final removed = state.loved.isNotEmpty;
    state = state.copyWith(loved: removed ? [] : [_entry]);
    return (removed: removed, count: 1);
  }
}

Widget _app(ProviderContainer container, Widget child) =>
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: child),
      ),
    );

void main() {
  testWidgets('Love All action keeps its add/remove feedback in Sistrum', (
    tester,
  ) async {
    final collections = _Collections();
    final container = ProviderContainer(
      overrides: [libraryCollectionsProvider.overrideWith(() => collections)],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      _app(
        container,
        Consumer(
          builder: (context, ref, _) => TextButton(
            onPressed: () => loveAllTracks(context, ref, [_track]),
            child: const Text('Apply'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Apply'));
    await tester.pumpAndSettle();
    final context = tester.element(find.text('Apply'));
    expect(
      find.text(context.l10n.snackbarAddedTracksToLoved(1)),
      findsOneWidget,
    );
    ScaffoldMessenger.of(context).removeCurrentSnackBar();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Apply'));
    await tester.pumpAndSettle();
    expect(
      find.text(context.l10n.snackbarRemovedTracksFromLoved(1)),
      findsOneWidget,
    );
    expect(collections._batchCalls, 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Love All isolates the library album page in Sistrum', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(430, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final collections = _Collections();
    final albumKey = LibraryDatabase.albumKeyFor(
      'Example album',
      null,
      'Example artist',
    );
    final container = ProviderContainer(
      overrides: [
        settingsProvider.overrideWith(FixedSettingsNotifier.new),
        libraryCollectionsProvider.overrideWith(() => collections),
        libraryAlbumItemsProvider(
          albumKey,
        ).overrideWith((ref) async => [_item]),
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream.value(null),
        ),
        playbackPlayingProvider.overrideWith((ref) => false),
        playbackLoadingProvider.overrideWith((ref) => false),
        downloadQueueLookupProvider.overrideWithValue(
          const DownloadQueueLookup.empty(),
        ),
        downloadHistoryVisibleBatchExistsProvider.overrideWith(
          (ref, request) => const <String>{},
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      _app(
        container,
        SelectionOverlayHost(child: AlbumScreen.library(albumKey: albumKey)),
      ),
    );
    await tester.pumpAndSettle();
    var pageBuilds = 0;
    var actionBuilds = 0;
    final previous = debugOnRebuildDirtyWidget;
    debugOnRebuildDirtyWidget = (element, builtOnce) {
      previous?.call(element, builtOnce);
      if (element.widget is AlbumScreen) pageBuilds++;
      if (element.widget is HeaderCircleButton) actionBuilds++;
    };
    addTearDown(() => debugOnRebuildDirtyWidget = previous);
    collections.publishWishlist();
    await tester.pumpAndSettle();
    expect(pageBuilds, 0);
    expect(actionBuilds, 0);
    collections.publishLoved();
    await tester.pumpAndSettle();
    expect(pageBuilds, 0);
    expect(actionBuilds, greaterThan(0));
    final context = tester.element(find.byType(HeaderCircleButton).first);
    expect(
      find.byTooltip(context.l10n.trackOptionRemoveFromLoved),
      findsWidgets,
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
}
