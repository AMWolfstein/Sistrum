import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/utils/playback_seek_preview.dart';
import 'package:sistrum/widgets/player_lyrics.dart';
import 'package:sistrum/widgets/player_lyrics_section.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> _pump(
  WidgetTester tester, {
  required double height,
  required double covered,
}) async {
  final metadata = PlayerMetadataController(
    readMetadata: (_) async => const {},
    parseLyrics: (raw) async => LyricsParser.parse(raw),
  );
  final preview = PlaybackSeekPreview();
  addTearDown(metadata.dispose);
  addTearDown(preview.dispose);
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Align(
            alignment: Alignment.topCenter,
            child: SizedBox(
              height: height,
              child: LyricsCoveredBottom(
                height: covered,
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
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  final message = find.text('No lyrics in this file');

  testWidgets('no-lyrics state centers above the covered controls', (
    tester,
  ) async {
    await _pump(tester, height: 600, covered: 300);

    final box = tester.getRect(message);
    expect(box.bottom, lessThanOrEqualTo(300));
    // Centered in the visible 300 px, not the whole 600 px area.
    expect(tester.getCenter(message).dy, closeTo(150, 40));
  });

  testWidgets('a short visible area scrolls to the no-lyrics message', (
    tester,
  ) async {
    await _pump(tester, height: 300, covered: 220);

    await tester.dragUntilVisible(
      message,
      find.byType(SingleChildScrollView),
      const Offset(0, -40),
    );
    await tester.pumpAndSettle();
    expect(tester.getRect(message).bottom, lessThanOrEqualTo(80));
  });
}
