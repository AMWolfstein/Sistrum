# ADR-003 — Dual-player crossfade on Media3

Status: Accepted (owner, 2026-10-05); **S1 PASS 2026-10-05 (T002): processor fade kept** · Date: 2026-10-05 ·
Spec: US5, FR-030…036, FR-046, SC-007, SC-013 · Research: D2, D3, D4

## Context

Constitution Principle 5: port Rhythm's A/B mechanics, keep our equal-power curve. The current contract
(discovery Q4): Kotlin decides when (ticker, `shouldStartCrossfade`) and how long; queue/state/stats/Last.fm
switch at fade **start**; snap on pause/seek/stop/queue edit; i+2 prepared only after the fade; overlap split in
statistics; artwork event.

## Decision

- **Players**: player A is current. Gapless (crossfade off or too short): the next item is appended to A's
  two-item playlist. Crossfade on: player B is created and prepared with the next item when remaining time ≤
  fade length + pre-buffer (**5 s**, confirmed in S3: always ready, memory ≤ +6 %, ADR-004 "S3 result"), and released right after the fade (FR-033). With crossfade
  off, B never exists.
- **Shared session**: B is built with A's audio session id and identical `AudioAttributes`; offload disabled on
  both; the session id survives B's recreation (ADR-004).
- **Start**: `Media3Engine.beginCrossfade(ms)` starts B, swaps roles (B becomes current for the engine and the
  service), emits `TransitionStarted`. The service does what it does today at fade start.
- **Fade (owner decision; S1 verifies by listening)**: each player's gain processor applies the equal-power curve per sample, keyed to frame
  positions (outgoing `cos(t·π/2)`, incoming `sin(t·π/2)`), before the EQ (exact linearity, ADR-004).
- **Snap** (pause, seek, stop, queue edit, next/previous during a fade): outgoing player stopped at once; the
  incoming player's curve is forced to 1 and its pending audio flushed at the current position, so nothing
  already faded plays after the snap.
- **Next during a fade**: hard cut to the track after the incoming one; **previous**: the 3 s rule on the
  incoming track (spec Clarifications). Repeat-one: B prepares the same file (fade or gapless into itself).
- **State machine** (after Rhythm's `TransitionController`): Idle → Scheduled (B due) → Preparing (B
  buffering) → Transitioning → Cleanup → Idle. Any snap trigger moves to Cleanup. If B is not ready at the fade
  start, the transition falls back to gapless on A (no silence).
- **Not ported from Rhythm**: per-player sessions, ExoPlayer playlists for the whole queue, the shaped curves,
  the 16 ms `delay` loop (unless S1 picks volume stepping).
- **Future settings stay possible** (FR-031): crossfade on manual skip = a snap trigger that starts a fade
  instead; repeat-one toggle = a flag on the Scheduled step; fractional seconds = ms already.

## S1 result (2026-10-05, T002, CPH2307, `.qa`)

Harness: `androidTest/.../spikes/` (two ExoPlayers, one shared session, per-player fade processor + tee).
- Curve: per-frame gains equal the native formula (`phase = at/total·π/2`, float), 16-bit within ±1 LSB — automated PASS.
- Snap: after pause/seek/next with the incoming flush (`seekTo(currentPosition)`), no tee buffer after the flush is
  faded (peak within 0.5 dB of unfaded) — automated PASS.
- Listening (owner): 12 s fade Lose Yourself → Lighters (FLAC), processor vs `player.volume` stepping: **both smooth,
  no audible difference**. Snap runs (pause/seek at 30 %, next at 70 %) were played but **not checked by ear**
  (owner unavailable; deferred owner check).
- Timing: incoming start offset (play → first processed buffer) 17–49 ms. **Outgoing processing lead** (frames processed
  ahead of the audible position when the fade starts) **459–686 ms** on FLAC (83 ms on a fresh 48 kHz WAV): the outgoing
  curve is applied to audio that is heard up to ~0.7 s later, so the outgoing fade audibly starts that much after the
  incoming one. Not audible on a 12 s fade, but it exceeds SC-007's 200 ms and matters for short fades.
- **Decision**: keep the per-sample processor fade (owner decision stands; no contradiction). **Requirement for the
  implementation (T056/T058/T059)**: key both curves to the *audible* timeline, i.e. start the outgoing curve at the
  frame that will be audible at fade start (current processed frame − lead, using the sink's position/latency) or
  delay the incoming start by the same lead, so the two curves start within 50 ms of each other; measured by the
  Media3 crossfade instrumented test.

## Consequences

- Memory: two pipelines only during transitions; S3 measures it against SC-013.
- Processor fades run ahead of output by the sink buffer (500 ms default); snaps need the flush above.
- Sample-accurate curves, no timer cadence dependence.

## Alternatives

- Timer-stepped `player.volume` (Rhythm): not chosen (owner, 2026-10-05): the fade would sit after the EQ and
  break exact linearity during fades. S1 still records it as the comparison point; if the processor fade fails
  S1, stop and update this ADR.
- One ExoPlayer + `DefaultAudioMixer` (mixer engine): not needed (limiter on the sum is solved by the shared
  session); future option, `research/crossfade-single-player.md`.
- Two players → one `AudioSink`, `CompositionPlayer`: rejected (research D5).
