import 'package:flutter/material.dart';
import 'package:sistrum/l10n/l10n.dart';

/// "Disc N" chip with a trailing hairline, shown between disc groups in
/// album track lists.
class DiscSeparatorChip extends StatelessWidget {
  const DiscSeparatorChip({super.key, required this.discNumber});

  final int discNumber;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
      child: Text(
        context.l10n.downloadedAlbumDiscHeader(discNumber),
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: colorScheme.onSurfaceVariant,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
