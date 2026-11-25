import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/theme_settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/settings_search_catalog.dart';
import 'package:sistrum/widgets/app_slider.dart';
import 'package:sistrum/widgets/autoplay_randomness_slider.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/slider_value_header.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  for (final locale in ['en', 'id', 'ar']) {
    testWidgets('Autoplay slider follows text scale ($locale)', (tester) async {
      SharedPreferences.setMockInitialValues({});
      tester.view.physicalSize = const Size(320, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final container = ProviderContainer(
        overrides: [
          mornyeGlassLevelProvider.overrideWithValue(MornyeGlassLevel.flat),
          initialSettingsProvider.overrideWithValue(
            const AppSettings(autoplayPersonalization: false),
          ),
        ],
      );
      addTearDown(container.dispose);
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: MornyeTheme.build(
              Brightness.dark,
              accent: MornyeAccent.blue,
            ),
            locale: Locale(locale),
            localizationsDelegates: appLocalizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: TextScaler.linear(2)),
              child: child!,
            ),
            home: const Scaffold(
              body: SingleChildScrollView(
                child: SettingsGroup(children: [AutoplayRandomnessSlider()]),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(SliderValueHeader), findsOneWidget);
      final slider = find.byType(AppSlider);
      final l10n = AppLocalizations.of(tester.element(slider));
      expect(find.text(l10n.autoplayLeastRecent), findsOneWidget);
      expect(find.text(l10n.autoplayMoreRandom), findsOneWidget);
      expect(
        SettingsSearchCatalog(l10n).playback.any(
          (entry) =>
              entry.title == l10n.autoplayRandomness &&
              entry.matches(SettingsSearchQuery('variety')),
        ),
        isTrue,
      );

      await tester.ensureVisible(slider);
      await tester.pumpAndSettle();
      expect(slider.hitTestable(), findsOneWidget);
      expect(tester.widget<AppSlider>(slider).onChanged, isNotNull);
      final rect = tester.getRect(slider);
      final rtl = locale == 'ar';
      final gesture = await tester.startGesture(
        Offset(rtl ? rect.right - 24 : rect.left + 24, rect.center.dy),
      );
      await tester.pump();
      await gesture.moveBy(Offset(rtl ? -30 : 30, 0));
      await tester.pump();
      await gesture.moveTo(
        Offset(
          rtl ? rect.left + rect.width * 0.2 : rect.left + rect.width * 0.8,
          rect.center.dy,
        ),
      );
      await tester.pump();
      expect(container.read(settingsProvider).autoplayRandomness, 0);
      expect(tester.widget<AppSlider>(slider).value, greaterThan(0.5));
      await gesture.up();
      await tester.pumpAndSettle();
      final value = container.read(settingsProvider).autoplayRandomness;
      expect(value, greaterThan(0.5));
      expect(container.read(settingsProvider).autoplayPersonalization, isFalse);
      final prefs = await SharedPreferences.getInstance();
      expect(
        AppSettings.fromJson(
          jsonDecode(prefs.getString('app_settings')!) as Map<String, dynamic>,
        ).autoplayRandomness,
        value,
      );

      expect(tester.takeException(), isNull);
    });
  }
}
