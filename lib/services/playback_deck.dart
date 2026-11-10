import 'dart:async';

import 'package:flutter/foundation.dart';

/// Transport state reported by a [PlaybackDeck].
enum PlaybackDeckState { stopped, playing, paused, completed, disposed }

/// In-app output gain from 0 to 1 for platforms where the app must not change
/// (normalization, AutoMix fades) by this value. Mobile keeps it at 1.
final playbackOutputVolume = ValueNotifier<double>(1);

/// A deck that can append a source to its existing output without opening a
/// second device. The start callback replaces the ordinary completion handoff.
abstract interface class QueuedPlaybackDeck implements PlaybackDeck {
  bool get canQueueNextSource;
  Future<bool> queueNextSource(
    String source, {
    required double volume,
    required void Function() onStarted,
  });
  Future<void> clearNextSource();
}

/// One audio player owned by the music runtime. The main player and each
/// AutoMix deck are separate instances created through
/// `PlaybackDependencies.createDeck`.
///
/// Every implementation also:
/// - applies `playbackEqualizer` (`playback_equalizer.dart`) to its own output
///   and reports failures with `reportEqualizerError`, keyed by player id;
/// - multiplies [setVolume] by [playbackOutputVolume];
/// - delivers runtime failures as errors on [onPlayerStateChanged], and makes
///   [resume] throw when the current source failed to load.
abstract interface class PlaybackDeck {
  PlaybackDeckState get state;

  /// True while a native output bypasses the mixer (Android USB bit-perfect).
  /// Volume, rate and the equalizer have no effect in that mode.
  bool get isDirect;

  /// True when a direct output lost its route and the source must be loaded
  /// again before playback can continue.
  bool get needsSourceReload;

  Stream<PlaybackDeckState> get onPlayerStateChanged;
  Stream<Duration> get onDurationChanged;
  Stream<Duration> get onPositionChanged;
  Stream<void> get onPlayerComplete;

  /// Emits [onPositionChanged] every [interval] while playing. Updates must
  /// not depend on rendered frames: AutoMix runs with the display asleep.
  /// A tick [wanted] answers false for is skipped without asking the engine.
  void startPositionUpdates(Duration interval, {bool Function()? wanted});

  /// Applies the platform's music output configuration (audio focus, usage,
  /// keeping the source after completion). Called before each new source.
  Future<void> configureOutput();

  /// Linear gain from 0 to 1, before [playbackOutputVolume].
  Future<void> setVolume(double volume);
  Future<void> setPlaybackRate(double rate);

  /// Loads [url]. [arriving] marks a file still being written behind it (a
  /// song a paired phone is sending), which must start before it is whole.
  Future<void> setNetworkSource(String url, {bool arriving = false});

  /// Loads a local file. The output flags request Android's native USB and
  /// exclusive paths; other decks ignore them, except that [requiresDsd]
  /// throws when the deck cannot play DSD.
  Future<void> setFileSource(
    String path, {
    bool preferBitPerfect = false,
    bool directUsb = false,
    bool allowDop = false,
    bool requiresDsd = false,
    bool allowFixedVolume = false,
    bool dapExclusive = false,
  });

  /// Abandons a source that is still preparing, if any.
  Future<void> cancelPreparation();

  Future<void> resume();
  Future<void> pause();
  Future<void> seek(Duration position);
  Future<void> stop();

  /// Has the engine start [next], a paused deck holding a loaded source, the
  /// moment this deck's source ends, so the two songs join without a gap.
  /// [resume] on [next] afterwards only confirms the state. Returns false
  /// when the engine cannot chain these decks; [next] then waits for
  /// [resume]. Null unlinks a deck set earlier.
  Future<bool> setNextDeck(PlaybackDeck? next);

  Future<Duration?> getCurrentPosition();
  Future<Duration?> getDuration();

  /// Releases the native player. The deck is unusable afterwards.
  Future<void> dispose();
}
