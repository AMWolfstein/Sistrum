# Future: on-device Mood analyzer (Mood Radio revival)

Record only (owner, 2026-10-05; revised 2026-10-08). **Loudness moved into 001** (owner decisions 2026-10-07, M6-1):
001 ships the BS.1770-4 meter (Kotlin port of WaxFlow `dsp/loudness`), the WorkManager job, the decode path through
the Decoder Registry and the `sync_documents` writer for `loudness_lufs` / `true_peak`, plus album loudness
(ADR-005 amendment, `contracts/loudness-analysis.md`). What stays for a separate later feature, after 001–003, is
**Mood**: the features Mood Radio needs. **All Kotlin/Java: no bridge, no native code** (constitution Principle 11).

## What it computes

Airmedy's method, unchanged in meaning:
- 12 raw features per track; definitions must match Airmedy's `ffmpeg_analyzer.h` so the weights keep their
  meaning.
- Locked weights from `formulas.go`; `normalizer.go` maps features through a sigmoid on corpus percentiles.
- Batched recompute when the corpus percentiles change.
- Output: `energy`, `danceability`, `brightness`, `tempo` added to the **existing** `analysis` documents that 001
  already writes (the read side is not touched: constitution "Do not touch"); `library_analysis_enabled` turns on
  only then.

## How (reusing 001)

- **Decode, job, storage**: 001's offline decoder, `LoudnessAnalysisWorker` scheduling (fingerprints, budget,
  checkpoints) and document writer; the Mood pass becomes a second consumer of the same decoded float PCM, with its
  own version key so it can run on tracks that already have loudness.
- **FFT**: TarsosDSP core 2.5 (GPL-3.0; never the `jvm` module; vendored at a pinned version, see
  `tarsosdsp-evaluation.md`) or a Kotlin port of WaxFlow's `dsp/fft`. Spectral descriptors (centroid, rolloff,
  flatness, flux) on top, with the definitions versioned.
- **Tempo and onsets**: TarsosDSP `ComplexOnsetDetector` + BeatRoot; BPM from the median beat interval.
- **Loudness range** (EBU Tech 3342), if a Mood feature needs it: WaxFlow's meter already computes it; 001's port
  leaves it out.

## Rollout

- User setting with progress (001's loudness progress line extends to Mood).
- Device benchmark before rollout (feature DSP real-time factor on top of 001's measured decode speed, memory,
  cancel/resume).
- The owner's Opus-heavy library is the first real test; then other format mixes (Principle 10).
