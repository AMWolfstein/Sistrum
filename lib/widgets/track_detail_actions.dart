import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';
import 'package:sistrum/utils/adaptive_layout.dart';

/// "dd/MM/yyyy" or "MM/yyyy" formatting for a backend release date string.
String formatReleaseDate(String date) {
  if (date.length >= 10) {
    final parts = date.substring(0, 10).split('-');
    if (parts.length == 3) {
      return '${parts[2]}/${parts[1]}/${parts[0]}';
    }
  } else if (date.length >= 7) {
    final parts = date.split('-');
    if (parts.length >= 2) {
      return '${parts[1]}/${parts[0]}';
    }
  }
  return date;
}

/// Shared release-date/track-count/duration footer for album/playlist detail
/// screens. Set [excludeEpochReleaseDate] to hide a `1970-...` placeholder
/// release date (playlists can carry one; albums never do).
Widget buildTrackListFooter(
  BuildContext context,
  ColorScheme colorScheme,
  List<Track> tracks, {
  bool excludeEpochReleaseDate = false,
}) {
  if (tracks.isEmpty) {
    return const SliverToBoxAdapter(child: SizedBox.shrink());
  }

  final releaseDate = tracks.first.releaseDate;
  final totalSeconds = tracks.fold<int>(
    0,
    (sum, t) => sum + (t.duration > 0 ? t.duration : 0),
  );
  final totalMinutes = (totalSeconds / 60).round();

  final lines = <String>[];
  final hasReleaseDate =
      releaseDate != null &&
      releaseDate.isNotEmpty &&
      (!excludeEpochReleaseDate || !releaseDate.startsWith('1970'));
  if (hasReleaseDate) {
    lines.add(formatReleaseDate(releaseDate));
  }
  final countText = context.l10n.tracksCount(tracks.length);
  lines.add(totalMinutes > 0 ? '$countText • $totalMinutes min' : countText);

  // Lines up with the track list, which centers on tablets.
  final inset = wideListInset(context);
  return SliverToBoxAdapter(
    child: Padding(
      padding: EdgeInsets.fromLTRB(20 + inset, 8, 20 + inset, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final line in lines)
            Padding(
              padding: const EdgeInsets.only(bottom: 2),
              child: Text(
                line,
                style: TextStyle(
                  color: colorScheme.onSurfaceVariant,
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
        ],
      ),
    ),
  );
}

/// Shared "love all / unlove all" batch toggle for album/playlist detail
/// screens.
Future<void> loveAllTracks(
  BuildContext context,
  WidgetRef ref,
  List<Track> tracks,
) async {
  if (tracks.isEmpty) return;
  final result = await ref
      .read(libraryCollectionsProvider.notifier)
      .toggleLovedTracks(tracks);
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          result.removed
              ? context.l10n.snackbarRemovedTracksFromLoved(result.count)
              : context.l10n.snackbarAddedTracksToLoved(result.count),
        ),
      ),
    );
  }
}
