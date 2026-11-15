import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/music_player_service.dart';

void main() {
  const media = PlayableMedia(
    id: 'track-1',
    source: '/music/track.flac',
    title: 'Track',
    artist: 'Artist',
    bitDepth: 24,
    sampleRate: 96000,
    bitrate: 2860,
    format: 'flac',
    explicit: true,
  );

  test('queue media exposes technical quality to Now Playing immediately', () {
    final metadata = playbackAudioMetadataFromMediaItem(media.toMediaItem());

    expect(metadata, {
      'bit_depth': 24,
      'sample_rate': 96000,
      'bitrate': 2860,
      'format': 'flac',
      'explicit': true,
    });
  });

  test('persisted playback keeps technical quality across app restarts', () {
    final restored = PlayableMedia.fromJson(media.toJson());

    expect(restored, isNotNull);
    expect(restored!.bitDepth, 24);
    expect(restored.sampleRate, 96000);
    expect(restored.bitrate, 2860);
    expect(restored.format, 'flac');
    expect(restored.explicit, isTrue);
  });

  test('file probe cannot erase valid queue quality with empty values', () {
    final merged = mergePlaybackFileMetadata(
      playbackAudioMetadataFromMediaItem(media.toMediaItem()),
      {'title': 'Track', 'bit_depth': 0, 'sample_rate': null, 'format': ''},
    );

    expect(merged['bit_depth'], 24);
    expect(merged['sample_rate'], 96000);
    expect(merged['format'], 'flac');
    expect(merged['title'], 'Track');
  });

  test('restored playback starts at its persisted position', () {
    const savedPosition = Duration(minutes: 1, seconds: 23);

    expect(
      normalizedPlaybackResumePosition(
        savedPosition,
        duration: const Duration(minutes: 4),
      ),
      savedPosition,
    );
  });

  test('a completed persisted position restarts safely from zero', () {
    expect(
      normalizedPlaybackResumePosition(
        const Duration(minutes: 4),
        duration: const Duration(minutes: 4),
      ),
      Duration.zero,
    );
  });

  test('cold-start metadata read retries thrown and reported errors', () async {
    var calls = 0;

    final metadata = await readPlaybackFileMetadataWithRetry(
      '/music/track.flac',
      retryDelays: const [Duration.zero, Duration.zero, Duration.zero],
      reader: (path) async {
        calls++;
        if (calls == 1) throw StateError('backend not ready');
        if (calls == 2) return {'error': 'file temporarily unavailable'};
        return {'lyrics': '[00:01.00]Ready'};
      },
    );

    expect(calls, 3);
    expect(metadata['lyrics'], '[00:01.00]Ready');
  });

  test('successful metadata without lyrics is not retried', () async {
    var calls = 0;

    final metadata = await readPlaybackFileMetadataWithRetry(
      '/music/instrumental.flac',
      retryDelays: const [Duration.zero, Duration.zero, Duration.zero],
      reader: (path) async {
        calls++;
        return {'title': 'Instrumental'};
      },
    );

    expect(calls, 1);
    expect(metadata['title'], 'Instrumental');
  });

  test('shuffle plans the whole queue once with the current song first', () {
    final order = buildShuffledQueueOrder(
      mediaCount: 12,
      currentIndex: 4,
      random: Random(42),
    );
    expect(order.first, 4);
    expect(order.toSet(), {for (var i = 0; i < 12; i++) i});
    expect(order.length, 12);
    expect(
      order.skip(1),
      isNot(orderedEquals([0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11])),
    );
  });

  test(
    'new shuffle draws a new permutation and handles empty/single queues',
    () {
      final random = Random(42);
      final first = buildShuffledQueueOrder(
        mediaCount: 12,
        currentIndex: 4,
        random: random,
      );
      final second = buildShuffledQueueOrder(
        mediaCount: 12,
        currentIndex: 4,
        random: random,
      );
      expect(first, isNot(orderedEquals(second)));
      expect(
        buildShuffledQueueOrder(
          mediaCount: 0,
          currentIndex: -1,
          random: random,
        ),
        isEmpty,
      );
      expect(
        buildShuffledQueueOrder(mediaCount: 1, currentIndex: 0, random: random),
        [0],
      );
    },
  );
}
