import 'dart:async';
import 'package:sistrum/controllers/open_audio_controller.dart';
import 'package:sistrum/services/audio_file_open_service.dart';
import 'package:flutter/foundation.dart' show listEquals;
import 'package:material_ui/material_ui.dart';
import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/playback_notification_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/providers/user_profile_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/library_directory_screen.dart';
import 'package:sistrum/screens/collection_detail_screen.dart';
import 'package:sistrum/screens/listening_statistics_screen.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/screens/settings/profile_settings_page.dart';
import 'package:sistrum/screens/settings/settings_tab.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/services/music_playback_deck.dart';
import 'package:sistrum/services/notification_service.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/nav_destination_icons.dart';
import 'package:sistrum/widgets/mini_player.dart';
import 'package:sistrum/widgets/tv_shell.dart';
import 'package:sistrum/widgets/lazy_tab_view.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_navigation.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/android_tv.dart';
import 'package:sistrum/utils/desktop_platform.dart';

final _log = AppLogger('MainShell');

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  int _currentIndex = 0;
  final _mornyeChrome = MornyeChromeController();
  // Preserves the tab view element (and its kept-alive tabs) when the body
  // structure swaps between rail and bottom-bar layouts on rotation.
  final GlobalKey _pageViewKey = GlobalKey();
  bool _usbVolumeWarningShown = false;
  StreamSubscription<List<OpenAudioFile>>? _audioOpenSubscription;
  DateTime? _lastBackPress;
  final GlobalKey<NavigatorState> _homeTabNavigatorKey =
      ShellNavigationService.homeTabNavigatorKey;
  final GlobalKey<NavigatorState> _libraryTabNavigatorKey =
      ShellNavigationService.libraryTabNavigatorKey;
  final GlobalKey<NavigatorState> _statisticsTabNavigatorKey =
      ShellNavigationService.statisticsTabNavigatorKey;
  final GlobalKey<NavigatorState> _searchTabNavigatorKey =
      ShellNavigationService.searchTabNavigatorKey;

  /// Desktop: whether Library shows its root page, so the Mornye sidebar
  /// returns to the Library root's link and the Material rail to Library.
  late final _libraryDepthObserver = _PageDepthObserver(
    onRoot: () => desktopSidebarLink.value = null,
  );

  /// What the services below were last configured with. Theme and media
  /// query changes also reach [didChangeDependencies]; the services only
  /// need a new language.
  AppLocalizations? _servicesL10n;
  String? _servicesLanguageTag;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final l10n = context.l10n;
    final languageTag = Localizations.localeOf(context).toLanguageTag();
    if (identical(l10n, _servicesL10n) && languageTag == _servicesLanguageTag) {
      return;
    }
    _servicesL10n = l10n;
    _servicesLanguageTag = languageTag;
    NotificationService().updateStrings(l10n);
    final playback = ref.read(musicPlayerRuntimeProvider);
    playback.updateStrings(
      unknownTitle: l10n.unknownTitle,
      unknownArtist: l10n.unknownArtist,
    );
    final settings = ref.read(settingsProvider);
    playback.configure(settings);
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    usbAudioStatus.addListener(_onUsbAudioStatusChanged);
    ShellNavigationService.registerTabSelectionHandler(
      owner: this,
      handler: _onShellTabRequested,
      libraryLinkHandler: _requestDesktopLibraryLink,
    );
    ShellNavigationService.syncState(tab: _tabForIndex(_currentIndex));
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      unawaited(_restorePlayback());
      _setupShareListener();
    });
  }

  /// Restores the last session, then its song's cover from the file; the
  /// desktop backend reads files only then.
  Future<void> _restorePlayback() async {
    final runtime = ref.read(musicPlayerRuntimeProvider);
    try {
      await runtime.restoreSession();
      if (!mounted) return;
      await runtime.recoverRestoredArtwork();
    } on Object catch (error) {
      _log.w('Could not restore the playing song\'s cover: $error');
    }
  }

  void _onUsbAudioStatusChanged() {
    if (usbAudioStatus.value.reason == 'active') {
      _usbVolumeWarningShown = false;
    }
    if (usbAudioStatus.value.reason != 'volume_unavailable' ||
        _usbVolumeWarningShown) {
      return;
    }
    _usbVolumeWarningShown = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || usbAudioStatus.value.reason != 'volume_unavailable') {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(context.l10n.usbVolumeUnavailable),
          duration: const Duration(seconds: 8),
        ),
      );
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      unawaited(
        ref.read(musicPlayerRuntimeProvider).handler?.persistCurrentSession(),
      );
    }
  }

  void _setupShareListener() {
    final audio = AudioFileOpenService.instance;
    _audioOpenSubscription = audio.requests.listen(_openAudioFiles);
    for (final files in audio.takePending()) {
      unawaited(_openAudioFiles(files));
    }
  }

  Future<void> _openAudioFiles(List<OpenAudioFile> files) async {
    try {
      await ref.read(openAudioControllerProvider.notifier).open(files);
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    usbAudioStatus.removeListener(_onUsbAudioStatusChanged);
    ShellNavigationService.unregisterTabSelectionHandler(this);
    _audioOpenSubscription?.cancel();
    _mornyeChrome.dispose();
    _libraryDepthObserver.dispose();
    super.dispose();
  }

  void _resetHomeToMain() {
    final homeNavigator = _navigatorForTab(0);
    homeNavigator?.popUntil((route) => route.isFirst);
    // Unfocus BEFORE clear so _onTrackStateChanged can properly
    // clear _urlController (it checks !_searchFocusNode.hasFocus)
    FocusManager.instance.primaryFocus?.unfocus();
  }

  /// Desktop and TV show Settings after Search in the content pane.
  /// Phones and tablets open it as a page instead.
  bool get _mornyeSettingsTab => isDesktopPlatform || isAndroidTv;

  void _onShellTabRequested(ShellTab tab) {
    // Picking a destination returns from Now Playing to the content pane.
    if (isDesktopPlatform) desktopNowPlayingOpen.value = false;
    if (tab == ShellTab.settings && !_mornyeSettingsTab) {
      unawaited(_openProfileSettings());
      return;
    }
    _onNavTap(_shellTabs().indexOf(tab), resetHome: tab != ShellTab.search);
  }

  bool _settingsPageOpen = false;

  Future<void> _openProfileSettings() async {
    if (_settingsPageOpen) return;
    _settingsPageOpen = true;
    FocusManager.instance.primaryFocus?.unfocus();
    try {
      await Navigator.of(context).push(
        slidePageRoute<void>(
          page: const Scaffold(body: SettingsTab(asPage: true)),
        ),
      );
    } finally {
      _settingsPageOpen = false;
    }
  }

  void _onNavTap(int index, {bool resetHome = true}) {
    _mornyeChrome.expand();
    if (index == 0 && resetHome) {
      _resetHomeToMain();
    }
    if (index == 0 && _currentIndex == 0) {
      return;
    }

    if (_currentIndex != index) {
      HapticFeedback.selectionClick();
      setState(() => _currentIndex = index);
      ShellNavigationService.syncState(tab: _tabForIndex(_currentIndex));
      if (!isAndroidTv) FocusManager.instance.primaryFocus?.unfocus();
    }
  }

  /// Reads the keyboard inset without depending on it, so keyboard frames
  /// do not rebuild the shell.
  bool get _keyboardVisible {
    final media = context.getInheritedWidgetOfExactType<MediaQuery>()!;
    return media.data.viewInsets.bottom > 0;
  }

  /// Whether scrolling may collapse the Mornye tab bar at [width].
  bool _canMinimizeChromeAt(double width, TextScaler textScaler) =>
      !isDesktopPlatform &&
      !useNavigationRailForWidth(width) &&
      textScaler.scale(15) <= 20;

  /// [_canMinimizeChromeAt] for the window now, read without depending on
  /// its size, so the tab pages' scroll listeners do not rebuild the shell.
  bool get _canMinimizeChrome {
    final media = context.getInheritedWidgetOfExactType<MediaQuery>()!.data;
    return _canMinimizeChromeAt(media.size.width, media.textScaler);
  }

  Future<void> _handleBackPress() async {
    final rootNavigator = Navigator.of(context, rootNavigator: true);
    final handledByRootNavigator = await rootNavigator.maybePop();
    if (handledByRootNavigator) {
      _lastBackPress = null;
      return;
    }

    final currentNavigator = _navigatorForTab(_currentIndex);
    final handledByCurrentNavigator =
        await currentNavigator?.maybePop() ?? false;
    if (handledByCurrentNavigator) {
      _lastBackPress = null;
      return;
    }

    if (!mounted) return;

    final isSearchTab = _tabForIndex(_currentIndex) == ShellTab.search;

    if (isSearchTab && _keyboardVisible) {
      FocusManager.instance.primaryFocus?.unfocus();
      _lastBackPress = null;
      return;
    }

    if (_currentIndex != 0) {
      _onNavTap(0);
      _lastBackPress = null;
      return;
    }

    if (isAndroidTv) {
      await SystemNavigator.pop();
      return;
    }

    final now = DateTime.now();
    if (_lastBackPress != null &&
        now.difference(_lastBackPress!) < const Duration(seconds: 2)) {
      unawaited(PlatformBridge.exitApp());
    } else {
      _lastBackPress = now;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(context.l10n.pressBackAgainToExit),
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  /// The destinations in bottom bar, rail and tab order. Settings is a tab
  /// only where it is shown in the shell.
  List<ShellTab> _shellTabs() => [
    ShellTab.home,
    ShellTab.statistics,
    ShellTab.library,
    ShellTab.search,
    if (_mornyeSettingsTab) ShellTab.settings,
  ];

  ShellTab _tabForIndex(int index) {
    final tabs = _shellTabs();
    return tabs[index.clamp(0, tabs.length - 1)];
  }

  List<DesktopDestination> _desktopDestinations() {
    final l10n = context.l10n;
    return [
      for (final tab in desktopDestinationTabs())
        switch (tab) {
          ShellTab.home => DesktopDestination(
            tab: tab,
            icon: CupertinoIcons.house,
            selectedIcon: CupertinoIcons.house_fill,
            label: l10n.navHome,
          ),
          ShellTab.statistics => DesktopDestination(
            tab: tab,
            icon: CupertinoIcons.chart_bar,
            selectedIcon: CupertinoIcons.chart_bar_fill,
            label: l10n.navInsights,
          ),
          ShellTab.library => DesktopDestination(
            tab: tab,
            icon: CupertinoIcons.square_stack,
            selectedIcon: CupertinoIcons.square_stack_fill,
            label: l10n.navLibrary,
          ),
          ShellTab.search => DesktopDestination(
            tab: tab,
            icon: CupertinoIcons.search,
            selectedIcon: CupertinoIcons.search,
            label: l10n.mornyeSearch,
          ),
          ShellTab.settings => DesktopDestination(
            tab: tab,
            icon: CupertinoIcons.gear_alt,
            selectedIcon: CupertinoIcons.gear_alt_fill,
            label: l10n.navSettings,
          ),
        },
    ];
  }

  /// The pages under the Mornye desktop sidebar's Library heading. The
  /// Library root is the overview they open from.
  List<DesktopSidebarLink> _desktopLibraryLinks() {
    final l10n = context.l10n;
    return [
      DesktopSidebarLink(
        id: 'artists',
        icon: CupertinoIcons.music_mic,
        label: l10n.searchArtists,
      ),
      DesktopSidebarLink(
        id: 'albums',
        icon: CupertinoIcons.square_stack,
        label: l10n.searchAlbums,
      ),
      DesktopSidebarLink(
        id: 'songs',
        icon: CupertinoIcons.music_note,
        label: l10n.searchSongs,
      ),
      DesktopSidebarLink(
        id: 'genres',
        icon: CupertinoIcons.guitars,
        label: l10n.libraryGenres,
      ),
      DesktopSidebarLink(
        id: 'folders',
        icon: CupertinoIcons.folder,
        label: l10n.libraryFolders,
      ),
    ];
  }

  /// The page a desktop sidebar or rail link opens in the Library tab, or
  /// null for the Library root.
  Widget? _desktopLibraryPage(String id) {
    const playlistPrefix = 'playlist:';
    if (id.startsWith(playlistPrefix)) {
      return CollectionDetailScreen.playlist(
        playlistId: id.substring(playlistPrefix.length),
      );
    }
    // Only the Library root links to sections from its own page.
    return switch (id) {
      'artists' => Scaffold(
        body: MornyeLibraryScreen(
          page: MornyeLibraryPage.artists,
          onOpenSection: (_) {},
        ),
      ),
      'albums' => Scaffold(
        body: MornyeLibraryScreen(
          page: MornyeLibraryPage.albums,
          onOpenSection: (_) {},
        ),
      ),
      'songs' => const Scaffold(body: QueueTab(librarySection: 'all')),
      'genres' => Scaffold(
        body: MornyeLibraryScreen(
          page: MornyeLibraryPage.genres,
          onOpenSection: (_) {},
        ),
      ),
      'folders' => const LibraryDirectoryScreen(),
      DesktopSidebarLink.allPlaylistsId => const Scaffold(
        body: QueueTab(librarySection: 'playlists'),
      ),
      _ => null,
    };
  }

  /// Shows a Library page or playlist picked in the Mornye desktop sidebar,
  /// or a playlist picked in the Material rail, in the Library tab, in place
  /// of the page it showed.
  void _openDesktopLibraryLink(String id) {
    final page = _desktopLibraryPage(id);
    // A link replaces the Library page at once, as in a source list, so the
    // Library root never shows on the way from another tab.
    void show(NavigatorState navigator) {
      navigator.popUntil((route) => route.isFirst);
      if (page != null) {
        unawaited(
          navigator.push(
            PageRouteBuilder<void>(
              transitionDuration: Duration.zero,
              reverseTransitionDuration: Duration.zero,
              pageBuilder: (_, _, _) => page,
            ),
          ),
        );
      }
      desktopSidebarLink.value = page == null ? null : id;
    }

    final navigator = _libraryTabNavigatorKey.currentState;
    if (navigator != null) show(navigator);
    _onShellTabRequested(ShellTab.library);
    if (navigator != null) return;
    // The Library navigator mounts with the tab on its first visit.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final navigator = _libraryTabNavigatorKey.currentState;
      if (mounted && navigator != null) show(navigator);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  /// Only the desktop sidebar has Library links.
  bool _requestDesktopLibraryLink(String id) {
    if (!mounted || !isDesktopPlatform) return false;
    _openDesktopLibraryLink(id);
    return true;
  }

  /// Opens the profile from the Mornye desktop sidebar's account row: in
  /// the Settings detail pane, or as a page when Settings shows only its
  /// list.
  void _openDesktopProfile() {
    final profile = ref.read(userProfileProvider).value;
    _onShellTabRequested(ShellTab.settings);
    if (profile == null) return;
    // Settings registers its detail pane once it has built.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final navigator =
          ShellNavigationService.activeBackNavigator() ?? Navigator.of(context);
      unawaited(
        navigator.push(
          MaterialPageRoute<void>(
            builder: (_) => MornyeSettingsTheme(
              child: ProfileSettingsPage(profile: profile),
            ),
          ),
        ),
      );
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  NavigatorState? _navigatorForTab(int index) => switch (_tabForIndex(index)) {
    ShellTab.home => _homeTabNavigatorKey.currentState,
    ShellTab.statistics => _statisticsTabNavigatorKey.currentState,
    ShellTab.library => _libraryTabNavigatorKey.currentState,
    ShellTab.search => _searchTabNavigatorKey.currentState,
    ShellTab.settings => null,
  };

  @override
  Widget build(BuildContext context) {
    ref.watch(
      playbackNotificationProvider((
        favorite: context.l10n.trackOptionAddToLoved,
        unfavorite: context.l10n.trackOptionRemoveFromLoved,
        shuffleOn: context.l10n.nowPlayingShuffleOn,
        shuffleOff: context.l10n.nowPlayingPlayInOrder,
      )),
    );
    ref.listen(settingsProvider, (_, settings) {
      ref.read(musicPlayerRuntimeProvider).configure(settings);
    });
    ShellNavigationService.syncState(tab: _tabForIndex(_currentIndex));

    final tabs = <Widget>[
      _TabNavigator(
        key: const ValueKey('tab-home'),
        navigatorKey: _homeTabNavigatorKey,
        child: const _HomeTabRoot(),
      ),
      _TabNavigator(
        key: const ValueKey('tab-statistics'),
        navigatorKey: _statisticsTabNavigatorKey,
        child: const ListeningStatisticsScreen(),
      ),
      _TabNavigator(
        key: const ValueKey('tab-library'),
        navigatorKey: _libraryTabNavigatorKey,
        observers: [if (isDesktopPlatform) _libraryDepthObserver],
        child: const _LibraryTabRoot(),
      ),
      _TabNavigator(
        key: const ValueKey('tab-search'),
        navigatorKey: _searchTabNavigatorKey,
        child: const HomeTab(mode: HomeTabMode.search),
      ),
      if (_mornyeSettingsTab) const SettingsTab(key: ValueKey('tab-settings')),
    ];

    final maxIndex = tabs.length - 1;
    final selectedDestination = _currentIndex.clamp(0, maxIndex);
    if (_currentIndex > maxIndex) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          setState(() => _currentIndex = maxIndex);
        }
      });
    }

    Widget observeScroll(Widget child, int index) =>
        NotificationListener<ScrollNotification>(
          onNotification: (notification) {
            // The chrome stays expanded while the keyboard is up.
            if (_canMinimizeChrome &&
                index == _currentIndex &&
                !_keyboardVisible) {
              return _mornyeChrome.handleScroll(notification);
            }
            return false;
          },
          child: child,
        );

    final pageView = KeyedSubtree(
      key: _pageViewKey,
      child: LazyTabView(
        index: selectedDestination,
        preloadKeys: isAndroidTv ? const {} : {const ValueKey('tab-search')},
        children: [
          for (var index = 0; index < tabs.length; index++)
            KeyedSubtree(
              key: tabs[index].key,
              child: observeScroll(tabs[index], index),
            ),
        ],
      ),
    );

    // From here only the navigation chrome follows the window size and the
    // badge counts. It gets the same tab view each time, so none of them
    // rebuilds the shell or the routes of its tabs; pages read them
    // themselves.
    if (isAndroidTv) {
      return BackButtonListener(
        onBackButtonPressed: () async {
          await _handleBackPress();
          return true;
        },
        child: Scaffold(
          body: Consumer(
            child: SelectionOverlayHost(child: pageView),
            builder: (context, ref, body) => TvShell(
              destinations: _navigationDestinations(),
              selectedIndex: selectedDestination,
              onSelected: _onNavTap,
              child: body!,
            ),
          ),
        ),
      );
    }

    if (isDesktopPlatform) {
      // Desktop windows: a source-list sidebar with a persistent player; no
      // bottom tabs or touch gestures.
      final selectedTab = _tabForIndex(selectedDestination);
      return Scaffold(
        body: Consumer(
          // The selection bar floats over the pages, not the sidebar.
          child: SelectionOverlayHost(child: pageView),
          builder: (context, ref, body) => DesktopShellLayout(
            destinations: _desktopDestinations(),
            selected: selectedTab,
            onSelected: _onShellTabRequested,
            libraryLinks: _desktopLibraryLinks(),
            // The Material rail opens playlists alone.
            onOpenLibraryLink: _openDesktopLibraryLink,
            onOpenProfile: _openDesktopProfile,
            body: body!,
          ),
        ),
      );
    }

    return SelectionOverlayHost(
      child: BackButtonListener(
        onBackButtonPressed: () async {
          await _handleBackPress();
          return true;
        },
        child: Consumer(
          child: pageView,
          builder: (context, ref, pageView) => _mobileScaffold(
            context,
            ref,
            pageView: pageView!,
            destinations: _navigationDestinations(),
            selectedDestination: selectedDestination,
          ),
        ),
      ),
    );
  }

  /// Phone, tablet and TV destinations.
  List<NavigationDestination> _navigationDestinations() {
    final l10n = context.l10n;
    return <NavigationDestination>[
      NavigationDestination(
        icon: const AppIcon(CupertinoIcons.house_fill),
        selectedIcon: BouncingIcon(child: const AppIcon(Icons.home)),
        label: l10n.navHome,
      ),
      NavigationDestination(
        icon: const AppIcon(CupertinoIcons.chart_bar_fill),
        selectedIcon: BouncingIcon(child: const AppIcon(Icons.insights)),
        label: l10n.navInsights,
      ),
      NavigationDestination(
        icon: const MornyeNavigationIcon(
          icon: CupertinoIcons.square_stack_fill,
          badgeCount: 0,
        ),
        selectedIcon: SlidingIcon(child: AppIcon(Icons.library_music)),
        label: l10n.navLibrary,
      ),
      NavigationDestination(
        icon: const Icon(CupertinoIcons.search),
        label: l10n.mornyeSearch,
      ),
      if (isAndroidTv)
        NavigationDestination(
          icon: const Icon(CupertinoIcons.gear_alt),
          label: l10n.navSettings,
        ),
    ];
  }

  /// The phone and tablet chrome around [pageView]: the bottom bar, or the
  /// rail in wide windows, and the mini player.
  Widget _mobileScaffold(
    BuildContext context,
    WidgetRef ref, {
    required Widget pageView,
    required List<NavigationDestination> destinations,
    required int selectedDestination,
  }) {
    final screenSize = MediaQuery.sizeOf(context);
    // Mornye keeps a bottom tab bar on tablets, compact and centered once the
    // window is expanded, and reserves the rail for desktop-width windows.
    final useNavigationRail = useNavigationRailForWidth(screenSize.width);
    final compactMornyeChrome = screenSize.width >= expandedLayoutBreakpoint;
    final canMinimizeChrome = _canMinimizeChromeAt(
      screenSize.width,
      MediaQuery.textScalerOf(context),
    );

    return Scaffold(
      extendBody: true,
      // The tab view keeps one element across the rail<->bar structure
      // swap via _pageViewKey so rotation doesn't remount tab navigators.
      body: useNavigationRail
          ? Row(
              children: [
                SafeArea(
                  right: false,
                  bottom: false,
                  // The rail needs ~300dp of height for four labeled
                  // destinations; on short viewports it must scroll, not
                  // overflow.
                  child: LayoutBuilder(
                    builder: (context, constraints) => SingleChildScrollView(
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          minHeight: constraints.maxHeight,
                        ),
                        child: IntrinsicHeight(
                          child: NavigationRail(
                            selectedIndex: selectedDestination,
                            onDestinationSelected: _onNavTap,
                            labelType: NavigationRailLabelType.all,
                            backgroundColor: Theme.of(
                              context,
                            ).colorScheme.surfaceContainer,
                            destinations: [
                              for (final destination in destinations)
                                NavigationRailDestination(
                                  icon: destination.icon,
                                  selectedIcon: destination.selectedIcon,
                                  label: Text(destination.label),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                const VerticalDivider(width: 1),
                // Width-based layout (centered columns, grids) measures
                // the space beside the rail rather than the whole screen.
                Expanded(
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final mediaQuery = MediaQuery.of(context);
                      return MediaQuery(
                        data: mediaQuery
                            .copyWith(
                              size: Size(
                                constraints.maxWidth,
                                mediaQuery.size.height,
                              ),
                            )
                            .removePadding(removeLeft: true),
                        child: pageView,
                      );
                    },
                  ),
                ),
              ],
            )
          : pageView,
      bottomNavigationBar: Builder(
        builder: (context) {
          return ListenableBuilder(
            listenable: Listenable.merge([
              ShellNavigationService.chromeBrightness,
              ShellNavigationService.chromeSurface,
            ]),
            builder: (context, child) => Theme(
              data: ShellNavigationService.chromeBrightness.value == null
                  ? Theme.of(context)
                  : MornyeTheme.fromContext(
                      context,
                      brightness:
                          ShellNavigationService.chromeBrightness.value!,
                      chromeSurface: ShellNavigationService.chromeSurface.value,
                    ),
              child: child!,
            ),
            child: SafeArea(
              top: false,
              minimum: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              // iPadOS-style chrome: one compact, centered pill rather
              // than a bar spread across a landscape tablet.
              child: Center(
                heightFactor: 1,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: compactMornyeChrome ? 600 : double.infinity,
                  ),
                  child: useNavigationRail
                      ? const MiniPlayer()
                      : ValueListenableBuilder<bool>(
                          valueListenable: _mornyeChrome,
                          builder: (context, collapsed, _) => MornyeBottomBar(
                            collapsed:
                                canMinimizeChrome &&
                                collapsed &&
                                MediaQuery.viewInsetsOf(context).bottom == 0,
                            destinations: destinations,
                            selectedIndex: selectedDestination,
                            onSelected: _onNavTap,
                            onHome: () => _onNavTap(0),
                            onSearch: ShellNavigationService.requestSearch,
                            blurEnabled: ref.watch(mornyeBlurEnabledProvider),
                          ),
                        ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _TabNavigator extends StatefulWidget {
  final GlobalKey<NavigatorState> navigatorKey;
  final Widget child;
  final List<NavigatorObserver> observers;

  const _TabNavigator({
    super.key,
    required this.navigatorKey,
    required this.child,
    this.observers = const [],
  });

  @override
  State<_TabNavigator> createState() => _TabNavigatorState();
}

class _TabNavigatorState extends State<_TabNavigator> {
  late final ShellChromeObserver _chromeObserver = ShellChromeObserver(
    widget.navigatorKey,
  );
  // Nested navigators get no HeroController from MaterialApp; without one,
  // Hero widgets on routes pushed inside a tab never fly.
  final HeroController _heroController =
      MaterialApp.createMaterialHeroController();
  final _nowPlayingCloser = DesktopNowPlayingCloser();

  /// The navigator as last built. A [Navigator] that is built again builds
  /// the page of every route on it again, so every tab's pages would follow
  /// each switch of tab; the same one is handed back until what it is made
  /// from changes.
  Navigator? _navigator;

  @override
  void didUpdateWidget(_TabNavigator oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.child, oldWidget.child) ||
        widget.navigatorKey != oldWidget.navigatorKey ||
        !listEquals(widget.observers, oldWidget.observers)) {
      _navigator = null;
    }
  }

  @override
  void reassemble() {
    super.reassemble();
    _navigator = null;
  }

  @override
  void dispose() {
    _chromeObserver.detach();
    _heroController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _navigator ??= Navigator(
      key: widget.navigatorKey,
      routeDirectionalTraversalEdgeBehavior: isAndroidTv
          ? TraversalEdgeBehavior.parentScope
          : TraversalEdgeBehavior.stop,
      observers: [
        _chromeObserver,
        _heroController,
        if (isDesktopPlatform) _nowPlayingCloser,
        ...widget.observers,
      ],
      onGenerateInitialRoutes: (_, _) => [
        MaterialPageRoute<void>(
          requestFocus: !isAndroidTv,
          builder: (_) => widget.child,
        ),
      ],
    );
  }
}

/// Counts the pages in a tab navigator for the desktop shell. Navigators
/// push their first page while building, so [depth] and [onRoot] follow
/// after the frame rather than marking the shell dirty mid-build.
class _PageDepthObserver extends NavigatorObserver {
  _PageDepthObserver({this.onRoot});

  final VoidCallback? onRoot;
  final depth = ValueNotifier<int>(0);
  int _pages = 0;
  bool _scheduled = false;
  bool _disposed = false;

  void _change(int delta) {
    _pages += delta;
    if (_scheduled) return;
    _scheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduled = false;
      if (_disposed) return;
      depth.value = _pages;
      if (_pages <= 1) onRoot?.call();
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  static int _pageCount(Route<dynamic>? route) =>
      route is PageRoute<dynamic> ? 1 : 0;

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _change(_pageCount(route));

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _change(-_pageCount(route));

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _change(-_pageCount(route));

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) =>
      _change(_pageCount(newRoute) - _pageCount(oldRoute));

  void dispose() {
    _disposed = true;
    depth.dispose();
  }
}

class _HomeTabRoot extends StatelessWidget {
  const _HomeTabRoot();

  @override
  Widget build(BuildContext context) => HomeTab(mode: HomeTabMode.browse);
}

/// Library's root page: one [QueueTab] for the life of the tab. A switch of
/// tab changes what it listens to and builds nothing of the Library again.
class _LibraryTabRoot extends StatelessWidget {
  const _LibraryTabRoot();

  @override
  Widget build(BuildContext context) => const QueueTab();
}
