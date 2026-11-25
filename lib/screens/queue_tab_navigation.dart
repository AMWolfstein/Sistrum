part of 'queue_tab.dart';

extension _QueueTabNavigation on _QueueTabState {
  PlaybackRowKey _libraryPlaybackKey(UnifiedLibraryItem item) =>
      (id: item.localItem?.id ?? item.id, path: item.filePath);

  /// Plays [item] and queues the rest of the Songs list in its order, so
  /// playback continues to the next song. Honors the player's shuffle.
  Future<void> _playLibraryItem(
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> libraryItems,
  ) async {
    if (!await songFileCanPlay(item.filePath)) {
      if (mounted) await _openLibraryItemMenu(item);
      return;
    }
    final playableItems = libraryItems
        .where(
          (u) => u.filePath.trim().isNotEmpty && !isCueVirtualPath(u.filePath),
        )
        .toList();
    if (playableItems.isEmpty) return;

    var start = playableItems.indexWhere((u) => u.id == item.id);
    if (start < 0) start = 0;

    try {
      await ref
          .read(playbackProvider.notifier)
          .playMediaQueue(
            playableItems.map(_toPlayableMedia),
            startIndex: start,
          );
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// Plays the songs the Songs page lists, in its sort: from the first song
  /// for Play, from a random one with [shuffle] on. A list the page has not
  /// [complete]ly loaded is read whole first.
  Future<void> _playLibrarySongs(
    List<UnifiedLibraryItem> loaded, {
    required bool complete,
    required bool shuffle,
  }) async {
    var songs = loaded;
    if (!complete) {
      // A list that cannot be read leaves the loaded songs to play.
      songs = await ref
          .read(
            librarySongListProvider((
              sortMode: _sortMode,
              includeLocal: ref.read(settingsProvider).localLibraryEnabled,
            )).future,
          )
          .catchError((Object _) => loaded);
      if (!mounted) return;
    }
    final playable = songs
        .where(
          (u) => u.filePath.trim().isNotEmpty && !isCueVirtualPath(u.filePath),
        )
        .toList(growable: false);
    if (playable.isEmpty) return;
    final start = shuffle ? Random().nextInt(playable.length) : 0;
    try {
      if (shuffle) {
        await ref.read(musicPlayerControllerProvider).setShuffle(true);
      }
      await ref
          .read(playbackProvider.notifier)
          .playMediaQueue(playable.map(_toPlayableMedia), startIndex: start);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  PlayableMedia _toPlayableMedia(UnifiedLibraryItem item) {
    final local = item.localItem;
    if (local != null) return playableFromLocal(local);

    final cover = item.coverUrl ?? item.localCoverPath ?? '';
    String? art;
    if (cover.isNotEmpty) {
      art =
          (cover.startsWith('http') ||
              cover.startsWith('content://') ||
              cover.startsWith('file://'))
          ? cover
          : Uri.file(cover).toString();
    }
    return PlayableMedia(
      id: item.id,
      source: item.filePath,
      title: item.trackName,
      artist: item.artistName,
      album: item.albumName,
      artUri: art,
    );
  }

  /// The song menu of a library song that cannot play, which is what its
  /// row tap opens.
  Future<void> _openLibraryItemMenu(UnifiedLibraryItem item) => showSongMenu(
    context,
    ref,
    track: item.toTrack(),
    filePath: item.filePath,
    localItem: item.localItem,
  );

  void _pushLibraryRoute(Route<dynamic> route) {
    Navigator.of(context).push(route);
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

  void _navigateToLocalAlbum(_GroupedLocalAlbum album) {
    Future<List<LocalLibraryItem>> loadTracks() => _loadLocalAlbumTracks(album);

    _pushLibraryRoute(
      slidePageRoute(
        page: LocalAlbumScreen(
          albumName: album.albumName,
          artistName: album.artistName,
          coverPath: album.coverPath,
          tracks: album.tracks,
          loadTracks: album.tracks.isEmpty && album.displayTrackCount > 0
              ? loadTracks
              : null,
        ),
      ),
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

  void _openLovedFolder() {
    _pushLibraryRoute(
      MaterialPageRoute(builder: (_) => const CollectionDetailScreen.loved()),
    );
  }

  void _openFavoriteArtistsFolder() {
    _pushLibraryRoute(
      MaterialPageRoute(builder: (_) => const FavoriteArtistsScreen()),
    );
  }

  void _openPlaylistById(String playlistId) {
    _pushLibraryRoute(
      MaterialPageRoute(
        builder: (_) => CollectionDetailScreen.playlist(playlistId: playlistId),
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

    Widget image(String? cover) => cover == null
        ? placeholder
        : LocalCoverImage(
            url: cover,
            width: size,
            height: size,
            borderRadius: borderRadius,
            localCacheWidth: cacheExtent,
            fadeInDuration: Duration.zero,
            placeholder: (_) => placeholder,
          );

    if ((customCoverPath != null && customCoverPath.isNotEmpty) ||
        collectionCoverIsPortable(cover) ||
        playlist.trackCount == 0) {
      return image(cover);
    }
    // The first song's stored cover can name a file this device does not
    // have, such as a playlist another device made: the Library answers.
    return Consumer(
      builder: (context, ref, _) => image(
        ref
            .watch(
              playlistCoverProvider((
                id: playlist.id,
                cover: cover,
                updatedAt: playlist.updatedAt,
              )),
            )
            .maybeWhen(data: (cover) => cover, orElse: () => cover),
      ),
    );
  }

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

  Widget _buildDragFeedback(
    BuildContext context,
    UnifiedLibraryItem item,
    ColorScheme colorScheme,
  ) {
    final isDraggingMultiple =
        _isSelectionMode &&
        _selectedIds.contains(item.id) &&
        _selectedIds.length > 1;
    final count = isDraggingMultiple ? _selectedIds.length : 1;

    return Material(
      elevation: 6,
      borderRadius: BorderRadius.circular(12),
      color: colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            AppIcon(Icons.playlist_add, size: 18, color: colorScheme.primary),
            const SizedBox(width: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 180),
              child: Text(
                isDraggingMultiple ? '$count tracks' : item.trackName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
