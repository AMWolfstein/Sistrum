import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

Widget _segmented(MornyeGlassLevel level) => ProviderScope(
  overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    home: Scaffold(
      body: Center(
        child: MornyeSegmentedControl(
          labels: const ['A', 'B'],
          selectedIndex: 0,
          onChanged: (_) {},
        ),
      ),
    ),
  ),
);

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  testWidgets(
    'Liquid: the selection pill is the package tab pill (clear, no blur)',
    (tester) async {
      await tester.pumpWidget(_segmented(MornyeGlassLevel.liquid));
      await tester.pumpAndSettle();
      final pill = tester.widget<LiquidGlassMotionPill>(
        find.byType(LiquidGlassMotionPill),
      );
      final style = pill.style!;
      expect(style.blur, 0, reason: 'a clear lens adds no blur pass');
      expect(
        style.refractionIndex,
        MornyeGlassPreset.liquid.pillRefractionIndex,
      );
      expect(
        style.refractionWidth,
        MornyeGlassPreset.liquid.pillRefractionWidth,
      );
      expect(style.refractionIndex, 1.015);
      expect(style.refractionWidth, 24);
      expect(style.dispersion, 0, reason: 'dispersion adds a backdrop read');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('None: no motion pill, the thumb is flat', (tester) async {
    await tester.pumpWidget(_segmented(MornyeGlassLevel.flat));
    await tester.pumpAndSettle();
    expect(find.byType(LiquidGlassMotionPill), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
