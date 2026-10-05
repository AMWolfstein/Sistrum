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
| Pinned fork commit | `446ca3124d890fd08caecdcbf8931a493d4ebd04` (`446ca31`) |
| Upstream commit it is based on | `446ca3124d890fd08caecdcbf8931a493d4ebd04` (2026-09-23, "Skip a CUE sheet's data track instead of cutting it as audio"); the fork has no own commits yet |
| Pinned on | 2026-10-05 |
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
