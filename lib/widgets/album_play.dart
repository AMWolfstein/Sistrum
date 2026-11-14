import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/album_track_order.dart';
import 'package:sistrum/models/download_history.dart';
import 'package:sistrum/providers/playback_provider.dart';
import 'package:sistrum/services/history_database.dart';
import 'package:sistrum/services/library_database.dart';

/// Plays a downloaded album from its first track, in disc and track order.
Future<void> playDownloadedAlbum(
  WidgetRef ref, {
  required String albumName,
  required String artistName,
}) async {
  final rows = await HistoryDatabase.instance.getAlbumTracks(
    albumName,
    artistName,
  );
  final tracks = AlbumTrackOrder(
    rows.map(DownloadHistoryItem.fromJson),
    discNumber: (track) => track.discNumber,
    trackNumber: (track) => track.trackNumber,
    trackName: (track) => track.trackName,
  ).tracks;
  if (tracks.isEmpty) return;
  await ref
      .read(playbackProvider.notifier)
      .playHistoryQueue(tracks, startItem: tracks.first);
}

/// Plays a scanned album from its first track, in disc and track order.
Future<void> playLocalAlbum(
  WidgetRef ref,
  Iterable<LocalLibraryItem> items,
) async {
  final tracks = AlbumTrackOrder(
    items,
    discNumber: (track) => track.discNumber,
    trackNumber: (track) => track.trackNumber,
    trackName: (track) => track.trackName,
  ).tracks;
  if (tracks.isEmpty) return;
  await ref
      .read(playbackProvider.notifier)
      .playLocalLibraryQueue(tracks, startItem: tracks.first);
}
