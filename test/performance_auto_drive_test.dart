import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart'
    show MaterialApp, MaterialPageRoute;
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/services/performance_auto_drive.dart';
import 'package:sistrum/services/performance_auto_measure.dart';
import 'package:sistrum/services/shell_navigation_service.dart';

Widget _list(ScrollController controller) => SizedBox(
  height: 400,
  child: ListView(
    controller: controller,
    children: List.generate(
      40,
      (i) => SizedBox(height: 100, child: Text('row $i')),
    ),
  ),
);

/// Runs [run] with frames pumped, so the pushed page is built and its
/// transition runs on the fake clock. The settle and transition waits are
/// fake timers, which only advance when pumped.
Future<AutoMeasureOutcome> _runPumped(
  WidgetTester tester,
  Future<AutoMeasureOutcome> Function() run,
) async {
  AutoMeasureOutcome? outcome;
  unawaited(run().then((o) => outcome = o));
  for (var i = 0; i < 400 && outcome == null; i++) {
    await tester.pump(const Duration(milliseconds: 16));
  }
  return outcome!;
}

void main() {
  testWidgets(
    'a route pushed over a list hides it: only the top route is measured',
    (tester) async {
      final key = GlobalKey<NavigatorState>();
      final rootController = ScrollController();
      final topController = ScrollController();
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: key,
          home: Directionality(
            textDirection: TextDirection.ltr,
            child: _list(rootController),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(primaryScrollPosition(key.currentState), rootController.position);

      key.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => Directionality(
            textDirection: TextDirection.ltr,
            child: _list(topController),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        primaryScrollPosition(key.currentState),
        topController.position,
        reason: 'the hidden root list is never chosen',
      );
    },
  );

  test(
    'the run has the five screens in order, and its cleanup is safe with nothing pushed',
    () {
      final driver = buildAutoMeasureSteps(
        loader: (_) async => const LibraryBrowsePage([]),
        noDataReason: 'no data',
        noScreenReason: 'no screen',
      );
      expect(driver.steps.map((s) => s.screen), [
        'Home',
        'Library',
        'Songs',
        'Artist',
        'Search',
      ]);
      expect(driver.cleanup, returnsNormally);
    },
  );

  test(
    'without a shell, the tab screens report that they cannot be reached',
    () async {
      final driver = buildAutoMeasureSteps(
        loader: (_) async => const LibraryBrowsePage([]),
        noDataReason: 'no data',
        noScreenReason: 'no screen',
      );
      // No tab handler is registered in a unit test, so the tabs cannot be shown.
      expect(await driver.steps[0].prepare(), 'no screen');
      expect(await driver.steps[1].prepare(), 'no screen');
    },
  );

  test(
    'an artist step with no artists reports no data, before any tab change',
    () async {
      final driver = buildAutoMeasureSteps(
        loader: (_) async => const LibraryBrowsePage([]),
        noDataReason: 'no data',
        noScreenReason: 'no screen',
      );
      expect(await driver.steps[3].prepare(), 'no data');
    },
  );

  testWidgets('cancel stops a running scroll where it is', (tester) async {
    final controller = ScrollController();
    await tester.pumpWidget(
      MaterialApp(
        home: Directionality(
          textDirection: TextDirection.ltr,
          child: _list(controller),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final position = controller.position;
    var cancelled = false;
    unawaited(autoMeasureScroll(position, isCancelled: () => cancelled));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(position.pixels, greaterThan(0));
    cancelled = true;
    await tester.pump(const Duration(milliseconds: 60));
    final stopped = position.pixels;
    await tester.pump(const Duration(seconds: 1));
    expect(
      position.pixels,
      stopped,
      reason: 'no further movement after cancel',
    );
  });

  testWidgets('mid-transition the page is found, never the screen behind it', (
    tester,
  ) async {
    final key = GlobalKey<NavigatorState>();
    final rootController = ScrollController();
    final topController = ScrollController();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: key,
        home: Directionality(
          textDirection: TextDirection.ltr,
          child: _list(rootController),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final route = MaterialPageRoute<void>(
      builder: (_) => Directionality(
        textDirection: TextDirection.ltr,
        child: _list(topController),
      ),
    );
    key.currentState!.push(route);
    // Mid-slide: the page is not opaque yet, so the list behind it still has
    // its tickers. The untargeted lookup finds that list, which is why the
    // driver always searches the pushed page.
    await tester.pump(const Duration(milliseconds: 250));
    expect(
      primaryScrollPosition(key.currentState),
      rootController.position,
      reason: 'the premise: the screen behind is still live mid-slide',
    );
    final mid = primaryScrollPosition(key.currentState, within: route);
    expect(mid, isNot(rootController.position));
    expect(
      mid == null || mid == topController.position,
      isTrue,
      reason: 'the page itself, or nothing yet',
    );
    // Once the page covers the screen, its own list is measured.
    await tester.pump(const Duration(milliseconds: 500));
    expect(
      primaryScrollPosition(key.currentState, within: route),
      topController.position,
    );
  });

  group('the pushed route is removed on cancel and on error', () {
    final owner = Object();
    late _PushObserver observer;
    setUp(() {
      observer = _PushObserver();
      ShellNavigationService.registerTabSelectionHandler(
        owner: owner,
        handler: (tab) => ShellNavigationService.syncState(tab: tab),
      );
    });
    tearDown(() {
      ShellNavigationService.unregisterTabSelectionHandler(owner);
    });

    Future<void> mountLibrary(WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: SizedBox(
            width: 300,
            height: 400,
            child: Navigator(
              key: ShellNavigationService.libraryTabNavigatorKey,
              observers: [observer],
              onGenerateRoute: (_) => MaterialPageRoute<void>(
                builder: (_) => Directionality(
                  textDirection: TextDirection.ltr,
                  child: _list(ScrollController()),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    AutoMeasureDriver driverWithSongs() => buildAutoMeasureSteps(
      loader: (_) async => const LibraryBrowsePage([]),
      noDataReason: 'no data',
      noScreenReason: 'no screen',
      songsBuilder: (_) => Directionality(
        textDirection: TextDirection.ltr,
        child: _list(ScrollController()),
      ),
    );

    testWidgets('cancel removes the page the run pushed', (tester) async {
      await mountLibrary(tester);
      final driver = driverWithSongs();
      var cancelled = false;
      final navigator = ShellNavigationService.libraryTabNavigatorKey;
      final outcome = await _runPumped(
        tester,
        () => runAutoMeasure(
          modes: const [MornyeGlassLevel.liquid],
          steps: [driver.steps[2]],
          setRenderMode: (_) {},
          measure: (screen, mode, drive) async {
            cancelled = true; // the user cancels while the page is up
            return null;
          },
          isCancelled: () => cancelled,
          noScrollReason: 'no scroll',
          tooFewFramesReason: 'too few',
        ),
      );
      driver.cleanup();
      await tester.pump();
      expect(outcome.cancelled, isTrue);
      expect(observer.pushed, isNotEmpty, reason: 'the page was pushed');
      expect(observer.removed, contains(observer.pushed.last));
      expect(navigator.currentState!.canPop(), isFalse);
    });

    testWidgets('an error removes the page the run pushed', (tester) async {
      await mountLibrary(tester);
      final driver = driverWithSongs();
      final navigator = ShellNavigationService.libraryTabNavigatorKey;
      final outcome = await _runPumped(
        tester,
        () => runAutoMeasure(
          modes: const [MornyeGlassLevel.liquid],
          steps: [driver.steps[2]],
          setRenderMode: (_) {},
          measure: (screen, mode, drive) async =>
              throw StateError('measure failed'),
          isCancelled: () => false,
          noScrollReason: 'no scroll',
          tooFewFramesReason: 'too few',
        ),
      );
      driver.cleanup();
      await tester.pump();
      expect(outcome.error, contains('measure failed'));
      expect(observer.removed, contains(observer.pushed.last));
      expect(navigator.currentState!.canPop(), isFalse);
    });
  });
}

/// Records the routes an observer sees pushed and removed.
class _PushObserver extends NavigatorObserver {
  final pushed = <Route<dynamic>>[];
  final removed = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      pushed.add(route);

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      removed.add(route);
}
