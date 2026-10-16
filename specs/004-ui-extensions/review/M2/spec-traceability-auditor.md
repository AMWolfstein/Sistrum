# M2 spec traceability audit

Range: `fc03e17e0dc812ed7e64dbe2edff0248fc10f1ca^..fb88f20a` (HEAD at review). Tasks: T009–T018, T013a, T018a, T020.

Read-only review of commits, task/spec/plan/decision records, test source and retained local gate logs. Only this report was written. No Gradle, adb, device execution or agents were started. Test presence and retained run evidence were checked; tests were not rerun.

`OK` below means the task's recorded completion is traceable, not that M2 device acceptance passed. All completed tasks claim quick gates and compilation only; device execution, full suites, lint, assembly and owner visual checks are explicitly deferred. T020 is unchecked, has no commit and is correctly recorded as the next step.

| task | commit | FR/SC | tests | HANDOFF evidence | status (OK/GAP/MISMATCH) |
|---|---|---|---|---|---|
| T009 | fc03e17e | FR-003/004; SC-001/003 | HomeAlbumsTest; HomeContentTest; HomeContentMatrixTest; host characterization | HANDOFF:264: 35 host tests; `/tmp/verify-task.yItsIf/unit.log` ends BUILD SUCCESSFUL; separate AndroidTest compile `/tmp/t009-device-compile-verified.log`; device/owner checks NOT RUN | MISMATCH: matrix count, finding 1 |
| T010 | 97ae0d76 | FR-003; SC-001/003 | LibraryContentTest; LibraryContentMatrixTest; migrated Home recent-track cases | HANDOFF:263: 29 host tests; `/tmp/verify-task.mjiehQ/unit.log` successful, includes AndroidTest compile; D-22 migration explicitly recorded | OK |
| T011 | 07e52877 | FR-003; SC-001/003 | LibraryAlbumsContentTest; LibraryTracksContentTest, including loading, action identity, bounded composition and restoration cases | HANDOFF:262: 54 host tests; `/tmp/verify-task.uPa6bX/unit.log` successful, unit task UP-TO-DATE and AndroidTest compile executed; no host LibraryVirtualList match explicitly disclosed | OK |
| T012 | 79bd1f16 | FR-003; SC-001/003 | GenreComposerContentMatrixTest; existing genre/composer list/detail classes | HANDOFF:261: 36 host tests; `/tmp/verify-task.gxtx03/unit.log` successful, unit task UP-TO-DATE and AndroidTest compile executed; 144 device cases NOT RUN | OK |
| T013 | b64925dc | FR-003; SC-001/003 | AlbumArtworkKeyTest; AlbumDetailsContentTest; AlbumDetailsDiscOrderTest; AlbumDetailsPresentationTest | HANDOFF:260: 36 host tests; `/tmp/verify-task.xA8R8s/unit.log` successful with unit execution and AndroidTest compile; 72 new device cases NOT RUN | OK |
| T013a | a33074aa | FR-003; newest-cover portion of FR-013; D-19 | ArtistDefaultArtworkTest (11 tests); LibraryArtistMapperTest and artist ViewModel regressions | HANDOFF:259: 203 host tests across 22 classes; `/tmp/verify-task.dJdzEd/unit.log` successful; device/visual checks NOT RUN | OK |
| T014 | 8f11808d | FR-003; SC-001/003 | ArtistRowTest; LibraryArtistsContentTest; ArtistListPresentationTest | HANDOFF:258: 38 host tests; `/tmp/verify-task.GCWp5O/unit.log` successful with unit execution and AndroidTest compile; 96 new device cases NOT RUN | OK |
| T015 | 8044f11f | FR-003; SC-001/003 | ArtistDetailsContentTest, including slots/manual-image/offline cases; ArtistDetailsPresentationTest | HANDOFF:256: 37 host tests; `/tmp/verify-task.hCERaQ/unit.log` successful with unit execution and AndroidTest compile; device checks NOT RUN | OK |
| T016 | 0640c6f8 | FR-003; SC-001/003 | LibraryPlaylistsContentTest; PlaylistListPresentationTest; FavoritesArtworkTest and playlist host regressions | HANDOFF:254: 58 host tests; `/tmp/verify-task.p8Liyy/unit.log` successful with unit and AndroidTest compile tasks UP-TO-DATE; 144 new device cases NOT RUN | OK |
| T017 | 95425ff5 | FR-003; SC-001/003 | FavoritesArtworkTest; PlaylistDetailsContentTest; PlaylistDetailsPresentationTest | HANDOFF:253: 38 host tests, explicitly cached on final attempt after prior execution; `/tmp/verify-task.2HcqQv/unit.log` successful with AndroidTest compile; 96 new device cases NOT RUN | OK |
| T018 | 0391d144 | FR-003; SC-001/003 | LibrarySearchContentTest; LibrarySearchArabicTest; SearchPresentationTest; SearchRestorationTest | HANDOFF:252: 36 host tests; `/tmp/verify-task.cVC3aa/unit.log` successful with unit execution and AndroidTest compile; device checks NOT RUN | OK |
| T018a | fb88f20a | FR-002 policy; FR-010 toggle exception; D-21 | GlassToggleTest (12 tests); existing AppearanceContentTest, IntegrationContentTest and CrossfadeDurationSliderTest | HANDOFF:251: 32 host tests; `/tmp/verify-task.5FsPV7/unit.log` successful, unit task UP-TO-DATE and AndroidTest compile executed; settings/device/API 31–32/visual checks explicitly NOT RUN; license decision recorded | MISMATCH: spec wording, finding 3 |
| T020 | none; unchecked | SC-001/003; M2 full acceptance | Required characterization and screen classes exist; expanded deferred class list is in HANDOFF:26 | HANDOFF:26 identifies T020 as next work; no M2 full-gate/device PASS or SC acceptance table claimed | MISMATCH: planned class list, finding 2; completion remains pending |

The milestone goal in tasks.md:51 references FR-003/004 and SC-001/003. Each has test coverage by behavior: screen state/action/presentation tests for FR-003 and SC-003; Home grouping, local-only source guard, collapsing-title, queue and carousel tests for FR-004; unchanged host/device UiContractCharacterizationTest for SC-001. Neither characterization file changes in this range. M2 device execution of SC-001/003 remains pending T020, rather than an unsupported PASS. No FR/SC identifier in the milestone goal lacks a corresponding test. Artist enrichment beyond the newest-cover portion of FR-013 remains M3 work.

All 12 checked task IDs occur once in tasks.md and each has exactly one matching task-named commit in the supplied range. All 12 commits name their task IDs; there are no unassigned commits. The actual order matches the dependency sequence at tasks.md:133 onward, including T009 before T010, T013a before T014/T015 and T018a before pending T020. Named repository source/test paths exist; optional alternative ArtistDefaultArtwork.kt was unnecessary because T013a uses SyncDatabase.kt.

Decision headings D-1 through D-23 are unique. Task references D-19, D-21 and D-22 exist; T018a's published-POM license confirmation is recorded in D-21. Other completed M2 rows declare no new decisions. Owner Home-look acceptance, listening/Bluetooth/headset checks, toggle visual comparison and API 31–32 validation remain explicitly pending in HANDOFF:26, :44, :75 and task rows; none is represented as a device/listening PASS.

Findings:

- [LOW] specs/004-ui-extensions/HANDOFF.md:264 — T009 reports a “48-case HomeContentMatrixTest”, but HomeContentTest.kt:134–170 defines one parameterized test across UiMatrix's 24 configurations (UiMatrix.kt:46–49). It checks both carousels and captures two images within each invocation; this gives 24 test cases, not 48. A reader reconciling the future instrumentation count would expect twice the actual matrix invocations. Fix the record to say 24 parameterized cases checking both carousels (48 captures), plus eight ordinary HomeContentTest cases.
- [LOW] specs/004-ui-extensions/tasks.md:67 — T020's device class list omits deferred coverage explicitly required by HANDOFF:26: HomeContentMatrixTest, LibraryContentMatrixTest, AlbumDetailsPresentationTest, ArtistListPresentationTest, ArtistDetailsPresentationTest, PlaylistListPresentationTest, PlaylistDetailsPresentationTest, SearchPresentationTest, SearchRestorationTest, GenreComposerContentMatrixTest and existing artist/genre/composer/slider classes. Running only the task line's classes would miss that coverage; AppearanceContentTest is also listed twice. Fix by synchronizing the task line with HANDOFF's complete per-class list, or explicitly referring to that list as part of T020's required run. T020 is still unchecked, so this is planned-run documentation drift, not a completed-task or unsupported-PASS finding.
- [LOW] specs/004-ui-extensions/spec.md:132 — FR-002 still says “No new backdrop library is added”, while T018a and DECISIONS.md:123 explicitly authorize backdrop 2.0.0 for GlassToggle alone. The completed dependency task therefore conflicts with the literal requirement even though the owner decision and implementation task agree. Fix FR-002 to state D-21's toggle-only exception while retaining the existing policy for other surfaces.

| Severity | Count |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 3 |

VERDICT: PASS
RESULT: PASS
