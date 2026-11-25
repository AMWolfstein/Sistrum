import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/collection_track_sort_provider.dart';
import 'package:sistrum/widgets/sort_header_button.dart';

/// The sort options of a collection's header, for a [SortHeaderButton].
/// Playlists and Loved pass [onCustomOrder], which leads the list with
/// Custom order; a genre has no saved order and passes none. Owner-provisional:
/// change the lists here.
List<List<SortOption<CollectionTrackSort>>> collectionTrackSortGroups(
  BuildContext context, {
  VoidCallback? onCustomOrder,
}) {
  final l10n = context.l10n;
  return [
    if (onCustomOrder != null)
      [
        SortOption(
          value: CollectionTrackSort.custom,
          label: l10n.albumSortCustom,
          icon: Icons.swap_vert,
          onChoose: (_) => onCustomOrder(),
        ),
      ],
    [
      SortOption(
        value: CollectionTrackSort.title,
        label: l10n.searchSortTitleAZ,
        icon: Icons.sort_by_alpha,
      ),
      SortOption(
        value: CollectionTrackSort.artist,
        label: l10n.searchSortArtistAZ,
        icon: Icons.person_outline,
      ),
      SortOption(
        value: CollectionTrackSort.album,
        label: l10n.libraryFilterSortAlbumAsc,
        icon: Icons.album_outlined,
      ),
      SortOption(
        value: CollectionTrackSort.mostPlayed,
        label: l10n.albumSortMostPlayed,
        icon: Icons.trending_up,
      ),
    ],
  ];
}
