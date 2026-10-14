# Decisions taken without the owner — 002-kotlin-decoders

Owner rule (2026-10-14): pick the recommended option, keep going, log it here for later review.

## D-1: Where to run the corpus rebuild before the first import
Case: the plan says to rebuild the corpora with `bash scripts/waxflow-oracle.sh --fetch-generate` before the first
cherry-pick, but on `feature/media3-migration` that option only arrives with import commit 24 (`60680e3`, which adds
`scripts/oracle-corpus.py`). The current script has no `--fetch-generate`.
Options:
- Run the port branch's own script from a temporary detached checkout of `origin/port/analysis` outside the repo.
  Pro: does exactly what the plan asks, now; changes no branch (read-only use); the corpus lives in `~/.cache`.
  Con: the scripts come from a branch not yet imported (same code that commit 24 will bring).
- Wait until commit 24 is imported and rebuild then. Pro: uses imported code only. Con: commits 1–23 would be tested
  against an old corpus, which is what the plan wants to avoid.
Chosen: the first — a detached checkout in the session scratchpad, removed afterwards; `port/analysis` is never written.
Revisit if: the rebuild changes files inside the checkout (it must only write to `~/.cache`), or commit 24's scripts
differ from what was run (they are the same commit's files; re-run after commit 24 if they ever do).

## D-2: WaxLabel oracle regeneration is not byte-identical on this machine
Case: after the corpus rebuild (D-1), `scripts/waxlabel-oracle.py --fetch-generate --step 1..8` rewrote four committed
reference files: the corpus manifest (5 Matroska/WebM files made by the local ffmpeg hash differently — the muxer
writes its version/date), and the step 1/2/7 JSON (step 1 now lists owned files that later steps added to the shared
cache). The dsp oracle and the WaxFlow/DSD/WavPack corpora reproduced and verified exactly.
Options:
- Keep the committed references (what the port was validated against) and treat the regenerated copies as throwaway.
  Pro: no change to imported commits; parity tests keep their reviewed expectations. Con: the 5 ffmpeg-made Matroska
  corpus files in `~/.cache` no longer match the committed manifest, so tests that check corpus hashes may refuse them.
- Commit the regenerated references. Con: changes imported commits' content for an environment reason, and step 1's
  file list would no longer describe step 1.
Chosen: keep the committed references. If the Matroska metadata tests (import commit 35, `b8d17fc`) refuse the
regenerated files, record those cases NOT RUN with this reason rather than editing expectations.
Revisit if: the Matroska tests fail for any reason other than the corpus hash check, or a pinned ffmpeg build
becomes part of the oracle procedure.
