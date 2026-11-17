import 'dart:async';
import 'dart:io';
import 'package:sistrum/controllers/open_audio_controller.dart';
import 'package:sistrum/services/audio_file_open_service.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/services/saf_migration_prompt_store.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/library_tag_credits_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/network_upload_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/playback_notification_provider.dart';
import 'package:sistrum/providers/repo_provider.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/screens/home_tab.dart';
import 'package:sistrum/screens/repo_tab.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/screens/settings/settings_tab.dart';
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/services/music_playback_deck.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/services/notification_service.dart';
import 'package:sistrum/services/update_checker.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/download_queue_feedback.dart';
import 'package:sistrum/widgets/update_dialog.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/nav_destination_icons.dart';
import 'package:sistrum/widgets/mini_player.dart';
import 'package:sistrum/widgets/lazy_tab_view.dart';
import 'package:sistrum/widgets/mornye_bottom_bar.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/mornye_chrome.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/saf_download_tree.dart';

part 'main_shell_startup_prompts.dart';

final _log = AppLogger('MainShell');

/// The badge counts on Library (queued downloads) and Store (extension
/// updates). Only the navigation chrome watches them, so a finished
/// download or an update check does not rebuild every tab.
int _watchQueuedCount(WidgetRef ref) =>
    ref.watch(downloadQueueProvider.select((s) => s.queuedCount));

int _watchRepoUpdatesCount(WidgetRef ref) =>
    ref.watch(repoProvider.select((s) => s.updatesAvailableCount));

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell>
    with WidgetsBindingObserver {
  int _currentIndex = 0;
  final _mornyeChrome = MornyeChromeController();
  // Preserves the tab view element (and its kept-alive tabs) when the body
  // structure swaps between rail and bottom-bar layouts on rotation.
  final GlobalKey _pageViewKey = GlobalKey();
  bool _hasCheckedUpdate = false;
  bool _initialSafRepairComplete = false;
  bool _safRepairDialogVisible = false;
  bool _usbVolumeWarningShown = false;
  StreamSubscription<List<OpenAudioFile>>? _audioOpenSubscription;
  DateTime? _lastBackPress;
  final GlobalKey<NavigatorState> _homeTabNavigatorKey =
      ShellNavigationService.homeTabNavigatorKey;
  final GlobalKey<NavigatorState> _libraryTabNavigatorKey =
      ShellNavigationService.libraryTabNavigatorKey;
  final GlobalKey<NavigatorState> _repoTabNavigatorKey =
      ShellNavigationService.repoTabNavigatorKey;
  final GlobalKey<NavigatorState> _searchTabNavigatorKey =
      ShellNavigationService.searchTabNavigatorKey;

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
    playback.configureTagCredits(ref.read(tagCreditScopeProvider));
    listeningRecorder.setEnabled(settings.listeningStatisticsEnabled);
    // Deezer & co. localize artist/genre names by IP unless told the app's
    // language (issue #480).
    unawaited(PlatformBridge.setMetadataLanguage(languageTag));
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    usbAudioStatus.addListener(_onUsbAudioStatusChanged);
    ShellNavigationService.registerTabSelectionHandler(
      owner: this,
      handler: _onShellTabRequested,
    );
    ShellNavigationService.syncState(
      currentTabIndex: _currentIndex,
      showRepoTab: false,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _repairSafAccessIfNeeded(
        knownLost: ref.read(initialSafAccessLostProvider),
      );
      _initialSafRepairComplete = true;
      if (!mounted) return;
      unawaited(_restorePlayback());
      // Library menus are built synchronously and offer an upload only once
      // the writable connections are known.
      ref.read(networkUploadConnectionsProvider);
      _setupAudioOpenListener();
      await _checkSafMigration();
      if (!mounted) return;
      await _checkForUpdates();
    });
  }

  /// Restores the last session, then its song's cover from the file once it is
  /// available.
  Future<void> _restorePlayback() async {
    final runtime = ref.read(musicPlayerRuntimeProvider);
    try {
      await runtime.restoreSession();
      await ref.read(extensionProvider.notifier).ensureInitialized();
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
    if (state == AppLifecycleState.resumed && _initialSafRepairComplete) {
      unawaited(_repairSafAccessIfNeeded());
    } else if (state == AppLifecycleState.paused) {
      unawaited(
        ref.read(musicPlayerRuntimeProvider).handler?.persistCurrentSession(),
      );
    }
  }

  void _setupAudioOpenListener() {
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
    super.dispose();
  }

  void _resetHomeToMain() {
    final showStore = ref.read(
      settingsProvider.select((s) => s.showExtensionStore),
    );
    final homeNavigator = _navigatorForTab(0, showStore);
    homeNavigator?.popUntil((route) => route.isFirst);
    FocusManager.instance.primaryFocus?.unfocus();
  }

  void _onShellTabRequested(ShellTab tab) {
    // Picking a destination returns from Now Playing to the content pane.
    if (tab == ShellTab.settings) {
      unawaited(_openProfileSettings());
      return;
    }
    final showStore = ref.read(
      settingsProvider.select((s) => s.showExtensionStore),
    );
    final index = switch (tab) {
      ShellTab.home => 0,
      ShellTab.search => (showStore ? 3 : 2),
      ShellTab.library => 1,
      ShellTab.repository => showStore ? 2 : null,
      ShellTab.settings => null,
    };
    if (index != null) _onNavTap(index, resetHome: tab != ShellTab.search);
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
    final showStore = ref.read(
      settingsProvider.select((s) => s.showExtensionStore),
    );
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
      ShellNavigationService.syncState(
        currentTabIndex: _currentIndex,
        showRepoTab: showStore,
      );
      FocusManager.instance.primaryFocus?.unfocus();
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
      !useNavigationRailForWidth(width) && textScaler.scale(15) <= 20;

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

    final showStore = ref.read(
      settingsProvider.select((s) => s.showExtensionStore),
    );
    final currentNavigator = _navigatorForTab(_currentIndex, showStore);
    final handledByCurrentNavigator =
        await currentNavigator?.maybePop() ?? false;
    if (handledByCurrentNavigator) {
      _lastBackPress = null;
      return;
    }

    if (!mounted) return;

    final isSearchTab = _currentIndex == ((showStore ? 3 : 2));

    if (isSearchTab && ref.read(homeSearchTextProvider).isNotEmpty) {
      FocusManager.instance.primaryFocus?.unfocus();
      ref.read(homeSearchTextProvider.notifier).set('');
      _lastBackPress = null;
      return;
    }

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

  NavigatorState? _navigatorForTab(int index, bool showStore) {
    if (index == 0) return _homeTabNavigatorKey.currentState;
    if (index == 1) return _libraryTabNavigatorKey.currentState;
    if (showStore && index == 2) return _repoTabNavigatorKey.currentState;
    if (index == (showStore ? 3 : 2)) {
      return _searchTabNavigatorKey.currentState;
    }
    return null;
  }

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
    ref.listen(
      tagCreditScopeProvider,
      (_, scope) =>
          ref.read(musicPlayerRuntimeProvider).configureTagCredits(scope),
    );
    ref.listen(
      settingsProvider.select((s) => s.listeningStatisticsEnabled),
      (_, enabled) => listeningRecorder.setEnabled(enabled),
    );
    final showStore = ref.watch(
      settingsProvider.select((s) => s.showExtensionStore),
    );
    final heroAnimationsEnabled = ref.watch(
      settingsProvider.select((s) => s.heroAnimationsEnabled),
    );
    ShellNavigationService.syncState(
      currentTabIndex: _currentIndex,
      showRepoTab: showStore,
    );

    final tabs = <Widget>[
      _TabNavigator(
        key: const ValueKey('tab-home'),
        navigatorKey: _homeTabNavigatorKey,
        heroAnimationsEnabled: heroAnimationsEnabled,
        child: Builder(builder: (context) => HomeTab(mode: HomeTabMode.browse)),
      ),
      _TabNavigator(
        key: const ValueKey('tab-library'),
        navigatorKey: _libraryTabNavigatorKey,
        heroAnimationsEnabled: heroAnimationsEnabled,
        child: const _LibraryTabRoot(),
      ),
      if (showStore)
        _TabNavigator(
          key: const ValueKey('tab-repo'),
          navigatorKey: _repoTabNavigatorKey,
          heroAnimationsEnabled: heroAnimationsEnabled,
          child: const RepoTab(),
        ),
      _TabNavigator(
        key: const ValueKey('tab-search'),
        navigatorKey: _searchTabNavigatorKey,
        heroAnimationsEnabled: heroAnimationsEnabled,
        child: const HomeTab(mode: HomeTabMode.search),
      ),
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
        preloadKeys: {const ValueKey('tab-search')},
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
    final shell = SelectionOverlayHost(
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
            destinations: _navigationDestinations(
              queuedCount: _watchQueuedCount(ref),
              repoUpdatesCount: _watchRepoUpdatesCount(ref),
              showStore: showStore,
            ),
            selectedDestination: selectedDestination,
            maxIndex: maxIndex,
          ),
        ),
      ),
    );
    return DownloadQueueFeedback(child: shell);
  }

  /// Phone and tablet destinations, with the download count on Library.
  List<NavigationDestination> _navigationDestinations({
    required int queuedCount,
    required int repoUpdatesCount,
    required bool showStore,
  }) {
    final l10n = context.l10n;
    return <NavigationDestination>[
      NavigationDestination(
        icon: AppIcon(CupertinoIcons.house_fill),
        selectedIcon: BouncingIcon(child: const AppIcon(Icons.home)),
        label: l10n.navHome,
      ),
      NavigationDestination(
        icon: MornyeNavigationIcon(
          icon: CupertinoIcons.square_stack_fill,
          badgeCount: queuedCount,
        ),
        selectedIcon: SlidingIcon(
          child: AnimatedBadge(
            count: queuedCount,
            child: Badge(
              isLabelVisible: queuedCount > 0,
              label: Text('$queuedCount'),
              child: const AppIcon(Icons.library_music),
            ),
          ),
        ),
        label: l10n.navLibrary,
      ),
      if (showStore)
        NavigationDestination(
          icon: MornyeNavigationIcon(
            icon: CupertinoIcons.square_grid_2x2,
            badgeCount: repoUpdatesCount,
          ),
          selectedIcon: BouncingIcon(
            child: AnimatedBadge(
              count: repoUpdatesCount,
              child: Badge(
                isLabelVisible: repoUpdatesCount > 0,
                label: Text('$repoUpdatesCount'),
                child: const AppIcon(Icons.extension),
              ),
            ),
          ),
          label: l10n.navStore,
        ),
      NavigationDestination(
        icon: const Icon(CupertinoIcons.search),
        label: l10n.sistrumSearch,
      ),
    ];
  }

  /// The phone and tablet chrome around [pageView]: the bottom bar, or the
  Widget _mobileScaffold(
    BuildContext context,
    WidgetRef ref, {
    required Widget pageView,
    required List<NavigationDestination> destinations,
    required int selectedDestination,
    required int maxIndex,
  }) {
    final screenSize = MediaQuery.sizeOf(context);
    // Mornye keeps a bottom tab bar on tablets, compact and centered once
    // the window is expanded.
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
  final bool heroAnimationsEnabled;

  const _TabNavigator({
    super.key,
    required this.navigatorKey,
    required this.child,
    required this.heroAnimationsEnabled,
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

  @override
  void dispose() {
    _chromeObserver.detach();
    _heroController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Navigator(
      key: widget.navigatorKey,
      routeDirectionalTraversalEdgeBehavior: TraversalEdgeBehavior.stop,
      observers: [
        _chromeObserver,
        if (widget.heroAnimationsEnabled) _heroController,
      ],
      onGenerateInitialRoutes: (_, _) => [
        MaterialPageRoute<void>(builder: (_) => widget.child),
      ],
    );
  }
}

class _LibraryTabRoot extends StatelessWidget {
  const _LibraryTabRoot();

  @override
  Widget build(BuildContext context) {
    return MornyeLibraryScreen(
      onOpenSection: (section) => pushPage<void>(
        context,
        (_) => Scaffold(body: QueueTab(librarySection: section)),
      ),
    );
  }
}
