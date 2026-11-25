import 'dart:io';
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:integration_test/integration_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_liquid_backdrop.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('glass segmented thumb is a native lens that lifts on press', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.liquid),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Center(
            child: SizedBox(
              width: 360,
              child: MornyeSegmentedControl(
                labels: const ['Home', 'Library', 'Repo', 'Search'],
                selectedIndex: 0,
                onChanged: (_) {},
              ),
            ),
          ),
        ),
      ),
    );
    await tester.runAsync(LiquidGlassShaders.ensureLoaded);
    await tester.pumpAndSettle();
    final thumb = find.byType(LiquidGlassMotionPill);
    expect(thumb, findsOneWidget);
    final gesture = await tester.startGesture(
      tester.getCenter(find.text('Library')),
    );
    await tester.pump();
    expect(tester.widget<LiquidGlassMotionPill>(thumb).active, isTrue);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(tester.widget<LiquidGlassMotionPill>(thumb).active, isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Impeller lens refracts its own bounds and preserves foreground',
    (tester) async {
      expect(ui.ImageFilter.isShaderFilterSupported, isTrue);
      final key = GlobalKey();
      final movement = ValueNotifier<double>(0);
      addTearDown(movement.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: RepaintBoundary(
            key: key,
            child: LiquidGlassBatch(
              child: Stack(
                children: [
                  const Positioned.fill(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [Colors.black, Colors.white],
                        ),
                      ),
                    ),
                  ),
                  for (final top in [160.0, 320.0])
                    Positioned(
                      left: 40,
                      top: top,
                      width: 300,
                      height: 80,
                      child: ValueListenableBuilder<double>(
                        valueListenable: movement,
                        builder: (context, value, child) => Transform.translate(
                          offset: Offset(value, 0),
                          child: Transform.scale(
                            scale: top == 320 ? 0.8 : 1,
                            child: child,
                          ),
                        ),
                        child: RepaintBoundary(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(40),
                            child: MornyeLiquidBackdrop(
                              borderRadius: BorderRadius.circular(40),
                              dispersion: 1.32,
                              blurSigma: 2,
                              child: const Center(
                                child: SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: ColoredBox(color: Color(0xff00ff00)),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
      // Asset compilation completes outside the fake widget-test clock.
      await tester.runAsync(() => LiquidGlassShaders.ensureLoaded());
      await tester.pumpAndSettle();
      expect(tester.binding.hasScheduledFrame, isFalse);
      final boundary =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final pixelRatio = tester.view.devicePixelRatio;
      final image = await boundary.toImage(pixelRatio: pixelRatio);
      final bytes = (await image.toByteData())!;
      int channel(int x, int y, int c) => bytes.getUint8(
        ((y * pixelRatio).round() * image.width + (x * pixelRatio).round()) *
                4 +
            c,
      );
      final png = (await image.toByteData(format: ui.ImageByteFormat.png))!;
      final directory = await getTemporaryDirectory();
      await File('${directory.path}/mornye-lens-check.png').writeAsBytes(
        png.buffer.asUint8List(png.offsetInBytes, png.lengthInBytes),
      );
      debugPrint('Glass diagnostic: ${directory.path}/mornye-lens-check.png');
      for (final y in [200, 360]) {
        // An inward lens shifts the left edge into the brighter background.
        final edgeX = y == 200 ? 42 : 72;
        expect(channel(edgeX, y, 0), greaterThan(channel(edgeX, 100, 0) + 4));
        // The middle of the backdrop remains at its original screen position.
        expect(channel(150, y, 0), closeTo(channel(150, 100, 0), 2));
        if (y == 200) {
          expect(channel(190, y, 0), 0);
          expect(channel(190, y, 1), 255);
          expect(channel(190, y, 2), 0);
        }
      }
      expect(channel(20, 200, 0), channel(20, 100, 0));
      image.dispose();
      movement.value = 20;
      await tester.pumpAndSettle();
      final moved = await boundary.toImage(pixelRatio: pixelRatio);
      final movedBytes = (await moved.toByteData())!;
      int red(int x, int y) => movedBytes.getUint8(
        ((y * pixelRatio).round() * moved.width + (x * pixelRatio).round()) * 4,
      );
      expect(red(62, 200), greaterThan(red(62, 100) + 4));
      expect(red(92, 360), greaterThan(red(92, 100) + 4));
      moved.dispose();
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'tab bar thumb follows taps and a cancelled press selects nothing',
    (tester) async {
      var selected = 0;
      final changes = <int>[];
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.liquid),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            home: Scaffold(
              body: Center(
                child: SizedBox(
                  width: 360,
                  child: StatefulBuilder(
                    builder: (context, setState) => MornyeTabBar(
                      destinations: const [
                        NavigationDestination(
                          icon: Icon(Icons.home),
                          label: 'Home',
                        ),
                        NavigationDestination(
                          icon: Icon(Icons.music_note),
                          label: 'Library',
                        ),
                        NavigationDestination(
                          icon: Icon(Icons.search),
                          label: 'Search',
                        ),
                      ],
                      selectedIndex: selected,
                      onSelected: (next) {
                        changes.add(next);
                        setState(() => selected = next);
                      },
                      blurEnabled: true,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.runAsync(LiquidGlassShaders.ensureLoaded);
      await tester.pumpAndSettle();
      final pill = find.byType(LiquidGlassMotionPill);
      await tester.tap(find.text('Library'));
      await tester.pumpAndSettle();
      expect(changes, [1]);
      final pillBox = tester.getTopLeft(pill);
      expect(
        pillBox.dx + tester.widget<LiquidGlassMotionPill>(pill).center.dx,
        closeTo(tester.getCenter(find.text('Library')).dx, 2),
      );
      final cancel = await tester.startGesture(
        tester.getCenter(find.text('Search')),
      );
      await cancel.moveTo(tester.getCenter(find.text('Home')));
      await tester.pumpAndSettle();
      await cancel.cancel();
      await tester.pumpAndSettle();
      expect(changes, [1]);
      expect(tester.takeException(), isNull);
    },
  );
}
