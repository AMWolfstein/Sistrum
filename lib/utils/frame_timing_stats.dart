// Pure frame statistics for the Performance hub. Durations are microseconds
// unless a field says milliseconds.

/// Refresh rates the engine can report or measure as valid, else null.
double? validRefreshHz(double? hz) =>
    hz != null && hz.isFinite && hz > 0 ? hz : null;

/// Frame budget in microseconds for [refreshHz], or null when unknown.
double? frameBudgetUs(double? refreshHz) {
  final hz = validRefreshHz(refreshHz);
  return hz == null ? null : 1000000 / hz;
}

/// Refresh rate from the median vsync interval (zero means no previous frame).
double? refreshHzFromIntervals(Iterable<int> intervalsUs) {
  final sorted = intervalsUs.where((us) => us > 0).toList()..sort();
  if (sorted.isEmpty) return null;
  return 1000000 / sorted[sorted.length ~/ 2];
}

/// Nearest-rank percentile of [sortedUs]; [sortedUs] must not be empty.
int _nearestRank(List<int> sortedUs, double percent) {
  final rank = (percent / 100 * sortedUs.length).ceil().clamp(
    1,
    sortedUs.length,
  );
  return sortedUs[rank - 1];
}

/// Frame count where build or raster exceeded [budgetUs]. This matches the
/// integration probe, so numbers from the app and the probe are comparable.
int countOverBudget(List<int> buildUs, List<int> rasterUs, double budgetUs) {
  var over = 0;
  for (var i = 0; i < buildUs.length; i++) {
    if (buildUs[i] > budgetUs || rasterUs[i] > budgetUs) over++;
  }
  return over;
}

/// P50, P95, P99 and max of one frame phase, in milliseconds.
class FramePhaseStats {
  const FramePhaseStats({
    required this.p50,
    required this.p95,
    required this.p99,
    required this.max,
  });

  final double p50;
  final double p95;
  final double p99;
  final double max;

  factory FramePhaseStats.fromJson(Map<String, Object?> json) =>
      FramePhaseStats(
        p50: (json['p50']! as num).toDouble(),
        p95: (json['p95']! as num).toDouble(),
        p99: (json['p99']! as num).toDouble(),
        max: (json['max']! as num).toDouble(),
      );

  Map<String, Object?> toJson() => {
    'p50': p50,
    'p95': p95,
    'p99': p99,
    'max': max,
  };
}

/// Null when [durationsUs] is empty, so the page shows "not available".
FramePhaseStats? summarizeFramePhase(Iterable<int> durationsUs) {
  final sorted = durationsUs.toList()..sort();
  if (sorted.isEmpty) return null;
  double ms(double percent) => _nearestRank(sorted, percent) / 1000;
  return FramePhaseStats(
    p50: ms(50),
    p95: ms(95),
    p99: ms(99),
    max: sorted.last / 1000,
  );
}
