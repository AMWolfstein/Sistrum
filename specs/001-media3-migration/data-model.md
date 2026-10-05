# Data model — 001-media3-migration

No Room schema or version change (FR-074). New persistent state lives in DataStore preferences.

## Persistent (DataStore)

| Entity | Fields | Rules |
|---|---|---|
| EngineSelection | `engine: Native \| Media3` | Default Native on the branch, Media3 before merge (FR-002/003). Read at playback start and at scan. Change → rescan (FR-065). Hidden setting. |
| NormalizationSettings (existing `NormalizationPreferences`) | enabled, targetLufs, mode (Track/Album), preventClip; **+ untaggedPreampDb** (default 0) | Enabled in UI only with Media3 selected (FR-045). targetLufs → global pre-amp = target + 18 dB. |
| EqualizerSettings (existing `EqualizerPreferences`) | 10 band gains (dB), preamp (dB), width | Unchanged storage and UI (FR-050). |
| SkippedFilesSummary | scanAt, entries: list of (format, codec, reason, count) | Written at the end of each Media3-engine scan; empty with the native engine. Shown after scan and in settings (FR-066). |
| Session snapshot (existing `PlaybackSessionStore`) | queue, index, position | Unchanged; saves ordered (FR-087). |

## In-memory

| Entity | Fields | Notes |
|---|---|---|
| FormatKey | format, codec | From scanner labels (`audioFormatOf`, `realCodec`). |
| DecoderProvider | id, availability, extractors, renderers, refusal | Contract: `contracts/decoder-registry.md`. |
| GainInfo | track/album gain (dB vs −18), track/album peak, form | Contract: `contracts/gain-source.md`. |
| ItemGain | gain dB | Native engine only: the service's analysis-based gain, passed to the native dB parameter. |
| GainProcessor state | target dB, current dB (ramping), fade curve | Media3, per player; gain resolved by the engine from `GainSource` when the track format is known; ramps 100–300 ms from the current value (FR-046a). |
| TransitionState | Idle, Scheduled, Preparing, Transitioning(startedAt, fadeMs), Cleanup | Media3 engine only (ADR-003). Snap from any state → Cleanup. |
| EngineEvent | TransitionStarted, GaplessAdvanced, Ended, OutputDisconnected, Error(provider, format, cause) | Contract: `contracts/player-engine.md`. |
| LimiterState | available, controlled (by us / another app) | Drives the clip-prevention note (FR-053/055). |

## Unchanged and untouched

`PlaybackQueue`, `ListeningTracker` (sharedLogic, zero changes), `PlaybackState`, `PlaybackItem`,
`ArtworkCrossfadeTransition`, the analysis read side (`sync_documents` kind `analysis`).
