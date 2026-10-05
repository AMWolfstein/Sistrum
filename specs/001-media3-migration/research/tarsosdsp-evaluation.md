# TarsosDSP evaluation for on-device audio feature analysis

Source: external research (Arena.ai), 2026-10-01. Unverified claims are the
researcher's; verify before relying on them.

## Recommendation
Use TarsosDSP core 2.5, but treat it as a GPL-3.0 Java DSP toolkit, not as an
Android audio-file-analysis SDK. Feed it PCM decoded by the app and implement
track-level aggregation and spectral descriptors yourself. Not a drop-in
aubio equivalent: tempo quality, confidence, descriptor definitions, Android
glue and phone throughput are our responsibility.

## 1. License
GPL-3.0 (repository and core publication metadata). Compatible with a GPL-3.0
app; normal GPL compliance applies.

## 2. Android status: use `core`, never `jvm`
- Modules: core, jvm, examples. No Android module or AAR.
- core is a Java-library JAR, version 2.5, Java 11 bytecode; no production
  dependency on jvm.
- Published at a maintainer-hosted Maven repo:
  `maven { url = uri("https://mvn.0110.be/releases") }`,
  `implementation("be.tarsos.dsp:core:2.5")`. Not on Maven Central: vendor a
  pinned copy.
- Do NOT add `be.tarsos.dsp:jvm`: its AudioDispatcherFactory imports
  javax.sound.sampled and says it does not work on Android.
- Maintainer: old Android support was "a bit of a hack" and removed; core does
  not depend on javax. Issue #213 (Android) still open.
- Avoid: jvm AudioDispatcherFactory; PipeDecoder / PipedAudioStream / fromPipe /
  AndroidFFMPEGLocator (they use FFmpeg); BeatRootSpectralFluxOnsetDetector as a
  flux extractor (non-streaming).

## 3. Feature coverage
| Feature | TarsosDSP core | We implement |
|---|---|---|
| RMS | AudioEvent.getRMS() per buffer | exact track RMS sqrt(sum x^2 / n) |
| Crest | none | max(abs x) / trackRms, silence policy |
| ZCR | ZeroCrossingRateProcessor per frame | count across whole stream incl. frame boundaries |
| FFT | FFT (window, forward, magnitudes, bin->Hz) | scheduling, aggregates, descriptors |
| Centroid | FFT only | sum(f*m)/sum(m) |
| Rolloff | FFT only | versioned threshold (e.g. 85%) |
| Flatness | FFT only | exp(mean(log(max(m,eps))))/mean(m) |
| Flux | internal only | sum(max(0, m_t - m_t-1)) |
| Onsets | ComplexOnsetDetector (translation of aubio onset.c) | thresholds, stats |
| BPM | BeatRootOnsetEventHandler -> beat times only | scalar BPM, confidence |
| Onset variance | none | define precisely and version it |

Notes: TarsosDSP FFT is in place and dispatcher buffers are reused: copy frames
first. BeatRoot gives beat times, no getBpm()/confidence; derive
rawBpm = 60 / median(valid beat intervals). For Sistrum, use Airmedy's
ffmpeg_analyzer.h definitions so formulas.go keeps its meaning.

## 4. Pipeline (one pass)
decode -> PCM conversion + deterministic mono downmix -> rolling frame buffer ->
time-domain accumulators + one STFT processor + ComplexOnsetDetector ->
BeatRoot -> finalize aggregates + BPM -> persist.
For Sistrum: decode through the Decoder Registry, not MediaCodec alone.
Version persisted metadata: FFT size, hop, window, downmix, rolloff percentile,
magnitude vs power, flatness epsilon, silence policy, onset thresholds, BPM
folding/confidence policy.

## 5. Background constraints
CoroutineWorker max ~10 minutes: bounded per-track work, transactional
checkpoints, source fingerprint (MediaStore id + mtime/size), release decoders
on cancellation, foreground execution when user-visible.

## 6. Maintenance (checked 2026-10-01)
core 2.5; no GitHub releases; latest master commit 41476b2 (2026-06-18,
Gradle maintenance). Open issues: #213 Android packaging, #207 docs, #225
stream-read failure. Pin artifact/commit, own the adapter, add instrumentation
tests.

## 7. Performance
No credible benchmark of TarsosDSP + Android decoding + full-track analysis on
phones. Gate rollout on a device benchmark: decode and DSP real-time factors
separately, memory, reliability (cancel/resume/corrupt/unsupported), accuracy
vs aubio desktop on a labelled corpus.

## 8. Alternatives
- JTransforms 3.2 (BSD-2) + own Kotlin DSP: transforms only; depends on
  JLargeArrays; prove D8/R8 compatibility.
- Small in-repo Kotlin FFT + descriptors + tempo tracker: full control, all on us.
- Kymatik (Kotlin, MIT): beta, WAV-focused BPM; reference only.
- jAudioGIT (LGPL-2.1): old, no modern Android path.
- aubio (C, GPL-3.0): needs NDK.
- Kotlin port of aubio algorithms: possible, a development project.

## Go/no-go gate
core:2.5 builds with minSdk 31 + R8; decode MP3 and AAC; validate
RMS/ZCR/crest/STFT on synthetic signals; compare onsets/BPM with aubio desktop;
benchmark on low/mid/high phones; verify cancellation and resume.
