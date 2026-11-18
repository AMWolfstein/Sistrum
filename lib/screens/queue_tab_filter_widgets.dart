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
    required String historyViewMode,
    required _FilterContentData filterData,
    required LibraryCollectionsState collectionState,
    required bool hasMoreLibrary,
    required bool isPageLoading,
  }) {
    final showFilteringIndicator = filterData.showFilteringIndicator;
    final filteredGroupedLocalAlbums = filterData.filteredGroupedLocalAlbums;
    final orderedAlbums = filterData.orderedAlbums;
    final unifiedItems = filterData.unifiedItems;
    final filteredUnifiedItems = filterData.filteredUnifiedItems;
    final totalTrackCount = filterData.totalTrackCount;
    final totalAlbumCount = filterData.totalAlbumCount;
    // Controls and grids share the centered column of the track list.
    final edge = 16 + wideListInset(context);

    final (
      local: localNavigationItems,
      localIndexById: localNavigationIndexByUnifiedId,
    ) = _libraryNavigation(
      filterMode,
      filteredUnifiedItems,
    );

    // The tab-root Library keeps Songs to songs: collections live on the
    // Playlists page. Section pages opened from Mornye keep them inline.
    final rootLibrary = widget.librarySection == null;
    final collectionEntries = filterMode == 'all' && !rootLibrary
        ? _getVisibleCollectionEntries(collectionState)
        : const <_CollectionEntry>[];
    final collectionCount = collectionEntries.length;
    final specialEntries = filterMode == 'playlists' && rootLibrary
        ? _getVisibleCollectionEntries(collectionState)
              .where((entry) => entry.type != _CollectionEntryType.playlist)
              .toList(growable: false)
        : const <_CollectionEntry>[];
    // Material lists group rows under pinned sections.
    final sectioned = rootLibrary && !context.isMornye;

    // Indexes into collectionState.playlists, name-filtered by the search
    // query, for the 'playlists' view.
    final playlistIndexes = <int>[];
    if (filterMode == 'playlists') {
      final query = _searchQuery.trim().toLowerCase();
      for (var i = 0; i < collectionState.playlists.length; i++) {
        if (query.isEmpty ||
            collectionState.playlists[i].name.toLowerCase().contains(query)) {
          playlistIndexes.add(i);
        }
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

    // Songs drag onto playlists; singles do not.
    final playlistDrag = filterMode == 'all';
    Widget trackTile(UnifiedLibraryItem item, {required bool grid}) {
      final tile = (grid ? _buildUnifiedGridItem : _buildUnifiedLibraryItem)(
        context,
        item,
        colorScheme,
        localNavigationItems: localNavigationItems,
        localNavigationIndex: localNavigationIndexByUnifiedId[item.id],
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

    // Songs, not Singles, offer Shuffle while the tools show.
    final showShuffle = filterMode == 'all' && !_isSelectionMode;
    void shuffleSongs() => _shuffleLibrarySongs(
      filteredUnifiedItems,
      complete: filteredUnifiedItems.length >= totalTrackCount,
    );

    Widget trackCountHeader() => SliverToBoxAdapter(
      child: _countHeaderRow(
        context,
        context.l10n.queueTrackCount(totalTrackCount),
        [
          // Material keeps Shuffle with the page's tools; Mornye shows its
          // capsule above this row.
          if (rootLibrary && !_isSelectionMode)
            ..._buildLibraryTools(
              context,
              viewMode: historyViewMode,
              onShuffle: showShuffle && !context.isMornye ? shuffleSongs : null,
            )
          else if (!_isSelectionMode) ...[
            if (showShuffle && !context.isMornye)
              _buildLibraryShuffleButton(context, onPressed: shuffleSongs),
            _buildFilterButton(context),
          ],
          // The root Library creates playlists from the Playlists page.
          if (!rootLibrary &&
              !_isSelectionMode &&
              filteredUnifiedItems.isNotEmpty)
            ExpressiveButton(
              text: true,
              onPressed: () => _showCreatePlaylistDialog(context),
              icon: const Icon(Icons.add, size: 20),
              style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
              child: Text(context.l10n.collectionCreatePlaylist),
            ),
        ],
      ),
    );

    final content = CustomScrollView(
      slivers: [
        if (rootLibrary && (filterMode == 'albums' || filterMode == 'singles'))
          SliverToBoxAdapter(child: _buildAlbumsModeToggle(context)),

        // Rewind lives with the listening history it replays.
        if (rootLibrary &&
            filterMode == 'all' &&
            _searchQuery.isEmpty &&
            !context.isMornye &&
            !_isSelectionMode &&
            ref.watch(
              settingsProvider.select((s) => s.listeningStatisticsEnabled),
            ))
          SliverToBoxAdapter(
            child: ListeningRewindBanner(
              margin: EdgeInsets.fromLTRB(edge, 8, edge, 4),
            ),
          ),

        if (totalTrackCount > 0 && showShuffle && context.isMornye)
          SliverToBoxAdapter(
            child: _buildLibraryShuffleButton(context, onPressed: shuffleSongs),
          ),

        if (totalTrackCount > 0 && filterMode == 'all') trackCountHeader(),

        if (filteredGroupedLocalAlbums.isNotEmpty && filterMode == 'albums')
          SliverToBoxAdapter(
            child: _countHeaderRow(
              context,
              context.l10n.queueAlbumCount(totalAlbumCount),
              [
                if (rootLibrary)
                  ..._buildLibraryTools(context)
                else
                  _buildFilterButton(context),
              ],
            ),
          ),

        if (filteredGroupedLocalAlbums.isEmpty &&
            filterMode == 'albums' &&
            (_activeFilterCount > 0 ||
                (ref.read(settingsProvider).localLibraryEnabled &&
                    ref.read(localLibraryProvider).totalCount > 0)))
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(edge, 8, edge, 8),
              child: Row(
                children: [const Spacer(), _buildFilterButton(context)],
              ),
            ),
          ),

        if ((filterMode == 'all' || filterMode == 'singles') &&
            totalTrackCount == 0 &&
            !showFilteringIndicator &&
            (_activeFilterCount > 0 || unifiedItems.isNotEmpty))
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(edge, 8, edge, 8),
              child: Row(
                children: [
                  const Spacer(),
                  if (!_isSelectionMode) _buildFilterButton(context),
                ],
              ),
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
              maxCrossAxisExtent: _QueueTabState._libraryGridDefaultExtent,
              extentListenable: _libraryGridExtent,
              extentScale: 1.45,
              minExtent: 150,
              maxExtent: 300,
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
                  ExpressiveButton(
                    text: true,
                    onPressed: () => _showCreatePlaylistDialog(context),
                    icon: const Icon(Icons.add, size: 20),
                    style: TextButton.styleFrom(
                      visualDensity: VisualDensity.compact,
                    ),
                    child: Text(context.l10n.collectionCreatePlaylist),
                  ),
              ],
            ),
          ),
          SliverPadding(
            padding: EdgeInsets.symmetric(horizontal: edge),
            sliver: AnimatedLibrarySliverGrid(
              maxCrossAxisExtent: _QueueTabState._libraryGridDefaultExtent,
              extentListenable: _libraryGridExtent,
              extentScale: 1.45,
              minExtent: 150,
              maxExtent: 300,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              childAspectRatio: 0.72,
              delegate: SliverChildBuilderDelegate((context, index) {
                // Wishlist, Loved and Favorite artists lead the grid.
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

        if (filterMode == 'singles') ...[trackCountHeader()],

        // Songs list collections first; singles hide an empty track list.
        if (filterMode == 'all' ||
            (filterMode == 'singles' && filteredUnifiedItems.isNotEmpty))
          if (historyViewMode == 'grid')
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: edge),
              sliver: AnimatedLibrarySliverGrid(
                maxCrossAxisExtent: _QueueTabState._libraryGridDefaultExtent,
                extentListenable: _libraryGridExtent,
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                childAspectRatio: 0.66,
                delegate: SliverChildBuilderDelegate((context, index) {
                  if (index < collectionCount) {
                    return _buildAllTabGridCollectionItem(
                      context: context,
                      colorScheme: colorScheme,
                      entry: collectionEntries[index],
                      collectionState: collectionState,
                      filteredUnifiedItems: filteredUnifiedItems,
                    );
                  }
                  final trackIndex = index - collectionCount;
                  if (trackIndex < filteredUnifiedItems.length) {
                    final item = filteredUnifiedItems[trackIndex];
                    return KeyedSubtree(
                      key: ValueKey(item.id),
                      child: trackTile(item, grid: true),
                    );
                  }
                  return const SizedBox.shrink();
                }, childCount: collectionCount + filteredUnifiedItems.length),
              ),
            )
          else if (isDesktopPlatform &&
              (filteredUnifiedItems.isNotEmpty || collectionCount > 0))
            // Desktop lists songs in the sortable table, with the collections
            // leading it.
            _buildLibrarySongTable(
              context,
              filterMode: filterMode,
              items: filteredUnifiedItems,
              leadCount: collectionCount,
              leadBuilder: (index) => _buildAllTabListCollectionItem(
                context: context,
                colorScheme: colorScheme,
                entry: collectionEntries[index],
                collectionState: collectionState,
                filteredUnifiedItems: filteredUnifiedItems,
              ),
              openAction: (item) => _libraryItemOpenAction(
                item,
                localNavigationItems: localNavigationItems,
                localNavigationIndex: localNavigationIndexByUnifiedId[item.id],
              ),
            )
          else if (sectioned)
            _buildSectionedTrackSlivers(
              context: context,
              items: filteredUnifiedItems,
              rowBuilder: (index) =>
                  trackTile(filteredUnifiedItems[index], grid: false),
            )
          else
            SliverPadding(
              padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate((context, index) {
                  if (index < collectionCount) {
                    return _buildAllTabListCollectionItem(
                      context: context,
                      colorScheme: colorScheme,
                      entry: collectionEntries[index],
                      collectionState: collectionState,
                      filteredUnifiedItems: filteredUnifiedItems,
                    );
                  }
                  final trackIndex = index - collectionCount;
                  if (trackIndex < filteredUnifiedItems.length) {
                    final item = filteredUnifiedItems[trackIndex];
                    return TrackCardSegment.at(
                      key: ValueKey(item.id),
                      index: index,
                      count: filteredUnifiedItems.length,
                      child: trackTile(item, grid: false),
                    );
                  }
                  return const SizedBox.shrink();
                }, childCount: collectionCount + filteredUnifiedItems.length),
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
      if (filterMode != 'all' && filterMode != 'singles') return null;
      final rows = collectionCount + filteredUnifiedItems.length;
      if (rows == 0) return null;
      final itemIndex = (fraction * (rows - 1)).round() - collectionCount;
      if (itemIndex < 0 || itemIndex >= filteredUnifiedItems.length) {
        return null;
      }
      return _trackSectionLabel(context, filteredUnifiedItems[itemIndex]);
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

    if (historyViewMode != 'grid') return scrollAwareContent;
    return TwoFingerPinchListener(
      onStart: _handleLibraryGridPinchStart,
      onUpdate: _handleLibraryGridPinchUpdate,
      onEnd: _handleLibraryGridPinchEnd,
      child: scrollAwareContent,
    );
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
      case 'singles':
        message = context.l10n.queueEmptySingles;
        subtitle = context.l10n.queueEmptySinglesSubtitle;
        icon = Icons.music_note;
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

    if (context.isMornye) {
      return EmptyState(
        icon: icon,
        title: message,
        message: subtitle,
        // Every empty branch here means the library has no songs of this
        // kind yet, so the way forward is always search.
        action: ExpressiveButton(
          onPressed: () => ShellNavigationService.requestTab(ShellTab.home),
          icon: const Icon(Icons.search),
          child: Text(context.l10n.navHome),
        ),
      );
    }

    // Material names the next step in the copy. Home already sits in the
    // navigation bar, so only creating the first playlist, which the empty
    // Playlists page does not otherwise offer, gets a button.
    return EmptyState(
      icon: icon,
      title: message,
      message: subtitle,
      action: filterMode == 'playlists'
          ? ExpressiveButton(
              onPressed: () => _showCreatePlaylistDialog(context),
              icon: const Icon(Icons.add),
              child: Text(context.l10n.collectionCreatePlaylist),
            )
          : null,
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

  /// The swipe-navigation list of local songs over [items], kept while the
  /// songs stay the same.
  _LibraryNavigation _libraryNavigation(
    String filterMode,
    List<UnifiedLibraryItem> items,
  ) {
    final cached = _navigationByMode[filterMode];
    if (cached != null && identical(cached.$1, items)) {
      return cached.$2;
    }
    final local = <LocalLibraryItem>[];
    final localIndexById = <String, int>{};
    for (final item in items) {
      final localItem = item.localItem;
      if (localItem != null) {
        localIndexById[item.id] = local.length;
        local.add(localItem);
      }
    }
    final navigation = (local: local, localIndexById: localIndexById);
    _navigationByMode[filterMode] = (items, navigation);
    return navigation;
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

/// Swipe-navigation lists over a Library page's songs, with each song's
/// position in them.
typedef _LibraryNavigation = ({
  List<LocalLibraryItem> local,
  Map<String, int> localIndexById,
});
