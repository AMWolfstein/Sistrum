import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/providers/song_leftovers_provider.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';

/// An Android storage tree held in memory: folder uri to its entries.
class _Documents implements SongFolderStore {
  _Documents(this.folders);

  final Map<String, List<SongFolderEntry>> folders;
  final deleted = <String>[];

  @override
  Future<List<SongFolderEntry>?> list(String folder) async {
    final entries = folders[folder];
    return entries == null ? null : List.of(entries);
  }

  @override
  Future<bool> delete(String id) async {
    deleted.add(id);
    for (final entries in folders.values) {
      entries.removeWhere((entry) => entry.id == id);
    }
    return true;
  }
}

/// Waits for work that includes real file IO.
Future<void> _until(bool Function() done) async {
  for (var i = 0; i < 300 && !done(); i++) {
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory temp;
  late String root;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    temp = await Directory.systemTemp.createTemp('song_leftovers');
    root = p.join(temp.path, 'Music');
    await Directory(root).create();
  });

  tearDown(() async {
    if (await temp.exists()) await temp.delete(recursive: true);
  });

  String file(String relative, {String? under}) {
    final path = p.join(under ?? root, relative);
    File(path)
      ..createSync(recursive: true)
      ..writeAsStringSync('x');
    return path;
  }

  bool exists(String relative) =>
      FileSystemEntity.typeSync(p.join(root, relative)) !=
      FileSystemEntityType.notFound;

  ({SongLeftovers leftovers}) setUpLeftovers({
    SongFolderStore? documents,
    Future<({String name, List<({String uri, bool root})> folders})?> Function(
      String source,
    )?
    documentPlace,
    Future<bool> Function(String? source)? deleteSong,
  }) {
    final provider = Provider<SongLeftovers>(
      (ref) => SongLeftovers(
        ref,
        deleteSong: deleteSong,
        documents: documents ?? const DocumentSongFolderStore(),
        documentPlace: documentPlace,
      ),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    return (leftovers: container.read(provider));
  }

  test('deletes the song and its lyrics files, and no folder', () async {
    final song = file('Artist/Album/01 Song.flac');
    file('Artist/Album/01 Song.lrc');
    file('Artist/Album/01 Song.mlrc');
    file('Artist/Album/.DS_Store');
    file('Artist/Album/cover.mp4');
    final (:leftovers) = setUpLeftovers();

    expect(await leftovers.delete(song), isTrue);
    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(exists('Artist/Album/01 Song.flac'), isFalse);
    expect(exists('Artist/Album/01 Song.lrc'), isFalse);
    expect(exists('Artist/Album/01 Song.mlrc'), isFalse);
    expect(exists('Artist/Album/.DS_Store'), isTrue);
    expect(exists('Artist/Album/cover.mp4'), isTrue);
  });

  test('keeps the other files of the song folder', () async {
    final song = file('Artist/Album/Song.flac');
    file('Artist/Album/Song.lrc');
    file('Artist/Album/folder.jpg');
    final (:leftovers) = setUpLeftovers();

    expect(await leftovers.delete(song), isTrue);
    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(exists('Artist/Album/Song.lrc'), isFalse);
    expect(exists('Artist/Album/folder.jpg'), isTrue);
  });

  test('keeps lyrics files a song of the same name still uses', () async {
    final song = file('Artist/Album/Song.flac');
    file('Artist/Album/Song.mp3');
    file('Artist/Album/Song.lrc');
    final (:leftovers) = setUpLeftovers();

    await leftovers.delete(song);
    await Future<void>.delayed(const Duration(milliseconds: 200));

    expect(exists('Artist/Album/Song.flac'), isFalse);
    expect(exists('Artist/Album/Song.lrc'), isTrue);
  });

  test('does not delete the lyrics files when the song stays', () async {
    final song = file('Artist/Album/Song.flac');
    file('Artist/Album/Song.lrc');
    final (:leftovers) = setUpLeftovers(deleteSong: (_) async => false);

    expect(await leftovers.delete(song), isFalse);
    await Future<void>.delayed(const Duration(milliseconds: 200));
    expect(exists('Artist/Album/Song.lrc'), isTrue);
  });

  test('deletes the lyrics files of a document song', () async {
    SongFolderEntry entry(String id) =>
        (name: id.split('/').last, id: id, folder: false);
    final documents = _Documents({
      'tree/Artist/Album': [
        entry('tree/Artist/Album/Song.lrc'),
        entry('tree/Artist/Album/cover.mp4'),
      ],
    });
    final (:leftovers) = setUpLeftovers(
      documents: documents,
      deleteSong: (_) async => true,
      documentPlace: (_) async => (
        name: 'Song.flac',
        folders: [(uri: 'tree/Artist/Album', root: false)],
      ),
    );

    expect(
      await leftovers.delete(
        'content://com.example.documents/tree/a/document/b',
      ),
      isTrue,
    );
    await _until(() => documents.deleted.isNotEmpty);

    expect(documents.deleted, ['tree/Artist/Album/Song.lrc']);
  });
}
