import 'dart:ui' show Tristate;
import 'package:flutter/semantics.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_switch.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

/// Holds a switch and records the value it sends.
class _Host extends StatefulWidget {
  const _Host({required this.enabled, this.onSent});

  final bool enabled;
  final ValueChanged<bool>? onSent;

  @override
  State<_Host> createState() => _HostState();
}

class _HostState extends State<_Host> {
  bool value = false;

  @override
  Widget build(BuildContext context) => AppSwitch(
    value: value,
    semanticLabel: 'Test switch',
    onChanged: widget.enabled
        ? (next) {
            widget.onSent?.call(next);
            setState(() => value = next);
          }
        : null,
  );
}

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
      child: Scaffold(body: Center(child: child)),
    ),
  ),
);

/// Backdrop samplers under the switch: lenses and backdrop filters.
int _samplers(WidgetTester tester, Finder root) =>
    find
        .descendant(of: root, matching: find.byType(LiquidGlassLens))
        .evaluate()
        .length +
    find
        .descendant(of: root, matching: find.byType(BackdropFilter))
        .evaluate()
        .length;

void main() {
  setUp(() => MornyeGlass.debugShaderFilterSupport = true);
  tearDown(() => MornyeGlass.debugShaderFilterSupport = null);

  for (final level in [MornyeGlassLevel.flat]) {
    testWidgets('${level.name}: the plain switch, no backdrop sampler', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(level: level, child: const _Host(enabled: true)),
      );
      final root = find.byType(AppSwitch);
      expect(find.byType(LiquidGlassSwitch), findsNothing);
      expect(_samplers(tester, root), 0);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Liquid: the package switch is present and toggles on tap', (
    tester,
  ) async {
    final sent = <bool>[];
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        child: _Host(enabled: true, onSent: sent.add),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(LiquidGlassSwitch), findsOneWidget);
    await tester.tap(find.byType(LiquidGlassSwitch));
    await tester.pumpAndSettle();
    expect(sent, [true]);
    await tester.tap(find.byType(LiquidGlassSwitch));
    await tester.pumpAndSettle();
    expect(sent, [true, false]);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Liquid: a disabled switch takes no input and reads as disabled',
    (tester) async {
      final handle = tester.ensureSemantics();
      final sent = <bool>[];
      await tester.pumpWidget(
        _app(
          level: MornyeGlassLevel.liquid,
          child: _Host(enabled: false, onSent: sent.add),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byType(LiquidGlassSwitch), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(sent, isEmpty);
      final node = tester.getSemantics(find.bySemanticsLabel('Test switch'));
      expect(node.flagsCollection.isToggled, isNot(Tristate.none));
      expect(node.flagsCollection.isToggled, Tristate.isFalse);
      expect(node.flagsCollection.isEnabled, isNot(Tristate.none));
      expect(node.flagsCollection.isEnabled, Tristate.isFalse);
      expect((node.getSemanticsData().hasAction(SemanticsAction.tap)), isFalse);
      handle.dispose();
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Liquid: RTL mirrors the switch and still toggles', (
    tester,
  ) async {
    final sent = <bool>[];
    await tester.pumpWidget(
      _app(
        level: MornyeGlassLevel.liquid,
        direction: TextDirection.rtl,
        child: _Host(enabled: true, onSent: sent.add),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.ancestor(
        of: find.byType(LiquidGlassSwitch),
        matching: find.byType(Transform),
      ),
      findsWidgets,
    );
    await tester.tap(find.byType(LiquidGlassSwitch));
    await tester.pumpAndSettle();
    expect(sent, [true]);
    expect(tester.takeException(), isNull);
  });

  for (final level in [MornyeGlassLevel.liquid, MornyeGlassLevel.flat]) {
    testWidgets(
      '${level.name}: the switch exposes a toggled, enabled semantics',
      (tester) async {
        final handle = tester.ensureSemantics();
        await tester.pumpWidget(
          _app(level: level, child: const _Host(enabled: true)),
        );
        await tester.pumpAndSettle();
        final node = tester.getSemantics(find.bySemanticsLabel('Test switch'));
        expect(node.flagsCollection.isToggled, isNot(Tristate.none));
        expect(node.flagsCollection.isToggled, Tristate.isFalse);
        expect(node.flagsCollection.isEnabled, Tristate.isTrue);
        expect(
          (node.getSemanticsData().hasAction(SemanticsAction.tap)),
          isTrue,
        );
        handle.dispose();
      },
    );
  }
}
