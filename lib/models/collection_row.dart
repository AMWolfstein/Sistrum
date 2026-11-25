import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';

/// One song a collection page lists. [library] is the Library song behind a
/// genre's row: its file plays, and its menu acts on it.
class CollectionRow {
  const CollectionRow({required this.key, required this.track, this.library});

  final String key;
  final Track track;
  final UnifiedLibraryItem? library;
}
