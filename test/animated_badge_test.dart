import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/animation_utils.dart';

void main() {
  Future<void> pumpBadge(
    WidgetTester tester, {
    required int count,
    bool reducedMotion = false,
  }) => tester.pumpWidget(
    MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: reducedMotion),
        child: Center(
          child: AnimatedBadge(count: count, child: Text('$count')),
        ),
      ),
    ),
  );

  double scale(WidgetTester tester) => tester
      .widget<ScaleTransition>(
        find.descendant(
          of: find.byType(AnimatedBadge),
          matching: find.byType(ScaleTransition),
        ),
      )
      .scale
      .value;

  testWidgets('reduced motion shows a new count without a bump', (
    tester,
  ) async {
    await pumpBadge(tester, count: 1, reducedMotion: true);
    await pumpBadge(tester, count: 2, reducedMotion: true);
    for (var frame = 0; frame < 30; frame++) {
      expect(scale(tester), 1);
      await tester.pump(const Duration(milliseconds: 16));
    }
  });

  /// Pumps frames until the bump settles and returns the lowest and highest
  /// scales seen.
  Future<(double, double)> track(WidgetTester tester) async {
    var low = scale(tester);
    var high = low;
    for (var frame = 0; frame < 60; frame++) {
      await tester.pump(const Duration(milliseconds: 8));
      low = math.min(low, scale(tester));
      high = math.max(high, scale(tester));
    }
    await tester.pumpAndSettle();
    return (low, high);
  }

  testWidgets('pushes on its fast spring without a bounce', (tester) async {
    await pumpBadge(tester, count: 1);
    await pumpBadge(tester, count: 2);
    expect(scale(tester), 1);
    final (low, high) = await track(tester);
    expect(high, closeTo(1 + AnimatedBadge.bumpHeight, 0.01));
    expect(low, greaterThanOrEqualTo(1 - 1e-6));
    expect(scale(tester), 1);
  });

  testWidgets('a count that grows mid-bump goes on from the scale on screen', (
    tester,
  ) async {
    await pumpBadge(tester, count: 1);
    await pumpBadge(tester, count: 2);
    await tester.pump(const Duration(milliseconds: 16));
    await tester.pump(const Duration(milliseconds: 16));
    final before = scale(tester);
    expect(before, greaterThan(1.05));

    await pumpBadge(tester, count: 3);
    expect(scale(tester), before);
    var previous = before;
    for (var frame = 0; frame < 3; frame++) {
      await tester.pump(const Duration(milliseconds: 8));
      // No restart from 1: each frame moves on from the last one.
      expect(scale(tester), greaterThan(1.05));
      expect((scale(tester) - previous).abs(), lessThan(0.1));
      previous = scale(tester);
    }
    await tester.pumpAndSettle();
    expect(scale(tester), 1);
  });
}
