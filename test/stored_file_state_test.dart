import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sistrum/utils/file_access.dart';

/// A path on a drive that is not plugged in: nothing of it exists below the
/// filesystem root.
String _unpluggedPath(String name) => p.join(
  p.rootPrefix(Directory.systemTemp.path),
  'sistrum-unplugged-$pid',
  'Album',
  name,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  SharedPreferences.setMockInitialValues({});

  group('mount containers', () {
    test('posix folders that hold drives, cards and shares', () {
      for (final path in [
        '/',
        '/Volumes',
        '/media',
        '/media/user',
        '/run/media/user',
        '/mnt',
        '/mnt/nas',
        '/storage',
        '/storage/emulated',
      ]) {
        expect(isStorageMountContainer(path, context: p.posix), isTrue);
      }
      for (final path in [
        '/Users/me/Music',
        '/Volumes/Drive',
        '/media/user/Drive',
        '/run/media/user/Drive',
        '/mnt/nas/Music',
        '/storage/emulated/0',
        '/storage/1234-ABCD',
        '/home/me',
      ]) {
        expect(isStorageMountContainer(path, context: p.posix), isFalse);
      }
    });

    test('windows drive and share roots', () {
      for (final path in [r'C:\', r'E:\', r'\\server\share']) {
        expect(isStorageMountContainer(path, context: p.windows), isTrue);
      }
      for (final path in [r'C:\Users', r'E:\Music', r'\\server\share\Music']) {
        expect(isStorageMountContainer(path, context: p.windows), isFalse);
      }
    });
  });

  group('stored file state', () {
    late Directory root;
    late Directory album;

    setUp(() async {
      root = await Directory.systemTemp.createTemp('stored_file_state_');
      album = await Directory(
        p.join(root.path, 'Artist', 'Album'),
      ).create(recursive: true);
    });

    tearDown(() => root.delete(recursive: true));

    test('a file that is there, and a CUE track of it, are present', () async {
      final file = await File(p.join(album.path, '01.flac')).writeAsString('');
      expect(await storedFileState(file.path), isTrue);
      expect(await storedFileState('${file.path}#track01'), isTrue);
    });

    test('a file gone from its folder or with its folder is absent', () async {
      expect(await storedFileState(p.join(album.path, '02.flac')), isFalse);
      final goneAlbum = p.join(root.path, 'Artist', 'Gone', '01.flac');
      expect(await storedFileState(goneAlbum), isFalse);
      expect((await fileExistenceByPath([goneAlbum]))[goneAlbum], isFalse);
    });

    test('a file on storage that is not there cannot be told', () async {
      final unplugged = _unpluggedPath('01.flac');
      expect(await storedFileState(unplugged), isNull);
      expect((await fileExistenceByPath([unplugged]))[unplugged], isNull);
      expect(await storedFileState('network://share/Song.flac'), isNull);
      expect(await storedFileState(''), isNull);
    });
  });
}
