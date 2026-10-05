# Crossfade inside ONE Media3 player: prior art and design comparison

Source: external research (Arena.ai), 2026-10-05. Condensed. Items marked
[unverified] are the researcher's own flags. The report references a companion
document "pure-kotlin-deferred-formats" that is not included here.

## Headline
1. Nobody has shipped crossfade inside a single ExoPlayer/Media3 instance (no
   two-decoder renderer, no mixing AudioSink/AudioProcessor, no merging
   MediaSource, no CompositionPlayer-for-playback). Every open-source crossfade
   found uses two players.
2. Media3 maintainers sketched the one-player design in 2018 and advised against
   it (messy Timeline, not general purpose); the crossfade feature request
   androidx/media#2 is still open with "no current plans".
3. Shipped building blocks: AudioMixer / DefaultAudioMixer (Transformer,
   @UnstableApi; aligned multi-source mixing; addSource / removeSource /
   setSourceVolume at any time) and CompositionPlayer (1.9.0, experimental).

## Two-player precedents
- cromaguy/Rhythm (GPL-3.0-or-later, active): RhythmPlayerEngine.kt (player A is
  the master exposed to MediaSession, B pre-buffers and fades in, roles swap),
  TransitionController.kt (IDLE/SCHEDULED/PREPARING/TRANSITIONING/CLEANUP;
  cancels and reschedules on seek, timeline, play/pause, repeat changes),
  PreloadController.kt, per-player ReplayGainAudioProcessor. Every DSP processor
  is instantiated twice.
- lovegaoshi/react-native-track-player, APM branch (Apache-2.0): PR #38
  "feat: crossfade"; ForwardingPlayer.java (+1,220 lines) synthesizes one Player
  over two ExoPlayers for MediaSession; AudioPlayer.kt ramps volume from a
  coroutine.

## Maintainer record
- androidx/media#2 (open since 2021): duplicate of google/ExoPlayer#3438.
- #3438 (andrewlewis, 2018): one could hack it with concurrent audio renderers, a
  mixing AudioProcessor, a custom renderer writing to the mixer and
  merging/clipping streams, "but this is going to give a messy player Timeline …
  I'd advise against it". ojw28: "major surgery". krocard (2021): during a fade
  the position would jump over the end of the outgoing track.
- Community findings in #2: an AudioProcessor fade-out works only for natural
  track ends (the processor runs seconds ahead of output, so pause/skip fades
  can't be anticipated); AudioProcessor can't tell a seek flush from a new item.
  No VolumeShaper access shipped.

## Media3 pieces
- DefaultAudioMixer: the real primitive; sources aligned to one clock, silence
  fill, live volume changes. @UnstableApi in the Transformer package: re-verify
  on every Media3 upgrade. Not used for playback anywhere public.
- CompositionPlayer: a SimpleBasePlayer with DefaultAudioSink + DefaultAudioMixer
  internally (Google's own single-player multi-source mixer), but an editing
  preview tool: overlapped sequences fail (#2866), only REPEAT_MODE_ALL/OFF,
  every queue edit rebuilds the composition (#3204), no LoadControl (#2417), no
  preload manager (#3034), stability issues (#3019, #3170). Not usable as a music
  queue player in 2026.
- ConcatenatingMediaSource2: sequential concat into one window (gapless), no
  overlap.
- Missing: multi-input AudioProcessor, public two-decoder renderer, VolumeShaper
  accessor, shared-AudioSink API. An AudioSink has one owner, one format, one
  clock.
- Shared audio session for A/B: Player.getAudioSessionId (@UnstableApi) and
  DefaultAudioSink accept a session id. [unverified] whether session-scoped
  AudioEffects then process the SUM of both tracks or each track separately:
  this is exactly what Sistrum's DynamicsProcessing spike must measure.

## Non-Android precedents (one-output mixers)
- MPD (GPL-2.0-or-later): src/player/CrossFade.hxx/.cxx and Thread.cxx. Two decode
  pipes mixed chunk-by-chunk before one output. Policies: no crossfade for songs
  under 20 s; crossfade only when both formats match; MixRamp (tag-driven fade
  points); the next song's tag owns the UI from fade start (cross_fade_tag).
- Mixxx (GPL-2.0-or-later): enginebuffer.cpp (per-deck decode), enginemixer.cpp
  (master bus), enginesidechaincompressor.cpp (compressor on the summed signal):
  proof that limiter/EQ on the mix is natural in mix-in-app designs.
- GStreamer adder vs audiomixer: synchronization, not summation, is the hard part;
  DefaultAudioMixer already handles alignment.

## Comparison
| | (1) Two players A/B | (2) Two players -> one shared sink | (3) One player + in-app mixer |
|---|---|---|---|
| Prior art | shipped (Rhythm, RNTP-APM) | none; unsupported by Media3 | none in Media3; MPD/Mixxx on desktop |
| Complexity | transition layer (1,000+ lines) | highest, negative value | high: custom AudioSink around DefaultAudioMixer, side decoder, timeline/seek policy; deletes A/B machinery |
| Memory | two pipelines, DSP twice | n/a | one pipeline + one extra decoder + mixer buffers |
| Limiter/EQ on the mix | structurally impossible in-app (unless session effects act on the sum: unverified) | n/a | yes, naturally |
| One audio session | needs manual unification | n/a | by construction |
| MediaSession | on master or a synthesized ForwardingPlayer | n/a | on the one real player |
| Gapless with crossfade off | B must not exist | n/a | free |

Verdict: drop (2). (1) is the only proven design. (3) is the only one that
meets "limiter on the mix, one session, one player" by construction, but it is
greenfield in Media3.

## Recommendation (researcher)
1. Ship 001 with A/B: shared session id, lazy player B, per-stream limiting as a
   documented compromise if needed.
2. Prototype (3) later behind the PlaybackEngine abstraction: (a) standalone
   DefaultAudioMixer fed by two decoders to validate fades and limiter-on-mix;
   (b) a custom AudioSink for one ExoPlayer pulling the side decoder through the
   mixer; (c) timeline policy: clip the outgoing item by the fade length so the
   next track owns the fade (MPD model), hard cut on seek/skip.
3. Watch androidx/media#2 and CompositionPlayer's evolution.
