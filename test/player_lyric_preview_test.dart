import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/song_lyrics_offset_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/widgets/lyric_line_scroller.dart';
import 'package:sistrum/widgets/player_lyric_preview.dart';
import 'package:sistrum/widgets/player_lyrics.dart';

import 'support/fixed_settings.dart';

const _lrc = '''
[00:00.00]
[00:05.00]First example line
[00:10.00]Second example line
''';

const _enabled = AppSettings(playerShowLyricSnippet: true);

final _item = MediaItem(
  id: 'example-song',
  title: 'Example Song',
  artist: 'Example Artist',
  duration: const Duration(minutes: 3),
  extras: const {'source': '/music/example.flac'},
);

Future<PlayerMetadataController> _metadata([ParsedLyrics? lyrics]) async {
  final parsed = lyrics ?? LyricsParser.parse(_lrc);
  final metadata = PlayerMetadataController(
    parseLyrics: (_) async => parsed,
    readMetadata: (_) async => {
      'title': 'Example Song',
      'artist': 'Example Artist',
      'lyrics': _lrc,
    },
  );
  await metadata.load(_item);
  return metadata;
}

PlaybackState _at(
  Duration position, {
  bool playing = true,
  AudioProcessingState processingState = AudioProcessingState.ready,
}) => PlaybackState(
  playing: playing,
  processingState: processingState,
  updatePosition: position,
);

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  Future<
    ({
      ProviderContainer container,
      StreamController<PlaybackState> playback,
      PlaybackSeekPreview seekPreview,
    })
  >
  pumpPreview(
    WidgetTester tester, {
    required PlayerMetadataController metadata,
    AppSettings settings = _enabled,
    VoidCallback? onTap,
  }) async {
    final playback = StreamController<PlaybackState>.broadcast();
    addTearDown(playback.close);
    final seekPreview = PlaybackSeekPreview();
    final container = ProviderContainer(
      overrides: [
        playbackStateProvider.overrideWith((ref) => playback.stream),
        settingsProvider.overrideWith(() => FixedSettingsNotifier(settings)),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: PlayerLyricPreview(
              metadata: metadata,
              seekPreview: seekPreview,
              colorScheme: const ColorScheme.dark(),
              onTap: onTap,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    return (container: container, playback: playback, seekPreview: seekPreview);
  }

  testWidgets('stays hidden with the default settings', (tester) async {
    expect(const AppSettings().playerShowLyricSnippet, isFalse);
    final view = await pumpPreview(
      tester,
      metadata: await _metadata(),
      settings: const AppSettings(),
    );
    view.playback.add(_at(const Duration(seconds: 6)));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsNothing);
    expect(find.text('•••'), findsNothing);
    expect(find.byIcon(CupertinoIcons.forward), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('follows the sung line and opens lyrics ', (tester) async {
    var opened = 0;
    final view = await pumpPreview(
      tester,
      metadata: await _metadata(),
      onTap: () => opened++,
    );

    view.playback.add(_at(const Duration(seconds: 1), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('•••'), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.forward), findsOneWidget);

    view.playback.add(_at(const Duration(seconds: 6), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);
    expect(
      find.bySemanticsLabel('Lyrics'),
      findsOneWidget,
      reason: 'The line reads as one Lyrics button',
    );

    await tester.tap(find.text('First example line'));
    await tester.pump();
    expect(opened, 1);

    view.playback.add(_at(const Duration(seconds: 11), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('Second example line'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('moves to the next line on time without a new state', (
    tester,
  ) async {
    final view = await pumpPreview(tester, metadata: await _metadata());
    view.playback.add(_at(const Duration(milliseconds: 4500)));
    await tester.pump();
    expect(find.text('•••'), findsOneWidget);

    // The extrapolated position reaches the line 80 ms before it starts.
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('First example line'), findsNothing);
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);

    // Pausing stops the line timer.
    view.playback.add(_at(const Duration(milliseconds: 9500), playing: false));
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);
    expect(find.text('Second example line'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('keeps the next line when playback reports a little late', (
    tester,
  ) async {
    final view = await pumpPreview(tester, metadata: await _metadata());
    view.playback.add(_at(const Duration(milliseconds: 4500)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);

    // A position report from before the line starts, as one arriving late
    // over a network is, keeps the line instead of stepping back.
    view.playback.add(_at(const Duration(milliseconds: 4800)));
    for (var frame = 0; frame < 6; frame++) {
      await tester.pump(const Duration(milliseconds: 16));
      expect(find.text('•••'), findsNothing, reason: 'frame $frame went back');
      expect(find.text('First example line'), findsOneWidget);
    }

    // A seek back moves at once.
    view.playback.add(_at(const Duration(seconds: 2)));
    await tester.pumpAndSettle();
    expect(find.text('•••'), findsOneWidget);
    expect(find.text('First example line'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('applies the lyrics timing offset', (tester) async {
    final view = await pumpPreview(tester, metadata: await _metadata());
    view.playback.add(_at(const Duration(milliseconds: 5500), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);

    // One second later: at 5.5 s the first line, now at 6 s, has not begun.
    await view.container
        .read(themeProvider.notifier)
        .setLyricsOffsetMs(1000, persist: false);
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsNothing);
    expect(find.text('•••'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('adds the song\'s own timing to the app-wide one', (
    tester,
  ) async {
    final view = await pumpPreview(tester, metadata: await _metadata());
    view.playback.add(_at(const Duration(milliseconds: 5800), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);

    // Another song's offset changes nothing here.
    final songs = view.container.read(songLyricsOffsetsProvider.notifier);
    await songs.setOffsetMs(
      songLyricsOffsetKey('Other Song', 'Example Artist'),
      2000,
    );
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsOneWidget);

    // 0.5 s app-wide and 0.5 s for this song: at 5.8 s the first line, now
    // at 6 s, has not begun. The key ignores case and spaces.
    await view.container
        .read(themeProvider.notifier)
        .setLyricsOffsetMs(500, persist: false);
    await songs.setOffsetMs(
      songLyricsOffsetKey(' example song ', 'EXAMPLE ARTIST'),
      500,
      persist: false,
    );
    await tester.pumpAndSettle();
    expect(find.text('First example line'), findsNothing);
    expect(find.text('•••'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('follows the seek preview while dragging', (tester) async {
    final view = await pumpPreview(tester, metadata: await _metadata());
    view.playback.add(_at(const Duration(seconds: 1), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('•••'), findsOneWidget);

    final drag = view.seekPreview.begin(const Duration(seconds: 11));
    await tester.pumpAndSettle();
    expect(find.text('Second example line'), findsOneWidget);

    // Releasing without a seek returns to the playback position.
    view.seekPreview.end(drag);
    await tester.pumpAndSettle();
    expect(find.text('•••'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('slides a long line with the singing instead of cutting it', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    const long =
        'This example line is much too long to fit on a narrow phone '
        'screen at once';
    final lyrics = LyricsParser.parse('''
[00:00.00]Short line
[00:05.00]$long
[00:15.00]
''');
    final view = await pumpPreview(tester, metadata: await _metadata(lyrics));
    Finder slide() => find.descendant(
      of: find.byType(LyricLineScroller),
      matching: find.byType(Transform),
    );
    double offset() =>
        tester.widget<Transform>(slide()).transform.getTranslation().x;

    // A line that fits stays still.
    view.playback.add(_at(const Duration(seconds: 1), playing: false));
    await tester.pumpAndSettle();
    expect(find.text('Short line'), findsOneWidget);
    expect(slide(), findsNothing);

    view.playback.add(_at(const Duration(seconds: 5)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text(long), findsOneWidget);
    expect(offset(), 0, reason: 'The line starts at its beginning');

    // It holds while its first words are sung, then follows the singing.
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 2));
    final sliding = offset();
    expect(sliding, lessThan(0));

    // Paused, the line holds where it is.
    view.playback.add(_at(const Duration(milliseconds: 9300), playing: false));
    await tester.pumpAndSettle();
    final paused = offset();
    await tester.pump(const Duration(seconds: 2));
    expect(offset(), paused);

    // The line lasts until the next one; near its end its last words are
    // in view.
    view.playback.add(_at(const Duration(milliseconds: 14500), playing: false));
    await tester.pumpAndSettle();
    final last = offset();
    expect(last, lessThan(paused));
    final box = tester.renderObject<RenderBox>(find.byType(LyricLineScroller));
    final text = tester.renderObject<RenderBox>(find.text(long));
    expect(
      text.size.width + last,
      moreOrLessEquals(box.size.width, epsilon: 0.5),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('slides a long right-to-left line towards its end on the '
      'left ', (tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    // Invented: "the blue door at the end of the old garden opens onto a
    // long road between the trees".
    const long =
        'الباب الأزرق في آخر الحديقة القديمة يفتح على طريق طويل بين '
        'الأشجار';
    final lyrics = LyricsParser.parse('''
[00:00.00]Short line
[00:05.00]$long
[00:15.00]
''');
    final view = await pumpPreview(tester, metadata: await _metadata(lyrics));
    double offset() => tester
        .widget<Transform>(
          find.descendant(
            of: find.byType(LyricLineScroller),
            matching: find.byType(Transform),
          ),
        )
        .transform
        .getTranslation()
        .x;
    Rect line() => tester.getRect(
      find.ancestor(
        of: find.text(long),
        matching: find.byType(LyricLineScroller),
      ),
    );
    Rect text() => tester.getRect(find.text(long));

    view.playback.add(_at(const Duration(seconds: 5)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      tester.renderObject<RenderParagraph>(find.text(long)).textDirection,
      TextDirection.rtl,
    );
    // The line starts at its beginning, on the right.
    expect(offset(), 0);
    expect(text().right, moreOrLessEquals(line().right, epsilon: 0.5));
    expect(text().left, lessThan(line().left));

    // Its first words sung, it moves right to bring the next ones in.
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 2));
    final sliding = offset();
    expect(sliding, greaterThan(0));

    // Near its end the line's last words, on its left, are in view.
    view.playback.add(_at(const Duration(milliseconds: 14500), playing: false));
    await tester.pumpAndSettle();
    expect(offset(), greaterThan(sliding));
    expect(text().left, moreOrLessEquals(line().left, epsilon: 0.5));
    // The row around it keeps the app's direction.
    expect(tester.getCenter(find.byType(Icon)).dx, greaterThan(line().right));
    expect(tester.takeException(), isNull);
  });

  testWidgets('keeps sliding forward when playback reports a later time late', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    const long =
        'This example line is much too long to fit on a narrow phone '
        'screen at once';
    final view = await pumpPreview(
      tester,
      metadata: await _metadata(
        LyricsParser.parse('[00:05.00]$long\n[00:15.00]\n'),
      ),
    );
    double offset() => tester
        .widget<Transform>(
          find.descendant(
            of: find.byType(LyricLineScroller),
            matching: find.byType(Transform),
          ),
        )
        .transform
        .getTranslation()
        .x;

    view.playback.add(_at(const Duration(seconds: 5)));
    await tester.pump();
    for (var step = 0; step < 20; step++) {
      await tester.pump(const Duration(milliseconds: 200));
    }
    final before = offset();
    expect(before, lessThan(0));

    // Playback, 9 s in, reports a position 200 ms behind the slide.
    view.playback.add(_at(const Duration(milliseconds: 8800)));
    var last = before;
    for (var step = 0; step < 10; step++) {
      await tester.pump(const Duration(milliseconds: 16));
      final now = offset();
      expect(now, lessThanOrEqualTo(last), reason: 'frame $step went back');
      last = now;
    }
    await tester.pump(const Duration(milliseconds: 500));
    expect(offset(), lessThan(last), reason: 'It keeps moving forward');
    expect(tester.takeException(), isNull);
  });

  testWidgets('sweeps word-timed lines', (tester) async {
    final lyrics = ParsedLyrics(
      synced: true,
      wordSynced: true,
      plainText: 'one word at a time',
      lines: [
        LyricLine(
          time: const Duration(seconds: 4),
          end: const Duration(seconds: 8),
          text: 'one word at a time',
          words: [
            for (final (index, word) in [
              'one ',
              'word ',
              'at ',
              'a ',
              'time',
            ].indexed)
              LyricWord(
                time: Duration(milliseconds: 4000 + index * 800),
                end: Duration(milliseconds: 4800 + index * 800),
                text: word,
              ),
          ],
        ),
      ],
    );
    final view = await pumpPreview(tester, metadata: await _metadata(lyrics));
    view.playback.add(_at(const Duration(seconds: 5)));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byType(WordTimedLyricLine), findsOneWidget);
    expect(find.byIcon(CupertinoIcons.forward), findsOneWidget);

    view.playback.add(_at(const Duration(seconds: 5), playing: false));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('a word-timed line without a translation takes the same room '
      'as with one, so the row does not jump', (tester) async {
    const text = 'Hello there my friend';
    LyricLine line({String? translation, bool timed = true}) => LyricLine(
      time: const Duration(seconds: 4),
      end: const Duration(seconds: 8),
      text: text,
      translation: translation,
      words: timed
          ? [
              for (final (index, word) in [
                'Hello ',
                'there ',
                'my ',
                'friend',
              ].indexed)
                LyricWord(
                  time: Duration(milliseconds: 4000 + index * 900),
                  end: Duration(milliseconds: 4900 + index * 900),
                  text: word,
                ),
            ]
          : const [],
    );
    Future<({Size size, Rect arrow})> measure(LyricLine sung) async {
      final view = await pumpPreview(
        tester,
        metadata: await _metadata(
          ParsedLyrics(
            synced: true,
            wordSynced: sung.hasWordTiming,
            plainText: text,
            lines: [sung],
          ),
        ),
      );
      view.playback.add(_at(const Duration(seconds: 5), playing: false));
      await tester.pumpAndSettle();
      final result = (
        size: tester.getSize(find.byType(PlayerLyricPreview)),
        arrow: tester.getRect(find.byIcon(CupertinoIcons.forward)),
      );
      await tester.pumpWidget(const SizedBox());
      return result;
    }

    final plain = await measure(line(timed: false));
    final timedAlone = await measure(line());
    final translated = await measure(line(translation: 'Hola amigo mio'));

    expect(timedAlone.size.height, plain.size.height);
    expect(timedAlone.size.width, plain.size.width);
    expect(timedAlone.arrow.left, closeTo(plain.arrow.left, 0.01));
    expect(translated.size.height, plain.size.height);
    expect(tester.takeException(), isNull);
  });

  group('song change', () {
    MediaItem song(String id, {String? resolved}) => MediaItem(
      id: id,
      title: 'Example $id',
      artist: 'Example Artist',
      duration: const Duration(minutes: 3),
      // A network source keeps the lyric file stores off the disk.
      extras: {'source': 'network://example/$id', 'resolvedSource': ?resolved},
    );
    String lrcFor(String id) => '[00:05.00]Line of $id\n[00:10.00]More of $id';

    late Map<String, Completer<String>> reads;
    PlayerMetadataController controller() {
      reads = {};
      final metadata = PlayerMetadataController(
        parseLyrics: (raw) async => LyricsParser.parse(raw ?? ''),
        readMetadata: (path) async {
          final lyrics = await (reads[path] ??= Completer<String>()).future;
          return {'title': 'Example', 'lyrics': lyrics};
        },
      );
      addTearDown(metadata.dispose);
      return metadata;
    }

    /// Lets the song at [path] finish loading with [lyrics].
    void answer(String path, String lyrics) =>
        (reads[path] ??= Completer<String>()).complete(lyrics);

    double height(WidgetTester tester) =>
        tester.getSize(find.byType(PlayerLyricPreview)).height;

    /// The row's height in every frame of the next [milliseconds].
    Future<List<double>> heights(WidgetTester tester, int milliseconds) async {
      final seen = <double>[];
      for (var elapsed = 0; elapsed < milliseconds; elapsed += 16) {
        await tester.pump(const Duration(milliseconds: 16));
        seen.add(height(tester));
      }
      return seen;
    }

    testWidgets('between two songs with lyrics the row never moves '
        '', (tester) async {
      final metadata = controller();
      answer('network://example/a', lrcFor('a'));
      await metadata.load(song('a'));
      final view = await pumpPreview(tester, metadata: metadata);
      view.playback.add(_at(const Duration(seconds: 6), playing: false));
      await tester.pumpAndSettle();
      expect(find.text('Line of a'), findsOneWidget);
      final open = height(tester);
      expect(open, greaterThan(20));

      // The next song starts loading: the row stays, its line leaves.
      unawaited(metadata.load(song('b')));
      expect(await heights(tester, 400), everyElement(open));
      expect(find.text('Line of a'), findsNothing);
      expect(find.text('•••'), findsNothing);

      answer('network://example/b', lrcFor('b'));
      expect(await heights(tester, 400), everyElement(open));
      expect(find.text('Line of b'), findsOneWidget);

      // The same song read again, as when its file replaces the catalog
      // entry: the line stays on screen throughout.
      unawaited(metadata.load(song('b', resolved: '/cache/b.flac')));
      for (var frame = 0; frame < 20; frame++) {
        await tester.pump(const Duration(milliseconds: 16));
        expect(height(tester), open);
        expect(find.text('Line of b'), findsOneWidget);
      }
      answer('/cache/b.flac', lrcFor('b'));
      expect(await heights(tester, 300), everyElement(open));
      expect(find.text('Line of b'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the row closes and opens once, without a bounce '
        '', (tester) async {
      final metadata = controller();
      answer('network://example/a', lrcFor('a'));
      await metadata.load(song('a'));
      final view = await pumpPreview(tester, metadata: metadata);
      view.playback.add(_at(const Duration(seconds: 6), playing: false));
      await tester.pumpAndSettle();
      final open = height(tester);

      // A song without lyrics: nothing moves while it loads.
      unawaited(metadata.load(song('plain')));
      expect(await heights(tester, 300), everyElement(open));
      answer('network://example/plain', '');
      final closing = await heights(tester, 1600);
      expect(closing.first, open);
      expect(closing.last, 0);
      for (var frame = 1; frame < closing.length; frame++) {
        expect(closing[frame], lessThanOrEqualTo(closing[frame - 1]));
      }
      expect(find.text('•••'), findsNothing);

      // Back to a song with lyrics: closed while it loads, then it opens.
      unawaited(metadata.load(song('c')));
      expect(await heights(tester, 300), everyElement(0));
      answer('network://example/c', lrcFor('c'));
      final opening = await heights(tester, 1000);
      expect(opening.last, open);
      for (var frame = 1; frame < opening.length; frame++) {
        expect(opening[frame], greaterThanOrEqualTo(opening[frame - 1]));
        expect(opening[frame], lessThanOrEqualTo(open));
      }
      expect(find.text('Line of c'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}
