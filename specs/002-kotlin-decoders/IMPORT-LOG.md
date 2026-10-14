# Import log — origin/port/analysis → feature/media3-migration (002 Phase A)

Source: `git log --reverse $(git merge-base HEAD origin/port/analysis)..origin/port/analysis` (merge base
`c58f769`), fetched 2026-10-14: 46 commits. Each is cherry-picked in order, rebranded inside the same commit
(packages `me.misa198.airmedy` → `com.amwolfstein.sistrum`, devDebug → devQa, app IDs; FfmpegDecoder JNI exception
kept), graph refreshed, all imported modules' tests + `assembleDevQa` run, amended with
`Co-authored-by: Claude <noreply@anthropic.com>`, pushed. `port/waxflow` and `port/analysis` are read-only.

| # | Original | New | Subject | Rebrand changes | Conflicts | Graph | Tests (counts) | Fixes | NOT RUN |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `01759c8` | `c4e7401` | test(waxflow): add pinned public corpus oracle fixtures | none needed (test resource + doc) | none | no | `assembleDevQa` PASS; no imported module yet | separate commit: pre-existing old IDs in `scripts/test-corpus/README.md` (T062a leftover) | — |
