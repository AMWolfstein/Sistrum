# Data model — 001-media3-migration

No Room schema or version change (FR-074). New persistent state lives in DataStore preferences, and (M6, revised
2026-10-08) loudness analysis rows in the existing `sync_documents` table.

## Persistent (DataStore)

| Entity | Fields | Rules |
|---|---|---|
| EngineSelection | `engine: Native \| Media3` | Default Native on the branch, Media3 before merge (FR-002/003). Read at playback start and at scan. Change → rescan (FR-065). Hidden setting. |
| NormalizationSettings (existing `NormalizationPreferences`) | enabled, targetLufs, mode (Track/Album), preventClip; **+ untaggedPreampDb** (default 0) | Enabled in UI only with Media3 selected (FR-045). targetLufs → global pre-amp = target + 18 dB (a measured track lands on target − L). preventClip off → no peak cap and the limiter stage neutral (FR-053). |
| EqualizerSettings (existing `EqualizerPreferences`) | 10 band gains (dB), preamp (dB), width | Unchanged storage and UI (FR-050). |
| Track visibility (FR-065a) | `sync_tracks.unavailableReason: String?` (Room column, v14) | null = available. Set by a Media3-engine scan for a file with no decoder on this device (the skip reason). Hidden rows keep their data and play count; every library read, playback resolve and search excludes them; the prior-scan state and play-count merge include them. Cleared by any scan that admits the file. |
| SkippedFilesSummary | scanAt, entries: list of (format, codec, reason, count) | Written at the end of each Media3-engine scan; empty with the native engine. Shown after scan and in settings (FR-066). |
| Session snapshot (existing `PlaybackSessionStore`) | queue, index, position | Unchanged; saves ordered (FR-087). |

## Persistent (`sync_documents` rows, no schema change) — M6 measured loudness

Shapes and rules: `contracts/loudness-analysis.md`.

| Entity | Row | Rules |
|---|---|---|
| TrackLoudness | kind `analysis`, key track id: `loudness_lufs`, `true_peak` (dBTP), `sample_peak`, `status`, `source_fingerprint`, `analyzer_version`, `analyzed_at` | The existing analysis shape, read unchanged by `activeAnalyses()`. Silent/failed rows omit `loudness_lufs`/`true_peak`. No Mood features. Copied to the new plan on rescan when the fingerprint is unchanged. |
| LoudnessHistogram | kind `loudness_histogram`, key track id: 0.1 LU bins of gated 400 ms blocks, sparse, base64 | Input for album loudness without re-decoding. ~0.5–1.5 KB per track, bounded by bin count. |
| AlbumLoudness | kind `album_loudness`, key album id: `loudness_lufs`, `true_peak`, `members`, `members_fingerprint` | Gated over the whole album from merged histograms; only when every member is analyzed; members = available tracks of the album whose album name comes from a tag; recomputed when the members fingerprint changes (after each analysis run and each scan). |
| AnalysisCheckpoint | kind `loudness_progress`, key track id: meter state + decode position | Only while a long track is part-way; deleted when the track finishes. |
| Analysis job | WorkManager unique work `loudness-analysis` | Constraints: charging, battery not low, storage not low; 8-minute budget per run, re-enqueued while tracks are pending; enqueued after every scan. |

## In-memory

| Entity | Fields | Notes |
|---|---|---|
| FormatKey | format, codec | From scanner labels (`audioFormatOf`, `realCodec`). |
| DecoderProvider | id, availability, extractors, renderers, refusal | Contract: `contracts/decoder-registry.md`. |
| GainInfo | track/album gain (dB vs −18), track/album peak, form (Measured, ReplayGain, R128, SoundCheck, None) | Contract: `contracts/gain-source.md`. Resolved by `GainResolver`: measured → tags → none. |
| ItemGain | gain dB | Native engine only: the service's analysis-based gain, passed to the native dB parameter. |
| GainProcessor state | target dB, current dB (ramping), fade curve | Media3, per player; gain resolved by the engine from `GainSource` when the track format is known; ramps 100–300 ms from the current value (FR-046a). |
| TransitionState | Idle, Scheduled, Preparing, Transitioning(startedAt, fadeMs), Cleanup | Media3 engine only (ADR-003). Snap from any state → Cleanup. |
| EngineEvent | TransitionStarted, GaplessAdvanced, Ended, OutputDisconnected, Error(provider, format, cause) | Contract: `contracts/player-engine.md`. |
| LimiterState | available, controlled (by us / another app) | Drives the clip-prevention note (FR-053/055). |

## Unchanged and untouched

`PlaybackQueue`, `ListeningTracker` (sharedLogic, zero changes), `PlaybackState`, `PlaybackItem`,
`ArtworkCrossfadeTransition`, the analysis read side (`sync_documents` kind `analysis` shape, `activeAnalyses()`,
`analysis()`, `normalizationGain*`, Mood Radio queries). M6 adds a writer for that shape; the readers stay as they are.
