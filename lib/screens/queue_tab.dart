import 'dart:async';
import 'dart:io';
import 'dart:math' show Random;
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/widgets/error_card.dart';
import 'package:sistrum/widgets/queue_swipe.dart';
import 'package:sistrum/widgets/song_menu.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/sort_header_button.dart';
import 'package:sistrum/widgets/album_detail_header.dart' show AlbumPlayActions;
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';
import 'package:sistrum/widgets/library_playlist_drag_source.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/create_playlist_sheet.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart' show DateFormat;
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/widgets/library_fast_scroller.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/ordered_range_selection.dart';
import 'package:sistrum/models/settings.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/album_completeness.dart';
import 'package:sistrum/services/batch_track_actions.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/screens/favorite_artists_screen.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/screens/collection_detail_screen.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/controllers/song_file.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';
import 'package:sistrum/controllers/queue_library_pager.dart';
import 'package:sistrum/controllers/queue_library_refresh_policy.dart';
import 'package:sistrum/widgets/animated_library_sliver_grid.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/desktop/hover_play_button.dart';
import 'package:sistrum/widgets/desktop/library_track_menu.dart';
import 'package:sistrum/widgets/desktop/track_table.dart';

part 'queue_tab_helpers.dart';
part 'queue_tab_widgets.dart';
part 'queue_tab_selection.dart';
part 'queue_tab_navigation.dart';
part 'queue_tab_collection_items.dart';
part 'queue_tab_filter_widgets.dart';
part 'queue_tab_sections.dart';
part 'queue_tab_batch_actions.dart';
part 'queue_tab_item_widgets.dart';

/// The Songs page (`librarySection` 'all') and the Playlists page
/// (`librarySection` 'playlists'). Without a section, the Library overview.
class QueueTab extends ConsumerStatefulWidget {
  final String? librarySection;

  const QueueTab({super.key, this.librarySection});

  @override
  ConsumerState<QueueTab> createState() => _QueueTabState();
}

class _QueueTabState extends ConsumerState<QueueTab> {
  static const int _libraryPageSize = 300;
  final LibraryFileAvailabilityCache _fileExistsCache =
      LibraryFileAvailabilityCache();
  // Desktop windows are much wider than a phone, so covers start larger there.
  static double get _libraryGridDefaultExtent => isDesktopPlatform ? 180 : 126;
  static double get _libraryAlbumGridExtent => _libraryGridDefaultExtent * 1.45;
  bool _embeddedCoverRefreshScheduled = false;
  // Version counter to trigger targeted cover image rebuilds
  // without rebuilding the entire widget tree via setState.
  final ValueNotifier<int> _embeddedCoverVersion = ValueNotifier<int>(0);

  bool _isSelectionMode = false;
  bool _isDraggingLibraryTrack = false;
  final Set<String> _selectedIds = {};
  String? _selectionAnchorId;
  final SelectionOverlayController _selectionOverlay =
      SelectionOverlayController();
  List<UnifiedLibraryItem> _selectionOverlayItems = const [];
  double _selectionOverlayBottomPadding = 0;

  /// Keeps the selection overlays hidden while a modal launched from the
  /// selection toolbar is open, so they don't reappear over its animation.
  bool _suppressSelectionOverlay = false;

  /// A right-click selected a song for its menu only. The selection toolbar
  /// stays hidden until the menu and its chosen action have run.
  bool _contextMenuSelection = false;

  bool _isPlaylistSelectionMode = false;
  final Set<String> _selectedPlaylistIds = {};
  final SelectionOverlayController _playlistSelectionOverlay =
      SelectionOverlayController();
  List<UserPlaylistCollection> _playlistSelectionOverlayItems = const [];
  double _playlistSelectionOverlayBottomPadding = 0;

  final ScrollController _libraryScrollController = ScrollController();

  /// Sort of the Songs list, or of the Playlists page.
  String get _sortMode {
    final mode = ref.read(settingsProvider).historySortMode;
    // Playlists have no size or release year. Keep the saved song order.
    return widget.librarySection == 'playlists' &&
            (mode.startsWith('size-') || mode.startsWith('year-'))
        ? 'latest'
        : mode;
  }

  /// Filter data per mode with the page and totals it came
  /// from, so an unchanged page keeps its filtered rows across rebuilds.
  final Map<String, (_QueueLibraryPageData, int?, int?, _FilterContentData)>
  _filterDataByMode = {};

  final Map<String, _LibrarySongTableRows> _songTableByMode = {};

  final QueueLibraryPager<
    LibraryCountsRequest,
    _QueueLibraryPageRequest,
    _QueueLibraryPageData
  >
  _pager = QueueLibraryPager(
    pageSize: _libraryPageSize,
    combinePages: _QueueLibraryPageData.combine,
  );

  /// setState is @protected, so the extension part files route rebuilds
  /// through this forwarder.
  void _setState(VoidCallback fn) => setState(fn);

  @override
  void initState() {
    super.initState();
    DownloadedEmbeddedCoverResolver.previewsCleared.addListener(
      _onEmbeddedCoverChanged,
    );
  }

  @override
  void dispose() {
    DownloadedEmbeddedCoverResolver.previewsCleared.removeListener(
      _onEmbeddedCoverChanged,
    );
    _hideSelectionOverlay();
    _hidePlaylistSelectionOverlay();
    _fileExistsCache.dispose();
    _embeddedCoverVersion.dispose();
    _libraryScrollController.dispose();
    super.dispose();
  }

  void _resetLibraryPaging() => _pager.resetPaging();

  bool _handleLibraryScrollNotification({
    required ScrollNotification notification,
    required String filterMode,
    required bool hasMoreLibrary,
    required bool isPageLoading,
  }) {
    return _pager.handleScrollNotification(
      notification: notification,
      filterMode: filterMode,
      hasMoreLibrary: hasMoreLibrary,
      isPageLoading: isPageLoading,
      setState: setState,
    );
  }

  void _onEmbeddedCoverChanged() {
    if (!mounted || _embeddedCoverRefreshScheduled) return;
    _embeddedCoverRefreshScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _embeddedCoverRefreshScheduled = false;
      if (mounted) {
        _embeddedCoverVersion.value++;
      }
    });
  }

  String? _resolveDownloadedEmbeddedCoverPath(String? filePath) {
    return DownloadedEmbeddedCoverResolver.resolve(
      filePath,
      onChanged: _onEmbeddedCoverChanged,
    );
  }

  ValueListenable<bool> _fileExistsListenable(String? filePath) {
    return _fileExistsCache.listenable(filePath);
  }

  @override
  Widget build(BuildContext context) {
    final section = widget.librarySection;
    if (section == null) {
      return MornyeLibraryScreen(
        onOpenSection: (section) => pushPage<void>(
          context,
          (_) => Scaffold(body: QueueTab(librarySection: section)),
        ),
      );
    }
    ref.watch(settingsProvider.select((s) => s.historySortMode));
    final localLibraryEnabled = ref.watch(
      settingsProvider.select((s) => s.localLibraryEnabled),
    );

    if (localLibraryEnabled) {
      ref.listen<int>(
        localLibraryProvider.select((state) => state.loadedIndexVersion),
        (previous, next) {
          if (previous == null || previous == next) return;
          // Keep stale rows visible until the refreshed query replaces them.
          _pager.resetOffsets();
          if (mounted) setState(() {});
        },
      );
    }

    // Watch with selector on key fields to reduce unnecessary rebuilds.
    // LibraryCollectionsState doesn't implement == so watching without
    // selector rebuilds on every provider notification.
    ref.watch(
      libraryCollectionsProvider.select(
        (s) => (
          s.lovedCount,
          s.favoriteArtistCount,
          s.playlistCount,
          s.hasPlaylistTracks,
          s.isLoaded,
        ),
      ),
    );
    final collectionState = ref.read(libraryCollectionsProvider);
    final colorScheme = Theme.of(context).colorScheme;
    final countsRequest = LibraryCountsRequest(
      localLibraryEnabled: localLibraryEnabled,
    );
    final countsValue = ref.watch(libraryCountsProvider(countsRequest));
    final queueCounts = _pager.resolveCounts(countsValue, countsRequest);

    _QueueLibraryPageRequest pageRequest() {
      final offset = _pager.pageOffsetFor(section);
      final baseRequest = _QueueLibraryPageRequest(
        filterMode: section,
        limit: _libraryPageSize,
        offset: offset,
        sortMode: _sortMode,
        localLibraryEnabled: localLibraryEnabled,
      );
      if (offset == 0) return baseRequest;
      final previousPage = _pager.cachedPageAt(
        baseRequest,
        offset - _libraryPageSize,
      );
      return _QueueLibraryPageRequest(
        filterMode: baseRequest.filterMode,
        limit: baseRequest.limit,
        offset: baseRequest.offset,
        sortMode: baseRequest.sortMode,
        localLibraryEnabled: baseRequest.localLibraryEnabled,
        cursor: previousPage?.nextCursor,
      );
    }

    final activePageRequest = pageRequest();
    final activePageValue = ref.watch(
      _queueLibraryPageProvider(activePageRequest),
    );
    _QueueLibraryPageData pageData() =>
        _pager.resolvePageData(activePageValue, activePageRequest);

    _FilterContentData getFilterData() {
      final page = pageData();
      final totalTrackCount = switch (section) {
        'albums' || 'playlists' => 0,
        _ => queueCounts.allTrackCount,
      };
      final totalAlbumCount = section == 'albums'
          ? queueCounts.albumCount
          : null;
      final cached = _filterDataByMode[section];
      if (cached != null &&
          identical(cached.$1, page) &&
          cached.$2 == totalTrackCount &&
          cached.$3 == totalAlbumCount) {
        return cached.$4;
      }
      final data = page.toFilterContentData(
        totalTrackCount: totalTrackCount,
        totalAlbumCount: totalAlbumCount,
      );
      _filterDataByMode[section] = (
        page,
        totalTrackCount,
        totalAlbumCount,
        data,
      );
      return data;
    }

    final currentPageData = pageData();
    final currentLoadedCount = section == 'albums'
        ? currentPageData.groupedLocalAlbums.length
        : currentPageData.items.length;
    final currentTotalCount = switch (section) {
      'albums' => queueCounts.albumCount,
      'playlists' => 0,
      _ => queueCounts.allTrackCount,
    };
    final hasMoreLibrary = currentLoadedCount < currentTotalCount;
    final isLibraryPageLoading =
        countsValue.isLoading || activePageValue.isLoading;
    final selectionItems = getFilterData().filteredUnifiedItems;
    if (_isSelectionMode || _isPlaylistSelectionMode) {
      final bottomPadding = MediaQuery.paddingOf(context).bottom;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_isSelectionMode) {
          _syncSelectionOverlay(
            items: selectionItems,
            bottomPadding: bottomPadding,
          );
        }
        if (_isPlaylistSelectionMode) {
          _syncPlaylistSelectionOverlay(
            playlists: collectionState.playlists,
            bottomPadding: bottomPadding,
          );
        }
      });
    }

    final page = PopScope(
      canPop: !_isSelectionMode && !_isPlaylistSelectionMode,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          if (_isPlaylistSelectionMode) {
            _exitPlaylistSelectionMode();
          } else if (_isSelectionMode) {
            _exitSelectionMode();
          }
        }
      },
      // ScrollConfiguration disables stretch overscroll to fix _StretchController exception
      // This is a known Flutter issue with NestedScrollView + Material 3 stretch indicator
      child: ScrollConfiguration(
        behavior: ScrollConfiguration.of(context).copyWith(overscroll: false),
        child: NestedScrollView(
          controller: _libraryScrollController,
          headerSliverBuilder: (context, innerBoxIsScrolled) => [
            AppSliverHeader.page(
              title: section == 'playlists'
                  ? context.l10n.searchPlaylists
                  : context.l10n.searchSongs,
              actions: _isSelectionMode ? null : [_sortHeaderButton(context)],
            ),
          ],
          body: _buildFilterContent(
            context: context,
            colorScheme: colorScheme,
            filterMode: section,
            filterData: getFilterData(),
            collectionState: collectionState,
            hasMoreLibrary: hasMoreLibrary,
            isPageLoading: isLibraryPageLoading,
          ),
        ),
      ),
    );
    return DesktopListScope(
      onSelectAll: selectionItems.isEmpty
          ? null
          : () {
              _applyDesktopClick(
                selectionItems.first.id,
                DesktopClick.replace,
                const [],
              );
              _selectAll(selectionItems);
            },
      onClearSelection: _isPlaylistSelectionMode
          ? _exitPlaylistSelectionMode
          : _isSelectionMode
          ? _exitSelectionMode
          : null,
      onDelete: _isSelectionMode ? () => _deleteSelected(selectionItems) : null,
      onActivate: () {
        final anchor = selectionItems
            .where((item) => item.id == _selectionAnchorId)
            .firstOrNull;
        if (anchor != null) _playLibraryItem(anchor, selectionItems);
      },
      child: page,
    );
  }
}
