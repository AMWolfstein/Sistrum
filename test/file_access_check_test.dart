import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/file_access_check.dart';
import 'package:sistrum/utils/file_access.dart';

void main() {
  test('found files retain their size and modification time', () async {
    final stat = FileAccessStat(size: 123, modified: DateTime(2026));
    final result = await checkFileAccess(
      '/music/track.flac',
      readStat: (_) async => stat,
    );
    expect(result, isA<FileAccessFound>());
    expect((result as FileAccessFound).stat, same(stat));
  });

  test('only a confirmed absent file is missing', () async {
    final result = await checkFileAccess(
      '/music/removed.flac',
      readStat: (_) async => null,
    );
    expect(result, isA<FileAccessMissing>());
  });

  for (final code in ['permission_denied', 'provider_unavailable']) {
    test('$code is unavailable and retains its diagnostic', () async {
      final error = PlatformException(code: code);
      final result = await checkFileAccess(
        'content://provider/music/track.flac',
        readStat: (_) async => throw error,
      );
      expect(result, isA<FileAccessUnavailable>());
      expect((result as FileAccessUnavailable).error, same(error));
      expect(result.stack.toString(), isNotEmpty);
    });
  }

  test('uniqueFilePath skips names that are already taken', () async {
    final directory = await Directory.systemTemp.createTemp('unique_path_');
    addTearDown(() => directory.delete(recursive: true));
    File('${directory.path}/Album_cover.jpg').writeAsStringSync('one');
    File('${directory.path}/Album_cover (2).jpg').writeAsStringSync('two');

    final result = await uniqueFilePath(directory.path, 'Album_cover.jpg');

    expect(result, endsWith('Album_cover (3).jpg'));
  });
}
