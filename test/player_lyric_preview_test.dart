import 'dart:async';
import 'dart:convert';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/lyrics_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/widgets/player_lyric_preview.dart';
import 'package:sistrum/widgets/player_lyrics.dart';

import 'support/fixed_settings.dart';

const _lrc = '''
[00:00.00]
[00:05.00]paisa mera dialect par -
[00:10.00]Next line of rap
''';

const _arabicLrc = '''
[00:00.00]
[00:05.00]كان عندك كام سنة
[00:10.00]لما عرفت
''';

/// Pumps the preview over [parsed] (or the LRC text) with playback events on
/// the returned controller.
Future<StreamController<PlaybackState>> _pumpPreview(
  WidgetTester tester, {
  String lrc = _lrc,
  ParsedLyrics? parsed,
  AppSettings settings = const AppSettings(playerShowLyricSnippet: true),
  bool disableAnimations = false,
  VoidCallback? onTap,
  ValueListenable<Duration?>? seekPreview,
  Locale? locale,
}) async {
  final lyrics = parsed ?? LyricsParser.parse(lrc);
  final metadata = PlayerMetadataController(
    parseLyrics: (_) async => lyrics,
    readMetadata: (_) async => {'title': 'Track', 'lyrics': 'dummy'},
  );
  await metadata.load(
    MediaItem(
      id: 'track',
      title: 'Track',
      extras: {'source': '/music/track.flac'},
    ),
  );
  final playback = StreamController<PlaybackState>.broadcast();
  addTearDown(playback.close);

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        playbackStateProvider.overrideWith((ref) => playback.stream),
        settingsProvider.overrideWith(() => FixedSettingsNotifier(settings)),
      ],
      child: MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: MornyeTheme.build(Brightness.dark),
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(disableAnimations: disableAnimations),
            child: Scaffold(
              body: PlayerLyricPreview(
                metadata: metadata,
                seekPreview: seekPreview ?? PlaybackSeekPreview(),
                colorScheme: const ColorScheme.dark(),
                onTap: onTap,
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  return playback;
}

/// Delivers a playback position and lets the preview rebuild on it.
Future<void> _emit(
  WidgetTester tester,
  StreamController<PlaybackState> playback,
  Duration position,
) async {
  playback.add(PlaybackState(playing: true, updatePosition: position));
  await tester.pump();
  await tester.pump();
}

ParsedLyrics _wordTimed(String text) => ParsedLyrics(
  synced: true,
  wordSynced: true,
  plainText: text,
  lines: [
    LyricLine(
      time: Duration.zero,
      end: const Duration(seconds: 20),
      text: text,
      words: [
        LyricWord(
          time: Duration.zero,
          end: const Duration(seconds: 10),
          text: '${text.split(' ').first} ',
        ),
        LyricWord(
          time: const Duration(seconds: 10),
          end: const Duration(seconds: 20),
          text: text.split(' ').last,
        ),
      ],
    ),
  ],
);

Finder _chevron() => find.byIcon(CupertinoIcons.chevron_right);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('the lyric preview setting defaults on and survives JSON', () {
    expect(AppSettings.fromJson({}).playerShowLyricSnippet, isTrue);
    expect(
      AppSettings.fromJson(
        const AppSettings(playerShowLyricSnippet: false).toJson(),
      ).playerShowLyricSnippet,
      isFalse,
    );
  });

  group('PlayerLyricPreview Widget Tests', () {
    testWidgets(
      'displays real-time active lyric line and chevron, and triggers onTap when clicked',
      (tester) async {
        var openedLyrics = false;
        final playback = await _pumpPreview(
          tester,
          onTap: () => openedLyrics = true,
        );

        // At position 00:01 (before 00:05 line):
        await _emit(tester, playback, const Duration(seconds: 1));
        await tester.pumpAndSettle();
        expect(find.text('•••'), findsOneWidget);
        expect(_chevron(), findsOneWidget);

        // At position 00:06 (active line "paisa mera dialect par -"):
        await _emit(tester, playback, const Duration(seconds: 6));
        await tester.pumpAndSettle();
        expect(find.text('paisa mera dialect par -'), findsOneWidget);
        expect(_chevron(), findsOneWidget);

        // Tap the preview line:
        await tester.tap(find.text('paisa mera dialect par -'));
        await tester.pump();
        expect(openedLyrics, isTrue);

        // At position 00:11 (active line "Next line of rap"):
        await _emit(tester, playback, const Duration(seconds: 11));
        await tester.pumpAndSettle();
        expect(find.text('Next line of rap'), findsOneWidget);
      },
    );

    testWidgets('hides when playerShowLyricSnippet is disabled in settings', (
      tester,
    ) async {
      await _pumpPreview(
        tester,
        settings: const AppSettings(playerShowLyricSnippet: false),
      );

      await tester.pumpAndSettle();
      expect(find.text('paisa mera dialect par -'), findsNothing);
      expect(find.byType(PlayerLyricPreview), findsOneWidget);
      expect(_chevron(), findsNothing);
    });

    testWidgets('renders WordTimedLyricLine for word-by-word synced lyrics', (
      tester,
    ) async {
      final playback = await _pumpPreview(
        tester,
        parsed: ParsedLyrics(
          synced: true,
          wordSynced: true,
          lines: [
            LyricLine(
              time: const Duration(seconds: 4),
              end: const Duration(seconds: 8),
              text: 'word by word preview',
              words: [
                LyricWord(
                  time: const Duration(seconds: 4),
                  end: const Duration(seconds: 5),
                  text: 'word ',
                ),
                LyricWord(
                  time: const Duration(seconds: 5),
                  end: const Duration(seconds: 6),
                  text: 'by ',
                ),
                LyricWord(
                  time: const Duration(seconds: 6),
                  end: const Duration(seconds: 7),
                  text: 'word ',
                ),
                LyricWord(
                  time: const Duration(seconds: 7),
                  end: const Duration(seconds: 8),
                  text: 'preview',
                ),
              ],
            ),
          ],
          plainText: 'word by word preview',
        ),
      );

      await _emit(tester, playback, const Duration(seconds: 5));
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.byType(WordTimedLyricLine), findsOneWidget);
      expect(_chevron(), findsOneWidget);
    });

    testWidgets('a right-to-left line is right-aligned beside the chevron', (
      tester,
    ) async {
      final playback = await _pumpPreview(tester, lrc: _arabicLrc);

      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      final text = tester.widget<Text>(find.text('كان عندك كام سنة'));
      expect(text.textDirection, TextDirection.rtl);
      // The line sits on the trailing edge, next to the chevron.
      final lineBox = tester.getRect(find.text('كان عندك كام سنة'));
      final chevron = tester.getRect(_chevron());
      expect(lineBox.right, closeTo(chevron.left - 4, 1));
    });

    testWidgets('a left-to-right line keeps its left alignment', (
      tester,
    ) async {
      final playback = await _pumpPreview(tester);
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      final text = tester.widget<Text>(find.text('paisa mera dialect par -'));
      expect(text.textDirection, TextDirection.ltr);
      final lineBox = tester.getRect(find.text('paisa mera dialect par -'));
      final preview = tester.getRect(find.byType(PlayerLyricPreview));
      expect(lineBox.left, closeTo(preview.left + 4, 1));
      expect(lineBox.right, closeTo(tester.getRect(_chevron()).left - 4, 1));
    });

    testWidgets('a mixed line keeps the direction of its first strong letter', (
      tester,
    ) async {
      final playback = await _pumpPreview(
        tester,
        lrc: '[00:00.00]\n[00:05.00]مرحبا Hello\n[00:10.00]Hello مرحبا\n',
      );

      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(
        tester.widget<Text>(find.text('مرحبا Hello')).textDirection,
        TextDirection.rtl,
      );

      await _emit(tester, playback, const Duration(seconds: 11));
      await tester.pumpAndSettle();
      expect(
        tester.widget<Text>(find.text('Hello مرحبا')).textDirection,
        TextDirection.ltr,
      );
    });

    testWidgets('a right-to-left word-timed line is laid out right to left', (
      tester,
    ) async {
      final playback = await _pumpPreview(
        tester,
        parsed: _wordTimed('كان عندك'),
      );

      await _emit(tester, playback, const Duration(seconds: 12));
      await tester.pump(const Duration(milliseconds: 300));

      // The sung words are painted by a TextPainter; its segment boxes show
      // the first word on the right of the second.
      final painter = tester
          .widget<CustomPaint>(
            find
                .descendant(
                  of: find.byType(WordTimedLyricLine),
                  matching: find.byType(CustomPaint),
                )
                .first,
          )
          .painter!;
      // ignore: avoid_dynamic_calls
      final boxes = (painter as dynamic).segmentBoxes as List<List<Rect>>;
      final first = boxes.first.reduce((a, b) => a.expandToInclude(b));
      final second = boxes.last.reduce((a, b) => a.expandToInclude(b));
      expect(second.right, lessThanOrEqualTo(first.left));
    });

    /// Offsets of the switcher's slide while a line change is half done.
    Future<List<Offset>> transitionOffsets(
      WidgetTester tester,
      String lrc,
    ) async {
      final playback = await _pumpPreview(tester, lrc: lrc);
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      await _emit(tester, playback, const Duration(seconds: 11));
      await tester.pump(const Duration(milliseconds: 60));
      return [
        for (final transition in tester.widgetList<SlideTransition>(
          find.descendant(
            of: find.byType(PlayerLyricPreview),
            matching: find.byType(SlideTransition),
          ),
        ))
          transition.position.value,
      ];
    }

    testWidgets('a right-to-left line enters from the right', (tester) async {
      final offsets = await transitionOffsets(tester, _arabicLrc);
      expect(offsets, isNotEmpty);
      for (final offset in offsets) {
        expect(offset.dy, 0);
        expect(offset.dx, greaterThan(0));
      }
    });

    testWidgets('a left-to-right line enters from below', (tester) async {
      final offsets = await transitionOffsets(tester, _lrc);
      expect(offsets, isNotEmpty);
      for (final offset in offsets) {
        expect(offset.dx, 0);
        expect(offset.dy, greaterThan(0));
      }
    });

    testWidgets('reduced motion swaps lines without a transition', (
      tester,
    ) async {
      final playback = await _pumpPreview(tester, disableAnimations: true);

      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      await _emit(tester, playback, const Duration(seconds: 11));
      await tester.pump();

      expect(
        tester.widget<AnimatedSwitcher>(find.byType(AnimatedSwitcher)).duration,
        Duration.zero,
      );
      expect(find.text('Next line of rap'), findsOneWidget);
    });
    testWidgets('an Arabic locale mirrors the chevron and the line order', (
      tester,
    ) async {
      final playback = await _pumpPreview(
        tester,
        lrc: _arabicLrc,
        locale: const Locale('ar'),
      );
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();

      // The row runs right to left, so the line sits left of the chevron's
      // forward edge and the chevron is drawn pointing left.
      final line = tester.getRect(find.text('كان عندك كام سنة'));
      final chevron = tester.getRect(find.byIcon(CupertinoIcons.chevron_left));
      expect(chevron.center.dx, lessThan(line.center.dx));
      expect(find.byIcon(CupertinoIcons.chevron_left), findsOneWidget);
      expect(find.byIcon(CupertinoIcons.chevron_right), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a left-to-right line under an Arabic locale follows the UI', (
      tester,
    ) async {
      final playback = await _pumpPreview(tester, locale: const Locale('ar'));
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();

      // Text without right-to-left script takes the layout direction.
      final text = tester.widget<Text>(find.text('paisa mera dialect par -'));
      expect(text.textDirection, TextDirection.rtl);
      final line = tester.getRect(find.text('paisa mera dialect par -'));
      final chevron = tester.getRect(find.byIcon(CupertinoIcons.chevron_left));
      expect(line.center.dx, greaterThan(chevron.center.dx));
      expect(tester.takeException(), isNull);
    });

    testWidgets('shows the translation when translations are on', (
      tester,
    ) async {
      const translation = 'translated line';
      final playback = await _pumpPreview(
        tester,
        settings: const AppSettings(
          playerShowLyricSnippet: true,
          playerShowTranslation: true,
        ),
        parsed: const ParsedLyrics(
          synced: true,
          wordSynced: false,
          lines: [
            LyricLine(
              time: Duration.zero,
              end: Duration(seconds: 20),
              text: 'original line',
              translation: translation,
            ),
          ],
          plainText: 'original line',
        ),
      );
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(find.text(translation), findsOneWidget);
      expect(find.text('original line'), findsNothing);
    });

    testWidgets('a word-timed translation keeps its word timing', (
      tester,
    ) async {
      const translation = 'translated line';
      final playback = await _pumpPreview(
        tester,
        settings: const AppSettings(
          playerShowLyricSnippet: true,
          playerShowTranslation: true,
        ),
        parsed: ParsedLyrics(
          synced: true,
          wordSynced: true,
          plainText: 'original line',
          lines: [
            LyricLine(
              time: Duration.zero,
              end: const Duration(seconds: 20),
              text: 'original line',
              words: [
                LyricWord(
                  time: Duration.zero,
                  end: const Duration(seconds: 10),
                  text: 'original ',
                ),
                LyricWord(
                  time: const Duration(seconds: 10),
                  end: const Duration(seconds: 20),
                  text: 'line',
                ),
              ],
              translation: translation,
              translationWords: const [
                LyricWord(
                  time: Duration.zero,
                  end: Duration(seconds: 10),
                  text: 'translated ',
                ),
                LyricWord(
                  time: Duration(seconds: 10),
                  end: Duration(seconds: 20),
                  text: 'line',
                ),
              ],
            ),
          ],
        ),
      );
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pump(const Duration(milliseconds: 300));

      // The sweep draws the translation, never the original words.
      final sweep = tester.widget<WordTimedLyricLine>(
        find.byType(WordTimedLyricLine),
      );
      expect(sweep.line.text, translation);
      expect(sweep.line.words.map((word) => word.text), [
        'translated ',
        'line',
      ]);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the original line returns when translations are off', (
      tester,
    ) async {
      final playback = await _pumpPreview(
        tester,
        settings: const AppSettings(
          playerShowLyricSnippet: true,
          playerShowTranslation: false,
        ),
        parsed: const ParsedLyrics(
          synced: true,
          wordSynced: false,
          lines: [
            LyricLine(
              time: Duration.zero,
              end: Duration(seconds: 20),
              text: 'original line',
              translation: 'translated line',
            ),
          ],
          plainText: 'original line',
        ),
      );
      await _emit(tester, playback, const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(find.text('original line'), findsOneWidget);
      expect(find.text('translated line'), findsNothing);
    });

    testWidgets('a seek preview moves the shown line while paused', (
      tester,
    ) async {
      final seek = PlaybackSeekPreview();
      addTearDown(seek.dispose);
      await _pumpPreview(tester, seekPreview: seek);

      seek.begin(const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(find.text('paisa mera dialect par -'), findsOneWidget);

      seek.begin(const Duration(seconds: 11));
      await tester.pumpAndSettle();
      expect(find.text('Next line of rap'), findsOneWidget);
      expect(find.text('paisa mera dialect par -'), findsNothing);

      seek.reset();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets(
    'the Lyrics settings switch turns the player preview off and on',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final container = ProviderContainer(
        overrides: [
          initialSettingsProvider.overrideWithValue(const AppSettings()),
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
            home: const LyricsSettingsPage(),
          ),
        ),
      );
      final option = find.byKey(const ValueKey('lyric-snippet-preview'));
      await tester.scrollUntilVisible(
        option,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).playerShowLyricSnippet, isTrue);

      await tester.tap(option);
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).playerShowLyricSnippet, isFalse);
      final prefs = await SharedPreferences.getInstance();
      final restored = AppSettings.fromJson(
        jsonDecode(prefs.getString('app_settings')!) as Map<String, dynamic>,
      );
      expect(restored.playerShowLyricSnippet, isFalse);

      final l10n = AppLocalizations.of(tester.element(option));
      expect(
        SettingsSearchCatalog(l10n).lyrics.where(
          (entry) =>
              entry.title == l10n.lyricsSnippetPreview &&
              entry.matches(SettingsSearchQuery('lyric preview')),
        ),
        hasLength(1),
      );
      expect(tester.takeException(), isNull);
    },
  );
}
