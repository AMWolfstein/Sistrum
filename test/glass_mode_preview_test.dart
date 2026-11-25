import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/screens/settings/mornye_glass_mode_preview.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Backdrop samplers under the preview: a lens, a backdrop filter, or a lite
/// frost with a blur above zero. A rim-only lite (blur 0) reads nothing.
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

final _preview = find.byKey(const ValueKey('mornye-glass-mode-preview'));

Widget _app(MornyeGlassLevel level, {bool comparing = false}) {
  return ProviderScope(
    overrides: [
      mornyeGlassLevelProvider.overrideWithValue(level),
      if (comparing)
        mornyeGlassCompareModeProvider.overrideWith(
          () => _Compare(MornyeGlassLevel.flat),
        ),
    ],
    child: MaterialApp(
      theme: MornyeTheme.build(Brightness.light),
      home: const Scaffold(
        body: Padding(
          padding: EdgeInsets.all(16),
          child: MornyeGlassModePreview(modeLabel: 'Liquid Glass'),
        ),
      ),
    ),
  );
}

class _Compare extends MornyeGlassCompareModeNotifier {
  _Compare(this._mode);
  final MornyeGlassLevel _mode;

  @override
  MornyeGlassLevel? build() => _mode;
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  testWidgets('None: the preview draws a plain fill, no sampler', (
    tester,
  ) async {
    await tester.pumpWidget(_app(MornyeGlassLevel.flat));
    expect(_preview, findsOneWidget);
    expect(_samplers(tester, _preview), 0);
    expect(find.byType(LiquidGlassLens), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Liquid: the preview draws a lens', (tester) async {
    await tester.pumpWidget(_app(MornyeGlassLevel.liquid));
    expect(_preview, findsOneWidget);
    expect(
      find.descendant(of: _preview, matching: find.byType(LiquidGlassLens)),
      findsOneWidget,
    );
    expect(_samplers(tester, _preview), 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the preview follows the mode when it changes', (tester) async {
    await tester.pumpWidget(_app(MornyeGlassLevel.liquid));
    expect(_samplers(tester, _preview), 1);
    await tester.pumpWidget(_app(MornyeGlassLevel.flat));
    expect(_samplers(tester, _preview), 0);
    await tester.pumpWidget(_app(MornyeGlassLevel.liquid));
    expect(_samplers(tester, _preview), 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a Performance hub comparison hides the preview', (tester) async {
    await tester.pumpWidget(_app(MornyeGlassLevel.liquid, comparing: true));
    expect(_preview, findsNothing);
    expect(tester.takeException(), isNull);
  });
}
