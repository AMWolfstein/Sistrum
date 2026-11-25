import 'package:cupertino_ui/cupertino_ui.dart' show CupertinoIcons;
import 'package:material_ui/material_ui.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/utils/artist_release_groups.dart';
import 'package:sistrum/widgets/cached_cover_image.dart';

/// The artist's latest release, with its year and track count.
class ArtistFeaturedAlbumCard extends StatelessWidget {
  const ArtistFeaturedAlbumCard({
    super.key,
    required this.release,
    required this.onTap,
  });

  final ArtistRelease release;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final caption = TextStyle(
      fontSize: 13,
      height: 1.25,
      color: Colors.white.withValues(alpha: 0.7),
    );
    final year = release.releasedOn?.year;
    final summary = [
      ?year?.toString(),
      if (release.totalTracks > 0) l10n.tracksCount(release.totalTracks),
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
                    child: LocalCoverImage(
                      url: release.coverPath ?? '',
                      placeholder: (_) =>
                          const Icon(CupertinoIcons.square_stack, size: 36),
                    ),
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
                        release.albumName,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      if (summary.isNotEmpty) ...[
                        const SizedBox(height: 2),
                        Text(summary, maxLines: 1, style: caption),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
