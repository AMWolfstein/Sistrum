import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/providers/player_motion_artwork_provider.dart';
import 'package:sistrum/providers/settings_provider.dart';
import 'package:sistrum/services/motion_artwork_folder.dart';
import 'package:sistrum/services/motion_artwork_store.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('MotionArtworkFolder', () {
    late Directory root;
    late File video;

    setUp(() async {
      root = await Directory.systemTemp.createTemp('motion-folder-');
      video = File(p.join(root.path, 'stored.mp4'))
        ..writeAsBytesSync([1, 2, 3]);
    });
    tearDown(() => root.delete(recursive: true));

    test('writes cover.mp4 beside a local song once', () async {
      final album = Directory(p.join(root.path, 'Artist', 'Album'))
        ..createSync(recursive: true);
      final folder = MotionArtworkFolder(directory: () async => root);
      final saves = await Future.wait([
        folder.save(video, song: p.join(album.path, '01.flac')),
        folder.save(video, song: p.join(album.path, '02.flac')),
      ]);
      expect(saves.where((saved) => saved), hasLength(1));
      final cover = File(p.join(album.path, motionArtworkFileName));
      expect(cover.readAsBytesSync(), [1, 2, 3]);
      expect(album.listSync().map((e) => p.basename(e.path)), [
        motionArtworkFileName,
      ]);
      expect(
        await folder.save(video, song: p.join(album.path, '03.flac')),
        isFalse,
      );
    });

    test('keeps a cover the user already has', () async {
      final album = Directory(p.join(root.path, 'Album'))..createSync();
      final cover = File(p.join(album.path, motionArtworkFileName))
        ..writeAsBytesSync([9]);
      final folder = MotionArtworkFolder(directory: () async => root);
      expect(
        await folder.save(video, song: p.join(album.path, '01.flac')),
        isFalse,
      );
      expect(cover.readAsBytesSync(), [9]);
    });

    test('documents go through their storage', () async {
      final created = <String>[];
      final folder = MotionArtworkFolder(
        directory: () async => root,
        createDocument: (treeUri, relativeDir, name, path) async {
          created.add('$treeUri|$relativeDir|$name|${p.basename(path)}');
          return true;
        },
      );
      expect(
        await folder.save(video, song: 'content://tree/document/song'),
        isFalse,
      );
      expect(
        await folder.save(
          video,
          song: 'content://tree/document/song',
          treeUri: 'content://tree',
          relativeDir: 'Artist/Album',
        ),
        isTrue,
      );
      expect(created, ['content://tree|Artist/Album|cover.mp4|stored.mp4']);
    });

    test('finds cover.mp4 beside a local song', () async {
      final album = Directory(p.join(root.path, 'Album'))..createSync();
      final song = p.join(album.path, '01.flac');
      final folder = MotionArtworkFolder(directory: () async => root);
      expect(await folder.find(song), isNull);
      final cover = File(p.join(album.path, motionArtworkFileName))
        ..writeAsBytesSync([4, 5]);
      expect((await folder.find(song))?.source, cover.uri.toString());
      expect(
        (await folder.find(Uri.file(song).toString()))?.source,
        cover.uri.toString(),
      );
    });

    test('copies a document cover in once per version', () async {
      var modified = 1;
      var copies = 0;
      final folder = MotionArtworkFolder(
        directory: () async => root,
        documents: true,
        sibling: (song, name) async {
          expect(name, motionArtworkFileName);
          return (
            uri: 'content://tree/document/cover',
            size: 2,
            modified: modified,
          );
        },
        copyDocument: (uri) async {
          copies++;
          final temp = File(p.join(root.path, 'temp-$copies.mp4'));
          await temp.writeAsBytes([7, copies]);
          return temp.path;
        },
      );
      final first = await Future.wait([
        folder.find('content://tree/document/song-1'),
        folder.find('content://tree/document/song-2'),
      ]);
      expect(copies, 1);
      expect(first[0]?.source, first[1]?.source);
      final copy = File.fromUri(Uri.parse(first[0]!.source));
      expect(p.isWithin(root.path, copy.path), isTrue);
      expect(copy.readAsBytesSync(), [7, 1]);

      // Another song of the album reuses the copy.
      await folder.find('content://tree/document/song-3');
      expect(copies, 1);

      // A replaced cover is copied again once lookups expire.
      modified = 2;
      folder.forget();
      await folder.find('content://tree/document/song-1');
      expect(copies, 2);
      expect(copy.readAsBytesSync(), [7, 2]);
    });

    test('documents are not read off Android', () async {
      var asked = false;
      final folder = MotionArtworkFolder(
        directory: () async => root,
        documents: false,
        sibling: (_, _) async {
          asked = true;
          return null;
        },
      );
      expect(await folder.find('content://tree/document/song'), isNull);
      expect(asked, isFalse);
    });
  });

  group('songMotionArtworkProvider', () {
    setUp(() => SharedPreferences.setMockInitialValues({}));

    test('prefers the folder cover over the saved one', () async {
      final root = await Directory.systemTemp.createTemp('motion-song-');
      addTearDown(() => root.delete(recursive: true));
      final album = Directory(p.join(root.path, 'Album'))..createSync();
      final song = p.join(album.path, '01.flac');
      const saved = MotionArtwork('file:///saved.mp4', aspectRatio: 1);
      final container = ProviderContainer(
        overrides: [
          motionArtworkFolderProvider.overrideWithValue(
            MotionArtworkFolder(directory: () async => root),
          ),
          playerMotionArtworkProvider.overrideWith((ref, album) async => saved),
        ],
      );
      addTearDown(container.dispose);
      Future<MotionArtwork?> read(String source) => container.read(
        songMotionArtworkProvider((
          album: 'Album',
          artist: 'Artist',
          source: source,
        )).future,
      );

      expect((await read(song))?.source, saved.source);
      final cover = File(p.join(album.path, motionArtworkFileName))
        ..writeAsBytesSync([1]);
      container.invalidate(songMotionArtworkProvider);
      expect((await read(song))?.source, cover.uri.toString());
      expect((await read(''))?.source, saved.source);

      container.read(settingsProvider.notifier).setMotionArtworkEnabled(false);
      expect(await read(song), isNull);
    });
  });
}
