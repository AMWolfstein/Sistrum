import 'package:flutter/widgets.dart';

const double kNormalizedHeaderTopPadding = 24.0;

double normalizedHeaderTopPadding(
  BuildContext context, {
  double max = kNormalizedHeaderTopPadding,
}) {
  final topPadding = MediaQuery.paddingOf(context).top;
  if (topPadding <= 0) return 0;
  return topPadding > max ? max : topPadding;
}
