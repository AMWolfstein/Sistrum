import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';

class _NothingPlaying extends CurrentMediaItemNotifier {
  @override
  Stream<MediaItem?> build() => Stream.value(null);
}

/// Alpha of the floating inspector's dark fill, the layer that keeps its text
/// legible over the content beneath it.
Future<double> _fillAlpha(WidgetTester tester, MornyeGlassLevel mode) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        mornyeGlassModeProvider.overrideWithValue(mode),
        deviceSupportsBackdropBlurProvider.overrideWithValue(true),
        deviceSupportsLiquidGlassProvider.overrideWithValue(true),
        currentMediaItemProvider.overrideWith(_NothingPlaying.new),
      ],
      child: MaterialApp(
        theme: MornyeTheme.build(Brightness.dark),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(
          body: Align(
            alignment: Alignment.centerRight,
            child: SizedBox(
              width: desktopInspectorWidth,
              height: 600,
              child: DesktopInspector(
                panel: DesktopInspectorPanel.lyrics,
                overlay: true,
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  final fill = tester
      .widgetList<ColoredBox>(find.byType(ColoredBox))
      .map((box) => box.color)
      .where((color) => color.toARGB32() & 0xffffff == 0x363636)
      .single;
  return fill.a;
}

void main() {
  testWidgets('the floating inspector fill stays legible in Liquid', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    // Sigma 60 made the blurred backdrop unreadable at 0.74; the fill is
    // raised to keep the text above it.
    expect(
      await _fillAlpha(tester, MornyeGlassLevel.liquid),
      greaterThanOrEqualTo(0.88),
    );
  });

  testWidgets('None keeps the inspector fill opaque', (tester) async {
    tester.view.physicalSize = const Size(900, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    expect(
      await _fillAlpha(tester, MornyeGlassLevel.flat),
      greaterThanOrEqualTo(0.97),
    );
  });
}
