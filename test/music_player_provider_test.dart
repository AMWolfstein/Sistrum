import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/providers/music_player_provider.dart';

PlaybackRowKey _row(String id, [String path = '']) => (id: id, path: path);

MediaItem _item(String id, String source) =>
    MediaItem(id: id, title: 'Song', extras: {'source': source});

void main() {
  group('mediaItemPlaysRow', () {
    test('compares files when both sides know one', () {
      // A playlist queues the file under its path rather than a row id.
      final fromPlaylist = _item('/music/a.flac', '/music/a.flac');
      final row = _row('row-a', '/music/a.flac');
      expect(mediaItemPlaysRow(fromPlaylist, row), isTrue);
      final fromLibrary = _item('row-a', '/music/a.flac');
      expect(mediaItemPlaysRow(fromLibrary, row), isTrue);
    });

    test('an id another source shares does not mark the row', () {
      final current = _item('42', '/music/a.flac');
      expect(mediaItemPlaysRow(current, _row('42', '/music/b.flac')), isFalse);
    });

    test('ignores the existence marker on stored paths', () {
      final current = _item('x', '/music/a.flac');
      expect(
        mediaItemPlaysRow(current, _row('', 'EXISTS:/music/a.flac')),
        isTrue,
      );
    });

    test('falls back to the media id without a path', () {
      expect(mediaItemPlaysRow(_item('row-a', ''), _row('row-a')), isTrue);
      expect(
        mediaItemPlaysRow(
          _item('/music/a.flac', ''),
          _row('', '/music/a.flac'),
        ),
        isTrue,
      );
      expect(mediaItemPlaysRow(_item('row-a', ''), _row('row-b')), isFalse);
      expect(mediaItemPlaysRow(null, _row('row-a')), isFalse);
    });
  });

  test(
    'late duration updates notify mounted player consumers for the same track',
    () async {
      final mediaItems = StreamController<MediaItem?>();
      addTearDown(mediaItems.close);
      final container = ProviderContainer(
        overrides: [
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => mediaItems.stream,
          ),
        ],
      );
      addTearDown(container.dispose);
      final durations = <Duration?>[];
      final ids = <String?>[];
      container.listen(currentMediaItemProvider, (_, next) {
        if (next.hasValue) durations.add(next.value?.duration);
      });
      container.listen(
        currentMediaItemProvider.select((state) => state.value?.id),
        (_, next) => ids.add(next),
      );
      const initial = MediaItem(
        id: 'track',
        title: 'Track',
        duration: Duration.zero,
      );
      mediaItems.add(initial);
      await container.read(currentMediaItemProvider.future);
      await container.pump();
      final ready = initial.copyWith(duration: const Duration(minutes: 3));
      expect(ready, initial); // audio_service equality compares only the ID.
      mediaItems.add(ready);
      await container.pump();
      expect(durations, [Duration.zero, const Duration(minutes: 3)]);
      expect(ids, ['track']);

      mediaItems.add(ready);
      await container.pump();
      expect(durations, hasLength(2));
      mediaItems.add(const MediaItem(id: 'next', title: 'Next'));
      await container.pump();
      expect(durations.last, isNull);
      expect(ids, ['track', 'next']);
      mediaItems.add(null);
      await container.pump();
      expect(container.read(currentMediaItemProvider).value, isNull);
      expect(ids, ['track', 'next', null]);
    },
  );

  test('Library playback state follows only the current media item', () async {
    final container = ProviderContainer(
      overrides: [
        currentMediaItemProvider.overrideWithBuild(
          (ref, notifier) =>
              Stream.value(const MediaItem(id: 'track-1', title: 'Track 1')),
        ),
        playbackPlayingProvider.overrideWith((ref) => true),
        playbackLoadingProvider.overrideWith((ref) => false),
      ],
    );
    addTearDown(container.dispose);
    final currentMediaSubscription = container.listen(
      currentMediaItemProvider,
      (_, _) {},
    );
    addTearDown(currentMediaSubscription.close);

    await container.read(currentMediaItemProvider.future);

    expect(container.read(mediaItemPlaybackUiProvider(_row('track-1'))), (
      isCurrent: true,
      isPlaying: true,
      isLoading: false,
    ));
    expect(container.read(mediaItemPlaybackUiProvider(_row('track-2'))), (
      isCurrent: false,
      isPlaying: false,
      isLoading: false,
    ));
  });

  test(
    'Library releases playback state for cells that leave the viewport',
    () async {
      final mediaItems = StreamController<MediaItem?>();
      addTearDown(mediaItems.close);
      final container = ProviderContainer(
        overrides: [
          currentMediaItemProvider.overrideWithBuild(
            (ref, notifier) => mediaItems.stream,
          ),
          playbackPlayingProvider.overrideWith((ref) => true),
          playbackLoadingProvider.overrideWith((ref) => false),
        ],
      );
      addTearDown(container.dispose);
      final visibleProvider = mediaItemPlaybackUiProvider(
        _row('visible-track'),
      );
      final visibleSubscription = container.listen(visibleProvider, (_, _) {});
      addTearDown(visibleSubscription.close);
      mediaItems.add(
        const MediaItem(id: 'visible-track', title: 'Visible track'),
      );
      await container.read(currentMediaItemProvider.future);

      for (var index = 0; index < 200; index++) {
        final provider = mediaItemPlaybackUiProvider(
          _row('scrolled-track-$index'),
        );
        final subscription = container.listen(provider, (_, _) {});
        expect(container.exists(provider), isTrue);
        subscription.close();
      }
      await container.pump();

      for (var index = 0; index < 200; index++) {
        expect(
          container.exists(
            mediaItemPlaybackUiProvider(_row('scrolled-track-$index')),
          ),
          isFalse,
        );
      }
      expect(container.exists(visibleProvider), isTrue);
      expect(visibleSubscription.read(), (
        isCurrent: true,
        isPlaying: true,
        isLoading: false,
      ));

      mediaItems.add(
        const MediaItem(id: 'scrolled-track-0', title: 'Scrolled track'),
      );
      await container.pump();
      final returningSubscription = container.listen(
        mediaItemPlaybackUiProvider(_row('scrolled-track-0')),
        (_, _) {},
      );
      addTearDown(returningSubscription.close);
      expect(returningSubscription.read(), (
        isCurrent: true,
        isPlaying: true,
        isLoading: false,
      ));
      expect(visibleSubscription.read(), (
        isCurrent: false,
        isPlaying: false,
        isLoading: false,
      ));
    },
  );
}
