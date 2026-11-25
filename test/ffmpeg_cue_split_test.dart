import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sistrum/services/ffmpeg_cue_split.dart';
import 'package:sistrum/models/ffmpeg_models.dart';
import 'package:sistrum/services/ffmpeg_desktop.dart';
import 'package:sistrum/services/ffmpeg_probe.dart';
import 'package:sistrum/utils/desktop_platform.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late DesktopFFmpegTools tools;
  late DesktopFFmpegTools conversionTools;
  late Directory directory;

  setUpAll(() async {
    tools = await DesktopFFmpegTools.locate(
      environment: {'PATH': Platform.environment['PATH'] ?? ''},
    );
    // Fixture generation needs lavfi. A bundled converter can still be tested
    // via SISTRUM_FFMPEG_DIR even when that bundle excludes input generators.
    conversionTools = await DesktopFFmpegTools.locate();
  });

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('cue-split-');
    debugDesktopPlatformOverride = true;
    DesktopFFmpegRunner.debugShared = DesktopFFmpegRunner(
      tools: () async => conversionTools,
    );
  });

  tearDown(() async {
    debugDesktopPlatformOverride = null;
    DesktopFFmpegRunner.debugShared = null;
    await directory.delete(recursive: true);
  });

  bool haveTools() {
    if (tools.ffmpegPath != null &&
        tools.ffprobePath != null &&
        conversionTools.ffmpegPath != null &&
        conversionTools.ffprobePath != null) {
      return true;
    }
    markTestSkipped('Requires ffmpeg and ffprobe on PATH');
    return false;
  }

  Future<String> ffmpeg(List<String> arguments) async {
    final result = await Process.run(tools.ffmpegPath!, [
      '-nostdin',
      '-v',
      'error',
      ...arguments,
    ]);
    expect(result.exitCode, 0, reason: result.stderr.toString());
    return result.stdout as String;
  }

  Future<String> source(int depth, int rate) async {
    final path = '${directory.path}/Song "live".wav';
    await ffmpeg([
      '-f',
      'lavfi',
      '-i',
      'aevalsrc=0.4*sin(440*2*PI*t)|0.3*sin(997*2*PI*t):s=$rate:d=0.3',
      '-c:a',
      'pcm_s${depth}le',
      path,
    ]);
    return path;
  }

  Future<String> pcmHash(String path, int depth) => ffmpeg([
    '-i',
    path,
    '-map',
    '0:a:0',
    '-c:a',
    'pcm_s${depth}le',
    '-f',
    'hash',
    '-hash',
    'sha256',
    '-',
  ]);

  test('CUE split keeps the original and preserves every PCM sample', () async {
    if (!haveTools()) return;
    final wav = await source(24, 48000);
    final input = '${directory.path}/Album.flac';
    await ffmpeg(['-i', wav, '-c:a', 'flac', input]);
    final originalHash = await pcmHash(input, 24);
    final folder = await Directory('${directory.path}/split').create();
    final existing = await File(
      '${folder.path}/01 - First.flac',
    ).writeAsString('existing split');
    final paths = await FFmpegCueSplit.splitCueToTracks(
      audioPath: input,
      outputDir: folder.path,
      tracks: [
        CueSplitTrackInfo(
          number: 1,
          title: 'First',
          artist: 'Artist',
          startSec: 0,
          endSec: 0.15,
        ),
        CueSplitTrackInfo(
          number: 2,
          title: 'Second',
          artist: 'Artist',
          startSec: 0.15,
          endSec: -1,
        ),
      ],
      albumMetadata: {'album': 'Album', 'artist': 'Artist'},
    );
    expect(paths, hasLength(2));
    expect(paths!.first, '${folder.path}/01 - First (2).flac');
    expect(await existing.readAsString(), 'existing split');
    expect(await File(input).exists(), isTrue);
    expect(await pcmHash(input, 24), originalHash);
    final combinedHash = await ffmpeg([
      '-i',
      paths[0],
      '-i',
      paths[1],
      '-filter_complex',
      '[0:a:0][1:a:0]concat=n=2:v=0:a=1[a]',
      '-map',
      '[a]',
      '-c:a',
      'pcm_s24le',
      '-f',
      'hash',
      '-hash',
      'sha256',
      '-',
    ]);
    expect(combinedHash, originalHash);
    for (final path in paths) {
      expect((await FFmpegProbe.audioProperties(path)).bitDepth, 24);
    }
  });
}
