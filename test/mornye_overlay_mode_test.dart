import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_context_menu.dart';

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

/// Opens one presented overlay from a button in [mode] on a capable device.
Future<void> _openOverlay(
  WidgetTester tester, {
  required MornyeGlassLevel mode,
  required void Function(BuildContext context) open,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        mornyeGlassModeProvider.overrideWithValue(mode),
        deviceSupportsLiquidGlassProvider.overrideWithValue(true),
        deviceSupportsBackdropBlurProvider.overrideWithValue(true),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => open(context),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

/// Sheets and dialogs are content: a plain fill in every mode, with no sampler.
void _expectPlainContent() {
  expect(find.byType(LiquidGlassLens), findsNothing);
  expect(find.byType(LiquidGlassLens), findsNothing);
  expect(find.byType(BackdropFilter), findsNothing);
}

/// The one renderer each mode may draw for a presented glass surface.
void _expectRenderer(MornyeGlassLevel mode) {
  switch (mode) {
    case MornyeGlassLevel.liquid:
      // Host tests have no Impeller or LiquidGlassView, so the lens draws its
      // own lite fallback inside; only the lens itself is asserted here.
      expect(find.byType(LiquidGlassLens), findsWidgets);
    case MornyeGlassLevel.flat:
      expect(find.byType(LiquidGlassLens), findsNothing);
      expect(find.byType(LiquidGlassLens), findsNothing);
      expect(find.byType(BackdropFilter), findsNothing);
  }
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  for (final mode in MornyeGlassLevel.values) {
    testWidgets('a bottom sheet is plain content in $mode', (tester) async {
      await _openOverlay(
        tester,
        mode: mode,
        open: (context) => showAppBottomSheet<void>(
          context: context,
          builder: (_) => const SizedBox(height: 120, child: Text('sheet')),
        ),
      );
      expect(find.text('sheet'), findsOneWidget);
      _expectPlainContent();
      expect(tester.takeException(), isNull);
    });

    testWidgets('an alert dialog is plain content in $mode', (tester) async {
      await _openOverlay(
        tester,
        mode: mode,
        open: (context) => showAppDialog<void>(
          context: context,
          builder: (_) => const AppAlertDialog(
            title: Text('dialog'),
            content: Text('body'),
          ),
        ),
      );
      expect(find.text('body'), findsOneWidget);
      _expectPlainContent();
      expect(tester.takeException(), isNull);
    });

    testWidgets('a context menu renders the $mode renderer', (tester) async {
      await _openOverlay(
        tester,
        mode: mode,
        open: (context) => showMornyeContextMenu<void>(
          context: context,
          builder: (_) => MornyeContextMenu(
            groups: [
              [
                MornyeMenuAction(
                  icon: Icons.play_arrow,
                  label: 'menu row',
                  onPressed: () {},
                ),
              ],
            ],
          ),
        ),
      );
      expect(find.text('menu row'), findsOneWidget);
      _expectRenderer(mode);
      expect(tester.takeException(), isNull);
    });
  }

  test('None is never overridden by device fallback or lowest tier', () {
    for (final lowEnd in [false, true]) {
      for (final liquidCapable in [false, true]) {
        final container = ProviderContainer(
          overrides: [
            mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.flat),
            lowEndDeviceProvider.overrideWithValue(lowEnd),
            deviceSupportsLiquidGlassProvider.overrideWithValue(liquidCapable),
            deviceSupportsBackdropBlurProvider.overrideWithValue(true),
          ],
        );
        addTearDown(container.dispose);
        expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.flat);
        expect(container.read(mornyeBlurEnabledProvider), isFalse);
        expect(MornyeGlassPreset.of(MornyeGlassLevel.flat).blurSigma, 0);
      }
    }
  });

  test('device fallback changes the effective level, never the selection', () {
    final container = ProviderContainer(
      overrides: [
        mornyeGlassModeProvider.overrideWithValue(MornyeGlassLevel.liquid),
        lowEndDeviceProvider.overrideWithValue(false),
        deviceSupportsLiquidGlassProvider.overrideWithValue(false),
        deviceSupportsBackdropBlurProvider.overrideWithValue(false),
      ],
    );
    addTearDown(container.dispose);
    expect(container.read(mornyeGlassModeProvider), MornyeGlassLevel.liquid);
    // Without the device's lens, Liquid renders as None; the choice is kept.
    expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.flat);
  });
  testWidgets('a presented sheet follows a live mode change', (tester) async {
    MornyeGlass.debugShaderFilterSupport = true;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          themeProvider.overrideWith(_Theme.new),
          deviceSupportsLiquidGlassProvider.overrideWithValue(true),
          deviceSupportsBackdropBlurProvider.overrideWithValue(true),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () => showAppBottomSheet<void>(
                    context: context,
                    builder: (_) =>
                        const SizedBox(height: 120, child: Text('sheet')),
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    final container = ProviderScope.containerOf(
      tester.element(find.text('sheet')),
    );
    // The open sheet is not rebuilt by hand: its glass reads the mode itself.
    for (final mode in [MornyeGlassLevel.flat, MornyeGlassLevel.liquid]) {
      await container.read(themeProvider.notifier).setMornyeGlassLevel(mode);
      await tester.pumpAndSettle();
      _expectPlainContent();
    }
    expect(find.text('sheet'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
