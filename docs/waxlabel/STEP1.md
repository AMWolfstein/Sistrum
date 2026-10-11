<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 1 validation report

Implemented the read-only, pure-JVM metadata core and WaxLabel vocabulary/mapping,
ID3v1 legacy projection, ID3v2.2/2.3/2.4, MP3 properties, and embedded ID3 in ordinary
WAV/AIFF/AIFF-C. All media and upstream files remain in the external cache. Work is
on `port/waxflow`; this step is the clean budget boundary. Steps 2–8 remain undone.

## Files changed

- `settings.gradle.kts`: register `:metadata`; no app dependency or integration.
- `metadata/build.gradle.kts`, `README.md`, `SISTRUM-PATCHES.md`,
  `THIRD-PARTY-NOTICES`: JVM configuration, API/lifetime documentation, deviations,
  and the copied MIT license. Only the source interface are used from codecs.
- `metadata/src/main/kotlin/me/misa198/airmedy/metadata/`: `Document.kt`,
  `Metadata.kt`, `Input.kt`, `TagVocabulary.kt`, `Id3.kt`, `Mpeg.kt`, `Iff.kt`,
  `PictureReader.kt`, `UnsynchronizedSource.kt`.
- `metadata/src/test/kotlin/me/misa198/airmedy/metadata/`: `OracleParityTest.kt`,
  `OracleSupport.kt`, `ReadContractsTest.kt`, `MetadataBenchmark.kt`.
- `scripts/waxlabel-oracle.py`: pinned source fetch/build, owned generation, CLI
  dumps, two-field public-API supplement, SHA-256 manifests and verification.
- `docs/waxlabel/`: pin/procedure, manifest, unmodified oracle projection, per-file
  benchmark snapshot, and this report.
- Root `THIRD-PARTY-NOTICES`: WaxLabel attribution and MIT license. Updating the
  existing settings/notices is necessary to register and attribute the new module.

No player, scanner, schemas, native code, generated fonts, audio assets, Graphify,
`.claude`, branch switches, or pushes were changed/performed.

## Commands and outcomes

1. `git ls-remote https://github.com/AMWolfstein/WaxLabel.git HEAD`, then external
   clone/source inspection: pinned `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
2. `~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate`:
   built the pinned CLI; generated **123** records and **689** manifest entries.
   `python3 scripts/waxlabel-oracle.py --verify`: all entries verified.
3. `./gradlew :metadata:compileKotlin` and targeted `:metadata:test`: successful
   after correcting port mismatches. Final metadata suite: **139 tests**, **0 failures,
   0 errors, 0 skips** (123 corpus cases plus 16 read/API contract cases).
4. `./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevDebug :metadata:benchmark`:
   successful; the full codec suite actually reran, with **1477 tests**, **0 failures,
   0 errors, 0 skips**. No codecs code changed afterward.
5. Final `./gradlew :codecs:test :metadata:test :androidApp:assembleDevDebug :metadata:benchmark`:
   successful after the last metadata/corpus additions; metadata compiled and all
   139 cases reran, `assembleDevDebug` succeeded, and all 123 files were benchmarked.
6. External upstream `go test -json ./internal/id3 ./internal/mp3 ./internal/wav
   ./internal/aiff ./tag ./internal/mapping ./internal/core`: **545 passed test
   events**, **0 failures**. Tests/source were not patched or weakened.
7. `python3 scripts/check-no-audio.py`: no tracked/staged audio.

The extra, broader command including `:androidApp:testDevDebugUnitTest` and
`:sharedLogic:testAndroidHostTest` found **7 failures and 1 existing skip in 907 app
tests**, while all **39 shared host tests passed**. All seven failures are
`GainRampTest`: the existing `GainProcessor.setTargetGainDb()` is a no-op T050/T052
stub, so gain remains 1.0. `git diff --exit-code HEAD` on both the processor and test
confirmed they are unchanged. This is outside the metadata brief and was not fixed,
ignored, or weakened. The required codecs/metadata/build command passes independently.

The optional external `go test -json ./...` also found two existing upstream MP4
high-resolution failures (`TestMP4DifferentialFFmpegFourccs/ipcm_hi-res` and
`fpcm_hi-res`: 48000 reported versus ffprobe 96000). It recorded 3840 passed test
events and the failing parent/test package. These are pinned source behavior to
investigate in step 3, not grounds to alter this step's oracle.

## Oracle matches and deviations

Of 123 corpus records, **120 match exactly**, including error classes; **3 have the
explicit bounded-probe delta in PATCH-2**. There are **0 unexplained mismatches**.
Both picture scan and FULL projections are checked on all successful records.
Hashes and total samples are asserted against the pinned public API supplement.
No expected upstream JSON was edited to obtain parity.

All deviations have tests and are recorded in `metadata/SISTRUM-PATCHES.md`:
METADATA_ONLY/NONE/lazy FULL and streamed picture hashes; fixed property probes and
no audio-body scan; JVM resource caps/typed errors; extra fields absent from CLI
JSON. Native metadata blocks are immutable inspection summaries rather than editors.

## Benchmark

Per-file measurements are in `step1-benchmark.tsv`, including every malformed/error
input. Median of those per-file medians by reference format, on this host:

| Format | Files | Parse time | Allocated bytes |
|---|---:|---:|---:|
| MP3 (includes malformed tags) | 105 | 7.59 µs | 2352 |
| WAV | 4 | 136.88 µs | 9764 |
| AIFF/AIFF-C | 4 | 468.52 µs | 17528 |
| Unsupported/invalid format errors | 10 | 3.08 µs | 1976 |

Large art intentionally costs more than the format median. The 1.1 MB normal APIC
vector took 1.481 ms with 41,712 allocated bytes; whole-tag and per-frame unsync
vectors took 13.790/15.193 ms with approximately 3.16 MB cumulative allocation.
Their largest source request remained 8192 bytes: allocation totals include repeated
small buffers and sparse checkpoints, not a full image array retained by scan mode.

These are warm-cache measurements, 25 warmups and 101 trials per file, with medians
from `System.nanoTime` and HotSpot thread allocation counters. File opening is
outside the measured call. The committed TSV records JDK/architecture. No hard
performance threshold was specified.
