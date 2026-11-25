import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/playback_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/widgets/player_volume_control.dart';

import 'support/fixed_settings.dart';

const _dialogTitle = 'Turn the volume up?';
const _switchTitle = 'Confirm loud volume';

/// The volume bar over a fake system volume that echoes every write, like
/// the platform does.
class _Bar {
  _Bar(this.tester);

  final WidgetTester tester;
  final writes = <double>[];
  final volumes = StreamController<double>.broadcast();

  Future<void> pump({bool enabled = true, double volume = 0.5}) async {
    addTearDown(volumes.close);
    tester.view.physicalSize = const Size(400, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.flat),
          settingsProvider.overrideWith(
            () => FixedSettingsNotifier(
              AppSettings(confirmLoudVolume: enabled, loudVolumeLevel: 80),
            ),
          ),
          systemVolumeProvider.overrideWith((ref) => volumes.stream),
          systemVolumeWriterProvider.overrideWith(
            (ref) => (value) async {
              writes.add(value);
              volumes.add(value);
            },
          ),
        ],
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: appLocalizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: Center(child: PlayerVolumeControl())),
        ),
      ),
    );
    await tester.pump();
    volumes.add(volume);
    await tester.pumpAndSettle();
  }

  Finder get _track => find.byType(Slider);

  double get value => tester.widget<Slider>(find.byType(Slider)).value;

  /// A point on the track at [fraction] of its width.
  Offset at(double fraction) {
    final rect = tester.getRect(_track);
    return Offset(rect.left + rect.width * fraction, rect.center.dy);
  }

  Future<TestGesture> press(double fraction) async {
    final finger = await tester.startGesture(at(fraction));
    await tester.pump();
    return finger;
  }

  Future<void> moveTo(TestGesture finger, double fraction) async {
    await finger.moveTo(at(fraction));
    await tester.pump();
  }

  /// Small steps, so short drags still pass the touch slop before the end.
  Future<void> drag(double from, double to) async {
    final finger = await press(from);
    for (var step = 1; step <= 6; step++) {
      await moveTo(finger, from + (to - from) * step / 6);
    }
    await finger.up();
    await tester.pumpAndSettle();
  }

  Future<void> answer(String label) async {
    await tester.tap(find.text(label));
    await tester.pumpAndSettle();
  }
}

void main() {
  testWidgets('a long drag past the level asks once', (tester) async {
    final bar = _Bar(tester);
    await bar.pump();
    final finger = await bar.press(0.4);
    await bar.moveTo(finger, 0.6);
    await bar.moveTo(finger, 0.97);
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(
      find.text(
        'Volume above 80% may hurt your hearing. Do you want to continue?',
      ),
      findsOneWidget,
    );
    expect(bar.writes.last, closeTo(0.8, 1e-9));
    expect(bar.value, closeTo(0.8, 1e-9));

    // The finger keeps going: one dialog, and the bar stays at the level.
    for (final fraction in [0.99, 0.9, 1.0, 0.95]) {
      await bar.moveTo(finger, fraction);
    }
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(bar.value, closeTo(0.8, 1e-9));
    expect(bar.writes.where((value) => value > 0.8 + 1e-9), isEmpty);

    await bar.answer('Cancel');
    expect(find.text(_dialogTitle), findsNothing);
    await bar.moveTo(finger, 1.0);
    await finger.up();
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, closeTo(0.8, 1e-9));
    expect(bar.writes.where((value) => value > 0.8 + 1e-9), isEmpty);
    expect(tester.takeException(), isNull);
  });

  // Opening the dialog cancels the touch, so the test above cannot show
  // what happens if a slider keeps reporting; drive its callbacks instead.
  testWidgets('reports while asking and after a cancel hold', (tester) async {
    final bar = _Bar(tester);
    await bar.pump();
    void changed(double value) =>
        tester.widget<Slider>(find.byType(Slider)).onChanged!(value);
    void ended(double value) =>
        tester.widget<Slider>(find.byType(Slider)).onChangeEnd!(value);

    tester.widget<Slider>(find.byType(Slider)).onChangeStart!(0.5);
    changed(0.9);
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    for (final value in [1.0, 0.95, 0.85]) {
      changed(value);
      await tester.pump();
    }
    // A stale end report (the old value) neither drops the bar below the
    // level nor replaces where the finger went.
    ended(0.5);
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(bar.value, closeTo(0.8, 1e-9));
    expect(bar.writes.where((value) => value > 0.8 + 1e-9), isEmpty);

    // Cancel: the same touch stays at the level and does not ask again.
    await bar.answer('Cancel');
    changed(1.0);
    ended(1.0);
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, closeTo(0.8, 1e-9));
    expect(bar.writes.where((value) => value > 0.8 + 1e-9), isEmpty);

    // A new touch asks again; Continue applies the finger's last value.
    tester.widget<Slider>(find.byType(Slider)).onChangeStart!(0.8);
    await tester.pumpAndSettle();
    expect(bar.value, closeTo(0.8, 1e-9));
    changed(0.9);
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    changed(0.95);
    await bar.answer('Continue');
    expect(bar.value, closeTo(0.95, 1e-9));
    expect(bar.writes.last, closeTo(0.95, 1e-9));
    expect(tester.takeException(), isNull);
  });

  testWidgets('confirm passes, then re-arms below the level', (tester) async {
    final bar = _Bar(tester);
    await bar.pump();
    await bar.drag(0.4, 0.97);
    expect(find.text(_dialogTitle), findsOneWidget);
    await bar.answer('Continue');
    expect(find.text(_dialogTitle), findsNothing);
    // The dialog takes the touch, so the volume goes to where the finger
    // was when it crossed.
    final loud = bar.value;
    expect(loud, greaterThan(0.8 + 1e-9));
    expect(bar.writes.last, loud);

    // Above the level the bar moves without asking.
    await bar.drag(loud, 0.85);
    await bar.drag(0.85, 1.0);
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, greaterThan(0.95));

    // Back down, then up again: the next crossing asks.
    await bar.drag(0.97, 0.3);
    expect(bar.value, lessThan(0.8));
    expect(find.text(_dialogTitle), findsNothing);
    await bar.drag(0.3, 0.97);
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(bar.value, closeTo(0.8, 1e-9));
    await bar.answer('Cancel');
    expect(bar.value, closeTo(0.8, 1e-9));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a tap past the level asks and stops at it', (tester) async {
    final bar = _Bar(tester);
    await bar.pump();
    await tester.tapAt(bar.at(0.97));
    await tester.pumpAndSettle();
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(bar.value, closeTo(0.8, 1e-9));
    await bar.answer('Continue');
    expect(bar.value, greaterThan(0.9));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a loud volume already set does not ask', (tester) async {
    final bar = _Bar(tester);
    await bar.pump(volume: 0.95);
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, 0.95);
    // Within the loud range, down and up again, the bar moves freely.
    await bar.drag(0.88, 1.0);
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, greaterThan(0.95));
    expect(bar.writes, everyElement(greaterThan(0.8)));
    // Volume buttons bring it below the level; the next crossing asks.
    bar.volumes.add(0.5);
    await tester.pumpAndSettle();
    expect(bar.value, 0.5);
    await bar.drag(0.5, 0.97);
    expect(find.text(_dialogTitle), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('with the setting off nothing changes', (tester) async {
    final bar = _Bar(tester);
    await bar.pump(enabled: false);
    await bar.drag(0.4, 1.0);
    expect(find.text(_dialogTitle), findsNothing);
    expect(bar.value, greaterThan(0.95));
    expect(bar.writes.last, bar.value);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the switch shows the level choice', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final container = ProviderContainer(
      overrides: [
        mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.flat),
        initialSettingsProvider.overrideWithValue(
          const AppSettings(confirmLoudVolume: false),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: appLocalizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PlaybackSettingsPage(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text(_switchTitle),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).confirmLoudVolume, isFalse);
    expect(find.text('Ask above'), findsNothing);

    await tester.tap(find.text(_switchTitle));
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).confirmLoudVolume, isTrue);
    final choice = find.byType(MornyeSegmentedControl);
    await tester.ensureVisible(choice);
    await tester.pumpAndSettle();
    expect(choice, findsOneWidget);
    expect(find.text('Ask above'), findsOneWidget);
    for (final label in ['60%', '70%', '80%', '90%']) {
      expect(find.text(label), findsWidgets, reason: label);
    }
    expect(container.read(settingsProvider).loudVolumeLevel, 80);
    await tester.tap(find.text('90%').first);
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).loudVolumeLevel, 90);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the switch shows the level choice in Liquid', (tester) async {
    MornyeGlass.debugShaderFilterSupport = true;
    addTearDown(() => MornyeGlass.debugShaderFilterSupport = null);
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final container = ProviderContainer(
      overrides: [
        mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.liquid),
        initialSettingsProvider.overrideWithValue(
          const AppSettings(confirmLoudVolume: false),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: appLocalizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PlaybackSettingsPage(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text(_switchTitle),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).confirmLoudVolume, isFalse);
    expect(find.text('Ask above'), findsNothing);

    await tester.tap(find.text(_switchTitle));
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).confirmLoudVolume, isTrue);
    final choice = find.byType(MornyeSegmentedControl);
    await tester.ensureVisible(choice);
    await tester.pumpAndSettle();
    expect(choice, findsOneWidget);
    expect(find.text('Ask above'), findsOneWidget);
    for (final label in ['60%', '70%', '80%', '90%']) {
      expect(find.text(label), findsWidgets, reason: label);
    }
    expect(container.read(settingsProvider).loudVolumeLevel, 80);
    await tester.tap(find.text('90%').first);
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).loudVolumeLevel, 90);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Settings search finds the switch', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('id'),
        localizationsDelegates: appLocalizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(),
      ),
    );
    final l10n = AppLocalizations.of(tester.element(find.byType(Scaffold)));
    final entry = SettingsSearchCatalog(
      l10n,
    ).playback.singleWhere((entry) => entry.title == l10n.confirmLoudVolume);
    expect(entry.title, 'Konfirmasi volume keras');
    expect(entry.matches(SettingsSearchQuery('limiter')), isTrue);
    expect(entry.matches(SettingsSearchQuery('volume keras')), isTrue);
  });
}
