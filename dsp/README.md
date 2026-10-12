# JVM DSP

Pure Kotlin/JVM kernels; no Android, Media3 or native dependencies.
`resample.Resampler` ports WaxFlow HQ/Fast; process/drain accept caller-owned
planar arrays and offsets, return produced frames, and expose `consumed`.
Equal rates are identity at the caller and are refused by the kernel.
`resample.AnalysisResampler` preserves the existing analysis oracle semantics.

See [oracle and tolerances](../docs/dsp/ORACLE.md). No kernels are wired into
playback by this port. Dither and psy are excluded.

`silence.Detector` takes minimum duration in integer nanoseconds. Allocate enough
span capacity before processing, then read `from(i)`/`to(i)` and dropped/total
counters after `flush()`. `reset()` reuses storage. Algorithm version: silence-1.
