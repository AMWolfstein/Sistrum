import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/controllers/clickable_metadata.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/library_browse_provider.dart';
import 'package:sistrum/providers/library_genres_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/screens/library_artist_screen.dart';
import 'package:sistrum/screens/library_directory_screen.dart';
import 'package:sistrum/screens/listening_rewind_screen.dart';
import 'package:sistrum/screens/local_album_screen.dart';
import 'package:sistrum/services/album_completeness.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/utils/nav_bar_inset.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/tv_navigation.dart';
import 'package:sistrum/widgets/app_refresh_indicator.dart';
import 'package:sistrum/widgets/album_detail_header.dart'
    show HeaderCircleButton;
import 'package:sistrum/widgets/app_sliver_header.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/app_snack_bar.dart';
import 'package:sistrum/widgets/desktop/context_menu.dart';
import 'package:sistrum/widgets/desktop/hover_play_button.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';
import 'package:sistrum/widgets/sliver_layout_info_builder.dart';
import 'package:sistrum/screens/collection_detail_screen.dart';

enum MornyeLibraryPage { overview, albums, artists, genres }

class MornyeLibraryScreen extends ConsumerStatefulWidget {
  const MornyeLibraryScreen({
    super.key,
    required this.onOpenSection,
    this.page = MornyeLibraryPage.overview,
  });

  final ValueChanged<String> onOpenSection;
  final MornyeLibraryPage page;

  @override
  ConsumerState<MornyeLibraryScreen> createState() =>
      _MornyeLibraryScreenState();
}

class _MornyeLibraryScreenState extends ConsumerState<MornyeLibraryScreen> {
  final _scroll = ScrollController();
  late String _sort;
  List<LibraryBrowseEntry> _rows = const [];
  bool _loading = true;

  bool get _overview => widget.page == MornyeLibraryPage.overview;
  bool get _artists => widget.page == MornyeLibraryPage.artists;
  bool get _genres => widget.page == MornyeLibraryPage.genres;
  LibraryBrowseRequest get _request =>
      (artists: _artists, sort: _sort, limit: 40, completeness: null);

  @override
  void initState() {
    super.initState();
    _sort = _overview || _artists || _genres
        ? 'a-z'
        : ref.read(settingsProvider).libraryAlbumSortMode;
    _scroll.addListener(() {
      // Genres arrive in one read.
      if (_scroll.position.extentAfter < 500 &&
          !_loading &&
          !_genres &&
          !_overview) {
        unawaited(
          ref.read(libraryBrowseProvider(_request).notifier).loadMore(),
        );
      }
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _openPage(MornyeLibraryPage page) {
    pushPage<void>(
      context,
      (_) => Scaffold(
        body: MornyeLibraryScreen(
          page: page,
          onOpenSection: widget.onOpenSection,
        ),
      ),
    );
  }

  void _openArtist(String artist) =>
      pushPage<void>(context, (_) => LibraryArtistScreen(artist: artist));

  Future<void> _openAlbum(LibraryBrowseEntry entry) async {
    Future<List<LocalLibraryItem>> loadTracks() => _loadLocalAlbum(entry);

    await pushPage<void>(
      context,
      (_) => LocalAlbumScreen(
        albumName: entry.name,
        artistName: entry.artist,
        coverPath: entry.cover,
        loadTracks: loadTracks,
      ),
    );
  }

  Future<List<LocalLibraryItem>> _loadLocalAlbum(
    LibraryBrowseEntry entry,
  ) async {
    final rows = await LibraryDatabase.instance.getQueueLocalAlbumTracksByKey(
      entry.key,
    );
    return rows.map(LocalLibraryItem.fromJson).toList(growable: false);
  }

  /// Desktop shows the choices as a checked menu below [buttonContext];
  /// returns null there and on phones, where the caller opens its sheet.
  Future<String?>? _chooseFromMenu(
    BuildContext buttonContext,
    List<(String, String, IconData)> options,
    String current,
  ) {
    final position = isDesktopPlatform
        ? contextMenuPositionBelow(buttonContext)
        : null;
    if (position == null) return null;
    final chosen = Completer<String?>();
    showContextMenu(
      buttonContext,
      position: position,
      groups: [
        [
          for (final option in options)
            ContextMenuAction(
              icon: option.$3,
              label: option.$2,
              checked: option.$1 == current,
              onSelected: () => chosen.complete(option.$1),
            ),
        ],
      ],
    ).whenComplete(() {
      if (!chosen.isCompleted) chosen.complete(null);
    });
    return chosen.future;
  }

  Future<void> _chooseSort(BuildContext buttonContext) async {
    final options = [
      ('a-z', context.l10n.searchSortTitleAZ, Icons.sort_by_alpha),
      if (!_artists) ...[
        (
          'year-asc',
          context.l10n.libraryFilterSortYearAsc,
          Icons.event_outlined,
        ),
        (
          'year-desc',
          context.l10n.libraryFilterSortYearDesc,
          Icons.event_outlined,
        ),
        (
          'size-desc',
          context.l10n.libraryFilterSortSizeLargest,
          Icons.storage_outlined,
        ),
        (
          'size-asc',
          context.l10n.libraryFilterSortSizeSmallest,
          Icons.storage_outlined,
        ),
      ],
    ];
    final selected =
        await (_chooseFromMenu(buttonContext, options, _sort) ??
            showAppBottomSheet<String>(
              context: context,
              builder: (context) => Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final option in options)
                    ListTile(
                      title: Text(option.$2),
                      selected: _sort == option.$1,
                      selectedColor: Theme.of(context).colorScheme.onSurface,
                      trailing: _sort == option.$1
                          ? const Icon(Icons.check)
                          : null,
                      onTap: () => Navigator.pop(context, option.$1),
                    ),
                ],
              ),
            ));
    if (!mounted || selected == null || selected == _sort) return;
    if (!_artists && !_overview) {
      ref.read(settingsProvider.notifier).setLibraryAlbumSortMode(selected);
    }
    setState(() {
      _sort = selected;
      _rows = const [];
    });
    _scroll.jumpTo(0);
  }

  Widget _buildCompletenessBadge(AlbumCompleteness completeness) {
    final colors = Theme.of(context).colorScheme;
    final expected = completeness.expected;
    final label = completeness.status == 'unknown'
        ? context.l10n.libraryAlbumCompletenessUnknown
        : expected == null
        ? context.l10n.libraryFilterIncompleteAlbums
        : context.l10n.libraryAlbumMissingTracks(
            completeness.present,
            expected,
            expected - completeness.present,
          );
    return Tooltip(
      message: label,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: colors.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(
          completeness.badge,
          style: TextStyle(
            color: colors.onSurface,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final listInset = wideListInset(context);
    // The overview lists categories only; it reads no album rows.
    final result = _overview
        ? const AsyncData(LibraryBrowseState([], hasMore: false))
        : _genres
        ? ref
              .watch(libraryGenresProvider)
              .whenData((genres) => LibraryBrowseState(genres, hasMore: false))
        : ref.watch(libraryBrowseProvider(_request));
    _loading = result.isLoading || (result.value?.isLoadingMore ?? false);
    if (result.hasValue) _rows = result.requireValue.entries;
    void reload() => _genres
        ? ref.invalidate(libraryGenresProvider)
        : ref.invalidate(libraryBrowseProvider(_request));
    final title = _overview
        ? context.l10n.navLibrary
        : _artists
        ? context.l10n.searchArtists
        : _genres
        ? context.l10n.libraryGenres
        : context.l10n.searchAlbums;
    final actions = <Widget>[
      if (!_overview && !_artists && !_genres) ...[
        Builder(
          builder: (buttonContext) => HeaderCircleButton(
            tooltip: context.l10n.searchSortTitle,
            icon: Icons.sort,
            onPressed: () => _chooseSort(buttonContext),
          ),
        ),
      ],
    ];
    return AppRefreshIndicator(
      onRefresh: () async {
        if (_overview) return;
        reload();
        await (_genres
            ? ref.read(libraryGenresProvider.future)
            : ref.read(libraryBrowseProvider(_request).future));
      },
      child: CustomScrollView(
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          if (_overview)
            AppSliverHeader.tabRoot(
              title: title,
              actions: actions,
              alignToContent: true,
            )
          else
            AppSliverHeader.page(title: title, actions: actions),
          AppRefreshIndicator.sliver(enabled: !_overview),
          if (_overview)
            SliverPadding(
              // Rows center at the reading width on tablets.
              padding: EdgeInsets.fromLTRB(
                24 + listInset,
                0,
                24 + listInset,
                20,
              ),
              sliver: SliverList.list(
                children: [
                  for (final category in [
                    (
                      Icons.queue_music,
                      context.l10n.searchPlaylists,
                      () => widget.onOpenSection('playlists'),
                    ),
                    (
                      Icons.mic_none,
                      context.l10n.searchArtists,
                      () => _openPage(MornyeLibraryPage.artists),
                    ),
                    (
                      Icons.album_outlined,
                      context.l10n.searchAlbums,
                      () => _openPage(MornyeLibraryPage.albums),
                    ),
                    (
                      Icons.music_note_outlined,
                      context.l10n.searchSongs,
                      () => widget.onOpenSection('all'),
                    ),
                    (
                      Icons.category_outlined,
                      context.l10n.libraryGenres,
                      () => _openPage(MornyeLibraryPage.genres),
                    ),
                    (
                      Icons.folder_outlined,
                      context.l10n.libraryFolders,
                      () => pushPage<void>(
                        context,
                        (_) => const LibraryDirectoryScreen(),
                      ),
                    ),
                  ]) ...[
                    // The desktop shell paints its background without a
                    // Material, which would hide the row's ink.
                    Material(
                      type: MaterialType.transparency,
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        minVerticalPadding: 12,
                        leading: Icon(
                          category.$1,
                          color: colors.primary,
                          size: 28,
                        ),
                        title: Text(
                          category.$2,
                          style: const TextStyle(fontSize: 22),
                        ),
                        trailing: Icon(
                          Icons.chevron_right,
                          color: colors.primary,
                        ),
                        onTap: category.$3,
                      ),
                    ),
                    const Divider(height: 1, indent: 48),
                  ],
                ],
              ),
            ),
          // The seasonal Rewind, beside the listening history it replays.
          if (_overview)
            SliverToBoxAdapter(
              child: ListeningRewindBanner(
                margin: EdgeInsets.fromLTRB(
                  24 + listInset,
                  0,
                  24 + listInset,
                  16,
                ),
              ),
            ),
          if (!_overview) ...[
            if (_rows.isNotEmpty)
              if (_artists || _genres)
                SliverList.builder(
                  itemCount: _rows.length,
                  itemBuilder: (context, index) {
                    final entry = _rows[index];
                    void open() => _genres
                        ? pushPage<void>(
                            context,
                            (_) => CollectionDetailScreen.genre(genre: entry),
                          )
                        : _openArtist(entry.artist);
                    final artwork = SizedBox.square(
                      dimension: 56,
                      child: _LibraryBrowseArtwork(entry: entry),
                    );
                    return Column(
                      children: [
                        DesktopContextMenuRegion(
                          onContextMenu: (position) => showContextMenu(
                            context,
                            position: position,
                            groups: [
                              [
                                ContextMenuAction(
                                  icon: Icons.open_in_new,
                                  label: context.l10n.desktopMenuOpen,
                                  onSelected: open,
                                ),
                              ],
                            ],
                          ),
                          child: ListTile(
                            contentPadding: EdgeInsets.symmetric(
                              horizontal: 24 + listInset,
                              vertical: 8,
                            ),
                            leading: _genres
                                ? ClipRRect(
                                    borderRadius: BorderRadius.circular(8),
                                    child: artwork,
                                  )
                                : ClipOval(child: artwork),
                            title: Text(_genres ? entry.name : entry.artist),
                            subtitle: Text(
                              context.l10n.queueTrackCount(entry.trackCount),
                            ),
                            trailing: Icon(
                              Icons.chevron_right,
                              color: colors.primary,
                            ),
                            onTap: open,
                          ),
                        ),
                        Divider(
                          height: 1,
                          indent: 96 + listInset,
                          endIndent: 24 + listInset,
                        ),
                      ],
                    );
                  },
                )
              else
                SliverPadding(
                  padding: EdgeInsets.symmetric(horizontal: 24 + listInset),
                  sliver: SliverLayoutInfoBuilder(
                    select: (constraints) => constraints.crossAxisExtent,
                    builder: (context, crossAxisExtent) {
                      // ~160dp covers: two on phones, more columns rather
                      // than giant covers on tablets.
                      final columns = (crossAxisExtent / 160).floor().clamp(
                        2,
                        8,
                      );
                      final width =
                          (crossAxisExtent - 20 * (columns - 1)) / columns;
                      final textHeight =
                          MediaQuery.textScalerOf(context).scale(16) * 3.2 + 24;
                      return SliverGrid.builder(
                        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: columns,
                          crossAxisSpacing: 20,
                          mainAxisSpacing: 16,
                          mainAxisExtent: width + textHeight,
                        ),
                        itemCount: _rows.length,
                        itemBuilder: (context, index) {
                          final entry = _rows[index];
                          return DesktopContextMenuRegion(
                            onContextMenu: (position) =>
                                _showAlbumMenu(entry, position),
                            child: _buildAlbumTile(entry, colors),
                          );
                        },
                      );
                    },
                  ),
                ),
            if (result.hasError || result.value?.loadMoreError != null)
              SliverToBoxAdapter(
                child: Center(
                  child: TextButton.icon(
                    onPressed: reload,
                    icon: const Icon(Icons.refresh),
                    label: Text(context.l10n.dialogRetry),
                  ),
                ),
              )
            else if (_loading)
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator.adaptive()),
                ),
              )
            else if (_rows.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: Text(context.l10n.libraryEmptyCollection)),
              ),
          ],
          const NavBarSliverSpacer(),
        ],
      ),
    );
  }

  Future<void> _playAlbum(LibraryBrowseEntry entry) async {
    try {
      await playLocalAlbum(ref, await _loadLocalAlbum(entry));
    } catch (e) {
      if (mounted) showCannotOpenFileSnackBar(context, e);
    }
  }

  void _showAlbumMenu(LibraryBrowseEntry entry, Offset position) {
    showContextMenu(
      context,
      position: position,
      groups: [
        [
          ContextMenuAction(
            icon: Icons.open_in_new,
            label: context.l10n.desktopMenuOpen,
            onSelected: () => _openAlbum(entry),
          ),
          ContextMenuAction(
            icon: Icons.play_arrow,
            label: context.l10n.tooltipPlay,
            onSelected: () => _playAlbum(entry),
          ),
        ],
      ],
    );
  }

  Widget _buildAlbumTile(LibraryBrowseEntry entry, ColorScheme colors) {
    return Semantics(
      button: true,
      child: TvTapTarget(
        behavior: HitTestBehavior.opaque,
        onTap: () => _openAlbum(entry),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: _LibraryBrowseArtwork(entry: entry),
                  ),
                  if (entry.completeness != null)
                    PositionedDirectional(
                      end: 8,
                      bottom: 8,
                      child: _buildCompletenessBadge(entry.completeness!),
                    ),
                  DesktopHoverPlayButton(
                    tooltip: context.l10n.tooltipPlay,
                    onPlay: () => _playAlbum(entry),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Text(
              entry.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 16),
            ),
            Text(
              entry.artist,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: 14, color: colors.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}

class _LibraryBrowseArtwork extends StatefulWidget {
  const _LibraryBrowseArtwork({required this.entry});
  final LibraryBrowseEntry entry;

  @override
  State<_LibraryBrowseArtwork> createState() => _LibraryBrowseArtworkState();
}

class _LibraryBrowseArtworkState extends State<_LibraryBrowseArtwork> {
  @override
  Widget build(BuildContext context) {
    final entry = widget.entry;
    Widget fallback(BuildContext context) => ColoredBox(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Center(
        child: Icon(
          Icons.album_outlined,
          size: 40,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
    return LocalLibraryArtworkBuilder(
      coverPath: entry.cover,
      filePath: entry.samplePath,
      builder: (context, cover) => cover == null
          ? fallback(context)
          : LocalCoverImage(url: cover, placeholder: fallback),
    );
  }
}
