import 'package:flutter/material.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/widgets/app_icon.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';
import 'package:sistrum/widgets/local_library_artwork_builder.dart';

/// A song's artwork: the download's cover, or the scanned file's thumbnail
/// with its embedded artwork recovered when the thumbnail is gone.
class LibrarySongArtwork extends StatelessWidget {
  const LibrarySongArtwork({super.key, required this.item, required this.size});

  final UnifiedLibraryItem item;
  final double size;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    Widget placeholder(BuildContext context) => ColoredBox(
      color: colorScheme.surfaceContainerHighest,
      child: Center(
        child: AppIcon.adaptive(
          Icons.music_note,
          size: size * 0.45,
          color: colorScheme.onSurfaceVariant,
        ),
      ),
    );
    Widget image(String? cover) => cover == null || cover.isEmpty
        ? placeholder(context)
        : LocalOrNetworkCoverImage(
            url: cover,
            localCacheWidth: (size * MediaQuery.devicePixelRatioOf(context))
                .round(),
            placeholder: placeholder,
          );
    final local = item.localItem;
    return ClipRRect(
      borderRadius: BorderRadius.circular(size > 40 ? 8 : 4),
      child: SizedBox.square(
        dimension: size,
        child: local == null
            ? image(item.coverUrl)
            : LocalLibraryArtworkBuilder(
                coverPath: local.coverPath,
                filePath: local.filePath,
                builder: (context, cover) => image(cover),
              ),
      ),
    );
  }
}
