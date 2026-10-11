<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- Sistrum DSD additions; WaxLabel MIT and dsf-meta/dff-meta MIT/Apache-2.0; see notices. -->
# Step 8 — DSF/DFF read additions

Implemented on `port/waxflow`. This completes the eight ordered read-port steps.
DSF reads ID3 at the metadata offset; DFF reads DIIN DIAR/DITI and top-level ID3,
including ID3 before audio. ID3 wins canonical keys and DIIN fills missing keys;
conflicting DIIN values remain in provenance. Properties expose DSD codec, one-bit
depth, rate, channels, declared samples, duration and bitrate. Sample payloads and
opaque bodies are skipped. Default pictures stream hashes; FULL loads fresh bytes
lazily and NONE skips images. No writing, player or scanner integration.

## References and deviations

WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9` has no DSD reader. The step-8
reference is an explicit composite: unmodified dsf-meta **0.3.0** and dff-meta
**0.2.0** container results, preserved per row, plus pinned WaxLabel ID3 JSON from
an external tag-only WAV wrapper. Archive SHA-256 pins and source/license provenance
are in [ORACLE.md](ORACLE.md); Apache notices are copied into :metadata.

DSD native summaries, property projection, warnings and standardized error mapping
are adapter definitions, not upstream WaxLabel DSD JSON. The adapter extracts ID3
independently of Kotlin and uses `waxlabel dump --native --json` and the existing Go
picture-hash supplement. Canonical ID3 uses WaxLabel rather than Rust id3's partial
tag model. DIIN and ID3 before audio are tested Sistrum additions beyond dff-meta.

[PATCH-9](../../metadata/SISTRUM-PATCHES.md) documents pointer guards, resource limits,
Int sample-rate/bitrate clamps and unsigned/overflowing duration errors. DSF pointers
into fixed headers or declared audio are warned about without probing those bytes.
PROP and DIIN fields must fit their enclosing metadata region. DSD sample counts
remain authoritative even when physical audio is truncated; this follows Rust's
metadata reader. DST and DFF layouts beyond mono/stereo return INVALID_DATA as the
pinned reference does. Existing codec/player capabilities are unaffected.

## Validation

- Metadata: **1391 tests**, zero failures/errors/skips: 1258 prior cases,
  125 composite reference cases and eight bounded-read contracts.
- Codecs: **1477 tests**, zero failures/errors/skips, explicitly rerun.
- `:androidApp:assembleDevQa`: successful; final combined checks pass.
- Corpus: **125 matches** against the documented composite reference, 82 successful
  documents and 43 typed errors; **zero unexplained mismatches**. METADATA_ONLY and
  FULL compare tags, native blocks, properties, pictures/hashes, lyrics, chapters,
  warnings and provenance. Raw Rust results remain in the JSON for review.
- Both upstream fixtures (packaged 1kHz.dff and the DSF unit-test sweep) and 123 owned
  cases remain external. **1912 manifest entries** verify source/tests/testdata,
  original crate/test archives, owned vectors and extracted tag wrappers.
- Owned mutagen 1.47.0 vectors and structural inputs cover ID3v2.2/2.3/2.4, multi-value
  text, art/large art, lyrics/chapters, DIIN/ID3 precedence, duplicate/early tags,
  malformed tags, rates, channels, sample counts/bit-order fields, header/reserved/
  block validation, declared sizes, unsafe pointers, opaque chunks and truncations.
- Guards reject DSD audio, large opaque chunks and unknown DIIN bodies. Tests cover
  pointer alias rejection, streamed/NONE/lazy FULL art, short reads, typed I/O,
  breadth/read/tag limits, unsigned counts/overflow, bytewise cuts through 2048 bytes
  on five seeds and 1500 seeded mutations without writing unmanifested media.
- Pinned Rust crates: **two unit tests and five documentation tests passed**, zero
  failures/ignored tests. dsf-meta's test downloads its sweep archive into the
  external Cargo cache; the archive and DSF are copied into the manifest cache and
  pinned by SHA-256. No reference source was modified.
- Manifest verification, audio guard and `git diff --check`: pass.

Commands run:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 8
python3 scripts/waxlabel-oracle.py --verify
# With external CARGO_HOME/RUSTUP_HOME/CARGO_TARGET_DIR as documented in the harness:
~/.cache/sistrum-waxflow-oracle/dsd/cargo/bin/cargo +1.90.0 test --locked --release \
  -p dsf-meta -p dff-meta \
  --manifest-path ~/.cache/sistrum-waxlabel-oracle/dsd-helper/Cargo.toml
./gradlew :metadata:compileKotlin
./gradlew :metadata:test --tests '*DsdOracleParity*'
./gradlew :metadata:test --rerun
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa
./gradlew :metadata:benchmark -PwaxlabelStep=8
python3 scripts/check-no-audio.py
git diff --check
```

The first reference adapter tried standalone MP3 tags; truncated leading tags have
MP3-specific detection behavior, so extraction now uses WaxLabel's bounded embedded
ID3 WAV path. Initial DFF vectors exposed the Rust reader's odd-property-pad handling;
the owned valid-vector generator now emits an even CMPR body, as the existing codec
corpus generator does. Native unparsed-ID3 summaries were made explicit, and the
reader gained early ID3 support as a declared addition. No existing test was weakened,
skipped or disabled. Logs remain external as step8-*.log.

## Benchmark

[step8-benchmark.tsv](step8-benchmark.tsv) records median parse time and allocation
for all 125 inputs, including both real upstream fixtures and typed errors.
JDK 21.0.12.1, amd64; warm filesystem cache, open source, METADATA_ONLY; 25 warmups,
101 trials per file. Includes metadata parsing/hashing, excludes file opening and
lazy FULL loads. The final standalone run follows test workloads; no threshold was
specified. Summary is the median of per-file medians.

| Format | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| DFF | 34 | 41.156 | 7764 |
| DSF | 48 | 13.925 | 2384 |
| Typed errors | 43 | 5.330 | 2528 |

## Files changed and completion

- Added `DsdMetadata.kt` and dispatch for DSF/DFF.
- Added DSD parity and guarded-read contracts and Gradle oracle inputs.
- Added `waxlabel-dsd-oracle.py`; extended external vector generation and manifest
  verification to the pinned crate/reference fixtures.
- Added composite reference JSON, benchmark snapshot and this report; updated
  README, ORACLE.md, patch log, manifest and third-party notices.

All changes belong to the read port and oracle support. No unrelated files, branch
switches, main changes or pushes. No remaining implementation step in this brief.
