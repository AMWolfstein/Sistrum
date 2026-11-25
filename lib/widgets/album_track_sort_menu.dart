import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/providers/album_track_sort_provider.dart';
import 'package:sistrum/widgets/app_bottom_sheet.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/sort_header_button.dart';

/// The album's sort options for a [SortHeaderButton]. [songs] are the album's
/// titles as the page shows them, for the custom order sheet.
List<List<SortOption<AlbumTrackSort>>> albumTrackSortGroups(
  BuildContext context, {
  required AlbumTrackSortKey key,
  required List<String> songs,
}) {
  final l10n = context.l10n;
  return [
    [
      SortOption(
        value: AlbumTrackSort.album,
        label: l10n.albumSortAlbumOrder,
        icon: Icons.format_list_numbered,
      ),
      SortOption(
        value: AlbumTrackSort.mostPlayed,
        label: l10n.albumSortMostPlayed,
        icon: Icons.trending_up,
      ),
      SortOption(
        value: AlbumTrackSort.custom,
        label: l10n.albumSortCustom,
        icon: Icons.swap_vert,
        onChoose: (context) =>
            showAlbumCustomOrderSheet(context, key: key, songs: songs),
      ),
    ],
  ];
}

/// Opens the album's songs with drag handles. Every drop applies at once, so
/// the sheet has nothing to save.
Future<void> showAlbumCustomOrderSheet(
  BuildContext context, {
  required AlbumTrackSortKey key,
  required List<String> songs,
}) {
  final navigator = Navigator.of(context, rootNavigator: true);
  return showAppScrollableBottomSheet<void>(
    context: navigator.context,
    title: context.l10n.albumSortCustom,
    builder: (sheetContext, controller) =>
        _CustomOrderList(albumKey: key, songs: songs, controller: controller),
  );
}

class _CustomOrderList extends ConsumerStatefulWidget {
  const _CustomOrderList({
    required this.albumKey,
    required this.songs,
    required this.controller,
  });

  final AlbumTrackSortKey albumKey;
  final List<String> songs;
  final ScrollController controller;

  @override
  ConsumerState<_CustomOrderList> createState() => _CustomOrderListState();
}

class _CustomOrderListState extends ConsumerState<_CustomOrderList> {
  // Numbered, since an album can repeat a title.
  late final _songs = widget.songs.indexed.toList();

  void _apply() {
    ref.read(albumTrackCustomOrdersProvider.notifier).setCustom(
      widget.albumKey,
      [for (final (_, song) in _songs) song],
    );
    ref
        .read(albumTrackSortsProvider.notifier)
        .setSort(albumTrackSortScope(widget.albumKey), AlbumTrackSort.custom);
  }

  void _move(int from, int to) {
    setState(() {
      _songs.insert(to, _songs.removeAt(from));
    });
    _apply();
  }

  @override
  void initState() {
    super.initState();
    // Opening the sheet selects the order it shows.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _apply();
    });
  }

  @override
  Widget build(BuildContext context) => ReorderableListView.builder(
    scrollController: widget.controller,
    buildDefaultDragHandles: false,
    padding: const EdgeInsets.only(bottom: 16),
    itemCount: _songs.length,
    onReorderItem: _move,
    itemBuilder: (context, index) => ListTile(
      key: ValueKey('album-order-${_songs[index].$1}'),
      title: Text(
        _songs[index].$2,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: ReorderableDragStartListener(
        index: index,
        child: const SizedBox.square(
          dimension: 44,
          child: AppIcon(Icons.drag_handle),
        ),
      ),
    ),
  );
}
