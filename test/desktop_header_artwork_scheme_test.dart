import 'dart:io';
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/theme/cover_palette.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

const _details = Key('details');
const _toolbar = Key('toolbar');
const _row = Key('row');

ColorScheme _schemeAt(WidgetTester tester, Finder finder) =>
    Theme.of(tester.element(finder)).colorScheme;

/// A bare desktop album header over one row of the page.
Widget _header(ThemeData theme, String cover) => MaterialApp(
  // A fresh app per style, so the theme does not tween in.
  key: ObjectKey(theme),
  theme: theme,
  home: Scaffold(
    body: Builder(
      builder: (context) => CustomScrollView(
        slivers: [
          ...AlbumDetailHeader(
            title: 'Album',
            expandedHeight: 300,
            showTitleInAppBar: false,
            background: const SizedBox(),
            paletteSource: cover,
            coverBuilder: (_, size) => SizedBox.square(dimension: size),
            meta: const SizedBox(key: _details, height: 20),
            leading: const SizedBox(key: _toolbar, width: 20),
          ).buildSlivers(context),
          const SliverToBoxAdapter(child: SizedBox(key: _row, height: 44)),
        ],
      ),
    ),
  ),
);

/// Writes a solid [color] PNG and resolves its scheme through the real
/// [CoverPalette], so the header has a cached cover it could have used.
Future<String> _resolvedCover(WidgetTester tester, Color color) async {
  final directory = await tester.runAsync(
    () => Directory.systemTemp.createTemp('header-cover-'),
  );
  addTearDown(() async {
    PaintingBinding.instance.imageCache.clear();
    PaintingBinding.instance.imageCache.clearLiveImages();
    await tester.runAsync(() => directory!.delete(recursive: true));
  });
  final path = '${directory!.path}/cover.png';
  await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    Canvas(
      recorder,
    ).drawRect(const Rect.fromLTWH(0, 0, 64, 64), Paint()..color = color);
    final picture = recorder.endRecording();
    final image = await picture.toImage(64, 64);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    picture.dispose();
    await File(path).writeAsBytes(bytes!.buffer.asUint8List());
    // Desktop hosts quantize through the native backend; the test host
    // uses the Dart quantizer that phones fall back to.
    debugDesktopPlatformOverride = null;
    try {
      await CoverPalette.resolve(path, Brightness.light);
    } finally {
      debugDesktopPlatformOverride = true;
    }
  });
  return path;
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = true;
  });

  tearDown(() {
    debugDesktopPlatformOverride = null;
  });

  testWidgets('Mornye desktop headers keep the app scheme', (tester) async {
    final theme = MornyeTheme.build(Brightness.light);
    final cover = await _resolvedCover(tester, Colors.red);
    expect(CoverPalette.peek(cover, Brightness.light), isNotNull);
    await tester.pumpWidget(_header(theme, cover));
    await tester.pump(const Duration(milliseconds: 100));
    expect(_schemeAt(tester, find.byKey(_details)), theme.colorScheme);
    expect(
      HeaderPalette.of(tester.element(find.byKey(_details))),
      theme.colorScheme,
    );
  });
}
