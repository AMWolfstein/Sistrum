# DSD FIR history performance

Step 1 replaces the 512-element shifting history with a mirrored 1024-element ring and one head index per channel. Each insertion performs two writes; the FIR still reads newest to oldest and accumulates taps 0 through 511 in the original order. Reset clears both halves and heads. CIC arithmetic, coefficients, partial-group discard and output normalization are unchanged. No other per-input O(taps) work or per-call allocations remain in the converter; the required 512-tap convolution occurs only per output frame.

## Measurements

Same laptop/JVM, sequential baseline and optimized runs: 20 warmups and median of five full decodes. Open, seek and hashing excluded; bounded reads included. These short synthetic files are not phone performance predictions. All successful rows have zero median allocated bytes before and after. Raw measurements: `codecs/benchmarks/dsd-before.tsv` and `codecs/benchmarks/dsd.tsv`.

| File | PCM before RTF | PCM after RTF | DoP before RTF | DoP after RTF |
|---|---:|---:|---:|---:|
| dsd128-1ch.dff | 0.599878 | 0.243017 | 0.010910 | 0.010904 |
| dsd128-1ch.dsf | 0.601590 | 0.240937 | 0.011701 | 0.007763 |
| dsd128-2ch.dff | 1.203814 | 0.534180 | 0.009674 | 0.011545 |
| dsd128-2ch.dsf | 1.207233 | 0.493282 | 0.012208 | 0.018761 |
| dsd128-6ch.dff | refused | refused | refused | refused |
| dsd128-6ch.dsf | 3.621940 | 1.512295 | 0.029909 | 0.035992 |
| dsd256-1ch.dff | 1.075229 | 0.346025 | 0.012835 | 0.012606 |
| dsd256-1ch.dsf | 1.090872 | 0.345157 | 0.015089 | 0.014956 |
| dsd256-2ch.dff | 1.484284 | 0.688590 | 0.019184 | 0.020052 |
| dsd256-2ch.dsf | 2.141361 | 0.694286 | 0.024103 | 0.024754 |
| dsd256-6ch.dff | refused | refused | refused | refused |
| dsd256-6ch.dsf | 5.138545 | 2.269914 | 0.058836 | 0.062923 |
| dsd64-1ch.dff | 0.367416 | 0.186384 | 0.003164 | 0.003141 |
| dsd64-1ch.dsf | 0.367873 | 0.186428 | 0.003766 | 0.003709 |
| dsd64-2ch.dff | 0.572088 | 0.372431 | 0.004875 | 0.005000 |
| dsd64-2ch.dsf | 0.737224 | 0.372861 | 0.005958 | 0.006241 |
| dsd64-6ch.dff | refused | refused | refused | refused |
| dsd64-6ch.dsf | 1.714817 | 1.163003 | 0.014690 | 0.015912 |
| dsd64-msb-partial-bits.dsf | 0.738155 | 0.370555 | 0.003786 | 0.004029 |
| dst-compressed.dff | refused | refused | refused | refused |

The three six-channel DFF files and DST file retain the same parser refusal messages. DSD256 stereo improves 2.16x (DFF) and 3.08x (DSF), but remains above 0.10 RTF. DoP is unchanged code; timing variation is benchmark noise.

## Validation

- `./gradlew :codecs:benchmarkDsd --console=plain`: baseline successful.
- `./gradlew :codecs:test :codecs:benchmarkDsd --console=plain`: successful; 838 tests, zero failures/errors/skips, including all 83 DSD checks. All 16 decoded files retain exact Rust PCM hashes and DoP windows; seek/reset and short-read checks pass.
- `./gradlew :androidApp:assembleDevDebug --console=plain`: successful.
- `git diff --check`: clean.

## Step 2 proposal — not implemented

Recommend a byte-table FIR decimator with a new matching Rust reference. Directly filter the MSB-first DSD bits and emit at 176400 Hz. For each byte position in the FIR window, precompute the weighted sum for all 256 byte values. A retained byte ring then needs one lookup and addition per eight taps, with no per-bit CIC integrator or comb loop. Total decimation is 16/32/64 bits for DSD64/128/256, so output windows stay byte aligned. Tables are constructed once and reused across channels; the decode path retains scratch.

An initial DSD256 design might use roughly 1024–1536 bit-rate taps, subject to frequency-response verification. That is 128–192 lookups per PCM frame per channel, versus 512 floating multiply-adds plus 64 per-bit third-order CIC updates in step 1. Float64 tables occupy roughly 256–384 KiB per rate. My engineering estimate is 8–15x faster than step 1 (DSD256 stereo about 0.046–0.087 RTF), not a measured guarantee: cache access, JVM bounds checks, container reads and the chosen response may reduce this gain. Profile and benchmark a prototype before adopting it. Merely naming the existing output-only convolution polyphase would not reduce its multiply count.

Response would deliberately change: remove third-order CIC droop and replace the pinned rate-dependent CIC + Kaiser response with a specified direct low-pass response. Proposed contract: DC gain 1, passband 0–18 kHz with at most 0.05 dB ripple, stopband from 88.2 kHz with at least 100 dB attenuation. This makes 18 kHz flat rather than the current FIR nominal cutoff, changes phase/delay and startup transients, and requires new golden outputs. Verify the actual response and folded DSD noise over all input rates; a nominal FIR stopband alone does not establish output noise performance. If preserving the current audible rolloff matters, instead fit the measured pinned composite response before generating tables.

A lower-risk alternative is CIC decimation 4/8/16 for DSD64/128/256, giving a common 705600 Hz FIR input and final decimation 4, with a redesigned shorter FIR and byte-batched CIC updates. At DSD256 this reduces comb and history-update work fourfold; it leaves per-bit integrator work unless byte batching is also implemented. Third-order CIC droop at 20 kHz increases from approximately 0.002 dB (R=4) to 0.034 dB (R=16); compensate it in the FIR. Reusing the existing coefficients at the new rate is invalid. I would expect a smaller gain than the direct byte-table design and would not assume it meets 0.10 RTF.

Validation against the new Rust design: same coefficient/table bytes, normalization, delay, channel order, reset and tail policy; identical output frame counts and refusals. Start with a per-sample float32 bound `abs(Kotlin - Rust) <= 2e-6 + 2e-5 * abs(Rust)` for normalized PCM, including startup and seek tails. This accommodates accumulation regrouping; it is not a tolerance against the old Flick waveform. Measure maximum/RMS error and tighten if actual agreement permits. Separately verify ripple, stopband, alias noise, DC, impulses, full-scale signals, sweeps, chunk boundaries and exact seek-to-linear output.

No step 2 implementation or golden fixture changes are included.
