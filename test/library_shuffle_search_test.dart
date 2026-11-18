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
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:material_3_expressive/material_3_expressive.dart'
    show M3EButton;
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/tool_button_group.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

import 'support/tooltip_finder.dart';

class _Settings extends SettingsNotifier {
  _Settings(this.viewMode);

  final String viewMode;

  @override
  AppSettings build() => AppSettings(
    defaultLibraryView: 'all',
    historyViewMode: viewMode,
    listeningStatisticsEnabled: false,
  );
}

/// A Library holding [totalCount] songs.
class _Local extends LocalLibraryNotifier {
  _Local(this.totalCount);

  final int totalCount;

  @override
  LocalLibraryState build() => LocalLibraryState(totalCount: totalCount);
}

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);
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
    bool mornye = false,
    bool reduceMotion = false,
    String viewMode = 'list',
    int songCount = 3,
    int? totalCount,
    String? librarySection,
    List<Override> overrides = const [],
  }) async {
    debugDesktopPlatformOverride = desktop;
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(() => _Settings(viewMode)),
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
          libraryCollectionsProvider.overrideWith(_Collections.new),
          ...overrides,
        ],
        child: MaterialApp(
          theme: mornye
              ? MornyeTheme.build(Brightness.light)
              : AppTheme.light(),
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
              librarySection: librarySection ?? (mornye ? 'all' : null),
            ),
          ),
        ),
      ),
    );
    await settle(tester);
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(QueueTab)));

  Finder shuffleButton(WidgetTester tester, {required bool mornye}) {
    final l10n = l10nOf(tester);
    // Material's Shuffle is a segment of the tool group.
    return (mornye
            ? find.text(l10n.actionShuffle)
            : find.byWidgetPredicate(
                (widget) =>
                    widget is M3EButton && widget.tooltip == l10n.actionShuffle,
              ))
        .hitTestable();
  }

  Future<void> openTab(WidgetTester tester, String label) async {
    await tester.tap(
      find.descendant(of: find.byType(TabBar), matching: find.text(label)),
    );
    await settle(tester, 40);
  }

  for (final desktop in [false, true]) {
    for (final mornye in [false, true]) {
      final views = mornye ? ['list'] : ['list', 'grid'];
      for (final viewMode in views) {
        testWidgets('Songs offers Shuffle (desktop: $desktop, '
            'Mornye: $mornye, view: $viewMode)', (tester) async {
          await pumpLibrary(
            tester,
            desktop: desktop,
            mornye: mornye,
            viewMode: viewMode,
          );
          final shuffle = shuffleButton(tester, mornye: mornye);
          expect(shuffle, findsOneWidget);
          if (!mornye) {
            // The first of the page's tools, in their group and its colours,
            // with the group's 48dp target.
            final group = find.ancestor(
              of: shuffle,
              matching: find.byType(ToolButtonGroup),
            );
            expect(group, findsOneWidget);
            expect(tester.getSize(group).height, greaterThanOrEqualTo(48));
            final filter = find.descendant(
              of: group,
              matching: find.text(l10nOf(tester).libraryFilterTitle),
            );
            expect(
              tester.getTopLeft(shuffle).dx,
              lessThan(tester.getTopLeft(filter.first).dx),
            );
          }
          expect(tester.takeException(), isNull);
        });
      }

      testWidgets('Shuffle queues the listed songs with shuffle on '
          '(desktop: $desktop, Mornye: $mornye)', (tester) async {
        final lists = <LibrarySongListRequest>[];
        final playback = _Playback();
        final player = _Player();
        await pumpLibrary(
          tester,
          desktop: desktop,
          mornye: mornye,
          overrides: [
            playbackProvider.overrideWith(() => playback),
            musicPlayerControllerProvider.overrideWithValue(player),
            librarySongListProvider.overrideWith((ref, request) async {
              lists.add(request);
              return const [];
            }),
          ],
        );
        await tester.tap(shuffleButton(tester, mornye: mornye));
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
    }
  }

  testWidgets('Material shows Shuffle on Songs only', (tester) async {
    await pumpLibrary(tester);
    final l10n = l10nOf(tester);
    expect(shuffleButton(tester, mornye: false), findsOneWidget);

    for (final tab in [
      l10n.historyFilterAlbums,
      l10n.searchArtists,
      l10n.searchPlaylists,
      l10n.libraryFolders,
    ]) {
      await openTab(tester, tab);
      expect(shuffleButton(tester, mornye: false), findsNothing, reason: tab);
    }
    await openTab(tester, l10n.searchSongs);
    expect(shuffleButton(tester, mornye: false), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final mornye in [false, true]) {
    testWidgets('selecting songs hides Shuffle with the tools '
        '(Mornye: $mornye)', (tester) async {
      await pumpLibrary(tester, mornye: mornye);
      expect(shuffleButton(tester, mornye: mornye), findsOneWidget);

      await tester.longPress(find.text('Song 1').hitTestable());
      await settle(tester, 10);
      expect(shuffleButton(tester, mornye: mornye), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  for (final section in ['albums', 'playlists']) {
    testWidgets('Mornye shows no Shuffle on the $section section', (
      tester,
    ) async {
      await pumpLibrary(tester, mornye: true, librarySection: section);
      expect(shuffleButton(tester, mornye: true), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  for (final mornye in [false, true]) {
    testWidgets('an empty Library shows no Shuffle (Mornye: $mornye)', (
      tester,
    ) async {
      await pumpLibrary(tester, mornye: mornye, songCount: 0);
      expect(shuffleButton(tester, mornye: mornye), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  for (final desktop in [false, true]) {
    for (final mornye in [false, true]) {
      for (final reduceMotion in [false, true]) {
        testWidgets('a hidden search field comes back from the header '
            '(desktop: $desktop, Mornye: $mornye, '
            'reduced motion: $reduceMotion)', (tester) async {
          await pumpLibrary(
            tester,
            desktop: desktop,
            mornye: mornye,
            reduceMotion: reduceMotion,
          );
          final l10n = l10nOf(tester);
          final action = findByTooltip(l10n.mornyeSearch).hitTestable();
          final outer = tester
              .widget<NestedScrollView>(find.byType(NestedScrollView))
              .controller!;
          final focus = tester
              .widget<EditableText>(
                find.descendant(
                  of: find.byType(AppSearchField),
                  matching: find.byType(EditableText),
                ),
              )
              .focusNode;

          // The field shows at the top, so the header repeats nothing.
          expect(action, findsNothing);

          outer.jumpTo(outer.position.maxScrollExtent);
          await settle(tester, 10);
          expect(action, findsOneWidget);
          expect(focus.hasFocus, isFalse);

          await tester.tap(action);
          await settle(tester, 30);
          expect(outer.offset, 0);
          expect(focus.hasFocus, isTrue);
          expect(action, findsNothing);
          expect(tester.takeException(), isNull);
        });
      }
    }
  }
}
