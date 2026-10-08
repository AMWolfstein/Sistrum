# WavPack validation — 2026-10-08

The external fork fix `b7857aff88820ad37936026421d1641e64611dbe` is published on
`fix/riff-wrapped-wavpack`. GitHub's branch ref and commit API confirm the hash.
The authorized Sistrum changes are split into an oracle-pin commit and a codec
port commit on `port/waxflow`. Neither repository's main branch is changed.

## Changes

- External fork: RIFF demuxer checks the first ten data bytes for the legacy
  version-1/2/3 header before truncation warnings or PCM decoding. Original code
  uses only MIT WaxFlow code and observed corpus bytes, without GPL/LGPL sources.
  New tests cover all eight streams in strict and tolerant modes, engine routing,
  ordinary PCM magic and exact bounded read cost. `SISTRUM-PATCHES.md` records it.
- Sistrum: script/document pin and public fixtures updated. Exactly eight rows
  changed from success to unsupported-version refusal; no source hash changed.
- Kotlin: positional `RandomAccessSource`, ByteBuffer and FileChannel adapters,
  bounded windows with Long offsets, reused packet/PCM buffers. InputStream is
  supported by a disk-spooling adapter only in tests. Unified version refusal
  in codec and container, including recognition of legacy RIFF wrappers.
- Synthetic extended-integer maximum-width file: full/partial/no-bit branches
  and signed samples. Expected PCM was decoded by the pinned Go CLI; the small
  vector and JSON provenance are test assets, not a copy of the corpus fixtures.
- Source tests cover partial reads, buffer regions, EOF, offsets beyond 2 GiB
  and sticky zero-progress failures. Corpus tests also decode file-backed input.

No new Sistrum edits outside `codecs/`, `docs/waxflow/`, `scripts/` and the public
fixtures. The `settings.gradle.kts` include was already present from the preceding
module task and was not edited during this follow-up.

## Commands and results

Corpus remains `/tmp/sistrum-waxflow-oracle/corpus`. Go commands ran in the
external clone with external Go caches and `GOMAXPROCS=2` / `-p 2`.
Go counts below are **leaf cases**, excluding parent/subtest double-counting.

| Command | Result |
|---|---|
| `go test -json -p 2 ./container/riff ./codec/wavpack ./container/wv` | 511 passed, 0 failures |
| `go test -json -p 2 ./tests -run '^TestWavPackSuite'` | 56 passed, 0 failures |
| `go test -json -p 2 -timeout 10m ./...` in `oracletest/` | 130 passed, 0 failures |
| `go test -json -p 2 -timeout 15m ./...` in root | 5663 passed, 2 failures, 273 skips; 66 packages passed, 1 failed, 5 skipped |
| `go test ... ./tests -run '^TestFixturesDecodeDifferential$/^sine-ima'` at unchanged base | Same two failures; 2 passes |
| `bash scripts/waxflow-oracle.sh /tmp/sistrum-waxflow-oracle/corpus androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv` | 132 rows: 70 decoded, 62 refused; `.wv`: 20 decoded, 30 refused |
| `python3 codecs/tools/generate-max-width.py /tmp/sistrum-waxflow-oracle/oracle-work/bin/waxflow` | Go confirmed all 16 synthetic PCM samples and the recorded checksum |
| `./gradlew :codecs:test :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest :androidApp:assembleDevDebug` | Codecs: 211 passed, 0 failures/skips. Android unit: 899 passed, 7 failed, 1 skipped; combined gate fails |
| `./gradlew :sharedLogic:testAndroidHostTest :androidApp:assembleDevDebug` | Build succeeds; shared test result initially cached |
| `./gradlew :sharedLogic:testAndroidHostTest --rerun :codecs:benchmark` | Shared: fresh 39 passes, 0 failures/skips. Benchmark succeeds |
| `./gradlew :codecs:test --rerun :sharedLogic:testAndroidHostTest --rerun --max-workers=2` | Pre-commit rerun: codecs 211 passed; shared logic 39 passed; zero failures/skips |
| `go test -json -count=1 -p 2 -timeout 10m ./...` in `oracletest/` | Pre-commit rerun: 130 leaf cases passed, zero failures/skips |

Full-suite failures remain outside the requested scope:

- Go `TestFixturesDecodeDifferential/sine-ima.wav` and `sine-ima-stereo.wav`
  also fail at `446ca3124d890fd08caecdcbf8931a493d4ebd04`.
- Android `GainRampTest`: `targetRampReachesSixDbForSixteenBitInput`,
  `targetRampReachesSixDbWithinTheRampWindow`, `flushJumpsStraightToTheCurrentTarget`,
  `nonFiniteTargetsAreIgnored`, `sliderDragHasNoStepsAndSettlesOnTheLastTarget`,
  `setterFromAnotherThreadIsAppliedAtTheNextBuffer`,
  `rampStartsFromTheGainActuallyApplied`. The same seven failures reproduce in a clean detached worktree at
  `feature/media3-migration` HEAD `c58f76905f2e687bba7a48f0b1a42045d770df28`,
  with none of the oracle or codec changes. These failures were not patched.

## GainRampTest baseline evidence

Clean worktree: `/tmp/sistrum-gainramp-baseline`, detached at
`c58f76905f2e687bba7a48f0b1a42045d770df28` (the local
`feature/media3-migration` HEAD). Its tracked and untracked git status is clean.
Only a symlink to the existing ignored machine-local `local.properties` supplied
SDK/font-tool paths; no tracked file or test was modified. Compiled artifacts
were restored from Gradle's content-based cache; the tests themselves executed.

```bash
./gradlew :androidApp:testDevDebugUnitTest \
  --tests 'me.misa198.airmedy.player.dsp.GainRampTest' \
  --no-daemon --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process
```

Result: **9 tests, 2 passed, 7 failed, zero skipped**. Exit code 1.
Assertion messages from the JUnit XML (all are `java.lang.AssertionError`):

| Test | Assertion message |
|---|---|
| `targetRampReachesSixDbForSixteenBitInput` | `settled 16-bit gain 1.0 must equal 1.9952623` |
| `targetRampReachesSixDbWithinTheRampWindow` | `ramp never reached 1.9952623 (first frame within 1e-4 was -1)` |
| `flushJumpsStraightToTheCurrentTarget` | `first sample after flush must already be at the target expected:<0.49881557> but was:<0.25>` |
| `nonFiniteTargetsAreIgnored` | `non-finite target changed the gain: 1.0, expected 1.9952623` |
| `sliderDragHasNoStepsAndSettlesOnTheLastTarget` | `did not settle on 9.0 dB: gain 1.0, expected 2.818383` |
| `setterFromAnotherThreadIsAppliedAtTheNextBuffer` | `gain from another thread not in effect at frame 380` |
| `rampStartsFromTheGainActuallyApplied` | `+12 dB ramp did not start: gain before retarget was 1.0` |

Verdict: **pre-existing**. GainRamp code and its tests remain unchanged.

The legacy RIFF misclassification is fixed in the fork. One remaining source
behavior difference is deliberate: native Go container sync filtering still
turns raw out-of-range versions into a malformed-header error, while Kotlin
uses the unified unsupported-version error requested in this follow-up.

## Benchmark

AMD Ryzen 5 3500U, OpenJDK 21.0.12.1, 8 reported CPUs. Five warmups and five
full measured decodes per file; median. Same 20 supported ByteBuffer inputs.
File loading, open, seek and hashing are excluded. Bounded memory reads and the
container block walk are included. These are rough laptop measurements;
file-I/O and phone performance gates remain unmeasured.

| Metric | Before | After |
|---|---:|---:|
| Minimum RTF | 0.003626 | 0.003555 |
| Median RTF across files | 0.010530 | 0.010392 |
| Maximum RTF | 0.015825 | 0.015666 |
| Median allocated bytes per decode, every file | 0 | 0 |

The paired median after/before ratio is 0.9955. Run noise prevents a strong
performance conclusion. Actual per-file records: [before](benchmarks/before.tsv),
[after](benchmarks/after.tsv), [comparison](benchmarks/comparison.tsv).
The historical before report retains the eight old oracle mismatches.

The [README refusal list](README.md#the-30-refused-wv-files-grouped-by-actual-first-refusal)
contains every refused `.wv` file, grouped by actual first refusal: hybrid 17,
float 1, DSD 2, channel count 0, version 8, sample CRC 1, framing 1.
The six-channel file hits hybrid refusal first.

## Sistrum codec commit message

```text
feat(codecs): port lossless WavPack with oracle parity

Add bounded random-access decoding, reused PCM buffers and sample seeking.
Pin the legacy RIFF refusal fix and add corpus, max-width and benchmark coverage.

Known pre-existing failures: GainRampTest.targetRampReachesSixDbForSixteenBitInput; GainRampTest.targetRampReachesSixDbWithinTheRampWindow; GainRampTest.flushJumpsStraightToTheCurrentTarget; GainRampTest.nonFiniteTargetsAreIgnored; GainRampTest.sliderDragHasNoStepsAndSettlesOnTheLastTarget; GainRampTest.setterFromAnotherThreadIsAppliedAtTheNextBuffer; GainRampTest.rampStartsFromTheGainActuallyApplied; WaxFlow TestFixturesDecodeDifferential/sine-ima.wav; WaxFlow TestFixturesDecodeDifferential/sine-ima-stereo.wav

Co-authored-by: Codex <noreply@openai.com>
```
