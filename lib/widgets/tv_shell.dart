import 'dart:async';

import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoButton;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mini_player.dart';

/// Remote destinations stay visible while the content and player own focus.
class TvShell extends StatefulWidget {
  const TvShell({
    super.key,
    required this.destinations,
    required this.selectedIndex,
    required this.onSelected,
    required this.child,
  });

  final List<NavigationDestination> destinations;
  final int selectedIndex;
  final ValueChanged<int> onSelected;
  final Widget child;

  @override
  State<TvShell> createState() => _TvShellState();
}

class _TvShellState extends State<TvShell> {
  final _destinations = <int, FocusNode>{};

  FocusNode _node(int index) => _destinations.putIfAbsent(
    index,
    () => FocusNode(debugLabel: 'TV destination $index'),
  );

  @override
  void initState() {
    super.initState();
    _focusDestination(widget.selectedIndex);
  }

  void _focusDestination(int index) {
    // The nested tab Navigator mounts after the sidebar. Wait for its initial
    // focus pass before restoring the remote's destination.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      scheduleMicrotask(() {
        if (mounted) _node(index).requestFocus();
      });
    });
  }

  void _select(int index) {
    widget.onSelected(index);
    _focusDestination(index);
  }

  @override
  void dispose() {
    for (final node in _destinations.values) {
      node.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(
          width: 112,
          child: ColoredBox(
            color: MornyeTheme.controlFill(context),
            child: ListView(
              padding: const EdgeInsets.all(8),
              children: [
                for (var index = 0; index < widget.destinations.length; index++)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: _destination(context, index),
                  ),
              ],
            ),
          ),
        ),
        Expanded(
          child: Column(
            children: [
              Expanded(child: widget.child),
              const MiniPlayer(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _destination(BuildContext context, int index) {
    final destination = widget.destinations[index];
    final selected = index == widget.selectedIndex;
    final scheme = Theme.of(context).colorScheme;
    final content = Semantics(
      selected: selected,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          selected
              ? destination.selectedIcon ?? destination.icon
              : destination.icon,
          const SizedBox(height: 4),
          Text(destination.label, textAlign: TextAlign.center),
        ],
      ),
    );
    return CupertinoButton(
      focusNode: _node(index),
      autofocus: selected,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 12),
      color: selected ? scheme.primary.withValues(alpha: 0.16) : null,
      onPressed: () => _select(index),
      child: DefaultTextStyle.merge(
        style: Theme.of(context).textTheme.labelLarge!.copyWith(
          color: selected ? scheme.primary : scheme.onSurface,
        ),
        child: IconTheme.merge(
          data: IconThemeData(
            color: selected ? scheme.primary : scheme.onSurface,
          ),
          child: content,
        ),
      ),
    );
  }
}
