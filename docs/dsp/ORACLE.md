# DSP oracle

Source: AMWolfstein/WaxFlow at `b7857aff88820ad37936026421d1641e64611dbe`.
Run `scripts/dsp-oracle/generate.sh` with `SISTRUM_WAXFLOW_DIR` pointing to
that clean checkout. The helper imports the Go packages directly; committed
JSON contains generated planar input and Go output, so JVM tests need no Go.
The script refuses another pin or modified checkout.

Resample: both profiles, 44.1/48 kHz both ways, 48→16 kHz and 8→48 kHz,
mono/stereo/three-channel accumulation paths, zero and nonzero anchor phases.
The JVM feeds each vector with 1, 31, and 513 frame chunks and 37-frame output
buffers. Counts and integer timeline arithmetic are exact. PCM tolerance is
absolute 2e-7 full scale: Go/JVM sin and Bessel design can differ in their final
float64 bits before float32 coefficients, and Go permits fused operations.
No tolerance is applied to chunk invariance in the additional JVM checks.

The FFmpeg-derived analysis resampler moved without arithmetic changes and
is tested through the existing analysis corpus/checkpoint suite. It remains a
separate class because its priming, tail and arithmetic match a different
oracle. No FFmpeg removal or playback wiring happens here.

`./gradlew :dsp:test :dsp:benchmark` runs parity, boundary and measured hot-loop
allocation checks. Benchmarks warm 20,000 chunks, then measure 10,000 chunks
using the JVM thread allocation counter. Construction and JSON loading are
outside measurement. Performance numbers are local evidence, not a portable
latency guarantee.

Silence: 81 vectors cover studio/room-tone/analog guidance (-80/-55/-40 dBFS),
8/44.1/48 kHz, 1/2/6 channels, exact and fractional nanosecond bounds,
leading/trailing spans and threshold equality/adjacent float32 values.
Endpoints, minimum frames and all counters compare exactly, including one-frame
and fragmented runs. NaN/Infinity follow Go's peak comparison behavior.
Spans are stored in caller-sized preallocated primitive arrays; endpoint reads
are allocation-free, snapshots allocate outside processing. Default capacity
is 4096 spans; exhaustion is explicit. This capacity policy is the only
storage adaptation from Go's growable slice.

Mix: 240 generated layout pairs cover every named WAVE speaker bit, mono/stereo,
quad, rear/side 5.1, 6.1, 7.1, all 18 positions, identity and unsupported layouts.
Impulse responses assert every float32 coefficient bit-exactly; MaxGain sums
compare exactly. Mixed float32 PCM allows absolute 2e-7 for fused arithmetic.
Rows are energy-normalized before narrowing, LFE is dropped on stereo/mono
fold-down, and widening retains WaxFlow's single-channel placement convention.

The separate Media3Layout adapter preserves named speakers even for single-channel
masks. It chooses the smallest compatible count-only layout among 1..8; missing
positions are silence. Every subset of the canonical eight-speaker layout is
covered by placement tests. Side 5.1 becomes eight channels with silent rear slots;
LFE alone becomes six channels with a silent centre. Height/front-of-centre/back-
centre combinations outside those envelopes use the stereo gain table; undefined
bits refuse. The adapter is available for spec 002 and is not wired into playback.
The count masks match the installed Media3 1.11.1 Util.getAudioTrackChannelConfig(int)
(bytecode inspected locally): mono, stereo, FL/FR/FC, quad, five-channel rear,
rear 5.1, rear 6.1, and rear+side 7.1. Android mask numbers are not WAVE mask numbers.

Gain: 72 compressor/limiter vectors (8/44.1/48/96 kHz; 1/2/6 channels;
broadband, isolated transients and high-frequency tones) plus six scalar gain
vectors. Voice preset, compressor-1, gain-1, and limiter-3 are preserved.
The linked peak detector caps at 2.0 and ignores NaN in its peak comparisons;
samples themselves retain NaN/Infinity behavior. Scalar outputs are bit-exact;
FromDB allows relative 1e-12 for Go/JVM pow. Compressor/limiter PCM allows absolute
4e-7 for transcendental design/envelope and fused float32 arithmetic differences.
JVM chunking and horizon rejoin compare output bits exactly. Fixed-point smoother
operations use the same signed integer arithmetic and 2^-40 truncation.
Under-ceiling limiter input is bit-identical; clamp is inert on ceiling fixtures.
NaN ceiling is rejected before allocation (Go lacks that validation); -Infinity
is allowed as in Go. The deque is preallocated; it has no growable objects.
Compressor and limiter are available only as kernels and are not wired into
Media3; the optional dynamics feature remains for later.
