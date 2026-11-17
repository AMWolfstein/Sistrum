import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';

String trackDeletionResultMessage(
  AppLocalizations l10n, {
  required int deleted,
  required int requested,
}) => [
  if (deleted > 0 || requested == 0) l10n.snackbarDeletedTracks(deleted),
  if (deleted < requested) l10n.snackbarDeleteTracksFailed(requested - deleted),
].join('\n');

/// Shows a delete-confirmation dialog, deletes the given [ids] one by one via
/// [deleteItem], then shows a "deleted N tracks" snackbar.
/// [onExitSelectionMode] runs after persistence if the screen is still open.
///
/// Returns the number of items [deleteItem] reported deleted, or null if the
/// user cancelled the dialog.
/// [persistDeletedItems] commits successfully deleted IDs once before the UI
/// reports completion, including partial success if a later deletion throws.
Future<int?> confirmAndDeleteTracks({
  required BuildContext context,
  required List<String> ids,
  required Future<bool> Function(String id) deleteItem,
  Future<void> Function(List<String> ids)? persistDeletedItems,
  required VoidCallback onExitSelectionMode,
  String? message,
}) async {
  final confirmed = await showAppConfirmDialog(
    context,
    title: context.l10n.downloadedAlbumDeleteSelected,
    message: message ?? context.l10n.downloadedAlbumDeleteMessage(ids.length),
    confirmLabel: context.l10n.dialogDelete,
    destructive: true,
    cancelIsDefault: true,
    confirmStyle: FilledButton.styleFrom(
      backgroundColor: Theme.of(context).colorScheme.error,
    ),
  );

  if (!confirmed || !context.mounted) return null;

  final deletedIds = <String>[];
  try {
    for (final id in ids) {
      if (await deleteItem(id)) deletedIds.add(id);
    }
  } finally {
    if (deletedIds.isNotEmpty && persistDeletedItems != null) {
      await persistDeletedItems(deletedIds);
    }
  }
  final deletedCount = deletedIds.length;

  if (context.mounted) {
    onExitSelectionMode();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          trackDeletionResultMessage(
            context.l10n,
            deleted: deletedCount,
            requested: ids.length,
          ),
        ),
      ),
    );
  }

  return deletedCount;
}

/// Confirms, then removes the file at [filePath] from the device and its library
/// entries (a CUE virtual track only loses its [cueItem] row). Returns true when
/// removed. [isActive] is checked after the dialog, before anything is deleted.
Future<bool> confirmRemoveTrackFromDevice(
  BuildContext context,
  WidgetRef ref, {
  required String filePath,
  required bool offlineCopy,
  required bool Function() isActive,
  LocalLibraryItem? cueItem,
}) async {
  final l10n = context.l10n;
  final confirmed = await showAppConfirmDialog(
    context,
    title: l10n.trackDeleteConfirmTitle,
    message: offlineCopy
        ? l10n.offlineRemoveMessage
        : l10n.trackDeleteConfirmMessage,
    confirmLabel: l10n.dialogDelete,
    destructive: true,
    filled: false,
    confirmStyle: TextButton.styleFrom(
      foregroundColor: Theme.of(context).colorScheme.error,
    ),
  );
  if (!confirmed || !isActive() || !context.mounted) return false;
  final container = ProviderScope.containerOf(context, listen: false);
  if (cueItem != null) {
    await container.read(localLibraryProvider.notifier).removeItem(cueItem.id);
    return true;
  }
  if (!await deleteFile(filePath)) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.snackbarDeleteTracksFailed(1))),
      );
    }
    return false;
  }
  await removeDeletedLibraryFileEntries(container, [filePath]);
  return true;
}
