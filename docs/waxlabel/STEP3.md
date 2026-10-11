<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
<!-- WaxLabel read port; Copyright (c) 2026 Cole Springer, MIT. -->
# Step 3 — MP4/M4A read path

Pinned oracle: AMWolfstein/WaxLabel `cfbb45d13135a76f5df6781fdeec75f5cfe63bf9`.
Implemented on `port/waxflow`; steps 4–8 remain undone.

## Result

MP4/M4A/M4B/M4R/MOV/ALAC now read iTunes ilst text, structured integers/booleans,
track/disc pairs, numeric genres, com.apple.iTunes freeforms and aliases, covr,
classic QuickTime multi-language udta text, indexed mdta keys and Nero/QuickTime
chapters. Native atom summaries, ordered multi-value tags, provenance/conflicts,
padding, codec configuration, media/edit duration and structural bitrate follow
the pinned read path. MPEG-4 AAC config includes LC/Main, explicit and sync SBR/PS,
MPEG object indications; ALAC/FLAC/PCM geometry and QuickTime sound v0/v1/v2 are
covered. Total samples stay zero as in the pinned MP4 public API.

Audio payloads and chapter style trailers are skipped. METADATA_ONLY streams cover
sniffing/hashes, NONE skips image bytes, FULL loads bounded fresh arrays lazily.
Document collections remain immutable. No writer, player, scanner, Android, Room,
codec decoder or native-code integration was added. Existing MIT notices remain
packaged and every added file carries attribution.

## Validation

- Metadata: **502 tests**, zero failures/errors/skips: 347 existing step-1/2 cases,
  146 step-3 oracle cases and nine MP4 contracts.
- Codecs: **1477 tests**, zero failures/errors/skips; suite explicitly rerun.
- `:androidApp:assembleDevQa`: successful.
- Step-3 corpus: **146 records**, 119 successful and 27 typed errors;
  **144 exact matches**, two precisely asserted chapter-title differences;
  **zero unexplained mismatches**. Successful records compare METADATA_ONLY and
  FULL, total samples and cover SHA-256 against the pinned public read API.
- **1021 manifest entries** verify all pinned fetched source/tests/testdata and
  owned inputs. All seven upstream MP4-family testdata files are included.
- Guarded sources reject encoded audio reads, permit only known legitimate
  chapter-title ranges, and verify NONE/scan/lazy FULL image policies. Contracts
  cover short/error sources, byte and element limits, all byte truncations through
  2048 bytes on three files and 3000 seeded mutations.
- Pinned upstream internal MP4/MPEG-4 audio tests: **338 passed test events**,
  zero failures/skips, explicitly rerun with `-count=1`.
- Upstream `./tests -run MP4`: **332 passed test events**, no skips, two known
  differential leaf failures (plus their failing parent/package): ipcm/fpcm
  high-resolution inputs report 48000 Hz versus ffprobe's 96000 Hz. Owned ffmpeg
  silence reproduces both. Kotlin exactly preserves the pinned oracle's 48000 Hz;
  neither upstream tests/source nor reference output was changed.
- Audio guard, manifest verification and `git diff --check`: pass.

Commands:

```sh
~/.cache/sistrum-corpus-venv/bin/python scripts/waxlabel-oracle.py --fetch-generate --step 3
python3 scripts/waxlabel-oracle.py --verify
# In the external pinned upstream checkout:
go test -count=1 -json ./internal/mp4 ./internal/mpeg4audio
go test -count=1 -json ./tests -run MP4
# In Sistrum:
./gradlew :metadata:compileKotlin
./gradlew :metadata:test --tests '*Mp4*'
./gradlew :codecs:test --rerun :metadata:test --rerun :androidApp:assembleDevQa
./gradlew :metadata:test --rerun
./gradlew :codecs:test :metadata:test :androidApp:assembleDevQa :metadata:benchmark -PwaxlabelStep=3
./gradlew :metadata:benchmark -PwaxlabelStep=3
python3 scripts/check-no-audio.py
git diff --check
```

The initial full run completed all codec tests but found one new overflow-chapter
rounding mismatch. The fix matches Go's saturated duration rounding; the final
metadata rerun and combined tests/build/benchmark command pass. The final standalone
benchmark ran after test workloads ended. Compilation/parity failures during the
port were corrected; no existing test was skipped or weakened. An exploratory
upstream `go test -count=1 -json . -run MP4` matched no tests.

Detailed logs remain local-only in `~/.cache/sistrum-waxlabel-oracle/step3-*.log`
and `step3-upstream-*.jsonl`. All audio/image inputs remain in the external cache.

## Deviations

PATCH-6 refuses chapter-title reads when corrupt offsets alias audio samples or
structural file bytes. `mp4-chapter-audio-alias.m4a` loses only the first title
`LEAK`; `mp4-stco-structural.m4a` loses only the second title taken from ftyp/free
bytes. Every other projected field matches. Unmodified reference JSON remains
committed; tests assert each upstream premise and exact allowed delta. See
[the patch log](../../metadata/SISTRUM-PATCHES.md).

PATCH-1's picture policy and PATCH-3's resource caps extend to MP4. These include
shared atom/data/offset budgets, bounded labels/nesting, contribution counts and
protected media-sample geometry. Classic udta and indexed mdta are included as
parts of the pinned MP4 READ path. The upstream PCM high-rate errors are preserved,
not additional Kotlin deviations.

## Benchmark

[step3-benchmark.tsv](step3-benchmark.tsv) records median nanoseconds and allocated
bytes for every one of the 146 inputs, including errors and large covers. JDK
21.0.12.1, amd64; warm filesystem cache, open source, METADATA_ONLY; 25 warmups and
101 measured calls per file. Hashing is included; file opening and lazy FULL loads
are excluded. No performance threshold was specified.

The summary below is the median of per-file medians grouped by extension; every
successful input has the MP4 container. It is not a pooled trial distribution.

| Inputs | Files | Median parse (µs) | Median allocation (bytes) |
|---|---:|---:|---:|
| .m4a | 109 | 59.321 | 10376 |
| .m4b | 2 | 305.524 | 72764 |
| .mov | 3 | 138.622 | 24520 |
| .mp4 | 5 | 151.522 | 25144 |
| Typed errors | 27 | 12.740 | 3040 |

## Files changed and remaining work

- Added `Mp4.kt`, `Mp4Tags.kt`, `Mp4Properties.kt`, `Mp4Chapters.kt`,
  `Mp4OracleParityTest.kt` and `Mp4ReadContractsTest.kt` in `:metadata`.
- Updated metadata dispatch, Gradle oracle inputs, README and patch log.
- Updated `Document.kt` duration rounding to preserve Go's saturated upper bound,
  required by the new overflow vectors. Updated `OracleSupport.kt` to use the
  pinned CLI's actual lossy-codec exclusion for bit depth; this adds validation
  for float64, companded PCM, ADPCM and unknown sample entries. Expected upstream
  values were not edited.
- Extended `scripts/waxlabel-oracle.py` with deterministic external step-3
  generation; added `oracle-step3.json`, manifest entries, oracle documentation,
  this report and the benchmark snapshot.

All edits stay within the metadata port and its oracle support. No unrelated files,
branch switches, main changes or pushes. The existing notices already contain the
pinned WaxLabel MIT license; no additional dependency was introduced.
Steps **4–8 remain** at this clean step-3 boundary.
