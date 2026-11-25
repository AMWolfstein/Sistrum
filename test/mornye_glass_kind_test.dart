import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Backdrop samplers under [root]: a `BackdropFilter` or a lens. A surface
/// without a lens reads nothing.
int _samplers(WidgetTester tester, Finder root) {
  var count = find
      .descendant(of: root, matching: find.byType(BackdropFilter))
      .evaluate()
      .length;
  count += find
      .descendant(of: root, matching: find.byType(LiquidGlassLens))
      .evaluate()
      .length;
  return count;
}

/// A page with one header control, one chrome bar and one content panel. The
/// control and the bar share one batch, as a real page does.
Widget _page(MornyeGlassLevel level, {bool controlSamples = true}) {
  return ProviderScope(
    overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      home: Scaffold(
        body: Column(
          children: [
            LiquidGlassBatch(
              child: Column(
                children: [
                  Align(
                    alignment: Alignment.centerRight,
                    child: MornyeGlass.navigation(
                      blurEnabled: true,
                      kind: MornyeGlassKind.control,
                      radius: 20,
                      samplesBackdrop: controlSamples,
                      child: const SizedBox(width: 40, height: 40),
                    ),
                  ),
                  MornyeGlass.navigation(
                    blurEnabled: true,
                    radius: 28,
                    child: const SizedBox(width: 300, height: 56),
                  ),
                ],
              ),
            ),
            const MornyeGlassPanel(
              key: ValueKey('content'),
              kind: MornyeGlassKind.content,
              child: SizedBox(width: 300, height: 120),
            ),
          ],
        ),
      ),
    ),
  );
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  group('preset table', () {
    test('Liquid keeps sigma 2 and has no colour split', () {
      expect(MornyeGlassPreset.liquid.blurSigma, 2);
      expect(MornyeGlassPreset.liquid.dispersion, 0);
    });

    test('Liquid batches its blur in the shader', () {
      expect(
        MornyeGlassPreset.liquid.batchBlurMethod,
        LiquidGlassBlurMethod.shader,
      );
    });

    test('None has no blur and no sampling', () {
      expect(MornyeGlassPreset.none.blurSigma, 0);
      expect(MornyeGlassPreset.none.controlsSample, isFalse);
    });

    test(
      'content never samples; chrome and controls sample only in Liquid',
      () {
        for (final preset in [
          MornyeGlassPreset.liquid,
          MornyeGlassPreset.none,
        ]) {
          expect(preset.samplesFor(MornyeGlassKind.content), isFalse);
        }
        expect(
          MornyeGlassPreset.liquid.samplesFor(MornyeGlassKind.chrome),
          isTrue,
        );
        expect(
          MornyeGlassPreset.liquid.samplesFor(MornyeGlassKind.control),
          isTrue,
        );
        expect(
          MornyeGlassPreset.none.samplesFor(MornyeGlassKind.control),
          isFalse,
        );
      },
    );
  });

  testWidgets('None draws no backdrop sampler for any surface kind', (
    tester,
  ) async {
    await tester.pumpWidget(_page(MornyeGlassLevel.flat));
    expect(_samplers(tester, find.byType(Scaffold)), 0);
    expect(find.byType(LiquidGlassLens), findsNothing);
    expect(find.byType(LiquidGlassLens), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Liquid: one shared batch; the page adds no batch of its own', (
    tester,
  ) async {
    await tester.pumpWidget(_page(MornyeGlassLevel.liquid));
    // Only the page's batch exists: the control and the bar join it.
    expect(find.byType(LiquidGlassBatch), findsOneWidget);
    final lenses = find.byType(LiquidGlassLens);
    expect(lenses, findsNWidgets(2));
    final key = LiquidGlassBatch.backdropIdOf(tester.element(lenses.first));
    expect(key, isNotNull);
    for (final lens in lenses.evaluate()) {
      expect(LiquidGlassBatch.backdropIdOf(lens), key);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'a chrome bar with no page batch gets its own shader-blur batch',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            home: const Scaffold(
              body: MornyeGlass.navigation(
                blurEnabled: true,
                radius: 28,
                child: SizedBox(width: 300, height: 56),
              ),
            ),
          ),
        ),
      );
      final batch = tester.widget<LiquidGlassBatch>(
        find.byType(LiquidGlassBatch),
      );
      expect(batch.blurMethod, LiquidGlassBlurMethod.shader);
      expect(tester.takeException(), isNull);
    },
  );

  for (final level in MornyeGlassLevel.values) {
    testWidgets('content panels build no sampler in ${level.name}', (
      tester,
    ) async {
      await tester.pumpWidget(_page(level));
      final content = find.byKey(const ValueKey('content'));
      expect(content, findsOneWidget);
      expect(_samplers(tester, content), 0);
      expect(
        find.descendant(of: content, matching: find.byType(LiquidGlassLens)),
        findsNothing,
      );
      expect(
        find.descendant(of: content, matching: find.byType(LiquidGlassBatch)),
        findsNothing,
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a control that does not sample draws no lens in Liquid', (
    tester,
  ) async {
    await tester.pumpWidget(
      _page(MornyeGlassLevel.liquid, controlSamples: false),
    );
    // Only the chrome bar reads the backdrop; the control is a plain fill.
    expect(find.byType(LiquidGlassLens), findsOneWidget);
    expect(_samplers(tester, find.byType(Scaffold)), 1);
    expect(tester.takeException(), isNull);
  });

  group('album and collection page (CollectionScaffold)', () {
    setUp(() => debugDesktopPlatformOverride = false);
    tearDown(() => debugDesktopPlatformOverride = null);

    Widget collectionPage(MornyeGlassLevel level) {
      return ProviderScope(
        overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          home: CollectionScaffold(
            scrollController: ScrollController(),
            isSelectionMode: false,
            onExitSelectionMode: () {},
            // Two header circles, as the album header's back and action buttons.
            appBar: SliverToBoxAdapter(
              child: Row(
                children: [
                  for (var i = 0; i < 2; i++)
                    MornyeGlass.navigation(
                      blurEnabled: true,
                      kind: MornyeGlassKind.control,
                      radius: 20,
                      child: const SizedBox(width: 40, height: 40),
                    ),
                ],
              ),
            ),
            slivers: const [
              SliverToBoxAdapter(
                child: MornyeGlassPanel(
                  key: ValueKey('content'),
                  kind: MornyeGlassKind.content,
                  child: SizedBox(height: 120),
                ),
              ),
            ],
          ),
        ),
      );
    }

    testWidgets('None: no sampler and the page batch is off', (tester) async {
      await tester.pumpWidget(collectionPage(MornyeGlassLevel.flat));
      expect(_samplers(tester, find.byType(Scaffold)), 0);
      expect(find.byType(LiquidGlassLens), findsNothing);
      final batch = tester.widget<LiquidGlassBatch>(
        find.byType(LiquidGlassBatch),
      );
      expect(batch.enabled, isFalse);
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'Liquid: one page batch in shader blur shared by both buttons',
      (tester) async {
        await tester.pumpWidget(collectionPage(MornyeGlassLevel.liquid));
        final batches = find.byType(LiquidGlassBatch);
        expect(batches, findsOneWidget);
        final batch = tester.widget<LiquidGlassBatch>(batches);
        expect(batch.enabled, isTrue);
        expect(batch.blurMethod, LiquidGlassBlurMethod.shader);
        final lenses = find.byType(LiquidGlassLens);
        expect(lenses, findsNWidgets(2));
        final key = LiquidGlassBatch.backdropIdOf(tester.element(lenses.first));
        expect(key, isNotNull);
        for (final lens in lenses.evaluate()) {
          expect(LiquidGlassBatch.backdropIdOf(lens), key);
        }
        expect(_samplers(tester, find.byType(Scaffold)), 2);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('content panel never samples on the collection page', (
      tester,
    ) async {
      for (final level in MornyeGlassLevel.values) {
        await tester.pumpWidget(collectionPage(level));
        expect(
          _samplers(tester, find.byKey(const ValueKey('content'))),
          0,
          reason: level.name,
        );
      }
    });
  });
}
