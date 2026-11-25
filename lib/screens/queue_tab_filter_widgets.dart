part of 'queue_tab.dart';

extension _QueueTabFilterWidgets on _QueueTabState {
  /// Count text left, action buttons right, always on one line; on narrow
  /// widths the buttons scale down instead of truncating the count.
  Widget _countHeaderRow(
    BuildContext context,
    String countText,
    List<Widget> actions,
  ) {
    final edge = 16 + wideListInset(context);
    return Padding(
      padding: EdgeInsets.fromLTRB(edge, 8, edge, 8),
      child: Row(
        children: [
          Text(
            countText,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: AlignmentDirectional.centerEnd,
              child: Row(mainAxisSize: MainAxisSize.min, children: actions),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterContent({
    required BuildContext context,
    required ColorScheme colorScheme,
    required String filterMode,
    required _FilterContentData filterData,
    required LibraryCollectionsState collectionState,
    required bool hasMoreLibrary,
    required bool isPageLoading,
  }) {
    final showFilteringIndicator = filterData.showFilteringIndicator;
    final filteredGroupedLocalAlbums = filterData.filteredGroupedLocalAlbums;
    final orderedAlbums = filterData.orderedAlbums;
    final filteredUnifiedItems = filterData.filteredUnifiedItems;
    final totalTrackCount = filterData.totalTrackCount;
    final totalAlbumCount = filterData.totalAlbumCount;
    // Controls and grids share the centered column of the track list.
    final edge = 16 + wideListInset(context);

    // The Playlists page leads with Loved and Favorite artists;
    // the Songs list holds songs only.
    final specialEntries = filterMode == 'playlists'
        ? _getVisibleCollectionEntries(collectionState)
              .where((entry) => entry.type != _CollectionEntryType.playlist)
              .toList(growable: false)
        : const <_CollectionEntry>[];
    // Indexes into collectionState.playlists, in the sort the page shows.
    final playlistIndexes = <int>[];
    if (filterMode == 'playlists') {
      for (var i = 0; i < collectionState.playlists.length; i++) {
        playlistIndexes.add(i);
      }
      playlistIndexes.sort((a, b) {
        final first = collectionState.playlists[a];
        final second = collectionState.playlists[b];
        return switch (_sortMode) {
          'a-z' => first.name.toLowerCase().compareTo(
            second.name.toLowerCase(),
          ),
          'z-a' => second.name.toLowerCase().compareTo(
            first.name.toLowerCase(),
          ),
          'oldest' => first.createdAt.compareTo(second.createdAt),
          _ => second.updatedAt.compareTo(first.updatedAt),
        };
      });
    }

    // Songs drag onto playlists.
    final playlistDrag = filterMode == 'all';
    Widget trackTile(UnifiedLibraryItem item) {
      final tile = _buildUnifiedLibraryItem(
        context,
        item,
        colorScheme,
        libraryItems: filteredUnifiedItems,
        playlistDragEnabled: playlistDrag,
      );
      if (!playlistDrag) return tile;
      return LibraryPlaylistDragSource<UnifiedLibraryItem>(
        data: item,
        onSelect: () => _selectDraggedTrack(item, filteredUnifiedItems),
        onDragStarted: _startLibraryTrackDrag,
        onDragEnd: _endLibraryTrackDrag,
        feedbackBuilder: (context) =>
            _buildDragFeedback(context, item, colorScheme),
        child: tile,
      );
    }

    // Songs offer Play and Shuffle while no selection is made.
    final showSongActions = filterMode == 'all' && !_isSelectionMode;
    void playSongs({required bool shuffle}) => _playLibrarySongs(
      filteredUnifiedItems,
      complete: filteredUnifiedItems.length >= totalTrackCount,
      shuffle: shuffle,
    );

    Widget trackCountHeader() => SliverToBoxAdapter(
      child: _countHeaderRow(
        context,
        context.l10n.queueTrackCount(totalTrackCount),
        const [],
      ),
    );

    final content = CustomScrollView(
      slivers: [
        if (totalTrackCount > 0 && showSongActions)
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(edge, 8, edge, 0),
              child: AlbumPlayActions(
                playLabel: context.l10n.tooltipPlay,
                shuffleTooltip: context.l10n.actionShuffle,
                onPlay: () => playSongs(shuffle: false),
                onShuffle: () => playSongs(shuffle: true),
              ),
            ),
          ),

        if (totalTrackCount > 0 && filterMode == 'all') trackCountHeader(),

        if (filteredGroupedLocalAlbums.isNotEmpty && filterMode == 'albums')
          SliverToBoxAdapter(
            child: _countHeaderRow(
              context,
              context.l10n.queueAlbumCount(totalAlbumCount),
              const [],
            ),
          ),

        if (showFilteringIndicator)
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(edge, 12, edge, 8),
              child: Row(
                children: [
                  AppLoadingIndicator(
                    size: 18,
                    strokeWidth: 2,
                    color: colorScheme.primary,
                  ),
                  const SizedBox(width: 12),
                  Text(
                    context.l10n.queueFilteringIndicator,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),

        if (filterMode == 'albums' && filteredGroupedLocalAlbums.isNotEmpty)
          SliverPadding(
            padding: EdgeInsets.symmetric(horizontal: edge),
            sliver: AnimatedLibrarySliverGrid(
              maxCrossAxisExtent: _QueueTabState._libraryAlbumGridExtent,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.72,
              delegate: SliverChildBuilderDelegate((context, index) {
                return switch (orderedAlbums[index]) {
                  final _GroupedLocalAlbum album => KeyedSubtree(
                    key: ValueKey('local_${album.key}'),
                    child: _buildLocalAlbumGridItem(
                      context,
                      album,
                      colorScheme,
                    ),
                  ),
                };
              }, childCount: orderedAlbums.length),
            ),
          ),

        if (filterMode == 'playlists' &&
            (playlistIndexes.isNotEmpty || specialEntries.isNotEmpty)) ...[
          SliverToBoxAdapter(
            child: _countHeaderRow(
              context,
              context.l10n.queuePlaylistCount(playlistIndexes.length),
              [
                if (!_isPlaylistSelectionMode)
                  TextButton.icon(
                    onPressed: () => _showCreatePlaylistDialog(context),
                    icon: const Icon(Icons.add, size: 20),
                    style: TextButton.styleFrom(
                      visualDensity: VisualDensity.compact,
                    ),
                    label: Text(context.l10n.collectionCreatePlaylist),
                  ),
              ],
            ),
          ),
          SliverPadding(
            padding: EdgeInsets.symmetric(horizontal: edge),
            sliver: AnimatedLibrarySliverGrid(
              maxCrossAxisExtent: _QueueTabState._libraryAlbumGridExtent,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.72,
              delegate: SliverChildBuilderDelegate((context, index) {
                // Loved and Favorite artists lead the grid.
                if (index < specialEntries.length) {
                  final entry = specialEntries[index];
                  return KeyedSubtree(
                    key: ValueKey('plgrid_special_${entry.type.name}'),
                    child: _buildAllTabGridCollectionItem(
                      context: context,
                      colorScheme: colorScheme,
                      entry: entry,
                      collectionState: collectionState,
                    ),
                  );
                }
                final playlistIndex =
                    playlistIndexes[index - specialEntries.length];
                return KeyedSubtree(
                  key: ValueKey(
                    'plgrid_${collectionState.playlists[playlistIndex].id}',
                  ),
                  child: _buildAllTabGridCollectionItem(
                    context: context,
                    colorScheme: colorScheme,
                    entry: _CollectionEntry.playlist(playlistIndex),
                    collectionState: collectionState,
                  ),
                );
              }, childCount: specialEntries.length + playlistIndexes.length),
            ),
          ),
        ],

        if (filterMode == 'all')
          if (isDesktopPlatform && filteredUnifiedItems.isNotEmpty)
            // Desktop lists songs in the sortable table.
            _buildLibrarySongTable(
              context,
              filterMode: filterMode,
              items: filteredUnifiedItems,
            )
          else
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate((context, index) {
                  final item = filteredUnifiedItems[index];
                  return TrackCardSegment.at(
                    key: ValueKey(item.id),
                    index: index,
                    count: filteredUnifiedItems.length,
                    child: trackTile(item),
                  );
                }, childCount: filteredUnifiedItems.length),
              ),
            ),

        if (totalTrackCount == 0 &&
            (filterMode != 'albums' || (filteredGroupedLocalAlbums.isEmpty)) &&
            (filterMode != 'playlists' ||
                (playlistIndexes.isEmpty && specialEntries.isEmpty)) &&
            !showFilteringIndicator &&
            !isPageLoading)
          SliverFillRemaining(
            hasScrollBody: false,
            child: _buildEmptyState(context, colorScheme, filterMode),
          )
        else if (isPageLoading)
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(edge, 12, edge, 8),
              child: Center(
                child: AppLoadingIndicator(
                  size: 22,
                  strokeWidth: 2,
                  color: colorScheme.primary,
                ),
              ),
            ),
          ),

        if (totalTrackCount > 0 ||
            (filterMode == 'albums' && filteredGroupedLocalAlbums.isNotEmpty) ||
            (filterMode == 'playlists' &&
                (playlistIndexes.isNotEmpty || specialEntries.isNotEmpty)))
          SliverToBoxAdapter(
            child: SizedBox(height: _isSelectionMode ? 100 : 16),
          ),
        const NavBarSliverSpacer(),
      ],
    );

    // The fast scroller follows the loaded rows. Near-end notifications load
    // the next page while a drag continues.
    final albumCount = orderedAlbums.length;
    String? labelAt(double fraction) {
      if (filterMode == 'albums') {
        if (albumCount == 0) return null;
        final index = (fraction * (albumCount - 1)).round();
        final (name, artist, added) = switch (orderedAlbums[index]) {
          final _GroupedLocalAlbum album => (
            album.albumName,
            album.artistName,
            album.latestScanned,
          ),
        };
        return _albumSectionLabel(
          context,
          album: name,
          artist: artist,
          added: added,
        );
      }
      if (filterMode != 'all') return null;
      final rows = filteredUnifiedItems.length;
      if (rows == 0) return null;
      final index = (fraction * (rows - 1)).round().clamp(0, rows - 1);
      return _trackSectionLabel(context, filteredUnifiedItems[index]);
    }

    final scrollAwareContent = NotificationListener<ScrollNotification>(
      onNotification: (notification) => _handleLibraryScrollNotification(
        notification: notification,
        filterMode: filterMode,
        hasMoreLibrary: hasMoreLibrary,
        isPageLoading: isPageLoading,
      ),
      child: LibraryFastScroller(
        topPadding: MediaQuery.paddingOf(context).top + kToolbarHeight,
        bottomPadding: context.navBarBottomInset,
        labelAt: labelAt,
        child: content,
      ),
    );

    return scrollAwareContent;
  }

  Widget _buildEmptyState(
    BuildContext context,
    ColorScheme colorScheme,
    String filterMode,
  ) {
    String message;
    String subtitle;
    IconData icon;

    switch (filterMode) {
      case 'albums':
        message = context.l10n.queueEmptyAlbums;
        subtitle = context.l10n.queueEmptyAlbumsSubtitle;
        icon = Icons.album;
        break;
      case 'playlists':
        message = context.l10n.collectionNoPlaylistsYet;
        subtitle = context.l10n.queueEmptyPlaylistsSubtitle;
        icon = Icons.queue_music;
        break;
      default:
        message = context.l10n.queueEmptyHistory;
        subtitle = context.l10n.queueEmptyHistorySubtitle;
        icon = Icons.history;
    }

    return EmptyState(
      icon: icon,
      title: message,
      message: subtitle,
      // Every empty branch here means the library has no songs of this
      // kind yet, so the way forward is always search.
      action: FilledButton.icon(
        onPressed: () => ShellNavigationService.requestTab(ShellTab.home),
        icon: const Icon(Icons.search),
        label: Text(context.l10n.navHome),
      ),
    );
  }

  Widget _buildAlbumGridItemCore({
    required BuildContext context,
    required String albumName,
    required String artistName,
    required int trackCount,
    required ColorScheme colorScheme,
    required Widget? coverWidget,
    required IconData badgeIcon,
    required VoidCallback onTap,
    VoidCallback? onPlay,
    AlbumCompleteness? completeness,
  }) {
    final completenessLabel = completeness == null
        ? null
        : completeness.status == 'unknown'
        ? context.l10n.libraryAlbumCompletenessUnknown
        : completeness.expected == null
        ? context.l10n.libraryFilterIncompleteAlbums
        : context.l10n.libraryAlbumMissingTracks(
            completeness.present,
            completeness.expected!,
            completeness.expected! - completeness.present,
          );
    return Semantics(
      button: true,
      label: [
        context.l10n.a11yOpenAlbumByArtistTrackCount(
          albumName,
          artistName,
          trackCount,
        ),
        ?completenessLabel,
      ].join('. '),
      child: DesktopContextMenuRegion(
        onContextMenu: (position) => showContextMenu(
          context,
          position: position,
          groups: [
            [
              ContextMenuAction(
                icon: Icons.open_in_new,
                label: context.l10n.desktopMenuOpen,
                onSelected: onTap,
              ),
              if (onPlay != null)
                ContextMenuAction(
                  icon: Icons.play_arrow_rounded,
                  label: context.l10n.tooltipPlay,
                  onSelected: onPlay,
                ),
            ],
          ],
        ),
        child: GestureDetector(
          onTap: onTap,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Stack(
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: coverWidget ?? _albumPlaceholder(colorScheme),
                    ),
                    PositionedDirectional(
                      end: 8,
                      bottom: 8,
                      child: Tooltip(
                        message:
                            completenessLabel ??
                            context.l10n.tracksCount(trackCount),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: colorScheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              AppIcon(
                                badgeIcon,
                                size: 12,
                                color: colorScheme.onSurfaceVariant,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                completeness?.badge ?? '$trackCount',
                                style: TextStyle(
                                  color: colorScheme.onSurfaceVariant,
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    DesktopHoverPlayButton(
                      tooltip: context.l10n.tooltipPlay,
                      onPlay: onPlay,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Text(
                albumName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              ClickableArtistName(
                artistName: artistName,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLocalAlbumGridItem(
    BuildContext context,
    _GroupedLocalAlbum album,
    ColorScheme colorScheme,
  ) {
    return LocalLibraryArtworkBuilder(
      coverPath: album.coverPath,
      filePath: album.sampleFilePath,
      builder: (context, coverPath) => _buildAlbumGridItemCore(
        context: context,
        albumName: album.albumName,
        artistName: album.artistName,
        trackCount: album.displayTrackCount,
        completeness: album.completeness,
        colorScheme: colorScheme,
        coverWidget: coverPath != null
            ? Image.file(
                File(coverPath),
                fit: BoxFit.cover,
                width: double.infinity,
                height: double.infinity,
                cacheWidth: 300,
                cacheHeight: 300,
                errorBuilder: (context, error, stackTrace) =>
                    _albumPlaceholder(colorScheme),
              )
            : null,
        badgeIcon: Icons.folder,
        onTap: () => _navigateToLocalAlbum(album),
        onPlay: () => _playLocalAlbum(album),
      ),
    );
  }

  Widget _albumPlaceholder(ColorScheme colorScheme) {
    return Container(
      color: colorScheme.surfaceContainerHighest,
      child: Center(
        child: AppIcon(
          Icons.album,
          color: colorScheme.onSurfaceVariant,
          size: 48,
        ),
      ),
    );
  }

  List<UnifiedLibraryItem> _selectedItemsFromAll(
    List<UnifiedLibraryItem> allItems,
  ) {
    final itemsById = {for (final item in allItems) item.id: item};
    return _selectedIds
        .map((id) => itemsById[id])
        .whereType<UnifiedLibraryItem>()
        .toList(growable: false);
  }
}
