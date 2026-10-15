# HANDOFF — 004-ui-extensions (UI part)

**Branch:** `feature/media3-migration` (shared with 002/003/005; D-2). **004 start SHA:** `9f0acac` (last commit before any 004
task; SC-012 "no `player/` change" is measured from here).

## Current status

- **Phase:** 004 UI M1; T001 host characterization complete (10 new tests), commit prepared for the runner on
  `feature/media3-migration`. T002–T008 remain. No production code or existing tests changed.
- **Scope now:** UI only (M1–M5). Decoders part of 004 (former 002 Phase 5b / M4b) comes later, after 002 M3–M6 and 003.
- **Next step:** runner commits T001, then runs T002 device characterization; finishes M1 (reviews, T008 gate + phone
  check, Haiku final check); then M2 (T009–T020) the
  same way. Order after 004 UI: T021a → 002 M3–M6 → 003 → 004 decoders.
- **Host baseline (SC-010, T001):** app 1,464 tests (1,463 passed, 1 existing skip), shared 39 passed; total
  1,503 discovered / 1,502 passed / 0 failures / 0 errors. Before T001, stored results contained 1,454 app + 39 shared
  tests (1,493 total); the new class adds 10. Existing skip:
  `WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures` (unchanged; not claimed PASS).
- **Known baseline:** the instrumented suite has known test-side failures (CLAUDE.md "Known state"); T002 lists the current
  pass/fail of `AppNavigationTest`, `MiniPlayerTest`, `FullScreenPlayerTest`, `RtlLayoutTest` on `.qa` here.
- **Owner checks pending (not blocking):** LARGE style look (D-12), Home look (D-22) at the M2 phone check.

## Task log (newest first)

| Date | Task | Result |
|---|---|---|
| 2026-10-15 | T001 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added only `test/ui/navigation/UiContractCharacterizationTest.kt` (10 passing tests); existing gesture tests referenced, not duplicated. Impact: App/MainViewModel stack and metadata consumers, navigation scroll/visibility/transport helpers, lyrics browse/parser entry points; no Room, analysis, preferences, decoder order or PlaybackController contract changes; all existing tests unmodified. Quick gate attempt 1 failed on new test dispatcher cleanup, fixed by JUnit teardown; attempt 2 PASS (full app/shared host tasks + Kotlin compile), logs `/tmp/verify-task.Ix9SN3` (attempt 1: `/tmp/verify-task.TSvm8t`). Counts: app 1,464 (1 existing skip), shared 39; new class 10/10; 0 failures/errors. Memory prechecks: 4,198,292 / 4,163,876 kB available after daemon stop. Required gate graph refresh generated `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json`; these extra files are included per autonomous step 4, without manual edits. `tasks.md` and this file record completion/baseline. Self-review and diff whitespace check passed. NOT RUN: device/recreation/full-player back-priority checks (T002/T008), lint and APK assembly (milestone gate); existing oracle skip remains NOT RUN. Decisions: none; no follow-ups. |
| 2026-10-15 | Plan update D-22 (Claude) | Home in the Apple Music style with local data; Recently added moves from Library to Home (T009/T010). |
