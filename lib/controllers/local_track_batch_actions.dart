import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/song_leftovers_provider.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';

Future<void> deleteLibraryTracks(
  BuildContext context,
  WidgetRef ref,
  List<UnifiedLibraryItem> selected, {
  required bool Function() isActive,
  required VoidCallback onComplete,
}) async {
  if (selected.isEmpty) return;
  final confirmed = await showAppConfirmDialog(
    context,
    title: context.l10n.dialogDeleteSelectedTitle,
    message: context.l10n.dialogDeleteSelectedMessage(selected.length),
    confirmLabel: context.l10n.dialogDelete,
    destructive: true,
    cancelIsDefault: true,
    confirmStyle: FilledButton.styleFrom(
      backgroundColor: Theme.of(context).colorScheme.error,
    ),
  );
  if (!confirmed || !isActive() || !context.mounted) return;

  final container = ProviderScope.containerOf(context, listen: false);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = context.l10n;
  final deletedPaths = <String>{};
  final leftovers = container.read(songLeftoversProvider);
  var deletedCount = 0;
  for (final item in selected) {
    final cleanPath = DownloadedEmbeddedCoverResolver.cleanFilePath(
      item.filePath,
    );
    if (!await leftovers.delete(cleanPath)) continue;
    deletedPaths.add(cleanPath);
    deletedCount++;
  }
  await removeDeletedLibraryFileEntries(container, deletedPaths);
  if (!isActive() || !context.mounted) return;
  onComplete();
  messenger.showSnackBar(
    SnackBar(
      content: Text(
        trackDeletionResultMessage(
          l10n,
          deleted: deletedCount,
          requested: selected.length,
        ),
      ),
    ),
  );
}
