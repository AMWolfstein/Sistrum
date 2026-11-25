import 'package:material_ui/material_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/l10n/l10n.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';

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
