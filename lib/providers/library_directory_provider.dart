import 'dart:isolate';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_directory_index.dart';

/// Libraries up to this many songs are arranged on the UI isolate, where a
/// background isolate would cost more than the work itself.
const _backgroundIndexThreshold = 3000;

/// The Library's songs by folder. Rebuilt when downloads or the scanned
/// Library change, or the scanned Library is turned on or off.
final libraryDirectoryIndexProvider =
    FutureProvider.autoDispose<LibraryDirectoryIndex>((ref) async {
      final includeLocal = ref.watch(
        settingsProvider.select((s) => s.localLibraryEnabled),
      );
      if (includeLocal) {
        ref.watch(
          localLibraryProvider.select((state) => state.loadedIndexVersion),
        );
      }
      final rows = await LibraryDatabase.instance.getDirectoryEntries(
        includeLocal: includeLocal,
      );
      final entries = rows
          .map(LibraryDirectoryEntry.fromRow)
          .toList(growable: false);
      if (entries.length <= _backgroundIndexThreshold) {
        return LibraryDirectoryIndex.build(entries);
      }
      return Isolate.run(() => LibraryDirectoryIndex.build(entries));
    });

/// Reads the Library records of folder songs, in the order given.
typedef LibraryDirectoryItemsLoader =
    Future<List<UnifiedLibraryItem>> Function(
      Iterable<LibraryDirectoryTrack> tracks,
    );

/// How folder songs are read: from the Library database.
final libraryDirectoryItemsLoaderProvider =
    Provider<LibraryDirectoryItemsLoader>((ref) => loadLibraryDirectoryItems);

/// The songs directly in the folder with the given key, in folder order.
final libraryDirectoryTracksProvider = FutureProvider.autoDispose
    .family<List<UnifiedLibraryItem>, String>((ref, key) async {
      final index = await ref.watch(libraryDirectoryIndexProvider.future);
      final folder = index[key];
      if (folder == null) return const [];
      return ref.read(libraryDirectoryItemsLoaderProvider)(folder.tracks);
    });

/// The Library records of [tracks], in the same order. Songs removed since
/// the index was built are left out.
Future<List<UnifiedLibraryItem>> loadLibraryDirectoryItems(
  Iterable<LibraryDirectoryTrack> tracks,
) async {
  final list = tracks.toList(growable: false);
  final scanned = {
    for (final row in await LibraryDatabase.instance.getByIds([
      for (final track in list)
        if (track.source == 'local') track.id,
    ]))
      row['id'] as String: row,
  };
  UnifiedLibraryItem? item(LibraryDirectoryTrack track) {
    final row = track.source == 'local' ? scanned[track.id] : null;
    return row == null
        ? null
        : UnifiedLibraryItem.fromLocalLibrary(LocalLibraryItem.fromJson(row));
  }

  return [for (final track in list) ?item(track)];
}
