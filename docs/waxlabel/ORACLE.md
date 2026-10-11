<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# WaxLabel read oracle

Repository: https://github.com/AMWolfstein/WaxLabel (fork of ColeSpringer/WaxLabel).
Exact commit: `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
License: MIT, Copyright (c) 2026 Cole Springer; copied verbatim into
`metadata/THIRD-PARTY-NOTICES`, also recorded in the repository notices.

`Metadata.read(RandomAccessSource, ReadOptions)` lives in the pure-JVM `:metadata`
module. It is independent of Android and uses `:codecs` only for the existing source
contract. Step 1 is the ID3/MP3 and embedded-ID3 boundary. There is no writer,
player integration, or scanner integration.

## Reproduce

Use Go 1.26 or a Go launcher with automatic toolchain download, ffmpeg with libmp3lame,
and Python with mutagen **1.47.0**. Cache root defaults to
`~/.cache/sistrum-waxlabel-oracle`; override with `SISTRUM_WAXLABEL_ORACLE_DIR`.
The harness rejects cache paths within this repository.

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate
python3 scripts/waxlabel-oracle.py --verify
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa :metadata:benchmark
```

The Python path above reuses this session's existing corpus virtualenv; another
environment with the exact mutagen version is equivalent. All audio, image inputs,
upstream tests/testdata/source, Go binaries/helpers, and source archives remain
external. Git stores only the harness, text manifests, reference JSON, Kotlin
source/tests, notices, and benchmark report. The generated owned audio is silence;
artwork and adversarial metadata are produced deterministically by the harness.

The harness fetches the exact commit, checks an existing checkout for the pin and
local edits, checks existing extracted source against the committed manifest, and
builds the pinned `./cmd/waxlabel` with `CGO_ENABLED=0 go build -trimpath`. It executes
`waxlabel dump --native --json` for each format input. For errors it records CLI exit
3 (`UNSUPPORTED_FORMAT`) or 4 (`INVALID_DATA`), never an invented reference document.
It strips only the absolute CLI filename from successful JSON.

`oracle-step1.json` preserves the reference output. A tiny external Go helper,
compiled against the exact same source using a local module replacement, supplies
only total samples and SHA-256 of each upstream picture's bytes: neither exists in
CLI JSON. Kotlin comparison retains ordered tags/values, native blocks, properties,
picture metadata, chapters, lyrics, warnings, provenance, legacy-only keys, and
cardinality markers. The declared bounded-probe exceptions are separately asserted
rather than silently normalized away; see `metadata/SISTRUM-PATCHES.md`.

`corpus-manifest.tsv` hashes **every** fetched source/test/testdata file and every
owned input, including standalone PNG generation inputs, with SHA-256. Verification
rejects missing, changed, or additional source/owned files. Gradle verifies before
metadata tests and benchmarks and declares the manifest/reference JSON as inputs.
Existing codec corpus checks continue to run as well. No upstream Go source is
copied into Git or modified to obtain different reference behavior.

## Step-1 coverage

The manifest includes all upstream formats and tests for subsequent steps; the
step-1 comparison includes all four MP3 fixtures, ordinary WAV fixtures, all AIFF/
AIFF-C fixtures, and generated ID3 vectors. `sample-rf64.wav` is retained and hashed
but reserved for step 5. Upstream APE-on-MP3 vectors will enter step 4.

Owned cases include mutagen 2.3/2.4 with multi-value text, Arabic, MusicBrainz and
custom keys, comments/lyrics, chapter titles and SYLT; hand-generated 2.2 PIC/unknown
frames, signed numbering, valid and malformed dates/genres, UTF-16 byte-order carry,
invalid UTF-8 runs, technical comments, opaque/compressed frames, grouping/DLI,
unsynchronization, extended headers, footer, invalid keys/pictures, legacy-only and
stacked/rejected ID3v1, partial headers and truncated tags, multiple art signatures,
external art links, large art, and ID3 inside WAV/AIFF.

In-memory fault/mutation tests use these hashed inputs without writing additional
files. They check every byte truncation through the first 2048 bytes, 1000 seeded
malformations, source failures/short reads, immutability, amplification/allocation
limits, picture loading policy, and guarded ranges that reject sample payload reads.

## Benchmark

`:metadata:benchmark` measures each of the step-1 files on a warm filesystem cache
with an open `FileChannelSource`: 25 warmups, 101 trials, median nanoseconds and
median per-thread allocated bytes from HotSpot's `ThreadMXBean`. File opening and
corpus verification are outside the measured call; parsing, document construction,
metadata reads and picture hashing are inside. FULL byte loading is intentionally
outside this scan benchmark. Errors and malformed files are included and named;
none are reported as successful metadata parses.

Generated output: `metadata/build/reports/metadata-benchmark.tsv`.
Recorded step-1 snapshot: `docs/waxlabel/step1-benchmark.tsv`.
These are per-file measurements, not a hard performance threshold; host/JIT/I/O
noise means a new run can differ.

## Changing the oracle pin

A pin change requires a reviewed source/license diff and explicit pin update in
this document and the harness. Fetch a clean external source for the new commit,
regenerate manifests/references/owned files, update source attribution, port/test
changed semantics, rerun the full required checks and benchmarks, and explain
intentional differences in `metadata/SISTRUM-PATCHES.md`. Do not update reference
JSON or hashes merely to hide a Kotlin mismatch.
