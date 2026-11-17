import 'dart:io';

import 'package:audio_service/audio_service.dart' show MediaItem;
import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/library_media_identity.dart';

void main() {
  test('a loved song keeps only a cover that outlasts this run', () {
    String? cover(String artUri) => collectionTrackForMedia(
      MediaItem(
        id: '/music/Album/song.flac',
        title: 'Song',
        artUri: Uri.parse(artUri),
      ),
      null,
    ).coverUrl;

    for (final durable in [
      'file:///data/user/0/app/files/covers/a_b.image',
      'https://example.com/cover.jpg',
    ]) {
      expect(cover(durable), durable);
    }
    for (final temporary in [
      'http://127.0.0.1:41234/7f3a',
      Uri.file('${Directory.systemTemp.path}/cover.jpg').toString(),
    ]) {
      expect(cover(temporary), isNull, reason: temporary);
    }
  });
}
