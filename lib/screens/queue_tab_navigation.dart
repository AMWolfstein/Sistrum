part of 'queue_tab.dart';

extension _QueueTabNavigation on _QueueTabState {
  PlaybackRowKey _libraryPlaybackKey(UnifiedLibraryItem item) => (
    id: item.historyItem?.id ?? item.localItem?.id ?? item.id,
    path: item.filePath,
  );

  Future<void> _openFile(
    String filePath, {
    String title = '',
    String artist = '',
    String album = '',
    String coverUrl = '',
  }) async {
    final cleanPath = _cleanFilePath(filePath);
    try {
      final fallbackTitle = cleanPath.split('/').last.split('\\').last;
      await ref
          .read(playbackProvider.notifier)
          .playLocalPath(
            path: cleanPath,
            title: title.isNotEmpty ? title : fallbackTitle,
            artist: artist,
            album: album,
            coverUrl: coverUrl,
          );
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// Plays [libraryItems] from [start], or from the top, or shuffled. A
  /// whole-list play sets shuffle, as Folders do; a row keeps the current mode.
  Future<void> _playLibraryItems(
    List<UnifiedLibraryItem> libraryItems, {
    UnifiedLibraryItem? start,
    bool shuffle = false,
  }) async {
    final playableItems = libraryItems
        .where(
          (u) => u.filePath.trim().isNotEmpty && !isCueVirtualPath(u.filePath),
        )
        .toList(growable: false);
    if (playableItems.isEmpty) return;

    final index = start != null
        ? max(0, playableItems.indexWhere((u) => u.id == start.id))
        : shuffle
        ? Random().nextInt(playableItems.length)
        : 0;
    try {
      if (start == null) {
        await ref.read(musicPlayerControllerProvider).setShuffle(shuffle);
      }
      await ref
          .read(playbackProvider.notifier)
          .playMediaQueue(
            playableItems.map(playableFromUnified),
            startIndex: index,
            externalPath: start?.filePath ?? playableItems[index].filePath,
          );
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// Plays the Songs list as filtered and sorted. Pages not loaded yet are
  /// read in one query, so a long list plays whole.
  Future<void> _playSongs(
    _QueueLibraryPageRequest request,
    int total,
    List<UnifiedLibraryItem> loaded, {
    bool shuffle = false,
  }) async {
    var songs = loaded;
    if (total > loaded.length) {
      try {
        final all = await ref.read(
          _queueLibraryPageProvider(request.unpaged(total)).future,
        );
        if (all.items.isNotEmpty) songs = all.items;
      } catch (e) {
        if (mounted) showCannotOpenFileSnackBar(context, e);
        return;
      }
    }
    await _playLibraryItems(songs, shuffle: shuffle);
  }

  Future<void> _navigateToMetadataScreen(DownloadItem item) async {
    final historyItem = ref
        .read(downloadHistoryProvider)
        .items
        .firstWhere(
          (h) => h.filePath == item.filePath,
          orElse: () => DownloadHistoryItem(
            id: item.id,
            trackName: item.track.name,
            artistName: item.track.artistName,
            albumName: item.track.albumName,
            coverUrl: item.track.coverUrl,
            filePath: item.filePath ?? '',
            downloadedAt: DateTime.now(),
            service: item.service,
          ),
        );
    final navigator = Navigator.of(context);
    if (!mounted) return;
    final backdropReady = Future<void>.value();
    _searchFocusNode.unfocus();
    final beforeModTime = await _readFileModTimeMillis(historyItem.filePath);
    await backdropReady;
    if (!mounted) return;
    final route = slidePageRoute<bool>(
      page: TrackMetadataScreen(item: historyItem),
    );
    final result = await navigator.push(route);
    if (route is TransitionRoute<bool>) await route.completed;
    if (!mounted) return;
    _searchFocusNode.unfocus();
    if (result == true) {
      await _scheduleDownloadedEmbeddedCoverRefreshForPath(
        historyItem.filePath,
        beforeModTime: beforeModTime,
        force: true,
      );
      return;
    }
    await _scheduleDownloadedEmbeddedCoverRefreshForPath(
      historyItem.filePath,
      beforeModTime: beforeModTime,
    );
  }

  Future<void> _navigateToHistoryMetadataScreen(
    DownloadHistoryItem item, {
    List<DownloadHistoryItem>? navigationItems,
    int? navigationIndex,
  }) async {
    final navigator = Navigator.of(context);
    if (!mounted) return;
    final backdropReady = Future<void>.value();
    _searchFocusNode.unfocus();
    final beforeModTime = await _readFileModTimeMillis(item.filePath);
    await backdropReady;
    if (!mounted) return;
    final route = slidePageRoute<bool>(
      page: TrackMetadataScreen(
        item: item,
        historyNavigationItems: navigationItems,
        navigationIndex: navigationIndex,
        coverHeroTag: 'cover_lib_dl_${item.id}',
      ),
    );
    final result = await navigator.push(route);
    if (route is TransitionRoute<bool>) await route.completed;
    if (!mounted) return;
    _searchFocusNode.unfocus();
    if (result == true) {
      await _scheduleDownloadedEmbeddedCoverRefreshForPath(
        item.filePath,
        beforeModTime: beforeModTime,
        force: true,
      );
      return;
    }
    await _scheduleDownloadedEmbeddedCoverRefreshForPath(
      item.filePath,
      beforeModTime: beforeModTime,
    );
  }

  Future<void> _navigateToLocalMetadataScreen(
    LocalLibraryItem item, {
    List<LocalLibraryItem>? navigationItems,
    int? navigationIndex,
  }) async {
    _searchFocusNode.unfocus();
    if (!mounted) return;
    await Navigator.push(
      context,
      slidePageRoute<void>(
        page: TrackMetadataScreen(
          localItem: item,
          localNavigationItems: navigationItems,
          navigationIndex: navigationIndex,
          coverHeroTag: 'cover_lib_local_${item.id}',
        ),
      ),
    );
    if (mounted) _searchFocusNode.unfocus();
  }

  void _navigateWithUnfocus(Route<dynamic> route) {
    _searchFocusNode.unfocus();
    Navigator.of(context).push(route).then((_) => _searchFocusNode.unfocus());
  }

  void _navigateToDownloadedAlbum(_GroupedAlbum album) {
    _navigateWithUnfocus(
      slidePageRoute(
        page: AlbumScreen.library(
          albumKey: LibraryDatabase.albumKeyFor(
            album.albumName,
            null,
            album.artistName,
          ),
        ),
      ),
    );
  }

  /// Plays a scanned album from its first track, loading its tracks when the
  /// Library page did not.
  Future<void> _playLocalAlbum(_GroupedLocalAlbum album) async {
    try {
      await playLocalAlbum(
        ref,
        album.tracks.isNotEmpty
            ? album.tracks
            : await _loadLocalAlbumTracks(album),
      );
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  Future<void> _playDownloadedAlbum(_GroupedAlbum album) async {
    try {
      await playDownloadedAlbum(
        ref,
        albumName: album.albumName,
        artistName: album.artistName,
      );
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  void _navigateToLocalAlbum(_GroupedLocalAlbum album) {
    _navigateWithUnfocus(
      slidePageRoute(page: AlbumScreen.library(albumKey: album.albumKey)),
    );
  }

  Future<List<LocalLibraryItem>> _loadLocalAlbumTracks(
    _GroupedLocalAlbum album,
  ) async {
    var rows = album.albumKey.isNotEmpty
        ? await LibraryDatabase.instance.getQueueLocalAlbumTracksByKey(
            album.albumKey,
          )
        : await LibraryDatabase.instance.getQueueLocalAlbumTracks(
            album.albumName,
            album.artistName,
          );
    if (rows.isEmpty && album.albumKey.isNotEmpty) {
      rows = await LibraryDatabase.instance.getQueueLocalAlbumTracks(
        album.albumName,
        album.artistName,
      );
    }
    return rows.map(LocalLibraryItem.fromJson).toList(growable: false);
  }

  void _openWishlistFolder() {
    _navigateWithUnfocus(
      MaterialPageRoute(builder: (_) => const LibraryTracksFolderScreen()),
    );
  }

  void _openLovedFolder() {
    _navigateWithUnfocus(
      MaterialPageRoute(builder: (_) => const PlaylistScreen.libraryLoved()),
    );
  }

  void _openFavoriteArtistsFolder() {
    _navigateWithUnfocus(
      MaterialPageRoute(builder: (_) => const FavoriteArtistsScreen()),
    );
  }

  void _openPlaylistById(String playlistId) {
    _navigateWithUnfocus(
      MaterialPageRoute(
        builder: (_) =>
            PlaylistScreen.libraryPlaylist(libraryPlaylistId: playlistId),
      ),
    );
  }

  Future<void> _showCreatePlaylistDialog(BuildContext context) =>
      showCreatePlaylistSheet(context, ref);

  /// Pass a finite [size] (e.g. 56) for list view, or `null` for grid view
  /// where the widget should expand to fill its parent.
  Widget _buildPlaylistCover(
    BuildContext context,
    UserPlaylistCollection playlist,
    ColorScheme colorScheme, [
    double? size,
  ]) {
    final borderRadius = BorderRadius.circular(8);
    final dpr = MediaQuery.devicePixelRatioOf(context);
    final cacheExtent = size != null
        ? (size * dpr).round().clamp(64, 1024)
        : 420;
    final placeholder = _playlistIconFallback(colorScheme, size);

    final customCoverPath = playlist.coverImagePath;
    final cover = customCoverPath != null && customCoverPath.isNotEmpty
        ? customCoverPath
        : playlist.tracks
                  .where(
                    (e) =>
                        e.track.coverUrl != null &&
                        e.track.coverUrl!.isNotEmpty,
                  )
                  .map((e) => e.track.coverUrl!)
                  .firstOrNull ??
              playlist.previewCover;

    if (cover != null) {
      return LocalOrNetworkCoverImage(
        url: cover,
        width: size,
        height: size,
        borderRadius: borderRadius,
        localCacheWidth: cacheExtent,
        networkCacheWidth: cacheExtent,
        fadeInDuration: Duration.zero,
        placeholder: (_) => placeholder,
      );
    }

    return placeholder;
  }

  /// Icon fallback for playlists with no cover.
  /// When [size] is null the container expands to fill its parent (grid view)
  /// and uses a fixed icon size.
  Widget _playlistIconFallback(ColorScheme colorScheme, [double? size]) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: colorScheme.secondaryContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: AppIcon(
        Icons.queue_music,
        color: colorScheme.onSecondaryContainer,
        size: size != null ? size * 0.5 : 40,
      ),
    );
  }

  /// Handle a track being dropped onto a playlist.
  /// When selection mode is active and the dragged item is among the selected,
  /// all selected tracks are added to the playlist.
  Future<void> _onTrackDroppedOnPlaylist(
    BuildContext context,
    UnifiedLibraryItem item,
    String playlistId,
    String playlistName, {
    List<UnifiedLibraryItem> allItems = const [],
  }) async {
    final notifier = ref.read(libraryCollectionsProvider.notifier);

    if (_isSelectionMode &&
        _selectedIds.isNotEmpty &&
        _selectedIds.contains(item.id)) {
      final selectedItems = allItems
          .where((e) => _selectedIds.contains(e.id))
          .toList();
      if (selectedItems.isEmpty) {
        selectedItems.add(item);
      }

      final batchResult = await notifier.addTracksToPlaylist(
        playlistId,
        selectedItems.map((selected) => selected.toTrack()),
      );
      final addedCount = batchResult.addedCount;
      final alreadyCount = batchResult.alreadyInPlaylistCount;

      if (!context.mounted) return;
      final message = addedCount > 0
          ? alreadyCount > 0
                ? context.l10n.collectionAddedTracksToPlaylistWithExisting(
                    addedCount,
                    playlistName,
                    alreadyCount,
                  )
                : context.l10n.collectionAddedTracksToPlaylist(
                    addedCount,
                    playlistName,
                  )
          : context.l10n.collectionAlreadyInPlaylist(playlistName);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
      _exitSelectionMode();
      return;
    }

    final track = item.toTrack();
    final added = await notifier.addTrackToPlaylist(playlistId, track);

    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          added
              ? context.l10n.collectionAddedToPlaylist(playlistName)
              : context.l10n.collectionAlreadyInPlaylist(playlistName),
        ),
      ),
    );
  }
}
