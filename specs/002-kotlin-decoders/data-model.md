# Data model — 002

## Library track (Room, `SyncDatabase` v14 → v15 in M5)
| Field (new) | Type | Rule |
|---|---|---|
| `cueSourcePath` | String? | set only for cue tracks: the audio file the cue points at |
| `cueIndex` | Int? | 1-based cue track number |
| `cueStartMs` / `cueEndMs` | Long? | start inclusive, end exclusive; end null = to end of file |
Cue track id: `<file track id>#<cueIndex>`. The plain file row stays, hidden (FR-065a style) while its cue is valid;
history/play counts of existing rows are untouched. Migration adds nullable columns only.

## Analysis document (JSON per track, existing)
Existing loudness keys unchanged. New keys:
- Mood: `energy`, `danceability`, `brightness`, `tempo` (doubles), `moodRaw` (object of raw features), `moodVersion`.
- Silence: `silenceLeadMs`, `silenceTailMs` (longs), `silenceVersion` = `"silence-1"`.
- Library-level mood percentiles: one document keyed `__mood_percentiles__` with columns and `sampleCount`.
Missing keys = not analysed (Mood Radio ignores the track; crossfade uses 001 timing).

## Decoder provider (registry entry, code)
`id`, formats (`FormatKey`s), `extractors()`, optional `audioRenderers()`, `refusal(header)`; order after
`PlatformProvider`.

## Settings (DataStore, existing playback settings)
- `nightMode`: Boolean, default false.
- `crossfadeSkipSilence`: Boolean, default true.

## Decode-failure entry (001 FR-064a, unchanged shape)
time, file, format, codec, provider id, error text (refusal reason for refusals).
