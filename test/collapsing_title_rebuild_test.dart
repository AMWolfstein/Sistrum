import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/screens/playlist_screen.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fixed_settings.dart';
import 'package:sistrum/providers/download_history_provider.dart';

final _tracks = [
  for (var i = 0; i < 30; i++)
    Track(
      id: 'example-track-$i',
      name: 'Example song $i',
      artistName: 'Example artist',
      albumName: 'Example album',
      duration: 180,
      trackNumber: i + 1,
    ),
];

final _albums = [
  for (var i = 0; i < 12; i++)
    ArtistAlbum(
      id: 'album-$i',
      name: 'Album $i',
      releaseDate: '2020',
      totalTracks: 1,
      albumType: i.isEven ? 'album' : 'single',
      artists: 'Example artist',
    ),
];

final _downloaded = [
  for (var i = 0; i < 30; i++)
    DownloadHistoryItem(
      id: 'example-download-$i',
      trackName: 'Example song $i',
      artistName: 'Example artist',
      albumName: 'Example album',
      filePath: '/music/example-$i.flac',
      service: 'example',
      downloadedAt: DateTime(2026),
      trackNumber: i + 1,
    ),
];

final _local = [
  for (var i = 0; i < 30; i++)
    LocalLibraryItem(
      id: 'example-local-$i',
      trackName: 'Example song $i',
      artistName: 'Example artist',
      albumName: 'Example album',
      filePath: '/music/example-$i.flac',
      scannedAt: DateTime(2026),
      trackNumber: i + 1,
    ),
];

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    playlists: [
      UserPlaylistCollection(
        id: 'example-playlist',
        name: 'Example playlist',
        createdAt: DateTime(2026),
        updatedAt: DateTime(2026),
        tracks: [
          for (final track in _tracks)
            CollectionTrackEntry(
              key: track.id,
              track: track,
              addedAt: DateTime(2026),
            ),
        ],
      ),
    ],
  );

  @override
  Future<void> ensurePlaylistLoaded(String playlistId) async {}
}

class _Library extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}

/// Opacity of every faded title in the pinned toolbars.
List<double> _titleOpacities(WidgetTester tester) => [
  for (final fade in tester.widgetList<FadeTransition>(
    find.descendant(
      of: find.byType(NavigationToolbar),
      matching: find.byType(FadeTransition),
    ),
  ))
    fade.opacity.value,
];

void main() {
  for (final page in [
    'artist',
    'downloaded album',
    'local album',
    'library playlist',
  ]) {
    testWidgets('$page title flip leaves the page unbuilt in '
        'Sistrum', (tester) async {
      tester.view.physicalSize = const Size(430, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({});
      const channel = MethodChannel('com.amwolfstein.sistrum/backend');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final container = ProviderContainer(
        overrides: [
          settingsProvider.overrideWith(FixedSettingsNotifier.new),
          libraryCollectionsProvider.overrideWith(_Collections.new),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(null),
          ),
          libraryAlbumItemsProvider(
            LibraryDatabase.albumKeyFor(
              'Example album',
              null,
              'Example artist',
            ),
          ).overrideWith(
            (ref) async => page == 'local album'
                ? [
                    for (final track in _local)
                      UnifiedLibraryItem.fromLocalLibrary(track),
                  ]
                : [
                    for (final track in _downloaded)
                      UnifiedLibraryItem.fromDownloadHistory(track),
                  ],
          ),
          libraryPlaylistEntriesProvider('example-playlist').overrideWith(
            (ref) async => [
              for (final track in _tracks)
                CollectionTrackEntry(
                  key: track.id,
                  track: track,
                  addedAt: DateTime(2026),
                ),
            ],
          ),
          localLibraryProvider.overrideWith(_Library.new),
          downloadHistoryProvider.overrideWith(_History.new),
          localLibraryCoverProvider.overrideWith((ref, request) async => null),
          localLibraryFirstCoverProvider.overrideWith(
            (ref, request) async => null,
          ),
          libraryArtistOverviewProvider(
            'Example artist',
          ).overrideWith((ref) async => LibraryArtistOverview(_albums)),
          libraryArtistPopularTracksProvider(
            'Example artist',
          ).overrideWith((ref) async => _tracks.take(10).toList()),
          libraryArtistFeaturedTracksProvider(
            'Example artist',
          ).overrideWith((ref) async => const <Track>[]),
        ],
      );
      addTearDown(container.dispose);
      final Widget screen = switch (page) {
        'artist' => const ArtistScreen(artistName: 'Example artist'),
        'downloaded album' || 'local album' => AlbumScreen.library(
          albumKey: LibraryDatabase.albumKeyFor(
            'Example album',
            null,
            'Example artist',
          ),
        ),
        _ => const PlaylistScreen.libraryPlaylist(
          libraryPlaylistId: 'example-playlist',
        ),
      };
      // The Mornye folder header always shows its toolbar title.
      final fades = !(page == 'library playlist');
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: screen,
          ),
        ),
      );
      await tester.pumpAndSettle();

      var pageBuilds = 0;
      final previous = debugOnRebuildDirtyWidget;
      debugOnRebuildDirtyWidget = (element, builtOnce) {
        previous?.call(element, builtOnce);
        if (element.widget.runtimeType == screen.runtimeType) pageBuilds++;
      };
      addTearDown(() => debugOnRebuildDirtyWidget = previous);

      final position = tester
          .state<ScrollableState>(find.byType(Scrollable).first)
          .position;
      final hidden = _titleOpacities(tester);
      final frames = <List<double>>[];
      Future<void> scrollTo(double offset) async {
        position.jumpTo(offset);
        await tester.pump();
        for (var frame = 0; frame < 20; frame++) {
          frames.add(_titleOpacities(tester));
          await tester.pump(const Duration(milliseconds: 16));
        }
      }

      await scrollTo(700);
      final shown = _titleOpacities(tester);
      await scrollTo(0);

      expect(pageBuilds, 0);
      expect(_titleOpacities(tester), hidden);
      if (fades) {
        expect(shown, isNot(equals(hidden)));
        // The title fades through intermediate frames both ways.
        expect(
          frames.where(
            (opacities) =>
                opacities.any((opacity) => opacity > 0 && opacity < 1),
          ),
          isNotEmpty,
        );
      }
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    });
  }
}
