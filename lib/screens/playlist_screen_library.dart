part of 'playlist_screen.dart';

/// What the library page shows, read once per build.
class _LibraryView {
  const _LibraryView({
    required this.loved,
    required this.playlistId,
    required this.title,
    required this.entries,
    required this.loading,
    required this.cover,
    required this.customCover,
  });

  final bool loved;
  final String? playlistId;
  final String title;

  /// The stored order, or the order being edited.
  final List<CollectionTrackEntry> entries;
  final bool loading;

  /// The cover shown without a custom one: the first song's, or the library's.
  final String? cover;
  final String? customCover;
}

/// A library playlist or the Loved songs: the playlist page filled from the
/// library's collections. Play and Shuffle sit beside Love and Add to playlist;
/// rows keep the offline actions (metadata, playback, selection, order, remove,
/// export and the playlist's rename and cover). Online actions are not offered.
extension _PlaylistLibrarySections on _PlaylistScreenState {
  Widget _buildLibrary(BuildContext context) {
    final loved = widget.libraryLoved;
    final playlistId = widget.libraryPlaylistId;
    // Every edit replaces the collection's list in the store; reload the rows
    // from the library then, whichever screen made the edit.
    ref.listen<Object?>(
      libraryCollectionsProvider.select(
        (state) => loved ? state.loved : state.playlistById(playlistId ?? ''),
      ),
      (_, _) => _reloadLibraryEntries(),
    );
    final entriesAsync = loved
        ? ref.watch(libraryLovedEntriesProvider)
        : ref.watch(libraryPlaylistEntriesProvider(playlistId!));
    final playlist = loved
        ? null
        : ref.watch(
            libraryCollectionsProvider.select(
              (state) => state.playlistById(playlistId!),
            ),
          );
    final entries = orderDraft ?? entriesAsync.value ?? const [];
    final tracks = [for (final entry in entries) entry.track];
    final rawCover = loved ? null : firstRawCollectionCover(tracks);
    final localCover = rawCover == null && !loved && entries.isNotEmpty
        ? ref
              .watch(
                localLibraryFirstCoverProvider(
                  collectionCoverBatchRequest(tracks),
                ),
              )
              .maybeWhen(data: (cover) => cover, orElse: () => null)
        : null;
    final customCover = playlist?.coverImagePath;
    final view = _LibraryView(
      loved: loved,
      playlistId: playlistId,
      title: loved
          ? context.l10n.collectionLoved
          : playlist?.name ?? context.l10n.collectionPlaylist,
      entries: entries,
      loading: entriesAsync.isLoading && !entriesAsync.hasValue,
      cover: rawCover ?? localCover,
      customCover: customCover != null && customCover.isNotEmpty
          ? customCover
          : null,
    );
    return MornyeArtistSurface(
      imageSource: view.customCover ?? view.cover,
      neutralActions: true,
      child: Builder(builder: (context) => _libraryPage(context, view)),
    );
  }

  void _reloadLibraryEntries() {
    final playlistId = widget.libraryPlaylistId;
    if (playlistId == null) {
      ref.invalidate(libraryLovedEntriesProvider);
    } else {
      ref.invalidate(libraryPlaylistEntriesProvider(playlistId));
    }
  }

  Widget _libraryPage(BuildContext context, _LibraryView view) {
    final ids = [for (final entry in view.entries) entry.key];
    pruneSelection(ids.toSet());
    final bottomPadding = isSelectionMode
        ? MediaQuery.paddingOf(context).bottom
        : 0.0;
    // Back during Edit order asks to discard the order, as the Wishlist does.
    return PopScope(
      canPop: !editingOrder,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && editingOrder) cancelOrder();
      },
      child: CollectionScaffold(
        scrollController: scrollController,
        isSelectionMode: showsSelectionBar,
        onExitSelectionMode: exitSelectionMode,
        selectionBar: buildSelectionBar(
          ids: ids,
          bottomPadding: bottomPadding,
          children: [_selectionActions(view)],
        ),
        appBar: _libraryAppBar(context, view),
        slivers: _librarySlivers(context, view),
      ),
    );
  }

  Widget _selectionActions(_LibraryView view) {
    final tracks = [
      for (final entry in view.entries)
        if (selectedIds.contains(entry.key)) entry.track,
    ];
    return CollectionTrackSelectionActions(
      media: selectedMedia(tracks),
      selectedCount: tracks.length,
      showPlayer: ref.watch(playerUiEnabledProvider),
      inProgress: trackActionInProgress,
      onPlay: playSelectedMedia,
      onAddToPlaylist: () => showAddTracksToPlaylistSheet(context, ref, tracks),
      removeLabel: view.loved
          ? context.l10n.trackOptionRemoveFromLoved
          : context.l10n.collectionRemoveFromPlaylist,
      onRemove: () => _removeLibrarySelected(view),
      onDelete: deleteSelectedMedia,
    );
  }

  Widget _libraryAppBar(BuildContext context, _LibraryView view) {
    final colorScheme = Theme.of(context).colorScheme;
    final l10n = context.l10n;
    final tracks = [for (final entry in view.entries) entry.track];
    final hasTracks = view.entries.isNotEmpty;
    final cacheWidth = coverCacheWidthForViewport(context);
    final cover = view.customCover ?? view.cover;
    Widget placeholder(double size) => Container(
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        view.loved ? Icons.favorite : Icons.queue_music,
        size: size,
        color: colorScheme.onSurfaceVariant,
      ),
    );
    final Widget background = cover != null
        ? LocalOrNetworkCoverImage(
            url: cover,
            fit: BoxFit.cover,
            localCacheWidth: cacheWidth,
            networkCacheWidth: cacheWidth,
            fadeInDuration: Duration.zero,
            urlTransform: (u) => highResCoverUrl(u) ?? u,
            placeholder: (_) => Container(color: colorScheme.surface),
          )
        : placeholder(80);

    return AlbumDetailHeader(
      title: view.title,
      appBarTitle: editingOrder
          ? l10n.playlistEditOrder
          : isSelectionMode
          ? l10n.selectionSelected(selectedIds.length)
          : view.title,
      immersive: true,
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: background,
      coverBuilder: (context, coverSize) => cover != null
          ? LocalOrNetworkCoverImage(
              url: cover,
              fit: BoxFit.cover,
              width: coverSize,
              height: coverSize,
              localCacheWidth: cacheWidth,
              networkCacheWidth: cacheWidth,
              urlTransform: (u) => highResCoverUrl(u) ?? u,
              placeholder: (_) => placeholder(48),
            )
          : placeholder(48),
      meta: hasTracks
          ? HeaderMetaRow(
              items: [
                HeaderMetaItem(
                  l10n.tracksCount(view.entries.length),
                  icon: view.loved ? Icons.favorite : Icons.queue_music,
                ),
              ],
            )
          : null,
      actions: hasTracks && !editingOrder
          ? CollectionHeaderActions(
              tracks: tracks,
              onLoveAll: () => loveAllTracks(context, ref, tracks),
              addToPlaylistIcon: Icons.playlist_add,
              onAddToPlaylist: () =>
                  showAddTracksToPlaylistSheet(context, ref, tracks),
              playActions: AlbumPlayActions(
                compact: true,
                playLabel: l10n.tooltipPlay,
                shuffleTooltip: l10n.actionShuffle,
                onPlay: () =>
                    playCollectionTracks(context, ref, tracks, shuffle: false),
                onShuffle: () =>
                    playCollectionTracks(context, ref, tracks, shuffle: true),
              ),
            )
          : null,
      appBarActions: [
        if (editingOrder)
          TextButton(
            key: const ValueKey('collection-order-save'),
            onPressed: !orderChanged || savingOrder || draggingOrder
                ? null
                : saveOrder,
            child: Text(l10n.dialogSave),
          ),
        if (!editingOrder) ...[
          if (!isSelectionMode && hasTracks)
            HeaderCircleButton(
              icon: Icons.ios_share,
              iconSize: 20,
              tooltip: l10n.collectionExportM3u,
              onPressed: () =>
                  exportCollectionAsM3u8(context, ref, view.title, tracks),
            ),
          if (!isSelectionMode && view.entries.length > 1) ...[
            const SizedBox(width: 8),
            HeaderCircleButton(
              key: const ValueKey('collection-edit-order'),
              icon: Icons.swap_vert,
              iconSize: 20,
              tooltip: l10n.playlistEditOrder,
              onPressed: () => _startLibraryEditOrder(view.entries),
            ),
          ],
          if (!view.loved && !isSelectionMode && view.playlistId != null) ...[
            const SizedBox(width: 8),
            HeaderCircleButton(
              icon: Icons.edit_outlined,
              iconSize: 20,
              tooltip: l10n.collectionRenamePlaylist,
              onPressed: () =>
                  _renameLibraryPlaylist(context, view.playlistId!, view.title),
            ),
            const SizedBox(width: 8),
            HeaderCircleButton(
              icon: Icons.camera_alt_outlined,
              iconSize: 20,
              tooltip: l10n.collectionPlaylistChangeCover,
              onPressed: () => _showCoverOptionsSheet(
                context,
                view.playlistId!,
                view.customCover != null,
              ),
            ),
          ],
        ],
      ],
      leading: Center(
        child: HeaderCircleButton(
          icon: isSelectionMode || editingOrder
              ? Icons.close
              : Icons.arrow_back,
          tooltip: isSelectionMode || editingOrder
              ? MaterialLocalizations.of(context).closeButtonTooltip
              : MaterialLocalizations.of(context).backButtonTooltip,
          onPressed: editingOrder
              ? cancelOrder
              : isSelectionMode
              ? exitSelectionMode
              : () => Navigator.pop(context),
        ),
      ),
    );
  }

  List<Widget> _librarySlivers(BuildContext context, _LibraryView view) {
    final l10n = context.l10n;
    final tracks = [for (final entry in view.entries) entry.track];
    return [
      if (editingOrder)
        SliverToBoxAdapter(
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              16 + wideListInset(context),
              16,
              16 + wideListInset(context),
              12,
            ),
            child: Text(l10n.playlistEditOrderDescription),
          ),
        ),
      if (view.loading)
        const SliverToBoxAdapter(child: TrackListSkeleton(itemCount: 8))
      else if (view.entries.isEmpty)
        SliverFillRemaining(
          hasScrollBody: false,
          child: CollectionEmptyState(
            title: view.loved
                ? l10n.collectionLovedEmptyTitle
                : l10n.collectionPlaylistEmptyTitle,
            subtitle: view.loved
                ? l10n.collectionLovedEmptySubtitle
                : l10n.collectionPlaylistEmptySubtitle,
          ),
        )
      else if (editingOrder)
        CollectionReorderSliver(
          entries: view.entries,
          enabled: !savingOrder,
          onDraggingChanged: setDraggingOrder,
          onReorder: moveDraftEntry,
        )
      else
        SliverPadding(
          padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) =>
                  _libraryRow(context, view.entries, tracks, index),
              childCount: view.entries.length,
            ),
          ),
        ),
    ];
  }

  Widget _libraryRow(
    BuildContext context,
    List<CollectionTrackEntry> entries,
    List<Track> tracks,
    int index,
  ) {
    final entry = entries[index];
    return KeyedSubtree(
      key: ValueKey(entry.key),
      child: StaggeredListItem(
        index: index,
        child: AlbumTrackTile(
          cover: CollectionTrackCover(
            track: entry.track,
            size: context.tokens.coverList,
          ),
          trackName: entry.track.name,
          subtitle: Text(
            entry.track.artistName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          isSelectionMode: isSelectionMode,
          isSelected: selectedIds.contains(entry.key),
          onToggleSelection: () => toggleSelection(entry.key),
          onOpen: () => openCollectionTrackMetadata(context, ref, entry.track),
          onEnterSelectionMode: () => enterSelectionMode(entry.key),
          onPlay: () => _playLibraryFrom(context, tracks, index),
        ),
      ),
    );
  }

  Future<void> _playLibraryFrom(
    BuildContext context,
    List<Track> tracks,
    int index,
  ) async {
    try {
      await ref
          .read(playbackProvider.notifier)
          .playTrackList(tracks, startIndex: index);
    } catch (error) {
      if (!context.mounted) return;
      showCannotOpenFileSnackBar(context, error);
    }
  }

  void _startLibraryEditOrder(List<CollectionTrackEntry> entries) {
    exitSelectionMode();
    editOrder(entries);
  }

  Future<void> _removeLibrarySelected(_LibraryView view) async {
    final keys = selectedIds.toSet();
    if (keys.isEmpty) return;
    final count = keys.length;
    final notifier = ref.read(libraryCollectionsProvider.notifier);
    if (view.loved) {
      await notifier.removeLovedTracks(keys);
    } else {
      await notifier.removeTracksFromPlaylist(view.playlistId!, keys);
    }

    exitSelectionMode();

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.selectionSelected(count))),
    );
  }

  Future<void> _pickCoverImage(String playlistId) async {
    final picked = await FilePicker.pickFile(type: FileType.image);
    if (picked == null) return;

    Directory? materializedDir;
    try {
      var sourcePath = picked.path;
      if (sourcePath == null || sourcePath.isEmpty) {
        materializedDir = await Directory.systemTemp.createTemp(
          'sistrum_playlist_cover_',
        );
        final pickedExtension = p.extension(picked.name);
        final materializedFile = File(
          p.join(
            materializedDir.path,
            'selected_cover${pickedExtension.isEmpty ? '.img' : pickedExtension}',
          ),
        );
        await materializedFile.writeAsBytes(
          await picked.readAsBytes(),
          flush: true,
        );
        sourcePath = materializedFile.path;
      }

      await ref
          .read(libraryCollectionsProvider.notifier)
          .setPlaylistCover(playlistId, sourcePath);
    } finally {
      try {
        if (materializedDir != null && await materializedDir.exists()) {
          await materializedDir.delete(recursive: true);
        }
      } catch (_) {
        // The cover has already been normalized into app storage.
      }
    }
  }

  Future<void> _removeCoverImage(String playlistId) async {
    await ref
        .read(libraryCollectionsProvider.notifier)
        .removePlaylistCover(playlistId);
  }

  void _showCoverOptionsSheet(
    BuildContext context,
    String playlistId,
    bool hasCustomCover,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    showAppModalBottomSheet<void>(
      context: context,
      useRootNavigator: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const AppSheetHandle(),
            const SizedBox(height: 16),
            AppSheetOption(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 24,
                vertical: 4,
              ),
              leading: Icon(
                mornyeIconFor(Icons.image_outlined),
                color: colorScheme.primary,
              ),
              title: Text(context.l10n.collectionPlaylistChangeCover),
              onTap: () {
                Navigator.pop(sheetContext);
                _pickCoverImage(playlistId);
              },
            ),
            if (hasCustomCover)
              AppSheetOption(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 4,
                ),
                leading: Icon(
                  mornyeIconFor(Icons.delete_outline),
                  color: colorScheme.primary,
                ),
                title: Text(context.l10n.collectionPlaylistRemoveCover),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _removeCoverImage(playlistId);
                },
              ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  Future<void> _renameLibraryPlaylist(
    BuildContext context,
    String playlistId,
    String currentName,
  ) async {
    final nextName = await promptPlaylistName(
      context,
      title: context.l10n.collectionRenamePlaylist,
      confirmLabel: context.l10n.dialogSave,
      initialName: currentName,
    );

    if (nextName == null || nextName.trim().isEmpty || !context.mounted) {
      return;
    }

    await ref
        .read(libraryCollectionsProvider.notifier)
        .renamePlaylist(playlistId, nextName.trim());

    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.collectionPlaylistRenamed)),
    );
  }
}
