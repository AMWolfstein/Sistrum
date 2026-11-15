import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/playback_settings_page.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/settings_group.dart';

void main() {
  testWidgets('automatic controls persist in both themes', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final container = ProviderContainer(
      overrides: [
        initialSettingsProvider.overrideWithValue(
          const AppSettings(pauseOnMute: false),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark, accent: MornyeAccent.blue),
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PlaybackSettingsPage(),
        ),
      ),
    );
    final muted = find.text('Pause when muted');
    final connected = find.text('Play when headphones connect');
    await tester.scrollUntilVisible(
      muted,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    await tester.tap(muted);
    await tester.pumpAndSettle();
    await tester.ensureVisible(connected);
    await tester.pumpAndSettle();
    await tester.tap(connected);
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).pauseOnMute, isTrue);
    expect(container.read(settingsProvider).playOnHeadphonesConnected, isTrue);
    final prefs = await SharedPreferences.getInstance();
    final saved = AppSettings.fromJson(
      jsonDecode(prefs.getString('app_settings')!) as Map<String, dynamic>,
    );
    expect(saved.pauseOnMute, isTrue);
    expect(saved.playOnHeadphonesConnected, isTrue);

    container.read(settingsProvider.notifier).setPlayerMode('external');
    await tester.pump();
    final automationControls = tester.widgetList<SettingsSwitchItem>(
      find.byWidgetPredicate(
        (widget) =>
            widget is SettingsSwitchItem &&
            const [
              'Pause when muted',
              'Play when headphones connect',
            ].contains(widget.title),
      ),
    );
    expect(automationControls, hasLength(2));
    expect(automationControls.every((widget) => !widget.enabled), isTrue);
    expect(tester.takeException(), isNull);

    final l10n = AppLocalizations.of(tester.element(connected));
    final catalog = SettingsSearchCatalog(l10n);
    expect(
      catalog.playback.any(
        (entry) => entry.matches(SettingsSearchQuery('mute')),
      ),
      isTrue,
    );
    expect(
      catalog.playback.any(
        (entry) => entry.matches(SettingsSearchQuery('earphone')),
      ),
      isTrue,
    );
  });

  testWidgets('Gapless playback yields to AutoMix', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final container = ProviderContainer(
      overrides: [
        initialSettingsProvider.overrideWithValue(const AppSettings()),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.dark, accent: MornyeAccent.blue),
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PlaybackSettingsPage(),
        ),
      ),
    );
    final gapless = find.text('Gapless playback');
    await tester.scrollUntilVisible(
      gapless,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    SettingsSwitchItem item() => tester.widget<SettingsSwitchItem>(
      find.byWidgetPredicate(
        (widget) =>
            widget is SettingsSwitchItem && widget.title == 'Gapless playback',
      ),
    );
    expect(item().value, isTrue);
    await tester.tap(gapless);
    await tester.pumpAndSettle();
    expect(container.read(settingsProvider).gaplessPlayback, isFalse);

    container.read(settingsProvider.notifier).setGaplessPlayback(true);
    container.read(settingsProvider.notifier).setAutoMix(true);
    await tester.pumpAndSettle();
    expect(item().enabled, isFalse);
    expect(item().value, isFalse);
    expect(tester.takeException(), isNull);

    final catalog = SettingsSearchCatalog(
      AppLocalizations.of(tester.element(gapless)),
    );
    expect(
      catalog.playback.any(
        (entry) => entry.matches(SettingsSearchQuery('gapless')),
      ),
      isTrue,
    );
  });
}
