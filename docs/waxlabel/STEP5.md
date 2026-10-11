<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 5 — WAV/RF64/BW64 and AIFF/AIFF-C read paths

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
Implemented on `port/waxflow`; steps 6–8 remain undone.

## Result

WAV reads LIST/INFO with mapped and unmapped native items, present-empty and
multi-value text, slash track pairs, Latin-1 fallback, malformed-pad recovery,
unparsed-byte warnings and embedded ID3 precedence. RF64/BW64 uses ds64 sizes,
ordered repeated table identifiers and trusted container boundaries. Shared IFF
framing handles recovery from short container declarations, streaming sentinel
sizes, truncated/oversized chunks, duplicate tags and trailing regions.

Compressed WAV length uses fact/ds64 samples only when the pinned reader's bitrate
and nominal-duration checks accept them; PCM retains byte/block geometry. AIFF/C
native text and ID3, extended sample rates, fixed sample widths, IMA/MACE packet
counts, SSND alignment, unknown layouts and overstated COMM counts are covered.
Native family lookups and ds64 sizes are indexed, including upstream Unicode
folding/whitespace semantics. Chapters and synced lyrics remain embedded-ID3 data.

The pinned reader keeps bext, iXML, cue/adtl, AIFF marker/comment/instrument chunks
opaque. Kotlin reports their native summaries without reading those bodies or
adding unsupported canonical fields/native chapters. Audio bodies are skipped.
METADATA_ONLY streams picture metadata/hashes, NONE skips images, and FULL loads
fresh bytes lazily. No writer, player, scanner, Android or native integration.

## Validation

- Metadata: **931 tests**, zero failures/errors/skips: 690 previous cases,
  232 step-5 oracle cases and nine IFF contracts.
- Codecs: **1477 tests**, zero failures/errors/skips, explicitly rerun.
- `:androidApp:assembleDevQa`: successful.
- Step-5 corpus: **232 exact matches**, 210 successful projections and 22 typed
  errors; **zero intentional or unexplained JSON differences**. Both picture modes
  compare ordered tags, native blocks, properties, pictures, chapters, lyrics,
  warnings and provenance. Total samples and picture SHA-256 use the pinned API
  supplement as in prior steps.
- All seven upstream WAV/AIFF-family testdata files are compared, including
  sample-rf64.wav, alongside 225 owned inputs. **1421 manifest entries** verify
  fetched source/tests/testdata and owned files. All media remain external.
- Payload guards cover PCM/compressed WAV, RF64/BW64, AIFF/C, SSND alignment,
  large bext/non-INFO LIST and other ancillary chunks. Tests cover accepted/rejected
  fact counts, empty/duplicate/Latin-1 INFO, scan/NONE/lazy FULL art, one-byte reads,
  typed I/O errors, limits and a 2000-item native-family flood. Bytewise truncations
  through 2048 bytes on six inputs and 1500 seeded mutations never escape read().
- Pinned upstream internal WAV/AIFF/IFF tests: **46 passed test events**,
  zero failures/skips, explicitly rerun.
- Pinned upstream WAV/AIFF/RF64/BW64 format tests: **227 passed test events**,
  zero failures/skips, explicitly rerun. Upstream writer tests execute only in the
  external checkout; their write paths are not ported.
- Manifest verification, audio guard and `git diff --check`: pass.

Commands:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 5
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned upstream checkout:
go test -count=1 -json ./internal/wav ./internal/aiff ./internal/iff
go test -count=1 -json ./tests -run 'WAV|Wav|RF64|BW64|AIFF|Aiff|AIFC|Riff|RIFF|IFF'
# In Sistrum:
./gradlew :metadata:compileKotlin
./gradlew :metadata:test --tests '*IffOracle*'
./gradlew :metadata:test
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :metadata:test --rerun
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa
./gradlew :metadata:benchmark -PwaxlabelStep=5
python3 scripts/check-no-audio.py
git diff --check
```

Initial parity checks found the exact truncated-audio warning wording and the
short-SSND distinction between property geometry and audio extent; both were fixed
in Kotlin. A new accepted-fact contract initially used a vector whose nominal byte
rate correctly made its count implausible. That rejection vector/reference stays
unchanged; separate accepted-count WAV/RF64 vectors now test the intended behavior.
No expected oracle values were edited to conceal a mismatch, and no existing test
was weakened, skipped or disabled. Final full tests/build and combined checks pass.
Logs remain local-only as step5-*.log and step5-upstream-*.jsonl in the external cache.

## Deviations

There are **no new oracle JSON deltas**. Existing PATCH-1 picture policy and PATCH-3
resource bounds apply. The port reads only structural ds64/fmt/fact/COMM/SSND
fields and relevant tag bodies, skipping upstream fingerprint/rewrite-grading
reads and opaque ancillary bodies. See [the patch log](../../metadata/SISTRUM-PATCHES.md)
for guards and the smaller caller element cap on ds64 entries.

## Benchmark

[step5-benchmark.tsv](step5-benchmark.tsv) records median parse nanoseconds and
allocated bytes for every one of the 232 inputs. JDK 21.0.12.1, amd64; warm filesystem
cache, open source, METADATA_ONLY; 25 warmups and 101 trials per file. Parsing and
hashing are included; opening files and lazy FULL loading are excluded. The
standalone run followed the test workloads; no threshold was specified.
The summary groups by successful container subformat and reports the median of
per-file medians, with typed errors separate.

| Container | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| AIFC | 26 | 27.936 | 4600 |
| AIFF | 44 | 27.716 | 4928 |
| BW64 | 1 | 974.649 | 120608 |
| RF64 | 11 | 21.251 | 5376 |
| Typed errors | 22 | 12.186 | 3280 |
| WAV | 128 | 35.171 | 4728 |

## Files changed and remaining work

- Completed `Iff.kt`; added `IffWalk.kt` and `IffProperties.kt` in `:metadata`.
- Added `IffOracleParityTest.kt` and `IffReadContractsTest.kt`.
- Updated format dispatch for RF64/BW64, Gradle oracle inputs, README,
  patch log and notice source list.
- Extended the oracle script with external step-5 generation; added the oracle
  JSON, manifest entries, oracle documentation, this report and benchmark snapshot.

Changes stay within the metadata read port and oracle support. No unrelated files,
branch switches, main changes or pushes. Steps **6–8 remain** at the step-5 boundary.
