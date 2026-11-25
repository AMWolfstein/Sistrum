import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/app.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/performance_hub_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';

import 'support/fixed_settings.dart';

class _Player implements MusicPlayerController {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  setUp(() => debugDesktopPlatformOverride = false);
  tearDown(() => debugDesktopPlatformOverride = null);

  testWidgets(
    'a live recording builds the badge in the app-wide layer with no Overlay above it',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      // The real placement: buildAppContent wraps the router's child, so the
      // badge sits above the navigator and no Overlay is an ancestor of it.
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            settingsProvider.overrideWith(FixedSettingsNotifier.new),
            musicPlayerControllerProvider.overrideWithValue(_Player()),
            playbackStateProvider.overrideWith(
              (_) => Stream.value(PlaybackState()),
            ),
            systemVolumeProvider.overrideWith((_) => Stream.value(0.5)),
          ],
          child: MaterialApp.router(
            theme: MornyeTheme.build(Brightness.dark),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: GoRouter(
              routes: [
                GoRoute(
                  path: '/',
                  builder: (context, state) =>
                      const Scaffold(body: Text('content')),
                ),
              ],
            ),
            builder: buildAppContent,
          ),
        ),
      );
      await tester.pumpAndSettle();

      final container = ProviderScope.containerOf(
        tester.element(find.text('content')),
      );
      container.read(performanceHubProvider.notifier).startRecording();
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(find.textContaining('Recording 00:00'), findsOneWidget);
      expect(find.byIcon(Icons.stop_rounded), findsOneWidget);
    },
  );
}
