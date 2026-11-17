import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/replaygain_service.dart';
import 'package:sistrum/utils/logger.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/batch_progress_dialog.dart';

final _batchActionsLog = AppLogger('BatchActions');

/// Adds or removes ReplayGain tags for [selectedItems].
///
/// [onConfirmOpen] / [onConfirmClosed] let the caller hide and restore any
/// selection UI around the confirmation dialog; [onConfirmClosed] receives
/// whether the user confirmed.
Future<void> runBatchReplayGain(
  BuildContext sourceContext,
  List<UnifiedLibraryItem> selectedItems, {
  required VoidCallback onExitSelectionMode,
  bool remove = false,
  VoidCallback? onConfirmOpen,
  Future<void> Function(bool confirmed)? onConfirmClosed,
}) async {
  if (selectedItems.isEmpty) return;

  final container = ProviderScope.containerOf(sourceContext, listen: false);
  // The selection overlay can disappear when opening the confirmation dialog.
  final context = Navigator.of(sourceContext, rootNavigator: true).context;

  onConfirmOpen?.call();

  final title = remove
      ? context.l10n.trackRemoveReplayGain
      : context.l10n.replayGainBatchConfirmTitle;
  final confirmed = await showAppConfirmDialog(
    context,
    title: title,
    message: remove
        ? context.l10n.replayGainRemoveConfirmMessage(selectedItems.length)
        : context.l10n.replayGainBatchConfirmMessage(selectedItems.length),
    confirmLabel: title,
    confirmIsDefault: true,
  );

  await onConfirmClosed?.call(confirmed);

  if (!confirmed || !context.mounted) return;

  var cancelled = false;
  int successCount = 0;
  var unsupportedDecoder = false;
  final total = selectedItems.length;

  BatchProgressDialog.show(
    context: context,
    title: remove
        ? context.l10n.replayGainRemoving
        : context.l10n.replayGainBatchAnalyzing,
    total: total,
    icon: Icons.graphic_eq,
    onCancel: () {
      cancelled = true;
      BatchProgressDialog.dismiss(context);
    },
  );

  for (int i = 0; i < total; i++) {
    if (!context.mounted || cancelled) break;
    final item = selectedItems[i];
    BatchProgressDialog.update(current: i + 1, detail: item.trackName);
    try {
      final ok = remove
          ? await ReplayGainService.removeFromFile(item.filePath)
          : await ReplayGainService.applyToFile(
              item.filePath,
              onUnsupportedDecoder: () => unsupportedDecoder = true,
            );
      if (ok) {
        successCount++;
        try {
          if (item.historyItem case final history?) {
            await container
                .read(downloadHistoryProvider.notifier)
                .updateAudioMetadataForItem(
                  id: history.id,
                  hasReplayGain: !remove,
                  replayGainMetadataScanVersion: 1,
                );
          }
          if (item.localItem case final local?) {
            await LibraryDatabase.instance.updateAudioMetadata(
              local.id,
              hasReplayGain: !remove,
            );
          }
        } catch (e) {
          _batchActionsLog.w('Could not refresh ReplayGain availability: $e');
        }
      }
    } catch (_) {}
  }

  onExitSelectionMode();

  if (successCount > 0 && context.mounted) {
    await container.read(localLibraryProvider.notifier).reloadFromStorage();
  }

  if (!context.mounted) return;
  if (!cancelled) {
    BatchProgressDialog.dismiss(context);
  }
  ScaffoldMessenger.of(context).clearSnackBars();
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(
        [
          remove
              ? context.l10n.replayGainRemoveBatchSuccess(successCount, total)
              : context.l10n.replayGainBatchSuccess(successCount, total),
          if (unsupportedDecoder) context.l10n.replayGainUnsupportedDecoder,
        ].join('\n'),
      ),
    ),
  );
}
