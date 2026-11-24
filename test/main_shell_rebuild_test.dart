import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/recent_access_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/main_shell.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart';
import 'package:sistrum/widgets/desktop/desktop_navigation.dart';
import 'package:sistrum/widgets/desktop/desktop_window.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const backend = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late SchedulingStrategy schedulingStrategy;

  setUp(() {
    schedulingStrategy = SchedulerBinding.instance.schedulingStrategy;
    // Idle tab preloads otherwise poll between frames under fake time.
    SchedulerBinding.instance.schedulingStrategy =
        ({required priority, required scheduler}) => true;
    SharedPreferences.setMockInitialValues({});
    messenger.setMockMethodCallHandler(backend, (_) async => null);
    desktopSidebarCollapsed.value = false;
    desktopSidebarLink.value = null;
  });
  tearDown(() {
    SchedulerBinding.instance.schedulingStrategy = schedulingStrategy;
    messenger.setMockMethodCallHandler(backend, null);
    debugDesktopPlatformOverride = null;
    debugDesktopTitleBarInContentOverride = null;
    desktopSidebarCollapsed.value = false;
    desktopSidebarLink.value = null;
  });

  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 25; i++) {
      await tester.pump(const Duration(milliseconds: 20));
    }
  }

  Future<void> pumpShell(WidgetTester tester, {required bool desktop}) async {
    debugDesktopPlatformOverride = desktop;
    debugDesktopTitleBarInContentOverride = false;
    tester.view.physicalSize = desktop
        ? const Size(1280, 800)
        : const Size(430, 932);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final router = GoRouter(
      routes: [GoRoute(path: '/', builder: (_, _) => const MainShell())],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsProvider.overrideWith(_Settings.new),
          recentAccessProvider.overrideWith(_Recent.new),
          localLibraryProvider.overrideWith(_Local.new),
          libraryCollectionsProvider.overrideWith(_Collections.new),
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => Stream.value(null),
          ),
          playbackStateProvider.overrideWith(
            (ref) => Stream.value(PlaybackState()),
          ),
          systemVolumeProvider.overrideWith((ref) => Stream.value(0.5)),
          desktopSidebarPlaylistsProvider.overrideWithValue(const []),
          desktopSidebarProfileProvider.overrideWithValue(null),
        ],
        child: MaterialApp.router(
          theme: MornyeTheme.build(Brightness.dark),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          routerConfig: router,
        ),
      ),
    );
    await tester.pump();
    await settle(tester);
  }

  /// Opens Library, pushes a probe page there and returns to Home, so the
  /// Library tab is mounted, hidden and holds a pushed page.
  Future<List<int>> pushProbeInLibrary(WidgetTester tester) async {
    final builds = [0];
    ShellNavigationService.requestTab(ShellTab.library);
    await settle(tester);
    expect(find.byType(QueueTab), findsOneWidget);
    unawaited(
      ShellNavigationService.libraryTabNavigatorKey.currentState!.push(
        MaterialPageRoute<void>(builder: (_) => _ProbePage(builds)),
      ),
    );
    await settle(tester);
    ShellNavigationService.requestTab(ShellTab.home);
    await settle(tester);
    expect(builds.single, greaterThan(0));
    expect(
      ShellNavigationService.activeTabNavigator(),
      same(ShellNavigationService.homeTabNavigatorKey.currentState),
    );
    expect(find.byType(_ProbePage, skipOffstage: false), findsOneWidget);
    return builds;
  }

  /// The widgets MainShell creates on every build (its tab navigators),
  /// the Navigators those build, and the tab root pages the routes build.
  /// An unchanged instance means its parent did not build again.
  List<Widget> shellAndPages(WidgetTester tester) => [
    for (final key in const ['tab-home', 'tab-library'])
      tester.widget(find.byKey(ValueKey(key), skipOffstage: false).last),
    ...tester.widgetList(find.byType(Navigator, skipOffstage: false)),
    ...tester.widgetList(find.byType(HomeTab, skipOffstage: false)),
    tester.widget(find.byType(QueueTab, skipOffstage: false)),
  ];

  void expectSameInstances(List<Widget> before, List<Widget> after) {
    expect(after, hasLength(before.length));
    for (var i = 0; i < before.length; i++) {
      expect(after[i], same(before[i]), reason: '${before[i]}');
    }
  }

  for (final desktop in [true, false]) {
    final platform = desktop ? 'desktop' : 'phone';
    final style = 'Mornye';

    testWidgets('resizing rebuilds no shell, route or page ($platform, '
        '$style)', (tester) async {
      await pumpShell(tester, desktop: desktop);
      final probeBuilds = await pushProbeInLibrary(tester);
      final builds = probeBuilds.single;
      final before = shellAndPages(tester);

      final start = tester.view.physicalSize;
      for (var frame = 1; frame <= 10; frame++) {
        tester.view.physicalSize = start + Offset(frame * 12, frame * -6);
        await tester.pump(const Duration(milliseconds: 16));
      }
      await settle(tester);

      expect(probeBuilds.single, builds);
      expectSameInstances(before, shellAndPages(tester));
      expect(tester.takeException(), isNull);
    });

    testWidgets('switching tabs rebuilds no page of a tab ($platform, '
        '$style)', (tester) async {
      await pumpShell(tester, desktop: desktop);
      final probeBuilds = await pushProbeInLibrary(tester);
      final builds = probeBuilds.single;
      List<Widget> pages() => [
        ...tester.widgetList(find.byType(HomeTab, skipOffstage: false)),
        tester.widget(find.byType(_ProbePage, skipOffstage: false)),
      ];
      final before = pages();

      for (final tab in [ShellTab.library, ShellTab.settings, ShellTab.home]) {
        ShellNavigationService.requestTab(tab);
        await settle(tester);
      }

      expect(probeBuilds.single, builds);
      expectSameInstances(before, pages());
      expect(tester.takeException(), isNull);
    });
  }

  for (final desktop in [true, false]) {
    testWidgets('Library learns each switch to and from its tab '
        '(${desktop ? 'desktop' : 'phone'})', (tester) async {
      await pumpShell(tester, desktop: desktop);
      bool libraryActive() => tester
          .widget<QueueTab>(find.byType(QueueTab, skipOffstage: false))
          .tabActive!
          .value;

      for (var round = 0; round < 2; round++) {
        ShellNavigationService.requestTab(ShellTab.library);
        await settle(tester);
        expect(libraryActive(), isTrue);
        ShellNavigationService.requestTab(ShellTab.home);
        await settle(tester);
        expect(libraryActive(), isFalse);
      }
      expect(tester.takeException(), isNull);
    });
  }

  /// The widget [type]'s state built last; a new instance means its state
  /// built again.
  Widget builtBy(WidgetTester tester, Type type) {
    late Widget child;
    tester
        .element(find.byType(type, skipOffstage: false).first)
        .visitChildElements((element) => child = element.widget);
    return child;
  }

  for (final desktop in [true, false]) {
    testWidgets('a switch of tab builds neither Home nor Library again '
        '(${desktop ? 'desktop' : 'phone'})', (tester) async {
      await pumpShell(tester, desktop: desktop);
      ShellNavigationService.requestTab(ShellTab.library);
      await settle(tester);
      ShellNavigationService.requestTab(ShellTab.home);
      await settle(tester);
      final home = builtBy(tester, HomeTab);
      final library = builtBy(tester, QueueTab);

      for (final tab in [ShellTab.library, ShellTab.settings, ShellTab.home]) {
        ShellNavigationService.requestTab(tab);
        await settle(tester);
      }

      expect(builtBy(tester, HomeTab), same(home));
      expect(builtBy(tester, QueueTab), same(library));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a phone turned into a tablet still swaps the bar for the '
      'rail', (tester) async {
    await pumpShell(tester, desktop: false);
    unawaited(
      ShellNavigationService.homeTabNavigatorKey.currentState!.push(
        MaterialPageRoute<void>(builder: (_) => _ProbePage([0])),
      ),
    );
    await settle(tester);
    final probe = tester.element(find.byType(_ProbePage));
    final bar = find.byType(MornyeBottomBar);
    expect(bar, findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);

    // Past both styles' rail breakpoints.
    tester.view.physicalSize = const Size(1500, 1000);
    await settle(tester);

    expect(find.byType(NavigationRail), findsOneWidget);
    expect(bar, findsNothing);
    // The tab pages moved with the tab view instead of starting over.
    expect(tester.element(find.byType(_ProbePage)), same(probe));
    expect(tester.takeException(), isNull);
  });
}

/// A page that reads neither the window size nor the download queue, so it
/// builds again only when something above forces it.
class _ProbePage extends StatelessWidget {
  const _ProbePage(this.builds);

  final List<int> builds;

  @override
  Widget build(BuildContext context) {
    builds[0]++;
    return const SizedBox.expand();
  }
}

class _Settings extends SettingsNotifier {
  @override
  AppSettings build() => const AppSettings();
}

class _Recent extends RecentAccessNotifier {
  @override
  RecentAccessState build() => const RecentAccessState(isLoaded: true);
}

class _Local extends LocalLibraryNotifier {
  @override
  LocalLibraryState build() => LocalLibraryState();
}

class _Collections extends LibraryCollectionsNotifier {
  @override
  LibraryCollectionsState build() => LibraryCollectionsState(isLoaded: true);
}
