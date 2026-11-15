import 'package:flutter/widgets.dart' show AppLifecycleState;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/widgets/decorative_phase.dart';

void main() {
  tearDown(DecorativePhase.debugResetClock);

  group('DecorativePhase', () {
    testWidgets('advances at most 30 times a second at loop speed', (
      tester,
    ) async {
      final phase = DecorativePhase(const Duration(seconds: 2));
      addTearDown(phase.dispose);
      var notifications = 0;
      phase
        ..addListener(() => notifications++)
        ..start();
      expect(DecorativePhase.debugClockRunning, isTrue);

      await tester.binding.delayed(const Duration(seconds: 1));
      expect(notifications, inInclusiveRange(29, 30));
      // Each tick advances by the time that passed, so one second of a
      // two second loop is half a cycle.
      expect(phase.value, closeTo(0.5, 34 / 2000));

      phase.stop();
      expect(DecorativePhase.debugClockRunning, isFalse);
      final held = phase.value;
      await tester.binding.delayed(const Duration(seconds: 1));
      expect(notifications, inInclusiveRange(29, 30));
      expect(phase.value, held);

      phase.start();
      await tester.binding.delayed(DecorativePhase.interval);
      expect(phase.value, closeTo(held + 34 / 2000, 1e-9));
      phase.stop();
    });

    testWidgets('shares one clock that runs while any phase runs', (
      tester,
    ) async {
      final first = DecorativePhase(const Duration(seconds: 1));
      final second = DecorativePhase(const Duration(seconds: 3));
      addTearDown(first.dispose);
      addTearDown(second.dispose);
      first.start();
      second.start();
      await tester.binding.delayed(const Duration(milliseconds: 340));
      expect(first.value, closeTo(0.34, 1e-9));
      expect(second.value, closeTo(0.34 / 3, 1e-9));

      first.stop();
      expect(DecorativePhase.debugClockRunning, isTrue);
      await tester.binding.delayed(const Duration(milliseconds: 340));
      expect(first.value, closeTo(0.34, 1e-9));
      expect(second.value, closeTo(0.68 / 3, 1e-9));

      second.stop();
      expect(DecorativePhase.debugClockRunning, isFalse);
    });

    testWidgets('holds while the app is hidden and resumes from there', (
      tester,
    ) async {
      addTearDown(
        () => tester.binding.handleAppLifecycleStateChanged(
          AppLifecycleState.resumed,
        ),
      );
      final phase = DecorativePhase(const Duration(seconds: 1));
      addTearDown(phase.dispose);
      phase.start();
      await tester.binding.delayed(const Duration(milliseconds: 340));
      expect(phase.value, closeTo(0.34, 1e-9));

      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
        expect(DecorativePhase.debugClockRunning, state == .inactive);
      }
      await tester.binding.delayed(const Duration(seconds: 1));
      expect(phase.value, closeTo(0.34, 1e-9));

      // Starting while hidden waits for frames too.
      phase.stop();
      phase.start();
      expect(DecorativePhase.debugClockRunning, isFalse);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      expect(DecorativePhase.debugClockRunning, isTrue);
      await tester.binding.delayed(DecorativePhase.interval);
      expect(phase.value, closeTo(0.374, 1e-9));
      phase.stop();
    });
  });
}
