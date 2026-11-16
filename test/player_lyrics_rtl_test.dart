import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/player_lyrics.dart';

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

/// Leftmost and rightmost x of the fully highlighted (red) pixels inside the
/// second word.
Future<(double, double)> _highlightEdges(
  WidgetTester tester,
  String text,
) async {
  final finder = _paintFinder(text);
  final size = tester.getSize(finder);
  final painter = tester.widget<CustomPaint>(finder).painter!;
  final box = _secondWordBox(tester, text);
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
  final width = size.width.ceil();
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
  expect(left, lessThan(right), reason: 'no highlight drawn for "$text"');
  return (left, right);
}
