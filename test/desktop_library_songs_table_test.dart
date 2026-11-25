import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/gestures.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/services/library_database.dart';

import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';

import 'support/tooltip_finder.dart';

class _Settings extends SettingsNotifier {
  // The Rewind banner animates for as long as it is shown; keep it off so
  // the page settles.
  @override
  AppSettings build() => const AppSettings(localLibraryEnabled: true);
}

/// The folder the test songs live in: playing a song checks its file first,
/// so the songs are real files made in [main]'s setUp.
late String _musicDir;

/// Two scanned songs, served as the Songs page's rows.
Future<QueueLibraryDbPage> _songPage(QueueLibraryDbQuery query) async =>
    QueueLibraryDbPage(
      rows: [
        for (final (index, name) in ['Bravo', 'Alpha'].indexed)
          {
            'source': 'local',
            'item': LocalLibraryItem(
              id: 'song-$index',
              trackName: name,
              artistName: 'Artist',
              albumName: 'Album $index',
              filePath: '$_musicDir/$name.flac',
              scannedAt: DateTime(2026, 1, 10 + index),
              format: 'flac',
            ).toJson(),
          },
      ],
      nextCursor: null,
    );

class _Local extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _Collections extends LibraryCollectionsNotifier {
  _Collections({this.withPlaylistTracks = false});

  final bool withPlaylistTracks;

  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    playlists: [
      if (withPlaylistTracks)
        UserPlaylistCollection(
          id: 'playlist',
          name: 'Playlist',
          createdAt: DateTime(2026),
          updatedAt: DateTime(2026),
          tracks: const [],
          tracksLoaded: false,
          trackKeys: {'local:song-0', 'local:song-1'},
        ),
    ],
  );
}

class _Playback extends PlaybackController {
  final started = <String>[];
  List<PlayableMedia> _queue = [];
  int? _startIndex;

  @override
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
  }) async {
    started.add(queue.elementAt(startIndex).source);
    _queue = queue.toList();
    _startIndex = startIndex;
  }
}

class _Player extends MusicPlayerController {
  final toggles = <bool>[];

  @override
  Future<void> togglePlayPause(bool isPlaying) async {
    toggles.add(isPlaying);
  }
}

/// The Library's database page keeps a loading indicator spinning, so this
/// settles with bounded pumps.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

/// Playing or opening a song's menu reads the song's file first, which needs
/// real time between the frames.
Future<void> _settleFileRead(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    await tester.pump(const Duration(milliseconds: 50));
  }
}

Future<void> _rightClick(WidgetTester tester, Finder finder) async {
  final gesture = await tester.startGesture(
    tester.getCenter(finder),
    kind: PointerDeviceKind.mouse,
    buttons: kSecondaryMouseButton,
  );
  await gesture.up();
  await _settle(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    _musicDir = Directory.systemTemp.createTempSync('songs-table-').path;
    addTearDown(() => Directory(_musicDir).deleteSync(recursive: true));
    for (final name in ['Alpha', 'Bravo']) {
      File('$_musicDir/$name.flac').writeAsStringSync('audio');
    }
    SharedPreferences.setMockInitialValues({});
    final previousFactory = databaseFactoryOrNull;
    databaseFactory = databaseFactorySqflitePlugin;
    addTearDown(() => databaseFactory = previousFactory);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    const paths = MethodChannel('plugins.flutter.io/path_provider');
    messenger.setMockMethodCallHandler(paths, (_) async => '/test/documents');
    addTearDown(() => messenger.setMockMethodCallHandler(paths, null));
    // An empty database: every page query returns no rows.
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
  // Clicks of one test must not complete a double click of the last one.
  setUp(DesktopDoubleClick.reset);
  tearDown(() => debugDesktopPlatformOverride = null);

  Future<void> pumpLibrary(
    WidgetTester tester, {
    required bool desktop,
    String filterMode = 'all',
    bool withPlaylistTracks = false,
    bool root = false,
    PlaybackController? playback,
    MusicPlayerController? player,
    MediaItem? currentItem,
    bool playing = false,
    bool loading = false,
  }) async {
    debugDesktopPlatformOverride = desktop;
    tester.view.physicalSize = desktop
        ? const Size(1280, 900)
        : const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          localLibraryProvider.overrideWith(_Local.new),
          queueLibraryTrackPageReaderProvider.overrideWithValue(_songPage),
          libraryCollectionsProvider.overrideWith(
            () => _Collections(withPlaylistTracks: withPlaylistTracks),
          ),
          if (playback != null) playbackProvider.overrideWith(() => playback),
          if (player != null)
            musicPlayerControllerProvider.overrideWith((ref) => player),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(currentItem),
          ),
          playbackPlayingProvider.overrideWith((ref) => playing),
          playbackLoadingProvider.overrideWith((ref) => loading),
          // Without a choice these tests play songs with a tap on the row.
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          // Songs open as a section page from the Library root.
          home: Scaffold(
            body: QueueTab(librarySection: !root ? filterMode : null),
          ),
        ),
      ),
    );
    // The app's player bar keeps the current item loaded, which Mornye rows
    // do not watch themselves.
    ProviderScope.containerOf(
      tester.element(find.byType(QueueTab)),
    ).listen(currentMediaItemProvider, (_, _) {});
    await _settle(tester);
  }

  testWidgets('desktop Songs list is the songs table', (tester) async {
    await pumpLibrary(tester, desktop: true);
    expect(find.byType(SliverTrackTable), findsOneWidget);
    expect(find.byType(TrackCard), findsNothing);
    expect(find.text('Alpha'), findsOneWidget);
    expect(find.text('Bravo'), findsOneWidget);
    // No month sections: the table sorts through its header instead.
    expect(find.text('January 2026'), findsNothing);
    final l10n = AppLocalizations.of(tester.element(find.byType(QueueTab)));
    await tester.tap(find.text(l10n.editMetadataFieldTitle));
    await _settle(tester);
    final preferences = await SharedPreferences.getInstance();
    expect(loadBootstrapSettings(preferences).historySortMode, 'a-z');
  });

  // A click plays the song and the queue after it, as the row's play circle
  // does.
  testWidgets('a click plays the full queue', (tester) async {
    final playback = _Playback();
    await pumpLibrary(tester, desktop: true, playback: playback);
    final l10n = AppLocalizations.of(tester.element(find.byType(QueueTab)));
    expect(
      findByTooltip(l10n.a11yPlayTrackByArtist('Alpha', 'Artist')),
      findsOneWidget,
    );

    await tester.tap(find.text('Alpha'));
    await _settleFileRead(tester);
    expect(playback.started, ['$_musicDir/Alpha.flac']);
    expect(playback._queue.map((item) => item.source), [
      '$_musicDir/Bravo.flac',
      '$_musicDir/Alpha.flac',
    ]);
    expect(playback._startIndex, 1);
    expect(find.byType(LibraryTrackSelectionBar), findsNothing);
  });

  for (final playing in [false, true]) {
    testWidgets(
      'a click on the current song toggles playback in place (playing: $playing)',
      (tester) async {
        final playback = _Playback();
        final player = _Player();
        await pumpLibrary(
          tester,
          desktop: true,
          playback: playback,
          player: player,
          currentItem: MediaItem(
            id: '$_musicDir/Alpha.flac',
            title: 'Alpha',
            artist: 'Artist',
          ),
          playing: playing,
        );
        await tester.tap(find.text('Alpha'));
        await _settle(tester);
        expect(player.toggles, [playing]);
        expect(playback.started, isEmpty);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('a click on the current song does nothing while it loads', (
    tester,
  ) async {
    final playback = _Playback();
    final player = _Player();
    await pumpLibrary(
      tester,
      desktop: true,
      playback: playback,
      player: player,
      currentItem: MediaItem(
        id: '$_musicDir/Alpha.flac',
        title: 'Alpha',
        artist: 'Artist',
      ),
      loading: true,
    );
    await tester.tap(find.text('Alpha'));
    await _settle(tester);
    expect(player.toggles, isEmpty);
    expect(playback.started, isEmpty);
  });

  testWidgets('a Shift-click still selects instead of playing', (tester) async {
    final playback = _Playback();
    await pumpLibrary(tester, desktop: true, playback: playback);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.tap(find.text('Alpha'));
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await _settle(tester);
    expect(find.byType(LibraryTrackSelectionBar), findsOneWidget);
    expect(playback.started, isEmpty);
  });

  testWidgets('a right-click menu leaves the selection toolbar closed', (
    tester,
  ) async {
    final playback = _Playback();
    await pumpLibrary(tester, desktop: true, playback: playback);
    final toolbar = find.byType(LibraryTrackSelectionBar);
    Finder menuRow(String label) => find.descendant(
      of: find.byType(MornyeContextMenu),
      matching: find.text(label),
    );

    // The menu acts on the song without opening the toolbar under it,
    // and closing the menu ends that selection.
    await _rightClick(tester, find.text('Alpha'));
    expect(menuRow('Play next'), findsOneWidget);
    expect(toolbar, findsNothing);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await _settle(tester);
    expect(menuRow('Play next'), findsNothing);
    expect(toolbar, findsNothing);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await _settle(tester);
    expect(toolbar, findsNothing);

    await _rightClick(tester, find.text('Alpha'));
    await tester.tap(menuRow('Play'));
    await _settleFileRead(tester);
    expect(playback.started, ['$_musicDir/Alpha.flac']);
    expect(toolbar, findsNothing);

    // A bulk action keeps the selection, so the toolbar returns.
    await _rightClick(tester, find.text('Bravo'));
    // The long menu scrolls inside the window.
    await tester.ensureVisible(menuRow('Delete 1 track'));
    await _settle(tester);
    await tester.tap(menuRow('Delete 1 track'));
    await _settle(tester);
    expect(toolbar, findsOneWidget);
  });

  testWidgets('phones show a song card per track', (tester) async {
    await pumpLibrary(tester, desktop: false);
    expect(find.byType(SliverTrackTable), findsNothing);
    expect(find.byType(TrackCard), findsNWidgets(2));
    // No month sections: Mornye lists the songs without dated headers.
    expect(find.text('January 2026'), findsNothing);
  });

  testWidgets('a phone Songs row plays on a tap', (tester) async {
    final playback = _Playback();
    await pumpLibrary(tester, desktop: false, playback: playback);
    final l10n = AppLocalizations.of(tester.element(find.byType(QueueTab)));
    final row = find.widgetWithText(TrackCard, 'Alpha');
    expect(
      find.descendant(of: row, matching: findByTooltip(l10n.tooltipPlay)),
      findsOneWidget,
    );

    await tester.tap(find.text('Alpha'));
    await _settleFileRead(tester);
    expect(playback.started, ['$_musicDir/Alpha.flac']);
    expect(playback._queue.map((item) => item.source), [
      '$_musicDir/Bravo.flac',
      '$_musicDir/Alpha.flac',
    ]);
  });

  testWidgets('a phone Songs row keeps toggling selection on a tap', (
    tester,
  ) async {
    final playback = _Playback();
    await pumpLibrary(tester, desktop: false, playback: playback);
    await tester.longPress(find.text('Alpha'));
    await _settle(tester);
    expect(find.byType(LibraryTrackSelectionBar), findsOneWidget);
    await tester.tap(find.text('Bravo'));
    await _settle(tester);
    expect(
      tester
          .widget<TrackCard>(find.widgetWithText(TrackCard, 'Bravo'))
          .isSelected,
      isTrue,
    );
    expect(playback.started, isEmpty);
  });

  testWidgets('a phone Songs row whose file is missing opens its song menu', (
    tester,
  ) async {
    final playback = _Playback();
    File('$_musicDir/Alpha.flac').deleteSync();
    await pumpLibrary(tester, desktop: false, playback: playback);
    // The test songs have no files; the Library confirms the miss after its
    // retries.
    for (var i = 0; i < 14; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 20)),
      );
      await tester.pump(const Duration(milliseconds: 500));
    }
    final row = find.widgetWithText(TrackCard, 'Alpha');
    final l10n = AppLocalizations.of(tester.element(find.byType(QueueTab)));
    expect(
      find.descendant(
        of: row,
        matching: findByTooltip(l10n.queueDownloadedFileMissing),
      ),
      findsOneWidget,
    );

    await tester.tap(find.text('Alpha'));
    await _settleFileRead(tester);
    expect(playback.started, isEmpty);
    expect(find.byType(MornyeContextMenu), findsOneWidget);
  });

  for (final desktop in [false, true]) {
    for (final mode in ['all']) {
      testWidgets(
        'playlist members remain visible (desktop: $desktop, mode: $mode)',
        (tester) async {
          await pumpLibrary(
            tester,
            desktop: desktop,
            filterMode: mode,
            withPlaylistTracks: true,
          );
          // The '2 tracks' album label came from the removed download-history
          // album grouping; the songs themselves are checked below.
          expect(find.text('Alpha'), findsOneWidget);
          expect(find.text('Bravo'), findsOneWidget);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets('the Mornye Library root shows its library screen', (
    tester,
  ) async {
    await pumpLibrary(
      tester,
      desktop: false,
      root: true,
      withPlaylistTracks: true,
    );
    expect(find.byType(MornyeLibraryScreen), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
