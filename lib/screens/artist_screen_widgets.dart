// ignore_for_file: invalid_use_of_protected_member
part of 'artist_screen.dart';

extension _ArtistScreenSections on _ArtistScreenState {
  /// The library artist's favourite key: its name under the default provider.
  String _favoriteProviderId() => resolveEffectiveMetadataProvider(
    legacyProviderIdFromResourceId(widget.artistName) ?? 'spotify',
    ref.read(extensionProvider),
  );

  String get _favoriteArtistId => stripPrefixedResourceId(widget.artistName);

  List<Widget> _buildHeader(
    BuildContext context,
    ColorScheme colorScheme, {
    String? libraryImageUrl,
  }) {
    final favoriteProviderId = _favoriteProviderId();
    final isFavoriteArtist = ref.watch(
      libraryCollectionsProvider.select(
        (state) => state.isFavoriteArtist(
          artistId: _favoriteArtistId,
          providerId: favoriteProviderId,
        ),
      ),
    );
    final hasValidImage = libraryImageUrl != null && libraryImageUrl.isNotEmpty;
    final artwork = hasValidImage
        ? CachedCoverImage(
            imageUrl: libraryImageUrl,
            fit: BoxFit.cover,
            alignment: Alignment.topCenter,
            memCacheWidth: 800,
            errorWidget: (_, _, _) =>
                const Center(child: Icon(CupertinoIcons.person, size: 80)),
          )
        : const Center(child: Icon(CupertinoIcons.person, size: 80));
    return MornyeArtistHeader(
      name: widget.artistName,
      showTitle: false,
      artwork: artwork,
      toolbarActions: [
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
                onSelected: () =>
                    _toggleFavoriteArtist(context, libraryImageUrl),
              ),
            ],
          ],
        ),
      ],
      actions: [
        // Balances the star, keeping the larger button centered.
        HeaderCircleButton(
          icon: CupertinoIcons.shuffle,
          tonal: true,
          buttonSize: 50,
          iconSize: 24,
          iconColor: colorScheme.primary,
          tooltip: context.l10n.actionShuffle,
          onPressed: () => _shuffleArtist(context),
        ),
        _headerPrimaryButton(
          colorScheme,
          icon: CupertinoIcons.play_fill,
          label: context.l10n.tooltipPlay,
          onPressed: _playLibraryArtist,
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
          onPressed: () => _toggleFavoriteArtist(context, libraryImageUrl),
        ),
      ],
    ).buildSlivers(context);
  }

  /// The large primary circle in the header, Play.
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

  Future<void> _toggleFavoriteArtist(
    BuildContext context,
    String? libraryImageUrl,
  ) async {
    final added = await ref
        .read(libraryCollectionsProvider.notifier)
        .toggleFavoriteArtist(
          artistId: _favoriteArtistId,
          providerId: _favoriteProviderId(),
          name: widget.artistName,
          imageUrl: libraryImageUrl,
        );
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          added
              ? context.l10n.collectionAddedToFavoriteArtists(widget.artistName)
              : context.l10n.collectionRemovedFromFavoriteArtists(
                  widget.artistName,
                ),
        ),
      ),
    );
  }

  Widget _buildAlbumSection(
    BuildContext context,
    String title,
    List<ArtistAlbum> albums,
    ColorScheme colorScheme, {
    bool showTypeBadge = false,
  }) {
    final tileSize = artistAlbumTileSize(context);
    final sectionHeight = artistAlbumSectionHeight(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
          child: Text(
            '$title (${albums.length})',
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
            itemCount: albums.length,
            itemBuilder: (context, index) {
              final album = albums[index];
              return KeyedSubtree(
                key: ValueKey(album.id),
                child: _buildAlbumCard(
                  context,
                  album,
                  colorScheme,
                  tileSize: tileSize,
                  sectionHeight: sectionHeight,
                  showTypeBadge: showTypeBadge,
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildAlbumCard(
    BuildContext context,
    ArtistAlbum album,
    ColorScheme colorScheme, {
    required double tileSize,
    required double sectionHeight,
    required bool showTypeBadge,
  }) {
    return Semantics(
      button: true,
      label: context.l10n.a11yOpenAlbum(album.name),
      child: GestureDetector(
        onTap: () => _openLibraryAlbum(album),
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
                    ClipRRect(
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
}
