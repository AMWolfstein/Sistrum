import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/widgets/in_library_badge.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:flutter/material.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/extension_provider.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/cover_art_utils.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/controllers/collection_order_editing.dart';
import 'package:sistrum/controllers/library_tracks_folder_data.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/widgets/collection_reorder_sliver.dart';
import 'package:sistrum/widgets/collection_track_actions.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';

export 'package:sistrum/models/library_collections.dart'
    show LibraryTracksFolderMode;

/// The Wishlist: saved songs to download. Library playlists and Loved songs
/// use the playlist page.
class LibraryTracksFolderScreen extends ConsumerStatefulWidget {
  const LibraryTracksFolderScreen({super.key});

  @override
  ConsumerState<LibraryTracksFolderScreen> createState() =>
      _LibraryTracksFolderScreenState();
}

class _LibraryTracksFolderScreenState
    extends ConsumerState<LibraryTracksFolderScreen>
    with
        SelectionModeMixin<LibraryTracksFolderScreen>,
        CollapsingHeaderScrollMixin<LibraryTracksFolderScreen>,
        CollectionOrderEditing<LibraryTracksFolderScreen>,
        CollectionTrackActions<LibraryTracksFolderScreen> {
  final _folderData = LibraryTracksFolderData();

  @override
  Future<bool> saveOrderKeys(List<String> orderedKeys) => ref
      .read(libraryCollectionsProvider.notifier)
      .reorderSavedTracks(LibraryTracksFolderMode.wishlist, orderedKeys);

  @override
  void leaveSelectionAfterTrackAction() => exitSelectionMode();

  Widget _selectionActions(List<CollectionTrackEntry> entries) {
    final tracks = [
      for (final entry in entries)
        if (selectedIds.contains(entry.key)) entry.track,
    ];
    return CollectionTrackSelectionActions(
      media: selectedMedia(tracks),
      selectedCount: tracks.length,
      showPlayer: ref.watch(playerUiEnabledProvider),
      inProgress: trackActionInProgress,
      onPlay: playSelectedMedia,
      onAddToPlaylist: () => showAddTracksToPlaylistSheet(context, ref, tracks),
      removeLabel: context.l10n.collectionRemoveFromFolder,
      onRemove: _removeSelected,
      onDelete: deleteSelectedMedia,
      onDownload: () => _downloadSelected(entries),
    );
  }

  void _startEditOrder(List<CollectionTrackEntry> entries) {
    exitSelectionMode();
    editOrder(entries);
  }

  Future<void> _removeSelected() async {
    final keysToRemove = selectedIds.toSet();
    if (keysToRemove.isEmpty) return;

    final count = keysToRemove.length;
    await ref
        .read(libraryCollectionsProvider.notifier)
        .removeWishlistTracks(keysToRemove);

    exitSelectionMode();

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.selectionSelected(count))),
    );
  }

  void _downloadSelected(List<CollectionTrackEntry> entries) {
    final settings = ref.read(settingsProvider);
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
    final queueNotifier = ref.read(downloadQueueProvider.notifier);
    final tracks = entries
        .where((entry) => selectedIds.contains(entry.key))
        .map((entry) => entry.track)
        .toList(growable: false);
    queueNotifier.addIndividualTracksToQueue(tracks, service);
    final count = tracks.length;

    exitSelectionMode();

    if (!mounted || count == 0) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(context.l10n.selectionSelected(count))),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    ref.watch(localLibraryProvider.select((s) => s.loadedIndexVersion));
    final stored = ref.watch(
      libraryCollectionsProvider.select((state) => state.wishlist),
    );
    final entries = orderDraft ?? stored;
    _folderData.update(entries);
    pruneSelection(_folderData.keys);

    final bottomPadding = isSelectionMode
        ? MediaQuery.paddingOf(context).bottom
        : 0.0;

    final folderTracks = _folderData.tracks;
    final existingHistoryKeys = editingOrder
        ? const <String>{}
        : ref.watch(
            downloadHistoryVisibleBatchExistsProvider(
              _folderData.historyRequest,
            ),
          );

    final inHistory = _folderData.inHistory(existingHistoryKeys);
    final ids = [for (final entry in entries) entry.key];
    final scaffold = CollectionScaffold(
      scrollController: scrollController,
      isSelectionMode: showsSelectionBar,
      onExitSelectionMode: exitSelectionMode,
      selectionBar: buildSelectionBar(
        ids: ids,
        bottomPadding: bottomPadding,
        children: [_selectionActions(entries)],
      ),
      appBar: _buildAppBar(context, colorScheme, entries),
      slivers: [
        if (editingOrder)
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                16 + wideListInset(context),
                16,
                16 + wideListInset(context),
                12,
              ),
              child: Text(context.l10n.playlistEditOrderDescription),
            ),
          ),
        if (entries.isEmpty)
          SliverFillRemaining(
            hasScrollBody: false,
            child: CollectionEmptyState(
              title: context.l10n.collectionWishlistEmptyTitle,
              subtitle: context.l10n.collectionWishlistEmptySubtitle,
            ),
          )
        else if (editingOrder)
          CollectionReorderSliver(
            entries: entries,
            enabled: !savingOrder,
            onDraggingChanged: setDraggingOrder,
            onReorder: moveDraftEntry,
          )
        else
          SliverPadding(
            padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
            sliver: SliverList(
              delegate: SliverChildBuilderDelegate((context, index) {
                final entry = entries[index];
                final isSelected = selectedIds.contains(entry.key);
                final isInHistory = inHistory[index];
                return KeyedSubtree(
                  key: ValueKey(entry.key),
                  child: StaggeredListItem(
                    index: index,
                    child: _CollectionTrackTile(
                      entry: entry,
                      folderTracks: folderTracks,
                      trackIndex: index,
                      isInHistory: isInHistory,
                      isSelectionMode: isSelectionMode,
                      isSelected: isSelected,
                      onTap: isSelectionMode
                          ? () => toggleSelection(entry.key)
                          : null,
                      onLongPress: isSelectionMode
                          ? null
                          : () => enterSelectionMode(entry.key),
                    ),
                  ),
                );
              }, childCount: entries.length),
            ),
          ),
      ],
    );
    return PopScope(
      canPop: !editingOrder,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && editingOrder) cancelOrder();
      },
      child: scaffold,
    );
  }

  Widget _buildAppBar(
    BuildContext context,
    ColorScheme colorScheme,
    List<CollectionTrackEntry> entries,
  ) {
    final title = context.l10n.collectionWishlist;
    final rawCoverUrl = firstRawCollectionCover(
      entries.map((entry) => entry.track),
    );
    final localCoverUrl = rawCoverUrl == null && entries.isNotEmpty
        ? ref
              .watch(
                localLibraryFirstCoverProvider(
                  collectionCoverBatchRequest(
                    entries.map((entry) => entry.track),
                  ),
                ),
              )
              .maybeWhen(data: (cover) => cover, orElse: () => null)
        : null;
    final coverUrl = rawCoverUrl ?? localCoverUrl;
    final hasCoverUrl = coverUrl != null;
    // is connected.
    final dpr = MediaQuery.devicePixelRatioOf(context);
    final cacheWidth = (MediaQuery.sizeOf(context).width * dpr).round().clamp(
      320,
      2048,
    );
    final coverFallback = Container(
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        Icons.bookmark,
        size: 80,
        color: colorScheme.onSurfaceVariant,
      ),
    );
    Widget squarePlaceholder() => Container(
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        Icons.bookmark,
        size: 48,
        color: colorScheme.onSurfaceVariant,
      ),
    );

    final Widget background = hasCoverUrl
        ? LocalOrNetworkCoverImage(
            url: coverUrl,
            fit: BoxFit.cover,
            localCacheWidth: cacheWidth,
            networkCacheWidth: cacheWidth,
            fadeInDuration: Duration.zero,
            urlTransform: (u) => highResCoverUrl(u) ?? u,
            placeholder: (_) => Container(color: colorScheme.surface),
          )
        : coverFallback;

    return AlbumDetailHeader(
      title: title,
      appBarTitle: editingOrder
          ? context.l10n.playlistEditOrder
          : isSelectionMode
          ? context.l10n.selectionSelected(selectedIds.length)
          : title,
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: background,
      coverBuilder: (context, coverSize) {
        if (hasCoverUrl) {
          return LocalOrNetworkCoverImage(
            url: coverUrl,
            fit: BoxFit.cover,
            width: coverSize,
            height: coverSize,
            localCacheWidth: cacheWidth,
            networkCacheWidth: cacheWidth,
            urlTransform: (u) => highResCoverUrl(u) ?? u,
            placeholder: (_) => squarePlaceholder(),
          );
        }
        return squarePlaceholder();
      },
      meta: entries.isNotEmpty
          ? HeaderMetaRow(
              items: [
                HeaderMetaItem(
                  context.l10n.tracksCount(entries.length),
                  icon: Icons.bookmark,
                ),
              ],
            )
          : null,
      // Play is the one wide action, between Download All and Shuffle.
      actions: entries.isNotEmpty && !editingOrder
          ? Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _buildDownloadAllCenterButton(entries),
                const SizedBox(width: 8),
                Flexible(child: _buildPlayAllCenterButton(entries)),
                const SizedBox(width: 8),
                HeaderCircleButton(
                  key: const ValueKey('collection-shuffle'),
                  icon: Icons.shuffle,
                  tooltip: context.l10n.actionShuffle,
                  tonal: true,
                  onPressed: () => _shuffleAll(
                    entries.map((e) => e.track).toList(growable: false),
                  ),
                ),
              ],
            )
          : null,
      appBarActions: [
        if (editingOrder)
          TextButton(
            key: const ValueKey('collection-order-save'),
            onPressed: !orderChanged || savingOrder || draggingOrder
                ? null
                : saveOrder,
            child: Text(context.l10n.dialogSave),
          ),
        if (!editingOrder) ...[
          if (!isSelectionMode && entries.isNotEmpty)
            HeaderCircleButton(
              icon: Icons.ios_share,
              iconSize: 20,
              tooltip: context.l10n.collectionExportM3u,
              onPressed: () => exportCollectionAsM3u8(context, ref, title, [
                for (final entry in entries) entry.track,
              ]),
            ),
          if (!isSelectionMode && entries.length > 1) ...[
            const SizedBox(width: 8),
            HeaderCircleButton(
              key: const ValueKey('collection-edit-order'),
              icon: Icons.swap_vert,
              iconSize: 20,
              tooltip: context.l10n.playlistEditOrder,
              onPressed: () => _startEditOrder(entries),
            ),
          ],
        ],
      ],
      // Centered so the tight leading slot cannot stretch it.
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

  Widget _buildPlayAllCenterButton(List<CollectionTrackEntry> entries) {
    final tracks = entries.map((e) => e.track).toList(growable: false);
    return HeaderFilledButton(
      icon: Icons.play_arrow_rounded,
      label: context.l10n.tooltipPlay,
      onPressed: tracks.isEmpty
          ? null
          : () => playCollectionTracks(context, ref, tracks, shuffle: false),
    );
  }

  Widget _buildDownloadAllCenterButton(List<CollectionTrackEntry> entries) {
    final tracks = entries.map((e) => e.track).toList(growable: false);
    return HeaderCircleButton(
      key: const ValueKey('collection-download-all'),
      icon: Icons.download_rounded,
      tooltip: context.l10n.downloadAllCount(tracks.length),
      tonal: true,
      onPressed: tracks.isEmpty ? null : () => _confirmDownloadAll(tracks),
    );
  }

  Future<void> _shuffleAll(List<Track> tracks) =>
      playCollectionTracks(context, ref, tracks, shuffle: true);

  void _confirmDownloadAll(List<Track> tracks) {
    confirmDownloadAllDialog(
      context,
      tracks.length,
      () => _downloadAll(tracks),
    );
  }

  Future<void> _downloadAll(List<Track> tracks) async {
    await queueTracksSkippingDownloaded(
      context,
      ref,
      tracks,
      artistNameForPicker: context.l10n.collectionWishlist,
      resolveDefaultService: false,
    );
  }
}

class _CollectionTrackTile extends ConsumerWidget {
  final CollectionTrackEntry entry;
  final List<Track> folderTracks;
  final int trackIndex;
  final bool isInHistory;
  final bool isSelectionMode;
  final bool isSelected;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  const _CollectionTrackTile({
    required this.entry,
    required this.folderTracks,
    required this.trackIndex,
    required this.isInHistory,
    this.isSelectionMode = false,
    this.isSelected = false,
    this.onTap,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final track = entry.track;
    final rawCoverUrl = rawCollectionCoverUrl(track);
    final localCoverUrl = rawCoverUrl == null
        ? ref
              .watch(
                localLibraryCoverProvider(
                  LocalLibraryCoverRequest(
                    isrc: track.isrc?.trim(),
                    trackName: track.name,
                    artistName: track.artistName,
                  ),
                ),
              )
              .maybeWhen(data: (cover) => cover, orElse: () => null)
        : null;
    final effectiveCoverUrl = rawCoverUrl ?? localCoverUrl;

    // Fine-grained provider watches – only this tile rebuilds when its own
    // history / local-library entry changes.
    final inMemoryHistoryItem = ref.watch(
      downloadHistoryProvider.select((state) {
        final byId = state.getBySpotifyId(track.id);
        if (byId != null) return byId;
        final isrc = track.isrc?.trim();
        if (isrc != null && isrc.isNotEmpty) {
          final byIsrc = state.getByIsrc(isrc);
          if (byIsrc != null) return byIsrc;
        }
        return state.findByTrackAndArtist(track.name, track.artistName);
      }),
    );
    final showLocalLibraryIndicator = ref.watch(
      settingsProvider.select(
        (s) => s.localLibraryEnabled && s.localLibraryShowDuplicates,
      ),
    );
    final isInLocalLibrary = showLocalLibraryIndicator
        ? ref.watch(
            localLibraryProvider.select((state) {
              final isrc = track.isrc?.trim();
              return state.existsInLibrary(
                isrc: isrc,
                trackName: track.name,
                artistName: track.artistName,
              );
            }),
          )
        : false;

    final heroTag = inMemoryHistoryItem != null
        ? 'cover_${inMemoryHistoryItem.id}'
        : null;

    return TrackCard(
      style: TrackCardStyle.flat,
      isSelectionMode: isSelectionMode,
      isSelected: isSelected,
      leading: HeroMode(
        enabled: heroTag != null,
        child: Builder(
          builder: (context) {
            final size = context.tokens.coverCompact;
            final cover = ClipRRect(
              borderRadius: context.tokens.borderRadiusThumb,
              child: effectiveCoverUrl != null && effectiveCoverUrl.isNotEmpty
                  ? _buildTrackCover(context, effectiveCoverUrl, size)
                  : TrackCoverPlaceholder(size: size),
            );
            return heroTag != null ? Hero(tag: heroTag, child: cover) : cover;
          },
        ),
      ),
      title: track.name,
      explicit: track.isExplicit,
      subtitle: Row(
        children: [
          Flexible(
            child: Text(
              track.artistName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (isInLocalLibrary || isInHistory) ...[
            const SizedBox(width: 6),
            const InLibraryBadge(),
          ],
        ],
      ),
      // The button reads its colours inside the row, which follow the fill
      // of the current track.
      trailing: isInHistory || isInLocalLibrary
          ? Builder(
              builder: (context) {
                final colorScheme = Theme.of(context).colorScheme;
                return IconButton(
                  tooltip: context.l10n.tooltipPlay,
                  onPressed: () => _playFromHere(context, ref),
                  icon: Icon(Icons.play_arrow, color: colorScheme.primary),
                  style: IconButton.styleFrom(
                    minimumSize: Size.square(context.tokens.minTouchTarget),
                    backgroundColor: colorScheme.primaryContainer.withValues(
                      alpha: 0.3,
                    ),
                  ),
                );
              },
            )
          : null,
      onTap: isSelectionMode
          ? onTap
          : () => downloadSingleTrack(context, ref, entry.track),
      onLongPress: isSelectionMode ? onTap : onLongPress,
    );
  }

  Future<void> _playFromHere(BuildContext context, WidgetRef ref) async {
    try {
      await ref
          .read(playbackProvider.notifier)
          .playTrackList(folderTracks, startIndex: trackIndex);
    } catch (error) {
      if (!context.mounted) return;
      showCannotOpenFileSnackBar(context, error);
    }
  }

  Widget _buildTrackCover(BuildContext context, String coverUrl, double size) {
    final colorScheme = Theme.of(context).colorScheme;
    Widget placeholder() => Container(
      width: size,
      height: size,
      color: colorScheme.surfaceContainerHighest,
      child: Icon(Icons.music_note, color: colorScheme.onSurfaceVariant),
    );

    return LocalOrNetworkCoverImage(
      url: coverUrl,
      width: size,
      height: size,
      networkCacheWidth: (size * 2).toInt(),
      fadeInDuration: const Duration(milliseconds: 180),
      fadeOutDuration: const Duration(milliseconds: 90),
      placeholder: (_) => placeholder(),
    );
  }
}
