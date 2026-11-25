import 'dart:convert';

import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/services/frame_timing_recorder.dart';
import 'package:sistrum/utils/frame_timing_stats.dart';

/// One measured window: frame statistics plus the app and device context.
class PerformanceSession {
  const PerformanceSession({
    required this.startedAt,
    required this.durationMs,
    required this.frames,
    required this.keptFrames,
    required this.overBudgetFrames,
    required this.ui,
    required this.raster,
    required this.budgetMs,
    required this.reportedHz,
    required this.measuredHz,
    required this.glassMode,
    required this.effectiveRenderer,
    required this.deviceModel,
    required this.androidVersion,
    required this.appVersion,
    required this.interfaceScale,
    this.modeChangedDuringRecording = false,
    this.screen,
  });

  final DateTime startedAt;
  final int durationMs;

  /// Every frame of the window, and the frames the statistics were taken from.
  final int frames;
  final int keptFrames;

  /// Null when no refresh rate was known, so jank cannot be computed.
  final int? overBudgetFrames;
  final FramePhaseStats? ui;
  final FramePhaseStats? raster;
  final double? budgetMs;
  final double? reportedHz;
  final double? measuredHz;
  final MornyeGlassLevel glassMode;
  final MornyeGlassLevel effectiveRenderer;
  final String? deviceModel;
  final String? androidVersion;
  final String appVersion;
  final double interfaceScale;

  /// The owner changed the glass mode while this single recording ran. The
  /// session keeps the mode it started in, so the result is marked.
  final bool modeChangedDuringRecording;

  /// The screen an automatic measurement scrolled; null for a plain session.
  final String? screen;

  /// The same session, labelled with the screen it measured.
  PerformanceSession withScreen(String name) => PerformanceSession(
    startedAt: startedAt,
    durationMs: durationMs,
    frames: frames,
    keptFrames: keptFrames,
    overBudgetFrames: overBudgetFrames,
    ui: ui,
    raster: raster,
    budgetMs: budgetMs,
    reportedHz: reportedHz,
    measuredHz: measuredHz,
    glassMode: glassMode,
    effectiveRenderer: effectiveRenderer,
    deviceModel: deviceModel,
    androidVersion: androidVersion,
    appVersion: appVersion,
    interfaceScale: interfaceScale,
    modeChangedDuringRecording: modeChangedDuringRecording,
    screen: name,
  );

  double? get jankPercent => overBudgetFrames == null || keptFrames == 0
      ? null
      : overBudgetFrames! * 100 / keptFrames;

  factory PerformanceSession.fromJson(Map<String, Object?> json) {
    FramePhaseStats? phase(String key) => json[key] == null
        ? null
        : FramePhaseStats.fromJson(json[key]! as Map<String, Object?>);
    double? number(String key) => (json[key] as num?)?.toDouble();
    return PerformanceSession(
      startedAt: DateTime.parse(json['started_at']! as String),
      durationMs: json['duration_ms']! as int,
      frames: json['frames']! as int,
      keptFrames: json['kept_frames']! as int,
      overBudgetFrames: json['over_budget_frames'] as int?,
      ui: phase('ui_ms'),
      raster: phase('raster_ms'),
      budgetMs: number('budget_ms'),
      reportedHz: number('reported_hz'),
      measuredHz: number('measured_hz'),
      glassMode: mornyeGlassLevelFromString(json['glass_mode'] as String?),
      effectiveRenderer: mornyeGlassLevelFromString(
        json['effective_renderer'] as String?,
      ),
      deviceModel: json['device_model'] as String?,
      androidVersion: json['android_version'] as String?,
      appVersion: json['app_version']! as String,
      interfaceScale: (json['interface_scale']! as num).toDouble(),
      modeChangedDuringRecording: json['mode_changed'] as bool? ?? false,
      screen: json['screen'] as String?,
    );
  }

  Map<String, Object?> toJson() => {
    'started_at': startedAt.toIso8601String(),
    'duration_ms': durationMs,
    'frames': frames,
    'kept_frames': keptFrames,
    'over_budget_frames': overBudgetFrames,
    'ui_ms': ui?.toJson(),
    'raster_ms': raster?.toJson(),
    'budget_ms': budgetMs,
    'reported_hz': reportedHz,
    'measured_hz': measuredHz,
    'glass_mode': glassMode.name,
    if (screen != null) 'screen': screen,
    'effective_renderer': effectiveRenderer.name,
    'device_model': deviceModel,
    'android_version': androidVersion,
    'app_version': appVersion,
    'interface_scale': interfaceScale,
    'mode_changed': modeChangedDuringRecording,
  };
}

/// Builds a session from the kept frames. The budget uses the reported
/// refresh rate, or the measured one when the engine reports none.
PerformanceSession measurePerformanceSession({
  required DateTime startedAt,
  required Duration duration,
  required FrameSamples samples,
  required double? reportedHz,
  required MornyeGlassLevel glassMode,
  required MornyeGlassLevel effectiveRenderer,
  required String? deviceModel,
  required String? androidVersion,
  required String appVersion,
  required double interfaceScale,
  bool modeChangedDuringRecording = false,
}) {
  final measuredHz = refreshHzFromIntervals(samples.intervalUs);
  final budgetUs = frameBudgetUs(validRefreshHz(reportedHz) ?? measuredHz);
  return PerformanceSession(
    startedAt: startedAt,
    durationMs: duration.inMilliseconds,
    frames: samples.frameCount,
    keptFrames: samples.buildUs.length,
    overBudgetFrames: budgetUs == null
        ? null
        : countOverBudget(samples.buildUs, samples.rasterUs, budgetUs),
    ui: summarizeFramePhase(samples.buildUs),
    raster: summarizeFramePhase(samples.rasterUs),
    budgetMs: budgetUs == null ? null : budgetUs / 1000,
    reportedHz: validRefreshHz(reportedHz),
    measuredHz: measuredHz,
    glassMode: glassMode,
    effectiveRenderer: effectiveRenderer,
    deviceModel: deviceModel,
    androidVersion: androidVersion,
    appVersion: appVersion,
    interfaceScale: interfaceScale,
    modeChangedDuringRecording: modeChangedDuringRecording,
  );
}

/// A saved result: one session, or the three modes of a comparison.
class PerformanceRun {
  const PerformanceRun({
    required this.at,
    required this.compare,
    required this.sessions,
    this.skipped = const [],
  });

  final DateTime at;
  final bool compare;
  final List<PerformanceSession> sessions;

  /// Screens an automatic measurement did not measure, with the reason.
  final List<String> skipped;

  factory PerformanceRun.fromJson(Map<String, Object?> json) => PerformanceRun(
    at: DateTime.parse(json['at']! as String),
    compare: json['compare']! as bool,
    sessions: [
      for (final session in json['sessions']! as List)
        PerformanceSession.fromJson((session as Map).cast<String, Object?>()),
    ],
    skipped: [
      for (final line in (json['skipped'] as List?) ?? const []) line as String,
    ],
  );

  Map<String, Object?> toJson() => {
    'at': at.toIso8601String(),
    'compare': compare,
    'sessions': [for (final session in sessions) session.toJson()],
    if (skipped.isNotEmpty) 'skipped': skipped,
  };
}

/// Plain-text copy for pasting into a message. Its labels stay English, like
/// the JSON keys, so a shared report reads the same on every device.
String performanceRunText(PerformanceRun run) {
  String value(num? number, [String unit = '']) =>
      number == null ? 'not available' : '${number.toStringAsFixed(2)}$unit';
  String phase(FramePhaseStats? stats) => stats == null
      ? 'not available'
      : 'P50 ${value(stats.p50)}  P95 ${value(stats.p95)}  '
            'P99 ${value(stats.p99)}  max ${value(stats.max)} ms';
  final buffer = StringBuffer()
    ..writeln(
      'Sistrum performance hub (${run.compare ? 'mode comparison' : 'session'})',
    )
    ..writeln('Recorded: ${run.at.toIso8601String()}')
    ..writeln(
      'Note: release builds are measured; profile mode is more accurate.',
    );
  for (final session in run.sessions) {
    final jank = session.jankPercent;
    // No reported rate means the budget used the measured interval instead.
    final jankNote = jank != null && session.reportedHz == null
        ? ' (unreliable: refresh rate not reported)'
        : '';
    buffer
      ..writeln()
      ..writeln('Glass mode: ${session.glassMode.name}')
      ..writeln('Effective renderer: ${session.effectiveRenderer.name}')
      ..writeln('Duration: ${(session.durationMs / 1000).toStringAsFixed(1)} s')
      ..writeln('Frames: ${session.frames} (statistics: ${session.keptFrames})')
      ..writeln('UI build: ${phase(session.ui)}')
      ..writeln('Raster: ${phase(session.raster)}')
      ..writeln(
        'Over budget: ${jank == null ? 'not available' : '${jank.toStringAsFixed(1)} %'}$jankNote',
      )
      ..writeln('Frame budget: ${value(session.budgetMs, ' ms')}')
      ..writeln('Refresh rate reported: ${value(session.reportedHz, ' Hz')}')
      ..writeln('Refresh rate measured: ${value(session.measuredHz, ' Hz')}')
      ..writeln('Device: ${session.deviceModel ?? 'not available'}')
      ..writeln('Android: ${session.androidVersion ?? 'not available'}')
      ..writeln('App version: ${session.appVersion}')
      ..writeln('Interface scale: ${session.interfaceScale.toStringAsFixed(2)}')
      ..writeln(
        'Glass mode changed during recording: '
        '${session.modeChangedDuringRecording ? 'yes' : 'no'}',
      );
  }
  final automatic = run.sessions.where((session) => session.screen != null);
  if (automatic.isNotEmpty) {
    buffer
      ..writeln()
      ..writeln('Automatic measurement (programmatic scroll at 8000 px/s)')
      ..writeln(
        'Screen | Mode | Frames | Raster P50 | P95 | P99 | UI P50 | P95 | '
        'Over budget',
      );
    String ms(FramePhaseStats? stats, double? Function(FramePhaseStats) pick) {
      final v = stats == null ? null : pick(stats);
      return v == null ? 'not available' : v.toStringAsFixed(2);
    }

    for (final session in automatic) {
      final jank = session.jankPercent;
      buffer.writeln(
        '${session.screen} | ${session.glassMode.name} | ${session.frames} | '
        '${ms(session.raster, (x) => x.p50)} | '
        '${ms(session.raster, (x) => x.p95)} | '
        '${ms(session.raster, (x) => x.p99)} | '
        '${ms(session.ui, (x) => x.p50)} | '
        '${ms(session.ui, (x) => x.p95)} | '
        '${jank == null ? 'not available' : '${jank.toStringAsFixed(1)} %'}',
      );
    }
  }
  for (final line in run.skipped) {
    buffer.writeln('Skipped: $line');
  }
  return buffer.toString();
}

String performanceRunJson(PerformanceRun run) =>
    const JsonEncoder.withIndent('  ').convert(run.toJson());
