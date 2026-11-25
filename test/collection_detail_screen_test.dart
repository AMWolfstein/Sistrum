import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/collection_track_sort_provider.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/collection_detail_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/fixed_settings.dart';
import 'support/header_icon_finder.dart';
import 'support/tooltip_finder.dart';

/// Where the phone screenshots are written; `--dart-define` can point
/// elsewhere.
const _shotDir = String.fromEnvironment(
  'COLLECTION_SHOT_DIR',
  defaultValue:
      '/tmp/claude-0/-home-user-Sistrum/9d293f2a-980b-503e-90a8-6a595050bc28/'
      'scratchpad/tor005/shots',
);

class _Player extends MusicPlayerController {
  @override
  Future<void> setShuffle(bool enabled) async {}

  @override
  Future<void> togglePlayPause(bool isPlaying) async {}
}

class _Playback extends PlaybackController {
  @override
  Future<void> playLocalLibraryQueue(
    List<LocalLibraryItem> items, {
    required LocalLibraryItem startItem,
  }) async {}

  // No song has a file on disk, so an export finds nothing to write.
  @override
  Future<List<String?>> resolveTrackFilePaths(List<Track> tracks) async => [
    for (final _ in tracks) null,
  ];
}

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    loved: _entries(),
    playlists: [
      UserPlaylistCollection(
        id: 'mix',
        name: 'Example playlist',
        createdAt: DateTime(2026),
        updatedAt: DateTime(2026),
        tracks: _entries(),
      ),
    ],
  );

  @override
  Future<void> ensurePlaylistLoaded(String id) async {}

  @override
  Future<bool> reorderPlaylistTracks(String id, List<String> keys) async =>
      true;

  @override
  Future<bool> reorderSavedTracks(
    LibraryTracksFolderMode mode,
    List<String> keys,
  ) async => true;
}

/// Loved with no songs, for the disabled export button.
class _EmptyCollections extends _Collections {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(
    isLoaded: true,
    loved: const [],
    playlists: const [],
  );
}

class _Library extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

/// Eight songs over two artists; the second is explicit.
List<Track> _tracks() => [
  for (var i = 1; i <= 8; i++)
    Track(
      id: 'song-$i',
      name: 'Song $i',
      artistName: i.isEven ? 'Example Artist' : 'Second Artist',
      albumName: 'Example Album',
      duration: 200 + i,
      explicit: i == 2,
    ),
];

List<CollectionTrackEntry> _entries() => [
  for (final track in _tracks())
    CollectionTrackEntry(key: track.id, track: track, addedAt: DateTime(2026)),
];

/// The same eight songs as Library rows, for the genre page.
List<UnifiedLibraryItem> _genreItems() => [
  for (var i = 1; i <= 8; i++)
    UnifiedLibraryItem.fromLocalLibrary(
      LocalLibraryItem(
        id: 'g$i',
        trackName: 'Song $i',
        artistName: i.isEven ? 'Example Artist' : 'Second Artist',
        albumName: 'Example Album',
        albumArtist: 'Example Artist',
        filePath: 'content://library/g$i.flac',
        trackNumber: i,
        discNumber: 1,
        totalDiscs: 1,
        totalTracks: 8,
        duration: 200 + i,
        genre: 'Rock',
        releaseDate: '2024-03-01',
        format: 'flac',
        bitDepth: 16,
        sampleRate: 44100,
        isExplicit: i == 2,
        scannedAt: DateTime(2026),
      ),
    ),
];

const _rock = LibraryBrowseEntry(
  source: 'local',
  key: 'rock',
  name: 'Rock',
  artist: '',
  samplePath: '',
  trackCount: 8,
);

/// Rows are built lazily, so the screen settles with bounded pumps: the
/// current row's equalizer glyph loops while playing.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

Widget _page(CollectionDetailKind kind) => switch (kind) {
  CollectionDetailKind.playlist => CollectionDetailScreen.playlist(
    playlistId: 'mix',
  ),
  CollectionDetailKind.loved => const CollectionDetailScreen.loved(),
  CollectionDetailKind.genre => const CollectionDetailScreen.genre(
    genre: _rock,
  ),
};

Widget _app(
  CollectionDetailKind kind, {
  bool emptyLoved = false,
}) => ProviderScope(
  overrides: [
    settingsProvider.overrideWith(FixedSettingsNotifier.new),
    libraryCollectionsProvider.overrideWith(
      emptyLoved ? _EmptyCollections.new : _Collections.new,
    ),
    localLibraryProvider.overrideWith(_Library.new),
    localLibraryCoverProvider.overrideWith((ref, request) async => null),
    localLibraryFirstCoverProvider.overrideWith((ref, request) async => null),
    libraryGenreSongsProvider.overrideWith((ref, genre) async => _genreItems()),
    currentMediaItemProvider.overrideWithBuild(
      (ref, notifier) => Stream<MediaItem?>.value(null),
    ),
    playbackPlayingProvider.overrideWith((ref) => false),
    musicPlayerControllerProvider.overrideWithValue(_Player()),
    playbackProvider.overrideWith(() => _Playback()),
  ],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: _page(kind),
  ),
);

/// 412x915 logical pixels at 2x, the usual Android phone size.
void _setPhoneSize(WidgetTester tester) {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(412, 915) * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
}

Finder _row(String title) => find.byWidgetPredicate(
  (widget) => widget is TrackCard && widget.title == title,
);

/// Rows are built lazily, so a row off the first screen is scrolled to first.
Future<void> _showRow(WidgetTester tester, String title) async {
  await tester.scrollUntilVisible(
    _row(title),
    200,
    scrollable: find
        .descendant(
          of: find.byType(CustomScrollView),
          matching: find.byType(Scrollable),
        )
        .first,
  );
  await _settle(tester);
}

Future<void> _writePng(
  WidgetTester tester,
  GlobalKey boundary,
  String path,
) async {
  await tester.runAsync(() async {
    final render =
        boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
    final image = await render.toImage(pixelRatio: 2);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    File(path)
      ..createSync(recursive: true)
      ..writeAsBytesSync(bytes!.buffer.asUint8List());
  });
  expect(File(path).existsSync(), isTrue);
}

Future<void> _pumpKind(WidgetTester tester, CollectionDetailKind kind) async {
  _setPhoneSize(tester);
  await tester.pumpWidget(_app(kind));
  await _settle(tester);
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final kind in CollectionDetailKind.values) {
    group(kind.name, () {
      testWidgets('header shows Play and Shuffle, a sort control, and no '
          'Download, heart or + circle', (tester) async {
        await _pumpKind(tester, kind);
        final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;

        expect(find.byType(AlbumPlayActions), findsOneWidget);
        expect(find.text(l10n.tooltipPlay), findsOneWidget);
        expect(find.text(l10n.actionShuffle), findsOneWidget);
        expect(
          find.byType(SortHeaderButton<CollectionTrackSort>),
          findsOneWidget,
        );
        expect(find.byTooltip(l10n.albumSortSongs), findsOneWidget);
        expect(find.byTooltip(l10n.offlineSave), findsNothing);
        expect(find.byIcon(Icons.download_for_offline_outlined), findsNothing);
        expect(find.byTooltip(l10n.tooltipLoveAll), findsNothing);
        expect(
          find.byWidgetPredicate(
            (w) => w is HeaderCircleButton && w.icon == CupertinoIcons.add,
          ),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
      });

      testWidgets('rows lead with the song cover, not a track number', (
        tester,
      ) async {
        await _pumpKind(tester, kind);
        for (var number = 1; number <= 8; number++) {
          final title = 'Song $number';
          await _showRow(tester, title);
          final card = tester.widget<TrackCard>(_row(title));
          expect(card.leading, isA<ClipRRect>(), reason: title);
          expect(
            find.descendant(of: _row(title), matching: find.text('$number')),
            findsNothing,
            reason: title,
          );
        }
        expect(tester.takeException(), isNull);
      });

      testWidgets('the explicit song shows the explicit chip', (tester) async {
        await _pumpKind(tester, kind);
        await _showRow(tester, 'Song 2');
        expect(
          find.descendant(
            of: _row('Song 2'),
            matching: find.byType(ExplicitChip),
          ),
          findsOneWidget,
        );
      });

      testWidgets('a long press selects, hides the sort control and the '
          'header actions', (tester) async {
        await _pumpKind(tester, kind);
        final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;
        await _showRow(tester, 'Song 3');

        await tester.longPress(_row('Song 3'));
        await _settle(tester);
        expect(find.text(l10n.selectionSelected(1)), findsWidgets);
        expect(
          find.byType(SortHeaderButton<CollectionTrackSort>),
          findsNothing,
        );
        expect(find.byType(AlbumPlayActions), findsNothing);
        expect(tester.takeException(), isNull);
      });

      testWidgets('reorder is offered only where the collection has a saved '
          'order', (tester) async {
        await _pumpKind(tester, kind);
        final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;
        await tester.tap(find.byTooltip(l10n.albumSortSongs));
        await _settle(tester);
        final customFinder = find.text(l10n.albumSortCustom);
        if (kind == CollectionDetailKind.genre) {
          expect(customFinder, findsNothing);
          return;
        }
        expect(customFinder, findsOneWidget);
        await tester.tap(customFinder);
        await _settle(tester);
        expect(find.byType(CollectionReorderSliver), findsOneWidget);
        expect(tester.takeException(), isNull);
      });
    });
  }

  group('phone header actions', () {
    testWidgets('Loved exports from a direct button, with no More menu', (
      tester,
    ) async {
      _setPhoneSize(tester);
      await tester.pumpWidget(_app(CollectionDetailKind.loved));
      await _settle(tester);
      final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;

      expect(findByTooltip(l10n.collectionExportM3u), findsOneWidget);
      expect(headerIcon(Icons.ios_share), findsOneWidget);
      expect(find.byType(HeaderMoreButton), findsNothing);

      // With no file on disk the export reports that, and writes nothing.
      await tester.tap(headerIcon(Icons.ios_share));
      await _settle(tester);
      expect(find.text(l10n.collectionExportM3uNone), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Loved export is disabled when there are no songs', (
      tester,
    ) async {
      _setPhoneSize(tester);
      await tester.pumpWidget(
        _app(CollectionDetailKind.loved, emptyLoved: true),
      );
      await _settle(tester);
      final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;

      expect(findByTooltip(l10n.collectionExportM3u), findsOneWidget);
      expect(
        tester
            .widget<HeaderCircleButton>(headerIcon(Icons.ios_share))
            .onPressed,
        isNull,
      );
      expect(find.byType(HeaderMoreButton), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a genre saves as a playlist from a direct button, with no '
        'More menu', (tester) async {
      await _pumpKind(tester, CollectionDetailKind.genre);
      final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;

      expect(findByTooltip(l10n.homeMixSaveAsPlaylist), findsOneWidget);
      expect(headerIcon(Icons.playlist_add), findsOneWidget);
      expect(find.byType(HeaderMoreButton), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a playlist keeps its More menu', (tester) async {
      await _pumpKind(tester, CollectionDetailKind.playlist);
      final l10n = tester.element(find.byType(CollectionDetailScreen)).l10n;

      expect(find.byType(HeaderMoreButton), findsOneWidget);
      expect(findByTooltip(l10n.collectionExportM3u), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('writes the phone screenshots of the three pages', (
    tester,
  ) async {
    for (final (kind, file) in [
      (CollectionDetailKind.playlist, 'playlist.png'),
      (CollectionDetailKind.loved, 'loved.png'),
      (CollectionDetailKind.genre, 'genre.png'),
    ]) {
      _setPhoneSize(tester);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(key: boundary, child: _app(kind)),
      );
      await _settle(tester);
      await _writePng(tester, boundary, '$_shotDir/$file');
    }
  });
}
