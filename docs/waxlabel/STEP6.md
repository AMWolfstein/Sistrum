<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 6 — ASF/WMA read path

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
Implemented on `port/waxflow`; steps 7–8 remain undone.

ASF/WMA now reads content descriptions, extended and nested metadata/library
objects, canonical mappings and native source families, numeric/UTF-16 values,
WM/Picture and marker chapters. Properties cover WMA variants, lossless depth,
WAVE codec identifiers, duration/preroll, samples and bitrate. Opaque objects,
binary descriptors and encoded Data packet bodies are skipped. No unsupported
synced-lyrics convention, writing, player or scanner integration is added.

## Validation

- Metadata: **1081 tests**, zero failures/errors/skips: 931 previous cases,
  142 ASF oracle cases and eight read contracts.
- Codecs: **1477 tests**, zero failures/errors/skips, explicitly rerun.
- `:androidApp:assembleDevQa`: successful; final combined checks pass.
- Corpus: **142 cases**, 130 successful projections and 12 typed errors;
  **140 exact matches, two tested deviations, zero unexplained mismatches**.
  METADATA_ONLY and FULL compare tags, native blocks, properties, pictures,
  chapters, lyrics, warnings and provenance; the pinned API supplement compares
  total samples and picture SHA-256.
- All three upstream ASF/WMA testdata files and 139 owned inputs are external.
  **1560 manifest entries** verify fetched source/tests/testdata and owned files.
- Guards reject encoded packets, opaque bodies and large binary descriptor reads.
  Tests cover picture modes, one-byte reads, typed source faults, resource limits,
  Unicode families, lossless depth, marker ordering, bytewise truncations through
  2048 bytes on five inputs and 1250 seeded mutations.
- Pinned upstream mapping tests: **28 passed test events**, zero failures/skips.
  `internal/asf` has no unit tests; the same command includes that package.
- Pinned upstream ASF/WMA format tests: **36 passed test events**, zero failures/skips.
- Manifest verification, audio guard and `git diff --check`: pass.

Commands run:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 6
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned checkout:
go test -count=1 -json ./internal/asf ./internal/mapping
go test -count=1 -json ./tests -run 'ASF|Asf|WMA|Wma'
# In Sistrum:
./gradlew :metadata:test --rerun
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa
./gradlew :metadata:benchmark -PwaxlabelStep=6
python3 scripts/check-no-audio.py
git diff --check
```

Initial parity checks corrected picture MIME handling to match the public upstream
normalization. A new contract initially assumed different object classes implied
different provenance sources; the pinned source names determine selection instead.
That premise was corrected while unchanged oracle JSON continued to be compared.
No existing test was weakened, skipped or disabled. Logs remain external as
step6-*.log and step6-upstream-*.jsonl.

## Deviations

[PATCH-8](../../metadata/SISTRUM-PATCHES.md) lists both exact JSON differences:
a fake Data header inside known packet bytes is not probed (native Data size zero
and no-audio warning); unsigned 4294967295 Hz clamps to the public Int maximum,
while total samples still match. Differential tests assert every unaffected field.
Existing streamed/default, NONE and lazy FULL picture policies apply. Structural
reads replace upstream whole-header allocation/fingerprinting; resource limits
bound the graph. Optional Data-locator source faults return typed IO under the
existing source-failure policy rather than being swallowed as upstream does.

## Benchmark

[step6-benchmark.tsv](step6-benchmark.tsv) contains median parse time and allocation
for every corpus file. JDK 21.0.12.1, amd64, warm filesystem cache, open source,
METADATA_ONLY; 25 warmups and 101 trials per file. Includes parsing and hashes;
excludes file opening and lazy FULL loads. Standalone run followed test workloads.
No threshold was specified. Summary is the median of per-file medians by reported
codec, with typed errors separate; all successful containers are ASF.

| Codec | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| A-law | 1 | 19.300 | 4056 |
| AAC | 1 | 15.621 | 4088 |
| ADPCM | 1 | 15.811 | 4088 |
| IEEE float | 1 | 15.621 | 4088 |
| IMA ADPCM | 1 | 15.790 | 4088 |
| MP2 | 1 | 19.440 | 4056 |
| MP3 | 1 | 19.550 | 4056 |
| PCM | 1 | 15.750 | 4088 |
| PCM (extensible) | 1 | 19.440 | 4056 |
| Typed errors | 12 | 8.556 | 2952 |
| Unknown | 11 | 20.680 | 4064 |
| WAVE format 0x0000 | 1 | 69.002 | 4840 |
| WAVE format 0x1234 | 1 | 62.211 | 4824 |
| WMA Lossless | 7 | 16.491 | 4264 |
| WMA Pro | 1 | 14.251 | 4056 |
| WMA Voice | 1 | 15.920 | 4088 |
| WMA v1 | 3 | 82.151 | 20592 |
| WMA v2 | 95 | 27.871 | 6312 |
| mu-law | 1 | 19.591 | 4056 |

## Files changed and remaining work

- Added `Asf.kt` and `AsfTables.kt`; updated metadata dispatch.
- Added ASF parity and bounded-read contracts; shared oracle projection now uses
  Go-compatible simple Unicode folding for cardinality; earlier cases still pass.
- Updated Gradle oracle inputs, README, patches and notice source list.
- Extended external oracle generation; added reference JSON, manifest entries,
  oracle documentation, this report and benchmark snapshot.

All changes belong to the read port and oracle support. No unrelated files,
branch switches, main changes or pushes. Steps **7–8 remain**.
