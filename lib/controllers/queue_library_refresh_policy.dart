import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:sistrum/services/downloaded_embedded_cover_resolver.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/utils/file_access.dart';

bool queueLibraryCountsHaveContent(QueueLibraryCounts counts) =>
    counts.allTrackCount > 0 ||
    counts.albumCount > 0 ||
    counts.singleTrackCount > 0;

QueueLibraryCounts resolveQueueLibraryCountsSnapshot({
  required QueueLibraryCounts current,
  QueueLibraryCounts? cached,
  QueueLibraryCounts? activeDownloadFallback,
}) {
  if (queueLibraryCountsHaveContent(current) ||
      activeDownloadFallback == null) {
    return current;
  }
  if (cached != null && queueLibraryCountsHaveContent(cached)) {
    return cached;
  }
  return activeDownloadFallback;
}

bool shouldRetainQueueLibraryPageSnapshot({
  required bool currentIsEmpty,
  required bool cachedHasContent,
  required bool activeDownloadFallbackAvailable,
}) => currentIsEmpty && cachedHasContent && activeDownloadFallbackAvailable;

typedef LibraryFilePathExists = Future<bool> Function(String path);
typedef LibraryFilePathNormalizer = String Function(String? path);

/// Keeps the per-card Library file check cheap without making a transient
/// storage miss permanent for the lifetime of the tab.
///
/// Android document providers can briefly return `false` immediately after a
/// file is published. Fresh paths therefore stay optimistically playable
/// during a bounded retry window. Confirmed misses are cached, but become
/// eligible for a new probe after [missingRecheckAfter] when their card is
/// rebuilt (for example after returning from the Metadata screen).
class LibraryFileAvailabilityCache {
  static const int _defaultMaxEntries = 500;
  static const List<Duration> _defaultRetryDelays = [
    Duration(milliseconds: 350),
    Duration(milliseconds: 700),
    Duration(milliseconds: 1400),
    Duration(milliseconds: 2200),
  ];

  final LibraryFilePathExists _pathExists;
  final LibraryFilePathNormalizer _normalizePath;
  final List<Duration> _retryDelays;
  final Duration _missingRecheckAfter;
  final int _maxEntries;
  final Map<String, bool> _cache = {};
  final Map<String, DateTime> _checkedAt = {};
  final Map<String, _LibraryFileAvailabilityNotifier> _notifiers = {};
  final Map<String, Timer> _retryTimers = {};
  final Map<String, int> _generations = {};
  final Set<String> _probingPaths = {};
  final ValueNotifier<bool> _alwaysMissingNotifier = ValueNotifier(false);
  bool _disposed = false;

  LibraryFileAvailabilityCache({
    LibraryFilePathExists pathExists = fileExists,
    LibraryFilePathNormalizer normalizePath =
        DownloadedEmbeddedCoverResolver.cleanFilePath,
    List<Duration> retryDelays = _defaultRetryDelays,
    Duration missingRecheckAfter = const Duration(seconds: 5),
    int maxEntries = _defaultMaxEntries,
  }) : assert(maxEntries > 0),
       _pathExists = pathExists,
       _normalizePath = normalizePath,
       _retryDelays = List.unmodifiable(retryDelays),
       _missingRecheckAfter = missingRecheckAfter,
       _maxEntries = maxEntries;

  ValueListenable<bool> listenable(String? filePath) {
    final path = _normalizePath(filePath);
    if (path.isEmpty || _disposed) return _alwaysMissingNotifier;

    final existingNotifier = _notifiers[path];
    if (existingNotifier != null) {
      final cached = _cache[path];
      if (cached != null && existingNotifier.value != cached) {
        existingNotifier.value = cached;
      }
      if (cached == null) {
        _beginProbe(path);
      } else if (!cached && _confirmedMissIsStale(path)) {
        // Keep showing the confirmed state while the background recheck runs;
        // it will flip to playable as soon as storage reports the file.
        _beginProbe(path, force: true);
      }
      return existingNotifier;
    }

    _evictUnusedEntriesIfNeeded();
    final notifier = _LibraryFileAvailabilityNotifier(_cache[path] ?? true);
    _notifiers[path] = notifier;
    _beginProbe(path);
    return notifier;
  }

  /// Starts a fresh optimistic check for a path whose storage state changed,
  /// such as the destination of a newly completed download.
  void refreshForPath(String? filePath) {
    final path = _normalizePath(filePath);
    if (path.isEmpty || _disposed) return;
    _beginProbe(path, force: true, optimistic: true);
  }

  /// Publishes a successful check performed by another component. This is
  /// used by the completion bridge so its verified final path immediately
  /// heals a stale missing icon on the normal Library card.
  void markExists(String? filePath) {
    final path = _normalizePath(filePath);
    if (path.isEmpty || _disposed) return;

    _generations[path] = (_generations[path] ?? 0) + 1;
    _retryTimers.remove(path)?.cancel();
    _probingPaths.remove(path);
    _cache[path] = true;
    _checkedAt[path] = DateTime.now();
    final notifier = _notifiers[path];
    if (notifier != null && !notifier.value) {
      notifier.value = true;
    }
  }

  bool _confirmedMissIsStale(String path) {
    final checkedAt = _checkedAt[path];
    return checkedAt == null ||
        DateTime.now().difference(checkedAt) >= _missingRecheckAfter;
  }

  void _beginProbe(String path, {bool force = false, bool optimistic = false}) {
    if (_disposed || (!force && _probingPaths.contains(path))) return;

    final generation = (_generations[path] ?? 0) + 1;
    _generations[path] = generation;
    _retryTimers.remove(path)?.cancel();
    _probingPaths.add(path);
    _cache.remove(path);
    _checkedAt.remove(path);
    if (optimistic) {
      final notifier = _notifiers[path];
      if (notifier != null && !notifier.value) notifier.value = true;
    }
    unawaited(_probe(path, generation: generation, attempt: 0));
  }

  Future<void> _probe(
    String path, {
    required int generation,
    required int attempt,
  }) async {
    var exists = false;
    try {
      exists = await _pathExists(path);
    } catch (_) {}
    if (_disposed || _generations[path] != generation) return;

    if (exists) {
      markExists(path);
      return;
    }

    if (attempt < _retryDelays.length) {
      _retryTimers[path] = Timer(_retryDelays[attempt], () {
        _retryTimers.remove(path);
        if (_disposed || _generations[path] != generation) return;
        unawaited(_probe(path, generation: generation, attempt: attempt + 1));
      });
      return;
    }

    _probingPaths.remove(path);
    _cache[path] = false;
    _checkedAt[path] = DateTime.now();
    final notifier = _notifiers[path];
    if (notifier != null && notifier.value) notifier.value = false;
  }

  void _evictUnusedEntriesIfNeeded() {
    while (_notifiers.length >= _maxEntries) {
      String? evictionPath;
      for (final entry in _notifiers.entries) {
        if (!entry.value.hasActiveListeners) {
          evictionPath = entry.key;
          break;
        }
      }
      if (evictionPath == null) return;
      _removeEntry(evictionPath);
    }
  }

  void _removeEntry(String path) {
    _retryTimers.remove(path)?.cancel();
    _probingPaths.remove(path);
    _cache.remove(path);
    _checkedAt.remove(path);
    _generations.remove(path);
    _notifiers.remove(path)?.dispose();
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    for (final timer in _retryTimers.values) {
      timer.cancel();
    }
    for (final notifier in _notifiers.values) {
      notifier.dispose();
    }
    _retryTimers.clear();
    _notifiers.clear();
    _probingPaths.clear();
    _cache.clear();
    _checkedAt.clear();
    _generations.clear();
    _alwaysMissingNotifier.dispose();
  }
}

class _LibraryFileAvailabilityNotifier extends ValueNotifier<bool> {
  _LibraryFileAvailabilityNotifier(super.value);

  bool get hasActiveListeners => hasListeners;
}

/// Probes both completion-path candidates and keeps a bridge in a neutral
/// checking state while delayed SAF publication becomes visible. A definitive
/// missing result is emitted only after the retry window is exhausted.
