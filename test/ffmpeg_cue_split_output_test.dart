import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/ffmpeg_cue_split.dart';
import 'package:sistrum/services/ffmpeg_models.dart';

void main() {
  late Directory directory;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('cue-split-output-');
  });
  tearDown(() async => directory.delete(recursive: true));

  Future<File> input(String name) =>
      File('${directory.path}/$name').writeAsString('source');

  Future<FFmpegResult> succeed(List<String> arguments) async {
    await File(arguments[arguments.length - 2]).writeAsString('converted');
    return FFmpegResult(success: true, returnCode: 0, output: '');
  }

  Future<List<String>?> splitCue(
    String source,
    Future<FFmpegResult> Function(List<String>) execute,
  ) => FFmpegCueSplit.splitCueToTracks(
    audioPath: source,
    outputDir: directory.path,
    tracks: [
      CueSplitTrackInfo(
        number: 1,
        title: 'Track',
        artist: 'Artist',
        startSec: 0,
        endSec: -1,
      ),
    ],
    albumMetadata: {'album': 'Album'},
    execute: execute,
  );

  test('repeated CUE splits preserve existing tracks and the source', () async {
    final source = await input('Album.flac');
    final sibling = await input('01 - Track.flac');
    final sourceBytes = await source.readAsBytes();
    final siblingBytes = await sibling.readAsBytes();
    final first = await splitCue(source.path, succeed);
    final second = await splitCue(source.path, succeed);

    expect(await sibling.readAsBytes(), siblingBytes);
    expect(first, ['${directory.path}/01 - Track (2).flac']);
    expect(second, ['${directory.path}/01 - Track (3).flac']);
    expect(await source.readAsBytes(), sourceBytes);
    expect(await File(first!.single).readAsString(), 'converted');
    expect(await File(second!.single).readAsString(), 'converted');
    expect(await directory.list().length, 4);
  });

  for (final failure in ['failed', 'throw', 'empty']) {
    test(
      'CUE $failure removes partial output and keeps existing files',
      () async {
        final source = await input('Album.flac');
        final sibling = await input('01 - Track.flac');
        final sourceBytes = await source.readAsBytes();
        final siblingBytes = await sibling.readAsBytes();
        final paths = await splitCue(source.path, (arguments) async {
          if (failure != 'empty') {
            await File(
              arguments[arguments.length - 2],
            ).writeAsString('partial');
          }
          if (failure == 'throw') throw StateError('Fixture failure');
          return FFmpegResult(
            success: failure == 'empty',
            returnCode: failure == 'empty' ? 0 : 1,
            output: '',
          );
        });

        expect(paths, isNull);
        expect(await source.readAsBytes(), sourceBytes);
        expect(await sibling.readAsBytes(), siblingBytes);
        expect(await directory.list().length, 2);
      },
    );
  }
}
