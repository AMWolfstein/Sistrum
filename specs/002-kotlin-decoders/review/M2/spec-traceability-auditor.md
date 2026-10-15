# M2 spec traceability audit

Reviewed `a18bbcb50e4c37c675d91846d8fd7d2d356b3109^..d2b9669` (HEAD), tasks T014–T021. This is a traceability PASS for the recorded work, not an M2 completion or device acceptance PASS: T021 remains unchecked and uncommitted.

| task | commit | FR/SC | tests | HANDOFF evidence | status (OK/GAP/MISMATCH) |
|---|---|---|---|---|---|
| T014 | `a18bbcb` | FR-019; SC-008 | FloatChainNightModeTest: 8 methods, including OFF identity, ≥30% range reduction, limiter boundary model, 50ms switching and shared reader | HANDOFF:434 onward; `/tmp/verify-task.KcByP9`: 113 cases, 108 pass, 5 expected assertion failures; expect-fail.txt confirms accepted failures. Real session measurements explicitly deferred. | OK |
| T015 | `b1bf978` | FR-019; SC-008 | Unchanged T014 tests; NightModePreferencesTest; FloatChainNightModeLifecycleTest | HANDOFF:379 onward; `/tmp/verify-task.WY4RoK`: reported 265/265; unit.log ends BUILD SUCCESSFUL. Android wiring/session capture explicitly NOT RUN. | OK |
| T016 | `654c30b` | FR-020, FR-021 (precomputed profiles) | SilenceProfileTest: 10 methods covering threshold/minimum, chunked edges, resume, persistence and one-shot version refresh | HANDOFF:342 onward; `/tmp/verify-task.aulDB7`: reported 171 cases, 170 pass, one existing corpus skip; unit.log ends BUILD SUCCESSFUL. Missing SISTRUM_CORPUS is recorded as the skip reason. | OK |
| T017 | `2eccc49` | FR-020, FR-021 | TransitionControllerSilenceTest: 21 methods, including missing-profile immediate legacy fallback and unchanged manual/repeat/gapless policy | HANDOFF:294 onward; `/tmp/verify-task.5iMAgk`: 163 cases, 143 pass, 20 expected assertion failures; expect-fail.txt confirms accepted failures. | OK |
| T018 | `fcee1d2` | FR-020, FR-021; SC-009 timing support | Unchanged T017 tests; PlaybackSilenceProfileTest; SilencePlaybackPreferencesTest; PlaybackCoordinatorSilenceTest, including missingProfileDoesNotDelayPlaybackForAnalysis | HANDOFF:223 onward; `/tmp/verify-task.IFo8D7`: reported 281/281 with filters and class scope; unit.log ends BUILD SUCCESSFUL. Captured overlap/SC-009 explicitly deferred. | OK |
| T019 | `936e772` | FR-019, FR-020 settings | ArabicTranslationCompletenessTest; NightModePreferencesTest; SilencePlaybackPreferencesTest; existing UI helpers and T017 tests | HANDOFF:177 onward; `/tmp/verify-task.I2tr2T`: reported 138/138, including Arabic 2/2; unit.log confirms filters and BUILD SUCCESSFUL. Compose interaction is explicitly NOT RUN. | OK |
| T020 | `d2b9669` | SC-008, SC-009; FR-019, FR-020 | NightModeDeviceTest (2 methods); SilenceCrossfadeDeviceTest (3 methods): identity/range, 2s/3s tails, ≤100ms silent overlap, ≤10ms legacy timing and option-OFF negative control | HANDOFF:125 onward; `/tmp/verify-task.hVguV8` confirms the three host classes and BUILD SUCCESSFUL (reported 32/32). `/tmp/t020-device-compile-retry.log` confirms final Android-test compilation BUILD SUCCESSFUL. All five device methods explicitly NOT RUN, as task requires. | MISMATCH: stale commit status only |
| T021 | None; `[ ]` | FR-019–021; SC-008, SC-009; full regression gate | Both T020 classes and prescribed Media3CrossfadeTest, Media3EngineCoreTest, Media3LimiterTest exist | HANDOFF:10–20 assigns full suites/lint/assemble/phone execution to T021. No M2 full-gate or device PASS claimed. | GAP: pending task, accurately unchecked |

All seven commits in the range name exactly their corresponding task ID in the message body. Each completed task appears once in tasks.md and has its matching commit. The dependency line at tasks.md:110 agrees with the observed T014 → T015 → T016 → T017 → T018 → T019 → T020 order; T021 has not run. Task aliases for source/test roots resolve to existing files, including all three prescribed regression device classes.

Requirement coverage: FR-019 is exercised by the Night-mode sink and persistence tests; FR-020 by SilenceProfileTest and TransitionControllerSilenceTest; FR-021 by absentProfilesKeepExact001TimingWithoutWaitingForAnalysis and PlaybackCoordinatorSilenceTest.missingProfileDoesNotDelayPlaybackForAnalysis. SC-008 and SC-009 have the T020 device methods above. No referenced FR/SC ID lacks a test. Device execution remains deferred to T021. Hardware post-session limiter capture and engine-to-factory Night-mode PCM propagation are explicitly unmeasured in HANDOFF:170–175 and D-40; no runtime PASS is inferred from the host model or compilation. Owner listening/Bluetooth/headset checks are explicitly NOT RUN; the concrete Night-mode/crossfade-feel owner list remains part of unchecked T021.

D-34 through D-40 exist for T014 through T020 respectively, covering every decision those tasks record. DECISIONS.md has unique D-numbers throughout. Git diffs confirm FloatChainNightModeTest remains unchanged after T014 and TransitionControllerSilenceTest remains unchanged after T017.

Evidence limits: saved unit logs and expected-failure summaries were read directly, along with the final T020 compilation log. Historical per-run JUnit XML totals are recorded in HANDOFF; those totals were not independently reconstructed from archived XML. No Gradle, adb, device commands, agents, or graph tools were run in this review. Only this report was written; existing unrelated worktree changes were left untouched.

[LOW] specs/002-kotlin-decoders/HANDOFF.md:8 — Current status and next step still say T020's commit is prepared/awaiting the runner, although HEAD is its `d2b9669` commit. The task-log rows at lines 82–88 likewise retain prepared-bundle wording for committed tasks. A reader following Current status would attempt an already completed commit step. — Update Current status to name `d2b9669` and make T021 the next step; annotate the task-log commit fields with actual hashes or explicitly label them as historical pre-commit records.

| Severity | Count |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 1 |

VERDICT: PASS
