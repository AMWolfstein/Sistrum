import 'dart:math';

import 'package:cupertino_ui/cupertino_ui.dart'
    show CupertinoButton, CupertinoIcons;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_easy/liquid_glass_easy.dart';
import 'package:sistrum/providers/runtime_profile_provider.dart';
import 'package:sistrum/theme/mornye_glass_presets.dart';
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_artist_songs_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/artist_release_groups.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/artist_featured_album_card.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/blurred_cover.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';

/// A Library artist, in the layout of the online artist page: the latest
/// release's cover as the header, then the artist's releases from the Library.
class LibraryArtistScreen extends ConsumerStatefulWidget {
  const LibraryArtistScreen({super.key, required this.artist});

  final String artist;

  @override
  ConsumerState<LibraryArtistScreen> createState() =>
      _LibraryArtistScreenState();
}

class _LibraryArtistScreenState extends ConsumerState<LibraryArtistScreen> {
  final _scroll = ScrollController();
  final _showTitle = ValueNotifier(false);

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.removeListener(_onScroll);
    _scroll.dispose();
    _showTitle.dispose();
    super.dispose();
  }

  void _onScroll() => _showTitle.value = _scroll.offset > 280;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final songs = ref.watch(libraryArtistSongsProvider(widget.artist));
    final tracks = [
      for (final item in songs.value ?? const <UnifiedLibraryItem>[])
        ?item.localItem,
    ];
    final favorite = ref.watch(
      libraryCollectionsProvider.select(
        (collections) => collections.containsFavoriteArtistKey(
          artistCollectionKey(artistId: widget.artist, providerId: null),
        ),
      ),
    );
    final groups = groupArtistReleases(widget.artist, tracks);
    final albums = groups.albums;
    final latest = groups.latest;
    final cover = latest?.coverPath;
    final header = MornyeArtistHeader(
      name: widget.artist,
      artwork: cover == null
          ? const Center(child: Icon(CupertinoIcons.person, size: 80))
          : LocalCoverImage(
              url: cover,
              fit: BoxFit.cover,
              placeholder: (_) =>
                  const Center(child: Icon(CupertinoIcons.person, size: 80)),
            ),
      showTitle: false,
      showTitleListenable: _showTitle,
      blurSource: localCoverBlurSource(cover),
      actions: [
        HeaderCircleButton(
          icon: CupertinoIcons.shuffle,
          buttonSize: 50,
          iconSize: 24,
          iconColor: Colors.white,
          glassTintColor: Colors.white,
          glassTintOpacity: 0.10,
          tooltip: l10n.actionShuffle,
          onPressed: tracks.isEmpty ? null : () => _play(tracks, shuffle: true),
        ),
        // Read under MornyeArtistSurface, so the circle is the artist page's
        // surface (the page background), not the outer app scheme.
        Builder(
          builder: (context) => CupertinoButton(
            padding: EdgeInsets.zero,
            borderRadius: BorderRadius.circular(37),
            color: Theme.of(context).colorScheme.surface,
            onPressed: tracks.isEmpty
                ? null
                : () => _play(tracks, shuffle: false),
            child: SizedBox.square(
              dimension: 74,
              child: Semantics(
                label: l10n.tooltipPlay,
                child: Icon(
                  CupertinoIcons.play_fill,
                  size: 34,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ),
        HeaderCircleButton(
          icon: favorite ? CupertinoIcons.star_fill : CupertinoIcons.star,
          buttonSize: 50,
          iconSize: 24,
          iconColor: Colors.white,
          glassTintColor: Colors.white,
          glassTintOpacity: 0.10,
          tooltip: favorite
              ? l10n.artistOptionRemoveFromFavorites
              : l10n.artistOptionAddToFavorites,
          onPressed: () => _toggleFavorite(context),
        ),
      ],
    );

    return MornyeArtistSurface(
      imageSource: cover,
      child: Builder(
        builder: (context) => Scaffold(
          backgroundColor: Theme.of(context).colorScheme.surface,
          body: LiquidGlassBatch(
            // The back button and the toolbar capsule share one read of the
            // page; they do not overlap. Off in None.
            enabled: ref.watch(mornyeBlurEnabledProvider),
            blurMethod: MornyeGlassPreset.of(
              ref.watch(mornyeGlassLevelProvider),
            ).batchBlurMethod,
            child: CustomScrollView(
              controller: _scroll,
              slivers: [
                ...header.buildSlivers(context),
                if (songs.hasError)
                  SliverToBoxAdapter(
                    child: Center(
                      child: TextButton.icon(
                        icon: const Icon(Icons.refresh),
                        onPressed: () => ref.invalidate(
                          libraryArtistSongsProvider(widget.artist),
                        ),
                        label: Text(l10n.dialogRetry),
                      ),
                    ),
                  ),
                if (latest != null)
                  SliverToBoxAdapter(
                    child: ArtistFeaturedAlbumCard(
                      release: latest,
                      onTap: () => _openRelease(latest),
                    ),
                  ),
                if (albums.isNotEmpty)
                  SliverToBoxAdapter(
                    child: _ReleaseShelf(
                      title: l10n.searchAlbums,
                      releases: albums,
                      onOpen: _openRelease,
                    ),
                  ),
                if (groups.singles.isNotEmpty)
                  SliverToBoxAdapter(
                    child: _ReleaseShelf(
                      title: l10n.artistSingles,
                      releases: groups.singles,
                      onOpen: _openRelease,
                    ),
                  ),
                if (groups.compilations.isNotEmpty)
                  SliverToBoxAdapter(
                    child: _ReleaseShelf(
                      title: l10n.artistCompilations,
                      releases: groups.compilations,
                      onOpen: _openRelease,
                    ),
                  ),
                const SliverToBoxAdapter(child: SizedBox(height: 32)),
                const NavBarSliverSpacer(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _toggleFavorite(BuildContext context) async {
    final added = await ref
        .read(libraryCollectionsProvider.notifier)
        .toggleFavoriteArtist(
          artistId: widget.artist,
          providerId: null,
          name: widget.artist,
        );
    if (!context.mounted) return;
    final l10n = context.l10n;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          added
              ? l10n.collectionAddedToFavoriteArtists(widget.artist)
              : l10n.collectionRemovedFromFavoriteArtists(widget.artist),
        ),
      ),
    );
  }

  void _openRelease(ArtistRelease release) => pushPage<void>(
    context,
    (_) => LocalAlbumScreen(
      albumName: release.albumName,
      artistName: release.primaryArtist,
      coverPath: release.coverPath,
      loadTracks: () => _loadAlbum(release),
    ),
  );

  /// Every track of the album, not only those crediting this artist.
  Future<List<LocalLibraryItem>> _loadAlbum(ArtistRelease release) async {
    final rows = await LibraryDatabase.instance.getQueueLocalAlbumTracksByKey(
      release.albumKey,
    );
    return rows.map(LocalLibraryItem.fromJson).toList(growable: false);
  }

  Future<void> _play(
    List<LocalLibraryItem> tracks, {
    required bool shuffle,
  }) async {
    final playable = [
      for (final track in tracks)
        if (!isCueVirtualPath(track.filePath)) track,
    ];
    if (playable.isEmpty) return;
    final start = shuffle
        ? playable[Random().nextInt(playable.length)]
        : playable.first;
    try {
      await ref.read(musicPlayerControllerProvider).setShuffle(shuffle);
      await ref
          .read(playbackProvider.notifier)
          .playLocalLibraryQueue(playable, startItem: start);
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    }
  }
}

/// A titled horizontal row of release covers.
class _ReleaseShelf extends StatelessWidget {
  const _ReleaseShelf({
    required this.title,
    required this.releases,
    required this.onOpen,
  });

  final String title;
  final List<ArtistRelease> releases;
  final ValueChanged<ArtistRelease> onOpen;

  @override
  Widget build(BuildContext context) {
    final tile = artistAlbumTileSize(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 12),
          child: Text(
            '$title (${releases.length})',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
          ),
        ),
        SizedBox(
          height: artistAlbumSectionHeight(context),
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: releases.length,
            itemBuilder: (context, index) {
              final release = releases[index];
              return _ReleaseTile(
                key: ValueKey(release.key),
                release: release,
                size: tile,
                onTap: () => onOpen(release),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ReleaseTile extends StatelessWidget {
  const _ReleaseTile({
    super.key,
    required this.release,
    required this.size,
    required this.onTap,
  });

  final ArtistRelease release;
  final double size;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final secondary = Theme.of(context).colorScheme.onSurfaceVariant;
    final year = release.releasedOn?.year;
    final cover = release.coverPath;
    return Semantics(
      button: true,
      label: release.albumName,
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: SizedBox(
          width: size,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: SizedBox.square(
                    dimension: size - 8,
                    child: LocalCoverImage(
                      url: cover ?? '',
                      placeholder: (_) =>
                          const Icon(CupertinoIcons.square_stack, size: 32),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  release.albumName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (year != null)
                  Text(
                    '$year',
                    maxLines: 1,
                    style: textTheme.bodySmall?.copyWith(color: secondary),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
