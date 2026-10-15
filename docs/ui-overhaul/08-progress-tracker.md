# Phase 0 progress tracker

**VERIFIED IN SOURCE:** source evidence and revisions in [01](01-reference-source-map.md). **PROPOSED:** all implementation tasks below; none started or tested by this audit. **UNKNOWN / NEEDS VALIDATION:** build/runtime/provider live behavior and performance. **BLOCKED:** activation/writing/provider shipping gates in [07](07-risks-and-decisions.md), not document completion.

## Sequential review passes

| Pass | Status | Evidence / result |
|---|---|---|
| Owner corrections and worktree audit | Complete | NOTES → master prompt → AGENTS → CLAUDE required sections → D-31/D-32; shared feature/media3-migration, baseline/dirty files preserved |
| Android architecture/Graphify | Complete, source-only | Installed CLI help inspected; committed-index query/explain; targeted local reads establish actual call chains |
| Reference source revisions | Complete | Six pinned/detached references including latest Rhythm main SHA; no scripts/builds/JS run |
| Registry/archive inspection | Complete, static | Registry snapshot +12 packages downloaded; all hashes match and equal X pinned packages; bounded member reads only |
| Runtime/security | Complete, planning | Rust/rquickjs upstream verified; sync host mismatch/native rule/isolation and authenticity gates documented |
| Metadata/artwork | Complete, source-only | Apple motion/artist fields, YTM/Deezer portraits; exact capability vs unsupported required features distinguished |
| Lyrics/tagging | Complete, planning | Existing two built-ins/local pipeline traced; no writer; read≠write D-31; format/recovery/test design |
| Compose visuals/integration | Complete, planning | V7 verified, LARGE proposed V8; Sistrum navigation/DEFAULT preserved; stable interfaces |
| License/compliance | Complete within stated limits | Root licenses + primary policies reviewed; unknown rights/current Deezer readable terms block shipping, not assumed resolved |
| Test strategy/roadmap | Complete, planning | All §14 fields per U task; unchanged M4b text, independent dependency path, test matrices |
| Build/test/device execution | NOT RUN | Phase0 prohibition, NOTES.md; no environment repair needed |
| Documentation consistency | See PHASE0-REPORT | Required files/links/fields/dependencies/original text/scope audited |

The owner resumed this task after reporting the laptop memory guard killed the prior run; existing collected references/results were reused. On resume owner stated no other run remained active. No process cleanup or build commands were used. Reference operations and further document work stayed modest; no new clone was needed.

## Future implementation queue — all PROPOSED / not started

| Task | Future Spec Kit milestone | Scheduling class | State |
|---|---|---|---|
| U-001 | SK004-M0 | SAFE NOW | Not started |
| U-002 | SK004-M1 | UI ONLY WITH DEPENDENCIES | Not started |
| U-003 | SK004-M1 | UI ONLY WITH DEPENDENCIES | Not started |
| U-004 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-005 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-006 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-007 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-008 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-009 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-010 | SK004-M2 | UI ONLY WITH DEPENDENCIES | Not started |
| U-011 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-012 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-013 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-014 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-015 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-016 | SK004-M3 | UI ONLY WITH DEPENDENCIES | Not started |
| U-017 | SK004-M10 | UI ONLY WITH DEPENDENCIES | Not started |
| U-018 | SK004-M10 | UI ONLY WITH DEPENDENCIES | Not started |
| U-019 | SK004-M0 | POST-MIGRATION | Not started |
| U-020 | SK004-M4 | SAFE NOW | Not started |
| U-021 | SK004-M4 | SAFE NOW | Not started |
| U-022 | SK004-M4 | SAFE NOW | Not started |
| U-023 | SK004-M4 | SAFE NOW | Not started |
| U-024 | SK004-M4 | EXTENSION RUNTIME DEPENDENT | Not started |
| U-025 | SK004-M4 | BLOCKED BY POLICY OR LICENSING | Not started |
| U-026 | SK004-M5 | SAFE NOW | Not started |
| U-027 | SK004-M5 | SAFE NOW | Not started |
| U-028 | SK004-M5 | EXTENSION RUNTIME DEPENDENT | Not started |
| U-029 | SK004-M5 | EXTENSION RUNTIME DEPENDENT | Not started |
| U-030 | SK004-M5 | EXTENSION RUNTIME DEPENDENT | Not started |
| U-031 | SK004-M6 | SAFE NOW | Not started |
| U-032 | SK004-M6 | SAFE NOW | Not started |
| U-033 | SK004-M6 | BLOCKED BY POLICY OR LICENSING | Not started |
| U-034 | SK004-M6 | BLOCKED BY POLICY OR LICENSING | Not started |
| U-035 | SK004-M6 | POST-MIGRATION | Not started |
| U-036 | SK004-M7 | SAFE NOW | Not started |
| U-037 | SK004-M7 | SAFE NOW | Not started |
| U-038 | SK004-M7 | SAFE NOW | Not started |
| U-039 | SK004-M7 | EXTENSION RUNTIME DEPENDENT | Not started |
| U-040 | SK004-M7 | UI ONLY WITH DEPENDENCIES | Not started |
| U-041 | SK004-M8 | POST-MIGRATION | Not started |
| U-042 | SK004-M8 | POST-MIGRATION | Not started |
| U-043 | SK004-M8 | POST-MIGRATION | Not started |
| U-044 | SK004-M8 | POST-MIGRATION | Not started |
| U-045 | SK004-M8 | POST-MIGRATION | Not started |
| U-046 | SK004-M8 | POST-MIGRATION | Not started |
| U-047 | SK004-M8 | POST-MIGRATION | Not started |
| U-048 | SK004-M8 | POST-MIGRATION | Not started |
| U-049 | SK004-M8 | POST-MIGRATION | Not started |
| U-050 | SK004-M9 | SAFE NOW | Not started |
| U-051 | SK004-M9 | UI ONLY WITH DEPENDENCIES | Not started |
| U-052 | SK004-M11 | POST-MIGRATION | Not started |
| U-053 | SK004-M12 | POST-MIGRATION | Not started |
| U-054 | SK004-M12 | POST-MIGRATION | Not started |
| U-055 | SK004-M12 | POST-MIGRATION | Not started |
| U-056 | SK004-M8 | POST-MIGRATION | Not started |
| U-058 | SK004-M5 | SAFE NOW | Not started |
| U-100 | SK004-A0 | POST-MIGRATION | Not started |
| U-101 | SK004-A1 | POST-MIGRATION | Not started |
| U-102 | SK004-A1 | POST-MIGRATION | Not started |
| U-103 | SK004-A2 | POST-MIGRATION | Not started |
| U-104 | SK004-A3 | POST-MIGRATION | Not started |
| U-105 | SK004-A4 | POST-MIGRATION | Not started |
| U-106 | SK004-A5 | POST-MIGRATION | Not started |
| U-107 | SK004-A6 | POST-MIGRATION | Not started |

## Open gates / next handoff

First future task: U-001 characterization on an accepted separate implementation baseline, followed by U-019 and tokens/glass. Runtime POC can be authorized separately; no production runtime choice exists yet. Storage tasks and analysis track require completed002. M4b original T033a–g remain unchecked. Native runtime architecture, authenticity governance, intended distribution channel and provider persistence rights require explicit decisions/evidence.

Final UI milestone order is U-053 → U-055 preparation → U-054, with phone verification and final HANDOFF in that last task. Analysis track ends with U-107.

Manual owner list for eventual milestones: listening continuity/gapless/crossfade/normalization, Bluetooth/headset/noisy/focus behavior with and without motion, RTL/font/visual review, representative local/SD/SAF providers and recovery usability. None is PASS in Phase0. Batch embedding/custom registries/Spatial Audio/8D remain deferred.
