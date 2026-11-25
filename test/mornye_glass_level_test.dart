import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Keeps the selected mode in memory, without touching stored preferences.
class _Theme extends ThemeNotifier {
  _Theme() : super(preferences: Completer<SharedPreferences>().future);

  @override
  ThemeSettings build() => const ThemeSettings();

  @override
  Future<void> setMornyeGlassLevel(MornyeGlassLevel value) async {
    state = state.copyWith(mornyeGlassLevel: value);
  }
}

MornyeGlassLevel _levelFor({
  required MornyeGlassLevel mode,
  required bool lowEnd,
  required bool tierBlur,
  required bool liquidCapable,
}) {
  final container = ProviderContainer(
    overrides: [
      mornyeGlassModeProvider.overrideWithValue(mode),
      lowEndDeviceProvider.overrideWithValue(lowEnd),
      deviceSupportsBackdropBlurProvider.overrideWithValue(tierBlur),
      deviceSupportsLiquidGlassProvider.overrideWithValue(liquidCapable),
    ],
  );
  addTearDown(container.dispose);
  return container.read(mornyeGlassLevelProvider);
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);
  for (final brightness in Brightness.values) {
    for (final reduceMotion in [false, true]) {
      testWidgets(
        'high contrast turns glass off in every mode ($brightness, motion: $reduceMotion)',
        (tester) async {
          tester.platformDispatcher.accessibilityFeaturesTestValue =
              FakeAccessibilityFeatures(
                highContrast: true,
                disableAnimations: reduceMotion,
              );
          addTearDown(
            tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
          );
          await tester.pumpWidget(
            ProviderScope(
              overrides: [
                themeProvider.overrideWith(_Theme.new),
                // A capable GPU must not implicitly override contrast.
                deviceSupportsBackdropBlurProvider.overrideWithValue(true),
                // A capable device renders the selected Liquid glass.
                deviceSupportsLiquidGlassProvider.overrideWithValue(true),
              ],
              child: MaterialApp(
                theme: MornyeTheme.build(brightness),
                home: Scaffold(
                  body: Column(
                    children: [
                      const MornyeGlass.navigation(
                        blurEnabled: true,
                        child: SizedBox(width: 200, height: 50),
                      ),
                      MornyeSegmentedControl(
                        labels: const ['One', 'Two'],
                        selectedIndex: 0,
                        onChanged: (_) {},
                      ),
                      MornyeTabBar(
                        destinations: const [
                          NavigationDestination(
                            icon: Icon(Icons.home),
                            label: 'Home',
                          ),
                          NavigationDestination(
                            icon: Icon(Icons.music_note),
                            label: 'Library',
                          ),
                        ],
                        selectedIndex: 0,
                        onSelected: (_) {},
                        blurEnabled: true,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
          final context = tester.element(find.byType(MornyeTabBar));
          final container = ProviderScope.containerOf(context);
          // Each selected mode keeps the system request: no glass is drawn.
          for (final mode in [MornyeGlassLevel.liquid]) {
            await container
                .read(themeProvider.notifier)
                .setMornyeGlassLevel(mode);
            await tester.pumpAndSettle();
            expect(find.byType(LiquidGlassLens), findsNothing);
            expect(find.byType(LiquidGlassMotionPill), findsNothing);
            expect(MediaQuery.highContrastOf(context), isTrue);
            expect(MediaQuery.disableAnimationsOf(context), reduceMotion);
            expect(
              MornyeTabBar.usesLiquidGlass(
                context,
                blurEnabled: true,
                liquidGlass: container.read(mornyeLiquidGlassProvider),
                contrastBlocked: container.read(
                  mornyeGlassContrastBlockedProvider(true),
                ),
              ),
              isFalse,
            );
          }
          // A live system-contrast change restores the selected glass.
          tester.platformDispatcher.accessibilityFeaturesTestValue =
              FakeAccessibilityFeatures(disableAnimations: reduceMotion);
          await tester.pumpAndSettle();
          // The selected Liquid glass is back: its lenses are drawn again.
          expect(find.byType(LiquidGlassLens), findsWidgets);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  test('device fallback renders None, never changes the saved Liquid', () {
    // Low-end devices render flat whatever the mode.
    for (final mode in MornyeGlassLevel.values) {
      expect(
        _levelFor(
          mode: mode,
          lowEnd: true,
          tierBlur: true,
          liquidCapable: true,
        ),
        MornyeGlassLevel.flat,
      );
    }
    // Standard Android has no lens, so Liquid Glass renders as None.
    expect(
      _levelFor(
        mode: MornyeGlassLevel.liquid,
        lowEnd: false,
        tierBlur: false,
        liquidCapable: false,
      ),
      MornyeGlassLevel.flat,
    );
    // iOS and high-tier devices keep the selected full material.
    expect(
      _levelFor(
        mode: MornyeGlassLevel.liquid,
        lowEnd: false,
        tierBlur: false,
        liquidCapable: true,
      ),
      MornyeGlassLevel.liquid,
    );
    // None is never overridden by device capability.
    expect(
      _levelFor(
        mode: MornyeGlassLevel.flat,
        lowEnd: false,
        tierBlur: true,
        liquidCapable: true,
      ),
      MornyeGlassLevel.flat,
    );
  });

  testWidgets('each glass mode has one fixed blur, and None has none', (
    tester,
  ) async {
    Future<double?> blurFor(MornyeGlassLevel level) async {
      await tester.pumpWidget(
        ProviderScope(
          key: UniqueKey(),
          overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.light),
            home: const MornyeGlass.navigation(
              blurEnabled: true,
              child: SizedBox(width: 200, height: 50),
            ),
          ),
        ),
      );
      final lite = find.byType(LiquidGlassLens);
      return lite.evaluate().isEmpty
          ? null
          : tester.widget<LiquidGlassLens>(lite).style.blur;
    }

    expect(await blurFor(MornyeGlassLevel.flat), isNull);
    // The lens carries the preset's Liquid blur.
    expect(await blurFor(MornyeGlassLevel.liquid), 2);
  });

  for (final level in [MornyeGlassLevel.flat, MornyeGlassLevel.liquid]) {
    testWidgets('liquid tab and segment pills follow the mode ($level)', (
      tester,
    ) async {
      late BuildContext context;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            home: Scaffold(
              body: Builder(
                builder: (builderContext) {
                  context = builderContext;
                  return Column(
                    children: [
                      MornyeSegmentedControl(
                        labels: const ['One', 'Two'],
                        selectedIndex: 0,
                        onChanged: (_) {},
                      ),
                      MornyeTabBar(
                        destinations: const [
                          NavigationDestination(
                            icon: Icon(Icons.home),
                            label: 'Home',
                          ),
                          NavigationDestination(
                            icon: Icon(Icons.music_note),
                            label: 'Library',
                          ),
                        ],
                        selectedIndex: 0,
                        onSelected: (_) {},
                        // The app takes both from the mode, as here.
                        blurEnabled: level != MornyeGlassLevel.flat,
                        liquidGlass: level == MornyeGlassLevel.liquid,
                      ),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final liquid = level == MornyeGlassLevel.liquid;
      // Overlays aligned with the bar share this decision.
      expect(
        MornyeTabBar.usesLiquidGlass(
          context,
          blurEnabled: true,
          liquidGlass: liquid,
          contrastBlocked: false,
        ),
        liquid,
      );
      // Every tier draws just one copy of each label.
      // Liquid draws a lens for each glass capsule and the selection pill; None
      // draws none.
      expect(find.byType(LiquidGlassLens), findsNWidgets(liquid ? 4 : 0));
      expect(find.text('Two'), findsOneWidget);
      expect(find.text('Library'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('plain-page panels keep the glass tint without a backdrop pass', (
    tester,
  ) async {
    Color? tintOf(Finder panel) {
      final decorated = tester
          .widgetList<DecoratedBox>(
            find.descendant(of: panel, matching: find.byType(DecoratedBox)),
          )
          .map((box) => box.decoration)
          .whereType<BoxDecoration>()
          .firstWhere((decoration) => decoration.color != null);
      return decorated.color;
    }

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Scaffold(
            body: Column(
              children: [
                const MornyeGlass.navigation(
                  key: ValueKey('sampled'),
                  blurEnabled: true,
                  child: SizedBox(width: 200, height: 40),
                ),
                const MornyeGlass.navigation(
                  key: ValueKey('plain-page'),
                  blurEnabled: true,
                  samplesBackdrop: false,
                  child: SizedBox(width: 200, height: 40),
                ),
                const MornyeGlassPanel.overlay(
                  samplesBackdrop: false,
                  child: SizedBox(width: 200, height: 40),
                ),
                MornyeSegmentedControl(
                  labels: const ['One', 'Two'],
                  selectedIndex: 0,
                  onChanged: (_) {},
                  samplesBackdrop: false,
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    final sampled = find.byKey(const ValueKey('sampled'));
    final plain = find.byKey(const ValueKey('plain-page'));
    // The sampled surface is a lens; the plain one reads nothing.
    expect(
      find.descendant(of: sampled, matching: find.byType(LiquidGlassLens)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: plain, matching: find.byType(LiquidGlassLens)),
      findsNothing,
    );
    // The segmented control's selection pill is Liquid's glass by design, so
    // only the panel is checked here.
    for (final type in [MornyeGlassPanel]) {
      expect(
        find.descendant(
          of: find.byType(type),
          matching: find.byType(LiquidGlassLens),
        ),
        findsNothing,
      );
    }
    // A plain surface keeps a translucent tint and reads nothing behind it.
    expect(tintOf(plain)!.a, lessThan(1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('full-quality glass has one blur; switches have none', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Scaffold(
            body: Column(
              children: [
                const MornyeGlass.navigation(
                  blurEnabled: true,
                  child: SizedBox(width: 200, height: 50),
                ),
                AppSwitch(value: true, onChanged: (_) {}),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    // The page's chrome lens, and the control's lens in its batch.
    expect(find.byType(LiquidGlassLens), findsNWidgets(2));
    expect(find.byType(ImageFiltered), findsNothing);
    // The package switch mounts its lens but keeps it hidden at rest, so an
    // idle switch draws no glass and reads no backdrop.
    for (final lens
        in find
            .descendant(
              of: find.byType(AppSwitch),
              matching: find.byType(LiquidGlassLens),
            )
            .evaluate()) {
      expect((lens.widget as LiquidGlassLens).visibility, isFalse);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('every lens composes into exactly one batch read', (
    tester,
  ) async {
    MornyeGlass.debugShaderFilterSupport = true;
    addTearDown(() => MornyeGlass.debugShaderFilterSupport = null);
    Widget glass(String key, {Widget? child}) => MornyeGlass.navigation(
      key: ValueKey(key),
      blurEnabled: true,
      child: child ?? const SizedBox(width: 80, height: 40),
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Scaffold(
            body: Column(
              children: [
                // Overlapping standalone glass must not share a backdrop.
                Stack(children: [glass('back'), glass('front')]),
                glass('outer', child: glass('inner')),
                MornyeGlassPanel.overlay(child: const SizedBox(height: 40)),
                MornyeSegmentedControl(
                  labels: const ['One', 'Two'],
                  selectedIndex: 0,
                  onChanged: (_) {},
                ),
                LiquidGlassBatch(
                  child: Row(children: [glass('chip-a'), glass('chip-b')]),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    // The segmented thumb is a native lens excluded from the batch; it has its
    // own read and is checked separately.
    final lenses = find
        .byType(LiquidGlassLens)
        .evaluate()
        .where(
          (lens) =>
              lens.findAncestorWidgetOfExactType<LiquidGlassMotionPill>() ==
              null,
        )
        .toList();
    // The nested glass paints on its parent's surface without a lens.
    expect(lenses, hasLength(7));
    for (final lens in lenses) {
      expect(
        find.ancestor(
          of: find.byWidget(lens.widget),
          matching: find.byType(LiquidGlassBatch),
        ),
        findsOneWidget,
      );
    }
    int keyOf(String glass) => LiquidGlassBatch.backdropIdOf(
      tester.element(
        find.descendant(
          of: find.byKey(ValueKey(glass)),
          matching: find.byType(LiquidGlassLens),
        ),
      ),
    )!;
    expect(keyOf('chip-a'), keyOf('chip-b'));
    final keys = {
      for (final lens in lenses) LiquidGlassBatch.backdropIdOf(lens)!,
    };
    expect(keys, hasLength(6));
    expect(keyOf('back'), isNot(keyOf('front')));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a lens under an interface scale measures the window', (
    tester,
  ) async {
    MornyeGlass.debugShaderFilterSupport = true;
    addTearDown(() => MornyeGlass.debugShaderFilterSupport = null);
    tester.view.devicePixelRatio = 2;
    tester.view.physicalSize = const Size(800, 1600);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    MediaQueryData? content;

    Future<void> pumpAt(double scale) => tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          builder: (context, child) =>
              AdaptiveUiScaler(userScale: scale, child: child!),
          home: Scaffold(
            body: MornyeGlass.navigation(
              blurEnabled: true,
              child: Builder(
                builder: (context) {
                  content = MediaQuery.of(context);
                  return const SizedBox(width: 80, height: 40);
                },
              ),
            ),
          ),
        ),
      ),
    );

    for (final scale in [0.8, 1.0, 1.3]) {
      await pumpAt(scale);
      // The lens turns its place in the window into pixels with these two.
      final lens = MediaQuery.of(tester.element(find.byType(LiquidGlassLens)));
      expect(lens.size.width, closeTo(400, 0.001), reason: '$scale');
      expect(lens.size.height, closeTo(800, 0.001), reason: '$scale');
      expect(lens.devicePixelRatio, closeTo(2, 0.001), reason: '$scale');
      // What sits on the glass still lays out in the scaled viewport.
      expect(content!.size.width, closeTo(400 / scale, 0.001));
      expect(content!.devicePixelRatio, closeTo(2 * scale, 0.001));
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('a lens under an interface scale keeps its build on a '
      'keyboard inset change', (tester) async {
    MornyeGlass.debugShaderFilterSupport = true;
    addTearDown(() => MornyeGlass.debugShaderFilterSupport = null);
    tester.view.devicePixelRatio = 2;
    tester.view.physicalSize = const Size(800, 1600);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    MediaQueryData? content;

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          builder: (context, child) =>
              AdaptiveUiScaler(userScale: 0.9, child: child!),
          home: Scaffold(
            resizeToAvoidBottomInset: false,
            body: MornyeGlass.navigation(
              blurEnabled: true,
              child: Builder(
                builder: (context) {
                  content = MediaQuery.of(context);
                  return const SizedBox(width: 80, height: 40);
                },
              ),
            ),
          ),
        ),
      ),
    );
    final lens = tester.widget<LiquidGlassLens>(find.byType(LiquidGlassLens));
    expect(content!.viewInsets.bottom, 0);

    tester.view.viewInsets = const FakeViewPadding(bottom: 600);
    await tester.pump();

    // A lens rebuild repaints its glass.
    expect(
      tester.widget<LiquidGlassLens>(find.byType(LiquidGlassLens)),
      same(lens),
    );
    expect(content!.viewInsets.bottom, closeTo(300 / 0.9, 0.001));
    final window = MediaQuery.of(tester.element(find.byType(LiquidGlassLens)));
    expect(window.size.width, closeTo(400, 0.001));
    expect(window.devicePixelRatio, closeTo(2, 0.001));
    expect(tester.takeException(), isNull);
  });
}
