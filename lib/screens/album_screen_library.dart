part of 'album_screen.dart';

/// The library album: the album page filled from the library's own files.
/// Play and Shuffle sit beside Love and Add to playlist; rows keep the offline
/// actions (metadata, playback, selection). Online actions are not offered.
extension _AlbumLibrarySections on _AlbumScreenState {
  Widget _buildLibrary(BuildContext context) {
    final albumKey = widget.albumKey;
    return ref
        .watch(libraryAlbumItemsProvider(albumKey))
        .when(
          // A reload shows the loader, not the rows of the last load.
          skipLoadingOnRefresh: false,
          loading: () =>
              const Scaffold(body: Center(child: AppLoadingIndicator())),
          error: (error, _) => Scaffold(
            body: Center(
              child: ErrorCard(
                error: context.friendlyError(error),
                colorScheme: Theme.of(context).colorScheme,
                onRetry: () =>
                    ref.invalidate(libraryAlbumItemsProvider(albumKey)),
              ),
            ),
          ),
          data: (items) {
            if (items.isEmpty) {
              return Scaffold(
                body: Center(child: Text(context.l10n.noTracksFoundForAlbum)),
              );
            }
            // A downloaded file decides the cover by its embedded art; a scanned
            // one by its folder cover, which the artwork builder checks.
            if (items.first.localItem == null) {
              return _libraryPage(
                context,
                items,
                DownloadedEmbeddedCoverResolver.resolve(
                      items.first.filePath,
                      onChanged: _onEmbeddedCoverChanged,
                    ) ??
                    items.first.coverUrl,
              );
            }
            return LocalLibraryArtworkBuilder(
              coverPath: _localCover(items),
              filePath: items.first.filePath,
              builder: (context, cover) => _libraryPage(context, items, cover),
            );
          },
        );
  }

  Widget _libraryPage(
    BuildContext context,
    List<UnifiedLibraryItem> items,
    String? cover,
  ) => MornyeArtistSurface(
    imageSource: cover,
    neutralActions: true,
    child: Builder(
      builder: (context) => _buildLibraryPage(context, items, cover),
    ),
  );

  String? _localCover(List<UnifiedLibraryItem> items) => items
      .map((item) => item.localItem?.coverPath?.trim())
      .nonNulls
      .where((path) => path.isNotEmpty)
      .firstOrNull;

  AlbumTrackOrder<UnifiedLibraryItem> _libraryOrder(
    List<UnifiedLibraryItem> items,
  ) => AlbumTrackOrder(
    items,
    discNumber: (item) =>
        item.historyItem?.discNumber ?? item.localItem?.discNumber,
    trackNumber: (item) =>
        item.historyItem?.trackNumber ?? item.localItem?.trackNumber,
    trackName: (item) => item.trackName,
  );

  String _libraryArtist(UnifiedLibraryItem item) {
    final albumArtist =
        (item.historyItem?.albumArtist ?? item.localItem?.albumArtist ?? '')
            .trim();
    return albumArtist.isNotEmpty ? albumArtist : item.artistName;
  }

  /// The one quality label every song shares, or null.
  String? _commonQuality(List<UnifiedLibraryItem> items, String mode) {
    String? label(UnifiedLibraryItem item) {
      final history = item.historyItem;
      final local = item.localItem;
      return buildLibraryAudioQualityLabel(
        mode: mode,
        format: history?.format ?? local?.format,
        filePath: item.filePath,
        fileName: history?.safFileName,
        bitrateKbps: history?.bitrate ?? local?.bitrate,
        bitDepth: history?.bitDepth ?? local?.bitDepth,
        sampleRate: history?.sampleRate ?? local?.sampleRate,
        storedQuality: history?.quality,
      );
    }

    final first = label(items.first);
    if (first == null) return null;
    return items.every((item) => label(item) == first) ? first : null;
  }

  Widget _buildLibraryPage(
    BuildContext context,
    List<UnifiedLibraryItem> items,
    String? cover,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    final order = _libraryOrder(items);
    final tracks = order.tracks;
    pruneSelection(tracks.map((item) => item.id).toSet());
    final bottomPadding = isSelectionMode
        ? MediaQuery.paddingOf(context).bottom
        : 0.0;

    return CollectionScaffold(
      scrollController: scrollController,
      isSelectionMode: showsSelectionBar,
      onExitSelectionMode: exitSelectionMode,
      appBar: _buildLibraryAppBar(context, colorScheme, tracks, cover),
      slivers: [_buildLibraryTrackList(context, order)],
      selectionBar: _buildLibrarySelectionBar(tracks, bottomPadding),
    );
  }

  Widget _buildLibraryAppBar(
    BuildContext context,
    ColorScheme colorScheme,
    List<UnifiedLibraryItem> items,
    String? cover,
  ) {
    final first = items.first;
    final title = first.albumName;
    final artist = _libraryArtist(first);
    final qualityLabelMode = ref.watch(
      settingsProvider.select((s) => s.libraryQualityLabelMode),
    );
    final motion = !MediaQuery.disableAnimationsOf(context)
        ? ref
              .watch(
                playerMotionArtworkProvider((
                  album: title,
                  artist: first.artistName,
                )),
              )
              .value
        : null;
    final cacheWidth = coverCacheWidthForViewport(context);
    Widget placeholder(double size) => Container(
      color: colorScheme.surfaceContainerHighest,
      child: Icon(Icons.album, size: size, color: colorScheme.onSurfaceVariant),
    );
    final Widget background = cover != null
        ? LocalOrNetworkCoverImage(
            url: cover,
            localCacheWidth: cacheWidth,
            placeholder: (_) => Container(color: colorScheme.surface),
          )
        : placeholder(80);
    final tracks = [for (final item in items) item.toTrack()];
    final totalSeconds = tracks.fold<int>(
      0,
      (sum, track) => sum + (track.duration > 0 ? track.duration : 0),
    );
    final totalMinutes = (totalSeconds / 60).round();
    final quality = _commonQuality(items, qualityLabelMode);

    return AlbumDetailHeader(
      title: title,
      immersive: true,
      squareArtwork: motion == null,
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: motion == null
          ? background
          : MotionHeaderBanner(videoUrl: motion.source, fallback: background),
      coverBuilder: (context, coverSize) => cover != null
          ? LocalOrNetworkCoverImage(
              url: cover,
              width: coverSize,
              height: coverSize,
              localCacheWidth: cacheWidth,
              placeholder: (_) => placeholder(48),
            )
          : placeholder(48),
      subtitle: Text(
        artist,
        style: TextStyle(
          color: colorScheme.primary,
          fontSize: context.tokens.headerSubtitleSize,
          fontWeight: FontWeight.w400,
        ),
        textAlign: TextAlign.center,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      meta: HeaderMetaRow(
        items: [
          if (_releaseYear(tracks.first.releaseDate) case final year?)
            HeaderMetaItem(year),
          HeaderMetaItem(context.l10n.queueTrackCount(items.length)),
          if (totalMinutes > 0)
            HeaderMetaItem(context.l10n.listeningStatsMinutes(totalMinutes)),
          if (quality != null && quality.isNotEmpty)
            HeaderMetaItem(quality, icon: Icons.graphic_eq),
        ],
      ),
      actions: CollectionHeaderActions(
        tracks: tracks,
        onLoveAll: () => loveAllTracks(context, ref, tracks),
        addToPlaylistIcon: Icons.add,
        onAddToPlaylist: () =>
            showAddTracksToPlaylistSheet(context, ref, tracks),
        playActions: Consumer(
          builder: (context, ref, _) {
            final isCurrent = ref.watch(
              currentMediaItemProvider.select(
                (state) => items.any(
                  (item) =>
                      mediaItemPlaysRow(state.value, _libraryPlaybackKey(item)),
                ),
              ),
            );
            final isPlaying = isCurrent && ref.watch(playbackPlayingProvider);
            return AlbumPlayActions(
              compact: true,
              isPlaying: isPlaying,
              playLabel: isPlaying
                  ? context.l10n.actionPause
                  : context.l10n.tooltipPlay,
              shuffleTooltip: context.l10n.actionShuffle,
              onPlay: isCurrent
                  ? () => ref
                        .read(musicPlayerControllerProvider)
                        .togglePlayPause(isPlaying)
                  : () => _playAllLibrary(items),
              onShuffle: () => _shuffleLibrary(items),
            );
          },
        ),
      ),
      appBarTitle: isSelectionMode
          ? context.l10n.selectionSelected(selectedIds.length)
          : null,
      leading: isSelectionMode
          ? HeaderCircleButton(
              icon: Icons.close,
              tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
              onPressed: exitSelectionMode,
            )
          : null,
    );
  }

  /// A row matches the player by its file, whichever queue played it.
  PlaybackRowKey _libraryPlaybackKey(UnifiedLibraryItem item) => (
    id: item.historyItem?.id ?? item.localItem?.id ?? item.id,
    path: item.filePath,
  );

  Widget _buildLibraryTrackList(
    BuildContext context,
    AlbumTrackOrder<UnifiedLibraryItem> order,
  ) {
    final items = order.tracks;
    final discMap = order.discGroups;
    final hasMultipleDiscs = discMap.length > 1;
    final slivers = <Widget>[];
    var revealIndex = 0;

    for (final discNumber in order.discNumbers) {
      final discItems = discMap[discNumber];
      if (discItems == null || discItems.isEmpty) continue;
      if (hasMultipleDiscs) {
        slivers.add(
          SliverToBoxAdapter(child: DiscSeparatorChip(discNumber: discNumber)),
        );
      }
      final discRevealStart = revealIndex;
      revealIndex += discItems.length;
      slivers.add(
        SliverList(
          delegate: SliverChildBuilderDelegate((context, index) {
            final item = discItems[index];
            return KeyedSubtree(
              key: ValueKey(item.id),
              child: StaggeredListItem(
                index: discRevealStart + index,
                child: _buildLibraryTrackItem(context, item, items),
              ),
            );
          }, childCount: discItems.length),
        ),
      );
    }

    return SliverPadding(
      padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
      sliver: SliverMainAxisGroup(slivers: slivers),
    );
  }

  Widget _buildLibraryTrackItem(
    BuildContext context,
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> items,
  ) {
    final format = item.localItem?.format;
    final subtitle = Row(
      children: [
        Flexible(
          child: Text(
            item.artistName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (format != null) ...[
          const Text(' • ', style: TextStyle(fontSize: 12)),
          Text(format.toUpperCase(), style: const TextStyle(fontSize: 12)),
        ],
      ],
    );
    return _TrackPlaybackBuilder(
      playbackKey: _libraryPlaybackKey(item),
      builder: (context, playback, toggle) => AlbumTrackTile(
        trackNumber:
            item.historyItem?.trackNumber ?? item.localItem?.trackNumber,
        trackName: item.trackName,
        subtitle: subtitle,
        isSelectionMode: isSelectionMode,
        isSelected: selectedIds.contains(item.id),
        isCurrent: playback.isCurrent,
        isPlaying: playback.isPlaying,
        onToggleSelection: () => toggleSelection(item.id),
        onOpen: () => _openLibraryMetadata(item, items),
        onEnterSelectionMode: () => enterSelectionMode(item.id),
        onPlay: playback.isCurrent
            ? toggle
            : () => _playLibraryRow(item, items),
      ),
    );
  }

  /// Opens the song's metadata, with the album's songs of the same source as
  /// its previous and next.
  Future<void> _openLibraryMetadata(
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> items,
  ) async {
    final history = item.historyItem;
    if (history != null) {
      final navigationItems = [for (final other in items) ?other.historyItem];
      await _openHistoryMetadata(history, navigationItems);
      return;
    }
    final local = item.localItem!;
    final navigationItems = [for (final other in items) ?other.localItem];
    await Navigator.of(context).push(
      slidePageRoute<void>(
        page: TrackMetadataScreen(
          localItem: local,
          localNavigationItems: navigationItems,
          navigationIndex: navigationItems.indexWhere(
            (other) => other.id == local.id,
          ),
        ),
      ),
    );
  }

  Future<void> _openHistoryMetadata(
    DownloadHistoryItem item,
    List<DownloadHistoryItem> navigationItems,
  ) async {
    final navigator = Navigator.of(context);
    final beforeModTime =
        await DownloadedEmbeddedCoverResolver.readFileModTimeMillis(
          item.filePath,
        );
    if (!mounted) return;
    final route = slidePageRoute<bool>(
      page: TrackMetadataScreen(
        item: item,
        historyNavigationItems: navigationItems,
        navigationIndex: navigationItems.indexWhere(
          (other) => other.id == item.id,
        ),
      ),
    );
    final result = await navigator.push(route);
    // Refresh the album after the return animation, not while both pages paint.
    if (route is TransitionRoute<bool>) await route.completed;
    if (!mounted) return;
    await DownloadedEmbeddedCoverResolver.scheduleRefreshForPath(
      item.filePath,
      beforeModTime: beforeModTime,
      force: result == true,
      onChanged: _onEmbeddedCoverChanged,
    );
  }

  void _showCueVirtualTrackSnackBar() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text(cueVirtualTrackRequiresSplitMessage)),
    );
  }

  Future<void> _playLibraryItem(
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> items,
  ) async {
    if (isCueVirtualPath(item.filePath)) {
      _showCueVirtualTrackSnackBar();
      return;
    }
    try {
      await ref
          .read(playbackProvider.notifier)
          .playLibraryQueue(items, startItem: item);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// A tapped row plays in order, so a previous Shuffle is switched off.
  Future<void> _playLibraryRow(
    UnifiedLibraryItem item,
    List<UnifiedLibraryItem> items,
  ) async {
    await ref.read(musicPlayerControllerProvider).setShuffle(false);
    await _playLibraryItem(item, items);
  }

  Future<void> _playAllLibrary(List<UnifiedLibraryItem> items) async {
    final playable = items
        .where((item) => !isCueVirtualPath(item.filePath))
        .toList();
    if (playable.isEmpty) return;
    await _playLibraryRow(playable.first, items);
  }

  Future<void> _shuffleLibrary(List<UnifiedLibraryItem> items) async {
    final playable = items
        .where((item) => !isCueVirtualPath(item.filePath))
        .toList();
    if (playable.isEmpty) return;
    await ref.read(musicPlayerControllerProvider).setShuffle(true);
    await _playLibraryItem(playable[Random().nextInt(playable.length)], items);
  }

  List<UnifiedLibraryItem> _selectedLibraryItems(
    List<UnifiedLibraryItem> items,
  ) => [
    for (final item in items)
      if (selectedIds.contains(item.id)) item,
  ];

  Widget _buildLibrarySelectionBar(
    List<UnifiedLibraryItem> items,
    double bottomPadding,
  ) {
    final allSelected = selectedIds.length == items.length && items.isNotEmpty;
    final selected = _selectedLibraryItems(items);

    return LibraryTrackSelectionBar(
      selectedCount: selectedIds.length,
      allSelected: allSelected,
      onClose: exitSelectionMode,
      onToggleSelectAll: allSelected
          ? exitSelectionMode
          : () => selectAll(items.map((item) => item.id)),
      bottomPadding: bottomPadding,
      albumDelete: true,
      playbackActions: LibrarySelectionPlaybackActions(
        items: selected,
        onClose: exitSelectionMode,
      ),
      onConvert: () => showBatchConvertSheet(
        context,
        ref,
        selected,
        onExitSelectionMode: exitSelectionMode,
      ),
      onReplayGain: ({required remove}) => runBatchReplayGain(
        context,
        selected,
        onExitSelectionMode: exitSelectionMode,
        remove: remove,
      ),
      onDelete: () => _deleteSelectedLibrary(items),
    );
  }

  Future<void> _deleteSelectedLibrary(List<UnifiedLibraryItem> items) async {
    final container = ProviderScope.containerOf(context, listen: false);
    final libraryNotifier = ref.read(localLibraryProvider.notifier);
    final itemsById = {for (final item in items) item.id: item};

    final deletedCount = await confirmAndDeleteTracks(
      context: context,
      ids: selectedIds.toList(),
      deleteItem: (id) async {
        final item = itemsById[id];
        if (item == null) return false;
        // A cue track is a virtual song of its sheet; only its row goes.
        if (isCueVirtualPath(item.filePath)) return true;
        return deleteFile(item.filePath);
      },
      persistDeletedItems: (ids) async {
        final selected = [for (final id in ids) itemsById[id]!];
        await removeDeletedLibraryFileEntries(
          container,
          selected
              .where((item) => !isCueVirtualPath(item.filePath))
              .map((item) => item.filePath),
        );
        await libraryNotifier.removeItems(
          selected
              .where((item) => isCueVirtualPath(item.filePath))
              .map((item) => item.id),
        );
      },
      onExitSelectionMode: exitSelectionMode,
    );

    if (deletedCount == items.length && mounted) {
      Navigator.pop(context);
    }
  }
}

/// Builds one track's row from its playback state, so a track change
/// rebuilds only the rows involved. [builder] gets a callback that pauses or
/// resumes the current track.
class _TrackPlaybackBuilder extends ConsumerWidget {
  const _TrackPlaybackBuilder({
    required this.playbackKey,
    required this.builder,
  });

  final PlaybackRowKey playbackKey;
  final Widget Function(
    BuildContext context,
    MediaPlaybackUi playback,
    VoidCallback toggle,
  )
  builder;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playback = ref.watch(mediaItemPlaybackUiProvider(playbackKey));
    return builder(
      context,
      playback,
      () => ref
          .read(musicPlayerControllerProvider)
          .togglePlayPause(playback.isPlaying),
    );
  }
}
