import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/playback_seek_slider.dart';

import 'mornye_artwork_performance_test.dart' as artwork;
import 'mornye_lyrics_performance_test.dart' as lyrics;
import 'performance_probe.dart';

/// Lyrics, artwork and seek scenarios with one build and shared fixtures.
void main() {
  final binding = PerformanceTestBinding.ensureInitialized();
  artwork.main();
  lyrics.main();
  testWidgets('mornye production seek track playback, drag and idle', (
    tester,
  ) async {
    final probe = PerformanceProbe(binding, tester, suite: 'mornye_seek');
    final playing = ValueNotifier(true);
    final seeks = <Duration>[];
    addTearDown(playing.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        home: Scaffold(
          body: Center(
            child: ValueListenableBuilder<bool>(
              valueListenable: playing,
              builder: (_, _, _) => PlaybackSeekSlider(
                position: const Duration(seconds: 40),
                duration: const Duration(minutes: 4),
                onSeek: (target) async => seeks.add(target),
              ),
            ),
          ),
        ),
      ),
    );
    await probe.measure(
      'playing_track_fixed_position',
      () => probe.wait(const Duration(milliseconds: 1500)),
      // A fixed Mornye progress track has no decorative animation.
      allowIdle: true,
    );
    await probe.measure('seek_drag', () async {
      final bounds = tester.getRect(find.byType(PlaybackSeekSlider));
      final touch = await tester.startGesture(
        Offset(bounds.left + bounds.width * 0.25, bounds.center.dy),
      );
      try {
        for (var step = 0; step < 20; step++) {
          await touch.moveBy(Offset(bounds.width * 0.02, 0));
          await probe.wait(const Duration(milliseconds: 16));
        }
      } finally {
        await touch.up();
      }
      await probe.wait(const Duration(milliseconds: 400));
    });
    expect(seeks, isNotEmpty);
    await tester.runAsync(() async {
      playing.value = false;
      await probe.wait(const Duration(milliseconds: 500));
    });
    await probe.measure(
      'paused_track_idle',
      () => probe.wait(const Duration(milliseconds: 1000)),
      allowIdle: true,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await probe.finish(
      metadata: {
        'style': 'mornye',
        'scope':
            'Production PlaybackSeekSlider with fixed position; no audio, '
            'database, network or provider timers.',
        'seek_commits': seeks.length,
      },
    );
  });
}
