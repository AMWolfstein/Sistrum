import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';

/// One slider style for settings and editing sheets.
///
/// In Liquid (and not with reduced motion) it is the package's
/// [LiquidGlassSlider], in the Mornye accent. The package slider has no
/// divisions, so they are applied here by snapping the values it reports. In
/// None, and with reduced motion, it is the standard [Slider]. Both keep the
/// same semantics, RTL mirroring and vertical layout.
///
/// The Now Playing volume and the progress bar do not use this widget.
class AppSlider extends ConsumerWidget {
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
  Widget build(BuildContext context, WidgetRef ref) {
    final glass =
        ref.watch(mornyeLiquidGlassProvider) &&
        !MediaQuery.disableAnimationsOf(context);
    final slider = glass
        ? _LiquidAppSlider(
            value: value,
            onChanged: onChanged,
            onChangeEnd: onChangeEnd,
            min: min,
            max: max,
            divisions: divisions,
            label: label,
            semanticLabel: semanticLabel,
            semanticFormatterCallback: semanticFormatterCallback,
          )
        : _MaterialAppSlider(
            value: value,
            onChanged: onChanged,
            onChangeEnd: onChangeEnd,
            min: min,
            max: max,
            divisions: divisions,
            label: label,
            semanticFormatterCallback: semanticFormatterCallback,
            semanticLabel: semanticLabel,
          );
    final laid = vertical ? RotatedBox(quarterTurns: 3, child: slider) : slider;
    return laid;
  }
}

/// Snaps [raw] to the nearest of [divisions] steps between [min] and [max].
double snapToDivisions(double raw, double min, double max, int? divisions) {
  final clamped = raw.clamp(min, max).toDouble();
  if (divisions == null || divisions <= 0 || max <= min) return clamped;
  final step = (max - min) / divisions;
  return (min + ((clamped - min) / step).round() * step)
      .clamp(min, max)
      .toDouble();
}

class _MaterialAppSlider extends StatelessWidget {
  const _MaterialAppSlider({
    required this.value,
    required this.onChanged,
    required this.onChangeEnd,
    required this.min,
    required this.max,
    required this.divisions,
    required this.label,
    required this.semanticFormatterCallback,
    required this.semanticLabel,
  });

  final double value;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final double min;
  final double max;
  final int? divisions;
  final String? label;
  final SemanticFormatterCallback? semanticFormatterCallback;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
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
    if (semanticLabel == null) return slider;
    return MergeSemantics(
      child: Semantics(label: semanticLabel, child: slider),
    );
  }
}

class _LiquidAppSlider extends StatefulWidget {
  const _LiquidAppSlider({
    required this.value,
    required this.onChanged,
    required this.onChangeEnd,
    required this.min,
    required this.max,
    required this.divisions,
    required this.label,
    required this.semanticLabel,
    required this.semanticFormatterCallback,
  });

  final double value;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final double min;
  final double max;
  final int? divisions;
  final String? label;
  final String? semanticLabel;
  final SemanticFormatterCallback? semanticFormatterCallback;

  @override
  State<_LiquidAppSlider> createState() => _LiquidAppSliderState();
}

class _LiquidAppSliderState extends State<_LiquidAppSlider> {
  /// The last snapped value sent to [_LiquidAppSlider.onChanged]. A drag
  /// reports continuously, but a discrete slider only sends a new step.
  double? _lastSent;

  void _send(double raw) {
    final snapped = snapToDivisions(
      raw,
      widget.min,
      widget.max,
      widget.divisions,
    );
    if (snapped == _lastSent) return;
    _lastSent = snapped;
    widget.onChanged?.call(snapped);
  }

  @override
  void didUpdateWidget(covariant _LiquidAppSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    // The parent echoes each sent value back as the new value. The step the
    // slider is on is then the last one sent, so the echo sends nothing; a
    // value set from outside becomes the step the next change is measured from.
    if (widget.value != oldWidget.value) {
      _lastSent = snapToDivisions(
        widget.value,
        widget.min,
        widget.max,
        widget.divisions,
      );
    }
  }

  String _format(double v) =>
      widget.semanticFormatterCallback?.call(v) ?? v.toStringAsFixed(2);

  void _move(double next) {
    final snapped = snapToDivisions(
      next,
      widget.min,
      widget.max,
      widget.divisions,
    );
    widget.onChanged?.call(snapped);
    widget.onChangeEnd?.call(snapped);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = widget.onChanged != null;
    final inactive = scheme.brightness == Brightness.dark
        ? const Color(0xff39393d)
        : const Color(0xff8e8e93);
    final min = widget.min;
    final max = widget.max;
    final divisions = widget.divisions;
    final current = snapToDivisions(widget.value, min, max, divisions);
    final step = divisions != null && divisions > 0
        ? (max - min) / divisions
        : (max - min) / 20;
    final up = enabled && current < max;
    final down = enabled && current > min;

    Widget control = LayoutBuilder(
      builder: (context, constraints) => Opacity(
        opacity: enabled ? 1 : 0.5,
        child: IgnorePointer(
          ignoring: !enabled,
          child: LiquidGlassSlider(
            value: current,
            minimumValue: min,
            maximumValue: max,
            // Reports while dragged; [_send] keeps only the steps.
            isContinuous: true,
            onChanged: _send,
            onChangeEnd: widget.onChangeEnd == null
                ? null
                : (next) => widget.onChangeEnd!(
                    snapToDivisions(next, min, max, divisions),
                  ),
            activeColor: scheme.primary,
            inactiveColor: inactive,
            thumbColor: Colors.white,
            width: constraints.hasBoundedWidth ? constraints.maxWidth : null,
          ),
        ),
      ),
    );
    if (Directionality.of(context) == TextDirection.rtl) {
      control = Transform.flip(flipX: true, child: control);
    }
    return Semantics(
      label: widget.semanticLabel,
      value: _format(current),
      slider: true,
      enabled: enabled,
      // Each action carries the value it moves to, from the same formatter.
      increasedValue: up ? _format((current + step).clamp(min, max)) : null,
      decreasedValue: down ? _format((current - step).clamp(min, max)) : null,
      onIncrease: up ? () => _move(current + step) : null,
      onDecrease: down ? () => _move(current - step) : null,
      child: control,
    );
  }
}
