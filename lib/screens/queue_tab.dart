import 'dart:async';
import 'dart:io';
import 'dart:math' show Random;
import 'package:sistrum/widgets/open_audio_button.dart';
import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/app_alert_dialog.dart';
import 'package:sistrum/widgets/app_choice_chip.dart';
import 'package:sistrum/widgets/app_content_card.dart';
import 'package:sistrum/services/shell_navigation_service.dart';
import 'package:sistrum/widgets/error_card.dart';
import 'package:sistrum/widgets/settings_group.dart';
import 'package:sistrum/widgets/queue_swipe.dart';
import 'package:sistrum/widgets/track_card.dart';
import 'package:sistrum/theme/app_motion.dart';
import 'package:sistrum/theme/app_tokens.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/pinned_section_sliver_list.dart';
import 'package:sistrum/widgets/app_icon_button.dart';
import 'package:sistrum/widgets/app_loading_indicator.dart';
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/widgets/album_detail_header.dart'
    show HeaderFilledButton;
import 'package:sistrum/widgets/app_search_field.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/library_directory_view.dart';
import 'package:sistrum/widgets/library_search_results.dart';
import 'package:sistrum/widgets/library_track_selection_bar.dart';
import 'package:sistrum/widgets/library_view_toggle.dart';
import 'package:sistrum/widgets/library_selection_playback_actions.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';
import 'package:sistrum/widgets/library_playlist_drag_source.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/create_playlist_sheet.dart';
import 'package:flutter/rendering.dart' show RenderSliver;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart' show DateFormat;
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/library_counts_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/widgets/app_shape.dart';
import 'package:sistrum/widgets/library_fast_scroller.dart';
import 'package:sistrum/widgets/player_artwork.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/audio_quality_badge_policy.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/library_format_filters.dart';
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
import 'package:sistrum/screens/track_metadata_screen.dart';
import 'package:sistrum/screens/listening_rewind_screen.dart';
import 'package:sistrum/screens/favorite_artists_screen.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/screens/library_tracks_folder_screen.dart';
import 'package:sistrum/screens/library_mix_screen.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/screens/mornye_library_screen.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/controllers/local_track_batch_actions.dart';
import 'package:sistrum/controllers/queue_library_pager.dart';
import 'package:sistrum/controllers/queue_library_refresh_policy.dart';
import 'package:sistrum/utils/string_utils.dart';
import 'package:sistrum/widgets/animated_library_sliver_grid.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/selection_bottom_bar.dart';
import 'package:sistrum/widgets/song_row_tap.dart';
import 'package:sistrum/widgets/two_finger_pinch_listener.dart';
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
part 'queue_tab_filter_sheet.dart';

class QueueTab extends ConsumerStatefulWidget {
  final String? librarySection;

  /// Whether the shell shows this tab; it does when this is null. A
  /// listenable, so a switch of tab reaches the tab without building it
  /// again.
  final ValueListenable<bool>? tabActive;

  const QueueTab({super.key, this.librarySection, this.tabActive});

  @override
  ConsumerState<QueueTab> createState() => _QueueTabState();
}

class _QueueTabState extends ConsumerState<QueueTab> {
  static const int _libraryPageSize = 300;
  final LibraryFileAvailabilityCache _fileExistsCache =
      LibraryFileAvailabilityCache();
  // Desktop windows are much wider than a phone, so covers start larger
  // there; pinching (or a trackpad pinch) resizes within the range.
  static double get _libraryGridMinExtent => isDesktopPlatform ? 140 : 92;
  static double get _libraryGridDefaultExtent => isDesktopPlatform ? 180 : 126;
  static double get _libraryGridMaxExtent => isDesktopPlatform ? 260 : 190;
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

  PageController? _filterPageController;

  /// Pages of the Library pager: Songs, Albums, Artists, Genres, Playlists
  /// and Folders. Singles share the Albums page behind [_albumsShowSingles].
  final List<String> _filterModes = [
    'all',
    'albums',
    'artists',
    'genres',
    'playlists',
    'folders',
  ];
  bool _albumsShowSingles = false;

  int _pageIndexFor(String mode) =>
      _filterModes.indexOf(mode == 'singles' ? 'albums' : mode);

  /// Data mode of the page at [index], resolving Albums to Singles when the
  /// Albums page shows singles.
  String _modeForPage(int index) {
    final mode = _filterModes[index];
    return mode == 'albums' && _albumsShowSingles ? 'singles' : mode;
  }

  bool _isPageControllerInitialized = false;
  bool _wasTabVisible = false;
  final TextEditingController _searchController = TextEditingController();
  final FocusNode _searchFocusNode = FocusNode();
  final ScrollController _libraryScrollController = ScrollController();
  final GlobalKey<NestedScrollViewState> _libraryScrollKey = GlobalKey();
  final GlobalKey _searchFieldKey = GlobalKey();

  /// The search field has scrolled under the pinned header, which then
  /// offers a Search action that brings it back.
  final ValueNotifier<bool> _searchFieldHidden = ValueNotifier(false);
  bool _searchFieldCheckScheduled = false;
  String _searchQuery = '';
  Timer? _searchDebounce;
  String? _filterSource;
  String? _filterQuality;
  String? _filterFormat;
  String? _filterMetadata;
  String get _sortMode {
    final settings = ref.read(settingsProvider);
    final mode = settings.historySortMode;
    final section = widget.librarySection ?? settings.historyFilterMode;
    // Playlists have no size or release year. Keep the saved song order.
    return section == 'playlists' &&
            (mode.startsWith('size-') || mode.startsWith('year-'))
        ? 'latest'
        : mode;
  }

  String _libraryQualityLabelMode = AppSettings.libraryQualityLabelBitDepthOnly;
  final _libraryGridExtent = ValueNotifier(_libraryGridDefaultExtent);
  double? _libraryGridPinchStartExtent;

  /// Filter data per mode with the page and totals it came
  /// from, so an unchanged page keeps its filtered rows across rebuilds.
  final Map<String, (_QueueLibraryPageData, int?, int?, _FilterContentData)>
  _filterDataByMode = {};

  final Map<String, (List<UnifiedLibraryItem>, _LibraryNavigation)>
  _navigationByMode = {};
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

  void _handleLibraryGridPinchStart() {
    _libraryGridPinchStartExtent = _libraryGridExtent.value;
  }

  void _handleLibraryGridPinchUpdate(double scale) {
    final startExtent = _libraryGridPinchStartExtent;
    if (startExtent == null) return;

    final nextExtent = (startExtent * scale).clamp(
      _libraryGridMinExtent,
      _libraryGridMaxExtent,
    );
    if ((nextExtent - _libraryGridExtent.value).abs() < 0.5) return;
    _libraryGridExtent.value = nextExtent;
  }

  void _handleLibraryGridPinchEnd() {
    _libraryGridPinchStartExtent = null;
  }

  @override
  void initState() {
    super.initState();
    widget.tabActive?.addListener(_applyDefaultLibraryViewOnTabVisible);
    DownloadedEmbeddedCoverResolver.previewsCleared.addListener(
      _onEmbeddedCoverChanged,
    );
    _libraryScrollController.addListener(_scheduleSearchFieldCheck);
  }

  void _initializePageController() {
    if (_isPageControllerInitialized) return;
    _isPageControllerInitialized = true;
    final settings = ref.read(settingsProvider);
    final initialFilter =
        widget.librarySection ??
        (settings.defaultLibraryView == 'last'
            ? settings.historyFilterMode
            : settings.defaultLibraryView);
    final initialPage = _pageIndexFor(
      initialFilter,
    ).clamp(0, _filterModes.length - 1);
    _albumsShowSingles = initialFilter == 'singles';
    final initialMode = _modeForPage(initialPage);
    if (widget.librarySection == null &&
        settings.historyFilterMode != initialMode) {
      Future.microtask(() {
        if (!mounted) return;
        ref.read(settingsProvider.notifier).setHistoryFilterMode(initialMode);
      });
    }
    _filterPageController = PageController(initialPage: initialPage);
  }

  /// When the shell switches back to this tab and a fixed default view is
  /// configured, jump the filter pager to it.
  void _applyDefaultLibraryViewOnTabVisible() {
    if (widget.librarySection != null) return;
    // Route transitions also disable TickerMode. Only a shell tab change
    // should reset the user's current album/single view.
    final isVisible = widget.tabActive?.value ?? true;
    final becameVisible = isVisible && !_wasTabVisible;
    _wasTabVisible = isVisible;
    if (!becameVisible) return;
    final defaultView = ref.read(settingsProvider).defaultLibraryView;
    if (defaultView == 'last') return;
    final index = _pageIndexFor(defaultView);
    if (index < 0) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_filterModes[index] == 'albums') {
        _setState(() => _albumsShowSingles = defaultView == 'singles');
      }
      if (ref.read(settingsProvider).historyFilterMode != defaultView) {
        ref.read(settingsProvider.notifier).setHistoryFilterMode(defaultView);
      }
      final controller = _filterPageController;
      if (controller != null &&
          controller.hasClients &&
          controller.page?.round() != index) {
        controller.jumpToPage(index);
      }
    });
  }

  @override
  void didUpdateWidget(QueueTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.tabActive, oldWidget.tabActive)) {
      oldWidget.tabActive?.removeListener(_applyDefaultLibraryViewOnTabVisible);
      widget.tabActive?.addListener(_applyDefaultLibraryViewOnTabVisible);
    }
  }

  @override
  void dispose() {
    widget.tabActive?.removeListener(_applyDefaultLibraryViewOnTabVisible);
    DownloadedEmbeddedCoverResolver.previewsCleared.removeListener(
      _onEmbeddedCoverChanged,
    );
    _hideSelectionOverlay();
    _hidePlaylistSelectionOverlay();
    _fileExistsCache.dispose();
    _embeddedCoverVersion.dispose();
    _libraryGridExtent.dispose();
    _filterPageController?.dispose();
    _searchController.dispose();
    _searchFocusNode.dispose();
    _libraryScrollController.dispose();
    _searchFieldHidden.dispose();
    _searchDebounce?.cancel();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _searchDebounce?.cancel();
    final normalized = value.trim().toLowerCase();
    _searchDebounce = Timer(const Duration(milliseconds: 350), () {
      if (!mounted || _searchQuery == normalized) return;
      setState(() {
        _searchQuery = normalized;
        _resetLibraryPaging();
      });
    });
  }

  void _clearSearch() {
    _searchDebounce?.cancel();
    if (_searchQuery.isEmpty) return;
    setState(() {
      _searchQuery = '';
      _resetLibraryPaging();
    });
  }

  void _scheduleSearchFieldCheck() {
    if (_searchFieldCheckScheduled) return;
    _searchFieldCheckScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _searchFieldCheckScheduled = false;
      if (mounted) _searchFieldHidden.value = _isSearchFieldHidden();
    });
  }

  /// Less than half of the search field shows below the pinned header,
  /// which paints over the part of the field it overlaps.
  bool _isSearchFieldHidden() {
    final sliver = _searchFieldKey.currentContext?.findRenderObject();
    if (sliver is! RenderSliver) return false;
    final geometry = sliver.geometry;
    if (geometry == null || geometry.scrollExtent <= 0) return false;
    final shown = geometry.paintExtent - sliver.constraints.overlap;
    return shown < geometry.scrollExtent / 2;
  }

  /// Brings the search field back from under the header and focuses it.
  /// A scrolled list jumps to its start first, so only the header and the
  /// field move.
  Future<void> _revealSearchField() async {
    final outer = _libraryScrollController;
    if (outer.hasClients && outer.offset > 0) {
      final inner = _libraryScrollKey.currentState?.innerController;
      if (inner != null &&
          inner.positions.any((position) => position.pixels > 0)) {
        inner.jumpTo(0);
      }
      if (MediaQuery.disableAnimationsOf(context)) {
        outer.jumpTo(0);
      } else {
        final motion = AppMotion.spatialSlow(
          context,
          const AppMotionSpec(Duration(milliseconds: 300), Curves.easeOutCubic),
        );
        await outer.animateTo(
          0,
          duration: motion.duration,
          curve: motion.curve,
        );
      }
    }
    if (mounted) _searchFocusNode.requestFocus();
  }

  Widget _buildSearchAction() => ValueListenableBuilder<bool>(
    valueListenable: _searchFieldHidden,
    builder: (context, hidden, _) {
      final motion = AppMotion.effectsFast(
        context,
        const AppMotionSpec(Duration(milliseconds: 150), Curves.easeOut),
      );
      return AnimatedSwitcher(
        duration: motion.duration,
        switchInCurve: motion.curve,
        switchOutCurve: motion.leaving,
        child: hidden
            ? AppIconButton(
                key: const ValueKey('library-search-action'),
                icon: const AppIcon(Icons.search),
                tooltip: context.l10n.mornyeSearch,
                onPressed: _revealSearchField,
              )
            : const SizedBox.shrink(),
      );
    },
  );

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

  void _onFilterPageChanged(int index) {
    if (widget.librarySection != null) return;
    HapticFeedback.selectionClick();
    ref
        .read(settingsProvider.notifier)
        .setHistoryFilterMode(_modeForPage(index));
  }

  void _animateToFilterPage(int index) {
    if (index >= 0 && index < _filterModes.length) {
      final filterMode = _modeForPage(index);
      if (ref.read(settingsProvider).historyFilterMode != filterMode) {
        ref.read(settingsProvider.notifier).setHistoryFilterMode(filterMode);
      }
    }
    final motion = AppMotion.spatialSlow(
      context,
      const AppMotionSpec(Duration(milliseconds: 300), Curves.easeOutCubic),
    );
    _filterPageController?.animateToPage(
      index,
      duration: motion.duration,
      curve: motion.curve,
    );
  }

  void _setAlbumsShowSingles(bool singles) {
    if (_albumsShowSingles == singles) return;
    HapticFeedback.selectionClick();
    setState(() => _albumsShowSingles = singles);
    ref
        .read(settingsProvider.notifier)
        .setHistoryFilterMode(singles ? 'singles' : 'albums');
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

  int get _activeFilterCount {
    int count = 0;
    if (_filterSource != null) count++;
    if (_filterQuality != null) count++;
    if (_filterFormat != null) count++;
    if (_filterMetadata != null) count++;
    return count;
  }

  void _resetFilters() {
    setState(() {
      _filterSource = null;
      _filterQuality = null;
      _filterFormat = null;
      _filterMetadata = null;
      _resetLibraryPaging();
    });
    ref.read(settingsProvider.notifier).setHistorySortMode('latest');
  }

  @override
  Widget build(BuildContext context) {
    if (widget.librarySection == null) {
      return MornyeLibraryScreen(
        onOpenSection: (section) => pushPage<void>(
          context,
          (_) => Scaffold(body: QueueTab(librarySection: section)),
        ),
      );
    }
    _initializePageController();
    _applyDefaultLibraryViewOnTabVisible();
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

    final localLibraryTotalCount = localLibraryEnabled
        ? ref.watch(localLibraryProvider.select((state) => state.totalCount))
        : 0;
    // Watch with selector on key fields to reduce unnecessary rebuilds.
    // LibraryCollectionsState doesn't implement == so watching without
    // selector rebuilds on every provider notification.
    ref.watch(
      libraryCollectionsProvider.select(
        (s) => (
          s.wishlistCount,
          s.lovedCount,
          s.favoriteArtistCount,
          s.playlistCount,
          s.hasPlaylistTracks,
          s.isLoaded,
        ),
      ),
    );
    final collectionState = ref.read(libraryCollectionsProvider);
    final historyViewMode = ref.watch(
      settingsProvider.select((s) => s.historyViewMode),
    );
    final String historyFilterMode =
        widget.librarySection ??
        ref.watch<String>(settingsProvider.select((s) => s.historyFilterMode));
    // Keep this mode out of the page-provider request: changing only badge
    // text must not re-query the database or reset Library pagination.
    _libraryQualityLabelMode = ref.watch(
      settingsProvider.select((s) => s.libraryQualityLabelMode),
    );
    final colorScheme = Theme.of(context).colorScheme;
    final countsRequest = LibraryCountsRequest(
      searchQuery: _searchQuery,
      filterSource: _filterSource,
      filterQuality: _filterQuality,
      filterFormat: _filterFormat,
      filterMetadata: _filterMetadata,
      localLibraryEnabled: localLibraryEnabled,
    );
    final searchingLibrary =
        _searchQuery.isNotEmpty &&
        !isAlbumCompletenessFilter(_filterMetadata) &&
        !_isSelectionMode &&
        !_isPlaylistSelectionMode;
    final countsValue = searchingLibrary
        ? const AsyncData(
            QueueLibraryCounts(
              allTrackCount: 0,
              albumCount: 0,
              singleTrackCount: 0,
            ),
          )
        : ref.watch(libraryCountsProvider(countsRequest));
    final queueCounts = _pager.resolveCounts(countsValue, countsRequest);

    _QueueLibraryPageRequest pageRequest(String filterMode) {
      final offset = _pager.pageOffsetFor(filterMode);
      final baseRequest = _QueueLibraryPageRequest(
        filterMode: filterMode,
        limit: _libraryPageSize,
        offset: offset,
        searchQuery: _searchQuery,
        filterSource: _filterSource,
        filterQuality: _filterQuality,
        filterFormat: _filterFormat,
        filterMetadata: _filterMetadata,
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
        searchQuery: baseRequest.searchQuery,
        filterSource: baseRequest.filterSource,
        filterQuality: baseRequest.filterQuality,
        filterFormat: baseRequest.filterFormat,
        filterMetadata: baseRequest.filterMetadata,
        sortMode: baseRequest.sortMode,
        localLibraryEnabled: baseRequest.localLibraryEnabled,
        cursor: previousPage?.nextCursor,
      );
    }

    final activePageRequest = pageRequest(historyFilterMode);
    // The Artists, Genres and Folders pages browse their own queries.
    final activePageValue =
        searchingLibrary ||
            historyFilterMode == 'artists' ||
            historyFilterMode == 'genres' ||
            historyFilterMode == 'folders'
        ? const AsyncData(_QueueLibraryPageData())
        : ref.watch(_queueLibraryPageProvider(activePageRequest));

    final pageDataByMode = <String, _QueueLibraryPageData>{};
    _QueueLibraryPageData pageData(String filterMode) =>
        pageDataByMode.putIfAbsent(
          filterMode,
          () => _pager.resolvePageData(
            filterMode == historyFilterMode ? activePageValue : null,
            filterMode == historyFilterMode
                ? activePageRequest
                : pageRequest(filterMode),
          ),
        );

    _FilterContentData getFilterData(String filterMode) {
      final page = pageData(filterMode);
      final totalTrackCount = switch (filterMode) {
        'singles' => queueCounts.singleTrackCount,
        'albums' || 'playlists' => 0,
        _ => queueCounts.allTrackCount,
      };
      final totalAlbumCount = filterMode == 'albums'
          ? queueCounts.albumCount
          : null;
      final cached = _filterDataByMode[filterMode];
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
      _filterDataByMode[filterMode] = (
        page,
        totalTrackCount,
        totalAlbumCount,
        data,
      );
      return data;
    }

    final currentPageData = pageData(historyFilterMode);
    final currentLoadedCount = historyFilterMode == 'albums'
        ? currentPageData.groupedLocalAlbums.length
        : currentPageData.items.length;
    final currentTotalCount = switch (historyFilterMode) {
      'albums' => queueCounts.albumCount,
      'singles' => queueCounts.singleTrackCount,
      'playlists' || 'artists' || 'genres' || 'folders' => 0,
      _ => queueCounts.allTrackCount,
    };
    final hasMoreLibrary = currentLoadedCount < currentTotalCount;
    final isLibraryPageLoading =
        countsValue.isLoading || activePageValue.isLoading;
    final hasAnyLibraryItems =
        queueCounts.allTrackCount > 0 || queueCounts.albumCount > 0;
    final hasLibraryContent = localLibraryEnabled && localLibraryTotalCount > 0;
    final hasActiveSearch =
        _searchQuery.isNotEmpty || _searchController.text.trim().isNotEmpty;
    final hasCollections =
        collectionState.playlistCount > 0 ||
        collectionState.wishlistCount > 0 ||
        collectionState.lovedCount > 0 ||
        collectionState.favoriteArtistCount > 0;
    final shouldShowLibraryControls =
        hasLibraryContent ||
        hasAnyLibraryItems ||
        hasCollections ||
        hasActiveSearch;
    final showSearchField = shouldShowLibraryControls;
    final listInset = wideListInset(context);
    final selectionItems = getFilterData(
      historyFilterMode,
    ).filteredUnifiedItems;
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
      child: Stack(
        children: [
          // ScrollConfiguration disables stretch overscroll to fix _StretchController exception
          // This is a known Flutter issue with NestedScrollView + Material 3 stretch indicator
          ScrollConfiguration(
            behavior: ScrollConfiguration.of(
              context,
            ).copyWith(overscroll: false),
            child: NestedScrollView(
              key: _libraryScrollKey,
              controller: _libraryScrollController,
              headerSliverBuilder: (context, innerBoxIsScrolled) => [
                if (widget.librarySection == null)
                  AppSliverHeader.tabRoot(
                    title: context.l10n.navLibrary,
                    alignToContent: true,
                    actions: [
                      if (showSearchField) _buildSearchAction(),
                      const OpenAudioButton(),
                    ],
                  )
                else
                  AppSliverHeader.page(
                    title: widget.librarySection == 'playlists'
                        ? context.l10n.searchPlaylists
                        : context.l10n.searchSongs,
                    actions: [if (showSearchField) _buildSearchAction()],
                  ),

                if (showSearchField)
                  SliverToBoxAdapter(
                    key: _searchFieldKey,
                    child: Padding(
                      padding: EdgeInsets.fromLTRB(
                        16 + listInset,
                        8,
                        16 + listInset,
                        0,
                      ),
                      child: GestureDetector(
                        onTap: () {},
                        child: AppSearchField(
                          controller: _searchController,
                          focusNode: _searchFocusNode,
                          hintText: context.l10n.librarySearchHint,
                          clearTooltip: context.l10n.dialogClear,
                          samplesBackdrop: false,
                          onChanged: _onSearchChanged,
                          onClear: () {
                            _clearSearch();
                            FocusScope.of(context).unfocus();
                          },
                        ),
                      ),
                    ),
                  ),

                // Playlists can contain online tracks or be empty. Navigation
                // must stay reachable before any songs are downloaded or scanned.
                if (!searchingLibrary && widget.librarySection == null)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: EdgeInsets.fromLTRB(
                        16 + listInset,
                        12,
                        16 + listInset,
                        4,
                      ),
                      child: _LibraryPageTabs(
                        selectedIndex: _pageIndexFor(historyFilterMode),
                        onSelected: _animateToFilterPage,
                        tabs: [
                          (context.l10n.searchSongs, queueCounts.allTrackCount),
                          (
                            context.l10n.historyFilterAlbums,
                            queueCounts.albumCount,
                          ),
                          // Artist totals need a full scan; the tab shows
                          // none rather than a guess.
                          (context.l10n.searchArtists, null),
                          (context.l10n.libraryGenres, null),
                          (
                            context.l10n.searchPlaylists,
                            _getVisibleCollectionEntries(
                              collectionState,
                            ).length,
                          ),
                          (context.l10n.libraryFolders, null),
                        ],
                      ),
                    ),
                  ),
              ],
              body: searchingLibrary
                  ? CustomScrollView(
                      key: const ValueKey('library-search-results'),
                      slivers: [
                        LibrarySearchResults(
                          query: _searchQuery,
                          songList: _searchSongList(),
                          onOpenArtist: (artist) => pushPage<void>(
                            context,
                            (_) => LibraryArtistScreen(artist: artist),
                          ),
                        ),
                        const NavBarSliverSpacer(),
                      ],
                    )
                  : widget.librarySection != null
                  ? _buildFilterContent(
                      context: context,
                      colorScheme: colorScheme,
                      filterMode: historyFilterMode,
                      historyViewMode: historyViewMode,
                      filterData: getFilterData(historyFilterMode),
                      collectionState: collectionState,
                      hasMoreLibrary: hasMoreLibrary,
                      isPageLoading: isLibraryPageLoading,
                    )
                  : PageView.builder(
                      controller: _filterPageController!,
                      // While song rows swipe to queue, the tabs alone
                      // change pages, so the two sideways swipes never
                      // compete.
                      physics: queueSwipeEnabled(ref)
                          ? const NeverScrollableScrollPhysics()
                          : const ClampingScrollPhysics(),
                      onPageChanged: _onFilterPageChanged,
                      itemCount: _filterModes.length,
                      itemBuilder: (context, index) {
                        if (_filterModes[index] == 'artists') {
                          return _buildArtistsPage(context, colorScheme);
                        }
                        if (_filterModes[index] == 'genres') {
                          return _buildGenresPage(context, colorScheme);
                        }
                        if (_filterModes[index] == 'folders') {
                          return const LibraryDirectoryView(showHeader: false);
                        }
                        final filterMode = _modeForPage(index);
                        final filterData = getFilterData(filterMode);
                        return _buildFilterContent(
                          context: context,
                          colorScheme: colorScheme,
                          filterMode: filterMode,
                          historyViewMode: historyViewMode,
                          filterData: filterData,
                          collectionState: collectionState,
                          hasMoreLibrary: filterMode == historyFilterMode
                              ? hasMoreLibrary
                              : false,
                          isPageLoading: isLibraryPageLoading,
                        );
                      },
                    ),
            ),
          ),
        ],
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
