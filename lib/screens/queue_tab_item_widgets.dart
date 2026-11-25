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

  Widget _buildUnifiedCoverImage(
    UnifiedLibraryItem item,
    ColorScheme colorScheme,
    double size,
  ) {
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
    ColorScheme colorScheme,
    double size,
  ) {
    final cacheSize = (size * 2).toInt();
    final iconSize = size * 0.4;

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
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Center(
          child: AppIcon(Icons.music_note, color: fgColor, size: iconSize),
        ),
      );
    }

    Widget fadeInFileImage(Widget child, int? frame, bool wasSync) {
      if (wasSync) return child;
      return SizedBox(
        width: size,
        height: size,
        child: Stack(
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
        ),
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

    return buildPlaceholder();
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

  /// The desktop Songs table. Songs load in pages already sorted, so the
  /// header sorts through the Library sort modes.
  Widget _buildLibrarySongTable(
    BuildContext context, {
    required String filterMode,
    required List<UnifiedLibraryItem> items,
  }) {
    final table = _librarySongTableRows(
      filterMode,
      items,
      Theme.of(context).colorScheme,
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
            // A click plays the song, as a tap does on a phone.
            onActivate: (id) {
              final item = byId[id];
              if (item == null) return;
              _LibraryPlaybackButton.run(
                ref,
                _libraryPlaybackKey(item),
                () => _playLibraryItem(item, items),
              );
            },
            onContextMenu: (id, position) {
              final item = byId[id];
              if (item == null) return;
              _showLibraryItemMenu(context, item, items, position);
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
  ) {
    final cached = _songTableByMode[filterMode];
    final reusable = cached != null && cached.colorScheme == colorScheme;
    if (reusable && identical(cached.items, items)) {
      return cached;
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
            quality: item.badgeQuality,
            duration: switch (item.localItem?.duration) {
              final seconds? when seconds > 0 => Duration(seconds: seconds),
              _ => null,
            },
            leading: _buildUnifiedCoverImage(item, colorScheme, 32),
            titleTrailing: Builder(
              builder: (context) => Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (item.isExplicit) ...[
                    const ExplicitChip(),
                    const SizedBox(width: 6),
                  ],
                  _LibraryPlaybackButton(
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
                  ),
                ],
              ),
            ),
            trailing: SongMenuButton(
              track: item.toTrack(),
              filePath: item.filePath,
              localItem: item.localItem,
              dense: true,
            ),
            playback: mediaItemPlaybackUiProvider(_libraryPlaybackKey(item)),
          );
      rows.add(row);
      rowByItem[item] = row;
      byId[item.id] = item;
    }
    return _songTableByMode[filterMode] = _LibrarySongTableRows(
      items: items,
      colorScheme: colorScheme,
      rows: rows,
      rowByItem: rowByItem,
      byId: byId,
    );
  }

  Widget _buildUnifiedLibraryItem(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme, {
    required List<UnifiedLibraryItem> libraryItems,
    bool playlistDragEnabled = false,
  }) {
    final fileExistsListenable = _fileExistsListenable(item.filePath);
    final isSelected = _selectedIds.contains(item.id);
    void openMenu() => _openLibraryItemMenu(item);
    final playbackKey = _libraryPlaybackKey(item);
    final leading = Hero(
      tag: 'cover_lib_${item.id}',
      child: _buildUnifiedCoverImage(
        item,
        colorScheme,
        context.tokens.coverList,
      ),
    );
    void play() => _playLibraryItem(item, libraryItems);
    final swipe = libraryItemQueueSwipe(ref, item);

    return Semantics(
      label: context.l10n.a11yTrackByArtist(item.trackName, item.artistName),
      selected: isSelected,
      // Rows follow their own file, so a track change rebuilds only the rows
      // involved.
      child: Consumer(
        builder: (context, ref, _) {
          final playback = ref.watch(mediaItemPlaybackUiProvider(playbackKey));
          return ValueListenableBuilder<bool>(
            valueListenable: fileExistsListenable,
            builder: (context, fileExists, _) {
              // A song whose file is missing has no play action; its tap
              // opens the song menu.
              final VoidCallback tap = fileExists
                  ? () => _LibraryPlaybackButton.run(ref, playbackKey, play)
                  : openMenu;
              return TrackCard(
                style: TrackCardStyle.flat,
                isSelectionMode: _isSelectionMode,
                isSelected: isSelected,
                isCurrent: playback.isCurrent,
                isPlaying: playback.isPlaying,
                leadingArtwork: true,
                leading: leading,
                title: item.trackName,
                subtitle: ClickableArtistName(artistName: item.artistName),
                isExplicit: item.isExplicit,
                onPlay: fileExists ? tap : null,
                // A song whose file is missing shows why it cannot play.
                trailing: fileExists
                    ? null
                    : Tooltip(
                        message: context.l10n.queueDownloadedFileMissing,
                        child: AppIcon(
                          Icons.error_outline,
                          color: colorScheme.error,
                          size: 20,
                        ),
                      ),
                menu: _isSelectionMode
                    ? null
                    : SongMenuButton(
                        track: item.toTrack(),
                        filePath: item.filePath,
                        localItem: item.localItem,
                      ),
                onTap: isDesktopPlatform
                    ? () => _desktopLibraryItemClick(item, libraryItems, tap)
                    : _isSelectionMode
                    ? () => _toggleSelection(item.id)
                    : tap,
                onContextMenu: (position) =>
                    _showLibraryItemMenu(context, item, libraryItems, position),
                onLongPress: playlistDragEnabled
                    ? null
                    : _isSelectionMode
                    ? () => _selectRangeTo(item.id, libraryItems)
                    : () => _enterSelectionMode(item.id),
                queueSwipe: swipe,
              );
            },
          );
        },
      ),
    );
  }
}

class _LibraryPlaybackButton extends ConsumerWidget {
  const _LibraryPlaybackButton({
    required this.playbackKey,
    required this.playTooltip,
    required this.onPlay,
  });

  final PlaybackRowKey playbackKey;
  final String playTooltip;
  final VoidCallback onPlay;

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
    final tooltip = playback.isPlaying ? context.l10n.actionPause : playTooltip;
    final onPressed = action(ref, playback, onPlay);
    final colorScheme = Theme.of(context).colorScheme;
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
}

/// The desktop Songs table rows of one song list, with the inputs they were
/// built from.
class _LibrarySongTableRows {
  _LibrarySongTableRows({
    required this.items,
    required this.colorScheme,
    required this.rows,
    required this.rowByItem,
    required this.byId,
  });

  final List<UnifiedLibraryItem> items;
  final ColorScheme colorScheme;
  final List<TrackTableRow> rows;
  final Map<UnifiedLibraryItem, TrackTableRow> rowByItem;
  final Map<String, UnifiedLibraryItem> byId;
}
