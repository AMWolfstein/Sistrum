import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

const _destinations = [
  NavigationDestination(icon: Icon(Icons.home), label: 'Home'),
  NavigationDestination(
    icon: MornyeNavigationIcon(icon: Icons.music_note, badgeCount: 7),
    label: 'Library',
  ),
  NavigationDestination(icon: Icon(Icons.search), label: 'Search'),
];

/// Pumps a tab bar in [mode]; returns the list of selections it reports.
Future<List<int>> _pumpTabBar(
  WidgetTester tester, {
  required TextDirection direction,
  MornyeGlassLevel mode = MornyeGlassLevel.liquid,
}) async {
  final changes = <int>[];
  var selected = 0;
  await tester.pumpWidget(
    ProviderScope(
      overrides: [mornyeGlassModeProvider.overrideWithValue(mode)],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        home: Directionality(
          textDirection: direction,
          child: Scaffold(
            body: Center(
              child: SizedBox(
                width: 360,
                child: StatefulBuilder(
                  builder: (context, setState) => MornyeTabBar(
                    destinations: _destinations,
                    selectedIndex: selected,
                    onSelected: (next) {
                      changes.add(next);
                      setState(() => selected = next);
                    },
                    blurEnabled: true,
                    liquidGlass: mode == MornyeGlassLevel.liquid,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return changes;
}

/// The native thumb's centre in global coordinates.
Offset _motionPillCentre(WidgetTester tester) {
  final pill = tester.widget<LiquidGlassMotionPill>(
    find.byType(LiquidGlassMotionPill),
  );
  return tester.getTopLeft(find.byType(LiquidGlassMotionPill)) + pill.center;
}

void main() {
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  for (final direction in TextDirection.values) {
    testWidgets(
      'a tap selects and the glass thumb moves to its slot ($direction)',
      (tester) async {
        MornyeGlass.debugShaderFilterSupport = true;
        final changes = await _pumpTabBar(tester, direction: direction);
        expect(find.byType(LiquidGlassMotionPill), findsOneWidget);
        await tester.tap(find.text('Search'));
        await tester.pumpAndSettle();
        expect(changes, [2]);
        expect(
          _motionPillCentre(tester).dx,
          closeTo(tester.getCenter(find.text('Search')).dx, 2),
        );
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('a cancelled press selects nothing ($direction)', (
      tester,
    ) async {
      MornyeGlass.debugShaderFilterSupport = true;
      final changes = await _pumpTabBar(tester, direction: direction);
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Search')),
      );
      await gesture.moveTo(tester.getCenter(find.text('Home')));
      await tester.pump();
      await gesture.cancel();
      await tester.pumpAndSettle();
      expect(changes, isEmpty);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a finger on the bar lifts the glass thumb until release', (
    tester,
  ) async {
    MornyeGlass.debugShaderFilterSupport = true;
    await _pumpTabBar(tester, direction: TextDirection.ltr);
    bool lifted() => tester
        .widget<LiquidGlassMotionPill>(find.byType(LiquidGlassMotionPill))
        .active;
    expect(lifted(), isFalse);
    final gesture = await tester.startGesture(
      tester.getCenter(find.text('Library')),
    );
    await tester.pump();
    expect(lifted(), isTrue);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(lifted(), isFalse);
  });

  testWidgets('the selected tab is primary and the others are on-surface', (
    tester,
  ) async {
    MornyeGlass.debugShaderFilterSupport = true;
    final theme = MornyeTheme.build(Brightness.dark);
    await _pumpTabBar(tester, direction: TextDirection.ltr);
    Color? labelColor(String label) =>
        tester.widget<Text>(find.text(label)).style?.color;
    expect(labelColor('Home'), theme.colorScheme.primary);
    expect(labelColor('Search'), theme.colorScheme.onSurface);
    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();
    expect(labelColor('Search'), theme.colorScheme.primary);
    expect(labelColor('Home'), theme.colorScheme.onSurface);
  });

  testWidgets('none draws no native lens thumb', (tester) async {
    MornyeGlass.debugShaderFilterSupport = true;
    await _pumpTabBar(
      tester,
      direction: TextDirection.ltr,
      mode: MornyeGlassLevel.flat,
    );
    expect(find.byType(LiquidGlassMotionPill), findsNothing);
    expect(find.byType(LiquidGlassLens), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('nested glass keeps one capsule frost and large text fits', (
    tester,
  ) async {
    MornyeGlass.debugShaderFilterSupport = true;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.liquid),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Scaffold(
              body: Column(
                children: [
                  MornyeGlassPanel(
                    child: MornyeSegmentedControl(
                      labels: const ['A', 'B'],
                      selectedIndex: 0,
                      onChanged: (_) {},
                    ),
                  ),
                  MornyeTabBar(
                    destinations: const [
                      NavigationDestination(
                        icon: Icon(Icons.home),
                        label: 'Home',
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
      ),
    );
    await tester.pumpAndSettle();
    // The panel's own lens is the only frost. The segmented thumb is a native
    // lens too; the capsule inside the panel must not add a second backdrop.
    expect(
      find.descendant(
        of: find.byType(MornyeGlassPanel),
        matching: find.byType(LiquidGlassLens),
      ),
      findsNWidgets(2),
    );
    expect(tester.getSize(find.byType(MornyeTabBar)).height, greaterThan(80));
    expect(tester.takeException(), isNull);
  });

  testWidgets('switch drag toggles once with no filter', (tester) async {
    var value = false;
    final changes = <bool>[];
    // The plain switch (None): the Liquid switch draws its own lens on touch.
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.flat),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Directionality(
            textDirection: TextDirection.ltr,
            child: Scaffold(
              body: Center(
                child: StatefulBuilder(
                  builder: (context, setState) => AppSwitch(
                    value: value,
                    onChanged: (next) {
                      changes.add(next);
                      setState(() => value = next);
                    },
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final gesture = await tester.startGesture(
      tester.getCenter(find.byType(AppSwitch)),
    );
    await gesture.moveBy(const Offset(25, 0));
    await gesture.moveBy(const Offset(25, 0));
    await gesture.up();
    await tester.pumpAndSettle();
    expect(changes, [true]);
    expect(find.byType(BackdropFilter), findsNothing);
    expect(find.byType(ImageFiltered), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
