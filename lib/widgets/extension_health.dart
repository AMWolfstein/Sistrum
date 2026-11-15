import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

Color extensionHealthColor(BuildContext context, String status) {
  final colorScheme = Theme.of(context).colorScheme;
  switch (status) {
    case 'online':
      return CupertinoColors.systemGreen.resolveFrom(context);
    case 'degraded':
      return colorScheme.tertiary;
    case 'offline':
      return colorScheme.error;
    default:
      return colorScheme.onSurfaceVariant;
  }
}

IconData extensionHealthIcon(BuildContext context, String status) {
  return switch (status) {
    'online' => CupertinoIcons.check_mark_circled_solid,
    'degraded' => CupertinoIcons.exclamationmark_triangle,
    'offline' => CupertinoIcons.exclamationmark_circle,
    _ => CupertinoIcons.question_circle,
  };
}
