import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:sistrum/services/platform_bridge.dart';
import 'package:sistrum/services/source_deletion_events.dart';
import 'package:sistrum/utils/file_access.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.amwolfstein.sistrum/backend');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  // A removable volume that is not mounted: its root does not exist.
  const unplugged = '/storage/1234-ABCD/Music/Album/01.flac';
  late Directory root;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('removable_volume_');
  });
  tearDown(() async {
    messenger.setMockMethodCallHandler(channel, null);
    await root.delete(recursive: true);
  });

  test('a file on an unmounted card is unavailable, not missing', () async {
    expect(await storageUnavailableFor(unplugged), isTrue);
    expect(await storageUnavailableFor('$unplugged#track01'), isTrue);
  });

  test(
    'a missing file in a present folder is not unavailable storage',
    () async {
      final album = await Directory(p.join(root.path, 'Album')).create();
      expect(
        await storageUnavailableFor(p.join(album.path, 'gone.flac')),
        isFalse,
      );
      expect(await storageUnavailableFor(''), isFalse);
    },
  );

  test('deleting a file on an unmounted card is not a deletion', () async {
    final events = SourceDeletionEvents();
    final reactions = <String>[];
    events.subscribe((source) async => reactions.add(source));
    expect(await deleteFile(unplugged, deletionEvents: events), isFalse);
    expect(reactions, isEmpty);
  });

  group('SAF documents', () {
    const uri =
        'content://com.android.externalstorage.documents/document/'
        '1234-ABCD%3AMusic%2Fa.flac';
    final calls = <String>[];

    void answer(Object? exists, {bool deleted = false}) {
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        return switch (call.method) {
          'safExists' => exists,
          'safDelete' => deleted,
          _ => throw MissingPluginException(call.method),
        };
      });
    }

    setUp(calls.clear);

    test(
      'a provider that cannot tell (unmounted volume) is unavailable',
      () async {
        answer(null);
        expect(await storageUnavailableFor(uri), isTrue);
        expect(await PlatformBridge.safExistsState(uri), isNull);
      },
    );

    test(
      'a confirmed missing document on mounted storage is not unavailable',
      () async {
        answer(false);
        expect(await storageUnavailableFor(uri), isFalse);
      },
    );

    test('an unknown SAF document is never reported deleted', () async {
      answer(null);
      final events = SourceDeletionEvents();
      final reactions = <String>[];
      events.subscribe((source) async => reactions.add(source));
      expect(await deleteFile(uri, deletionEvents: events), isFalse);
      expect(reactions, isEmpty);
      expect(calls, ['safDelete', 'safExists']);
    });
  });
}
