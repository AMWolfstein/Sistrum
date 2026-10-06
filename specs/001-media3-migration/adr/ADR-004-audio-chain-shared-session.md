# ADR-004 — Audio chain, shared session, limiter

Status: Accepted (owner, 2026-10-05); **S2 PARTIAL 2026-10-05 (T003)**: shared session + routing confirmed, limiter level not measured on the device; **T003b closed 2026-10-06 as accepted on evidence (owner)**, device-level measurement = open follow-up, not a gate (below); **effect-state hedge approved 2026-10-05** (below); **S3 PASS 2026-10-05 (T004)**: track-rate processing, CPU requirement below; **width stage amended 2026-10-07 (owner)**: own ramped processor instead of `ChannelMixingAudioProcessor` (below) · Date: 2026-10-05 ·
Spec: US7, FR-035, FR-036, FR-044, FR-050…056, SC-011, SC-015 · Research: D5, `research/dynamics-processing-session.md`

## Context

Two ExoPlayers mean the mix happens in AudioFlinger. EQ, preamp and width must match the native engine exactly;
the limiter must see the sum of both players.

## Decision

Per player (Media3 `AudioProcessor` chain, built by a `DefaultRenderersFactory.buildAudioSink` override):

1. `GainProcessor` — normalization gain × fade curve, per sample, with the 100–300 ms ramps of FR-046a inside.
2. `StereoWidthProcessor` — our own float `AudioProcessor` (not Media3's `ChannelMixingAudioProcessor`, see
   "Width stage amendment") applying the native mid/side width
   `L' = ((1+w)/2)L + ((1−w)/2)R`, `R' = ((1−w)/2)L + ((1+w)/2)R`; a width change ramps to the new value over a
   short ramp without reconfiguring; inactive when w = 1 and no ramp is running.
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
  Control loss is also treated as a **possible mute** (hedge below).
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

**Not measured on the device (T003b; closed on evidence 2026-10-06, see "T003b closure"):** whether the limiter reduces a summed overlap that would clip, the SC-011
"no reduction below threshold" check, and whether disabling the limiter or the whole DP mutes or attenuates the
session (FR-053/055). The Visualizer could not measure them: on the session it is inserted first (reads the DP
input); on the output mix it read near-silence whenever the DP was disabled.

**Measurement split (owner decision 2026-10-05, after T003b stopped; superseded 2026-10-06 by "T003b closure"):**
- (c) mute/attenuation when the DP is disabled: owner listening check on `.qa` at normal volume with Dolby off
  (steps in `HANDOFF.md` "T003b").
- (a) limiting of a summed overlap that would clip, and (b) SC-011 (no reduction below threshold): recorded from the
  phone's output through a USB-C audio dongle into the dev laptop's input and analysed by the orchestrator
  (procedure in `HANDOFF.md` "T003b"); if that recording is not possible, a `[MANUAL]` check inside M5 (T048). M5
  may start; **T046 cannot close until (a)–(c) pass.** No max-volume headphone tests.

**Decision — effect-state hedge (owner-approved 2026-10-05; holds whatever (a)–(c) show).** The DP descriptor says
"volume mgmt: implements control" (AudioFlinger hands the stream volume to the effect), and in every DP-disabled
phase of T003/T003b no output power was logged although our track was active (three independent sources). So:
- Sistrum **never disables the DynamicsProcessing effect** (`enabled = false`) to turn the limiter off. The effect
  stays enabled for the session's lifetime; "clip prevention off" = the limiter stage made neutral (ratio 1,
  threshold 0 dBFS, post-gain 0 dB, input gain 0 dB). Disabling only the limiter stage (`inUse`/`enabled` false) is
  allowed only if (c) shows it is safe; (c) was not measured (T003b closure), so neutral parameters only. The effect is released only together with
  the session (engine release).
- **Losing control of the effect (FR-055) is treated as a possible mute.** On `onControlStatusChange(false)` (or an
  `enabled` change we did not make) Sistrum detects whether the session is still audible and, if it cannot confirm
  it, recovers: release its own effect instance and continue without the limiter (FR-053 note shown), re-creating it
  when control returns. The detection method and its device verification are part of T046/T048.

## T003b closure (owner decision 2026-10-06) — accepted on evidence

No device measurement of the limiter level was made. The 2026-10-05 dumpsys method could not measure it, the owner
has no USB-C audio dongle, and Dolby Atmos cannot be disabled on the CPH2307. A listening check of (c) with Dolby on
was started (two runs, both completed: `OK (1 test)`, 36 s) and then skipped by the owner. No result was recorded.
The Bluetooth A2DP recording route (laptop as a PipeWire sink) was available but not used. On the owner's
instruction, (a)–(c) are closed as **accepted on evidence**:

- (a) the limiter reduces a summed overlap that would clip, and (b) no reduction below the threshold (SC-011):
  - AOSP source review (`research/dynamics-processing-session.md`): a session chain processes the summed buffer of
    every track in the session once per mixer cycle, and the DP limiter is a feed-forward per-channel stage with
    link groups.
  - T003's routing check on the CPH2307: both players and the DP are on the shared session, on the normal mixer
    thread `AudioOut_1D`, and the limiter-only config is accepted.
  - T046's `LimiterConfigTest`: every limiter parameter is set explicitly (threshold ≈ −1 dBFS, other stages off,
    neutral gains), so the native fallback defaults (threshold −30 dB, ratio 2) can never apply.
- (c) whether disabling the limiter stage or the DP mutes the session: made moot by the effect-state hedge above.
  The effect is never disabled and the limiter stage is never switched off; "off" means neutral parameters. Losing
  control of the effect is handled as a possible mute (FR-055).

**Open follow-up (not a gate):** a device-level measurement of (a)–(c), for example the laptop as a Bluetooth A2DP
sink recorded with `pw-record` (judge level reduction, not exact peaks; the codecs are lossy), or a dongle
recording. It is tracked in `HANDOFF.md` and T048. T046 does not wait for it.

**Withdrawn claims** (from earlier S2 runs, both wrong): "DP on the mixer thread with both players" (it was an orphan
chain on an idle in-call thread) and "the limiter acts before volume" (the difference was two 10 Hz tones partly
cancelling).

## S3 result (2026-10-05, T004, CPH2307, `.qa`) — resources

Harness: `androidTest/.../spikes/ResourceSpikeTest.kt` + `SpikeLoadProcessors.kt`; 10 min per mode; 60 s cycles,
12 s fade, 5 s pre-buffer (worst case: a transition every minute); process CPU from `Process.getElapsedCpuTime()`,
PSS from `Debug.getMemoryInfo` (batterystats unusable while on USB power; SC-013 proper is measured in M8 vs native).

| Mode | Opus (12 tracks) CPU / PSS mean | FLAC+WAV+MP3 (9 tracks) CPU / PSS mean |
|---|---|---|
| single player | 20.6 % / 92 MB | 19.3 % / 107 MB |
| A/B cycle (B created at fade + 5 s, released after) | 28.0 % / 98 MB | 24.3 % / 107 MB |
| A/B + Gain/Width/EQ(10 biquads)/preamp per player | 47.6 % / 96 MB | 36.1 % / 107 MB |
| … + resample to the 48 kHz output rate first | 47.3 % / 97 MB | 39.9 % / 107 MB |

- Second-player lifetime behaves as designed: mean live players 1.28 = (12 + 5) / 60; 5 s pre-buffer always enough;
  memory cost ≤ +6 %. **Pre-buffer = 5 s** (ADR-003).
- **Decision: process at the track's own rate** (no fixed-output-rate resampling): +4 CPU points for 44.1 kHz content,
  no benefit to band-centre exactness; shapes match the native engine at 48 kHz content (research D5).
- **Risk → requirement:** the spike's naive per-sample processors cost +12…+20 CPU points. Production processors MUST
  process float arrays in bulk (no per-sample `ByteBuffer` get/put), MUST report inactive / bypass when neutral (flat
  EQ bands skipped as in the native engine, preamp 0 dB, width 1, gain 1 outside fades/ramps), and T045 measures the
  chain's CPU cost on device against a no-processor baseline.

## Width stage amendment (owner decision 2026-10-07)

Found while preparing T044: Media3 1.11.1's `ChannelMixingAudioProcessor` reads its matrix only in `onConfigure()`
(a matrix put later applies at the next configure, i.e. after a flush), takes 16-bit PCM per its documentation, and
does not ramp between matrices. A width change during playback would need a reconfigure/flush: an audible gap or
click, against FR-056 and US7 sc3. Decision (owner): `StereoWidthProcessor` is our own float processor with the same
mid/side formula, at the same place in the chain, ramping width changes (as the preamp ramps gain changes), bulk
float arrays, inactive when neutral. Order, linearity (FR-051) and the S3 CPU requirement are unchanged.

## Consequences

- Order vs the native engine (fade → EQ → preamp/width → focus duck, `ffmpeg_player.cpp:375-400`): the EQ is
  linear and identical on both channels, so it commutes with the width matrix and the scalar preamp; gain/fade stay
  before the EQ in both. Output equals the native chain up to float rounding (`DspLinearityTest`, T043).

- Per-player = on-the-mix exactly for linear stages (fade before EQ, ADR-003). Shapes identical when the track
  rate equals the native output rate; band-centre gains identical always (research D5 exactness note).
- Limiter feed-forward without lookahead: bounded overshoot on loud overlaps (SC-011); accepted on evidence, device measurement is an open follow-up (T003b closure).
- The DP effect stays enabled for the whole session; "off" is neutral parameters, never `enabled = false`.
- Direct-output routes (some hi-res) bypass session effects: EQ still applies (in-app), only the limiter is lost
  there (S2 checks the corpus).
- CPU: four small processors per player; two players only during fades (S3).

## Alternatives

- DP pre-EQ for the EQ (owner: no, response differs). `audiofx.Equalizer` (auto-attenuation). Processing at a
  fixed output rate (exact shapes for every rate; extra resampler CPU — decided after S3).
