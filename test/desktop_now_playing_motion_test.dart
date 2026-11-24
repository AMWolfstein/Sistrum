import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart' show RenderOpacity;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/lyrics_parser.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_now_playing.dart';
import 'package:sistrum/widgets/mornye_player_queue.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/widgets/player_lyrics_section.dart';
import 'package:sistrum/widgets/player_playback_controls.dart';

const _coverA = '/covers/a.jpg';

MediaItem _track(String id, String cover) => MediaItem(
  id: id,
  title: 'Song $id',
  artist: 'An artist',
  duration: const Duration(minutes: 4),
  artUri: Uri.file(cover),
  extras: const {'source': '/music/song.flac'},
);

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

  Future<void> pumpPage(WidgetTester tester, Stream<MediaItem?> items) async {
    tester.view.physicalSize = const Size(1520, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          currentMediaItemProvider.overrideWithBuild((ref, notifier) => items),
          playbackStateProvider.overrideWith(
            (ref) => Stream.value(
              PlaybackState(processingState: AudioProcessingState.ready),
            ),
          ),
          playQueueProvider.overrideWith(
            (ref) => Stream.value([_track('a', _coverA)]),
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
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
  }

  Widget controls(WidgetTester tester) =>
      tester.widget(find.byType(PlayerPlaybackControls));

  testWidgets('Mornye panel motion and window resize keep the column, lyrics '
      'and queue built', (tester) async {
    await pumpPage(tester, Stream.value(_track('a', _coverA)));
    final column = controls(tester);
    final cover = find.byWidgetPredicate(
      (widget) => widget is PlayerArtwork && widget.iconSize == 96,
    );
    double panelOpacity() => tester
        .renderObject<RenderOpacity>(
          find
              .ancestor(
                of: find.byType(PlayerLyricsSection),
                matching: find.byType(Opacity),
              )
              .first,
        )
        .opacity;

    desktopInspectorPanel.value = DesktopInspectorPanel.lyrics;
    await tester.pump();
    // Showing the lyrics builds them once.
    final lyrics = tester.widget(find.byType(PlayerLyricsSection));
    final opacities = <double>{};
    // Long enough for the panel's open spring to settle.
    for (var frame = 0; frame < 40; frame++) {
      await tester.pump(const Duration(milliseconds: 16));
      opacities.add(panelOpacity());
      final coverSize = tester.getSize(cover);
      expect(coverSize.width, coverSize.height);
      expect(controls(tester), same(column), reason: 'open frame $frame');
      expect(
        tester.widget(find.byType(PlayerLyricsSection)),
        same(lyrics),
        reason: 'open frame $frame',
      );
    }
    expect(opacities.where((value) => value > 0 && value < 1), isNotEmpty);
    expect(panelOpacity(), 1);

    // A live window resize.
    for (var frame = 1; frame <= 10; frame++) {
      tester.view.physicalSize = Size(1520 - frame * 12, 1000 - frame * 8);
      await tester.pump();
      expect(controls(tester), same(column), reason: 'resize frame $frame');
      expect(
        tester.widget(find.byType(PlayerLyricsSection)),
        same(lyrics),
        reason: 'resize frame $frame',
      );
    }

    desktopInspectorPanel.value = DesktopInspectorPanel.upNext;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    desktopInspectorPanel.value = null;
    await tester.pump();
    final queue = tester.widget(find.byType(MornyePlayerQueue));
    var closing = 0;
    for (var frame = 0; frame < 30; frame++) {
      await tester.pump(const Duration(milliseconds: 16));
      // The closed panel drops its queue once the motion ends.
      if (find.byType(MornyePlayerQueue).evaluate().isEmpty) break;
      closing++;
      expect(controls(tester), same(column), reason: 'close frame $frame');
      expect(
        tester.widget(find.byType(MornyePlayerQueue)),
        same(queue),
        reason: 'close frame $frame',
      );
    }
    expect(closing, greaterThan(5));
    expect(find.byType(MornyePlayerQueue), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
