import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/player_lyrics.dart';
import 'package:sistrum/widgets/player_lyrics_section.dart';

const _arabic = 'كان عندك كام سنة لما عرفت';
const _english = 'hello there general kenobi this line wraps across rows';

LyricWord _word(String text, int start, int end) => LyricWord(
  time: Duration(seconds: start),
  end: Duration(seconds: end),
  text: text,
);

ParsedLyrics _lyrics(LyricLine line) => ParsedLyrics(
  synced: true,
  wordSynced: true,
  plainText: line.text,
  lines: [line],
);

Future<void> _pump(
  WidgetTester tester,
  ParsedLyrics lyrics, {
  required Duration position,
  double width = 320,
  bool showSupplements = false,
  ColorScheme? colorScheme,
}) async {
  tester.view.physicalSize = const Size(900, 1200);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final playback = StreamController<PlaybackState>.broadcast();
  addTearDown(playback.close);
  final preview = ValueNotifier<Duration?>(position);
  addTearDown(preview.dispose);
  final theme = MornyeTheme.build(Brightness.dark);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [playbackStateProvider.overrideWith((ref) => playback.stream)],
      child: MaterialApp(
        theme: theme,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: width,
              height: 800,
              child: SyncedLyricsView(
                lyrics: lyrics,
                seekPreview: preview,
                colorScheme: colorScheme ?? theme.colorScheme,
                isActive: true,
                showPronunciation: showSupplements,
                showTranslation: showSupplements,
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// The x offset of the first character: near the right edge when the
/// paragraph is right-aligned, near zero when it is left-aligned.
double _startX(WidgetTester tester, Finder text) {
  final paragraph = tester.renderObject<RenderParagraph>(text);
  return paragraph
      .getOffsetForCaret(const TextPosition(offset: 0), Rect.zero)
      .dx;
}

void main() {
  testWidgets('wrapped right-to-left lines are right-aligned and wrap', (
    tester,
  ) async {
    await _pump(
      tester,
      _lyrics(
        const LyricLine(
          time: Duration(seconds: 10),
          end: Duration(seconds: 20),
          text: _arabic,
        ),
      ),
      position: const Duration(seconds: 1),
    );
    final arabic = find.text(_arabic);
    expect(arabic, findsOneWidget);
    final paragraph = tester.renderObject<RenderParagraph>(arabic);
    expect(paragraph.textDirection, TextDirection.rtl);
    final rows = paragraph
        .getBoxesForSelection(
          const TextSelection(baseOffset: 0, extentOffset: _arabic.length),
        )
        .map((box) => box.top.round())
        .toSet();
    expect(rows.length, greaterThan(1));
    expect(_startX(tester, arabic), greaterThan(paragraph.size.width / 2));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'translations and pronunciations of right-to-left lines follow it',
    (tester) async {
      const translation = 'مرحبا بالعالم';
      const romanization = 'kan andak';
      await _pump(
        tester,
        _lyrics(
          const LyricLine(
            time: Duration(seconds: 10),
            end: Duration(seconds: 20),
            text: _arabic,
            translation: translation,
            romanization: romanization,
          ),
        ),
        position: const Duration(seconds: 11),
        showSupplements: true,
      );
      final translated = find.text(translation);
      expect(translated, findsOneWidget);
      final translationParagraph = tester.renderObject<RenderParagraph>(
        translated,
      );
      expect(translationParagraph.textDirection, TextDirection.rtl);
      expect(
        _startX(tester, translated),
        greaterThan(translationParagraph.size.width / 2),
      );

      // Latin pronunciation reads left to right but sits on the right edge.
      final pronunciation = find.text(romanization);
      expect(pronunciation, findsOneWidget);
      expect(
        tester.renderObject<RenderParagraph>(pronunciation).textDirection,
        TextDirection.ltr,
      );
      expect(tester.widget<Text>(pronunciation).textAlign, TextAlign.right);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('left-to-right lines keep their left alignment', (tester) async {
    await _pump(
      tester,
      _lyrics(
        const LyricLine(
          time: Duration(seconds: 10),
          end: Duration(seconds: 20),
          text: _english,
        ),
      ),
      position: const Duration(seconds: 1),
    );
    final english = find.text(_english);
    expect(english, findsOneWidget);
    final paragraph = tester.renderObject<RenderParagraph>(english);
    expect(paragraph.textDirection, TextDirection.ltr);
    expect(_startX(tester, english), lessThan(paragraph.size.width / 2));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'the word highlight sweeps from the right edge to the left in Arabic',
    (tester) async {
      // Only the highlight is red, so its pixels can be told apart from the
      // pending text and measured by position.
      final theme = MornyeTheme.build(Brightness.dark);
      final scheme = theme.colorScheme.copyWith(
        onSurface: const Color(0xFFFF0000),
      );
      Future<(double, double)> edgesAt(
        WidgetTester tester,
        String text,
        Duration position,
      ) async {
        await _pump(
          tester,
          ParsedLyrics(
            synced: true,
            wordSynced: true,
            plainText: text,
            lines: [
              LyricLine(
                time: Duration.zero,
                end: const Duration(seconds: 20),
                text: text,
                words: [
                  _word(text.split(' ').first, 0, 10),
                  _word(text.split(' ').last, 10, 20),
                ],
              ),
            ],
          ),
          position: position,
          width: 600,
          colorScheme: scheme,
        );
        return _highlightEdges(tester, text);
      }

      const arabicWords = 'كان عندك';
      // The highlight's leading edge is the boundary between sung and unsung
      // letters: its leftmost and rightmost red pixels show where it is.
      final arabicQuarter = await edgesAt(
        tester,
        arabicWords,
        const Duration(milliseconds: 12500),
      );
      final arabicThreeQuarters = await edgesAt(
        tester,
        arabicWords,
        const Duration(milliseconds: 17500),
      );
      // Centre of the second word, measured by its own box in the painter.
      final arabicMid = _secondWordMid(tester, arabicWords);
      expect(arabicQuarter.$1, greaterThan(arabicMid));
      expect(arabicThreeQuarters.$1, lessThan(arabicMid));
      expect(arabicThreeQuarters.$1, lessThan(arabicQuarter.$1));

      // Control: a Latin word sweeps the other way.
      const latinWords = 'hello world';
      final latinQuarter = await edgesAt(
        tester,
        latinWords,
        const Duration(milliseconds: 12500),
      );
      final latinThreeQuarters = await edgesAt(
        tester,
        latinWords,
        const Duration(milliseconds: 17500),
      );
      final latinMid = _secondWordMid(tester, latinWords);
      expect(latinQuarter.$2, lessThan(latinMid));
      expect(latinThreeQuarters.$2, greaterThan(latinMid));
      expect(latinThreeQuarters.$2, greaterThan(latinQuarter.$2));
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('a wrapped right-to-left word sweeps row by row from the right', (
    tester,
  ) async {
    final theme = MornyeTheme.build(Brightness.dark);
    final scheme = theme.colorScheme.copyWith(
      onSurface: const Color(0xFFFF0000),
    );
    Future<void> pumpAt(Duration position) => _pump(
      tester,
      ParsedLyrics(
        synced: true,
        wordSynced: true,
        plainText: _arabic,
        lines: [
          LyricLine(
            time: Duration.zero,
            end: const Duration(seconds: 20),
            text: _arabic,
            words: [_word(_arabic, 0, 20)],
          ),
        ],
      ),
      position: position,
      width: 140,
      colorScheme: scheme,
    );

    await pumpAt(const Duration(seconds: 5));
    final boxes = _firstSegmentBoxes(tester, _arabic);
    expect(boxes.length, greaterThanOrEqualTo(2));
    final painter = tester.widget<CustomPaint>(_paintFinder(_arabic)).painter!;
    // ignore: avoid_dynamic_calls
    final directions =
        (painter as dynamic).segmentDirections as List<List<TextDirection>>;
    // Glow and lift are on for this sweep, so the glow and feather paths run.
    // ignore: avoid_dynamic_calls
    expect((painter as dynamic).glowSigma as double, greaterThan(0));
    // ignore: avoid_dynamic_calls
    expect((painter as dynamic).highlightLift as double, greaterThan(0));
    expect(directions.first, everyElement(TextDirection.rtl));
    // Boxes run row by row, and right to left within each row.
    for (var i = 1; i < boxes.length; i++) {
      final previous = boxes[i - 1];
      final current = boxes[i];
      if ((current.top - previous.top).abs() < 1) {
        expect(current.left, lessThan(previous.left));
      } else {
        expect(current.top, greaterThan(previous.top));
      }
    }

    // Ink extents depend on glyph placement, so compare which rows hold any
    // sung pixels: the sweep fills rows from the top, so the sung rows grow
    // as a prefix that starts at the first row.
    Set<int> sungRows(Uint8List pixels, int width) => {
      for (var row = 0; row < boxes.length; row++)
        if (_redExtent(pixels, width, boxes[row]) != null) row,
    };
    final (quarterPixels, quarterWidth) = await _paintedPixels(tester, _arabic);
    final quarterRows = sungRows(quarterPixels, quarterWidth);
    expect(quarterRows, contains(0));
    expect(quarterRows, isNot(contains(boxes.length - 1)));

    // Three quarters: more rows are sung, still from the top down.
    await pumpAt(const Duration(seconds: 15));
    final (pixels, width) = await _paintedPixels(tester, _arabic);
    final threeQuarterRows = sungRows(pixels, width);
    expect(threeQuarterRows, containsAll(quarterRows));
    expect(threeQuarterRows.length, greaterThan(quarterRows.length));
    expect(
      threeQuarterRows,
      equals({for (var row = 0; row < threeQuarterRows.length; row++) row}),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'a mixed Latin and Arabic word sweeps its Latin part left to right',
    (tester) async {
      const text = 'Hello مرحبا';
      final theme = MornyeTheme.build(Brightness.dark);
      final scheme = theme.colorScheme.copyWith(
        onSurface: const Color(0xFFFF0000),
      );
      await _pump(
        tester,
        ParsedLyrics(
          synced: true,
          wordSynced: true,
          plainText: text,
          lines: [
            LyricLine(
              time: Duration.zero,
              end: const Duration(seconds: 20),
              text: text,
              words: [_word(text, 0, 20)],
            ),
          ],
        ),
        position: const Duration(seconds: 5),
        width: 600,
        colorScheme: scheme,
      );
      final boxes = _firstSegmentBoxes(tester, text);
      final painter = tester.widget<CustomPaint>(_paintFinder(text)).painter!;
      // ignore: avoid_dynamic_calls
      final directions =
          (painter as dynamic).segmentDirections as List<List<TextDirection>>;
      // The Latin run is first in the paragraph; the Arabic run follows it.
      expect(boxes.length, greaterThanOrEqualTo(2));
      expect(directions.first.first, TextDirection.ltr);
      expect(directions.first.last, TextDirection.rtl);
      expect(boxes.first.left, lessThan(boxes.last.left));

      // A quarter of the word is sung: the red starts at the Latin run's left
      // edge, not at the Arabic run on the right.
      final (pixels, width) = await _paintedPixels(tester, text);
      final latin = _redExtent(pixels, width, boxes.first);
      expect(latin, isNotNull);
      expect(latin!.$1, closeTo(boxes.first.left, 2));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'a mixed word in an Arabic line reveals in the order it is shown',
    (tester) async {
      const text = 'مرحبا Hello';
      final theme = MornyeTheme.build(Brightness.dark);
      final scheme = theme.colorScheme.copyWith(
        onSurface: const Color(0xFFFF0000),
      );
      await _pump(
        tester,
        ParsedLyrics(
          synced: true,
          wordSynced: true,
          plainText: text,
          lines: [
            LyricLine(
              time: Duration.zero,
              end: const Duration(seconds: 20),
              text: text,
              words: [_word(text, 0, 20)],
            ),
          ],
        ),
        position: const Duration(seconds: 5),
        width: 600,
        colorScheme: scheme,
      );
      final boxes = _firstSegmentBoxes(tester, text);
      expect(boxes.length, greaterThanOrEqualTo(2));
      // In an Arabic line the Arabic run is shown rightmost and read first.
      expect(boxes.first.left, greaterThan(boxes.last.left));

      final (pixels, width) = await _paintedPixels(tester, text);
      final first = _redExtent(pixels, width, boxes.first);
      expect(first, isNotNull);
      expect(first!.$2, closeTo(boxes.first.right, 6));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('plain lyrics give each line its own direction', (tester) async {
    const latin = 'Hello there';
    const arabic = 'مرحبا بالعالم';
    final metadata = PlayerMetadataController(
      parseLyrics: (raw) async => LyricsParser.parse(raw),
      readMetadata: (_) async => {
        'title': 'Track',
        'lyrics': '$latin\n$arabic',
      },
    );
    addTearDown(metadata.dispose);
    await metadata.load(
      const MediaItem(
        id: 'track',
        title: 'Track',
        extras: {'source': '/music/track.flac'},
      ),
    );
    final preview = PlaybackSeekPreview();
    addTearDown(preview.dispose);
    tester.view.physicalSize = const Size(900, 1200);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: SizedBox(
              width: 320,
              height: 800,
              child: PlayerLyricsSection(
                metadata: metadata,
                seekPreview: preview,
                colorScheme: const ColorScheme.dark(),
                isActive: true,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      tester.widget<Text>(find.text(latin)).textDirection,
      TextDirection.ltr,
    );
    expect(
      tester.widget<Text>(find.text(arabic)).textDirection,
      TextDirection.rtl,
    );
    expect(tester.takeException(), isNull);
  });
}

Finder _paintFinder(String text) => find.descendant(
  of: find.byWidgetPredicate(
    (widget) => widget is Semantics && widget.properties.label == text,
  ),
  matching: find.byType(CustomPaint),
);

/// The bounds of the second word, from the painter's own boxes.
Rect _secondWordBox(WidgetTester tester, String text) {
  final painter = tester.widget<CustomPaint>(_paintFinder(text)).painter!;
  // ignore: avoid_dynamic_calls
  final boxes = (painter as dynamic).segmentBoxes as List<List<Rect>>;
  return boxes.last.reduce((a, b) => a.expandToInclude(b));
}

double _secondWordMid(WidgetTester tester, String text) {
  final box = _secondWordBox(tester, text);
  return (box.left + box.right) / 2;
}

/// Painted RGBA pixels of the lyric's sweep, with the painter's width.
Future<(Uint8List, int)> _paintedPixels(
  WidgetTester tester,
  String text,
) async {
  final finder = _paintFinder(text);
  final size = tester.getSize(finder);
  final painter = tester.widget<CustomPaint>(finder).painter!;
  final pixels = (await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    painter.paint(Canvas(recorder), size);
    final picture = recorder.endRecording();
    final image = await picture.toImage(size.width.ceil(), size.height.ceil());
    final bytes = (await image.toByteData(format: ui.ImageByteFormat.rawRgba))!;
    final result = bytes.buffer.asUint8List();
    image.dispose();
    picture.dispose();
    return result;
  }))!;
  return (pixels, size.width.ceil());
}

/// Leftmost and rightmost x of the fully red pixels inside [box], or null.
(double, double)? _redExtent(Uint8List pixels, int width, Rect box) {
  var left = double.infinity;
  var right = double.negativeInfinity;
  for (var y = math.max(0, box.top.floor()); y < box.bottom.ceil(); y++) {
    for (var x = math.max(0, box.left.floor()); x < box.right.ceil(); x++) {
      final i = (y * width + x) * 4;
      final red = pixels[i];
      final green = pixels[i + 1];
      final blue = pixels[i + 2];
      final alpha = pixels[i + 3];
      if (alpha >= 200 && red > 200 && green < 80 && blue < 80) {
        left = math.min(left, x.toDouble());
        right = math.max(right, x.toDouble() + 1);
      }
    }
  }
  return left < right ? (left, right) : null;
}

/// Leftmost and rightmost x of the fully highlighted (red) pixels inside the
/// second word.
Future<(double, double)> _highlightEdges(
  WidgetTester tester,
  String text,
) async {
  final box = _secondWordBox(tester, text);
  final (pixels, width) = await _paintedPixels(tester, text);
  final edges = _redExtent(pixels, width, box);
  expect(edges, isNotNull, reason: 'no highlight drawn for "$text"');
  return edges!;
}

/// The painter's boxes of the first segment, in painter order.
List<Rect> _firstSegmentBoxes(WidgetTester tester, String text) {
  final painter = tester.widget<CustomPaint>(_paintFinder(text)).painter!;
  // ignore: avoid_dynamic_calls
  final boxes = (painter as dynamic).segmentBoxes as List<List<Rect>>;
  return boxes.first;
}
