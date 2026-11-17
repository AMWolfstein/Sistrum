import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/cover_store.dart';

void main() {
  test('durableCoverReference keeps ordinary covers and drops loopback', () {
    expect(durableCoverReference(null), isNull);
    expect(durableCoverReference('  '), isNull);
    expect(
      durableCoverReference('https://example.test/cover.jpg'),
      'https://example.test/cover.jpg',
    );
    expect(durableCoverReference('http://127.0.0.1:41234/7f3a'), isNull);
    expect(
      durableCoverReference('/music/covers/cover.jpg'),
      '/music/covers/cover.jpg',
    );
    expect(
      durableCoverReference('file:///music/covers/cover.jpg'),
      'file:///music/covers/cover.jpg',
    );
  });

  test('durableCoverReference drops covers in temporary folders', () {
    expect(
      durableCoverReference(
        '/data/user/0/app/cache/download_cover_preview_1/cover.jpg',
      ),
      isNull,
    );
    expect(
      durableCoverReference(
        Uri.file('${Directory.systemTemp.path}/cover.jpg').toString(),
      ),
      isNull,
    );
  });

  test('localCoverPath reads absolute paths and file URIs only', () {
    expect(localCoverPath('/music/cover.jpg'), '/music/cover.jpg');
    expect(
      localCoverPath('file:///music/cover%20one.jpg'),
      '/music/cover one.jpg',
    );
    expect(localCoverPath('https://example.test/cover.jpg'), isNull);
    expect(localCoverPath('content://media/external/images/1'), isNull);
  });

  test(
    'CoverStore.file answers existing local files and nothing else',
    () async {
      final directory = await Directory.systemTemp.createTemp('cover-store-');
      addTearDown(() => directory.delete(recursive: true));
      final cover = File('${directory.path}/cover.jpg')..writeAsBytesSync([1]);
      expect(await CoverStore.instance.file(cover.path), cover.path);
      expect(
        await CoverStore.instance.file('${directory.path}/missing.jpg'),
        isNull,
      );
      expect(await CoverStore.instance.file(null), isNull);
      expect(await CoverStore.instance.file(''), isNull);
      expect(
        await CoverStore.instance.file('https://example.test/cover.jpg'),
        isNull,
      );
    },
  );
}
