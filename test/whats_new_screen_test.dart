import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show MethodChannel;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mornye/app.dart';
import 'package:mornye/constants/app_info.dart';
import 'package:mornye/l10n/app_localizations.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/providers/settings_provider.dart';
import 'package:mornye/screens/whats_new_screen.dart';
import 'package:mornye/services/local_network_access.dart';
import 'package:mornye/theme/mornye_theme.dart';

class _Preferences implements SharedPreferences {
  final saved = <String, String>{};

  @override
  String? getString(String key) => saved[key];

  @override
  Future<bool> setString(String key, String value) async {
    saved[key] = value;
    return true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Settings extends SettingsNotifier {
  _Settings(this._initial, _Preferences preferences)
    : super(preferences: () async => preferences);

  final AppSettings _initial;

  @override
  AppSettings build() => _initial;

  @override
  Future<void> ensureLoaded() async {}
}

const _updated = AppSettings(isFirstLaunch: false, hasCompletedTutorial: true);

/// Opens the tour as Android 17 without Nearby devices; Android answers a
/// request with [allow].
Future<void> _openNearbyTour(WidgetTester tester, {required bool allow}) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  const channel = MethodChannel('com.zarz.mornye/local_network');
  final messenger = tester.binding.defaultBinaryMessenger;
  var granted = false;
  messenger.setMockMethodCallHandler(channel, (call) async {
    if (call.method == 'request') granted = allow;
    return granted;
  });
  addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
  final container = ProviderContainer(
    overrides: [
      settingsProvider.overrideWith(() => _Settings(_updated, _Preferences())),
    ],
  );
  addTearDown(container.dispose);
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: WhatsNewScreen(
          localNetworkAccess: LocalNetworkAccess(
            isAndroid: true,
            channel: channel,
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  test('only an update shows the tour, after setup and the tutorial', () {
    expect(
      initialLocationForAppState(
        isFirstLaunch: false,
        hasCompletedTutorial: true,
        hasSeenWhatsNew: false,
      ),
      '/whats-new',
    );
    expect(
      initialLocationForAppState(
        isFirstLaunch: false,
        hasCompletedTutorial: true,
      ),
      '/',
    );
    expect(
      initialLocationForAppState(
        isFirstLaunch: true,
        hasCompletedTutorial: false,
        hasSeenWhatsNew: false,
      ),
      '/setup',
    );
    expect(
      initialLocationForAppState(
        isFirstLaunch: false,
        hasCompletedTutorial: false,
        hasSeenWhatsNew: false,
      ),
      '/tutorial',
    );
    // Settings saved by an older build have no tour mark.
    final older = AppSettings.fromJson(
      Map<String, dynamic>.from(_updated.toJson())..remove('whatsNewSeen'),
    );
    expect(older.whatsNewSeen, isNot(AppInfo.whatsNewRelease));
  });

  test('finishing setup marks the current tour seen', () async {
    final preferences = _Preferences();
    final container = ProviderContainer(
      overrides: [
        settingsProvider.overrideWith(
          () => _Settings(const AppSettings(), preferences),
        ),
      ],
    );
    addTearDown(container.dispose);

    await container.read(settingsProvider.notifier).setFirstLaunchComplete();

    expect(
      container.read(settingsProvider).whatsNewSeen,
      AppInfo.whatsNewRelease,
    );
    final saved = jsonDecode(preferences.saved['app_settings']!) as Map;
    expect(saved['whatsNewSeen'], AppInfo.whatsNewRelease);
  });


  testWidgets('Skip stops at Nearby devices, which waits for an answer', (
    tester,
  ) async {
    await _openNearbyTour(tester, allow: false);
    final skip = find.text('Skip for now').hitTestable();

    await tester.tap(skip);
    await tester.pumpAndSettle();
    expect(find.text('Nearby devices'), findsOne);
    // No Skip, no Continue and no swiping on until Android answers.
    expect(skip, findsNothing);
    expect(find.text('Continue'), findsNothing);
    await tester.drag(find.byType(PageView), const Offset(-300, 0));
    await tester.pumpAndSettle();
    expect(find.text('Nearby devices'), findsOne);

    await tester.tap(find.text('Allow nearby devices'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Not allowed.'), findsOne);
    expect(skip, findsOne);
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
    expect(find.text('A Home that learns'), findsOne);
    expect(tester.takeException(), isNull);
  });

  testWidgets('allowing Nearby devices confirms it and moves on', (
    tester,
  ) async {
    await _openNearbyTour(tester, allow: true);
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Allow nearby devices'));
    await tester.pumpAndSettle();
    expect(find.text('Nearby devices allowed'), findsOne);
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
    expect(find.text('A Home that learns'), findsOne);
  });

  for (final mornye in [false, true]) {
    final style = mornye ? 'Mornye' : 'Material';

    Future<ProviderContainer> openTour(
      WidgetTester tester, {
      double textScale = 1,
    }) async {
      final container = ProviderContainer(
        overrides: [
          settingsProvider.overrideWith(
            () => _Settings(_updated, _Preferences()),
          ),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: mornye ? MornyeTheme.build(Brightness.light) : ThemeData(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(textScale)),
              child: child!,
            ),
            home: Builder(
              builder: (context) => TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const WhatsNewScreen(),
                  ),
                ),
                child: const Text('about'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('about'));
      await tester.pumpAndSettle();
      return container;
    }

    testWidgets('$style tour pages through to Done and closes', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final container = await openTour(tester);

      expect(find.text('Mornye ${AppInfo.whatsNewRelease} is here'), findsOne);
      expect(find.text('Skip for now'), findsOne);
      for (final title in [
        'A Home that learns',
        'Player and lyrics',
        'Library and search',
      ]) {
        await tester.tap(find.text('Continue'));
        await tester.pumpAndSettle();
        expect(find.text(title), findsWidgets);
      }
      // Back steps to the previous page before it leaves the tour.
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Player and lyrics'), findsWidgets);
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();
      expect(find.text('Faster everywhere'), findsWidgets);
      expect(find.text('Continue'), findsNothing);
      expect(container.read(settingsProvider).whatsNewSeen, isEmpty);

      await tester.tap(find.text('Start listening'));
      await tester.pumpAndSettle();
      expect(find.byType(WhatsNewScreen), findsNothing);
      expect(find.text('about'), findsOne);
      expect(
        container.read(settingsProvider).whatsNewSeen,
        AppInfo.whatsNewRelease,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('$style tour skips at once and fits a small phone', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(320, 568);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final container = await openTour(tester, textScale: 1.6);
      for (var page = 0; page < 4; page++) {
        expect(tester.takeException(), isNull);
        await tester.tap(find.text('Continue'));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Skip for now'));
      await tester.pumpAndSettle();
      expect(find.byType(WhatsNewScreen), findsNothing);
      expect(
        container.read(settingsProvider).whatsNewSeen,
        AppInfo.whatsNewRelease,
      );
    });

  }
}
