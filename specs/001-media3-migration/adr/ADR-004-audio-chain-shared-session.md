# ADR-004 — Audio chain, shared session, limiter

Status: Accepted (owner, 2026-10-05); **pending device spikes S2, S3** (first tasks after ADR-007's `.qa` build) · Date: 2026-10-05 ·
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
