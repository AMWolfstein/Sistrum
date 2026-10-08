# WaxFlow — port source and test oracle

WaxFlow is **not vendored** in Sistrum and **not shipped**: no app code links it. Sistrum pins one commit of the
owner's fork and uses it two ways (feature `002-kotlin-decoders`, and later the analyzer):

1. **Port source**: Kotlin decoders are ported from the pinned commit, one codec at a time.
2. **Test oracle**: `scripts/waxflow-oracle.sh` decodes a corpus with the pinned WaxFlow and writes golden PCM
   checksums and loudness numbers to a small committed fixtures file. Every Kotlin port must be bit-exact
   against it.

| | |
|---|---|
| Fork (Sistrum's only WaxFlow source) | https://github.com/AMWolfstein/WaxFlow |
| Upstream | https://github.com/ColeSpringer/WaxFlow (MIT) |
| Pinned fork commit | `b7857aff88820ad37936026421d1641e64611dbe` (`b7857af`), branch `fix/riff-wrapped-wavpack` |
| Upstream commit it is based on | `446ca3124d890fd08caecdcbf8931a493d4ebd04` (2026-09-23, "Skip a CUE sheet's data track instead of cutting it as audio"); fork fix `b7857af` refuses RIFF-wrapped legacy WavPack before PCM decoding |
| Pinned on | 2026-10-08 |
| Go module path | `github.com/colespringer/waxflow` (never renamed in the fork: renaming would make every upstream merge conflict) |

The pin lives in two places that always change together: `WAXFLOW_COMMIT` in `scripts/waxflow-oracle.sh` and
the table above. Ports record the pin they were made from (header below).

## The oracle script

```
scripts/waxflow-oracle.sh <corpus-dir> [fixtures-file]
```

- Clones the fork at the pinned commit **outside the repository** (`$SISTRUM_WAXFLOW_DIR`, default
  `~/.cache/sistrum/waxflow`; it refuses a path inside the repo, and refuses a clone with local changes).
- Builds the WaxFlow CLI and a small loudness helper there (needs the Go version WaxFlow's `go.mod` names,
  1.26+; `GO=/path/to/go` to pick one).
- For every file in the corpus: decodes with `waxflow transcode --format wav --no-tags` at the source rate,
  channels and depth (no gain, no resampling, no dither: a plain decode), hashes the WAV data chunk, and
  measures loudness with `waxflow.Engine.Analyze` (BS.1770-4 integrated loudness, EBU Tech 3342 loudness
  range, true peak, sample peak). Refusals are recorded with WaxFlow's message.
- Writes `androidApp/src/test/resources/waxflow/oracle-fixtures.tsv` (default): a header with the pinned commit,
  then one row per file: `file`, `file_sha256`, `status`, `frames`, `rate`, `channels`, `bits`,
  `sample_format`, `pcm_sha256`, `integrated_lufs`, `lra_lu`, `true_peak_dbtp`, `sample_peak_dbfs`.
- **No audio goes into git.** The corpus stays outside the repo; tests find it through a path given at test
  time and skip with a clear message when it's absent. `file_sha256` detects a corpus that drifted from the
  one the fixtures were made from.

Notes: integrated loudness is `-Inf` for clips shorter than one 400 ms gating block (expected). Lossy decoders
(MP3, Musepack, …) output float PCM; their `pcm_sha256` is over IEEE floats. Corpus sources: WaxFlow's
`testdata/`, the official WavPack decoder test suite (wavpack.com/downloads.html), and Sistrum's own test
corpus (`/sdcard/Music/SistrumTestCorpus`, pulled to the dev machine).

## Public corpus fixtures (2026-10-08)

The public corpus fixtures are in
`androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv`. The existing
`oracle-fixtures.tsv` (including Sistrum device samples and album groups) is preserved.
No corpus audio or downloaded archives are stored in the repository.

Both archives were fetched with `curl -fL --retry 2` into
`/tmp/sistrum-waxflow-oracle/downloads/` and extracted outside the repository:

| Source | Version / URL | Archive SHA-256 | Corpus files |
|---|---|---|---|
| WaxFlow fork `testdata/` | Commit `446ca3124d890fd08caecdcbf8931a493d4ebd04`; [commit archive](https://codeload.github.com/AMWolfstein/WaxFlow/tar.gz/446ca3124d890fd08caecdcbf8931a493d4ebd04) | `243d61bece25cae701a8036f35eccfc158586d53bc1370ceb91a8c1a494f5d9a` | 58 |
| Official WavPack decoder test suite | Version **2.0**, dated **2017-01-31** in its readme; [official listing](https://www.wavpack.com/downloads.html), [ZIP download](https://www.rarewares.org/wavpack/test_suite.zip) | `cfeee02f6f873f10da127603898546b03d9a1f7d3db1fbd0395b6c526696d675` | 74 |

Only the WaxFlow archive's `testdata/` subtree was copied, with its subdirectories
intact, to `corpus/waxflow-testdata/`. The complete WavPack ZIP was extracted to
`corpus/wavpack-test-suite-2.0/` (retaining its `test_suite/` directory). Its 74 files
include 48 `.wv` streams, 17 `.wvc` correction sidecars, eight `.txt` files and one
`.pk` file. The oracle visits every file, so auxiliary files also have refusal rows;
`.wvc` sidecars are not counted as standalone WavPack streams.

The oracle ran with Go `go1.27.1-X:nodwarf5 linux/amd64`, using the pinned fork,
an external work directory and external Go caches. To reproduce the generated file:

```bash
SISTRUM_WAXFLOW_DIR=/tmp/sistrum-waxflow-oracle/oracle-work \
GOCACHE=/tmp/sistrum-waxflow-oracle/go-cache \
GOPATH=/tmp/sistrum-waxflow-oracle/go-path \
bash scripts/waxflow-oracle.sh /tmp/sistrum-waxflow-oracle/corpus \
  androidApp/src/test/resources/waxflow/oracle-corpus-fixtures.tsv
```

The initial run used the default output path; its generated bytes were moved to
the public corpus filename, and the existing default fixtures were restored unchanged.
No groups file was supplied. Results: **132 file rows, 78 decoded, 54 refused**,
with no loudness failures. All **50 `.wv` streams** across both sources yielded
**28 decoded / 22 refused**; the official suite alone yielded **26 decoded / 22 refused**.
The two WaxFlow `.wv` samples decoded. Official-suite refusals cover unsupported
hybrid, DSD and float streams, a corrupt lossless stream and a self-extracting file.
Four HLS initialization files decode successfully to zero frames. All 132 source
SHA-256 values were checked against the extracted corpus, and decoded rows were
checked for PCM hashes and nonnegative frame counts.

## RIFF-wrapped WavPack fix and regenerated fixtures (2026-10-08)

Pin [`b7857aff88820ad37936026421d1641e64611dbe`](https://github.com/AMWolfstein/WaxFlow/commit/b7857aff88820ad37936026421d1641e64611dbe)
is published on fork branch `fix/riff-wrapped-wavpack`. The external clone used
for the fix and regeneration is `/tmp/sistrum-waxflow-oracle/oracle-work/src`.
The GitHub branch ref and commit API both returned the full pinned hash.

RIFF/WavPack format tests, WavPack conformance and the separate oracle module
pass at this pin. The full root Go suite still has two unrelated IMA differential
failures (`sine-ima.wav`, `sine-ima-stereo.wav`); both reproduce at the unchanged
base commit. They are reported rather than changed as part of the WavPack fix.

The fix is original recognition code based on WaxFlow's MIT RIFF parser and the
bytes of the eight official suite files. No FFmpeg or other GPL/LGPL source was
used. The first ten bytes of a RIFF data chunk identify the legacy version-1/2/3
header; recognition checks magic, legacy header size and version together. The
error is `wavpack: unsupported stream version: only version-4 streams
(0x402..0x410) are supported`. The probe precedes truncation warnings, so strict
and tolerant modes refuse for the same reason. Tests cover all eight files,
engine routing, magic-only ordinary PCM and the exact ten-byte added read cost.

The public corpus has not changed: it still contains the original 58 WaxFlow
files at `446ca31` and the 74 files from official suite 2.0, with the download
URLs and archive checksums above. Only the oracle implementation pin changes.
Regenerated `oracle-corpus-fixtures.tsv` has **132 rows, 70 decoded, 62 refused**;
its **50 `.wv` rows are 20 decoded / 30 refused**. The eight changed rows are
`legacy/vers-10.wv`, `vers-20.wv`, `vers-20-lossy.wv`, `vers-30.wv`,
`vers-30-fast.wv`, `vers-30-lossy.wv`, `vers-397.wv`, and
`vers-397-hybrid.wv`, under `wavpack-test-suite-2.0/test_suite/`. They now contain
unsupported-version refusals rather than hashes of compressed bytes read as PCM.
The original default `oracle-fixtures.tsv` is preserved at its recorded old pin;
it has not been regenerated as part of this public-corpus task.

## Attribution for ports

Every Kotlin file ported from WaxFlow starts with:

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from WaxFlow <path/in/waxflow.go>, fork github.com/AMWolfstein/WaxFlow at <full commit>,
// Copyright (c) 2026 Cole Springer, MIT License (see THIRD-PARTY-NOTICES).
// <If the source file uses FFmpeg-derived tables: "Tables derived from FFmpeg (LGPL-2.1-or-later)
//  via WaxFlow's extraction; see THIRD-PARTY-NOTICES.">
```

The first port adds WaxFlow (MIT, full license text) and FFmpeg's WMA tables (LGPL-2.1-or-later, as WaxFlow's
`THIRD-PARTY-NOTICES.md` records them) to Sistrum's third-party notices, plus any other source WaxFlow's notices
list for the ported code.

## Procedure 1 — sync the fork from upstream

Deliberate, never automatic. In a clone of the fork, not in Sistrum:

```
git clone git@github.com:AMWolfstein/WaxFlow.git && cd WaxFlow
git remote add upstream https://github.com/ColeSpringer/WaxFlow.git   # once
git fetch upstream
git checkout main
git merge upstream/main           # resolve conflicts; keep SISTRUM-PATCHES.md accurate
make verify-vectors               # fetch the pinned conformance vectors
make test test-oracle             # WaxFlow's format tests; all must pass
git push origin main
```

If one of the fork's patches was accepted upstream, drop the duplicate during the merge and mark it "merged
upstream" in `SISTRUM-PATCHES.md`. Syncing the fork does not change Sistrum; Procedure 3 does.

## Procedure 2 — fix a bug in the fork

1. In the fork, one commit per fix, with a message that says what and why.
2. Add an entry to the fork's `SISTRUM-PATCHES.md` (created with the first fix):

   ```
   ## <short title> — <commit hash>
   - What: <the change>
   - Why: <the bug or need, e.g. found by a Kotlin port mismatch>
   - Clean-room: yes | no (read FFmpeg/GPL/LGPL code)
   - Upstream: sent as <PR link> | merged upstream | not sent | not upstreamable (clean-room)
   ```

3. `make test test-oracle`, push the fork, then Procedure 3.

### Clean-room rule

WaxFlow upstream only accepts code written without looking at FFmpeg or other LGPL/GPL code (its
`MAINTENANCE.md`, "Clean-room procedure", and `docs/adr/0001-clean-room-policy.md`).

- A fix written clean-room may be sent upstream.
- A fix made while reading FFmpeg or other GPL/LGPL code stays in the fork only. That is fine for Sistrum
  (GPL-3.0). Mark it **not upstreamable** in `SISTRUM-PATCHES.md`, and never put it, even partly, in an
  upstream PR.
- The same applies to Sistrum's Kotlin ports: a port may be written while reading FFmpeg (Sistrum is GPL), but
  a fix found that way goes back to the fork as "not upstreamable".

## Procedure 3 — bump the pin

Only after Procedure 1 or 2 left the fork's format tests passing on the new commit. In Sistrum, on a feature
branch:

1. Set `WAXFLOW_COMMIT` in `scripts/waxflow-oracle.sh` and update the table at the top of this file (fork
   commit, upstream base, date).
2. Run the script on the same corpus. Review the fixtures diff: every changed row needs a reason (a fix in the
   fork, an upstream change). Unexplained changes stop the bump.
3. Re-run the Kotlin decoder tests against the new fixtures. Where WaxFlow changed decoder behaviour, port the
   change and update the affected files' attribution headers to the new commit.
4. Commit script, this file and fixtures together: `chore(waxflow): bump oracle pin to <short-hash>`.


## APE regression corpus extension (2026-10-08)

Eight free, committed WaxFlow regression files were fetched from the clean
external clone at the existing pin `b7857aff88820ad37936026421d1641e64611dbe`:
[codec/ape/testdata](https://github.com/AMWolfstein/WaxFlow/tree/b7857aff88820ad37936026421d1641e64611dbe/codec/ape/testdata)
(five `noise-c*.ape` files) and
[container/apen/testdata](https://github.com/AMWolfstein/WaxFlow/tree/b7857aff88820ad37936026421d1641e64611dbe/container/apen/testdata)
(`golden-known.ape`, `seek.ape`, `tagged.ape`). WaxFlow's test suite is MIT;
the noise fixtures are generated using the BSD-3-Clause Monkey's Audio reference
encoder, as its `fixturegen_test.go` documents. Each file was copied into the
external corpus under `waxflow-ape-tests/`, retaining its source-relative path.
No audio was copied into Sistrum and no non-free corpus was added.

Running the existing oracle command on the expanded corpus produced **140 rows,
78 decoded, 62 refused**. All eight additions decode. Every prior fixture row
is byte-identical; the oracle pin is unchanged. APE now has ten successful rows.


## ALAC regression corpus extension (2026-10-08)

Five free WaxFlow MP4 test-suite vectors were copied from the pinned external
clone's [container/mp4/testdata](https://github.com/AMWolfstein/WaxFlow/tree/b7857aff88820ad37936026421d1641e64611dbe/container/mp4/testdata):
`alac-stereo.m4a`, `alac-mono-tail.m4a`, and `golden/golden-s16-stereo.m4a`,
`golden/golden-s24-mono.m4a`, `golden/golden-s32-stereo.m4a`. They are MIT
WaxFlow test-suite assets generated with its ALAC encoder. Source-relative paths
are preserved under the external corpus's `waxflow-alac-tests/` directory.
The existing `waxflow-testdata/chapters.m4b` also contains ALAC.

Regeneration at the unchanged `b7857af` pin produces **145 rows, 83 decoded,
62 refused**; all five additions decode and every previous row is unchanged.
`scripts/waxflow-alac-packets.sh` builds a test-only Go helper outside Sistrum
and uses the pinned MP4 demuxer to save codec cookies and access units outside
the corpus and repository. These dumps contain compressed data and source hashes,
not expected PCM. Kotlin tests compare output only with the committed oracle.
The production module remains decoder-only for ALAC.
