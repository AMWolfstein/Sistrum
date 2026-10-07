# Contract — Loudness analysis documents and album loudness (ADR-005, measured-loudness amendment)

Added 2026-10-08 (owner decisions 2026-10-07, M6-1 and M6-3). All rows live in the existing `sync_documents` table
(`planId`, `kind`, `documentKey`, `rawJson`) of the active plan. No Room schema or version change.

## Kinds

| kind | documentKey | Written by | Read by |
|---|---|---|---|
| `analysis` | track id | `LoudnessStore` | **existing** read side (`activeAnalyses()`, `analysis()`, native `normalizationGain()`), `MeasuredGainSource` via `PlaybackItem.analysis` |
| `loudness_histogram` | track id | `LoudnessStore` | `AlbumLoudnessPlanner` only |
| `album_loudness` | album id | `AlbumLoudnessPlanner` | `MeasuredGainSource` (album snapshot) |
| `loudness_progress` | track id | worker checkpoint of a long track | worker only; deleted when the track finishes |

### `analysis` (the existing shape; extra keys are ignored by the read side)

```json
{ "loudness_lufs": -13.27, "true_peak": -0.41,
  "sample_peak": -0.93, "status": "ok",
  "source_fingerprint": "<identityHash(path|size|mtime) = the track's audio asset sha256>",
  "analyzer_version": "sistrum-loudness-1/waxflow-446ca31", "analyzed_at": "2026-10-08T01:02:03Z" }
```

- `loudness_lufs`: BS.1770-4 gated integrated loudness. `true_peak`: dBTP (the read side computes
  `min(gain, -true_peak)`).
- `status` `silent` (no block above −70 LUFS, or shorter than one 400 ms block) or `failed` (decode error, with
  `reason`): **no** `loudness_lufs` / `true_peak` keys, so the existing read side skips the row and never sees
  −Inf/NaN. Such tracks fall through to tags.
- Never written: `energy`, `danceability`, `brightness`, `tempo` (Mood stays out of 001, so Mood Radio eligibility
  is unchanged).

### `loudness_histogram`

```json
{ "bin_lu": 0.1, "min_lufs": -70.0, "bins": "<base64 of varint pairs (bin-index delta, count)>",
  "blocks": 2394, "source_fingerprint": "…", "analyzer_version": "…" }
```

Counts of 400 ms gating blocks (100 ms hop) above the absolute gate, binned by block loudness. Size is bounded by
the bin count, not the track length (typically 0.5–1.5 KB).

### `album_loudness`

```json
{ "loudness_lufs": -12.84, "true_peak": -0.12, "members": 12,
  "members_fingerprint": "<sha256 of sorted trackId:source_fingerprint:analyzer_version>",
  "analyzer_version": "…" }
```

## Album loudness

- **Members**: available (not hidden) tracks with the same `albumId` whose album name comes from a tag. MediaStore's
  folder fallback ("Music" for untagged files) is not an album; those tracks have no album record.
- **Value**: BS.1770 gating over the union of all members' blocks (absolute gate −70 LUFS, relative gate −10 LU of
  the pooled ungated mean), computed from the merged member histograms, as WaxFlow's `loudness.Group` and
  libebur128's multi-state loudness do. Never an average of track values. Album true peak = max of members.
- **Complete only**: written only when every member has a current histogram; otherwise no record (album mode uses
  the track's measured value).
- **Recompute**: the planner compares `members_fingerprint` after every analysis run and after every scan; a change
  (track added, removed, re-tagged into or out of the album, re-analyzed, analyzer version) recomputes from the
  stored histograms without decoding, in milliseconds per album.

## Rescans

`writeLocalLibrary` creates a new plan per scan and deletes the old plan's documents. Before that delete it copies
`analysis`, `loudness_histogram` and `loudness_progress` rows whose `source_fingerprint` equals the track's new audio
fingerprint into the new plan; changed or removed files lose theirs and become pending. `album_loudness` rows are
copied too and re-validated by the planner right after the scan.

## Versions

`analyzer_version` changes when the meter, histogram or decode path changes in a way that moves results. A mismatch
makes the track pending again; old values stay in use until the new ones are written.
