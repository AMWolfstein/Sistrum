import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/models/theme_settings.dart';

PerformanceSession _session({bool modeChanged = false}) => PerformanceSession(
  startedAt: DateTime.utc(2026, 1, 1),
  durationMs: 10000,
  frames: 600,
  keptFrames: 600,
  overBudgetFrames: 0,
  ui: null,
  raster: null,
  budgetMs: 16.67,
  reportedHz: 60,
  measuredHz: 60,
  glassMode: MornyeGlassLevel.liquid,
  effectiveRenderer: MornyeGlassLevel.liquid,
  deviceModel: null,
  androidVersion: null,
  appVersion: 'test',
  interfaceScale: 1,
  modeChangedDuringRecording: modeChanged,
);

void main() {
  test('a changed mode is saved and read back', () {
    final json = _session(modeChanged: true).toJson();
    expect(json['mode_changed'], isTrue);
    expect(
      PerformanceSession.fromJson(json).modeChangedDuringRecording,
      isTrue,
    );
  });

  test('a saved session without the key reads as unchanged', () {
    final json = _session().toJson()..remove('mode_changed');
    expect(
      PerformanceSession.fromJson(json).modeChangedDuringRecording,
      isFalse,
    );
  });

  test('the text export says whether the mode changed', () {
    final run = PerformanceRun(
      at: DateTime.utc(2026, 1, 1),
      compare: false,
      sessions: [_session(modeChanged: true)],
    );
    expect(
      performanceRunText(run),
      contains('Glass mode changed during recording: yes'),
    );
  });
}
