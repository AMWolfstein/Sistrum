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
      onConvert: () => _showBatchConvertSheet(context, unifiedItems),
      onReplayGain: ({required remove}) =>
          _runBatchReplayGain(unifiedItems, remove: remove),
      onDelete: () => _deleteSelected(unifiedItems),
    );
  }

  Widget _buildFilterButton(BuildContext context) {
    return GestureDetector(
      onLongPress: _activeFilterCount > 0 ? _resetFilters : null,
      child: ExpressiveButton(
        text: true,
        onPressed: () => _showFilterSheet(context),
        icon: Badge(
          isLabelVisible: _activeFilterCount > 0,
          label: Text('$_activeFilterCount'),
          child: const AppIcon(Icons.filter_list, size: 18),
        ),
        style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
        child: Text(context.l10n.libraryFilterTitle),
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
    final inner = _buildUnifiedCoverImageInner(item, colorScheme, size);
    // Scanned SAF tracks also recover artwork after a transient provider error.
    if (!item.filePath.startsWith('content://')) return inner;
    return ValueListenableBuilder<int>(
      valueListenable: _embeddedCoverVersion,
      builder: (context, _, child) =>
          _buildUnifiedCoverImageInner(item, colorScheme, size),
    );
  }

  Widget _buildUnifiedCoverImageInner(
    UnifiedLibraryItem item,
    ColorScheme colorScheme, [
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
      final bgColor = colorScheme.secondaryContainer;
      final fgColor = colorScheme.onSecondaryContainer;
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
      final animated = Stack(
        fit: StackFit.expand,
        children: [
          buildPlaceholder(isLocal: true, recoverDocument: false),
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
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
  }) {
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

  /// A desktop click on a library song runs [open], what a tap does on a
  /// phone; modifier clicks select.
  void _desktopLibraryItemClick(
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> libraryItems,
    VoidCallback open,
  ) {
    handleDesktopRowClick(
      item.id,
      select: (kind) => _applyDesktopClick(item.id, kind, [
        for (final song in libraryItems) song.id,
      ]),
      activate: open,
    );
  }

  /// The desktop Songs table, after the [leadCount] active downloads. Songs
  /// load in pages already sorted, so the header sorts through the Library
  /// sort modes.
  Widget _buildLibrarySongTable(
    BuildContext context, {
    required String filterMode,
    required List<UnifiedLibraryItem> items,
    required int leadCount,
    required Widget Function(int index) leadBuilder,
    required VoidCallback Function(UnifiedLibraryItem item) openAction,
  }) {
    final table = _librarySongTableRows(
      filterMode,
      items,
      Theme.of(context).colorScheme,
      openAction,
    );
    final byId = table.byId;
    final sort = switch (_sortMode) {
      'a-z' => (column: TrackTableColumn.title, ascending: true),
      'z-a' => (column: TrackTableColumn.title, ascending: false),
      'artist-asc' => (column: TrackTableColumn.artist, ascending: true),
      'artist-desc' => (column: TrackTableColumn.artist, ascending: false),
      'album-asc' => (column: TrackTableColumn.album, ascending: true),
      'album-desc' => (column: TrackTableColumn.album, ascending: false),
      _ => null,
    };
    final inset = EdgeInsets.symmetric(horizontal: wideListInset(context));
    return SliverMainAxisGroup(
      slivers: [
        if (leadCount > 0)
          SliverPadding(
            padding: inset,
            sliver: SliverList.builder(
              itemCount: leadCount,
              itemBuilder: (context, index) => leadBuilder(index),
            ),
          ),
        SliverPadding(
          padding: inset,
          sliver: SliverTrackTable(
            rows: table.rows,
            uniformRowHeight: true,
            columns: const [
              TrackTableColumn.title,
              TrackTableColumn.artist,
              TrackTableColumn.album,
              TrackTableColumn.quality,
              TrackTableColumn.duration,
            ],
            selectedIds: _selectedIds,
            onSelect: _applyDesktopClick,
            // A click does what a tap does on a phone: by the Library song
            // tap setting, plays as the row's button does or opens the
            // details.
            onActivate: (id) {
              final item = byId[id];
              if (item == null) return;
              songRowTap(
                ref,
                path: item.filePath,
                openDetails: openAction(item),
                play: () => _LibraryPlaybackButton.run(
                  ref,
                  _libraryPlaybackKey(item),
                  () => _playLibraryItem(item, items),
                ),
              )();
            },
            onContextMenu: (id, position) {
              final item = byId[id];
              if (item == null) return;
              _showLibraryItemMenu(
                context,
                item,
                items,
                position,
                onOpenDetails: openAction(item),
              );
            },
            sort: sort,
            sortableColumns: const {
              TrackTableColumn.title,
              TrackTableColumn.artist,
              TrackTableColumn.album,
            },
            onSort: (next) {
              final mode = switch (next) {
                null => 'latest',
                (column: TrackTableColumn.title, ascending: true) => 'a-z',
                (column: TrackTableColumn.title, ascending: false) => 'z-a',
                (column: TrackTableColumn.artist, ascending: true) =>
                  'artist-asc',
                (column: TrackTableColumn.artist, ascending: false) =>
                  'artist-desc',
                (column: TrackTableColumn.album, ascending: true) =>
                  'album-asc',
                (column: TrackTableColumn.album, ascending: false) =>
                  'album-desc',
                _ => _sortMode,
              };
              _setSortMode(mode);
            },
          ),
        ),
      ],
    );
  }

  /// The Songs table rows for [items], kept while the songs, the quality
  /// label mode and [colorScheme] stay the same. A new list, such as one
  /// with another page loaded, reuses the rows of the songs it keeps.
  _LibrarySongTableRows _librarySongTableRows(
    String filterMode,
    List<UnifiedLibraryItem> items,
    ColorScheme colorScheme,
    VoidCallback Function(UnifiedLibraryItem item) openAction,
  ) {
    final qualityMode = _libraryQualityLabelMode;
    final cached = _songTableByMode[filterMode];
    final reusable =
        cached != null &&
        cached.qualityMode == qualityMode &&
        cached.colorScheme == colorScheme;
    if (reusable && identical(cached.items, items)) {
      return cached..openAction = openAction;
    }
    final previous = reusable ? cached.rowByItem : null;
    final rows = <TrackTableRow>[];
    final rowByItem = Map<UnifiedLibraryItem, TrackTableRow>.identity();
    final byId = <String, UnifiedLibraryItem>{};
    for (final item in items) {
      final row =
          previous?[item] ??
          TrackTableRow(
            id: item.id,
            title: item.trackName,
            artist: item.artistName,
            album: item.albumName,
            quality: item.qualityForMode(qualityMode),
            duration: switch (item.localItem?.duration) {
              final seconds? when seconds > 0 => Duration(seconds: seconds),
              _ => null,
            },
            leading: _buildUnifiedCoverImage(item, colorScheme, 32),
            titleTrailing: Builder(
              builder: (context) => SongRowButton(
                playButton: _LibraryPlaybackButton(
                  playbackKey: _libraryPlaybackKey(item),
                  playTooltip: context.l10n.a11yPlayTrackByArtist(
                    item.trackName,
                    item.artistName,
                  ),
                  // Cached rows must use the current page and sort order.
                  onPlay: () => _playLibraryItem(
                    item,
                    _songTableByMode[filterMode]?.items ?? items,
                  ),
                  inline: true,
                ),
                onOpenDetails: () =>
                    (_songTableByMode[filterMode]?.openAction ?? openAction)(
                      item,
                    )(),
                path: item.filePath,
              ),
            ),
            playback: mediaItemPlaybackUiProvider(_libraryPlaybackKey(item)),
          );
      rows.add(row);
      rowByItem[item] = row;
      byId[item.id] = item;
    }
    return _songTableByMode[filterMode] = _LibrarySongTableRows(
      items: items,
      qualityMode: qualityMode,
      colorScheme: colorScheme,
      rows: rows,
      rowByItem: rowByItem,
      byId: byId,
      openAction: openAction,
    );
  }

  Widget _buildUnifiedLibraryItem(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme, {
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
    required List<UnifiedLibraryItem> libraryItems,
    bool playlistDragEnabled = false,
  }) {
    final fileExistsListenable = _fileExistsListenable(item.filePath);
    final isSelected = _selectedIds.contains(item.id);
    final date = item.addedAt;
    final dateStr =
        '${shortMonthNames[date.month - 1]} ${date.day}, ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';

    final quality = item.qualityForMode(_libraryQualityLabelMode);
    final sourceLabel = context.l10n.librarySourceLocal;
    // The label text tells the sources apart; rows keep one neutral role.
    final sourceColor = colorScheme.surfaceContainerHighest;
    final sourceTextColor = colorScheme.onSurfaceVariant;

    final open = _libraryItemOpenAction(
      item,
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
    // The text reads the row's supporting colour, which follows the fill of
    // the current track; the badges keep their own surfaces.
    final subtitle = Builder(
      builder: (context) {
        final textColor = DefaultTextStyle.of(context).style.color;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            ClickableArtistName(
              artistName: item.artistName,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: textColor),
            ),
            const SizedBox(height: 2),
            Row(
              children: [
                Container(
                  padding: context.tokens.badgePadding,
                  decoration: BoxDecoration(
                    color: sourceColor,
                    borderRadius: context.tokens.borderRadiusBadge,
                  ),
                  child: Text(
                    sourceLabel,
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: sourceTextColor,
                      fontSize: context.tokens.badgeFontSize,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Flexible(
                  child: Text(
                    dateStr,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(
                      context,
                    ).textTheme.labelSmall?.copyWith(color: textColor),
                  ),
                ),
                if (quality != null && quality.isNotEmpty) ...[
                  const SizedBox(width: 8),
                  _buildLibraryQualityBadge(
                    context,
                    colorScheme,
                    quality,
                    listStyle: true,
                  ),
                ],
              ],
            ),
          ],
        );
      },
    );
    void play() => _playLibraryItem(item, libraryItems);
    // A song whose file is missing has no play action and keeps opening.
    final tap = songRowTap(
      ref,
      path: item.filePath,
      openDetails: open,
      play: () => fileExistsListenable.value
          ? _LibraryPlaybackButton.run(ref, playbackKey, play)
          : open(),
    );
    final trailing = ValueListenableBuilder<bool>(
      valueListenable: fileExistsListenable,
      builder: (context, fileExists, child) {
        if (fileExists) {
          return SongRowButton(
            playButton: _LibraryPlaybackButton(
              playbackKey: playbackKey,
              playTooltip: context.l10n.a11yPlayTrackByArtist(
                item.trackName,
                item.artistName,
              ),
              onPlay: play,
            ),
            onOpenDetails: open,
            path: item.filePath,
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
    final swipe = libraryItemQueueSwipe(ref, item);
    Widget card({bool current = false, bool playing = false}) => TrackCard(
      isSelectionMode: _isSelectionMode,
      isSelected: isSelected,
      isCurrent: current,
      isPlaying: playing,
      leadingArtwork: true,
      onTap: isDesktopPlatform
          ? () => _desktopLibraryItemClick(item, libraryItems, tap)
          : _isSelectionMode
          ? () => _toggleSelection(item.id)
          : tap,
      onContextMenu: (position) => _showLibraryItemMenu(
        context,
        item,
        libraryItems,
        position,
        onOpenDetails: open,
      ),
      onLongPress: playlistDragEnabled
          ? null
          : _isSelectionMode
          ? () => _selectRangeTo(item.id, libraryItems)
          : () => _enterSelectionMode(item.id),
      leading: leading,
      title: item.trackName,
      subtitle: subtitle,
      trailing: trailing,
      queueSwipe: swipe,
    );

    return Semantics(
      label: context.l10n.a11yTrackByArtist(item.trackName, item.artistName),
      selected: isSelected,
      // Rows follow their own file, so a track change rebuilds only the rows
      // involved.
      child: Consumer(
        builder: (context, ref, _) {
          final playback = ref.watch(mediaItemPlaybackUiProvider(playbackKey));
          return card(current: playback.isCurrent, playing: playback.isPlaying);
        },
      ),
    );
  }

  Widget _buildUnifiedGridItem(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme, {
    required List<LocalLibraryItem> localNavigationItems,
    required int? localNavigationIndex,
    required List<UnifiedLibraryItem> libraryItems,
    bool playlistDragEnabled = false,
  }) {
    final fileExistsListenable = _fileExistsListenable(item.filePath);
    final isSelected = _selectedIds.contains(item.id);
    final quality = item.qualityForMode(_libraryQualityLabelMode);

    final open = _libraryItemOpenAction(
      item,
      localNavigationItems: localNavigationItems,
      localNavigationIndex: localNavigationIndex,
    );
    final playbackKey = _libraryPlaybackKey(item);
    void play() => _playLibraryItem(item, libraryItems);
    // A song whose file is missing has no play action and keeps opening.
    final tap = songRowTap(
      ref,
      path: item.filePath,
      openDetails: open,
      play: () => fileExistsListenable.value
          ? _LibraryPlaybackButton.run(ref, playbackKey, play)
          : open(),
    );

    return TrackGridCard(
      isSelected: _isSelectionMode && isSelected,
      semanticLabel: context.l10n.a11yTrackByArtist(
        item.trackName,
        item.artistName,
      ),
      onTap: isDesktopPlatform
          ? () => _desktopLibraryItemClick(item, libraryItems, tap)
          : _isSelectionMode
          ? () => _toggleSelection(item.id)
          : tap,
      onContextMenu: (position) => _showLibraryItemMenu(
        context,
        item,
        libraryItems,
        position,
        onOpenDetails: open,
      ),
      onLongPress: playlistDragEnabled
          ? null
          : _isSelectionMode
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
                Icons.folder,
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
        // A desktop click selects, so the play button stays during selection.
        if (!_isSelectionMode || isDesktopPlatform)
          PositionedDirectional(
            end: 4,
            bottom: 4,
            child: ValueListenableBuilder<bool>(
              valueListenable: fileExistsListenable,
              builder: (context, fileExists, child) {
                if (fileExists) {
                  return SongRowButton(
                    playButton: _LibraryPlaybackButton(
                      playbackKey: playbackKey,
                      playTooltip: context.l10n.a11yPlayTrackByArtist(
                        item.trackName,
                        item.artistName,
                      ),
                      onPlay: play,
                      grid: true,
                    ),
                    onOpenDetails: open,
                    size: SongRowButtonSize.grid,
                    path: item.filePath,
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
    this.inline = false,
  });

  final PlaybackRowKey playbackKey;
  final String playTooltip;
  final VoidCallback onPlay;
  final bool grid;
  final bool inline;

  /// What the button does for [playback]: nothing while its song loads,
  /// pause or resume for the current song, and [onPlay] for any other.
  static VoidCallback? action(
    WidgetRef ref,
    MediaPlaybackUi playback,
    VoidCallback onPlay,
  ) => playback.isLoading
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

  /// Runs [action] for the song's playback state at the time of the call, as
  /// a row tap that does what the button does.
  static void run(
    WidgetRef ref,
    PlaybackRowKey playbackKey,
    VoidCallback onPlay,
  ) => action(
    ref,
    ref.read(mediaItemPlaybackUiProvider(playbackKey)),
    onPlay,
  )?.call();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playback = ref.watch(mediaItemPlaybackUiProvider(playbackKey));
    final icon = playback.isPlaying ? Icons.pause : Icons.play_arrow;
    final tooltip = playback.isPlaying ? context.l10n.actionPause : playTooltip;
    final onPressed = action(ref, playback, onPlay);

    if (grid) {
      return TrackGridPlayButton(
        tooltip: tooltip,
        onPressed: onPressed,
        icon: icon,
      );
    }

    final colorScheme = Theme.of(context).colorScheme;
    if (inline) {
      return AppIconButton(
        tooltip: tooltip,
        onPressed: onPressed,
        iconSize: 24,
        padding: EdgeInsets.zero,
        constraints: BoxConstraints.tightFor(
          width: context.tokens.minTouchTarget,
          height: context.tokens.minTouchTarget,
        ),
        icon: playback.isLoading
            ? AppLoadingIndicator(
                size: 18,
                strokeWidth: 2,
                color: colorScheme.primary,
              )
            : Icon(
                playback.isPlaying
                    ? Icons.pause_circle_filled_rounded
                    : playback.isCurrent
                    ? Icons.play_circle_fill_rounded
                    : Icons.play_circle_outline_rounded,
                color: playback.isCurrent
                    ? colorScheme.primary
                    : colorScheme.onSurfaceVariant,
              ),
      );
    }

    // Material draws a filled tonal icon button: a solid role pair rather
    // than a faded primary container.
    final material = !context.isMornye;
    return AppIconButton(
      onPressed: onPressed,
      icon: Icon(
        icon,
        color: material
            ? colorScheme.onSecondaryContainer
            : colorScheme.primary,
      ),
      tooltip: tooltip,
      style: IconButton.styleFrom(
        minimumSize: Size.square(context.tokens.minTouchTarget),
        backgroundColor: material
            ? colorScheme.secondaryContainer
            : colorScheme.primaryContainer.withValues(alpha: 0.3),
      ),
    );
  }
}

/// The desktop Songs table rows of one song list, with the inputs they were
/// built from.
class _LibrarySongTableRows {
  _LibrarySongTableRows({
    required this.items,
    required this.qualityMode,
    required this.colorScheme,
    required this.rows,
    required this.rowByItem,
    required this.byId,
    required this.openAction,
  });

  final List<UnifiedLibraryItem> items;
  final String qualityMode;
  final ColorScheme colorScheme;
  final List<TrackTableRow> rows;
  final Map<UnifiedLibraryItem, TrackTableRow> rowByItem;
  final Map<String, UnifiedLibraryItem> byId;

  /// Opens a song's details with the latest build's navigation lists, for
  /// the details buttons of rows kept from an earlier build.
  VoidCallback Function(UnifiedLibraryItem item) openAction;
}
