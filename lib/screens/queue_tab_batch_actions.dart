part of 'queue_tab.dart';

extension _QueueTabBatchActions on _QueueTabState {
  Future<void> _runBatchReplayGain(
    List<UnifiedLibraryItem> allItems, {
    bool remove = false,
  }) {
    return runBatchReplayGain(
      context,
      _selectedItemsFromAll(allItems),
      onExitSelectionMode: _exitSelectionMode,
      remove: remove,
      onConfirmOpen: () {
        _suppressSelectionOverlay = true;
        _hideSelectionOverlay();
        _hidePlaylistSelectionOverlay();
      },
      onConfirmClosed: (confirmed) async {
        if (!mounted) {
          _suppressSelectionOverlay = false;
          return;
        }
        if (confirmed) {
          _suppressSelectionOverlay = false;
          return;
        }
        // Restore after the dialog's exit animation.
        await Future<void>.delayed(const Duration(milliseconds: 220));
        _suppressSelectionOverlay = false;
        if (!mounted) return;
        if (_isSelectionMode) {
          _syncSelectionOverlay(
            items: allItems,
            bottomPadding: MediaQuery.paddingOf(context).bottom,
          );
        }
      },
    );
  }
}
