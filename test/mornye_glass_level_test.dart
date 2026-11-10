import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/providers/runtime_profile_provider.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/app_switch.dart';
import 'package:mornye/widgets/extension_repo_card.dart';
import 'package:mornye/widgets/mornye_chrome.dart';
import 'package:mornye/widgets/mornye_liquid_backdrop.dart';
import 'package:mornye/widgets/mornye_selection_pill.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();

  @override
  void setForceBackdropBlur(bool enabled) {
    state = state.copyWith(forceBackdropBlur: enabled);
  }
}

MornyeGlassLevel _levelFor({
  required bool lowEnd,
  required bool blur,
  required bool liquidCapable,
}) {
  final container = ProviderContainer(
    overrides: [
      lowEndDeviceProvider.overrideWithValue(lowEnd),
      // Device default or the user's "always use blur effects" override.
      backdropBlurEnabledProvider.overrideWithValue(blur),
      deviceSupportsLiquidGlassProvider.overrideWithValue(liquidCapable),
    ],
  );
  addTearDown(container.dispose);
  return container.read(mornyeGlassLevelProvider);
}

void main() {
  for (final brightness in Brightness.values) {
    for (final reduceMotion in [false, true]) {
      testWidgets(
        'glass override preserves accessibility ($brightness, motion: $reduceMotion)',
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
                settingsProvider.overrideWith(_Settings.new),
                // A capable GPU must not implicitly override contrast.
                deviceSupportsBackdropBlurProvider.overrideWithValue(true),
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
          // Turn the override on and off while the system request stays on.
          for (final forceBlur in [false, true, false]) {
            container
                .read(settingsProvider.notifier)
                .setForceBackdropBlur(forceBlur);
            await tester.pumpAndSettle();
            expect(
              find.byType(BackdropFilter),
              findsNWidgets(forceBlur ? 3 : 0),
            );
            final pills = tester.widgetList<MornyeSelectionPill>(
              find.byType(MornyeSelectionPill),
            );
            expect(
              pills.every((pill) => pill.contrastBlocked == !forceBlur),
              isTrue,
            );
            expect(pills.last.liquidInteraction, forceBlur && !reduceMotion);
            expect(MediaQuery.highContrastOf(context), isTrue);
            expect(MediaQuery.disableAnimationsOf(context), reduceMotion);
            expect(
              MornyeTabBar.usesLiquidGlass(
                context,
                blurEnabled: true,
                liquidGlass: true,
                contrastBlocked: container.read(
                  mornyeGlassContrastBlockedProvider(true),
                ),
              ),
              forceBlur && !reduceMotion,
            );
          }
          // A live system-contrast change restores normal glass automatically.
          tester.platformDispatcher.accessibilityFeaturesTestValue =
              FakeAccessibilityFeatures(disableAnimations: reduceMotion);
          await tester.pumpAndSettle();
          expect(find.byType(BackdropFilter), findsNWidgets(3));
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  test('runtime tiers map to Mornye glass levels', () {
    // Low tier: low-RAM Android.
    expect(
      _levelFor(lowEnd: true, blur: false, liquidCapable: false),
      MornyeGlassLevel.flat,
    );
    // Standard Android: frosted blur without shader passes.
    expect(
      _levelFor(lowEnd: false, blur: false, liquidCapable: false),
      MornyeGlassLevel.frosted,
    );
    expect(
      _levelFor(lowEnd: false, blur: false, liquidCapable: true),
      MornyeGlassLevel.liquid,
    );
    // High-tier Android.
    expect(
      _levelFor(lowEnd: false, blur: true, liquidCapable: true),
      MornyeGlassLevel.liquid,
    );
    // "Always use blur effects" restores the full material on any tier.
    for (final lowEnd in [true, false]) {
      expect(
        _levelFor(lowEnd: lowEnd, blur: true, liquidCapable: false),
        MornyeGlassLevel.liquid,
      );
    }
  });

  testWidgets('frosted level keeps blur but drops every shader surface', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          deviceSupportsLiquidGlassProvider.overrideWithValue(false),
          backdropBlurEnabledProvider.overrideWithValue(false),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
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
                AppSwitch(value: true, onChanged: (_) {}),
                Consumer(
                  builder: (context, ref, _) => MornyeTabBar(
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
                    blurEnabled: ref.watch(mornyeBlurEnabledProvider),
                    liquidGlass: ref.watch(mornyeLiquidGlassProvider),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(MornyeSelectionPill), findsNWidgets(2));
    expect(find.byType(ImageFiltered), findsNothing);
    // The frosted material still samples the page behind it.
    expect(find.byType(BackdropFilter), findsWidgets);
    expect(find.text('Library'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'glass uses the fixed Clear blur and mid-range glass caps its radius',
    (tester) async {
      Future<ui.ImageFilter?> filterFor(bool liquid) async {
        await tester.pumpWidget(
          ProviderScope(
            key: UniqueKey(),
            overrides: [
              mornyeGlassLevelProvider.overrideWithValue(
                liquid ? MornyeGlassLevel.liquid : MornyeGlassLevel.frosted,
              ),
            ],
            child: MaterialApp(
              theme: MornyeTheme.build(Brightness.light),
              home: const MornyeGlass.navigation(
                blurEnabled: true,
                child: SizedBox(width: 200, height: 50),
              ),
            ),
          ),
        );
        final filters = tester.widgetList<BackdropFilter>(
          find.byType(BackdropFilter),
        );
        return filters.isEmpty ? null : filters.single.filter;
      }

      expect(await filterFor(false), ui.ImageFilter.blur(sigmaX: 3, sigmaY: 3));
      expect(await filterFor(true), ui.ImageFilter.blur(sigmaX: 2, sigmaY: 2));
    },
  );

  {
    testWidgets('liquid tab and segment pills use the fixed Clear glass', (
      tester,
    ) async {
      late BuildContext context;
      await tester.pumpWidget(
        ProviderScope(
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
                        blurEnabled: true,
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

      // Overlays aligned with the bar share this decision.
      expect(
        MornyeTabBar.usesLiquidGlass(
          context,
          blurEnabled: true,
          liquidGlass: true,
          contrastBlocked: false,
        ),
        isTrue,
      );
      // Every tier draws just one copy of each label and a painted pill.
      expect(find.byType(MornyeSelectionPill), findsNWidgets(2));
      expect(find.byType(BackdropFilter), findsNWidgets(2));
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
                const ExtensionRepoCard(
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
    expect(
      find.descendant(of: sampled, matching: find.byType(BackdropFilter)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: plain, matching: find.byType(BackdropFilter)),
      findsNothing,
    );
    for (final type in [
      ExtensionRepoCard,
      MornyeGlassPanel,
      MornyeSegmentedControl,
    ]) {
      expect(
        find.descendant(
          of: find.byType(type),
          matching: find.byType(BackdropFilter),
        ),
        findsNothing,
      );
    }
    // Same translucent material, so the page looks identical behind it.
    expect(tintOf(plain), tintOf(sampled));
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

    expect(find.byType(BackdropFilter), findsOneWidget);
    expect(find.byType(ImageFiltered), findsNothing);
    expect(
      find.descendant(
        of: find.byType(AppSwitch),
        matching: find.byType(BackdropFilter),
      ),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('every lens composes into exactly one batch read', (
    tester,
  ) async {
    MornyeGlassRules.debugShaderFilterSupport = true;
    addTearDown(() => MornyeGlassRules.debugShaderFilterSupport = null);
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
                MornyeGlassPanel.overlay(
                  samplesBackdrop: true,
                  child: const SizedBox(height: 40),
                ),
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

    final lenses = find.byType(LiquidGlassLens).evaluate().toList();
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

  testWidgets(
    'overlay glass keeps its tint and skips the backdrop by default',
    (tester) async {
      MornyeGlassRules.debugShaderFilterSupport = true;
      addTearDown(() => MornyeGlassRules.debugShaderFilterSupport = null);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
          ],
          child: MaterialApp(
            theme: MornyeTheme.build(Brightness.dark),
            home: Scaffold(
              body: MornyeGlassPanel.overlay(child: const SizedBox(height: 40)),
            ),
          ),
        ),
      );
      expect(find.byType(LiquidGlassLens), findsNothing);
      expect(find.byType(BackdropFilter), findsNothing);
      expect(find.byType(MornyeGlassPanel), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
