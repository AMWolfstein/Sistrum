# Byte-table DSD performance and analytic comparison

0.136–0.141 RTF on laptop for DSD256 stereo; phone gate pending

Laptop performance is accepted. The 0.10 laptop RTF target is dropped; the real
performance gate is the phone measurement in
[spec 002](../../specs/002-kotlin-decoders/NOTES.md). DSD256 stereo measured
0.136461 RTF (DFF) / 0.141493 RTF (DSF). No phone result is claimed. The user
prioritized a flat response through 20 kHz over matching Flick's old roll-off;
the corrected comparison below identifies the source of the difference.

## Implementation

Production runs a float byte-table FIR on raw MSB-first DSD, with double accumulation, to a 352800 Hz intermediate rate. DSD64/128/256 decimation factors are 8/16/32 bits, respectively. Its symmetric 128/256/512-tap kernels have a 176400 Hz cutoff and beta-14 Kaiser windows. Shared tables contain 16/32/64 byte rows of 256 values. They are generated at construction/cache miss, never in the sample loop.

A sparse 65-tap half-band FIR decimates by two to 176400 Hz; only its 16 symmetric nonzero pairs and center are evaluated. A symmetric 192-tap, beta-14 Kaiser FIR at 176400 Hz has a 25000 Hz cutoff and emits only the requested output phases (decimation 1 or 2 for the defaults). This yields flat audio through 20 kHz and suppresses content from 30 kHz upward. Four independent double sums shorten inner-loop dependencies. Doubled byte and PCM rings retain state, as do decimation phases across calls. No new drain operation is introduced.

`Dsd`, `Dsf`, and `Dff` default to `bestTargetRate`: 88200 Hz for DSD64, 176400 Hz for DSD128/256. Existing explicit targets (including 352800/705600 where already supported) remain available; intermediate/filter sizes scale for those targets. Explicit 44100 Hz uses a 2048-tap final FIR with a 21025 Hz cutoff. Default selection never exceeds 176400.

The pinned converter and a matching test-only container adapter are retained as `FlickExactDecimationPipeline` and `FlickExactDsd`; unchanged Rust hashes still validate that path. Production never calls them. Original production DoP parity/seek tests remain on `Dsd`. New production corpus checks cover exact seeks, positions/discontinuity flags, invalid seeks, borrowed output, short reads, direct buffers, refusals, defaults and explicit targets.

## Gain and measurement convention

DSD bits map to +/-1, and unity DC gain maps all-one/all-zero DSD to PCM +1/-1. This is Flick's level convention; no gain adjustment is applied. Full-scale constant levels are compared directly to the exact reference. The -6 dB sine uses peak amplitude `10^(-6/20)`. THD+N is residual RMS divided by fitted fundamental RMS, over the full output band; the 20 Hz–20 kHz result is also recorded. Noise dBFS is RMS relative to amplitude 1.

The original test-code generator is a ninth-order error-feedback one-bit modulator. Its Butterworth high-pass noise-transfer function has a 60.6816 kHz corner, unity leading coefficient and peak gain 1.476 at DSD64 (lower at higher rates). Factored sections avoid numerical polynomial cancellation. All sines, sweep, silence, full-scale constants and ultrasonic square patterns are generated in the tests. No new audio goldens or reference downloads are needed.

## Acceptance measurements

| Metric | DSD64 | DSD128 | DSD256 | Requirement |
|---|---:|---:|---:|---|
| Coefficient passband maximum absolute deviation (dB) | 0.00000252 | 0.00000293 | 0.00000313 | <= 0.1 dB |
| Generated-tone maximum absolute gain deviation (dB) | 0.00023525 | 0.00030983 | 0.00034333 | <= 0.1 dB |
| Coefficient stopband maximum (dB) | -137.249 | -137.201 | -137.204 | <= -100 dB |
| Stopband including worst byte-table rounding bound (dB) | -129.543 | -129.983 | -130.688 | <= -100 dB |
| 1 kHz THD+N, full output band (dB) | -100.358 | -110.467 | -114.653 | < -100 dB |
| 1 kHz THD+N, 20 Hz–20 kHz (dB) | -117.432 | -126.948 | -130.236 | < -100 dB |
| Ultrasonic sine noise above 30 kHz (dBFS) | -166.928 | -163.899 | -163.808 | <= -60 dBFS |
| Full-scale 35.28 kHz DSD square: total output RMS (dBFS) | -144.870 | -144.871 | -144.872 | <= -60 dBFS |
| 1 kHz sine gain (dB) | -0.00000049 | -0.00000049 | -0.00000055 | unity within passband tolerance |
| Full-scale DSD to PCM gain (dB) | 0.000 | 0.000 | 0.000 | 0 dB |
| Flick 0–15 kHz difference after measured delay/gain/polarity alignment (dB) | -66.796 | -70.334 | -28.544 | explained by pinned response below |
| Ours vs analytic flat signal, 0–15 kHz (dB) | -134.574 | -144.589 | -150.091 | < -90 dB |
| Flick vs its analytic filter prediction, 0–15 kHz (dB) | -111.115 | -132.212 | -142.973 | < -90 dB |
| Seek/chunk/reset error | 0 samples / 0 float error | 0 samples / 0 float error | 0 samples / 0 float error | exact |

Frequency checks evaluate a 131072-point grid across the entire input Nyquist band and exact passband endpoints. Composite response includes all multirate frequency folds. Stopband checks cover every sampled input frequency at/above output Nyquist, not just the last FIR. The rounding bound sums maximum float table-entry error per row and propagates it through subsequent FIR L1 norms. This bounds every byte sequence; it is not a measured tone attenuation. Production full-scale ultrasonic patterns independently verify strong rejection. Raw metrics, including silence and output-Nyquist patterns, are in `codecs/benchmarks/dsd-byte-table-spec.tsv`; -6000 dB denotes exactly zero under the report's logarithm floor, not a physical precision claim.

## Corrected Flick comparison and analytic diagnosis

The corrected test generates equal-amplitude 1 kHz + 10 kHz DSD tones and uses
coherent 100 ms windows after startup. An arbitrary-length Bluestein DFT
includes **every bin from DC through 15 kHz**, with no interpolation/window
leakage at the signal tones. It searches both polarities, fits a single
fractional delay on the physical group-delay branch, and fits one least-squares
scalar gain across the whole comparison band. The measurement code is checked
against a direct DFT and a known fractional shift/gain/polarity inversion.

| Rate | Delay applied to Flick (µs) | Gain applied to Flick | Polarity | 0–15 kHz difference (dB) | Ours vs flat analytic signal (dB) | Flick vs its analytic filter (dB) |
|---|---:|---:|---:|---:|---:|---:|
| DSD64 | 290.887187 | 1.000470263 | +1 | -66.795582 | -134.574147 | -111.115029 |
| DSD128 | 472.824546 | 1.000301965 | +1 | -70.333549 | -144.589063 | -132.212237 |
| DSD256 | 563.793226 | 1.036051842 | +1 | -28.544328 | -150.090538 | -142.973155 |

Positive delay means delaying Flick to align it with the longer new FIR.
These are **fitted measurements**, not just the coefficient-derived delays;
the test independently checks agreement with those delays within 20 ns.
The gains are comparison calibration only, not changes to either decoder.

The difference is still above -90 dB because Flick does not reproduce the
analytic flat signal's frequency response. The production decoder does. For
Flick, a separate analytic prediction uses the generated sine at its actual
sample times, normalized third-order CIC response, pinned 512-tap Kaiser FIR
response and known group delay. That check has **no delay/gain fitting** and
matches Flick below -111/-132/-142 dB. Thus the exact port is correct to its
Rust source; the **pinned algorithm's filter response fails the new flat-audio
contract**, most clearly at DSD256. This is documented in
`codecs/SISTRUM-PATCHES.md`; no production arithmetic fix is indicated.

| Rate | Flick 1 kHz gain (dB) | Flick 10 kHz gain (dB) | Flick 19.5 kHz gain (dB) | Earlier 0–20 kHz difference, rechecked (dB) |
|---|---:|---:|---:|---:|
| DSD64 | -0.000115 | -0.008059 | -17.119633 | -4.396882 |
| DSD128 | +0.000019 | -0.005267 | -10.566493 | -6.427217 |
| DSD256 | -0.000703 | -0.651034 | -8.071546 | -8.002360 |

A nominal 18 kHz cutoff is not a flat passband through 18 kHz. Flick keeps 512
taps and beta 10 while its FIR input rate rises from 705.6 kHz to 2.8224 MHz;
the absolute-Hz transition widens and reaches well below 18 kHz at DSD256.
It already attenuates 10 kHz by 0.651 dB, violating ±0.1 dB flatness. At the
previous 19.5 kHz comparison tone, attenuation is 17.12/10.57/8.07 dB. With
approximately unit 1 kHz gain, the ratio of the high-tone amplitude is about
0.139/0.296/0.395. For equal-energy tones and an optimal scalar gain, the
relative residual is `abs(1-a) / sqrt(2*(1+a*a))`, about 0.603/0.477/0.398,
which is -4.40/-6.43/-8.00 dB. The earlier large number is therefore explained
by the measured filter roll-off, not polarity, byte order or a delay error.
Even DSD64/128's small 10 kHz gain differences exceed a -90 dB waveform-null
requirement after a single gain fit; that criterion is much stricter than the
±0.1 dB passband contract.

The 0–15 kHz test is retained. It gates our output against the analytic flat
signal and Flick against its own analytic filter, and checks the measured
Flick attenuation; it does not pretend the differently shaped decoders can
null below -90 dB. Original Flick PCM golden hashes and parity assertions are
unchanged. Full comparison data are in
`codecs/benchmarks/dsd-byte-table-comparison.tsv`.

Exact seek means that replay reconstructs the uninterrupted filter state and adds no sample discontinuity. Jumping between arbitrary waveform phases can still create a playback splice; playback crossfades are outside this codec change.

## Laptop measurements

| Attempt | Design | DSD256 stereo DFF RTF | DSF RTF |
|---|---|---:|---:|
| 1 | Double row tables; 384-tap final FIR at 352.8 kHz | 0.287568 | 0.292068 |
| 2 | Sparse half-band; symmetric 192-tap final FIR at 176.4 kHz | 0.153416 | 0.158417 |
| 3 | Flattened float tables; double partial sums; allocation-free half-band loop | 0.136461 | 0.141493 |

Final speedup versus the fresh ring baseline is 5.085x (DFF) / 4.901x (DSF).
The laptop measurements are accepted; no additional optimization is needed
for the former 0.10 target. Stopband margin is about 30 dB and DSD64 full-band
THD+N margin is 0.36 dB on this generated signal. Phone CPU, crossfade,
battery and memory measurements remain pending under spec 002.

Initial signal-generator overload failures were repaired by keeping the NTF corner constant in Hz across rates and using -12.04 dB auxiliary sweep/high-frequency tones. The required 1 kHz THD+N signal remains -6 dB. No acceptance thresholds were reduced (except the explicit user-approved Flick-comparison change).

## Per-file before/after RTF

Same laptop/JVM, sequential runs; 20 warmups and median of five full decodes. Open/seek/hash excluded, bounded input reads included. All successful final rows have zero median decode-loop allocated bytes. Baseline uses the previous 176400 Hz default for every file; byte-table converter uses 88200 for DSD64 and 176400 for DSD128/256. The benchmark now derives duration from DSD input samples, avoiding dependence on target/output rounding. That changes these corpus durations by less than 0.03%. These short synthetic files are not a phone performance gate. Raw files: `codecs/benchmarks/dsd-byte-table-before.tsv` and `dsd-byte-table-after.tsv`.

| File | PCM before | PCM after | DoP before | DoP after |
|---|---:|---:|---:|---:|
| dsd128-1ch.dff | 0.145386 | 0.063220 | 0.006205 | 0.018741 |
| dsd128-1ch.dsf | 0.138682 | 0.055290 | 0.006686 | 0.007514 |
| dsd128-2ch.dff | 0.478531 | 0.090360 | 0.009765 | 0.014623 |
| dsd128-2ch.dsf | 0.481269 | 0.096369 | 0.012195 | 0.020735 |
| dsd128-6ch.dff | refused | refused | refused | refused |
| dsd128-6ch.dsf | 1.442372 | 0.332818 | 0.029937 | 0.040163 |
| dsd256-1ch.dff | 0.343302 | 0.072154 | 0.012800 | 0.014282 |
| dsd256-1ch.dsf | 0.345401 | 0.074664 | 0.015150 | 0.016643 |
| dsd256-2ch.dff | 0.693896 | 0.136461 | 0.019380 | 0.020083 |
| dsd256-2ch.dsf | 0.693542 | 0.141493 | 0.024045 | 0.024844 |
| dsd256-6ch.dff | refused | refused | refused | refused |
| dsd256-6ch.dsf | 2.083675 | 0.733296 | 0.058765 | 0.063271 |
| dsd64-1ch.dff | 0.185902 | 0.054705 | 0.003189 | 0.003144 |
| dsd64-1ch.dsf | 0.186012 | 0.035082 | 0.003753 | 0.003709 |
| dsd64-2ch.dff | 0.371947 | 0.052507 | 0.004822 | 0.004998 |
| dsd64-2ch.dsf | 0.373167 | 0.054232 | 0.005986 | 0.006171 |
| dsd64-6ch.dff | refused | refused | refused | refused |
| dsd64-6ch.dsf | 1.121527 | 0.162846 | 0.014803 | 0.016390 |
| dsd64-msb-partial-bits.dsf | 0.370756 | 0.050554 | 0.003842 | 0.004013 |
| dst-compressed.dff | refused | refused | refused | refused |

Refused-file timing cells in the TSVs are `NA` (not measured).
DoP code is unchanged; timing differences are noise. Three multichannel DFF files and DST retain their exact refusal messages.

## Validation and files

- Baseline: `./gradlew :codecs:benchmarkDsd --console=plain` — successful.
- Attempt 1: DSD oracle tests/benchmark passed; subsequent new spec tests exposed test-generator overload, repaired without changing thresholds.
- Attempts 2/3: `./gradlew :codecs:test --tests "*DsdSpecTest" :codecs:benchmarkDsd --continue --console=plain` — final spec tests and benchmark successful; laptop performance subsequently accepted.
- Corrected comparison: `./gradlew :codecs:test --tests "*DsdSpecTest" --console=plain` — successful, including full 0–15 kHz comparison and independent analytic checks.
- Final: `./gradlew :codecs:test :androidApp:assembleDevDebug --continue --console=plain` — successful; 846 tests, zero failures/errors/skips, Android debug assembly successful.
- `git diff --check` — clean.

Changed production files: `codec/dsd/DsdDecimationPipeline.kt`, `container/dsd/Dsd.kt`, `container/dsf/Dsf.kt`, `container/dff/Dff.kt` (all below `codecs/src/main/kotlin/me/misa198/airmedy/codecs/`). Test files: `DsdBenchmark.kt`, `DsdOracleTest.kt`, new `DsdSpecTest.kt`, `codec/dsd/FlickExactDecimationPipeline.kt`, and `container/dsd/FlickExactDsd.kt` (below the matching test root). Documentation: `codecs/README.md`, `codecs/SISTRUM-PATCHES.md`, `docs/dsd/ORACLE.md` and this report; four new TSVs under `codecs/benchmarks/` preserve measurements. Parser wrapper changes implement consistent defaults; documentation changes distinguish production specifications from the retained exact reference.

Remaining external gate: phone measurement in spec 002. Laptop results are accepted; no further laptop speed target applies.
