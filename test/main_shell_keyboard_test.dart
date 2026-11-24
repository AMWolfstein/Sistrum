import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/routes/now_playing_route.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/main_shell.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/tv_navigation.dart';
import 'package:sistrum/widgets/tv_shell.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:sistrum/widgets/mini_player.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const backend = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    messenger.setMockMethodCallHandler(backend, (call) async => null);
  });
  tearDown(() => messenger.setMockMethodCallHandler(backend, null));

  Future<ValueNotifier<Locale>> pumpShell(
    WidgetTester tester, {
    bool tv = false,
    bool externalPlayer = false,
    Stream<MediaItem?>? media,
  }) async {
    debugAndroidTvOverride = tv;
    addTearDown(() => debugAndroidTvOverride = null);
    tester.view.physicalSize = tv ? const Size(960, 540) : const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final locale = ValueNotifier(const Locale('en'));
    addTearDown(locale.dispose);
    final router = GoRouter(
      routes: [GoRoute(path: '/', builder: (_, _) => const MainShell())],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(
            () => _Settings(externalPlayer: externalPlayer),
          ),
          if (media != null)
            currentMediaItemProvider.overrideWithBuild(
              (ref, notifier) => media,
            ),
          if (externalPlayer) ...[
            currentMediaItemProvider.overrideWithBuild(
              (ref, notifier) => Stream.value(
                const MediaItem(id: 'old', title: 'Old internal song'),
              ),
            ),
            playbackResumeProvider.overrideWith(
              (ref) async => (
                media: const PlayableMedia(
                  id: 'saved',
                  source: '/music/saved.flac',
                  title: 'Saved song',
                  artist: 'Artist',
                ),
                position: const Duration(seconds: 23),
              ),
            ),
          ],
          recentAccessProvider.overrideWith(_Recent.new),
          // Home's replay shelves read statistics storage; stub them empty.
          homeRecentlyPlayedProvider.overrideWith((ref) async => const []),
          homeOnRepeatProvider.overrideWith((ref) async => const []),
        ],
        child: ValueListenableBuilder(
          valueListenable: locale,
          builder: (context, value, _) => MaterialApp.router(
            theme: MornyeTheme.build(Brightness.dark),
            builder: tv
                ? (context, child) => MediaQuery(
                    data: MediaQuery.of(
                      context,
                    ).copyWith(navigationMode: NavigationMode.directional),
                    child: AdaptiveUiScaler(
                      child: SafeArea(
                        minimum: const EdgeInsets.symmetric(
                          horizontal: 40,
                          vertical: 24,
                        ),
                        child: TvNavigation(child: child!),
                      ),
                    ),
                  )
                : null,
            locale: value,
            localizationsDelegates: appLocalizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: router,
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));
    return locale;
  }

  bool collapsed(WidgetTester tester) =>
      tester.widget<MornyeBottomBar>(find.byType(MornyeBottomBar)).collapsed;

  testWidgets('Home restores its viewport and navigation after the player', (
    tester,
  ) async {
    Future<void> settleFrames() async {
      for (var i = 0; i < 30; i++) {
        await tester.pump(const Duration(milliseconds: 20));
      }
    }

    final media = StreamController<MediaItem?>();
    addTearDown(media.close);
    await pumpShell(tester, media: media.stream);
    tester.view.padding = const FakeViewPadding(top: 24, bottom: 24);
    tester.view.viewPadding = const FakeViewPadding(top: 24, bottom: 24);
    media.add(
      const MediaItem(id: 'song', title: 'Current song', artist: 'Artist'),
    );
    await settleFrames();
    // The shell keeps a Search tab's HomeTab beside the Home one.
    final homeTab = find.byWidgetPredicate(
      (w) => w is HomeTab && w.mode == HomeTabMode.browse,
    );
    final scroll = find.descendant(
      of: homeTab,
      matching: find.byType(CustomScrollView),
    );
    expect(find.text('Continue listening'), findsOneWidget);
    expect(tester.getRect(scroll).bottom, 932);
    // Mornye's Home has no field of its own; the Search tab's does.
    final searchTab = find.byWidgetPredicate(
      (w) => w is HomeTab && w.mode == HomeTabMode.search,
    );
    final searchScroll = find.descendant(
      of: searchTab,
      matching: find.byType(CustomScrollView),
    );
    await tester.tap(
      find.descendant(
        of: find.byType(MornyeBottomBar),
        matching: find.text('Search'),
      ),
    );
    await settleFrames();
    await tester.tap(
      find.descendant(of: searchTab, matching: find.byType(TextField)),
    );
    await tester.pump();
    expect(tester.testTextInput.isVisible, isTrue);
    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pump();
    expect(tester.getRect(searchScroll).bottom, 632);
    final home = tester.element(homeTab);
    final navigator = Navigator.of(home, rootNavigator: true);
    unawaited(navigator.push(NowPlayingRoute(child: const Scaffold())));
    await settleFrames();
    tester.view.viewInsets = FakeViewPadding.zero;
    await tester.pump();
    navigator.pop();
    await settleFrames();
    expect(tester.testTextInput.isVisible, isFalse);
    final bar = find.byType(MornyeBottomBar);
    Finder destination(String label) =>
        find.descendant(of: bar, matching: find.text(label));
    await tester.tap(destination('Home'));
    await settleFrames();
    expect(find.text('Continue listening'), findsOneWidget);
    expect(tester.getRect(scroll).bottom, 932);
    // Phones reach Settings as a page, so the bar only holds the Library tab.
    await tester.tap(destination('Search'));
    await settleFrames();
    expect(tester.widget<MornyeBottomBar>(bar).selectedIndex, 2);
    await tester.tap(destination('Home'));
    await settleFrames();
    expect(tester.widget<MornyeBottomBar>(bar).selectedIndex, 0);
    expect(tester.getRect(scroll).bottom, 932);
    expect(tester.takeException(), isNull);
  });

  testWidgets('external mode hides Home playback and mini player', (
    tester,
  ) async {
    await pumpShell(tester, externalPlayer: true);
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Continue Listening'), findsNothing);
    expect(find.text('Old internal song'), findsNothing);
    expect(find.text('Saved song'), findsNothing);
    for (final player in find.byType(MiniPlayer).evaluate()) {
      expect(tester.getSize(find.byWidget(player.widget)).height, 0);
    }
    expect(tester.takeException(), isNull);
  });

  void scrollHome(WidgetTester tester, {required double pixels}) {
    ScrollUpdateNotification(
      metrics: FixedScrollMetrics(
        minScrollExtent: 0,
        maxScrollExtent: 2000,
        pixels: pixels,
        viewportDimension: 800,
        axisDirection: AxisDirection.down,
        devicePixelRatio: 1,
      ),
      context: tester.element(find.byType(HomeTab).first),
      scrollDelta: 40,
      dragDetails: DragUpdateDetails(globalPosition: Offset.zero),
    ).dispatch(tester.element(find.byType(HomeTab).first));
  }

  testWidgets('keyboard frames do not re-run the shell side effects', (
    tester,
  ) async {
    await pumpShell(tester);
    // A back press at a tab root checks the keyboard too.
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(find.text('Press back again to exit'), findsOneWidget);
    final homeTab = find.byKey(const ValueKey('tab-home')).first;
    final tabs = tester.widget(homeTab);

    for (final inset in [80.0, 160.0, 240.0, 300.0, 150.0, 0.0]) {
      tester.view.viewInsets = FakeViewPadding(bottom: inset);
      await tester.pump(const Duration(milliseconds: 16));
    }

    // The shell did not rebuild its tab navigators.
    expect(tester.widget(homeTab), same(tabs));
    // Lets the back press snack bar time out.
    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a locale change re-runs the shell without errors', (
    tester,
  ) async {
    final locale = await pumpShell(tester);
    locale.value = const Locale('id');
    await tester.pump();
    await tester.pump();

    expect(tester.takeException(), isNull);
  });

  testWidgets('the tab bar stays expanded while the keyboard is up', (
    tester,
  ) async {
    await pumpShell(tester);
    expect(collapsed(tester), isFalse);

    scrollHome(tester, pixels: 400);
    await tester.pump();
    expect(collapsed(tester), isTrue);

    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pump();
    expect(collapsed(tester), isFalse);

    // Scrolling back to the top with the keyboard up does not reach the bar.
    scrollHome(tester, pixels: 0);
    await tester.pump();
    tester.view.viewInsets = FakeViewPadding.zero;
    await tester.pump();
    expect(collapsed(tester), isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('TV remote opens Settings and Back returns Home', (tester) async {
    await pumpShell(tester, tv: true);
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byType(MornyeBottomBar), findsNothing);
    final tvShell = tester.widget<TvShell>(find.byType(TvShell));
    for (var i = 1; i < tvShell.destinations.length; i++) {
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
    }
    await tester.sendKeyEvent(LogicalKeyboardKey.select);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      tester.widget<TvShell>(find.byType(TvShell)).selectedIndex,
      tvShell.destinations.length - 1,
    );
    final back = tester.binding.handlePopRoute();
    await tester.pump();
    await back;
    await tester.pump();
    expect(tester.widget<TvShell>(find.byType(TvShell)).selectedIndex, 0);
    expect(tester.takeException(), isNull);
    await tester.pump(const Duration(seconds: 1));
  });
}

class _Settings extends SettingsNotifier {
  _Settings({this.externalPlayer = false});

  final bool externalPlayer;

  @override
  AppSettings build() =>
      AppSettings(playerMode: externalPlayer ? 'external' : 'internal');
}

class _Recent extends RecentAccessNotifier {
  @override
  RecentAccessState build() => const RecentAccessState(isLoaded: true);
}
