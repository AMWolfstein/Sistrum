import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/providers/download_history_provider.dart';
import 'package:sistrum/providers/library_search_provider.dart';
import 'package:sistrum/providers/local_library_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_lyrics_search.dart';
import 'package:sistrum/services/library_search.dart';
import 'package:sistrum/services/playback_metadata.dart';
import 'package:sistrum/services/source_metadata_events.dart';

final FutureProvider<LibraryLyricsSearchStore>
libraryLyricsSearchStoreProvider = FutureProvider<LibraryLyricsSearchStore>((
  ref,
) async {
  final store = LibraryLyricsSearchStore(
    await LibraryDatabase.instance.openLyricsSearchDatabase(),
    readLyrics: (path) async {
      final metadata = await readPlaybackFileMetadataWithRetry(
        path,
        retryDelays: const [Duration.zero],
      );
      return metadata['lyrics']?.toString() ?? '';
    },
  );
  await store.open();
  if (ref.mounted) {
    ref.onDispose(
      SourceMetadataEvents.instance.subscribe((path) async {
        await store.invalidate(path);
        if (ref.mounted) ref.invalidate(libraryLyricsIndexProvider);
      }),
    );
  }
  return store;
});

/// Query changes reuse this pass. Removing the Lyrics filter cancels it after
/// the current native read, so typing never starts concurrent Library scans.
final libraryLyricsIndexProvider =
    StreamProvider.autoDispose<LibraryLyricsIndexProgress>((ref) async* {
      var cancelled = false;
      ref.onDispose(() => cancelled = true);
      final includeLocal = ref.watch(
        settingsProvider.select((s) => s.localLibraryEnabled),
      );
      ref.watch(downloadHistoryProvider.select((s) => s.loadedIndexVersion));
      if (includeLocal) {
        ref.watch(localLibraryProvider.select((s) => s.loadedIndexVersion));
      }
      final store = await ref.watch(libraryLyricsSearchStoreProvider.future);
      if (cancelled) return;
      yield* store.index(
        includeLocal: includeLocal,
        cancelled: () => cancelled,
      );
    });

final libraryLyricsSearchProvider = FutureProvider.autoDispose
    .family<List<LibrarySearchHit>, LibrarySearchRequest>((ref, request) async {
      if (LibrarySearchQuery(request.query).terms.isEmpty) return [];
      final includeLocal = ref.watch(
        settingsProvider.select((s) => s.localLibraryEnabled),
      );
      final progress = ref.watch(libraryLyricsIndexProvider);
      if (progress.hasError) {
        Error.throwWithStackTrace(progress.error!, progress.stackTrace!);
      }
      final store = await ref.watch(libraryLyricsSearchStoreProvider.future);
      return store.search(
        query: request.query,
        includeLocal: includeLocal,
        limit: request.limit,
        offset: request.offset,
      );
    });
