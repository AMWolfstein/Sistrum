import 'package:flutter/cupertino.dart' show CupertinoIcons;
import 'package:flutter/material.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/artist_album.dart';
import 'package:sistrum/widgets/album_detail_header.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

/// The artist's newest album, described by its type, year and track count,
/// with one action: play.
class ArtistFeaturedAlbumCard extends StatelessWidget {
  const ArtistFeaturedAlbumCard({
    super.key,
    required this.album,
    required this.onTap,
    required this.onPlay,
  });

  final ArtistAlbum album;
  final VoidCallback onTap;
  final VoidCallback onPlay;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final caption = TextStyle(
      fontSize: 13,
      height: 1.25,
      color: Colors.white.withValues(alpha: 0.7),
    );
    final summary = [
      switch (album.albumType) {
        'ep' => l10n.releaseTypeEp,
        'single' => l10n.releaseTypeSingle,
        _ => l10n.recentTypeAlbum,
      },
      if (album.releaseDate.isNotEmpty) album.releaseDate.split('-').first,
      if (album.totalTracks > 0) l10n.tracksCount(album.totalTracks),
    ].join(' · ');
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 28, 20, 0),
      child: Material(
        color: Colors.white.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(18),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: SizedBox.square(
                    dimension: 96,
                    child: album.coverUrl != null
                        ? CachedCoverImage(
                            imageUrl: album.coverUrl!,
                            fit: BoxFit.cover,
                          )
                        : const Icon(CupertinoIcons.square_stack, size: 36),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.artistLatestRelease, style: caption),
                      const SizedBox(height: 2),
                      Text(
                        album.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        summary,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: caption,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                HeaderCircleButton(
                  icon: CupertinoIcons.play_fill,
                  buttonSize: 32,
                  iconSize: 16,
                  tonal: true,
                  tooltip: l10n.tooltipPlay,
                  onPressed: onPlay,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
