import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/widgets/mornye_glass_lens_fade.dart';

void main() {
  test('the lens stays on until 55% of the collapse and is off from 95%', () {
    expect(MornyeGlassLensFade.opacityFor(0, 200), 1);
    expect(MornyeGlassLensFade.opacityFor(110, 200), 1);
    expect(MornyeGlassLensFade.opacityFor(150, 200), inExclusiveRange(0, 1));
    expect(MornyeGlassLensFade.opacityFor(190, 200), 0);
    expect(MornyeGlassLensFade.opacityFor(400, 200), 0);
    expect(MornyeGlassLensFade.opacityFor(50, 0), 1);
  });

  testWidgets('buttons read the lens opacity from the scroll position', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    double? seen;
    await tester.pumpWidget(
      MaterialApp(
        home: CustomScrollView(
          controller: controller,
          slivers: [
            SliverAppBar(
              pinned: true,
              leading: MornyeGlassLensFade(
                collapseExtent: 200,
                child: Builder(
                  builder: (context) {
                    seen = MornyeGlassLensFade.opacityOf(context);
                    return const SizedBox.square(dimension: 44);
                  },
                ),
              ),
            ),
            const SliverToBoxAdapter(child: SizedBox(height: 2000)),
          ],
        ),
      ),
    );
    expect(seen, 1);
    controller.jumpTo(150);
    await tester.pump();
    expect(seen, inExclusiveRange(0, 1));
    controller.jumpTo(300);
    await tester.pump();
    expect(seen, 0);
    controller.jumpTo(0);
    await tester.pump();
    expect(seen, 1);
    expect(
      MornyeGlassLensFade.opacityOf(
        tester.element(find.byType(SizedBox).first),
      ),
      1,
    );
  });
}
