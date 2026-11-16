import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/download_history.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_directory_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_directory_screen.dart';
import 'package:sistrum/services/library_directory_index.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_track_tile.dart';
import 'package:sistrum/widgets/library_directory_view.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/fixed_settings.dart';

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

const _root = '/storage/emulated/0/Music';

/// Two album folders, a song beside them and a CUE sheet in one of them.
final _paths = {
  'a1': '$_root/Artist A/Album A/01 First.flac',
  'a2': '$_root/Artist A/Album A/02 Second.flac',
  'cue': '$_root/Artist A/Album A/sheet.cue#track1',
  'b1': '$_root/Artist B/Album B/01 Other.flac',
  'loose': '$_root/Loose.flac',
};

UnifiedLibraryItem _item(String id) => UnifiedLibraryItem.fromDownloadHistory(
  DownloadHistoryItem(
    id: id,
    trackName: 'Song $id',
    artistName: 'Artist',
    albumName: 'Album',
    filePath: _paths[id]!,
    service: 'example-provider',
    downloadedAt: DateTime(2026, 10, 24),
  ),
);

LibraryDirectoryIndex _index([Map<String, String>? paths]) =>
    LibraryDirectoryIndex.build([
      for (final MapEntry(key: id, value: path) in (paths ?? _paths).entries)
        LibraryDirectoryEntry(source: 'downloaded', id: id, path: path),
    ]);

Future<_Playback> _pump(
  WidgetTester tester, {
  LibraryDirectoryIndex? index,
  UnifiedLibraryItem Function(String id) song = _item,
  Widget? home,
}) async {
  tester.view.physicalSize = const Size(1000, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final playback = _Playback();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(
          () => FixedSettingsNotifier(const AppSettings()),
        ),
        libraryDirectoryIndexProvider.overrideWith(
          (ref) async => index ?? _index(),
        ),
        libraryDirectoryItemsLoaderProvider.overrideWithValue(
          (tracks) async => [for (final track in tracks) song(track.id)],
        ),
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream.value(null),
        ),
        playbackProvider.overrideWith(() => playback),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home ?? const LibraryDirectoryScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return playback;
}

void main() {
  testWidgets('opens where the music starts and walks into a folder', (
    tester,
  ) async {
    final playback = await _pump(tester);

    expect(find.text('Folders'), findsWidgets);
    expect(find.text(_root), findsOneWidget);
    expect(find.text('2 folders · 5 tracks'), findsOneWidget);
    expect(find.widgetWithText(TrackCard, 'Artist A'), findsOneWidget);
    expect(find.widgetWithText(TrackCard, 'Artist B'), findsOneWidget);
    expect(find.widgetWithText(TrackCard, 'Song loose'), findsOneWidget);

    await tester.tap(find.widgetWithText(TrackCard, 'Artist A'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TrackCard, 'Album A'));
    await tester.pumpAndSettle();
    expect(find.text('$_root/Artist A/Album A'), findsOneWidget);
    expect(find.text('3 tracks'), findsOneWidget);

    // A song's play button queues the folder's songs from it; the CUE
    // track needs splitting first, so it stays out of the queue.
    await tester.tap(
      find.descendant(
        of: find.widgetWithText(TrackCard, 'Song a2'),
        matching: find.byTooltip('Play'),
      ),
    );
    await tester.pumpAndSettle();
    expect(playback.queues.single, [_paths['a1'], _paths['a2']]);
    expect(playback.starts.single, 1);
  });

  testWidgets('Play queues every song under the folder in order', (
    tester,
  ) async {
    final playback = await _pump(tester);

    await tester.tap(find.text('Play').first);
    await tester.pumpAndSettle();
    expect(playback.queues.single, [
      _paths['loose'],
      _paths['a1'],
      _paths['a2'],
      _paths['b1'],
    ]);
    expect(playback.starts.single, 0);
  });

  testWidgets('a long press selects songs for the Library actions', (
    tester,
  ) async {
    await _pump(tester);

    await tester.longPress(find.widgetWithText(TrackCard, 'Song loose'));
    await tester.pumpAndSettle();
    expect(find.byType(LibraryTrackSelectionBar), findsOneWidget);
    expect(
      tester
          .widget<TrackCard>(find.widgetWithText(TrackCard, 'Song loose'))
          .isSelected,
      isTrue,
    );

    // A second long press while selecting toggles the row off.
    await tester.longPress(find.widgetWithText(TrackCard, 'Song loose'));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TrackCard>(find.widgetWithText(TrackCard, 'Song loose'))
          .isSelected,
      isFalse,
    );
    await tester.longPress(find.widgetWithText(TrackCard, 'Song loose'));
    await tester.pumpAndSettle();

    // Back ends the selection before it leaves the page.
    final navigator = tester.state<NavigatorState>(find.byType(Navigator));
    await navigator.maybePop();
    await tester.pumpAndSettle();
    expect(find.byType(LibraryTrackSelectionBar), findsNothing);
    expect(find.byType(LibraryDirectoryView), findsOneWidget);
  });

  testWidgets('lists each volume by its path', (tester) async {
    await _pump(
      tester,
      index: _index({
        'a1': '$_root/Album/1.flac',
        'b1': 'SD Card/Music/Album/1.flac',
      }),
    );

    expect(find.widgetWithText(TrackCard, '$_root/Album'), findsOneWidget);
    expect(
      find.widgetWithText(TrackCard, 'SD Card/Music/Album'),
      findsOneWidget,
    );
  });

  testWidgets('an empty Library names the next step', (tester) async {
    await _pump(tester, index: _index(const {}));

    expect(find.text('No folders yet'), findsOneWidget);
    expect(find.textContaining('Local Library settings'), findsOneWidget);
    expect(find.text('Play'), findsNothing);
  });

  testWidgets('a folder that is gone says so', (tester) async {
    await _pump(tester, home: const LibraryDirectoryScreen(folderKey: 'gone'));

    expect(find.text('This folder is no longer in your Library'), findsOne);
  });

  testWidgets('a song row shows only its cover, title and artist', (
    tester,
  ) async {
    await _pump(tester);

    final row = find.widgetWithText(AlbumTrackTile, 'Song loose');
    expect(row, findsOneWidget);
    expect(tester.widget<AlbumTrackTile>(row).cover, isNotNull);
    expect(
      tester
          .widgetList<Text>(
            find.descendant(of: row, matching: find.byType(Text)),
          )
          .map((text) => text.data),
      ['Song loose', 'Artist'],
    );
  });

  testWidgets('Sistrum lists folders and songs as flat rows', (tester) async {
    await _pump(tester);

    final rows = tester.widgetList<TrackCard>(find.byType(TrackCard));
    expect(rows, isNotEmpty);
    expect(rows.every((row) => row.style == TrackCardStyle.flat), isTrue);
    await tester.tap(find.widgetWithText(TrackCard, 'Artist B'));
    await tester.pumpAndSettle();
    expect(find.widgetWithText(TrackCard, 'Album B'), findsOneWidget);
  });
}
