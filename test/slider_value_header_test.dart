import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
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
    final boxes = find.ancestor(
      of: find.text(value),
      matching: find.byType(DecoratedBox),
    );
    if (boxes.evaluate().isEmpty) return null;
    return tester.widget<DecoratedBox>(boxes.first).decoration
        as ShapeDecoration;
  }

  testWidgets('Sistrum shows the value as detail text', (tester) async {
    await pump(
      tester,
      const SliderValueHeader(title: 'Strength', value: '40%'),
    );
    expect(find.text('40%'), findsOneWidget);
    expect(pill(tester, '40%'), isNull);
  });

  testWidgets('Sistrum settings sliders keep their inline labels', (
    tester,
  ) async {
    await pump(
      tester,
      const Column(
        children: [
          MusicHapticsSettings(enabled: true),
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
