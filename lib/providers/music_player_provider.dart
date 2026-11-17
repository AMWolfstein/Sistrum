import 'dart:async';
import 'dart:math';

import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/models/unified_library_item.dart';
import 'package:sistrum/providers/download_queue_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/library_media_identity.dart';
import 'package:sistrum/services/library_tag_credits.dart';
import 'package:sistrum/services/sqlite_helpers.dart' as sqlite;
import 'package:sistrum/services/music_player_service.dart';

export 'package:sistrum/services/playable_media.dart'
    show playableFromHistory, playableFromLocal;

final musicPlayerRuntimeProvider = Provider<MusicPlayerRuntime>(
  (ref) => musicPlayerRuntime,
);

/// External apps own local-file playback.
final playerUiEnabledProvider = Provider<bool>(
  (ref) =>
      ref.watch(settingsProvider.select((s) => s.playerMode == 'internal')),
);

/// What the player screens show: the local player's track.
final currentMediaItemProvider =
    StreamNotifierProvider<CurrentMediaItemNotifier, MediaItem?>(
      CurrentMediaItemNotifier.new,
    );

class CurrentMediaItemNotifier extends StreamNotifier<MediaItem?> {
  @override
  Stream<MediaItem?> build() {
    if (!ref.watch(playerUiEnabledProvider)) return Stream.value(null);
    return ref.watch(musicPlayerRuntimeProvider).mediaItemEvents();
  }

  @override
  bool updateShouldNotify(
    AsyncValue<MediaItem?> previous,
    AsyncValue<MediaItem?> next,
  ) {
    // MediaItem equality compares IDs only. A new snapshot of the same track
    // can carry its late duration, artwork or metadata and must reach the UI.
    if (previous is AsyncData<MediaItem?> && next is AsyncData<MediaItem?>) {
      return !identical(previous.value, next.value);
    }
    return super.updateShouldNotify(previous, next);
  }
}

/// The local player's track, also while a remote device plays in its place:
/// what this device's media notification shows.
final localMediaItemProvider =
    StreamNotifierProvider<LocalMediaItemNotifier, MediaItem?>(
      LocalMediaItemNotifier.new,
    );

class LocalMediaItemNotifier extends CurrentMediaItemNotifier {
  @override
  Stream<MediaItem?> build() =>
      ref.watch(musicPlayerRuntimeProvider).mediaItemEvents();
}

/// A dismissed session stays available on Home without restoring the player.
final playbackResumeProvider = FutureProvider<PlaybackResumePreview?>((ref) {
  if (!ref.watch(settingsProvider.select((s) => s.playerMode == 'internal'))) {
    return null;
  }
  final hasCurrent = ref.watch(
    currentMediaItemProvider.select((state) => state.value != null),
  );
  if (hasCurrent) {
    return null;
  }
  final runtime = ref.watch(musicPlayerRuntimeProvider);
  ref.onDispose(
    runtime.dependencies.sourceDeletionEvents.subscribe((_) async {
      ref.invalidateSelf();
    }),
  );
  return loadPlaybackResumePreview(runtime);
});

/// [libraryItemForMedia] with the download History the app has loaded.
Future<UnifiedLibraryItem?> _libraryItemForMedia(
  Ref ref, {
  required String id,
  required String source,
  required String title,
  required String artist,
}) => libraryItemForMedia(
  id: id,
  source: source,
  title: title,
  artist: artist,
  historyByPath: ref.read(downloadHistoryProvider.notifier).getByFilePathAsync,
  library: ref.watch(musicPlayerRuntimeProvider).dependencies.libraryDatabase,
);

/// Use the Library's identity for favorites, including restored queues and
/// playback started from a file path rather than a Library row ID.
final playerCollectionTrackProvider = FutureProvider.autoDispose
    .family<Track, MediaItem>((ref, item) async {
      final match = await _libraryItemForMedia(
        ref,
        id: item.id,
        source: item.extras?['source']?.toString() ?? '',
        title: item.title,
        artist: item.artist ?? '',
      );
      return collectionTrackForMedia(item, match);
    });

final playbackStateProvider = StreamProvider<PlaybackState>((ref) {
  if (!ref.watch(playerUiEnabledProvider)) return Stream.value(PlaybackState());
  return ref.watch(musicPlayerRuntimeProvider).playbackStateEvents();
});

/// Small derived providers keep position ticks from rebuilding widgets that
/// only care about transport state (and vice versa).
final playbackPositionProvider = Provider<Duration>((ref) {
  return ref.watch(
    playbackStateProvider.select(
      (state) => state.value?.position ?? Duration.zero,
    ),
  );
});

final playbackPlayingProvider = Provider<bool>((ref) {
  return ref.watch(
    playbackStateProvider.select((state) => state.value?.playing ?? false),
  );
});

final playbackLoadingProvider = Provider<bool>((ref) {
  return ref.watch(
    playbackStateProvider.select((state) {
      final processingState = state.value?.processingState;
      return processingState == AudioProcessingState.loading ||
          processingState == AudioProcessingState.buffering;
    }),
  );
});

/// Whether a list row is the current track, and if so whether it plays or
/// loads.
typedef MediaPlaybackUi = ({bool isCurrent, bool isPlaying, bool isLoading});

const MediaPlaybackUi _notCurrent = (
  isCurrent: false,
  isPlaying: false,
  isLoading: false,
);

/// The transport state of a row once it is known to be current. Only the
/// current row follows play and loading.
MediaPlaybackUi _currentPlaybackUi(Ref ref) => (
  isCurrent: true,
  isPlaying: ref.watch(playbackPlayingProvider),
  isLoading: ref.watch(playbackLoadingProvider),
);

/// What a list row plays: its media [id] (a download or Library row id) and
/// the [path] of its file, either of which may be empty.
typedef PlaybackRowKey = ({String id, String path});

String _cleanMediaPath(String path) =>
    path.startsWith('EXISTS:') ? path.substring(7) : path;

/// Whether [item] plays the row [row].
///
/// Every queued item carries its file in `extras['source']`, but its id
/// depends on where the queue started: a download or Library row id, or the
/// path itself when a playlist or a single file started it. So when both
/// sides know a path only the paths are compared, and an id that another
/// source happens to share never marks the row. Rows without a path fall
/// back to the media id.
bool mediaItemPlaysRow(MediaItem? item, PlaybackRowKey row) {
  if (item == null) return false;
  final source = _cleanMediaPath(item.extras?['source']?.toString() ?? '');
  final path = _cleanMediaPath(row.path);
  if (source.isNotEmpty && path.isNotEmpty) return source == path;
  return (row.id.isNotEmpty && item.id == row.id) ||
      (path.isNotEmpty && item.id == path);
}

/// Transport state for one list row. Non-current Library cells only watch
/// whether the current item plays them; the active cell additionally follows
/// play/loading so a transport toggle does not rebuild the entire Library
/// grid. Release each cell's subscription when it scrolls out of the Library.
final mediaItemPlaybackUiProvider = Provider.autoDispose
    .family<MediaPlaybackUi, PlaybackRowKey>((ref, row) {
      final isCurrent = ref.watch(
        currentMediaItemProvider.select(
          (state) => mediaItemPlaysRow(state.value, row),
        ),
      );
      return isCurrent ? _currentPlaybackUi(ref) : _notCurrent;
    });

final playQueueProvider = StreamProvider<List<MediaItem>>((ref) {
  if (!ref.watch(playerUiEnabledProvider)) return Stream.value(const []);
  return ref.watch(musicPlayerRuntimeProvider).queueEvents();
});

/// Prefers the reported queue index while it still names the current item.
final currentQueueIndexProvider = Provider<int>((ref) {
  final queue = ref.watch(playQueueProvider).value ?? const <MediaItem>[];
  final id = ref.watch(currentMediaItemProvider.select((s) => s.value?.id));
  final index = ref.watch(
    playbackStateProvider.select((state) => state.value?.queueIndex),
  );
  return index != null &&
          index >= 0 &&
          index < queue.length &&
          queue[index].id == id
      ? index
      : queue.indexWhere((item) => item.id == id);
});

/// Drives this device's own player.
class MusicPlayerController {
  MusicPlayerController({MusicPlayerRuntime? runtime})
    : _runtime = runtime ?? musicPlayerRuntime;

  final MusicPlayerRuntime _runtime;

  MusicPlayerHandler? get _handler => _runtime.handler;

  DateTime? get sleepTimerEndsAt => _handler?.sleepTimerEndsAt;

  Future<MusicPlayerHandler?> ensureInitialized() async {
    try {
      return await _runtime.initialize();
    } catch (_) {
      return null;
    }
  }

  Future<void> playAll(
    List<PlayableMedia> items, {
    int initialIndex = 0,
  }) async {
    final handler = await ensureInitialized();
    await handler?.setQueueAndPlay(items, initialIndex: initialIndex);
  }

  Future<void> playSingle(PlayableMedia item) => playAll([item]);

  Future<LocalLibraryItem?> localAlbumFor(String mediaId) async {
    final row = await _runtime.dependencies.libraryDatabase.getById(mediaId);
    return row == null ? null : LocalLibraryItem.fromJson(row);
  }

  /// Plays every Library song, downloads and enabled scanned sources alike,
  /// from a random one with shuffle on. The queue keeps the Library order
  /// underneath, so turning shuffle off returns to that order. With [artist],
  /// plays only the songs that credit that artist. Returns false when there
  /// is nothing to play.
  Future<bool> shuffleLibrary({String? artist}) async {
    var media = await _runtime.browser.librarySongs();
    if (artist != null) {
      final name = sqlite.normalizeLookupText(artist);
      final rules = TagCreditRules.fromSettings(_runtime.settings.tagCredits);
      media = [
        for (final song in media)
          if (splitTagCredits(
            song.artist,
            TagCreditField.artist,
            rules,
          ).any((credited) => sqlite.normalizeLookupText(credited) == name))
            song,
      ];
    }
    if (media.isEmpty) return false;
    final handler = await _runtime.initialize();
    await handler.setShuffleMode(AudioServiceShuffleMode.all);
    await handler.setQueueAndPlay(
      media,
      initialIndex: Random().nextInt(media.length),
    );
    return true;
  }

  Future<void> seek(Duration position) async => _handler?.seek(position);
  Future<void> next() async => _handler?.skipToNext();
  Future<void> previous() async => _handler?.skipToPrevious();

  /// Hides a paused player, retaining its queue and position for Home's Resume.
  Future<void> dismiss() async {
    final handler = _handler;
    if (handler == null) return;
    final state = handler.playbackState.value;
    if (state.playing ||
        state.processingState == AudioProcessingState.loading ||
        state.processingState == AudioProcessingState.buffering) {
      return;
    }
    await handler.stop(keepResume: true);
  }

  Future<bool>? _resumeFuture;

  Future<bool> resumeSavedSession() {
    final pending = _resumeFuture;
    if (pending != null) return pending;
    return _resumeFuture = _resumeSavedSession().whenComplete(() {
      _resumeFuture = null;
    });
  }

  Future<bool> _resumeSavedSession() async {
    if (_runtime.settings.playerMode != 'internal') {
      return false;
    }
    final handler = await ensureInitialized();
    if (handler == null) return false;
    final generation = handler.playbackRequestGeneration;
    if (handler.mediaItem.value == null) {
      final restored = await restorePlaybackSession(
        _runtime,
        fromResume: true,
        expectedPlaybackGeneration: generation,
      );
      if (!restored ||
          handler.isDisposed ||
          handler.playbackRequestGeneration != generation) {
        return false;
      }
    }
    await handler.play();
    return true;
  }

  void setSleepTimer(Duration duration) => _handler?.setSleepTimer(duration);

  void cancelSleepTimer() => _handler?.cancelSleepTimer();

  Future<void> togglePlayPause(bool isPlaying) async {
    final handler = _handler;
    if (handler == null) return;
    final processingState = handler.playbackState.value.processingState;
    if (processingState == AudioProcessingState.loading ||
        processingState == AudioProcessingState.buffering) {
      return;
    }
    if (isPlaying) {
      await handler.pause();
    } else {
      await handler.play();
    }
  }

  Future<void> setShuffle(bool enabled) async {
    await _handler?.setShuffleMode(
      enabled ? AudioServiceShuffleMode.all : AudioServiceShuffleMode.none,
    );
  }

  Future<void> setRepeatMode(AudioServiceRepeatMode mode) async =>
      _handler?.setRepeatMode(mode);

  Future<void> playNext(PlayableMedia item) async {
    await (await ensureInitialized())?.enqueue(item, playNext: true);
  }

  Future<void> addToQueue(PlayableMedia item) async {
    await (await ensureInitialized())?.enqueue(item);
  }

  Future<void> enqueueLibraryItems(
    List<UnifiedLibraryItem> items, {
    bool playNext = false,
  }) async {
    final media = [
      for (final item in items)
        if (item.localItem != null)
          playableFromLocal(item.localItem!)
        else if (item.historyItem != null)
          playableFromHistory(item.historyItem!),
    ];
    await enqueueMedia(media, playNext: playNext);
  }

  Future<void> enqueueMedia(
    List<PlayableMedia> media, {
    bool playNext = false,
  }) async {
    if (media.isEmpty) return;
    await (await ensureInitialized())?.enqueueAll(media, playNext: playNext);
  }

  Future<void> playNextHistory(DownloadHistoryItem item) async =>
      playNext(playableFromHistory(item));

  Future<void> addToQueueHistory(DownloadHistoryItem item) async =>
      addToQueue(playableFromHistory(item));

  Future<void> playNextLocal(LocalLibraryItem item) async =>
      playNext(playableFromLocal(item));

  Future<void> addToQueueLocal(LocalLibraryItem item) async =>
      addToQueue(playableFromLocal(item));

  Future<void> jumpTo(int index) async => _handler?.skipToQueueItem(index);

  void moveQueueItem(int oldIndex, int newIndex) {
    _handler?.moveQueueItem(oldIndex, newIndex);
  }

  void removeQueuedItem(MediaItem item) {
    _handler?.removeQueuedItem(item);
  }

  /// Drops the playing entry [item] after its file was deleted: playback moves
  /// to the next entry, or stops when it was the last one.
  Future<void> removeDeletedPlaying(MediaItem item) async {
    final handler = _handler;
    if (handler == null) return;
    final queue = handler.queue.value;
    final index = handler.playbackState.value.queueIndex;
    if (index == null || index < 0 || index >= queue.length) return;
    final playing = queue[index];
    if (playing.id != item.id) return;
    if (index + 1 < queue.length) {
      await handler.skipToNext();
    } else {
      await handler.stop();
    }
    removeQueuedItem(playing);
  }
}

/// The controller the player screens use.
final musicPlayerControllerProvider = Provider<MusicPlayerController>(
  (ref) =>
      MusicPlayerController(runtime: ref.watch(musicPlayerRuntimeProvider)),
);
