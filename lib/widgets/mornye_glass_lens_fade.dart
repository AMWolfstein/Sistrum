import 'package:flutter/widgets.dart';

/// Fades the glass lens of the header buttons below it as the page scrolls the
/// header into its collapsed bar. Once collapsed the bar is a flat surface, so
/// the buttons keep their tint and stop reading the backdrop.
///
/// [collapseExtent] is the scroll offset at which the header is fully
/// collapsed. The lens is fully on until 55% of it and fully off from 95%.
class MornyeGlassLensFade extends StatefulWidget {
  const MornyeGlassLensFade({
    super.key,
    required this.collapseExtent,
    required this.child,
  });

  final double collapseExtent;
  final Widget child;

  /// Opacity for the lens of any glass surface below this widget; `1` outside.
  static double opacityOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_LensFadeScope>()?.opacity ??
      1;

  /// The lens opacity for a header scrolled [offset] of [extent] pixels.
  @visibleForTesting
  static double opacityFor(double offset, double extent) {
    if (extent <= 0) return 1;
    final t = ((offset / extent - 0.55) / 0.40).clamp(0.0, 1.0);
    return 1 - t * t * (3 - 2 * t);
  }

  @override
  State<MornyeGlassLensFade> createState() => _MornyeGlassLensFadeState();
}

class _MornyeGlassLensFadeState extends State<MornyeGlassLensFade> {
  ScrollPosition? _position;
  double _opacity = 1;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final position = Scrollable.maybeOf(context)?.position;
    if (!identical(position, _position)) {
      _position?.removeListener(_update);
      _position = position?..addListener(_update);
      _opacity = _read();
    }
  }

  @override
  void didUpdateWidget(MornyeGlassLensFade oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.collapseExtent != widget.collapseExtent) _opacity = _read();
  }

  double _read() {
    final position = _position;
    if (position == null || !position.hasPixels) return 1;
    return MornyeGlassLensFade.opacityFor(
      position.pixels,
      widget.collapseExtent,
    );
  }

  void _update() {
    final next = _read();
    // Rebuild the buttons only while the lens is actually changing.
    final settled = next == 0 || next == 1;
    if ((next - _opacity).abs() < 0.02 && !(settled && next != _opacity)) {
      return;
    }
    setState(() => _opacity = next);
  }

  @override
  void dispose() {
    _position?.removeListener(_update);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      _LensFadeScope(opacity: _opacity, child: widget.child);
}

class _LensFadeScope extends InheritedWidget {
  const _LensFadeScope({required this.opacity, required super.child});

  final double opacity;

  @override
  bool updateShouldNotify(_LensFadeScope oldWidget) =>
      opacity != oldWidget.opacity;
}
