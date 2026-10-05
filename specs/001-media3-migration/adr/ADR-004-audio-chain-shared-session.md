# ADR-004 — Audio chain, shared session, limiter

Status: Accepted (owner, 2026-10-05); **S2 PARTIAL 2026-10-05 (T003)**: shared session + routing confirmed, limiter level not yet measured (T003b); **pending S3** · Date: 2026-10-05 ·
Spec: US7, FR-035, FR-036, FR-044, FR-050…056, SC-011, SC-015 · Research: D5, `research/dynamics-processing-session.md`

## Context

Two ExoPlayers mean the mix happens in AudioFlinger. EQ, preamp and width must match the native engine exactly;
the limiter must see the sum of both players.

## Decision

Per player (Media3 `AudioProcessor` chain, built by a `DefaultRenderersFactory.buildAudioSink` override):

1. `GainProcessor` — normalization gain × fade curve, per sample, with the 100–300 ms ramps of FR-046a inside.
2. `StereoWidthProcessor` — Media3 `ChannelMixingAudioProcessor` with matrix
   `L' = ((1+w)/2)L + ((1−w)/2)R`, `R' = ((1−w)/2)L + ((1+w)/2)R` (same as native mid/side).
3. `EqualizerProcessor` — 10 RBJ peaking biquads, Q = 1, at 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16 kHz, the
   native coefficient formula (`ffmpeg_player.cpp:321-339`), float state per channel; coefficient changes
   crossfaded over a short block, filter state never reset (FR-056).
4. `PreampProcessor` — linear gain (may fold into 3).

Then `player.volume` = focus duck (0.2 ramp, D7). Both players share one audio session id and identical
`AudioAttributes`; offload disabled. On the session: one `DynamicsProcessing`, **limiter stage only**
(input/output gain 0 dB, pre-EQ/MBC/post-EQ off), threshold ≈ −1 dBFS, fast attack, explicit release/ratio/
post-gain, both channels in one link group, `setPreferredFrameDuration` = real sink buffer duration.

- Creation failure → no limiter; EQ/preamp/width unaffected; clip-prevention setting shows a note (FR-053).
- `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` broadcast with the session id and package (FR-054).
- Control loss (`OnControlStatusChangeListener`) → note "limiter not active"; restored on regain (FR-055).
- The current engine is untouched (its native DSP stays as is).

## S2 result (2026-10-05, T003, CPH2307, `.qa`, Media3 1.11.1) — PARTIAL

Harness: `androidTest/.../spikes/SharedSessionLimiterSpikeTest.kt`.

Passed:
- **Shared session — only with re-apply after ready + check (requirement).** `ExoPlayer.setAudioSessionId` is applied
  asynchronously and the player's own initial session can land later and overwrite it: without the check both
  players ran on their own sessions (4873, 4881) while the DP sat orphaned on the shared one, i.e. the limiter would
  silently have covered neither or only one player. With "after STATE_READY, before play: if
  `player.audioSessionId != shared` re-set it and poll until equal (≤ 3 s), else fail" both players and their
  AudioTracks used the shared session. **Every player creation/preparation in `Media3Engine` MUST do this check, and
  a player not on the shared session MUST NOT start** (spec FR-036).
- **Routing:** during the two-player overlap the session's effect chain runs on the normal MIXER music thread
  (`AudioOut_1D`); a 24-bit/96 kHz WAV also stays on that mixer thread (no direct output on this device).
- The limiter-only DP config is accepted (frame duration 4 ms from `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`/rate).

**Not yet measured (T003b, blocks M5):** whether the limiter reduces a summed overlap that would clip, the SC-011
"no reduction below threshold" check, and whether disabling the limiter or the whole DP mutes or attenuates the
session (FR-053/055). The Visualizer could not measure them: on the session it is inserted first (reads the DP
input); on the output mix it read near-silence whenever the DP was disabled.

**Withdrawn claims** (from earlier S2 runs, both wrong): "DP on the mixer thread with both players" (it was an orphan
chain on an idle in-call thread) and "the limiter acts before volume" (the difference was two 10 Hz tones partly
cancelling).

## Consequences

- Order vs the native engine (fade → EQ → preamp/width → focus duck, `ffmpeg_player.cpp:375-400`): the EQ is
  linear and identical on both channels, so it commutes with the width matrix and the scalar preamp; gain/fade stay
  before the EQ in both. Output equals the native chain up to float rounding (`DspLinearityTest`, T043).

- Per-player = on-the-mix exactly for linear stages (fade before EQ, ADR-003). Shapes identical when the track
  rate equals the native output rate; band-centre gains identical always (research D5 exactness note).
- Limiter feed-forward without lookahead: bounded overshoot on loud overlaps, measured in S2 (SC-011).
- Direct-output routes (some hi-res) bypass session effects: EQ still applies (in-app), only the limiter is lost
  there (S2 checks the corpus).
- CPU: four small processors per player; two players only during fades (S3).

## Alternatives

- DP pre-EQ for the EQ (owner: no, response differs). `audiofx.Equalizer` (auto-attenuation). Processing at a
  fixed output rate (exact shapes for every rate; extra resampler CPU — decided after S3).
