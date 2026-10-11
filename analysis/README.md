# Mood analysis

Independent JVM module with no Android, decoder, database, scanner or player
references. Pinned algorithms and corpus reconstruction are documented in
[ORACLE.md](../docs/analysis/ORACLE.md); licence and port changes are in
THIRD-PARTY-NOTICES and SISTRUM-PATCHES.md.

`Mood` computes the locked Airmedy energy, danceability and brightness formulas
from `RawFeatures` and corpus-relative `Percentile` values. Missing percentile
entries use the upstream neutral 0.5 normalization. `computeCorpusPercentiles`
returns the pure sorting/interpolation result and sample count; the caller owns
persistence. It copies columns rather than mutating caller-owned arrays, with
Go's NaN-first ordering preserved.

```
python3 scripts/analysis-oracle/build.py
python3 scripts/analysis-oracle/corpus.py --generate
./gradlew :analysis:testMood
```

The Kotlin suite ports all 17 upstream mood test cases, separating the pure
percentile calculation from repository persistence. Another test compares 1,024
vectors (6,144 scalar comparisons) emitted by the pinned Go code at 1e-12. The
additional edge case checks Go's NaN-first sorting behavior. Reproduce Go vectors:

```
cp scripts/analysis-oracle/mood-parity_test.go "$HOME/.cache/sistrum-analysis-oracle/src-airmedy/internal/app/analysis/mood/sistrum_parity_test.go"
cd "$HOME/.cache/sistrum-analysis-oracle/src-airmedy"
SISTRUM_MOOD_FIXTURES=/absolute/path/to/Sistrum/docs/analysis/mood-oracle.json go test -v ./internal/app/analysis/mood
```

## Incremental PCM features

Construct `PcmAnalyzer(channels, sampleRate)`, then call
`feed(interleaved, channels, sampleRate)` with complete interleaved frames.
The format remains fixed. The current supported bounds are 1–9 channels and
8,000–2,822,400 Hz, including the pinned DSD decoder's PCM rates. Channel order
is FFmpeg's default for the channel count. `finish()` seals the analyzer and
returns an immutable `AnalysisResult` with upstream field names and units;
repeated finish calls return the same result.

`cancel()` makes the next feed return false with the entire block unconsumed.
`clearCancellation()` or `resume(checkpoint)` enables continuation. `frames`
is the consumed PCM cursor. Checkpoints contain all bounded DSP histories and
are for trusted local state with the same checkpoint version. The caller
records the source fingerprint and decoded cursor. Byte-array and stream
checkpoint/resume APIs are available; the caller owns streams. Use the stream
variant for high-rate multichannel state to avoid duplicate byte arrays.

The loudness histories reserve 3.4 seconds of double energy values per channel
at construction (about `27.2 * channels * sampleRate` bytes), plus filter and
histogram state. At 6 channels / 1.4112 MHz this is about 220 MiB. Feed never
grows these buffers and makes no allocations. Silence/non-finite results retain
the pinned oracle's semantics, including -Infinity RMS and NaN crest in the
floating-point torture vectors. Integration and storage policy belong to future
work; this module has neither.

Run `./gradlew :analysis:test :analysis:benchmark`. Results and endpoint fixes
are documented in [VALIDATION.md](../docs/analysis/VALIDATION.md).

Spectral features use channel 1 and preserve FFmpeg's magnitude-based units,
Hann window and hop cadence. `PcmAnalyzer.ALL` enables every implemented group;
explicit `DYNAMICS`, `LOUDNESS`, `SPECTRAL` and `RHYTHM` masks support individual profiling.
The checkpoint format is versioned as groups are added.

Tempo and onset analysis share a mono float stream at 44.1 kHz, with native
channel coefficients and no EOF resampler drain or partial-hop processing.
Tempo is the mean of aubio's per-beat estimates; `onset_variance` is sample
variance of intervals in **samples squared**, not seconds squared. All native
octave decisions are retained. Checkpoint format version 3 includes every
rhythm state. `:analysis:testRhythm` runs the complete native rhythm comparison.
