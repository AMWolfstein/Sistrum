import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/swipe_commit_haptic.dart';
import 'package:sistrum/widgets/track_card.dart';

/// End-to-start swipe that removes a track row over a delete background.
///
/// The background takes the row's [TrackCardSegment] corners and keeps its
/// static fill.
class SwipeDeleteDismissible extends StatefulWidget {
  const SwipeDeleteDismissible({
    super.key,
    required this.dismissKey,
    required this.onDismissed,
    required this.child,
    this.confirmDismiss,
  });

  final Key dismissKey;
  final DismissDirectionCallback onDismissed;
  final ConfirmDismissCallback? confirmDismiss;
  final Widget child;

  @override
  State<SwipeDeleteDismissible> createState() => _SwipeDeleteDismissibleState();
}

class _SwipeDeleteDismissibleState extends State<SwipeDeleteDismissible> {
  static const _threshold = 0.4;
  final _haptic = SwipeCommitHaptic();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final radius =
        TrackCardSegment.radiusOf(context) ?? BorderRadius.circular(12);
    final margin =
        TrackCardSegment.marginOf(context) ??
        const EdgeInsets.symmetric(horizontal: 16, vertical: 4);
    // The fill turns at the threshold on the fast effects spring; reduced
    // motion keeps the short dissolve.
    final tint = AppMotion.effectsFast(
      context,
      const AppMotionSpec(Duration(milliseconds: 150), Curves.linear),
    );

    return Dismissible(
      key: widget.dismissKey,
      direction: DismissDirection.endToStart,
      dismissThresholds: const {DismissDirection.endToStart: _threshold},
      confirmDismiss: widget.confirmDismiss,
      onDismissed: widget.onDismissed,
      onUpdate: (details) => _haptic.update(details.reached),
      background: AnimatedContainer(
        duration: tint.duration,
        curve: tint.curve,
        margin: margin,
        decoration: BoxDecoration(
          color: colorScheme.errorContainer,
          borderRadius: radius,
        ),
        // The row slides toward the start, uncovering its end.
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsetsDirectional.only(end: 20),
        child: AppIcon(
          Icons.delete_outline,
          color: colorScheme.onErrorContainer,
        ),
      ),
      child: widget.child,
    );
  }
}
