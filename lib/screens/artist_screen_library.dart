part of 'artist_screen.dart';

/// The library artist page, filled from the library's own files.
extension _ArtistLibrarySections on _ArtistScreenState {
  Widget _buildLibraryArtist(BuildContext context) {
    final overview = ref.watch(
      libraryArtistOverviewProvider(widget.artistName),
    );
    final popular = ref.watch(
      libraryArtistPopularTracksProvider(widget.artistName),
    );
    final featured = ref.watch(
      libraryArtistFeaturedTracksProvider(widget.artistName),
    );
    return MornyeArtistSurface(
      imageSource: overview.value?.imageCover,
      child: Builder(
        builder: (context) => _buildLibraryPage(
          context,
          overview,
          popular: popular.value ?? const [],
          featured: featured.value ?? const [],
        ),
      ),
    );
  }

  Widget _buildLibraryPage(
    BuildContext context,
    AsyncValue<LibraryArtistOverview> overview, {
    required List<Track> popular,
    required List<Track> featured,
  }) {
    final colorScheme = Theme.of(context).colorScheme;
    final data = overview.value;
    final albums = sortArtistAlbumsNewestFirst(
      data?.albums ?? const <ArtistAlbum>[],
    );
    _ensureAlbumBuckets(albums);
    final latest = data?.latestRelease;
    return Scaffold(
      body: CustomScrollView(
        controller: _scrollController,
        slivers: [
          ..._buildHeader(
            context,
            colorScheme,
            libraryImageUrl: data?.imageCover,
          ),
          if (overview.hasError)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: ErrorCard(
                  error: context.friendlyError(overview.error!),
                  colorScheme: colorScheme,
                  onRetry: () => ref.invalidate(
                    libraryArtistOverviewProvider(widget.artistName),
                  ),
                ),
              ),
            )
          else if (data == null)
            const SliverToBoxAdapter(
              child: ArtistScreenSkeleton(
                showCoverHeader: false,
                showPopularSection: true,
              ),
            )
          else ...[
            if (latest != null)
              SliverToBoxAdapter(
                key: const ValueKey('artist-latest'),
                child: ArtistFeaturedAlbumCard(
                  album: latest,
                  onTap: () => _openLibraryAlbum(latest),
                  onPlay: () => _playLibraryAlbum(latest),
                ),
              ),
            if (popular.isNotEmpty)
              SliverToBoxAdapter(
                key: const ValueKey('artist-popular'),
                child: _buildLibraryPopular(context, colorScheme, popular),
              ),
            if (_albumsOnlyBucket.isNotEmpty)
              SliverToBoxAdapter(
                key: const ValueKey('artist-albums'),
                child: _buildAlbumSection(
                  context,
                  context.l10n.artistAlbums,
                  _albumsOnlyBucket,
                  colorScheme,
                ),
              ),
            if (_singlesBucket.isNotEmpty)
              SliverToBoxAdapter(
                key: const ValueKey('artist-singles'),
                child: _buildAlbumSection(
                  context,
                  context.l10n.artistSingles,
                  _singlesBucket,
                  colorScheme,
                  showTypeBadge: true,
                ),
              ),
            if (featured.isNotEmpty)
              SliverToBoxAdapter(
                key: const ValueKey('artist-compilations'),
                child: _buildLibraryFeatured(context, colorScheme, featured),
              ),
          ],
          const SliverToBoxAdapter(child: SizedBox(height: 32)),
          const NavBarSliverSpacer(),
        ],
      ),
    );
  }

  Widget _buildLibraryPopular(
    BuildContext context,
    ColorScheme colorScheme,
    List<Track> tracks,
  ) {
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
        ArtistPopularCarousel(
          tracks: tracks,
          pageSize: libraryPopularPageSize,
          coverBuilder: (track) => _libraryCover(track.coverUrl, colorScheme),
          onPlay: (index) => _playLibraryTracks(tracks, startIndex: index),
          onMenu: (buttonContext, index) =>
              _showPopularMenu(buttonContext, tracks, index),
        ),
      ],
    );
  }

  /// The three dots of a Popular row: the offline song actions.
  Future<void> _showPopularMenu(
    BuildContext buttonContext,
    List<Track> tracks,
    int index,
  ) async {
    final origin = contextMenuOriginOf(buttonContext);
    if (origin == null) return;
    final items = await ref.read(
      libraryArtistPopularItemsProvider(widget.artistName).future,
    );
    if (!mounted || items.length != tracks.length) return;
    final item = items[index];
    void refresh() =>
        ref.invalidate(libraryArtistPopularItemsProvider(widget.artistName));
    await showContextMenu(
      context,
      position: origin,
      groups: libraryTrackMenuGroups(
        context,
        ref,
        items: [item],
        onPlay: () => _playLibraryTracks(tracks, startIndex: index),
        onConvert: () => showBatchConvertSheet(context, ref, [
          item,
        ], onExitSelectionMode: refresh),
        onDelete: item.filePath.startsWith('network://')
            ? null
            : () => deleteLibraryTracks(
                context,
                ref,
                [item],
                isActive: () => mounted,
                onComplete: refresh,
              ),
      ),
    );
  }

  Widget _buildLibraryFeatured(
    BuildContext context,
    ColorScheme colorScheme,
    List<Track> tracks,
  ) {
    final tileSize = artistAlbumTileSize(context);
    final sectionHeight = artistAlbumSectionHeight(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
          child: Text(
            '${context.l10n.artistCompilations} (${tracks.length})',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
          ),
        ),
        SizedBox(
          height: sectionHeight,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: tracks.length,
            itemBuilder: (context, index) {
              final track = tracks[index];
              // The same tile width and gaps as the album rows above.
              return Container(
                width: tileSize,
                margin: const EdgeInsets.symmetric(horizontal: 4),
                child: TrackGridCard(
                  cover: _libraryCover(track.coverUrl, colorScheme),
                  title: track.name,
                  explicit: track.isExplicit,
                  subtitle: Text(
                    track.albumArtist ?? track.artistName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  onTap: () => _playLibraryTracks(tracks, startIndex: index),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  /// A library cover is a file path or a URL; a song without one shows a note.
  Widget _libraryCover(String? coverUrl, ColorScheme colorScheme) {
    Widget fallback() => ColoredBox(
      color: colorScheme.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.music_note, color: colorScheme.onSurfaceVariant),
      ),
    );
    if (coverUrl == null || coverUrl.isEmpty) return fallback();
    return LocalOrNetworkCoverImage(
      url: coverUrl,
      placeholder: (_) => fallback(),
    );
  }

  Future<void> _playLibraryArtist() async {
    try {
      final tracks = await ref.read(
        libraryArtistTracksProvider(widget.artistName).future,
      );
      if (!mounted) return;
      await _playLibraryTracks(tracks);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  Future<void> _playLibraryAlbum(ArtistAlbum album) async {
    try {
      final tracks = await ref.read(
        libraryAlbumTracksProvider(album.id).future,
      );
      if (!mounted) return;
      await _playLibraryTracks(tracks);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  Future<void> _playLibraryTracks(
    List<Track> tracks, {
    int startIndex = 0,
  }) async {
    if (tracks.isEmpty) return;
    try {
      await ref.read(musicPlayerControllerProvider).setShuffle(false);
      await ref
          .read(playbackProvider.notifier)
          .playTrackList(tracks, startIndex: startIndex);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// Opens a library album on the library album page.
  void _openLibraryAlbum(ArtistAlbum album) {
    pushPage<void>(context, (_) => AlbumScreen.library(albumKey: album.id));
  }
}
