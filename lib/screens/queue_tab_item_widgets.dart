part of 'queue_tab.dart';

extension _QueueTabItemWidgets on _QueueTabState {
  Widget _buildSelectionBottomBar(
    BuildContext context,
    ColorScheme colorScheme,
    List<UnifiedLibraryItem> unifiedItems,
    double bottomPadding,
  ) {
    final selectedCount = _selectedIds.length;
    final allSelected =
        selectedCount == unifiedItems.length && unifiedItems.isNotEmpty;

    return LibraryTrackSelectionBar(
      playbackActions: LibrarySelectionPlaybackActions(
        items: _selectedItemsFromAll(unifiedItems),
        onClose: _exitSelectionMode,
      ),
      selectedCount: selectedCount,
      allSelected: allSelected,
      onClose: _exitSelectionMode,
      onToggleSelectAll: () {
        if (allSelected) {
          _exitSelectionMode();
        } else {
          _selectAll(unifiedItems);
        }
      },
      bottomPadding: bottomPadding,
      onReplayGain: ({required remove}) =>
          _runBatchReplayGain(unifiedItems, remove: remove),
      onDelete: () => _deleteSelected(unifiedItems),
    );
  }

  /// Preserve folder recovery errors before the model's generic type mapping.
  /// Legacy permission failures also need the picker instead of another retry.
  /// Download error messages are stored as fixed English sentinels on the
  /// item (so code can match on them); translate the known ones for display.
  /// sheet elsewhere.
  Widget _buildFilterButton(BuildContext context) {
    return GestureDetector(
      onLongPress: _activeFilterCount > 0 ? _resetFilters : null,
      child: TextButton.icon(
        onPressed: () => _showFilterSheet(context),
        icon: Badge(
          isLabelVisible: _activeFilterCount > 0,
          label: Text('$_activeFilterCount'),
          child: const AppIcon(Icons.filter_list, size: 18),
        ),
        style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
        label: Text(context.l10n.libraryFilterTitle),
      ),
    );
  }

  /// When [size] is provided, renders at fixed dimensions (list mode).
  /// When [size] is null, fills the parent container (grid mode).
  Widget _buildUnifiedCoverImage(
    UnifiedLibraryItem item,
    ColorScheme colorScheme, [
    double? size,
  ]) {
    final isDownloaded = item.source == LibraryItemSource.downloaded;

    // Scanned SAF tracks also recover artwork after a transient provider error.
    if (isDownloaded || item.filePath.startsWith('content://')) {
      return ValueListenableBuilder<int>(
        valueListenable: _embeddedCoverVersion,
        builder: (context, _, child) =>
            _buildUnifiedCoverImageInner(item, colorScheme, isDownloaded, size),
      );
    }

    return _buildUnifiedCoverImageInner(item, colorScheme, isDownloaded, size);
  }

  Widget _buildUnifiedCoverImageInner(
    UnifiedLibraryItem item,
    ColorScheme colorScheme,
    bool isDownloaded, [
    double? size,
  ]) {
    final cacheSize = size != null ? (size * 2).toInt() : 200;
    final iconSize = size != null ? size * 0.4 : 32.0;

    Widget buildPlaceholder({
      bool isLocal = false,
      bool recoverDocument = true,
    }) {
      if (recoverDocument && item.filePath.startsWith('content://')) {
        final recovered = _resolveDownloadedEmbeddedCoverPath(item.filePath);
        if (recovered != null) {
          return ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.file(
              File(recovered),
              width: size,
              height: size,
              fit: BoxFit.cover,
              cacheWidth: cacheSize,
              errorBuilder: (_, _, _) =>
                  buildPlaceholder(isLocal: isLocal, recoverDocument: false),
            ),
          );
        }
      }
      final bgColor = (isDownloaded && !isLocal)
          ? colorScheme.surfaceContainerHighest
          : colorScheme.secondaryContainer;
      final fgColor = (isDownloaded && !isLocal)
          ? colorScheme.onSurfaceVariant
          : colorScheme.onSecondaryContainer;
      return Container(
        width: size,
        height: size,
        decoration: size != null
            ? BoxDecoration(
                color: bgColor,
                borderRadius: BorderRadius.circular(8),
              )
            : null,
        color: size != null ? null : bgColor,
        child: Center(
          child: AppIcon(Icons.music_note, color: fgColor, size: iconSize),
        ),
      );
    }

    Widget fadeInFileImage(Widget child, int? frame, bool wasSync) {
      if (wasSync) return child;
      final Widget backdrop;
      if (isDownloaded && item.coverUrl != null) {
        backdrop = CachedCoverImage(
          imageUrl: item.coverUrl!,
          width: size,
          height: size,
          memCacheWidth: cacheSize,
          placeholder: (context, url) =>
              buildPlaceholder(recoverDocument: false),
          errorWidget: (context, url, error) => buildPlaceholder(),
        );
      } else {
        backdrop = buildPlaceholder(
          isLocal: !isDownloaded,
          recoverDocument: false,
        );
      }
      final animated = Stack(
        fit: StackFit.expand,
        children: [
          backdrop,
          AnimatedOpacity(
            opacity: frame == null ? 0.0 : 1.0,
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            child: child,
          ),
        ],
      );
      if (size == null) return animated;
      return SizedBox(width: size, height: size, child: animated);
    }

    if (isDownloaded) {
      final embeddedCoverPath = _resolveDownloadedEmbeddedCoverPath(
        item.filePath,
      );
      if (embeddedCoverPath != null) {
        return ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.file(
            File(embeddedCoverPath),
            width: size,
            height: size,
            fit: BoxFit.cover,
            cacheWidth: cacheSize,
            gaplessPlayback: true,
            frameBuilder: (context, child, frame, wasSynchronouslyLoaded) =>
                fadeInFileImage(child, frame, wasSynchronouslyLoaded),
            errorBuilder: (context, error, stackTrace) => buildPlaceholder(),
          ),
        );
      }
    }

    if (item.coverUrl != null) {
      return CachedCoverImage(
        imageUrl: item.coverUrl!,
        width: size,
        height: size,
        memCacheWidth: cacheSize,
        borderRadius: BorderRadius.circular(8),
        placeholder: (context, url) => buildPlaceholder(),
        errorWidget: (context, url, error) => buildPlaceholder(),
        fadeInDuration: const Duration(milliseconds: 180),
        fadeOutDuration: const Duration(milliseconds: 90),
      );
    }

    if (item.localCoverPath != null && item.localCoverPath!.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.file(
          File(item.localCoverPath!),
          width: size,
          height: size,
          fit: BoxFit.cover,
          cacheWidth: cacheSize,
          gaplessPlayback: true,
          frameBuilder: (context, child, frame, wasSynchronouslyLoaded) =>
              fadeInFileImage(child, frame, wasSynchronouslyLoaded),
          errorBuilder: (context, error, stackTrace) =>
              buildPlaceholder(isLocal: true),
        ),
      );
    }

    if (size != null) {
      return buildPlaceholder();
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: buildPlaceholder(),
    );
  }

  /// A tap on a library song outside selection mode: opens its metadata, or
  /// the bare file when the song has no history or library record.
  VoidCallback _libraryItemOpenAction(
    UnifiedLibraryItem item, {
    required List<DownloadHistoryItem> downloadedNavigationItems,
    required int? downloadedNavigationIndex,
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
  }) {
    if (item.source == LibraryItemSource.downloaded) {
      return () => _navigateToHistoryMetadataScreen(
        item.historyItem!,
        navigationItems: downloadedNavigationItems,
        navigationIndex: downloadedNavigationIndex,
      );
    }
    if (item.localItem != null) {
      return () => _navigateToLocalMetadataScreen(
        item.localItem!,
        navigationItems: localNavigationItems,
        navigationIndex: localNavigationIndex,
      );
    }
    return () => _openFile(
      item.filePath,
      title: item.trackName,
      artist: item.artistName,
      album: item.albumName,
      coverUrl: item.coverUrl ?? item.localCoverPath ?? '',
    );
  }

  Widget _buildUnifiedLibraryItem(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme, {
    required List<DownloadHistoryItem> downloadedNavigationItems,
    required int? downloadedNavigationIndex,
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
    required List<UnifiedLibraryItem> libraryItems,
  }) {
    final fileExistsListenable = _fileExistsListenable(item.filePath);
    final isSelected = _selectedIds.contains(item.id);

    final open = _libraryItemOpenAction(
      item,
      downloadedNavigationItems: downloadedNavigationItems,
      downloadedNavigationIndex: downloadedNavigationIndex,
      localNavigationItems: localNavigationItems,
      localNavigationIndex: localNavigationIndex,
    );

    final playbackKey = _libraryPlaybackKey(item);
    final leading = Hero(
      tag: 'cover_lib_${item.id}',
      child: _buildUnifiedCoverImage(
        item,
        colorScheme,
        context.tokens.coverList,
      ),
    );
    final subtitle = ClickableArtistName(
      artistName: item.artistName,
      style: Theme.of(context).textTheme.bodySmall,
    );
    final trailing = ValueListenableBuilder<bool>(
      valueListenable: fileExistsListenable,
      builder: (context, fileExists, child) {
        if (fileExists) {
          return _LibraryPlaybackButton(
            playbackKey: playbackKey,
            playTooltip: context.l10n.a11yPlayTrackByArtist(
              item.trackName,
              item.artistName,
            ),
            onPlay: () => _playLibraryItems(libraryItems, start: item),
          );
        }
        return Tooltip(
          message: context.l10n.queueDownloadedFileMissing,
          child: AppIcon(
            Icons.error_outline,
            color: colorScheme.error,
            size: 20,
          ),
        );
      },
    );
    final card = TrackCard(
      style: TrackCardStyle.flat,
      isSelectionMode: _isSelectionMode,
      isSelected: isSelected,
      onTap: _isSelectionMode ? () => _toggleSelection(item.id) : open,
      onLongPress: _isSelectionMode
          ? () => _selectRangeTo(item.id, libraryItems)
          : () => _enterSelectionMode(item.id),
      leading: leading,
      title: item.trackName,
      explicit: item.explicit,
      subtitle: subtitle,
      trailing: trailing,
    );

    return Semantics(
      label: context.l10n.a11yTrackByArtist(item.trackName, item.artistName),
      selected: isSelected,
      // Rows show no current-track state.
      child: card,
    );
  }

  Widget _buildUnifiedGridItem(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme, {
    required List<DownloadHistoryItem> downloadedNavigationItems,
    required int? downloadedNavigationIndex,
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
    required List<UnifiedLibraryItem> libraryItems,
  }) {
    final fileExistsListenable = _fileExistsListenable(item.filePath);
    final isSelected = _selectedIds.contains(item.id);
    final isDownloaded = item.source == LibraryItemSource.downloaded;
    final quality = item.qualityForMode(_libraryQualityLabelMode);

    final open = _libraryItemOpenAction(
      item,
      downloadedNavigationItems: downloadedNavigationItems,
      downloadedNavigationIndex: downloadedNavigationIndex,
      localNavigationItems: localNavigationItems,
      localNavigationIndex: localNavigationIndex,
    );

    return TrackGridCard(
      isSelected: _isSelectionMode && isSelected,
      semanticLabel: context.l10n.a11yTrackByArtist(
        item.trackName,
        item.artistName,
      ),
      onTap: _isSelectionMode ? () => _toggleSelection(item.id) : open,
      onLongPress: _isSelectionMode
          ? () => _selectRangeTo(item.id, libraryItems)
          : () => _enterSelectionMode(item.id),
      cover: Hero(
        tag: 'cover_lib_${item.id}',
        child: _buildUnifiedCoverImage(item, colorScheme),
      ),
      overlays: [
        if (!_isSelectionMode)
          PositionedDirectional(
            end: 4,
            top: 4,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              // The glyph tells the sources apart; the badge stays neutral.
              decoration: BoxDecoration(
                color: colorScheme.surfaceContainerHighest,
                borderRadius: context.tokens.borderRadiusBadge,
              ),
              child: AppIcon(
                isDownloaded ? Icons.download_done : Icons.folder,
                size: 12,
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        if (quality != null && quality.isNotEmpty)
          PositionedDirectional(
            start: 4,
            end: _isSelectionMode ? 4 : 28,
            top: 4,
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: _buildLibraryQualityBadge(context, colorScheme, quality),
            ),
          ),
        if (!_isSelectionMode)
          PositionedDirectional(
            end: 4,
            bottom: 4,
            child: ValueListenableBuilder<bool>(
              valueListenable: fileExistsListenable,
              builder: (context, fileExists, child) {
                if (fileExists) {
                  return _LibraryPlaybackButton(
                    playbackKey: _libraryPlaybackKey(item),
                    playTooltip: context.l10n.a11yPlayTrackByArtist(
                      item.trackName,
                      item.artistName,
                    ),
                    onPlay: () => _playLibraryItems(libraryItems, start: item),
                    grid: true,
                  );
                }
                return Tooltip(
                  message: context.l10n.queueDownloadedFileMissing,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(
                      color: colorScheme.errorContainer,
                      shape: BoxShape.circle,
                    ),
                    child: AppIcon(
                      Icons.error_outline,
                      color: colorScheme.error,
                      size: 14,
                    ),
                  ),
                );
              },
            ),
          ),
        if (_isSelectionMode)
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: isSelected
                    ? colorScheme.primary.withValues(alpha: 0.3)
                    : Colors.transparent,
                borderRadius: context.tokens.borderRadiusThumb,
              ),
            ),
          ),
        if (_isSelectionMode)
          PositionedDirectional(
            end: 4,
            top: 4,
            child: AnimatedSelectionCheckbox(
              selected: isSelected,
              colorScheme: colorScheme,
              size: 24,
              unselectedColor: colorScheme.surface,
            ),
          ),
      ],
      title: item.trackName,
      explicit: item.explicit,
      // Not the history service: see the list row above.
      subtitle: ClickableArtistName(
        artistName: item.artistName,
        style: Theme.of(
          context,
        ).textTheme.labelSmall?.copyWith(color: colorScheme.onSurfaceVariant),
      ),
    );
  }
}

class _LibraryPlaybackButton extends ConsumerWidget {
  const _LibraryPlaybackButton({
    required this.playbackKey,
    required this.playTooltip,
    required this.onPlay,
    this.grid = false,
  });

  final PlaybackRowKey playbackKey;
  final String playTooltip;
  final VoidCallback onPlay;
  final bool grid;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playback = ref.watch(mediaItemPlaybackUiProvider(playbackKey));
    final icon = playback.isPlaying ? Icons.pause : Icons.play_arrow;
    final tooltip = playback.isPlaying ? context.l10n.actionPause : playTooltip;
    final onPressed = playback.isLoading
        ? null
        : () {
            if (playback.isCurrent) {
              ref
                  .read(musicPlayerControllerProvider)
                  .togglePlayPause(playback.isPlaying);
            } else {
              onPlay();
            }
          };
    if (grid) {
      return TrackGridPlayButton(
        tooltip: tooltip,
        onPressed: onPressed,
        icon: icon,
      );
    }
    final colorScheme = Theme.of(context).colorScheme;
    return IconButton(
      onPressed: onPressed,
      icon: Icon(icon, color: colorScheme.primary),
      tooltip: tooltip,
      style: IconButton.styleFrom(
        minimumSize: Size.square(context.tokens.minTouchTarget),
        backgroundColor: colorScheme.primaryContainer.withValues(alpha: 0.3),
      ),
    );
  }
}
