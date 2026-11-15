import 'package:flutter/material.dart';
import 'package:sistrum/widgets/app_icon.dart';

/// End-to-start swipe that removes a track row over a static delete
/// background.
class SwipeDeleteDismissible extends StatelessWidget {
  const SwipeDeleteDismissible({
    super.key,
    required this.dismissKey,
    required this.onDismissed,
    required this.child,
    this.confirmDismiss,
  });

  static const _threshold = 0.4;

  final Key dismissKey;
  final DismissDirectionCallback onDismissed;
  final ConfirmDismissCallback? confirmDismiss;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Dismissible(
      key: dismissKey,
      direction: DismissDirection.endToStart,
      dismissThresholds: const {DismissDirection.endToStart: _threshold},
      confirmDismiss: confirmDismiss,
      onDismissed: onDismissed,
      background: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        decoration: BoxDecoration(
          color: colorScheme.errorContainer,
          borderRadius: BorderRadius.circular(12),
        ),
        // The row slides toward the start, uncovering its end.
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsetsDirectional.only(end: 20),
        child: AppIcon(
          Icons.delete_outline,
          color: colorScheme.onErrorContainer,
        ),
      ),
      child: child,
    );
  }
}
