
import 'package:flutter_test/flutter_test.dart';

import 'package:sistrum/controllers/queue_library_refresh_policy.dart';
import 'package:sistrum/services/library_database.dart';

void main() {
  const empty = QueueLibraryCounts(
    allTrackCount: 0,
    albumCount: 0,
    singleTrackCount: 0,
  );
  const cached = QueueLibraryCounts(
    allTrackCount: 20,
    albumCount: 2,
    singleTrackCount: 3,
  );
  const fallback = QueueLibraryCounts(
    allTrackCount: 15,
    albumCount: 1,
    singleTrackCount: 4,
  );

  test('keeps cached counts when a download refresh returns empty', () {
    final resolved = resolveQueueLibraryCountsSnapshot(
      current: empty,
      cached: cached,
      activeDownloadFallback: fallback,
    );

    expect(identical(resolved, cached), isTrue);
  });

  test('uses in-memory history when no page cache exists yet', () {
    final resolved = resolveQueueLibraryCountsSnapshot(
      current: empty,
      activeDownloadFallback: fallback,
    );

    expect(identical(resolved, fallback), isTrue);
  });

  test('allows a legitimate empty refresh without an active fallback', () {
    final resolved = resolveQueueLibraryCountsSnapshot(
      current: empty,
      cached: cached,
    );

    expect(identical(resolved, empty), isTrue);
    expect(
      shouldRetainQueueLibraryPageSnapshot(
        currentIsEmpty: true,
        cachedHasContent: true,
        activeDownloadFallbackAvailable: false,
      ),
      isFalse,
    );
  });

  test('retains a non-empty page only while its fallback is available', () {
    expect(
      shouldRetainQueueLibraryPageSnapshot(
        currentIsEmpty: true,
        cachedHasContent: true,
        activeDownloadFallbackAvailable: true,
      ),
      isTrue,
    );
    expect(
      shouldRetainQueueLibraryPageSnapshot(
        currentIsEmpty: false,
        cachedHasContent: true,
        activeDownloadFallbackAvailable: true,
      ),
      isFalse,
    );
  });

  test('library file cache hides transient publication misses', () async {
    var checks = 0;
    final cache = LibraryFileAvailabilityCache(
      pathExists: (_) async => ++checks >= 3,
      normalizePath: (path) => path ?? '',
      retryDelays: const [Duration.zero, Duration.zero],
    );
    addTearDown(cache.dispose);

    final availability = cache.listenable('/music/new.flac');
    final publishedValues = <bool>[];
    availability.addListener(() => publishedValues.add(availability.value));
    expect(availability.value, isTrue);

    await Future<void>.delayed(const Duration(milliseconds: 20));

    expect(checks, 3);
    expect(availability.value, isTrue);
    expect(publishedValues, isNot(contains(false)));
  });

  test('library file cache rechecks a stale confirmed miss', () async {
    var exists = false;
    final cache = LibraryFileAvailabilityCache(
      pathExists: (_) async => exists,
      normalizePath: (path) => path ?? '',
      retryDelays: const [],
      missingRecheckAfter: Duration.zero,
    );
    addTearDown(cache.dispose);

    final availability = cache.listenable('/music/late.flac');
    await Future<void>.delayed(const Duration(milliseconds: 10));
    expect(availability.value, isFalse);

    exists = true;
    expect(identical(cache.listenable('/music/late.flac'), availability), true);
    await Future<void>.delayed(const Duration(milliseconds: 10));

    expect(availability.value, isTrue);
  });

}
