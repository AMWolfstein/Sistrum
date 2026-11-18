part of 'queue_tab.dart';

sealed class _LibraryAlbumGroup {}

class _GroupedLocalAlbum extends _LibraryAlbumGroup {
  final String albumKey;
  final String albumName;
  final String artistName;
  final String? coverPath;
  final String sampleFilePath;
  final List<LocalLibraryItem> tracks;
  final int? trackCount;
  final AlbumCompleteness? completeness;
  final DateTime latestScanned;
  final String searchKey;

  _GroupedLocalAlbum({
    required this.albumKey,
    required this.albumName,
    required this.artistName,
    this.coverPath,
    required this.sampleFilePath,
    required this.tracks,
    this.trackCount,
    this.completeness,
    required this.latestScanned,
  }) : searchKey = '${albumName.toLowerCase()}|${artistName.toLowerCase()}';

  String get key => albumKey;

  int get displayTrackCount => trackCount ?? tracks.length;
}

class _FilterContentData {
  final List<UnifiedLibraryItem> unifiedItems;
  final List<UnifiedLibraryItem> filteredUnifiedItems;
  final List<_GroupedLocalAlbum> filteredGroupedLocalAlbums;
  final List<_LibraryAlbumGroup> orderedAlbums;
  final bool showFilteringIndicator;
  final int? totalTrackCountOverride;
  final int? totalAlbumCountOverride;

  const _FilterContentData({
    required this.unifiedItems,
    required this.filteredUnifiedItems,
    required this.filteredGroupedLocalAlbums,
    required this.orderedAlbums,
    required this.showFilteringIndicator,
    this.totalTrackCountOverride,
    this.totalAlbumCountOverride,
  });

  int get totalTrackCount =>
      totalTrackCountOverride ?? filteredUnifiedItems.length;
  int get totalAlbumCount =>
      totalAlbumCountOverride ?? filteredGroupedLocalAlbums.length;
}

class _QueueLibraryPageRequest
    implements QueueLibraryPageKey<_QueueLibraryPageRequest> {
  final String filterMode;
  @override
  final int limit;
  @override
  final int offset;
  final String searchQuery;
  final String? filterSource;
  final String? filterQuality;
  final String? filterFormat;
  final String? filterMetadata;
  final String sortMode;
  final bool localLibraryEnabled;
  final QueueLibraryDbCursor? cursor;

  const _QueueLibraryPageRequest({
    required this.filterMode,
    required this.limit,
    required this.offset,
    required this.searchQuery,
    required this.filterSource,
    required this.filterQuality,
    required this.filterFormat,
    required this.filterMetadata,
    required this.sortMode,
    required this.localLibraryEnabled,
    this.cursor,
  });

  QueueLibraryDbQuery toDbQuery() => QueueLibraryDbQuery(
    limit: limit,
    offset: offset,
    filterMode: filterMode,
    searchQuery: searchQuery,
    source: filterSource,
    quality: filterQuality,
    format: filterFormat,
    metadata: filterMetadata,
    sortMode: sortMode,
    includeLocal: localLibraryEnabled,
    cursor: cursor,
  );

  @override
  bool hasSamePageScope(_QueueLibraryPageRequest other) =>
      filterMode == other.filterMode &&
      limit == other.limit &&
      searchQuery == other.searchQuery &&
      filterSource == other.filterSource &&
      filterQuality == other.filterQuality &&
      filterFormat == other.filterFormat &&
      filterMetadata == other.filterMetadata &&
      sortMode == other.sortMode &&
      localLibraryEnabled == other.localLibraryEnabled;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is _QueueLibraryPageRequest &&
          filterMode == other.filterMode &&
          limit == other.limit &&
          offset == other.offset &&
          searchQuery == other.searchQuery &&
          filterSource == other.filterSource &&
          filterQuality == other.filterQuality &&
          filterFormat == other.filterFormat &&
          filterMetadata == other.filterMetadata &&
          sortMode == other.sortMode &&
          localLibraryEnabled == other.localLibraryEnabled &&
          cursor == other.cursor;

  @override
  int get hashCode => Object.hash(
    filterMode,
    limit,
    offset,
    searchQuery,
    filterSource,
    filterQuality,
    filterFormat,
    filterMetadata,
    sortMode,
    localLibraryEnabled,
    cursor,
  );
}

class _QueueLibraryPageData implements QueueLibraryPageContent {
  final List<UnifiedLibraryItem> items;
  final List<LocalLibraryItem> localItems;
  final List<_GroupedLocalAlbum> groupedLocalAlbums;
  final List<_LibraryAlbumGroup> orderedAlbums;
  final QueueLibraryDbCursor? nextCursor;

  const _QueueLibraryPageData({
    this.items = const [],
    this.localItems = const [],
    this.groupedLocalAlbums = const [],
    this.orderedAlbums = const [],
    this.nextCursor,
  });

  @override
  bool get isEmpty =>
      items.isEmpty &&
      localItems.isEmpty &&
      groupedLocalAlbums.isEmpty;

  factory _QueueLibraryPageData.combine(List<_QueueLibraryPageData> pages) {
    if (pages.isEmpty) return const _QueueLibraryPageData();
    if (pages.length == 1) return pages.first;

    final items = <UnifiedLibraryItem>[];
    final localItems = <LocalLibraryItem>[];
    final groupedLocalAlbums = <_GroupedLocalAlbum>[];
    final orderedAlbums = <_LibraryAlbumGroup>[];

    for (final page in pages) {
      items.addAll(page.items);
      localItems.addAll(page.localItems);
      groupedLocalAlbums.addAll(page.groupedLocalAlbums);
      orderedAlbums.addAll(page.orderedAlbums);
    }

    return _QueueLibraryPageData(
      items: items,
      localItems: localItems,
      groupedLocalAlbums: groupedLocalAlbums,
      orderedAlbums: orderedAlbums,
      nextCursor: pages.last.nextCursor,
    );
  }

  _FilterContentData toFilterContentData({
    int? totalTrackCount,
    int? totalAlbumCount,
  }) {
    return _FilterContentData(
      unifiedItems: items,
      filteredUnifiedItems: items,
      filteredGroupedLocalAlbums: groupedLocalAlbums,
      orderedAlbums: orderedAlbums,
      showFilteringIndicator: false,
      totalTrackCountOverride: totalTrackCount,
      totalAlbumCountOverride: totalAlbumCount,
    );
  }
}

/// Reads one page of the Songs list; tests replace it with in-memory rows.
final queueLibraryTrackPageReaderProvider =
    Provider<Future<QueueLibraryDbPage> Function(QueueLibraryDbQuery)>(
      (ref) => LibraryDatabase.instance.getQueueTrackPageResult,
    );

final _queueLibraryPageProvider = FutureProvider.autoDispose
    .family<_QueueLibraryPageData, _QueueLibraryPageRequest>((
      ref,
      request,
    ) async {
      if (request.localLibraryEnabled) {
        ref.watch(
          localLibraryProvider.select((state) => state.loadedIndexVersion),
        );
      }
      // Playlists render from libraryCollectionsProvider, not the DB.
      if (request.filterMode == 'playlists') {
        return const _QueueLibraryPageData();
      }
      final dbQuery = request.toDbQuery();
      if (request.filterMode == 'albums') {
        final page = await LibraryDatabase.instance.getQueueAlbumPageResult(
          dbQuery,
        );
        final rows = page.rows;
        final groupedLocalAlbums = <_GroupedLocalAlbum>[];
        final orderedAlbums = <_LibraryAlbumGroup>[];
        for (final row in rows) {
          final source = row['queue_source'] as String? ?? '';
          final latestMillis = (row['sort_added'] as num?)?.toInt() ?? 0;
          final latest = DateTime.fromMillisecondsSinceEpoch(latestMillis);
          if (source == 'local') {
            final album = _GroupedLocalAlbum(
              albumKey: row['album_key'] as String? ?? '',
              albumName: row['album_name'] as String? ?? '',
              artistName: row['artist_name'] as String? ?? '',
              coverPath: row['cover_path'] as String?,
              sampleFilePath: row['sample_file_path'] as String? ?? '',
              tracks: const [],
              trackCount: (row['track_count'] as num?)?.toInt() ?? 0,
              completeness: AlbumCompleteness.fromRow(row),
              latestScanned: latest,
            );
            groupedLocalAlbums.add(album);
            orderedAlbums.add(album);
          }
        }
        return _QueueLibraryPageData(
          groupedLocalAlbums: groupedLocalAlbums,
          orderedAlbums: orderedAlbums,
          nextCursor: page.nextCursor,
        );
      }

      final page = await ref.read(queueLibraryTrackPageReaderProvider)(
        dbQuery,
      );
      final rows = page.rows;
      final items = <UnifiedLibraryItem>[];
      final localItems = <LocalLibraryItem>[];
      for (final row in rows) {
        final source = row['source'] as String? ?? '';
        final itemJson = Map<String, dynamic>.from(row['item'] as Map);
        if (source == 'local') {
          final item = LocalLibraryItem.fromJson(itemJson);
          localItems.add(item);
          items.add(UnifiedLibraryItem.fromLocalLibrary(item));
        }
      }
      return _QueueLibraryPageData(
        items: items,
        localItems: localItems,
        nextCursor: page.nextCursor,
      );
    });
