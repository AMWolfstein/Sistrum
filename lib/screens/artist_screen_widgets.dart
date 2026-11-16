// ignore_for_file: invalid_use_of_protected_member
part of 'artist_screen.dart';

extension _ArtistScreenSections on _ArtistScreenState {
  Widget _concertsButton() => ArtistConcertsButton(
    artistName: widget.artistName,
    concerts: _concerts,
    coverUrl: widget.coverUrl ?? _headerImageUrl,
    providerId: _directMetadataProviderId(),
  );

  List<Widget> _buildHeader(
    BuildContext context,
    ColorScheme colorScheme, {
    required List<ArtistAlbum> albums,
    required bool hasDiscography,
    String? libraryImageUrl,
  }) {
    String? imageUrl = widget.library ? libraryImageUrl : _headerImageUrl;
    if (imageUrl == null || imageUrl.isEmpty) {
      imageUrl = widget.headerImageUrl;
    }
    if (imageUrl == null || imageUrl.isEmpty) {
      imageUrl = widget.coverUrl;
    }
    // A library cover is a file path; online covers are URLs.
    final hasValidImage =
        imageUrl != null &&
        imageUrl.isNotEmpty &&
        (widget.library || Uri.tryParse(imageUrl)?.hasAuthority == true);
    String? headerVideoUrl = _headerVideoUrl;
    if (headerVideoUrl == null || headerVideoUrl.isEmpty) {
      headerVideoUrl = widget.headerVideoUrl;
    }
    final hasMotionBanner =
        headerVideoUrl != null &&
        headerVideoUrl.isNotEmpty &&
        Uri.tryParse(headerVideoUrl)?.hasAuthority == true;
    String? listenersText;
    final listeners = _monthlyListeners ?? widget.monthlyListeners;
    if (listeners != null && listeners > 0) {
      final formatter = NumberFormat.compact();
      listenersText = context.l10n.artistMonthlyListeners(
        formatter.format(listeners),
      );
    }
    final favoriteProviderId = _directMetadataProviderId();
    final favoriteArtistId = _metadataResourceId(favoriteProviderId ?? '');
    final isFavoriteArtist = ref.watch(
      libraryCollectionsProvider.select(
        (state) => state.isFavoriteArtist(
          artistId: favoriteArtistId,
          providerId: favoriteProviderId,
        ),
      ),
    );
    {
      final artwork = hasValidImage
          ? DownloadableCover(
              coverUrl: imageUrl,
              baseName: widget.artistName,
              enabled: !widget.library,
              child: CachedCoverImage(
                imageUrl: imageUrl,
                fit: BoxFit.cover,
                alignment: Alignment.topCenter,
                memCacheWidth: 800,
                errorWidget: (_, _, _) =>
                    const Center(child: Icon(CupertinoIcons.person, size: 80)),
              ),
            )
          : const Center(child: Icon(CupertinoIcons.person, size: 80));
      return MornyeArtistHeader(
        name: widget.artistName,
        logoUrl: _headerLogoUrl ?? widget.headerLogoUrl,
        badge: _concerts.isEmpty || isSelectionMode ? null : _concertsButton(),
        listeners: listenersText,
        showTitle: false,
        artwork: hasMotionBanner
            ? MotionHeaderBanner(videoUrl: headerVideoUrl, fallback: artwork)
            : artwork,
        toolbarActions: isSelectionMode
            ? const []
            : [
                if (!widget.library)
                  HeaderCircleButton(
                    icon: CupertinoIcons.square_arrow_up,
                    tooltip: context.l10n.openInOtherServices,
                    onPressed: () => _showShareSheet(context),
                  ),
                HeaderMoreButton(
                  groups: () => [
                    [
                      ContextMenuAction(
                        icon: isFavoriteArtist
                            ? CupertinoIcons.star_fill
                            : CupertinoIcons.star,
                        label: isFavoriteArtist
                            ? context.l10n.artistOptionRemoveFromFavorites
                            : context.l10n.artistOptionAddToFavorites,
                        onSelected: () => _toggleFavoriteArtist(context),
                      ),
                      if (hasDiscography)
                        ContextMenuAction(
                          icon: CupertinoIcons.arrow_down,
                          label: context.l10n.discographyDownload,
                          onSelected: () => _showDiscographyOptions(
                            context,
                            colorScheme,
                            albums,
                          ),
                        ),
                    ],
                  ],
                ),
              ],
        actions: isSelectionMode
            ? const []
            : [
                // Balances the star, keeping the larger button centered.
                if (widget.library || _isLoadingDiscography || hasDiscography)
                  HeaderCircleButton(
                    icon: CupertinoIcons.shuffle,
                    tonal: true,
                    buttonSize: 50,
                    iconSize: 24,
                    iconColor: colorScheme.primary,
                    tooltip: context.l10n.actionShuffle,
                    onPressed: () => _shuffleArtist(context),
                  ),
                if (widget.library)
                  _headerPrimaryButton(
                    colorScheme,
                    icon: CupertinoIcons.play_fill,
                    label: context.l10n.tooltipPlay,
                    onPressed: _playLibraryArtist,
                  )
                else if (_isLoadingDiscography)
                  const ShimmerLoading(
                    child: SkeletonBox(width: 74, height: 74, borderRadius: 37),
                  )
                else if (hasDiscography)
                  _headerPrimaryButton(
                    colorScheme,
                    icon: CupertinoIcons.arrow_down,
                    label: context.l10n.discographyDownload,
                    onPressed: () =>
                        _showDiscographyOptions(context, colorScheme, albums),
                  ),
                HeaderCircleButton(
                  icon: isFavoriteArtist
                      ? CupertinoIcons.star_fill
                      : CupertinoIcons.star,
                  tonal: true,
                  buttonSize: 50,
                  iconSize: 24,
                  iconColor: colorScheme.primary,
                  tooltip: isFavoriteArtist
                      ? context.l10n.artistOptionRemoveFromFavorites
                      : context.l10n.artistOptionAddToFavorites,
                  onPressed: () => _toggleFavoriteArtist(context),
                ),
              ],
      ).buildSlivers(context);
    }
  }

  /// The large primary circle in the header, Play or Download.
  Widget _headerPrimaryButton(
    ColorScheme colorScheme, {
    required IconData icon,
    required String label,
    required VoidCallback onPressed,
  }) => CupertinoButton(
    padding: EdgeInsets.zero,
    borderRadius: BorderRadius.circular(37),
    color: colorScheme.primary,
    onPressed: onPressed,
    child: SizedBox.square(
      dimension: 74,
      child: Semantics(
        label: label,
        child: Icon(icon, size: 34, color: colorScheme.onPrimary),
      ),
    ),
  );

  Future<void> _shuffleArtist(BuildContext context) async {
    final shuffled = await ref
        .read(musicPlayerControllerProvider)
        .shuffleLibrary(artist: widget.artistName);
    if (shuffled || !context.mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(context.l10n.artistShuffleEmpty)));
  }

  void _showShareSheet(BuildContext context) {
    CrossExtensionShareSheet.show(
      context,
      name: widget.artistName,
      artists: '',
      type: 'artist',
      sourceExtensionId: _directMetadataProviderId() ?? '',
    );
  }

  Widget _buildPopularSection(BuildContext context, ColorScheme colorScheme) {
    if (_topTracks == null || _topTracks!.isEmpty) {
      return const SizedBox.shrink();
    }

    final tracks = _topTracks!;
    _historySnapshot.update(tracks);
    final historyKeys = _historySnapshot.lookupKeys;
    final existingHistoryKeys = ref.watch(
      downloadHistoryVisibleBatchExistsProvider(_historySnapshot.request),
    );
    const tracksPerPage = 5;
    final pageCount = (tracks.length / tracksPerPage).ceil();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
          child: Text(
            context.l10n.artistPopular,
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
          ),
        ),
        SizedBox(
          height: tracksPerPage * 64.0,
          child: PageView.builder(
            controller: _popularPageController,
            itemCount: pageCount,
            onPageChanged: (page) {
              setState(() {
                _popularCurrentPage = page;
              });
            },
            itemBuilder: (context, pageIndex) {
              final startIndex = pageIndex * tracksPerPage;
              final endIndex = (startIndex + tracksPerPage).clamp(
                0,
                tracks.length,
              );
              final pageTracks = tracks.sublist(startIndex, endIndex);

              return Column(
                children: pageTracks.asMap().entries.map((entry) {
                  final globalIndex = startIndex + entry.key;
                  return _buildPopularTrackItem(
                    globalIndex + 1,
                    entry.value,
                    colorScheme,
                    existingHistoryKeys.contains(historyKeys[globalIndex]),
                  );
                }).toList(),
              );
            },
          ),
        ),
        if (pageCount > 1)
          Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: List.generate(pageCount, (index) {
                  final isActive = _popularCurrentPage == index;
                  return Container(
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: isActive ? 8 : 6,
                    height: isActive ? 8 : 6,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isActive
                          ? colorScheme.primary
                          : colorScheme.onSurfaceVariant.withValues(alpha: 0.3),
                    ),
                  );
                }),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildPopularTrackItem(
    int rank,
    Track track,
    ColorScheme colorScheme,
    bool isInHistory, {
    bool compact = false,
    bool separated = true,
  }) {
    return Consumer(
      builder: (context, ref, child) {
        final queueItem = ref.watch(
          downloadQueueLookupProvider.select(
            (lookup) => lookup.byTrackId[track.id],
          ),
        );

        final showLocalLibraryIndicator = ref.watch(
          settingsProvider.select(
            (s) => s.localLibraryEnabled && s.localLibraryShowDuplicates,
          ),
        );
        final isInLocalLibrary = showLocalLibraryIndicator
            ? ref.watch(
                localLibraryProvider.select(
                  (state) => state.existsInLibrary(
                    isrc: track.isrc,
                    trackName: track.name,
                    artistName: track.artistName,
                  ),
                ),
              )
            : false;

        final isQueued = queueItem != null;

        final row = InkWell(
          onTap: () => _handlePopularTrackTap(
            track,
            isQueued: isQueued,
            isInHistory: isInHistory,
            isInLocalLibrary: isInLocalLibrary,
          ),
          onLongPress: () => TrackCollectionQuickActions.showTrackOptionsSheet(
            context,
            ref,
            track,
            hasLocalPlaybackCandidate: isInHistory || isInLocalLibrary,
          ),
          child: Padding(
            padding: compact
                ? const EdgeInsets.symmetric(vertical: 4)
                : EdgeInsetsDirectional.fromSTEB(20, 8, 10, 8),
            child: Row(
              children: [
                track.coverUrl != null
                    ? DownloadableCover(
                        coverUrl: track.coverUrl,
                        baseName: '${track.artistName} - ${track.name}',
                        child: CachedCoverImage(
                          imageUrl: track.coverUrl!,
                          width: 48,
                          height: 48,
                          borderRadius: BorderRadius.circular(4),
                          placeholder: (context, url) => ShimmerLoading(
                            child: Container(
                              width: 48,
                              height: 48,
                              color: colorScheme.surfaceContainerHighest,
                            ),
                          ),
                          errorWidget: (context, url, error) => Container(
                            width: 48,
                            height: 48,
                            color: colorScheme.surfaceContainerHighest,
                            child: Icon(
                              Icons.music_note,
                              color: colorScheme.onSurfaceVariant,
                              size: 24,
                            ),
                          ),
                        ),
                      )
                    : Container(
                        width: 48,
                        height: 48,
                        color: colorScheme.surfaceContainerHighest,
                        child: Icon(
                          Icons.music_note,
                          color: colorScheme.onSurfaceVariant,
                          size: 24,
                        ),
                      ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        track.name,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          fontSize: compact ? 14 : 17,
                          fontWeight: FontWeight.w400,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (track.albumName.isNotEmpty ||
                          isInLocalLibrary ||
                          isInHistory)
                        Row(
                          children: [
                            if (track.albumName.isNotEmpty)
                              Expanded(
                                child: ClickableAlbumName(
                                  albumName: track.albumName,
                                  albumId: track.albumId,
                                  artistName: track.artistName,
                                  coverUrl: track.coverUrl,
                                  extensionId: widget.extensionId,
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(
                                        color: colorScheme.onSurfaceVariant,
                                        fontSize: compact ? 13 : 15,
                                      ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                            if (isInLocalLibrary || isInHistory) ...[
                              if (track.albumName.isNotEmpty)
                                const SizedBox(width: 6),
                              const InLibraryBadge(),
                            ],
                          ],
                        ),
                    ],
                  ),
                ),
                TrackCollectionQuickActions(
                  track: track,
                  hasLocalPlaybackCandidate: isInHistory || isInLocalLibrary,
                ),
              ],
            ),
          ),
        );
        if (!separated) return row;
        return Stack(
          children: [
            row,
            PositionedDirectional(
              start: compact ? 60 : 80,
              end: compact ? 0 : 20,
              bottom: 0,
              child: Divider(
                height: 1,
                thickness: 0.5,
                color: Colors.white.withValues(alpha: 0.12),
              ),
            ),
          ],
        );
      },
    );
  }

  void _handlePopularTrackTap(
    Track track, {
    required bool isQueued,
    required bool isInHistory,
    required bool isInLocalLibrary,
  }) async {
    if (isQueued) return;

    final settings = ref.read(settingsProvider);
    if (settings.allowQualityVariants && (isInHistory || isInLocalLibrary)) {
      _downloadTrack(track);
      return;
    }

    final playedLocal = await playLocalIfAvailable(context, ref, track);
    if (playedLocal) {
      return;
    }

    _downloadTrack(track);
  }

  void _downloadTrack(Track track) {
    final settings = ref.read(settingsProvider);
    ref.read(settingsProvider.notifier).setHasSearchedBefore();

    void enqueue(String service, {String? quality}) {
      ref
          .read(downloadQueueProvider.notifier)
          .addToQueue(track, service, qualityOverride: quality);
      showAddedToQueueSnackBar(context, track.name);
    }

    if (settings.askQualityBeforeDownload || settings.allowQualityVariants) {
      DownloadServicePicker.show(
        context,
        recommendedService: _recommendedDownloadService(),
        onSelect: (quality, service) {
          if (!mounted) return;
          enqueue(service, quality: quality);
        },
      );
      return;
    }

    final extensionState = ref.read(extensionProvider);
    final service = resolveEffectiveDownloadService(
      settings.defaultService,
      extensionState,
    );
    if (service.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.extensionsNoDownloadProvider)),
      );
      return;
    }
    enqueue(service);
  }

  Widget _buildAlbumSection(
    BuildContext context,
    String title,
    List<ArtistAlbum> albums,
    ColorScheme colorScheme, {
    bool showTypeBadge = false,
  }) {
    return _albumShelf(
      context,
      '$title (${albums.length})',
      albums,
      colorScheme,
      showTypeBadge: showTypeBadge,
      tileSize: artistAlbumTileSize(context),
      sectionHeight: artistAlbumSectionHeight(context),
      headingStyle: Theme.of(
        context,
      ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
      headingPadding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
      listPadding: 12,
    );
  }

  Widget _albumShelf(
    BuildContext context,
    String title,
    List<ArtistAlbum> albums,
    ColorScheme colorScheme, {
    required bool showTypeBadge,
    required double tileSize,
    required double sectionHeight,
    required TextStyle? headingStyle,
    required EdgeInsets headingPadding,
    required double listPadding,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: headingPadding,
          child: Text(title, style: headingStyle),
        ),
        NotificationListener<ScrollNotification>(
          onNotification: _onAlbumsScroll,
          child: SizedBox(
            height: sectionHeight,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: EdgeInsets.symmetric(horizontal: listPadding),
              itemCount: albums.length,
              itemBuilder: (context, index) {
                final album = albums[index];
                return KeyedSubtree(
                  key: ValueKey(album.id),
                  child: _buildAlbumCard(
                    context,
                    album,
                    colorScheme,
                    sectionAlbums: albums,
                    tileSize: tileSize,
                    sectionHeight: sectionHeight,
                    showTypeBadge: showTypeBadge,
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAlbumCard(
    BuildContext context,
    ArtistAlbum album,
    ColorScheme colorScheme, {
    required List<ArtistAlbum> sectionAlbums,
    required double tileSize,
    required double sectionHeight,
    bool showTypeBadge = false,
  }) {
    final isSelected = selectedIds.contains(album.id);

    return _buildAlbumCardBody(
      context,
      album,
      colorScheme,
      sectionAlbums: sectionAlbums,
      isSelected: isSelected,
      tileSize: tileSize,
      sectionHeight: sectionHeight,
      showTypeBadge: showTypeBadge,
    );
  }

  Widget _buildAlbumCardBody(
    BuildContext context,
    ArtistAlbum album,
    ColorScheme colorScheme, {
    required List<ArtistAlbum> sectionAlbums,
    required bool isSelected,
    required double tileSize,
    required double sectionHeight,
    required bool showTypeBadge,
  }) {
    return Semantics(
      button: true,
      selected: isSelectionMode && isSelected,
      label: showsSelectionBar
          ? context.l10n.a11ySelectAlbum(album.name)
          : context.l10n.a11yOpenAlbum(album.name),
      child: GestureDetector(
        onTap: () {
          if (isSelectionMode) {
            toggleSelection(album.id);
          } else if (widget.library) {
            _openLibraryAlbum(album);
          } else {
            _navigateToAlbum(album);
          }
        },
        onLongPress: () {
          if (!isSelectionMode && !widget.library) {
            enterSelectionMode(album.id);
          }
        },
        child: Container(
          width: tileSize,
          height: sectionHeight,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox.square(
                dimension: tileSize,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    DownloadableCover(
                      coverUrl: album.coverUrl,
                      baseName: '${album.artists} - ${album.name}',
                      enabled: !isSelectionMode && !widget.library,
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: album.coverUrl != null
                            ? CachedCoverImage(
                                imageUrl: album.coverUrl!,
                                width: tileSize,
                                height: tileSize,
                                fit: BoxFit.cover,
                                memCacheWidth: (tileSize * 2).round(),
                                memCacheHeight: (tileSize * 2).round(),
                                placeholder: (context, url) => ShimmerLoading(
                                  child: Container(
                                    color: colorScheme.surfaceContainerHighest,
                                  ),
                                ),
                                errorWidget: (context, url, error) => Container(
                                  color: colorScheme.surfaceContainerHighest,
                                  child: Icon(
                                    Icons.album,
                                    color: colorScheme.onSurfaceVariant,
                                    size: 40,
                                  ),
                                ),
                              )
                            : Container(
                                color: colorScheme.surfaceContainerHighest,
                                child: Icon(
                                  Icons.album,
                                  color: colorScheme.onSurfaceVariant,
                                  size: 40,
                                ),
                              ),
                      ),
                    ),
                    if (isSelected || showsSelectionBar)
                      Positioned.fill(
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            color: isSelected
                                ? colorScheme.primary.withValues(alpha: 0.3)
                                : Colors.black.withValues(alpha: 0.1),
                            border: isSelected
                                ? Border.all(
                                    color: colorScheme.primary,
                                    width: 3,
                                  )
                                : null,
                          ),
                        ),
                      ),
                    if (showsSelectionBar)
                      PositionedDirectional(
                        top: 8,
                        end: 8,
                        child: AnimatedSelectionCheckbox(
                          selected: isSelected,
                          colorScheme: colorScheme,
                          size: 28,
                          unselectedColor: colorScheme.surface.withValues(
                            alpha: 0.9,
                          ),
                        ),
                      ),
                    if (showTypeBadge)
                      PositionedDirectional(
                        start: 6,
                        bottom: 6,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.7),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            album.albumType == 'ep'
                                ? context.l10n.releaseTypeEp
                                : context.l10n.releaseTypeSingle,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: Text(
                        album.name,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      album.totalTracks > 0
                          ? '${album.releaseDate.length >= 4 ? album.releaseDate.substring(0, 4) : album.releaseDate} ${context.l10n.tracksCount(album.totalTracks)}'
                          : album.releaseDate.length >= 4
                          ? album.releaseDate.substring(0, 4)
                          : album.releaseDate,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: colorScheme.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _navigateToAlbum(ArtistAlbum album) {
    ref.read(settingsProvider.notifier).setHasSearchedBefore();

    if (album.providerId != null && album.providerId!.isNotEmpty) {
      pushPage<void>(
        context,
        (context) => ExtensionAlbumScreen(
          extensionId: album.providerId!,
          albumId: album.id,
          albumName: album.name,
          coverUrl: album.coverUrl,
          initialAlbumType: album.albumType,
          initialTotalTracks: album.totalTracks,
        ),
      );
    } else {
      pushPage<void>(
        context,
        (context) => AlbumScreen(
          albumId: album.id,
          albumName: album.name,
          coverUrl: album.coverUrl,
        ),
      );
    }
  }
}
