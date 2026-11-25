import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/providers/library_mixes_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fixed_settings.dart';

/// The Material Home without a feed, resuming a saved song unless
/// [saved] is false.
Widget _home({bool saved = true}) => ProviderScope(
  overrides: [
    settingsProvider.overrideWith(() => FixedSettingsNotifier()),
    recentAccessProvider.overrideWith(_Recent.new),
    libraryMixesProvider.overrideWith((ref) async => const []),
    homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
    homeOnRepeatProvider.overrideWith((ref) async => const []),
    currentMediaItemProvider.overrideWithBuild(
      (ref, notifier) => Stream<MediaItem?>.value(null),
    ),
    playbackResumeProvider.overrideWith(
      (ref) async => saved
          ? (
              media: const PlayableMedia(
                id: '/music/Album/song.flac',
                source: '/music/Album/song.flac',
                title: 'Saved song',
                artist: 'Artist',
              ),
              position: const Duration(seconds: 23),
            )
          : null,
    ),
  ],
  child: MaterialApp(
    theme: MornyeTheme.build(Brightness.dark),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: const HomeTab(mode: HomeTabMode.combined),
  ),
);

/// Opens [_home] in a [width] wide window, as the desktop app when
/// [desktop] is true.
Future<void> _open(
  WidgetTester tester, {
  required bool desktop,
  double width = 1280,
  bool saved = true,
}) async {
  tester.view.physicalSize = Size(width, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDesktopPlatformOverride = desktop;
  addTearDown(() => debugDesktopPlatformOverride = null);
  await tester.pumpWidget(_home(saved: saved));
  await _settle(tester);
  await _settle(tester);
}

/// Lets a menu, sheet or route finish opening. Home keeps an animation
/// running, so it never settles; short frames let the menu spring run.
Future<void> _settle(WidgetTester tester) async {
  for (var frame = 0; frame < 10; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  testWidgets('desktop Home shows the saved song under Continue listening', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    await _open(tester, desktop: true);
    expect(tester.takeException(), isNull);
    expect(find.text('Continue listening'), findsOneWidget);
    expect(find.text('Saved song'), findsOneWidget);
  });
}

class _Recent extends RecentAccessNotifier {
  @override
  RecentAccessState build() => const RecentAccessState(isLoaded: true);
}
