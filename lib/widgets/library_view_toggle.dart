import 'package:material_ui/material_ui.dart';

/// A switch between the views of a Library page, such as Albums | Singles,
/// as a segmented button.
class LibraryViewToggle extends StatelessWidget {
  const LibraryViewToggle({
    super.key,
    required this.labels,
    required this.selected,
    required this.onChanged,
  });

  final List<String> labels;
  final int selected;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => SegmentedButton<int>(
    showSelectedIcon: false,
    segments: [
      for (final (index, label) in labels.indexed)
        ButtonSegment(value: index, label: Text(label)),
    ],
    selected: {selected},
    onSelectionChanged: (value) => onChanged(value.first),
  );
}
