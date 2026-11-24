import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';

// Every map avoids Flutter's Android default,
// PredictiveBackPageTransitionsBuilder: its gesture detector mis-routes the
// predictive-back gesture to a nested Navigator instead of the topmost route
// (flutter#152323), which pops the page behind a root modal, sheet or dialog
// instead of closing the modal first. The builders used here register no back
// gesture observer, so system back keeps closing the topmost route first.

/// Mornye pushes the iOS way on every platform. Desktops drop the edge
/// back-drag, which would otherwise start under a mouse.
const PageTransitionsTheme mornyePageTransitionsTheme = PageTransitionsTheme(
  builders: <TargetPlatform, PageTransitionsBuilder>{
    TargetPlatform.android: ReducedMotionPageTransitionsBuilder(
      CupertinoPageTransitionsBuilder(),
    ),
    TargetPlatform.iOS: ReducedMotionPageTransitionsBuilder(
      CupertinoPageTransitionsBuilder(),
    ),
    TargetPlatform.macOS: ReducedMotionPageTransitionsBuilder(
      DesktopCupertinoPageTransitionsBuilder(),
    ),
    TargetPlatform.windows: ReducedMotionPageTransitionsBuilder(
      DesktopCupertinoPageTransitionsBuilder(),
    ),
    TargetPlatform.linux: ReducedMotionPageTransitionsBuilder(
      DesktopCupertinoPageTransitionsBuilder(),
    ),
  },
);

/// Shows [builder]'s transition, or none when the OS asks for reduced
/// motion. Flutter routes never read `MediaQuery.disableAnimations`.
///
/// Under reduced motion the pushed page is drawn at rest from the first
/// frame and a popped page leaves at once. The wrapped transition stays in
/// the tree at rest, so a back swipe that the finger drives still works and
/// still moves with the finger.
class ReducedMotionPageTransitionsBuilder extends PageTransitionsBuilder {
  const ReducedMotionPageTransitionsBuilder(this.builder);

  final PageTransitionsBuilder builder;

  @override
  Duration get transitionDuration => builder.transitionDuration;

  @override
  Duration get reverseTransitionDuration => builder.reverseTransitionDuration;

  @override
  DelegatedTransitionBuilder? get delegatedTransition {
    final delegated = builder.delegatedTransition;
    if (delegated == null) return null;
    return (context, animation, secondaryAnimation, allowSnapshotting, child) {
      if (_reduced(context, Navigator.maybeOf(context))) return child;
      return delegated(
        context,
        animation,
        secondaryAnimation,
        allowSnapshotting,
        child,
      );
    };
  }

  static bool _reduced(BuildContext context, NavigatorState? navigator) =>
      MediaQuery.maybeDisableAnimationsOf(context) == true &&
      !(navigator?.userGestureInProgress ?? false);

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final reduced = _reduced(context, route.navigator);
    // The Offstage is always present, so switching in and out of a gesture
    // keeps the wrapped transition's state (and its drag recognizer).
    return Offstage(
      offstage: reduced && animation.status == AnimationStatus.reverse,
      child: builder.buildTransitions<T>(
        route,
        context,
        reduced ? kAlwaysCompleteAnimation : animation,
        reduced ? kAlwaysDismissedAnimation : secondaryAnimation,
        child,
      ),
    );
  }
}

/// The iOS push without its edge back-drag. Flutter's drag recognizer
/// accepts every pointer kind, so on a desktop a mouse press in the
/// leftmost 20dp of a page would drag it away. Desktops go back with the
/// toolbar button, Esc and the Back shortcut instead.
class DesktopCupertinoPageTransitionsBuilder extends PageTransitionsBuilder {
  const DesktopCupertinoPageTransitionsBuilder();

  @override
  Duration get transitionDuration =>
      CupertinoRouteTransitionMixin.kTransitionDuration;

  @override
  DelegatedTransitionBuilder? get delegatedTransition =>
      CupertinoPageTransition.delegatedTransition;

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    final linear = route.popGestureInProgress;
    if (route.fullscreenDialog) {
      return CupertinoFullscreenDialogTransition(
        primaryRouteAnimation: animation,
        secondaryRouteAnimation: secondaryAnimation,
        linearTransition: linear,
        child: child,
      );
    }
    return CupertinoPageTransition(
      primaryRouteAnimation: animation,
      secondaryRouteAnimation: secondaryAnimation,
      linearTransition: linear,
      child: child,
    );
  }
}
