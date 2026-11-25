import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/controllers/song_file.dart';

void main() {
  test('a song row plays only a CUE-free file that is still there', () async {
    final directory = await Directory.systemTemp.createTemp('song-can-play-');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/Song.flac')
      ..writeAsStringSync('audio');

    expect(await songFileCanPlay(file.path), isTrue);
    expect(await songFileCanPlay('EXISTS:${file.path}'), isTrue);
    expect(await songFileCanPlay('${directory.path}/Gone.flac'), isFalse);
    expect(await songFileCanPlay('${file.path}#track01'), isFalse);
    expect(await songFileCanPlay(null), isFalse);
    expect(await songFileCanPlay('  '), isFalse);
    expect(await songFileCanPlay('network://nas/Song.flac'), isTrue);
  });
}
