import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/services/library_database.dart';

/// The Library count request. Equal requests share one count, so the Library
/// badges, Home and the Home mixes read the whole Library once per change.
class LibraryCountsRequest {
  final bool localLibraryEnabled;

  const LibraryCountsRequest({required this.localLibraryEnabled});

  QueueLibraryDbQuery toDbQuery() =>
      QueueLibraryDbQuery(includeLocal: localLibraryEnabled);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LibraryCountsRequest &&
          localLibraryEnabled == other.localLibraryEnabled;

  @override
  int get hashCode => localLibraryEnabled.hashCode;
}

/// Track, album and single counts, taken again when downloads or the scanned
/// Library change. The scanned side follows the index revision, so a count
/// does not load the Library index ahead of its startup warmup.
final libraryCountsProvider = FutureProvider.autoDispose
    .family<QueueLibraryCounts, LibraryCountsRequest>((ref, request) async {
      if (request.localLibraryEnabled) {
        ref.watch(localLibraryIndexRevisionProvider);
      }
      return LibraryDatabase.instance.getQueueCounts(request.toDbQuery());
    });
