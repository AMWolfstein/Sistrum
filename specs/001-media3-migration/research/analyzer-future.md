# Future: on-device analyzer (true LUFS + Mood Radio revival)

Record only (owner, 2026-10-05). A separate later feature, after 001–003. Revives true LUFS normalization and
Mood Radio together. **All Kotlin/Java: no bridge, no native code** (constitution Principle 11).

## What it computes

Airmedy's method, unchanged in meaning:
- 12 raw features per track; definitions must match Airmedy's `ffmpeg_analyzer.h` so the weights keep their
  meaning.
- Locked weights from `formulas.go`; `normalizer.go` maps features through a sigmoid on corpus percentiles.
- Batched recompute when the corpus percentiles change.
- Output: `loudness_lufs`, `true_peak`, `energy`, `danceability`, `brightness`, `tempo` into `sync_documents`
  in the **existing** analysis-document shape (the read side is not touched: constitution "Do not touch").

## How

- **Decode** through the Decoder Registry (platform codecs + the 002 Kotlin decoders), separate from
  playback (its own decoder instances, never the players).
- **Loudness**: Kotlin port of WaxFlow's `dsp/loudness` (~700 lines; BS.1770-4 integrated loudness, true peak,
  EBU Tech 3342 loudness range). WaxFlow's numbers are the oracle (`scripts/waxflow-oracle.sh` already writes
  integrated LUFS, LRA, true peak and sample peak per corpus file). Cross-check against mp3care PR#16
  (github.com/andresdelcampo/mp3care/pull/16, Kotlin R128 verified vs ffmpeg) and the EBU test vectors.
- **FFT**: TarsosDSP core 2.5 (GPL-3.0; never the `jvm` module; vendored at a pinned version, see
  `tarsosdsp-evaluation.md`) or a Kotlin port of WaxFlow's `dsp/fft`. Spectral descriptors (centroid, rolloff,
  flatness, flux) on top, with the definitions versioned.
- **Tempo and onsets**: TarsosDSP `ComplexOnsetDetector` + BeatRoot; BPM from the median beat interval.
- **Scheduling**: WorkManager, bounded per-track work (10-minute worker limit), checkpointed, only new or
  changed files (MediaStore id + mtime/size fingerprint). Store raw features + the analysis config version.
- **Gain source**: plugs into 001's pluggable gain source; precedence tags → analysis → unity + untagged pre-amp.

## Rollout

- User setting with progress.
- Device benchmark before rollout (decode and DSP real-time factors separately, memory, cancel/resume).
- The owner's Opus-heavy library is the first real test against WaxFlow's numbers; then other format mixes
  (Principle 10).
