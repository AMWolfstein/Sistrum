import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/utils/desktop_platform.dart';
import 'package:sistrum/widgets/album_detail_header.dart';

/// Love all and add to playlist buttons of a track collection header. Both
/// are disabled while [tracks] is empty.
class CollectionHeaderActions extends StatelessWidget {
  const CollectionHeaderActions({
    super.key,
    required this.tracks,
    required this.onLoveAll,
    required this.addToPlaylistIcon,
    required this.onAddToPlaylist,
  });

  final List<Track> tracks;
  final VoidCallback onLoveAll;
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
    final addToPlaylist = HeaderCircleButton(
      icon: addToPlaylistIcon,
      tonal: true,
      tooltip: context.l10n.tooltipAddToPlaylist,
      onPressed: enabled ? onAddToPlaylist : null,
    );
    if (isDesktopPlatform) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 10,
        children: [love, addToPlaylist],
      );
    }
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [love, const SizedBox(width: 16), addToPlaylist],
    );
  }
}
