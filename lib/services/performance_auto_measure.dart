import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:sistrum/models/performance_run.dart';
import 'package:sistrum/models/theme_settings.dart';

/// The scroll speed of the automatic measurement: Flutter's maximum fling
/// velocity, in logical pixels a second (`kMaxFlingVelocity`, 8000). Each leg
/// is `distance / velocity` seconds. A programmatic scroll is not a finger, so
/// its frames only compare between the modes of one run.
const double kAutoMeasureScrollVelocity = 8000.0;

/// Screens are scrolled this many viewports down, then back to the top.
const double kAutoMeasureViewports = 2;

/// One screen the run visits. [prepare] shows it and returns null when it is
/// ready, or the reason it is skipped (no data). [position] is the screen's
/// primary scroll position once it is shown, or null when it has none.
class AutoMeasureStep {
  const AutoMeasureStep({
    required this.screen,
    required this.prepare,
    required this.position,
  });

  final String screen;
  final Future<String?> Function() prepare;
  final ScrollPosition? Function() position;
}

/// A measured screen in one mode.
class AutoMeasureRow {
  const AutoMeasureRow({
    required this.screen,
    required this.mode,
    required this.session,
  });

  final String screen;
  final MornyeGlassLevel mode;
  final PerformanceSession session;
}

/// A screen that was not measured in one mode, and why.
class AutoMeasureSkip {
  const AutoMeasureSkip({
    required this.screen,
    required this.mode,
    required this.reason,
  });

  final String screen;
  final MornyeGlassLevel mode;
  final String reason;
}

/// What a run produced. [cancelled] or [error] say how it ended early.
class AutoMeasureOutcome {
  const AutoMeasureOutcome({
    required this.rows,
    required this.skipped,
    this.cancelled = false,
    this.error,
  });

  final List<AutoMeasureRow> rows;
  final List<AutoMeasureSkip> skipped;
  final bool cancelled;
  final String? error;
}

/// Scroll distance for a screen: two viewports, never past its end.
double autoMeasureDistance(ScrollPosition position) => math.min(
  position.maxScrollExtent - position.minScrollExtent,
  position.viewportDimension * kAutoMeasureViewports,
);

/// Duration of one leg at [velocity]: distance divided by speed.
Duration autoMeasureLeg(
  double distance, {
  double velocity = kAutoMeasureScrollVelocity,
}) => Duration(microseconds: (distance / velocity * 1e6).round());

/// Shortest time a screen is scrolled: long enough for the recorder's frame
/// floor (60 frames) at 60 Hz, with margin for a slower refresh.
const Duration kAutoMeasureMinDuration = Duration(seconds: 2);

/// Hard cap on down-and-back cycles, so a screen cannot run forever.
const int kAutoMeasureMaxCycles = 40;

/// Hard cap on the wall-clock time one screen may take, stalls included.
const Duration kAutoMeasureMaxScreenDuration = Duration(seconds: 20);

/// Extra time a leg may take beyond its duration before it is cut short.
const Duration kAutoMeasureLegSlack = Duration(seconds: 3);

/// Moves [position] to [target] over [leg]. A leg that does not finish in
/// time (a muted ticker, a stalled frame) is stopped where it is. A position
/// that is gone (disposed, detached) is reported as an error, not thrown from
/// a timer.
Future<void> _leg(ScrollPosition position, double target, Duration leg) async {
  try {
    await position
        .animateTo(target, duration: leg, curve: Curves.linear)
        .timeout(
          leg + kAutoMeasureLegSlack,
          onTimeout: () => _stopAt(position),
        );
  } on Object catch (e) {
    throw StateError('scroll failed: $e');
  }
}

/// Stops a running scroll at its current position. A detached position has
/// nothing to stop; the next leg reports that as an error.
void _stopAt(ScrollPosition position) {
  try {
    position.jumpTo(position.pixels);
  } on Object catch (_) {}
}

/// Scrolls [position] away from its current place by [autoMeasureDistance]
/// and back, at a fixed speed. The direction is the one with more room, so a
/// list already at its end is scrolled up. The cycle repeats until [minDuration]
/// of scrolling has passed and [frames] reports at least [minFrames], unless
/// [maxCycles] cycles have run or [maxDuration] of wall-clock time has passed
/// ([clock] is the wall clock; the default is a stopwatch). Returns the
/// scrolled time. When [isCancelled]
/// turns true, the running leg is stopped at its current position, within 50 ms,
/// and no further leg starts.
Future<Duration> autoMeasureScroll(
  ScrollPosition position, {
  bool Function()? isCancelled,
  int Function()? frames,
  int minFrames = 0,
  Duration minDuration = kAutoMeasureMinDuration,
  int maxCycles = kAutoMeasureMaxCycles,
  Duration maxDuration = kAutoMeasureMaxScreenDuration,
  Duration Function()? clock,
}) async {
  final watch = Stopwatch()..start();
  final elapsedWall = clock ?? () => watch.elapsed;
  final start = position.pixels;
  final distance = autoMeasureDistance(position);
  if (distance <= 0) return Duration.zero;
  final downRoom = position.maxScrollExtent - start;
  final upRoom = start - position.minScrollExtent;
  final far = downRoom >= upRoom
      ? math.min(start + distance, position.maxScrollExtent)
      : math.max(start - distance, position.minScrollExtent);
  final leg = autoMeasureLeg((far - start).abs());
  bool cancelled() => isCancelled?.call() ?? false;
  bool keepGoing(Duration elapsed, int cycles) =>
      (elapsed < minDuration || (frames != null && frames() < minFrames)) &&
      cycles < maxCycles &&
      elapsedWall() < maxDuration &&
      !cancelled();
  final poll = isCancelled == null
      ? null
      : Timer.periodic(const Duration(milliseconds: 50), (_) {
          if (cancelled()) _stopAt(position);
        });
  var elapsed = Duration.zero;
  var cycles = 0;
  try {
    while (keepGoing(elapsed, cycles)) {
      await _leg(position, far, leg);
      elapsed += leg;
      if (cancelled()) break;
      await _leg(position, start, leg);
      elapsed += leg;
      cycles++;
    }
  } finally {
    poll?.cancel();
  }
  return elapsed;
}

/// Runs [steps] in each of [modes], in order: Liquid first, then None. Each
/// mode is rendered through [setRenderMode] (the in-memory override) and the
/// override is always cleared at the end, also on cancel or error. A screen
/// with no data, or no scroll position, is skipped with its reason, and the
/// run goes on. [measure] records one scroll and returns the session, or null
/// when it did not keep enough frames. [frames] reports the frames recorded
/// so far in the window; a scroll goes on until it reaches [minFrames].
Future<AutoMeasureOutcome> runAutoMeasure({
  required List<MornyeGlassLevel> modes,
  required List<AutoMeasureStep> steps,
  required void Function(MornyeGlassLevel? mode) setRenderMode,
  required Future<PerformanceSession?> Function(
    String screen,
    MornyeGlassLevel mode,
    Future<void> Function() drive,
  )
  measure,
  required bool Function() isCancelled,
  required String noScrollReason,
  required String tooFewFramesReason,
  int Function()? frames,
  int minFrames = 0,
}) async {
  final rows = <AutoMeasureRow>[];
  final skipped = <AutoMeasureSkip>[];
  var cancelled = false;
  String? error;
  try {
    for (final mode in modes) {
      setRenderMode(mode);
      for (final step in steps) {
        if (isCancelled()) {
          cancelled = true;
          break;
        }
        final reason = await step.prepare();
        if (isCancelled()) {
          cancelled = true;
          break;
        }
        if (reason != null) {
          skipped.add(
            AutoMeasureSkip(screen: step.screen, mode: mode, reason: reason),
          );
          continue;
        }
        final position = step.position();
        if (position == null) {
          skipped.add(
            AutoMeasureSkip(
              screen: step.screen,
              mode: mode,
              reason: noScrollReason,
            ),
          );
          continue;
        }
        final session = await measure(step.screen, mode, () async {
          await autoMeasureScroll(
            position,
            isCancelled: isCancelled,
            frames: frames,
            minFrames: minFrames,
          );
        });
        if (session == null) {
          skipped.add(
            AutoMeasureSkip(
              screen: step.screen,
              mode: mode,
              reason: tooFewFramesReason,
            ),
          );
        } else {
          rows.add(
            AutoMeasureRow(screen: step.screen, mode: mode, session: session),
          );
        }
      }
      if (cancelled) break;
    }
  } catch (e) {
    error = e.toString();
  } finally {
    setRenderMode(null);
  }
  return AutoMeasureOutcome(
    rows: rows,
    skipped: skipped,
    cancelled: cancelled || isCancelled(),
    error: error,
  );
}
