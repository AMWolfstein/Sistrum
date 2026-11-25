import 'dart:io';
import 'dart:math';
import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:flutter/foundation.dart' show mapEquals;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/album_track_sort_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';
import 'package:sistrum/widgets/album_track_sort_menu.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/audio_quality_badge_policy.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/image_cache_utils.dart';
import 'package:sistrum/services/library_album_artwork.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/batch_track_actions.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/library_album_artwork_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/player_motion_artwork_provider.dart';
import 'package:sistrum/providers/song_leftovers_provider.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/desktop/detail_header.dart';
import 'package:sistrum/widgets/desktop/library_track_menu.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/song_menu.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/loaded_sliver_fade_in.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';
import 'package:sistrum/widgets/disc_separator_chip.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/blurred_cover.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/widgets/motion_header_banner.dart';
import 'package:sistrum/widgets/queue_swipe.dart';

class LocalAlbumScreen extends ConsumerStatefulWidget {
  final String albumName;
  final String artistName;
  final String? coverPath;
  final List<LocalLibraryItem> tracks;
  final Future<List<LocalLibraryItem>> Function()? loadTracks;

  const LocalAlbumScreen({
    super.key,
    required this.albumName,
    required this.artistName,
    this.coverPath,
    this.tracks = const [],
    this.loadTracks,
  });

  @override
  ConsumerState<LocalAlbumScreen> createState() => _LocalAlbumScreenState();
}

class _LocalAlbumScreenState extends ConsumerState<LocalAlbumScreen>
    with
        SelectionModeMixin<LocalAlbumScreen>,
        CollapsingHeaderScrollMixin<LocalAlbumScreen> {
  late AlbumTrackOrder<LocalLibraryItem> _trackOrder;
  Map<String, int>? _trackRanks;
  List<LocalLibraryItem> get _sortedTracksCache => _trackOrder.tracks;
  Map<int, List<LocalLibraryItem>> get _discGroupsCache =>
      _trackOrder.discGroups;
  List<LocalLibraryItem>? _loadedTracks;
  bool _loadingTracks = false;
  Object? _loadError;
  int _loadGeneration = 0;

  List<int> get _sortedDiscNumbersCache => _trackOrder.discNumbers;
  bool get _hasMultipleDiscsCache => _trackOrder.discGroups.length > 1;
  String? _commonQualityCache;

  @override
  void initState() {
    super.initState();
    _rebuildTrackCaches();
    _loadTracks();
  }

  Future<void> _loadTracks() async {
    final loader = widget.loadTracks;
    if (loader == null) return;
    final generation = ++_loadGeneration;
    _loadingTracks = true;
    _loadError = null;
    try {
      final tracks = await loader();
      if (!mounted || generation != _loadGeneration) return;
      _loadedTracks = tracks;
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      _loadError = error;
    }
    _loadingTracks = false;
    setState(_rebuildTrackCaches);
  }

  @override
  void didUpdateWidget(covariant LocalAlbumScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.tracks, widget.tracks) ||
        oldWidget.tracks.length != widget.tracks.length ||
        oldWidget.loadTracks != widget.loadTracks) {
      _loadGeneration++;
      _loadedTracks = null;
      _loadingTracks = false;
      _loadError = null;
      _rebuildTrackCaches();
      _loadTracks();
    }
  }

  void _rebuildTrackCaches() {
    _trackOrder = AlbumTrackOrder(
      _loadedTracks ?? widget.tracks,
      discNumber: (track) => track.discNumber,
      trackNumber: (track) => track.trackNumber,
      trackName: (track) => track.trackName,
      ranks: _trackRanks,
    );
    _commonQualityCache = null;
  }

  Future<void> _deleteSelected(List<LocalLibraryItem> currentTracks) async {
    final container = ProviderScope.containerOf(context, listen: false);
    final libraryNotifier = ref.read(localLibraryProvider.notifier);
    final tracksById = {for (final track in currentTracks) track.id: track};

    final deletedCount = await confirmAndDeleteTracks(
      context: context,
      ids: selectedIds.toList(),
      deleteItem: (id) async {
        final item = tracksById[id];
        if (item == null) return false;
        if (!isCueVirtualPath(item.filePath)) {
          final deleted = await ref
              .read(songLeftoversProvider)
              .delete(item.filePath);
          if (!deleted) return false;
        }
        return true;
      },
      persistDeletedItems: (ids) async {
        final items = ids.map((id) => tracksById[id]!);
        await removeDeletedLibraryFileEntries(
          container,
          items
              .where((item) => !isCueVirtualPath(item.filePath))
              .map((item) => item.filePath),
        );
        await libraryNotifier.removeItems(
          items
              .where((item) => isCueVirtualPath(item.filePath))
              .map((item) => item.id),
        );
      },
      onExitSelectionMode: exitSelectionMode,
    );

    if (deletedCount == currentTracks.length && mounted) {
      Navigator.pop(context);
    }
  }

  Future<void> _openFile(LocalLibraryItem track) async {
    if (!await songFileCanPlay(track.filePath)) {
      if (mounted) {
        await showSongMenu(
          context,
          ref,
          track: UnifiedLibraryItem.fromLocalLibrary(track).toTrack(),
          filePath: track.filePath,
          localItem: track,
        );
      }
      return;
    }
    try {
      await ref
          .read(playbackProvider.notifier)
          .playLocalLibraryQueue(_sortedTracksCache, startItem: track);
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  /// A row matches the player by its file, so playlist playback, which uses
  /// the file path rather than the Library row ID, marks it too.
  PlaybackRowKey _playbackKey(LocalLibraryItem track) =>
      (id: track.id, path: track.filePath);

  bool _isCurrentTrack(MediaItem? item, LocalLibraryItem track) =>
      mediaItemPlaysRow(item, _playbackKey(track));

  @override
  Widget build(BuildContext context) {
    final albumCover = widget.coverPath?.trim();
    final artworkTrack =
        _sortedTracksCache
            .where(
              (track) => albumCover?.isNotEmpty == true
                  ? track.coverPath == albumCover
                  : track.coverPath?.isNotEmpty == true,
            )
            .firstOrNull ??
        _sortedTracksCache.firstOrNull;
    // Songs handed to the page keep the covers they had when it opened, so
    // a cover found from here shows from its record.
    final foundCover = _albumArtwork()?.record?.coverFile;
    return LocalLibraryArtworkBuilder(
      coverPath: albumCover?.isNotEmpty == true
          ? albumCover
          : artworkTrack?.coverPath ?? foundCover,
      filePath: artworkTrack?.filePath,
      builder: (context, coverPath) => MornyeArtistSurface(
        imageSource: coverPath,
        neutralActions: true,
        child: Builder(builder: (context) => _buildPage(context, coverPath)),
      ),
    );
  }

  Widget _buildPage(BuildContext context, String? coverPath) {
    final colorScheme = Theme.of(context).colorScheme;
    final bottomPadding = isSelectionMode
        ? MediaQuery.paddingOf(context).bottom
        : 0.0;
    final ranks = ref.watch(albumTrackRanksProvider(_sortKey));
    if (!mapEquals(ranks, _trackRanks)) {
      _trackRanks = ranks;
      _rebuildTrackCaches();
    }
    final tracks = _sortedTracksCache;

    if (tracks.isEmpty && !_loadingTracks && _loadError == null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.albumName)),
        body: Center(child: Text(context.l10n.noTracksFoundForAlbum)),
      );
    }

    pruneSelection(tracks.map((t) => t.id).toSet());

    final scaffold = CollectionScaffold(
      scrollController: scrollController,
      isSelectionMode: showsSelectionBar,
      onExitSelectionMode: exitSelectionMode,
      appBar: _buildAppBar(context, colorScheme, coverPath),
      slivers: [
        if (_loadError != null)
          SliverToBoxAdapter(
            child: Center(
              child: TextButton.icon(
                onPressed: () => setState(() {
                  _loadTracks();
                }),
                icon: const Icon(Icons.refresh),
                label: Text(context.l10n.dialogRetry),
              ),
            ),
          )
        else ...[
          // Loaded songs fade in once over the skeleton's place where their
          // rows do not stagger in: the desktop table, and phone rows that
          // load after the page opens.
          LoadedSliverFadeIn(
            key: const ValueKey('local-album-tracks'),
            loading: _loadingTracks,
            enabled: isDesktopPlatform || widget.loadTracks != null,
            sliver: _loadingTracks
                ? const SliverToBoxAdapter(
                    child: RepaintBoundary(
                      child: AlbumTrackListSkeleton(
                        itemCount: 8,
                        showQuality: true,
                      ),
                    ),
                  )
                : _buildTrackList(context, colorScheme, tracks),
          ),
        ],
      ],
      selectionBar: _buildSelectionBottomBar(tracks, bottomPadding),
    );
    return DesktopListScope(
      onSelectAll: tracks.isEmpty
          ? null
          : () => selectAll(tracks.map((track) => track.id)),
      onClearSelection: isSelectionMode ? exitSelectionMode : null,
      onDelete: isSelectionMode ? () => _deleteSelected(tracks) : null,
      onActivate: () {
        final anchor = tracks
            .where((track) => track.id == selectionAnchorId)
            .firstOrNull;
        if (anchor != null) _openFile(anchor);
      },
      child: scaffold,
    );
  }

  Widget _buildAppBar(
    BuildContext context,
    ColorScheme colorScheme,
    String? coverPath,
  ) {
    final expandedHeight = calculateExpandedHeight(context);
    final motion = !MediaQuery.disableAnimationsOf(context)
        ? ref
              .watch(
                songMotionArtworkProvider((
                  album: widget.albumName,
                  artist:
                      _sortedTracksCache.firstOrNull?.artistName ??
                      widget.artistName,
                  source: _sortedTracksCache.firstOrNull?.filePath ?? '',
                )),
              )
              .value
        : null;

    final cacheWidth = coverCacheWidthForViewport(context);
    final Widget background = coverPath != null
        ? LocalCoverImage(
            url: coverPath,
            localCacheWidth: cacheWidth,
            placeholder: (_) => Container(color: colorScheme.surface),
          )
        : Container(
            color: colorScheme.surfaceContainerHighest,
            child: Icon(
              Icons.album,
              size: 80,
              color: colorScheme.onSurfaceVariant,
            ),
          );

    return AlbumDetailHeader(
      title: widget.albumName,
      immersive: true,
      squareArtwork: motion == null,
      expandedHeight: expandedHeight,
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: motion == null
          ? background
          : MotionHeaderBanner(videoUrl: motion.source, fallback: background),
      paletteSource: coverPath,
      blurAndScrimBackground: coverPath != null,
      blurSource: localCoverBlurSource(coverPath),
      coverBuilder: (context, coverSize) => coverPath != null
          ? Image.file(
              File(coverPath),
              fit: BoxFit.cover,
              width: coverSize,
              height: coverSize,
              cacheWidth: cacheWidth,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) => Container(
                color: colorScheme.surfaceContainerHighest,
                child: Icon(
                  Icons.album,
                  size: 48,
                  color: colorScheme.onSurfaceVariant,
                ),
              ),
            )
          : Container(
              color: colorScheme.surfaceContainerHighest,
              child: Icon(
                Icons.album,
                size: 48,
                color: colorScheme.onSurfaceVariant,
              ),
            ),
      // Built inside the header, so desktop reads its artwork scheme.
      subtitle: Builder(
        builder: (header) => Text(
          widget.artistName,
          style: isDesktopPlatform
              ? desktopDetailSubtitleStyle(header)
              : TextStyle(
                  color: colorScheme.primary,
                  fontSize: context.tokens.headerSubtitleSize,
                  fontWeight: FontWeight.w400,
                ),
          textAlign: TextAlign.center,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
      meta: _buildLocalHeaderMeta(context),
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
      appBarActions: isSelectionMode
          ? const []
          : [
              SortHeaderButton<AlbumTrackSort>(
                tooltip: context.l10n.albumSortSongs,
                current: ref.watch(albumTrackSortProvider(_sortKey)),
                groups: albumTrackSortGroups(
                  context,
                  key: _sortKey,
                  songs: [
                    for (final track in _sortedTracksCache) track.trackName,
                  ],
                ),
                onSelected: (sort) => ref
                    .read(albumTrackSortsProvider.notifier)
                    .setSort(albumTrackSortScope(_sortKey), sort),
              ),
            ],
      actions: isSelectionMode
          ? null
          : Consumer(
              builder: (context, ref, _) {
                final isCurrent = ref.watch(
                  currentMediaItemProvider.select(
                    (state) => _sortedTracksCache.any(
                      (track) => _isCurrentTrack(state.value, track),
                    ),
                  ),
                );
                final isPlaying =
                    isCurrent && ref.watch(playbackPlayingProvider);
                final albumTracks = [
                  for (final item in _albumItems()) item.toTrack(),
                ];
                // Filled once every song of the album is in Loved.
                final isLoved = ref.watch(
                  libraryCollectionsProvider.select(
                    (state) =>
                        albumTracks.isNotEmpty &&
                        albumTracks.every(state.isLoved),
                  ),
                );
                return AlbumPlayActions(
                  isPlaying: isPlaying,
                  isLoved: isLoved,
                  onLove: () => loveAllTracks(context, ref, albumTracks),
                  playLabel: isPlaying
                      ? context.l10n.actionPause
                      : context.l10n.tooltipPlay,
                  shuffleTooltip: context.l10n.actionShuffle,
                  onPlay: isCurrent
                      ? () => ref
                            .read(musicPlayerControllerProvider)
                            .togglePlayPause(isPlaying)
                      : _playAll,
                  onShuffle: _shuffleAll,
                  onAddToPlaylist: () => showAddTracksToPlaylistSheet(
                    Navigator.of(context, rootNavigator: true).context,
                    ref,
                    albumTracks,
                  ),
                );
              },
            ),
      moreMenu: () => _withArtworkActions(
        libraryTrackMenuGroups(
          context,
          ref,
          items: _albumItems(),
          onPlay: _playAll,
        ),
      ),
    );
  }

  /// The album's key and what was found for it. Null when the album has no
  /// Library song to look it up by.
  ({String albumKey, AlbumArtworkRecord? record})? _albumArtwork() {
    final path = _sortedTracksCache.firstOrNull?.filePath;
    if (path == null) return null;
    return ref.watch(libraryAlbumArtworkForSongProvider(path)).value;
  }

  /// [groups] with Remove Found Artwork, once something was found. It goes
  /// before a last group of destructive actions.
  List<List<ContextMenuAction>> _withArtworkActions(
    List<List<ContextMenuAction>> groups,
  ) {
    final artwork = _albumArtwork();
    final record = artwork?.record;
    if (artwork == null ||
        (record?.coverFile == null && record?.motionSaved != true)) {
      return groups;
    }
    final actions = [
      ContextMenuAction(
        icon: Icons.hide_image_outlined,
        label: context.l10n.albumRemoveFoundArtwork,
        onSelected: () => _removeFoundArtwork(artwork.albumKey),
      ),
    ];
    final destructiveLast =
        groups.isNotEmpty && groups.last.every((action) => action.destructive);
    return [
      ...destructiveLast ? groups.take(groups.length - 1) : groups,
      actions,
      if (destructiveLast) groups.last,
    ];
  }

  Future<void> _removeFoundArtwork(String albumKey) async {
    final l10n = context.l10n;
    await ref
        .read(libraryAlbumArtworkProvider.notifier)
        .removeForAlbum(albumKey);
    if (!mounted) return;
    showAppSnackBar(context, content: Text(l10n.albumRemoveFoundArtworkDone));
    _loadTracks();
  }

  AlbumTrackSortKey get _sortKey =>
      (album: widget.albumName, artist: widget.artistName);

  /// Every song of the album, for the header's album-wide actions.
  List<UnifiedLibraryItem> _albumItems() => [
    for (final track in _sortedTracksCache)
      UnifiedLibraryItem.fromLocalLibrary(track),
  ];

  Widget _buildLocalHeaderMeta(BuildContext context) {
    final tracks = _sortedTracksCache;
    final totalSeconds = tracks.fold<int>(
      0,
      (sum, t) => sum + ((t.duration ?? 0) > 0 ? t.duration! : 0),
    );
    final totalMinutes = (totalSeconds / 60).round();
    final quality = _getCommonQuality();
    final first = tracks.firstOrNull;
    final genre = first?.genre?.trim();
    final year = _releaseYear(first?.releaseDate);

    return HeaderMetaRow(
      items: [
        if (genre != null && genre.isNotEmpty) HeaderMetaItem(genre),
        if (year != null) HeaderMetaItem(year),
        HeaderMetaItem(context.l10n.queueTrackCount(tracks.length)),
        if (totalMinutes > 0) HeaderMetaItem('$totalMinutes min'),
        if (quality != null && quality.isNotEmpty)
          HeaderMetaItem(quality, icon: Icons.graphic_eq),
      ],
    );
  }

  static final _releaseYearPattern = RegExp(r'(\d{4})');

  String? _releaseYear(String? date) {
    if (date == null || date.isEmpty) return null;
    return _releaseYearPattern.firstMatch(date)?.group(1);
  }

  Future<void> _playAll() async {
    final tracks = _sortedTracksCache;
    if (tracks.isEmpty) return;
    await ref.read(musicPlayerControllerProvider).setShuffle(false);
    await _openFile(tracks.first);
  }

  Future<void> _shuffleAll() async {
    final tracks = _sortedTracksCache
        .where((t) => !isCueVirtualPath(t.filePath))
        .toList();
    if (tracks.isEmpty) return;
    await ref.read(musicPlayerControllerProvider).setShuffle(true);
    await _openFile(tracks[Random().nextInt(tracks.length)]);
  }

  String? _getCommonQuality() =>
      _commonQualityCache ??= _computeCommonQuality(_sortedTracksCache);

  String? _computeCommonQuality(List<LocalLibraryItem> tracks) {
    if (tracks.isEmpty) return null;
    String? label(LocalLibraryItem track) => buildLibraryAudioQualityLabel(
      format: track.format,
      filePath: track.filePath,
      bitrateKbps: track.bitrate,
      bitDepth: track.bitDepth,
    );

    final firstQuality = label(tracks.first);
    if (firstQuality == null) return null;
    for (final track in tracks) {
      if (label(track) != firstQuality) return null;
    }
    return firstQuality;
  }

  Widget _buildTrackList(
    BuildContext context,
    ColorScheme colorScheme,
    List<LocalLibraryItem> tracks,
  ) {
    if (isDesktopPlatform) return _buildDesktopTable(context, tracks);
    final discGroups = _discGroupsCache;
    final hasMultipleDiscs = _hasMultipleDiscsCache;

    final slivers = <Widget>[];

    for (final discNumber in _sortedDiscNumbersCache) {
      final discTracks = discGroups[discNumber]!;

      if (hasMultipleDiscs) {
        slivers.add(
          SliverToBoxAdapter(child: DiscSeparatorChip(discNumber: discNumber)),
        );
      }

      slivers.add(
        SliverList(
          delegate: SliverChildBuilderDelegate((context, index) {
            final track = discTracks[index];
            return KeyedSubtree(
              key: ValueKey(track.id),
              child: StaggeredListItem(
                index: index,
                animate: widget.loadTracks == null,
                child: _buildTrackItem(context, track),
              ),
            );
          }, childCount: discTracks.length),
        ),
      );
    }

    return SliverPadding(
      padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
      sliver: SliverMainAxisGroup(slivers: slivers),
    );
  }

  /// The desktop song table: a click plays, right-click opens the
  /// selection's actions.
  Widget _buildDesktopTable(
    BuildContext context,
    List<LocalLibraryItem> tracks,
  ) {
    final byId = {for (final track in tracks) track.id: track};
    return SliverPadding(
      padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
      sliver: SliverTrackTable(
        rows: [
          for (final track in tracks)
            TrackTableRow(
              id: track.id,
              number: track.trackNumber,
              title: track.trackName,
              artist: track.artistName,
              album: track.albumName,
              quality: buildLibraryAudioQualityLabel(
                format: track.format,
                filePath: track.filePath,
                bitrateKbps: track.bitrate,
                bitDepth: track.bitDepth,
              ),
              duration: track.duration == null
                  ? null
                  : Duration(seconds: track.duration!),
              titleTrailing: track.isExplicit ? const ExplicitChip() : null,
              playback: mediaItemPlaybackUiProvider(_playbackKey(track)),
              trailing: Consumer(
                builder: (context, ref, _) {
                  final isCurrent = ref.watch(
                    currentMediaItemProvider.select(
                      (state) => _isCurrentTrack(state.value, track),
                    ),
                  );
                  final isPlaying =
                      isCurrent && ref.watch(playbackPlayingProvider);
                  return SongPlayButton(
                    isPlaying: isPlaying,
                    onPlay: isCurrent
                        ? () => ref
                              .read(musicPlayerControllerProvider)
                              .togglePlayPause(isPlaying)
                        : () => _openFile(track),
                    dense: true,
                  );
                },
              ),
            ),
        ],
        columns: const [
          TrackTableColumn.number,
          TrackTableColumn.title,
          TrackTableColumn.artist,
          TrackTableColumn.quality,
          TrackTableColumn.duration,
        ],
        selectedIds: selectedIds,
        albumLayout: true,
        albumArtist: widget.artistName,
        onSelect: applyDesktopClick,
        onActivate: (id) {
          final track = byId[id];
          if (track != null) _openFile(track);
        },
        onContextMenu: (id, position) {
          final track = byId[id];
          if (track == null) return;
          showSelectionMenu(
            id,
            () => _showDesktopMenu(context, tracks, track, position),
          );
        },
      ),
    );
  }

  Future<bool> _showDesktopMenu(
    BuildContext context,
    List<LocalLibraryItem> tracks,
    LocalLibraryItem track,
    Offset position,
  ) {
    final items = _selectedUnifiedItems(tracks);
    return showContextMenu(
      context,
      position: position,
      groups: libraryTrackMenuGroups(
        context,
        ref,
        items: items,
        onPlay: () => _openFile(track),
        onDone: exitSelectionMode,
        onDelete: () => _deleteSelected(tracks),
      ),
    );
  }

  Widget _buildTrackItem(BuildContext context, LocalLibraryItem track) {
    return Consumer(
      builder: (context, ref, _) {
        final isCurrent = ref.watch(
          currentMediaItemProvider.select(
            (state) => _isCurrentTrack(state.value, track),
          ),
        );
        final isPlaying = isCurrent && ref.watch(playbackPlayingProvider);
        final VoidCallback play = isCurrent
            ? () => ref
                  .read(musicPlayerControllerProvider)
                  .togglePlayPause(isPlaying)
            : () => _openFile(track);
        return TrackCard(
          style: TrackCardStyle.flat,
          queueSwipe: libraryItemQueueSwipe(
            ref,
            UnifiedLibraryItem.fromLocalLibrary(track),
          ),
          isSelectionMode: isSelectionMode,
          isSelected: selectedIds.contains(track.id),
          isCurrent: isCurrent,
          isPlaying: isPlaying,
          leading: SizedBox(
            width: 24,
            child: Text(
              track.trackNumber?.toString() ?? '-',
              style: const TextStyle(fontWeight: FontWeight.w500),
              textAlign: TextAlign.center,
            ),
          ),
          title: track.trackName,
          subtitle: Text(
            track.artistName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          isExplicit: track.isExplicit,
          onPlay: play,
          menu: isSelectionMode
              ? null
              : SongMenuButton(
                  track: UnifiedLibraryItem.fromLocalLibrary(track).toTrack(),
                  filePath: track.filePath,
                  localItem: track,
                ),
          onTap: isSelectionMode ? () => toggleSelection(track.id) : play,
          onLongPress: isSelectionMode
              ? null
              : () => enterSelectionMode(track.id),
        );
      },
    );
  }

  List<UnifiedLibraryItem> _selectedUnifiedItems(
    List<LocalLibraryItem> tracks,
  ) {
    final tracksById = {for (final t in tracks) t.id: t};
    return [
      for (final t in selectedIds.map((id) => tracksById[id]))
        if (t != null) UnifiedLibraryItem.fromLocalLibrary(t),
    ];
  }

  Widget _buildSelectionBottomBar(
    List<LocalLibraryItem> tracks,
    double bottomPadding,
  ) {
    final allSelected =
        selectedIds.length == tracks.length && tracks.isNotEmpty;

    return LibraryTrackSelectionBar(
      selectedCount: selectedIds.length,
      allSelected: allSelected,
      onClose: exitSelectionMode,
      onToggleSelectAll: allSelected
          ? exitSelectionMode
          : () => selectAll(tracks.map((e) => e.id)),
      bottomPadding: bottomPadding,
      albumDelete: true,
      playbackActions: LibrarySelectionPlaybackActions(
        items: _selectedUnifiedItems(tracks),
        onClose: exitSelectionMode,
      ),
      onReplayGain: ({required remove}) => runBatchReplayGain(
        context,
        _selectedUnifiedItems(tracks),
        onExitSelectionMode: exitSelectionMode,
        remove: remove,
      ),
      onDelete: () => _deleteSelected(tracks),
    );
  }
}
