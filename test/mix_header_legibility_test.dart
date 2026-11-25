import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// A solid 8x8 PNG of [color]. The worst case for the title is the cover
/// least like its colour: a bright cover for white text, a dark one for black.
Future<Uint8List> _solidPng(WidgetTester tester, ui.Color color) async =>
    (await tester.runAsync(() async {
      final recorder = ui.PictureRecorder();
      ui.Canvas(
        recorder,
      ).drawRect(ui.Rect.fromLTWH(0, 0, 8, 8), ui.Paint()..color = color);
      final image = await recorder.endRecording().toImage(8, 8);
      final data = await image.toByteData(format: ui.ImageByteFormat.png);
      return data!.buffer.asUint8List();
    }))!;

double _luminance(Color c) {
  double channel(double v) =>
      v <= 0.03928 ? v / 12.92 : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
  return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b);
}

double _contrast(Color a, Color b) {
  final la = _luminance(a) + 0.05;
  final lb = _luminance(b) + 0.05;
  return la > lb ? la / lb : lb / la;
}

void main() {
  setUp(() {
    MornyeGlass.debugShaderFilterSupport = true;
    debugDesktopPlatformOverride = false;
  });
  tearDown(() {
    MornyeGlass.debugShaderFilterSupport = null;
    debugDesktopPlatformOverride = null;
  });

  // Offset 120: the large details title is on the cover. Offset 400: the
  // header has collapsed and the 17 px toolbar title is on the toolbar.
  for (final brightness in [Brightness.light, Brightness.dark]) {
    for (final level in [MornyeGlassLevel.liquid, MornyeGlassLevel.flat]) {
      for (final offset in [120.0, 400.0]) {
        testWidgets('the mix title stays legible over its worst-case cover '
            '(${brightness.name}, ${level.name}, offset $offset)', (
          tester,
        ) async {
          tester.view.physicalSize = const Size(390, 844);
          tester.view.devicePixelRatio = 1;
          tester.view.padding = FakeViewPadding(top: 59, bottom: 34);
          addTearDown(tester.view.reset);
          final controller = ScrollController();
          addTearDown(controller.dispose);
          final pageKey = GlobalKey();
          // Light theme: black title text, so a dark cover is the worst case.
          // Dark theme: white title text, so a bright cover is the worst case.
          final png = await _solidPng(
            tester,
            brightness == Brightness.light
                ? const ui.Color(0xFF000000)
                : const ui.Color(0xFFFFFFFF),
          );
          await tester.pumpWidget(
            ProviderScope(
              overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
              child: MaterialApp(
                theme: MornyeTheme.build(brightness),
                home: RepaintBoundary(
                  key: pageKey,
                  child: CollectionScaffold(
                    scrollController: controller,
                    isSelectionMode: false,
                    onExitSelectionMode: () {},
                    appBar: AlbumDetailHeader(
                      title: 'Mix Title',
                      immersive: true,
                      expandedHeight: 480,
                      showTitleInAppBar: false,
                      background: Image.memory(png, fit: BoxFit.cover),
                      blurSource: MemoryImage(png),
                      // The mix, genre and playlist headers set this scrim.
                      titleScrim: true,
                      actions: const Text('Mix action'),
                    ),
                    slivers: const [
                      SliverToBoxAdapter(child: SizedBox(height: 2000)),
                    ],
                  ),
                ),
              ),
            ),
          );
          // Let the cover decode and the blurred copy be drawn.
          for (var i = 0; i < 40; i++) {
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 30)),
            );
            await tester.pump(const Duration(milliseconds: 50));
          }
          await tester.pumpAndSettle();
          controller.jumpTo(offset);
          await tester.pumpAndSettle();

          final title = find.text('Mix Title');
          expect(title, findsWidgets);
          final texts = title.evaluate().map((e) => e.widget as Text);
          final shown = texts.firstWhere(
            (t) => offset < 200
                ? (t.style?.fontSize ?? 0) > 17
                : (t.style?.fontSize ?? 0) < 20,
          );
          // The colour the title is drawn in: its own, or the one it inherits
          // from the app bar or the header at the title's element.
          final color = DefaultTextStyle.of(
            tester.element(find.byWidget(shown)),
          ).style.merge(shown.style).color!;
          final rect = tester.getRect(
            find.byWidgetPredicate((w) => identical(w, shown)),
          );

          final boundary = tester.renderObject<RenderRepaintBoundary>(
            find.byKey(pageKey),
          );
          final image = (await tester.runAsync(() => boundary.toImage()))!;
          final data = (await tester.runAsync(
            () => image.toByteData(format: ui.ImageByteFormat.rawRgba),
          ))!;
          // The background beside the title, inside the title scrim's padding.
          final x = (rect.left - 4).round();
          final y = rect.center.dy.round();
          final i = (y * image.width + x) * 4;
          final background = Color.fromARGB(
            255,
            data.getUint8(i),
            data.getUint8(i + 1),
            data.getUint8(i + 2),
          );
          // Light text needs 3:1 (WCAG large text). Dark text is held to 7:1:
          // the light scrim must leave it near full contrast. A dark scrim behind
          // it (a grey box) drops it to about 5:1.
          final needed = color.computeLuminance() > 0.5 ? 3.0 : 7.0;
          expect(
            _contrast(color, background),
            greaterThanOrEqualTo(needed),
            reason: 'title $color over $background',
          );
          expect(tester.takeException(), isNull);
        });
      }
    }
  }
}
