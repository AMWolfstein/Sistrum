
import 'package:flutter/gestures.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/app_theme.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/hover_play_button.dart';

const _artKey = ValueKey('art');

Widget _app(Widget child, {bool mornye = false, bool reduced = false}) =>
    MaterialApp(
      theme: mornye ? MornyeTheme.build(Brightness.light) : AppTheme.light(),
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
          child: Scaffold(body: Center(child: child)),
        ),
      ),
    );

void main() {
  tearDown(() {
    PaintingBinding.instance.imageCache
      ..clear()
      ..clearLiveImages();
    debugDesktopPlatformOverride = null;
  });

  group('desktop cover hover', () {
    Widget tile({bool mornye = false, bool reduced = false}) => _app(
      SizedBox.square(
        dimension: 160,
        child: GestureDetector(
          onTap: () {},
          child: Stack(
            children: [
              const Positioned.fill(
                child: ColoredBox(key: _artKey, color: Colors.grey),
              ),
              DesktopCoverHover(borderRadius: BorderRadius.circular(10)),
            ],
          ),
        ),
      ),
      mornye: mornye,
      reduced: reduced,
    );

    double layerAlpha(WidgetTester tester) {
      final box = tester.widget<AnimatedContainer>(
        find.byKey(const ValueKey('desktop-hover-layer')),
      );
      return (box.decoration! as BoxDecoration).color!.a;
    }

    for (final reduced in [false, true]) {
      testWidgets('Material shows the hover and press state layer'
          '${reduced ? ' under reduced motion' : ''}', (tester) async {
        debugDesktopPlatformOverride = true;
        await tester.pumpWidget(tile(reduced: reduced));
        expect(layerAlpha(tester), 0);
        expect(find.byType(IconButton), findsNothing);

        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        await mouse.addPointer(location: tester.getCenter(find.byKey(_artKey)));
        await tester.pump();
        expect(layerAlpha(tester), closeTo(0.08, 0.001));
        expect(
          RendererBinding.instance.mouseTracker.debugDeviceActiveCursor(1),
          SystemMouseCursors.click,
        );

        await mouse.down(tester.getCenter(find.byKey(_artKey)));
        await tester.pump();
        expect(layerAlpha(tester), closeTo(0.10, 0.001));
        await mouse.up();
        await tester.pump();
        expect(layerAlpha(tester), closeTo(0.08, 0.001));

        await mouse.moveTo(const Offset(1, 1));
        await tester.pump();
        expect(layerAlpha(tester), 0);
        await tester.pump(const Duration(milliseconds: 400));
        await mouse.removePointer();
      });
    }

    testWidgets('Mornye dims the cover and darkens it while pressed', (
      tester,
    ) async {
      debugDesktopPlatformOverride = true;
      await tester.pumpWidget(tile(mornye: true));
      final scrim = find.byKey(const ValueKey('desktop-hover-scrim'));
      expect(tester.widget<AnimatedOpacity>(scrim).opacity, 0);

      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      final center = tester.getCenter(find.byKey(_artKey));
      await mouse.addPointer(location: center);
      await tester.pump();
      expect(tester.widget<AnimatedOpacity>(scrim).opacity, 1);
      Color scrimColor() =>
          (tester
                      .widget<AnimatedContainer>(
                        find.descendant(
                          of: scrim,
                          matching: find.byType(AnimatedContainer),
                        ),
                      )
                      .decoration!
                  as BoxDecoration)
              .color!;
      final resting = scrimColor();
      await mouse.down(center);
      await tester.pump();
      expect(scrimColor().a, greaterThan(resting.a));
      await mouse.up();
      await tester.pump(const Duration(milliseconds: 400));
      await mouse.removePointer();
    });

    testWidgets('phones show no hover', (tester) async {
      debugDesktopPlatformOverride = false;
      await tester.pumpWidget(tile());
      expect(find.byKey(const ValueKey('desktop-hover-layer')), findsNothing);
    });
  });
}
