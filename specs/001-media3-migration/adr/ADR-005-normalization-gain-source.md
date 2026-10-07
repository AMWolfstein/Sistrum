# ADR-005 — Normalization: measured loudness, tags as fallback, and the gain source

Status: Accepted (owner, 2026-10-05); **S5 PASS 2026-10-05 (T005)**: platform decoder applies the Opus header gain once; **amended 2026-10-08: measured loudness first** (owner decisions 2026-10-07, M6-1…M6-5; see the amendment at the end, which overrides the sections above where they differ) · Date: 2026-10-05 · Spec: US6, FR-040…047h, SC-004, SC-017, SC-018 ·
Research: D9

## Context

Normalization is broken for everyone (no analysis data). Stage 1 reads gain tags at playback time on the new
engine. Later, on-device analysis becomes a second source.

## Decision

- `GainSource` interface (contract: `contracts/gain-source.md`) → `GainInfo` (track/album gain and peak, tag
  form, found). 001 implements `TagGainSource` only. Resolution order fixed in one place for later:
  tags → analysis → unity + untagged pre-amp.
- `TagGainSource` = port of Rhythm's `ReplayGainUtil` (pin `ef16e7b`, GPL-3.0-or-later): TXXX (MP3), Vorbis
  comments (FLAC/Ogg), `R128_*` (Opus, Q7.8 / 256), iTunNORM and `com.apple.iTunes:replaygain_*` (M4A), plus the
  forms Rhythm also reads (RVA2/RVAD/RGAD). Keys case-insensitive. Precedence ReplayGain → R128 → Sound Check.
- Gain math (pure Kotlin, unit-tested): tag gain vs −18 LUFS, adjusted by `REPLAYGAIN_REFERENCE_LOUDNESS` when
  present (tag gain + (−18 − reference)); R128 + 5 dB; global pre-amp =
  target − (−18); untagged = 0 dB + untagged pre-amp; album mode = album gain if present else track gain;
  clip prevention = cap so tagged peak (album peak in album mode) × gain ≤ 1.0; non-finite → untagged.
- Applied in each player's `GainProcessor` (ADR-004) with ramps from the current actual gain (FR-046a).
  Rhythm's knee compressor is not ported; the session limiter does that job.
- Opus header output gain: verified on device (S5) that the platform decoder applies it exactly once; if it
  doesn't, `GainProcessor` adds it (FR-041).
- **S5 result (2026-10-05, T005, CPH2307, `.qa`)**: fixtures identical except the OpusHead output-gain field
  (`scripts/spikes/opus-header-gain.py`); decoded by ExoPlayer through `c2.android.opus.decoder`: 0 dB header →
  −26.00 dBFS RMS, +6 dB → −20.00 (+6.00), −6 dB → −32.00 (−6.00); identical to ffmpeg's reference decode.
  **Decision: the platform decoder applies the header gain exactly once; `GainProcessor` never adds it** and applies
  only the converted tag gain (R128 + 5 dB). Principle 10: other devices may use another Opus decoder; T052 logs the
  active decoder name and re-runs this check (`OpusHeaderGainSpikeTest`) whenever the decoder is not `c2.android.opus*`.
- Settings: enabled only with the new engine selected (FR-045); `NormalizationPreferences` + untagged pre-amp.

## Reference loudness (owner decision, 2026-10-05)

`REPLAYGAIN_REFERENCE_LOUDNESS` is honoured when present, as Rhythm does (`ReplayGainUtil.kt:291-299`):
adjusted gain = tag gain + (−18 − reference). Without the tag, gains are taken against −18, and R128 against −23
with +5 dB. Constitution and spec FR-041 updated.

## Consequences

- The analysis feature later plugs in as a second `GainSource` with no processor change.
- `VolumeNormalization.kt` (sharedLogic) stays for the native engine's analysis-based path (unchanged).

## Amendment 2026-10-08 — measured loudness first (owner decisions 2026-10-07)

Context: tags depend on the listener's tagging tool and are missing for most libraries; the original Airmedy
measured loudness on the desktop and the app only read it. The owner chose to measure on the device in 001 instead
of shipping tags as the only source.

Decision:
- **Analyzer in 001 (loudness only).** A Kotlin port of WaxFlow `dsp/loudness` at the pinned commit `446ca31`
  (BS.1770-4 gated integrated loudness, true peak per Annex 2; no loudness range), attribution header per
  `docs/waxflow/ORACLE.md`. Validated against generated EBU Tech 3341 cases, WaxFlow's own synthetic cases and the
  WaxFlow oracle fixtures (`scripts/waxflow-oracle.sh`; only the fixtures file is committed). Mood features stay out
  of 001 (`research/analyzer-future.md`).
- **Decode**: through the Decoder Registry's provider for the file (same extractors/renderers as playback, own
  instances, no AudioTrack, no focus), to float via `PcmToFloatProcessor` (the float path of the ADR-004 amendment).
  ExoPlayer with a capture sink unless T051d measures it slower than a direct extractor + MediaCodec loop over the
  same provider.
- **Job**: WorkManager (`androidx.work`, Apache-2.0), unique work, constraints charging + battery not low + storage not
  low; 8-minute budget per run (under the 10-minute worker limit), re-enqueued while tracks are pending; every
  finished track is written immediately; a long track checkpoints its meter state; only new or changed files
  (audio fingerprint `identityHash(path|size|mtime)`) or an `analyzer_version` change. Background thread priority;
  the meter is allocation-free per chunk so it cannot cause GC pauses in playback.
- **Storage**: `sync_documents` rows (`contracts/loudness-analysis.md`): kind `analysis` in the existing shape
  (`loudness_lufs`, `true_peak` dBTP), read unchanged by `activeAnalyses()`; plus `loudness_histogram`,
  `album_loudness`, `loudness_progress`. Rows are carried over to the new plan on rescans when the fingerprint is
  unchanged. No Room schema change.
- **Precedence (M6-2)**: `GainResolver`: measured → tags (Rhythm port, unchanged, incl. `REPLAYGAIN_REFERENCE_LOUDNESS`)
  → unity + untagged pre-amp. Per track as a whole; a measured track's gain is target − L (−18 − L, plus the
  global pre-amp).
- **Album (M6-3)**: BS.1770 gating over all the album's blocks (WaxFlow `loudness.Group` semantics), from stored
  per-track histograms, not an average; only when every member is analyzed; recomputed from histograms when the
  members fingerprint changes. Unlike the native engine (average of track values, only when the next track is on the
  same album), it applies whatever the queue order (FR-043).
- **Kept (M6-4)**: `GainProcessor` and its ramps, true-peak clip prevention (tagged peak for tag sources),
  `NormalizationPreferences` as the UI contract, tag parsing as the fallback. "Prevent clipping" now reaches the
  engine: the coordinator calls `setNormalization`; off = no peak cap + limiter stage neutral (FR-053).
- **Native engine**: unchanged code. Once `analysis` rows exist, its existing read side finds them, so the native
  engine (developer switch only) also normalizes again with its own old rules. The coordinator's native gain lookup,
  which force-disables normalization when no analysis exists, no longer runs for Media3.

Consequences:
- The first WaxFlow port lands in 001 (the loudness meter), so WaxFlow's MIT notice is added in 001; 002's decoder
  ports follow the same header and pin.
- New dependency: WorkManager. New package `analysis/` outside `player/`.
- SC-004 is measured after analysis; tags remain covered by their own tests for not-yet-analyzed tracks.
- The analyzer feature after 001 shrinks to Mood features (12 raw features, Mood Radio revival); it reuses this
  job, decode path and document writer.
