import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart' show LogicalKeyboardKey;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_now_playing.dart';
import 'package:sistrum/widgets/playback_tempo_sheet.dart';
import 'package:sistrum/widgets/player_lyrics.dart';

import 'support/app_icon_finder.dart';

const _track = MediaItem(
  id: 'track',
  title: 'A song',
  artist: 'An artist',
  duration: Duration(minutes: 4),
  extras: {'source': '/music/song.flac'},
);

const _ttml = '''
<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
<p begin="0s" end="120s"><span begin="0s" end="60s">Timed </span><span begin="60s" end="120s">words</span></p>
</div></body></tt>
''';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = true;
    desktopInspectorPanel.value = null;
  });

  tearDown(() {
    debugDesktopPlatformOverride = null;
    desktopInspectorPanel.value = null;
  });

  testWidgets('hidden lyrics stop word sweep frames', (tester) async {
    tester.view.physicalSize = const Size(1520, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final playback = StreamController<PlaybackState>();
    addTearDown(playback.close);
    void reportPosition(int seconds, {bool playing = false}) => playback.add(
      PlaybackState(
        playing: playing,
        processingState: AudioProcessingState.ready,
        updatePosition: Duration(seconds: seconds),
      ),
    );
    reportPosition(1);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(_track),
          ),
          playbackStateProvider.overrideWith((ref) => playback.stream),
          playQueueProvider.overrideWith((ref) => Stream.value(const [_track])),
          systemVolumeProvider.overrideWith((ref) => Stream.value(0.5)),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: DesktopNowPlaying(
              metadataControllerFactory: () => PlayerMetadataController(
                readMetadata: (_) async => {'lyrics': _ttml},
                parseLyrics: (raw) async => LyricsParser.parse(raw!),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    final view = find.byType(SyncedLyricsView);
    final retainedState = tester.state(view);
    expect(tester.widget<SyncedLyricsView>(view).animateWords, isFalse);
    reportPosition(90);
    await tester.pump();

    desktopInspectorPanel.value = DesktopInspectorPanel.lyrics;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();
    final paint = find.descendant(
      of: view,
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is CustomPaint &&
            widget.painter.runtimeType.toString() == '_TimedLyricSweepPainter',
      ),
    );
    expect(paint, findsOneWidget);
    final painter = tester.widget<CustomPaint>(paint).painter!;
    // ignore: avoid_dynamic_calls
    final position = (painter as dynamic).currentPosition() as Duration;
    expect(position, const Duration(seconds: 90));
    var repaints = 0;
    void repaint() => repaints++;
    painter.addListener(repaint);
    addTearDown(() => painter.removeListener(repaint));
    await tester.pump(const Duration(milliseconds: 100));
    expect(repaints, 0);
    reportPosition(90, playing: true);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(repaints, greaterThan(0));

    desktopInspectorPanel.value = null;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(tester.widget<SyncedLyricsView>(view).animateWords, isTrue);
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();
    expect(tester.widget<SyncedLyricsView>(view).animateWords, isFalse);
    final hiddenRepaints = repaints;
    await tester.pump(const Duration(seconds: 1));
    expect(repaints, hiddenRepaints);
    expect(tester.state(view), same(retainedState));

    desktopInspectorPanel.value = DesktopInspectorPanel.lyrics;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(tester.widget<SyncedLyricsView>(view).animateWords, isTrue);
    expect(tester.state(view), same(retainedState));
    final resumedRepaints = repaints;
    await tester.pump(const Duration(milliseconds: 100));
    expect(repaints, greaterThan(resumedRepaints));

    desktopInspectorPanel.value = DesktopInspectorPanel.upNext;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(tester.widget<SyncedLyricsView>(view).animateWords, isFalse);
    final queueRepaints = repaints;
    await tester.pump(const Duration(seconds: 1));
    expect(repaints, queueRepaints);
    expect(tester.state(view), same(retainedState));
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'desktop shows a changed speed and opens Speed and pitch as a dialog',
    (tester) async {
      tester.view.physicalSize = const Size(1520, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            currentMediaItemProvider.overrideWithBuild(
              (ref, notifier) => Stream.value(_track),
            ),
            playbackStateProvider.overrideWith(
              (ref) => Stream.value(
                PlaybackState(processingState: AudioProcessingState.ready),
              ),
            ),
            playQueueProvider.overrideWith(
              (ref) => Stream.value(const [_track]),
            ),
            systemVolumeProvider.overrideWith((ref) => Stream.value(0.5)),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: DesktopNowPlaying(
                metadataControllerFactory: () => PlayerMetadataController(
                  readMetadata: (_) async => const {},
                  parseLyrics: (raw) async => LyricsParser.parse(raw ?? ''),
                ),
              ),
            ),
          ),
        ),
      );
      Future<void> settle() async {
        for (var i = 0; i < 30; i++) {
          await tester.pump(const Duration(milliseconds: 16));
        }
      }

      await settle();
      const badge = ValueKey('playback-tempo-badge');
      expect(find.byKey(badge), findsNothing);
      final settings = ProviderScope.containerOf(
        tester.element(find.byType(DesktopNowPlaying)),
      ).read(settingsProvider.notifier);
      settings.setPlaybackSpeed(0.75);
      await settle();
      expect(find.byKey(badge).hitTestable(), findsOneWidget);
      expect(find.text('0.75x'), findsOneWidget);

      await tester.tap(find.byKey(badge));
      await settle();
      expect(find.byType(PlaybackTempoControls), findsOneWidget);
      expect(find.byType(AppSheetDialog), findsOneWidget);
      expect(find.byType(BottomSheet), findsNothing);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await settle();
      expect(find.byType(PlaybackTempoControls), findsNothing);

      await tester.tap(findAppIcon(CupertinoIcons.ellipsis).hitTestable());
      await settle();
      expect(find.text('Speed and pitch'), findsOneWidget);
      expect(find.text('0.75x'), findsNWidgets(2));
      await tester.tap(find.text('Speed and pitch'));
      await settle();
      expect(find.byType(PlaybackTempoControls), findsOneWidget);
      expect(find.byType(AppSheetDialog), findsOneWidget);
      expect(
        find.byKey(const ValueKey('desktop-now-playing-tempo')),
        findsNothing,
      );
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
