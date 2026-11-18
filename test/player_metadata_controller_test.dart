import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/player_metadata_controller.dart';
import 'package:sistrum/services/source_metadata_events.dart';
import 'package:sistrum/utils/lyrics_parser.dart';

MediaItem _item(
  String id, {
  String? source,
  String? resolved,
  Map<String, dynamic> metadata = const {},
}) => MediaItem(
  id: id,
  title: id,
  artist: 'Artist',
  album: 'Album',
  extras: {
    'source': source ?? '/music/$id.flac',
    'resolvedSource': ?resolved,
    ...metadata,
  },
);

class _EditEvents extends SourceMetadataEvents {
  final published = <String>[];

  @override
  Future<void> publish(String source) async => published.add(source);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'file changes refresh cached lyrics and tags without changing tracks',
    () async {
      final events = SourceMetadataEvents();
      var reads = 0;
      final controller = PlayerMetadataController(
        metadataEvents: events,
        readMetadata: (_) async => {
          'title': 'Title ${++reads}',
          'lyrics': '[00:01]Verse $reads',
          'replaygain_track_gain': '-$reads dB',
        },
      );
      await controller.load(_item('a'));
      expect(controller.lyrics.lines.single.text, 'Verse 1');
      await events.publish('/other.flac');
      expect(reads, 1);
      await events.publish('/music/a.flac');
      expect(controller.metadata!['title'], 'Title 2');
      expect(controller.lyrics.lines.single.text, 'Verse 2');
      expect(controller.metadata!['replaygain_track_gain'], '-2 dB');
      controller.dispose();
      await events.publish('/music/a.flac');
      expect(reads, 2);
    },
  );

  test(
    'new metadata is visible immediately while lyrics parse is pending',
    () async {
      final parse = Completer<ParsedLyrics>();
      final controller = PlayerMetadataController(
        readMetadata: (path) async => path.endsWith('a.flac')
            ? {'lyrics': '[00:01]Old', 'format': 'MP3'}
            : {'lyrics': '[00:01]New', 'format': 'FLAC', 'bit_depth': 24},
        parseLyrics: (raw) => raw == '[00:01]Old'
            ? Future.value(LyricsParser.parse(raw))
            : parse.future,
      );
      addTearDown(controller.dispose);
      await controller.load(_item('a'));
      expect(controller.lyrics.lines.single.text, 'Old');
      final pending = controller.load(_item('b', metadata: {'format': 'AAC'}));
      expect(controller.lyrics.isEmpty, isTrue);
      expect(controller.qualityLabel, 'AAC');
      await Future<void>.delayed(Duration.zero);
      expect(controller.qualityLabel, 'FLAC  ·  24-bit');
      expect(controller.lyrics.isEmpty, isTrue);
      expect(controller.loading, isTrue);
      parse.complete(LyricsParser.parse('[00:01]New'));
      await pending;
      expect(controller.lyrics.lines.single.text, 'New');
      expect(controller.loading, isFalse);
    },
  );

  test(
    'failed native lyrics retain new quality metadata and clear loading',
    () async {
      final controller = PlayerMetadataController(
        readMetadata: (_) async => {'lyrics': '[00:01]Text', 'format': 'FLAC'},
        parseLyrics: (_) async => throw StateError('Invalid native lyrics'),
      );
      addTearDown(controller.dispose);
      await controller.load(_item('a'));
      expect(controller.qualityLabel, 'FLAC');
      expect(controller.lyrics.isEmpty, isTrue);
      expect(controller.loading, isFalse);
    },
  );

  test(
    'late native lyrics cannot publish after switching away and back',
    () async {
      final parses = <String, Completer<ParsedLyrics>>{};
      final controller = PlayerMetadataController(
        readMetadata: (path) async => {'lyrics': path},
        parseLyrics: (raw) => (parses[raw!] = Completer<ParsedLyrics>()).future,
      );
      addTearDown(controller.dispose);
      final first = controller.load(_item('a'));
      await Future<void>.delayed(Duration.zero);
      final oldParse = parses['/music/a.flac']!;
      final second = controller.load(_item('b'));
      await Future<void>.delayed(Duration.zero);
      final current = controller.load(_item('a'));
      await Future<void>.delayed(Duration.zero);
      parses['/music/a.flac']!.complete(LyricsParser.parse('[00:01]Current A'));
      await current;
      oldParse.complete(LyricsParser.parse('[00:01]Old A'));
      parses['/music/b.flac']!.complete(LyricsParser.parse('[00:01]Old B'));
      await Future.wait([first, second]);
      expect(controller.lyrics.lines.single.text, 'Current A');
      expect(controller.loading, isFalse);
    },
  );

  test(
    'disposal while native lyrics parse is pending never publishes',
    () async {
      final parse = Completer<ParsedLyrics>();
      final controller = PlayerMetadataController(
        readMetadata: (_) async => {'lyrics': '[00:01]Late'},
        parseLyrics: (_) => parse.future,
      );
      var notifications = 0;
      controller.addListener(() => notifications++);
      final pending = controller.load(_item('a'));
      await Future<void>.delayed(Duration.zero);
      final beforeDispose = notifications;
      controller.dispose();
      parse.complete(LyricsParser.parse('[00:01]Late'));
      await pending;
      expect(notifications, beforeDispose);
      expect(controller.lyrics.isEmpty, isTrue);
    },
  );
  test('late result cannot overwrite a track switched away and back', () async {
    final reads = <Completer<Map<String, dynamic>>>[];
    final controller = PlayerMetadataController(
      readMetadata: (_) {
        final read = Completer<Map<String, dynamic>>();
        reads.add(read);
        return read.future;
      },
    );
    addTearDown(controller.dispose);
    final first = controller.load(_item('a'));
    final second = controller.load(_item('b'));
    final current = controller.load(_item('a'));

    reads[2].complete({'lyrics': '[00:01.00]Current A'});
    await current;
    reads[0].complete({'lyrics': '[00:01.00]Old A'});
    reads[1].completeError(StateError('Old B failed'));
    await Future.wait([first, second]);

    expect(controller.lyrics.lines.single.text, 'Current A');
    expect(controller.loading, isFalse);
  });

  test('resolved source supersedes an outstanding content URI read', () async {
    final old = Completer<Map<String, dynamic>>();
    final paths = <String>[];
    final controller = PlayerMetadataController(
      readMetadata: (path) {
        paths.add(path);
        return path.startsWith('content://')
            ? old.future
            : Future.value({'lyrics': '[00:01.00]Resolved'});
      },
    );
    addTearDown(controller.dispose);
    final source = 'content://library/song.flac';
    final pending = controller.load(
      _item('a', source: source),
      inspectUnresolvedContentUri: true,
    );
    await controller.load(
      _item('a', source: source, resolved: '/tmp/song.flac'),
    );
    old.complete({'lyrics': '[00:01.00]Unresolved'});
    await pending;

    expect(paths, [source, '/tmp/song.flac']);
    expect(controller.lyrics.lines.single.text, 'Resolved');
  });

  test(
    'a tag change reloads a document from its URI, not its playback copy',
    () async {
      final events = SourceMetadataEvents();
      final paths = <String>[];
      const source = 'content://library/song.flac';
      final controller = PlayerMetadataController(
        metadataEvents: events,
        readMetadata: (path) async {
          paths.add(path);
          return {
            'lyrics': path == source ? '[00:02.00]Fresh' : '[00:01.00]Copy',
          };
        },
      );
      addTearDown(controller.dispose);
      await controller.load(
        _item('a', source: source, resolved: '/tmp/song.flac'),
      );
      expect(controller.lyrics.lines.single.text, 'Copy');
      await events.publish(source);
      expect(paths, ['/tmp/song.flac', source]);
      expect(controller.lyrics.lines.single.text, 'Fresh');
      expect(controller.filePath, source);
    },
  );

  test('a tag change leaves an unread document unread', () async {
    final events = SourceMetadataEvents();
    final paths = <String>[];
    const source = 'content://library/song.flac';
    final controller = PlayerMetadataController(
      metadataEvents: events,
      readMetadata: (path) async {
        paths.add(path);
        return {'lyrics': '[00:01.00]Read'};
      },
    );
    addTearDown(controller.dispose);
    await controller.load(_item('a', source: source));
    await events.publish(source);
    expect(paths, isEmpty);
    expect(controller.source, source);
  });

  test(
    'disposal prevents outstanding reads from notifying or mutating',
    () async {
      final read = Completer<Map<String, dynamic>>();
      final controller = PlayerMetadataController(
        readMetadata: (_) => read.future,
      );
      var notifications = 0;
      controller.addListener(() => notifications++);
      final pending = controller.load(_item('a'));
      final beforeDispose = notifications;
      controller.dispose();
      read.complete({'lyrics': '[00:01.00]Late'});
      await pending;
      await controller.load(_item('b'));

      expect(notifications, beforeDispose);
      expect(controller.lyrics.isEmpty, isTrue);
    },
  );

  test(
    'unresolved SAF metadata stays on demand and reads are deduplicated',
    () async {
      final read = Completer<Map<String, dynamic>>();
      final paths = <String>[];
      final controller = PlayerMetadataController(
        readMetadata: (path) {
          paths.add(path);
          return read.future;
        },
      );
      addTearDown(controller.dispose);
      final item = _item('a', source: 'content://library/a.flac');
      await controller.load(item);
      expect(paths, isEmpty);
      expect(controller.loading, isFalse);
      final pending = controller.load(item, inspectUnresolvedContentUri: true);
      await controller.load(item, inspectUnresolvedContentUri: true);
      expect(paths, [item.extras!['source']]);
      read.complete({'lyrics': '[00:01.00]Embedded'});
      await pending;
      await controller.load(item, inspectUnresolvedContentUri: true);
      expect(paths, hasLength(1));
    },
  );

  test('HTTP quality fallback avoids file probes and old lyrics', () async {
    final paths = <String>[];
    final controller = PlayerMetadataController(
      readMetadata: (path) async {
        paths.add(path);
        return {'lyrics': '[00:01.00]Local'};
      },
    );
    addTearDown(controller.dispose);
    await controller.load(_item('local'));
    final remote = _item(
      'remote',
      source: 'https://audio.test/song.mp3',
      resolved: 'https://cdn.audio.test/song.mp3',
      metadata: {'format': 'mp3', 'bitrate': 320, 'sample_rate': 44100},
    );
    expect((await controller.readDetails(remote))['title'], 'remote');
    await controller.load(remote);
    expect((await controller.readDetails(remote))['format'], 'mp3');

    expect(paths, ['/music/local.flac']);
    expect(controller.lyrics.isEmpty, isTrue);
    expect(controller.qualityLabel, 'MP3  ·  44.1 kHz  ·  320 kbps');
  });

  test(
    'network metadata uses logical source despite playback HTTP URL',
    () async {
      final paths = <String>[];
      final controller = PlayerMetadataController(
        readMetadata: (path) async {
          paths.add(path);
          return {'lyrics': '[00:01.00]Network'};
        },
      );
      addTearDown(controller.dispose);
      await controller.load(
        _item(
          'network',
          source: 'network://library/song.flac',
          resolved: 'http://127.0.0.1/playback',
        ),
      );
      expect(paths, ['network://library/song.flac']);
      expect(controller.lyrics.lines.single.text, 'Network');
    },
  );

  test('failed probes retain quality metadata and may retry', () async {
    var reads = 0;
    final controller = PlayerMetadataController(
      readMetadata: (_) async {
        if (++reads == 1) throw StateError('Temporarily unavailable');
        return {
          'lyrics': '[00:01.00]Recovered',
          'format': '',
          'sample_rate': 0,
          'lyricist': 'Writer',
          'lyrics_provider': 'Provider',
        };
      },
    );
    addTearDown(controller.dispose);
    final item = _item(
      'a',
      metadata: {'format': 'FLAC', 'sample_rate': 48000, 'bit_depth': 24},
    );
    await controller.load(item);
    expect(controller.loading, isFalse);
    expect(controller.qualityLabel, 'FLAC  ·  24-bit  ·  48 kHz');
    await controller.load(item);
    expect(controller.qualityLabel, 'FLAC  ·  24-bit  ·  48 kHz');
    expect(controller.lyrics.lines.single.text, 'Recovered');
    expect(controller.credits, (writers: 'Writer', provider: 'Provider'));
    expect(
      () => controller.metadata!['format'] = 'MP3',
      throwsUnsupportedError,
    );
  });

  test('details snapshot probes SAF without changing lyrics state', () async {
    final controller = PlayerMetadataController(
      readMetadata: (_) async => {
        'title': 'Tagged',
        'lyrics': '[00:01.00]Lyrics',
      },
    );
    addTearDown(controller.dispose);
    final item = _item('a', source: 'content://library/a.flac');
    await controller.load(item);
    final details = await controller.readDetails(item);
    expect(details['title'], 'Tagged');
    expect(details['artist'], 'Artist');
    expect(controller.lyrics.isEmpty, isTrue);
    expect(controller.loading, isFalse);
    await controller.load(null);
    expect(controller.source, isNull);
    expect(controller.metadata, isNull);
  });

  test(
    'failed details retain their snapshot after the track changes',
    () async {
      final read = Completer<Map<String, dynamic>>();
      final controller = PlayerMetadataController(
        readMetadata: (path) => path.startsWith('content://')
            ? read.future
            : Future.value({'lyrics': '[00:01.00]Current'}),
      );
      addTearDown(controller.dispose);
      final item = _item(
        'a',
        source: 'content://library/a.flac',
        metadata: {'format': 'FLAC'},
      );
      await controller.load(item);
      final pending = controller.readDetails(item);
      await controller.load(_item('b'));
      read.completeError(StateError('File unavailable'));

      expect(await pending, {
        'title': 'a',
        'artist': 'Artist',
        'album': 'Album',
        'format': 'FLAC',
      });
      expect(controller.lyrics.lines.single.text, 'Current');
    },
  );

  group('edited lyrics ownership', () {
    for (final phase in ['parse', 'write']) {
      test(
        'SAF edit stays on the original document while $phase is pending',
        () async {
          const raw = '[00:01.00]Edited original';
          final parse = Completer<ParsedLyrics>();
          final write = Completer<void>();
          final writes = <(String, String)>[];
          final events = _EditEvents();
          final controller = PlayerMetadataController(
            readMetadata: (path) async => {
              'lyrics': path == '/music/b.flac'
                  ? '[00:02.00]Track B lyrics'
                  : '[00:01.00]Original lyrics',
            },
            parseLyrics: (text) async => text == raw && phase == 'parse'
                ? await parse.future
                : LyricsParser.parse(text),
            saveOnlineLyrics: (source, lyrics) async {
              writes.add((source, lyrics));
              if (phase == 'write') await write.future;
            },
            metadataEvents: events,
          );
          addTearDown(controller.dispose);
          const source =
              'content://example.documents/tree/music/document/a.flac';
          await controller.load(
            _item('a', source: source, resolved: '/cache/a.flac'),
          );
          final edit = controller.beginLyricsEdit()!;
          final pending = controller.saveEditedLyrics(raw, edit: edit);
          await Future<void>.delayed(Duration.zero);
          await controller.load(_item('b'));
          if (phase == 'parse') {
            parse.complete(LyricsParser.parse(raw));
          } else {
            write.complete();
          }
          await pending;
          expect(writes, [(source, raw)]);
          expect(events.published, [source]);
          expect(edit.rawLyrics, '[00:01.00]Original lyrics');
          expect(controller.source, '/music/b.flac');
          expect(controller.rawLyrics, '[00:02.00]Track B lyrics');
        },
      );
    }

    test(
      'a saved edit cannot update another source with the same queue ID',
      () async {
        final writes = <String>[];
        final controller = PlayerMetadataController(
          readMetadata: (source) async => {'lyrics': '[00:01.00]$source'},
          parseLyrics: (raw) async => LyricsParser.parse(raw),
          saveOnlineLyrics: (source, _) async => writes.add(source),
          metadataEvents: _EditEvents(),
        );
        addTearDown(controller.dispose);
        await controller.load(_item('same', source: '/music/a.flac'));
        final edit = controller.beginLyricsEdit()!;
        await controller.load(_item('same', source: '/music/b.flac'));
        await controller.saveEditedLyrics('[00:01.00]Edited A', edit: edit);
        expect(writes, ['/music/a.flac']);
        expect(controller.rawLyrics, '[00:01.00]/music/b.flac');
      },
    );

    test(
      'failed writes preserve the original editor snapshot for retry',
      () async {
        var fail = true;
        final sources = <String>[];
        final events = _EditEvents();
        final controller = PlayerMetadataController(
          readMetadata: (_) async => {'lyrics': '[00:01.00]Original'},
          parseLyrics: (raw) async => LyricsParser.parse(raw),
          saveOnlineLyrics: (source, _) async {
            sources.add(source);
            if (fail) throw StateError('Document write failed');
          },
          metadataEvents: events,
        );
        addTearDown(controller.dispose);
        await controller.load(_item('a'));
        final edit = controller.beginLyricsEdit()!;
        await expectLater(
          controller.saveEditedLyrics('[00:01.00]Edited', edit: edit),
          throwsStateError,
        );
        expect(controller.rawLyrics, '[00:01.00]Original');
        expect(events.published, isEmpty);
        await controller.load(_item('b'));
        fail = false;
        await controller.saveEditedLyrics('[00:01.00]Edited', edit: edit);
        expect(sources, ['/music/a.flac', '/music/a.flac']);
        expect(events.published, ['/music/a.flac']);
        expect(controller.rawLyrics, '[00:01.00]Original');
      },
    );
  });
}
