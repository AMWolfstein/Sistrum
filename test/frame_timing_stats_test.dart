import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/services/frame_timing_recorder.dart';
import 'package:sistrum/utils/frame_timing_stats.dart';

FrameSamples _samples(List<int> build, List<int> raster, List<int> interval) =>
    FrameSamples(
      buildUs: build,
      rasterUs: raster,
      intervalUs: interval,
      frameCount: build.length,
    );

PerformanceSession _session({
  double? reportedHz,
  required FrameSamples samples,
}) => measurePerformanceSession(
  startedAt: DateTime.utc(2026, 11, 25),
  duration: const Duration(seconds: 10),
  samples: samples,
  reportedHz: reportedHz,
  glassMode: MornyeGlassLevel.liquid,
  effectiveRenderer: MornyeGlassLevel.flat,
  deviceModel: 'Test phone',
  androidVersion: '15',
  appVersion: '1.0.0 (build 2)',
  interfaceScale: 1,
);

void main() {
  // The recorder registers its timings callback with SchedulerBinding.
  TestWidgetsFlutterBinding.ensureInitialized();

  group('frame statistics', () {
    test('percentiles use nearest rank and max is the largest frame', () {
      final stats = summarizeFramePhase([
        for (var i = 100; i >= 1; i--) i * 1000,
      ]);
      expect(stats!.p50, 50);
      expect(stats.p95, 95);
      expect(stats.p99, 99);
      expect(stats.max, 100);
    });

    test('a single frame is every percentile and no frames have none', () {
      final stats = summarizeFramePhase([2500]);
      expect(stats!.p50, 2.5);
      expect(stats.p99, 2.5);
      expect(stats.max, 2.5);
      expect(summarizeFramePhase(const []), isNull);
    });

    test('a frame counts once when its build or raster is over budget', () {
      expect(
        countOverBudget(
          [9000, 12000, 9000, 4000],
          [9000, 9000, 11000, 4000],
          10000,
        ),
        2,
      );
    });

    test('budgets follow the refresh rate and reject invalid rates', () {
      expect(frameBudgetUs(60), closeTo(16666.67, 0.01));
      expect(frameBudgetUs(90), closeTo(11111.11, 0.01));
      expect(frameBudgetUs(120), closeTo(8333.33, 0.01));
      for (final hz in [null, 0.0, -1.0, double.nan, double.infinity]) {
        expect(frameBudgetUs(hz), isNull, reason: '$hz');
      }
    });

    test('measured refresh uses the median interval and skips zero', () {
      expect(refreshHzFromIntervals([0, 8333, 8334, 8333]), closeTo(120, 0.1));
      expect(refreshHzFromIntervals([0]), isNull);
    });
  });

  group('frame recorder', () {
    test('keeps only the newest frames, oldest first', () {
      final recorder = FrameTimingRecorder(capacity: 4);
      for (var i = 1; i <= 10; i++) {
        recorder.addFrame(i * 100, i * 10, i * 1000);
      }
      final samples = recorder.samples();
      expect(samples.frameCount, 10);
      expect(samples.buildUs, [700, 800, 900, 1000]);
      expect(samples.rasterUs, [70, 80, 90, 100]);
      expect(samples.intervalUs, [1000, 1000, 1000, 1000]);
      expect(recorder.lastBuildUs, 1000);
    });

    test(
      'a partly filled ring keeps every frame and no interval for the first',
      () {
        final recorder = FrameTimingRecorder(capacity: 4);
        recorder.addFrame(100, 10, 5000);
        recorder.addFrame(200, 20, 5500);
        final samples = recorder.samples();
        expect(samples.buildUs, [100, 200]);
        expect(samples.intervalUs, [0, 500]);
      },
    );

    test('start and stop toggle recording and a new start clears frames', () {
      final recorder = FrameTimingRecorder(capacity: 4);
      expect(recorder.isRecording, isFalse);
      recorder
        ..start()
        ..start();
      expect(recorder.isRecording, isTrue);
      recorder.addFrame(100, 10, 1000);
      final first = recorder.stop();
      expect(recorder.isRecording, isFalse);
      expect(first.frameCount, 1);
      recorder.stop();

      recorder.start();
      expect(recorder.frameCount, 0);
      expect(recorder.samples().buildUs, isEmpty);
      recorder.stop();
    });
  });

  group('performance sessions', () {
    test('jank uses the reported refresh rate', () {
      // At 60 Hz the budget is 16.67 ms: one of four frames is over.
      final session = _session(
        reportedHz: 60,
        samples: _samples([20000, 5000, 5000, 5000], [5000, 5000, 5000, 5000], [
          0,
          16667,
          16667,
          16667,
        ]),
      );
      expect(session.reportedHz, 60);
      expect(session.budgetMs, closeTo(16.67, 0.01));
      expect(session.overBudgetFrames, 1);
      expect(session.jankPercent, 25);
    });

    test('the measured interval sets the budget when no rate is reported', () {
      final session = _session(
        samples: _samples([9000, 5000], [1, 1], [0, 8333]),
      );
      expect(session.reportedHz, isNull);
      expect(session.measuredHz, closeTo(120, 0.1));
      expect(session.budgetMs, closeTo(8.33, 0.01));
      expect(session.jankPercent, 50);
    });

    test('jank stays not available when no refresh rate is known', () {
      final session = _session(samples: _samples([9000], [9000], [0]));
      expect(session.budgetMs, isNull);
      expect(session.overBudgetFrames, isNull);
      expect(session.jankPercent, isNull);
    });

    test(
      'jank from a measured interval is flagged as unreliable in the text',
      () {
        PerformanceRun runWith({double? reportedHz}) => PerformanceRun(
          at: DateTime.utc(2026, 11, 25),
          compare: false,
          sessions: [
            _session(
              reportedHz: reportedHz,
              samples: _samples([9000, 5000], [1, 1], [0, 8333]),
            ),
          ],
        );

        expect(
          performanceRunText(runWith()),
          contains(
            'Over budget: 50.0 % (unreliable: refresh rate not reported)',
          ),
        );
        expect(
          performanceRunText(runWith(reportedHz: 120)),
          contains('Over budget: 50.0 %\n'),
        );
      },
    );

    test('a run round-trips through JSON and marks what is not measured', () {
      final run = PerformanceRun(
        at: DateTime.utc(2026, 11, 25),
        compare: true,
        sessions: [
          _session(
            reportedHz: 90,
            samples: _samples([9000, 5000], [1000, 2000], [0, 11111]),
          ),
        ],
      );
      final decoded = PerformanceRun.fromJson(
        jsonDecode(performanceRunJson(run)) as Map<String, Object?>,
      );
      final session = decoded.sessions.single;
      expect(session.overBudgetFrames, run.sessions.single.overBudgetFrames);
      expect(session.ui!.p95, run.sessions.single.ui!.p95);
      expect(session.glassMode, MornyeGlassLevel.liquid);
      expect(session.effectiveRenderer, MornyeGlassLevel.flat);
    });
  });
}
