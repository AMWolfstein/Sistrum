import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/widgets/library_song_artwork.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_genre_screen.dart';
import 'package:sistrum/screens/library_genres_screen.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/services/library_detail_data.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings(
    localLibraryEnabled: false,
    listeningStatisticsEnabled: false,
  );
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
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

const _genres = [
  LibraryGenreEntry(key: 'pop', name: 'Pop', trackCount: 1),
  LibraryGenreEntry(key: 'rock', name: 'Rock', trackCount: 2),
];

final _rockSongs = [
  for (final index in [0, 1, 2])
    UnifiedLibraryItem.fromDownloadHistory(
      DownloadHistoryItem(
        id: 'rock-$index',
        trackName: 'Song $index',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: '/music/rock-$index.flac',
        service: 'example-provider',
        downloadedAt: DateTime(2026, 1, 10 + index),
      ),
    ),
];

Widget _app(Widget home, List<Override> overrides) => ProviderScope(
  overrides: overrides,
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

void main() {
  testWidgets('the overview has a Genres row that opens the genres', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _app(Scaffold(body: MornyeLibraryScreen(onOpenSection: (_) {})), [
        downloadQueueLookupProvider.overrideWithValue(
          DownloadQueueLookup.fromItems(const []),
        ),
        settingsProvider.overrideWith(_Settings.new),
        downloadHistoryProvider.overrideWith(_History.new),
        libraryGenresProvider.overrideWith((ref) async => _genres),
      ]),
    );
    await tester.pumpAndSettle();

    final row = find.text('Genres');
    expect(row, findsOneWidget);
    expect(find.text('Songs'), findsOneWidget);
    expect(find.text('Folders'), findsOneWidget);
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(find.byType(LibraryGenresScreen), findsOneWidget);
    expect(find.text('Pop'), findsOneWidget);
  });

  testWidgets('the genres page lists genres with counts and searches them', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(const LibraryGenresScreen(), [
        libraryGenresProvider.overrideWith((ref) async => _genres),
        libraryGenreItemsProvider.overrideWith((ref, key) async => _rockSongs),
        musicPlayerControllerProvider.overrideWithValue(_Player()),
        playbackProvider.overrideWith(_Playback.new),
      ]),
    );
    await tester.pumpAndSettle();

    expect(find.text('Pop'), findsOneWidget);
    expect(find.text('Rock'), findsOneWidget);
    expect(find.text('1 track'), findsOneWidget);
    expect(find.text('2 tracks'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'ro');
    await tester.pumpAndSettle();
    expect(find.text('Pop'), findsNothing);
    expect(find.text('Rock'), findsOneWidget);

    await tester.tap(find.text('Rock'));
    await tester.pumpAndSettle();
    expect(find.byType(LibraryGenreScreen), findsOneWidget);
  });

  testWidgets('a genre page has Play and Shuffle over its song rows', (
    tester,
  ) async {
    final player = _Player();
    final playback = _Playback();
    await tester.pumpWidget(
      _app(const LibraryGenreScreen(genreKey: 'rock', name: 'Rock'), [
        libraryGenreItemsProvider.overrideWith((ref, key) async => _rockSongs),
        musicPlayerControllerProvider.overrideWithValue(player),
        playbackProvider.overrideWith(() => playback),
      ]),
    );
    await tester.pumpAndSettle();

    expect(find.text('Rock'), findsOneWidget);
    expect(find.text('3 tracks'), findsOneWidget);
    expect(find.byType(AlbumPlayActions), findsOneWidget);
    expect(find.text('Play'), findsOneWidget);
    expect(find.text('Shuffle'), findsOneWidget);
    expect(find.text('Song 0'), findsOneWidget);
    expect(find.text('Song 2'), findsOneWidget);
    // Rows read a file cover or an embedded one, not only a network cover.
    expect(find.byType(LibrarySongArtwork), findsNWidgets(3));

    await tester.tap(find.text('Shuffle'));
    await tester.pumpAndSettle();
    expect(player.shuffles, [true]);
    expect(playback.played.single.titles, ['Song 0', 'Song 1', 'Song 2']);

    // A tapped row plays in order, so the earlier Shuffle is switched off.
    await tester.tap(find.text('Song 1'));
    await tester.pumpAndSettle();
    expect(playback.played.last.startIndex, 1);
    expect(player.shuffles, [true, false]);
  });
}
