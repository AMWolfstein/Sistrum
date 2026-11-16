import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/main_shell.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:sistrum/widgets/mini_player.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const backend = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final languageTags = <Object?>[];

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    languageTags.clear();
    messenger.setMockMethodCallHandler(backend, (call) async {
      if (call.method == 'setMetadataLanguage') {
        languageTags.add((call.arguments as Map)['tag']);
      }
      return null;
    });
  });
  tearDown(() => messenger.setMockMethodCallHandler(backend, null));

  Future<ValueNotifier<Locale>> pumpShell(
    WidgetTester tester, {
    bool externalPlayer = false,
    bool search = false,
    Stream<MediaItem?>? media,
  }) async {
    tester.view.physicalSize = const Size(430, 932);
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
          extensionProvider.overrideWith(() => _Extensions(search: search)),
          downloadHistoryProvider.overrideWith(_History.new),
        ],
        child: ValueListenableBuilder(
          valueListenable: locale,
          builder: (context, value, _) => MaterialApp.router(
            theme: MornyeTheme.build(Brightness.dark),
            locale: value,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
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
    expect(languageTags, ['en']);
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

    expect(languageTags, ['en']);
    // The shell did not rebuild its tab navigators.
    expect(tester.widget(homeTab), same(tabs));
    // Lets the back press snack bar time out.
    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a locale change still re-runs the shell side effects', (
    tester,
  ) async {
    final locale = await pumpShell(tester);
    locale.value = const Locale('id');
    await tester.pump();
    await tester.pump();

    expect(languageTags, ['en', 'id']);
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
}

class _Settings extends SettingsNotifier {
  _Settings({this.externalPlayer = false});

  final bool externalPlayer;

  @override
  AppSettings build() => AppSettings(
    searchProvider: 'example',
    playerMode: externalPlayer ? 'external' : 'internal',
  );
}

class _Extensions extends ExtensionNotifier {
  _Extensions({this.search = false});

  final bool search;

  @override
  ExtensionState build() => ExtensionState(
    isInitialized: true,
    extensions: [
      if (search)
        const Extension(
          id: 'example',
          name: 'example',
          displayName: 'Example',
          version: '1.0.0',
          description: '',
          enabled: true,
          status: 'loaded',
          searchBehavior: SearchBehavior(enabled: true),
        ),
    ],
  );
}

class _History extends DownloadHistoryNotifier {
  @override
  DownloadHistoryState build() => DownloadHistoryState();
}
