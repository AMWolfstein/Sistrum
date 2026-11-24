import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/settings/playback_settings_page.dart';
import 'package:sistrum/services/playback_deck.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/widgets/settings_group.dart';

Future<ProviderContainer> _pumpPage(
  WidgetTester tester, {
  AppSettings settings = const AppSettings(autoMix: true),
}) async {
  SharedPreferences.setMockInitialValues({});
  tester.view.physicalSize = const Size(1200, 1600);
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
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const PlaybackSettingsPage(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return container;
}

SettingsSwitchItem _switch(WidgetTester tester, String title) =>
    tester.widget<SettingsSwitchItem>(
      find.byWidgetPredicate(
        (widget) => widget is SettingsSwitchItem && widget.title == title,
      ),
    );

Future<void> _scrollTo(WidgetTester tester, Finder finder, double delta) async {
  await tester.scrollUntilVisible(
    finder,
    delta,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => debugDesktopPlatformOverride = true);
  tearDown(() {
    debugDesktopPlatformOverride = null;
    playbackExclusiveStatus.value = ExclusiveOutputStatus.pending;
  });

  testWidgets(
    'Windows offers WASAPI exclusive mode',
    variant: TargetPlatformVariant.only(TargetPlatform.windows),
    (tester) async {
      final container = await _pumpPage(tester);
      expect(_switch(tester, 'AutoMix').enabled, isTrue);
      const title = 'WASAPI exclusive mode';
      await _scrollTo(tester, find.text(title), 300);
      await tester.tap(find.text(title));
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).exclusiveOutput, isTrue);
      expect(
        find.textContaining('Takes the device when a track plays.'),
        findsOneWidget,
      );
      expect(find.text('Output device'), findsNothing);

      playbackExclusiveStatus.value = ExclusiveOutputStatus.unavailable;
      await tester.pump();
      expect(
        find.textContaining('The device refused exclusive access'),
        findsOneWidget,
      );

      // AutoMix needs a second deck, which the held device refuses.
      await _scrollTo(tester, find.text('AutoMix'), -300);
      expect(_switch(tester, 'AutoMix').enabled, isFalse);
      expect(_switch(tester, 'AutoMix').value, isFalse);
      expect(_switch(tester, 'Gapless playback').enabled, isTrue);
      expect(_switch(tester, 'Gapless playback').value, isTrue);
      expect(container.read(settingsProvider).autoMix, isTrue);
      _switch(tester, 'Gapless playback').onChanged!(false);
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).gaplessPlayback, isFalse);
      _switch(tester, 'Gapless playback').onChanged!(true);
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).gaplessPlayback, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'macOS offers hog mode with its status',
    variant: TargetPlatformVariant.only(TargetPlatform.macOS),
    (tester) async {
      await _pumpPage(
        tester,
        settings: const AppSettings(exclusiveOutput: true),
      );
      const title = 'Exclusive access (hog mode)';
      await _scrollTo(tester, find.text(title), 300);
      expect(find.textContaining('Core Audio does not resample'), findsOne);

      playbackExclusiveStatus.value = ExclusiveOutputStatus.active;
      await tester.pump();
      expect(find.textContaining('Playing in exclusive mode.'), findsOne);

      playbackExclusiveStatus.value = ExclusiveOutputStatus.unavailable;
      await tester.pump();
      expect(find.textContaining('Another app holds the output'), findsOne);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Linux offers direct ALSA output with a device choice',
    variant: TargetPlatformVariant.only(TargetPlatform.linux),
    (tester) async {
      final container = await _pumpPage(tester);
      const title = 'Direct ALSA output';
      await _scrollTo(tester, find.text(title), 300);
      expect(find.text('Output device'), findsNothing);
      await tester.tap(find.text(title));
      await tester.pumpAndSettle();
      expect(container.read(settingsProvider).exclusiveOutput, isTrue);
      expect(find.text('Output device'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('the option is desktop only', (tester) async {
    debugDesktopPlatformOverride = false;
    await _pumpPage(tester);
    for (var i = 0; i < 10; i++) {
      await tester.drag(find.byType(Scrollable).first, const Offset(0, -600));
      await tester.pumpAndSettle();
      expect(find.text('WASAPI exclusive mode'), findsNothing);
    }
    final l10n = AppLocalizations.of(tester.element(find.byType(Scaffold)));
    bool listed() => SettingsSearchCatalog(
      l10n,
    ).playback.any((entry) => entry.title == l10n.exclusiveOutputWindows);
    expect(listed(), isFalse);
    debugDesktopPlatformOverride = true;
    expect(listed(), isTrue);
  });
}
