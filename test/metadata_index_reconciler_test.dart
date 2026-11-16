import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/models/library_database_models.dart';
import 'package:sistrum/services/metadata_index_reconciler.dart';

void main() {
  late Directory tempDir;
  late List<Map<String, dynamic>> upserts;
  late List<String> upsertSources;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('metadata-index-test');
    upserts = [];
    upsertSources = [];
  });
  tearDown(() => tempDir.delete(recursive: true));

  final item = LocalLibraryItem(
    id: 'stable-id',
    sourceId: 'sd-card',
    trackName: 'Old title',
    artistName: 'Artist',
    albumName: 'Album',
    filePath: '/music/song.flac',
    coverPath: '/music/old-cover.jpg',
    scannedAt: DateTime(2026),
    duration: 215,
    format: 'flac',
  );

  MetadataIndexReconciler reconciler({
    Future<Map<String, dynamic>> Function(String)? read,
    Future<Map<String, dynamic>> Function(String, String)? extractCover,
    Future<int> Function(Map<String, dynamic>, {required String sourceId})?
    upsert,
  }) => MetadataIndexReconciler(
    read: read ?? (_) async => {'title': 'New title', 'genre': 'Jazz'},
    extractCover:
        extractCover ??
        (_, path) async {
          File(path).writeAsBytesSync([1]);
          return <String, dynamic>{};
        },
    supportDirectory: () async => tempDir,
    upsert:
        upsert ??
        (json, {required sourceId}) async {
          upserts.add(json);
          upsertSources.add(sourceId);
          return 0;
        },
  );

  test('saved tags and a fresh cover reach the index', () async {
    String? written;
    final result = await reconciler(
      read: (_) async => {
        'title': 'New title',
        'genre': 'Jazz',
        'album': 'New album',
        'track_number': '3',
        'total_tracks': '12',
        'disc_number': '1',
        'total_discs': '2',
        'explicit': '1',
        'lyrics': 'la la',
      },
      extractCover: (_, path) async {
        written = path;
        File(path).writeAsBytesSync([1, 2, 3]);
        return {'error': null};
      },
    ).reconcile(item);

    expect(result.failure, isNull);
    expect(upsertSources, ['sd-card']);
    final json = upserts.single;
    expect(json['trackName'], 'New title');
    expect(json['genre'], 'Jazz');
    expect(json['albumName'], 'New album');
    expect(json['trackNumber'], 3);
    expect(json['totalTracks'], 12);
    expect(json['discNumber'], 1);
    expect(json['totalDiscs'], 2);
    expect(json['explicit'], true);
    expect(json['hasLyrics'], true);
    expect(json['id'], item.id);
    expect(json['filePath'], item.filePath);
    expect(json['coverPath'], written);
    expect(written, startsWith('${tempDir.path}/library_covers/'));
    expect(result.item?.trackName, 'New title');
    expect(result.item?.albumName, 'New album');
    expect(result.item?.coverPath, written);
  });

  test('the scanned identity and audio facts are kept', () async {
    final result = await reconciler().reconcile(item);

    expect(result.failure, isNull);
    final json = upserts.single;
    expect(json['duration'], 215);
    expect(json['format'], 'flac');
    expect(result.item?.duration, 215);
  });

  test('a failed extraction keeps the previous cover reference', () async {
    final result = await reconciler(
      extractCover: (_, _) async => {'error': 'no artwork'},
    ).reconcile(item);

    expect(result.failure, isNull);
    expect(upserts.single['coverPath'], '/music/old-cover.jpg');
  });

  test('an unreadable file reports a stale index without writing', () async {
    final result = await reconciler(
      read: (_) async => throw const FileSystemException('missing'),
    ).reconcile(item);

    expect(result.failure, MetadataIndexFailure.read);
    expect(result.item, isNull);
    expect(upserts, isEmpty);
  });

  test('a reader error map reports a stale index without writing', () async {
    final result = await reconciler(
      read: (_) async => {'error': 'unreadable tags'},
    ).reconcile(item);

    expect(result.failure, MetadataIndexFailure.read);
    expect(result.error, 'unreadable tags');
    expect(result.item, isNull);
    expect(upserts, isEmpty);
  });

  test('a failed upsert reports the index failure', () async {
    final result = await reconciler(
      upsert: (_, {required sourceId}) async =>
          throw StateError('database closed'),
    ).reconcile(item);

    expect(result.failure, MetadataIndexFailure.database);
    expect(result.item, isNull);
  });

  test('a row shadowed by download history reports a stale index', () async {
    final result = await reconciler(
      upsert: (json, {required sourceId}) async {
        upserts.add(json);
        return 1;
      },
    ).reconcile(item);

    expect(upserts, hasLength(1));
    expect(result.failure, MetadataIndexFailure.database);
    expect(result.item, isNull);
  });

  test('a CUE virtual track is refused without rereading or writing', () async {
    final reads = <String>[];
    final cueItem = LocalLibraryItem(
      id: 'cue-track',
      sourceId: 'sd-card',
      trackName: 'Track 1',
      artistName: 'Artist',
      albumName: 'Album',
      filePath: '/music/album.cue#track01',
      scannedAt: DateTime(2026),
    );
    final result = await reconciler(
      read: (path) async {
        reads.add(path);
        return {'title': 'New title'};
      },
    ).reconcile(cueItem);

    expect(result.failure, MetadataIndexFailure.virtualTrack);
    expect(result.item, isNull);
    expect(reads, isEmpty);
    expect(upserts, isEmpty);
  });

  test('tags already read by the caller are used without rereading', () async {
    final result = await reconciler(
      read: (_) async => throw StateError('should not reread'),
    ).reconcile(item, tags: {'title': 'From screen'});

    expect(result.failure, isNull);
    expect(upserts.single['trackName'], 'From screen');
    expect(result.item?.trackName, 'From screen');
  });
}
