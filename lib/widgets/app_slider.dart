import 'package:material_ui/material_ui.dart';

/// Settings slider with the standard [Slider].
class AppSlider extends StatelessWidget {
  const AppSlider({
    super.key,
    required this.value,
    required this.onChanged,
    this.onChangeEnd,
    this.min = 0,
    this.max = 1,
    this.divisions,
    this.label,
    this.semanticFormatterCallback,
    this.vertical = false,
    this.semanticLabel,
  });

  final double value;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final double min;
  final double max;
  final int? divisions;
  final String? label;
  final SemanticFormatterCallback? semanticFormatterCallback;

  /// Lays the slider out bottom (min) to top (max).
  final bool vertical;

  /// What the slider adjusts, read before its value; [label] only names the
  /// value bubble.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final slider = _buildSlider();
    if (semanticLabel == null) return slider;
    return MergeSemantics(
      child: Semantics(label: semanticLabel, child: slider),
    );
  }

  Widget _buildSlider() {
    final slider = Slider(
      value: value,
      min: min,
      max: max,
      divisions: divisions,
      label: label,
      semanticFormatterCallback: semanticFormatterCallback,
      onChanged: onChanged,
      onChangeEnd: onChangeEnd,
    );
    return vertical ? RotatedBox(quarterTurns: 3, child: slider) : slider;
  }
}
