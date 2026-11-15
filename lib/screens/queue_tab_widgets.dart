part of 'queue_tab.dart';

class _QueueItemSliverRow extends ConsumerWidget {
  final String itemId;
  final ColorScheme colorScheme;
  final Widget Function(BuildContext, DownloadItem, ColorScheme) itemBuilder;

  const _QueueItemSliverRow({
    required this.itemId,
    required this.colorScheme,
    required this.itemBuilder,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final item = ref.watch(
      downloadQueueLookupProvider.select((lookup) => lookup.byItemId[itemId]),
    );
    if (item == null) {
      return const SizedBox.shrink();
    }

    return RepaintBoundary(child: itemBuilder(context, item, colorScheme));
  }
}

enum _CollectionEntryType { wishlist, loved, favoriteArtists, playlist }

class _CollectionEntry {
  final _CollectionEntryType type;
  final int playlistIndex;

  const _CollectionEntry._(this.type, [this.playlistIndex = -1]);

  static const wishlist = _CollectionEntry._(_CollectionEntryType.wishlist);
  static const loved = _CollectionEntry._(_CollectionEntryType.loved);
  static const favoriteArtists = _CollectionEntry._(
    _CollectionEntryType.favoriteArtists,
  );
  static _CollectionEntry playlist(int index) =>
      _CollectionEntry._(_CollectionEntryType.playlist, index);
}
