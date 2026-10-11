<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# WaxLabel read oracle

Repository: https://github.com/AMWolfstein/WaxLabel (fork of ColeSpringer/WaxLabel).
Exact commit: `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
License: MIT, Copyright (c) 2026 Cole Springer; copied verbatim into
`metadata/THIRD-PARTY-NOTICES`, also recorded in the repository notices.

`Metadata.read(RandomAccessSource, ReadOptions)` lives in the pure-JVM `:metadata`
module. It is independent of Android and uses `:codecs` only for the existing source
contract. Steps 1–7 cover ID3/MP3, FLAC/Ogg Vorbis/Opus/FLAC, MP4/M4A/QuickTime,
APE-family containers, WAV/RF64/BW64/AIFF/AIFF-C, ASF/WMA and Matroska/MKA/WebM. There is no writer,
player integration, or scanner integration.

## Reproduce

Use Go 1.26 or a Go launcher with automatic toolchain download, ffmpeg with
libmp3lame/libvorbis/libopus/FLAC encoders,
and Python with mutagen **1.47.0**. Cache root defaults to
`~/.cache/sistrum-waxlabel-oracle`; override with `SISTRUM_WAXLABEL_ORACLE_DIR`.
The harness rejects cache paths within this repository.

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 2
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 3
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 4
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 5
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 6
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 7
python3 scripts/waxlabel-oracle.py --verify
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevDebug
./gradlew :metadata:benchmark -PwaxlabelStep=2
./gradlew :metadata:benchmark -PwaxlabelStep=3
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

`oracle-step1.json` through `oracle-step7.json` preserve the reference output. A tiny external Go helper,
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
and compared in step 5. APE-on-MP3 vectors are compared in step 4.

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

## Step-2 coverage

All eight pinned `testdata` FLAC/Ogg Vorbis/Opus/FLAC files are compared, alongside
owned ffmpeg silence encoded in each format, mutagen comments/art, and Opus output
gain written by the pinned WaxLabel CLI. Structural owned vectors cover aliases,
conflicts, slash pairs, malformed names/unseparated entries, invalid UTF-8, duplicate
comment blocks, padding, unknown blocks, native and base64 pictures (large, invalid,
linked, unrecognized bytes, over-byte-range type), stray leading ID3 and trailing
ID3v1, tag-only FLAC, damaged STREAMINFO/block framing, continued Ogg packets,
shared metadata/audio pages, chains, trailing junk, LRC offsets/line cap and chapter
ordering. Every generated file is hashed in the same external manifest.

The unmodified JSON includes the three frame-scan differences enumerated in
`metadata/SISTRUM-PATCHES.md`. Guarded source tests verify that no encoded frames or
Ogg audio bodies are read. Default picture hashing is streamed; NONE skips native
and encoded-comment image payloads. FULL loads are lazy and repeatable, including
fresh transformed-source budgets. Bytewise truncations through 2048 bytes and 1000
seeded mutations supplement the corpus cases without creating unmanifested files.

## Step-3 coverage

All seven pinned MP4/M4A/M4B/MOV testdata files are included: tags, chapters,
untagged AAC, QuickTime MP3 and three HE-AAC variants. Owned ffmpeg silence covers
AAC, ALAC, FLAC, PCM/float, fragmented MP4, mdta tags and chapter tracks; mutagen
1.47.0 adds multi-value text, freeforms, pairs, booleans and cover art. Structural
vectors exercise every mapped text atom, freeform case folds/aliases/foreign means,
invalid keys/UTF-8/types, unsigned integer widths, numeric genres, native inspection,
Nero v0/v1/v2/fractional/overflow/unordered chapters, conflicting and QT-only chapters,
classic multi-language udta text, indexed mdta and cover art, padding, extended/zero
sizes, malformed child boxes/sample tables, truncated files and edit durations.
Codec-config vectors cover ALAC/wave wrappers, dfLa STREAMINFO, sound entries
v0/v1/v2, mdhd v1, MPEG-4 descriptor errors, AAC Main/LC/SBR/PS/explicit frequency/
sync extension/denial/PCE/escaped AOT and MPEG audio object indications.

`oracle-step3.json` contains **146 records** (119 successful and 27 typed errors).
**144 exact matches**, two guarded chapter-title differences, zero unexplained
mismatches. PATCH-6 asserts the complete unchanged projection except for one title
in each hostile input. A chapter offset pointing at audio samples or file headers
must not make a bounded metadata read consume those bytes. METADATA_ONLY streams
covers, NONE skips their payloads, FULL loads lazily with fresh budgets. Contracts
check forbidden audio reads, chapter-title-only reads, short/error sources, explicit
limits, a 2000-item family-selection flood, bytewise truncations through 2048 bytes and 3000 seeded mutations.

The two known pinned upstream ffprobe differential failures for high-resolution
`ipcm`/`fpcm` remain upstream behavior: WaxLabel reports 48000 rather than 96000 Hz
for these ffmpeg files. Kotlin preserves that exact result; reference output and
upstream source/tests are unmodified.

## Step 4 — APEv2, Monkey's Audio, WavPack and Musepack

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 4
```

The same exact source pin is built and invoked with `dump --native --json`.
`oracle-step4.json` has 180 records: five pinned upstream testdata inputs and
175 owned vectors. Owned inputs combine mutagen 1.47.0 APEv2 tags, ffmpeg WavPack
silence and deterministic structural APE/WavPack/Musepack packets. Cases include
APEv1/v2 footer-only/header tags, aliases, multi-values, conflicting provenance,
legacy MP3/ID3v1, Latin-1 fallback, malformed items, flags, large covers/binary data,
modern/legacy Monkey's Audio, WavPack 40-bit counts/custom rates/DSD/multichannel,
Musepack SV7/SV8 leading ID3, silence, CT chapters and SO/ST seeks. Every fetched
source/test/testdata and owned file has a manifest SHA-256 in the external cache.

140 records parse successfully and 40 return typed errors. There are 179 exact
matches and one precisely asserted PATCH-7 safety delta: a corrupt Musepack seek
aliases encoded audio containing a fake chapter. Kotlin omits that chapter and its
native block, retaining all other fields. Reference JSON remains unmodified.
Successful records compare both picture modes, public total samples and SHA-256.
Guarded sources, one-byte reads, I/O failures, resource limits, all byte truncations
through 2048 bytes and 1000 seeded mutations verify bounded, nonthrowing reads.

## Step 5 — WAV/RF64/BW64 and AIFF/AIFF-C

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 5
```

The same pinned CLI builds and supplies unmodified `dump --native --json` output.
`oracle-step5.json` contains 232 records: all seven upstream WAV/AIFF-family
inputs (including sample-rf64.wav) and 225 owned vectors. Owned ffmpeg silence
covers PCM, float, IMA WAV, PCM AIFF, A-law and IMA AIFF-C. Structural vectors cover
all INFO mappings, empty/duplicate values, Latin-1, slash pairs, ID3 precedence,
pictures/lyrics/chapters, duplicate/corrupt ID3, missing INFO pads, unread tails,
container-size recovery/trusted boundaries, malformed/truncated/sentinel chunks,
opaque bext/iXML/cue/adtl/native blocks, compressed fact sanity, RF64/BW64/repeated
ds64 identifiers, extended sample rates and AIFF-C fixed/packet/unknown layouts.

All 232 records match exactly: 205 successful projections and 22 typed errors;
zero intentional or unexplained JSON differences. Pictures compare METADATA_ONLY
and FULL plus public total samples and SHA-256. Guards reject audio and opaque
bodies and test streamed/lazy/NONE image policies. Source faults, one-byte reads,
limits, a 2000-item family-selection flood, bytewise truncations through 2048 bytes and 1500 seeded mutations verify
nonthrowing bounded reads. All 1421 fetched/generated files have manifest hashes.
The pinned reader treats bext/iXML and native cue/marker chapters as opaque; no
additional canonical mapping is claimed.

## Step 6 — ASF/WMA

`oracle-step6.json` contains 142 records: three pinned upstream testdata files and
139 owned inputs; 130 successful projections and 12 typed errors. There are 140
exact matches and two precisely tested PATCH-8 differences, zero unexplained
mismatches. All 1560 external source/test/testdata and owned files have SHA-256.
The pinned CLI is built and its output remains unchanged.

Owned ffmpeg WMA v1/v2 silence and mutagen tags supplement structural vectors for
all mappings, suppressed/unknown keys, multi-values, source conflicts, numeric and
UTF-16 descriptors, Unicode folding, nested metadata, pictures and markers.
Cases cover lossless depth, WAVE codec identifiers, preroll/duration/bitrate/rate
limits, duplicate properties, opaque objects, corrupt Header/Data boundaries,
truncated descriptors, marker ordering and large binary/image values. Both
picture modes compare every public field, plus sample counts and image hashes.
Guards prohibit Data packet and opaque-object reads. NONE skips WM/Picture values;
default hashing streams and FULL loads lazily. Short reads, I/O faults, resource
limits, byte truncations through 2048 bytes and 1250 seeded mutations never throw.
See [STEP6.md](STEP6.md) for counts, benchmarks and exact deviations.

## Step 7 — Matroska/MKA/WebM

`oracle-step7.json` contains 170 records: all four pinned upstream testdata files
and 166 owned inputs; 160 successful projections and ten typed errors.
**170 exact matches, zero intentional or unexplained JSON differences.**
All 1726 fetched/generated source/test/testdata and owned files have manifest
SHA-256. The pinned CLI builds and supplies unmodified native JSON; the same
public-API supplement supplies sample counts and picture hashes.

Owned ffmpeg silence covers Opus/Vorbis WebM and FLAC/AAC/PCM MKA. Deterministic
EBML vectors cover mappings, scoped families, nested/binary SimpleTags, empty and
invalid UTF-8 text, statistics suppression, pairs, numeric/keyword/UID targets,
attachments/MIME/name gates, large images and opaque data, chapter editions,
flags/languages/nesting, stable submillisecond order, overflow and open ends.
Properties cover codec names/AAC private prefixes, output rates, invalid floats,
multiple/video tracks and duration geometry. Framing cases include unknown-size
Segments/Clusters, trailing/interleaved tags, repeated metadata, many Clusters,
truncations and malformed VINTs. Default and FULL compare every public field;
NONE and guarded sources enforce bounded payload skips. Source faults, one-byte
reads, breadth/depth/allocation/read limits, every truncation through 2048 bytes
on five seeds and 1500 mutations verify nonthrowing reads.
See [STEP7.md](STEP7.md) for test results, changed files and per-file benchmarks.

## Benchmark

`:metadata:benchmark` measures each file (step 1 by default, `-PwaxlabelStep=2`
for step 2, `-PwaxlabelStep=3` for step 3, `-PwaxlabelStep=4` for step 4, `-PwaxlabelStep=5` for step 5, `-PwaxlabelStep=6` for step 6, `-PwaxlabelStep=7` for step 7) on a warm filesystem cache
with an open `FileChannelSource`: 25 warmups, 101 trials, median nanoseconds and
median per-thread allocated bytes from HotSpot's `ThreadMXBean`. File opening and
corpus verification are outside the measured call; parsing, document construction,
metadata reads and picture hashing are inside. FULL byte loading is intentionally
outside this scan benchmark. Errors and malformed files are included and named;
none are reported as successful metadata parses.

Generated output: `metadata/build/reports/metadata-benchmark.tsv`.
Recorded snapshots: `docs/waxlabel/step1-benchmark.tsv` and
`docs/waxlabel/step2-benchmark.tsv` and `docs/waxlabel/step3-benchmark.tsv` and `docs/waxlabel/step4-benchmark.tsv` and `docs/waxlabel/step5-benchmark.tsv` and `docs/waxlabel/step6-benchmark.tsv` and `docs/waxlabel/step7-benchmark.tsv`.
These are per-file measurements, not a hard performance threshold; host/JIT/I/O
noise means a new run can differ.

## Changing the oracle pin

A pin change requires a reviewed source/license diff and explicit pin update in
this document and the harness. Fetch a clean external source for the new commit,
regenerate manifests/references/owned files, update source attribution, port/test
changed semantics, rerun the full required checks and benchmarks, and explain
intentional differences in `metadata/SISTRUM-PATCHES.md`. Do not update reference
JSON or hashes merely to hide a Kotlin mismatch.
