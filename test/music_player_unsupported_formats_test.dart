import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/services/app_state_database.dart';
import 'package:mornye/services/file_access_check.dart';
import 'package:mornye/services/music_player_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/music_player_automix_harness.dart';

class _SessionStore implements AppStateDatabase {
  List<Map<String, dynamic>> songs = [];
  int? index;
  int clears = 0;

  @override
  Future<void> savePlaybackSession({
    required Uint8List mediaJson,
    required int index,
    required int positionMs,
    required bool shuffle,
    required String repeatMode,
  }) async {
    songs = (jsonDecode(utf8.decode(mediaJson)) as List<dynamic>)
        .cast<Map<String, dynamic>>();
    this.index = index;
  }

  @override
  Future<bool> updatePlaybackSessionState({
    required int index,
    required int positionMs,
    required bool shuffle,
    required String repeatMode,
  }) async {
    this.index = index;
    return songs.isNotEmpty;
  }

  @override
  Future<void> clearPlaybackSession() async {
    songs = [];
    index = null;
    clears++;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A player with native source failures and isolated playback dependencies.
class _Player {
  _Player({
    bool gapless = false,
    bool android = false,
    Future<String?> Function(String)? recoverMissingFile,
    Future<FileAccessCheck> Function(String) fileAccess = checkFileAccess,
  }) : this._(
         gapless,
         android,
         recoverMissingFile,
         fileAccess,
         _SessionStore(),
       );

  _Player._(
    bool gapless,
    bool android,
    Future<String?> Function(String)? recoverMissingFile,
    Future<FileAccessCheck> Function(String) fileAccess,
    this.session,
  ) : runtime = MusicPlayerRuntime(
        settings: const AppSettings().copyWith(
          musicHaptics: false,
          autoMix: false,
          gaplessPlayback: gapless,
          pauseOnMute: false,
          autoplay: false,
        ),
        dependencies: PlaybackDependencies(
          isAndroid: android,
          synchronizePlaybackState: () async {},
          recoverMissingFile: recoverMissingFile ?? (_) async => null,
          fileAccess: fileAccess,
          sessionDatabase: session,
          recoverMovedDownload: (_, _, _) async => null,
        ),
      ) {
    handler = MusicPlayerHandler(
      runtime: runtime,
      autoMixAnalyzer: FakeAutoMixAnalyzer(),
      autoMixEffectRenderer: FakeEffectRenderer(),
      hapticsAnalyzer: FakeHapticsAnalyzer(),
      hapticPulse: (_, _) async {},
    );
    _subscription = runtime.unplayableSongs.listen(notices.add);
  }

  final MusicPlayerRuntime runtime;
  final _SessionStore session;
  late final MusicPlayerHandler handler;
  final notices = <UnplayableSong>[];
  late final StreamSubscription<UnplayableSong> _subscription;

  Future<void> dispose() async {
    await handler.dispose();
    await _subscription.cancel();
    await runtime.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  SharedPreferences.setMockInitialValues({});
  late AudioNative native;
  late Directory folder;
  const backend = MethodChannel('com.zarz.mornye/backend');
  const ffmpeg = MethodChannel('flutter.arthenica.com/ffmpeg_kit');
  final ffmpegCalls = <MethodCall>[];
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() async {
    native = AudioNative()..install();
    ffmpegCalls.clear();
    messenger.setMockMethodCallHandler(ffmpeg, (call) async {
      ffmpegCalls.add(call);
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(ffmpeg, null));
    folder = await Directory.systemTemp.createTemp('unsupported-formats-');
    addTearDown(() => folder.delete(recursive: true));
    addTearDown(() => messenger.setMockMethodCallHandler(backend, null));
  });

  Future<PlayableMedia> song(String name, {String? format}) async {
    final path = '${folder.path}/$name';
    await File(path).writeAsBytes([0]);
    return PlayableMedia(
      id: name,
      source: path,
      title: name,
      artist: 'Artist',
      format: format,
    );
  }

  Future<_Player> player({
    bool gapless = false,
    bool android = false,
    Future<String?> Function(String)? recoverMissingFile,
    Future<FileAccessCheck> Function(String) fileAccess = checkFileAccess,
  }) async {
    if (android) {
      const channel = MethodChannel(
        'com.ryanheise.audio_service.handler.methods',
      );
      messenger.setMockMethodCallHandler(channel, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    }
    final player = _Player(
      gapless: gapless,
      android: android,
      recoverMissingFile: recoverMissingFile,
      fileAccess: fileAccess,
    );
    addTearDown(() async {
      await player.dispose();
      await Future<void>.delayed(const Duration(milliseconds: 30));
      expect(native.live, isEmpty);
    });
    return player;
  }

  test('a missing file skips without an unsupported format notice', () async {
    final gone = await song('one.ape');
    await File(gone.source).delete();
    native.sourceFailures.add(gone.source);
    final p = await player();
    await p.handler.setQueueAndPlay([gone, await song('two.flac')]);
    await waitUntil(() => native.resumedSources.isNotEmpty);

    expect(p.notices, isEmpty);
    expect(p.handler.mediaItem.value?.id, 'two.flac');
    expect(p.handler.playbackState.value.errorMessage, isNull);
    expect(p.handler.queue.value.map((item) => item.id), ['two.flac']);
  });

  test(
    'Android advances past a deleted song after the previous one ends',
    () async {
      final first = await song('first.flac');
      final gone = await song('gone.flac');
      final last = await song('last.flac');
      await File(gone.source).delete();
      native.sourceFailures.add(gone.source);
      final p = await player(android: true);
      await p.handler.setQueueAndPlay([first, gone, last]);
      await native.complete('music-player');
      await waitUntil(() => native.resumedSources.length == 2);

      expect(native.resumedSources, [first.source, last.source]);
      expect(p.handler.mediaItem.value?.id, last.id);
      expect(p.handler.playbackState.value.errorMessage, isNull);
      expect(p.handler.queue.value.map((item) => item.id), [first.id, last.id]);
      expect(p.notices, isEmpty);
    },
  );

  test(
    'confirmed missing songs do not use the format conversion limit',
    () async {
      final missing = <PlayableMedia>[];
      for (var i = 0; i < 12; i++) {
        final gone = await song('gone-$i.flac');
        await File(gone.source).delete();
        missing.add(gone);
      }
      native.sourceFailures.addAll(missing.map((song) => song.source));
      final last = await song('last.flac');
      final p = await player();
      await p.handler.setQueueAndPlay([...missing, last]);
      await waitUntil(() => native.resumedSources.isNotEmpty);

      expect(native.resumedSources, [last.source]);
      expect(p.handler.playbackState.value.errorMessage, isNull);
      expect(p.handler.queue.value.map((item) => item.id), [last.id]);
    },
  );

  test(
    'an entirely missing queue stops after one pass in repeat all',
    () async {
      final missing = [await song('one.flac'), await song('two.flac')];
      for (final media in missing) {
        await File(media.source).delete();
        native.sourceFailures.add(media.source);
      }
      final p = await player();
      await p.handler.setRepeatMode(AudioServiceRepeatMode.all);
      await p.handler.setQueueAndPlay(missing, initialIndex: 1);
      await waitUntil(
        () => p.handler.mediaItem.value == null && p.session.clears > 0,
      );
      await Future<void>.delayed(const Duration(milliseconds: 50));

      final opened = native.calls
          .where((call) => call.$2 == 'setSourceUrl')
          .map((call) => call.$3['url']);
      expect(opened, [missing[1].source, missing[0].source]);
      expect(p.handler.playbackState.value.playing, isFalse);
      expect(native.resumedSources, isEmpty);
      expect(p.handler.queue.value, isEmpty);
      expect(p.handler.playbackState.value.queueIndex, isNull);
      expect(p.handler.playbackState.value.errorMessage, isNull);
      expect(p.session.songs, isEmpty);

      // Play cannot reopen a missing song from the cleared session.
      await p.handler.play();
      expect(native.resumedSources, isEmpty);
    },
  );

  test('repeat one removes a missing song and advances once', () async {
    final gone = await song('gone.flac');
    await File(gone.source).delete();
    native.sourceFailures.add(gone.source);
    final p = await player();
    await p.handler.setRepeatMode(AudioServiceRepeatMode.one);
    await p.handler.setQueueAndPlay([gone, await song('next.flac')]);
    await waitUntil(() => native.resumedSources.isNotEmpty);

    expect(p.handler.mediaItem.value?.id, 'next.flac');
    expect(p.handler.queue.value.map((item) => item.id), ['next.flac']);
    expect(p.handler.playbackState.value.errorMessage, isNull);
    expect(
      p.handler.playbackState.value.repeatMode,
      AudioServiceRepeatMode.one,
    );
  });

  test(
    'a missing current file removes duplicates and saves the successor',
    () async {
      final gone = await song('gone.flac');
      final first = await song('first.flac');
      final next = await song('next.flac');
      final p = await player();
      await p.handler.setQueueAndPlay([
        gone,
        first,
        gone,
        next,
      ], initialIndex: 2);
      await File(gone.source).delete();
      await native.error('music-player');
      await waitUntil(
        () => p.session.songs.length == 2 && p.session.index == 1,
      );

      expect(native.resumedSources, [gone.source, next.source]);
      expect(p.handler.queue.value.map((item) => item.id), [first.id, next.id]);
      expect(p.handler.playbackState.value.queueIndex, 1);
      expect(p.handler.mediaItem.value?.id, next.id);
      expect(p.session.songs.map((item) => item['id']), [first.id, next.id]);
    },
  );

  test(
    'a missing current file preserves shuffle and its original order',
    () async {
      final first = await song('first.flac');
      final gone = await song('gone.flac');
      final last = await song('last.flac');
      final p = await player();
      await p.handler.restoreSession(
        items: [gone, last, first],
        index: 0,
        position: Duration.zero,
        shuffle: true,
        originalOrder: [1, 2, 0],
      );
      await p.handler.play();
      await File(gone.source).delete();
      await native.error('music-player');
      await waitUntil(() => p.session.songs.length == 2);

      expect(p.handler.queue.value.map((item) => item.id), [last.id, first.id]);
      expect(p.handler.mediaItem.value?.id, last.id);
      expect(p.handler.playbackState.value.queueIndex, 0);
      expect(
        p.handler.playbackState.value.shuffleMode,
        AudioServiceShuffleMode.all,
      );
      expect(p.session.songs.map((item) => item['queueOriginalIndex']), [1, 0]);
      await p.handler.setShuffleMode(AudioServiceShuffleMode.none);
      expect(p.handler.queue.value.map((item) => item.id), [first.id, last.id]);
      expect(p.handler.mediaItem.value?.id, last.id);
      expect(p.handler.playbackState.value.queueIndex, 1);
    },
  );

  for (final onlySong in [false, true]) {
    test(
      'a missing final song stops and clears the current item ($onlySong)',
      () async {
        final gone = await song('gone.flac');
        final first = await song('first.flac');
        final p = await player();
        await p.handler.setQueueAndPlay([
          if (!onlySong) first,
          gone,
        ], initialIndex: onlySong ? 0 : 1);
        await File(gone.source).delete();
        await native.error('music-player');
        await waitUntil(() => p.handler.mediaItem.value == null);

        expect(p.handler.queue.value.map((item) => item.id), [
          if (!onlySong) first.id,
        ]);
        expect(p.handler.playbackState.value.playing, isFalse);
        expect(p.handler.playbackState.value.queueIndex, isNull);
        expect(p.handler.playbackState.value.errorMessage, isNull);
        expect(p.session.songs, isEmpty);
        expect(native.resumedSources, [gone.source]);
      },
    );
  }

  test('Previous in shuffle keeps the surviving playback history', () async {
    final first = await song('first.flac');
    final gone = await song('gone.flac');
    final next = await song('next.flac');
    final p = await player();
    await p.handler.restoreSession(
      items: [first, gone, next],
      index: 0,
      position: Duration.zero,
      shuffle: true,
    );
    await p.handler.skipToQueueItem(0);
    await p.handler.skipToNext();
    await File(gone.source).delete();
    await native.error('music-player');
    await waitUntil(() => native.resumedSources.length == 3);
    await p.handler.skipToPrevious();

    expect(p.handler.mediaItem.value?.id, first.id);
    expect(p.handler.playbackState.value.queueIndex, 0);
    expect(native.resumedSources, [
      first.source,
      gone.source,
      next.source,
      first.source,
    ]);
  });

  for (final runtimeError in [false, true]) {
    test(
      'permission errors retain the queue and current song ($runtimeError)',
      () async {
        final denied = await song('denied.flac');
        final next = await song('next.flac');
        final p = await player(
          fileAccess: (_) async => FileAccessUnavailable(
            FileSystemException(
              'Permission denied',
              denied.source,
              const OSError('', 13),
            ),
            StackTrace.current,
          ),
        );
        if (!runtimeError) native.sourceFailures.add(denied.source);
        await p.handler.setQueueAndPlay([denied, next]);
        if (runtimeError) await native.error('music-player');
        await waitUntil(
          () => p.handler.playbackState.value.errorMessage != null,
        );

        expect(p.handler.mediaItem.value?.id, denied.id);
        expect(p.handler.queue.value.map((item) => item.id), [
          denied.id,
          next.id,
        ]);
        expect(native.resumedSources, [if (runtimeError) denied.source]);
      },
    );
  }

  test(
    'a runtime error on a network stream never probes or removes it',
    () async {
      const source = 'https://example.com/song.flac';
      final p = await player(
        fileAccess: (_) async => throw StateError('Must not probe a stream'),
      );
      await p.handler.setQueueAndPlay([
        PlayableMedia(
          id: 'stream',
          source: source,
          title: 'Song',
          artist: 'Artist',
        ),
        await song('next.flac'),
      ]);
      await native.error('music-player');
      await waitUntil(() => p.handler.playbackState.value.errorMessage != null);
      expect(p.handler.mediaItem.value?.id, 'stream');
      expect(p.handler.queue.value, hasLength(2));
    },
  );

  test(
    'a late missing-file probe cannot remove a newer playback request',
    () async {
      final probing = Completer<void>();
      final result = Completer<FileAccessCheck>();
      final p = await player(
        fileAccess: (_) {
          probing.complete();
          return result.future;
        },
      );
      final first = await song('first.flac');
      final next = await song('next.flac');
      await p.handler.setQueueAndPlay([first, next]);
      await native.error('music-player');
      await probing.future;
      // Completion after the decoder error must wait for the existence check.
      await native.complete('music-player');
      expect(p.handler.mediaItem.value?.id, first.id);
      await p.handler.skipToNext();
      result.complete(const FileAccessMissing());
      await Future<void>.delayed(const Duration(milliseconds: 30));

      expect(p.handler.mediaItem.value?.id, next.id);
      expect(p.handler.queue.value, hasLength(2));
      expect(native.resumedSources, [first.source, next.source]);
    },
  );

  test(
    'a recovered local file plays before considering a missing skip',
    () async {
      final gone = await song('gone.flac');
      final recovered = await song('recovered.flac');
      await File(gone.source).delete();
      native.sourceFailures.add(gone.source);
      final p = await player(
        recoverMissingFile: (path) async =>
            path == gone.source ? recovered.source : null,
      );
      await p.handler.setQueueAndPlay([gone, await song('next.flac')]);

      expect(p.handler.mediaItem.value?.id, gone.id);
      expect(native.resumedSources, [recovered.source]);
      expect(p.handler.playbackState.value.errorMessage, isNull);
      await native.error('music-player');
      await waitUntil(() => p.handler.playbackState.value.errorMessage != null);
      expect(p.handler.mediaItem.value?.id, gone.id);
      expect(p.handler.queue.value, hasLength(2));
    },
  );

  for (final status in [
    'missing',
    'found',
    'permission_denied',
    'provider_unavailable',
  ]) {
    test('Android SAF skips only confirmed absence: $status', () async {
      const source = 'content://example.documents/document/track.flac';
      final probes = <String>[];
      messenger.setMockMethodCallHandler(backend, (call) async {
        if (call.method != 'safStat') return null;
        final arguments = call.arguments as Map<Object?, Object?>;
        probes.add(arguments['uri']! as String);
        if (status == 'missing') return '{"exists":false}';
        if (status == 'found') return '{"exists":true}';
        throw PlatformException(code: status);
      });
      final p = await player(android: true);
      final next = await song('next.flac');
      await p.handler.setQueueAndPlay([
        PlayableMedia(
          id: 'saf-song',
          source: source,
          title: 'Song',
          artist: 'Artist',
        ),
        next,
      ]);
      if (status == 'missing') {
        await waitUntil(() => native.resumedSources.isNotEmpty);
        expect(native.resumedSources, [next.source]);
        expect(p.handler.playbackState.value.errorMessage, isNull);
      } else {
        expect(native.resumedSources, isEmpty);
        expect(p.handler.mediaItem.value?.id, 'saf-song');
        expect(
          p.handler.playbackState.value.processingState,
          AudioProcessingState.error,
        );
      }
      expect(probes, contains(source));
    });
  }

  test(
    'pausing during a missing-file check cancels automatic advancement',
    () async {
      final probing = Completer<void>();
      final result = Completer<String>();
      messenger.setMockMethodCallHandler(backend, (call) async {
        if (call.method != 'safStat') return null;
        if (!probing.isCompleted) probing.complete();
        return result.future;
      });
      final p = await player(android: true);
      final next = await song('next.flac');
      final opening = p.handler.setQueueAndPlay([
        PlayableMedia(
          id: 'saf-song',
          source: 'content://example.documents/document/gone.flac',
          title: 'Song',
          artist: 'Artist',
        ),
        next,
      ]);
      await probing.future;
      final pausing = p.handler.pause();
      result.complete('{"exists":false}');
      await Future.wait([opening, pausing]);
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(p.handler.mediaItem.value?.id, 'saf-song');
      expect(p.handler.playbackState.value.playing, isFalse);
      expect(p.handler.playbackState.value.errorMessage, isNull);
      expect(native.resumedSources, isEmpty);
    },
  );

  test(
    'native playback does not transcode or skip an existing unplayable file',
    () async {
      final ape = await song('one.ape');
      native.sourceFailures.add(ape.source);
      final p = await player();
      await p.handler.setQueueAndPlay([ape, await song('two.flac')]);
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(ffmpegCalls, isEmpty);
      expect(p.notices, isEmpty);
      expect(p.handler.mediaItem.value?.id, 'one.ape');
      expect(
        p.handler.playbackState.value.processingState,
        AudioProcessingState.error,
      );
    },
  );
}
