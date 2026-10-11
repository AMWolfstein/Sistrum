# Analysis port changes

- Source pins and algorithm definitions are in `docs/analysis/ORACLE.md`.
- Independent pure-JVM PCM API; no decoder, Android, Room, scanner or player dependency.
- Desktop oracle has a standalone cgo transport around the unmodified Airmedy
  header; it is external and never shipped.
- The research's BeatRoot suggestion is superseded by the owner's exact aubio requirement.
- Spec 001 M6 separately ports a BS.1770 loudness meter into androidApp from
  WaxFlow. This module's FFmpeg-derived loudness/LRA/true-peak implementation
  overlaps. Unification can happen later after measuring definition, channel
  layout, EOF, checkpoint and tolerance parity; neither module depends on the other.
- These ports read LGPL/GPL sources and are not clean-room contributions to WaxFlow.
- Mood maths preserve operation order, weights, sigmoid steepness, tempo cap,
  triangular tempo score, linear percentile ranks and NaN-first sorting. The
  pure percentile API copies input arrays and returns results instead of
  calling a database repository. All source Go tests and 1,024 numeric vectors
  have Kotlin parity checks at 1e-12.

- Dynamics/loudness preserve 100 ms metadata boundaries, 0.01 LU energy bins,
  absolute/relative gates, FFmpeg's LRA ranks and channel weights, 192 kHz
  Kaiser true-peak resampling (including initial reflection and no EOF drain),
  metadata rounding and astats sample-peak fallback/extrema behavior. The
  resampler uses a power-of-two ring to remove modulo divisions from each tap.
- PCM format and buffers are fixed at construction; `feed` allocates nothing.
  Cancellation stops before consuming a block. Local versioned Java
  serialization captures all DSP state; stream overloads avoid duplicate
  state-sized byte arrays on high-rate multichannel input.

- Spectral descriptors preserve the 2048-point symmetric Hann window, 1024
  hop, initial zero half-window, partial final-hop padding, channel 1,
  magnitude normalization, Nyquist exclusion, float accumulation and six-digit
  metadata rounding. The mathematical FFT uses JVM radix-2 butterflies instead
  of FFmpeg's architecture-specific AVTX codelets; all four descriptor errors
  remain within the requested 0.5%. Checkpoint format advances to version 2
  to include the spectral window, previous magnitudes and accumulators.
