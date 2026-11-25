import 'dart:async';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/track_card.dart';

import 'support/app_icon_finder.dart';
import 'support/tooltip_finder.dart';

class _Playing extends Notifier<bool> {
  @override
  bool build() => false;

  void emit(bool playing) => state = playing;
}

final _playingProvider = NotifierProvider<_Playing, bool>(_Playing.new);

class _Player extends MusicPlayerController {
  _Player(this._emit);

  final void Function(bool) _emit;
  final _toggles = <bool>[];

  @override
  Future<void> setShuffle(bool enabled) async {}

  @override
  Future<void> togglePlayPause(bool isPlaying) async {
    _toggles.add(isPlaying);
    _emit(!isPlaying);
  }
}

class _Playback extends PlaybackController {
  _Playback(this._start);

  final void Function(LocalLibraryItem) _start;
  final _started = <String>[];

  @override
  Future<void> playLocalLibraryQueue(
    List<LocalLibraryItem> items, {
    required LocalLibraryItem startItem,
  }) async {
    _started.add(startItem.id);
    _start(startItem);
  }
}

/// The current row's equalizer glyph loops while playing, so this settles
/// with bounded pumps instead of waiting for an idle frame.
/// Settles frames and lets real time pass, since a play checks the file
/// through the platform channel before it starts.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 5)),
    );
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  for (final tapPlays in [true]) {
    testWidgets(
      'local album controls pause and resume without restarting '
      '(tapPlays: $tapPlays)',
      (tester) => _pauseAndResume(tester, tapPlays: tapPlays),
    );
  }
}

/// With [tapPlays] the row tap does what the row's play circle does.
Future<void> _pauseAndResume(
  WidgetTester tester, {
  required bool tapPlays,
}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(430, 1200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final media = StreamController<MediaItem?>();
  addTearDown(media.close);
  late final ProviderContainer container;
  void emitPlaying(bool playing) =>
      container.read(_playingProvider.notifier).emit(playing);
  final player = _Player(emitPlaying);
  final playback = _Playback((track) {
    media.add(
      MediaItem(
        id: track.id,
        title: track.trackName,
        extras: {'source': track.filePath},
      ),
    );
    emitPlaying(true);
  });
  final tracks = [
    for (final number in [1, 2])
      LocalLibraryItem(
        id: '$number',
        trackName: 'Song $number',
        artistName: 'Artist',
        albumName: 'Album',
        filePath: 'content://library/song-$number.flac',
        trackNumber: number,
        scannedAt: DateTime(2026),
      ),
  ];
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) => media.stream,
        ),
        playbackPlayingProvider.overrideWith(
          (ref) => ref.watch(_playingProvider),
        ),
        musicPlayerControllerProvider.overrideWithValue(player),
        playbackProvider.overrideWith(() => playback),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: LocalAlbumScreen(
          albumName: 'Album',
          artistName: 'Artist',
          tracks: tracks,
        ),
      ),
    ),
  );
  container = ProviderScope.containerOf(
    tester.element(find.byType(LocalAlbumScreen)),
  );
  await _settle(tester);
  final header = find.byType(AlbumPlayActions);
  Finder row(int number) => find.byWidgetPredicate(
    (widget) => widget is TrackCard && widget.title == 'Song $number',
  );
  Finder rowButton(int number) => tapPlays
      ? find.text('Song $number')
      : find.descendant(of: row(number), matching: find.byType(IconButton));
  // Every row keeps its play circle, which names the action in its tooltip.
  const rowGlyph = findsOneWidget;
  const pauseIcon = CupertinoIcons.pause_fill;

  await tester.tap(find.descendant(of: header, matching: find.text('Play')));
  await _settle(tester);
  expect(playback._started, ['1']);
  expect(
    find.descendant(of: header, matching: find.text('Pause')),
    findsOneWidget,
  );
  expect(
    find.descendant(of: header, matching: findAppIcon(pauseIcon)),
    findsOneWidget,
  );
  expect(
    find.descendant(of: row(1), matching: findByTooltip('Pause')),
    rowGlyph,
  );
  expect(
    find.descendant(of: row(2), matching: findByTooltip('Play')),
    rowGlyph,
  );

  await tester.tap(find.descendant(of: header, matching: find.text('Pause')));
  await _settle(tester);
  expect(player._toggles, [true]);
  expect(
    find.descendant(of: header, matching: find.text('Play')),
    findsOneWidget,
  );
  expect(
    find.descendant(of: row(1), matching: findByTooltip('Play')),
    rowGlyph,
  );

  await tester.ensureVisible(rowButton(1));
  await tester.tap(rowButton(1));
  await _settle(tester);
  expect(player._toggles, [true, false]);
  expect(playback._started, ['1']);
  expect(
    find.descendant(of: row(1), matching: findByTooltip('Pause')),
    rowGlyph,
  );

  await tester.ensureVisible(rowButton(2));
  await tester.tap(rowButton(2));
  await _settle(tester);
  expect(playback._started, ['1', '2']);
  expect(
    find.descendant(of: row(1), matching: findByTooltip('Play')),
    rowGlyph,
  );
  expect(
    find.descendant(of: row(2), matching: findByTooltip('Pause')),
    rowGlyph,
  );
  await tester.tap(rowButton(2));
  await _settle(tester);
  expect(player._toggles, [true, false, true]);
  expect(playback._started, ['1', '2']);

  // A queue started from a playlist uses its file path as the media ID.
  media.add(MediaItem(id: tracks.first.filePath, title: 'Song 1'));
  emitPlaying(true);
  await _settle(tester);
  expect(
    find.descendant(of: row(1), matching: findByTooltip('Pause')),
    rowGlyph,
  );
  await tester.tap(rowButton(1));
  await _settle(tester);
  expect(player._toggles.last, isTrue);
  expect(playback._started, ['1', '2']);

  // A matching row ID must not pause a different file in another album.
  media.add(
    const MediaItem(id: '1', title: 'Other', extras: {'source': '/other.flac'}),
  );
  emitPlaying(true);
  await _settle(tester);
  expect(
    find.descendant(of: header, matching: find.text('Play')),
    findsOneWidget,
  );
  expect(findAppIcon(pauseIcon), findsNothing);
  await tester.ensureVisible(header);
  await tester.tap(find.descendant(of: header, matching: find.text('Play')));
  await _settle(tester);
  expect(playback._started, ['1', '2', '1']);
  expect(tester.takeException(), isNull);
  await tester.pumpWidget(const SizedBox.shrink());
}
