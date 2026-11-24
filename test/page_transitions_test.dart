import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:flutter/gestures.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/theme/page_transitions.dart';

final _navigatorKey = GlobalKey<NavigatorState>();

Future<void> _pumpApp(
  WidgetTester tester,
  ThemeData theme, {
  bool reducedMotion = false,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      navigatorKey: _navigatorKey,
      theme: theme,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reducedMotion),
        child: child!,
      ),
      home: const Scaffold(body: Text('home')),
    ),
  );
}

void _push(String label) {
  _navigatorKey.currentState!.push(
    MaterialPageRoute<void>(
      builder: (_) => Scaffold(body: Center(child: Text(label))),
    ),
  );
}

PageTransitionsBuilder _builderFor(ThemeData theme, TargetPlatform platform) {
  final builder = theme.pageTransitionsTheme.builders[platform];
  expect(builder, isA<ReducedMotionPageTransitionsBuilder>());
  return (builder! as ReducedMotionPageTransitionsBuilder).builder;
}

double _opacityAbove(WidgetTester tester, String label) => tester
    .widgetList<FadeTransition>(
      find.ancestor(
        of: find.text(label),
        matching: find.byType(FadeTransition),
      ),
    )
    .fold(1.0, (opacity, fade) => opacity * fade.opacity.value);

void main() {
  final mornye = MornyeTheme.build(Brightness.light);

  test('every platform has a Mornye builder', () {
    for (final platform in [
      TargetPlatform.android,
      TargetPlatform.iOS,
      TargetPlatform.macOS,
      TargetPlatform.windows,
      TargetPlatform.linux,
    ]) {
      final mornyeBuilder = _builderFor(mornye, platform);
      expect(mornyeBuilder, isNot(isA<PredictiveBackPageTransitionsBuilder>()));
      final desktop =
          platform == TargetPlatform.macOS ||
          platform == TargetPlatform.windows ||
          platform == TargetPlatform.linux;
      if (desktop) {
        expect(mornyeBuilder, isA<DesktopCupertinoPageTransitionsBuilder>());
      } else {
        expect(mornyeBuilder, isA<CupertinoPageTransitionsBuilder>());
      }
    }
  });

  for (final platform in [TargetPlatform.windows, TargetPlatform.linux]) {
    testWidgets('Mornye ${platform.name} push uses the Cupertino transition '
        'and ignores a mouse drag from the edge', (tester) async {
      await _pumpApp(tester, mornye.copyWith(platform: platform));
      _push('page');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(
        find.ancestor(
          of: find.text('page'),
          matching: find.byType(CupertinoPageTransition),
        ),
        findsOneWidget,
      );
      await tester.pumpAndSettle();

      final gesture = await tester.startGesture(
        const Offset(5, 300),
        kind: PointerDeviceKind.mouse,
      );
      await gesture.moveBy(const Offset(200, 0));
      await gesture.moveBy(const Offset(200, 0));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('page'), findsOneWidget);
    });
  }

  testWidgets('Mornye Android push uses the Cupertino transition', (
    tester,
  ) async {
    await _pumpApp(tester, mornye.copyWith(platform: TargetPlatform.android));
    _push('page');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(
      find.ancestor(
        of: find.text('page'),
        matching: find.byType(CupertinoPageTransition),
      ),
      findsOneWidget,
    );
    await tester.pumpAndSettle();
  });

  testWidgets('Mornye Android back closes a sheet before the page', (
    tester,
  ) async {
    await _pumpApp(tester, mornye.copyWith(platform: TargetPlatform.android));
    _push('page');
    await tester.pumpAndSettle();
    showModalBottomSheet<void>(
      context: _navigatorKey.currentContext!,
      builder: (_) => const SizedBox(height: 200, child: Text('sheet')),
    );
    await tester.pumpAndSettle();

    // A predictive back gesture from the system, then its commit.
    const codec = StandardMethodCodec();
    Future<void> send(String method, [Object? arguments]) =>
        tester.binding.defaultBinaryMessenger.handlePlatformMessage(
          SystemChannels.backGesture.name,
          codec.encodeMethodCall(MethodCall(method, arguments)),
          (_) {},
        );
    await send('startBackGesture', <String, Object?>{
      'touchOffset': <double>[5, 300],
      'progress': 0.0,
      'swipeEdge': 0,
    });
    await send('commitBackGesture');
    await tester.pumpAndSettle();

    expect(find.text('sheet'), findsNothing);
    expect(find.text('page'), findsOneWidget);
  });

  group('reduced motion', () {
    for (final platform in [TargetPlatform.macOS, TargetPlatform.iOS]) {
      testWidgets('Mornye ${platform.name} pushes and pops at once', (
        tester,
      ) async {
        await _pumpApp(
          tester,
          mornye.copyWith(platform: platform),
          reducedMotion: true,
        );
        _push('page');
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 16));
        expect(tester.getTopLeft(find.byType(Scaffold).last), Offset.zero);
        expect(_opacityAbove(tester, 'page'), 1);
        await tester.pumpAndSettle();

        _navigatorKey.currentState!.pop();
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 16));
        expect(find.text('page'), findsNothing);
        expect(find.text('home'), findsOneWidget);
        await tester.pumpAndSettle();
      });
    }

    testWidgets('Mornye iOS back swipe still follows the finger', (
      tester,
    ) async {
      await _pumpApp(
        tester,
        mornye.copyWith(platform: TargetPlatform.iOS),
        reducedMotion: true,
      );
      _push('page');
      await tester.pumpAndSettle();

      final gesture = await tester.startGesture(const Offset(5, 300));
      await gesture.moveBy(const Offset(20, 0));
      await gesture.moveBy(const Offset(180, 0));
      await tester.pump();
      expect(
        tester.getTopLeft(find.byType(Scaffold).last).dx,
        greaterThan(100),
      );
      await gesture.moveBy(const Offset(300, 0));
      await gesture.up();
      await tester.pumpAndSettle();
      expect(find.text('page'), findsNothing);
    });
  });
}
