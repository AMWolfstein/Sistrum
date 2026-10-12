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
