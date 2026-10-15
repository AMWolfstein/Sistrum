# M1 spec traceability audit

Reviewed `7b5b318^..HEAD` through `f0236e53`, for T001–T008. Code and tests were read only; no Gradle, adb or agents were run. Only this report was written.

| task | commit | FR/SC | tests | HANDOFF evidence | status (OK/GAP/MISMATCH) |
|---|---|---|---|---|---|
| T001 | `7b5b318e` | SC-001, SC-010 baseline | Host `ui/navigation/UiContractCharacterizationTest` (10); existing swipe/seek tests referenced in KDoc | Row 47: full app/shared host tasks, 1,503 discovered, 1 existing skip; `/tmp/verify-task.Ix9SN3/unit.log` records successful app execution and shared task UP-TO-DATE | OK |
| T002 | `7607b187` | SC-001 | Device `ui/navigation/UiContractCharacterizationTest` (5); all four named existing baseline classes exist | Row 46 and lines 23–34: compile only; `/tmp/verify-task.QJjtJ3/unit.log` records successful androidTest Kotlin compilation; device results explicitly unknown/deferred to T008 | OK |
| T003 | `8ddcf5f5` | SC-002 harness, SC-011 baseline | `UiMatrixTest` (3 tests x 24 configurations); `scripts/ui-frame-stats.sh` | Row 45: `/tmp/verify-task.yRFHLV/unit.log` successful, host test task UP-TO-DATE disclosed; 72 device cases NOT RUN. Baseline artifacts exist under `/tmp/t003-sc011-baseline`; summary matches 5/250, 18/299, 3/30 janky frames | OK |
| T004 | `de1193ab` | FR-001, SC-002 | `SistrumTokensTest` (7 x 2 themes): palette, scales, compositing, normal/large contrast; existing `ThemeModeTest` | Row misplaced at line 30: `/tmp/verify-task.W5Lilk/unit.log` names token/theme/characterization filters and successful host execution; 26 reported passed | MISMATCH (LOW: row location) |
| T005 | `b1eb103d` | FR-001, SC-001 preservation | `ReducedMotionTest` (3): scale changes, observer lifecycle, 150/300/500 and zero durations; token/theme/host characterization tests | Row 44: `/tmp/verify-task.1xQYoN/unit.log` names all four host filters and successful host/androidTest compile tasks; 29 reported passed. Actual setting/composition integration explicitly NOT RUN | OK |
| T006 | `a1066c1f` | FR-002 | `LiquidGlassTest`: original resolution assertion plus 4 new methods covering all 8 policy inputs | Row 43: `/tmp/verify-task.zNLXyp/unit.log` names glass/token/characterization filters and successful execution; 29 reported passed | OK |
| T007 | `fd58914b` | FR-002, SC-002 | `GlassFallbackTest` (4 x 24 configurations): attached blur removal/recovery, opaque pixels, contrast, missing Haze, library labels; host glass/token/characterization tests | Row 42: `/tmp/verify-task.FszXQX/unit.log` names host filters and successful host/androidTest compile tasks; 29 reported passed; 96 device cases compiled only, NOT RUN until T008 | OK |
| T008 | None; task remains `[ ]` | SC-001, SC-002; full-gate SC-010 checks | All named device classes exist, including `CardTest`; host translation-completeness test exists | Current status explicitly says T008 remains; no full-gate/device PASS claimed | GAP (pending execution, not a false completion claim) |

All seven ticked tasks have exactly one matching task commit in dependency order. Each commit message names its task; the remaining commit, `f0236e53`, is an allowed workflow commit. The task-file history shows T001–T007 completion changes and leaves T008 unticked. The dependency chain at `tasks.md:131` matches the execution order so far. All source/test/script paths named by these tasks exist after expanding the documented aliases.

Requirement mapping: FR-001 is exercised by `SistrumTokensTest`, `ReducedMotionTest` and existing `ThemeModeTest`; FR-002 by `LiquidGlassTest` and `GlassFallbackTest`; SC-001 by both characterization classes; SC-002 by token contrast tests and the glass matrix; SC-010 by `ArabicTranslationCompletenessTest` and the host-suite/lint gates; SC-011 by the frame-stat script and recorded baseline. No referenced FR/SC lacks an exercising test or required measurement mechanism. End-of-M1 SC-001/SC-002 evidence and remaining full-gate SC-010 evidence are pending T008, not established by this review. SC-011's final comparison belongs to later milestones; only its baseline is claimed here.

For every claimed quick PASS, the referenced local log directory exists and its `unit.log` records `BUILD SUCCESSFUL`; graph logs also exist and match the reported refresh counts. T003's cached host execution and all compile-only device checks are disclosed. The retained files do not include the complete outer gate terminal transcript or a literal `GATE: PASS` line; verification here corroborates the recorded component runs, not an independent rerun or reconstruction of historical test counts. Current result files cannot independently establish every earlier filtered test count. NOT RUN items have reasons; device/listening/Bluetooth/headset checks are deferred rather than marked PASS. The T003 baseline explicitly discloses reused APK provenance and the small player-open sample.

T005's numeric-duration decision is recorded as D-23 at `DECISIONS.md:142`. D-1 through D-23 are unique. Other task rows declare no new decisions; their cited existing decisions are present.

Findings:

- [LOW] `specs/004-ui-extensions/HANDOFF.md:30` — T004's task-log row sits inside the three-column device-baseline table, so it renders under the wrong headers and is absent from the task-log section — move the unchanged T004 row into the task log between T005 and T003.
- [LOW] `specs/004-ui-extensions/HANDOFF.md:9` — Current status still calls T007 prepared and the next step still asks the runner to commit it, although `fd58914b` is in the reviewed range — record that SHA and make T008 the next task. Historical prepared-commit wording in individual task rows is otherwise resolvable from Git.

VERDICT: PASS

| Severity | Count |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |

RESULT: PASS
