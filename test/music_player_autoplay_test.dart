import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/models/settings.dart';
import 'package:mornye/services/library_database.dart';
import 'package:mornye/services/music_player_service.dart';

PlayableMedia track(
  String id, {
  String artist = 'Artist',
  String? genre,
  String? source,
}) => PlayableMedia(
  id: id,
  source: source ?? '/$id.flac',
  title: id,
  artist: artist,
  genre: genre,
);

/// Favors 'a', then each song after the one it names.
class _ChainSignal extends AutoplaySignal {
  _ChainSignal(this.after);

  final Map<String, String> after;
  List<PlayableMedia> session = const [], candidates = const [];

  @override
  String get name => 'chain';

  @override
  void prepare(List<PlayableMedia> session, List<PlayableMedia> candidates) {
    this.session = session;
    this.candidates = candidates;
  }

  @override
  double score(PlayableMedia candidate) => candidate.id == 'a' ? 5 : 0;

  @override
  double follow(PlayableMedia previous, PlayableMedia candidate) =>
      after[candidate.id] == previous.id ? 10 : 0;
}

class _NoNoise implements Random {
  @override
  double nextDouble() => 0;
  @override
  int nextInt(int max) => 0;
  @override
  bool nextBool() => false;
}

/// A Library whose every page holds [rows].
class _Library implements LibraryDatabase {
  _Library(this.rows);

  final List<Map<String, dynamic>> rows;

  @override
  Future<QueueLibraryCounts> getQueueCounts(
    QueueLibraryDbQuery request,
  ) async => QueueLibraryCounts(
    allTrackCount: rows.length,
    albumCount: 0,
    singleTrackCount: 0,
  );

  @override
  Future<List<Map<String, dynamic>>> getQueueTrackPage(
    QueueLibraryDbQuery request,
  ) async => rows;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {

  test('picks carry their score parts, which weights multiply', () {
    final seed = track('seed', artist: 'S', genre: 'Rock');
    List<AutoplayPick> rank(Map<String, double> weights) => rankAutoplayTracks(
      seed: seed,
      candidates: [
        track('rock', artist: 'R', genre: 'Rock'),
        track('chain', artist: 'C'),
      ],
      upcoming: [],
      recent: [seed],
      dismissedSources: {},
      random: _NoNoise(),
      limit: 1,
      signals: [
        _ChainSignal({'chain': 'seed'}),
      ],
      weights: weights,
    );
    final first = rank(const {}).single;
    // Following the seed (10) beats fitting its genre (6); both artists
    // are new to the session.
    expect(first.media.id, 'chain');
    expect(first.parts, {
      'genre': 0,
      'signal:chain': 0,
      'novelty': 1,
      'follow:chain': 10,
    });
    // A listener for whom transitions count little.
    final rock = rank(const {'follow:chain': 0.5}).single;
    expect(rock.media.id, 'rock');
    expect(rock.parts['genre'], 6);
    expect(rock.parts['follow:chain'], 0);
  });

  test('Autoplay preference and recommendation provenance survive restore', () {
    expect(AppSettings.fromJson({}).autoplay, isFalse);
    final settings = const AppSettings().copyWith(autoplay: true);
    expect(AppSettings.fromJson(settings.toJson()).autoplay, isTrue);
    final media = PlayableMedia.fromJson({
      ...track('one', genre: 'Rock').toJson(),
      'autoplay': true,
    })!;
    final restored = PlayableMedia.fromJson(media.toJson())!;
    expect(restored.autoplay, isTrue);
    expect(restored.genre, 'Rock');
    expect(restored.toMediaItem().extras?['autoplay'], isTrue);
  });

  test(
    'Autoplay draws on network Library songs while their host is reachable',
    () async {
      final dir = await Directory.systemTemp.createTemp('autoplay_library');
      addTearDown(() => dir.delete(recursive: true));
      final local = File('${dir.path}/local.flac')..writeAsStringSync('x');
      Map<String, dynamic> row(String id, String path) => {
        'item': {
          'id': id,
          'filePath': path,
          'trackName': id,
          'artistName': 'A',
        },
      };
      final asked = <String>[];
      final runtime = MusicPlayerRuntime(
        settings: const AppSettings(localLibraryEnabled: true),
        dependencies: PlaybackDependencies(
          libraryDatabase: _Library([
            row('local', local.path),
            row('missing', '${dir.path}/missing.flac'),
            row('phone 1', 'network://link-phone-1/a.flac'),
            row('phone 2', 'network://link-phone-1/b.flac'),
            row('away', 'network://link-phone-2/c.flac'),
            row('web', 'https://example.com/d.flac'),
          ]),
          networkSourceReachable: (source) async {
            asked.add(source);
            return source.contains('link-phone-1');
          },
        ),
      );

      final songs = await loadAutoplayLibrary(track('seed'), runtime);
      expect(songs.map((song) => song.id), ['local', 'phone 1', 'phone 2']);
      // One question per host.
      expect(asked, [
        'network://link-phone-1/a.flac',
        'network://link-phone-2/c.flac',
      ]);
    },
  );
}
