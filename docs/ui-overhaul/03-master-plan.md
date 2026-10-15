# Dependency-aware master plan

Evidence: [current state](00-current-state.md), [source map](01-reference-source-map.md), owner NOTES.md B and working-copy D-31/D-32. All milestones and branch recommendations are **PROPOSED**; Phase 0 source findings are **VERIFIED IN SOURCE**, not execution results.

## Branch and migration baseline

Do not switch/create/reset the existing stale `feature/ui-overhaul`. After 002 finishes and its owner records a stable accepted SHA, propose a fresh `feature/ui-overhaul-004` in a separate worktree from that SHA (or completed 003 if available). Preserve the current shared worktree. Record base SHA and contracts again before implementation. Apply documentation through the orchestrator's normal review process; no documentation commit, stage, push or `.codex-commit/` is created in Phase 0.

002 completion is required for storage-integrating work and the analysis decoder track. UI components may be designed independently, but integration touching App/MainActivity/settings/scanner waits for a stable baseline. 003 does not depend on M4b; do not reverse that relation. Features that need new native code are blocked by the constitution, not unlocked by 003.

## Future Spec Kit milestones

These labels are proposed milestone names for the later Spec Kit task list, not claims Spec Kit has already run. Atomic U IDs are in [04](04-atomic-tasks.md).

| Milestone | Goal / U tasks | Dependencies and exit |
|---|---|---|
| SK004-M0 | Baseline contracts/characterization U-001, U-019 | Stable integration base; baseline test evidence before refactors |
| SK004-M1 | Tokens/glass U-002–003 | M0; accessible opaque fallback and performance reference |
| SK004-M2 | Home/library/detail/search U-004–010 | M1; each screen separately reviewed; local feature parity |
| SK004-M3 | Navigation/mini/style/lyrics presentation U-011–016 | M0/M1; screens need not all land first; DEFAULT parity |
| SK004-M4 | Registry/package/runtime/security POC U-020–025 | Can run alongside UI after authorization; no real provider credentials; independent isolation, compatibility and no-native gate |
| SK004-M5 | Extension persistence/manager/update UI U-026–030 | M4 go decision for code activation; listing/validation can precede it |
| SK004-M6 | Matching, artist photos/motion/cache U-031–035 | M0, repository persistence, M4/M5 for extension execution; provider rights gate; motion renderer after migration stable |
| SK004-M7 | Lyrics policy/cache/aggregation/extension words U-036–040 | Preserve two built-ins; U-036/037 can proceed without runtime; U-039 requires M4/M5 and provider clearance |
| SK004-M8 | Trusted storage and single-track embedding U-041–049 | Completed 002 D-31, `:metadata` baseline, tests-first and rights decisions; independent of UI overhaul and online runtime for local/user-owned lyrics |
| SK004-M9 | Optional Wikipedia U-050–051 | Identity/cache contracts; no extension-runtime dependency; attribution and reliable matching |
| SK004-M10 | Library Settings / volume preference U-017–018 | M1; chosen-folder presentation waits for 002 |
| SK004-M11 | Integration U-052 | 002 stable and chosen 003 integration base; full contracts checked; no engine rewrite |
| SK004-M12 | Release verification U-053 → U-055 → U-054 (phone gate and final report last) | All selected deliverables, policy blockers resolved or explicitly held out; full host/lint/build/security/QA evidence |
| SK004-A0 | Analysis-only decoder tests U-100 | Completed 002; no UI dependency |
| SK004-A1 | Opus CELT / SILK U-101–102 (= T033a/b) | A0, then CELT before SILK; preserved decoder/oracle obligations |
| SK004-A2 | AAC U-103 (= T033c) | A0; independent from UI, Opus and other format ports |
| SK004-A3 | MP3 + FLAC U-104 (= T033d) | A0; unchanged moved task boundary |
| SK004-A4 | Vorbis U-105 (= T033e) | A0; independent from UI and other ports |
| SK004-A5 | Analysis-only wiring U-106 (= T033f) | U-100–105 and completed 002; all Kotlin initially, retry-once provider attribution |
| SK004-A6 | M4b full gate/phone benchmark U-107 (= T033g) | U-106; measured per-format choice, SC-012, full verification; no UI dependency |

M4b is its own **analysis decoders** track. Its original seven task texts are copied unchanged into 04, under U wrappers retaining T033a–g identity. Adding U-100 ensures high-risk analysis-wiring tests precede implementation without altering the moved text. No original task has been marked complete.

## Critical paths and conditional scope

UI: M0 → M1 → individual screens/navigation/player → M11 → M12. Extension: parser/package → broker → runtime POC → compatibility/security/compliance decision → manager → providers. Lyrics file writing: 002 grants/metadata → preservation fixtures → recoverable storage transaction → per-format writer → single-track UX → recovery/device verification. Analysis: 002 → A0 → parallel-in-concept format ports → A5 → A6. Future tasks remain single implementer unless a later brief authorizes delegation.

BLOCKED: Rust/native QuickJS transplantation; unresolved package authenticity/trust policy; unapproved Apple/YouTube/Deezer access/persistence; lyrics embedding without confirmed rights. These gates do not block tokens, local screens, local lyrics, safe storage design or analysis ports. Choosing a narrower native Kotlin provider adapter does not meet arbitrary package compatibility: if POC cannot satisfy the required extension contract safely, report that gap rather than declaring full scope delivered.

## Sequencing and delivery policy

Each U task is one reviewable commit boundary in future implementation, with tests and rollback described in 04. No unrelated UI/backend combination. Required schema work gets explicit migration tasks and tests before DAO changes. All English strings also get Arabic; all milestone completion checks use devQa and only `.qa` device ID. Batch embedding, custom registries, Spatial Audio and 8D remain deferred. Real listening/Bluetooth/headset checks stay manual and never become automated PASS.

## UNKNOWN / NEEDS VALIDATION

Runtime POC may recommend a JVM engine with isolated service, a system JavaScript engine, or a provider-specific Kotlin adapter. Size/startup/memory must be measured, not estimated as facts. Exact stable 002/003 baseline and eventual milestone task splitting require Spec Kit; Phase 0 provides complete fielded U tasks and gates, not implementation briefs.
