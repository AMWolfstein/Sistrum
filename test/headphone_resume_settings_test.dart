import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/playback_settings_page.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';

const _title = 'Play when headphones or a car connect';

Future<ProviderContainer> _pumpPage(
  WidgetTester tester, {
  AppSettings settings = const AppSettings(),
  String locale = 'en',
}) async {
  SharedPreferences.setMockInitialValues({});
  // A phone width, so the three choices must share one row.
  tester.view.physicalSize = const Size(360, 800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final container = ProviderContainer(
    overrides: [initialSettingsProvider.overrideWithValue(settings)],
  );
  addTearDown(container.dispose);
  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark, accent: MornyeAccent.blue),
        locale: Locale(locale),
        localizationsDelegates: appLocalizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const PlaybackSettingsPage(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return container;
}

Future<void> _scrollToChoice(WidgetTester tester) async {
  await tester.scrollUntilVisible(
    find.text(_title),
    200,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.ensureVisible(_option('Always'));
  await tester.pumpAndSettle();
}

/// The connected button group lays a label out more than once; any copy
/// sits on the same segment.
Finder _option(String label) => find.text(label).first;

Future<Set<String>> _searchTitles(WidgetTester tester) async {
  await tester.pumpWidget(
    MaterialApp(
      locale: const Locale('en'),
      localizationsDelegates: appLocalizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const Scaffold(),
    ),
  );
  final l10n = AppLocalizations.of(tester.element(find.byType(Scaffold)));
  return {
    for (final entry in SettingsSearchCatalog(l10n).playback) entry.title,
  };
}

void main() {
  tearDown(() {
    debugAndroidTvOverride = null;
    debugDesktopPlatformOverride = null;
  });

  testWidgets('one three-way choice, off by default', (tester) async {
    final container = await _pumpPage(tester);
    await _scrollToChoice(tester);
    expect(find.byType(MornyeSegmentedControl), findsOneWidget);
    expect(find.text(_title), findsOneWidget);
    expect(
      find.text('Connecting headphones or a car does not start playback.'),
      findsOneWidget,
    );
    expect(find.text('Resume when headphones reconnect'), findsNothing);

    await tester.tap(_option('Reconnect'));
    await tester.pumpAndSettle();
    expect(
      container.read(settingsProvider).headphoneResume,
      'after_disconnect',
    );
    expect(find.textContaining('within 30 minutes'), findsOneWidget);

    await tester.tap(_option('Always'));
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).headphoneResume, 'always');
    expect(find.textContaining('Disconnecting pauses'), findsOneWidget);
    final prefs = await SharedPreferences.getInstance();
    final saved = jsonDecode(prefs.getString('app_settings')!) as Map;
    expect(saved['headphoneResume'], 'always');
    expect(saved['playOnHeadphonesConnected'], isTrue);

    await tester.tap(_option('Off'));
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).headphoneResume, 'off');
    expect(tester.takeException(), isNull);
  });

  testWidgets('Indonesian shows its own choices', (tester) async {
    await _pumpPage(tester, locale: 'id');
    // By its title: the page holds other segmented controls above it.
    await tester.scrollUntilVisible(
      find.text('Putar saat earphone atau mobil terhubung'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    for (final label in ['Mati', 'Sambung lagi', 'Selalu']) {
      expect(find.text(label), findsWidgets, reason: label);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('a saved old switch shows as Always', (tester) async {
    await _pumpPage(
      tester,
      settings: AppSettings.fromJson(const {'playOnHeadphonesConnected': true}),
    );
    await _scrollToChoice(tester);
    expect(find.textContaining('Disconnecting pauses'), findsOneWidget);
  });

  for (final (name, backup, expected) in [
    (
      'a new backup',
      const AppSettings(headphoneResume: 'after_disconnect').toJson(),
      'after_disconnect',
    ),
    (
      'an old backup',
      <String, dynamic>{'playOnHeadphonesConnected': true},
      'always',
    ),
  ]) {
    testWidgets('restoring $name keeps the choice', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final container = ProviderContainer(
        overrides: [
          initialSettingsProvider.overrideWithValue(const AppSettings()),
        ],
      );
      addTearDown(container.dispose);
      // A backup archive stores the settings as JSON text.
      final json = jsonDecode(jsonEncode(backup)) as Map<String, dynamic>;
      await tester.runAsync(
        () => container.read(settingsProvider.notifier).restoreFromBackup(json),
      );
      expect(container.read(settingsProvider).headphoneResume, expected);
      final prefs = await SharedPreferences.getInstance();
      final saved = jsonDecode(prefs.getString('app_settings')!) as Map;
      expect(saved['headphoneResume'], expected);
    });
  }

  testWidgets('Android TV shows and saves the choice like a phone', (
    tester,
  ) async {
    debugAndroidTvOverride = true;
    final container = await _pumpPage(
      tester,
      settings: AppSettings.fromJson(const {'playOnHeadphonesConnected': true}),
    );
    await _scrollToChoice(tester);
    expect(find.text(_title), findsOneWidget);
    expect(find.textContaining('Disconnecting pauses'), findsOneWidget);
    await tester.tap(_option('Reconnect'));
    await tester.pumpAndSettle();
    expect(
      container.read(settingsProvider).headphoneResume,
      'after_disconnect',
    );
    expect(await _searchTitles(tester), contains(_title));
  });

  testWidgets('desktop hides it', (tester) async {
    debugDesktopPlatformOverride = true;
    await _pumpPage(tester);
    // Walk the whole page; rows below the fold are built lazily.
    for (var page = 0; page < 30; page++) {
      expect(find.text(_title), findsNothing);
      expect(find.text('Reconnect'), findsNothing);
      await tester.drag(find.byType(Scrollable).first, const Offset(0, -600));
      await tester.pumpAndSettle();
    }
    expect(await _searchTitles(tester), isNot(contains(_title)));
  });

  testWidgets('settings search finds it on a phone', (tester) async {
    final titles = await _searchTitles(tester);
    expect(titles, contains(_title));
    expect(titles, isNot(contains('Resume when headphones reconnect')));
  });
}
