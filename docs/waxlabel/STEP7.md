<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 7 — Matroska/MKA/WebM read path

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
Implemented on `port/waxflow`; step 8 remains undone.

Matroska now reads scoped SimpleTags, Info.Title, nested native tag trees,
attachments, default-edition chapters and audio properties. Canonical projection
prefers album scope, retains within-scope values and suppresses cross-scope echoes.
Native summaries and provenance preserve album/track/edition/chapter scope.
Chapters retain language/IETF language, hidden/disabled flags, explicit ends and
stable nanosecond ordering before the public millisecond conversion.

Properties cover first-audio-track geometry, codec/profile names, AAC private
configuration and output sampling frequency. Duration is segment-based; bitrate
uses the Cluster span only for single-audio/no-video files, as upstream does.
The pinned public API provides no Matroska sample count or Opus private gain, so
those remain zero. No unsupported synced-lyrics convention is invented.

Known Cluster bodies, opaque objects, TagBinary, non-image attachments and
non-AAC CodecPrivate are skipped. Unknown-size streamed Clusters walk block headers
only to reach trailing metadata. Covers stream hashes by default; NONE skips data
and FULL loads fresh bytes lazily. No writer, player, scanner or Android integration.

## Validation

- Metadata: **1258 tests**, zero failures/errors/skips: 1081 prior cases,
  170 step-7 oracle cases and seven bounded-read contracts.
- Codecs: **1477 tests**, zero failures/errors/skips, explicitly rerun.
- `:androidApp:assembleDevDebug`: successful.
- Corpus: **170 exact matches**, 160 successful projections and ten typed errors;
  **zero intentional or unexplained JSON differences**. METADATA_ONLY and FULL
  compare tags, native blocks, properties, picture metadata, chapters, lyrics,
  warnings and provenance, plus public samples and picture SHA-256.
- All four upstream fixtures and 166 owned inputs remain external; **1726 manifest
  entries** verify all fetched source/tests/testdata and owned files.
- Guards reject known Cluster bodies, streamed block bodies, opaque/binary and
  non-image payloads and private data beyond AAC's 32-byte prefix. Contracts cover
  pictures, stable ordering, scope conflicts, one-byte reads, typed source faults,
  breadth/depth/allocation/read limits, 70 nested masters, every truncation through
  2048 bytes on five seeds and 1500 seeded mutations.
- Pinned upstream Matroska/mapping tests: **107 passed test events**, no failures/skips.
- Pinned upstream Matroska/WebM format tests: **174 passed test events**, no failures/skips.
  Upstream write tests execute only in the external checkout; writing is not ported.
- Manifest verification, audio guard and `git diff --check`: pass.

Commands run:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 7
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned checkout:
go test -count=1 -json ./internal/matroska ./internal/mapping
go test -count=1 -json ./tests -run 'Matroska|WebM|MKA'
# In Sistrum:
./gradlew :metadata:compileKotlin
./gradlew :metadata:test --tests '*MatroskaOracle*'
./gradlew :metadata:test --tests '*MatroskaReadContracts*'
./gradlew :metadata:test --rerun
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevDebug
./gradlew :metadata:benchmark -PwaxlabelStep=7
python3 scripts/check-no-audio.py
git diff --check
```

Initial parity checks corrected chapter ends beyond segment duration and the exact
warning escape of invalid key bytes. A new guard initially located a block by an
unscoped byte match inside a track header; it now finds the block within the
Cluster range. A fault offset initially lay beyond all reads in an AAC vector;
the test now injects at the Cluster header and asserts IO there. Oracle JSON stays
unchanged; no existing test was weakened, skipped or disabled. Logs remain external
as step7-*.log and step7-upstream-*.jsonl.

## Deviations

There are no new oracle JSON differences. Existing PATCH-1 picture policy and
PATCH-3 limits/source-fault handling apply. EBML headers use their actual widths
rather than upstream lookahead; write-only raw captures and fingerprints are
omitted. Unknown-size Cluster header traversal additionally consumes the caller's
breadth budget; known Cluster siblings remain exempt. Optional informational read
faults return IO rather than being swallowed. These policies have guarded-source
and resource tests in [the patch log](../../metadata/SISTRUM-PATCHES.md).

## Benchmark

[step7-benchmark.tsv](step7-benchmark.tsv) records median parse time and allocation
for every corpus file. JDK 21.0.12.1, amd64, warm filesystem cache, open source,
METADATA_ONLY; 25 warmups and 101 trials per file. Includes parsing and hashes;
excludes file opening and lazy FULL loads. The standalone run follows test workloads;
no performance threshold was specified. Summary groups successful container types
and reports the median of per-file medians, with typed errors separate.

| Container | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| Matroska | 155 | 92.380 | 20672 |
| Typed errors | 10 | 13.615 | 3460 |
| WebM | 5 | 238.070 | 61816 |

## Files changed and remaining work

- Added `Ebml.kt`, `Matroska.kt`, `MatroskaTags.kt` and `MatroskaChapters.kt`.
- Added Matroska oracle parity and bounded-read contracts; updated dispatch and
  Gradle oracle inputs.
- Extended immutable chapter fields and provenance scope with compatible defaults;
  extended JSON projection. Reused the existing AAC decoder via internal visibility
  in `Mp4Properties.kt`, avoiding a second decoder implementation.
- Updated README, notices, patch log and oracle documentation; added external vector
  generation, reference JSON, manifest hashes, this report and benchmark snapshot.

All changes belong to metadata reading and oracle support. No unrelated files,
branch switches, main changes or pushes. Step **8, DSF/DFF, remains**.
