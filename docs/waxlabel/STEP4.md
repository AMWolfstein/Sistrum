<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 4 — APEv2 read paths

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
Implemented on `port/waxflow`; steps 5–8 remain undone.

## Result

APEv1/v2 tags now read on Monkey's Audio, WavPack and Musepack, with aliases,
ordered multi-value canonical tags, number pairs, native items, provenance,
Latin-1 fallback, cover metadata/hashes and warnings. APE tags on MP3 remain
legacy-only: ID3 is authoritative and APE covers are not projected.

Monkey's Audio modern/legacy headers, WavPack block geometry, 40-bit sample
counts, custom sample rates, multichannel/float/DSD properties, and Musepack
SV7/SV8 stream headers are covered. Musepack CT chapters include title tags,
malformed packet warnings and valid SO/ST seeks; leading ID3 stays legacy.
No APE synced-lyrics/chapter convention absent from the pinned reader is invented.

Reads skip encoded audio bodies. Covers stream in METADATA_ONLY, are skipped in
NONE, and load fresh bytes lazily in FULL. Fixed trailer signatures discover tags;
Musepack seeks must resolve to verified packet boundaries. No writer, player,
scanner, Android or native-code integration was added. MIT notices remain packaged
and their source list now covers all four implemented steps.

## Validation

- Metadata: **690 tests**, zero failures/errors/skips: 502 previous tests,
  180 step-4 oracle cases and eight new contracts.
- Codecs: **1477 tests**, zero failures/errors/skips, explicitly rerun.
- `:androidApp:assembleDevQa`: successful.
- Step-4 corpus: **180 records**, 140 successes and 40 typed errors;
  **179 exact matches**, one precisely asserted safety difference;
  **zero unexplained mismatches**. Both METADATA_ONLY and FULL compare the CLI
  projection, public total samples and picture SHA-256 against the pinned reader.
- All five pinned upstream APE/WavPack/Musepack testdata files are included,
  alongside 175 owned inputs. **1196 manifest entries** verify fetched upstream
  source/tests/testdata and owned vectors. Inputs remain external and local-only.
- Guarded sources reject audio bodies in modern/legacy Monkey's Audio, WavPack
  sample-rate/DSD subblocks, and Musepack AP packets. They check genuine and hostile
  chapter seeks, ignored MP3 APE covers, large binary items, streamed/lazy picture
  policies, one-byte reads, typed I/O errors and resource limits. Bytewise
  truncations through 2048 bytes and 1000 seeded mutations do not escape read().
- Pinned upstream internal APE/Monkey's Audio/WavPack/Musepack/MP3 tests:
  **67 passed test events**, zero failures/skips, explicitly rerun.
- Pinned upstream `./tests -run 'APE|WavPack|Musepack'`: **81 passed test events**,
  zero failures/skips, explicitly rerun. These external upstream tests include
  writer checks; only their read behavior is ported.
- Manifest verification, audio guard and `git diff --check`: pass.

Commands:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 4
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned upstream checkout:
go test -count=1 -json ./internal/ape ./internal/apen ./internal/wavpack ./internal/musepack ./internal/mp3
go test -count=1 -json ./tests -run 'APE|WavPack|Musepack'
# In Sistrum:
./gradlew :metadata:compileKotlin
./gradlew :metadata:test --tests '*ApeOracle*'
./gradlew :metadata:test
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :metadata:test --rerun
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa
./gradlew :metadata:benchmark -PwaxlabelStep=4
python3 scripts/check-no-audio.py
git diff --check
```

Initial new contract failures were corrected: packet guards stop at SE rather
than treating APETAGEX as an audio packet, trailer guards permit the exact existing
ID3v1 signature probe, and the lenient item-cap test uses single-value items so it
does not independently exceed the total-value resource cap. Oracle expected values
and existing restrictions remain intact; no test was skipped or disabled.
The existing MP3 audio guard additionally permits only the new eight-byte APE
footer signature probe. The final combined tests/build pass; codec rerun evidence
is retained from the full run. Logs remain in the external cache as step4-*.log
and step4-upstream-*.jsonl.

## Deviations

PATCH-7 replaces broad audio windows with structural reads and validates Musepack
chapter seeks. `ape-chapter-audio-alias.mpc` exposes a fake 25 ms chapter titled
LEAK and a 51-byte CT native block in upstream. Kotlin omits both; every other
field matches. Reference JSON remains unmodified and the exact delta is asserted.
See [the patch log](../../metadata/SISTRUM-PATCHES.md).

PATCH-1 picture policy and PATCH-3 resource caps extend to APE-family readers.
The port does not preserve opaque native binary payloads for rewriting; native
blocks are read-only summaries as already documented.

## Benchmark

[step4-benchmark.tsv](step4-benchmark.tsv) records median parse nanoseconds and
allocated bytes for every one of the 180 files. JDK 21.0.12.1, amd64; warm filesystem
cache, open source, METADATA_ONLY; 25 warmups and 101 trials per file. Parsing and
hashing are measured; file opening and lazy FULL loading are excluded. No threshold
was specified. The grouped summary is the median of per-file medians.

| Format | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| MP3 | 22 | 14.100 | 3120 |
| Monkey's Audio | 60 | 202.793 | 49248 |
| Musepack | 30 | 286.346 | 49712 |
| WavPack | 28 | 201.793 | 29676 |
| error | 40 | 8.575 | 2600 |

## Files changed and remaining work

- Added `Ape.kt`, `ApeContainers.kt`, `Musepack.kt`, `ApeOracleParityTest.kt` and
  `ApeReadContractsTest.kt` in `:metadata`.
- Updated metadata dispatch/MP3 trailers, Gradle oracle inputs, the existing MP3
  guard's fixed APE detection probe, README, patch log and attribution source list.
- Extended the oracle script with step-4 external generation; added the oracle
  JSON, manifest entries, oracle documentation, this report and benchmark snapshot.

All changes stay within the metadata port and its oracle support. No unrelated
application code, branch switches, main changes or pushes. Steps **5–8 remain**
at the step-4 boundary.
