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
import 'package:sistrum/providers/album_track_sort_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:sistrum/widgets/track_card.dart';

/// Where the phone screenshots are written; `--dart-define` can point
/// elsewhere.
const _shotDir = String.fromEnvironment(
  'ALBUM_SHOT_DIR',
  defaultValue:
      '/tmp/claude-0/-home-user-Sistrum/9d293f2a-980b-503e-90a8-6a595050bc28/'
      'scratchpad/tor005/shots',
);
const _shotPath = '$_shotDir/album.png';
const _scrolledShotPath = '$_shotDir/album-second-disc.png';

class _Player extends MusicPlayerController {
  @override
  Future<void> setShuffle(bool enabled) async {}

  @override
  Future<void> togglePlayPause(bool isPlaying) async {}
}

/// Loved songs in memory: the real notifier writes to the library database.
class _FakeLoved extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState();

  @override
  Future<({bool removed, int count})> toggleLovedTracks(
    Iterable<Track> tracks,
  ) async {
    final list = tracks.toList(growable: false);
    if (list.every(state.isLoved)) {
      final keys = {for (final track in list) trackCollectionKey(track)};
      state = LibraryCollectionsState(
        loved: [
          for (final entry in state.loved)
            if (!keys.contains(entry.key)) entry,
        ],
      );
      return (removed: true, count: keys.length);
    }
    final added = [
      for (final track in list)
        if (!state.isLoved(track))
          CollectionTrackEntry(
            key: trackCollectionKey(track),
            track: track,
            addedAt: DateTime(2026),
          ),
    ];
    state = LibraryCollectionsState(loved: [...state.loved, ...added]);
    return (removed: false, count: added.length);
  }
}

/// The round "+" of the header, found by its icon.
Finder _addButton() => find.byWidgetPredicate(
  (w) => w is HeaderCircleButton && w.icon == CupertinoIcons.add,
);

Finder _heartButton(IconData icon) =>
    find.byWidgetPredicate((w) => w is HeaderCircleButton && w.icon == icon);

class _Playback extends PlaybackController {
  @override
  Future<void> playLocalLibraryQueue(
    List<LocalLibraryItem> items, {
    required LocalLibraryItem startItem,
  }) async {}
}

/// Eight songs over two discs; the second disc's first song is explicit.
List<LocalLibraryItem> _albumTracks() => [
  for (var disc = 1; disc <= 2; disc++)
    for (var number = 1; number <= 4; number++)
      LocalLibraryItem(
        id: 'd$disc-$number',
        trackName: 'Song $disc.$number',
        artistName: 'Example Artist',
        albumName: 'Example Album',
        albumArtist: 'Example Artist',
        filePath: 'content://library/d$disc-$number.flac',
        trackNumber: number,
        discNumber: disc,
        totalDiscs: 2,
        totalTracks: 4,
        genre: 'Ambient',
        releaseDate: '2024-03-01',
        format: 'flac',
        bitDepth: 16,
        sampleRate: 44100,
        isExplicit: disc == 2 && number == 1,
        scannedAt: DateTime(2026),
      ),
];

/// The current row's equalizer glyph loops while playing, so this settles
/// with bounded pumps instead of waiting for an idle frame.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

Widget _app(Widget home, {Brightness brightness = Brightness.dark}) =>
    ProviderScope(
      overrides: [
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => Stream<MediaItem?>.value(null),
        ),
        playbackPlayingProvider.overrideWith((ref) => false),
        musicPlayerControllerProvider.overrideWithValue(_Player()),
        playbackProvider.overrideWith(() => _Playback()),
        libraryCollectionsProvider.overrideWith(_FakeLoved.new),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(brightness),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home,
      ),
    );

void _setPhoneSize(WidgetTester tester) {
  SharedPreferences.setMockInitialValues({});
  // 412x915 logical pixels at 2x, the usual Android phone size.
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

void main() {
  testWidgets('album header shows Play and Shuffle, and no Download', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);
    final l10n = tester.element(find.byType(LocalAlbumScreen)).l10n;

    expect(find.byType(AlbumPlayActions), findsOneWidget);
    expect(find.text(l10n.tooltipPlay), findsOneWidget);
    expect(find.text(l10n.actionShuffle), findsOneWidget);
    expect(find.byTooltip(l10n.offlineSave), findsNothing);
    expect(find.byIcon(Icons.download_for_offline_outlined), findsNothing);
    expect(find.byIcon(Icons.download_rounded), findsNothing);
    expect(find.byTooltip(l10n.tooltipLoveAll), findsOneWidget);
    expect(_addButton(), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the heart loves every song, shows filled, and unloves them', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);
    final screen = tester.element(find.byType(LocalAlbumScreen));
    final l10n = screen.l10n;
    final container = ProviderScope.containerOf(screen);

    expect(_heartButton(CupertinoIcons.heart), findsOneWidget);
    await tester.tap(find.byTooltip(l10n.tooltipLoveAll));
    await _settle(tester);
    expect(container.read(libraryCollectionsProvider).loved.length, 8);
    expect(_heartButton(CupertinoIcons.heart_fill), findsOneWidget);
    expect(find.byTooltip(l10n.trackOptionRemoveFromLoved), findsOneWidget);

    await tester.tap(find.byTooltip(l10n.trackOptionRemoveFromLoved));
    await _settle(tester);
    expect(container.read(libraryCollectionsProvider).loved, isEmpty);
    expect(_heartButton(CupertinoIcons.heart), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('+ opens the playlist picker with all eight songs', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);
    final l10n = tester.element(find.byType(LocalAlbumScreen)).l10n;

    await tester.tap(_addButton());
    await _settle(tester);
    await _settle(tester);
    // The header meta shows the count too, so look inside the picker.
    expect(
      find.descendant(
        of: find.byType(MornyeGlassPanel),
        matching: find.text(l10n.tracksCount(8)),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('the sort control sits top right, with no share icon', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);
    final screen = tester.element(find.byType(LocalAlbumScreen));
    final l10n = screen.l10n;

    final sort = find.byType(SortHeaderButton<AlbumTrackSort>);
    final sortCenter = tester.getCenter(sort);
    expect(sortCenter.dx, greaterThan(412 * 0.7));
    expect(sortCenter.dy, lessThan(120));
    final back = find.byTooltip(
      MaterialLocalizations.of(screen).backButtonTooltip,
    );
    // Same row and the same circle: the back button's hit box is taller.
    expect(
      (tester.getCenter(sort).dy - tester.getCenter(back).dy).abs(),
      lessThan(6),
    );

    expect(
      find.byWidgetPredicate(
        (w) =>
            w is HeaderCircleButton &&
            (w.icon == CupertinoIcons.share ||
                w.icon == CupertinoIcons.share_solid ||
                w.icon == Icons.share ||
                w.icon == Icons.ios_share),
      ),
      findsNothing,
    );
    expect(find.byTooltip(l10n.trackMetadataShare), findsNothing);
  });

  testWidgets('rows lead with the track number, discs are separated', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);

    // Each row's leading text is its track number within its disc, and each
    // disc opens with one separator.
    for (final disc in [1, 2]) {
      await _showRow(tester, 'Song $disc.1');
      // Cached rows of the other disc can still be built, so match by number.
      final l10n = tester.element(find.byType(LocalAlbumScreen)).l10n;
      expect(
        find.text(l10n.downloadedAlbumDiscHeader(disc)),
        findsOneWidget,
        reason: 'disc $disc',
      );
      for (var number = 1; number <= 4; number++) {
        final title = 'Song $disc.$number';
        await _showRow(tester, title);
        final card = tester.widget<TrackCard>(_row(title));
        final leading = card.leading as SizedBox;
        expect((leading.child as Text).data, '$number', reason: title);
      }
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('only the explicit song shows the explicit chip', (tester) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);

    await _showRow(tester, 'Song 2.1');
    expect(find.byType(ExplicitChip), findsOneWidget);
    expect(
      find.descendant(
        of: _row('Song 2.1'),
        matching: find.byType(ExplicitChip),
      ),
      findsOneWidget,
    );
  });

  testWidgets('the sort control is in the header, and selection hides it', (
    tester,
  ) async {
    _setPhoneSize(tester);
    await tester.pumpWidget(
      _app(
        LocalAlbumScreen(
          albumName: 'Example Album',
          artistName: 'Example Artist',
          tracks: _albumTracks(),
        ),
      ),
    );
    await _settle(tester);
    final l10n = tester.element(find.byType(LocalAlbumScreen)).l10n;

    expect(find.byType(SortHeaderButton<AlbumTrackSort>), findsOneWidget);
    expect(find.byTooltip(l10n.albumSortSongs), findsOneWidget);

    await _showRow(tester, 'Song 1.2');
    await tester.longPress(_row('Song 1.2'));
    await _settle(tester);
    expect(find.byType(LibraryTrackSelectionBar), findsOneWidget);
    // The count shows in the header and in the selection bar.
    expect(find.text(l10n.selectionSelected(1)), findsWidgets);
    expect(find.byType(SortHeaderButton<AlbumTrackSort>), findsNothing);
    expect(find.byType(AlbumPlayActions), findsNothing);
    expect(find.byTooltip(l10n.tooltipLoveAll), findsNothing);
    expect(_addButton(), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('writes a phone screenshot of the album', (tester) async {
    _setPhoneSize(tester);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: _app(
          LocalAlbumScreen(
            albumName: 'Example Album',
            artistName: 'Example Artist',
            tracks: _albumTracks(),
          ),
        ),
      ),
    );
    await _settle(tester);
    await _writePng(tester, boundary, _shotPath);

    // The second disc, with its separator and the explicit song.
    await _showRow(tester, 'Song 2.4');
    await _writePng(tester, boundary, _scrolledShotPath);
  });
}
