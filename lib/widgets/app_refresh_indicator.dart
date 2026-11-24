import 'dart:async';

import 'package:cupertino_ui/cupertino_ui.dart'
    show CupertinoSliverRefreshControl;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/utils/desktop_platform.dart';

/// Pull-to-refresh with the standard [RefreshIndicator].
///
/// Mornye phones, whose lists bounce, use the iOS refresh control instead.
/// It is a sliver, so a [CustomScrollView] under this widget places
/// [AppRefreshIndicator.sliver] right after its large title. Sistrum on
/// desktop, or with bouncing off (low-end phones), keeps [RefreshIndicator].
///
/// The page on screen also refreshes through [refreshVisible], which the
/// desktop Refresh command (Cmd/Ctrl+R) runs.
class AppRefreshIndicator extends StatefulWidget {
  const AppRefreshIndicator({
    super.key,
    required this.onRefresh,
    required this.child,
    this.notificationPredicate = defaultScrollNotificationPredicate,
  });

  final RefreshCallback onRefresh;
  final ScrollNotificationPredicate notificationPredicate;
  final Widget child;

  static final _mounted = <_AppRefreshIndicatorState>[];

  /// The iOS refresh control where the enclosing [AppRefreshIndicator]
  /// uses it, and an empty sliver elsewhere. [enabled] false hides it, for
  /// pages whose `notificationPredicate` turns refreshing off.
  static Widget sliver({bool enabled = true}) =>
      _AppRefreshSliver(enabled: enabled);

  /// Refreshes the most recently opened page that is on screen, showing its
  /// indicator. Returns whether one was found.
  static bool refreshVisible() {
    for (final state in _mounted.reversed) {
      if (state._onScreen) {
        unawaited(state._show());
        return true;
      }
    }
    return false;
  }

  @override
  State<AppRefreshIndicator> createState() => _AppRefreshIndicatorState();
}

class _AppRefreshIndicatorState extends State<AppRefreshIndicator> {
  final _materialKey = GlobalKey<RefreshIndicatorState>();

  bool get _onScreen =>
      mounted &&
      TickerMode.valuesOf(context).enabled &&
      (ModalRoute.of(context)?.isCurrent ?? true);

  @override
  void initState() {
    super.initState();
    AppRefreshIndicator._mounted.add(this);
  }

  @override
  void dispose() {
    AppRefreshIndicator._mounted.remove(this);
    super.dispose();
  }

  // The iOS control has no programmatic show; it only runs on phones, which
  // have no Refresh command.
  Future<void> _show() =>
      _materialKey.currentState?.show() ?? widget.onRefresh();

  static bool _bounces(BuildContext context) {
    ScrollPhysics? physics = ScrollConfiguration.of(
      context,
    ).getScrollPhysics(context);
    while (physics != null) {
      if (physics is BouncingScrollPhysics) return true;
      physics = physics.parent;
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    if (!isDesktopPlatform && _bounces(context)) {
      return _RefreshScope(onRefresh: widget.onRefresh, child: widget.child);
    }
    return RefreshIndicator(
      key: _materialKey,
      onRefresh: widget.onRefresh,
      notificationPredicate: widget.notificationPredicate,
      child: widget.child,
    );
  }
}

/// Hands the refresh callback to [AppRefreshIndicator.sliver] when the iOS
/// refresh control replaces the indicator.
class _RefreshScope extends InheritedWidget {
  const _RefreshScope({required this.onRefresh, required super.child});

  final RefreshCallback onRefresh;

  @override
  bool updateShouldNotify(_RefreshScope oldWidget) =>
      onRefresh != oldWidget.onRefresh;
}

class _AppRefreshSliver extends StatelessWidget {
  const _AppRefreshSliver({required this.enabled});

  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<_RefreshScope>();
    if (scope == null || !enabled) return const SliverToBoxAdapter();
    return CupertinoSliverRefreshControl(onRefresh: scope.onRefresh);
  }
}
