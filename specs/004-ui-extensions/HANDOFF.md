# HANDOFF — 004-ui-extensions (UI part)

**Branch:** `feature/media3-migration` (shared with 002/003/005; D-2). **004 start SHA:** `9f0acac` (last commit before any 004
task; SC-012 "no `player/` change" is measured from here).

## Current status

- **Active (T021 complete, 2026-10-16):** M3 fetcher contracts authored; quick expected-fail gate PASS (`/tmp/verify-task.fpLn1M`): 17 tests compile and fail only on assertions for the absent T022 classes, 0 errors/skips; app compilation and graph PASS. Commit prepared in `.codex-commit/` for the runner on `feature/media3-migration`. Production fetcher remains unimplemented, as required by tests-first. T022 is next and must pass T021 unmodified; constructor/result/test-fake boundary documented in D-25. M2 automated gate remains complete under D-24; owner finish HOLD and manual checks remain pending.
- **Known baseline rule for every 004 gate (D-24, measured 2026-10-16):** 52 pre-004 device methods failed on the archived `9f0acac` `.qa` build. A gate passes only when host/build/lint pass and every failing device method belongs to this explicit list (fewer failures are allowed). The original 30 methods in AppNavigationTest (16), MiniPlayerTest (8), FullScreenPlayerTest (6) retain their T008 lists below; the 22 additional methods measured by T020-fix3 (including the shared-height Insight consumer check) are:
  - `AlbumDetailsContentTest`: `heroOverflowShowsAlbumActionsAndAddsOnlyNonFavoriteTracks`, `displaysPublishedYearTrackCountAndTotalDurationInHeroMetadata`.
  - `ArtistDetailsContentTest`: `artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist`, `displaysArtistSummaryAndOpensSelectedAlbum`.
  - `ComposerDetailsContentTest`: `displaysComposerSummaryAndOpensSelectedAlbum`, `composerMoreMenuUsesOrderedTracksForPlayNextAndPlaylist`.
  - `CrossfadeDurationSliderTest`: `tappingNearTheEndSelectsTheTwelveSecondMaximum`.
  - `GenreDetailsContentTest`: `genreMoreMenuUsesOrderedTracksForPlayNextAndPlaylist`, `displaysGenreSummaryAndOpensSelectedAlbum`.
  - `InsightContentTest`: `insightReplacesPlaceholderAndExposesFiltersAndExpandableTracks`.
  - `LibraryAlbumsContentTest`: `holdingAnAlbumOpensItsContextMenu`.
  - `LibraryArtistsContentTest`: `artistLongPressUsesOrderedTracksAndAddOnlyPlaylist`.
  - `LibraryComposersContentTest`: `composerOverflowUsesProvidedOrderedTrackIds`.
  - `LibraryContentTest`: `trackInfoSheetOpensFromRecentlyAddedGrid`.
  - `LibraryGenresContentTest`: `genreOverflowUsesProvidedOrderedTrackIds`.
  - `LibraryPlaylistsContentTest`: `displaysPlaylistWithLargerArtworkAndChevron`.
  - `LibraryTracksContentTest`: `trackRemainsVisibleWhileContextBottomSheetIsOpen`, `keepsTheFilterVisibleAndReportsChangesWhenAQueryIsActive`.
  - `PlaylistDetailsContentTest`: `displaysTrackCountAndDesktopStyleDurationWithoutArtistOrCopyright`, `favoritesMenuOffersEditButNotDelete`, `reorderModeReplacesTrackOptionsWithDragHandlesAndDoneAction`, `heroMoreShowsPlaylistPlaybackAndEditingActions`.
  Baseline logs: `/tmp/t020-base-device/instr-*.log` and `logcat-*.log`; per-method comparison is in the T020-fix3 report below. Baseline tests remain unchanged; no new 004 test receives an exemption. Phone-log triage treats only these methods as LOW known baseline.
- **Phase:** **004 UI M2 automated gate DONE** (T020, 2026-10-16; manual finish HOLD). **M1 DONE** (2026-10-16). T008 finished by Claude after Codex ended BLOCKED (owner rule). Final full
  gate `logs: /tmp/verify-task.Jdpta3`: unit (app + sharedLogic) PASS, assembleDevQa PASS, lint 0 errors / 72 warnings,
  graph PASS; `.qa` device: UiContractCharacterization 5/5, RtlLayout 5/5, GlassFallback 96/96, Card 1/1, UiMatrix 72/72;
  AppNavigationTest 16, MiniPlayerTest 8, FullScreenPlayerTest 6 failures = **exactly the same method sets as archived
  T003/T002** (diff 0; legacy baseline below, CLAUDE.md "Known state"), so the script's `GATE: FAIL` is the known
  baseline only. Two T003 harness bugs fixed in `androidTest/ui/testing/UiMatrix.kt` (test helper only, no assertion
  changed): output dir from the target `.qa` context (Codex's fix: the test APK context returns null), and the label
  overflow check (Claude): `hasVisualOverflow` reports overflow for every fitting label in a wrap-content button because
  the paragraph is laid out at the incoming max width (936 px) while the box is the measured size (83 px for "Play";
  device debug log 2026-10-16); now overflow = lines cut / ellipsis / a line's own width > box (RTL-safe). UiMatrixTest's
  48 negative clipping/overflow cases still fail as required. Graph-refresh "read-only index" = Codex sandbox only.
- **Next step:** Runner commits the T021 packet, then implements T022 in a fresh session using the unmodified T021 tests and D-25 transport/result boundary. Pending T020 phone-log triage remains `/tmp/verify-task.huW5GI/instr-*.log` and `logcat-*.log`. Owner checks remain: UI finish approval; light/dark/RTL/200% and landscape visuals, toggle lens/tap/drag and API 31/32 static fallback, frame statistics, real listening/Bluetooth/headset checks. Existing UI finish HOLD decisions remain HOLD; none of those manual checks is represented as PASS.
- **Scope now:** UI only (M1–M5). Decoders part of 004 (former 002 Phase 5b / M4b) comes later, after 002 M3–M6 and 003.
- **Host baseline (SC-010, T001):** app 1,464 tests (1,463 passed, 1 existing skip), shared 39 passed; total
  1,503 discovered / 1,502 passed / 0 failures / 0 errors. Before T001, stored results contained 1,454 app + 39 shared
  tests (1,493 total); the new class adds 10. Existing skip:
  `WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures` (unchanged; not claimed PASS).
- **Known baseline (now measured on `.qa`):** T008 executed the four existing classes unchanged. The three
  failing classes have exactly the same failing method sets at archived T003 `8ddcf5f5`; `git diff 7607b187 8ddcf5f5`
  confirms no production or legacy-test changes since T002. These are recorded failures, not PASS/exemptions.

  | Existing device class (unchanged) | Current `.qa` result | Reason |
  |---|---|---|
| 2026-10-15 | T004 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added `ui/theme/SistrumTokens.kt`: spacing 4/8/12/16/24/32 dp, rounded shapes 12/16/24/28 dp, ten semantic colors derived from the shipped palettes, and light-default `LocalSistrumTokens`; no theme provision until T005. Added `test/ui/theme/SistrumTokensTest.kt` (14 cases across Light/Dark): exact palette mappings/scales, opaque fallback and glass compositing, WCAG normal text >= 4.5:1 and large accent text >= 3:1 against all three supported surfaces; independent WCAG reference ratios verify the test oracle. Minimum checked normal text 6.1154:1; large accent 3.3368:1 (accent documented for large text). Impact before editing: git grep found 66 existing LocalSistrumColors consumers, palette construction only in SistrumTheme plus two test fixtures; new tokens have no existing callers. No Room, analysis documents, preferences, DecoderProvider order, PlaybackController or feature changes. All existing tests, including ThemeModeTest and characterization tests, untouched. Extra source file required: SistrumTheme.kt changes only palette visibility private -> internal to share actual values without duplication; tasks.md/HANDOFF.md record completion; both graph files generated by required autonomous step 4, never manually edited. Memory precheck: gradlew --stop (no daemons), Kotlin daemon stop, MemAvailable 4,118,932 kB. Gate attempt 1 PASS: `bash .claude/skills/verify-task/scripts/verify.sh --files '<three task Kotlin files>' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.theme.SistrumTokensTest' --tests 'com.amwolfstein.sistrum.settings.ThemeModeTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest'`; 14 + 2 + 10 = 26 passed, 0 failures/errors/skips; Kotlin compile PASS; `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,757 nodes, 34,908 edges). Logs `/tmp/verify-task.W5Lilk`. Self-review: minimal diff, no weakened/disabled tests, swallowed exceptions or contract changes; whitespace clean. NOT RUN: full host/shared suites, lint, APK assembly and device tests (milestone T008 gate); no phone/daily app touched. Decisions: none; no follow-ups beyond planned T005 wiring. |
  | `com.amwolfstein.sistrum.AppNavigationTest` | FAIL: 39 run / 16 failures | Same 16 failing methods at archived T003 (T002-equivalent production/tests) |
  | `com.amwolfstein.sistrum.ui.navigation.MiniPlayerTest` | FAIL: 14 run / 8 failures | Same 8 failing methods at archived T003 |
  | `com.amwolfstein.sistrum.ui.navigation.FullScreenPlayerTest` | FAIL: 20 run / 6 failures | Same 6 failing methods at archived T003 |
  | `com.amwolfstein.sistrum.ui.navigation.RtlLayoutTest` | PASS: 5/5 | T008 actual `.qa` execution |

- **Owner checks pending (not blocking):** LARGE style look (D-12), Home look (D-22) at the M2 phone check.

## T020 — M2 full gate and phone check (2026-10-16, COMPLETE under D-24)

Impact before editing: `git grep -n T020 -- specs/004-ui-extensions/plan.md specs/004-ui-extensions/tasks.md` confirms M2 closes with this gate. Only tasks.md (completion checkbox) and HANDOFF.md (status, evidence, log) require changes; these records are consumed by the runner and subsequent task sessions. No production/test caller changes, Room, analysis documents, preferences, DecoderProvider order, PlaybackController, schema, public API or shipped behavior touched. All source/tests remain unmodified. No new decision or architecture change; D-24 is applied exactly.

Memory prechecks: `./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'`, `/proc/meminfo`: attempt 1 MemAvailable 2,956,476 kB; attempt 2 2,871,844 kB, both >= 2 GB. Branch confirmed `feature/media3-migration`; initial working tree clean and no pending `.codex-commit/`.

Attempt 1: full gate with the 36-class T020-fix3 list, driver `/tmp/t020-final-gate.log`, logs `/tmp/verify-task.bAklE8`, INTERRUPTED (exit 130). Host/build/lint passed; completed presentation passes included playlist list 144/144, Search 72/72 + restoration 1/1, playlist details 96/96, artist details 24/24, artist list 96/96, album 72/72 and genre/composer 144/144. `/tmp` filled (2.9 GB tmpfs); verify.sh explicitly failed to write instrumentation logs for Home/Library matrices. Those incomplete logs are NOT valid final evidence; no final gate/audit PASS claimed for this attempt. Shell creation and a HANDOFF write also failed while full; subsequent git status confirmed no project change. Stopped the gate before retrying. Recovery only touched temporary artifacts: removed the two reproducible APKs under `/tmp/t020-base/androidApp/build/outputs/apk/` (archived source and baseline logs preserved), losslessly compressed `logcat-*.log` to `.log.gz` in `/tmp/verify-task.NKwmsw`, `/tmp/verify-task.4LtLF1`, `/tmp/verify-task.bAklE8` (834 MB free). Interrupted logs may already be truncated and are not claimed complete. Both installs prompted Play Protect about sending Sistrum QA to Google; selected Don't send, without changing security settings. No daily app installed/instrumented/force-stopped.

Attempt 2 (final): `bash .claude/skills/verify-task/scripts/verify.sh --files 'specs/004-ui-extensions/tasks.md specs/004-ui-extensions/HANDOFF.md' --lint --instrumented "$(cat /tmp/t020-final-classes.txt)"`. The class file contains the exact 14 distinct classes named by T020 (AppearanceContentTest is duplicated in the task line and ran once), one at a time. Full host suites, no narrowing/quick flag and no early stop. Driver `/tmp/t020-final-gate2.log`; **logs: `/tmp/verify-task.huW5GI`**, including every required class's `instr-*.log` and `logcat-*.log` for runner triage. Only `.qa` APK IDs installed/instrumented on the API 35 phone; QA build left installed.

Host app: 1,501 discovered / 1,500 passed / one unchanged `WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures` skip; shared 39/39; zero failures/errors. Gradle reused UP-TO-DATE completed full-suite results. assembleDevQa and test APK build PASS. Lint task BUILD SUCCESSFUL, XML zero errors/fatal issues, 73 warnings / 14 hints. Graph hook in gate PASS; explicit `python3 .claude/hooks/graph_refresh.py auto` PASS, not needed (no source change); no generated graph changes.

Raw verify.sh **GATE: FAIL** because it has no baseline allowlist. Strict audit `python3 /tmp/t020-final-acceptance.py /tmp/verify-task.huW5GI /tmp/t020-final-gate2.log` **GATE: PASS under D-24**, output `/tmp/t020-final-acceptance.log`. Audit checks host counts and exact skip, completed phases, lint task/XML, expected case counts for all 14 classes and every failing method plus normalized cause against `/tmp/t020-base-device` and the 52-method measured baseline. Audit helpers `/tmp/t020-final-acceptance.py` and `/tmp/t020-check-device.py` reuse the T020-fix3 checks with only the class-list/helper paths adjusted. Device **128 run / 101 passed / 27 exact baseline failures / zero new or changed failures / zero audit problems**. No result edited or exemption added.

| Required device class | Cases | Raw failures | Outside D-24 baseline |
|---|---:|---:|---:|
| UiContractCharacterizationTest | 5 | 0 | 0 |
| AppNavigationTest | 39 | 15 | 0 |
| LibraryContentTest | 6 | 0 | 0 |
| LibraryAlbumsContentTest | 9 | 1 | 0 |
| LibraryTracksContentTest | 17 | 2 | 0 |
| AlbumDetailsContentTest | 7 | 2 | 0 |
| ArtistDetailsContentTest | 7 | 2 | 0 |
| GlassToggleTest | 12 | 0 | 0 |
| AppearanceContentTest | 1 | 0 | 0 |
| IntegrationContentTest | 3 | 0 | 0 |
| LibraryPlaylistsContentTest | 7 | 1 | 0 |
| PlaylistDetailsContentTest | 6 | 4 | 0 |
| LibrarySearchContentTest | 1 | 0 | 0 |
| HomeContentTest | 8 | 0 | 0 |

### T020 final SC-001 / SC-003 evidence

The earlier BLOCKED T020 tables below are historical; this table and the completed T020-fix3 36-class table supersede their unresolved automated findings.

| Criterion / screen group | Evidence | Automated disposition |
|---|---|---|
| SC-001 host characterization | Full host suite includes unchanged characterization 10/10 | PASS |
| SC-001 device navigation / DEFAULT player / mini-player | Final characterization 5/5; AppNavigation 39 cases, 15 exact baseline failures. T020-fix3 completed MiniPlayer 14/8 baseline and FullScreenPlayer 20/6 baseline; unchanged since then | PASS under D-24 |
| SC-003 Home | Final content 8/8; completed fix3 matrix 24/24 across theme/transparency/language/font scales | PASS |
| SC-003 Library / lists | Final overview 6/6; list content only measured baseline failures; fix3 Library matrix 24/24, artist list 96/96, ArtistRow 1/1 | PASS under D-24 |
| SC-003 Album details | Final content 7 cases / 2 baseline failures; fix3 presentation 72/72 (also repeated in interrupted attempt before disk failure) | PASS under D-24 |
| SC-003 Artist / genre / composer details | Final artist 7 cases / 2 baseline failures; fix3 artist presentation 24/24, genre/composer matrix 144/144; content failures only measured baseline | PASS under D-24 |
| SC-003 Playlists / editor | Final list 7 cases / 1 baseline failure, detail 6 / 4 baseline; fix3 list presentation 144/144 and detail presentation 96/96 | PASS under D-24 |
| SC-003 Search | Final content 1/1; fix3 presentation 72/72 and restoration 1/1 (also repeated in interrupted attempt before disk failure) | PASS |
| SC-003 Settings / shared components | Final toggle 12/12, Appearance 1/1, Integration 3/3; fix3 UiMatrix 72/72 including 48 negative clipping/overflow cases, glass fallback 96/96 | PASS |

Existing `review/M2/` UI finish reviewer VERDICT PASS means no CRITICAL/HIGH finding, not visual approval; its seven finish decisions remain HOLD. Runner phone-log triage is pending by the task's explicit assignment, not performed by this implementer. NOT RUN/manual owner list: light/dark/RTL/200%/landscape visual approval; toggle lens/tap/drag and keyboard focus follow-up; API 31/32 static-fallback visuals (connected device API 35); frame statistics; real listening/Bluetooth/headset checks. Existing oracle skip requires external corpus/fixtures and remains NOT RUN. These are not claimed PASS. Existing MEDIUM/LOW review follow-ups remain deferred. No delegation, code/test edits or architecture changes.

Final self-review: each changed file is required solely to close T020 and hand off evidence; no extra tracked file. All existing tests untouched, none skipped/weakened, no feature removed or exception handling changed. `git diff --check` PASS. Commit prepared only via `.codex-commit/files.txt` and `message.txt`; no staging/commit/push performed. Runner assigns SHA. T020 complete under the task's D-24 rule; next task T021, owner finish HOLD retained.

## T008-fix2 M1 phone-log triage — 2026-10-16 (BLOCKED)

Read all six reports in `review/M1/`: phone-log-triage, kotlin-reviewer, comment-analyzer,
spec-traceability-auditor, pr-test-analyzer and silent-failure-hunter. Only phone-log-triage reports HIGH findings
(30); none reports CRITICAL. Checked every reported method and failure site against the current test source,
the saved T008 logs `/tmp/verify-task.Jdpta3`, and the archived T003 logs `/tmp/t008-bisect-t003-device`.
All three legacy test files are byte-for-byte identical to `git show 8ddcf5f5:<path>`; the failure-method sets match
at both revisions (16/8/6). `git diff 7607b187 8ddcf5f5` confirms T003 changed no production or legacy test code.
Historical instrumentation was not rerun in this task; these comparisons use the retained logs, not new baseline results.

These are confirmed test failures, not rejected findings or passing exemptions. No new M1 regression was established.
One concrete blocker is `AppNavigationTest.kt:695–700`: it requires an artwork/metadata gap of at most 24 dp, but
`ui/navigation/FullScreenPlayer.kt:335` offsets metadata by `expandedArtworkSize + 36.dp`. The same 36 dp expression
exists at the pre-004 start SHA `9f0acac`, and the full player and metadata files have no diff from that SHA.
Changing the shipped layout violates the task's MUST NOT; changing the assertion violates the test rules; changing
ADRs (including D-4's existing-test preservation rule) is also prohibited. Stop BLOCKED rather than relabeling the
failures, weakening assertions or changing shipped behavior. No partial source/test fixes were made.

**Impact before editing:** `git grep`/source and history reads traced the reported failures through App navigation,
NavigationChrome/MiniPlayer, FullScreenPlayer and their existing test fixtures. Only this HANDOFF requires an edit,
to record the blocked task, evidence and follow-ups for the runner. Preserve the runner-added unchecked T008-fix2
line in tasks.md. All host/device tests, Room, analysis documents, preference storage, DecoderProvider order and
PlaybackController contracts remain untouched. No architecture or decision change.

**Follow-ups only (MEDIUM/LOW, not fixed):** phone triage's three frame-skip warnings (30/41/31 frames), vendor
reflection/GC warnings and ABI/platform harness warnings; pr-test-analyzer's bright/dark real-Haze-source contrast
coverage and App preference-to-attached-blur coverage; silent-failure-hunter's scroll-axis/movement validation;
comment-analyzer's shared-transparency-provider and matrix-acceptance comments; traceability auditor's misplaced
T004 row and historical status wording. Some reports predate T008; no claim that their advice is still reproducible.
Owner listening/Bluetooth/headset checks remain manual and NOT RUN.

**Verification (one full-gate attempt, exit 1 / `GATE: FAIL`):** memory precheck `./gradlew --stop` found no daemons;
`pkill -f '[K]otlinCompileDaemon'` found none; MemAvailable was 3,750,196 kB (above 2 GB).

```bash
bash .claude/skills/verify-task/scripts/verify.sh \
  --files 'specs/004-ui-extensions/tasks.md specs/004-ui-extensions/HANDOFF.md' \
  --lint \
  --instrumented 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest,com.amwolfstein.sistrum.AppNavigationTest,com.amwolfstein.sistrum.ui.navigation.MiniPlayerTest,com.amwolfstein.sistrum.ui.navigation.FullScreenPlayerTest,com.amwolfstein.sistrum.ui.navigation.RtlLayoutTest,com.amwolfstein.sistrum.ui.components.GlassFallbackTest,com.amwolfstein.sistrum.ui.components.CardTest,com.amwolfstein.sistrum.ui.testing.UiMatrixTest'
```

| Check | Actual result |
|---|---|
| Full app/shared host gate | PASS; both Gradle test tasks UP-TO-DATE. Cached XML: app 1,485 discovered / 1,484 passed / 1 existing skip; shared 39/39; zero failures/errors. No fresh host test execution claimed. |
| `:androidApp:assembleDevQa` | PASS |
| `:androidApp:lintDevQa` | PASS; lint XML has 0 errors, 72 warnings, 14 hints; script prints INFO |
| `:androidApp:assembleDevQaAndroidTest` | PASS; build completed before QA installation |
| Device UiContractCharacterizationTest | PASS: 5/5 |
| AppNavigationTest | FAIL: 39 run / 16 failures; exact T003/T008 failure-method set |
| MiniPlayerTest | FAIL: 14 run / 8 failures; exact T003/T008 failure-method set |
| FullScreenPlayerTest | FAIL: 20 run / 6 failures; exact T003/T008 failure-method set |
| RtlLayoutTest | PASS: 5/5 |
| GlassFallbackTest | PASS: 96/96 |
| CardTest | PASS: 1/1 |
| UiMatrixTest | PASS: 72/72, including the existing negative clipping/overflow controls |
| Device total | 252 run / 222 passed / 30 failed; no new failing methods |
| Graph | PASS: `graph: not needed (no source change in scope)`; gate and separate `python3 .claude/hooks/graph_refresh.py auto` agree; no graph files changed |
| Whitespace / scope | `git diff --check` PASS; only HANDOFF edited by this task; runner's original tasks.md addition preserved |

**logs: `/tmp/verify-task.03BDdT`**, including all eight `instr-<Class>.log` and `logcat-<Class>.log` pairs,
`unit.log`, `build.log`, `lint.log`, `androidtest-build.log` and `graphify.log`.
Installation paused at Play Protect's optional "Send app for a security check?" dialog for **Sistrum QA**;
declined the optional Google upload using "Don't send", then the gate completed normally. The diagnostic hierarchy
is saved as `install-prompt.xml` in the logs directory (device copy under QA's own external-files directory).
Only allowlisted QA APKs were installed/instrumented; neither daily music app was launched, installed over or stopped.

SC-001 evidence: cached host characterization 10/10 and fresh device characterization 5/5, unchanged.
SC-002 evidence: cached token tests 14/14 and fresh glass fallback 96/96; retain the review's real-blur-backdrop
contrast and App preference integration coverage gaps as MEDIUM follow-ups, not a claim of full acceptance.
ArabicTranslationCompletenessTest cached XML: 2/2. The existing
`WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures` skip remains NOT RUN, never PASS.

**Self-review / remaining work:** no tests/assertions/expected values changed, disabled or deleted; no feature,
API, Room, playback, preference, decoder or exception-handling change. Every changed file is required for reporting
this task; no code fix or architecture decision was smuggled into documentation. All 30 HIGH failures remain
confirmed and unresolved; none was rejected or exempted to manufacture a pass. Fresh historical instrumentation,
further identical gate attempts and fixes requiring a shipped-layout/assertion/ADR change are NOT RUN: the saved
baseline matches exactly, and the brief explicitly requires stopping rather than making those changes.
No commit packet prepared; T008-fix2 stays unchecked. Runner/owner must resolve this scope conflict.

### Fresh-session retry — 2026-10-16 (BLOCKED)

Started with `git status --short`: only the prior HANDOFF edits and runner-added unchecked task line were present;
`.codex-commit/` was absent and the branch was `feature/media3-migration`. Kept both existing documentation edits.
Read all six M1 reports again. Independent `git show`/`git diff` checks confirm FullScreenPlayer.kt and
FullScreenPlayerMetadata.kt are unchanged from pre-004 `9f0acac`, including the 36 dp metadata offset at line 335;
AppNavigationTest.kt:695–700 still requires a gap <=24 dp. D-23 explicitly preserves DEFAULT player geometry,
and D-4 preserves existing tests. This contradiction cannot be repaired through a harness-only fix without changing
the measured contract. The brief requires stopping rather than changing shipped behavior, assertions or ADRs.
Python comparisons reconfirmed all three legacy test files are byte-identical to T003 `8ddcf5f5`, and the failed-method
sets in retained T003, T008 and prior-fix2 logs match exactly (16/8/6). These are historical results, not fresh runs.

Impact before editing: `git grep` traced App's FullScreenPlayer call and the artwork/metadata semantics consumers;
only HANDOFF needs a retry record. No source, test, data contract or decision changes. Full gate, Gradle, graph refresh
and phone check NOT RUN again: no implementation changed, the prior full gate failed on this same verified conflict,
and the brief's explicit stop rule applies. No device/daily-app action or commit packet. All HIGH findings remain
unresolved; MEDIUM/LOW follow-ups above are unchanged. `git diff --check` PASS; final status still contains only
HANDOFF and the runner's tasks.md addition; `.codex-commit/` remains absent.

### Second fresh-session retry — 2026-10-16 (BLOCKED)

Kept the existing HANDOFF edits and runner-added unchecked T008-fix2 line; these were the only changes reported
by initial `git status --short`. Branch: `feature/media3-migration`; `.codex-commit/` absent. Read all six M1 reports
and checked the 30 HIGH findings' named methods and failure sites against current test source. Python/`git show`
comparisons confirm all three legacy test files are still byte-identical to T003 `8ddcf5f5`. Retained T008 and
prior-fix2 instrumentation logs both contain the metadata-gap failure at AppNavigationTest.kt:698; no new execution
is claimed. `git diff 9f0acac -- <FullScreenPlayer.kt> <FullScreenPlayerMetadata.kt>` is empty, and `git show`
confirms the same 36 dp offset at FullScreenPlayer.kt:335 before M1. The existing test's limit is 24 dp at
AppNavigationTest.kt:695–700. FR-006 preserves DEFAULT unchanged; D-4 and D-23 preserve existing tests and geometry.
Changing semantics bounds solely to satisfy the assertion would change its measured contract, not repair that gap.
The brief's explicit stop rule still applies; no finding is rejected or exempted.

Impact before editing: `git grep` traced the App.kt:578 caller and artwork/metadata semantics declarations and
consumers. Only HANDOFF needs this retry record; no source/tests, Room, analysis documents, preferences,
DecoderProvider order, PlaybackController, architecture or ADR changes. Existing tests must remain unmodified.
MEDIUM/LOW follow-ups remain as listed above. Full gate, memory precheck, Gradle, graph refresh and phone check:
NOT RUN on this retry because the verified contract conflict requires stopping before implementation. No daily-app
or other device action; no commit packet. `git diff --check` PASS. T008-fix2 remains unchecked and unresolved.

## T008 M1 verification — 2026-10-16 (BLOCKED)

No commit prepared; T008 remains unchecked. The task's final full gate prints `GATE: FAIL`.
The only code diff is `androidTest/ui/testing/UiMatrix.kt`: use `instrumentation.targetContext` instead of
`instrumentation.context` for the external output directory. The test APK context returns null on this phone;
the target context writes under `.qa`'s external-files directory. Existing GlassFallbackTest and UiMatrixTest
assertions reproduced the failure first and remain untouched. Callers/consumers (`git grep`) are only
GlassFallbackTest and UiMatrixTest. No production files, Room, analysis documents, preferences, decoder order,
PlaybackController contracts or existing test assertions changed. Documentation files are required to record
this blocked gate and baseline. No extra improvements or architecture decisions.

| Verification | Actual result |
|---|---|
| Full host suites | PASS: app 1,485 discovered / 1,484 passed / 1 existing skip; shared 39/39; zero failures/errors |
| `assembleDevQa` | PASS |
| `lintDevQa` | PASS: 0 errors, 72 warnings, 14 hints (checked report; script prints INFO) |
| Host UiContractCharacterizationTest (T001) | PASS: 10/10, unmodified |
| Device UiContractCharacterizationTest (T002) | PASS: 5/5, unmodified |
| GlassFallbackTest | PASS after directory fix: 96/96 |
| UiMatrixTest | FAIL: 72 run / 24 failures; all `rendersConfigurationAndSavesArtifact` cases report `action label ... overflows`; 48 negative clipping/overflow checks pass |
| AppNavigationTest | FAIL: 39 run / 16 failures |
| MiniPlayerTest | FAIL: 14 run / 8 failures |
| FullScreenPlayerTest | FAIL: 20 run / 6 failures |
| RtlLayoutTest | PASS: 5/5 |
| CardTest | PASS: 1/1 |
| Graph refresh | FAIL: metadata-only restore attempts `git checkout HEAD -- graphify-out/...`, cannot create read-only Git index.lock |

**SC-001 evidence:** host 10/10 and device 5/5 characterization tests pass unmodified. FullScreenPlayerTest's
legacy failures are separately recorded above, never represented as PASS.
**SC-002 evidence:** all 14 SistrumTokensTest cases pass (both palettes, opaque/glass surfaces, normal text >=4.5:1,
large accent >=3:1 with independent WCAG reference checks). All 96 GlassFallbackTest cases pass over 24 configurations:
Light/Dark x Reduce Transparency off/on x EN/AR x font scales 1.0/1.3/2.0; assertions inspect actual attached Haze
modifiers, exercise reduction and recovery, sample opaque pixels and validate text/muted/library-label contrast.
Reduced screenshots are in `/sdcard/Android/data/com.amwolfstein.sistrum.dev.qa/files/ui-matrix/glass-fallback/`.
This evidence does not turn the failing overall gate or matrix harness into PASS.

**Commands/results and logs:**

- Before attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`; MemAvailable 4,146,800 / 3,728,708 /
  3,721,384 kB (all >=2 GB). No commit bundle existed at entry; branch `feature/media3-migration`.
- Attempt 1: `bash .claude/skills/verify-task/scripts/verify.sh --files '<HANDOFF.md tasks.md>' --lint
  --instrumented '<the seven T008 FQCNs in task order>'`: full host/build/lint PASS; characterization/RTL/Card PASS;
  legacy classes FAIL as above; GlassFallback 96/96 fail in constructor at UiMatrix.kt:60 (null external directory).
  Graph not needed before source change; `GATE: FAIL`. **logs: `/tmp/verify-task.3FsZhe`**.
- Attempt 2 after one-line fix: same full gate plus `com.amwolfstein.sistrum.ui.testing.UiMatrixTest`, allowing
  UiMatrix.kt in `--files`: host/build/lint PASS, characterization 5/5, legacy 16/8/6 failures; session was interrupted
  by usage limit before remaining classes/graph/summary. **logs: `/tmp/verify-task.N382li`**.
- Attempt 3 (final): same full gate with `--files 'androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/testing/UiMatrix.kt
  specs/004-ui-extensions/tasks.md specs/004-ui-extensions/HANDOFF.md' --lint --instrumented
  'com.amwolfstein.sistrum.ui.components.GlassFallbackTest,com.amwolfstein.sistrum.ui.testing.UiMatrixTest,
  com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest,com.amwolfstein.sistrum.AppNavigationTest,
  com.amwolfstein.sistrum.ui.navigation.MiniPlayerTest,com.amwolfstein.sistrum.ui.navigation.FullScreenPlayerTest,
  com.amwolfstein.sistrum.ui.navigation.RtlLayoutTest,com.amwolfstein.sistrum.ui.components.CardTest'`
  (FQCN argument is a single comma-separated string without whitespace). Results in table; **`GATE: FAIL`**.
  **logs: `/tmp/verify-task.mVkOyx`**, including `instr-<Class>.log`, `logcat-<Class>.log`, unit/build/lint/graphify logs.
- Read-only historical narrowing: `git archive 8ddcf5f5 | tar -x -C /tmp/t008-bisect-t003`;
  `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest` there PASS after linking existing ignored
  FFmpeg headers/libraries into the temporary archive (first build lacked artifacts; original native files untouched).
  Build log `/tmp/t008-bisect-t003-build.log`. Archived T003 is the first matrix-helper commit; only T003 changes separate
  it from T002, and none touch production or the legacy classes. Install/instrument allowlisted `.qa` IDs only, one
  failing class at a time: matrix 72/72 constructor failures; legacy 16/8/6 failures with identical failing-method sets.
  **logs: `/tmp/t008-bisect-t003-device`** (per-class instr/logcat).
- To expose archived T003 assertions beyond its directory failure, instrument UiMatrixTest with
  `-e additionalTestOutputDir /sdcard/Android/data/com.amwolfstein.sistrum.dev.qa/files/ui-matrix-baseline`.
  Same 24 rendering-overflow failures, 48 negative checks pass. This is an instrumentation argument supported by the
  unchanged helper, not a test/code modification. **logs: `/tmp/t008-bisect-t003-matrix-output`** (instr/logcat).
- Gate invoked `python3 .claude/hooks/graph_refresh.py auto`; final graph differs only in `built_at_commit`.
  Its automatic Git checkout and an index-free-intended `git restore --worktree` both hit read-only index.lock.
  Restored generated graph bytes exactly from the HEAD Git object, without graph content edits; no graph diff remains.
- After historical diagnostics, reinstalled the current branch app/test APKs with `adb install -r -g` after
  validating both application IDs with aapt2: PASS. The phone is left with the current `.qa` build.
- `git diff --check`: PASS. Self-review: no deleted/disabled/weakened assertions, changed expected values, swallowed
  errors, feature/API changes or production edits. The one-line directory change is boundary handling; 96 glass cases
  pass with unchanged assertions. All remaining observed failures are recorded, not skipped or hidden.

**NOT RUN:** owner listening/Bluetooth/headset checks (manual only); phone-log triage/final reviewer (runner responsibility);
remaining follow-up gates/fixes after the three attempts (retry limit). Existing WaxFlowOracleLoudnessTest skip remains
NOT RUN, never PASS. Only `.qa` APKs were installed/instrumented; no daily app launched, installed or force-stopped.
**Blockers:** legacy baseline failures and T003 matrix rendering fixture/overflow failures prevent a full PASS;
graph refresh's metadata restoration also fails under the read-only Git index. No assertions were weakened to obtain PASS.
Orchestrator follow-up required; no new DECISIONS entry or architecture/ADR changes.

## T020-fix3 — measured pre-004 baseline and M2 corrections (2026-10-16, READY)

Entry: clean tree, `feature/media3-migration`, no `.codex-commit/`. D-24 supplies the baseline/testing exception; this task explicitly requires `.qa` phone use. No daily package installed, instrumented or force-stopped.

Impact before editing (`git grep`, notes `/tmp/t020-fix3-impact.txt`): HomeContent feeds AppDestinationContent and Home/moved-shelf tests; LibraryContent feeds navigation/characterization and overview tests. UiMatrix feeds all 004 presentation/matrix tests and the glass/negative harness tests. ArtistRow and collection rows feed list/detail presentation checks. Only verified 004-added fixture/query/scroll errors may be corrected. No Room/schema/version, analysis documents, preferences, DecoderProvider order, PlaybackController APIs/flows, player/native code or features touched. All pre-004 tests and existing assertion strengths/expected values stay intact.

Baseline: `git archive 9f0acac | tar -x -C /tmp/t020-base`; copied ignored FFmpeg headers and arm64 libraries into the archive. Neither checkout contains local.properties; used the existing ANDROID_HOME/ANDROID_SDK_ROOT instead, without creating/editing local.properties. `./gradlew -p /tmp/t020-base :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest` PASS (35 s), `/tmp/t020-base-build.log`. aapt2 verified exactly `com.amwolfstein.sistrum.dev.qa` and `.qa.test` before both installs. Ran all 16 classes present at base that failed T020; `/tmp/t020-base-device-summary.log`, **logs: `/tmp/t020-base-device`** (per-class instr/logcat). 51 baseline failing methods, including the original 30 legacy methods; 50 of the methods that failed T020 are baseline. Base LibraryContentTest additionally fails `holdingRecentlyAddedTrackOpensContextMenu`, which now passes after the approved D-22 shelf move. Five T020 navigation regressions are the missing destination descriptions already fixed in `b3d46d24`. `UiContractCharacterizationTest` was added in 004; its destination description failure was also fixed there.

Per-method classification of every T020 failure (parameterized configurations grouped by method, with **no** baseline exception for 004-added cases):

| Class / method | Actual result at `9f0acac` / classification |
|---|---|
| `AlbumDetailsContentTest#heroOverflowShowsAlbumActionsAndAddsOnlyNonFavoriteTracks` | FAIL, same failure — baseline |
| `AlbumDetailsContentTest#displaysPublishedYearTrackCountAndTotalDurationInHeroMetadata` | FAIL, same failure — baseline |
| `AppNavigationTest#changingStackPageKeepsCompactNavigationAndMiniPlayer` | FAIL, same failure — baseline |
| `AppNavigationTest#librarySearchActionOpensTheSearchPage` | PASS — 004 regression (destination labels fixed in b3d46d24) |
| `AppNavigationTest#fullScreenPlayerUpdatesSystemBarVisibilityWhilePaused` | FAIL, same failure — baseline |
| `AppNavigationTest#reselectingInsightScrollsToTheTop` | FAIL, same failure — baseline |
| `AppNavigationTest#libraryAlbumsActionOpensTheAlbumList` | PASS — 004 regression (destination labels fixed in b3d46d24) |
| `AppNavigationTest#playbackSettingsOpenAndUpdateEqualizer` | FAIL, same failure — baseline |
| `AppNavigationTest#libraryGenresActionOpensTheGenreList` | PASS — 004 regression (destination labels fixed in b3d46d24) |
| `AppNavigationTest#fullScreenPlayerOpensAboveNavigationAndClosesWhenDraggedDown` | FAIL, same failure — baseline |
| `AppNavigationTest#libraryComposersActionOpensTheComposerList` | PASS — 004 regression (destination labels fixed in b3d46d24) |
| `AppNavigationTest#fullScreenPlayerKeepsMetadataWithArtwork` | FAIL, same failure — baseline |
| `AppNavigationTest#playbackSettingsExposePersistedCrossfadeControls` | FAIL, same failure — baseline |
| `AppNavigationTest#reselectingHomeRestoresItsRootStackAndScrollsToTheTop` | FAIL, same failure — baseline |
| `AppNavigationTest#playbackCardsRespectThePageHorizontalInset` | FAIL, same failure — baseline |
| `AppNavigationTest#homeDisplaysSyncPlaceholderWhenThereAreNoTracks` | FAIL, same failure — baseline |
| `AppNavigationTest#homeDisplaysListeningSectionsAndPlaysFromTheirSectionQueue` | FAIL, same failure — baseline |
| `AppNavigationTest#selectingLibraryDispatchesIntentAndUpdatesTheVisibleDestination` | FAIL, same failure — baseline |
| `AppNavigationTest#equalizerHeaderMenuResetsBuiltInProfile` | FAIL, same failure — baseline |
| `AppNavigationTest#equalizerHeaderMenuConfirmsDeletingUserProfile` | FAIL, same failure — baseline |
| `AppNavigationTest#draggingBackToTheSelectedDestinationDoesNotReselectIt` | FAIL, same failure — baseline |
| `AppNavigationTest#libraryArtistsActionOpensTheArtistList` | PASS — 004 regression (destination labels fixed in b3d46d24) |
| `AppNavigationTest#draggingPillMostlyOverTheNextDestinationSelectsIt` | FAIL, same failure — baseline |
| `ArtistDetailsContentTest#optionalSlotsKeepLocalAlbumAndOrderedQueueActions` | Absent at base — 004-added test, must pass |
| `ArtistDetailsContentTest#artistMoreMenuUsesOrderedTracksForPlayNextAndPlaylist` | FAIL, same failure — baseline |
| `ArtistDetailsContentTest#displaysArtistSummaryAndOpensSelectedAlbum` | FAIL, same failure — baseline |
| `ArtistDetailsContentTest#manualImageWinsAndRemovingItRestoresPortraitSlot` | Absent at base — 004-added test, must pass |
| `ArtistListPresentationTest#suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible` | Absent at base — 004-added test, must pass |
| `ArtistListPresentationTest#loadingEmptyFilteredAndContentStatesKeepArtistIdentity` | Absent at base — 004-added test, must pass |
| `ComposerDetailsContentTest#displaysComposerSummaryAndOpensSelectedAlbum` | FAIL, same failure — baseline |
| `ComposerDetailsContentTest#composerMoreMenuUsesOrderedTracksForPlayNextAndPlaylist` | FAIL, same failure — baseline |
| `CrossfadeDurationSliderTest#tappingNearTheEndSelectsTheTwelveSecondMaximum` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#lyricsAndQueuePanelsToggleAndPersistAcrossTrackChanges` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#moreOptionsUsesTheTrackContextMenuForTheCurrentTrack` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#preparingPlaybackUsesMetadataDuration` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#moreOptionsRequestsAnArtistPickerForCollaborations` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#queueStatusBadgeWaitsForQueueButtonBackgroundToFadeOut` | FAIL, same failure — baseline |
| `FullScreenPlayerTest#queueRowLongPressOpensQueueMenuWithoutAddToQueueOrCurrentTrackPlayNext` | FAIL, same failure — baseline |
| `GenreComposerContentMatrixTest#composerListLoadingEmptyFilteredAndContentStates` | Absent at base — 004-added test, must pass |
| `GenreComposerContentMatrixTest#composerLongNameWrapsAndKeepsOpenActionVisible` | Absent at base — 004-added test, must pass |
| `GenreComposerContentMatrixTest#genreListLoadingEmptyFilteredAndContentStates` | Absent at base — 004-added test, must pass |
| `GenreComposerContentMatrixTest#genreDetailsMissingEmptyAndAlbumStates` | Absent at base — 004-added test, must pass |
| `GenreComposerContentMatrixTest#composerDetailsMissingEmptyAndAlbumStates` | Absent at base — 004-added test, must pass |
| `GenreComposerContentMatrixTest#genreLongNameWrapsAndKeepsOpenActionVisible` | Absent at base — 004-added test, must pass |
| `GenreDetailsContentTest#genreMoreMenuUsesOrderedTracksForPlayNextAndPlaylist` | FAIL, same failure — baseline |
| `GenreDetailsContentTest#displaysGenreSummaryAndOpensSelectedAlbum` | FAIL, same failure — baseline |
| `HomeContentTest#suppliedScrollStateIsUsedWhenHomeReturns` | Absent at base — 004-added test, must pass |
| `LibraryAlbumsContentTest#holdingAnAlbumOpensItsContextMenu` | FAIL, same failure — baseline |
| `LibraryAlbumsContentTest#tenThousandAlbumsKeepListAndGridCompositionBounded` | Absent at base — 004-added test, must pass |
| `LibraryArtistsContentTest#artistLongPressUsesOrderedTracksAndAddOnlyPlaylist` | FAIL, same failure — baseline |
| `LibraryComposersContentTest#composerOverflowUsesProvidedOrderedTrackIds` | FAIL, same failure — baseline |
| `LibraryContentTest#libraryScrollRestoresAfterRecreation` | Absent at base — 004-added test, must pass |
| `LibraryGenresContentTest#genreOverflowUsesProvidedOrderedTrackIds` | FAIL, same failure — baseline |
| `LibraryPlaylistsContentTest#displaysPlaylistWithLargerArtworkAndChevron` | FAIL, same failure — baseline |
| `LibraryTracksContentTest#trackRemainsVisibleWhileContextBottomSheetIsOpen` | FAIL, same failure — baseline |
| `LibraryTracksContentTest#tenThousandTracksKeepCompositionBoundedAndRestoreScrolledPosition` | Absent at base — 004-added test, must pass |
| `LibraryTracksContentTest#keepsTheFilterVisibleAndReportsChangesWhenAQueryIsActive` | FAIL, same failure — baseline |
| `MiniPlayerTest#draggingMiniPlayerDownDismissesIt` | FAIL, same failure — baseline |
| `MiniPlayerTest#hidesWhenPlaybackIsIdleOrFailed` | FAIL, same failure — baseline |
| `MiniPlayerTest#pausedStateUsesPlayAndPreparingDisablesTheTransportToggle` | FAIL, same failure — baseline |
| `MiniPlayerTest#swipingMetadataLeftSkipsToTheNextTrackWithNoVerticalPlayerAction` | FAIL, same failure — baseline |
| `MiniPlayerTest#swipingMetadataRightReturnsToThePreviousTrack` | FAIL, same failure — baseline |
| `MiniPlayerTest#metadataSwipeUsesTheLatestQueueNavigationAvailability` | FAIL, same failure — baseline |
| `MiniPlayerTest#newlyStartedPlaybackSlidesMiniPlayerUpFromNavigation` | FAIL, same failure — baseline |
| `MiniPlayerTest#draggingMiniPlayerUpOpensTheFullScreenPlayer` | FAIL, same failure — baseline |
| `PlaylistDetailsContentTest#displaysTrackCountAndDesktopStyleDurationWithoutArtistOrCopyright` | FAIL, same failure — baseline |
| `PlaylistDetailsContentTest#favoritesMenuOffersEditButNotDelete` | FAIL, same failure — baseline |
| `PlaylistDetailsContentTest#reorderModeReplacesTrackOptionsWithDragHandlesAndDoneAction` | FAIL, same failure — baseline |
| `PlaylistDetailsContentTest#heroMoreShowsPlaylistPlaybackAndEditingActions` | FAIL, same failure — baseline |
| `PlaylistDetailsPresentationTest#favoritesKeepLocalizedIdentityAndProtectedActions` | Absent at base — 004-added test, must pass |
| `PlaylistListPresentationTest#favoritesEditorKeepsNameHiddenAndClearsOnlyTheCover` | Absent at base — 004-added test, must pass |
| `PlaylistListPresentationTest#createRejectsWhitespaceAndReturnsTrimmedLocalNameWithoutCover` | Absent at base — 004-added test, must pass |
| `PlaylistListPresentationTest#contextActionsKeepQueueOrderRenameAndConfirmedDelete` | Absent at base — 004-added test, must pass |
| `PlaylistListPresentationTest#narrowLongNameUsesTwoLinesAndKeepsOpenTarget` | Absent at base — 004-added test, must pass |
| `PlaylistListPresentationTest#loadingEmptyAndContentKeepFavoritesAndClickIdentity` | Absent at base — 004-added test, must pass |
| `SearchPresentationTest#controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone` | Absent at base — 004-added test, must pass |
| `UiContractCharacterizationTest#destinationAndChildPageSurviveActivityRecreationAndTabRoundTrip` | Absent at base — 004-added test, must pass |
| `HomeContentMatrixTest#heroRemainsClickableAtEveryFontAndCarouselsMirror` (T020 focused run) | Absent at base — 004-added test, must pass |
| `InsightContentTest#insightReplacesPlaceholderAndExposesFiltersAndExpandableTracks` (extra shared-height consumer) | FAIL, same empty-series exception — baseline |

Extra consumer check: InsightContent also uses discCardHeight. Its unchanged `insightReplacesPlaceholderAndExposesFiltersAndExpandableTracks` fails with `IllegalArgumentException: Series can’t be empty` in Vico's chart model on both current and archived `9f0acac` APKs. Archived Insight 4 run / 1 failure, `/tmp/t020-insight-base-summary.log` and `/tmp/t020-base-device/instr-InsightContentTest.log` / `logcat-InsightContentTest.log`; application IDs checked again before archival installation. This adds one measured pre-004 method to the explicit list (52 total); no Insight code/test changed. Current APKs reinstalled for subsequent checks.

Corrections (D-24; every original assertion/expected value retained):

- `HomeContentMatrixTest#heroRemainsClickableAtEveryFontAndCarouselsMirror`: unchanged RED test fails 16/24 before production correction. Diagnostic shows subtitle `Artist 0` at font 1.3 allocated 61 px for an 81 px paragraph, and font 2.0 allocated 92 px for 112 px. `DiscCard.kt` now measures the actual line box rather than assuming requested lineHeight equals the rendered box; Home and Insight are the two callers of `discCardHeight` (`git grep`). Insight is an extra verification consumer, not a separate feature change. Final Home matrix GREEN: 24/24 after the measured-height correction, `/tmp/t020-fix3-resume-focused-device/instr-HomeContentMatrixTest.log`.
- `HomeContentTest#suppliedScrollStateIsUsedWhenHomeReturns`: added a 320 dp viewport so the existing shelf fixture can actually scroll to its requested index; the unconstrained fixture fits and Compose correctly clamps the state back to the start. Both collapsed/large-title assertions unchanged. Diagnostic PASS.
- `LibraryContentTest#libraryScrollRestoresAfterRecreation`: scroll the LazyColumn to index 6 before locating its uncomposed last item. Existing display/bounds/restoration assertions unchanged. Diagnostic PASS.
- `LibraryAlbumsContentTest#tenThousandAlbumsKeepListAndGridCompositionBounded` and `LibraryTracksContentTest#tenThousandTracksKeepCompositionBoundedAndRestoreScrolledPosition`: select the real scrollable descendant of each tagged wrapper. The wrappers have no ScrollToIndex action. All 10,000-item identity, composition bounds, first-key, display and restoration assertions unchanged. Diagnostic PASS for both.
- `ArtistDetailsContentTest#optionalSlotsKeepLocalAlbumAndOrderedQueueActions` and `#manualImageWinsAndRemovingItRestoresPortraitSlot`: query the options icon by its existing content description, and supply the application popup host needed by AnchoredPopupMenu. All queue order, destination, image-priority, color and add/remove assertions unchanged. Diagnostic class has only its two measured pre-004 failures.
- `ArtistListPresentationTest#suppliedPortraitIsRoundAndLongNameKeepsOpenActionVisible`: inspect exact portrait pixels before the clicks that start the row ripple; the old ordering captured the transient pressed overlay. All exact color/shape/name-line/callback assertions retained.
- `UiMatrix.kt`: explicitly preserve localized LocalResources so Dialog stringResource calls use the matrix locale, rather than the Activity's system-English resources. Before this fix, only the 12 Arabic favorites-editor methods fail looking up the unchanged Arabic clear-cover description; the other 132 playlist-list cases pass. Also map the entire un-clipped local rectangle into root coordinates. The old origin-plus-width construction treats a mirrored glyph's right edge as its left edge and reports an otherwise visible arrow as clipped. Both containment assertions and label-overflow checks remain; all 72 existing UiMatrixTest cases PASS, including all 48 negative clipping/overflow cases. Error messages now include measured text height to diagnose real overflow.
- `SearchPresentationTest#controlledQueryClearLoadingAndEmptyStatesKeepFocusUntilDone`: assert exactly empty EditableText after clear. The merged node also contains the visible placeholder Text, so assertTextEquals on all merged text was the wrong field; all other query/focus/loading/no-results assertions remain.
- `PlaylistListPresentationTest` (create/favorites/context editor methods) and `PlaylistDetailsPresentationTest#favoritesKeepLocalizedIdentityAndProtectedActions`: use the real ComponentActivity test rule and preserve its LocalActivityResultRegistryOwner in UiMatrix before replacing LocalContext with a localized ConfigurationContext. The latter is not an Activity wrapper; merely adding the Activity rule still reproduced the same 24/96 detail-editor failures. This supplies the actual application host, with no assertion or expected-value changes.

Completed verification and intermediate history follow. Memory precheck `./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'`, MemAvailable 3,716,552 kB before baseline build. `./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest --continue`, `/tmp/t020-fix3-full-host.log`, completed normally in 7m 15s: app 1,501 discovered / 1,500 passed / one existing WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures skip; shared 39/39. No full host run stopped early. Subsequent memory precheck 3,617,848 kB before the final full gate. Intermediate test APK compile diagnostics: missing assert import and nullable registry-provider type error corrected; logs `/tmp/t020-fix3-test-build.log`, `/tmp/t020-fix3-registry-build.log`; successful diagnostic compilation `/tmp/t020-fix3-test-build2.log` (56 s), Home production build `/tmp/t020-fix3-home-build.log` (1m 13s). Low-memory diagnostic builds used `--no-daemon -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` to avoid interrupting the running full host suite.

Diagnostic device run (before final Home height/registry-context corrections were installed): **logs `/tmp/t020-fix3-diagnostic-device`**, `/tmp/t020-fix3-diagnostic-summary.log`. UiMatrix 72/72, ArtistList 96/96, GenreComposer 144/144, Search 72/72, Home 8/8, LibraryContent 6/6; album/track/artist detail classes retain only measured baseline failures. Home matrix RED 16/24 and playlist registry failures informed the subsequent fixes; not represented as passes.

After resuming: `/tmp/t020-fix3-resume-build.log` PASS (42 s); `/tmp/t020-fix3-resume-focused-device` Home matrix 24/24, playlist list 144 run / 12 Arabic fixture failures, Insight 4 run / one measured baseline. `/tmp/t020-fix3-resources-build.log` PASS (40 s). The single-method parameterized diagnostic selected **0 tests** (`/tmp/t020-fix3-localized-editor-summary.log`); NOT RUN, no pass claimed. The full class is rerun instead.

Temporary evidence storage: `/tmp` reached 86% usage during the full gate. Moved this task's completed baseline/diagnostic log directories to `/home/amwolfstein/.cache/sistrum-t020-fix3-evidence/` and left symlinks at their original `/tmp` paths; the cited instr/logcat files remain readable at every reported path. No active gate logs or unrelated artifacts moved or deleted.

Required file reasons / diff review: `DiscCard.kt` fixes the observed Home label truncation in the shared height calculation; `UiMatrix.kt` corrects transformed clipping geometry and preserves the host registry/localized resources; `HomeContentTest.kt` supplies a scrollable restoration viewport; `LibraryContentTest.kt` composes the lazy restoration target; `LibraryAlbumsContentTest.kt` / `LibraryTracksContentTest.kt` target the actual scrollable list; `ArtistDetailsContentTest.kt` corrects only the two 004-added action fixtures; `ArtistListPresentationTest.kt` captures portrait pixels before the press ripple; `PlaylistListPresentationTest.kt` / `PlaylistDetailsPresentationTest.kt` supply the real activity host; `SearchPresentationTest.kt` checks the exact editable field rather than its merged placeholder. `tasks.md` / `HANDOFF.md` are task records; graph files only if generated by the required hook. No source/test cleanup beyond these defects.

Self-review: four pre-004 artist-detail, five album-list, four library-overview and fourteen track-list test bodies compared against HEAD and unchanged; other edited test classes were added in 004. No assertion removed/disabled/weakened or expected value changed; exact pixel, list identity, queue order, restore bounds and callback assertions retained. The original UiMatrix negative clipping/overflow assertions remain intact. No feature/API/schema/default/decoder/player/ADR changes or exception swallowing. `git diff --check` PASS. Existing MEDIUM/LOW M2 review follow-ups remain with T020/owner review; none added to this task's scope.

Gate attempt 2 exposed one new, non-baseline failure after the isolated 144/144 playlist pass: `PlaylistListPresentationTest#contextActionsKeepQueueOrderRenameAndConfirmedDelete[Dark/reduce=true/ar/font=1.3]`, expected Renamed / actual null. Logs `/tmp/verify-task.4LtLF1/instr-PlaylistListPresentationTest.log` and `logcat-PlaylistListPresentationTest.log`; IME request/show at 12:36:51.271–.659, window relayout .881 before the failed tap. The real Activity fixture permits the keyboard to move the dialog; the test tapped before settling. Corrected only that 004-added callback-contract fixture by requiring Save to be displayed and invoking its accessibility OnClick action, asserting that the action returns true. The test already invokes menu long-press by semantics. This avoids a coordinate sampled during asynchronous IME relayout while still requiring the real Save handler and exact rename/id/artwork/queue/delete results; physical Save taps and 48 dp targets remain covered by create/favorites editor cases. No assertion/expected value removed, retries, longer timeouts or baseline exemption added. An attempted Espresso keyboard-close import did not compile because Espresso is not exposed to the test compile classpath (`/tmp/t020-fix3-keyboard-build.log`); removed it, no dependency added. Gate 2 remains recorded as FAIL; final gate 3 verifies the correction below. Gate 2 otherwise completes all classes, with UiMatrix 72/72, GlassFallback 96/96 and graph PASS/changed (12,021 nodes / 36,125 edges); full logs `/tmp/verify-task.4LtLF1`. The correction compiles (`/tmp/t020-fix3-save-action-build.log`, 12 s). Final gate attempt 3 runs all 36 classes with playlist list first, `/tmp/t020-fix3-gate3.log`; no fourth attempt permitted. Gate 3 memory precheck 3,320,212 kB; logs `/tmp/verify-task.SSzzLz`; corrected playlist list GREEN 144/144. All remaining classes completed; final disposition below.

Final localized editor verification: `/tmp/t020-fix3-final-editor-device/instr-PlaylistListPresentationTest.log` and `logcat-PlaylistListPresentationTest.log`: **144/144 PASS**, all 24 configurations, exact existing Arabic clear-cover description preserved. Final Home matrix 24/24 PASS as above. Gate attempt 2 starts after these completed checks (no concurrent instrumentation), memory precheck 3,345,372 kB; full host/build/lint plus all 36 classes, `/tmp/t020-fix3-gate2.log`.

The final gate includes all 31 original T020 classes plus HomeContentMatrixTest, LibraryContentMatrixTest, UiMatrixTest, GlassFallbackTest and InsightContentTest (36 classes). Temporary `/home/amwolfstein/.gradle/init.d/t020-fix3-device-serialization.gradle` only delayed the final test-APK build until the already-running diagnostic instrumentation finished, preventing two runners from using the phone concurrently; it was removed after the usage-limit interruption and is not a project change. Gate attempt 1 logs `/tmp/verify-task.LxTO5v`: host/build PASS, lint 0 errors / 73 warnings / 14 hints. The usage limit terminated the runners before the final test APK build/device phase; that phase is INCOMPLETE, not PASS. The preliminary PlaylistList diagnostic was likewise interrupted before its completed JUnit summary. Final focused verification and gate retry completed as recorded below.

### Final T020-fix3 gate and disposition

Command: `bash .claude/skills/verify-task/scripts/verify.sh --files '<the 11 changed Kotlin files, tasks.md, HANDOFF.md and two generated graph files>' --lint --instrumented '<all 36 classes below; playlist list first>'`. Full command arguments are preserved in `/tmp/t020-fix3-gate3-files.txt` and `/tmp/t020-fix3-gate3-classes.txt`. Driver `/tmp/t020-fix3-gate3.log`; **logs: `/tmp/verify-task.SSzzLz`**, per-class `instr-*.log` and `logcat-*.log` included for runner triage. Only allowlisted `.qa` IDs installed/instrumented, API 35; current branch QA APKs left installed. No daily app installed, instrumented or force-stopped.

- Host: app 1,501 discovered / 1,500 passed / one unchanged oracle skip; shared 39/39; zero failures/errors. The complete slow host suite finished normally, no exclusions or early stop. Final gate reused completed full-suite results where Gradle marked them UP-TO-DATE.
- Build: assembleDevQa and test APK build PASS. Lint: BUILD SUCCESSFUL, **0 errors / 73 warnings / 14 hints** (XML inspected; the gate only prints INFO).
- Device: **1,051 executed / 1,001 passed / 50 failures**, all failures exact methods and normalized causes already measured on `9f0acac`; no new/changed failure, no lost cases, no 004-added failing method. The accepted list contains 52 methods; two now pass. Characterization 5/5, Home matrix 24/24, playlist detail 96/96, playlist list 144/144, artist list 96/96, genre/composer 144/144, UiMatrix 72/72 (including all 48 negative clipping/overflow cases), glass fallback 96/96.
- Graph: gate PASS/changed; explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed, 12,021 nodes / 36,125 edges; `/tmp/t020-fix3-final-graph.log`. Both graph files generated by the hook, never manually edited.
- Raw verify.sh: **GATE: FAIL**, solely its 50 measured pre-004 device failures. D-24 acceptance: **GATE: PASS**. `/tmp/t020-fix3-final-acceptance.py` checks completed phase banners, full host counts/skip identity, lint XML/task success, each class's executed count against the completed previous run, and every failure's method plus normalized cause against the archived logs. Audit `/tmp/t020-fix3-final-acceptance.log`: 36 classes, 1,051 cases, 50 failures, **0 problems**. No results/logs edited or assertions relaxed to produce this disposition.

| Final device class | Executed | Raw failures | Disposition |
|---|---:|---:|---|
| `UiContractCharacterizationTest` | 5 | 0 | PASS |
| `AppNavigationTest` | 39 | 15 | Measured baseline only |
| `MiniPlayerTest` | 14 | 8 | Measured baseline only |
| `FullScreenPlayerTest` | 20 | 6 | Measured baseline only |
| `LibraryContentTest` | 6 | 0 | PASS |
| `LibraryAlbumsContentTest` | 9 | 1 | Measured baseline only |
| `LibraryTracksContentTest` | 17 | 2 | Measured baseline only |
| `AlbumDetailsContentTest` | 7 | 2 | Measured baseline only |
| `ArtistDetailsContentTest` | 7 | 2 | Measured baseline only |
| `GlassToggleTest` | 12 | 0 | PASS |
| `AppearanceContentTest` | 1 | 0 | PASS |
| `IntegrationContentTest` | 3 | 0 | PASS |
| `LibraryPlaylistsContentTest` | 7 | 1 | Measured baseline only |
| `PlaylistDetailsContentTest` | 6 | 4 | Measured baseline only |
| `LibrarySearchContentTest` | 1 | 0 | PASS |
| `HomeContentTest` | 8 | 0 | PASS |
| `CrossfadeDurationSliderTest` | 2 | 1 | Measured baseline only |
| `SearchPresentationTest` | 72 | 0 | PASS |
| `SearchRestorationTest` | 1 | 0 | PASS |
| `PlaylistDetailsPresentationTest` | 96 | 0 | PASS |
| `PlaylistListPresentationTest` | 144 | 0 | PASS |
| `ArtistDetailsPresentationTest` | 24 | 0 | PASS |
| `ArtistListPresentationTest` | 96 | 0 | PASS |
| `LibraryArtistsContentTest` | 3 | 1 | Measured baseline only |
| `ArtistRowTest` | 1 | 0 | PASS |
| `AlbumDetailsPresentationTest` | 72 | 0 | PASS |
| `LibraryGenresContentTest` | 3 | 1 | Measured baseline only |
| `LibraryComposersContentTest` | 3 | 1 | Measured baseline only |
| `GenreDetailsContentTest` | 4 | 2 | Measured baseline only |
| `ComposerDetailsContentTest` | 4 | 2 | Measured baseline only |
| `GenreComposerContentMatrixTest` | 144 | 0 | PASS |
| `HomeContentMatrixTest` | 24 | 0 | PASS |
| `LibraryContentMatrixTest` | 24 | 0 | PASS |
| `UiMatrixTest` | 72 | 0 | PASS |
| `GlassFallbackTest` | 96 | 0 | PASS |
| `InsightContentTest` | 4 | 1 | Measured baseline only |

NOT RUN (outside T020-fix3; retained for T020/owner): API 31/32 fallback visuals (only API 35 connected), owner light/dark/RTL/large-font/landscape and toggle lens/tap/drag approval, frame statistics, real listening/Bluetooth/headset checks, runner UI finish review / phone-log triage. The single existing WaxFlow oracle host skip requires external oracle fixtures/corpus and remains NOT RUN, never a pass. No outstanding regression or fix3 implementation work. Decisions: D-24 applied; no new decision, ADR or architecture change. Pre-existing MEDIUM/LOW review follow-ups remain deferred as recorded in T020-fix.

Commit prepared, not staged/committed/pushed: `.codex-commit/files.txt` names all 15 paths; `.codex-commit/message.txt` supplies the task/verification/trailer. T020-fix3 checked; T020 unchanged/unchecked. Self-review and `git diff --check` PASS.

## T020 — M2 full gate and phone check (2026-10-16, BLOCKED)

Initial state: clean tree, branch `feature/media3-migration`, no `.codex-commit/`.
Impact before edits: `HomeContent` is consumed by AppDestinationContent and Home/Library tests;
`LibraryContent` is consumed by AppDestinationContent, LibraryContentTest/LibraryContentMatrixTest and
navigation characterization/AppNavigationTest. No Room, analysis documents, preferences,
DecoderProvider ordering, PlaybackController APIs/StateFlows or player code changed. All tests stay unmodified.

Attempt 1: memory precheck `./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'` (none),
MemAvailable 3,901,532 kB. Ran
`bash .claude/skills/verify-task/scripts/verify.sh --files 'specs/004-ui-extensions/tasks.md specs/004-ui-extensions/HANDOFF.md' --lint --instrumented '<31 FQCNs listed by the per-class table below>'`.
Logs: `/tmp/verify-task.NKwmsw`, including `unit.log`, `build.log`, `lint.log`,
`instr-<class>.log` and `logcat-<class>.log` for every listed class.

- App full host suite: INCOMPLETE (gate phase FAIL after operator stop). Its binary results stopped advancing near
  `KotlinDecodingExtractorLifecycleTest.initialSeekIsAppliedAndSeekingAfterEofResumesTheCodec`;
  after several minutes without flushed results, `./gradlew --stop` stopped the daemon. A later thread dump showed active DSD decoding, so this was not a verified deadlock. This is a
  last-observed test, not a verified root cause. Existing 22-test XML was stale, not full-suite evidence.
  `jcmd 211 Thread.print` could not attach (nonexistent PID in this tool namespace); diagnostic
  `/tmp/t020-worker-211.txt`. No decoder/test changes or exclusions made.
- Shared full host suite: 39 tests, zero failures/errors/skips.
- `assembleDevQa`: PASS.
- Lint: FAIL, 1 error / 73 warnings / 14 hints. `HomeContent.kt:219` used non-observable
  `java.util.Locale.getDefault()` in composition. Corrected to `LocalConfiguration.current.locales[0]`.
  Gate reports lint as INFO, so its final banner alone does not establish lint success.
- Device: API 35, QA IDs only; 831 tests/cases, 314 failures. No daily app installed,
  instrumented or force-stopped. Three original legacy classes compared to retained M1 logs
  `/tmp/verify-task.Jdpta3`: MiniPlayer's 8 and FullScreenPlayer's 6 failing methods match exactly;
  AppNavigation contains all 16 baseline methods plus five new Library destination lookup failures.
- Unchanged characterization: 4/5; `destinationAndChildPageSurviveActivityRecreationAndTabRoundTrip`
  cannot find Artists content description. `LibraryContent` replaced ActionListRow but omitted
  its label semantics. Restored the same merged content description on destination rows;
  this existing failing test provides regression coverage before the fix.
- Graph: PASS, changed (12,018 nodes / 36,102 edges); generated graph files changed only via hook.
- Final banner: `GATE: FAIL`.

| Device class (attempt 1, before corrections were rebuilt) | Run | Failures |
|---|---:|---:|
| `AlbumDetailsContentTest` | 7 | 2 |
| `AlbumDetailsPresentationTest` | 72 | 0 |
| `AppNavigationTest` | 39 | 21 |
| `AppearanceContentTest` | 1 | 0 |
| `ArtistDetailsContentTest` | 7 | 4 |
| `ArtistDetailsPresentationTest` | 24 | 0 |
| `ArtistListPresentationTest` | 96 | 36 |
| `ArtistRowTest` | 1 | 0 |
| `ComposerDetailsContentTest` | 4 | 2 |
| `CrossfadeDurationSliderTest` | 2 | 1 |
| `FullScreenPlayerTest` | 20 | 6 |
| `GenreComposerContentMatrixTest` | 144 | 72 |
| `GenreDetailsContentTest` | 4 | 2 |
| `GlassToggleTest` | 12 | 0 |
| `HomeContentTest` | 8 | 1 |
| `IntegrationContentTest` | 3 | 0 |
| `LibraryAlbumsContentTest` | 9 | 2 |
| `LibraryArtistsContentTest` | 3 | 1 |
| `LibraryComposersContentTest` | 3 | 1 |
| `LibraryContentTest` | 6 | 1 |
| `LibraryGenresContentTest` | 3 | 1 |
| `LibraryPlaylistsContentTest` | 7 | 1 |
| `LibrarySearchContentTest` | 1 | 0 |
| `LibraryTracksContentTest` | 17 | 3 |
| `MiniPlayerTest` | 14 | 8 |
| `PlaylistDetailsContentTest` | 6 | 4 |
| `PlaylistDetailsPresentationTest` | 96 | 24 |
| `PlaylistListPresentationTest` | 144 | 96 |
| `SearchPresentationTest` | 72 | 24 |
| `SearchRestorationTest` | 1 | 0 |
| `UiContractCharacterizationTest` | 5 | 1 |

Attempt 2 (focused correction verification, not a replacement full milestone pass):
MemAvailable 3,665,596 kB after daemon checks. Command:
`bash .claude/skills/verify-task/scripts/verify.sh --files '<HomeContent.kt LibraryContent.kt tasks.md HANDOFF.md>' --quick ':androidApp:testDevQaUnitTest' --tests '*HomeAlbumsTest' --tests '*SistrumTokensTest' --tests '*UiContractCharacterizationTest' --lint --instrumented 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest,com.amwolfstein.sistrum.AppNavigationTest,com.amwolfstein.sistrum.ui.screens.LibraryContentTest,com.amwolfstein.sistrum.ui.components.HomeContentMatrixTest,com.amwolfstein.sistrum.ui.screens.LibraryContentMatrixTest'`.
Logs: `/tmp/verify-task.qMyJyA`, per-class `instr-*.log` and `logcat-*.log`.
Host: 27/27, zero failures/errors/skips; Kotlin compile PASS. Lint BUILD SUCCESSFUL,
0 errors / 73 warnings / 14 hints. Device characterization 5/5 PASS; AppNavigation
39 run / 15 failures, no new methods vs M1 (baseline method
`changingStackPageKeepsCompactNavigationAndMiniPlayer` now passes); LibraryContentTest
6 run / 1 failure (unchanged restoration failure); HomeContentMatrixTest 24 run / 16 failures
(action-label overflow at font scales 1.3 and 2.0); LibraryContentMatrixTest 24/24 PASS.
Graph PASS/changed, final `GATE: FAIL`. The two code corrections are verified, but other M2 failures remain.

Additional host diagnosis: after daemon checks (MemAvailable 3,865,600 kB),
`timeout --kill-after=10s 90s ./gradlew --no-daemon :androidApp:testDevQaUnitTest --tests '*KotlinDecodingExtractorLifecycleTest'`
PASS in 30 s, exit 0; `/tmp/t020-lifecycle.log`. Therefore the class near the last flushed
binary result is not an established root cause of the initial full-suite delay.

Final bounded full-host retry: daemon checks, MemAvailable 3,868,464 kB;
`timeout --kill-after=10s 180s ./gradlew --no-daemon :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest --continue`
exit 124 (time limit), app full suite INCOMPLETE; shared task UP-TO-DATE from its 39-test pass.
Log `/tmp/t020-full-host-retry.log`; a successful worker thread dump at 60 s is
`/tmp/t020-full-host-threads.txt`. The Test worker was RUNNABLE in
`DsdState.decode(Dsd.kt:129)` through `KotlinDsdProviderTest.wavpackDsdSeeksMatchLinearPcmForAllStandaloneDsdRows`.
This is evidence of active codec work, not a proven infinite loop; no decoder test is blamed,
excluded or modified. A longer host run remains required. No completed full-app test count claimed.
Explicit final `python3 .claude/hooks/graph_refresh.py auto`: PASS/changed, 12,018 nodes / 36,102 edges.

Files required: HomeContent.kt fixes the observed lint error; LibraryContent.kt restores shipped
label semantics proven missing by the unchanged device characterization; HANDOFF.md records actual
verification and blockers; graph.json/.graphify_analysis.json are generated by the required hook.
`tasks.md` stays unchanged because T020 is not complete. Self-review: diff kept to these two corrections,
no tests changed/deleted/disabled/weakened, no swallowed exceptions, API/schema/feature removals or
unrelated refactors. `git diff --check` PASS. No commit prepared while the milestone gate fails.

### SC-001 / SC-003 evidence

| Criterion | Evidence | Status |
|---|---|---|
| SC-001 host characterization | Focused rerun: 10/10 unchanged; full host retry recorded below | PASS (characterization) |
| SC-001 device characterization | After restoring labels: 5/5 unchanged. AppNavigation 15 failures, all a subset of its 16-method accepted baseline | PASS under accepted baseline rule |
| SC-003 Home | HomeContentTest 7/8; actual discovered HomeContentMatrixTest 8/24 (historical 48 count was incorrect) | FAIL |
| SC-003 Library/lists | LibraryContentMatrixTest 24/24; LibraryContentTest 5/6 and other collection/artist-list classes fail | FAIL |
| SC-003 Album/genre/composer details | Album presentation 72/72; original detail classes and genre/composer matrix fail | FAIL |
| SC-003 Artist details | Presentation 24/24; content 3/7 | FAIL |
| SC-003 Playlists/editor | Detail presentation 72/96, list presentation 48/144; original classes fail | FAIL |
| SC-003 Search | Restoration 1/1, content 1/1; presentation 48/72 | FAIL |
| Settings toggle | GlassToggle 12/12, Appearance 1/1, Integration 3/3; crossfade slider 1/2 | Mixed, not blanket PASS |

Verified failure details for triage: Search's 24 failures assert empty merged text while EditableText
is empty and the placeholder remains in Text; playlist editor cases report missing
`LocalActivityResultRegistryOwner`; large-list album/track tests target wrapper tags without
ScrollToIndex; Home restoration cannot display collapsed title; crossfade maximum test returns 4
instead of 12. These are recorded observations, not exemptions or completed fixes. All original
assertions/expected values remain intact. Other failure stacks are in the per-class instrumentation logs.

NOT RUN: API 31/32 visual fallback (only API 35 connected), owner visual approval of lens/tap/drag
and screen groups, frame statistics, real listening/Bluetooth/headset (owner manual checks),
Claude `ui-finish-gate-reviewer` and `phone-log-triage` (no delegation allowed in this session),
archived-build commit bisection (remaining failures unresolved; no claim of identified culprit).
Existing UI finish HOLD decisions remain HOLD. T020 remains unchecked; no `.codex-commit/`.

### T020 fresh-session retry — 2026-10-16 (BLOCKED)

Initial state: branch `feature/media3-migration`, no `.codex-commit/`. Read Current status,
T020, the previous blocked report and `git status` first. Retained all five inherited modified
files: HomeContent.kt, LibraryContent.kt, HANDOFF.md and both generated graph files.
Impact before editing: `git grep` confirms AppDestinationContent consumes both screens;
HomeContentTest, LibraryContentTest/LibraryContentMatrixTest and navigation characterization
consume their presentation/semantics. Only HANDOFF is edited by this retry; the required hook
also regenerates the graph. No Room, analysis document, preferences, DecoderProvider order,
PlaybackController API/StateFlow, player code or tests changed. All tests remain unmodified.

Verified stop condition:

- `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/CrossfadeDurationSliderTest.kt:63`
  calls `click(topRight - Offset(2f, 24f))`. Compose's local `top` is zero, so this injects at
  `y = -24`, outside the slider. Confirmed against the cached Compose 1.12.1 InjectionScope
  bytecode (`javap`; classes extracted to `/tmp/t020-ui-test-classes.jar`), rather than assuming
  what `topRight` means. The fresh correct-class run reproduces expected 12 / actual 4 at line 65.
- `git diff 9f0acac HEAD -- <CrossfadeDurationSlider.kt> <CrossfadeDurationSliderTest.kt>`
  is empty (also `git diff --quiet`, exit 0). Neither source nor test was changed by M1/M2.
  The unchanged RTL test uses `topLeft + Offset(2f, 24f)` and passes on the same rebuilt APK.
  This establishes a pre-existing test-input defect, not a new slider regression.
- D-4 (`DECISIONS.md:31–32`) requires existing screen tests unmodified; Current status and
  T018a explicitly require unchanged CrossfadeDurationSliderTest. Altering its input here
  would violate that requirement; changing production hit geometry to accept an off-control
  click would change shipped Settings outside D-20/D-21. Neither is done. The accepted baseline
  names only AppNavigation/MiniPlayer/FullScreenPlayer; no new exemption is invented.
  An owner/orchestrator decision on this test is needed before T020 can pass.

Verification attempt 1:

Memory precheck: `./gradlew --stop` (none), `pkill -f '[K]otlinCompileDaemon'` (none),
MemAvailable 3,839,808 kB. Command:
`bash .claude/skills/verify-task/scripts/verify.sh --files '<HomeContent.kt LibraryContent.kt tasks.md HANDOFF.md>' --lint --instrumented 'com.amwolfstein.sistrum.ui.components.CrossfadeDurationSliderTest'`.
Logs: `/tmp/verify-task.sPMqDF` (`unit.log`, `build.log`, `lint.log`, `androidtest-build.log`,
`instr-CrossfadeDurationSliderTest.log`, `logcat-CrossfadeDurationSliderTest.log`, `graphify.log`).

- Full app host suite: INCOMPLETE. Allowed over four minutes without the earlier 180-second
  deadline, then stopped with `./gradlew --stop` after establishing the mandatory D-4 stop
  condition. Gradle reports daemon stopped by stop command. No deadlock or decoder failure is
  established; no test excluded/modified and no full-app pass/count claimed. Shared suite was
  UP-TO-DATE; its retained XML contains 39 tests, zero failures/errors/skips (cached evidence).
- `assembleDevQa`: PASS. `lintDevQa`: BUILD SUCCESSFUL; report XML has zero Errors/Fatal,
  73 Warnings and 14 Hints. App/AndroidTest APK assembly PASS.
- The class argument above mistakenly used `ui.components` instead of `ui.screens`:
  initializationError/ClassNotFoundException, not a slider test result. Corrected in attempt 2.
- Graph refresh PASS/changed (12,018 nodes / 36,102 edges). Final `GATE: FAIL`.

Verification attempt 2 (focused diagnosis only, not the full milestone gate):

Repeated daemon checks (none), MemAvailable 3,834,824 kB. Command:
`bash .claude/skills/verify-task/scripts/verify.sh --files '<HomeContent.kt LibraryContent.kt tasks.md HANDOFF.md>' --quick ':androidApp:testDevQaUnitTest' --tests '*HomeAlbumsTest' --tests '*SistrumTokensTest' --tests '*UiContractCharacterizationTest' --instrumented 'com.amwolfstein.sistrum.ui.screens.CrossfadeDurationSliderTest,com.amwolfstein.sistrum.ui.navigation.RtlLayoutTest,com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest'`.
Logs: `/tmp/verify-task.Yb3GmW`, including per-class `instr-*.log` and `logcat-*.log`.
Focused host XML: HomeAlbums 3, SistrumTokens 14, characterization 10 = 27 tests,
zero failures/errors/skips. App Kotlin compilation and QA APK assembly PASS.
Device: CrossfadeDurationSliderTest 2 run / 1 failure (maximum method); RtlLayoutTest 5/5 PASS;
UiContractCharacterizationTest 5/5 PASS. No daily app installed, instrumented or force-stopped.
Graph PASS/changed (12,018 nodes / 36,102 edges); final `GATE: FAIL`.

SC-001: fresh host characterization 10/10 and device characterization 5/5 PASS, with no
assertions changed. No fresh full AppNavigation/MiniPlayer/FullScreenPlayer result claimed.
SC-003: the preceding per-group FAIL table remains valid unresolved evidence; no group is
promoted to PASS by this focused run. Other original M2 device failures, complete host suite,
archived-build device bisection, API 31/32 fallback/visual comparison, frame statistics and
Claude finish/log-triage checks are NOT RUN to completion in this retry because the unchanged
slider-test requirement already blocks the gate. Real listening/Bluetooth/headset remain
owner manual checks, never PASS. No architecture/ADR/spec-001 behavior changed or decision added.

File reasons/self-review: prior Home locale and Library label corrections are retained for their
documented lint/accessibility regressions; HANDOFF records the exact stop condition and actual
results; graph files are generated only by the required hook. No new production changes,
test weakening/skipping, swallowed exceptions or unrelated fixes. `tasks.md` stays unchecked;
no `.codex-commit/` while verification fails.
Explicit final `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (12,018 nodes /
36,102 edges); final `git diff --check` PASS. No commit/stage/push attempted.

### T020 second fresh-session retry — 2026-10-16 (BLOCKED)

Read Current status, T020 and prior retry notes with initial `git status --short`. Branch is
`feature/media3-migration`; `.codex-commit/` absent. Kept all five inherited modified files:
HomeContent.kt, LibraryContent.kt, HANDOFF.md and both generated graph files. This retry edits
only HANDOFF to record its result; no graph regeneration or source/test changes.

Impact before editing: `git grep` traced CrossfadeDurationSlider to PlaybackSettingsContent
and its CrossfadeDurationSliderTest/RtlLayoutTest consumers, and both retained screen fixes
to AppDestinationContent. All existing tests must stay unmodified. No Room, analysis documents,
preferences, DecoderProvider order, PlaybackController API/StateFlows, architecture or ADR changes.

Independently verified `CrossfadeDurationSliderTest.kt:63` subtracts `Offset(2f, 24f)` from
`topRight`. `javap` on the retained `/tmp/t020-ui-test-classes.jar` confirms InjectionScope's
`getTop()` returns zero and `getTopRight` uses it: input y is -24, above the slider.
`git diff 9f0acac -- <CrossfadeDurationSlider.kt> <CrossfadeDurationSliderTest.kt>` is empty,
including the working tree. The retained `/tmp/verify-task.Yb3GmW/instr-CrossfadeDurationSliderTest.log`
confirms expected 12 / actual 4 at line 65. This is historical execution evidence, not a fresh run.
D-4 requires existing tests unmodified; D-20/D-21 preserve Settings except the specified rows/toggles.
Changing the test input violates that explicit preservation requirement; changing production to
accept this off-control click alters shipped behavior. Neither is authorized by this retry.
This class is outside the accepted three-class legacy baseline; no exemption is added.

Commands/results: `git status`, `git branch --show-current`, `git diff --stat`, scoped
`git diff`/`git grep`, source/document reads, retained-log `rg` and cached-bytecode `javap`
completed; `git diff --check` PASS. Full gate, memory precheck, Gradle, lint, graph refresh,
phone check and archived-build bisection NOT RUN on this retry: the brief explicitly requires
stopping when resolution would violate scope/ADRs/shipped behavior. Other M2 failures, complete
host verification, visual/API31–32/frame checks and Claude reviews remain unresolved as previously
recorded. Owner listening/Bluetooth/headset remain manual, NOT RUN. No device or daily-app action.

Self-review: only the required HANDOFF status/report/log changed in this retry; inherited source
fixes and generated graphs retained intact. No test/input/assertion weakening, skipped test,
feature removal or new decision. T020 stays unchecked and no commit packet is prepared.

## T020-fix — M2 review fixes (2026-10-16)

Read every report in `review/M2/`: comment-analyzer, kotlin-reviewer, pr-test-analyzer,
silent-failure-hunter, spec-traceability-auditor, translation-reviewer and ui-finish-gate-reviewer.
One HIGH, no CRITICAL findings; independently confirmed pr-test-analyzer H1 against
SearchRestorationTest and the App → AppDestinationContent → LibrarySearchContent state forwarding.
No CRITICAL/HIGH finding rejected.

Impact before editing: `git grep` found SearchRestorationTest as the affected test, App.kt as the
production state owner, AppDestinationContent as its forwarding consumer, and LibrarySearchContent
as the LazyColumn consumer; LibrarySearchContentTest and SearchPresentationTest are sibling fixtures.
No production source, Room/schema, analysis documents, preferences, DecoderProvider order,
PlaybackController API/StateFlows, query or navigation contracts changed. Every other test is untouched,
including T001/T002 characterization, original search tests and the accepted legacy device baselines.

Test-first change: first added a nonzero-scroll precondition to the existing restoration test, then
replaced copied index/offset observations with a published LazyListState reference. Each runOnIdle
reads that live reference, including the new instance published after saved-state restoration.
The Composer-result visibility check, both exact position comparisons, query checks and host-back
assertion remain intact. RED/device execution NOT RUN: AGENTS step 8 reserves phone use for T020;
this task compiles the corrected device test and does not claim runtime reproduction or a device PASS.

Memory precheck: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'`,
MemAvailable 3,889,888 kB (above 2 GB). Quick gate attempt 1: **GATE: PASS**, logs
`/tmp/verify-task.0tbL7N`. Exact command:

```sh
bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/ui/screens/SearchRestorationTest.kt specs/004-ui-extensions/tasks.md specs/004-ui-extensions/HANDOFF.md' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests '*LibrarySearch*Test' --tests '*LibraryTextSearchTest' --tests '*UiContractCharacterizationTest'
```

22 host tests passed (LibrarySearchArabic 1, LibrarySearchViewModel 9, LibraryTextSearch 2,
UiContractCharacterization 10), zero failures/errors/skips. App and AndroidTest Kotlin compilation PASS.
Gate graph refresh PASS/changed (12,018 nodes, 36,099 edges). Explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed with the same counts.
Self-review: no tests deleted/disabled or assertions weakened, no changed expected values/timeouts,
no feature removal, swallowed exceptions, production edits or unrelated refactors; `git diff --check` PASS.
File reasons: SearchRestorationTest fixes H1; tasks.md retains the runner-added task line and ticks it;
HANDOFF.md records impact/results/dispositions; both graph files are generated by the required refresh,
never manually edited. No decisions or ADR changes.

MEDIUM/LOW follow-ups only (left unchanged per AGENTS step 10):

- MEDIUM, comment-analyzer: SyncDatabase.kt:1245 — explain newest-cover ordering and fallback precedence.
- LOW, comment-analyzer: LibraryContentTest.kt:93 — update the completed T010 migration comment.
- LOW, comment-analyzer: GlassToggle.kt:126 — remove redundant drawing labels.
- MEDIUM, kotlin-reviewer: HomeContent.kt:189 / App.kt:256,365 — coordinate collapsed Home header ownership and add app-host coverage.
- MEDIUM, kotlin-reviewer: App.kt:156 — save hoisted search scroll state across Activity recreation, preserving page-pop resets.
- MEDIUM, pr-test-analyzer M1: SearchRestorationTest.kt:51 — add production navigation roundtrip/reset coverage beyond this direct presentation fixture.
- LOW, spec-traceability-auditor: prior T009 HANDOFF row — correct matrix count to 24 cases / 48 captures.
- LOW, spec-traceability-auditor: tasks.md T020 — reconcile its short class list with the complete deferred list in Current status (which remains required).
- LOW, spec-traceability-auditor: spec.md:132 — reconcile FR-002 with the owner-authorized D-21 toggle-only dependency exception.
- MEDIUM, ui-finish-gate-reviewer: GlassToggle.kt:119 — add visible keyboard focus feedback and focus assertions. All seven UI finish gates remain HOLD pending T020 rendering/device evidence and the toggle focus follow-up.

NOT RUN: corrected SearchRestorationTest/device suites, visual/API 31–32 checks, full host/shared suites,
lint, APK assembly and frame statistics (T020 milestone gate; this task requires the quick gate).
Owner listening/Bluetooth/headset checks remain manual, never PASS. No phone/daily app touched.

## Task log (newest first)

| Date | Task | Result |
|---|---|---|
| 2026-10-16 | T021 | Commit prepared in `.codex-commit/`, runner assigns SHA. Tests only: added HttpFetcherTest (17 cases) and reusable scripted FakeHttpFetcher. Impact before edits: `git grep` found no existing HttpFetcher/FetchResult/UrlConnectionFetcher consumers; existing User-Agent convention is Sistrum-Android/version in lyrics and Last.fm (untouched). No Room/schema, analysis documents, preferences, DecoderProvider order, PlaybackController, feature or production code changes; every existing test stays unmodified. Coverage: HTTP refusal before open, downgrade redirects, bounded loops, relative HTTPS redirect success, configured connect/read timeouts and typed Timeout, declared/unknown/lying Content-Length caps, exact-cap/empty-at-zero success, six non-2xx status codes, connect/body CancellationException identity, active-job cancellation, stream/connection cleanup, immutable fixed User-Agent plus forwarded Accept, failure diagnostics with neither full URLs nor embedded tokens, and scripted interface fake/request snapshots/cancellation. D-25 records the tests-first reflection bridge (absent classes -> assertion failures, no production stubs), injectable constructor/result shapes and boundary choices. Files required: two test files implement T021; tasks/HANDOFF record completion; DECISIONS records the T022 boundary; both graph files generated by required refresh, never manually edited. Memory precheck: gradlew --stop (no daemons), Kotlin daemon stop (none, exit 1), MemAvailable 3,117,792 kB. Gate attempt 1 PASS with `--quick :androidApp:testDevQaUnitTest --tests com.amwolfstein.sistrum.integrations.net.HttpFetcherTest --expect-fail com.amwolfstein.sistrum.integrations.net.HttpFetcherTest` and both Kotlin files in `--files`: 17 expected assertion failures, 0 errors/skips; compileDevQaKotlin PASS; graph PASS/changed (12,085 nodes, 36,339 edges). Logs `/tmp/verify-task.fpLn1M`; expected-fail XML confirms missing T022 classes. Explicit `python3 .claude/hooks/graph_refresh.py auto` also PASS/changed (12,085 nodes, 36,339 edges). Self-review: no tests weakened/deleted/disabled, no swallowed exceptions, implementation/stubs or unrelated changes; meaningful literal/status/body/boundary oracles, diff whitespace PASS. NOT RUN: T022 green implementation (next task), full host/shared suites, lint, APK assembly and device tests (milestone gate); no phone/daily app touched. Follow-ups: T022 must pass these tests unmodified; existing M2 owner HOLD/manual checks retained. |
| 2026-10-16 | T020 | Commit prepared in `.codex-commit/`, runner assigns SHA. Full gate attempt 2 D-24 PASS: host app 1,501 (one unchanged oracle skip), shared 39/39, assembly/lint/graph PASS; required 14 distinct device classes 128 cases / 27 exact pre-004 baseline failures / zero new or changed failures. Raw script FAIL baseline only; strict method/cause/count audit PASS. Logs `/tmp/verify-task.huW5GI`, driver `/tmp/t020-final-gate2.log`, audit `/tmp/t020-final-acceptance.log`. Attempt 1 interrupted on full tmpfs/log write failures; partial evidence not counted as final gate. Recovered temporary storage with APK removal and lossless old logcat compression; no tracked extras. Changed only tasks.md/HANDOFF.md to close T020 and record SC-001/SC-003 evidence; all source/tests unchanged. Explicit graph hook not needed, diff whitespace PASS. NOT RUN: existing oracle fixture skip; owner visuals/API 31–32/frame stats/listening/Bluetooth/headset and runner phone-log triage; UI finish HOLD retained. D-24 only, no new decision. |
| 2026-10-16 | T020-fix3 | Commit prepared in `.codex-commit/`, runner assigns SHA. D-24 PASS: full host app 1,501 (one unchanged oracle skip), shared 39/39, build/lint/graph PASS; device 1,051 run / 50 exact pre-004 baseline failures / 0 new or changed failures. Raw script FAIL is baseline only; strict audit PASS. Logs `/tmp/verify-task.SSzzLz`, audit `/tmp/t020-fix3-final-acceptance.log`; baseline `/tmp/t020-base-device`. Fixed Home measured line height (RED 16/24 to GREEN 24/24) and only verified 004-added host/resources/query/scroll/pixel/action fixtures; legacy test bodies and all expected values preserved. Full per-method classification, every file reason, intermediate failed/interrupted attempts and final 36-class table above. Two graph files hook-generated. NOT RUN: existing oracle skip (external fixtures/corpus), API 31/32/owner visuals/frame stats/listening/Bluetooth/headset and runner triage/review (T020/manual). No ADR/schema/player/preferences/decoder/API/feature change; no new decision beyond D-24. |
| 2026-10-16 | T020 second retry | BLOCKED; no commit packet. Only HANDOFF edited; all inherited changes retained. Source/history, cached Compose bytecode and retained device log independently confirm line 63 clicks at y=-24 and fails expected 12 / actual 4. D-4 requires this test unchanged; shipped Settings cannot be changed to accommodate invalid input. No new baseline exemption or decision. `git diff --check` PASS. Full gate/Gradle/memory precheck/graph/phone/bisection NOT RUN under the brief's explicit stop rule; other M2 failures and reviews remain unresolved. |
| 2026-10-16 | T020 retry | BLOCKED; no commit packet. Retained prior Home/Library fixes; only HANDOFF edited plus hook-generated graphs. Full gate `/tmp/verify-task.sPMqDF` FAIL: host stopped after the mandatory D-4 conflict was established, assembly/lint PASS (0 errors), wrong device FQCN initialization failure corrected in focused `/tmp/verify-task.Yb3GmW`. Focused host 27/27, RTL 5/5, device characterization 5/5 PASS; unchanged CrossfadeDurationSliderTest 1/2 FAIL. Verified line 63 injects at y=-24; test and production slider identical to pre-004 `9f0acac`; D-4 forbids changing the required unchanged test, and the accepted baseline excludes this class. No tests/player/contracts/decisions changed or daily app touched. Full host, other device failures, archival device bisection, visual/API31–32/frame/Claude reviews remain incomplete/NOT RUN as detailed above. |
| 2026-10-16 | T020 | BLOCKED; no commit packet. Full gate `/tmp/verify-task.NKwmsw` FAIL: app host stopped before completion, lint error, 831 device tests/cases with 314 failures including 30 exact legacy failures. Fixed observable Home locale and restored Library destination descriptions; focused gate `/tmp/verify-task.qMyJyA` still FAIL, but 27/27 host tests, compile, lint (0 errors), characterization 5/5 and Library matrix 24/24 PASS; AppNavigation 15 failures are a subset of baseline; Home matrix 16/24 fail and Library restoration still fails. Isolated lifecycle host class PASS; bounded full-host retry exit 124, active WavPack DSD stack captured, no full-app pass claimed. Full per-class SC-001/SC-003 table, logs, file reasons and NOT RUN items in T020 report above. Graph PASS/changed; no tests modified, no daily apps touched. Remaining failures, archival commit bisection, visual finish approval, Claude log triage, frame stats and API 31/32 checks remain undone; no architecture decision changed. |
| 2026-10-16 | T020-fix | Commit: prepared in `.codex-commit/`, runner assigns SHA. Fixed confirmed pr-test-analyzer HIGH H1 in SearchRestorationTest; nonzero-scroll precondition added first, then live LazyListState reads replace stale SideEffect snapshots. All existing assertions retained; no other tests/production changed. Quick gate attempt 1 PASS, 22 host tests (0 failures/errors/skips), app/AndroidTest compilation PASS; logs `/tmp/verify-task.0tbL7N`. No CRITICAL/HIGH findings rejected; all 10 MEDIUM/LOW follow-ups recorded in the T020-fix report above. Files required: corrected test, task/handoff records, two generated graph files. NOT RUN: device execution/RED reproduction, visual checks, full suites/lint/assembly/frame statistics (T020); owner listening remains manual. Decisions: none. |
| 2026-10-16 | T018a | Commit: prepared in `.codex-commit/`, runner assigns SHA. Ported Exhale LiquidToggle drawing from pinned checkout `166e2cc7c6a3bcfb944999571e1bc5e0e3c1a066` into GlassToggle with GPL-3 adaptation header: 64×28 dp primary-colored track, 40×24 dp white thumb, local sibling-track lens/blur/highlight/shadow and damped spring drag/tap feedback. Compose toggleable supplies Role.Switch/state and tap/keyboard/accessibility input; horizontal drag respects RTL and cancellation; checked remains caller-controlled; disabled blocks input and uses alpha 0.38. GlassPolicy selects the same-sized static opaque thumb for API < 33, Reduce Transparency, reduced motion or performance fallback. No app-content backdrop is sampled. Impact before editing: git grep found 13 switches in AppearanceContent, PlaybackSettingsContent, IntegrationContent and TagSeparatorsContent; only imports/calls replaced there. No Room/schema, analysis documents, preference values/contracts, DecoderProvider order, PlaybackController API/StateFlows, playback code or screen rows/text/order/icons/cards changed. All existing tests byte-for-byte unmodified. New GlassToggleTest has 12 device tests: touch/semantic taps and switch state, bidirectional LTR/RTL drags, cancelled drag, disabled input and exact alpha, RTL thumb side, static fallback pixel colors/geometry and primary track, reduced motion/dark theme, font scale 2.0, externally controlled state, and live transparency/lens removal/restoration with API-aware positive/negative modifier checks. Backdrop dependency added to catalog/app build; published `backdrop-2.0.0.pom` fetched directly from Maven Central confirms The Apache License, Version 2.0; D-21 and THIRD-PARTY-NOTICES record it and the Exhale source. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 3,931,732 / 4,009,676 / 3,937,536 / 3,932,776 kB. Completed gate 1 FAIL (`/tmp/verify-task.wqN2lO`): new test used removed LayoutInfo.childrenInfo; fixed with direct thumb modifier inspection. Invocation `/tmp/verify-task.tepTH3` interrupted by usage limit before compilation, no result claimed. Resumed gate 2 PASS (`/tmp/verify-task.lo9npx`); self-review then moved semantics fetching out of runOnIdle to avoid UI-thread synchronization. Final gate 3 PASS, logs `/tmp/verify-task.5FsPV7`: `bash .claude/skills/verify-task/scripts/verify.sh --files '<seven task Kotlin/build files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests '*LiquidGlassTest' --tests '*ReducedMotionTest' --tests '*SistrumTokensTest' --tests '*UiContractCharacterizationTest'`. 32 host tests, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed; explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (12,018 nodes, 36,097 edges). Self-review: no weakened/deleted tests, feature removal, swallowed exceptions, Room/player edits or unrelated refactors; whitespace clean, no remaining Material Switch calls under ui. File reasons: new component/test implement required behavior and coverage; four Settings files replace switches only; two build files add required dependency; notice/D-21 record source/license; tasks/HANDOFF record completion; two graph files generated per step 4, never manually edited. NOT RUN: all device tests (including existing Settings tests), visual review, API 31/32 device validation, frame stats, full app/shared suites, lint and APK assembly (T020 milestone gate); owner listening/Bluetooth/headset checks remain manual, never PASS. No phone/daily app touched. Decisions: license confirmation only, no architecture/ADR changes. Follow-up: T020 checks named in Current status. |
| 2026-10-16 | T018 | Commit: prepared in `.codex-commit/`, runner assigns SHA. LibrarySearchContent uses token page/spacing/divider/section colors, controlled SistrumTextField query/clear/Done semantics, explicit nonblank loading state, and optional supplied lazy-list state. Replaced the fixed 183 dp three-row grid with virtualized horizontal wrap-content columns constrained to the available width, so long EN/AR tracks at font 2.0 keep their actions reachable. Existing category order, local search model/FTS/debounce, empty/no-match text, five navigation callbacks, artwork and track/album context actions remain intact. Impact before editing: git grep found AppDestinationContent as production caller, LibrarySearchContentTest and LibrarySearchViewModelTest as consumers, and LibraryTextFilter as shared input; the shared filter remains untouched. Extra production files required: App.kt owns search scroll state and resets it by the existing page-pop generation; AppDestinationContent.kt forwards that state so opening a result and returning keeps position. No Room/schema, analysis document, preference, DecoderProvider order, PlaybackController API/StateFlow or network contract change. All pre-existing tests, including LibrarySearchContentTest, LibrarySearchViewModelTest and LibraryTextSearchTest, byte-for-byte unmodified. Added LibrarySearchArabicTest (1 host fixture: diacritic-folded Arabic title/artist prefixes, FTS terms, candidate filtering and no match), SearchPresentationTest (72 device cases: controlled query/clear/focus/Done, loading/empty/no-match, every category destination, narrow long EN/AR tracks and unclipped menu actions), SearchRestorationTest (1 device case: query/list restoration, removal/re-entry with hoisted state, host back handling). No error state exists in the search UI contract. Memory prechecks: ./gradlew --stop (no daemons), pkill -f '[K]otlinCompileDaemon' (none), MemAvailable 3,927,624 / 3,898,080 kB. Gate attempt 1 FAIL: new restoration test referenced unavailable Espresso; switched to the repository's existing instrumentation back-key approach, no assertions weakened. Attempt 2 GATE: PASS, logs `/tmp/verify-task.cVC3aa` (attempt 1 `/tmp/verify-task.Z0hbxa`); command `bash .claude/skills/verify-task/scripts/verify.sh --files '<six task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests '*LibrarySearch*Test' --tests '*LibraryTextSearchTest' --tests '*SistrumTokensTest' --tests '*UiContractCharacterizationTest'`: 36 host tests, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed (11,981 nodes, 35,954 edges); explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed with the same counts. Self-review: minimal task scope, every extra file required as described, no disabled/weakened tests, swallowed exceptions, removed features, player/Room edits or unrelated refactors; whitespace clean. tasks.md/HANDOFF.md required for completion records; both graph files generated by required refresh, never manually edited. NOT RUN: all device execution, owner visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); no phone/daily app touched. Owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: execute all three search device classes at T020. |
| 2026-10-16 | T017 | Commit: prepared in `.codex-commit/`, runner assigns SHA. PlaylistDetailsContent now uses token page/spacing/divider colors and rounded policy-controlled glass feedback while dragging; hero cover stays square within available width; the localized drag label covers its full 48 dp target. Shared DetailHero already supplies token title/count/actions. Callback signatures, track keys, order/drop-anchor persistence, custom cover/mosaic resolution, playlist editor/delete actions and Favorites protections are unchanged. Impact before editing: git grep identified AppDestinationContent as production caller and PlaylistDetailsContentTest as device consumer; shared DetailHero/PlaylistArtwork/editor/context-menu consumers inspected but unchanged. No ViewModel, Room/schema, analysis document, preference, DecoderProvider order, PlaybackController API/StateFlow or data contract changed. FavoritesArtworkTest and PlaylistDetailsContentTest byte-for-byte unmodified; all existing tests untouched. Added PlaylistDetailsPresentationTest: 96 device cases for narrow long EN/AR titles, square mosaic and play/shuffle callbacks, unavailable-to-empty state and empty reorder completion, localized Favorites identity/protected menu/removal callback, and actual long-press drag reporting exact drop anchors before Done restores track clicks. No distinct loading/error state exists. Drag fixture forwards Reduce Transparency; without a Haze source the policy uses opaque fallback. Memory checks: ./gradlew --stop (no daemons), pkill -f '[K]otlinCompileDaemon' (none), MemAvailable 3,845,548 / 3,898,908 kB. Gate attempt 1 FAIL: new test fixture incorrectly used a nonexistent isFavorite constructor parameter; corrected to existing is_favorite metadata, no assertions weakened. Attempt 2 GATE: PASS, logs `/tmp/verify-task.2HcqQv` (attempt 1 `/tmp/verify-task.fRzepR`); command `bash .claude/skills/verify-task/scripts/verify.sh --files '<two task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests '*PlaylistDetails*Test' --tests '*FavoritesArtworkTest' --tests '*SistrumTokensTest' --tests '*UiContractCharacterizationTest'`: 38 host tests, 0 failures/errors/skips (host task up-to-date on attempt 2 after execution during attempt 1); app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed; explicit `python3 .claude/hooks/graph_refresh.py auto` changed (11,957 nodes, 35,837 edges). Self-review: no tests weakened/disabled, features removed, swallowed exceptions, player/Room changes or unrelated refactors; whitespace clean. File reasons: named production file for required presentation, new matrix test for changed behavior/state/reorder/Favorites coverage, tasks.md/HANDOFF.md for mandatory records, two graph files generated by step 4. NOT RUN: device execution, owner visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); no phone/daily app touched. Owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: execute both playlist detail device classes at T020. |
| 2026-10-16 | T016 | Commit: prepared in `.codex-commit/`, runner assigns SHA. LibraryPlaylistsContent uses token page color and empty-state spacing; PlaylistRow uses token text/spacing and two-line ellipsized names, keeping 110 dp covers and 48 dp open targets. Shared PlaylistArtwork uses token shapes/opaque fill/divider; create/edit sheet uses token text/spacing/shapes/opaque artwork fill and a scrollable body, retaining the existing primary/onPrimary button pair. Existing create/trim/rename/delete/custom-cover/Favorites semantics, queue order, callbacks, stable keys and list state unchanged. Impact before editing: git grep identified AppDestinationContent as list caller, App as create-sheet caller, LibraryPlaylistsContent as row caller, and PlaylistDetailsContent as shared artwork/editor consumer. No ViewModel, Room/schema, analysis document, preference, DecoderProvider order, PlaybackController API/StateFlow or data contract changed. All existing tests, including LibraryPlaylistsContentTest and FavoritesArtworkTest, byte-for-byte unmodified. New PlaylistListPresentationTest: 144 device cases for loading/empty/content and Favorites identity, narrow long EN/AR two-line names and open actions, whitespace rejection/trimmed creation without artwork, Favorites name hiding and cover clearing, ordered play-next/queue IDs plus rename and confirmed delete, red local cover pixel oracle, supplied scroll state and bounded visible rows before/after a jump in 1,000 playlists. No separate error state or Haze source exists; transparency variants use opaque artwork/editor fills. Memory prechecks: ./gradlew --stop (no daemons), pkill -f '[K]otlinCompileDaemon' (none), MemAvailable 3,969,228 / 3,934,568 kB. Quick gates 1 and 2 PASS; logs `/tmp/verify-task.wvx0vk` and final `/tmp/verify-task.p8Liyy`; command `bash .claude/skills/verify-task/scripts/verify.sh --files '<four task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*LibraryPlaylistsViewModelTest`, `*PlaylistWritesTest`, `*PlaylistArtworkCleanupTest`, `*PlaylistDetails*Test`, `*FavoritesArtworkTest`, `*SistrumTokensTest`, `*UiContractCharacterizationTest`: 58 host tests passed, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed (11,943 nodes, 35,758 edges); explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed with the same counts. Self-review: no tests weakened/disabled, features removed, swallowed exceptions, player/Room changes or unrelated refactors; whitespace clean. File reasons: three named production files for required list/row/editor presentation; one new matrix test for state/actions/artwork/readability/scroll coverage; tasks.md/HANDOFF.md for mandatory records; two graph files generated by step 4, never manually edited. NOT RUN: device execution, owner visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); no phone/daily app touched. Owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: execute playlist list and shared detail-consumer classes at T020. |

| 2026-10-16 | T015 | Commit: prepared in `.codex-commit/`, runner assigns SHA. ArtistDetailsContent uses token page/divider colors and spacing; optional nullable portrait, headerBackground and about composable slots retain the local-only screen when absent. Portrait uses the shared circular hero clip, with hasCustomArtwork forcing the persisted local image; headerBackground is constrained behind the hero with a theme scrim; About is a separate lazy item before albums. Local album stable keys/navigation and ordered-track play/shuffle/queue/playlist context actions, image add/remove actions and list state unchanged. Impact before editing: git grep identified AppDestinationContent as the production caller and the four original ArtistDetailsContentTest methods as protected coverage; ArtistDetailsViewModelTest and ArtistDefaultArtworkTest also stay unmodified. No Room/schema, analysis documents, preferences, DecoderProvider order, PlaybackController API/StateFlows, ViewModels or data contracts touched. Extended ArtistDetailsContentTest with three new cases only: all supplied slots with local album identity and exact ordered queue IDs; red manual image pixel oracle overriding blue portrait with image add/remove callbacks and portrait restoration; green projected newest-cover pixel oracle with null slots. Added ArtistDetailsPresentationTest: 24 theme/transparency/EN/AR/font configurations at 280 dp, long name, missing art, unclipped play/shuffle and exact callback counts. No separate loading/error state exists in ArtistDetailsUiState; transparency variants have no Haze source and retain the opaque hero-action fallback. Memory precheck: ./gradlew --stop (no daemons), pkill -f '[K]otlinCompileDaemon' (none), MemAvailable 3,908,256 kB. Quick gate attempt 1 PASS, logs `/tmp/verify-task.hCERaQ`; command `bash .claude/skills/verify-task/scripts/verify.sh --files '<three task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*ArtistDetailsViewModelTest`, `*ArtistDefaultArtworkTest`, `*SistrumTokensTest`, `*UiContractCharacterizationTest`: 37 host tests passed, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed; required explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,925 nodes, 35,667 edges). Self-review: four original device methods byte-for-byte unchanged; no assertions weakened, tests disabled, features removed, swallowed exceptions, player/Room edits or unrelated refactors; whitespace clean. File reasons: ArtistDetailsContent for required slots/token presentation; existing test extension for slot/offline/manual/context regression cases; new matrix test for accessibility presentation; tasks.md/HANDOFF.md for required records; two graph files generated by step 4, never edited manually. NOT RUN: device execution/owner visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); no phone/daily app touched. Owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: execute both artist detail classes at T020. |

| 2026-10-16 | T014 | Commit: prepared in `.codex-commit/`, runner assigns SHA. ArtistRow and LibraryArtistsContent now use token page/opaque portrait/text/divider colors and spacing; names allow two lines with ellipsis, and the open action retains its 48 dp target. ArtistRow accepts nullable `portrait: @Composable (() -> Unit)?`, clipped to the existing 48 dp circle; LibraryArtistsContent accepts an artist-aware portrait callback and forwards the entity. Null slots retain the existing thumbnail/person fallback, including T013a's projected newest-track/manual path; supplied portraits avoid redundant local thumbnail loading. Existing list keys, alphabetical index, sorting/filter inputs, scroll state and deferred context-menu track lookup/callbacks are unchanged. Impact before editing: `git grep` identified LibraryArtistsContent as the sole production ArtistRow consumer and AppDestinationContent as the list caller. No Room/schema, analysis documents, preferences, DecoderProvider order, PlaybackController API/StateFlows, ViewModels or data contracts changed. All existing tests, including ArtistRowTest, LibraryArtistsContentTest, LibraryArtistsViewModelTest and ArtistDefaultArtworkTest, byte-for-byte unmodified. New ArtistListPresentationTest adds 96 device cases: loading/empty/filtered/content states and exact click identity, narrow long EN/AR names and open actions, circular clipping with a red/white pixel oracle, blue supplied portrait overriding red local artwork and null-slot restoration/local-art removal, plus per-artist slot forwarding and bounded composition before/after jumping in a 1,000-artist list. No separate error state or Haze source exists here; transparency variants use the opaque portrait surface. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 3,929,548 / 3,891,240 kB. Quick gates 1 and 2 PASS, logs `/tmp/verify-task.tnVnOZ` and final `/tmp/verify-task.GCWp5O`; final command `bash .claude/skills/verify-task/scripts/verify.sh --files '<three task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*LibraryArtistsViewModelTest`, `*ArtistDefaultArtworkTest`, `*SistrumTokensTest`, `*UiContractCharacterizationTest`. Final 38 host tests passed, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph PASS/changed; explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,912 nodes, 35,589 edges). Self-review: no tests weakened/disabled, features removed, swallowed exceptions, player/Room edits or unrelated refactors; whitespace clean. File reasons: two task production files for the required slots/tokens and readable names; one new matrix test for presentation/state/fallback/virtualization coverage; tasks.md/HANDOFF.md for required records; two graph files generated by step 4, never manually edited. NOT RUN: device execution/visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate). No phone/daily app touched; owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: execute the artist list/row classes named in Current status at T020. |
| 2026-10-16 | T013a | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added newest-track default artwork in `libraryArtistsFrom`, ranked by highest track year (missing = 0), then most recently added, then highest track ID; ignores unresolved artwork and returns null when none exists. Reuses `libraryAlbumsFrom` artwork-asset resolution, with existing row artwork paths as fallback; no file reads. Explicit `artist_artwork_staging` paths now pass separately to the mapper, win over manifest/default artwork, and set `hasCustomArtwork`; legacy manifest artwork resolution remains intact. Impact before editing: git grep identified AndroidLibrarySyncStore as mapper caller; LibraryArtistsViewModel/ArtistDetailsViewModel, artist list and detail header/context-menu anchors consume the same LibraryArtist path; search/insight also consume it. No Room entity/column/index/DAO SQL/version/migration, analysis documents, preferences, DecoderProvider order, PlaybackController API/StateFlows or architecture changed. All existing tests, including LibraryArtistMapperTest and artist view-model/device tests, unmodified. New ArtistDefaultArtworkTest: 11 host tests for year/date/ID ranking and input-order independence, missing years, unresolved/no artwork and empty library, manual precedence/custom flag/removal fallback, multi-artist entities with punctuation in names, album-flow artwork reuse and row artwork fallback. Memory precheck: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 3,917,192 kB. Quick gate attempt 1 PASS: `bash .claude/skills/verify-task/scripts/verify.sh --files '<two task Kotlin files>' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.sync.*' --tests '*LibraryArtistsViewModelTest' --tests '*ArtistDetailsViewModelTest'`; 203 tests across 22 classes, 0 failures/errors/skips; app Kotlin compile PASS; logs `/tmp/verify-task.dJdzEd`. Gate graph PASS/changed; explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,900 nodes, 35,515 edges). Self-review: no tests weakened/disabled, features removed, swallowed exceptions added, or extra file reads; whitespace PASS. File reasons: SyncDatabase.kt for shared mapper/store projection; new ArtistDefaultArtworkTest.kt for required regression coverage; tasks.md/HANDOFF.md for required records; two graph files generated by autonomous step 4, never manually edited. NOT RUN: device/visual checks, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); owner listening/Bluetooth/headset remain manual, never PASS. No phone/daily app touched. Decisions: follows D-19, no new decisions or follow-ups. |
| 2026-10-16 | T013 | Commit: prepared in `.codex-commit/`, runner assigns SHA. AlbumDetailsContent uses token page/text/divider colors and spacing; shared DetailHero/ArtworkHeroBackdrop use token colors, shapes, spacing and reduced-motion duration. Hero artwork shrinks to available width; title supports three lines, subtitle/metadata two; the play action has a 48 dp minimum height and constrained width while shuffle/more retain 48 dp targets. Hero secondary actions use the common opaque glass fallback (no Haze source supplied). Artwork resolution still uses persisted/manual artwork then embedded local artwork; track order, stable IDs, context menus, queue/favorite/playlist and play/shuffle callbacks unchanged. Impact before editing: `git grep` identified AppDestinationContent as album-screen caller and album/artist/playlist/genre/composer screens as shared hero/backdrop consumers. No Room/schema, analysis documents, preferences, DecoderProvider order, PlaybackController API/StateFlows or data contracts changed. AlbumArtworkKeyTest and AlbumDetailsContentTest byte-for-byte unmodified, as are all existing tests. New AlbumDetailsDiscOrderTest verifies disc/track ordering including repeated track numbers, missing tags and exact playback IDs/start index. New AlbumDetailsPresentationTest adds 72 device cases: narrow long EN/AR title with missing art and unclipped play/shuffle callbacks, red local artwork winning over unavailable audio (pixel oracle), and multi-disc selection identity. Matrix transparency variants have no Haze source, so all use opaque fallback. No separate loading/error state exists on AlbumDetailsUiState. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none); MemAvailable 3,933,724 / 3,951,036 kB. Quick gates attempts 1 and 2 PASS, logs `/tmp/verify-task.dn0IiT` and final `/tmp/verify-task.xA8R8s`; final command `bash .claude/skills/verify-task/scripts/verify.sh --files '<four task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*AlbumDetails*Test`, `*AlbumArtworkKeyTest`, `*SistrumTokensTest`, `*UiContractCharacterizationTest`. Final 36 host tests passed, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Gate graph refresh PASS/changed (11,885 nodes, 35,462 edges); explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed with the same counts per step 4. Self-review: no disabled/weakened tests, feature removal, swallowed exceptions or player/Room edits; whitespace clean. File reasons: two task production files for token presentation and responsive hero; two new tests for required disc/artwork/long-title coverage; tasks.md/HANDOFF.md for required records; two graph files generated by step 4, never manually edited. NOT RUN: device execution, visual review, full host/shared suites, lint, APK assembly and frame stats (T020 milestone gate). No phone/daily app touched; owner listening/Bluetooth/headset checks remain manual, never PASS. Decisions: none. Follow-up: T020 must include the album classes and shared-hero consumers listed in Current status. |
| 2026-10-16 | T012 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Genre/composer lists and details now use token page colors and spacing; GenreRow/ComposerRow use token spacing/colors and titleMedium, two-line names with a minimum rather than fixed height, retaining 48 dp open actions. Detail album rows already inherit T011 AlbumRow tokens. Impact before editing: `git grep` found AppDestinationContent as the four screen callers and each collection screen as its name-row consumer. Lazy keys, alphabetical indexes, supplied scroll state, sort/filter wiring, hero/play/shuffle/context-menu/queue/playlist callbacks unchanged. No Room/schema, analysis documents, preferences, DecoderProvider order, PlaybackController API/StateFlows, queries or data-contract changes. All 14 existing genre/composer device test bodies and all host/characterization/legacy tests untouched. Added GenreComposerContentMatrixTest: six tests across UiMatrix's 24 Light/Dark, transparency, EN/AR and font 1/1.3/2 configurations (144 cases): collection loading -> empty -> filtered-empty -> reordered content and exact selection ID; long names at constrained width verify two-line layout, full text-layout height, visible unclipped 48 dp open actions and selection; detail unavailable -> existing entity without albums -> album content and exact album navigation. No separate error/loading state exists for details, and these fixtures have no Haze or transparency parameter. Device tests compile; execution NOT RUN until T020. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none); MemAvailable 3,920,140 / 3,915,588 kB. Quick gates attempts 1 and 2 PASS, logs `/tmp/verify-task.sDe5qM` and final `/tmp/verify-task.gxtx03`; command `bash .claude/skills/verify-task/scripts/verify.sh --files '<seven task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*LibraryGenresViewModelTest`, `*LibraryComposersViewModelTest`, `*GenreDetailsViewModelTest`, `*ComposerDetailsViewModelTest`, `*SistrumTokensTest`, `*UiContractCharacterizationTest`, `*ArabicTranslationCompletenessTest`. Final 36 host tests passed, 0 failures/errors/skips; app and AndroidTest Kotlin compilation PASS. Second gate verifies the strengthened full-text-height assertion. Gate graph refresh and explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,868 nodes, 35,389 edges). Self-review: minimal presentation diff, no weakened/disabled tests, swallowed exceptions, feature removal or player/Room changes; whitespace PASS. File reasons: four named screens for token presentation; two shared name rows for typography and font-scale growth; one new matrix test file for state/row coverage; tasks.md/HANDOFF.md for required records; two graph files generated by autonomous step 4, never manually edited. NOT RUN: device execution/visual review, full host/shared suites, lint, APK assembly and frame stats (T020 milestone gate). Owner listening/Bluetooth/headset remain manual, never PASS; no phone/daily app touched. Decisions: none. Follow-up: T020 must include all five genre/composer device classes above. |
| 2026-10-16 | T011 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Album/track collection screens use token page colors and empty-state spacing; album grid gaps/padding use tokens. Shared AlbumRow/TrackRow use token spacing, opaque artwork placeholders, token artwork corners, titleMedium titles with two lines and existing 48 dp actions; DiscCard uses token surfaces/colors/corners while retaining its text-height calculation and explicit artwork-shape overrides. Impact before edits: `git grep` found AppDestinationContent as both screen callers; AlbumRow consumers Artist/Composer/Genre details; TrackRow consumers Search/Playlist/Insight; DiscCard consumers Home/Search/Insight/entity grids. Shared styling intentionally reaches these consumers without changing signatures, callbacks, artwork loading or fixed grid height. No Room, analysis documents, preference storage, DecoderProvider order, PlaybackController API/StateFlows, queue logic or library queries changed. All 19 existing screen test method bodies verified unchanged against HEAD; all other tests, including T001/T002 and legacy device baselines, untouched. Added seven device cases (album 4, track 3): loading versus filtered-empty albums; reordered grid selection and play/shuffle/next/queue/favorite exact IDs and disc/track order; reordered track selection/next/queue/favorite callbacks; 10,000-item bounded composition (1–40 semantic rows/cards before/after deep scrolling), exact stable lazy keys, absent offscreen first item, album list/grid and track scroll restoration; long Arabic titles at font scale 2/RTL require two text lines and a displayed action. Both classes compile (26 total cases); device execution NOT RUN until T020. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 4,013,652 / 3,880,212 kB. Quick gates attempts 1 and 2 PASS, logs `/tmp/verify-task.kQApzu` and final `/tmp/verify-task.uPa6bX`; command `bash .claude/skills/verify-task/scripts/verify.sh --files '<seven task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest'` with filters `*LibraryAlbumsViewModelTest`, `*LibraryTracksViewModelTest`, `*SistrumTokensTest`, `*HomeAlbumsTest`, `*LibraryVirtualList*`, `*UiContractCharacterizationTest`, `*ArabicTranslationCompletenessTest`. Actual host results: 5 + 20 + 14 + 3 + 10 + 2 = 54 passed, 0 failures/errors/skips (no host class matches LibraryVirtualList; the unchanged filter slot is not exercised by that filter). App and AndroidTest Kotlin compilation PASS; gate graph refresh and explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed (11,853 nodes, 35,301 edges). Self-review: no test weakening, swallowed exceptions, removed features, player/Room edits or unrelated refactors; whitespace PASS. File reasons: two screens required for token presentation; three shared row/card files required for collection styling; two named test files required for acceptance coverage; tasks.md/HANDOFF.md required completion records; two graph files generated by autonomous step 4, never manually edited. NOT RUN: device execution and visual review, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); owner listening/Bluetooth/headset remain manual, never PASS. No phone/daily app touched. Decisions: none. Follow-ups: T020 executes all 26 album/track device cases and checks shared-card/row consumers visually. |
| 2026-10-16 | T010 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Library overview now renders seven individually lazy, keyed destination tiles with token spacing, rounded shapes, opaque surfaces, semantic colors and wrapping labels; existing Search/Artists/Albums/Tracks/Genres/Composers/Playlists callbacks, caller padding and supplied saved list state retained. D-22 removes Recently Added and its track/menu-only parameters and caller wiring from Library; Home retains the shelf and reuses `library_recently_added`, so both translations stay unchanged. Impact before editing: `git grep` identified the sole production caller in AppDestinationContent, four LibraryContentTest methods, and Home as the remaining string/shelf consumer. No ViewModel/query, Room, analysis documents, preference storage, DecoderProvider order or PlaybackController API/StateFlow changes. Required source files: LibraryContent.kt for the tiles/removal; AppDestinationContent.kt for obsolete shelf arguments and unused local alias removal. Required test file LibraryContentTest.kt: preserves the four existing method names, extends destination assertions to Playlists, migrates the three recent-track tests to a Home fixture explicitly citing D-22 (click/menu assertions retained; the old Coming soon assertion becomes an exact Info(track) request assertion at Home's app-host boundary), adds absent-shelf/empty-library and saved-instance scroll restoration cases, plus 24 LibraryContentMatrixTest cases checking every destination callback and unclipped action/label across theme, transparency, language and font scale. Six behavior tests + 24 matrix cases compiled, NOT RUN on device until T020. All other tests, including T001/T002 characterization and the legacy device classes, untouched. Extra files required: tasks.md/HANDOFF.md completion records and both graph files generated by autonomous step 4, never manually edited. Memory prechecks: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 3,995,584 / 4,015,804 kB. Quick gate attempt 1 PASS, logs `/tmp/verify-task.hbv432`; separate device compile first failed on an invalid assertDoesNotExist import, removed it (member method, assertions retained), then `./gradlew :androidApp:compileDevQaAndroidTestKotlin` PASS, logs `/tmp/t010-device-compile-final.log` (initial failure `/tmp/t010-device-compile.log`). Final quick gate attempt 2 PASS, logs `/tmp/verify-task.mjiehQ`: `bash .claude/skills/verify-task/scripts/verify.sh --files '<three task Kotlin paths>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest' --tests 'com.amwolfstein.sistrum.ui.theme.SistrumTokensTest' --tests 'com.amwolfstein.sistrum.ui.components.HomeAlbumsTest' --tests '*ArabicTranslationCompletenessTest'`; 10 + 14 + 3 + 2 = 29 host tests, 0 failures/errors/skips; app and device-test Kotlin compilation PASS. Explicit `python3 .claude/hooks/graph_refresh.py auto` PASS/changed; final gate refresh PASS/changed (11,846 nodes, 35,210 edges). Self-review: every changed file required; minimal scope, no deleted/disabled tests or weakened assertions outside the D-22 migration, swallowed exceptions, player/Room changes or unrelated refactors; whitespace PASS. NOT RUN: device execution, full app/shared suites, lint, APK assembly and frame stats (T020 milestone gate); owner listening/Bluetooth/headset and visual reviews remain manual, never PASS; no phone/daily app touched. Decisions: none. Follow-up outside T010: the existing public onRecentTrackClick model/MainActivity binding is now unused by this destination; retained to avoid expanding into model/ViewModel API cleanup. |
| 2026-10-16 | T009 | Commit: prepared in `.codex-commit/`, runner assigns SHA. D-22 local Home: bold large title replaced by a centered opaque top-bar title after scrolling; newest-first `recentTracks` grouped by album into wide rounded heroes with square local covers over their dominant-color backdrops, album/artist labels and plural track-count/year caption; single-row square-card shelves preserve their queue and long-press callbacks, explicit/quality badges, and supplied scroll state. Default shelf width shows about three cards plus a peek; it grows with font scale. Accent label uses bold 20sp for the token's large-text contrast contract. Impact before editing: `git grep` found one Home caller (`AppDestinationContent`), recent data/queue in `LibraryTracksViewModel`, Library shelf consumer, and existing card-height tests. No ViewModel/query, Room, analysis document, preferences, decoder order, player API/StateFlow or feature changes; all pre-existing tests unchanged. Required files: `HomeContent.kt` layout/grouping; `AppDestinationContent.kt` adds the existing recent list; extra source files `DetailHero.kt` and `TrackInfoContent.kt` only expose the existing dominant-color function and quality badge internally for reuse. Added `HomeAlbumsTest.kt` (3 host tests: exact grouping/order, empty/unknown albums, no network references) and `HomeContentTest.kt` (8 behavior tests plus 48-case `HomeContentMatrixTest`: empty/loading, bounded large library, queue/caption, shelf/hero actions, badges, restored scroll, collapsing title, fonts, clipping and RTL for both carousels). Existing strings/plurals reused; no resources/dependencies added. `tasks.md`/HANDOFF record completion; both graph files generated by autonomous step 4. Memory prechecks: `./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'` (no daemons); MemAvailable 3,639,208 / 3,720,468 / 3,958,868 kB. Quick gate attempt 1 PASS (`/tmp/verify-task.J117qf`); after accessibility/test polish, attempt 2 unit compilation reported unresolved existing app classes during overlapping Gradle builds, then was interrupted (`/tmp/verify-task.jRBR4H`, device compile `/tmp/t009-device-compile-final.log`). Stopped overlapping builds and removed only generated `androidApp/build/kotlin/compileDevQaKotlin` and `androidApp/build/intermediates/built_in_kotlinc/devQa/compileDevQaKotlin` before sequential retry. Final attempt 3 PASS, logs `/tmp/verify-task.yItsIf`: `bash .claude/skills/verify-task/scripts/verify.sh --files '<six task Kotlin files>' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.components.HomeAlbumsTest' --tests 'com.amwolfstein.sistrum.LibraryTracksViewModelTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest' --tests '*ArabicTranslationCompletenessTest'`; 3 + 20 + 10 + 2 = 35 passed, 0 failures/errors/skips; app Kotlin compile PASS; graph PASS/changed (11,833 nodes, 35,178 edges). Explicit final `python3 .claude/hooks/graph_refresh.py auto` PASS/changed with the same counts. Final sequential `./gradlew :androidApp:compileDevQaAndroidTestKotlin` PASS (27s), log `/tmp/t009-device-compile-verified.log`; compilation only, no device execution. Self-review: every file required; no weakened/deleted/disabled tests, swallowed exceptions, player/Room changes or unrelated refactors; whitespace PASS. NOT RUN: device execution, full app/shared host suites, lint, APK assembly and frame stats (T020 milestone gate); real listening/Bluetooth/headset and Home-look acceptance remain owner checks, not PASS. No phone/daily app touched. Decisions: none; no unplanned follow-ups. |
| 2026-10-16 | T008-fix2 second retry | BLOCKED; no commit/packet. Only HANDOFF edited to record the retry; prior edits and unchecked task line retained. Read six reports, checked all 30 HIGH failure sites, verified unchanged pre-M1 player source and T003 test sources, and confirmed the retained metadata-gap failures. Required <=24 dp conflicts with shipped 36 dp and FR-006/D-4/D-23 preservation rules; no findings rejected or decisions changed. Full gate/phone/graph NOT RUN under the brief's stop rule; whitespace PASS. MEDIUM/LOW follow-ups unchanged. |
| 2026-10-16 | T008-fix2 retry | BLOCKED; no commit/packet; task remains unchecked. Re-read all six reports; independently verified the unchanged pre-004 36 dp layout versus existing <=24 dp assertion, D-4/D-23 preservation rules, identical T003 test sources and identical retained failure-method sets (16/8/6). Only HANDOFF edited; prior documentation edits kept. Full gate/phone/graph NOT RUN again because no implementation changed and the brief requires stopping on shipped-behavior/ADR conflicts. Commands and evidence in retry notes above; no findings rejected, no contract/decision changes, MEDIUM/LOW follow-ups retained. |
| 2026-10-16 | T008-fix2 | BLOCKED; no commit/packet; task unchecked. Read all six M1 reports and checked all 30 HIGH failure sites against source and archived T003/T008 logs; all are confirmed failures, with no new M1 failure established, none rejected/exempted. Full gate attempt 1 FAIL, logs `/tmp/verify-task.03BDdT`: cached host suites PASS (app 1,485 with 1 existing skip; shared 39), build/lint PASS (0 errors), fresh QA device 252 run / 222 passed / 30 baseline failures (16/8/6), graph PASS/not needed. Blocker: AppNavigationTest.kt:695–700 requires <=24 dp metadata gap, while FullScreenPlayer.kt:335 uses the pre-004 shipped 36 dp; brief forbids changing shipped behavior, assertions or ADRs (D-4 preserves existing tests). Only HANDOFF changed by task; runner-added tasks.md line preserved. MEDIUM/LOW follow-ups, impact, commands, per-class results, NOT RUN reasons and self-review in T008-fix2 report above. No source/test/contract/decision changes or daily-app actions. |
| 2026-10-16 | T008 | BLOCKED; no commit/bundle. Full gate FAIL after three attempts (attempt 2 interrupted by usage limit). One-line UiMatrix target-context output fix; host/build/lint and SC-001/SC-002 checks pass; 30 legacy failures and 24 matrix-rendering failures reproduce at archived T003; graph restore hits read-only index. See T008 milestone report above for files, commands, logs, NOT RUN items and follow-ups. |
| 2026-10-15 | T007 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added layout-neutral `GlassSurface` backed by the existing shared modifier: token tint/opaque fill, optional token scrim/border, and `GlassPolicy.resolve` with Reduce Transparency, Haze availability and performance fallback; Opaque attaches only background/border, no Haze blur. App provides the live existing preference through `LocalReduceTransparency`; `LocalGlassPerformanceFallback` allows the performance input without new settings/storage. Existing wrapper callers updated; menu/navigation/mini-player background Boxes replaced with the equivalent surface Box; dimensions, clipping, radii, padding and callbacks retained. Library playback labels use normal token text instead of the large-text accent to meet normal-text contrast. Added GlassFallbackTest via all 24 UiMatrix cases: 4 tests/case (96 compiled, 0 executed) inspect real attached Haze modifier elements with positive controls, toggle Reduce Transparency/performance fallback, sample opaque pixels, verify normal/muted text contrast, cover missing Haze and real library-label layout colors against rendered backgrounds, and save reduced screenshots. Impact before editing: git grep found AnchoredPopupMenu, LibraryPlaybackActions, SistrumIconButton, StackPageLayout, FloatingNavigationBar, MiniPlayer and the dragged row in PlaylistDetailsContent; App supplies the preference/Haze state. No Room, analysis documents, preference storage, DecoderProvider order or PlaybackController contracts touched; existing tests/assertions remain unmodified. Required files: LiquidGlass.kt shared surface/policy integration; App.kt preference provision; the seven caller files above to adopt the token wrapper/surface (LibraryPlaybackActions also fixes text contrast); new GlassFallbackTest.kt acceptance; tasks.md/HANDOFF.md completion; both graph files generated by autonomous step 4, never manually edited. Self-review: each file required, no layout/API/feature removal outside the wrapper signature, no weakened/disabled tests, no swallowed failures; full-player explicit solid/background overrides and player code untouched; whitespace clean. Memory precheck before gate: `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (none), MemAvailable 4,163,548 kB. Preliminary `./gradlew :androidApp:compileDevQaAndroidTestKotlin` PASS. Gate attempt 1 PASS: `bash .claude/skills/verify-task/scripts/verify.sh --files '<ten task Kotlin paths in .codex-commit/files.txt>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.components.LiquidGlassTest' --tests 'com.amwolfstein.sistrum.ui.theme.SistrumTokensTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest'`; 5 + 14 + 10 = 29 host tests passed, 0 failures/errors/skips; final device-test and app Kotlin compilation PASS. Gate ran `python3 .claude/hooks/graph_refresh.py auto`: PASS/changed (11,802 nodes, 35,038 edges). Logs `/tmp/verify-task.FszXQX`; preliminary compile `/tmp/t007-device-compile.log`. NOT RUN: all device assertions (phone reserved for T008), full host/shared suites, lint and APK assembly (T008 milestone gate); no phone/daily app touched. Decisions: none; no unrelated follow-ups. |
| 2026-10-15 | T006 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added `GlassPolicy.resolve(reduceTransparency, hazeAvailable, performanceFallback)` returning Blur only when transparency is allowed, Haze is available and performance fallback is off; otherwise Opaque. Four new LiquidGlassTest cases assert all eight input combinations from FR-002; original full-resolution assertion unchanged. Existing modifier and all call-site APIs/behaviour untouched; surface integration belongs to T007. Impact before editing: git grep identified AnchoredPopupMenu, LibraryPlaybackActions, SistrumIconButton, StackPageLayout, FloatingNavigationBar and MiniPlayer wrapper consumers; App currently translates ThemePreferences-derived reduceTransparency into null Haze state. Policy takes that preference value as a Boolean; preference storage, Room, analysis documents, DecoderProvider order and PlaybackController contracts untouched. All pre-existing tests/assertions stay unmodified. Required files: LiquidGlass.kt policy, LiquidGlassTest.kt new cases, tasks.md/HANDOFF.md completion, and both graph files generated by autonomous step 4 (never manually edited). No new dependency or architecture decision. Memory precheck: `./gradlew --stop` (no daemons), Kotlin daemon stop, MemAvailable 4,161,588 kB. Gate attempt 1 PASS: `bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/src/main/kotlin/com/amwolfstein/sistrum/ui/components/LiquidGlass.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/ui/components/LiquidGlassTest.kt' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.components.LiquidGlassTest' --tests 'com.amwolfstein.sistrum.ui.theme.SistrumTokensTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest'`; 5 + 14 + 10 = 29 passed, 0 failures/errors/skips; Kotlin compile PASS. Gate ran `python3 .claude/hooks/graph_refresh.py auto`: PASS/changed (11,784 nodes, 34,970 edges). Logs `/tmp/verify-task.zNLXyp`. Self-review: minimal additive diff, no existing assertion changed, no disabled tests, swallowed exceptions or feature removal; whitespace clean. NOT RUN: full host/shared suites, lint, APK assembly and device tests (T008 milestone gate); no phone/daily app touched. Decisions: none; no unrelated follow-ups. |
| 2026-10-15 | T005 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Theme provides palette-derived `LocalSistrumTokens` and `LocalReducedMotion`; `ReducedMotion.kt` observes Settings.Global animator duration scale live, registers before initial read, unregisters on collection cancellation, and propagates failures. `SistrumTokens.motion` adds short/medium/long 150/300/500 ms durations, all zero at system scale 0 (D-6/D-23). New `ReducedMotionTest` (3 cases) checks zero/positive scales, live toggles, registration/read/cancellation ordering, exact normal/reduced durations across both palettes and unchanged colors/spacing/shapes. Impact before editing: git grep found App's SistrumTheme call, no existing token consumers beyond the definition, and MainActivity's existing content-observer pattern; later UI tasks consume the locals. No Room, analysis documents, preference storage, DecoderProvider order or PlaybackController contracts touched; DEFAULT player and all existing tests untouched. Files required: SistrumTheme wiring; ReducedMotion observer; SistrumTokens durations (T005 explicitly requires motion tokens); new host test; tasks/HANDOFF completion; DECISIONS records unspecified numeric defaults; graph files generated by required autonomous step 4, never edited manually. Memory prechecks after daemon stops: 4,170,100 / 4,192,320 kB available. Gate attempt 1 FAIL because --tests followed the compile task (unknown option; no tests executed), production compile and graph PASS; logs `/tmp/verify-task.8f0wiu`. Attempt 2 PASS: `bash .claude/skills/verify-task/scripts/verify.sh --files '<four task Kotlin files>' --quick ':androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ui.theme.ReducedMotionTest' --tests 'com.amwolfstein.sistrum.ui.theme.SistrumTokensTest' --tests 'com.amwolfstein.sistrum.settings.ThemeModeTest' --tests 'com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest'`; 3 + 14 + 2 + 10 = 29 passed, 0 failures/errors/skips; app and device-test Kotlin compilation PASS; required graph refresh PASS/changed (11,776 nodes, 34,954 edges). Logs `/tmp/verify-task.1xQYoN`. Self-review: minimal diff, no existing tests weakened/disabled, no swallowed exceptions, feature removals or data-contract changes; whitespace clean. NOT RUN: full host/shared suites, lint, APK assembly, device characterization and actual system-setting/composition integration (T008 milestone gate; host tests use fake setting callbacks); listening/Bluetooth/headset owner checks. No phone or daily app touched. Decisions: D-23; no unrelated follow-ups. |
| 2026-10-15 | T003 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added `androidTest/ui/testing/UiMatrix.kt` (24 localized/theme/transparency/font configurations, action and label clipping checks, WCAG contrast including alpha, per-case PNGs), `UiMatrixTest.kt` (72 parameterized harness cases; compiled, 0 executed), and `.qa`-only `scripts/ui-frame-stats.sh` (foreground guards, independent resets/raw stats, Home shelf scroll, Library scroll, confirmed player open). Impact checked before editing with git grep: no existing helper/script consumers; inspected SistrumTheme, explicit reduceTransparency parameters and navigation/mini-player semantics; future UI tests and performance gates consume the additions. No Room, analysis document, preference implementation, DecoderProvider order, PlaybackController contract or production changes; all existing tests untouched. Gate attempt 1 FAIL (missing semantics getOrNull import), attempt 2 PASS, third gate after OEM focus-query correction PASS: `:androidApp:compileDevQaAndroidTestKotlin :androidApp:testDevQaUnitTest --tests com.amwolfstein.sistrum.ui.navigation.UiContractCharacterizationTest`, app Kotlin compile and graph refresh. Final logs `/tmp/verify-task.yRFHLV`; earlier `/tmp/verify-task.tCLJ8b`, `/tmp/verify-task.nAskuf`. Memory prechecks after daemon stops: 4,173,828 / 4,178,012 / 4,167,388 kB available. Host class UP-TO-DATE (cached XML: 10 tests, no failures/errors/skips); no new device assertions executed. Final Home-axis correction additionally verified with bash syntax, isolated fake-adb fixture (three scenarios each 7/100 = 7.000%, `/tmp/t003-script-final-fixture2`) and the single real baseline run below. Daily-package refusal checked before adb; self-review/whitespace clean, negative clipping/overflow and external WCAG boundary fixtures added, no tests weakened/disabled and no swallowed failures. Extra files: UiMatrixTest.kt validates the helper; tasks.md/HANDOFF.md record completion/baseline; two graph files generated by required autonomous step 4, never manually edited. NOT RUN: 72 new device cases and existing device suites (T008), full host/shared suites, lint and APK assembly (milestone gate), owner listening/Bluetooth/headset checks. Expected-fail: none (pass-now infrastructure). Decisions: none. Follow-up: T008 must execute UiMatrixTest; retain the small player-open sample when interpreting comparisons. |
| 2026-10-15 | T002 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added only `androidTest/ui/navigation/UiContractCharacterizationTest.kt` (5 Compose tests): circle collapse/expand and paired x/y bounds, real Activity recreation with retained MainViewModel and destination/tab restoration, detail back order, full-player-before-detail back priority, DEFAULT play/pause/previous/next, seek presence, queue/lyrics panels, favorite on/off callbacks. Impact checked before editing: App consumes NavigationChrome and FullScreenPlayer; MainViewModel owns destination stacks; player metadata/controls consume fake playback and favorite callbacks. No Room, analysis documents, preferences, DecoderProvider order or PlaybackController contract changed; existing host/device tests unmodified. Quick gate attempt 1 FAIL (new fixture omitted required onMiniPlayerDismiss callback); attempt 2 PASS; self-review corrected a new geometry expectation (Home keeps its leading edge) and reused MainViewModel.Factory; final attempt 3 PASS: `:androidApp:compileDevQaAndroidTestKotlin` + `:androidApp:compileDevQaKotlin`, graph refresh PASS/changed. Logs: final `/tmp/verify-task.QJjtJ3`; earlier `/tmp/verify-task.fozRD5`, `/tmp/verify-task.xEUN2j`. Memory prechecks after daemon stops: 4,140,548 / 4,128,928 / 4,156,800 kB available. Test count: 5 new compiled, 0 executed; no expected failures (pass-now characterization). Self-review: no existing assertions altered, disabled tests, swallowed exceptions, production/API/feature changes; diff whitespace clean. Extra files required: tasks.md and HANDOFF.md record this task; graphify-out/graph.json and graphify-out/.graphify_analysis.json generated by required autonomous step 4, never manually edited. NOT RUN: new device assertions and four existing baseline classes (T002 compile-only, phone reserved for T008; current pass/fail unknown, table above), host test execution/lint/APK assembly (milestone gate). Device baseline remains a T008 verification follow-up; no daily app touched. Decisions: none. |
| 2026-10-15 | T001 | Commit: prepared in `.codex-commit/`, runner assigns SHA. Added only `test/ui/navigation/UiContractCharacterizationTest.kt` (10 passing tests); existing gesture tests referenced, not duplicated. Impact: App/MainViewModel stack and metadata consumers, navigation scroll/visibility/transport helpers, lyrics browse/parser entry points; no Room, analysis, preferences, decoder order or PlaybackController contract changes; all existing tests unmodified. Quick gate attempt 1 failed on new test dispatcher cleanup, fixed by JUnit teardown; attempt 2 PASS (full app/shared host tasks + Kotlin compile), logs `/tmp/verify-task.Ix9SN3` (attempt 1: `/tmp/verify-task.TSvm8t`). Counts: app 1,464 (1 existing skip), shared 39; new class 10/10; 0 failures/errors. Memory prechecks: 4,198,292 / 4,163,876 kB available after daemon stop. Required gate graph refresh generated `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json`; these extra files are included per autonomous step 4, without manual edits. `tasks.md` and this file record completion/baseline. Self-review and diff whitespace check passed. NOT RUN: device/recreation/full-player back-priority checks (T002/T008), lint and APK assembly (milestone gate); existing oracle skip remains NOT RUN. Decisions: none; no follow-ups. |
| 2026-10-15 | Plan update D-22 (Claude) | Home in the Apple Music style with local data; Recently added moves from Library to Home (T009/T010). |

## SC-011 baseline — T003 (2026-10-15)

One actual device invocation, after the quick gate:
`bash scripts/ui-frame-stats.sh --track-title '<loaded paused title>' --output-dir /tmp/t003-sc011-baseline`.
All three scenarios completed; player-open was confirmed by the full player's seek control. No instrumentation ran.

| Scenario | Total frames | Janky frames | Janky share |
|---|---:|---:|---:|
| Home horizontal shelf scroll (3 forward, 3 reverse) | 250 | 5 | 2.000% |
| Library vertical scroll (3 up, 3 down) | 299 | 18 | 6.020% |
| Player open (paused track) | 30 | 3 | 10.000% |

Device: OPPO CPH2307, serial `4ae58ea6`; 1080 x 2400 physical display, 480 dpi. English UI, font scale 1.0,
System theme resolving Light (`cmd uimode night`: no), Reduce Transparency off (appearance store absent, default false).
The initially empty `.qa` library was populated through its own scan UI: 775 tracks / 237 albums / 71 artists. A track was
loaded through Home and paused before measurement; this is frame measurement, not a listening PASS. Only `.qa` was launched
and interacted with; neither daily app was installed, instrumented, force-stopped or opened. Installed `.qa` update time
21:48:03 follows the last production source commit (`b9200a87`, 21:41:40); the installed binary was reused, not rebuilt
in T003, and its exact source provenance was not independently verified. T001–T003 contain no production changes.

Artifacts: `/tmp/t003-sc011-baseline/summary.tsv`, `device.txt`, `home-scroll-framestats.txt`,
`library-scroll-framestats.txt`, `player-open-framestats.txt`. These are temporary local logs; retain them for later comparison.
Use the same device, library, settings and gesture count for T055/T066. M5 limit: no scenario more than 5 percentage points
above its baseline; Reduce Transparency on must be no worse than off. On-mode comparison is NOT RUN here (one baseline run
required by T003). Player-open contains only 30 frames, so its percentage is sensitive to individual janky frames.
