import 'dart:async';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/services/performance_auto_measure.dart';
import 'package:sistrum/utils/frame_timing_stats.dart';

PerformanceSession _session(MornyeGlassLevel mode, String screen) =>
    PerformanceSession(
      startedAt: DateTime.utc(2026, 11, 25),
      durationMs: 3000,
      frames: 120,
      keptFrames: 120,
      overBudgetFrames: 6,
      ui: const FramePhaseStats(p50: 4, p95: 9, p99: 12, max: 20),
      raster: const FramePhaseStats(p50: 5, p95: 14, p99: 19, max: 30),
      budgetMs: 16.7,
      reportedHz: 60,
      measuredHz: 60,
      glassMode: mode,
      effectiveRenderer: mode,
      deviceModel: 'Test phone',
      androidVersion: '15',
      appVersion: '1.0.0',
      interfaceScale: 1,
      screen: screen,
    );

/// A list that scrolls, so its position has extent, mounted in [tester].
Future<ScrollPosition> _mountList(WidgetTester tester) async {
  final controller = ScrollController();
  await tester.pumpWidget(
    Directionality(
      textDirection: TextDirection.ltr,
      child: SizedBox(
        width: 300,
        height: 400,
        child: ListView(
          controller: controller,
          children: List.generate(
            40,
            (i) => SizedBox(height: 100, child: Text('row $i')),
          ),
        ),
      ),
    ),
  );
  return controller.position;
}

AutoMeasureStep _step(
  String screen, {
  String? skipReason,
  ScrollPosition? position,
}) => AutoMeasureStep(
  screen: screen,
  prepare: () async => skipReason,
  position: () => position,
);

void main() {
  test('the scroll speed is Flutter\'s maximum fling velocity', () {
    expect(kAutoMeasureScrollVelocity, 8000);
    expect(
      autoMeasureLeg(8000),
      const Duration(seconds: 1),
      reason: 'a leg of 8000 px at 8000 px/s takes one second',
    );
  });

  testWidgets('a scroll goes down two viewports and back, at the fixed speed', (
    tester,
  ) async {
    final position = await _mountList(tester);
    expect(position.pixels, 0);
    final down = position.viewportDimension * kAutoMeasureViewports;
    unawaited(autoMeasureScroll(position));
    // The ticker starts on the first frame; then time passes in steps.
    await tester.pump();
    await tester.pump(autoMeasureLeg(down) ~/ 2);
    expect(position.pixels, greaterThan(0), reason: 'moving down');
    expect(position.pixels, lessThan(down));
    // Both legs complete within a little over the two leg durations.
    // Frames carry the clock forward; the return leg starts after the first.
    for (var i = 0; i < 40; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(position.pixels, closeTo(0, 0.5), reason: 'back where it started');
  });

  testWidgets(
    'the run visits the screens in order, Liquid then None, and restores the mode',
    (tester) async {
      final home = await _mountList(tester);
      final visited = <String>[];
      final modes = <MornyeGlassLevel?>[];
      final outcome = await tester.runAsync(
        () => runAutoMeasure(
          modes: const [MornyeGlassLevel.liquid, MornyeGlassLevel.flat],
          steps: [
            _step('Home', position: home),
            _step('Songs', skipReason: 'no songs'),
            _step('Search', position: home),
          ],
          setRenderMode: modes.add,
          measure: (screen, mode, drive) async {
            visited.add('$screen/${mode.name}');
            return _session(mode, screen);
          },
          isCancelled: () => false,
          noScrollReason: 'nothing to scroll',
          tooFewFramesReason: 'too few frames',
        ),
      );
      expect(visited, [
        'Home/liquid',
        'Search/liquid',
        'Home/flat',
        'Search/flat',
      ]);
      expect(modes, [MornyeGlassLevel.liquid, MornyeGlassLevel.flat, null]);
      expect(outcome!.rows.map((r) => '${r.screen}/${r.mode.name}'), visited);
      expect(outcome.skipped.map((s) => '${s.screen}: ${s.reason}'), [
        'Songs: no songs',
        'Songs: no songs',
      ]);
      expect(outcome.cancelled, isFalse);
      expect(outcome.error, isNull);
    },
  );

  testWidgets(
    'a screen with nothing to scroll and one with too few frames are skipped with their reasons',
    (tester) async {
      final list = await _mountList(tester);
      final modes = <MornyeGlassLevel?>[];
      final outcome = await tester.runAsync(
        () => runAutoMeasure(
          modes: const [MornyeGlassLevel.liquid],
          steps: [
            _step('Artist'),
            _step('Search', position: list),
          ],
          setRenderMode: modes.add,
          measure: (screen, mode, drive) async => null,
          isCancelled: () => false,
          noScrollReason: 'nothing to scroll',
          tooFewFramesReason: 'too few frames',
        ),
      );
      expect(outcome!.rows, isEmpty);
      expect(outcome.skipped.map((s) => s.reason), [
        'nothing to scroll',
        'too few frames',
      ]);
      expect(modes.last, isNull);
    },
  );

  testWidgets('a cancel stops the run and still restores the mode', (
    tester,
  ) async {
    final list = await _mountList(tester);
    final modes = <MornyeGlassLevel?>[];
    var cancelled = false;
    final measured = <String>[];
    final outcome = await tester.runAsync(
      () => runAutoMeasure(
        modes: const [MornyeGlassLevel.liquid, MornyeGlassLevel.flat],
        steps: [
          _step('Home', position: list),
          _step('Library', position: list),
        ],
        setRenderMode: modes.add,
        measure: (screen, mode, drive) async {
          measured.add('$screen/${mode.name}');
          cancelled = true; // the user cancels after the first screen
          return _session(mode, screen);
        },
        isCancelled: () => cancelled,
        noScrollReason: 'nothing to scroll',
        tooFewFramesReason: 'too few frames',
      ),
    );
    expect(measured, ['Home/liquid']);
    expect(outcome!.cancelled, isTrue);
    expect(modes.last, isNull, reason: 'the rendered mode is always restored');
  });

  testWidgets(
    'an error stops the run, reports it, and still restores the mode',
    (tester) async {
      final list = await _mountList(tester);
      final modes = <MornyeGlassLevel?>[];
      final outcome = await tester.runAsync(
        () => runAutoMeasure(
          modes: const [MornyeGlassLevel.liquid],
          steps: [
            _step('Home', position: list),
            _step('Search', position: list),
          ],
          setRenderMode: modes.add,
          measure: (screen, mode, drive) async {
            if (screen == 'Search') throw StateError('boom');
            return _session(mode, screen);
          },
          isCancelled: () => false,
          noScrollReason: 'nothing to scroll',
          tooFewFramesReason: 'too few frames',
        ),
      );
      expect(outcome!.rows.length, 1);
      expect(outcome.error, contains('boom'));
      expect(modes.last, isNull);
    },
  );

  test('the report lists the automatic rows and the skipped screens', () {
    final run = PerformanceRun(
      at: DateTime.utc(2026, 11, 25),
      compare: true,
      sessions: [_session(MornyeGlassLevel.liquid, 'Home')],
      skipped: const ['Songs (liquid): No Songs screen in this build'],
    );
    final text = performanceRunText(run);
    expect(text, contains('Automatic measurement'));
    expect(text, contains('Home | liquid | 120 |'));
    expect(text, contains('Skipped: Songs (liquid)'));
    // The round trip keeps the screen labels and the skip list.
    final back = PerformanceRun.fromJson(run.toJson());
    expect(back.sessions.single.screen, 'Home');
    expect(back.skipped, run.skipped);
  });

  testWidgets('a screen is scrolled long enough for the 60-frame floor', (
    tester,
  ) async {
    final position = await _mountList(tester);
    var done = false;
    Duration? elapsed;
    unawaited(
      autoMeasureScroll(position).then((e) {
        elapsed = e;
        done = true;
      }),
    );
    // A fake frame clock at 60 Hz: one frame every 16 ms, until the run ends.
    var frames = 0;
    while (!done && frames < 2000) {
      await tester.pump(const Duration(milliseconds: 16));
      await tester.runAsync(() => Future<void>.delayed(Duration.zero));
      frames++;
    }
    expect(done, isTrue, reason: 'the scroll finishes');
    expect(elapsed, greaterThanOrEqualTo(kAutoMeasureMinDuration));
    expect(frames, greaterThanOrEqualTo(60), reason: 'the frame floor');
  });

  testWidgets(
    'a slow window keeps scrolling past the time floor, until the cycle cap',
    (tester) async {
      final position = await _mountList(tester);
      // A slow frame clock: one frame every 500 ms (2 fps), far below 60.
      var frames = 0;
      final clock = Timer.periodic(
        const Duration(milliseconds: 500),
        (_) => frames++,
      );
      addTearDown(clock.cancel);
      var done = false;
      Duration? elapsed;
      unawaited(
        autoMeasureScroll(position, frames: () => frames, minFrames: 60).then((
          e,
        ) {
          elapsed = e;
          done = true;
        }),
      );
      for (var i = 0; i < 1000 && !done; i++) {
        await tester.pump(const Duration(milliseconds: 16));
      }
      expect(done, isTrue, reason: 'the cycle cap ends the run');
      expect(frames, lessThan(60), reason: 'the frame floor was never reached');
      // Each cycle is two legs of the measured distance at 8000 px/s. The
      // cycle cap then ends the run, well past the 2 s time floor.
      final leg = autoMeasureLeg(autoMeasureDistance(position));
      expect(elapsed, leg * (2 * kAutoMeasureMaxCycles));
      expect(elapsed!, greaterThan(kAutoMeasureMinDuration));
    },
  );

  testWidgets(
    'a fast window stops at the time floor once the frame floor is met',
    (tester) async {
      final position = await _mountList(tester);
      var frames = 0;
      final clock = Timer.periodic(
        const Duration(milliseconds: 16),
        (_) => frames++,
      );
      addTearDown(clock.cancel);
      var done = false;
      Duration? elapsed;
      unawaited(
        autoMeasureScroll(position, frames: () => frames, minFrames: 60).then((
          e,
        ) {
          elapsed = e;
          done = true;
        }),
      );
      for (var i = 0; i < 1000 && !done; i++) {
        await tester.pump(const Duration(milliseconds: 16));
      }
      expect(done, isTrue);
      expect(frames, greaterThanOrEqualTo(60));
      // The time floor ends it: at most one cycle past 2 s.
      final leg = autoMeasureLeg(autoMeasureDistance(position));
      expect(elapsed!, greaterThanOrEqualTo(kAutoMeasureMinDuration));
      expect(elapsed!, lessThan(kAutoMeasureMinDuration + leg * 2));
    },
  );

  testWidgets('a list already at its end is scrolled up, not left still', (
    tester,
  ) async {
    final position = await _mountList(tester);
    position.jumpTo(position.maxScrollExtent);
    await tester.pump();
    final end = position.pixels;
    var cancelled = false;
    var done = false;
    unawaited(
      autoMeasureScroll(
        position,
        minDuration: const Duration(seconds: 1),
        isCancelled: () => cancelled,
      ).then((_) => done = true),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    // Stop it before the test ends, so the list is not unmounted mid-leg.
    cancelled = true;
    for (var i = 0; i < 50 && !done; i++) {
      await tester.pump(const Duration(milliseconds: 20));
    }
    expect(done, isTrue);
    expect(position.pixels, lessThan(end), reason: 'it moves, up from the end');
  });

  testWidgets(
    'a leg whose ticker is muted is cut short at its timeout and reports no error',
    (tester) async {
      final controller = ScrollController();
      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: SizedBox(
            width: 300,
            height: 400,
            child: TickerMode(
              enabled: false,
              child: ListView(
                controller: controller,
                children: List.generate(
                  40,
                  (i) => SizedBox(height: 100, child: Text('row $i')),
                ),
              ),
            ),
          ),
        ),
      );
      final position = controller.position;
      var done = false;
      Duration? elapsed;
      Object? failure;
      unawaited(
        autoMeasureScroll(
          position,
          minDuration: const Duration(hours: 1),
          maxCycles: 1,
        ).then((e) {
          elapsed = e;
          done = true;
        }, onError: (Object e) => failure = e),
      );
      // Two legs, each cut at its timeout (leg plus 3 s): about 6 s in all.
      for (var i = 0; i < 500 && !done && failure == null; i++) {
        await tester.pump(const Duration(milliseconds: 20));
      }
      expect(failure, isNull);
      expect(done, isTrue, reason: 'the stalled legs end at their timeouts');
      expect(position.pixels, 0, reason: 'stopped where it started');
      final leg = autoMeasureLeg(autoMeasureDistance(position));
      expect(elapsed, leg * 2);
    },
  );

  testWidgets('a screen stops at its wall-clock cap, however long it stalls', (
    tester,
  ) async {
    final position = await _mountList(tester);
    // Each read of the clock is 10 s later than the last: the third read is
    // at the 20 s cap, so the run is two cycles, whatever the time floor says.
    var reads = 0;
    var done = false;
    Duration? elapsed;
    unawaited(
      autoMeasureScroll(
        position,
        minDuration: const Duration(hours: 1),
        clock: () => Duration(seconds: 10 * reads++),
      ).then((e) {
        elapsed = e;
        done = true;
      }),
    );
    for (var i = 0; i < 2000 && !done; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    expect(done, isTrue);
    expect(reads, 3);
    final leg = autoMeasureLeg(autoMeasureDistance(position));
    expect(elapsed, leg * 4, reason: 'two cycles of two legs');
  });
}
