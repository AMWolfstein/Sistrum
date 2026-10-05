# One audio session, two ExoPlayers, one DynamicsProcessing

Source: external research (Arena.ai), 2026-10-05, from AOSP source (LineageOS
mirror lineage-23.2, about Android 16; line numbers may drift). Condensed.
[unverified] marks the researcher's open points.

## Key answer
On the same output thread, a session-attached effect processes the MIX of all
tracks in that session, once per mixer cycle. One DynamicsProcessing on a session
shared by two ExoPlayers is a true limiter over A+B.

Evidence (services/audioflinger/):
1. Threads.cpp PlaybackThread::addEffectChain_l(): one float buffer per
   non-global session; "Attach all tracks with same session ID to this chain"
   (track->setMainBuffer for each track).
2. Threads.cpp createTrack_l(): a new track of a session with an existing chain
   gets chain->inBuffer() as its main buffer.
3. Threads.cpp MixerThread::prepareTracks_l(): "Tracks with effects go into their
   own effects chain buffer"; AudioMixer sums all tracks into their main buffer.
4. Effects.cpp EffectChain::process_l(): the chain runs once over that summed
   buffer.
Rules: one chain per session framework-wide (AudioFlinger::createEffect refuses a
second); same-session tracks must share a routing strategy (createTrack_l
"mismatched strategy"); a chain created before any track is moved to the track's
thread later, so attach order doesn't matter. Session chains run before session 0
/ output-mix / device effects. Effect latency is common to both players, so fade
timing isn't skewed.

## When the effect would NOT see the sum
| Case | Result |
|---|---|
| Same mixer thread (same AudioAttributes, PCM, no offload) | sees A+B |
| Tracks on different output threads | the single chain moves to the newest track's thread; the other plays dry |
| Offload | chains never processed; APM refuses offload when a non-offloadable effect is enabled |
| MMAP / AAudio low latency | not processed (ExoPlayer DefaultAudioSink doesn't use it) |
| Direct output (some hi-res/multichannel formats) | session chains effectively don't work; test hi-res files on device |
| Fast mixer | bypasses chains; DefaultAudioSink never requests it |

## DynamicsProcessing in practice
- Pipeline per channel: inputGain -> Pre-EQ -> MBC -> Post-EQ -> Limiter ->
  outputGain. inputGain is the preamp.
- EqBand: enabled, cutoffFrequency, gain only (no Q); adjacent bands mapped to
  FFT bins. Band counts fixed in Config.Builder. No documented band cap
  [unverified].
- Limiter: attackTime, releaseTime, ratio, threshold, postGain, linkGroup. NO
  lookahead (feed-forward envelope follower). Same linkGroup on both channels for
  stereo-linked limiting.
- Engine: STFT, only VARIANT_FAVOR_FREQUENCY_RESOLUTION implemented. Block size
  from setPreferredFrameDuration (power of two, 8..16384, 50% overlap): set it to
  the real buffer duration.
- Latency and CPU: unpublished [unverified]; no extra AudioFlinger buffering
  stage, internal STFT buffering about one block.
- Native fallback limiter defaults: attack 50 ms, release 120 ms, ratio 2,
  threshold -30 dB: set every parameter explicitly.
- Availability: API 28+ and the device must ship the effect; constructing it can
  fail: probe and degrade. lissen-android (MIT, PR #538) hides its EQ when DP is
  unavailable and chose DP because the AOSP Equalizer auto-attenuates boosted
  output.

## Coexistence with other effects
- All effects on a session run in series in one chain. A second client attaching
  the same effect type binds to the same engine; priority decides control; the
  loser gets OnControlStatusChangeListener. Last writer wins on parameters.
- Wavelet attaches per player session (legacy mode: session 0); Poweramp EQ and
  Wavelet use DynamicsProcessing; RootlessJamesDSP uses AudioPlaybackCapture
  instead.
- ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION is a cooperative convention;
  Media3 doesn't send it (google/ExoPlayer#3058): the app must broadcast with
  EXTRA_AUDIO_SESSION and EXTRA_PACKAGE_NAME.
- OEM effects (Samsung SoundAlive/Dolby, Xiaomi, OnePlus): attach points not
  documented [unverified]; they run after our session chain. Device-test.

## Offload
Media3 AudioProcessors are PCM-only; session effects without
EFFECT_FLAG_OFFLOAD_SUPPORTED (DP has none) block offload. Offload saves power
mainly for long screen-off playback (Qualcomm: ExoPlayer offload within 5% of
MediaPlayer). No published PCM+effect vs offload numbers. Moot while crossfade
needs two players anyway.

## Stereo width
DynamicsProcessing has no cross-channel stage; no audiofx effect does M/S width.
Use a per-player Media3 ChannelMixingAudioProcessor with a custom
ChannelMixingMatrix: L' = ((1+w)/2)L + ((1-w)/2)R, R' = ((1-w)/2)L + ((1+w)/2)R.
Per-player processors run before the session chain, so the limiter still sees
the widened sum.

## Prior art
No open-source player found that attaches one DP to a session shared by two
players. lissen-android (single player DP EQ), Equalizer314 (DP EQ app),
Rhythm (in-app AudioProcessors per player, no session DP).

## Recommended wiring
1. Identical AudioAttributes on both players.
2. playerB.setAudioSessionId(playerA.audioSessionId).
3. Config.Builder(VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, preEq..., mbc off,
   postEq off, limiter on) + setPreferredFrameDuration; explicit limiter params.
4. Offload disabled.
5. Broadcast OPEN/CLOSE effect-control session.
6. Handle control loss and construction failure.
7. Stereo width as per-player ChannelMixingAudioProcessor.
Verify per device: adb shell dumpsys media.audio_flinger (both tracks on the
same thread, DP chain enabled on it).
