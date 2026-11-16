import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/screens/artist_screen.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/library_genre_screen.dart';
import 'package:sistrum/screens/playlist_screen.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/library_mixes.dart';
import 'package:sistrum/services/music_player_service.dart' show PlayableMedia;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

const _fresh = ArtistAlbum(
  id: 'fresh-key',
  name: 'Fresh Album',
  releaseDate: '2026-02-01',
  totalTracks: 10,
  coverUrl: 'https://img.example/fresh.jpg',
  albumType: 'album',
  artists: 'Dana Vale',
);

final _recentItem = UnifiedLibraryItem.fromLocalLibrary(
  LocalLibraryItem(
    id: 'recent-1',
    sourceId: 'music',
    trackName: 'Recent song',
    artistName: 'Dana Vale',
    albumName: 'Fresh Album',
    filePath: '/music/recent.flac',
    scannedAt: DateTime.utc(2026, 9, 1),
    format: 'flac',
  ),
);

/// One item in every Home shelf, so their order and taps can be checked.
List<Override> _shelfOverrides() => [
  playerUiEnabledProvider.overrideWith((ref) => false),
  playbackResumeProvider.overrideWith((ref) async => null),
  homeRecentlyPlayedProvider.overrideWith(
    (ref) async => [
      (
        track: ListeningTrack(
          key: '/music/recent.flac',
          title: 'Recent song',
          artist: 'Dana Vale',
          album: 'Recent Album',
        ),
        item: null,
      ),
    ],
  ),
  homeOnRepeatProvider.overrideWith(
    (ref) async => [
      (
        track: ListeningTrack(
          key: '/music/repeat.flac',
          title: 'Repeat song',
          artist: 'Dana Vale',
          album: '',
        ),
        item: null,
      ),
    ],
  ),
  homeRecentlyAddedProvider.overrideWith(
    (ref) async => (items: [_recentItem], trackCount: 1),
  ),
  homeNewReleasesProvider.overrideWith((ref) async => const [_fresh]),
  homeTopArtistsProvider.overrideWith(
    (ref) async => [(name: 'Dana Vale', cover: 'https://img.example/dana.jpg')],
  ),
  libraryCollectionsProvider.overrideWith(_Collections.new),
  libraryGenresProvider.overrideWith(
    (ref) async => const [
      LibraryGenreEntry(key: 'rock', name: 'Rock', trackCount: 2),
    ],
  ),
  libraryMixesProvider.overrideWith(
    (ref) async => const [
      LibraryMixView(
        mix: LibraryMix(
          kind: LibraryMixKind.favorites,
          key: 'favorites',
          label: 'Favorites',
          artists: [],
          songIds: [],
        ),
        items: [],
      ),
    ],
  ),
  libraryArtistOverviewProvider.overrideWith(
    (ref, artist) async => const LibraryArtistOverview([_fresh]),
  ),
  libraryArtistPopularTracksProvider.overrideWith((ref, artist) async => []),
  libraryArtistFeaturedTracksProvider.overrideWith((ref, artist) async => []),
  libraryAlbumItemsProvider.overrideWith((ref, key) async => const []),
  libraryPlaylistEntriesProvider.overrideWith((ref, id) async => const []),
  libraryGenreItemsProvider.overrideWith((ref, key) async => const []),
];

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    playlists: [
      UserPlaylistCollection(
        id: 'mix-1',
        name: 'Mixtape list',
        createdAt: DateTime.utc(2026, 1, 1),
        updatedAt: DateTime.utc(2026, 1, 1),
        tracks: const [],
        previewCover: 'https://img.example/list.jpg',
      ),
    ],
  );
}

class _CountingExtensions extends ExtensionNotifier {
  static int builds = 0;

  @override
  ExtensionState build() {
    builds++;
    return ExtensionState();
  }
}

Future<void> _pump(
  WidgetTester tester, {
  required List<Override> overrides,
  HomeTabMode mode = HomeTabMode.browse,
}) async {
  tester.view.physicalSize = const Size(430, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: overrides,
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: HomeTab(mode: mode)),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

double _top(WidgetTester tester, String text) =>
    tester.getTopLeft(find.text(text)).dy;

/// Taps the shelf tile labelled [label]; a tile below the fold is scrolled to.
Future<void> _openLibraryPage(WidgetTester tester, String label) async {
  final semantics = tester.ensureSemantics();
  final tile = find.bySemanticsLabel(label).first;
  await tester.ensureVisible(tile);
  await tester.pump();
  await tester.tap(tile);
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  semantics.dispose();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  _portedContinueListeningTests();

  const shelves = [
    'Top Picks for You',
    'Recently Played',
    'New Releases',
    'On repeat',
    'Your Top Artists',
    'Recently added',
    'Your Playlists',
    'Genres',
  ];

  testWidgets('Home lists the library shelves in the owner order', (
    tester,
  ) async {
    await _pump(tester, overrides: _shelfOverrides());
    for (final title in shelves) {
      expect(find.text(title), findsOneWidget, reason: title);
    }
    for (var i = 1; i < shelves.length; i++) {
      expect(
        _top(tester, shelves[i]),
        greaterThan(_top(tester, shelves[i - 1])),
        reason: '${shelves[i]} comes after ${shelves[i - 1]}',
      );
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('an empty library shows no shelf and the empty message', (
    tester,
  ) async {
    await _pump(
      tester,
      overrides: [
        playerUiEnabledProvider.overrideWith((ref) => false),
        playbackResumeProvider.overrideWith((ref) async => null),
        homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
        homeOnRepeatProvider.overrideWith((ref) async => const []),
        homeRecentlyAddedProvider.overrideWith(
          (ref) async => (items: <UnifiedLibraryItem>[], trackCount: 0),
        ),
        homeNewReleasesProvider.overrideWith((ref) async => const []),
        homeTopArtistsProvider.overrideWith((ref) async => const []),
        libraryCollectionsProvider.overrideWith(() => _EmptyCollections()),
        libraryGenresProvider.overrideWith((ref) async => const []),
        libraryMixesProvider.overrideWith((ref) async => const []),
      ],
    );
    for (final title in shelves) {
      expect(find.text(title), findsNothing, reason: title);
    }
    expect(find.text('Your music will appear here'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a New Releases tap opens the library album page', (
    tester,
  ) async {
    await _pump(tester, overrides: _shelfOverrides());
    await _openLibraryPage(tester, 'Fresh Album');
    expect(find.byType(AlbumScreen), findsOneWidget);
  });

  testWidgets('a Top Artists tap opens the library artist page', (
    tester,
  ) async {
    await _pump(tester, overrides: _shelfOverrides());
    await _openLibraryPage(tester, 'Dana Vale');
    expect(find.byType(ArtistScreen), findsOneWidget);
    // The artist header's cover palette gives up after five seconds.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 6));
  });

  testWidgets('a Your Playlists tap opens the library playlist page', (
    tester,
  ) async {
    await _pump(tester, overrides: _shelfOverrides());
    await _openLibraryPage(tester, 'Mixtape list');
    expect(find.byType(PlaylistScreen), findsOneWidget);
  });

  testWidgets('a Genres tap opens the genre songs page', (tester) async {
    await _pump(tester, overrides: _shelfOverrides());
    await _openLibraryPage(tester, 'Rock');
    expect(find.byType(LibraryGenreScreen), findsOneWidget);
  });

  testWidgets('Search lists library results only and reads no extension', (
    tester,
  ) async {
    _CountingExtensions.builds = 0;
    await _pump(
      tester,
      mode: HomeTabMode.search,
      overrides: [
        extensionProvider.overrideWith(_CountingExtensions.new),
        librarySearchProvider.overrideWith((ref, request) async {
          if (request.kind != LibrarySearchKind.artists) return const [];
          return const [
            LibrarySearchHit(
              kind: LibrarySearchKind.artists,
              id: 'dana',
              title: 'Dana Vale',
              cover: 'https://db.example/other.jpg',
            ),
          ];
        }),
        libraryArtistOverviewProvider.overrideWith(
          (ref, artist) async => const LibraryArtistOverview([_fresh]),
        ),
      ],
    );
    await tester.enterText(find.byType(TextField), 'dana');
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    expect(find.text('Dana Vale'), findsOneWidget);
    expect(_CountingExtensions.builds, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Search clear empties the field and its library results', (
    tester,
  ) async {
    await _pump(
      tester,
      mode: HomeTabMode.search,
      overrides: [
        librarySearchProvider.overrideWith((ref, request) async {
          if (request.kind != LibrarySearchKind.artists) return const [];
          return const [
            LibrarySearchHit(
              kind: LibrarySearchKind.artists,
              id: 'dana',
              title: 'Dana Vale',
            ),
          ];
        }),
        libraryArtistOverviewProvider.overrideWith(
          (ref, artist) async => const LibraryArtistOverview([]),
        ),
      ],
    );
    await tester.enterText(find.byType(TextField), 'dana');
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    expect(find.text('Dana Vale'), findsOneWidget);
    await tester.tap(find.byIcon(Icons.clear).first);
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    expect(find.text('Dana Vale'), findsNothing);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      isEmpty,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('a Search artist shows the image of the artist page', (
    tester,
  ) async {
    await _pump(
      tester,
      mode: HomeTabMode.search,
      overrides: [
        librarySearchProvider.overrideWith((ref, request) async {
          if (request.kind != LibrarySearchKind.artists) return const [];
          return const [
            LibrarySearchHit(
              kind: LibrarySearchKind.artists,
              id: 'dana',
              title: 'Dana Vale',
              cover: 'https://db.example/other.jpg',
            ),
          ];
        }),
        libraryArtistOverviewProvider.overrideWith(
          (ref, artist) async => const LibraryArtistOverview([_fresh]),
        ),
      ],
    );
    await tester.enterText(find.byType(TextField), 'dana');
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    final covers = tester
        .widgetList<LocalOrNetworkCoverImage>(
          find.byType(LocalOrNetworkCoverImage),
        )
        .map((cover) => cover.url);
    expect(covers, contains('https://img.example/fresh.jpg'));
    expect(covers, isNot(contains('https://db.example/other.jpg')));
  });
}

class _EmptyCollections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);
}

class _ResumeController extends MusicPlayerController {
  int resumes = 0;

  @override
  Future<bool> resumeSavedSession() async {
    resumes++;
    return true;
  }
}

/// Home without the library shelves: only the playback card and its inputs.
List<Override> _resumeOverrides({
  required PlayableMedia media,
  required Duration position,
  MusicPlayerController? controller,
}) => [
  playerUiEnabledProvider.overrideWith((ref) => true),
  currentMediaItemProvider.overrideWithBuild(
    (ref, notifier) => Stream.value(null),
  ),
  playbackResumeProvider.overrideWith(
    (ref) async => (media: media, position: position),
  ),
  if (controller != null)
    musicPlayerControllerProvider.overrideWithValue(controller),
  homeRecentlyAddedProvider.overrideWith(
    (ref) async => (items: <UnifiedLibraryItem>[], trackCount: 0),
  ),
  homeOnRepeatProvider.overrideWith((ref) async => const []),
  homeNewReleasesProvider.overrideWith((ref) async => const []),
  homeTopArtistsProvider.overrideWith((ref) async => const []),
  libraryCollectionsProvider.overrideWith(() => _EmptyCollections()),
  libraryGenresProvider.overrideWith((ref) async => const []),
  libraryMixesProvider.overrideWith((ref) async => const []),
];

const _lastSession = PlayableMedia(
  id: 'saved-song',
  source: '/saved.flac',
  title: 'Last session',
  artist: 'Saved artist',
  duration: Duration(minutes: 3),
);

void _portedContinueListeningTests() {
  testWidgets('Home keeps Resume without a player', (tester) async {
    final controller = _ResumeController();
    await _pump(
      tester,
      overrides: [
        ..._resumeOverrides(
          media: _lastSession,
          position: const Duration(seconds: 23),
          controller: controller,
        ),
        homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
      ],
    );
    expect(find.text('Continue listening'), findsOneWidget);
    expect(find.text('Last session'), findsOneWidget);
    expect(find.text('Saved artist'), findsOneWidget);
    expect(
      tester
          .widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator))
          .value,
      closeTo(23 / 180, 0.001),
    );
    await tester.tap(find.byTooltip('Play'));
    await tester.pump();
    expect(controller.resumes, 1);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  });

  for (final result in ['nothing played', 'load error']) {
    testWidgets('Home holds Continue listening until Recently Played settles '
        '($result)', (tester) async {
      final recentlyPlayed = Completer<List<HomeRepeatTrack>>();
      await _pump(
        tester,
        overrides: [
          ..._resumeOverrides(
            media: _lastSession,
            position: const Duration(seconds: 23),
          ),
          homeRecentlyPlayedProvider.overrideWith(
            (ref) => recentlyPlayed.future,
          ),
        ],
      );
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Continue listening'), findsNothing);

      if (result == 'load error') {
        recentlyPlayed.completeError(StateError('Unavailable'));
      } else {
        recentlyPlayed.complete(const <HomeRepeatTrack>[]);
      }
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Continue listening'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 1));
    });
  }

  testWidgets('Home without a feed still shows local playback', (tester) async {
    await _pump(
      tester,
      overrides: [
        ..._resumeOverrides(
          media: const PlayableMedia(
            id: 'local-song',
            source: '/local.flac',
            title: 'Local song',
            artist: 'Local artist',
          ),
          position: const Duration(seconds: 10),
        ),
        homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
      ],
    );
    expect(find.text('Continue listening'), findsOneWidget);
    expect(find.text('Local song'), findsOneWidget);
    expect(find.text('Shortcuts'), findsNothing);
    expect(find.text('No search providers yet'), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  });
}
