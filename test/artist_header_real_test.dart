import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart' as current;
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

import 'support/reference/artist_header_base.dart' as base;

/// A tall (120 x 160) PNG with red top-left, green top-right, blue bottom-left
/// and yellow bottom-right, decoded by the framework like a real cover.
Future<Uint8List> _tallPng(WidgetTester tester) async {
  return (await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    final paint = ui.Paint();
    void quad(double x, double y, ui.Color c) {
      paint.color = c;
      canvas.drawRect(ui.Rect.fromLTWH(x, y, 60, 80), paint);
    }

    quad(0, 0, const ui.Color(0xFFFF0000));
    quad(60, 0, const ui.Color(0xFF00FF00));
    quad(0, 80, const ui.Color(0xFF0000FF));
    quad(60, 80, const ui.Color(0xFFFFFF00));
    final image = await recorder.endRecording().toImage(120, 160);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    return data!.buffer.asUint8List();
  }))!;
}

enum _Which { current, base }

Future<List<int>> _pixel(
  WidgetTester tester,
  GlobalKey key,
  double x,
  double y,
) async {
  await tester.pumpAndSettle();
  final boundary = tester.renderObject<RenderRepaintBoundary>(find.byKey(key));
  final image = (await tester.runAsync(() => boundary.toImage()))!;
  final data = (await tester.runAsync(
    () => image.toByteData(format: ui.ImageByteFormat.rawRgba),
  ))!;
  final i = (y.round() * image.width + x.round()) * 4;
  return [
    data.getUint8(i),
    data.getUint8(i + 1),
    data.getUint8(i + 2),
    data.getUint8(i + 3),
  ];
}

Future<void> _mount(
  WidgetTester tester,
  ScrollController controller,
  GlobalKey pageKey,
  Uint8List png,
  MornyeGlassLevel level,
  _Which which,
) async {
  tester.view.physicalSize = const Size(390 * 3, 844 * 3);
  tester.view.devicePixelRatio = 3;
  tester.view.padding = FakeViewPadding(top: 59 * 3, bottom: 34 * 3);
  final artwork = Image.memory(png, fit: BoxFit.cover, gaplessPlayback: true);
  final header = which == _Which.current
      ? current.MornyeArtistHeader(
          name: 'Artist',
          artwork: artwork,
          blurSource: MemoryImage(png),
          actions: const [SizedBox(width: 10)],
          showTitle: false,
        )
      : base.MornyeArtistHeader(
          name: 'Artist',
          artwork: artwork,
          actions: const [SizedBox(width: 10)],
          showTitle: false,
        );
  await tester.pumpWidget(
    ProviderScope(
      overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        home: RepaintBoundary(
          key: pageKey,
          child: Scaffold(
            body: CustomScrollView(
              controller: controller,
              slivers: [
                header,
                const SliverToBoxAdapter(child: SizedBox(height: 2000)),
              ],
            ),
          ),
        ),
      ),
    ),
  );
  // Decoding runs outside the fake clock: give the image real time.
  for (var i = 0; i < 40; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 30)),
    );
    await tester.pump(const Duration(milliseconds: 50));
  }
  await tester.pumpAndSettle();
}

/// Width of the red/green edge: pixels across x 175..215 that are neither pure
/// red nor pure green. A sharp edge gives about two; a softened one gives more.
Future<int> _edgeWidth(WidgetTester tester, GlobalKey key, double y) async {
  var width = 0;
  for (var x = 175.0; x <= 215; x += 1) {
    final p = await _pixel(tester, key, x, y);
    if (p[0] > 10 && p[0] < 215) width++;
  }
  return width;
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);
  albumMain();

  for (final level in MornyeGlassLevel.values) {
    testWidgets('artist header ${level.name}: framing matches the base, '
        'and Liquid softens mid-collapse', (tester) async {
      final png = await _tallPng(tester);
      final controller = ScrollController();
      addTearDown(controller.dispose);
      final pageKey = GlobalKey();
      final xs = [40.0, 97.0, 150.0, 240.0, 300.0, 350.0];

      // The sharp edge width, measured on the current widget at rest.
      await _mount(tester, controller, pageKey, png, level, _Which.current);
      final sharpEdge = await _edgeWidth(tester, pageKey, 40);

      for (final offset in [60.0, 120.0]) {
        // Current widget.
        await _mount(tester, controller, pageKey, png, level, _Which.current);
        controller.jumpTo(offset);
        await tester.pumpAndSettle();
        final now = <List<int>>[];
        for (final x in xs) {
          now.add(await _pixel(tester, pageKey, x, 4));
        }
        final edgeNow = await _edgeWidth(tester, pageKey, 40);

        // Base widget, same offset.
        await _mount(tester, controller, pageKey, png, level, _Which.base);
        controller.jumpTo(offset);
        await tester.pumpAndSettle();
        final before = <List<int>>[];
        for (final x in xs) {
          before.add(await _pixel(tester, pageKey, x, 4));
        }
        final edgeBase = await _edgeWidth(tester, pageKey, 40);

        for (var i = 0; i < xs.length; i++) {
          // The top row stays anchored at the top edge: the same colour as the
          // base. Near the edge (x 170..220) a blur is expected, so skip it.
          if (level == MornyeGlassLevel.liquid && xs[i] > 170 && xs[i] < 220) {
            continue;
          }
          for (var c = 0; c < 3; c++) {
            expect(
              (now[i][c] - before[i][c]).abs(),
              lessThanOrEqualTo(12),
              reason:
                  '${level.name} @$offset x=${xs[i]} channel $c: '
                  'now ${now[i]} base ${before[i]}',
            );
          }
        }
        if (level == MornyeGlassLevel.liquid) {
          // Visibly softer than the sharp edge, and close to the base's softening.
          expect(
            edgeNow,
            greaterThanOrEqualTo(sharpEdge + 6),
            reason: 'Liquid @$offset softens: now $edgeNow, sharp $sharpEdge',
          );
          // The cross-fade blends a full-sigma copy, so mid-collapse reads wider
          // than the base's growing sigma. It must not be wider than 3x that.
          expect(
            edgeNow,
            lessThanOrEqualTo(edgeBase * 3),
            reason: 'Liquid @$offset edge: now $edgeNow, base $edgeBase',
          );
        } else {
          expect(
            edgeNow,
            lessThanOrEqualTo(sharpEdge + 1),
            reason: '${level.name} @$offset has no blur: now $edgeNow',
          );
        }
      }
      expect(tester.takeException(), isNull);
    });
  }
}

/// The immersive album and collection header (every collection page), real
/// PNG artwork, in each mode: anchored at the top, and softened in Liquid.
void albumMain() {
  for (final level in MornyeGlassLevel.values) {
    testWidgets('album header ${level.name}: anchored, and Liquid softens', (
      tester,
    ) async {
      final png = await _tallPng(tester);
      tester.view.physicalSize = const Size(390 * 3, 844 * 3);
      tester.view.devicePixelRatio = 3;
      tester.view.padding = FakeViewPadding(top: 59 * 3, bottom: 34 * 3);
      final controller = ScrollController();
      addTearDown(controller.dispose);
      final pageKey = GlobalKey();
      Future<void> mount() async {
        await tester.pumpWidget(
          ProviderScope(
            overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.light),
              home: RepaintBoundary(
                key: pageKey,
                child: CollectionScaffold(
                  scrollController: controller,
                  isSelectionMode: false,
                  onExitSelectionMode: () {},
                  appBar: AlbumDetailHeader(
                    title: 'Album',
                    immersive: true,
                    expandedHeight: 480,
                    showTitleInAppBar: false,
                    background: Image.memory(
                      png,
                      fit: BoxFit.cover,
                      gaplessPlayback: true,
                    ),
                    blurSource: MemoryImage(png),
                    actions: const Text('Album action'),
                  ),
                  slivers: const [
                    SliverToBoxAdapter(child: SizedBox(height: 2000)),
                  ],
                ),
              ),
            ),
          ),
        );
        for (var i = 0; i < 40; i++) {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 30)),
          );
          await tester.pump(const Duration(milliseconds: 50));
        }
        await tester.pumpAndSettle();
      }

      await mount();
      final sharpEdge = await _edgeWidth(tester, pageKey, 40);
      for (final offset in [60.0, 120.0]) {
        controller.jumpTo(offset);
        await tester.pumpAndSettle();
        // The album artwork scrolls with the page, so the row at the top edge
        // is a lower row of the cover: check the quadrant placement, not the
        // exact colour. Left is red and right is green wherever the cover is.
        for (final x in [40.0, 97.0]) {
          final p = await _pixel(tester, pageKey, x, 4);
          expect(
            p[0],
            greaterThan(p[1] + 60),
            reason: '${level.name} @$offset x=$x red $p',
          );
        }
        for (final x in [300.0, 350.0]) {
          final p = await _pixel(tester, pageKey, x, 4);
          expect(
            p[1],
            greaterThan(p[0] + 60),
            reason: '${level.name} @$offset x=$x green $p',
          );
        }
        final edge = await _edgeWidth(tester, pageKey, 40);
        if (level == MornyeGlassLevel.liquid) {
          expect(
            edge,
            greaterThanOrEqualTo(sharpEdge + 6),
            reason: 'album Liquid @$offset softens: $edge vs sharp $sharpEdge',
          );
        } else {
          expect(edge, lessThanOrEqualTo(sharpEdge + 1));
        }
      }
      expect(tester.takeException(), isNull);
    });
  }
}
