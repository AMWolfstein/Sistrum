# Atomic implementation tasks — proposed, not started

All tasks are **PROPOSED**, unchecked and require a future implementation brief. No task here authorizes application work in Phase0. IDs U-xxx are stable; gaps are intentional. Source IDs expand to exact paths/symbols/full SHAs in [01-reference-source-map](01-reference-source-map.md). `A/` expands to `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`; expected paths marked new are proposals. Every UI task includes English+Arabic strings/plurals and RTL helpers when it introduces text/geometry, even if omitted from its path shorthand. Tests listed as new are planned names, not claimed existing.

Classification means scheduling risk: SAFE NOW = independently design/implement after a new brief, never permission to modify this shared checkout; UI ONLY WITH DEPENDENCIES = visual task after listed contracts; EXTENSION RUNTIME DEPENDENT = approved compatible backend required; POST-MIGRATION = completed002/stable seam; BLOCKED BY POLICY OR LICENSING = rights/architecture decision required. Runtime native alternatives also require owner ADR. Complexity S/M/L is relative review effort, not time estimate.

For every task: run required task checks and docs/REGRESSION.md downstream checks; preserve all existing tests and migration APIs. One task = one future reviewed commit; no commit/stage/push in this planning pass. Default rollback is a normal reviewed revert of that task's files after canceling its operations; retain user selections/manual artwork and recovery backups, never use git reset/stash/branch switching. High-risk persistence/write/analysis tasks consume earlier tests-first tasks unchanged. No silent failure/cancellation swallow or fake provider success is acceptable.


## U-001: Characterize existing UI and local feature contracts [TESTS-FIRST]

- **Status:** PROPOSED — not started.
- **Goal:** Freeze DEFAULT/nav/mini/lyrics behavior before visual extraction.
- **Source references:** [source map](01-reference-source-map.md): S01–S04/S14/S17.
- **Expected files/modules:** androidApp/src/test and src/androidTest: baseline UI contract fixtures only.
- **Dependencies:** Accepted integration-base SHA.
- **Future Spec Kit milestone:** SK004-M0.
- **Owner or agent role:** Test auditor (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Capture circle collapse + mini relocation, saved destination/back order, DEFAULT layout/actions; no production code changes.
- **Required tests:** New characterization tests plus existing AppNavigationTest, MiniPlayerTest, FullScreenPlayerTest and lyrics parser/browse tests; record baseline failures.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-001 only: Characterize existing UI and local feature contracts; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert only added characterization files; keep existing tests untouched.


## U-002: Introduce semantic UI tokens

- **Status:** PROPOSED — not started.
- **Goal:** Provide theme-aware spacing/color/shape/motion tokens.
- **Source references:** [source map](01-reference-source-map.md): S05/E05/E07.
- **Expected files/modules:** A/ui/theme/ + focused token consumers; EN/AR strings if needed.
- **Dependencies:** U-001.
- **Future Spec Kit milestone:** SK004-M1.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Light/Dark/System and font scaling retain legibility; named tokens do not alter DEFAULT player geometry.
- **Required tests:** Token/theme tests; snapshots of representative cards at EN/AR and font2.0; ThemeModeTest.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-002 only: Introduce semantic UI tokens; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-003: Adapt shared glass and opaque fallback

- **Status:** PROPOSED — not started.
- **Goal:** Centralize Haze2 rendering and accessibility effect policy.
- **Source references:** [source map](01-reference-source-map.md): S05/E05.
- **Expected files/modules:** A/ui/components/LiquidGlass.kt, theme/effect policy.
- **Dependencies:** U-002.
- **Future Spec Kit milestone:** SK004-M1.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Reduce Transparency avoids hidden blur; missing Haze/performance fallback opaque; no new backdrop library without separate decision.
- **Required tests:** Glass fallback/contrast UI tests; low-end baseline/frame comparison scheduled in U-054.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-003 only: Adapt shared glass and opaque fallback; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-004: Restyle local Home

- **Status:** PROPOSED — not started.
- **Goal:** Express SpotiFLAC Home structure using existing local data.
- **Source references:** [source map](01-reference-source-map.md): S06/M01.
- **Expected files/modules:** A/ui/components/HomeContent.kt and Home-only components.
- **Dependencies:** U-002, U-003.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Local recent/quick-pick sections and context actions survive empty/large library; no remote feed request.
- **Required tests:** Home state/interaction snapshots, offline zero-network assertion, EN/AR font matrix.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-004 only: Restyle local Home; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-005: Restyle Library overview

- **Status:** PROPOSED — not started.
- **Goal:** Add Exhale-inspired destination tiles and shelves.
- **Source references:** [source map](01-reference-source-map.md): S07/E01.
- **Expected files/modules:** A/ui/screens/LibraryContent.kt and shared library presentation.
- **Dependencies:** U-002, U-003.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Tracks/albums/artists/playlists/genres/composers/search still reachable; restore scroll and menu actions.
- **Required tests:** Library destination/action tests, empty library and restored-scroll UI tests.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-005 only: Restyle Library overview; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-006: Restyle album and track collection lists

- **Status:** PROPOSED — not started.
- **Goal:** Unify lazy collection rows/grids without changing library queries.
- **Source references:** [source map](01-reference-source-map.md): S07/E01.
- **Expected files/modules:** A/ui/screens/LibraryAlbumsContent.kt, LibraryTracksContent.kt.
- **Dependencies:** U-005.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Stable IDs/sorts/selection/queue/favorite actions and large-list virtualization preserved.
- **Required tests:** List sorting/action/selection UI fixtures, large-list scrolling snapshots.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-006 only: Restyle album and track collection lists; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-007: Restyle album details

- **Status:** PROPOSED — not started.
- **Goal:** Adapt hero/actions/track hierarchy to Exhale.
- **Source references:** [source map](01-reference-source-map.md): S08/S18/E02.
- **Expected files/modules:** A/ui/screens/AlbumDetailsContent.kt and album hero components.
- **Dependencies:** U-006.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Disc order/count/duration/track actions and local artwork fallback unchanged.
- **Required tests:** Album UI action tests, disc/missing-art/long-title fixtures, AlbumArtworkKeyTest.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-007 only: Restyle album details; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-008: Restyle artist lists and details

- **Status:** PROPOSED — not started.
- **Goal:** Create portrait/header and local catalog presentation.
- **Source references:** [source map](01-reference-source-map.md): S07/S08/S18/E02.
- **Expected files/modules:** A/ui/screens/LibraryArtistsContent.kt, ArtistDetailsContent.kt.
- **Dependencies:** U-005.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Local albums/tracks/context actions and manual image override survive absent enrichment; no dependency on online service.
- **Required tests:** Artist identity/manual-image fixtures and offline/missing-art UI tests.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-008 only: Restyle artist lists and details; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-009: Restyle playlists

- **Status:** PROPOSED — not started.
- **Goal:** Preserve local mutation and favorites behavior in new layout.
- **Source references:** [source map](01-reference-source-map.md): S07/S08/E02.
- **Expected files/modules:** A/ui/screens/LibraryPlaylistsContent.kt, PlaylistDetailsContent.kt.
- **Dependencies:** U-005.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Create/rename/delete/reorder/context/favorite/custom cover semantics retained; no remote playlist model.
- **Required tests:** Existing FavoritesArtworkTest and playlist mutation/reorder UI tests.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-009 only: Restyle playlists; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-010: Restyle local search

- **Status:** PROPOSED — not started.
- **Goal:** Present local categorized search with stable query state.
- **Source references:** [source map](01-reference-source-map.md): S08/E02.
- **Expected files/modules:** A/ui/screens/LibrarySearchContent.kt and search presentation.
- **Dependencies:** U-005.
- **Future Spec Kit milestone:** SK004-M2.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Query/keyboard/back/scroll restore and empty/errors accessible; search stays local.
- **Required tests:** Query restore/focus/category/no-network tests, Arabic text matching fixture.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-010 only: Restyle local search; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-011: Adapt floating navigation visuals

- **Status:** PROPOSED — not started.
- **Goal:** Apply glass/tokens while retaining compact geometry/state.
- **Source references:** [source map](01-reference-source-map.md): S02–S04/E07.
- **Expected files/modules:** A/ui/navigation/NavigationChrome.kt, FloatingNavigationBar.kt; App.kt only necessary wiring.
- **Dependencies:** U-001, U-003.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Circle collapse and paired mini x/y movement preserved, back/restoration/insets correct, no double RTL mirroring.
- **Required tests:** Unmodified navigation characterization + RtlLayoutTest + AppNavigationTest; compact geometry at font2.0.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-011 only: Adapt floating navigation visuals; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-012: Adapt mini-player presentation

- **Status:** PROPOSED — not started.
- **Goal:** Align mini-player with new visual foundation.
- **Source references:** [source map](01-reference-source-map.md): S03/S04/E06.
- **Expected files/modules:** A/ui/navigation/MiniPlayer.kt and presentation-only components.
- **Dependencies:** U-011.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Transport availability, tap expansion, drag dismiss/skip and collapse reposition remain correct.
- **Required tests:** MiniPlayerTest, QueueTransportAvailabilityTest, swipe/tap/RTL gesture cases.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-012 only: Adapt mini-player presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-013: Persist three player styles

- **Status:** PROPOSED — not started.
- **Goal:** Add stable DEFAULT/LARGE/IMMERSIVE preference and selector.
- **Source references:** [source map](01-reference-source-map.md): S04/S05/E03.
- **Expected files/modules:** A/settings/ player-style DataStore, AppearanceContent.kt, EN/AR strings.
- **Dependencies:** U-001, U-002.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Missing/unknown preference = DEFAULT; selector persists across recreation; no fullscreen-cover toggle or audio restart.
- **Required tests:** Preference default/unknown/recreation tests; DEFAULT characterization; Arabic completeness.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-013 only: Persist three player styles; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-014: Add LARGE player presentation

- **Status:** PROPOSED — not started.
- **Goal:** Implement approved V8-inspired floating-cover layout.
- **Source references:** [source map](01-reference-source-map.md): S04/S19/E03/E04.
- **Expected files/modules:** A/ui/navigation/ LARGE presentation component + style dispatch.
- **Dependencies:** U-013, U-019; visual mapping review.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** All existing player commands/lyrics/queue available; DEFAULT unchanged, large-art fallback accessible.
- **Required tests:** Player command parity across DEFAULT/LARGE; missing-art/font/landscape screenshots.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-014 only: Add LARGE player presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-015: Add IMMERSIVE player presentation

- **Status:** PROPOSED — not started.
- **Goal:** Implement V7-inspired full-art layout with readable scrim.
- **Source references:** [source map](01-reference-source-map.md): S04/S19/E03/E04.
- **Expected files/modules:** A/ui/navigation/ IMMERSIVE presentation + style dispatch.
- **Dependencies:** U-013, U-019.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** All transport/queue/lyrics/volume actions preserved; fallback art and Reduce Transparency legible.
- **Required tests:** Style command parity, bright/dark/missing artwork, EN/AR font screenshots.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-015 only: Add IMMERSIVE player presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-016: Adapt lyrics presentation

- **Status:** PROPOSED — not started.
- **Goal:** Use Exhale-inspired active-line emphasis without changing legacy parsing.
- **Source references:** [source map](01-reference-source-map.md): S17/E06.
- **Expected files/modules:** A/ui/navigation/FullScreenPlayerLyricsPanel.kt presentation only.
- **Dependencies:** U-003, U-001.
- **Future Spec Kit milestone:** SK004-M3.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Existing offset/bilingual/tap-seek/browse/follow remain; reduced motion readable; no false word-sync claim.
- **Required tests:** Unmodified PlayerLyricsParserTest/LyricsBrowseModeTest plus focus/scroll/reduced-motion snapshots.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-016 only: Adapt lyrics presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-017: Add Flick-inspired Library Settings presentation

- **Status:** PROPOSED — not started.
- **Goal:** Group chosen folders/scan status/cache controls around existing contracts.
- **Source references:** [source map](01-reference-source-map.md): S22/F01; working-copy D-31.
- **Expected files/modules:** A/ui/screens/ Library Settings and SettingsContent wiring; EN/AR strings.
- **Dependencies:** U-003, completed002 D-31.
- **Future Spec Kit milestone:** SK004-M10.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Required chosen-folder prompt/no-folder no-scan semantics remain; no excluded folders/About/conversion/all-files permission.
- **Required tests:** Chosen-folder/scan-progress/error/cancel UI tests; folder grant regression from final002.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-017 only: Add Flick-inspired Library Settings presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-018: Add persistent Hide Volume Bar

- **Status:** PROPOSED — not started.
- **Goal:** Expose requested preference consistently across styles.
- **Source references:** [source map](01-reference-source-map.md): S04/B01.
- **Expected files/modules:** A/settings/ player appearance preferences, player controls, EN/AR strings.
- **Dependencies:** U-013.
- **Future Spec Kit milestone:** SK004-M10.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Default false, survives restart, applies to three styles, hardware volume unaffected.
- **Required tests:** False/true/default/restart setting tests and style UI presence checks.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-018 only: Add persistent Hide Volume Bar; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-019: Define UI enrichment boundary adapters [TESTS-FIRST]

- **Status:** PROPOSED — not started.
- **Goal:** Pin read-only player/library identities and command consumption.
- **Source references:** [source map](01-reference-source-map.md): S01/S15/S18/S19.
- **Expected files/modules:** App/UI contract tests and adapter types outside player/.
- **Dependencies:** U-001, accepted002 integration base.
- **Future Spec Kit milestone:** SK004-M0.
- **Owner or agent role:** Architecture implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** No Engine/Decoder/AudioSink or writable URI exposed to UI/provider runtime; album/artist identity and unit conversions explicit.
- **Required tests:** Contract fixtures cover track changes/stale completions/missing artist IDs/duration ms→sec; PlaybackController API/renderer baseline guard.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-019 only: Define UI enrichment boundary adapters; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-020: Parse default registry without execution

- **Status:** PROPOSED — not started.
- **Goal:** Introduce strict discovery model with last-valid snapshot.
- **Source references:** [source map](01-reference-source-map.md): M04/X01.
- **Expected files/modules:** Proposed A/extensions/registry/ parser/default configuration + tests.
- **Dependencies:** U-019.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Runtime implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** All 12 pinned entries parse; invalid schema/duplicates/versions/hash/URL fail visibly; zero JS evaluation/install.
- **Required tests:** Registry matrix in 05; frozen snapshot hash and invalid fixtures.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-020 only: Parse default registry without execution; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-021: Validate packages and trust records

- **Status:** PROPOSED — not started.
- **Goal:** Create bounded ZIP/digest/manifest inspection and staging.
- **Source references:** [source map](01-reference-source-map.md): M04/M05/X01.
- **Expected files/modules:** Proposed A/extensions/packages/ verifier + tests.
- **Dependencies:** U-020.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Security implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Match registry identity/version; reject unsafe members/hash/signature/required-feature failures before any evaluation.
- **Required tests:** Traversal/symlink/bomb/duplicate/case/CRC/trust/missing entry/limit fixtures.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-021 only: Validate packages and trust records; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-022: Build restricted network/KV broker tests-first

- **Status:** PROPOSED — not started.
- **Goal:** Enforce capability checks independently of interpreter.
- **Source references:** [source map](01-reference-source-map.md): M03/M07/S11.
- **Expected files/modules:** Proposed A/extensions/broker/ policy and test harness.
- **Dependencies:** U-021.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Security implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** HTTPS/redirect/DNS/actual peer checks and output/KV quotas; no direct library/DB/file access; cancellation propagates.
- **Required tests:** SSRF/rebinding/mapped IP/secret redirect/large response/timeout/cancellation malicious fixtures; no real provider calls.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-022 only: Build restricted network/KV broker tests-first; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-023: Spike no-native isolated runtime candidates

- **Status:** PROPOSED — not started.
- **Goal:** Measure pure JVM and system sandbox feasibility.
- **Source references:** [source map](01-reference-source-map.md): M02/M03; official Android/Rhino sources in runtime audit.
- **Expected files/modules:** Isolated POC test source/module only after dependency/license approval; manifest service if authorized.
- **Dependencies:** U-022; pinned candidate license review.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Runtime implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Sync host compatibility, no reflection/direct sockets/files and kill/heap/time bounds demonstrated or explicit no-go; no production activation.
- **Required tests:** Synthetic ES/builtin/register/http lifecycle; CPU/regex/heap/crash/interrupt/reentrancy; record size/startup/RSS, no registry code until approved.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-023 only: Spike no-native isolated runtime candidates; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Remove isolated POC and its test-only dependency/config; no production data migration.


## U-024: Validate exact package compatibility profiles

- **Status:** PROPOSED — not started.
- **Goal:** Test approved Apple/YTM profiles and refusal behavior.
- **Source references:** [source map](01-reference-source-map.md): M03/M06/X02–X04.
- **Expected files/modules:** POC compatibility tests + exact-hash profile fixtures.
- **Dependencies:** U-023; package trust/security/rights approval.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Runtime auditor (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** EXTENSION RUNTIME DEPENDENT.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Mock provider responses produce verified method/output contract; Deezer feature mismatch refuses; no tokens/network downloads/library files.
- **Required tests:** Lifecycle/sync HTTP/fetch/storage fixtures, TTML output, required-feature/permission/output drift and malformed responses.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-024 only: Validate exact package compatibility profiles; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-025: Record runtime and compliance go/no-go

- **Status:** PROPOSED — not started.
- **Goal:** Choose safe feasible backend or document unsupported runtime.
- **Source references:** [source map](01-reference-source-map.md): M02–M07; runtime/security/compliance documents.
- **Expected files/modules:** specs/004-ui-extensions/ future decisions and POC measurement report.
- **Dependencies:** U-023, U-024; policy/licensing review.
- **Future Spec Kit milestone:** SK004-M4.
- **Owner or agent role:** Architecture/security reviewer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** BLOCKED BY POLICY OR LICENSING.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Exact engine/package hashes, measurements, limits/trust/rights/channel recorded; native rule preserved; no-go never labeled full compatibility.
- **Required tests:** Review POC evidence against all go/no-go criteria and policy sources; NOT RUN explicit.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-025 only: Record runtime and compliance go/no-go; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reopen documented decision with evidence; leave execution disabled.


## U-026: Specify persistence schemas and migrations [TESTS-FIRST]

- **Status:** PROPOSED — not started.
- **Goal:** Prevent data loss before extension state creation.
- **Source references:** [source map](01-reference-source-map.md): S15/S18; architecture cache table.
- **Expected files/modules:** Enrichment database schema/migration/selection reconciliation tests only.
- **Dependencies:** U-019; keep runtime-specific fields optional until U-025.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Persistence implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Legacy provider_lyrics/manual artwork remain authoritative; migration/orphan/corrupt-cache tests describe preservation; no production schema edits.
- **Required tests:** Create/open/upgrade future schema fixtures, hash-bound provenance/outbox interruption, pending recovery exclusion from cache cleanup.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-026 only: Specify persistence schemas and migrations; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-027: Implement enrichment state and quota stores

- **Status:** PROPOSED — not started.
- **Goal:** Add new private persistence without duplicating library entities.
- **Source references:** [source map](01-reference-source-map.md): S15/S18/M05.
- **Expected files/modules:** Proposed A/extensions/storage/ + enrichment DB + DataStore/KV stores.
- **Dependencies:** U-026; chosen runtime profile where applicable.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Persistence implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Explicit v1/migrations, namespaces/quotas, source-hash provenance and recovery; existing library Room untouched unless separately approved.
- **Required tests:** Make U-026 pass unchanged; corruption/quota/cache clearing/offline tests.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-027 only: Implement enrichment state and quota stores; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert new-store consumers and keep old selections; export user state, delete only disposable new caches with consent.


## U-028: Implement explicit install/enable lifecycle

- **Status:** PROPOSED — not started.
- **Goal:** Activate only trusted compatible consented packages.
- **Source references:** [source map](01-reference-source-map.md): M05; update design.
- **Expected files/modules:** Proposed A/extensions/manager/ + runtime facade bindings.
- **Dependencies:** U-021, U-025 GO, U-027.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Runtime implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** EXTENSION RUNTIME DEPENDENT.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Install defaults disabled; enable separate; disable/uninstall cancel broker generation; no hidden evaluation at discovery.
- **Required tests:** State transitions, missing approval/permission/incompatible profile, cancellation/death/late completion; no library access.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-028 only: Implement explicit install/enable lifecycle; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-029: Implement manual update and rollback

- **Status:** PROPOSED — not started.
- **Goal:** Preserve prior working code/settings on failed updates.
- **Source references:** [source map](01-reference-source-map.md): M04/M05; update design.
- **Expected files/modules:** Extension manager update journal/version pointer + tests.
- **Dependencies:** U-028.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Runtime implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** EXTENSION RUNTIME DEPENDENT.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** No automatic code update; permission delta consent; immutable versions/prior settings; disabled remains disabled; recover every activation edge.
- **Required tests:** Same-version hash mutation, revocation/downgrade/settings/crash/full-disk/rollback matrix in 08.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-029 only: Implement manual update and rollback; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Restore last verified pointer/settings; disable if revoked or restore unsafe; retain journal.


## U-030: Add Settings → Extensions UI

- **Status:** PROPOSED — not started.
- **Goal:** Expose available/installed/compatibility/permissions/errors and actions.
- **Source references:** [source map](01-reference-source-map.md): M05; visual spec.
- **Expected files/modules:** A/ui/screens/ Extensions UI, settings destination, EN/AR strings.
- **Dependencies:** U-028, U-029.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** EXTENSION RUNTIME DEPENDENT.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Default registry visible; refresh/install/enable/update/uninstall/rollback/version and trust reason accessible; no implied remote code safety.
- **Required tests:** UI state/action tests for every lifecycle state, offline snapshot, permission delta, RTL/font/accessibility.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-030 only: Add Settings → Extensions UI; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-031: Implement entity matching and provider identifiers

- **Status:** PROPOSED — not started.
- **Goal:** Avoid incorrect artist/album assignments.
- **Source references:** [source map](01-reference-source-map.md): S18/S19/X02–X04/R03.
- **Expected files/modules:** Proposed artwork/provider match repository outside sync library records.
- **Dependencies:** U-019, U-027.
- **Future Spec Kit milestone:** SK004-M6.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Namespace IDs by provider/kind; distinguish homonyms/editions; ambiguous result requires selection; tags/manual match edits invalidate appropriately.
- **Required tests:** Same-name artists/album thumbnail/compilation/Arabic transliteration/ID collision and cache invalidation fixtures.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-031 only: Implement entity matching and provider identifiers; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-032: Add artist image priority/cache and override integration

- **Status:** PROPOSED — not started.
- **Goal:** Expose fallback through existing manual artwork authority.
- **Source references:** [source map](01-reference-source-map.md): S18/X03/X04.
- **Expected files/modules:** Proposed artwork repository + priority preferences; existing manual staging adapter.
- **Dependencies:** U-031.
- **Future Spec Kit milestone:** SK004-M6.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Manual override first, editable YTM→Deezer→local order; disabled/error/offline fallback; no file-tag writes.
- **Required tests:** Priority/manual override/refresh/missing/cache corrupt/offline fixtures; ArtistArtworkCleanupTest.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-032 only: Add artist image priority/cache and override integration; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-033: Adapt approved artist-image providers

- **Status:** PROPOSED — not started.
- **Goal:** Connect compatible installed portrait methods.
- **Source references:** [source map](01-reference-source-map.md): X03/X04.
- **Expected files/modules:** Provider-specific YTM/Deezer artwork adapters.
- **Dependencies:** U-024, U-025 GO, U-028, U-032; provider rights clearance.
- **Future Spec Kit milestone:** SK004-M6.
- **Owner or agent role:** Provider implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** BLOCKED BY POLICY OR LICENSING.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Only verified artist portrait fields attach to matched identity; unsupported Deezer package shown incompatible unless separately approved narrower adapter.
- **Required tests:** Recorded getArtist schema/matching/partial failure/domain drift tests; no album thumbnail false positives.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-033 only: Adapt approved artist-image providers; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-034: Adapt approved Apple album/artist motion metadata

- **Status:** PROPOSED — not started.
- **Goal:** Resolve validated descriptors and authorized cache.
- **Source references:** [source map](01-reference-source-map.md): X02/R02.
- **Expected files/modules:** Apple adapter + bounded motion cache, outside music player.
- **Dependencies:** U-025 GO, U-028, U-031; Apple authorization/retention clearance.
- **Future Spec Kit milestone:** SK004-M6.
- **Owner or agent role:** Provider implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** BLOCKED BY POLICY OR LICENSING.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Album and artist fields distinct; absent/expired/unsupported HLS uses static; no scraped credential onboarding.
- **Required tests:** Album-tall/artist-square/wide/absent/expired/rendition/playlist redirect/domain/encryption fixtures.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-034 only: Adapt approved Apple album/artist motion metadata; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-035: Add isolated silent motion rendering

- **Status:** PROPOSED — not started.
- **Goal:** Render visible authorized motion without music interference.
- **Source references:** [source map](01-reference-source-map.md): S19/R04/E04.
- **Expected files/modules:** New UI-owned motion composable/session; no player/ engine internals.
- **Dependencies:** U-019, U-032; stable Media3 baseline; U-034 for real providers.
- **Future Spec Kit milestone:** SK004-M6.
- **Owner or agent role:** Compose/Media3 integration implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Audio track disabled, volume 0/no focus/session, one visible decoder; pause/dispose on lifecycle/power/reduced motion; synthetic assets work without provider.
- **Required tests:** Music focus/session/queue/position invariant tests; video-with-audio/background/error/disposal/rapid nav fixtures; .qa final perf.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-035 only: Add isolated silent motion rendering; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Disable motion preference and release UI video resources; retain static artwork and music state.


## U-036: Add explicit local-first lyrics search policy

- **Status:** PROPOSED — not started.
- **Goal:** Default automatic missing-lyrics network access to OFF.
- **Source references:** [source map](01-reference-source-map.md): S01/S09/S13.
- **Expected files/modules:** A/lyrics/LyricsPreferences.kt, MainActivity lyrics effect, EN/AR explanation.
- **Dependencies:** U-019.
- **Future Spec Kit milestone:** SK004-M7.
- **Owner or agent role:** Lyrics implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Existing provider booleans/selections retained; fresh+upgrade auto OFF; local fetch allowed; no network burst on library/track recomposition.
- **Required tests:** Preference migration/default + fake network call counts, local priority, same-track coalescing, existing FirstLyricTest.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-036 only: Add explicit local-first lyrics search policy; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-037: Bound built-in provider errors/retries and cache misses

- **Status:** PROPOSED — not started.
- **Goal:** Preserve two providers with cancellable typed outcomes.
- **Source references:** [source map](01-reference-source-map.md): S09–S13.
- **Expected files/modules:** A/lyrics/AndroidLyricsService.kt + coordinator/negative-cache adapter.
- **Dependencies:** U-027, U-036.
- **Future Spec Kit milestone:** SK004-M7.
- **Owner or agent role:** Lyrics implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** LRCLIB/KuGou APIs preserved; cancel propagates; body/time/retry bounded; no auth/error treated as NotFound; provider+cause logged safely.
- **Required tests:** Existing provider tests unchanged plus cancellation/large body/timeout/429/auth/24h miss/backoff/offline cases.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-037 only: Bound built-in provider errors/retries and cache misses; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-038: Consolidate lyrics candidates and previews

- **Status:** PROPOSED — not started.
- **Goal:** Add provider priority/dedup/version matching with manual selection.
- **Source references:** [source map](01-reference-source-map.md): S12/S14/S15.
- **Expected files/modules:** Lyrics coordinator/model + FindLyricsContent/preview + EN/AR strings.
- **Dependencies:** U-037.
- **Future Spec Kit milestone:** SK004-M7.
- **Owner or agent role:** Lyrics implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Multiple sources/statuses visible; preserve editions/alternative content, no title-only dedup; selection cache/provenance tied to track/content hash.
- **Required tests:** Live/remix/cover/unknown-duration/dedup/partial error/stale track/preview/selection/offline tests.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-038 only: Consolidate lyrics candidates and previews; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-039: Add installed extension lyrics adapter

- **Status:** PROPOSED — not started.
- **Goal:** Extend built-ins with verified enabled compatible lyrics providers.
- **Source references:** [source map](01-reference-source-map.md): M06/X02/S09.
- **Expected files/modules:** Lyrics provider registration/runtime adapter, not replacement service.
- **Dependencies:** U-024, U-025 GO, U-028, U-038; lyrics display/cache rights cleared.
- **Future Spec Kit milestone:** SK004-M7.
- **Owner or agent role:** Lyrics/provider implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** EXTENSION RUNTIME DEPENDENT.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Apple exact package output normalized; disabled/uninstalled/incompatible providers absent with reason; no file writes or token logs.
- **Required tests:** Recorded fetchLyrics arg/unit/plain/line/word/auth/missing/invalid/cancel fixtures, both built-in regressions.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-039 only: Add installed extension lyrics adapter; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-040: Add additive word timing model and renderer

- **Status:** PROPOSED — not started.
- **Goal:** Support genuinely timed words without losing legacy content.
- **Source references:** [source map](01-reference-source-map.md): S17/X02/E06.
- **Expected files/modules:** Lyrics structured model/parser, content-hash provenance and lyrics renderer.
- **Dependencies:** U-016, U-038; U-039 for extension outputs.
- **Future Spec Kit milestone:** SK004-M7.
- **Owner or agent role:** Lyrics/Compose implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Legacy line/bilingual/offset parser remains compatible; bad/absent word timing explicit downgrade; raw text preserved as permitted.
- **Required tests:** Independent TTML/enhanced-LRC timestamps, repeated lines/invalid order/Arabic word layout/tap seek; existing parser tests unchanged.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-040 only: Add additive word timing model and renderer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-041: Define tag preservation and recovery fixtures [TESTS-FIRST]

- **Status:** PROPOSED — not started.
- **Goal:** Freeze safe write contracts before any original-file modification.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; embedding design.
- **Expected files/modules:** metadata/src/test fixtures; app storage transaction tests only.
- **Dependencies:** completed002 D-31/:metadata; U-019.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata/storage test implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Fixtures cover all proposed formats/unsupported cases and journal edges; independent essence/art/unknown-tag hashes recorded; no writer implementation.
- **Required tests:** MP3/FLAC/Opus/Vorbis/M4A boundary+corrupt fixtures, raw hash oracles, fault injection for every publish transition.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-041 only: Define tag preservation and recovery fixtures; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert only new tests/owned fixtures; never alter existing metadata oracle expectations.


## U-042: Implement URI capability and recovery coordinator

- **Status:** PROPOSED — not started.
- **Goal:** Enable recoverable single-track publishing with explicit consent.
- **Source references:** [source map](01-reference-source-map.md): S20/S22; D-31; Android storage docs.
- **Expected files/modules:** New trusted storage coordinator/journal + D-31 grant adapter.
- **Dependencies:** U-041; completed002 storage identity contract.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Storage implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Read≠write; re-request allowed flags; no all-files access; backup/stage/verify/recover; Busy playback/analysis; incapable provider refused.
- **Required tests:** U-041 storage tests unchanged; revoke/deny/out-of-space/kill/rename/cancel/restore/refresh-pending cases and MediaStore-vs-SAF routing.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-042 only: Implement URI capability and recovery coordinator; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Disable new writes, recover all pending journals/backups first; never delete only-good original.


## U-043: Add MP3 USLT structural writer

- **Status:** PROPOSED — not started.
- **Goal:** Embed plain lyrics without audio or unrelated metadata changes.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; ID3 spec.
- **Expected files/modules:** metadata/ ID3 writer + MP3 tests.
- **Dependencies:** U-041, U-042.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Supported ID3 encodings/versions preserved; APIC/unknown frames/audio/trailers exact; unsupported flags refuse; confirmation before replacement.
- **Required tests:** U-041 MP3 tests; independent USLT roundtrip/art/audio hashes, unsynchronization/large tag/partial input fixtures.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-043 only: Add MP3 USLT structural writer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-044: Add SYLT read/write interoperability

- **Status:** PROPOSED — not started.
- **Goal:** Support native MP3 synchronization only when verified.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; ID3 spec.
- **Expected files/modules:** metadata/ SYLT reader+writer and app lyrics adapter if needed.
- **Dependencies:** U-043, U-040.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Timestamp units/encoding/language/content type defined; preserve USLT and other frames; no claim all clients support word timing.
- **Required tests:** Independent SYLT fixture/encoding/time-unit/seek/unknown frame preservation tests; reader regressions.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-044 only: Add SYLT read/write interoperability; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-045: Add FLAC lyrics comment writer

- **Status:** PROPOSED — not started.
- **Goal:** Edit comment metadata with lossless audio/block preservation.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; Vorbis comments spec.
- **Expected files/modules:** metadata/ FLAC structural writer + tests.
- **Dependencies:** U-041, U-042.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** STREAMINFO/MD5/pictures/cuesheet/seek/unknown blocks/audio unchanged; plain/LRC convention labeled accurately.
- **Required tests:** U-041 FLAC tests; metadata-growth/padding/last-block/multiple-comments/corrupt lengths and exact audio hash.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-045 only: Add FLAC lyrics comment writer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-046: Add OpusTags writer

- **Status:** PROPOSED — not started.
- **Goal:** Safely repage Opus header comments with identical audio packets.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; RFC7845.
- **Expected files/modules:** metadata/ Ogg Opus structural writer + tests.
- **Dependencies:** U-041, U-042.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Preserve pre-skip/gain/mapping/granules/packets; CRC/sequence/lacing correct; chained/multiplexed unsupported until covered.
- **Required tests:** U-041 Opus tests; continued comment pages/multichannel/header growth/CRC/truncation/packet and timing hashes.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-046 only: Add OpusTags writer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-047: Add M4A/ALAC lyrics atom writer

- **Status:** PROPOSED — not started.
- **Goal:** Edit supported MP4 lyric atoms while preserving media/layout.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; existing Mp4Tags.
- **Expected files/modules:** metadata/ MP4 writer + tests.
- **Dependencies:** U-041, U-042.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** ©lyr/freeform representation explicit; stco/co64 offset adjustments correct; covers/chapters/gapless/unknown atoms preserved; fragmented/encrypted refuse.
- **Required tests:** U-041 MP4 tests; moov before/after mdat, extended sizes/co64 growth, AAC/ALAC essence/art hashes and independent seeking checks.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-047 only: Add M4A/ALAC lyrics atom writer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-048: Add safe URI sidecar save

- **Status:** PROPOSED — not started.
- **Goal:** Write authorized UTF-8 LRC beside selected audio or explicit export.
- **Source references:** [source map](01-reference-source-map.md): S09/S16/S22; D-31.
- **Expected files/modules:** Trusted sidecar coordinator + existing reader URI adapter.
- **Dependencies:** U-041, U-042.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Storage implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Basename from trusted document, preview existing, replace only confirmed, no traversal; denied/unsupported sibling offers truthful cache/export.
- **Required tests:** Sidecar collision/multiple extensions/Arabic/deny/revoke/interruption/readback and lookup-order regression tests.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-048 only: Add safe URI sidecar save; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-049: Add single-track cache/embed/sidecar UX and refresh

- **Status:** PROPOSED — not started.
- **Goal:** Expose safe explicit writer actions without batch scope.
- **Source references:** [source map](01-reference-source-map.md): S14/S15/S20; embedding design.
- **Expected files/modules:** FindLyrics preview/write UI + trusted coordinator/library refresh adapter; EN/AR strings.
- **Dependencies:** U-038, U-042–048, U-056; format rights approved.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Lyrics/storage implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Per-format capability/rights reasons, existing-content confirmation, progress/cancel/recovery/refresh-pending status; no hidden file write on select.
- **Required tests:** Preview/action/permission denial/unsupported format/replacement confirmation/cache-only/track-ID retention/refreshed lyrics/art tests.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-049 only: Add single-track cache/embed/sidecar UX and refresh; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-050: Add entity-aware Wikipedia repository

- **Status:** PROPOSED — not started.
- **Goal:** Fetch optional attributed artist/album text independently of extensions.
- **Source references:** [source map](01-reference-source-map.md): R01; Wikimedia terms.
- **Expected files/modules:** New Wikipedia repository/match adapter and cache records.
- **Dependencies:** U-019, U-027, U-031.
- **Future Spec Kit milestone:** SK004-M9.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Page identity/disambiguation/language/revision/license/source stored; missing/ambiguous explicit; no scraped Flick/extension About restoration.
- **Required tests:** Fake summary/entity homonym/artist bio/album false-match/missing/offline/license+cache fixtures; cancellation/errors.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-050 only: Add entity-aware Wikipedia repository; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-051: Add optional Wikipedia About presentation

- **Status:** PROPOSED — not started.
- **Goal:** Show reliable text with visible attribution and controls.
- **Source references:** [source map](01-reference-source-map.md): S08/R01.
- **Expected files/modules:** Artist/album optional About UI + EN/AR strings.
- **Dependencies:** U-050, U-007, U-008.
- **Future Spec Kit milestone:** SK004-M9.
- **Owner or agent role:** Compose implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** UI ONLY WITH DEPENDENCIES.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** Opt-in provider, missing state, source link/license and cached age visible; no fabricated description or image-license assumption.
- **Required tests:** Opt-in/offline/disambiguation/attribution click/long Arabic text/accessibility UI tests.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-051 only: Add optional Wikipedia About presentation; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-052: Integrate on accepted migration baseline

- **Status:** PROPOSED — not started.
- **Goal:** Resolve presentation/provider seams without player changes.
- **Source references:** [source map](01-reference-source-map.md): S01/S19–S22; integration contract.
- **Expected files/modules:** App/MainActivity/settings/repository adapters only as agreed contract.
- **Dependencies:** completed002; accepted003 base decision; selected UI/provider tasks.
- **Future Spec Kit milestone:** SK004-M11.
- **Owner or agent role:** Migration integration reviewer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Playback APIs/decoder order/queue/session/DSP/analysis documents unchanged, URI metadata final002 contract obeyed; conflict decisions recorded.
- **Required tests:** PlaybackCommandPathGuardTest, selected Media3/queue/normalization/lyrics/artwork regressions; no motion focus/audio interference.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-052 only: Integrate on accepted migration baseline; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert integration adapter changes on review branch; retain user data and recover pending writes; no reset/stash.


## U-053: Run full host/UI/security release gate

- **Status:** PROPOSED — not started.
- **Goal:** Establish actual build/lint/test and screenshot evidence.
- **Source references:** [source map](01-reference-source-map.md): S21; docs/REGRESSION.md; test plan.
- **Expected files/modules:** Verification artifacts + specs/004 HANDOFF/report only; fixes as separate tasks.
- **Dependencies:** U-052; all selected functional tasks.
- **Future Spec Kit milestone:** SK004-M12.
- **Owner or agent role:** Test auditor (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** devQa build/host/shared/module/lint results recorded; security/runtime/preservation/offline matrices pass or release blocked; no weakened tests.
- **Required tests:** Commands in 05; full suites and new security/format tests; EN/AR theme/font/style screenshots.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-053 only: Run full host/UI/security release gate; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** No release on failure; record exact failing classes and prepare separate scoped fix.


## U-054: Run QA device accessibility/performance/recovery gate

- **Status:** PROPOSED — not started.
- **Goal:** Measure real lifecycle/focus/storage and UI behavior.
- **Source references:** [source map](01-reference-source-map.md): S04/S19/R04; NOTES device rule.
- **Expected files/modules:** Device-test fixtures/artifacts + milestone report only.
- **Dependencies:** U-053, U-055; milestone-end device authorization.
- **Future Spec Kit milestone:** SK004-M12.
- **Owner or agent role:** Device test auditor (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** This is SK004-M12’s last task, including final HANDOFF/readiness update; only .qa; frame/memory/background/power/RTL/font/storage recovery/music coexistence measured with log paths; manual listening not PASS.
- **Required tests:** Mini/full/nav/RTL + motion audio-focus tests, writer process-death/storage tests, low-end performance; record untested OEM/providers.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-054 only: Run QA device accessibility/performance/recovery gate; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Do not promote build; disable unreleased provider/motion path only through separate reviewed fix, preserve current stable app.


## U-055: Prepare release evidence and owner manual list

- **Status:** PROPOSED — not started.
- **Goal:** Prepare host/security/compliance evidence and manual checks before the final phone gate.
- **Source references:** [source map](01-reference-source-map.md): Master plan/test/security/compliance docs.
- **Expected files/modules:** specs/004 future HANDOFF/tasks/decisions and release report.
- **Dependencies:** U-053; relevant policy clearances.
- **Future Spec Kit milestone:** SK004-M12.
- **Owner or agent role:** Release auditor (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Low.
- **Acceptance criteria:** No unmeasured PASS; host logs/test counts/rights/runtime decisions/NOT RUN/manual listening list and rollback procedures linked; final phone evidence remains pending for U-054 to append.
- **Required tests:** Documentation consistency and gate-evidence audit; unresolved required scope blocks release; U-054 makes the final device/readiness update as the milestone’s last task.
- **Estimated relative complexity:** S.
- **Suggested commit boundary:** U-055 only: Prepare release evidence and owner manual list; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reopen readiness report; no source or release artifact mutation.


## U-056: Add Ogg Vorbis comment writer

- **Status:** PROPOSED — not started.
- **Goal:** Cover Vorbis separately from Opus repaging.
- **Source references:** [source map](01-reference-source-map.md): S16/S20; Vorbis comments spec.
- **Expected files/modules:** metadata/ Vorbis comment writer + tests.
- **Dependencies:** U-041, U-042; Ogg container preservation helpers from U-046.
- **Future Spec Kit milestone:** SK004-M8.
- **Owner or agent role:** Metadata implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Setup/audio packets/granules/unknown comments preserved, CRC/lacing correct, unsupported chained streams refuse.
- **Required tests:** U-041 Vorbis fixtures, large/continued headers, setup/audio hashes, malformed/chained refusal, app sync regressions.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-056 only: Add Ogg Vorbis comment writer; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-058: Add cached automatic registry checks

- **Status:** PROPOSED — not started.
- **Goal:** Check updates without downloading/executing code.
- **Source references:** [source map](01-reference-source-map.md): M04/S22; update design.
- **Expected files/modules:** Extension registry scheduler/cache and settings; EN/AR strings.
- **Dependencies:** U-020, U-027; U-029 for update badge integration.
- **Future Spec Kit milestone:** SK004-M5.
- **Owner or agent role:** Runtime implementer (one implementer; no delegation implied).
- **Risk classification:** Medium; **scheduling class:** SAFE NOW.
- **Migration conflict risk:** Medium.
- **Acceptance criteria:** Checks ON/24h, code updates OFF, conditional cache/offline/backoff; manual refresh debounce; never activate on refresh.
- **Required tests:** Clock/network fake tests for stale cache/ETag/429/backoff/duplicate jobs/zero package downloads; scheduling permission constraints.
- **Estimated relative complexity:** M.
- **Suggested commit boundary:** U-058 only: Add cached automatic registry checks; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Reviewed revert of this task only; stop its new operations first, retain existing selections/manual overrides and any recovery backup; prior local presentation/provider path remains available.


## U-100: Freeze analysis-only renderer and fallback contracts [TESTS-FIRST]

- **Status:** PROPOSED — not started.
- **Goal:** Assert M4b cannot change playback rendering or loudness contracts.
- **Source references:** [source map](01-reference-source-map.md): Owner NOTES B / D-32; S19–S22.
- **Expected files/modules:** App analysis host tests incl. KotlinAnalysisDecodersTest.kt; codecs oracle harness fixtures as needed.
- **Dependencies:** completed002; no UI dependency.
- **Future Spec Kit milestone:** SK004-A0.
- **Owner or agent role:** Analysis test implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** High.
- **Acceptance criteria:** Playback Media3PlayerFactory renderer list snapshotted; analysis own renderer order, one platform retry, cause logs/cancellation and tolerance contracts tested before wiring.
- **Required tests:** Golden expected provider order + injected failure/cancellation/end cases; lossless≤5e-5LU/lossy≤0.003LU from spec; tests-first expected failures documented.
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-100 only: Freeze analysis-only renderer and fallback contracts; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert only new tests/harness; do not edit existing 001/002 oracles.


## U-101: Port Opus CELT analysis decoder

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033a exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033a (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** codecs/.../codec/opus/, scripts/ oracle corpus, OpusCeltOracleTest, notices.
- **Dependencies:** completed002, U-100; no UI dependency.
- **Future Spec Kit milestone:** SK004-A1.
- **Owner or agent role:** Decoder implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium within codecs; none with UI.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** RFC8251 CELT + Go oracle exact hashes/tolerances; no encoder/per-block steady allocation; :codecs:test
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-101 only: Port Opus CELT analysis decoder; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033a [US3] Opus part 1 — `codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/opus/`: packet/TOC parsing, range decoder, CELT decode (MDCT via `:dsp` FFT), packet-level API taking Media3-style Opus packets + `OpusHead` (pre-skip, gain, channel mapping family 0/1); decoder code only, never the encoder files (`*enc*`, `*_encode*`, `bitwriter.go`, `analysis*.go`, `psymap.go`, `quantize.go`, `floorfit.go`, `mdctfwd.go`, `rangeenc.go`); SPDX/attribution header per file "Ported from WaxFlow codec/<name>/<files>, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe, Copyright (c) 2026 Cole Springer, MIT License" and a THIRD-PARTY-NOTICES line; oracle = WaxFlow's Go decoder output (exact PCM hashes, or the Go test's own tolerance) from the Go source in `~/.cache/sistrum-waxflow-oracle/oracle-work/src`, inputs in `~/.cache` (never in git) fetched/generated by a script under `scripts/` like the earlier `waxflow-*-corpus.py`; no per-block allocation in steady state; quick gate `:codecs:test`; RFC 8251 test vectors (`opus_testvectors-rfc8251.tar.gz`, fetched once into `~/.cache`) CELT-only vectors + host test `codecs/src/test/.../OpusCeltOracleTest.kt`


## U-102: Port Opus SILK/hybrid/multistream

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033b exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033b (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** codecs/.../codec/opus/, OpusConformanceTest, corpus/notices.
- **Dependencies:** completed002, U-100, U-101; no UI dependency.
- **Future Spec Kit milestone:** SK004-A1.
- **Owner or agent role:** Decoder implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium within codecs; none with UI.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** All 12 RFC8251 + Go oracle; family1/resampler/stereo correctness; :codecs:test
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-102 only: Port Opus SILK/hybrid/multistream; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033b [US3] Opus part 2 — SILK, hybrid, stereo prediction, multistream (family 1) and the SILK resampler; all 12 RFC 8251 vectors pass the reference comparison WaxFlow's `conformance_test.go` uses, plus Go-oracle hashes; decoder code only, never the encoder files (`*enc*`, `*_encode*`, `bitwriter.go`, `analysis*.go`, `psymap.go`, `quantize.go`, `floorfit.go`, `mdctfwd.go`, `rangeenc.go`); SPDX/attribution header per file "Ported from WaxFlow codec/<name>/<files>, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe, Copyright (c) 2026 Cole Springer, MIT License" and a THIRD-PARTY-NOTICES line; oracle = WaxFlow's Go decoder output (exact PCM hashes, or the Go test's own tolerance) from the Go source in `~/.cache/sistrum-waxflow-oracle/oracle-work/src`, inputs in `~/.cache` (never in git) fetched/generated by a script under `scripts/` like the earlier `waxflow-*-corpus.py`; no per-block allocation in steady state; quick gate `:codecs:test`; host test `OpusConformanceTest.kt`


## U-103: Port AAC LC/SBR/PS analysis decoder

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033c exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033c (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** codecs/.../codec/aac/, AacOracleTest, corpus/notices.
- **Dependencies:** completed002, U-100; no UI dependency.
- **Future Spec Kit milestone:** SK004-A2.
- **Owner or agent role:** Decoder implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium within codecs; none with UI.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** AAC-LC/HEv1/v2 raw packets+ASC Go oracle, no allocation/encoder; :codecs:test
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-103 only: Port AAC LC/SBR/PS analysis decoder; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033c [US3] AAC decoder `codecs/.../codec/aac/`: AAC-LC, HE-AAC v1 (SBR) and v2 (PS), from an AudioSpecificConfig + raw access units (as Media3 delivers them from MP4/ADTS); decoder code only, never the encoder files (`*enc*`, `*_encode*`, `bitwriter.go`, `analysis*.go`, `psymap.go`, `quantize.go`, `floorfit.go`, `mdctfwd.go`, `rangeenc.go`); SPDX/attribution header per file "Ported from WaxFlow codec/<name>/<files>, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe, Copyright (c) 2026 Cole Springer, MIT License" and a THIRD-PARTY-NOTICES line; oracle = WaxFlow's Go decoder output (exact PCM hashes, or the Go test's own tolerance) from the Go source in `~/.cache/sistrum-waxflow-oracle/oracle-work/src`, inputs in `~/.cache` (never in git) fetched/generated by a script under `scripts/` like the earlier `waxflow-*-corpus.py`; no per-block allocation in steady state; quick gate `:codecs:test`; host test `AacOracleTest.kt`


## U-104: Port MP3 and FLAC analysis decoders

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033d exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033d (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** codecs/.../codec/mp3/ and flac/, Mp3OracleTest/FlacOracleTest, corpus/notices.
- **Dependencies:** completed002, U-100; no UI dependency.
- **Future Spec Kit milestone:** SK004-A3.
- **Owner or agent role:** Decoder implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium within codecs; none with UI.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** MPEG1/2/2.5 + FLAC/STREAMINFO Go oracle; FLAC bit exact; :codecs:test
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-104 only: Port MP3 and FLAC analysis decoders; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033d [US3] MP3 `codecs/.../codec/mp3/` (MPEG-1/2/2.5 Layer III frames as Media3's Mp3Extractor delivers them) and FLAC `codecs/.../codec/flac/` (frames + STREAMINFO as Media3's FlacExtractor delivers them); decoder code only, never the encoder files (`*enc*`, `*_encode*`, `bitwriter.go`, `analysis*.go`, `psymap.go`, `quantize.go`, `floorfit.go`, `mdctfwd.go`, `rangeenc.go`); SPDX/attribution header per file "Ported from WaxFlow codec/<name>/<files>, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe, Copyright (c) 2026 Cole Springer, MIT License" and a THIRD-PARTY-NOTICES line; oracle = WaxFlow's Go decoder output (exact PCM hashes, or the Go test's own tolerance) from the Go source in `~/.cache/sistrum-waxflow-oracle/oracle-work/src`, inputs in `~/.cache` (never in git) fetched/generated by a script under `scripts/` like the earlier `waxflow-*-corpus.py`; no per-block allocation in steady state; quick gate `:codecs:test`; host tests `Mp3OracleTest.kt`, `FlacOracleTest.kt` (FLAC must be bit-exact)


## U-105: Port Vorbis analysis decoder

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033e exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033e (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** codecs/.../codec/vorbis/, VorbisOracleTest, corpus/notices.
- **Dependencies:** completed002, U-100; no UI dependency.
- **Future Spec Kit milestone:** SK004-A4.
- **Owner or agent role:** Decoder implementer (one implementer; no delegation implied).
- **Risk classification:** High; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Medium within codecs; none with UI.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** Identification/comment/setup+audio packet Go oracle; no encoder/steady allocation; :codecs:test
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-105 only: Port Vorbis analysis decoder; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033e [US3] Vorbis `codecs/.../codec/vorbis/` (identification/comment/setup headers as Media3's Ogg/Matroska extractors deliver them + audio packets); decoder code only, never the encoder files (`*enc*`, `*_encode*`, `bitwriter.go`, `analysis*.go`, `psymap.go`, `quantize.go`, `floorfit.go`, `mdctfwd.go`, `rangeenc.go`); SPDX/attribution header per file "Ported from WaxFlow codec/<name>/<files>, fork github.com/AMWolfstein/WaxFlow at b7857aff88820ad37936026421d1641e64611dbe, Copyright (c) 2026 Cole Springer, MIT License" and a THIRD-PARTY-NOTICES line; oracle = WaxFlow's Go decoder output (exact PCM hashes, or the Go test's own tolerance) from the Go source in `~/.cache/sistrum-waxflow-oracle/oracle-work/src`, inputs in `~/.cache` (never in git) fetched/generated by a script under `scripts/` like the earlier `waxflow-*-corpus.py`; no per-block allocation in steady state; quick gate `:codecs:test`; host test `VorbisOracleTest.kt`


## U-106: Wire analysis-only Kotlin renderers

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033f exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033f (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** A/analysis/OfflineDecoder.kt, AnalysisDecoderChoice.kt, analysis renderer adapters + KotlinAnalysisDecodersTest.
- **Dependencies:** completed002, U-100–105; no UI dependency.
- **Future Spec Kit milestone:** SK004-A5.
- **Owner or agent role:** Analysis integration/benchmark implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Low for UI; High within analysis.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** Own renderer list, setting-free all-Kotlin table until benchmark; retry once platform with cause; unchanged playback renderer guard; tolerance tests
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-106 only: Wire analysis-only Kotlin renderers; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033f [HIGH-RISK] [US3] Analysis-only wiring: `app/analysis/OfflineDecoder.kt` builds its own renderer list with Kotlin `SimpleDecoder` renderers (Pattern B, like `KotlinAlacAudioRenderer`) for `audio/opus`, `audio/mp4a-latm`, `audio/vorbis`, `audio/mpeg`, `audio/flac`, ahead of MediaCodec, chosen per format by a setting-free table `app/analysis/AnalysisDecoderChoice.kt` (all Kotlin until T033g decides); a Kotlin decode error on a file retries that file once with the platform decoder and logs provider + cause; playback's `Media3PlayerFactory` renderers unchanged (assert in a test); loudness results vs WaxFlow oracle within 001's tolerances (lossless ≤ 5e-5 LU, lossy ≤ 0.003 LU); no analysis-version bump unless results leave tolerance (then D-entry); host tests `test/analysis/KotlinAnalysisDecodersTest.kt`; quick gate


## U-107: Benchmark and choose analysis decoders per format

- **Status:** PROPOSED — not started.
- **Goal:** Complete moved T033g exactly, for background analysis only.
- **Source references:** [source map](01-reference-source-map.md): NOTES.md B::T033g (owner working-copy); D-32; WaxFlow pinned source per unchanged text.
- **Expected files/modules:** AnalysisThroughputBenchmarkTest, AnalysisDecoderChoice.kt, spec decisions/HANDOFF.
- **Dependencies:** completed002, U-106; no UI dependency.
- **Future Spec Kit milestone:** SK004-A6.
- **Owner or agent role:** Analysis integration/benchmark implementer (one implementer; no delegation implied).
- **Risk classification:** Critical; **scheduling class:** POST-MIGRATION.
- **Migration conflict risk:** Low for UI; High within analysis.
- **Acceptance criteria:** All unchanged original task requirements below are mandatory; oracle/provenance/performance evidence recorded, playback unchanged.
- **Required tests:** Same-run Kotlin/platform 3 medians ms/audio-second/worker per5 formats; no run>10min; full/lint/assembleDevQa + QA LoudnessAnalysis*/OfflineDecodeBenchmarkTest; SC012 report
- **Estimated relative complexity:** L.
- **Suggested commit boundary:** U-107 only: Benchmark and choose analysis decoders per format; include its necessary tests/notices and future task log, no unrelated changes.
- **Rollback strategy:** Revert this decoder/analysis-only task through normal reviewed revert; preserve oracles and prior platform table, no playback renderer edits.


**Unchanged moved task text (owner NOTES.md B):**

- [ ] T033g [US3] M4b full gate + phone benchmark: extend `androidTest/.../AnalysisThroughputBenchmarkTest.kt` with Kotlin vs platform per format (Opus, AAC, MP3, FLAC, Vorbis): ms per audio second per worker, 3 runs, median, no run over 10 min; set `AnalysisDecoderChoice` to Kotlin only for formats that beat platform in the same run, platform for the rest (D-entry with numbers); full suites, `--lint`, `assembleDevQa`; on `.qa` run the benchmark + `LoudnessAnalysis*` + `OfflineDecodeBenchmarkTest`; bisect on failure; HANDOFF report with SC-012 numbers
