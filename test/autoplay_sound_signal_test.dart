import 'dart:math';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:mornye/services/autoplay_sound_signal.dart';
import 'package:mornye/services/music_player_service.dart';
import 'package:mornye/services/song_features.dart';

PlayableMedia track(String id) =>
    PlayableMedia(id: id, source: '/$id.flac', title: id, artist: 'by $id');

/// Features of a song: [timbre] moves its MFCCs and brightness together.
Float32List sound({
  double timbre = 0,
  double tempo = 120,
  double tempoConfidence = 0.9,
  double loudness = -10,
  int key = 0,
  bool major = true,
  double keyStrength = 0.8,
}) {
  final v = Float32List(SongFeatures.length);
  v[SongFeatures.tempo] = tempo;
  v[SongFeatures.tempoConfidence] = tempoConfidence;
  v[SongFeatures.loudness] = loudness;
  v[SongFeatures.loudnessSpread] = 3;
  v[SongFeatures.centroid] = 2000 * exp(0.3 * timbre);
  v[SongFeatures.centroidSpread] = 500;
  v[SongFeatures.rolloff] = 4000 * exp(0.3 * timbre);
  v[SongFeatures.flatness] = 0.01;
  v[SongFeatures.onsetRate] = 2;
  v[SongFeatures.flux] = 0.05;
  v[SongFeatures.bands] = 0.5;
  v[SongFeatures.bands + 1] = 0.4;
  v[SongFeatures.bands + 2] = 0.1;
  v[SongFeatures.key] = key.toDouble();
  v[SongFeatures.mode] = major ? 1 : 0;
  v[SongFeatures.keyStrength] = keyStrength;
  for (var i = 0; i < SongFeatures.mfccCount; i++) {
    v[SongFeatures.mfcc + i] = timbre * (i.isEven ? 1 : -1);
    v[SongFeatures.mfccSpread + i] = 1;
  }
  return v;
}

void main() {
  final seed = track('seed');

  test('songs without analysis score and follow as an average analyzed '
      'song', () {
    final near = track('near'), far = track('far'), unknown = track('new');
    final signal = SoundAutoplaySignal({
      seed.source: sound(),
      near.source: sound(timbre: 0.3, tempo: 121),
      far.source: sound(timbre: 4, tempo: 150, loudness: -3),
    })..prepare([seed], [near, far, unknown]);
    expect(
      signal.score(unknown),
      closeTo((signal.score(near) + signal.score(far)) / 2, 1e-9),
    );
    expect(
      signal.follow(seed, unknown),
      closeTo((signal.follow(seed, near) + signal.follow(seed, far)) / 2, 1e-9),
    );
    // Nothing is known about how songs follow an unanalyzed one.
    expect(signal.follow(unknown, near), 0);
    expect(signal.follow(unknown, far), 0);
  });

  test('a weak key earns no key bonus', () {
    final fifth = track('fifth');
    final signal = SoundAutoplaySignal({
      seed.source: sound(key: 0, keyStrength: 0.3),
      fifth.source: sound(key: 7),
    })..prepare([seed], [fifth]);
    expect(signal.follow(seed, fifth), closeTo(0, 1e-9));
  });
}
