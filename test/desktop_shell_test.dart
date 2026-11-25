import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter/gestures.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/l10n/app_localizations.dart';
import 'package:sistrum/models/library_collections.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/system_volume_provider.dart';
import 'package:sistrum/services/playback_deck.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/services/user_profile_store.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_navigation.dart';
import 'package:sistrum/widgets/desktop/desktop_now_playing.dart';
import 'package:sistrum/widgets/desktop/desktop_player_bar.dart';
import 'package:sistrum/widgets/desktop/desktop_shortcuts.dart';
import 'package:sistrum/widgets/desktop/desktop_window.dart';
import 'package:sistrum/widgets/mini_player.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fixed_settings.dart';
import 'support/tooltip_finder.dart';

class _FakeController implements MusicPlayerController {
  final calls = <String>[];
  VoidCallback? onDismiss;

  @override
  Future<void> dismiss() async {
    calls.add('dismiss');
    onDismiss?.call();
  }

  @override
  Future<void> togglePlayPause(bool isPlaying) async =>
      calls.add(isPlaying ? 'pause' : 'play');

  @override
  Future<void> next() async => calls.add('next');

  @override
  Future<void> previous() async => calls.add('previous');

  @override
  Future<void> seek(Duration position) async => calls.add('seek');

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _track = MediaItem(
  id: 'track',
  title: 'A song',
  artist: 'An artist',
  duration: Duration(minutes: 3),
);

final _playlist = UserPlaylistCollection(
  id: 'mix',
  name: 'Road trip',
  createdAt: DateTime(2026),
  updatedAt: DateTime(2026),
  tracks: const [],
);

const _libraryLinks = [
  DesktopSidebarLink(
    id: 'recent',
    icon: Icons.schedule,
    label: 'Recently Added',
  ),
  DesktopSidebarLink(id: 'albums', icon: Icons.album, label: 'Albums'),
];

List<DesktopDestination> _destinations() => [
  for (final tab in desktopDestinationTabs())
    DesktopDestination(
      tab: tab,
      icon: Icons.circle_outlined,
      selectedIcon: Icons.circle,
      label: tab.name,
      badgeCount: tab == ShellTab.library ? 2 : 0,
    ),
];

void main() {
  late _FakeController controller;
  late List<double> volumeWrites;
  late List<ShellTab> selections;

  setUp(() {
    // The rail's menu button saves its state.
    SharedPreferences.setMockInitialValues({});
    debugDesktopPlatformOverride = true;
    debugDesktopTitleBarInContentOverride = false;
    desktopSidebarCollapsed.value = false;
    desktopSidebarLink.value = null;
    desktopPlayerDismissed.value = false;
    controller = _FakeController();
    volumeWrites = [];
    selections = [];
  });

  tearDown(() {
    debugDesktopPlatformOverride = null;
    debugDesktopTitleBarInContentOverride = null;
    desktopSidebarCollapsed.value = false;
    desktopSidebarLink.value = null;
    desktopPlayerDismissed.value = false;
  });

  List<Override> overrides({
    MediaItem? item = _track,
    Stream<MediaItem?>? mediaItems,
    bool playing = false,
    bool loading = false,
    UserProfile profile = const UserProfile(name: 'Ada Lovelace'),
  }) => [
    settingsProvider.overrideWith(
      () => FixedSettingsNotifier(const AppSettings()),
    ),
    currentMediaItemProvider.overrideWithBuild(
      (ref, notifier) => mediaItems ?? Stream.value(item),
    ),
    playbackStateProvider.overrideWith(
      (ref) => Stream.value(
        PlaybackState(
          playing: playing,
          processingState: loading
              ? AudioProcessingState.buffering
              : AudioProcessingState.ready,
        ),
      ),
    ),
    musicPlayerControllerProvider.overrideWithValue(controller),
    systemVolumeProvider.overrideWith((ref) => Stream.value(0.5)),
    systemVolumeWriterProvider.overrideWithValue((value) async {
      volumeWrites.add(value);
    }),
    desktopSidebarPlaylistsProvider.overrideWithValue([_playlist]),
    desktopSidebarProfileProvider.overrideWithValue(profile),
  ];

  Future<void> pumpShell(
    WidgetTester tester, {
    required double width,
    ShellTab selected = ShellTab.home,
    MediaItem? item = _track,
    Stream<MediaItem?>? mediaItems,
    bool playing = false,
    bool loading = false,
    List<DesktopSidebarLink> libraryLinks = const [],
    ValueChanged<String>? onOpenLibraryLink,
    VoidCallback? onOpenProfile,
    UserProfile profile = const UserProfile(name: 'Ada Lovelace'),
    Widget? nowPlaying,
    bool reducedMotion = false,
  }) async {
    tester.view.physicalSize = Size(width, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: overrides(
          item: item,
          mediaItems: mediaItems,
          playing: playing,
          loading: loading,
          profile: profile,
        ),
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(disableAnimations: reducedMotion),
            child: child!,
          ),
          home: Scaffold(
            body: DesktopShellLayout(
              destinations: _destinations(),
              selected: selected,
              onSelected: selections.add,
              libraryLinks: libraryLinks,
              onOpenLibraryLink: onOpenLibraryLink,
              onOpenProfile: onOpenProfile,
              nowPlaying: nowPlaying ?? const DesktopNowPlaying(),
              body: const Center(child: Text('content')),
            ),
          ),
        ),
      ),
    );
    if (loading) {
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    } else {
      await tester.pumpAndSettle();
    }
  }

  /// The page shown in the content pane.
  Finder contentPage() => find
      .ancestor(of: find.text('content'), matching: find.byType(Center))
      .first;

  void expectNoTouchNavigation() {
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byType(MornyeBottomBar), findsNothing);
    expect(find.byType(MiniPlayer), findsNothing);
  }

  testWidgets('paused desktop player dismisses and returns for a new song', (
    tester,
  ) async {
    final items = StreamController<MediaItem?>();
    addTearDown(items.close);
    controller.onDismiss = () => items.add(null);
    await pumpShell(tester, width: 900, mediaItems: items.stream);
    items.add(_track);
    await tester.pumpAndSettle();
    expect(findByTooltip('Dismiss'), findsOneWidget);
    desktopInspectorPanel.value = DesktopInspectorPanel.upNext;
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('desktop-player-dismiss')));
    await tester.pumpAndSettle();

    expect(controller.calls, ['dismiss']);
    expect(find.byType(DesktopPlayerBar), findsNothing);
    expect(desktopInspectorPanel.value, isNull);
    expect(desktopNowPlayingOpen.value, isFalse);
    expect(tester.getSize(contentPage()).height, 800);
    expect(
      MediaQuery.paddingOf(tester.element(find.text('content'))).bottom,
      0,
    );

    items.add(_track.copyWith(id: 'next', title: 'Another song'));
    await tester.pumpAndSettle();
    expect(find.byType(DesktopPlayerBar), findsOneWidget);
    expect(find.text('Another song'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  for (final state in ['playing', 'loading', 'idle']) {
    testWidgets('desktop player cannot dismiss $state playback', (
      tester,
    ) async {
      await pumpShell(
        tester,
        width: 900,
        item: state == 'idle' ? null : _track,
        playing: state == 'playing',
        loading: state == 'loading',
      );
      expect(
        find.byKey(const ValueKey('desktop-player-dismiss')),
        findsNothing,
      );
      expect(controller.calls, isEmpty);
      expect(tester.takeException(), isNull);
    });
  }

  double nowPlayingOpacity(WidgetTester tester) => tester
      .widget<Opacity>(find.byKey(const ValueKey('desktop-now-playing-fade')))
      .opacity;

  double nowPlayingRise(WidgetTester tester) =>
      tester.getTopLeft(find.byKey(const ValueKey('desktop-now-playing'))).dy;

  testWidgets('Now Playing fades in over the shell and out again', (
    tester,
  ) async {
    addTearDown(() => desktopNowPlayingOpen.value = false);
    await pumpShell(
      tester,
      width: 1400,
      nowPlaying: const ColoredBox(color: Colors.black),
    );

    desktopNowPlayingOpen.value = true;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(nowPlayingOpacity(tester), greaterThan(0));
    expect(nowPlayingOpacity(tester), lessThan(1));
    expect(nowPlayingRise(tester), 0);
    // The shell keeps painting under the entrance.
    expect(find.text('content'), findsOneWidget);

    await tester.pumpAndSettle();
    expect(nowPlayingOpacity(tester), 1);
    expect(nowPlayingRise(tester), 0);
    expect(find.text('content'), findsNothing);

    desktopNowPlayingOpen.value = false;
    await tester.pump();
    expect(find.text('content'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 80));
    expect(nowPlayingOpacity(tester), lessThan(1));
    // The exit is shorter than the entrance.
    await tester.pump(const Duration(milliseconds: 130));
    expect(find.byKey(const ValueKey('desktop-now-playing')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Esc during the Now Playing entrance fades out from where it '
      'is', (tester) async {
    addTearDown(() => desktopNowPlayingOpen.value = false);
    await pumpShell(
      tester,
      width: 1400,
      nowPlaying: const ColoredBox(color: Colors.black),
    );

    desktopNowPlayingOpen.value = true;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 60));
    final opacity = nowPlayingOpacity(tester);
    final rise = nowPlayingRise(tester);
    expect(dismissDesktopPlayerLayer(1400), isTrue);
    await tester.pump();
    expect(nowPlayingOpacity(tester), closeTo(opacity, 0.01));
    expect(nowPlayingRise(tester), rise);
    await tester.pump(const Duration(milliseconds: 16));
    expect(nowPlayingOpacity(tester), lessThan(opacity));

    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('desktop-now-playing')), findsNothing);
    expect(find.text('content'), findsOneWidget);
  });

  testWidgets('focus leaves the shell as soon as Now Playing opens', (
    tester,
  ) async {
    addTearDown(() => desktopNowPlayingOpen.value = false);
    await pumpShell(
      tester,
      width: 1400,
      nowPlaying: const ColoredBox(color: Colors.black),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    final shellFocus = FocusManager.instance.primaryFocus!;
    expect(
      shellFocus.context!.findAncestorWidgetOfExactType<DesktopShellLayout>(),
      isNotNull,
    );

    desktopNowPlayingOpen.value = true;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 16));
    // Still mid-entrance, with the shell painted underneath.
    expect(find.text('content'), findsOneWidget);
    expect(shellFocus.hasPrimaryFocus, isFalse);

    await tester.pumpAndSettle();
    desktopNowPlayingOpen.value = false;
    await tester.pump();
    // The shell takes focus again while Now Playing leaves.
    shellFocus.requestFocus();
    await tester.pump();
    expect(shellFocus.hasPrimaryFocus, isTrue);
    await tester.pumpAndSettle();
  });

  testWidgets('reduced motion swaps Now Playing at once', (tester) async {
    addTearDown(() => desktopNowPlayingOpen.value = false);
    await pumpShell(
      tester,
      width: 1400,
      nowPlaying: const ColoredBox(color: Colors.black),
      reducedMotion: true,
    );

    desktopNowPlayingOpen.value = true;
    await tester.pump();
    expect(nowPlayingOpacity(tester), 1);
    expect(nowPlayingRise(tester), 0);
    expect(find.text('content'), findsNothing);

    desktopNowPlayingOpen.value = false;
    await tester.pump();
    expect(find.byKey(const ValueKey('desktop-now-playing')), findsNothing);
    expect(find.text('content'), findsOneWidget);
  });

  group('Mornye shell', () {
    for (final width in [900.0, 1400.0]) {
      testWidgets('shows the sidebar and player bar at $width', (tester) async {
        await pumpShell(tester, width: width);

        expect(find.byType(DesktopSidebar), findsOneWidget);
        expect(
          tester.getSize(find.byType(DesktopSidebar)).width,
          DesktopSidebar.width,
        );
        expect(find.byType(NavigationRail), findsNothing);
        expectNoTouchNavigation();
        expect(find.byType(DesktopPlayerBar), findsOneWidget);
        // A title too long for the capsule also shows its marquee copy.
        expect(find.text('A song'), findsWidgets);
        expect(find.byKey(const ValueKey('desktop-sidebar-search')), findsOne);
      });
    }

    testWidgets('player capsule floats centered over the content pane', (
      tester,
    ) async {
      await pumpShell(tester, width: 1235);

      final player = tester.getRect(find.byType(DesktopPlayerBar));
      final pane = tester.getRect(
        find.byKey(const ValueKey('desktop-content')),
      );
      expect(player.height, DesktopPlayerBar.mornyeHeight);
      expect(player.bottom, 800 - DesktopPlayerBar.floatingMargin);
      expect(player.center.dx, closeTo(pane.center.dx, 0.5));
      expect(
        player.width,
        closeTo(DesktopPlayerBar.floatingWidth(pane.width), 0.01),
      );
      expect(player.width, inInclusiveRange(650, 750));
      // Pages scroll under it, so the pane reaches the window bottom.
      expect(pane.bottom, 800);
      expect(DesktopPlayerBar.floatingWidth(700), 520);
      expect(DesktopPlayerBar.floatingWidth(500), 476);
      expect(DesktopPlayerBar.floatingWidth(2000), 900);
    });

    testWidgets(
      'capsule artwork grows and offers Now Playing under the mouse',
      (tester) async {
        await pumpShell(tester, width: 1400);

        final overlay = find.byKey(
          const ValueKey('desktop-player-artwork-overlay'),
        );
        expect(tester.widget<AnimatedOpacity>(overlay).opacity, 0);
        final scale = find.ancestor(
          of: overlay,
          matching: find.byType(AnimatedScale),
        );
        expect(tester.widget<AnimatedScale>(scale).scale, 1);

        final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
        addTearDown(mouse.removePointer);
        await mouse.addPointer(location: Offset.zero);
        await mouse.moveTo(tester.getCenter(overlay));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));

        expect(tester.widget<AnimatedOpacity>(overlay).opacity, 1);
        expect(tester.widget<AnimatedScale>(scale).scale, 1.1);
      },
    );

    testWidgets('player capsule keeps volume in a popover', (tester) async {
      await pumpShell(tester, width: 1400);

      expect(find.byType(Slider), findsOneWidget, reason: 'seek bar only');
      playbackOutputVolume.value = 0.5;
      addTearDown(() => playbackOutputVolume.value = 1);
      tester.binding.handlePointerEvent(
        PointerScrollEvent(
          position: tester.getCenter(
            find.byKey(const ValueKey('desktop-player-volume')),
          ),
          scrollDelta: const Offset(0, -20),
        ),
      );
      await tester.pump();
      expect(volumeWrites.single, closeTo(0.55, 0.000001));
      expect(
        find.byKey(const ValueKey('desktop-player-volume-popover')),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('desktop-player-volume')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('desktop-player-volume-popover')),
        findsOneWidget,
      );
      expect(find.byType(Slider), findsNWidgets(2));
    });

    testWidgets('idle capsule shows the glyph and dims its controls', (
      tester,
    ) async {
      await pumpShell(tester, width: 1400, item: null);

      expect(find.bySemanticsLabel('Not playing'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('desktop-player-shuffle')));
      await tester.tap(findByTooltip('Next track (Ctrl+Right)'));
      expect(controller.calls, isEmpty);
    });

    testWidgets('sidebar rows select destinations and name shortcuts', (
      tester,
    ) async {
      await pumpShell(tester, width: 1400);

      expect(findByTooltip('library (Ctrl+3)'), findsOneWidget);
      expect(findByTooltip('settings (Ctrl+5)'), findsOneWidget);
      await tester.tap(find.text('library'));
      await tester.tap(find.byKey(const ValueKey('desktop-sidebar-settings')));
      expect(selections, [ShellTab.library, ShellTab.settings]);

      final home = tester.getRect(
        find.ancestor(of: find.text('home'), matching: find.byType(InkWell)),
      );
      expect(home.height, 32);
      // The sidebar runs to the window's edge, so its rows bring the inset.
      expect(home.left, 10);
    });

    testWidgets('the sidebar is one piece with the window', (tester) async {
      await pumpShell(tester, width: 1400);

      final window = tester.getRect(find.byType(MaterialApp));
      final sidebar = tester.getRect(find.byType(DesktopSidebar));
      expect(sidebar.left, window.left);
      expect(sidebar.top, window.top);
      expect(sidebar.bottom, window.bottom);
      expect(
        tester.getRect(find.byKey(const ValueKey('desktop-content'))).left,
        sidebar.right,
      );
      expect(
        find.descendant(
          of: find.byType(DesktopSidebar),
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is DecoratedBox &&
                switch (widget.decoration) {
                  BoxDecoration(:final borderRadius, :final boxShadow) =>
                    borderRadius != null || boxShadow != null,
                  _ => false,
                },
          ),
        ),
        findsNothing,
        reason: 'the panel has no corners or shadow of its own',
      );
    });

    testWidgets('the sidebar shows the page hero under its tint', (
      tester,
    ) async {
      addTearDown(() => ShellNavigationService.chromeBackdrop.value = null);
      await pumpShell(tester, width: 1400);
      const backdrop = ValueKey('desktop-sidebar-backdrop');
      expect(find.byKey(backdrop), findsNothing);

      ShellNavigationService.chromeBackdrop.value = '/tmp/artist.jpg';
      await tester.pumpAndSettle();
      final top = tester.getRect(find.byKey(backdrop)).top;
      expect(top, 0);

      ShellNavigationService.chromeBackdropOffset.value = 120;
      await tester.pump();
      expect(tester.getRect(find.byKey(backdrop)).top, top - 120);
      ShellNavigationService.chromeBackdropOffset.value = 0;

      ShellNavigationService.chromeBackdrop.value = null;
      await tester.pumpAndSettle();
      expect(find.byKey(backdrop), findsNothing);
    });

    for (final reduced in [false, true]) {
      testWidgets('the sidebar tint follows the backdrop crossfade'
          '${reduced ? ' (reduced motion)' : ''}', (tester) async {
        if (reduced) {
          tester.platformDispatcher.accessibilityFeaturesTestValue =
              const FakeAccessibilityFeatures(disableAnimations: true);
          addTearDown(
            tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
          );
        }
        addTearDown(() => ShellNavigationService.chromeSurface.value = null);
        await pumpShell(tester, width: 1400);
        Color panel() =>
            (tester
                        .widget<DecoratedBox>(
                          find
                              .descendant(
                                of: find.byType(DesktopSidebar),
                                matching: find.byType(DecoratedBox),
                              )
                              .first,
                        )
                        .decoration
                    as BoxDecoration)
                .color!;
        final context = tester.element(find.byType(DesktopSidebar));
        final start = DesktopSidebar.panelColor(context, null);
        final end = DesktopSidebar.panelColor(context, const Color(0xffc02040));
        expect(panel(), start);

        ShellNavigationService.chromeSurface.value = const Color(0xffc02040);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 150));
        if (reduced) {
          expect(panel(), end);
        } else {
          expect(panel(), isNot(start));
          expect(panel(), isNot(end));
          expect(
            (panel().g - start.g) / (end.g - start.g),
            inExclusiveRange(0, 1),
          );
          await tester.pumpAndSettle();
          expect(panel(), end);
        }
      });
    }

    testWidgets('sidebar lists Library pages and playlists', (tester) async {
      final opened = <String>[];
      await pumpShell(
        tester,
        width: 1400,
        libraryLinks: _libraryLinks,
        onOpenLibraryLink: opened.add,
      );

      expect(find.text('Library'), findsOneWidget);
      expect(find.text('Playlists'), findsOneWidget);
      expect(find.text('library'), findsNothing);
      // The first Library page stands for Library and takes its shortcut.
      expect(findByTooltip('Recently Added (Ctrl+3)'), findsOneWidget);
      await tester.tap(find.text('Albums'));
      await tester.tap(find.text('All Playlists'));
      await tester.tap(find.text('Road trip'));
      expect(opened, [
        'albums',
        DesktopSidebarLink.allPlaylistsId,
        DesktopSidebarLink.playlistId('mix'),
      ]);
    });

    testWidgets('account row opens the profile', (tester) async {
      var profileOpened = 0;
      await pumpShell(
        tester,
        width: 1400,
        onOpenProfile: () => profileOpened++,
      );

      expect(find.text('Ada Lovelace'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('desktop-sidebar-account')));
      expect(profileOpened, 1);
      expect(selections, isEmpty);
    });

    testWidgets('an unnamed account row shows a label that fits', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await pumpShell(tester, width: 1400, profile: const UserProfile());

      expect(find.text('Profile'), findsOneWidget);
      expect(find.text('Set up your profile'), findsNothing);
      expect(
        tester.getSemantics(
          find.byKey(const ValueKey('desktop-sidebar-account')),
        ),
        matchesSemantics(label: 'Set up your profile', isButton: true),
      );
      semantics.dispose();
    });

    testWidgets('sidebar stays in narrow windows and ignores the shortcut', (
      tester,
    ) async {
      await pumpShell(tester, width: 820);
      desktopSidebarCollapsed.value = true;
      await tester.pumpAndSettle();

      expect(
        find.byWidgetPredicate(
          (w) => w is AppIcon && w.icon == CupertinoIcons.sidebar_left,
        ),
        findsNothing,
      );
      expect(
        tester.getSize(find.byType(DesktopSidebar)).width,
        DesktopSidebar.width,
      );
      expect(
        tester.getRect(find.byKey(const ValueKey('desktop-content'))).left,
        DesktopSidebar.width,
      );
    });
  });

  group('desktop shortcuts', () {
    final navigatorKey = GlobalKey<NavigatorState>();
    late Object owner;
    late List<ShellTab> requested;

    setUp(() {
      owner = Object();
      requested = [];
      ShellNavigationService.registerTabSelectionHandler(
        owner: owner,
        handler: requested.add,
      );
    });

    tearDown(() => ShellNavigationService.unregisterTabSelectionHandler(owner));

    Future<void> pumpApp(WidgetTester tester, {bool playing = false}) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: overrides(playing: playing),
          child: MaterialApp(
            navigatorKey: navigatorKey,
            theme: MornyeTheme.build(Brightness.light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) =>
                DesktopShortcuts(navigatorKey: navigatorKey, child: child!),
            home: const Scaffold(
              body: Column(children: [Text('home'), TextField()]),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> press(
      WidgetTester tester,
      LogicalKeyboardKey key, {
      bool control = false,
    }) async {
      if (control) await tester.sendKeyDownEvent(LogicalKeyboardKey.control);
      await tester.sendKeyEvent(key);
      if (control) await tester.sendKeyUpEvent(LogicalKeyboardKey.control);
      await tester.pump();
    }

    testWidgets('control playback and volume', (tester) async {
      await pumpApp(tester, playing: true);

      await press(tester, LogicalKeyboardKey.space);
      await press(tester, LogicalKeyboardKey.arrowRight, control: true);
      await press(tester, LogicalKeyboardKey.arrowLeft, control: true);
      expect(controller.calls, ['pause', 'next', 'previous']);

      await press(tester, LogicalKeyboardKey.arrowUp, control: true);
      await press(tester, LogicalKeyboardKey.arrowDown, control: true);
      expect(volumeWrites, [closeTo(0.55, 1e-9), closeTo(0.45, 1e-9)]);
    });

    testWidgets('leave Space and arrows to a focused text field', (
      tester,
    ) async {
      await pumpApp(tester);
      await tester.tap(find.byType(TextField));
      await tester.pump();

      await press(tester, LogicalKeyboardKey.space);
      await press(tester, LogicalKeyboardKey.arrowRight, control: true);
      expect(controller.calls, isEmpty);

      // Escape first leaves the field, then Space plays again.
      await press(tester, LogicalKeyboardKey.escape);
      expect(
        FocusManager.instance.primaryFocus?.context
            ?.findAncestorStateOfType<EditableTextState>(),
        isNull,
      );
      await press(tester, LogicalKeyboardKey.space);
      expect(controller.calls, ['play']);
    });

    testWidgets('select destinations, search and settings', (tester) async {
      await pumpApp(tester);

      await press(tester, LogicalKeyboardKey.digit3, control: true);
      await press(tester, LogicalKeyboardKey.digit1, control: true);
      await press(tester, LogicalKeyboardKey.comma, control: true);
      await press(tester, LogicalKeyboardKey.keyF, control: true);
      expect(requested, [
        ShellTab.library,
        ShellTab.home,
        ShellTab.settings,
        ShellTab.search,
      ]);
    });

    testWidgets('Escape and Back close the top page', (tester) async {
      await pumpApp(tester);
      for (final close in [
        () => press(tester, LogicalKeyboardKey.escape),
        () => press(tester, LogicalKeyboardKey.bracketLeft, control: true),
      ]) {
        unawaited(
          navigatorKey.currentState!.push(
            MaterialPageRoute<void>(
              builder: (_) => const Scaffold(body: Text('page')),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('page'), findsOneWidget);

        await close();
        await tester.pumpAndSettle();
        expect(find.text('page'), findsNothing);
        expect(find.text('home'), findsOneWidget);
      }
    });
  });

  test('shortcut labels follow the platform', () {
    final next = desktopCommandShortcut(
      DesktopCommand.next,
      TargetPlatform.macOS,
    )!;
    expect(next.meta, isTrue);
    expect(desktopShortcutLabel(next, TargetPlatform.macOS), '⌘→');
    expect(
      desktopShortcutLabel(
        desktopCommandShortcut(DesktopCommand.lyrics, TargetPlatform.windows)!,
        TargetPlatform.windows,
      ),
      'Ctrl+Shift+L',
    );
    expect(
      desktopShortcutLabel(
        desktopCommandShortcut(
          DesktopCommand.toggleSidebar,
          TargetPlatform.macOS,
        )!,
        TargetPlatform.macOS,
      ),
      '⌃⌘S',
    );
    expect(
      desktopCommandShortcut(
        DesktopCommand.toggleSidebar,
        TargetPlatform.linux,
      ),
      isNull,
    );
  });

  testWidgets('desktop mini player drops the touch gestures', (tester) async {
    Future<void> pump() => tester.pumpWidget(
      ProviderScope(
        overrides: overrides(),
        child: MaterialApp(
          theme: MornyeTheme.build(Brightness.light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: Center(child: MiniPlayer())),
        ),
      ),
    );

    await pump();
    await tester.pumpAndSettle();
    expect(find.byType(Dismissible), findsNothing);
    Finder touchGestures() => find.descendant(
      of: find.byType(MiniPlayer),
      matching: find.byWidgetPredicate(
        (widget) =>
            widget is GestureDetector && widget.onHorizontalDragEnd != null,
      ),
    );
    expect(touchGestures(), findsNothing);

    debugDesktopPlatformOverride = false;
    await tester.pumpWidget(const SizedBox());
    await pump();
    await tester.pumpAndSettle();
    // The surface and its title lines share one sideways swipe.
    expect(touchGestures(), findsWidgets);
  });
}
