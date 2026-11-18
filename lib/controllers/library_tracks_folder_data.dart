import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/library_collections_provider.dart';

/// Collection providers replace their track list when its contents change.
/// Keep derived data across selection/layout rebuilds of the same snapshot.
class LibraryTracksFolderData {
  List<CollectionTrackEntry>? _entries;
  Set<String> keys = const {};
  List<Track> tracks = const [];
  List<Track>? _newestFirstTracks;

  /// The tracks by `addedAt`, newest first.
  List<Track> get newestFirstTracks => _newestFirstTracks ??= [
    for (final entry in [
      ...?_entries,
    ]..sort((a, b) => b.addedAt.compareTo(a.addedAt)))
      entry.track,
  ];

  void update(List<CollectionTrackEntry> entries) {
    if (identical(entries, _entries)) return;
    _entries = entries;
    keys = Set.unmodifiable(entries.map((entry) => entry.key));
    tracks = List.unmodifiable(entries.map((entry) => entry.track));
    _newestFirstTracks = null;
  }
}
