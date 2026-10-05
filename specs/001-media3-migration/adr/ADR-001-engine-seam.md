# ADR-001 — Engine seam: `PlayerEngine`

Status: Accepted (owner, 2026-10-05) · Date: 2026-10-05 · Spec: FR-001…006, FR-012, FR-089 · Research: D1, D2

## Context

`PlaybackService` drives `FfmpegDecoder` (JNI) directly and polls it every 200 ms (discovery §1, Q2). The
migration needs both engines behind one interface, selectable by a hidden switch, with zero behaviour change on
the native engine and `PlaybackQueue` / `ListeningTracker` untouched.

## Decision

- Interface `PlayerEngine` in `player/engine/` (contract: `contracts/player-engine.md`). Operations: prepare
  (item, gain, start position, paused), preload next (item, gain) / clear, play, pause, seek, position and
  duration, begin crossfade (ms) / snap, focus gain, DSP settings, normalization gains, release. Output: one
  `Flow<EngineEvent>` (TransitionStarted, GaplessAdvanced, Ended, OutputDisconnected, Error(provider, cause)).
- `LegacyNativeEngine` wraps `FfmpegDecoder` 1:1 and converts its polls (`consumeTransition`, `isFinished`,
  `isOutputDisconnected`) into events on the service ticker. Dead native APIs (`finishCrossfade`, `stop`,
  `preloadedDurationMs/PositionMs`) are not exposed. No native or `FfmpegDecoder` change.
- `Media3Engine` implements the same contract (ADR-003, ADR-004).
- Everything else stays above the seam in the service: queue, statistics, Last.fm, Mood Radio refill,
  normalization gain *lookup*, MediaSession, focus, the 200 ms ticker (position for lyrics/stats).
- Engine selection: a DataStore preference read at each playback start (FR-002); default native on the branch.
  Hidden developer setting, unlocked by a gesture (decided in the task; e.g. 7 taps on the version row).

## Amendment — host testability (approved by the owner 2026-10-05: pure move, no logic change; T017's parity pass confirms it)

`PlaybackService` cannot run in host tests (no Robolectric; framework `MediaSession`, notifications and
`Service` stubs return null). To characterize service behaviour on a fake `PlayerEngine` (plan phase 3), the
orchestration moves, mechanically and without logic change, into a plain-Kotlin `PlaybackCoordinator`
(`player/PlaybackCoordinator.kt`) behind small ports (`player/PlaybackPorts.kt`: now-playing/session,
focus, listening sink, scrobble sink, session store, clock, item resolver). `PlaybackService` keeps the Android
glue: intents, MediaSession callbacks, notification, focus listener, noisy receiver, lifecycle. The command
channel of ADR-006 (FR-081) then lives in the coordinator. Tasks T012/T013 do the move; T014–T016 add the
fake-engine characterization tests.

## Consequences

- Characterization tests pin today's service behaviour with a fake `PlayerEngine` once the seam exists;
  pure-policy characterization tests come first (tests-first, CLAUDE.md).
- "Two slots" stays an engine-internal concept; the service only knows "current", "preloaded next",
  "crossfading".
- `PlaybackService` loses its direct `FfmpegDecoder` dependency; Graphify should show `FfmpegDecoder` with
  `LegacyNativeEngine` as its only dependant.

## Alternatives

- Name `PlaybackEngine` (clashes with the native struct, discovery R6). Polling in the seam (rejected: Media3 is
  event-driven, R7). Moving orchestration into each engine (rejected: duplicates stats/Last.fm/queue logic).
