import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/performance_hub_provider.dart';
import 'package:sistrum/services/performance_auto_drive.dart';
import 'package:sistrum/services/performance_auto_measure.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/theme_provider.dart';
import 'package:sistrum/screens/settings/performance_hub_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_action_button.dart';
import 'package:sistrum/widgets/performance_recording_badge.dart';

/// Keeps the selected glass mode in memory and records every write to it.
class _Theme extends ThemeNotifier {
  _Theme(this.calls, this.initial)
    : super(preferences: Completer<SharedPreferences>().future);

  final List<MornyeGlassLevel> calls;
  final MornyeGlassLevel initial;

  @override
  ThemeSettings build() => ThemeSettings(mornyeGlassLevel: initial);

  @override
  Future<void> setMornyeGlassLevel(MornyeGlassLevel value) async {
    calls.add(value);
    state = state.copyWith(mornyeGlassLevel: value);
  }
}

ProviderScope _scope({
  required Widget child,
  required List<MornyeGlassLevel> calls,
  MornyeGlassLevel mode = MornyeGlassLevel.liquid,
  int minFrames = 0,
  List<Override> extra = const [],
}) => ProviderScope(
  overrides: [
    ...extra,
    themeProvider.overrideWith(() => _Theme(calls, mode)),
    performanceCompareWindowProvider.overrideWithValue(
      const Duration(milliseconds: 20),
    ),
    // Test windows contain few frames, so the floor is off unless a test
    // checks it.
    performanceMinFramesProvider.overrideWithValue(minFrames),
    deviceSupportsLiquidGlassProvider.overrideWithValue(true),
    deviceSupportsBackdropBlurProvider.overrideWithValue(true),
  ],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: child,
  ),
);

Future<void> _pumpHub(
  WidgetTester tester, {
  required List<MornyeGlassLevel> calls,
  MornyeGlassLevel mode = MornyeGlassLevel.liquid,
  int minFrames = 0,
  List<Override> extra = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  // Tall enough that every section is built; slivers build only the visible part.
  tester.view.physicalSize = const Size(800, 4000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    _scope(
      calls: calls,
      mode: mode,
      minFrames: minFrames,
      extra: extra,
      child: const MornyeSettingsTheme(child: PerformanceHubPage()),
    ),
  );
  await tester.pump();
}

ProviderContainer _container(WidgetTester tester) =>
    ProviderScope.containerOf(tester.element(find.byType(MaterialApp)));

Finder _compareButton() =>
    find.widgetWithText(AppActionButton, 'Compare modes');

Finder _cancelButton() => find.widgetWithText(AppActionButton, 'Cancel');

const _tooFewFrames =
    'Too few frames were recorded: keep scrolling or interacting while recording';
const _modeChanged =
    'The glass mode was changed during this recording. The result shows the mode it started in.';

/// A comparison that fails before it can measure anything.
class _ThrowingHub extends PerformanceHubNotifier {
  @override
  Future<void> compareModes() => Future.error(StateError('compare failed'));
}

void main() {
  testWidgets('the hub shows its controls and marks what it cannot measure', (
    tester,
  ) async {
    await _pumpHub(tester, calls: []);

    expect(find.text('Performance hub'), findsWidgets);
    expect(find.text('Start recording'), findsOneWidget);
    expect(_compareButton(), findsOneWidget);
    expect(
      find.text('No results yet. Record a session or compare the modes.'),
      findsOneWidget,
    );
    expect(find.text('Copy as text'), findsOneWidget);
    expect(find.textContaining('profile mode'), findsOneWidget);
  });

  testWidgets(
    'compare modes renders Liquid and None in memory and never writes the saved mode',
    (tester) async {
      final calls = <MornyeGlassLevel>[];
      await _pumpHub(tester, calls: calls);

      await tester.tap(_compareButton());
      await tester.pump();
      // Mid-step, rendering follows the compared mode; the saved one does not.
      final container = _container(tester);
      expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.liquid);
      expect(container.read(mornyeGlassModeProvider), MornyeGlassLevel.liquid);
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 25));
      }

      expect(calls, isEmpty, reason: 'a comparison never writes the selection');
      expect(container.read(mornyeGlassModeProvider), MornyeGlassLevel.liquid);
      expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.liquid);
      final hub = container.read(performanceHubProvider);
      expect(hub.live, isNull);
      expect(hub.runs.single.compare, isTrue);
      expect(hub.runs.single.sessions.map((s) => s.glassMode), [
        MornyeGlassLevel.liquid,
        MornyeGlassLevel.flat,
      ]);
      expect(find.text('Liquid Glass'), findsWidgets);
      expect(find.text('None'), findsWidgets);
      expect(find.text('Not available'), findsWidgets);
    },
  );

  testWidgets('cancelling a comparison writes nothing and saves nothing', (
    tester,
  ) async {
    final calls = <MornyeGlassLevel>[];
    await _pumpHub(tester, calls: calls);

    await tester.tap(_compareButton());
    await tester.pump(const Duration(milliseconds: 5));
    await tester.tap(_cancelButton());
    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 25));
    }

    expect(calls, isEmpty);
    final container = _container(tester);
    expect(container.read(mornyeGlassModeProvider), MornyeGlassLevel.liquid);
    expect(container.read(mornyeGlassLevelProvider), MornyeGlassLevel.liquid);
    expect(container.read(performanceHubProvider).live, isNull);
    expect(container.read(performanceHubProvider).runs, isEmpty);
  });

  testWidgets('a second comparison cannot start while one runs', (
    tester,
  ) async {
    await _pumpHub(tester, calls: []);
    final container = _container(tester);
    final notifier = container.read(performanceHubProvider.notifier);

    unawaited(notifier.compareModes());
    unawaited(notifier.compareModes());
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 25));
    }

    expect(container.read(performanceHubProvider).runs.single.compare, isTrue);
    expect(container.read(performanceHubProvider).runs, hasLength(1));
  });

  testWidgets('backgrounding the app cancels a comparison and saves nothing', (
    tester,
  ) async {
    await _pumpHub(tester, calls: []);
    final container = _container(tester);

    await tester.tap(_compareButton());
    await tester.pump(const Duration(milliseconds: 5));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 25));
    }

    expect(container.read(performanceHubProvider).live, isNull);
    expect(container.read(performanceHubProvider).runs, isEmpty);
    expect(container.read(mornyeGlassCompareModeProvider), isNull);
  });

  testWidgets('a window below the frame floor is discarded, not saved', (
    tester,
  ) async {
    await _pumpHub(tester, calls: [], minFrames: 1000000);

    await tester.tap(_compareButton());
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 25));
    }

    expect(_container(tester).read(performanceHubProvider).runs, isEmpty);
  });

  testWidgets('the recording badge stays on screen until the recording stops', (
    tester,
  ) async {
    final calls = <MornyeGlassLevel>[];
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(
      _scope(
        calls: calls,
        child: const Stack(
          fit: StackFit.expand,
          children: [SizedBox.expand(), PerformanceRecordingBadge()],
        ),
      ),
    );
    final container = ProviderScope.containerOf(
      tester.element(find.byType(PerformanceRecordingBadge)),
    );

    container.read(performanceHubProvider.notifier).startRecording();
    await tester.pump();
    expect(find.textContaining('Recording 00:00'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.stop_rounded));
    await tester.pump();
    expect(container.read(performanceHubProvider).live, isNull);
    expect(container.read(performanceHubProvider).runs.single.compare, isFalse);
    expect(find.textContaining('Recording'), findsNothing);
  });

  testWidgets('the hub is not built until a recording starts', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(
      _scope(
        calls: [],
        child: const Stack(
          fit: StackFit.expand,
          children: [SizedBox.expand(), PerformanceRecordingBadge()],
        ),
      ),
    );
    final container = ProviderScope.containerOf(
      tester.element(find.byType(PerformanceRecordingBadge)),
    );
    expect(container.read(performanceHubProvider.exists), isFalse);

    container.read(performanceHubProvider.notifier).startRecording();
    await tester.pump();
    expect(container.read(performanceHubProvider.exists), isTrue);
  });

  testWidgets('a comparison can be cancelled from the page while it runs', (
    tester,
  ) async {
    await _pumpHub(tester, calls: []);

    await tester.tap(_compareButton());
    await tester.pump(const Duration(milliseconds: 5));
    expect(_cancelButton(), findsOneWidget);
    await tester.tap(_cancelButton());
    await tester.pump(const Duration(milliseconds: 25));

    expect(_container(tester).read(performanceHubProvider).live, isNull);
  });

  testWidgets(
    'a recording below the frame floor says so until the next one starts',
    (tester) async {
      await _pumpHub(tester, calls: [], minFrames: 1000000);
      final start = find.widgetWithText(AppActionButton, 'Start recording');

      await tester.tap(start);
      await tester.pump();
      await tester.tap(find.byIcon(Icons.stop_rounded));
      await tester.pump();
      expect(find.text(_tooFewFrames), findsOneWidget);

      await tester.tap(start);
      await tester.pump();
      expect(find.text(_tooFewFrames), findsNothing);
      await tester.tap(find.byIcon(Icons.stop_rounded));
      await tester.pump();
    },
  );

  testWidgets('a comparison below the frame floor says so', (tester) async {
    await _pumpHub(tester, calls: [], minFrames: 1000000);

    await tester.tap(_compareButton());
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 25));
    }

    expect(find.text(_tooFewFrames), findsOneWidget);
  });

  testWidgets('a mode change during a recording marks the result', (
    tester,
  ) async {
    await _pumpHub(tester, calls: [], mode: MornyeGlassLevel.liquid);

    await tester.tap(find.widgetWithText(AppActionButton, 'Start recording'));
    await tester.pump();
    _container(
      tester,
    ).read(themeProvider.notifier).setMornyeGlassLevel(MornyeGlassLevel.flat);
    await tester.pump();
    await tester.tap(find.byIcon(Icons.stop_rounded));
    await tester.pump();

    expect(find.text(_modeChanged), findsOneWidget);
  });

  testWidgets('a recording with no mode change is not marked', (tester) async {
    await _pumpHub(tester, calls: [], mode: MornyeGlassLevel.liquid);

    await tester.tap(find.widgetWithText(AppActionButton, 'Start recording'));
    await tester.pump();
    await tester.tap(find.byIcon(Icons.stop_rounded));
    await tester.pump();

    expect(find.text(_modeChanged), findsNothing);
  });

  testWidgets('a comparison that throws does not leak to the zone', (
    tester,
  ) async {
    await _pumpHub(
      tester,
      calls: [],
      extra: [performanceHubProvider.overrideWith(_ThrowingHub.new)],
    );

    await tester.tap(_compareButton());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 25));

    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'automatic measurement without an app shell skips the screens and restores the mode',
    (tester) async {
      final calls = <MornyeGlassLevel>[];
      await _pumpHub(tester, calls: calls);
      await tester.tap(
        find.widgetWithText(AppActionButton, 'Run automatic measurement'),
      );
      await tester.pump();
      // The run settles on real time, so let it finish on the fake clock too.
      for (var i = 0; i < 60; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 20)),
        );
        await tester.pump(const Duration(milliseconds: 20));
      }
      final container = _container(tester);
      expect(container.read(mornyeGlassCompareModeProvider), isNull);
      expect(container.read(performanceRecordingActiveProvider), isFalse);
      expect(container.read(performanceHubProvider).runs, isEmpty);
      expect(calls, isEmpty, reason: 'the saved mode is never written');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'a cancelled automatic run is not saved, though a Liquid row was measured',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final controller = ScrollController();
      await tester.pumpWidget(
        _scope(
          calls: [],
          child: SizedBox(
            height: 400,
            child: ListView(
              controller: controller,
              children: List.generate(
                40,
                (i) => SizedBox(height: 100, child: Text('row $i')),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      final container = _container(tester);
      final notifier = container.read(performanceHubProvider.notifier);
      var prepares = 0;
      final driver = AutoMeasureDriver(
        steps: [
          AutoMeasureStep(
            screen: 'Home',
            prepare: () async {
              prepares++;
              // The second prepare is the None mode: cancel before it runs.
              if (prepares == 2) notifier.cancelComparison();
              return null;
            },
            position: () => controller.position,
          ),
        ],
        cleanup: () {},
      );
      var finished = false;
      unawaited(
        notifier
            .runAutomaticMeasurement(
              driver: driver,
              noScrollReason: 'no scroll',
              tooFewFramesReason: 'too few frames',
            )
            .then((_) => finished = true),
      );
      for (var i = 0; i < 400 && !finished; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 10)),
        );
        await tester.pump(const Duration(milliseconds: 16));
      }
      expect(finished, isTrue);
      expect(prepares, 2, reason: 'the run stopped before None measured');
      expect(
        container.read(performanceHubProvider).runs,
        isEmpty,
        reason: 'a cancelled run is not saved, with or without rows',
      );
      expect(container.read(mornyeGlassCompareModeProvider), isNull);
    },
  );
}
