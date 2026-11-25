import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/widgets/player_lyrics.dart';
import 'package:sistrum/widgets/player_lyrics_section.dart';

import 'support/fixed_settings.dart';

const _viewWidth = 600.0;
const _translation = 'A small cat';

/// Three lines; the middle one has [translation] when given.
List<LyricLine> _lines({String? translation, bool timed = false}) => [
  const LyricLine(time: Duration.zero, text: 'First line'),
  LyricLine(
    time: const Duration(seconds: 10),
    text: 'Second line',
    translation: translation,
    words: timed
        ? [
            const LyricWord(
              time: Duration(seconds: 10),
              end: Duration(seconds: 12),
              text: 'Second line',
            ),
          ]
        : const [],
  ),
  const LyricLine(time: Duration(seconds: 20), text: 'Third line'),
];

Future<void> _pumpSynced(
  WidgetTester tester,
  List<LyricLine> lines, {
  required bool showTranslation,
}) async {
  tester.view.physicalSize = const Size(_viewWidth, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final theme = MornyeTheme.build(Brightness.dark);
  final preview = ValueNotifier<Duration?>(Duration.zero);
  addTearDown(preview.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        playbackStateProvider.overrideWith(
          (ref) => Stream.value(PlaybackState()),
        ),
      ],
      child: MaterialApp(
        theme: theme,
        home: Scaffold(
          body: SizedBox(
            width: _viewWidth,
            child: SyncedLyricsView(
              lyrics: ParsedLyrics(
                synced: true,
                wordSynced: lines.any((line) => line.hasWordTiming),
                plainText: lines.map((line) => line.text).join('\n'),
                lines: lines,
              ),
              seekPreview: preview,
              colorScheme: theme.colorScheme,
              isActive: true,
              showPronunciation: false,
              showTranslation: showTranslation,
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _pumpPlain(
  WidgetTester tester,
  List<LyricLine> lines, {
  required bool showTranslation,
}) async {
  SharedPreferences.setMockInitialValues({});
  final parsed = ParsedLyrics(
    synced: false,
    wordSynced: false,
    lines: lines,
    plainText: lines.map((line) => line.text).join('\n'),
  );
  final metadata = PlayerMetadataController(
    parseLyrics: (_) async => parsed,
    readMetadata: (_) async => {'title': 'Example', 'lyrics': 'x'},
  );
  addTearDown(metadata.dispose);
  await metadata.load(
    MediaItem(
      id: 'example',
      title: 'Example',
      artist: 'Artist',
      duration: const Duration(minutes: 3),
      extras: const {'source': '/music/example.flac'},
    ),
  );
  final container = ProviderContainer(
    overrides: [
      playbackStateProvider.overrideWith(
        (ref) => Stream.value(PlaybackState()),
      ),
      settingsProvider.overrideWith(
        () => FixedSettingsNotifier(
          AppSettings(playerShowTranslation: showTranslation),
        ),
      ),
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
          body: SizedBox(
            width: _viewWidth,
            child: PlayerLyricsSection(
              metadata: metadata,
              seekPreview: PlaybackSeekPreview(),
              colorScheme: const ColorScheme.dark(),
              isActive: true,
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

double _top(WidgetTester tester, String text) =>
    tester.getTopLeft(find.text(text)).dy;

void main() {
  for (final timed in [false, true]) {
    testWidgets(
      'synced lyrics${timed ? ' (word-timed)' : ''}: a line without a '
      'translation keeps the slot a translated line takes',
      (tester) async {
        await _pumpSynced(
          tester,
          _lines(translation: _translation, timed: timed),
          showTranslation: true,
        );
        final withTranslation = _top(tester, 'Third line');

        await _pumpSynced(tester, _lines(timed: timed), showTranslation: true);
        final withoutTranslation = _top(tester, 'Third line');
        expect(withoutTranslation, closeTo(withTranslation, 0.5));

        // The slot is there only while translations are shown.
        await _pumpSynced(tester, _lines(timed: timed), showTranslation: false);
        expect(_top(tester, 'Third line'), lessThan(withTranslation - 1));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'plain lyrics: a line without a translation keeps the row a translated '
    'line takes',
    (tester) async {
      await _pumpPlain(
        tester,
        _lines(translation: _translation),
        showTranslation: true,
      );
      final withTranslation = _top(tester, 'Third line');

      await _pumpPlain(tester, _lines(), showTranslation: true);
      expect(_top(tester, 'Third line'), closeTo(withTranslation, 0.5));
      expect(tester.takeException(), isNull);
    },
  );
}
