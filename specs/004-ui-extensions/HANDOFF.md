# HANDOFF — 004-ui-extensions (UI part)

**Branch:** `feature/media3-migration` (shared with 002/003/005; D-2). **004 start SHA:** `9f0acac` (last commit before any 004
task; SC-012 "no `player/` change" is measured from here).

## Current status

- **Phase:** 004 UI M1 (T001–T008) handed to Codex on 2026-10-15 through `milestone-auto.sh` (state dir
  `~/.local/state/sistrum-delegate/milestones/004`, log `.../milestones/004-M1-auto.log`). Owner: "send it to Codex to
  implement" after D-22 (Apple Music style Home, Recently added moved from Library to Home).
- **Scope now:** UI only (M1–M5). Decoders part of 004 (former 002 Phase 5b / M4b) comes later, after 002 M3–M6 and 003.
- **Next step:** runner finishes M1 (tasks, reviews, T008 gate + phone check, Haiku final check); then M2 (T009–T020) the
  same way. Order after 004 UI: T021a → 002 M3–M6 → 003 → 004 decoders.
- **Known baseline:** the instrumented suite has known test-side failures (CLAUDE.md "Known state"); T002 lists the current
  pass/fail of `AppNavigationTest`, `MiniPlayerTest`, `FullScreenPlayerTest`, `RtlLayoutTest` on `.qa` here.
- **Owner checks pending (not blocking):** LARGE style look (D-12), Home look (D-22) at the M2 phone check.

## Task log (newest first)

| Date | Task | Result |
|---|---|---|
| 2026-10-15 | Plan update D-22 (Claude) | Home in the Apple Music style with local data; Recently added moves from Library to Home (T009/T010). |
