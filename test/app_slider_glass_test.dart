import 'dart:ui' show Tristate;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_slider.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/mornye_player_slider.dart';

Widget _app({
  required MornyeGlassLevel level,
  required Widget child,
  TextDirection direction = TextDirection.ltr,
}) => ProviderScope(
  overrides: [mornyeGlassLevelProvider.overrideWithValue(level)],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.light),
    home: Directionality(
      textDirection: direction,
      child: Scaffold(
        body: Center(child: SizedBox(width: 300, child: child)),
      ),
    ),
  ),
);

/// A parent that stores each value the slider reports, as the app does.
class _EchoSlider extends StatefulWidget {
  const _EchoSlider({required this.sent});

  final List<double> sent;

  @override
  State<_EchoSlider> createState() => _EchoSliderState();
}

class _EchoSliderState extends State<_EchoSlider> {
  double _value = 0;

  @override
  Widget build(BuildContext context) => AppSlider(
    value: _value,
    divisions: 4,
    onChanged: (v) {
      widget.sent.add(v);
      setState(() => _value = v);
    },
  );
}

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  testWidgets('None: the standard slider, and no package lens', (tester) async {
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.flat,
        child: AppSlider(value: 0.5, onChanged: (_) {}),
      ),
    );
    expect(find.byType(Slider), findsOneWidget);
    expect(find.byType(LiquidGlassSlider), findsNothing);
    expect(find.byType(LiquidGlassLens), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Liquid: the package slider, and the value snaps to divisions', (
    tester,
  ) async {
    final values = <double>[];
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        child: AppSlider(value: 0, divisions: 4, onChanged: values.add),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(LiquidGlassSlider), findsOneWidget);
    expect(find.byType(Slider), findsNothing);
    await tester.drag(find.byType(LiquidGlassSlider), const Offset(80, 0));
    // The package slider springs after a drag, so it never settles: pump time.
    await tester.pump(const Duration(seconds: 1));
    expect(values, isNotEmpty);
    for (final v in values) {
      expect((v * 4 - (v * 4).round()).abs(), lessThan(1e-9), reason: '$v');
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('Liquid: a disabled slider takes no input', (tester) async {
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        child: const AppSlider(value: 0.5, onChanged: null),
      ),
    );
    await tester.pumpAndSettle();
    await tester.drag(
      find.byType(LiquidGlassSlider),
      const Offset(80, 0),
      warnIfMissed: false,
    );
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Liquid: RTL mirrors the slider', (tester) async {
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        direction: TextDirection.rtl,
        child: AppSlider(value: 0.5, onChanged: (_) {}),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.ancestor(
        of: find.byType(LiquidGlassSlider),
        matching: find.byType(Transform),
      ),
      findsWidgets,
    );
    expect(tester.takeException(), isNull);
  });

  for (final level in MornyeGlassLevel.values) {
    testWidgets(
      '${level.name}: the slider exposes its label and enabled state',
      (tester) async {
        final handle = tester.ensureSemantics();
        await tester.pumpWidget(
          _app(
            level: level,
            child: AppSlider(
              value: 0.5,
              semanticLabel: 'Speed',
              onChanged: (_) {},
            ),
          ),
        );
        await tester.pumpAndSettle();
        final node = tester.getSemantics(find.bySemanticsLabel('Speed'));
        expect(node.flagsCollection.isEnabled, isNot(Tristate.isFalse));
        handle.dispose();
      },
    );
  }

  testWidgets(
    'the Now Playing player slider is not switched to the package slider',
    (tester) async {
      await tester.pumpWidget(
        _app(
          level: MornyeGlassLevel.liquid,
          child: MornyePlayerSlider(value: 0.5, onChanged: (_) {}),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(LiquidGlassSlider), findsNothing);
      expect(find.byType(Slider), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Liquid: the semantic value and steps use the formatter, not the label',
    (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(
        _app(
          level: MornyeGlassLevel.liquid,
          child: AppSlider(
            value: 0.5,
            min: 0,
            max: 2,
            divisions: 4,
            label: 'LABEL ONLY',
            semanticLabel: 'Speed',
            semanticFormatterCallback: (v) => '${(v * 2).toStringAsFixed(1)}x',
            onChanged: (_) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      final node = tester.getSemantics(find.bySemanticsLabel('Speed'));
      expect(node.value, '1.0x');
      expect(node.increasedValue, '2.0x');
      expect(node.decreasedValue, '0.0x');
      expect(node.value, isNot(contains('LABEL ONLY')));
      expect(node.increasedValue, isNot(contains('LABEL ONLY')));
      handle.dispose();
    },
  );

  testWidgets('Liquid: a discrete slider reports only steps that change', (
    tester,
  ) async {
    final sent = <double>[];
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        child: AppSlider(value: 0, divisions: 4, onChanged: sent.add),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));
    await tester.drag(find.byType(LiquidGlassSlider), const Offset(120, 0));
    await tester.pump(const Duration(seconds: 1));
    expect(sent, isNotEmpty);
    expect(sent.toSet().length, sent.length, reason: 'no repeated steps');
    for (final v in sent) {
      expect((v * 4 - (v * 4).round()).abs(), lessThan(1e-9));
    }
  });

  testWidgets(
    'Liquid: a parent that echoes each step back is sent each step once',
    (tester) async {
      final sent = <double>[];
      await tester.pumpWidget(
        _app(
          level: MornyeGlassLevel.liquid,
          child: _EchoSlider(sent: sent),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      await tester.drag(find.byType(LiquidGlassSlider), const Offset(120, 0));
      await tester.pump(const Duration(seconds: 1));
      final up = List<double>.of(sent);
      sent.clear();
      await tester.drag(find.byType(LiquidGlassSlider), const Offset(-120, 0));
      await tester.pump(const Duration(seconds: 1));
      // Within one drag each step is sent once; the echo of a step sends
      // nothing. (A later drag may cross steps the earlier one sent.)
      expect(up, isNotEmpty);
      expect(sent, isNotEmpty);
      for (final drag in [up, sent]) {
        expect(
          drag.toSet().length,
          drag.length,
          reason: 'a step is sent once per drag: $drag',
        );
      }
    },
  );
}
