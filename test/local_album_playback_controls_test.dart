import 'dart:async';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_detail_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/album_track_tile.dart';

import 'support/app_icon_finder.dart';

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
  Future<void> playLibraryQueue(
    List<UnifiedLibraryItem> items, {
    required UnifiedLibraryItem startItem,
  }) async {
    _started.add(startItem.localItem!.id);
    _start(startItem.localItem!);
  }
}

/// The current row's equalizer glyph loops while playing, so this settles
/// with bounded pumps instead of waiting for an idle frame.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  final albumKey = LibraryDatabase.albumKeyFor('Album', null, 'Artist');

  testWidgets('local album controls pause and resume without restarting', (
    tester,
  ) async {
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
          libraryAlbumItemsProvider(albumKey).overrideWith(
            (ref) async => [
              for (final track in tracks)
                UnifiedLibraryItem.fromLocalLibrary(track),
            ],
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AlbumScreen.library(albumKey: albumKey),
        ),
      ),
    );
    container = ProviderScope.containerOf(
      tester.element(find.byType(AlbumScreen)),
    );
    await _settle(tester);
    final header = find.byType(AlbumPlayActions);
    Finder row(int number) => find.byWidgetPredicate(
      (widget) =>
          widget is AlbumTrackTile && widget.trackName == 'Song $number',
    );
    Finder rowButton(int number) => find
        .descendant(of: row(number), matching: find.byType(IconButton))
        .first;
    final pauseIcon = CupertinoIcons.pause_fill;
    // Rows end with an outlined circle.
    final rowPauseIcon = CupertinoIcons.pause_circle;
    final rowPlayIcon = CupertinoIcons.play_circle;

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
      find.descendant(of: row(1), matching: find.byIcon(rowPauseIcon)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row(2), matching: find.byIcon(rowPlayIcon)),
      findsOneWidget,
    );

    await tester.tap(find.descendant(of: header, matching: find.text('Pause')));
    await _settle(tester);
    expect(player._toggles, [true]);
    expect(
      find.descendant(of: header, matching: find.text('Play')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row(1), matching: find.byTooltip('Play')),
      findsOneWidget,
    );

    await tester.ensureVisible(rowButton(1));
    await tester.tap(rowButton(1));
    await _settle(tester);
    expect(player._toggles, [true, false]);
    expect(playback._started, ['1']);
    expect(
      find.descendant(of: row(1), matching: find.byTooltip('Pause')),
      findsOneWidget,
    );

    await tester.ensureVisible(rowButton(2));
    await tester.tap(rowButton(2));
    await _settle(tester);
    expect(playback._started, ['1', '2']);
    expect(
      find.descendant(of: row(1), matching: find.byIcon(rowPlayIcon)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: row(2), matching: find.byIcon(rowPauseIcon)),
      findsOneWidget,
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
      find.descendant(of: row(1), matching: find.byIcon(rowPauseIcon)),
      findsOneWidget,
    );
    await tester.tap(rowButton(1));
    await _settle(tester);
    expect(player._toggles.last, isTrue);
    expect(playback._started, ['1', '2']);

    // A matching row ID must not pause a different file in another album.
    media.add(
      const MediaItem(
        id: '1',
        title: 'Other',
        extras: {'source': '/other.flac'},
      ),
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
  });
}
