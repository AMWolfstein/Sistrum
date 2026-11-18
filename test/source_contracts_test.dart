// These tests read production files across Dart, Kotlin and Swift by path and
// pin literal source text on purpose. When a refactor moves code, update the
// path or the assertion here in the same commit; never delete the check.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('app state database migrations', () {
    final source = File(
      'lib/services/app_state_database.dart',
    ).readAsStringSync();

    test('current schema retains v4 playback queue and scalar state', () {
      expect(source, contains('const _dbVersion = 5;'));
      expect(source, contains('await _migratePlaybackSessionToV4(db);'));
      expect(source, contains('media_json TEXT NOT NULL'));
      expect(source, contains('position_ms INTEGER NOT NULL DEFAULT 0'));
      expect(source, contains('updatePlaybackSessionState'));
    });
  });
}
