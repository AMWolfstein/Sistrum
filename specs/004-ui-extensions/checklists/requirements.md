# Requirements checklist - 004 (UI part)

- [x] Every user story has a priority, an independent test and acceptance scenarios (US1-US7).
- [x] Every FR has a task: FR-001 T004/T005, FR-002 T006/T007, FR-003 T009-T018, FR-004 T009, FR-005 T043/T044, FR-006 T045-T048,
      FR-007 T049-T051, FR-008 T052/T053, FR-009 T056/T057, FR-010 removed (D-20), FR-011 T023/T038, FR-012 T021/T022,
      FR-013 T028/T029, FR-014 T026/T027/T030, FR-015 T025/T027/T035, FR-016 T031-T037/T039/T040/T054, FR-017 T030/T038/T047/T052,
      FR-018 T060-T062, FR-019 T042/T064, FR-020 T025/T027/T033/T034/T037/T050/T051/T065.
- [x] Every SC is measurable and has a task: SC-001 T001/T002 and each gate, SC-002 T004/T007, SC-003 T009-T018/T062, SC-004 T049,
      SC-005 T052/T053, SC-006 T023/T038/T041, SC-007 T024/T026/T032, SC-008 T040, SC-009 T060/T061, SC-010 T001/T066, SC-011 T003/T066,
      SC-012 T042/T064, SC-013 T056/T057.
- [x] Each milestone ends with exactly one full gate + `.qa` phone-check task (T008, T020, T041, T055, T066); T067 is the owner merge step.
- [x] State/isolation risk areas have tests-first tasks (D-16); HIGH-RISK tags set.
- [x] No [NEEDS CLARIFICATION] markers; each choice is a D-entry.
- [x] Out of scope stated (decoders, extension runtime, other providers, lyrics search/writing).
- [x] Device work only on `.qa`; daily apps never touched; no change under `player/`; no Room change; no new dependency.
- [x] English + Arabic (values-ar complete), Western digits, RTL and LTR transport covered by tasks.
- [x] GPL/MIT reuse and attribution rules written (FR-020, plan.md).
- [ ] Open: owner may veto D-9 (no fallback token), D-10 (all three OFF), D-11 (English Wikipedia), D-12 (LARGE mapping).
