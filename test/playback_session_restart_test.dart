import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart'
    show databaseFactoryFfi, sqfliteFfiInit;
import 'package:sistrum/services/app_state_database.dart';
import 'package:sistrum/services/file_access_check.dart';
import 'package:sistrum/services/playable_media.dart';
import 'package:sistrum/services/playback_session_restore.dart';
import 'package:sistrum/utils/file_access.dart';

PlayableMedia _song(int i) => PlayableMedia(
  id: 'song-$i',
  source: '/card/Artist/Song $i.flac',
  title: 'Song $i',
  artist: 'Artist',
  album: 'Album',
);

void main() {
  late Directory root;
  String path() => '${root.path}/app_state.db';

  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
    SharedPreferences.setMockInitialValues({});
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });
  setUp(() async {
    root = await Directory.systemTemp.createTemp('playback-restart-');
  });
  tearDown(() => root.delete(recursive: true));

  test('a saved queue, position and modes survive a process restart', () async {
    final media = [for (var i = 0; i < 3; i++) _song(i)];
    final first = await AppStateDatabase.openWithFactory(
      databaseFactoryFfi,
      path(),
    );
    try {
      await AppStateDatabase.withDatabase(first).savePlaybackSession(
        mediaJson: Uint8List.fromList(
          utf8.encode(
            jsonEncode([
              for (var i = 0; i < media.length; i++)
                {...media[i].toJson(), 'queueOriginalIndex': i},
            ]),
          ),
        ),
        index: 1,
        positionMs: 4200,
        shuffle: true,
        repeatMode: 'all',
      );
    } finally {
      await first.close();
    }

    // A new process opens the same file and finds the same session.
    final second = await AppStateDatabase.openWithFactory(
      databaseFactoryFfi,
      path(),
    );
    try {
      final session = await AppStateDatabase.withDatabase(
        second,
      ).getPlaybackSession();
      expect(session, isNotNull);
      expect(session!['index'], 1);
      expect(session['positionMs'], 4200);
      expect(session['shuffle'], isTrue);
      expect(session['repeat'], 'all');

      final restored = await prepareRestoredPlaybackMedia(
        session['media'] as List<Object?>,
        checkAccess: (_) async => const FileAccessFound(FileAccessStat()),
        loadLibraryCoverPath: (_) async => null,
      );
      expect(restored.items.map((item) => item.id), [
        'song-0',
        'song-1',
        'song-2',
      ]);
      expect(restored.keptOriginalIndices, [0, 1, 2]);
    } finally {
      await second.close();
    }
  });

  test('an unreadable card after restart keeps every saved entry', () async {
    final media = [for (var i = 0; i < 2; i++) _song(i)];
    final first = await AppStateDatabase.openWithFactory(
      databaseFactoryFfi,
      path(),
    );
    await AppStateDatabase.withDatabase(first).savePlaybackSession(
      mediaJson: Uint8List.fromList(
        utf8.encode(jsonEncode([for (final item in media) item.toJson()])),
      ),
      index: 0,
      positionMs: 0,
      shuffle: false,
      repeatMode: 'none',
    );
    await first.close();

    final second = await AppStateDatabase.openWithFactory(
      databaseFactoryFfi,
      path(),
    );
    try {
      final session = await AppStateDatabase.withDatabase(
        second,
      ).getPlaybackSession();
      final restored = await prepareRestoredPlaybackMedia(
        session!['media'] as List<Object?>,
        checkAccess: (_) async => FileAccessUnavailable(
          const FileSystemException('Input/output error'),
          StackTrace.empty,
        ),
        loadLibraryCoverPath: (_) async => null,
      );
      expect(restored.keptOriginalIndices, [0, 1]);
      expect(restored.items, hasLength(2));
    } finally {
      await second.close();
    }
  });
}
