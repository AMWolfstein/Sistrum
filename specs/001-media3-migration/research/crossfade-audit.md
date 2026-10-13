# Crossfade behaviour audit (before M7, 2026-10-13)

Sources: native engine `androidApp/src/main/cpp/ffmpeg_player.cpp`, `player/PlaybackCoordinator.kt`,
`player/PlaybackModels.kt`, `player/PlaybackPreferences.kt`, M2 characterization tests
(`PlaybackCoordinatorCrossfadeCharacterizationTest`, T015), Airmedy `catalog/player/README.md`
(§ Native playback, gapless transitions, and crossfade), spec US5 / FR-024, FR-030–036, FR-050–051, ADR-003.

| # | Native behaviour | Where it lives today | Covered by |
|---|---|---|---|
| 1 | Equal-power curve: out `cos`, in `sin`, `phase = at/total·π/2`, per frame | cpp mix loop | T056, T058; T059 tee check; T061 by ear |
| 2 | Start only on automatic advance, with a preloaded next, crossfade > 0, track ≥ 2 s, remaining in (0.4 s, min(N, dur/2)] | `shouldStartCrossfade` (shared) | T015 characterization (stays green, T060) |
| 3 | Duration = min(N s, dur/2, remaining) | `crossfadeDurationMs` (shared) | T015 |
| 4 | 0 disables; clamp 0..12; UI default 4; last enabled length remembered | `PlaybackPreferences` (engine-independent, unchanged) | `PlaybackModelsTest`; no M7 change |
| 5 | Queue + state + session metadata switch at fade **start** | coordinator + `kTransitionCrossfadeStarted` | T015; T059 (one `TransitionStarted`); T060 (FR-024) |
| 6 | Manual next/select/previous: no fade, immediate | coordinator | T015; T055 |
| 7 | Pause, seek, stop, queue edit snap the fade (incoming continues at full level) | `snap_fade` in pause/stop/seek; coordinator | T015; T055 (any snap → Cleanup); T056 (no old-gain frame after flush); T059 |
| 8 | Next during a fade → hard cut to i+2; previous → 3 s rule on incoming | new in 001 (FR-031) | T055; T060 |
| 9 | Repeat-one fades/joins into itself | FR-031 | T055 |
| 10 | Next-but-one prepared only after the fade (both slots busy) | `canPreloadNext` | T015; T055 |
| 11 | Setting change never alters a running fade | coordinator | T015; T055 |
| 12 | Artwork transition (id, paths, effective duration; cleared on end/snap; blend off clears) | coordinator | T015; T060 |
| 13 | Listening overlap split at half the elapsed fade, clamped | coordinator + ListeningTracker | T015; T060 |
| 14 | Transition whose incoming doesn't match the queue → Failed | coordinator | T015 |
| 15 | Normalization gain per source, multiplied with the fade | cpp per-slot gain | T056 (FR-035 multiply) |
| 16 | Fade lead compensation 0.46–0.69 s (S1) | new (ADR-003) | T056, T058, T059 (audible starts within 50 ms) |
| 17 | Summed overlap limited (SC-015 / SC-011 in a crossfade) | new (shared-session limiter) | T061 (owner + tones) |
| 18 | Mood Radio refill during a fade | coordinator | T060 |
| 19 | No second player when crossfade is off; B only shortly before a fade, released after | FR-033 | T055; T059 |
| 20 | Shared session id + identical attributes | FR-036 | T059 |

## Not covered by any M7 task line (proposed additions)

| Gap | Native / spec basis | Proposal |
|---|---|---|
| G1 Focus duck and user volume during a fade | Native ducks the mixed output (`next_focus_gain`, 120/240 ms); FR-035 multiplies per player | T059 test: `setFocusGain` during a fade reaches both players; B starts at the current duck level |
| G2 B gets the same chain and settings | FR-050 / US7 sc2 (both players equalized the same), FR-035 (normalization per player) | T059 test: B is built with the gain hook, current `NormalizationSettings`, EQ/preamp/width; tee check that the sum equals processing the mix |
| G3 Errors during a fade | Spec silent; native has one decode path per slot | B fails before the fade → no fade, stay on A (advance then follows the normal decode-failure path, FR-064); an error on either player during the fade → snap, then the normal error path with the right attribution (T041a). T055 + T059 tests; one edge-case line in spec.md |
| G4 Position/duration during and after the swap | Native position comes from rendered frames of the active (incoming) slot | T059 test: from fade start `positionMs`/`durationMs` report the incoming player |
| G5 Spec edge case "seek right after the fade starts, or two automatic transitions within one position tick" | spec Edge Cases | T055 tests for both |

Engine switch during a fade (spec edge case) is already covered: the engine choice applies only at the next playback
start (M3).
