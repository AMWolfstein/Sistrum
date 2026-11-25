import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/constants/app_info.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/services/performance_auto_drive.dart';
import 'package:sistrum/services/performance_auto_measure.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/services/frame_timing_recorder.dart';
import 'package:sistrum/utils/frame_timing_stats.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('PerformanceHub');

const kPerformanceRunsKey = 'performance_hub_runs';
const kPerformanceRunsKept = 5;

/// Order of the guided comparison.
const _compareModes = [MornyeGlassLevel.liquid, MornyeGlassLevel.flat];

/// How long the owner scrolls in each mode of a comparison.
final performanceCompareWindowProvider = Provider<Duration>(
  (ref) => const Duration(seconds: 10),
);

/// Fewest frames a window needs to be saved (one second at 60 Hz). A shorter
/// window, such as one the app was backgrounded in, is discarded.
final performanceMinFramesProvider = Provider<int>((ref) => 60);

/// Whether a recording or comparison runs. The recording badge watches only
/// this, so the hub and its saved runs load when a recording starts or the
/// hub opens.
final performanceRecordingActiveProvider =
    NotifierProvider<PerformanceRecordingFlag, bool>(
      PerformanceRecordingFlag.new,
    );

class PerformanceRecordingFlag extends Notifier<bool> {
  @override
  bool build() => false;

  void setActive(bool value) => state = value;
}

/// Calls [onPause] when the app leaves the foreground. Added only while a
/// recording or comparison runs.
class _LifecycleWatch extends WidgetsBindingObserver {
  _LifecycleWatch(this.onPause);

  final VoidCallback onPause;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      onPause();
    }
  }
}

/// A recording in progress, refreshed once a second while it runs.
class PerformanceLive {
  const PerformanceLive({
    required this.compare,
    required this.mode,
    required this.elapsed,
    required this.frames,
    required this.keptFrames,
    required this.lastBuildUs,
    required this.lastRasterUs,
    required this.overBudgetFrames,
    required this.stepSecondsLeft,
  });

  final bool compare;

  /// The glass mode being measured: the comparison's mode, or else the
  /// selected mode. Not the device fallback.
  final MornyeGlassLevel mode;
  final Duration elapsed;
  final int frames;
  final int keptFrames;
  final int lastBuildUs;
  final int lastRasterUs;

  /// Null when no refresh rate is known yet.
  final int? overBudgetFrames;

  /// Seconds left in the current comparison step; zero outside a comparison.
  final int stepSecondsLeft;
}

class PerformanceHubState {
  const PerformanceHubState({
    this.live,
    this.runs = const [],
    this.tooFewFrames = false,
  });

  /// Null when nothing is recording, so the hub does no work.
  final PerformanceLive? live;

  /// Newest first, at most [kPerformanceRunsKept].
  final List<PerformanceRun> runs;

  /// The last recording or comparison window was discarded for too few
  /// frames. Cleared when the next recording starts.
  final bool tooFewFrames;
}

/// What a recording needs from the moment it starts.
class _Recording {
  const _Recording({
    required this.startedAt,
    required this.mode,
    required this.effective,
    required this.interfaceScale,
    required this.reportedHz,
    required this.compare,
  });

  final DateTime startedAt;
  final MornyeGlassLevel mode;
  final MornyeGlassLevel effective;
  final double interfaceScale;
  final double? reportedHz;
  final bool compare;
}

final performanceHubProvider =
    NotifierProvider<PerformanceHubNotifier, PerformanceHubState>(
      PerformanceHubNotifier.new,
    );

class PerformanceHubNotifier extends Notifier<PerformanceHubState> {
  final _recorder = FrameTimingRecorder();
  Timer? _ticker;
  _Recording? _current;
  DateTime? _stepEnds;
  Completer<void>? _stepStop;
  bool _cancelled = false;
  bool _disposed = false;

  /// The glass mode changed while a single recording ran.
  bool _modeChanged = false;
  _LifecycleWatch? _lifecycle;
  Future<({String? model, String? androidVersion})>? _device;

  @override
  PerformanceHubState build() {
    // A comparison changes the mode itself, so only a single recording counts.
    ref.listen(mornyeGlassActiveModeProvider, (previous, next) {
      final current = _current;
      if (current != null && !current.compare && previous != next) {
        _modeChanged = true;
      }
    });
    ref.onDispose(() {
      _disposed = true;
      _ticker?.cancel();
      if (_recorder.isRecording) _recorder.stop();
      if (_stepStop?.isCompleted == false) _stepStop!.complete();
      _detachLifecycle();
    });
    unawaited(_loadRuns());
    return const PerformanceHubState();
  }

  /// Starts a recording of the selected mode. Frames are counted until
  /// [stopRecording]; the page and the recording badge can be left meanwhile.
  void startRecording() {
    if (!_claim()) return;
    _begin(compare: false);
  }

  Future<void> stopRecording() async {
    final live = state.live;
    if (live == null || live.compare) return;
    try {
      final session = await _finish();
      if (session != null && _enoughFrames(session)) {
        await _saveRun(
          PerformanceRun(
            at: DateTime.now(),
            compare: false,
            sessions: [session],
          ),
        );
      }
    } finally {
      _release();
      _emit(
        PerformanceHubState(runs: state.runs, tooFewFrames: state.tooFewFrames),
      );
    }
  }

  /// Measures Liquid and None in turn. Each mode is rendered from
  /// memory, so the saved selection is never written. A cancelled or short
  /// comparison is not saved.
  Future<void> compareModes() async {
    if (!_claim()) return;
    final override = ref.read(mornyeGlassCompareModeProvider.notifier);
    final window = ref.read(performanceCompareWindowProvider);
    final sessions = <PerformanceSession>[];
    var valid = true;
    _cancelled = false;
    try {
      for (final mode in _compareModes) {
        override.setMode(mode);
        final stop = _stepStop = Completer<void>();
        _begin(compare: true, stepEnds: DateTime.now().add(window));
        final timer = Timer(window, () {
          if (!stop.isCompleted) stop.complete();
        });
        await stop.future;
        timer.cancel();
        _stepStop = null;
        final session = await _finish();
        if (session == null || _cancelled || !_enoughFrames(session)) {
          valid = false;
          break;
        }
        sessions.add(session);
      }
      if (valid && sessions.length == _compareModes.length) {
        await _saveRun(
          PerformanceRun(at: DateTime.now(), compare: true, sessions: sessions),
        );
      }
    } finally {
      _stepStop = null;
      await _endComparison(override);
    }
  }

  /// Drives the app through [steps] in Liquid, then None, measuring each
  /// screen while it scrolls. Each mode is rendered from memory, so the saved
  /// mode is never written, and the rendered mode is restored at the end, also
  /// on cancel or error. The run is saved as one comparison, with its skipped
  /// screens and any error.
  Future<void> runAutomaticMeasurement({
    required AutoMeasureDriver driver,
    required String noScrollReason,
    required String tooFewFramesReason,
  }) async {
    if (!_claim()) return;
    final override = ref.read(mornyeGlassCompareModeProvider.notifier);
    _cancelled = false;
    AutoMeasureOutcome? outcome;
    try {
      outcome = await runAutoMeasure(
        modes: const [MornyeGlassLevel.liquid, MornyeGlassLevel.flat],
        steps: driver.steps,
        setRenderMode: override.setMode,
        isCancelled: () => _cancelled,
        noScrollReason: noScrollReason,
        tooFewFramesReason: tooFewFramesReason,
        frames: () => _recorder.frameCount,
        minFrames: ref.read(performanceMinFramesProvider),
        measure: (screen, mode, drive) async {
          _begin(compare: true);
          await drive();
          final session = await _finish();
          if (session == null || _cancelled || !_enoughFrames(session)) {
            return null;
          }
          return session.withScreen(screen);
        },
      );
    } catch (e) {
      _log.w('Automatic measurement failed: $e');
    } finally {
      // Always: the override is cleared and the hub released (in
      // _endComparison), then the routes this run pushed are removed.
      await _endComparison(override);
      driver.cleanup();
    }
    final result = outcome;
    if (result == null) return;
    final skipped = [
      for (final skip in result.skipped)
        '${skip.screen} (${skip.mode.name}): ${skip.reason}',
      if (result.error != null) 'error: ${result.error}',
    ];
    // A cancelled or backgrounded run is not saved, even with rows (decision 76).
    if (!result.cancelled && result.rows.isNotEmpty) {
      await _saveRun(
        PerformanceRun(
          at: DateTime.now(),
          compare: true,
          sessions: [for (final row in result.rows) row.session],
          skipped: skipped,
        ),
      );
    }
  }

  /// Ends the current comparison step early. The run is then discarded.
  void cancelComparison() {
    _cancelled = true;
    final stop = _stepStop;
    if (stop != null && !stop.isCompleted) stop.complete();
  }

  /// Claims the hub for one recording or comparison; false when one runs.
  /// The check and the claim are one synchronous step, so a second start
  /// cannot interleave.
  bool _claim() {
    if (ref.read(performanceRecordingActiveProvider)) return false;
    ref.read(performanceRecordingActiveProvider.notifier).setActive(true);
    _lifecycle = _LifecycleWatch(cancelComparison);
    WidgetsBinding.instance.addObserver(_lifecycle!);
    _emit(PerformanceHubState(runs: state.runs));
    return true;
  }

  void _release() {
    _detachLifecycle();
    ref.read(performanceRecordingActiveProvider.notifier).setActive(false);
  }

  void _detachLifecycle() {
    final watch = _lifecycle;
    if (watch == null) return;
    WidgetsBinding.instance.removeObserver(watch);
    _lifecycle = null;
  }

  /// Restores the rendered mode from memory. It runs in a finally, so a
  /// failure here still releases the hub and emits the final state.
  Future<void> _endComparison(MornyeGlassCompareModeNotifier override) async {
    try {
      if (_current != null) await _finish();
    } catch (e) {
      _log.w('Comparison ended with an error: $e');
    } finally {
      try {
        override.setMode(null);
      } finally {
        _release();
        _emit(
          PerformanceHubState(
            runs: state.runs,
            tooFewFrames: state.tooFewFrames,
          ),
        );
      }
    }
  }

  bool _enoughFrames(PerformanceSession session) {
    final minimum = ref.read(performanceMinFramesProvider);
    if (session.frames >= minimum) return true;
    _log.w('Window discarded: ${session.frames} frames, minimum $minimum');
    _emit(
      PerformanceHubState(
        runs: state.runs,
        live: state.live,
        tooFewFrames: true,
      ),
    );
    return false;
  }

  void _begin({required bool compare, DateTime? stepEnds}) {
    _stepEnds = stepEnds;
    _modeChanged = false;
    _current = _Recording(
      startedAt: DateTime.now(),
      mode: ref.read(mornyeGlassActiveModeProvider),
      effective: ref.read(mornyeGlassLevelProvider),
      interfaceScale: ref.read(settingsProvider).interfaceScale,
      reportedHz:
          ui.PlatformDispatcher.instance.implicitView?.display.refreshRate,
      compare: compare,
    );
    _recorder.start();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => _publish());
    _publish();
  }

  void _publish() {
    final current = _current;
    if (current == null) return;
    final samples = _recorder.samples();
    final budgetUs = frameBudgetUs(
      validRefreshHz(current.reportedHz) ??
          refreshHzFromIntervals(samples.intervalUs),
    );
    final stepEnds = _stepEnds;
    final stepLeft = stepEnds == null
        ? 0
        : (stepEnds.difference(DateTime.now()).inMilliseconds / 1000).ceil();
    _emit(
      PerformanceHubState(
        runs: state.runs,
        tooFewFrames: state.tooFewFrames,
        live: PerformanceLive(
          compare: current.compare,
          mode: current.mode,
          elapsed: DateTime.now().difference(current.startedAt),
          frames: _recorder.frameCount,
          keptFrames: samples.buildUs.length,
          lastBuildUs: _recorder.lastBuildUs,
          lastRasterUs: _recorder.lastRasterUs,
          overBudgetFrames: budgetUs == null
              ? null
              : countOverBudget(samples.buildUs, samples.rasterUs, budgetUs),
          stepSecondsLeft: stepLeft < 0 ? 0 : stepLeft,
        ),
      ),
    );
  }

  Future<PerformanceSession?> _finish() async {
    _ticker?.cancel();
    _ticker = null;
    final current = _current;
    if (current == null) return null;
    _current = null;
    final duration = DateTime.now().difference(current.startedAt);
    final samples = _recorder.stop();
    final device = await _deviceInfo();
    return measurePerformanceSession(
      startedAt: current.startedAt,
      duration: duration,
      samples: samples,
      reportedHz: current.reportedHz,
      glassMode: current.mode,
      effectiveRenderer: current.effective,
      deviceModel: device.model,
      androidVersion: device.androidVersion,
      appVersion: '${AppInfo.displayVersion} (build ${AppInfo.buildNumber})',
      interfaceScale: current.interfaceScale,
      modeChangedDuringRecording: !current.compare && _modeChanged,
    );
  }

  Future<({String? model, String? androidVersion})> _deviceInfo() =>
      _device ??= _readDevice();

  Future<({String? model, String? androidVersion})> _readDevice() async {
    if (!Platform.isAndroid) return (model: null, androidVersion: null);
    try {
      final info = await DeviceInfoPlugin().androidInfo;
      return (model: info.model, androidVersion: info.version.release);
    } catch (e) {
      _log.w('Device details unavailable: $e');
      return (model: null, androidVersion: null);
    }
  }

  void _emit(PerformanceHubState next) {
    if (!_disposed) state = next;
  }

  Future<void> _loadRuns() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(kPerformanceRunsKey);
      if (raw == null || _disposed || state.runs.isNotEmpty) return;
      final runs = [
        for (final item in jsonDecode(raw) as List)
          PerformanceRun.fromJson((item as Map).cast<String, Object?>()),
      ];
      _emit(
        PerformanceHubState(
          runs: runs.take(kPerformanceRunsKept).toList(),
          live: state.live,
          tooFewFrames: state.tooFewFrames,
        ),
      );
    } catch (e) {
      _log.w('Saved performance results unavailable: $e');
    }
  }

  Future<void> _saveRun(PerformanceRun run) async {
    final runs = [run, ...state.runs].take(kPerformanceRunsKept).toList();
    _emit(
      PerformanceHubState(
        runs: runs,
        live: state.live,
        tooFewFrames: state.tooFewFrames,
      ),
    );
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(
        kPerformanceRunsKey,
        jsonEncode([for (final item in runs) item.toJson()]),
      );
    } catch (e) {
      _log.w('Performance results not saved: $e');
    }
  }
}
