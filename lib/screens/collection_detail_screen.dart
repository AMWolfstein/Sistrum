import 'dart:io';
import 'dart:math';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path/path.dart' as p;
import 'package:share_plus/share_plus.dart' show ShareParams, SharePlus, XFile;
import 'package:sistrum/controllers/batch_track_actions.dart';
import 'package:sistrum/widgets/blurred_cover.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/controllers/confirm_and_delete_tracks.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/collection_row.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/collection_track_files_provider.dart';
import 'package:sistrum/providers/collection_track_sort_provider.dart';
import 'package:sistrum/providers/deleted_library_files.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/batch_track_actions.dart';
import 'package:sistrum/services/m3u_playlist_service.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/image_cache_utils.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/collection_track_sort_menu.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/desktop/library_track_menu.dart';
import 'package:sistrum/widgets/desktop/track_list_table.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';
import 'package:sistrum/widgets/destructive_selection_button.dart';
import 'package:sistrum/widgets/discard_changes_dialog.dart';
import 'package:sistrum/widgets/library_mix_cover.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/queue_swipe.dart';
import 'package:sistrum/widgets/selection_action_button.dart';
import 'package:sistrum/widgets/song_menu.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:sistrum/widgets/track_card.dart';

/// The collection a [CollectionDetailScreen] shows.
enum CollectionDetailKind { playlist, loved, genre }

/// A playlist, Loved, or the songs of one Library genre. The three share the
/// Album page's layout: header art, title, meta line, the Play and Shuffle
/// row, then the songs, each row led by the song's cover.
class CollectionDetailScreen extends ConsumerStatefulWidget {
  const CollectionDetailScreen.playlist({super.key, required this.playlistId})
    : kind = CollectionDetailKind.playlist,
      genre = null;

  const CollectionDetailScreen.loved({super.key})
    : kind = CollectionDetailKind.loved,
      playlistId = null,
      genre = null;

  const CollectionDetailScreen.genre({
    super.key,
    required LibraryBrowseEntry this.genre,
  }) : kind = CollectionDetailKind.genre,
       playlistId = null;

  final CollectionDetailKind kind;
  final String? playlistId;
  final LibraryBrowseEntry? genre;

  @override
  ConsumerState<CollectionDetailScreen> createState() =>
      _CollectionDetailScreenState();
}

class _CollectionDetailScreenState extends ConsumerState<CollectionDetailScreen>
    with
        SelectionModeMixin<CollectionDetailScreen>,
        CollapsingHeaderScrollMixin<CollectionDetailScreen> {
  bool get _isGenre => widget.kind == CollectionDetailKind.genre;
  bool get _editingOrder => _orderDraft != null;

  UserPlaylistCollection? _playlist;

  /// The songs in their saved order, which Custom order and the M3U export
  /// use whatever sort the page shows.
  List<CollectionRow> _savedRows = const [];

  /// A genre's songs, less those deleted from this page. Null until they load.
  List<UnifiedLibraryItem>? _genreItems;
  AsyncValue<List<UnifiedLibraryItem>>? _genreSongs;

  /// The order being edited (Custom order), null when not editing.
  List<CollectionRow>? _orderDraft;
  List<String> _orderBefore = const [];
  bool _savingOrder = false;
  bool _draggingOrder = false;
  bool _confirmingDiscard = false;
  bool _trackActionInProgress = false;

  /// The looked-up cover of each desktop row by key. The last answer stays
  /// while a changed list is looked up again.
  Map<String, String?> _desktopCovers = const {};

  @override
  void initState() {
    super.initState();
    final playlistId = widget.playlistId;
    if (widget.kind == CollectionDetailKind.playlist && playlistId != null) {
      Future.microtask(
        () => ref
            .read(libraryCollectionsProvider.notifier)
            .ensurePlaylistLoaded(playlistId),
      );
    }
  }

  String get _sortKey => switch (widget.kind) {
    CollectionDetailKind.playlist => playlistTrackSortKey(
      widget.playlistId ?? '',
    ),
    CollectionDetailKind.loved => 'loved',
    CollectionDetailKind.genre => 'genre:${widget.genre!.key}',
  };

  String _title(AppLocalizations l10n) => switch (widget.kind) {
    CollectionDetailKind.playlist => _playlist?.name ?? l10n.collectionPlaylist,
    CollectionDetailKind.loved => l10n.collectionLoved,
    CollectionDetailKind.genre => widget.genre!.name,
  };

  @override
  Widget build(BuildContext context) {
    ref.watch(localLibraryProvider.select((s) => s.loadedIndexVersion));
    final source = _sourceRows();
    _savedRows = source;
    final sort = ref.watch(
      collectionTrackSortsProvider.select(
        (sorts) =>
            sorts[_sortKey] ??
            (_isGenre ? CollectionTrackSort.title : CollectionTrackSort.custom),
      ),
    );
    final rows =
        _orderDraft ??
        sortCollectionRows(
          source,
          sort,
          sort == CollectionTrackSort.mostPlayed
              ? ref.watch(collectionSongPlaysProvider).value ??
                    const <String, int>{}
              : const <String, int>{},
        );
    final art = _headerArt(context, rows);
    return MornyeArtistSurface(
      imageSource: art.paletteSource,
      neutralActions: true,
      child: Builder(
        builder: (context) => _buildPage(context, rows, art, sort),
      ),
    );
  }

  Widget _buildPage(
    BuildContext context,
    List<CollectionRow> rows,
    _HeaderArt art,
    CollectionTrackSort sort,
  ) {
    final ids = [for (final row in rows) row.key];
    pruneSelection(ids.toSet());
    _lookUpDesktopCovers(rows);

    final bottomPadding = isSelectionMode
        ? MediaQuery.paddingOf(context).bottom
        : 0.0;
    final scaffold = CollectionScaffold(
      scrollController: scrollController,
      isSelectionMode: showsSelectionBar,
      onExitSelectionMode: exitSelectionMode,
      selectionBar: _buildSelectionBar(context, rows, ids, bottomPadding),
      appBar: _buildHeader(context, rows, sort, art),
      slivers: _buildSlivers(context, rows),
    );
    return PopScope(
      canPop: !_editingOrder,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _editingOrder) _cancelOrder();
      },
      child: DesktopListScope(
        onSelectAll: rows.isEmpty || _editingOrder
            ? null
            : () => selectAll(ids),
        onClearSelection: _editingOrder
            ? _cancelOrder
            : isSelectionMode
            ? exitSelectionMode
            : null,
        onDelete: isSelectionMode ? () => _deleteSelected(rows) : null,
        onActivate: () {
          if (_editingOrder) return;
          final index = rows.indexWhere((row) => row.key == selectionAnchorId);
          if (index >= 0) _activate(rows, index);
        },
        child: scaffold,
      ),
    );
  }

  /// The songs in their saved order. A genre's come from the Library, and
  /// show once they have loaded.
  List<CollectionRow> _sourceRows() {
    switch (widget.kind) {
      case CollectionDetailKind.playlist:
        final playlistId = widget.playlistId;
        _playlist = playlistId == null
            ? null
            : ref.watch(
                libraryCollectionsProvider.select(
                  (state) => state.playlistById(playlistId),
                ),
              );
        return [
          for (final entry
              in _playlist?.tracks ?? const <CollectionTrackEntry>[])
            CollectionRow(key: entry.key, track: entry.track),
        ];
      case CollectionDetailKind.loved:
        _playlist = null;
        final loved = ref.watch(
          libraryCollectionsProvider.select((state) => state.loved),
        );
        return [
          for (final entry in loved)
            CollectionRow(key: entry.key, track: entry.track),
        ];
      case CollectionDetailKind.genre:
        final songs = ref.watch(libraryGenreSongsProvider(widget.genre!.key));
        _genreSongs = songs;
        // A new read replaces the list; songs deleted here stay out until then.
        if (songs.value case final items? when items != _genreItems) {
          _genreItems = items;
        }
        return [
          for (final item in _genreItems ?? const <UnifiedLibraryItem>[])
            CollectionRow(key: item.id, track: item.toTrack(), library: item),
        ];
    }
  }

  /// The phone list or the desktop table, or the state that stands in for
  /// the songs: the empty note, the loading skeleton or the retry.
  List<Widget> _buildSlivers(BuildContext context, List<CollectionRow> rows) {
    final l10n = context.l10n;
    final genreSongs = _genreSongs;
    final genreLoading = _isGenre && genreSongs != null && !genreSongs.hasValue;
    return [
      if (_editingOrder)
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
      if (genreLoading)
        if (genreSongs.hasError)
          SliverToBoxAdapter(
            child: Center(
              child: TextButton.icon(
                icon: const Icon(Icons.refresh),
                onPressed: () => ref.invalidate(
                  libraryGenreSongsProvider(widget.genre!.key),
                ),
                label: Text(l10n.dialogRetry),
              ),
            ),
          )
        else
          SliverToBoxAdapter(
            child: RepaintBoundary(
              child: TrackListSkeleton(
                itemCount: min(widget.genre?.trackCount ?? 8, 8),
              ),
            ),
          )
      else if (rows.isEmpty)
        _isGenre
            ? SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(32),
                  child: Center(child: Text(l10n.errorNoTracksFound)),
                ),
              )
            : SliverFillRemaining(
                hasScrollBody: false,
                child: _EmptyCollectionState(
                  title: widget.kind == CollectionDetailKind.loved
                      ? l10n.collectionLovedEmptyTitle
                      : l10n.collectionPlaylistEmptyTitle,
                  subtitle: widget.kind == CollectionDetailKind.loved
                      ? l10n.collectionLovedEmptySubtitle
                      : l10n.collectionPlaylistEmptySubtitle,
                ),
              )
      else if (_editingOrder)
        CollectionReorderSliver(
          entries: rows,
          enabled: !_savingOrder,
          onDraggingChanged: (dragging) {
            if (mounted) setState(() => _draggingOrder = dragging);
          },
          onReorder: (oldIndex, newIndex) {
            if (_orderDraft == null || _savingOrder) return;
            setState(() {
              final next = [..._orderDraft!];
              next.insert(newIndex, next.removeAt(oldIndex));
              _orderDraft = next;
            });
          },
        )
      else if (isDesktopPlatform)
        _buildDesktopTable(context, rows)
      else
        SliverPadding(
          padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate((context, index) {
              return KeyedSubtree(
                key: ValueKey(rows[index].key),
                child: StaggeredListItem(
                  index: index,
                  child: _buildRow(context, rows, index),
                ),
              );
            }, childCount: rows.length),
          ),
        ),
    ];
  }

  // ---------------------------------------------------------------- header

  Widget _buildHeader(
    BuildContext context,
    List<CollectionRow> rows,
    CollectionTrackSort sort,
    _HeaderArt art,
  ) {
    final l10n = context.l10n;
    final count = _isGenre && _genreSongs?.hasValue != true
        ? widget.genre?.trackCount
        : rows.length;
    final minutes =
        rows.fold<int>(0, (sum, row) => sum + max(0, row.track.duration)) ~/ 60;
    final selecting = isSelectionMode;
    final editing = _editingOrder;
    final moreMenu = editing || selecting
        ? null
        : () => _moreMenuGroups(context, rows);

    return AlbumDetailHeader(
      title: _title(l10n),
      immersive: true,
      expandedHeight: calculateExpandedHeight(context),
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: art.background,
      paletteSource: art.paletteSource,
      blurAndScrimBackground: art.blur,
      blurSource: art.blurSource,
      titleScrim: true,
      coverBuilder: art.coverBuilder,
      meta: HeaderMetaRow(
        items: [
          if (count != null) HeaderMetaItem(l10n.tracksCount(count)),
          if (minutes > 0) HeaderMetaItem('$minutes min'),
        ],
      ),
      appBarTitle: editing
          ? l10n.playlistEditOrder
          : selecting
          ? l10n.selectionSelected(selectedIds.length)
          : null,
      leading: selecting || editing
          ? HeaderCircleButton(
              icon: Icons.close,
              tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
              onPressed: editing ? _cancelOrder : exitSelectionMode,
            )
          : null,
      appBarActions: [
        if (editing)
          TextButton(
            key: const ValueKey('collection-order-save'),
            onPressed: !_orderChanged || _savingOrder || _draggingOrder
                ? null
                : _saveOrder,
            child: Text(l10n.dialogSave),
          ),
        if (!editing && !selecting && rows.length > 1)
          SortHeaderButton<CollectionTrackSort>(
            tooltip: l10n.albumSortSongs,
            current: sort,
            groups: collectionTrackSortGroups(
              context,
              onCustomOrder: _isGenre ? null : () => _editOrder(_savedRows),
            ),
            onSelected: (picked) => ref
                .read(collectionTrackSortsProvider.notifier)
                .setSort(_sortKey, picked),
          ),
        // The desktop header adds its own from [moreMenu].
        if (moreMenu != null && !isDesktopPlatform)
          // Loved and genre pages act straight from their header, with no menu.
          if (widget.kind == CollectionDetailKind.loved)
            HeaderCircleButton(
              icon: Icons.ios_share,
              tooltip: l10n.collectionExportM3u,
              onPressed: rows.isEmpty
                  ? null
                  : () => _exportAsM3u8(context, _title(l10n), _savedRows),
            )
          else if (_isGenre)
            HeaderCircleButton(
              icon: Icons.playlist_add,
              tooltip: l10n.homeMixSaveAsPlaylist,
              onPressed: rows.isEmpty
                  ? null
                  : () => showAddTracksToPlaylistSheet(
                      Navigator.of(context, rootNavigator: true).context,
                      ref,
                      [for (final row in rows) row.track],
                      playlistNamePrefill: _title(l10n),
                    ),
            )
          else
            HeaderMoreButton(groups: moreMenu),
      ],
      actions: editing || selecting || rows.isEmpty
          ? null
          : _buildPlayActions(context, rows),
      moreMenu: moreMenu,
    );
  }

  /// The header art: a playlist's custom or first song cover, a heart tile
  /// for Loved, and a collage of a genre's songs.
  _HeaderArt _headerArt(BuildContext context, List<CollectionRow> rows) {
    final colorScheme = Theme.of(context).colorScheme;
    final cacheWidth = coverCacheWidthForViewport(context);
    Widget tile(IconData icon, double size) => Container(
      color: colorScheme.surfaceContainerHighest,
      child: Icon(icon, size: size, color: colorScheme.onSurfaceVariant),
    );

    switch (widget.kind) {
      case CollectionDetailKind.loved:
        return _HeaderArt(
          background: tile(Icons.favorite, 80),
          coverBuilder: (_, _) => tile(Icons.favorite, 48),
        );
      case CollectionDetailKind.genre:
        final items = [
          for (final row in rows)
            if (row.library != null) row.library!,
        ];
        return _HeaderArt(
          background: LibraryMixCover(items: items, size: 400),
          coverBuilder: (_, coverSize) =>
              LibraryMixCover(items: items, size: coverSize),
          paletteSource: _genrePalette(items),
          blur: _genrePalette(items) != null,
          // The first cover of the genre, blurred once and cached.
          blurSource: localCoverBlurSource(_genrePalette(items)),
        );
      case CollectionDetailKind.playlist:
        final customCover = _playlist?.coverImagePath;
        final hasCustomCover = customCover != null && customCover.isNotEmpty;
        final rawCoverUrl = _firstRawCoverUrl(rows);
        // A stored file path can name a file this device does not have, so the
        // Library answers every cover that is not a web address.
        final coverUrl = collectionCoverIsPortable(rawCoverUrl)
            ? rawCoverUrl
            : ref
                  .watch(
                    localLibraryFirstCoverProvider(_coverBatchRequest(rows)),
                  )
                  .maybeWhen(data: (cover) => cover, orElse: () => rawCoverUrl);
        Widget image(String url, double? size) => LocalCoverImage(
          url: url,
          fit: BoxFit.cover,
          width: size,
          height: size,
          localCacheWidth: cacheWidth,
          fadeInDuration: Duration.zero,
          placeholder: (_) => Container(color: colorScheme.surface),
        );
        final custom = hasCustomCover ? customCover : null;
        final url = coverUrl;
        final Widget background = custom != null
            ? image(custom, null)
            : url != null
            ? image(url, null)
            : tile(Icons.queue_music, 80);
        return _HeaderArt(
          background: background,
          coverBuilder: (context, coverSize) {
            if (custom != null) {
              return Image.file(
                File(custom),
                fit: BoxFit.cover,
                width: coverSize,
                height: coverSize,
                cacheWidth: cacheWidth,
                gaplessPlayback: true,
                errorBuilder: (_, _, _) => tile(Icons.queue_music, 48),
              );
            }
            if (url != null) return image(url, coverSize);
            return tile(Icons.queue_music, 48);
          },
          paletteSource: url,
          blur: custom != null || url != null,
          blurSource: localCoverBlurSource(custom ?? url),
        );
    }
  }

  /// The first genre song that names a file or address, for the header colors.
  String? _genrePalette(List<UnifiedLibraryItem> items) {
    for (final item in items) {
      final source = item.localCoverPath ?? item.coverUrl;
      if (source != null && source.trim().isNotEmpty) return source;
    }
    return null;
  }

  /// Shuffle and Play, with the Album page's Play label and pause toggle.
  Widget _buildPlayActions(BuildContext context, List<CollectionRow> rows) {
    final l10n = context.l10n;
    return Consumer(
      builder: (context, ref, _) {
        final isCurrent = _isGenre
            ? ref.watch(
                currentMediaItemProvider.select(
                  (state) => _items(rows).any(
                    (item) => mediaItemPlaysRow(state.value, _rowKey(item)),
                  ),
                ),
              )
            : ref.watch(
                currentTrackIdentityProvider.select(
                  (state) =>
                      !state.isLoading &&
                      rows.any(
                        (row) =>
                            state.value?.matches(catalogTrackKey(row.track)) ??
                            false,
                      ),
                ),
              );
        final isPlaying = isCurrent && ref.watch(playbackPlayingProvider);
        return AlbumPlayActions(
          isPlaying: isPlaying,
          playLabel: isPlaying ? l10n.actionPause : l10n.tooltipPlay,
          shuffleTooltip: l10n.actionShuffle,
          onPlay: isCurrent
              ? () => ref
                    .read(musicPlayerControllerProvider)
                    .togglePlayPause(isPlaying)
              : () => _playAll(rows),
          onShuffle: () => _shuffleAll(rows),
        );
      },
    );
  }

  List<List<ContextMenuAction>> _moreMenuGroups(
    BuildContext context,
    List<CollectionRow> rows,
  ) {
    final l10n = context.l10n;
    final title = _title(l10n);
    if (_isGenre) {
      return [
        [
          ContextMenuAction(
            icon: Icons.playlist_add,
            label: l10n.homeMixSaveAsPlaylist,
            onSelected: () => showAddTracksToPlaylistSheet(
              Navigator.of(context, rootNavigator: true).context,
              ref,
              [for (final row in rows) row.track],
              playlistNamePrefill: title,
            ),
          ),
        ],
      ];
    }
    final isPlaylist = widget.kind == CollectionDetailKind.playlist;
    final hasCustomCover = _playlist?.coverImagePath?.isNotEmpty ?? false;
    return [
      [
        if (rows.isNotEmpty)
          ContextMenuAction(
            icon: Icons.ios_share,
            label: l10n.collectionExportM3u,
            onSelected: () => _exportAsM3u8(context, title, _savedRows),
          ),
      ],
      [
        if (isPlaylist) ...[
          ContextMenuAction(
            icon: Icons.edit_outlined,
            label: l10n.collectionRenamePlaylist,
            onSelected: () => _renamePlaylist(context, title),
          ),
          ContextMenuAction(
            icon: Icons.image_outlined,
            label: l10n.collectionPlaylistChangeCover,
            onSelected: _pickCoverImage,
          ),
        ],
      ],
      if (isPlaylist && hasCustomCover)
        [
          ContextMenuAction(
            icon: Icons.hide_image_outlined,
            label: l10n.collectionPlaylistRemoveCover,
            onSelected: _removeCoverImage,
          ),
        ],
      if (isPlaylist)
        [
          ContextMenuAction(
            icon: Icons.delete_outline,
            label: l10n.collectionDeletePlaylist,
            destructive: true,
            onSelected: () => _deletePlaylist(context),
          ),
        ],
    ];
  }

  // ------------------------------------------------------------- playback

  static PlaybackRowKey _rowKey(UnifiedLibraryItem item) =>
      (id: item.localItem?.id ?? item.id, path: item.filePath);

  List<UnifiedLibraryItem> _items(List<CollectionRow> rows) => [
    for (final row in rows)
      if (row.library != null) row.library!,
  ];

  List<Track> _tracks(List<CollectionRow> rows) => [
    for (final row in rows) row.track,
  ];

  Future<void> _playAll(List<CollectionRow> rows) => _isGenre
      ? _playLibrary(_items(rows), 0)
      : _playTracks(_tracks(rows), shuffle: false);

  Future<void> _shuffleAll(List<CollectionRow> rows) async {
    if (_isGenre) return _shuffleLibrary(_items(rows));
    final tracks = _tracks(rows);
    if (tracks.isEmpty) return;
    await _playTracks(
      tracks,
      startIndex: Random().nextInt(tracks.length),
      shuffle: true,
    );
  }

  /// Activates the row at [index] of [rows] on desktop: a Library song plays
  /// alone, and a row without a file opens its song menu.
  void _activate(List<CollectionRow> rows, int index) {
    if (_isGenre) {
      _playLibrary(_items(rows), index);
      return;
    }
    playLocalIfAvailable(context, ref, rows[index].track).then((played) {
      if (!played && mounted) {
        showSongMenu(context, ref, track: rows[index].track);
      }
    });
  }

  /// Queues [tracks] from [startIndex]. [shuffle] sets shuffle first, when given.
  Future<void> _playTracks(
    List<Track> tracks, {
    int startIndex = 0,
    bool? shuffle,
  }) async {
    if (tracks.isEmpty) return;
    try {
      if (shuffle != null) {
        await ref.read(musicPlayerControllerProvider).setShuffle(shuffle);
      }
      await ref
          .read(playbackProvider.notifier)
          .playTrackList(tracks, startIndex: startIndex);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(context.l10n.snackbarCannotOpenFile('$e'))),
      );
    }
  }

  /// The Library songs whose files can still play, each with its position in
  /// [items].
  Future<List<(int, PlayableMedia)>> _playable(
    List<UnifiedLibraryItem> items,
  ) async {
    final present = await Future.wait([
      for (final item in items) fileExists(item.filePath),
    ]);
    return [
      for (final (index, item) in items.indexed)
        if (present[index])
          if (item.localItem case final local?)
            (index, playableFromLocal(local)),
    ];
  }

  /// Plays the songs as the queue, from the song at [index] or the nearest
  /// playable one after it.
  Future<void> _playLibrary(List<UnifiedLibraryItem> items, int index) async {
    final songs = await _playable(items);
    if (songs.isEmpty || !mounted) return;
    final start = songs.indexWhere((song) => song.$1 >= index);
    final controller = ref.read(musicPlayerControllerProvider);
    await controller.setShuffle(false);
    await controller.playAll([
      for (final song in songs) song.$2,
    ], initialIndex: max(0, start));
  }

  Future<void> _shuffleLibrary(List<UnifiedLibraryItem> items) async {
    final songs = await _playable(items);
    if (songs.isEmpty || !mounted) return;
    final controller = ref.read(musicPlayerControllerProvider);
    await controller.playAll([
      for (final song in songs) song.$2,
    ], initialIndex: Random().nextInt(songs.length));
    await controller.setShuffle(true);
  }

  /// Plays the row's song from its place in the list, or toggles it when it
  /// is the current one.
  VoidCallback _playRow(
    WidgetRef ref,
    List<CollectionRow> rows,
    int index,
    bool isCurrent,
    bool isPlaying,
  ) {
    if (isCurrent) {
      return () =>
          ref.read(musicPlayerControllerProvider).togglePlayPause(isPlaying);
    }
    if (_isGenre) return () => _playLibrary(_items(rows), index);
    return () => _playTracks(_tracks(rows), startIndex: index);
  }

  // ----------------------------------------------------------------- rows

  Widget _buildRow(BuildContext context, List<CollectionRow> rows, int index) {
    final row = rows[index];
    final track = row.track;
    final item = row.library;
    return Consumer(
      builder: (context, ref, _) {
        final playback = item != null
            ? ref.watch(mediaItemPlaybackUiProvider(_rowKey(item)))
            : ref.watch(catalogTrackPlaybackUiProvider(catalogTrackKey(track)));
        // Songs with a local file play; the rest open their song menu.
        final hasFile = item != null || _inLocalLibrary(ref, track);
        final VoidCallback play = _playRow(
          ref,
          rows,
          index,
          playback.isCurrent,
          playback.isPlaying,
        );
        Future<void> openMenu() => showSongMenu(
          context,
          ref,
          track: track,
          filePath: item?.filePath,
          localItem: item?.localItem,
        );
        // A local song that cannot play (a CUE virtual track or a missing
        // file) opens its song menu instead.
        final VoidCallback playOrMenu = item == null
            ? play
            : () async {
                if (await songFileCanPlay(item.filePath)) {
                  play();
                } else {
                  await openMenu();
                }
              };
        return TrackCard(
          style: TrackCardStyle.flat,
          queueSwipe: item != null
              ? libraryItemQueueSwipe(ref, item)
              : hasFile
              ? mediaQueueSwipe(
                  ref,
                  () => resolveCollectionTrackFiles(
                    ref.read(playbackProvider.notifier),
                    [track],
                  ),
                )
              : null,
          isSelectionMode: isSelectionMode,
          isSelected: selectedIds.contains(row.key),
          isCurrent: playback.isCurrent,
          isPlaying: playback.isPlaying,
          leadingArtwork: true,
          leading: _rowCover(context, ref, row),
          title: track.name,
          subtitle: Text(
            track.artistName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          // A Library song keeps its explicit flag, which its Track does not.
          isExplicit: item?.isExplicit ?? track.isExplicit,
          onPlay: hasFile ? playOrMenu : null,
          menu: isSelectionMode
              ? null
              : SongMenuButton(
                  track: track,
                  filePath: item?.filePath,
                  localItem: item?.localItem,
                ),
          onTap: isSelectionMode
              ? () => toggleSelection(row.key)
              : hasFile
              ? playOrMenu
              : openMenu,
          onLongPress: isSelectionMode
              ? null
              : () => enterSelectionMode(row.key),
        );
      },
    );
  }

  /// The song's cover, the leading artwork of every row.
  Widget _rowCover(BuildContext context, WidgetRef ref, CollectionRow row) {
    final tokens = context.tokens;
    final size = tokens.coverCompact;
    final item = row.library;
    if (item != null) {
      return ClipRRect(
        borderRadius: tokens.borderRadiusThumb,
        child: LibraryItemArtwork(item: item, size: size),
      );
    }
    final rawCoverUrl = _resolveRawCoverUrl(row.track);
    final cover = collectionCoverIsPortable(rawCoverUrl)
        ? rawCoverUrl
        : ref
              .watch(
                localLibraryCoverProvider(
                  LocalLibraryCoverRequest.of(row.track),
                ),
              )
              .maybeWhen(data: (cover) => cover, orElse: () => rawCoverUrl);
    return ClipRRect(
      borderRadius: tokens.borderRadiusThumb,
      child: cover != null && cover.isNotEmpty
          ? LocalCoverImage(
              url: cover,
              width: size,
              height: size,
              localCacheWidth: (size * 2).toInt(),
              fadeInDuration: const Duration(milliseconds: 180),
              fadeOutDuration: const Duration(milliseconds: 90),
              placeholder: (_) => TrackCoverPlaceholder(size: size),
            )
          : TrackCoverPlaceholder(size: size),
    );
  }

  /// Whether the song's file is in the local library.
  bool _inLocalLibrary(WidgetRef ref, Track track) {
    final showLocalLibraryIndicator = ref.watch(
      settingsProvider.select(
        (s) => s.localLibraryEnabled && s.localLibraryShowDuplicates,
      ),
    );
    if (!showLocalLibraryIndicator) return false;
    return ref.watch(
      localLibraryProvider.select((state) {
        final isrc = track.isrc?.trim();
        return state.existsInLibrary(
          isrc: isrc,
          trackName: track.name,
          artistName: track.artistName,
        );
      }),
    );
  }

  // -------------------------------------------------------------- desktop

  /// The desktop table builds every row at once, so one lookup answers the
  /// covers the rows cannot load from their own stored path.
  void _lookUpDesktopCovers(List<CollectionRow> rows) {
    if (!isDesktopPlatform ||
        _editingOrder ||
        _isGenre ||
        !rows.any(
          (row) => !collectionCoverIsPortable(_resolveRawCoverUrl(row.track)),
        )) {
      return;
    }
    final covers = ref
        .watch(localLibraryCoversProvider(_coverBatchRequest(rows)))
        .maybeWhen(data: (covers) => covers, orElse: () => null);
    if (covers != null && covers.length == rows.length) {
      _desktopCovers = {
        for (var i = 0; i < rows.length; i++) rows[i].key: covers[i],
      };
    }
  }

  Widget _buildDesktopTable(BuildContext context, List<CollectionRow> rows) {
    if (_isGenre) return _buildGenreTable(context, rows);
    final tracks = _tracks(rows);
    return TrackListTable(
      tracks: tracks,
      ids: [for (final row in rows) row.key],
      selectedIds: selectedIds,
      onSelect: applyDesktopClick,
      onActivate: (index) => _activate(rows, index),
      onContextMenu: (index, position) => showSelectionMenu(
        rows[index].key,
        () => _showEntryMenu(context, rows, index, position),
      ),
      showArtwork: true,
      showAlbum: true,
      covers: [
        for (final row in rows)
          _desktopCovers.containsKey(row.key)
              ? _desktopCovers[row.key]
              : row.track.coverUrl,
      ],
    );
  }

  /// The genre's desktop table: a click selects, a double click plays from
  /// the song, a right click opens the Library song menu.
  Widget _buildGenreTable(BuildContext context, List<CollectionRow> rows) {
    final items = _items(rows);
    final ids = [for (final row in rows) row.key];
    return SliverPadding(
      padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
      sliver: SliverTrackTable(
        rows: [
          for (final (index, item) in items.indexed)
            TrackTableRow(
              id: item.id,
              number: index + 1,
              title: item.trackName,
              artist: item.artistName,
              album: item.albumName,
              titleTrailing: item.isExplicit ? const ExplicitChip() : null,
              duration: switch (item.localItem?.duration) {
                final seconds? when seconds > 0 => Duration(seconds: seconds),
                _ => null,
              },
              leading: ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LibraryItemArtwork(item: item, size: 32, iconSize: 16),
              ),
              playback: mediaItemPlaybackUiProvider(_rowKey(item)),
            ),
        ],
        columns: const [
          TrackTableColumn.title,
          TrackTableColumn.artist,
          TrackTableColumn.album,
          TrackTableColumn.duration,
        ],
        selectedIds: selectedIds,
        onSelect: applyDesktopClick,
        onActivate: (id) => _activate(rows, ids.indexOf(id)),
        onContextMenu: (id, position) {
          final index = ids.indexOf(id);
          if (index < 0) return;
          showSelectionMenu(
            id,
            () => showContextMenu(
              context,
              position: position,
              groups: libraryTrackMenuGroups(
                context,
                ref,
                items: _selectedItems(rows),
                onPlay: () => _activate(rows, index),
                onDone: exitSelectionMode,
                onDelete: () => _deleteSelected(rows),
              ),
            ),
          );
        },
      ),
    );
  }

  /// The right-click menu of a playlist or Loved row: the selection bar's
  /// actions for the selection the row joins, after the row's own Play, which
  /// ends the selection.
  Future<bool> _showEntryMenu(
    BuildContext context,
    List<CollectionRow> rows,
    int index,
    Offset position,
  ) async {
    final l10n = context.l10n;
    final count = selectedIds.length;
    final isPlaylist = widget.kind == CollectionDetailKind.playlist;
    final request = _selectedTrackFiles(rows);
    final media = await ref
        .read(collectionTrackFilesProvider(request).future)
        .catchError((Object _) => <PlayableMedia>[]);
    if (!mounted || !context.mounted || request != _selectedTrackFiles(rows)) {
      return false;
    }
    final localCount = media
        .where((item) => !item.isNetwork)
        .map((item) => item.source)
        .toSet()
        .length;
    return showContextMenu(
      context,
      position: position,
      groups: [
        [
          if (media.isNotEmpty)
            for (final next in [true, false])
              ContextMenuAction(
                icon: next ? Icons.playlist_play : Icons.queue_music,
                label: next ? l10n.trackPlayNext : l10n.trackAddToQueue,
                onSelected: () => _playSelectedFiles(media, playNext: next),
              ),
          ContextMenuAction(
            icon: Icons.playlist_add,
            label: '${l10n.collectionAddToPlaylist} ($count)',
            onSelected: () => _addSelectedToPlaylist(rows),
          ),
        ],
        [
          ContextMenuAction(
            icon: Icons.remove_circle_outline,
            label:
                '${isPlaylist ? l10n.collectionRemoveFromPlaylist : l10n.collectionRemoveFromFolder} ($count)',
            destructive: true,
            onSelected: () => _removeSelected(rows),
          ),
          if (localCount > 0)
            ContextMenuAction(
              icon: Icons.delete_outline,
              label: l10n.selectionDeleteTracksCount(localCount),
              destructive: true,
              onSelected: () => _deleteSelectedFiles(media),
            ),
        ],
      ],
    );
  }

  // ------------------------------------------------------------ selection

  List<UnifiedLibraryItem> _selectedItems(List<CollectionRow> rows) => [
    for (final row in rows)
      if (selectedIds.contains(row.key) && row.library != null) row.library!,
  ];

  CollectionTrackFilesRequest _selectedTrackFiles(List<CollectionRow> rows) =>
      CollectionTrackFilesRequest([
        for (final row in rows)
          if (selectedIds.contains(row.key)) row.track,
      ]);

  Widget _buildSelectionBar(
    BuildContext context,
    List<CollectionRow> rows,
    List<String> ids,
    double bottomPadding,
  ) {
    if (_isGenre) {
      final items = _selectedItems(rows);
      final allSelected = ids.isNotEmpty && selectedIds.length == ids.length;
      return LibraryTrackSelectionBar(
        selectedCount: selectedIds.length,
        allSelected: allSelected,
        onClose: exitSelectionMode,
        onToggleSelectAll: allSelected
            ? exitSelectionMode
            : () => selectAll(ids),
        bottomPadding: bottomPadding,
        playbackActions: LibrarySelectionPlaybackActions(
          items: items,
          onClose: exitSelectionMode,
        ),
        onReplayGain: ({required remove}) => runBatchReplayGain(
          context,
          items,
          remove: remove,
          onExitSelectionMode: exitSelectionMode,
        ),
        onDelete: () => _deleteSelected(rows),
      );
    }
    return _buildCollectionSelectionBar(context, rows, ids, bottomPadding);
  }

  Widget _buildCollectionSelectionBar(
    BuildContext context,
    List<CollectionRow> rows,
    List<String> ids,
    double bottomPadding,
  ) {
    final colorScheme = Theme.of(context).colorScheme;
    final selectedCount = selectedIds.length;
    final request = _selectedTrackFiles(rows);
    final files = selectedCount == 0
        ? const <PlayableMedia>[]
        : ref.watch(collectionTrackFilesProvider(request)).value ??
              const <PlayableMedia>[];
    final localCount = files
        .where((item) => !item.isNetwork)
        .map((item) => item.source)
        .toSet()
        .length;
    final canPlay = files.isNotEmpty && !_trackActionInProgress;
    final deleteLabel = context.l10n.selectionDeleteTracksCount(localCount);
    final isPlaylist = widget.kind == CollectionDetailKind.playlist;

    return buildSelectionBar(
      ids: ids,
      bottomPadding: bottomPadding,
      children: [
        SelectionActionGrid(
          children: [
            SelectionActionButton(
              icon: Icons.play_arrow,
              label: context.l10n.tooltipPlay,
              onPressed: canPlay ? () => _playSelectedFiles(files) : null,
              colorScheme: colorScheme,
            ),
            for (final next in [true, false])
              SelectionActionButton(
                icon: next ? Icons.playlist_play : Icons.queue_music,
                label: next
                    ? context.l10n.trackPlayNext
                    : context.l10n.trackAddToQueue,
                onPressed: canPlay
                    ? () => _playSelectedFiles(files, playNext: next)
                    : null,
                colorScheme: colorScheme,
              ),
            SelectionActionButton(
              icon: Icons.playlist_add,
              label: '${context.l10n.collectionAddToPlaylist} ($selectedCount)',
              onPressed: selectedCount > 0
                  ? () => _addSelectedToPlaylist(rows)
                  : null,
              colorScheme: colorScheme,
            ),
            // Removing entries keeps the files, so it stays a plain action.
            // Desktop keeps file deletion a toolbar button beside it, as in
            // the Library selection bar.
            SelectionActionButton(
              onPressed: selectedCount > 0 ? () => _removeSelected(rows) : null,
              icon: Icons.remove_circle_outline,
              colorScheme: colorScheme,
              label: selectedCount > 0
                  ? '${isPlaylist ? context.l10n.collectionRemoveFromPlaylist : context.l10n.collectionRemoveFromFolder} ($selectedCount)'
                  : isPlaylist
                  ? context.l10n.collectionRemoveFromPlaylist
                  : context.l10n.collectionRemoveFromFolder,
            ),
            if (isDesktopPlatform && localCount > 0)
              SelectionActionButton(
                icon: Icons.delete_outline,
                label: deleteLabel,
                colorScheme: colorScheme,
                destructive: true,
                onPressed: _trackActionInProgress
                    ? null
                    : () => _deleteSelectedFiles(files),
              ),
          ],
        ),
        if (!isDesktopPlatform && localCount > 0) ...[
          const SizedBox(height: 8),
          DestructiveSelectionButton(
            count: _trackActionInProgress ? 0 : localCount,
            label: deleteLabel,
            onPressed: () => _deleteSelectedFiles(files),
          ),
        ],
      ],
    );
  }

  /// The selection's "delete": a genre deletes the songs' files, the others
  /// take the songs out of the collection.
  Future<void> _deleteSelected(List<CollectionRow> rows) async {
    if (_isGenre) return _deleteSelectedLibrary(rows);
    return _removeSelected(rows);
  }

  /// Takes the selected songs out of the playlist or Loved. Their files stay.
  Future<void> _removeSelected(List<CollectionRow> rows) async {
    final keysToRemove = selectedIds.toSet();
    if (keysToRemove.isEmpty) return;

    final count = keysToRemove.length;
    final notifier = ref.read(libraryCollectionsProvider.notifier);
    switch (widget.kind) {
      case CollectionDetailKind.loved:
        await notifier.removeLovedTracks(keysToRemove);
      case CollectionDetailKind.playlist:
        final playlistId = widget.playlistId;
        if (playlistId != null) {
          await notifier.removeTracksFromPlaylist(playlistId, keysToRemove);
        }
      case CollectionDetailKind.genre:
        return;
    }

    exitSelectionMode();

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.selectionSelected(count))),
    );
  }

  /// Deletes the selected genre songs' files, and drops those gone from the
  /// genre.
  Future<void> _deleteSelectedLibrary(List<CollectionRow> rows) async {
    final selected = _selectedItems(rows);
    await deleteLibraryTracks(
      context,
      ref,
      selected,
      isActive: () => mounted,
      onComplete: exitSelectionMode,
    );
    final present = await Future.wait([
      for (final item in selected) fileExists(item.filePath),
    ]);
    final gone = {
      for (final (index, item) in selected.indexed)
        if (!present[index]) item.id,
    };
    if (gone.isEmpty || !mounted) return;
    setState(() {
      _genreItems = [
        for (final item in _genreItems ?? const <UnifiedLibraryItem>[])
          if (!gone.contains(item.id)) item,
      ];
    });
  }

  void _addSelectedToPlaylist(List<CollectionRow> rows) {
    final selectedTracks = [
      for (final row in rows)
        if (selectedIds.contains(row.key)) row.track,
    ];
    if (selectedTracks.isEmpty) return;
    showAddTracksToPlaylistSheet(context, ref, selectedTracks);
  }

  Future<void> _playSelectedFiles(
    List<PlayableMedia> media, {
    bool? playNext,
  }) async {
    if (media.isEmpty || _trackActionInProgress) return;
    setState(() => _trackActionInProgress = true);
    try {
      if (playNext == null) {
        await ref
            .read(playbackProvider.notifier)
            .playMediaQueue(media, startIndex: 0);
      } else {
        await ref
            .read(musicPlayerControllerProvider)
            .enqueueMedia(media, playNext: playNext);
      }
      if (mounted) exitSelectionMode();
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    } finally {
      if (mounted) setState(() => _trackActionInProgress = false);
    }
  }

  Future<void> _deleteSelectedFiles(List<PlayableMedia> media) async {
    final paths = {
      for (final item in media)
        if (!item.isNetwork && !isCueVirtualPath(item.source)) item.source,
    }.toList();
    if (paths.isEmpty || _trackActionInProgress) return;
    final container = ProviderScope.containerOf(context, listen: false);
    setState(() => _trackActionInProgress = true);
    try {
      await confirmAndDeleteTracks(
        context: context,
        ids: paths,
        message: context.l10n.collectionDeleteFilesMessage(paths.length),
        deleteItem: deleteFile,
        persistDeletedItems: (deleted) =>
            removeDeletedLibraryFileEntries(container, deleted),
        onExitSelectionMode: () {
          if (mounted) exitSelectionMode();
        },
      );
    } catch (error) {
      if (mounted) showCannotOpenFileSnackBar(context, error);
    } finally {
      if (mounted) setState(() => _trackActionInProgress = false);
    }
  }

  // ----------------------------------------------------------- custom order

  void _editOrder(List<CollectionRow> saved) {
    exitSelectionMode();
    setState(() {
      _orderDraft = [...saved];
      _orderBefore = [for (final row in saved) row.key];
      _draggingOrder = false;
    });
  }

  bool get _orderChanged =>
      _orderDraft?.indexed.any(
        (item) => item.$2.key != _orderBefore[item.$1],
      ) ??
      false;

  Future<void> _cancelOrder() async {
    if (_savingOrder || _confirmingDiscard) return;
    _confirmingDiscard = true;
    try {
      if (_orderChanged && !await showDiscardChangesDialog(context)) return;
      if (mounted) {
        setState(() {
          _orderDraft = null;
          _draggingOrder = false;
        });
      }
    } finally {
      _confirmingDiscard = false;
    }
  }

  Future<void> _saveOrder() async {
    final draft = _orderDraft;
    if (draft == null || _savingOrder || _draggingOrder) return;
    setState(() => _savingOrder = true);
    try {
      final keys = [for (final row in draft) row.key];
      final notifier = ref.read(libraryCollectionsProvider.notifier);
      final saved = widget.kind == CollectionDetailKind.playlist
          ? await notifier.reorderPlaylistTracks(widget.playlistId!, keys)
          : await notifier.reorderSavedTracks(
              LibraryTracksFolderMode.loved,
              keys,
            );
      if (!mounted) return;
      // The page shows the order that was just saved.
      ref
          .read(collectionTrackSortsProvider.notifier)
          .setSort(_sortKey, CollectionTrackSort.custom);
      setState(() => _orderDraft = null);
      showAppSnackBar(
        context,
        content: Text(
          saved
              ? context.l10n.collectionOrderSaved
              : context.l10n.collectionOrderChanged,
        ),
      );
    } on Object {
      if (mounted) {
        showAppSnackBar(
          context,
          content: Text(context.l10n.collectionOrderSaveFailed),
        );
      }
    } finally {
      if (mounted) setState(() => _savingOrder = false);
    }
  }

  // ------------------------------------------------------ playlist actions

  Future<void> _renamePlaylist(BuildContext context, String currentName) async {
    final playlistId = widget.playlistId ?? _playlist?.id;
    if (playlistId == null || playlistId.isEmpty) return;
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

  Future<void> _deletePlaylist(BuildContext context) async {
    final playlistId = widget.playlistId;
    if (playlistId == null) return;
    final l10n = context.l10n;
    final confirmed = await showAppConfirmDialog(
      context,
      title: l10n.collectionDeletePlaylist,
      message: l10n.collectionDeletePlaylistsMessage(1),
      confirmLabel: l10n.dialogDelete,
      destructive: true,
      cancelIsDefault: true,
    );
    if (!confirmed || !context.mounted) return;
    await ref.read(libraryCollectionsProvider.notifier).deletePlaylists({
      playlistId,
    });
    if (context.mounted) Navigator.pop(context);
  }

  Future<void> _pickCoverImage() async {
    final playlistId = widget.playlistId;
    if (playlistId == null) return;

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

  Future<void> _removeCoverImage() async {
    final playlistId = widget.playlistId;
    if (playlistId == null) return;

    await ref
        .read(libraryCollectionsProvider.notifier)
        .removePlaylistCover(playlistId);
  }

  /// Exports the collection as an .m3u8 shared via the system sheet. Tracks
  /// without a resolvable local file are skipped; SAF entries are written
  /// relative to the download tree root.
  Future<void> _exportAsM3u8(
    BuildContext context,
    String title,
    List<CollectionRow> rows,
  ) async {
    final l10n = context.l10n;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final tracks = _tracks(rows);
      final paths = await ref
          .read(playbackProvider.notifier)
          .resolveTrackFilePaths(tracks);

      final exportEntries = <M3uExportEntry>[];
      for (var i = 0; i < tracks.length; i++) {
        final path = paths[i];
        if (path == null || isCueVirtualPath(path)) continue;
        final entryPath = M3uPlaylistService.exportPathFor(path);
        if (entryPath == null) continue;
        exportEntries.add(M3uExportEntry(track: tracks[i], path: entryPath));
      }

      if (exportEntries.isEmpty) {
        messenger.showSnackBar(
          SnackBar(content: Text(l10n.collectionExportM3uNone)),
        );
        return;
      }

      final file = await M3uPlaylistService.writeExportEntries(
        title,
        exportEntries,
      );
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            l10n.collectionExportM3uDone(exportEntries.length, tracks.length),
          ),
        ),
      );
      await SharePlus.instance.share(
        ShareParams(files: [XFile(file.path)], text: title),
      );
    } catch (_) {
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.collectionExportM3uFailed)),
      );
    }
  }

  LocalLibraryCoverBatchRequest _coverBatchRequest(List<CollectionRow> rows) =>
      LocalLibraryCoverBatchRequest([
        for (final row in rows) LocalLibraryCoverRequest.of(row.track),
      ]);
}

/// The header art the page's kind shows, with the colors it is read from.
class _HeaderArt {
  const _HeaderArt({
    required this.background,
    required this.coverBuilder,
    this.paletteSource,
    this.blur = false,
    this.blurSource,
  });

  final Widget background;
  final Widget Function(BuildContext context, double coverSize) coverBuilder;
  final String? paletteSource;
  final bool blur;

  /// The single cover the background shows, for its blurred copy. Null for a
  /// composite (mix-style) or icon background, which stays sharp.
  final ImageProvider? blurSource;
}

String? _resolveRawCoverUrl(Track track) =>
    LocalLibraryCoverRequest.of(track).cover;

String? _firstRawCoverUrl(List<CollectionRow> rows) {
  for (final row in rows) {
    final cover = _resolveRawCoverUrl(row.track);
    if (cover != null && cover.isNotEmpty) return cover;
  }
  return null;
}

/// The placeholder shown when a collection has no tracks.
class _EmptyCollectionState extends StatelessWidget {
  const _EmptyCollectionState({required this.title, required this.subtitle});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.folder_open,
              size: 60,
              color: colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              title,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
