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

`mix.Matrix.forLayouts` provides WaxFlow's energy-normalized downmix/widening.
`mix.Media3Layout.forMask` provides named-speaker placement into Media3's count
layouts, with silence padding. Pass the matrix's output count to Media3 after
applying it; passing the source count would mislabel speakers. Correlated downmix
content can exceed unity (`maxGain` reports the bound); normalization is by energy,
not a clip guarantee. No limiter or audio sink is wired by this module.

`gain.Compressor` supports the pinned `voice` preset with a linked peak detector
and 10-second settle horizon. `gain.Limiter` ports the package's 4x true-peak
min-hold kernel, with a 2-second horizon and compensated drain. `gain.Gain` is
the scalar kernel. These optional dynamics kernels are not wired into playback.
