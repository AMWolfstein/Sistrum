import 'package:material_ui/material_ui.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/collection_header_actions.dart';
import 'package:sistrum/controllers/selection_mode_mixin.dart';
import 'package:sistrum/widgets/collection_scaffold.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/utils/image_cache_utils.dart';
import 'package:sistrum/utils/adaptive_layout.dart';
import 'package:sistrum/widgets/playlist_picker_sheet.dart';
import 'package:sistrum/widgets/animation_utils.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/motion_header_banner.dart';
import 'package:sistrum/controllers/local_playback.dart';
import 'package:sistrum/widgets/track_detail_actions.dart';
import 'package:sistrum/widgets/track_list_tile.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/desktop/desktop_selection.dart';
import 'package:sistrum/widgets/desktop/track_list_table.dart';
import 'package:sistrum/controllers/collapsing_header_scroll_mixin.dart';
import 'package:sistrum/theme/mornye_theme.dart';
import 'package:sistrum/widgets/mornye_artist_header.dart';

class PlaylistScreen extends ConsumerStatefulWidget {
  final String playlistName;
  final String? coverUrl;
  final String? headerVideoUrl;
  final List<Track> tracks;
  final String? playlistId;
  final String? metadataProviderId;
  final String? recommendedService;

  const PlaylistScreen({
    super.key,
    required this.playlistName,
    this.coverUrl,
    this.headerVideoUrl,
    required this.tracks,
    this.playlistId,
    this.metadataProviderId,
    this.recommendedService,
  });

  @override
  ConsumerState<PlaylistScreen> createState() => _PlaylistScreenState();
}

class _PlaylistScreenState extends ConsumerState<PlaylistScreen>
    with
        SelectionModeMixin<PlaylistScreen>,
        CollapsingHeaderScrollMixin<PlaylistScreen> {
  /// Double click and Enter actions of the desktop table rows, by row id.
  final Map<String, VoidCallback> _desktopActivations = {};
  List<Track> get _tracks => widget.tracks;
  String get _playlistName => widget.playlistName;
  String? get _coverUrl => widget.coverUrl;
  String? get _headerVideoUrl => widget.headerVideoUrl;

  /// Playlists can legitimately contain the same track twice, so selection is
  /// keyed by position as well as id (matching the album screen).
  String _trackSelectionId(Track track, int index) => '${track.id}#$index';

  @override
  Widget build(BuildContext context) {
    if (context.isMornye) {
      return MornyeArtistSurface(
        imageSource: _coverUrl,
        neutralActions: true,
        child: Builder(builder: _buildPage),
      );
    }
    return _buildPage(context);
  }

  Widget _buildPage(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    final ids = [
      for (var index = 0; index < _tracks.length; index++)
        _trackSelectionId(_tracks[index], index),
    ];
    pruneSelection(ids.toSet());

    return DesktopListScope(
      onSelectAll: ids.isEmpty ? null : () => selectAll(ids),
      onClearSelection: isSelectionMode ? exitSelectionMode : null,
      onActivate: () => _desktopActivations[selectionAnchorId]?.call(),
      child: CollectionScaffold(
        scrollController: scrollController,
        isSelectionMode: isSelectionMode,
        onExitSelectionMode: exitSelectionMode,
        appBar: _buildAppBar(context, colorScheme),
        slivers: [
          // The desktop table fades in once over its skeleton's place;
          // phone rows stagger in on their own.
          _buildTrackList(context, colorScheme),
          _buildPlaylistFooter(context, colorScheme),
        ],
      ),
    );
  }

  Widget _buildAppBar(BuildContext context, ColorScheme colorScheme) {
    final expandedHeight = calculateExpandedHeight(context);
    final cacheWidth = coverCacheWidthForViewport(context);
    final motionUrl = _headerVideoUrl;
    final hasMotion =
        motionUrl != null &&
        motionUrl.trim().isNotEmpty &&
        Uri.tryParse(motionUrl)?.hasAuthority == true;
    Widget playlistPlaceholder({double? size}) {
      return Container(
        color: colorScheme.surfaceContainerHighest,
        child: Icon(
          Icons.playlist_play,
          size: size ?? 80,
          color: colorScheme.onSurfaceVariant,
        ),
      );
    }

    final Widget coverImage = _coverUrl != null
        ? LocalCoverImage(
            url: _coverUrl!,
            fit: BoxFit.cover,
            localCacheWidth: cacheWidth,
            placeholder: (_) => Container(color: colorScheme.surface),
          )
        : playlistPlaceholder();

    return AlbumDetailHeader(
      title: _playlistName,
      immersive: context.isMornye,
      squareArtwork: !hasMotion,
      expandedHeight: expandedHeight,
      showTitleInAppBar: false,
      showTitleListenable: showTitleNotifier,
      background: hasMotion
          ? MotionHeaderBanner(videoUrl: motionUrl, fallback: coverImage)
          : coverImage,
      paletteSource: _coverUrl,
      blurAndScrimBackground: !hasMotion && _coverUrl != null,
      coverBuilder: hasMotion
          ? null
          : (context, coverSize) => _coverUrl != null
                ? LocalCoverImage(
                    url: _coverUrl!,
                    fit: BoxFit.cover,
                    localCacheWidth: cacheWidth,
                    placeholder: (_) => playlistPlaceholder(),
                  )
                : playlistPlaceholder(size: 48),
      meta: HeaderMetaRow(
        items: [
          HeaderMetaItem(
            context.l10n.tracksCount(_tracks.length),
            icon: Icons.playlist_play,
          ),
        ],
      ),
      actions: CollectionHeaderActions(
        tracks: _tracks,
        onLoveAll: () => loveAllTracks(this.context, ref, _tracks),
        addToPlaylistIcon: Icons.playlist_add,
        onAddToPlaylist: () => showAddTracksToPlaylistSheet(
          context,
          ref,
          _tracks,
          playlistNamePrefill: widget.playlistName,
          preserveOccurrences: true,
        ),
      ),
    );
  }

  Widget _buildPlaylistFooter(BuildContext context, ColorScheme colorScheme) =>
      buildTrackListFooter(
        context,
        colorScheme,
        _tracks,
        excludeEpochReleaseDate: true,
      );

  Widget _buildTrackList(BuildContext context, ColorScheme colorScheme) {
    if (_tracks.isEmpty) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Center(
            child: Text(
              context.l10n.errorNoTracksFound,
              style: TextStyle(color: colorScheme.onSurfaceVariant),
            ),
          ),
        ),
      );
    }

    if (isDesktopPlatform) {
      final ids = [
        for (var i = 0; i < _tracks.length; i++)
          _trackSelectionId(_tracks[i], i),
      ];
      final tracks = _tracks;
      _desktopActivations
        ..clear()
        ..addAll({
          for (var i = 0; i < ids.length; i++)
            ids[i]: () => playLocalIfAvailable(context, ref, tracks[i]),
        });
      return TrackListTable(
        tracks: tracks,
        ids: ids,
        selectedIds: selectedIds,
        onSelect: applyDesktopClick,
        onActivate: (index) =>
            playLocalIfAvailable(context, ref, tracks[index]),
        showArtwork: true,
        showAlbum: true,
      );
    }
    return SliverPadding(
      padding: EdgeInsets.symmetric(horizontal: wideListInset(context)),
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate((context, index) {
          final track = _tracks[index];
          final selectionId = _trackSelectionId(track, index);
          return KeyedSubtree(
            key: ValueKey(track.id),
            child: StaggeredListItem(
              index: index,
              child: TrackListTile(
                track: track,
                isSelectionMode: isSelectionMode,
                isSelected: selectedIds.contains(selectionId),
                onToggleSelection: () => toggleSelection(selectionId),
                onEnterSelectionMode: () => enterSelectionMode(selectionId),
                leadingArtwork: true,
                leading: track.coverUrl != null
                    ? LocalCoverImage(
                        url: track.coverUrl!,
                        width: 48,
                        height: 48,
                        borderRadius: BorderRadius.circular(8),
                        placeholder: (_) => const SizedBox(width: 48, height: 48),
                      )
                    : Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Theme.of(
                            context,
                          ).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Icon(
                          Icons.music_note,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
              ),
            ),
          );
        }, childCount: _tracks.length),
      ),
    );
  }
}
