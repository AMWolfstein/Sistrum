import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/widgets/app_action_button.dart';

/// Full-width delete action for selection-mode bottom bars: destructive
/// (error) styling once at least one item is selected, disabled/neutral
/// otherwise.
class DestructiveSelectionButton extends StatelessWidget {
  const DestructiveSelectionButton({
    super.key,
    required this.count,
    required this.onPressed,
    this.label,
  });

  final int count;
  final VoidCallback onPressed;

  /// Replaces the downloaded album delete label.
  final String? label;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: AppActionButton(
        isDestructive: true,
        onPressed: count > 0 ? onPressed : null,
        icon: const Icon(Icons.delete_outline),
        label: Text(
          label ??
              (count > 0
                  ? context.l10n.downloadedAlbumDeleteCount(count)
                  : context.l10n.downloadedAlbumSelectToDelete),
        ),
      ),
    );
  }
}
