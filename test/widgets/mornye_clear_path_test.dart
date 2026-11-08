import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/providers/runtime_profile_provider.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/theme/mornye_theme.dart';
import 'package:mornye/widgets/mornye_chrome.dart';
import 'package:mornye/widgets/mornye_liquid_backdrop.dart';
import 'package:mornye/widgets/mornye_selection_pill.dart';

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

Future<void> _pumpGlass(WidgetTester tester) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWith(_Settings.new),
        deviceSupportsBackdropBlurProvider.overrideWithValue(true),
        deviceSupportsLiquidGlassProvider.overrideWithValue(true),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.light),
        home: const Scaffold(
          body: MornyeGlass.navigation(
            blurEnabled: true,
            child: SizedBox(width: 200, height: 50),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  setUp(() => MornyeGlassRules.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlassRules.debugShaderFilterSupport = null);

  // Glass is fixed at the old Clear end: sigma 2 and the full dispersion.
  testWidgets('fixed Clear glass maps to sigma 2 and dispersion 1.32', (
    tester,
  ) async {
    await _pumpGlass(tester);
    final lens = tester.widget<LiquidGlassLens>(
      find.byType(LiquidGlassLens).first,
    );
    final backdrop = tester.widget<MornyeLiquidBackdrop>(
      find.byType(MornyeLiquidBackdrop).first,
    );
    expect(backdrop.blurSigma, MornyeGlassRules.clearSigma);
    expect(backdrop.blurSigma, 2);
    expect(lens.style.dispersion, closeTo(1.32, 1e-9));
  });

  testWidgets('the held lens has no blur', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [settingsProvider.overrideWith(_Settings.new)],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          home: Center(
            child: SizedBox(
              width: 300,
              height: 56,
              child: MornyeSelectionPill(
                labels: const ['A', 'B', 'C'],
                selectedIndex: 0,
                liquidInteraction: true,
                padding: EdgeInsets.zero,
                onChanged: (_) {},
                itemBuilder: (_, index, _) => const SizedBox.shrink(),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    // At rest the held lens is present but draws no lens.
    expect(
      find.descendant(
        of: find.byType(MornyeSelectionPill),
        matching: find.byType(LiquidGlassLens),
      ),
      findsNothing,
    );

    final gesture = await tester.startGesture(
      tester.getCenter(find.byType(MornyeSelectionPill)),
    );
    addTearDown(gesture.up);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(seconds: 1));

    final backdrop = tester.widget<MornyeLiquidBackdrop>(
      find.descendant(
        of: find.byType(MornyeSelectionPill),
        matching: find.byType(MornyeLiquidBackdrop),
      ),
    );
    expect(backdrop.blurSigma, 0);
    expect(backdrop.interaction, isTrue);
    expect(backdrop.progress, greaterThan(0));
    expect(tester.takeException(), isNull);
  });
}
