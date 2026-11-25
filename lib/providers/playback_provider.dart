import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:sistrum/models/track.dart';
import 'package:sistrum/providers/music_player_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/library_database.dart';
import 'package:sistrum/services/music_player_service.dart';
import 'package:sistrum/services/track_file_resolver.dart';
import 'package:sistrum/utils/file_access.dart';
import 'package:sistrum/utils/logger.dart';

final _log = AppLogger('PlaybackProvider');

class PlaybackState {
  const PlaybackState();
}

class PlaybackController extends Notifier<PlaybackState> {
  PlaybackController({LibraryDatabase? library})
    : _library = library ?? LibraryDatabase.instance;

  final LibraryDatabase _library;

  @override
  PlaybackState build() => const PlaybackState();

  /// The built-in player, started if it is not running yet.
  Future<MusicPlayerController> _player() async {
    final controller = ref.read(musicPlayerControllerProvider);
    if (await controller.ensureInitialized() == null) {
      throw Exception('The built-in player could not start.');
    }
    return controller;
  }

  Future<void> playLocalPath({
    required String path,
    required String title,
    required String artist,
    String album = '',
    String? genre,
    String coverUrl = '',
    Duration? duration,
    int? bitDepth,
    int? sampleRate,
    int? bitrate,
    String? format,
    bool? explicit,
  }) async {
    if (isCueVirtualPath(path)) {
      throw Exception(cueVirtualTrackRequiresSplitMessage);
    }

    final player = await _player();
    _log.d('Playing "$title" in the built-in player: $path');
    await player.playSingle(
      PlayableMedia(
        id: path,
        source: path,
        title: title,
        artist: artist,
        album: album,
        genre: genre,
        artUri: normalizeArtUri(coverUrl),
        duration: duration,
        bitDepth: bitDepth,
        sampleRate: sampleRate,
        bitrate: bitrate,
        format: format,
        explicit: explicit ?? false,
      ),
    );
  }

  /// Plays a local-library album/list starting at [startItem], queuing the rest
  /// so playback continues to the next track automatically.
  Future<void> playLocalLibraryQueue(
    List<LocalLibraryItem> items, {
    required LocalLibraryItem startItem,
  }) => _playQueue(
    items,
    (i) => i.id == startItem.id,
    (i) => i.filePath,
    playableFromLocal,
  );

  Future<void> _playQueue<T>(
    List<T> items,
    bool Function(T item) isStart,
    String Function(T item) path,
    PlayableMedia Function(T item) toMedia,
  ) async {
    final playable = items
        .where((i) => path(i).trim().isNotEmpty && !isCueVirtualPath(path(i)))
        .toList();
    if (playable.isEmpty) return;
    var startIndex = playable.indexWhere(isStart);
    if (startIndex < 0) startIndex = 0;
    await playMediaQueue(playable.map(toMedia), startIndex: startIndex);
  }

  /// Plays a prebuilt media queue starting at [startIndex].
  Future<void> playMediaQueue(
    Iterable<PlayableMedia> queue, {
    required int startIndex,
  }) async {
    final player = await _player();
    final items = queue.toList(growable: false);
    if (items.isEmpty) return;
    final i = startIndex.clamp(0, items.length - 1);
    await player.playAll(items, initialIndex: i);
  }

  Future<void> playTrackList(List<Track> tracks, {int startIndex = 0}) async {
    if (tracks.isEmpty) return;

    final safeStart = startIndex.clamp(0, tracks.length - 1);
    final resolvedPaths = await resolveTrackFilePaths(tracks);
    final player = await _player();

    final queue = <PlayableMedia>[];
    int? initialIndex;
    var skippedCueVirtualTrack = false;
    for (var index = 0; index < tracks.length; index++) {
      final track = tracks[index];
      final resolvedPath = resolvedPaths[index];
      if (resolvedPath == null) continue;
      if (isCueVirtualPath(resolvedPath)) {
        skippedCueVirtualTrack = true;
        continue;
      }
      // Keep the playlist's original order so Previous can reach earlier
      // tracks and reaching its end still respects the player's repeat mode.
      if (index >= safeStart) initialIndex ??= queue.length;
      queue.add(playableFromTrack(track, resolvedPath));
    }

    if (queue.isNotEmpty) {
      _log.d('Playing ${queue.length} tracks in the built-in player');
      await player.playAll(queue, initialIndex: initialIndex ?? 0);
      return;
    }
    if (skippedCueVirtualTrack) {
      throw Exception(cueVirtualTrackRequiresSplitMessage);
    }
    throw Exception('No local audio file is available to play.');
  }

  /// Public wrapper so non-playback features (e.g. M3U export) reuse the
  /// same Library file resolution.
  Future<List<String?>> resolveTrackFilePaths(List<Track> tracks) =>
      resolveTrackFiles(
        tracks,
        library: _library,
        includeLocal: ref.read(settingsProvider).localLibraryEnabled,
      );
}

final playbackProvider = NotifierProvider<PlaybackController, PlaybackState>(
  PlaybackController.new,
);
