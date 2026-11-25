import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show MaterialPageRoute, Scaffold;
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/screens/queue_tab.dart';
import 'package:sistrum/services/performance_auto_measure.dart';
import 'package:sistrum/services/shell_navigation_service.dart';

/// Time for a screen to show its first frames before it is measured.
const Duration kAutoMeasureSettle = Duration(milliseconds: 500);

/// Reads one page of library browse entries (the app's own loader).
typedef AutoMeasureLoader =
    Future<LibraryBrowsePage> Function(LibraryBrowsePageRequest request);

/// The steps of a run and the cleanup that removes what the run pushed. The
/// hub calls [cleanup] in its finally, so a cancelled or failed run leaves no
/// route behind.
class AutoMeasureDriver {
  const AutoMeasureDriver({required this.steps, required this.cleanup});

  final List<AutoMeasureStep> steps;
  final void Function() cleanup;
}

/// How long past its duration a page transition is waited for.
const Duration kAutoMeasureTransitionMargin = Duration(milliseconds: 300);

/// The primary scroll position under [navigator], or under the page [within]
/// when one is given: the first scrollable that
/// can scroll and whose tickers run. The overlay mutes the tickers of every
/// route under an opaque route, so a list hidden by a pushed page is never
/// measured. The ticker mode is read without subscribing, so the scrollable
/// is not rebuilt when the route changes. Null when the visible screen has
/// nothing to scroll.
ScrollPosition? primaryScrollPosition(
  NavigatorState? navigator, {
  ModalRoute<dynamic>? within,
}) {
  // With [within], only the page's own subtree is searched: the lists under
  // it, never the screen behind it. The subtree is read without subscribing.
  final root = within != null ? within.subtreeContext : navigator?.context;
  if (root is! Element) return null;
  ScrollPosition? found;
  void visit(Element element) {
    if (found != null) return;
    final state = element is StatefulElement ? element.state : null;
    if (state is ScrollableState) {
      final tickersOn = TickerMode.getValuesNotifier(
        state.context,
      ).value.enabled;
      final position = state.position;
      if (tickersOn &&
          position.maxScrollExtent - position.minScrollExtent > 0) {
        found = position;
        return;
      }
    }
    element.visitChildren(visit);
  }

  visit(root);
  return found;
}

/// Completes when [route] is covering the screen behind it, or after its
/// transition plus [kAutoMeasureTransitionMargin], whichever comes first.
Future<void> _untilOpaque(PageRoute<void> route) async {
  final animation = route.animation;
  if (animation == null || animation.isCompleted) return;
  final done = Completer<void>();
  void onStatus(AnimationStatus status) {
    if (status == AnimationStatus.completed && !done.isCompleted) {
      done.complete();
    }
  }

  animation.addStatusListener(onStatus);
  try {
    await done.future.timeout(
      route.transitionDuration + kAutoMeasureTransitionMargin,
      onTimeout: () {},
    );
  } finally {
    animation.removeStatusListener(onStatus);
  }
}

/// The steps of the automatic measurement, through the app's own APIs:
/// [ShellNavigationService.requestTab] for the tabs, [loader] for the first
/// artist, and the Library tab's navigator for the Artist and Songs pages. The
/// Songs page is the one the Library overview opens (`QueueTab` with section
/// `all`). [loader] is read once, when the steps are built, so no ref is used
/// after the shell changes tabs.
AutoMeasureDriver buildAutoMeasureSteps({
  required AutoMeasureLoader loader,
  required String noDataReason,
  required String noScreenReason,
  WidgetBuilder? songsBuilder,
  Widget Function(String artist)? artistBuilder,
}) {
  NavigatorState? pushedOn;
  ModalRoute<void>? pushed;

  // Removes only the route this run pushed, from the navigator it pushed on.
  void removePushed() {
    final navigator = pushedOn;
    final route = pushed;
    pushedOn = null;
    pushed = null;
    if (navigator != null && route != null && route.isActive) {
      navigator.removeRoute(route);
    }
  }

  Future<String?> settle() async {
    await Future<void>.delayed(kAutoMeasureSettle);
    return null;
  }

  Future<String?> showTab(ShellTab tab) async {
    removePushed();
    if (!ShellNavigationService.requestTab(tab)) return noScreenReason;
    return settle();
  }

  // Shows the Library tab and pushes [builder] on its navigator, keeping the
  // route so it can be removed later.
  Future<String?> pushOnLibrary(WidgetBuilder builder) async {
    removePushed();
    if (!ShellNavigationService.requestTab(ShellTab.library)) {
      return noScreenReason;
    }
    await settle();
    final navigator = ShellNavigationService.activeTabNavigator();
    if (navigator == null) return noScreenReason;
    final route = MaterialPageRoute<void>(builder: builder);
    pushedOn = navigator;
    pushed = route;
    navigator.push<void>(route);
    // While the page slides in it is not opaque, so the screen behind it keeps
    // its tickers and a list there could be picked. Wait for the page to cover it.
    await _untilOpaque(route);
    return settle();
  }

  Future<String?> songsPage() => pushOnLibrary(
    songsBuilder ??
        (_) => const Scaffold(body: QueueTab(librarySection: 'all')),
  );

  Future<String?> artistPage() async {
    removePushed();
    final page = await loader((
      browse: (artists: true, sort: 'a-z', limit: 1, completeness: null),
      offset: 0,
      cursor: null,
      includeLocal: true,
    ));
    if (page.entries.isEmpty) return noDataReason;
    final name = page.entries.first.name;
    final build =
        artistBuilder ?? (String name) => LibraryArtistScreen(artist: name);
    return pushOnLibrary((_) => build(name));
  }

  // The page the run pushed is searched alone; a tab screen is searched on its
  // tab navigator. A pushed page that is gone or not built has nothing to scroll.
  ScrollPosition? visible() {
    final page = pushed;
    final navigator = ShellNavigationService.activeTabNavigator();
    if (page == null) return primaryScrollPosition(navigator);
    if (!page.isActive) return null;
    return primaryScrollPosition(navigator, within: page);
  }

  return AutoMeasureDriver(
    steps: [
      AutoMeasureStep(
        screen: 'Home',
        prepare: () => showTab(ShellTab.home),
        position: visible,
      ),
      AutoMeasureStep(
        screen: 'Library',
        prepare: () => showTab(ShellTab.library),
        position: visible,
      ),
      AutoMeasureStep(screen: 'Songs', prepare: songsPage, position: visible),
      AutoMeasureStep(screen: 'Artist', prepare: artistPage, position: visible),
      AutoMeasureStep(
        screen: 'Search',
        prepare: () => showTab(ShellTab.search),
        position: visible,
      ),
    ],
    cleanup: removePushed,
  );
}
