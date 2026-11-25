import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';

/// One choice in a [SortHeaderButton].
class SortOption<T> {
  const SortOption({
    required this.value,
    required this.label,
    required this.icon,
    this.onChoose,
  });

  final T value;
  final String label;
  final IconData icon;

  /// Replaces the plain pick of [value] when set, for a choice that needs its
  /// own step first. Such a step sets the sort itself when it applies.
  final void Function(BuildContext context)? onChoose;
}

/// A header's sort control: a circle button that opens [groups] as a menu and
/// marks [current]. Each screen keeps its own options and its own persisted
/// choice; see `PersistedSortChoices`.
class SortHeaderButton<T> extends StatelessWidget {
  const SortHeaderButton({
    super.key,
    required this.tooltip,
    required this.current,
    required this.groups,
    required this.onSelected,
  });

  final String tooltip;
  final T current;
  final List<List<SortOption<T>>> groups;
  final ValueChanged<T> onSelected;

  @override
  Widget build(BuildContext context) => HeaderCircleButton(
    icon: Icons.sort,
    tooltip: tooltip,
    onPressed: () => showContextMenu(
      context,
      position:
          contextMenuPositionBelow(context) ??
          contextMenuOriginOf(context) ??
          Offset.zero,
      origin: contextMenuOriginOf(context),
      groups: [
        for (final group in groups)
          [
            for (final option in group)
              ContextMenuAction(
                icon: option.icon,
                label: option.label,
                checked: option.value == current,
                onSelected: () {
                  final choose = option.onChoose;
                  if (choose != null) {
                    choose(context);
                  } else {
                    onSelected(option.value);
                  }
                },
              ),
          ],
      ],
    ),
  );
}
