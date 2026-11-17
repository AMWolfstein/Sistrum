import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/download_item.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sqflite/sqflite.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings(listeningStatisticsEnabled: false);
}

/// Three downloaded songs, shown in this order by the Songs page.
class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState(
    items: [
      for (final index in [0, 1, 2])
        DownloadHistoryItem(
          id: 'song-$index',
          trackName: 'Song $index',
          artistName: 'Artist',
          albumName: 'Album $index',
          filePath: '/music/song-$index.flac',
          service: 'example-provider',
          downloadedAt: DateTime(2026, 1, 10 + index),
        ),
    ],
    totalCount: 3,
  );
}

class _Local extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _Collections extends LibraryCollectionsNotifier {
  _Collections(this.playlists);

  final List<UserPlaylistCollection> playlists;

  @override
  LibraryCollectionsState build() =>
      LibraryCollectionsState(isLoaded: true, playlists: playlists);
}

class _Queue extends DownloadQueueNotifier {
  @override
  DownloadQueueState build() => DownloadQueueState();
}

class _Player extends MusicPlayerController {
  final shuffles = <bool>[];

  @override
  Future<void> setShuffle(bool enabled) async => shuffles.add(enabled);
}

class _Playback extends PlaybackController {
  final played = <({List<String> titles, int startIndex})>[];

  @override
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
    required String externalPath,
  }) async {
    played.add((
      titles: [for (final media in queue) media.title],
      startIndex: startIndex,
    ));
  }
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
    // An empty database: the Songs page shows the in-memory history.
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

  Future<void> pumpQueue(
    WidgetTester tester, {
    required String section,
    required _Player player,
    required _Playback playback,
    List<UserPlaylistCollection> playlists = const [],
  }) async {
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final queue = DownloadQueueLookup.fromItems([
      DownloadItem(
        id: 'completed',
        track: const Track(
          id: 'song',
          name: 'Song',
          artistName: 'Artist',
          albumName: 'Album',
          duration: 1000,
        ),
        service: 'example-provider',
        status: DownloadStatus.completed,
        createdAt: DateTime(2026),
      ),
    ]);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          downloadHistoryProvider.overrideWith(_History.new),
          localLibraryProvider.overrideWith(_Local.new),
          libraryCollectionsProvider.overrideWith(
            () => _Collections(playlists),
          ),
          downloadQueueProvider.overrideWith(_Queue.new),
          downloadQueueLookupProvider.overrideWithValue(queue),
          musicPlayerControllerProvider.overrideWithValue(player),
          playbackProvider.overrideWith(() => playback),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: QueueTab(librarySection: section)),
        ),
      ),
    );
    // The database page never settles, so pump for a bounded time.
    await pumpBounded(tester);
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(QueueTab)));

  testWidgets('Songs page shows Play and Shuffle above the count', (
    tester,
  ) async {
    await pumpQueue(
      tester,
      section: 'all',
      player: _Player(),
      playback: _Playback(),
    );
    final l10n = l10nOf(tester);
    expect(find.text(l10n.tooltipPlay), findsOneWidget);
    expect(find.text(l10n.actionShuffle), findsOneWidget);
    expect(
      tester.getTopLeft(find.text(l10n.tooltipPlay)).dy,
      lessThan(tester.getTopLeft(find.text(l10n.queueTrackCount(3))).dy),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Play and Shuffle play the Songs list in order', (tester) async {
    final player = _Player();
    final playback = _Playback();
    await pumpQueue(tester, section: 'all', player: player, playback: playback);
    final l10n = l10nOf(tester);

    await tester.tap(find.text(l10n.tooltipPlay));
    await pumpBounded(tester);
    expect(player.shuffles, [false]);
    expect(playback.played.single.titles, ['Song 0', 'Song 1', 'Song 2']);
    expect(playback.played.single.startIndex, 0);

    await tester.tap(find.text(l10n.actionShuffle));
    await pumpBounded(tester);
    expect(player.shuffles, [false, true]);
    expect(playback.played.last.titles, ['Song 0', 'Song 1', 'Song 2']);
    expect(playback.played.last.startIndex, inInclusiveRange(0, 2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Songs rows show only the cover, title and artist', (
    tester,
  ) async {
    await pumpQueue(
      tester,
      section: 'all',
      player: _Player(),
      playback: _Playback(),
    );
    final row = find.widgetWithText(TrackCard, 'Song 0');
    expect(row, findsOneWidget);
    expect(find.descendant(of: row, matching: find.byType(Hero)), findsOne);
    expect(_rowTexts(tester, row), ['Song 0', 'Artist']);
  });

  testWidgets('Songs page has no Create playlist and no drag to playlists', (
    tester,
  ) async {
    await pumpQueue(
      tester,
      section: 'all',
      player: _Player(),
      playback: _Playback(),
    );
    final l10n = l10nOf(tester);

    expect(find.text(l10n.collectionCreatePlaylist), findsNothing);
    expect(
      find.byWidgetPredicate((w) => w is LongPressDraggable),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Playlists page keeps Create playlist and no Songs Play', (
    tester,
  ) async {
    await pumpQueue(
      tester,
      section: 'playlists',
      player: _Player(),
      playback: _Playback(),
      playlists: [
        UserPlaylistCollection(
          id: 'mix',
          name: 'Mix',
          createdAt: DateTime(2026),
          updatedAt: DateTime(2026),
          tracks: const [],
        ),
      ],
    );
    final l10n = l10nOf(tester);

    expect(find.text(l10n.collectionCreatePlaylist), findsOneWidget);
    expect(find.text(l10n.actionShuffle), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

/// Pumps for a bounded time; a loading indicator keeps the page from settling.
Future<void> pumpBounded(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

/// The text a row shows, in order.
List<String?> _rowTexts(WidgetTester tester, Finder row) => [
  for (final text in tester.widgetList<Text>(
    find.descendant(of: row, matching: find.byType(Text)),
  ))
    (text.data ?? text.textSpan!.toPlainText()),
];
