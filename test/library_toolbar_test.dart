import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_mix_screen.dart';
import 'package:sistrum/screens/library_tracks_folder_screen.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/services/playable_media.dart' show PlayableMedia;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/library_directory_view.dart';
import 'package:sistrum/widgets/song_row_tap.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

import 'support/page_back.dart';

class _Settings extends SettingsNotifier {
  _Settings(this.initial);

  final AppSettings? initial;

  // The Rewind banner animates for as long as it is shown; keep it off so
  // the page settles.
  @override
  AppSettings build() =>
      initial ??
      const AppSettings(
        defaultLibraryView: 'all',
        historyViewMode: 'list',
        listeningStatisticsEnabled: false,
      );
}

class _Local extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState(totalCount: 1);
}

class _Collections extends LibraryCollectionsNotifier {
  _Collections(this.playlists);

  final List<UserPlaylistCollection> playlists;

  @override
  LibraryCollectionsState build() =>
      LibraryCollectionsState(isLoaded: true, playlists: playlists);
}

class _Playback extends PlaybackController {
  final queues = <List<String>>[];
  final starts = <int>[];

  @override
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
    required String externalPath,
  }) async {
    queues.add([for (final media in queue) media.source]);
    starts.add(startIndex);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final queries = <String>[];
  final albumRows = <List<Object?>>[];

  setUp(() {
    queries.clear();
    albumRows.clear();
    SharedPreferences.setMockInitialValues({});
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const paths = MethodChannel('plugins.flutter.io/path_provider');
    messenger.setMockMethodCallHandler(paths, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
    // Most tests use an empty database; album order tests supply page rows.
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
      queries.add(sql);
      final offset = ((args['arguments'] as List?) ?? const []).lastOrNull;
      if (sql.trimLeft().startsWith('SELECT *') &&
          sql.contains("'local' AS queue_source") &&
          !sql.contains('sample_file_path')) {
        // One scanned song on the first page, as the Songs page reads it.
        return {
          'columns': [
            'queue_source',
            'id',
            'track_name',
            'artist_name',
            'album_name',
            'file_path',
            'scanned_at',
            'track_number',
          ],
          'rows': offset == 0
              ? <List<Object?>>[
                  [
                    'local',
                    'song-0',
                    'Song 0',
                    'Artist',
                    'Album',
                    '/music/song-0.flac',
                    '2026-01-01T00:00:00.000Z',
                    1,
                  ],
                ]
              : const <List<Object?>>[],
        };
      }
      if (sql.toLowerCase().contains('pragma user_version')) {
        return {
          'columns': ['user_version'],
          'rows': [
            [versions[args['id']]],
          ],
        };
      }
      if (sql.trimLeft().startsWith('SELECT *') &&
          sql.contains('sample_file_path') &&
          sql.contains('AS queue_source')) {
        return {
          'columns': [
            'queue_source',
            'album_key',
            'album_name',
            'artist_name',
            'track_count',
            'sort_added',
            'sample_file_path',
            'sort_release',
          ],
          'rows': albumRows,
        };
      }
      return {'columns': <String>[], 'rows': <List<Object?>>[]};
    });
    addTearDown(() => messenger.setMockMethodCallHandler(sqlite, null));
  });
  tearDown(() => debugDesktopPlatformOverride = null);

  Future<void> pumpLibrary(
    WidgetTester tester, {
    bool desktop = false,
    bool reduceMotion = false,
    bool libraryRoot = false,
    List<UserPlaylistCollection> playlists = const [],
    String? librarySection,
    List<Override> overrides = const [],
    Size size = const Size(1280, 900),
  }) async {
    final preferences = await SharedPreferences.getInstance();
    debugDesktopPlatformOverride = desktop;
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          // These tests play songs with a tap on the row.
          librarySongTapPlaysProvider.overrideWithValue(true),
          settingsProvider.overrideWith(
            () => _Settings(
              hasPersistedAppSettings(preferences)
                  ? loadBootstrapSettings(preferences)
                  : null,
            ),
          ),
          localLibraryProvider.overrideWith(_Local.new),
          libraryCollectionsProvider.overrideWith(
            () => _Collections(playlists),
          ),
          libraryCountsProvider.overrideWith(
            (ref, request) async => const QueueLibraryCounts(
              allTrackCount: 1,
              albumCount: 1,
              singleTrackCount: 0,
            ),
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
          // Mornye opens Songs as a section page from its Library root.
          home: Scaffold(
            body: QueueTab(
              librarySection: libraryRoot ? null : (librarySection ?? 'all'),
            ),
          ),
        ),
      ),
    );
    // A loading indicator spins while the database page is pending, so the
    // page never settles; pump for a bounded time instead.
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(QueueTab)));

  for (final desktop in [false, true]) {
    for (final trackCount in <int?>[null, 0, 143]) {
      testWidgets(
        'opens Playlists without downloaded or local songs '
        '(desktop: $desktop, playlist tracks: $trackCount)',
        (tester) async {
          final now = DateTime(2026);
          await pumpLibrary(
            tester,
            desktop: desktop,
            libraryRoot: true,
            playlists: [
              if (trackCount != null)
                UserPlaylistCollection(
                  id: 'remote-playlist',
                  name: 'Example saved playlist',
                  createdAt: now,
                  updatedAt: now,
                  tracks: [
                    for (var i = 0; i < trackCount; i++)
                      CollectionTrackEntry(
                        key: 'example:$i',
                        addedAt: now,
                        track: Track(
                          id: 'example:$i',
                          name: 'Remote song $i',
                          artistName: 'Example Artist',
                          albumName: 'Example Album',
                          duration: 180,
                          source: 'example',
                        ),
                      ),
                  ],
                ),
            ],
          );
          final l10n = l10nOf(tester);
          final playlistsRow = find.text(l10n.searchPlaylists);
          expect(playlistsRow.hitTestable(), findsOneWidget);
          await tester.tap(playlistsRow);
          // Let the spatial spring finish before clicking inside the page.
          for (var i = 0; i < 40; i++) {
            await tester.pump(const Duration(milliseconds: 50));
          }
          if (trackCount == null) {
            // Mornye's empty Playlists page offers Home, not Create playlist.
            expect(find.text(l10n.collectionNoPlaylistsYet), findsOneWidget);
            expect(find.text(l10n.navHome), findsOneWidget);
            expect(find.text(l10n.collectionCreatePlaylist), findsNothing);
          } else {
            expect(find.text(l10n.collectionCreatePlaylist), findsOneWidget);
          }
          if (trackCount != null) {
            expect(find.byType(AppSearchField), findsOneWidget);
            expect(find.text('Example saved playlist'), findsOneWidget);
            expect(find.text(l10n.itemCount(trackCount)), findsOneWidget);
            await tester.tap(find.text('Example saved playlist'));
            for (var i = 0; i < 10; i++) {
              await tester.pump(const Duration(milliseconds: 50));
            }
            expect(
              tester
                  .widget<LibraryTracksFolderScreen>(
                    find.byType(LibraryTracksFolderScreen),
                  )
                  .playlistId,
              'remote-playlist',
            );
            if (trackCount > 0) {
              expect(find.text('Remote song 0'), findsOneWidget);
            }
          }
          expect(tester.takeException(), isNull);
        },
        variant: TargetPlatformVariant({
          desktop ? TargetPlatform.windows : TargetPlatform.android,
        }),
      );
    }
  }

  for (final desktop in [false, true]) {
    testWidgets('title sort survives a fresh Library and settings scope '
        '(desktop: $desktop)', (tester) async {
      await pumpLibrary(tester, desktop: desktop);
      final l10n = l10nOf(tester);
      await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      final choice = find.text(l10n.searchSortTitleAZ);
      await tester.ensureVisible(choice);
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(choice);
      await tester.ensureVisible(find.text(l10n.libraryFilterApply));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.tap(find.text(l10n.libraryFilterApply));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      final preferences = await SharedPreferences.getInstance();
      expect(loadBootstrapSettings(preferences).historySortMode, 'a-z');
      await tester.pumpWidget(const SizedBox.shrink());
      queries.clear();
      await pumpLibrary(tester, desktop: desktop);
      final container = ProviderScope.containerOf(
        tester.element(find.byType(QueueTab)),
      );
      expect(container.read(settingsProvider).historySortMode, 'a-z');
      expect(
        queries.any((sql) => sql.contains('ORDER BY sort_track ASC')),
        isTrue,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('size sort applies from the Library menu '
        '(desktop: $desktop)', (tester) async {
      await pumpLibrary(tester, desktop: desktop);
      final l10n = l10nOf(tester);
      // The search field scrolls with the page over its plain background.
      expect(
        find.descendant(
          of: find.byType(AppSearchField),
          matching: find.byType(BackdropFilter),
        ),
        findsNothing,
      );
      await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      await tester.ensureVisible(find.text(l10n.libraryFilterSortSizeLargest));
      expect(find.text(l10n.libraryFilterSortSizeLargest), findsOneWidget);
      expect(find.text(l10n.libraryFilterSortSizeSmallest), findsOneWidget);
      await tester.tap(find.text(l10n.libraryFilterSortSizeLargest));
      await tester.ensureVisible(find.text(l10n.libraryFilterApply));
      await tester.tap(find.text(l10n.libraryFilterApply));
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      expect(queries.any((sql) => sql.contains('sort_size DESC')), isTrue);
      expect(find.text(l10n.libraryFilterSortSizeSmallest), findsNothing);
      expect(tester.takeException(), isNull);
    });

    for (final newest in [false, true]) {
      testWidgets('year sort applies and restores from the Library menu '
          '(desktop: $desktop, newest: $newest)', (tester) async {
        await pumpLibrary(tester, desktop: desktop);
        final l10n = l10nOf(tester);
        final label = newest
            ? l10n.libraryFilterSortYearDesc
            : l10n.libraryFilterSortYearAsc;
        final mode = newest ? 'year-desc' : 'year-asc';
        await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 50));
        }
        final choice = find.text(label);
        await tester.ensureVisible(choice);
        await tester.pump(const Duration(milliseconds: 300));
        await tester.tap(choice);
        await tester.ensureVisible(find.text(l10n.libraryFilterApply));
        await tester.pump(const Duration(milliseconds: 300));
        await tester.tap(find.text(l10n.libraryFilterApply));
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 50));
        }
        final preferences = await SharedPreferences.getInstance();
        expect(loadBootstrapSettings(preferences).historySortMode, mode);
        expect(
          queries.any(
            (sql) =>
                sql.contains('THEN CAST(') &&
                sql.contains('sort_release ${newest ? 'DESC' : 'ASC'}'),
          ),
          isTrue,
        );
        await tester.pumpWidget(const SizedBox.shrink());
        await pumpLibrary(tester, desktop: desktop);
        final container = ProviderScope.containerOf(
          tester.element(find.byType(QueueTab)),
        );
        expect(container.read(settingsProvider).historySortMode, mode);
        expect(tester.takeException(), isNull);
      });
    }
  }

  testWidgets('the Folders row browses the Library by folder', (tester) async {
    await pumpLibrary(tester, libraryRoot: true);
    final l10n = l10nOf(tester);

    await tester.tap(find.text(l10n.libraryFolders).hitTestable());
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    expect(find.byType(LibraryDirectoryView), findsOneWidget);
    // The mocked database has no songs.
    expect(find.text(l10n.libraryFoldersEmptyTitle), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('the Genres row lists genres and opens one; every category is '
      'reachable at phone width', (tester) async {
    const genre = LibraryBrowseEntry(
      source: '',
      key: 'rock',
      name: 'Rock',
      artist: '',
      samplePath: '',
      trackCount: 2,
    );
    await pumpLibrary(
      tester,
      libraryRoot: true,
      size: const Size(390, 844),
      overrides: [
        libraryGenresProvider.overrideWith((ref) async => const [genre]),
        libraryGenreSongsProvider.overrideWith(
          (ref, key) async => [
            for (final id in ['a', 'b'])
              UnifiedLibraryItem.fromLocalLibrary(
                LocalLibraryItem(
                  id: id,
                  trackName: 'Rock song $id',
                  artistName: 'Artist',
                  albumName: 'Album',
                  filePath: '/music/$id.flac',
                  scannedAt: DateTime(2026, 11, 16),
                ),
              ),
          ],
        ),
      ],
    );
    final l10n = l10nOf(tester);
    for (final label in [
      l10n.searchPlaylists,
      l10n.searchArtists,
      l10n.searchAlbums,
      l10n.searchSongs,
      l10n.libraryGenres,
      l10n.libraryFolders,
    ]) {
      expect(find.text(label).hitTestable(), findsOneWidget);
    }
    await tester.tap(find.text(l10n.libraryGenres).hitTestable());
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(find.text('Rock'), findsOneWidget);
    expect(find.text(l10n.tracksCount(2)), findsOneWidget);

    await tester.tap(find.text('Rock'));
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(find.byType(LibraryMixScreen), findsOneWidget);
    expect(find.text('Rock song a'), findsOneWidget);
    expect(find.text('Rock song b'), findsOneWidget);
    // Back to the Genres page, then to the Library root.
    await pageBack(tester);
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    await pageBack(tester);
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    // The last category is still reachable at phone width.
    await tester.tap(find.text(l10n.libraryFolders).hitTestable());
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(find.byType(LibraryDirectoryView), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('the Genres row explains an empty Library', (tester) async {
    await pumpLibrary(tester, libraryRoot: true);
    final l10n = l10nOf(tester);

    await tester.tap(find.text(l10n.libraryGenres).hitTestable());
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    // The mocked database has no songs.
    expect(find.text(l10n.libraryEmptyCollection), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('a searched song plays on into the Songs list in its sort '
      'and filters', (tester) async {
    UnifiedLibraryItem song(String id) => UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem(
        id: id,
        trackName: 'Track $id',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/$id.flac',
        scannedAt: DateTime(2026),
      ),
    );
    final lists = <LibrarySongListRequest>[];
    final player = _Playback();
    await pumpLibrary(
      tester,
      overrides: [
        playbackProvider.overrideWith(() => player),
        librarySearchProvider.overrideWith(
          (ref, request) async => request.kind == LibrarySearchKind.songs
              ? [
                  const LibrarySearchHit(
                    kind: LibrarySearchKind.songs,
                    id: 'b',
                    title: 'Track b',
                    source: 'local',
                  ),
                ]
              : [],
        ),
        librarySearchTrackProvider.overrideWith(
          (ref, key) async => song(key.id),
        ),
        librarySongListProvider.overrideWith((ref, request) async {
          lists.add(request);
          return [
            for (final id in ['a', 'b', 'c']) song(id),
          ];
        }),
      ],
    );
    final l10n = l10nOf(tester);
    final container = ProviderScope.containerOf(
      tester.element(find.byType(QueueTab)),
    );
    container.read(settingsProvider.notifier).setHistorySortMode('oldest');
    await tester.tap(find.text(l10n.libraryFilterTitle).hitTestable());
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    await tester.tap(find.text(l10n.libraryFilterQualityHiRes));
    await tester.ensureVisible(find.text(l10n.libraryFilterApply));
    await tester.tap(find.text(l10n.libraryFilterApply));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    await tester.enterText(
      find.descendant(
        of: find.byType(AppSearchField),
        matching: find.byType(EditableText),
      ),
      'Track',
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(find.text('Track b'), findsOneWidget);
    expect(lists, isEmpty);
    // A tap on the song plays it, as its Play button did.
    await tester.tap(find.text('Track b').hitTestable());
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    expect(lists, [
      (
        filterMode: 'all',
        sortMode: 'oldest',
        source: null,
        quality: 'hires',
        format: null,
        metadata: null,
        includeLocal: container.read(settingsProvider).localLibraryEnabled,
      ),
    ]);
    expect(player.queues, [
      ['/music/a.flac', '/music/b.flac', '/music/c.flac'],
    ]);
    expect(player.starts, [1]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Mornye keeps its filter button', (tester) async {
    await pumpLibrary(tester);
    final l10n = l10nOf(tester);

    expect(find.text(l10n.libraryFilterTitle), findsOneWidget);
  });
}
