import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
import 'package:mornye/theme/material_expressive.dart';

/// Pull-to-refresh with the expressive shape-morphing indicator on Material.
/// Mornye and reduced motion keep Flutter's [RefreshIndicator].
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



  @override
  State<AppRefreshIndicator> createState() => _AppRefreshIndicatorState();
}

class _AppRefreshIndicatorState extends State<AppRefreshIndicator> {
  final _materialKey = GlobalKey<RefreshIndicatorState>();
  final _expressive = M3ERefreshIndicatorController();
  bool _usesExpressive = false;

  /// While a pull is under way: its status and how far it has pulled.
  M3ERefreshStatus? _status;
  double _pull = 0;


  /// A pull being pushed back shrinks the indicator first; the list only
  /// scrolls once the pull is gone.
  bool _holdsPull() =>
      (_status == M3ERefreshStatus.drag || _status == M3ERefreshStatus.armed) &&
      _pull > 0;

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    super.dispose();
  }


  bool _trackPull(OverscrollNotification notification) {
    final delta = notification.dragDetails?.delta.dy;
    if (_holdsPull() || _status == M3ERefreshStatus.drag) {
      if (delta != null) _pull = math.max(0, _pull + delta);
    }
    return false;
  }

  void _statusChanged(M3ERefreshStatus? status) {
    // A new pull starts from nothing; falling back from armed keeps it.
    if (status == M3ERefreshStatus.drag && _status != M3ERefreshStatus.armed) {
      _pull = 0;
    }
    _status = status;
  }

  @override
  Widget build(BuildContext context) {
    _usesExpressive = materialExpressiveEnabled(context);
    if (!_usesExpressive) {
      return RefreshIndicator(
        key: _materialKey,
        onRefresh: widget.onRefresh,
        notificationPredicate: widget.notificationPredicate,
        child: widget.child,
      );
    }
    Widget indicator = M3ERefreshIndicator.contained(
      controller: _expressive,
      onRefresh: widget.onRefresh,
      notificationPredicate: widget.notificationPredicate,
      onStatusChange: _statusChanged,
      child: NotificationListener<OverscrollNotification>(
        onNotification: _trackPull,
        child: widget.child,
      ),
    );
    // bounce opens a gap below the title, and the indicator ignores a pull
    // that starts while it is still closing, so the top edge stays put.
    return MaterialExpressiveScope(child: indicator);
  }
}
