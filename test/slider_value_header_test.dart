import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_slider.dart';
import 'package:sistrum/widgets/automix_settings.dart';
import 'package:sistrum/widgets/music_haptics_settings.dart';
import 'package:sistrum/widgets/playback_normalization_settings.dart';
import 'package:sistrum/widgets/slider_value_header.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  Future<ProviderContainer> pump(WidgetTester tester, Widget child) async {
    final container = ProviderContainer(
      overrides: [
        initialSettingsProvider.overrideWithValue(
          const AppSettings(
            musicHaptics: true,
            playbackNormalization: true,
            autoMix: true,
            autoMixDuration: 5,
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(body: SingleChildScrollView(child: child)),
        ),
      ),
    );
    return container;
  }

  /// The tonal pill around [value], if Material drew one.
  ShapeDecoration? pill(WidgetTester tester, String value) {
    // Mornye's page transition draws its edge shadow in a DecoratedBox too.
    final boxes = find.ancestor(
      of: find.text(value),
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is DecoratedBox && widget.decoration is ShapeDecoration,
      ),
    );
    if (boxes.evaluate().isEmpty) return null;
    return tester.widget<DecoratedBox>(boxes.first).decoration
        as ShapeDecoration;
  }

  testWidgets('Mornye shows the value as detail text', (tester) async {
    await pump(
      tester,
      const SliderValueHeader(title: 'Strength', value: '40%'),
    );
    expect(find.text('40%'), findsOneWidget);
    expect(pill(tester, '40%'), isNull);
  });

  testWidgets(
    'haptics sliders show a draft while dragging and save on release',
    (tester) async {
      final container = await pump(tester, const MusicHapticsSettings());
      expect(find.textContaining('Sensitivity'), findsOneWidget);
      expect(find.textContaining('50%'), findsWidgets);

      final slider = tester.widget<AppSlider>(find.byType(AppSlider).first);
      slider.onChanged!(0.8);
      await tester.pump();
      expect(find.textContaining('80%'), findsWidgets);
      expect(container.read(settingsProvider).musicHapticsSensitivity, 0.5);

      slider.onChangeEnd!(0.8);
      await tester.pump();
      expect(container.read(settingsProvider).musicHapticsSensitivity, 0.8);
      expect(find.textContaining('80%'), findsWidgets);
    },
  );

  testWidgets('Mornye settings sliders keep their inline labels', (
    tester,
  ) async {
    await pump(
      tester,
      const Column(
        children: [
          MusicHapticsSettings(),
          PlaybackNormalizationSettings(enabled: true),
          AutoMixSettings(),
        ],
      ),
    );
    expect(find.text('Sensitivity: 50%'), findsOneWidget);
    expect(find.text('Pre-amp: 0 dB (target -18 LUFS)'), findsOneWidget);
    expect(find.text('Mix duration: 5 s'), findsOneWidget);
    expect(find.byType(SliderValueHeader), findsNothing);
  });
}
