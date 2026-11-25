import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/blurred_cover.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// A 1x1 PNG: a real image for the blurred copy to decode.
final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
);

/// The blurred layer's opacity, or null when no blurred copy is built.
double? _blurOpacity(WidgetTester tester) {
  final finder = find.byType(BlurredCoverLayer);
  if (finder.evaluate().isEmpty) return null;
  return tester.widget<BlurredCoverLayer>(finder).opacity;
}

Widget _artistPage(ScrollController controller, MornyeGlassLevel level) {
  return ProviderScope(
    overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.dark),
      home: Scaffold(
        body: CustomScrollView(
          controller: controller,
          slivers: [
            MornyeArtistHeader(
              name: 'Artist',
              artwork: const ColoredBox(color: Colors.orange),
              blurSource: MemoryImage(_png),
              actions: const [Text('Artist action')],
              showTitle: false,
            ),
            const SliverToBoxAdapter(child: SizedBox(height: 2000)),
          ],
        ),
      ),
    ),
  );
}

Widget _collectionPage(ScrollController controller, MornyeGlassLevel level) {
  return ProviderScope(
    overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      home: CollectionScaffold(
        scrollController: controller,
        isSelectionMode: false,
        onExitSelectionMode: () {},
        appBar: AlbumDetailHeader(
          title: 'Album',
          immersive: true,
          expandedHeight: 480,
          showTitleInAppBar: false,
          background: const ColoredBox(color: Colors.orange),
          blurSource: MemoryImage(_png),
          actions: const Text('Album action'),
        ),
        slivers: const [SliverToBoxAdapter(child: SizedBox(height: 2000))],
      ),
    ),
  );
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  tester.view.padding = FakeViewPadding(top: 59, bottom: 34);
  addTearDown(tester.view.reset);
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

  testWidgets('artist header: the blurred copy cross-fades in with collapse', (
    tester,
  ) async {
    _phone(tester);
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_artistPage(controller, MornyeGlassLevel.liquid));
    await tester.pumpAndSettle();

    expect(_blurOpacity(tester), 0, reason: 'sharp at rest');
    controller.jumpTo(60);
    await tester.pumpAndSettle();
    final early = _blurOpacity(tester)!;
    controller.jumpTo(120);
    await tester.pumpAndSettle();
    final later = _blurOpacity(tester)!;
    expect(early, greaterThan(0));
    expect(later, greaterThan(early), reason: 'progressive with collapse');
    expect(later, lessThanOrEqualTo(1));
    // No filter runs per frame: the blur is a cached image, not an ImageFiltered.
    expect(
      find.descendant(
        of: find.byType(MornyeArtistHeader),
        matching: find.byType(ImageFiltered),
      ),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('album and collection header: the blurred copy cross-fades in', (
    tester,
  ) async {
    _phone(tester);
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _collectionPage(controller, MornyeGlassLevel.liquid),
    );
    await tester.pumpAndSettle();

    expect(_blurOpacity(tester), isNull, reason: 'sharp at rest');
    controller.jumpTo(100);
    await tester.pumpAndSettle();
    final early = _blurOpacity(tester)!;
    controller.jumpTo(250);
    await tester.pumpAndSettle();
    final later = _blurOpacity(tester)!;
    expect(later, greaterThan(early));
    expect(later, lessThanOrEqualTo(1));
    expect(
      find.descendant(
        of: find.byType(AlbumDetailHeader),
        matching: find.byType(ImageFiltered),
      ),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  for (final level in [MornyeGlassLevel.flat]) {
    testWidgets('${level.name}: no blurred copy is built on either header', (
      tester,
    ) async {
      _phone(tester);
      final controller = ScrollController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_artistPage(controller, level));
      await tester.pumpAndSettle();
      controller.jumpTo(160);
      await tester.pumpAndSettle();
      expect(find.byType(BlurredCoverLayer), findsNothing);

      await tester.pumpWidget(_collectionPage(controller, level));
      await tester.pumpAndSettle();
      controller.jumpTo(250);
      await tester.pumpAndSettle();
      expect(find.byType(BlurredCoverLayer), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }
}
