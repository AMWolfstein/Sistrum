# DSP port validation — 2026-10-12

Pinned fork: AMWolfstein/WaxFlow `b7857aff88820ad37936026421d1641e64611dbe`.

## Changes

- New pure JVM `:dsp`: resample (HQ/Fast), silence, channel matrices and Media3
  named-speaker layout adapter, scalar gain, voice compressor/linked detector,
  true-peak limiter, and the existing codec radix-4/2 FFT.
- Analysis's FFmpeg-derived resampler relocated without arithmetic changes;
  analysis depends on DSP. Codec FFT consumers change only imports; codecs
  depends on DSP. Supporting Gradle, notices, README and oracle scripts/docs
  are within the module-move and parity-validation scope.
- Dynamics and channel adapter remain unwired. Dither/psy are excluded.
  Player, native sources, Room schema, strings and generated font are unchanged.
  FFmpeg removal remains a later migration task.

## Commands and results

`./gradlew :dsp:test :analysis:test --console=plain`: passed; 1,127 analysis
checks preserve the existing corpus and checkpoint semantics after relocation.

`./gradlew :dsp:test :dsp:benchmark --console=plain`: passed. Final DSP suite:
18 tests, zero failures/errors/skips. Go vectors cover 48 resampling streams,
81 silence streams, 240 mix pairs, 78 gain streams and 24 FFT transforms.
Frozen pre-move FFT hashes cover 27 transforms. Independent profile quality,
exact chunking, integer anchors/durations, limiter identity/ceiling, settle
horizons and thread-allocation checks also pass. Eight benchmark rows all
record zero allocated bytes; timings are in `benchmarks.tsv`.

`scripts/dsp-oracle/generate.sh`: all five fixture files regenerate byte-for-byte
from the clean pin. Float tolerances and storage adaptations are in `ORACLE.md`.

Full gate:

```sh
./gradlew :dsp:test :codecs:test :analysis:test :metadata:test \
  :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest \
  :androidApp:assembleDevDebug --continue --console=plain
```

| Module/task | Tests | Failures | Skips |
| --- | ---: | ---: | ---: |
| DSP | 18 | 0 | 0 |
| Codecs | 1,477 | 0 | 0 |
| Analysis | 1,127 | 0 | 0 |
| Metadata | 1,391 | 0 | 0 |
| Android unit tests | 907 | 7 | 1 |
| Shared logic host tests | 39 | 0 | 0 |
| assembleDevDebug | completed | — | — |

Codec hash/seek assertions remain unmodified and pass after the FFT move.
The FFT file is identical to its old source except for the package declaration.
`git diff --check` passes.

## Remaining acceptance blocker

The full gate exits nonzero on seven existing `GainRampTest` assertions:
`targetRampReachesSixDbForSixteenBitInput`,
`targetRampReachesSixDbWithinTheRampWindow`,
`flushJumpsStraightToTheCurrentTarget`, `nonFiniteTargetsAreIgnored`,
`sliderDragHasNoStepsAndSettlesOnTheLastTarget`,
`setterFromAnotherThreadIsAppliedAtTheNextBuffer`, and
`rampStartsFromTheGainActuallyApplied`.

These tests, introduced in commit `96d1b1c`, explicitly precede T052. The existing
`player/dsp/GainProcessor.setTargetGainDb` is still its intentional no-op stub.
Neither that stub nor the tests are changed by this port. Implementing T052 is
outside this brief's DSP ports and the AGENTS.md player-scope restriction; scope
clarification was requested. The new compressor is unrelated to this failure
and remains unwired. **The requested zero-failure acceptance is not met.**

One legacy `WaxFlowOracleLoudnessTest` is skipped by its existing assumption
because `SISTRUM_CORPUS` for the older `sistrum/` corpus is absent. The pinned
codec and analysis corpora verify successfully and their suites do not skip.
No test was deleted, disabled, weakened or given a changed expected value.
