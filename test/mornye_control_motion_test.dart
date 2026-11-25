import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// The Mornye controls keep their motion when a new value interrupts them, and
/// the switch keeps the finger's velocity when let go.
void main() {
  group('segmented selection', () {
    late StateSetter update;
    late int selected;

    Future<void> pumpSegments(
      WidgetTester tester, {
      bool reduced = false,
    }) async {
      selected = 0;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            // None mode: the thumb is an ordinary tinted capsule.
            mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.flat),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            home: MediaQuery(
              data: MediaQueryData(disableAnimations: reduced),
              child: Center(
                child: SizedBox(
                  width: 300,
                  height: 64,
                  child: StatefulBuilder(
                    builder: (_, setState) {
                      update = setState;
                      return MornyeSegmentedControl(
                        labels: const ['A', 'B', 'C'],
                        selectedIndex: selected,
                        onChanged: (value) => setState(() => selected = value),
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    /// The thumb's horizontal alignment: -1 for the first slot, 1 for the last.
    double thumb(WidgetTester tester) {
      final align = tester.widget<Align>(
        find
            .descendant(
              of: find.byType(MornyeSegmentedControl),
              matching: find.byWidgetPredicate(
                (widget) =>
                    widget is Align &&
                    widget.alignment is Alignment &&
                    widget.child is FractionallySizedBox,
              ),
            )
            .first,
      );
      return (align.alignment as Alignment).x;
    }

    testWidgets('a tap selects and the thumb slides to the new slot', (
      tester,
    ) async {
      await pumpSegments(tester);
      expect(thumb(tester), -1);
      await tester.tap(find.text('C'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(selected, 2);
      expect(thumb(tester), inExclusiveRange(-1.0, 1.0));
      await tester.pumpAndSettle();
      expect(thumb(tester), 1);
      expect(find.byType(LiquidGlassMotionPill), findsNothing);
    });

    testWidgets('reduced motion moves the thumb at once', (tester) async {
      await pumpSegments(tester, reduced: true);
      update(() => selected = 2);
      await tester.pump();
      expect(thumb(tester), 1);
      expect(tester.binding.transientCallbackCount, 0);
    });
  });

  group('switch', () {
    late bool value;

    Future<void> pumpSwitch(WidgetTester tester, {bool reduced = false}) async {
      value = false;
      // These check the plain switch's thumb (None). Liquid uses the package
      // switch, which has its own motion and is not measured here.
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.flat),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            home: MediaQuery(
              data: MediaQueryData(disableAnimations: reduced),
              child: Scaffold(
                body: Center(
                  child: StatefulBuilder(
                    builder: (context, setState) => AppSwitch(
                      value: value,
                      onChanged: (next) => setState(() => value = next),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    /// The thumb's directional alignment: -1 off, 1 on.
    double thumb(WidgetTester tester) {
      final align = tester.widget<Align>(
        find
            .descendant(
              of: find.byType(AppSwitch),
              matching: find.byWidgetPredicate(
                (widget) =>
                    widget is Align && widget.alignment is AlignmentDirectional,
              ),
            )
            .first,
      );
      return (align.alignment as AlignmentDirectional).start;
    }

    testWidgets('a second tap mid-move goes on from the thumb velocity', (
      tester,
    ) async {
      await pumpSwitch(tester);
      await tester.tap(find.byType(AppSwitch));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 40));
      final before = thumb(tester);
      expect(value, isTrue);
      expect(before, inExclusiveRange(-1.0, 1.0));

      await tester.tap(find.byType(AppSwitch));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 16));
      expect(value, isFalse);
      // Still travelling on, then back, with no jump to either side.
      expect(thumb(tester), greaterThanOrEqualTo(before));
      expect(thumb(tester) - before, lessThan(0.5));

      await tester.pumpAndSettle();
      expect(thumb(tester), -1);
    });

    Future<double> thumbAfterRelease(
      WidgetTester tester,
      Duration dragTime,
    ) async {
      await pumpSwitch(tester);
      // Just past the middle of the 22dp travel, after the drag slop.
      final center = tester.getCenter(find.byType(AppSwitch));
      final gesture = await tester.startGesture(center - const Offset(20, 0));
      for (var step = 1; step <= 8; step++) {
        await gesture.moveBy(
          const Offset(4, 0),
          timeStamp: dragTime * step ~/ 8,
        );
        await tester.pump(dragTime ~/ 8);
      }
      await gesture.up(timeStamp: dragTime);
      expect(thumb(tester), closeTo(1 / 11, 0.001));
      // The spring starts with the next build, then moves a frame.
      await tester.pump(const Duration(milliseconds: 16));
      await tester.pump(const Duration(milliseconds: 16));
      final early = thumb(tester);
      await tester.pumpAndSettle();
      expect(value, isTrue);
      expect(thumb(tester), 1);
      return early;
    }

    testWidgets('a released thumb keeps the finger velocity', (tester) async {
      final quick = await thumbAfterRelease(
        tester,
        const Duration(milliseconds: 80),
      );
      final slow = await thumbAfterRelease(tester, const Duration(seconds: 2));
      // A frame after release a quick drag has carried the thumb further.
      expect(quick, greaterThan(slow + 0.05));
    });

    testWidgets('reduced motion moves the thumb at once', (tester) async {
      await pumpSwitch(tester, reduced: true);
      await tester.tap(find.byType(AppSwitch));
      await tester.pump();
      expect(thumb(tester), 1);
    });
  });
}
