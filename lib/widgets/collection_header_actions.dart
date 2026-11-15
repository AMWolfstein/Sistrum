import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

/// Love all, download all and add to playlist buttons of a track collection
/// while [tracks] is empty.
class CollectionHeaderActions extends StatelessWidget {
  const CollectionHeaderActions({
    super.key,
    required this.tracks,
    required this.onLoveAll,
    required this.onDownloadAll,
    required this.addToPlaylistIcon,
    required this.onAddToPlaylist,
    this.tonalDownload = false,
  });

  final List<Track> tracks;
  final VoidCallback onLoveAll;

  final VoidCallback onDownloadAll;
  final bool tonalDownload;
  final IconData addToPlaylistIcon;
  final VoidCallback onAddToPlaylist;

  @override
  Widget build(BuildContext context) {
    final enabled = tracks.isNotEmpty;
    final love = Consumer(
      builder: (context, ref, _) {
        ref.watch(libraryCollectionsProvider.select((state) => state.loved));
        final collectionsState = ref.read(libraryCollectionsProvider);
        final allLoved =
            enabled && tracks.every((t) => collectionsState.isLoved(t));
        return HeaderCircleButton(
          icon: allLoved ? Icons.favorite : Icons.favorite_border,
          tonal: true,
          iconColor: allLoved ? Theme.of(context).colorScheme.error : null,
          tooltip: allLoved
              ? context.l10n.trackOptionRemoveFromLoved
              : context.l10n.tooltipLoveAll,
          onPressed: enabled ? onLoveAll : null,
        );
      },
    );
    final download = HeaderFilledButton(
      icon: CupertinoIcons.arrow_down_circle_fill,
      tonal: tonalDownload,
      label: context.l10n.dialogDownload,
      onPressed: enabled ? onDownloadAll : null,
    );
    final addToPlaylist = HeaderCircleButton(
      icon: addToPlaylistIcon,
      tonal: true,
      tooltip: context.l10n.tooltipAddToPlaylist,
      onPressed: enabled ? onAddToPlaylist : null,
    );
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        love,
        const SizedBox(width: 16),
        Flexible(child: SizedBox(width: 172, child: download)),
        const SizedBox(width: 16),
        addToPlaylist,
      ],
    );
  }
}
