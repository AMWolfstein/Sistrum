import 'package:flutter/material.dart';

/// Indeterminate loading only. Downloads/analysis keep their real progress.
class AppLoadingIndicator extends StatelessWidget {
  const AppLoadingIndicator({
    super.key,
    this.size = 32,
    this.color,
    this.semanticLabel,
    this.strokeWidth = 3,
  });

  final double size;
  final Color? color;
  final String? semanticLabel;

  /// Stroke of the Mornye circular indicator.
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    final animate =
        !MediaQuery.disableAnimationsOf(context) &&
        TickerMode.valuesOf(context).enabled;
    return SizedBox.square(
      dimension: size,
      child: TickerMode(
        enabled: animate,
        child: CircularProgressIndicator(
          color: color,
          strokeWidth: strokeWidth,
          semanticsLabel: semanticLabel,
        ),
      ),
    );
  }
}
