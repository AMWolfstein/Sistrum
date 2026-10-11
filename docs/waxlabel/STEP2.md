<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 2 — FLAC and Ogg Vorbis comments

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.

## Result

Native FLAC and Ogg Vorbis/Opus/FLAC now project canonical multi-value comments,
aliases and number pairs, native block descriptions, audio properties and padding,
native/base64 cover metadata and hashes, LRC synced lyrics, CHAPTER chapters and
warnings. Legacy leading ID3 and trailing ID3v1 around FLAC remain non-authoritative.
Ogg handles continued header packets, native FLAC pictures, Opus output gain,
pre-skip, shared header/audio pages, chains and trailing bytes. Source views stream
art without retaining whole decoded images or base64 image strings.

No writer, decoder, player, scanner or Android integration was added. Production
code still imports only the codecs `RandomAccessSource` interface. Existing MIT
notices remain packaged, and every added file carries attribution.

## Validation

- Metadata: **347 tests**, zero failures/errors/skips: 123 step-1 oracle cases,
  16 existing contracts, 200 step-2 oracle cases and eight Vorbis contracts.
- Codecs: **1477 tests**, zero failures/errors/skips; tests explicitly rerun.
- `:androidApp:assembleDevDebug`: successful.
- Pinned upstream `internal/vorbis`, `internal/flac`, `internal/ogg`: **121 passed
  test events**, zero failures.
- Step-2 oracle: **200 records**, including 146 typed-error inputs. **197 exact
  matches**, three precise intentional frame-scan differences; **zero unexplained
  mismatches**. Every successful record compares METADATA_ONLY and FULL, total
  samples and cover SHA-256 against the pinned supplementary public read API.
- **881 manifest entries** verify all fetched source/tests/testdata and owned
  inputs. All eight upstream audio fixtures in these formats are included.
- Audio guard and `git diff --check`: pass. All audio/image inputs remain external.

Commands:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 2
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned upstream source checkout:
go test -json ./internal/vorbis ./internal/flac ./internal/ogg
# In Sistrum, with both test tasks explicitly forced:
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevDebug
./gradlew :metadata:test
./gradlew :metadata:benchmark -PwaxlabelStep=2
python3 scripts/check-no-audio.py
git diff --check
```

Full validation and generation logs are local-only under
`~/.cache/sistrum-waxlabel-oracle/step2-*.log`; upstream test events are in
`step2-upstream-tests.jsonl`. A final metadata test also verified tightened name
bounds and source-error propagation. No existing test expectations were weakened.

## Deviations

PATCH-1's requested picture policy applies to native and base64 covers; FULL source
views receive fresh budgets for repeatable lazy loading. PATCH-3's allocation and
breadth limits also bound blocks, packet/region descriptors and comment names.
PATCH-4 retains samples/hashes supplied by the pinned read API and the API exposes
Ogg native descriptor units (the CLI JSON omits units).

New PATCH-5 omits encoded FLAC frame scanning to meet the no-audio-payload rule.
Two truncated files omit only `truncated-audio`; one junk-tail file omits the native
tail block and warning and includes that extent in average bitrate. Exact filenames,
upstream premises, allowed field deltas and guarded-source tests are recorded in
[the patch log](../../metadata/SISTRUM-PATCHES.md). These are intentional differences,
not claimed as exact matches. Step-1's three bounded MP3-probe differences remain.

## Benchmark

[step2-benchmark.tsv](step2-benchmark.tsv) records median parse nanoseconds and
allocated bytes **for every one of the 200 inputs**, including errors. JDK 21.0.12.1,
amd64; warm filesystem cache, open source, METADATA_ONLY; 25 warmups and 101 measured
reads per file. Final run was taken after the codec test workload completed. Picture
hashing is included; on-demand FULL loading and file opening are excluded.

The table summarizes the median of per-file medians within each cohort, rather
than a pooled trial distribution. Large art and lyric-cap files remain individually
visible in the TSV; these figures are measurements without a performance threshold.

| Format | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| FLAC | 26 | 222.545 | 29704 |
| Ogg FLAC | 8 | 294.025 | 40668 |
| Ogg Opus | 13 | 120.630 | 19200 |
| Ogg Vorbis | 7 | 157.470 | 33704 |
| Typed errors (all four formats) | 146 | 5.475 | 2296 |

## Files and remaining work

Added `Flac.kt`, `Ogg.kt`, `Vorbis.kt`, `RegionSource.kt` and two Vorbis test classes.
Updated metadata dispatch/picture reuse/native units, Gradle oracle inputs and
benchmark step selection, shared test fixture loading, the generation/verification
harness, manifest, oracle references, README, oracle instructions and patch log.
Added this report and the per-file benchmark snapshot. All changes are within the
metadata port and its supporting oracle documentation/tools.

Steps **3–8 remain**: MP4, APEv2, full WAV/AIFF, ASF, Matroska and DSF/DFF additions.
This is the clean step-2 boundary; no partial step-3 implementation is included.
