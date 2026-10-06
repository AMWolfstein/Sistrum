# ADR-006 — MediaSession, service and focus

Status: Accepted (owner, 2026-10-05) · Date: 2026-10-05 · Spec: US4, FR-020…024, FR-080…092 · Research: D6, D7, D10

## Context

`PlaybackService` owns a framework `MediaSession`, the notification, focus and the queue for both engines. Two
ExoPlayers must look like one player to the system (FR-024). The review (Part 2) lists service-path faults the
new code must not repeat.

## Decision

- Keep the framework `MediaSession` in `PlaybackService`, fed from service state (one logical player; switches
  to the incoming track at fade start). No Media3 `MediaSessionService` / `ForwardingPlayer` in 001. The
  output-switcher token type is unchanged. `publishNowPlaying` also sets ALBUM and
  ALBUM_ARTIST (artwork stays on `decodeArtworkBitmaps`).
- Amended 2026-10-06 (owner-approved; root cause in HANDOFF "T033 root cause"): "Unknown artist" is not a
  session fault. The session already shows the in-app artist. MediaStore reports no artist, album artist or album
  for some containers (e.g. WAV), and the scanner took those fields only from MediaStore. It is fixed in the scan
  (T033a): when MediaStore has no value for a field, the file's own tags from `EmbeddedTagReader` fill it in.
  Non-empty MediaStore values are never overridden. `CurrentMetadataSchemaVersion` is bumped, and there is no new
  tag library.
- Focus stays manual (`handleAudioFocus = false` on both players), semantics of discovery Q5, plus FR-085.
- Command path: one `Channel<Command>` consumed by one coroutine (FR-081, FR-091); queue requests handed over
  in process (FR-083); every command and tick wrapped (FR-084); foreground start on every start path and
  `stopSelf` when idle (FR-080); ordered shutdown, no `runBlocking` (FR-082); session publishing without whole-
  library loads, coalesced seeks, ordered saves (FR-087); prepare failures release what they created (FR-086).
- These live in the shared service, so they also change the current engine's service path in failure cases
  (owner: accepted, 2026-10-05); the native engine and `FfmpegDecoder` are untouched (FR-006). US1 parity tests
  must still pass, and characterization tests must not assert the old faulty behaviour (FR-073).

## Consequences

- No token or notification migration in 001 (R9 avoided). A Media3 session is its own feature after 001 and
  002 (owner, 2026-10-05); it does not wait for 003.

## Alternatives

- Media3 `MediaSession` + `ForwardingPlayer` switching A/B (RNTP-APM model; Rhythm swaps the session player via
  listeners). Needs a `Player` adapter for the native engine and a token change; deferred to its own feature.
