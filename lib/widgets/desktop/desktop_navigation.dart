import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/user_profile_provider.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/services/user_profile_store.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/desktop/desktop_commands.dart';
import 'package:sistrum/widgets/desktop/desktop_inspector.dart';
import 'package:sistrum/widgets/desktop/desktop_now_playing.dart';
import 'package:sistrum/widgets/desktop/desktop_player_bar.dart';
import 'package:sistrum/widgets/desktop/desktop_window.dart';
import 'package:sistrum/widgets/profile_avatar.dart';
import 'package:sistrum/widgets/no_scrollbars.dart';

/// A Library page or playlist in the Mornye sidebar.
class DesktopSidebarLink {
  const DesktopSidebarLink({
    required this.id,
    required this.icon,
    required this.label,
    this.badgeCount = 0,
  });

  /// Identifies the link to [DesktopShellLayout.onOpenLibraryLink] and
  /// [desktopSidebarLink]. Playlists use [playlistId].
  final String id;
  final IconData icon;
  final String label;
  final int badgeCount;

  /// The link id of the user playlist [id].
  static String playlistId(String id) => 'playlist:$id';

  /// The link that opens the list of every playlist.
  static const allPlaylistsId = 'playlists';

  /// The Library root, which the sidebar's Library link opens while a link is
  /// open.
  static const libraryRootId = 'library';
}

/// The Library link open in the Mornye sidebar, or null for the Library
/// root, which the first Library link stands for.
final desktopSidebarLink = ValueNotifier<String?>(null);

/// The user's playlists listed in the Mornye sidebar.
final desktopSidebarPlaylistsProvider = Provider<List<UserPlaylistCollection>>(
  (ref) => ref.watch(libraryCollectionsProvider.select((s) => s.playlists)),
);

/// The profile in the Mornye sidebar's account row, once it has loaded.
final desktopSidebarProfileProvider = Provider<UserProfile?>(
  (ref) => ref.watch(userProfileProvider).value,
);

/// A top-level desktop destination.
class DesktopDestination {
  const DesktopDestination({
    required this.tab,
    required this.icon,
    required this.selectedIcon,
    required this.label,
    this.badgeCount = 0,
  });

  final ShellTab tab;
  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final int badgeCount;
}

/// The desktop shell: navigation on the leading edge, the content pane and
/// the trailing inspector.
///
/// [DesktopSidebar] runs the full window height, and the player floats as a
/// glass capsule at the bottom of the content pane, which pads its pages so
/// their last rows scroll clear of it.
///
/// On macOS the window content reaches under a transparent title bar
/// ([desktopTitleBarInContent]): the traffic lights sit over the top of the
/// navigation, and the top [desktopTitleBarHeight] of the content pane drags
/// the window.
///
/// Now Playing covers the whole window while [desktopNowPlayingOpen] is
/// set. Lyrics and Up Next ([desktopInspectorPanel]) sit beside the content
/// from [desktopInspectorBesideBreakpoint] and float over it below that.
class DesktopShellLayout extends ConsumerWidget {
  const DesktopShellLayout({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    required this.body,
    this.libraryLinks = const [],
    this.onOpenLibraryLink,
    this.onOpenProfile,
    this.playerBar = const DesktopPlayerBar(),
    this.nowPlaying = const DesktopNowPlaying(),
  });

  /// Destinations in [desktopDestinationTabs] order; Cmd/Ctrl+1..5 follow it.
  final List<DesktopDestination> destinations;
  final ShellTab? selected;
  final ValueChanged<ShellTab> onSelected;
  final Widget body;

  /// Mornye: the Library pages listed under the sidebar's Library heading.
  /// The first stands for the Library destination itself.
  final List<DesktopSidebarLink> libraryLinks;

  /// Opens a Library link or a playlist from the sidebar.
  final ValueChanged<String>? onOpenLibraryLink;

  /// Mornye: opens the profile from the sidebar's account row.
  final VoidCallback? onOpenProfile;
  final Widget playerBar;
  final Widget nowPlaying;

  /// The mini player ([desktopMiniPlayerOpen]) replaces the whole window.
  /// The shell stays mounted behind it, in the same place in the tree and at
  /// the last full window size, so its pages keep their layout and state
  /// while the window is small.
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final hasTrack = ref.watch(
      currentMediaItemProvider.select((state) => state.value != null),
    );
    return ValueListenableBuilder<bool>(
      valueListenable: desktopPlayerDismissed,
      builder: (context, dismissed, _) => _window(!dismissed || hasTrack),
    );
  }

  Widget _window(bool show) => ValueListenableBuilder<bool>(
    valueListenable: desktopMiniPlayerOpen,
    builder: (context, mini, window) {
      mini = mini && show;
      final mediaQuery = MediaQuery.of(context);
      if (!mini) _fullWindowSize = mediaQuery.size;
      final full = mini ? _fullWindowSize : null;
      // Follow native resize frames instead of running a second animation
      // clock that could finish before AppKit or a slow Flutter frame.
      final span = full == null
          ? 0.0
          : full.width - desktopMiniPlayerSize.width;
      final progress = !mini
          ? 0.0
          : span <= 0
          ? 1.0
          : ((full!.width - mediaQuery.size.width) / span).clamp(0.0, 1.0);
      final fade = Curves.easeInOut.transform(progress);
      return ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            Visibility(
              visible: progress < 1,
              maintainState: true,
              child: IgnorePointer(
                ignoring: mini,
                child: ExcludeSemantics(
                  excluding: mini,
                  child: Opacity(
                    key: const ValueKey('desktop-mini-player-shell-fade'),
                    opacity: 1 - fade,
                    child: RepaintBoundary(
                      child: OverflowBox(
                        alignment: Alignment.topLeft,
                        minWidth: full?.width,
                        maxWidth: full?.width,
                        minHeight: full?.height,
                        maxHeight: full?.height,
                        child: MediaQuery(
                          data: full == null
                              ? mediaQuery
                              : mediaQuery.copyWith(size: full),
                          child: window!,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            if (mini)
              IgnorePointer(
                ignoring: progress < 1,
                child: Opacity(
                  key: const ValueKey('desktop-mini-player-fade'),
                  opacity: fade,
                  child: Align(
                    alignment: Alignment.topRight,
                    child: SizedBox(
                      width: desktopMiniPlayerSize.width,
                      child: const KeyedSubtree(
                        key: ValueKey('desktop-mini-player'),
                        child: DesktopMiniPlayer(),
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      );
    },
    child: Builder(builder: (context) => _nowPlayingOrShell(context, show)),
  );

  /// The window's size before the mini player shrank it.
  static Size? _fullWindowSize;

  /// Now Playing covers the whole window. The destinations keep their state
  /// underneath, but stop painting and ticking once it has opened.
  Widget _nowPlayingOrShell(BuildContext context, bool showPlayer) =>
      ValueListenableBuilder<bool>(
        valueListenable: desktopNowPlayingOpen,
        builder: (context, nowPlayingOpen, shell) => _NowPlayingTransition(
          open: nowPlayingOpen && showPlayer,
          shell: shell!,
          nowPlaying: nowPlaying,
        ),
        child: Builder(builder: (context) => _shell(context, showPlayer)),
      );

  Widget _shell(BuildContext context, bool showPlayer) {
    final width = MediaQuery.sizeOf(context).width;
    // The sidebar always shows, full height against the window's edges.
    return ColoredBox(
      color: Theme.of(context).colorScheme.surface,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          DesktopSidebar(
            destinations: destinations,
            selected: selected,
            onSelected: onSelected,
            libraryLinks: libraryLinks,
            onOpenLibraryLink: onOpenLibraryLink,
            onOpenProfile: onOpenProfile,
          ),
          Expanded(
            key: const ValueKey('desktop-content'),
            child: _DesktopContentArea(
              windowWidth: width,
              body: body,
              floatingPlayer: showPlayer ? playerBar : null,
              showPlayer: showPlayer,
              titleBarDrag: true,
            ),
          ),
        ],
      ),
    );
  }
}

/// Opens and closes the full-window Now Playing over [shell]. It dissolves in
/// on the effects spring and exits with a shorter eased fade. The shell keeps
/// painting until the entrance ends and Now Playing stays mounted until the
/// exit ends, so either can reverse midway (Esc during the entrance). Reduced
/// motion swaps at once.
class _NowPlayingTransition extends StatefulWidget {
  const _NowPlayingTransition({
    required this.open,
    required this.shell,
    required this.nowPlaying,
  });

  final bool open;
  final Widget shell;
  final Widget nowPlaying;

  @override
  State<_NowPlayingTransition> createState() => _NowPlayingTransitionState();
}

class _NowPlayingTransitionState extends State<_NowPlayingTransition>
    with SingleTickerProviderStateMixin {
  /// The resting value of [_fade] before the first entrance. The entrance
  /// itself is the style's effects spring; reduced motion opens and closes
  /// at once.
  static const _initialEntrance = AppMotionSpec(
    Duration(milliseconds: 220),
    Curves.easeOutCubic,
  );

  late final AnimationController _controller = AnimationController(
    vsync: this,
    value: widget.open ? 1 : 0,
  );

  // The entrance in use.
  AppMotionSpec _fade = _initialEntrance;

  // Where the exit started, so Esc during the entrance fades out from
  // there instead of jumping to the exit's start.
  double _exitFrom = 1;
  double _exitOpacity = 1;

  @override
  void didUpdateWidget(_NowPlayingTransition oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.open == oldWidget.open) return;
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.value = widget.open ? 1 : 0;
      return;
    }
    if (widget.open) {
      _fade = AppMotion.effects(context, _initialEntrance);
      _controller.duration = _fade.duration;
      _controller.forward();
    } else {
      _exitFrom = _controller.value;
      _exitOpacity = _entranceOpacity(_exitFrom);
      _controller.reverseDuration = const Duration(milliseconds: 160);
      _controller.reverse();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  double _entranceOpacity(double t) => _fade.curve.transform(t.clamp(0.0, 1.0));

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) {
      final t = _controller.value;
      final exiting = !widget.open;
      final opacity = exiting
          ? _exitFrom <= 0
                ? 0.0
                : _exitOpacity *
                      (1 - Curves.easeInCubic.transform(1 - t / _exitFrom))
          : _entranceOpacity(t);
      final covered = widget.open && _controller.isCompleted;
      return Stack(
        fit: StackFit.expand,
        children: [
          Visibility(
            visible: !covered,
            maintainState: true,
            // Focus leaves the shell as soon as Now Playing opens, and leaves
            // Now Playing as soon as it closes, as without the transition, so
            // a field left focused underneath cannot take Space meanwhile.
            child: IgnorePointer(
              ignoring: widget.open,
              child: ExcludeFocus(excluding: widget.open, child: widget.shell),
            ),
          ),
          if (widget.open || !_controller.isDismissed)
            IgnorePointer(
              ignoring: exiting,
              child: ExcludeFocus(
                excluding: exiting,
                child: Opacity(
                  key: const ValueKey('desktop-now-playing-fade'),
                  opacity: opacity.clamp(0.0, 1.0),
                  child: KeyedSubtree(
                    key: const ValueKey('desktop-now-playing'),
                    child: widget.nowPlaying,
                  ),
                ),
              ),
            ),
        ],
      );
    },
  );
}

/// Gives [child] the size of the space it lays out in, so width-based layout
/// (centered columns, grids) measures the pane rather than the whole window.
/// [bottomInset] becomes the bottom padding, which pages add after their
/// last row ([NavBarInset]) so it scrolls clear of the floating player.
class _PaneMediaQuery extends StatelessWidget {
  const _PaneMediaQuery({required this.child, this.bottomInset = 0});

  final Widget child;
  final double bottomInset;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final mediaQuery = MediaQuery.of(context);
      var data = mediaQuery
          .copyWith(size: Size(constraints.maxWidth, constraints.maxHeight))
          .removePadding(removeLeft: true, removeBottom: true);
      if (bottomInset > 0) {
        data = data.copyWith(
          padding: data.padding.copyWith(bottom: bottomInset),
          viewPadding: data.viewPadding.copyWith(bottom: bottomInset),
        );
      }
      return MediaQuery(data: data, child: child);
    },
  );
}

/// The narrowest pane that fits the Mornye capsule beside the glass inspector.
const double _capsuleBesideInspectorWidth = mornyeGlassInspectorWidth + 520;

/// The content pane, the trailing inspector and, in Mornye, the floating
/// player. Pages bring their own search fields (Home, Library), so there is
/// no shell search bar above them.
class _DesktopContentArea extends StatelessWidget {
  const _DesktopContentArea({
    required this.windowWidth,
    required this.body,
    this.floatingPlayer,
    this.showPlayer = true,
    this.titleBarDrag = false,
  });

  final double windowWidth;
  final Widget body;

  /// The Mornye player capsule, floating over the bottom of the pane.
  final Widget? floatingPlayer;
  final bool showPlayer;

  /// Whether the top of the pane is the window's title bar row, where a
  /// drag moves the window.
  final bool titleBarDrag;

  @override
  Widget build(BuildContext context) {
    final player = floatingPlayer;
    final playerInset = player == null ? 0.0 : DesktopPlayerBar.floatingInset;
    return ListenableBuilder(
      listenable: Listenable.merge([
        desktopInspectorPanel,
        desktopWindowFullScreen,
      ]),
      builder: (context, _) {
        final panel = showPlayer ? desktopInspectorPanel.value : null;
        final overlayInspector = desktopInspectorOverlays(windowWidth);
        final content = LayoutBuilder(
          builder: (context, pane) {
            // The glass runs the window's height and the capsule moves
            // beside it, while the pane has room for the two.
            final beside =
                overlayInspector &&
                pane.maxWidth >= _capsuleBesideInspectorWidth;
            final motion = desktopInspectorMotion(context);
            return Stack(
              fit: StackFit.expand,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // A semantics container keeps the tab navigators'
                    // modal barriers from hiding the sidebar.
                    Expanded(
                      key: const ValueKey('desktop-pages'),
                      child: Semantics(
                        container: true,
                        child: _PaneMediaQuery(
                          bottomInset: playerInset,
                          child: body,
                        ),
                      ),
                    ),
                  ],
                ),
                if (player != null)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: DesktopPlayerBar.floatingMargin,
                    child: AnimatedPadding(
                      duration: MediaQuery.disableAnimationsOf(context)
                          ? Duration.zero
                          : motion.duration,
                      curve: motion.curve,
                      padding: EdgeInsetsDirectional.only(
                        end: beside && panel != null
                            ? mornyeGlassInspectorWidth
                            : 0,
                      ),
                      child: LayoutBuilder(
                        builder: (context, constraints) => Center(
                          child: SizedBox(
                            width: DesktopPlayerBar.floatingWidth(
                              constraints.maxWidth,
                            ),
                            child: player,
                          ),
                        ),
                      ),
                    ),
                  ),
                // A narrow window's inspector floats above the player.
                PositionedDirectional(
                  top: 0,
                  bottom: beside ? 0 : playerInset,
                  end: 0,
                  width: mornyeGlassInspectorWidth,
                  child: DesktopInspectorTransition(
                    key: const ValueKey('desktop-inspector-overlay'),
                    panel: overlayInspector ? panel : null,
                    overlay: true,
                    builder: (context, panel) =>
                        DesktopInspector(panel: panel, overlay: true),
                  ),
                ),
              ],
            );
          },
        );
        // One structure for every state, so opening the inspector never
        // remounts the destinations.
        final pane = Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: content),
            DesktopInspectorTransition(
              key: const ValueKey('desktop-inspector-beside'),
              panel: overlayInspector ? null : panel,
              builder: (context, panel) => DesktopInspector(panel: panel),
            ),
          ],
        );
        if (!titleBarDrag || !desktopTitleBarInContentFor(context)) return pane;
        // Pages keep their clicks in the title bar row; a drag that
        // starts on it moves the window.
        return Stack(
          fit: StackFit.expand,
          children: [
            pane,
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              height: desktopTitleBarHeightFor(context),
              child: const DesktopWindowDragArea(),
            ),
          ],
        );
      },
    );
  }
}

int _positionOf(List<DesktopDestination> destinations, ShellTab tab) =>
    destinations.indexWhere((destination) => destination.tab == tab) + 1;

/// The Mornye source-list sidebar: a rounded panel
/// inset from the window edges, with the window controls and the sidebar
/// button on top, then Search and the top-level destinations, the Library
/// pages and the user's playlists under section headings, and the account
/// row pinned at the bottom with Settings beside it. The panel takes a tint
/// from the page's artwork color when the page sets one. The sidebar
/// button, View > Hide Sidebar and Control-Command-S hide it completely.
///
/// Without [onOpenLibraryLink] it lists the destinations alone, Library
/// included.
class DesktopSidebar extends ConsumerWidget {
  const DesktopSidebar({
    super.key,
    required this.destinations,
    required this.selected,
    required this.onSelected,
    this.libraryLinks = const [],
    this.onOpenLibraryLink,
    this.onOpenProfile,
  });

  /// Width of the panel.
  static const double width = 226;

  final List<DesktopDestination> destinations;
  final ShellTab? selected;
  final ValueChanged<ShellTab> onSelected;

  /// The Library pages under the Library heading. The first stands for the
  /// Library destination and takes its place and shortcut.
  final List<DesktopSidebarLink> libraryLinks;

  /// Opens a Library link, All Playlists or a playlist by its link id.
  final ValueChanged<String>? onOpenLibraryLink;

  /// Opens the profile from the account row. Without it the row opens
  /// Settings.
  final VoidCallback? onOpenProfile;

  /// The panel fill: a step off the page background, tinted by the page's
  /// artwork color ([ShellNavigationService.chromeSurface]) when it has one.
  static Color panelColor(BuildContext context, Color? tint) {
    final scheme = Theme.of(context).colorScheme;
    final dark = scheme.brightness == Brightness.dark;
    final base = dark ? const Color(0xff1e1e20) : const Color(0xffefeff3);
    if (tint == null) return base;
    return Color.lerp(base, tint, dark ? 0.32 : 0.18)!;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final openLink = onOpenLibraryLink;
    final links = openLink == null
        ? const <DesktopSidebarLink>[]
        : libraryLinks;
    final playlists = openLink == null
        ? const <UserPlaylistCollection>[]
        : ref.watch(desktopSidebarPlaylistsProvider);
    Widget destinationRow(DesktopDestination destination) {
      final search = destination.tab == ShellTab.search;
      return _SidebarRow(
        // Search focuses its field, as Command-F does.
        key: search ? const ValueKey('desktop-sidebar-search') : null,
        icon: destination.icon,
        label: destination.label,
        badgeCount: destination.badgeCount,
        selected: destination.tab == selected,
        tooltip: search
            ? desktopTooltip(context, destination.label, DesktopCommand.search)
            : desktopDestinationTooltip(
                context,
                destination.label,
                _positionOf(destinations, destination.tab),
              ),
        onTap: search
            ? ShellNavigationService.requestSearch
            : () => onSelected(destination.tab),
      );
    }

    // Search leads the list.
    final topLevel = [
      for (final tab in [ShellTab.search, ShellTab.home, ShellTab.statistics])
        ...destinations.where((destination) => destination.tab == tab),
      if (links.isEmpty)
        ...destinations.where(
          (destination) => destination.tab == ShellTab.library,
        ),
    ];
    final libraryPosition = _positionOf(destinations, ShellTab.library);
    final settings = destinations
        .where((destination) => destination.tab == ShellTab.settings)
        .firstOrNull;
    final list = ValueListenableBuilder<String?>(
      valueListenable: desktopSidebarLink,
      builder: (context, openId, _) {
        final current = selected == ShellTab.library
            ? openId ?? links.firstOrNull?.id
            : null;
        Widget linkRow(DesktopSidebarLink link, {String? tooltip}) =>
            _SidebarRow(
              icon: link.icon,
              label: link.label,
              badgeCount: link.badgeCount,
              selected: link.id == current,
              tooltip: tooltip ?? link.label,
              onTap: () => openLink!(link.id),
            );
        // The source list scrolls without a scrollbar.
        return NoScrollbars(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(10, 0, 10, 8),
            children: [
              for (final destination in topLevel) destinationRow(destination),
              if (links.isNotEmpty) ...[
                _SidebarSectionHeader(l10n.navLibrary),
                for (final (index, link) in links.indexed)
                  linkRow(
                    link,
                    tooltip: index == 0 && libraryPosition > 0
                        ? desktopDestinationTooltip(
                            context,
                            link.label,
                            libraryPosition,
                          )
                        : null,
                  ),
              ],
              if (openLink != null) ...[
                _SidebarSectionHeader(l10n.searchPlaylists),
                linkRow(
                  DesktopSidebarLink(
                    id: DesktopSidebarLink.allPlaylistsId,
                    icon: CupertinoIcons.square_grid_2x2,
                    label: l10n.desktopAllPlaylists,
                  ),
                ),
                for (final playlist in playlists)
                  linkRow(
                    DesktopSidebarLink(
                      id: DesktopSidebarLink.playlistId(playlist.id),
                      icon: CupertinoIcons.music_note_list,
                      label: playlist.name,
                    ),
                  ),
              ],
            ],
          ),
        );
      },
    );
    final panel = ValueListenableBuilder<Color?>(
      valueListenable: ShellNavigationService.chromeSurface,
      // The tint follows the backdrop's crossfade instead of landing in one
      // frame.
      builder: (context, tint, child) => TweenAnimationBuilder<Color?>(
        tween: ColorTween(end: panelColor(context, tint)),
        duration: _SidebarBackdrop.crossFade(context).duration,
        curve: _SidebarBackdrop.crossFade(context).curve,
        child: child,
        builder: (context, color, child) => DecoratedBox(
          // One piece with the window: only a hairline parts it
          // from the content, and the window's own corners are its corners.
          decoration: BoxDecoration(
            color: color,
            border: BorderDirectional(
              end: BorderSide(
                color: Theme.of(
                  context,
                ).colorScheme.onSurface.withValues(alpha: 0.1),
                width: 0.5,
              ),
            ),
          ),
          child: child,
        ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          const _SidebarBackdrop(),
          Material(
            type: MaterialType.transparency,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const _SidebarHeader(),
                Expanded(child: list),
                if (settings != null)
                  _SidebarAccountRow(
                    settings: settings,
                    settingsSelected: selected == ShellTab.settings,
                    settingsTooltip: desktopDestinationTooltip(
                      context,
                      settings.label,
                      _positionOf(destinations, ShellTab.settings),
                    ),
                    onOpenSettings: () => onSelected(ShellTab.settings),
                    onOpenProfile:
                        onOpenProfile ?? () => onSelected(ShellTab.settings),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
    return SizedBox(width: width, child: panel);
  }
}

/// Extends the page's hero beneath the sidebar's backdrop blur.
///
/// The sidebar and the page are separate panes, so this paints the artwork
/// again rather than sampling the page: a tiny decode, enlarged and mirrored
/// at the seam. The enlargement is the blur, with no filter to rerun on each
/// scroll frame, and the mirror meets the hero's edge with the same colors.
class _SidebarBackdrop extends StatelessWidget {
  const _SidebarBackdrop();

  /// Decoded width; the fewer pixels, the softer the enlarged image.
  static const int _decodeWidth = 48;

  /// The artwork's crossfade, which the panel's tint follows: Mornye's
  /// effects spring, or a cut with reduced motion.
  static AppMotionSpec crossFade(BuildContext context) =>
      MediaQuery.disableAnimationsOf(context)
      ? const AppMotionSpec(Duration.zero, Curves.linear)
      : MornyeMotion.effects;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<String?>(
    valueListenable: ShellNavigationService.chromeBackdrop,
    builder: (context, source, _) {
      final image = source == null
          ? null
          : coverImageProvider(source, cacheWidth: _decodeWidth);
      final fade = crossFade(context);
      return AnimatedSwitcher(
        duration: fade.duration,
        switchInCurve: fade.curve,
        switchOutCurve: fade.leaving,
        layoutBuilder: (current, previous) =>
            Stack(fit: StackFit.expand, children: [...previous, ?current]),
        child: image == null
            ? const SizedBox.expand()
            : ClipRect(
                key: ValueKey(source),
                child: _SidebarArtwork(image: image),
              ),
      );
    },
  );
}

class _SidebarArtwork extends StatelessWidget {
  const _SidebarArtwork({required this.image});

  final ImageProvider image;

  @override
  Widget build(BuildContext context) {
    final window = MediaQuery.sizeOf(context);
    final hero = Size(
      window.width - DesktopSidebar.width,
      // MornyeArtistHeader's hero height on the desktop.
      (window.height * 0.82).clamp(420.0, 820.0),
    );
    return ValueListenableBuilder<Color?>(
      valueListenable: ShellNavigationService.chromeSurface,
      builder: (context, tint, _) {
        final fill = DesktopSidebar.panelColor(context, tint);
        return AnimatedBuilder(
          animation: ShellNavigationService.chromeBackdropOffset,
          builder: (context, child) => Transform.translate(
            offset: Offset(
              0,
              -ShellNavigationService.chromeBackdropOffset.value,
            ),
            child: child,
          ),
          child: RepaintBoundary(
            child: OverflowBox(
              alignment: AlignmentDirectional.topEnd,
              minWidth: hero.width,
              maxWidth: hero.width,
              minHeight: hero.height,
              maxHeight: hero.height,
              child: Stack(
                key: const ValueKey('desktop-sidebar-backdrop'),
                fit: StackFit.expand,
                children: [
                  Transform.flip(
                    flipX: true,
                    child: Image(
                      image: image,
                      fit: BoxFit.cover,
                      alignment: Alignment.topCenter,
                      filterQuality: FilterQuality.high,
                      gaplessPlayback: true,
                      excludeFromSemantics: true,
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                  ),
                  // The sidebar glass fades out where the hero ends.
                  DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        stops: const [0, 0.6, 1],
                        colors: [
                          fill.withValues(alpha: 0.42),
                          fill.withValues(alpha: 0.62),
                          fill,
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// The account row pinned under the sidebar's list: the
/// profile picture and name, which open the profile, then Settings.
class _SidebarAccountRow extends ConsumerWidget {
  const _SidebarAccountRow({
    required this.settings,
    required this.settingsSelected,
    required this.settingsTooltip,
    required this.onOpenSettings,
    required this.onOpenProfile,
  });

  final DesktopDestination settings;
  final bool settingsSelected;
  final String settingsTooltip;
  final VoidCallback onOpenSettings;
  final VoidCallback onOpenProfile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final colorScheme = Theme.of(context).colorScheme;
    final profile = ref.watch(desktopSidebarProfileProvider);
    final name = profile?.name.trim() ?? '';
    // The sidebar is too narrow for the full invitation in long languages,
    // where it ellipsized mid-word; screen readers still hear all of it.
    final label = name.isEmpty ? context.l10n.profileTitle : name;
    final semanticLabel = name.isEmpty ? context.l10n.profileSetUp : name;
    final radius = BorderRadius.circular(_SidebarRow.radius);
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(
            color: MornyeTheme.metadataDividerColor(context),
            width: 0.5,
          ),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 6, 10, 8),
        child: Row(
          children: [
            Expanded(
              child: Semantics(
                button: true,
                label: semanticLabel,
                excludeSemantics: true,
                child: InkWell(
                  key: const ValueKey('desktop-sidebar-account'),
                  borderRadius: radius,
                  hoverColor: _SidebarRow.hoverFill(context),
                  onTap: onOpenProfile,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 40),
                    child: Row(
                      children: [
                        const SizedBox(width: 8),
                        SizedBox.square(
                          dimension: 28,
                          child: ProfileAvatar(
                            name: name,
                            photoPath: profile?.photoPath,
                            size: 28,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            label,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(
                                  color: colorScheme.onSurface,
                                  fontWeight: FontWeight.w600,
                                ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            Semantics(
              selected: settingsSelected,
              child: AppIconButton(
                key: const ValueKey('desktop-sidebar-settings'),
                tooltip: settingsTooltip,
                isSelected: settingsSelected,
                iconSize: 18,
                color: settingsSelected
                    ? colorScheme.primary
                    : colorScheme.onSurfaceVariant,
                // A fill marks Settings as open by more than its color.
                style: settingsSelected
                    ? IconButton.styleFrom(
                        backgroundColor: _SidebarRow.selectionFill(context),
                      )
                    : null,
                icon: AppIcon(
                  settingsSelected ? settings.selectedIcon : settings.icon,
                ),
                onPressed: onOpenSettings,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The top of the sidebar panel: on macOS the rest of the title bar row,
/// where the traffic lights sit and a drag moves the window.
class _SidebarHeader extends StatelessWidget {
  const _SidebarHeader();

  /// Height without a title bar in the content (Windows, Linux).
  static const double height = 44;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: desktopTitleBarInContentFor(context)
        ? desktopTitleBarHeightFor(context)
        : height,
    child: const DesktopWindowDragArea(
      zoomOnDoubleClick: true,
      child: SizedBox.expand(),
    ),
  );
}

/// A sidebar section heading, in the small bold secondary style of macOS
/// source lists.
class _SidebarSectionHeader extends StatelessWidget {
  const _SidebarSectionHeader(this.label);

  final String label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsetsDirectional.fromSTEB(14, 16, 10, 4),
    child: Semantics(
      header: true,
      child: Text(
        label,
        style: Theme.of(
          context,
        ).textTheme.labelSmall?.copyWith(fontWeight: FontWeight.w600),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    ),
  );
}

/// One source-list row: a 32pt row inset 6pt from the
/// panel, an 18pt accent icon and a 13pt label, and an optional count. The
/// selected row gets a rounded translucent fill and a semibold label.
class _SidebarRow extends StatelessWidget {
  const _SidebarRow({
    super.key,
    required this.icon,
    required this.label,
    required this.selected,
    required this.tooltip,
    required this.onTap,
    this.badgeCount = 0,
  });

  /// Row height at the default text size; larger text grows the row.
  static const double height = 32;

  /// Corner radius of the selection fill.
  static const double radius = 8;

  /// The selected row's fill.
  static Color selectionFill(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return scheme.onSurface.withValues(
      alpha: scheme.brightness == Brightness.dark ? 0.12 : 0.08,
    );
  }

  /// A hovered row's fill, lighter than the selection.
  static Color hoverFill(BuildContext context) =>
      Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.05);

  final IconData icon;
  final String label;
  final bool selected;
  final String tooltip;
  final VoidCallback onTap;
  final int badgeCount;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final borderRadius = BorderRadius.circular(radius);
    final badge = badgeCount > 0 ? '$badgeCount' : null;
    return Tooltip(
      message: tooltip,
      waitDuration: const Duration(milliseconds: 800),
      child: Semantics(
        button: true,
        selected: selected,
        label: badge == null ? label : '$label, $badge',
        excludeSemantics: true,
        child: InkWell(
          borderRadius: borderRadius,
          hoverColor: hoverFill(context),
          onTap: onTap,
          child: Ink(
            decoration: BoxDecoration(
              color: selected ? selectionFill(context) : null,
              borderRadius: borderRadius,
            ),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: height),
              child: Row(
                children: [
                  // Icon at 20pt and label at 56pt from the panel edge.
                  const SizedBox(width: 14),
                  AppIcon(icon, size: 18, color: colorScheme.primary),
                  const SizedBox(width: 18),
                  Expanded(
                    child: Text(
                      label,
                      style: textTheme.bodySmall?.copyWith(
                        color: colorScheme.onSurface,
                        fontWeight: selected
                            ? FontWeight.w600
                            : FontWeight.w400,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (badge != null)
                    Text(
                      badge,
                      style: textTheme.labelSmall?.copyWith(
                        fontFeatures: const [FontFeature.tabularFigures()],
                      ),
                    ),
                  const SizedBox(width: 10),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
