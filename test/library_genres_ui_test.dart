import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_mix_screen.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/services/playable_media.dart' show PlayableMedia;
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/fixed_settings.dart';
import 'support/tooltip_finder.dart';

const _rock = LibraryBrowseEntry(
  source: '',
  key: 'rock',
  name: 'Rock',
  artist: '',
  samplePath: '',
  trackCount: 3,
);

const _jazz = LibraryBrowseEntry(
  source: '',
  key: 'jazz',
  name: 'Jazz',
  artist: '',
  samplePath: '',
  trackCount: 1,
);

late Directory _songDir;

UnifiedLibraryItem _song(String id) => UnifiedLibraryItem.fromLocalLibrary(
  LocalLibraryItem(
    id: id,
    trackName: 'Song $id',
    artistName: 'Artist $id',
    albumName: 'Album $id',
    // Songs play only when their file is on disk.
    filePath: '${_songDir.path}/$id.flac',
    scannedAt: DateTime(2026, 11, 16),
    duration: 200,
  ),
);

final _songs = {
  'rock': [_song('r1'), _song('r2'), _song('r3')],
  'jazz': [_song('j1')],
};

class _Player implements MusicPlayerController {
  final queues = <List<String>>[];
  final starts = <int>[];
  final shuffles = <bool>[];

  @override
  Future<void> playAll(
    List<PlayableMedia> items, {
    int initialIndex = 0,
  }) async {
    queues.add([for (final media in items) media.source]);
    starts.add(initialIndex);
  }

  @override
  Future<void> setShuffle(bool enabled) async => shuffles.add(enabled);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<_Player> _pump(
  WidgetTester tester,
  Widget home, {
  bool desktop = false,
  List<LibraryBrowseEntry> genres = const [_rock, _jazz],
}) async {
  debugDesktopPlatformOverride = desktop;
  addTearDown(() => debugDesktopPlatformOverride = null);
  tester.view.physicalSize = desktop
      ? const Size(1280, 900)
      : const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final player = _Player();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(
            const AppSettings(listeningStatisticsEnabled: false),
          ),
        ),
        libraryBrowsePageLoaderProvider.overrideWithValue(
          (_) async => const LibraryBrowsePage([]),
        ),
        libraryGenresProvider.overrideWith((ref) async => genres),
        libraryGenreSongsProvider.overrideWith(
          (ref, genre) async => _songs[genre] ?? const [],
        ),
        musicPlayerControllerProvider.overrideWithValue(player),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home,
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  return player;
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(() async {
    _songDir = await Directory.systemTemp.createTemp('genre-songs');
    for (final id in ['r1', 'r2', 'r3', 'j1']) {
      await File('${_songDir.path}/$id.flac').writeAsBytes(const [0]);
    }
  });
  tearDownAll(() => _songDir.delete(recursive: true));

  for (final desktop in [false, true]) {
    testWidgets('the Mornye Library lists genres and opens one '
        '(desktop: $desktop)', (tester) async {
      await _pump(
        tester,
        Scaffold(body: MornyeLibraryScreen(onOpenSection: (_) {})),
        desktop: desktop,
      );
      final row = find.widgetWithText(ListTile, 'Genres');
      expect(row, findsOneWidget);

      await tester.tap(row);
      await _settle(tester);
      expect(find.text('Rock'), findsOneWidget);
      expect(find.text('3 tracks'), findsOneWidget);
      expect(find.text('Jazz'), findsOneWidget);
      // The page keeps the order the query sorts them in.
      expect(
        tester.getTopLeft(find.text('Rock')).dy,
        lessThan(tester.getTopLeft(find.text('Jazz')).dy),
      );

      await tester.tap(find.text('Jazz'));
      await _settle(tester);
      expect(find.byType(LibraryMixScreen), findsOneWidget);
      expect(find.text('Song j1'), findsOneWidget);
      expect(find.text('Song r1'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('the Mornye Genres page explains an empty Library', (
    tester,
  ) async {
    await _pump(
      tester,
      Scaffold(
        body: MornyeLibraryScreen(
          page: MornyeLibraryPage.genres,
          onOpenSection: (_) {},
        ),
      ),
      genres: const [],
    );
    await _settle(tester);
    expect(find.text('Your music will appear here'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final desktop in [false, true]) {
    testWidgets('a genre lists exactly its songs, and Play and Shuffle '
        'queue them (desktop: $desktop)', (tester) async {
      final player = await _pump(
        tester,
        const LibraryMixScreen.genre(genre: _rock),
        desktop: desktop,
      );
      await _settle(tester);

      expect(find.text('Rock'), findsWidgets);
      expect(find.text('3 tracks'), findsOneWidget);
      // A genre is not a mix: no artists line or daily refresh.
      expect(find.text('Updated daily'), findsNothing);
      for (final id in ['r1', 'r2', 'r3']) {
        expect(find.text('Song $id'), findsOneWidget);
      }
      expect(find.text('Song j1'), findsNothing);
      if (desktop) {
        expect(find.byType(SliverTrackTable), findsOneWidget);
      } else {
        expect(find.byType(TrackCard), findsNWidgets(3));
      }

      await tester.tap(find.text('Play'));
      // Playing checks the song files on disk, which needs real I/O.
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await _settle(tester);
      final queue = [
        for (final id in ['r1', 'r2', 'r3']) '${_songDir.path}/$id.flac',
      ];
      expect(player.queues.single, queue);
      expect(player.starts.single, 0);
      expect(player.shuffles, [false]);

      // The Mornye phone header labels its Shuffle button.
      await tester.tap(
        !desktop
            ? find.text('Shuffle')
            : findByTooltip('Shuffle').hitTestable().first,
      );
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await _settle(tester);
      expect(player.queues.last, queue);
      expect(player.shuffles.last, isTrue);
      expect(tester.takeException(), isNull);
    });
  }
}
