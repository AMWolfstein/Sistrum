import 'dart:io';
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';
import 'package:sistrum/widgets/mornye_glass_preparation.dart';

import 'performance_probe.dart';

void main() {
  final binding = PerformanceTestBinding.ensureInitialized();
  for (final brightness in Brightness.values) {
    testWidgets('anchored squircle glass ($brightness)', (tester) async {
      final probe = PerformanceProbe(
        binding,
        tester,
        suite: 'menu_650_${brightness.name}',
      );
      final capture = GlobalKey();
      final button = GlobalKey();
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
            mornyeBlurEnabledProvider.overrideWithValue(true),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(brightness),
            builder: (_, child) => RepaintBoundary(
              key: capture,
              child: MornyeGlassPreparation(child: child!),
            ),
            home: Scaffold(
              body: Stack(
                fit: StackFit.expand,
                children: [
                  const DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [
                          Color(0xff524584),
                          Color(0xffaa6858),
                          Color(0xff355e65),
                        ],
                      ),
                    ),
                  ),
                  const Center(
                    child: Text(
                      'Your music\nYour library',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 48,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Positioned(
                    top: 120,
                    right: 24,
                    child: Builder(
                      builder: (context) => IconButton(
                        key: button,
                        icon: const Icon(Icons.more_horiz),
                        onPressed: () => showMornyeContextMenu<void>(
                          context: context,
                          anchor: mornyeMenuAnchor(button.currentContext!),
                          builder: (menuContext) => MornyeContextMenu(
                            quickActions: [
                              MornyeMenuAction(
                                icon: Icons.favorite_border,
                                label: 'Favorite',
                                onPressed: () {},
                              ),
                              MornyeMenuAction(
                                icon: Icons.share_outlined,
                                label: 'Share',
                                onPressed: () {},
                              ),
                            ],
                            groups: [
                              [
                                MornyeMenuAction(
                                  icon: Icons.queue_music,
                                  label: 'Add to playlist',
                                  onPressed: () {},
                                ),
                                MornyeMenuAction(
                                  icon: Icons.album_outlined,
                                  label: 'Go to album',
                                  subtitle: 'Example album',
                                  onPressed: () {},
                                ),
                                MornyeMenuAction(
                                  icon: Icons.person_outline,
                                  label: 'Go to artist',
                                  onPressed: () {},
                                ),
                              ],
                              [
                                MornyeMenuAction(
                                  icon: Icons.close,
                                  label: 'Close',
                                  onPressed: () => Navigator.pop(menuContext),
                                ),
                              ],
                            ],
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
      await probe.wait(const Duration(milliseconds: 500));

      Future<void> captureMenu(String phase) async {
        await tester.runAsync(() async {
          final boundary = tester.renderObject<RenderRepaintBoundary>(
            find.byKey(capture),
          );
          final image = await boundary.toImage(
            pixelRatio: tester.view.devicePixelRatio,
          );
          try {
            final png = (await image.toByteData(
              format: ui.ImageByteFormat.png,
            ))!;
            final file = File(
              '${(await getTemporaryDirectory()).path}/menu-$phase-${brightness.name}.png',
            );
            await file.writeAsBytes(
              png.buffer.asUint8List(png.offsetInBytes, png.lengthInBytes),
            );
            debugPrint('MENU_PRESENTATION: ${file.path}');
          } finally {
            image.dispose();
          }
        });
      }

      Future<void> open({bool captureOpening = false}) async {
        await tester.tap(find.byKey(button));
        if (captureOpening) {
          await probe.wait(const Duration(milliseconds: 64));
          final menu = tester.renderObject<RenderBox>(
            find.byType(MornyeContextMenu),
          );
          final width =
              (menu.localToGlobal(Offset(menu.size.width, 0)) -
                      menu.localToGlobal(Offset.zero))
                  .dx;
          expect(width, lessThan(menu.size.width));
          expect(
            tester
                .widget<MornyeGlassPanel>(find.byType(MornyeGlassPanel))
                .glassProgress,
            1,
          );
          await captureMenu('opening');
        }
        await probe.wait(const Duration(milliseconds: 450));
        expect(find.byType(MornyeContextMenu), findsOneWidget);
      }

      Future<void> close() async {
        await tester.tap(find.text('Close'));
        await probe.wait(const Duration(milliseconds: 450));
        expect(find.byType(MornyeContextMenu), findsNothing);
      }

      await probe.measure('open_close', () async {
        await open();
        await close();
      });
      await open(captureOpening: true);
      await captureMenu('settled');
      await close();
      await probe.finish();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
