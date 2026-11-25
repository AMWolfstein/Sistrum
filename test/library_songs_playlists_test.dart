import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/playable_media.dart' show PlayableMedia;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

import 'support/tooltip_finder.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

/// A Library holding [totalCount] songs.
class _Local extends LocalLibraryNotifier {
  _Local(this.totalCount);

  final int totalCount;

  @override
  LocalLibraryState build() => LocalLibraryState(totalCount: totalCount);
}

class _Collections extends LibraryCollectionsNotifier {
  _Collections(this.initial);

  final LibraryCollectionsState initial;

  @override
  LibraryCollectionsState build() => initial;
}

final _lovedEntries = [
  for (final id in ['a', 'b'])
    CollectionTrackEntry(
      key: id,
      addedAt: DateTime(2026),
      track: Track(
        id: id,
        name: 'Loved $id',
        artistName: 'Artist',
        albumName: 'Album',
        duration: 180,
      ),
    ),
];

LibraryCollectionsState _collections({bool withPlaylist = false}) =>
    LibraryCollectionsState(
      isLoaded: true,
      loved: _lovedEntries,
      playlists: [
        if (withPlaylist)
          UserPlaylistCollection(
            id: 'mix',
            name: 'My mix',
            createdAt: DateTime(2026),
            updatedAt: DateTime(2026),
            tracks: _lovedEntries,
          ),
      ],
    );

class _Playback extends PlaybackController {
  final queues = <List<String>>[];
  final starts = <int>[];

  @override
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
  }) async {
    queues.add([for (final media in queue) media.source]);
    starts.add(startIndex);
  }
}

class _Player extends MusicPlayerController {
  final shuffles = <bool>[];

  @override
  Future<void> setShuffle(bool enabled) async => shuffles.add(enabled);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const paths = MethodChannel('plugins.flutter.io/path_provider');
    messenger.setMockMethodCallHandler(paths, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
    // An empty database: the Library shows the in-memory History snapshot.
    const sqlite = MethodChannel('com.tekartik.sqflite');
    final versions = <int, int>{};
    messenger.setMockMethodCallHandler(sqlite, (call) async {
      final args = (call.arguments as Map?) ?? {};
      if (call.method == 'openDatabase') {
        final id = versions.length + 1;
        versions[id] = (args['path'] as String).endsWith('history.db')
            ? HistoryDatabase.schemaVersion
            : LibraryDatabase.schemaVersion;
        return {'id': id};
      }
      final sql = args['sql'] as String? ?? '';
      if (call.method == 'execute' && sql.contains('CREATE VIRTUAL TABLE')) {
        throw PlatformException(code: 'sqlite_error', message: 'no fts5');
      }
      if (call.method != 'query') return null;
      if (sql.toLowerCase().contains('pragma user_version')) {
        return {
          'columns': ['user_version'],
          'rows': [
            [versions[args['id']]],
          ],
        };
      }
      return {'columns': <String>[], 'rows': <List<Object?>>[]};
    });
    addTearDown(() => messenger.setMockMethodCallHandler(sqlite, null));
  });
  tearDown(() => debugDesktopPlatformOverride = null);

  Future<void> settle(WidgetTester tester, [int frames = 20]) async {
    // A loading indicator spins while the database page is pending, so the
    // page never settles; pump for a bounded time instead.
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  Future<void> pumpLibrary(
    WidgetTester tester, {
    bool desktop = false,
    bool reduceMotion = false,
    int songCount = 3,
    int? totalCount,
    String? librarySection,
    List<Override> overrides = const [],
    LibraryCollectionsState? collections,
  }) async {
    debugDesktopPlatformOverride = desktop;
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          localLibraryProvider.overrideWith(
            () => _Local(totalCount ?? songCount),
          ),
          queueLibraryTrackPageReaderProvider.overrideWithValue(
            (query) async => QueueLibraryDbPage(
              rows: [
                for (var index = 0; index < songCount; index++)
                  {
                    'source': 'local',
                    'item': LocalLibraryItem(
                      id: 'song-$index',
                      trackName: 'Song $index',
                      artistName: 'Artist',
                      albumName: 'Album $index',
                      filePath: '/music/song-$index.flac',
                      scannedAt: DateTime(2026),
                    ).toJson(),
                  },
              ],
              nextCursor: null,
            ),
          ),
          libraryCountsProvider.overrideWith(
            (ref, request) async => QueueLibraryCounts(
              allTrackCount: totalCount ?? songCount,
              albumCount: 0,
              singleTrackCount: totalCount ?? songCount,
            ),
          ),
          libraryCollectionsProvider.overrideWith(
            () => _Collections(collections ?? _collections()),
          ),
          ...overrides,
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(disableAnimations: reduceMotion),
            child: child!,
          ),
          // Songs opens as a section page from the Library root.
          home: Scaffold(
            body: QueueTab(librarySection: librarySection ?? 'all'),
          ),
        ),
      ),
    );
    await settle(tester);
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(QueueTab)));

  // Desktop's Shuffle is a round button named by its tooltip.
  Finder shuffleButton(WidgetTester tester) {
    final label = l10nOf(tester).actionShuffle;
    return (isDesktopPlatform ? find.byTooltip(label) : find.text(label))
        .hitTestable();
  }

  Finder playButton(WidgetTester tester) => find.descendant(
    of: find.byType(AlbumPlayActions),
    matching: find.text(l10nOf(tester).tooltipPlay),
  );

  for (final desktop in [false, true]) {
    testWidgets('Songs shows Play and Shuffle side by side at the top '
        '(desktop: $desktop)', (tester) async {
      await pumpLibrary(tester, desktop: desktop);
      final play = playButton(tester);
      final shuffle = shuffleButton(tester);
      expect(play, findsOneWidget);
      expect(shuffle, findsOneWidget);
      final playCenter = tester.getCenter(play);
      final shuffleCenter = tester.getCenter(shuffle);
      expect((playCenter.dy - shuffleCenter.dy).abs(), lessThan(2));
      expect(
        playCenter.dy,
        lessThan(tester.getTopLeft(find.text('Song 0').hitTestable()).dy),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('Shuffle queues the listed songs with shuffle on '
        '(desktop: $desktop)', (tester) async {
      final lists = <LibrarySongListRequest>[];
      final playback = _Playback();
      final player = _Player();
      await pumpLibrary(
        tester,
        desktop: desktop,
        overrides: [
          playbackProvider.overrideWith(() => playback),
          musicPlayerControllerProvider.overrideWithValue(player),
          librarySongListProvider.overrideWith((ref, request) async {
            lists.add(request);
            return const [];
          }),
        ],
      );
      await tester.tap(shuffleButton(tester));
      await settle(tester, 10);

      // The page loaded every song, so nothing is read again.
      expect(lists, isEmpty);
      expect(player.shuffles, [true]);
      expect(playback.queues, [
        ['/music/song-0.flac', '/music/song-1.flac', '/music/song-2.flac'],
      ]);
      expect(playback.starts.single, inInclusiveRange(0, 2));
      expect(tester.takeException(), isNull);
    });

    testWidgets('Play queues the songs in their order and leaves shuffle '
        'alone (desktop: $desktop)', (tester) async {
      final playback = _Playback();
      final player = _Player();
      await pumpLibrary(
        tester,
        desktop: desktop,
        overrides: [
          playbackProvider.overrideWith(() => playback),
          musicPlayerControllerProvider.overrideWithValue(player),
        ],
      );
      await tester.tap(playButton(tester));
      await settle(tester, 10);

      expect(player.shuffles, isEmpty);
      expect(playback.queues, [
        ['/music/song-0.flac', '/music/song-1.flac', '/music/song-2.flac'],
      ]);
      expect(playback.starts, [0]);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('selecting songs hides Play and Shuffle with the tools', (
    tester,
  ) async {
    await pumpLibrary(tester);
    expect(playButton(tester), findsOneWidget);
    expect(shuffleButton(tester), findsOneWidget);

    await tester.longPress(find.text('Song 1').hitTestable());
    await settle(tester, 10);
    expect(playButton(tester), findsNothing);
    expect(shuffleButton(tester), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Songs lists songs only, with no filter or add-playlist button', (
    tester,
  ) async {
    await pumpLibrary(tester, collections: _collections(withPlaylist: true));
    final l10n = l10nOf(tester);
    expect(find.text('My mix'), findsNothing);
    expect(find.text(l10n.collectionLoved), findsNothing);
    expect(findByTooltip('Filters'), findsNothing);
    expect(find.text(l10n.collectionCreatePlaylist), findsNothing);
    expect(find.byIcon(Icons.filter_list), findsNothing);
    expect(find.text('Song 0'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Loved is a playlist entry in Playlists and not in Songs', (
    tester,
  ) async {
    await pumpLibrary(
      tester,
      librarySection: 'playlists',
      collections: _collections(withPlaylist: true),
    );
    final l10n = l10nOf(tester);
    expect(find.text(l10n.collectionLoved), findsOneWidget);
    expect(find.text('My mix'), findsOneWidget);
    expect(find.text(l10n.collectionCreatePlaylist), findsOneWidget);
    expect(find.byIcon(Icons.favorite), findsOneWidget);
    expect(tester.takeException(), isNull);

    await pumpLibrary(tester, collections: _collections(withPlaylist: true));
    expect(find.text(l10n.collectionLoved), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Play and Shuffle are absent on the Playlists section', (
    tester,
  ) async {
    await pumpLibrary(tester, librarySection: 'playlists');
    expect(playButton(tester), findsNothing);
    expect(shuffleButton(tester), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an empty Library shows no Play or Shuffle', (tester) async {
    await pumpLibrary(tester, songCount: 0);
    expect(playButton(tester), findsNothing);
    expect(shuffleButton(tester), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the Library overview lists six categories, with no search '
      'or recently added', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [settingsProvider.overrideWith(_Settings.new)],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: MornyeLibraryScreen(onOpenSection: (_) {})),
        ),
      ),
    );
    await settle(tester, 10);
    final l10n = AppLocalizations.of(
      tester.element(find.byType(MornyeLibraryScreen)),
    );
    expect(find.text(l10n.searchPlaylists), findsOneWidget);
    expect(find.text(l10n.searchArtists), findsOneWidget);
    expect(find.text(l10n.searchAlbums), findsOneWidget);
    expect(find.text(l10n.searchSongs), findsOneWidget);
    expect(find.text(l10n.libraryGenres), findsOneWidget);
    expect(find.text(l10n.libraryFolders), findsOneWidget);
    expect(find.text('Recently Added'), findsNothing);
    expect(find.byIcon(Icons.search), findsNothing);
    expect(find.byIcon(Icons.folder_open_outlined), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an empty Library shows no Shuffle', (tester) async {
    await pumpLibrary(tester, songCount: 0);
    expect(shuffleButton(tester), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
