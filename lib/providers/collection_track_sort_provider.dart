import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/collection_row.dart';
import 'package:sistrum/providers/persisted_sort_choices.dart';
import 'package:sistrum/services/listening_statistics.dart';
import 'package:sistrum/utils/natural_compare.dart';

/// How a playlist, Loved or a genre lists its songs. [custom] is the saved
/// order of a playlist or Loved, the one Custom order changes.
enum CollectionTrackSort { custom, title, artist, album, mostPlayed }

/// The key of a user playlist in [collectionTrackSortsProvider]; Loved uses
/// `loved` and a genre `genre:<key>`.
String playlistTrackSortKey(String playlistId) => 'playlist:$playlistId';

/// [rows] as [sort] lists them. Names compare as a file manager does and
/// ties keep the given order. An album lists its songs by disc and number,
/// and most played by the plays in [plays] (see [playCountKey]).
List<CollectionRow> sortCollectionRows(
  List<CollectionRow> rows,
  CollectionTrackSort sort,
  Map<String, int> plays,
) {
  if (sort == CollectionTrackSort.custom || rows.length < 2) return rows;
  int byName(String a, String b) => compareNaturally(a.trim(), b.trim());
  int compare(CollectionRow a, CollectionRow b) {
    final x = a.track;
    final y = b.track;
    switch (sort) {
      case CollectionTrackSort.custom:
        return 0;
      case CollectionTrackSort.title:
        return byName(x.name, y.name);
      case CollectionTrackSort.artist:
        final artist = byName(x.artistName, y.artistName);
        return artist != 0 ? artist : byName(x.name, y.name);
      case CollectionTrackSort.album:
        final album = byName(x.albumName, y.albumName);
        if (album != 0) return album;
        final disc = (x.discNumber ?? 1).compareTo(y.discNumber ?? 1);
        if (disc != 0) return disc;
        return (x.trackNumber ?? 999).compareTo(y.trackNumber ?? 999);
      case CollectionTrackSort.mostPlayed:
        final played = (plays[playCountKey(y.name, y.artistName)] ?? 0)
            .compareTo(plays[playCountKey(x.name, x.artistName)] ?? 0);
        return played != 0 ? played : byName(x.name, y.name);
    }
  }

  final indexed = rows.indexed.toList()
    ..sort((a, b) {
      final order = compare(a.$2, b.$2);
      return order != 0 ? order : a.$1.compareTo(b.$1);
    });
  return [for (final (_, row) in indexed) row];
}

/// Plays of every song, for [CollectionTrackSort.mostPlayed]. Read once each
/// time the page opens, after pending listening is written.
final collectionSongPlaysProvider =
    FutureProvider.autoDispose<Map<String, int>>((ref) async {
      await listeningRecorder.flush();
      return ListeningStatisticsStore.instance.read(readSongPlays);
    });

/// The collections whose songs are not listed in their saved order.
final collectionTrackSortsProvider =
    NotifierProvider<
      SortChoices<CollectionTrackSort>,
      Map<String, CollectionTrackSort>
    >(
      () => SortChoices(
        const SortChoiceStore(
          prefsKey: 'collection_track_sorts',
          values: CollectionTrackSort.values,
          fallback: CollectionTrackSort.custom,
        ),
      ),
    );
