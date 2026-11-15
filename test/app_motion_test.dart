import 'package:flutter_test/flutter_test.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:sistrum/theme/app_motion.dart';

void main() {
  test('spring curves start at rest and settle within their duration', () {
    for (final spring in [
      M3EMotion.expressiveSpatialFast,
      M3EMotion.expressiveSpatialDefault,
      M3EMotion.expressiveSpatialSlow,
      M3EMotion.effectsFast,
      M3EMotion.effectsDefault,
    ]) {
      final curve = SpringCurve(spring);
      expect(curve.transform(0), 0);
      expect(curve.transform(1), 1);
      expect(curve.transform(0.98), closeTo(1, 0.01));
      expect(curve.duration.inMilliseconds, inInclusiveRange(100, 1200));
    }
  });

  test('spatial springs overshoot while effects springs do not', () {
    double peak(SpringCurve curve) => [
      for (var t = 0.0; t <= 1; t += 0.01) curve.transform(t),
    ].reduce((a, b) => a > b ? a : b);

    expect(
      peak(SpringCurve(M3EMotion.expressiveSpatialDefault)),
      greaterThan(1),
    );
    expect(peak(SpringCurve(M3EMotion.effectsDefault)), lessThanOrEqualTo(1));
  });
}
