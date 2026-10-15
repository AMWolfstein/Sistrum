# HANDOFF — 002-kotlin-decoders

**Branch:** `feature/media3-migration` (no separate branch; owner plan 2026-10-14). Plan: `OWNER-PLAN-2026-10-14.md`.

## Current status

- **Phase:** M1 DONE (T013 passed 2026-10-15, finished by Claude after Codex BLOCKED on seek latency; D-33). **M2 on hold by
  the owner (2026-10-15: "Don't push M2 to codex")**: do not start the runner for T014 onward until the owner says how.
- **Next step:** wait for the owner's instruction on M2. 004: Phase 0 done; owner choices recorded in
  `specs/004-ui-extensions/NOTES.md` (Wikipedia + Deezer artist images from Rhythm; Exhale motion artwork off by default in
  Integrations, no warning; no extension runtime decided yet).
- **T013 result:** full gate `/tmp/verify-task.W50YyC`: unit (app + sharedLogic) PASS, assembleDevQa PASS, lintDevQa 0
  errors (71 warnings), KotlinFormatsPlaybackTest 7/7, Media3EngineGaplessTest 7/7, Media3EngineCoreTest 12/12,
  NormalizationLevelDeviceTest 2/2; Media3CrossfadeTest 15/16 (`endOfTrackFadeDecidedOnProcessedTimelineEndsWithTrackA`
  104 ms vs its 100 ms bound; FLAC/WAV through Media3's own decoders, not touched by M1), rerun alone 16/16 PASS. Seek
  medians: wavpack 293, wma-voice 247, every seekable fixture ≤ 300 ms, short AIFF fixtures audible (58 ms). SC-001/SC-002 as
  in the earlier T013 report below. `:metadata:test` corpus blocker unchanged (T021a). Owner listening checks NOT RUN.
- **T013-fix2:** committed by runner as `20d9369`; previous prepared-bundle status is historical. Current QA APKs restored after the comparison; daily apps untouched. SC-001's 24-fixture playback/PCM checks and SC-002's ten same-format joins PASS within their documented DSD/ALAC boundaries; no SC-006 PASS. Full evidence, commands, log paths and remaining manual checks are in the T013 report below.
- **Earlier T013 phone check (CPH2307, `.qa`, before T013-fix2):** full gate `/tmp/verify-task.DViDgs` — unit (app + sharedLogic) PASS, lintDevQa PASS (0 errors), assembleDevQa PASS, Media3EngineGaplessTest 7/7, Media3CrossfadeTest 16/16, Media3EngineCoreTest 12/12, NormalizationLevelDeviceTest 2/2; KotlinFormatsPlaybackTest 7/7 after the short-tail seek fix (`/tmp/verify-task.G3yR9D`). `:codecs:test` PASS; `:metadata:test` 1302/1391 (89 known WaxLabel corpus cases, T021a). Seek ms: wavpack 258, ape 234, musepack 207/84, alac 128, wma 82/240/218/69/320 (bound waived, D-30), PCM variants 55–99, dsf 68, dff 72; three 50 ms AIFF fixtures untimed (25 ms tail). SC-001/SC-002: every covered format plays on Media3 and matches pinned PCM; lossless joins within 2 frames (WavPack, APE, ALAC, Musepack SV7, WMA Lossless, AIFF variants, DSF, DFF).
- **Phase A closed:** 46/46 imported; end gate: `testDevQaUnitTest` 1279/0 fail, sharedLogic 39/0,
  `lintDevQa` 109 errors, all MissingTranslation (known category); scratch worktree `port-ro` removed.
- **T013-fix gate:** PASS, final logs `/tmp/verify-task.SDRQjU`; app 1,379 cases, 0 failures/errors, 1 existing loudness-oracle assumption skip (`SISTRUM_CORPUS` unset); sharedLogic 39 and codecs 1,477 cached passing tests; production and device-test Kotlin compilation PASS. No new regression test skipped.
- **Early host speed signal (T004):** 72 PCM/float files, five warmups/five measured loops per file;
  RTF min/median/max 0.000485809 / 0.005968458 / 0.021165319 (slowest 47.247× real time).
  This is the provider extraction path on a laptop, not SC-005's CPH2307 performance result.
- **Decisions:** D-28 defines failed-only decoder-set retries and same-source measurement retention without changing ANALYZER_VERSION. D-25 records the owner-approved single WaxFlow AIFF path and migrated expectations; D-24 still defines device PCM comparison boundaries and T013-only device access. Execution by Codex in autonomous mode (D-9).

## Task log (newest first)

| When | Item | Result |
|---|---|---|
| 2026-10-15 | T013 finished by Claude (Codex BLOCKED: Voice seek 305–312 ms) | PASS, see Current status. Causes (D-33): Voice 18-object preroll → 3 (app Voice seek test moved to the D-30 RMS bar, as D-30 required); extractor announces blocks as ≤ 1024-frame samples so a seek inside buffered audio no longer drops a whole 4096-frame block (50 ms AIFF fixtures produced no post-seek PCM). Tried and dropped: 250 ms Media3 start buffer (no change). migration-guard PASS (2 notes applied: D-30 text updated, Voice helper requires a non-silent reference when the full 0.5 s window exists). |
| 2026-10-15 | T013 M1 full gate + phone check; commit: none | BLOCKED after three current-build attempts. Full GATE: FAIL `/tmp/verify-task.1R6yM5`: decoder 6/7 (WavPack seek 301 ms); app 1381 cases, 0 failures/errors, 1 unchanged corpus skip; sharedLogic 39 PASS; assemble/lint PASS (0 lint errors); 001 device 37/37 PASS. Isolated seek reruns FAIL on Voice 312/305 ms; WavPack 284/269 ms. Read-only older-snapshot comparisons and full per-class logs retained below. Codecs 1487, analysis 1127, DSP 18 cached PASS; metadata verifier FAIL on known pinned input, cases NOT RUN. Graph not needed. No source/test/decision changes, no task tick or commit bundle; owner manual checks NOT RUN. |
| 2026-10-15 | T013-fix2; commit: prepared, awaiting runner | READY. Final quick GATE: PASS `/tmp/verify-task.lHROXH`: codecs 1487/0 fail/error/skip; affected app 378/0 fail/error/1 unchanged corpus skip; sharedLogic 39 cached passes; production/device Kotlin compile PASS; graph changed 11325/33436. Broad quick GATE: PASS `/tmp/verify-task.MEwYTN`, full app 1381/0 fail/error/1 unchanged skip. Exact cold noise-coded Standard PCM fixed without prefix synthesis or playback checkpoint allocation; inherited T013/item (4)/four-flavour seek edits retained; WMA waiver removed. D-18/D-24/D-29/D-30 recorded. Required metadata command FAIL before tests on the known T021a pinned input; metadata cases NOT RUN. Device/lint/assemble/listening NOT RUN (T013/owner); no phone latency claim. Complete path/reason/command record below and in `.codex-commit/`; separate owner files excluded. |
| 2026-10-15 | T013-fix2 item (5), resumed after memory-guard interruption; commit: none | BLOCKED after three completed attempts. Final quick GATE: FAIL, `/tmp/verify-task.9TJbzp`: codecs 1486 cases, 1485 pass, 1 failure, no errors/skips (new long noise-coded Standard exact-tail hash); all original codec tests pass. App 1381 cases: 1380 pass, 0 failures/errors, 1 unchanged corpus skip; sharedLogic 39 cached passes. Production/device Kotlin compilation PASS; graph changed (11322 nodes / 33426 edges). Fragmented Standard and Lossless defects fixed; Voice 18-object preroll preserves unchanged app exact seeks and passes D-30 cold-seek RMS/work checks. Metadata downstream fails before tests on missing/changed pinned `owned/matroska-ffmpeg-aac.mka` (known T021a precondition). No waiver removal, task tick or commit bundle because the correctness gate fails. Device/lint/assemble/listening NOT RUN (T013/owner scope). D-30 records unshipped trial choices; no architecture/ADR/001 contract changes. |
| 2026-10-15 | T013 reviews (Claude) | migration-guard on KotlinFormatsPlaybackTest: DSD frames, crossfade driver, WMA waiver, private fixtures accepted; my first short-tail change (untimed) rejected → reworked: short-tail fixtures timed to first post-seek PCM under the same 300 ms, post-seek PCM only, pinned DSD 8821, recorded in D-30. phone-log-triage (haiku) on `/tmp/verify-task.DViDgs` + `.G3yR9D`: no crash/ANR/underrun/codec error. HIGH checked by Claude, not ours: Media3 `FlacMetadataReader` throws IllegalArgumentException on the malformed-FLAC fixture, surfaced as a Source error (malformedFilePrepareThrowsWithoutLeakingPlayers passes). Deferred to cleanup/perf: ~174 "Handler on a dead thread" warnings from `onAudioDisabled` after player release in NormalizationLevelDeviceTest (release ordering); that 001 class runs 14.5 min, over the 10-min device time-box. |
| 2026-10-15 | T013 M1 full gate + phone check (Claude ran the gate; Codex quota-blocked then) | PASS — see Current status. First run (`/tmp/verify-task.UD4Bkn`) failed KotlinFormatsPlaybackTest 4/7: DSD frame count from Flick (8820 vs production 8821), crossfade test never called `beginCrossfade`, WMA seek 305–400 ms → T013-fix2. pr-test-analyzer gained the assumption traces (trial 2 found all three from code alone). |
| 2026-10-15 | T013-fix2 (Codex attempts 1–4 BLOCKED on WMA seek; finished by Claude) | WMA packet seek deferred by owner (D-30, T041a; patch `~/.local/state/sistrum-delegate/milestones/T013-fix2/wma-packet-seek-wip.patch`, codecs reverted). Kept: DSD expected frames from production codec (D-18 addendum), coordinator-style `beginCrossfade` in the crossfade test, private `.qa` fixtures (D-29), lint SDK_INT guard. Claude: WMA bound waiver + short-tail seek handling (aiff-pcm reproduced timeout); verify.sh sets `logcat -G 16M`. |
| 2026-10-15 | T013-fix2 resumed under D-30; commit: none | BLOCKED after three attempts; final quick GATE: FAIL, logs `/tmp/verify-task.HvoqOC`. Codecs 1481 cases: 1473 pass, 8 fail, 0 skips/errors; app 1379 cases: 1377 pass, 1 failure, 1 unchanged loudness-corpus assumption skip, 0 errors; sharedLogic 39 cached passing cases. Long Standard/Pro/Voice tests pass; long Lossless tail loses 8192 frames, two pinned Lossless seeks return EOF, two fragmented Standard checks misposition, and three pinned Voice seek PCM comparisons fail. No existing test changed, no architecture/ADR/001 behavior change, no commit bundle. Detailed files, commands and remaining work below. |
| 2026-10-15 | T013-fix2; commit: none | BLOCKED: packet-only cold Voice seek does not preserve exact PCM within the required frame budget (reproduction below). DSD production frame-count expectation and explicit crossfade start corrected; prior T013 edits retained. Partial quick regression GATE: PASS (attempt 1), logs `/tmp/verify-task.rYtiij`: codecs 1477 restored passing cases, app 1379 cached cases (1 existing corpus skip), sharedLogic 39 cached passes; production/device Kotlin compilation PASS. Graph auto-refresh changed both generated artifacts. Task remains unchecked, no commit bundle. WMA product changes and new four-flavour seek acceptance tests remain undone. Device/lint/assemble/manual checks NOT RUN (T013/owner scope). No architecture/ADR/001 behavior changes. |
| 2026-10-14 | T013-fix review (Claude) | Runner commit 0aa7938; migration-guard PASS on the 6 changed existing tests (additions, required rename/extension of the join test, required replacement of the reflective assert) and the query-only DAO change. Note for later: the rewritten DSD sniff test no longer asserts the matching extractor is `kotlin-dsd`. |
| 2026-10-14 | T013-fix; commit: prepared, awaiting runner | GATE: PASS on final verification pass 3 (all three passes passed; passes 2/3 validate added boundary coverage and independently authored retained-measurement JSON). App 1,379 cases: 1,378 pass, 0 failures/errors, 1 existing oracle assumption skip. sharedLogic 39 and codecs 1,477 cached PASS; compileDevQaKotlin and compileDevQaAndroidTestKotlin PASS. All 43 cases in the six affected host classes pass without skips, including exact oracle PCM after interrupted cache I/O + seek, startup sweep/constructor cleanup, typed decode() failure logging, throwing/cancelled sinks, actual pending DAO SQL, retained activeAnalyses values, route limits and scan cancellation. D-28 addresses drift D-01/D-06. Original T002 unchanged; Room SQL only, schema/version unchanged. Graph changed (11,285 nodes / 33,313 edges), both generated files included. Final logs `/tmp/verify-task.SDRQjU`. NOT RUN: matchesWaxFlowOracleFixtures (SISTRUM_CORPUS unset; original assumption, unchanged); device classes, lint and assemble (T013 scope); listening/Bluetooth/headset and performance (owner/M6 scope); stage/commit/push (runner consumes .codex-commit). Extra-file reasons and full impact/command record below. |
| 2026-10-14 | M1 milestone review (Claude) | Reviewers on ded051c^..HEAD: silent-failure-hunter PASS (5 MED), pr-test-analyzer NEEDS CHANGES (3 test gaps), kotlin-reviewer NEEDS CHANGES (1 HIGH), audio-correctness PASS (2 MED), spec-traceability PASS (3 MED), comment-analyzer (LOW only), codebase-archaeologist one-time drift audit → `specs/review/drift/REGISTRY.md` (12 rows). Port fidelity by script instead of the agent: 128/133 imported files identical to origin/port/waxflow; the 5 differences are the FFT Plan move to :dsp (3 import lines) and T012a's AiffException. Confirmed by Claude: interruptible FileChannel codec cache closed by Media3's `Thread.interrupt()` on seek (javap of 1.11.1 `Loader$LoadTask.cancel`); drift D-01 (failed analyses never retried, version unchanged) and D-02 (generic-ID DFF refused). → T013-fix (items 1–7). Deferred with reason: per-sample boxing in the packing loop (M6 perf), WMA seek decodes from start (T013's seek-latency test measures it), ALAC gapless delay/padding (T013-fix item 7 join test covers it on device), G.711 truncated-WAV clamp, unsupported-by-message-text classification, IOException vs malformed classification, downmix front L/R −3 dB (owner listening), D-02/D-03/D-04/D-05/D-07 per registry plan. Also fixed: runner task lookup `\b` matched `T013` to the `T013-fix` line. |
| 2026-10-14 | T012a finished by Claude (Codex BLOCKED on the external WaxLabel corpus) | Codex's single WaxFlow AIFF path kept after review (D-25). Claude fix: decoder-table rows aiff/ima4, aiff/alaw, aiff/ulaw still named `kotlin-pcm-variants`, which no longer serves AIFF → repointed to `kotlin-aiff`. GATE: PASS, full `testDevQaUnitTest` + sharedLogic, `assembleDevQa`; `:codecs:test` PASS; `compileDevQaAndroidTestKotlin` PASS; graph changed. `:metadata:test`: corpus not reproducible (D-27), run with `-x verifyWaxLabelCorpus`: 1302/1391 pass, 89 `changed fixture` precondition failures on the non-reproducible files only; fix = T021a before M3. migration-guard: PASS (all 17 legacy tests mapped into 22; changed expectations all in D-25). Device classes NOT RUN: T013. |
| 2026-10-14 | T012a Codex attempt; superseded (finished by Claude in d1b676d, row above) | Single WaxFlow AIFF path implemented; D-25 records owner decision and migrated expectations. Broad quick gate PASS: 444 tests, 0 failures, 1 existing corpus assumption skip; final focused gate PASS: 35 tests, 0 failures/errors/skips, logs `/tmp/verify-task.PmYN1k`. Codecs PASS: 1,477/0; instrumented-test compile PASS; 24 fixture hashes/staging PASS; graph changed (11,197 nodes / 33,141 edges). Required `:metadata:test` FAILED before tests in verifyWaxLabelCorpus: pinned external inputs missing/mismatched in both available caches; metadata tests NOT RUN. Full suite/lint/assemble/device/manual checks NOT RUN: T013/owner scope. No commit preparation, task remains unchecked. Scope/extra-file reasons and commands below. |
| 2026-10-14 | T012; commit: b4e4a28 (runner) | GATE: PASS on attempt 1; 105 host tests, zero failures/errors/skips; production compile PASS; final instrumented-source compile PASS (7 new device tests, execution NOT RUN). Graph changed (11,214 nodes, 33,175 edges). 21 staged source hashes verified; 19 WaxFlow raw PCM hashes independently verified before deriving float capture hashes; preservation/corruption/repo-output staging checks PASS. D-24 records full-depth fragmented ALAC and the approved D-18 DSD production integration boundary. Device/push/full suites/lint/assemble/listening NOT RUN: T013/owner scope under AGENTS.md step 8. No existing tests or production files changed. Logs: `/tmp/verify-task.fN5RNc`, `/tmp/t012-quick-gate.log`, `/tmp/t012-androidtest-compile-final.log`. Detailed impact/files/commands below. |
| 2026-10-14 | T011 finished by Claude (owner rule 2026-10-14: no Astra fallback, Claude takes failed tasks) | Kept Codex's OfflineDecoder change (registry built from the injected providers, decode-failure log with provider + typed refusal, cancellation not logged) after diff review. Fixes: offline matrix picks integer AIFF (float AIFF joins in T012a, task line extended); `sync/ScanGateTest` had 3 stale expectations since T006–T009 (DSD/WMA/IMA4/ALAC now admitted) that the filtered quick gates never ran: updated with spec reason, skip/no-device intents kept via TTA and AC-3; `docs/REGRESSION.md` now sends decoder changes to `sync.*` + `analysis.*`. GATE: PASS, full `testDevQaUnitTest` 1354 tests 0 fail (1 corpus skip), sharedLogic PASS, `assembleDevQa` PASS, graph changed. migration-guard: PASS (non-blocking: one always-true assert in the corpus test, root-cause-only failure message). Lint/device NOT RUN: T013 scope. |
| 2026-10-14 | T011 Codex attempt; superseded (finished by Claude in 72f26fe, row above) | GATE: FAIL on attempt 3; 164 tests discovered: 162 pass, 1 failure, 1 existing assumption skip; compileDevQaKotlin PASS. New offline matrix reaches `waxflow-testdata/sine-f32.aiff`, refused by the current AiffExtractor with `float AIFF-C (fl32)`; float AIFF support is T012a, outside this task. Attempt 1: new-test AudioSink configuration constructor mismatch. Attempt 2: host Uri.EMPTY stub caused a null pointer in Media3's default extractor factory; added an empty-base test seam (production default unchanged). Attempt 3 logs: `/tmp/verify-task.zwi8L9`; earlier logs: `/tmp/verify-task.mAsA5G`, `/tmp/verify-task.1WSE1O`. Existing WaxFlowOracleLoudnessTest was NOT RUN because SISTRUM_CORPUS is unset (assumption skip, not PASS). Graph refreshed by the gate: changed (11,129 nodes, 32,980 edges). Full suites/sharedLogic, lint, assemble, ExoPlayer scheduling/device checks NOT RUN: T013 scope. Commit preparation/staging/commit/push NOT RUN: gate failed. Existing tests untouched. |
| 2026-10-14 | T010; commit: a836dd6 (runner) | GATE: PASS on attempt 2; 104 host tests, zero failures/errors/skips; compileDevQaKotlin PASS. Eight new ChannelMapperTest tests cover 3ch/5.1/reordered 6ch corpora, placement, analytic downmix gains, integer/float conversion, refusals, seeks/EOS/storage and wrapped-provider output limits. Existing tests unchanged. D-23 records API 33+ route counts, logged unknown/pre-33 limits, and stereo reductions for smaller intermediate layouts. Graph changed; both generated artifacts included. Files/impact/commands detailed below. Full suites, lint, assemble, phone, listening and benchmarks NOT RUN: T013/M6/owner scope. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | D-22 languages: English + Arabic only (owner; done by Claude with a Haiku subagent, not Codex, owner instruction) | Removed 11 unreviewed locales + stale `values-en`; `locales_config.xml` en, ar. `ArabicTranslationCompletenessTest` 2 tests PASS; `assembleDevQa` PASS; `lintDevQa` 0 errors (71 warnings). Added agent `translation-reviewer`. |
| 2026-10-14 | T009 finished by Claude (owner instruction; Codex quota out) | GATE: PASS (quick); `:androidApp:testDevQaUnitTest --tests com.amwolfstein.sistrum.player.decoders.*` 92 tests, 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (11,028 nodes, 32,723 edges). Root cause of the attempt-3 failure: Media3's FragmentedMp4Extractor uses android.util.Pair (null on the plain host stub), not the URI. Fix: host MP4 test runs the full demux for the five ordinary MP4 files and only extractor selection for the three fragmented goldens, whose Media3 demux moves to T012 on `.qa` (D-20). Codex's attempt-4 Robolectric change (build.gradle.kts + SDK jars) reverted: too heavy for one test. Existing-test edits reviewed (ALAC skip → fallback per US1 acceptance 3; provider lists gain kotlin-alac): spec-backed, nothing weakened. Full suites/lint/assemble/device NOT RUN: T013 scope. |
| 2026-10-14 | T009; commit: none (three attempts exhausted; finished by Claude in 81688a3, row above) | GATE: FAIL on attempt 3; 79 tests, 1 failure, 0 errors/skips; production compile PASS; MP4 integration test fails in Media3 default extractor creation on the plain Android host stub. Attempts 1/2 failed production pool-array nullability/test DiscardingTrackOutput compilation respectively; both corrected. D-20 records implementation choices. No commit bundle; T009 unchecked. Full suites/lint/assemble/device/performance/listening NOT RUN: later milestone/owner scope. Staging/commit/push NOT RUN: failed gate, AGENTS.md steps 5/9. |
| 2026-10-14 | T008; commit: a484e26 (runner) | GATE: PASS on final attempt 3; 67 tests, zero failures/errors/skips; compileDevQaKotlin PASS. Broader attempt 2 PASS: 87 tests including previous provider corpus regressions. All 14 PCM-variant fixtures match oracle hashes and exact seek tails; WAV PCM/float/IMA and available G.711 retain platform priority. Named unsupported and malformed failures preserve causes/provider logging. D-19 records routing and scope. Attempt 1 FAIL: new fixture count was 12 instead of 14 and wrapper did not expose underlying implementation; corrected inventory and forwarding. Attempt 3 validates the AIFF-only extractor restriction and all 21 adapter contracts through the suite. Graph refreshed; both artifacts included. Full app/sharedLogic/module suites, lint, assemble, phone and speed checks NOT RUN: T013/M6 scope. Staging/commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T007; commit: 245e349 (runner) | GATE: PASS on final attempt 3; 79 app tests, 0 failures/errors/skips; :codecs:test UP-TO-DATE (1,477 cached passing tests); compileDevQaKotlin PASS; graph changed (10,834 nodes, 32,284 edges), both artifacts included. Production DSF/DFF and WavPack DSD PCM/seek tails match exactly; 352.8/705.6 kHz targets match :dsp HQ conversion and exact seeks. DST refusal and corrupt WavPack retain typed failures/provider logging. D-18 records production/reference distinctions and rate conversion. Attempt 1 FAIL: new test assumptions incorrectly treated production 6ch DFF as refused and corrupt WavPack as playable; corrected coverage per imported production behaviour and fixture status. Attempt 2 FAIL: missing provider header-probe size; fixed to 4 KiB. Full app/sharedLogic/other module suites, lint, assemble, DSD speed and phone checks NOT RUN: T013/M6 scope. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T006; commit: 22473a4 (runner) | GATE: PASS on final attempt 3; 73 tests, 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (10,768 nodes, 32,103 edges), both artifacts included. All 52 playable WMA-family fixtures match exact oracle bytes and forward/backward/end seeks; all 15 refusals retain named typed causes and provider logging. Understated/unknown ASF durations preserve full PCM and seeks beyond estimates. D-17 records dispatch/duration/packing and the oracle label limitation. Attempt 1 FAIL: packing storage grew; fixed capacity reservation. Attempt 2 FAIL: new format assertion trusted outer WAVE tag; corrected per FR-002 and the imported raw-float oracle, retaining exact hashes/depth/encoding checks. Full suites, sharedLogic/module suites, lint, assemble, WMA speed and phone checks NOT RUN: T013/M6 scope. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T005; commit: ce81207 (runner) | GATE: PASS on attempt 2; 50 tests (11 unchanged T002, 10 T003, 6 WavPack, 6 APE/Musepack, 17 registry), 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (10,711 nodes, 31,956 edges), both artifacts included. All 31 APE and 32 Musepack fixtures match exact oracle hashes and seek tails. D-16 records the float-buffer subtype and exact end seek. Attempt 1 FAIL: refusal probe size, EOF rounding and reflection compatibility; fixed in production without editing T002. Full suites, sharedLogic/module suites, lint, assemble and device checks NOT RUN: T013 owns the full/device gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T004; commit: 1264c0b (runner) | GATE: PASS on attempt 2; 44 tests (11 unchanged T002, 10 T003, 6 provider, 17 registry), 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (10,642 nodes, 31,788 edges), both artifacts included. Checks all 67 original WavPack inputs and 96 standalone libwavpack rows; 14 DSD rows explicitly refused pending T007. Host speed: 72 files, RTF median 0.005968458, worst 0.021165319 (47.247×). D-15 records scope/method. Attempt 1 FAIL: JVM constructor compatibility, duplicate refusal prefix, packed width; all fixed without editing T002. Full suites, sharedLogic/module tests, lint, assemble and device checks NOT RUN: T013 owns the full/device gate. Correction-paired decoding NOT RUN: single-source provider uses committed standalone hybrid oracle. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T003; commit: 32bf856 (runner) | GATE: PASS on final attempt 3; 21 tests (11 unchanged T002 + 10 new), 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (10,588 nodes, 31,651 edges), both artifacts included. D-13/D-14 record cache ownership and suite setup. Attempt 1 FAIL (refusal cause + Media3 host stub); attempt 2 PASS; attempt 3 verifies suite discovery after configuration. Full suites, lint, assemble, corpus/provider speed and device checks NOT RUN: T004 owns the provider/speed signal and T013 owns the full/device gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T002 [TESTS-FIRST]; commit: f47e86b (runner) | GATE: PASS on attempt 1; 11 expected assertion failures, 0 errors/skips; compileDevQaKotlin PASS; graph changed (10,525 nodes, 31,477 edges), both artifacts included. D-12 records the reflection bridge/API. PCM/seek/refusal/allocation assertions NOT RUN past the missing-adapter assertion: T003 supplies the implementation. Full suites, lint, assemble and device checks NOT RUN: T013 owns the full gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T001 resumed; commit: none (Git metadata still mounted read-only) | Reviewed existing T001 diff and prior verification record; explicit-path `git add` failed creating `index.lock` (read-only filesystem), confirmed by `findmnt`. Implementation unchanged; tests/build/graph refresh NOT RUN again because the prior checks passed and no implementation changed. Commit/push NOT RUN: staging blocked. T001 remains unchecked; unrelated `AGENTS.md` edit left untouched. |
| 2026-10-14 | T001; commit: none here (staging blocked by read-only Git metadata; committed later as ded051c) | GATE: PASS; 4 host smoke tests, 0 failures/errors/skips; compileDevQaKotlin PASS; unsigned assembleProdRelease + R8 PASS (4m 7s); graph refreshed. D-11: merge notices after initial release packaging collision; all four notices verified in APK. Push NOT RUN: no commit. Full module/app/sharedLogic suites, lintDevQa and device checks NOT RUN: per-task quick gate; full/device gate belongs to T013. |
| 2026-10-14 | Phase A end gate; 002 spec/plan/tasks | PASS (lint: 109 MissingTranslation only) |
| 2026-10-14 | Imports 2–46 (see `IMPORT-LOG.md`; conflicts at 9, 14, 29, 42, 46 resolved and checked; env fixes at 15, 19, 21, 24, 30, 36) | PASS |
| 2026-10-14 | Import 1/46 `01759c8` → `c4e7401`; README rebrand leftover fixed | PASS |
| 2026-10-14 | Corpora rebuilt (WaxFlow/DSD/WavPack verified; WaxLabel, analysis, dsp oracles built) | D-1, D-2 |
| 2026-10-14 | Graphify scope for the 002 modules | `693a54d`, graph unchanged (no module code yet) |

## T013 M1 full gate and phone report — 2026-10-15 (BLOCKED)

Scope/impact before editing: `/tmp/t013-impact.txt`; `git grep` traced the device matrix
procedure in `scripts/test-corpus/README.md` and the task/handoff consumers. T013
requires verification and a milestone report; only this HANDOFF is changed because
completion is blocked. `tasks.md` remains unchecked. No production/test/fixture,
Room/schema/version, analysis document, preference, DecoderProvider priority,
PlaybackController API/StateFlow, architecture, ADR or spec 001 behavior changed.
Existing owner edits (`.claude/**`, `CLAUDE.md`, spec/plan, UI docs, specs 003/004)
remain untouched. No delegation, Git metadata mutation or brief file.

### Commands and results

- Initial branch `feature/media3-migration`, HEAD `20d9369`; `.codex-commit/` absent.
  `./gradlew --stop` (no daemons), `pkill -f '[K]otlinCompileDaemon'` (no match),
  `/proc/meminfo` MemAvailable 2,794,056 kB, above 2 GB. Gradle commands used
  `GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.workers.max=1
  -Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process'`; repo build
  configuration unchanged. Later memory preflights: 2,731,428 / 2,366,172 kB.
- Device `4ae58ea6`, CPH2307, Android 15. Existing QA-private `files/KotlinFormats`
  contains all 24 fixtures; each test's unchanged setup validates every source hash.
  Existing public 001 corpus retained. No corpus regeneration/push needed. Only
  allowlisted `com.amwolfstein.sistrum.dev.qa` / `.qa.test` APKs installed/instrumented;
  neither daily app was installed, instrumented or force-stopped.
- Full command:
  `bash .claude/skills/verify-task/scripts/verify.sh --files
  'specs/002-kotlin-decoders/tasks.md specs/002-kotlin-decoders/HANDOFF.md'
  --lint --instrumented
  'com.amwolfstein.sistrum.player.KotlinFormatsPlaybackTest,com.amwolfstein.sistrum.player.media3.Media3EngineGaplessTest,com.amwolfstein.sistrum.player.media3.Media3CrossfadeTest,com.amwolfstein.sistrum.player.media3.Media3EngineCoreTest,com.amwolfstein.sistrum.analysis.NormalizationLevelDeviceTest'`.
  **GATE: FAIL**, console `/tmp/t013-full-gate.log`, **logs:
  `/tmp/verify-task.1R6yM5`**. App 1381 cases: 1380 pass, no failures/errors,
  1 unchanged `WaxFlowOracleLoudnessTest` assumption skip (`SISTRUM_CORPUS` unset).
  sharedLogic 39/39 PASS; `assembleDevQa` PASS; instrumentation APK build PASS.
  `lintDevQa` BUILD SUCCESSFUL; XML has 0 errors, 71 warnings, 13 hints (script's
  generic INFO summary is not the lint verdict).
- `./gradlew :codecs:test :metadata:test :analysis:test :dsp:test --continue`:
  **FAIL**, `/tmp/t013-all-modules.log`. Codecs 1487, analysis 1127 and DSP 18
  tests UP-TO-DATE with cached zero-failure/error/skip XML. Metadata's
  `verifyWaxLabelCorpus` fails on missing/changed `owned/matroska-ffmpeg-aac.mka`;
  metadata cases **NOT RUN**. Old metadata XML (1391/89 failures) is stale, not
  this run's evidence. `git log` / `git diff ded051c^ HEAD -- metadata docs/waxlabel
  scripts/waxlabel-oracle.py` show no M1 changes to the affected source, manifests
  or verifier. Existing D-27/T021a blocker; no bypass, skip, re-pin or other-task work.
- `python3 .claude/hooks/graph_refresh.py auto`: PASS, `graph: not needed
  (no source change in scope)` (also ran through full gate). No graph files changed.
  `git diff --check`: PASS. All source/tests remain unchanged.

### Phone results and success criteria

| Device class | Result | Evidence in `/tmp/verify-task.1R6yM5/` |
|---|---|---|
| KotlinFormatsPlaybackTest | 6/7 PASS; seek FAIL | `instr-KotlinFormatsPlaybackTest.log`, `logcat-KotlinFormatsPlaybackTest.log` |
| Media3EngineGaplessTest | 7/7 PASS | `instr-Media3EngineGaplessTest.log`, `logcat-Media3EngineGaplessTest.log` |
| Media3CrossfadeTest | 16/16 PASS | `instr-Media3CrossfadeTest.log`, `logcat-Media3CrossfadeTest.log` |
| Media3EngineCoreTest | 12/12 PASS | `instr-Media3EngineCoreTest.log`, `logcat-Media3EngineCoreTest.log` |
| NormalizationLevelDeviceTest | 2/2 PASS; 86 measured records, no failed measurements | `instr-NormalizationLevelDeviceTest.log`, `logcat-NormalizationLevelDeviceTest.log` |

SC-001: all **24 pinned device fixtures** play on Media3 with provider attribution;
22 non-DSD fixtures match the independent float-capture PCM hashes. The three
fragmented ALAC goldens also match full-depth raw PCM through real Media3 demux.
DSF/DFF match the approved production converter exactly at 8821 frames, per
D-18/D-24; this is integration coverage, not independent Flick-filter parity.
This reports the committed fixture matrix, not exhaustive playback of every host
oracle input.

SC-002: all ten same-format joins PASS with at most two frames difference:
WavPack, APE, ordinary ALAC, Musepack SV7, WMA Lossless, AIFF PCM, AIFF-C sowt,
AIFF-C float, DSF, DFF. CUE-track joins NOT RUN (M5 scope).
Kotlin two-player crossfade/zero-underrun test PASS. SC-005 performance/CPU NOT RUN
(M6); no inference of a benchmark PASS from the integration test.

SC-006 seek: FAIL. First run stops at WavPack, median **301 ms** from
`[301, 264, 308]`; later formats' seek timing NOT RUN in that first invocation.
Two isolated current-build reruns pass WavPack, but both stop at WMA Voice:

| Current-build attempt | WavPack median | WMA Voice median | Voice runs |
|---|---|---|---|
| 1, full gate | 301 ms FAIL | NOT RUN | — |
| 2, isolated seek | 284 ms PASS | 312 ms FAIL | 413, 305, 312 ms |
| 3, isolated seek | 269 ms PASS | 305 ms FAIL | 417, 305, 277 ms |

Attempt 3's other completed medians (ms): APE 237, Musepack SV7/SV8 242/77,
ALAC 145, WMA1/2/Pro/Lossless 89/215/236/70. Formats after Voice NOT RUN in
these two current-build seek reruns. The 300 ms bound and three-run-median
assertion are unchanged; no WMA waiver restored. Three current-build attempts
failed, so AGENTS.md step 9 requires stopping without a commit bundle.

### Read-only M1 comparison / failure investigation

The seek test first exists at `b4e4a28`. Halved the remaining test-capable M1 tail
at `2c2c1a5`, then checked later `7203f18` against current `20d9369`. Used
`git archive <sha> | tar -x -C /tmp/t013-bisect-<sha>`; never switched/reset/rebased.
Scratch builds symlink unchanged local FFmpeg libraries/headers; repository native
files are untouched. No local.properties was created or edited (an attempted copy
found that no local.properties exists; builds use the configured SDK environment).

Both historical original APK builds PASS. Old tests require the inaccessible
public T012 fixture directory, so diagnostic test APKs use a byte-identical copy
of the current KotlinFormatsPlaybackTest against each archived production state.
Harness SHA-256 in all three trees:
`a62c8c7a4597be683b70cf04f3b8640f9a83ec422098a76aa78242c825a4c03e`.
No assertions, expected values, inventory or timing bounds changed. Rebuilt only
`:androidApp:assembleDevQaAndroidTest`; both builds PASS. Ran **only**
`KotlinFormatsPlaybackTest#seekToDecodedOutputCompletesWithin300MsPerFormat`
using `.qa.test/androidx.test.runner.AndroidJUnitRunner` after checking both
APK IDs with aapt2. Current-build isolated reruns use the same method and APK-ID
checks (`/tmp/t013-run-isolated.sh`).

- `2c2c1a5`: WavPack **263 ms** `[253,302,263]`, Voice **292 ms**
  `[388,292,286]`; later short-tail fixture times out with `[OutputStarted, Ended]`.
- `7203f18`: WavPack **277 ms** `[250,277,285]`, Voice **293 ms**
  `[392,293,229]`; same later short-tail timeout.
- Current `20d9369`: timing results above. Observed Voice boundary narrows to
  the latest T013-fix2 commit, but this small timing difference and WavPack's
  non-reproduction do not establish a deterministic culprit. Short-tail behavior
  in older states is also different. No speculative decoder/preroll/quality change
  or claim of a completed causal bisection. Further profiling/fix remains undone.

Build logs: `/tmp/t013-bisect-{2c2c1a5,7203f18}-build.log` and
`/tmp/t013-bisect-{2c2c1a5,7203f18}-harness-build.log`.
Instrumentation/logcat/install evidence:
`/tmp/t013-bisect-{2c2c1a5,7203f18}-{instr,logcat,install}.log` and
`/tmp/t013-current-seek-attempt{2,3}-{instr,logcat,install}.log`.
Current QA app/test APKs restored before attempts 2/3; no older QA build remains
installed. No daily-app access.

Read-only log triage: no FATAL EXCEPTION, ANR or closed-channel exception in any
of the five full-gate logs. Crossfade/core Source errors correspond to intentional
missing/corrupt-input tests, including the already-recorded malformed-FLAC loader
IllegalArgumentException. Dead-handler warnings remain: 238 in decoder class,
942 in normalization (known deferred release ordering). No underrun failure.
No automated agent was started for triage; manual inspection only.

Final review: this HANDOFF is required to preserve the failed milestone evidence;
all test expectations and protected contracts stay unchanged. T013 remains
unchecked; `.codex-commit/` absent. Stage/commit/push NOT RUN (failed gate,
AGENTS.md steps 5/9). Owner listening/Bluetooth/headset checks NOT RUN, never
PASS. Retain the existing M1 manual list below. No new decision/ADR or follow-up
task authored; existing T021a owns the metadata input repair.

## T013-fix2 scope and impact review

### Completed resumed implementation — 2026-10-15

T013-fix2 is complete; commit prepared for the runner on `feature/media3-migration`.
Earlier blocked trials below are historical. T013 must rerun its `.qa` phone gate:
previous device evidence retained the WMA waiver and predates these packet seeks.

Impact before editing: `git grep` confirmed ASF streams → `KotlinWmaProvider` →
playback/`OfflineDecoder`; `Demux` serves all four variants. Notes:
`/tmp/t013-fix2-resume-impact.txt`. No Room, analysis-document/version, preference,
provider-priority, PlaybackController API/StateFlow, ADR, architecture or 001 change.

Files required (all 24 commit paths are in `.codex-commit/files.txt`):

- `codecs/.../container/asf/{Demux,Wma,WmaPro,WmaLossless,WmaVoice}.kt`: inherited
  complete-object packet seek/preroll and exact target discard. This resume adds
  first-fragment source offsets and noise checkpoints for Standard WMA. Repeated
  timestamps cannot alias checkpoints.
- `codecs/.../codec/{wma,wmapro,wmalossless,wmavoice}/Decode.kt`: inherited true
  synthesis counters and Lossless independent-tile probe. Standard now restores
  its deterministic noise phase by parsing uncached compressed history, including
  carried frames, without PCM reconstruction/IMDCT/overlap synthesis. Uncoded
  channels consume no noise; sample count alone cannot supply this phase. Seek
  preparation caches phases; steady-state playback does not allocate checkpoints
  (FR-008). Cold uncached noise history still costs O(prefix) entropy parsing.
- `WmaPacketSeekTest.kt`: nine inherited long-stream/index cases untouched,
  including the formerly failing exact entire-tail noise hash/work bound. One
  added test pins 512-sample coded-channel advances, no advance for an uncoded
  frame, zero scan synthesis, malformed-packet latching and reset.
  `WmaVoiceOracleTest.kt`: only the three D-30-authorized seek expectations change;
  linear hashes, position/length, finite PCM and other exact checks are retained.
- Item (4) retained: `ExtractorRandomAccessSource.kt`, `KotlinDecodingExtractor.kt`,
  their two host test files and `OfflineDecoderKotlinFormatsTest.kt` resume at the
  cache end, asserting no prefix read/skip and exact PCM after seek/interruption.
- `KotlinFormatsPlaybackTest.kt`: inherited DSD 8821-frame expectation, explicit
  `beginCrossfade`, private `.qa` fixtures and three-run median; removed only the
  WMA timing waiver, restoring the same 300 ms bound for every covered seek.
  `Media3PlayerFactory.kt`: inherited SDK_INT lint guard. Corpus `README.md`:
  inherited private fixture procedure. `Dsd.kt` remains untouched.
- `DECISIONS.md`: D-18/D-24, inherited D-29/D-30 and final noise-state choice;
  `tasks.md`: tick T013-fix2 only; this HANDOFF: status/log/evidence. Required shared
  documents retain owner D-31/D-32/planning text without edits here. Both graph
  artifacts are generated by the required hook, never hand-edited. Separate owner
  edits (`.claude/**`, `CLAUDE.md`, spec/plan/UI/specs-003/004) remain untouched and
  are excluded from the bundle. No extra implementation file was needed.

Verification (three successful implementation/verification passes):

1. Correct branch; `.codex-commit/` absent initially. Before verification:
   `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'` (exit 1: none);
   MemAvailable 2613664 / 2676788 / 2858416 kB (all ≥2 GB). All Gradle commands
   use `GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.workers.max=1
   -Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process'`.
   Repository Gradle configuration is unchanged.
2. `./gradlew :codecs:test --tests '*WmaPacketSeekTest' --tests '*Wma*OracleTest'`:
   PASS, 210 cases; `/tmp/t013-fix2-phase-attempt1.log`.
3. `bash .claude/skills/verify-task/scripts/verify.sh --files
   "$(cat /tmp/t013-fix2-gate-files.txt)" --quick ':codecs:test
   :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest
   :androidApp:compileDevQaAndroidTestKotlin'`: **GATE: PASS**,
   `/tmp/verify-task.MEwYTN`; console `/tmp/t013-fix2-phase-quick-gate.log`.
   Codecs 1487/0 fail/error/skip; app 1381/0 fail/error/1 unchanged corpus skip
   (1380 pass); sharedLogic 39 cached passes. Production/device Kotlin compile
   PASS; generated graph changed (11325 nodes / 33436 edges).
4. Final allocation review removed playback-loop checkpoint insertion. Final
   quick command uses the same `--files` and
   `--quick ':codecs:test :androidApp:testDevQaUnitTest --tests
   com.amwolfstein.sistrum.player.decoders.kotlin.KotlinWmaProviderTest --tests
   *KotlinDecodingExtractorTest --tests *ExtractorRandomAccessSourceTest --tests
   com.amwolfstein.sistrum.analysis.* --tests com.amwolfstein.sistrum.sync.*
   :sharedLogic:testAndroidHostTest :androidApp:compileDevQaAndroidTestKotlin'`:
   **GATE: PASS**, `/tmp/verify-task.lHROXH`. Console `/tmp/t013-fix2-phase-final-gate.log`.
   Codecs 1487/0 fail/error/skip; app 378/0 fail/error/1 unchanged corpus skip; sharedLogic 39 cached passes; production/device compilation PASS. The broad gate above supplies full-app evidence before the
   allocation-only refinement; unchanged WavPack app tests were not repeated.
5. Required downstream `./gradlew :metadata:test`: FAIL before tests in `verifyWaxLabelCorpus`: missing/changed `owned/matroska-ffmpeg-aac.mka`; log `/tmp/t013-fix2-phase-metadata.log`.
   Metadata cases NOT RUN, not PASS. Both caches mismatch the pinned input;
   no verifier/fixture/hash change, bypass or skip was added. This is the existing
   T021a precondition; metadata consumes only unchanged `RandomAccessSource`, never
   these decoder/ASF implementations (same dependency evidence as D-27).
6. Host diagnostic `/tmp/NoiseSeekTiming.java`, same pinned 190.08 s noise payloads,
   seek 1429418: cold + first PCM 128.727 / 150.037 / 21.620 ms; cached 1.571 /
   1.140 / 1.344 ms. `/tmp/t013-fix2-noise-phase-timing.log`. Before the allocation
   refinement; diagnostic only, not a phone SC-006 PASS or acceptance timeout.
7. `git diff --check`: PASS. Required
   `python3 .claude/hooks/graph_refresh.py auto` ran through the gate: PASS, changed (11325 nodes / 33436 edges).
   `.codex-commit/files.txt` and `message.txt` prepared. Stage/commit/push NOT RUN:
   runner owns them.

Self-review: original Standard/Pro/Lossless and inherited noise tests remain
intact; only D-30's authorized three Voice quality expectations differ. No disabled
or weakened tests/timeouts, swallowed exceptions/cancellation, hidden prefix
synthesis, feature removal or PlaybackController/Room/architecture change. NOT RUN: metadata
cases (pinned corpus), existing loudness oracle (`SISTRUM_CORPUS` unset), phone,
lint/assemble (T013), listening/Bluetooth/headset (owner). M6 must include cold
noise-coded seeks on real long files, because uncached entropy-history work grows
with prefix length. No phone/listening result is claimed here.

### Earlier blocked item (5) outcome — 2026-10-15 (superseded)

**BLOCKED; do not ship this trial.** Three completed implementation attempts were used;
no further retry, no task tick and no `.codex-commit/`. The memory-interrupted gate
`/tmp/verify-task.k8Pxjk` has no verdict and is not a passing result.

Final blocker: `WmaPacketSeekTest.noiseCodedStandardColdSeekHasExactPositionLengthAndBoundedWork`.
It remuxes unchanged pinned `v2-16000-1ch.wma` payloads into 3041280 frames (190.08 s),
then cold-seeks to 1429418 (47% + 17). Exact first position, total tail length and the
three-packet synthesis bound pass; the entire-tail SHA-256 differs from linear PCM.
The failure is at `WmaPacketSeekTest.kt:124`. Suspected mechanism: WMA's historical
noise-table phase is reset to zero by `Decoder.reset`; packet seek does not restore
that phase. This is not authorization to relax Standard's exact-PCM requirement.
All 1477 original codec cases pass, including unchanged fragmented Standard and
Lossless oracle seeks. Eight of nine new packet-seek cases pass: Standard, low-rate
Standard, Pro, 16/24-bit and surround Lossless, Voice, and index-hint/corrupt-index cases.

Changes made in this dispatch (paths relative to the repository):

- `codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/Demux.kt`:
  validated Simple Index hints, timestamp search, whole-media-object rewind before
  preroll, and header-only restart selection. This fixes fragmented Standard's
  2048/12288 misposition and incomplete-object startup; no fragment decode is skipped.
- The same directory's `Wma.kt`, `WmaPro.kt`, `WmaLossless.kt`, `WmaVoice.kt`:
  packet restarts and exact target discard. Trial choices per D-30: Standard/Pro two
  media objects; Lossless nearest independent first tile, zero extra overlap preroll;
  Voice 18 media objects so the pinned short speech files retain unchanged app exact
  seeks. The long Voice budget is 18 + 1 packets, not an increased timeout or relaxed
  quality bar. No device seek-latency PASS is claimed.
- `codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/{wma,wmapro,wmalossless,wmavoice}/Decode.kt`:
  monotonic decoder synthesis counters; Lossless also probes the frame header/tiling
  for an independent first tile. Required because the acceptance budget measures
  actual codec work, including discarded/failed startup frames. No PCM synthesis
  formula changed and no exception is swallowed by the header probe.
- `codecs/src/test/kotlin/com/amwolfstein/sistrum/codecs/WmaPacketSeekTest.kt` (new):
  nine long-stream/index cases, whole seek-tail hashes/length, finite output, true codec
  work counters, off-boundary positions and lengths derived from pinned payloads.
- `codecs/src/test/kotlin/com/amwolfstein/sistrum/codecs/WmaVoiceOracleTest.kt`:
  only the three authorized 8 kHz seek-quality expectations move to D-30's finite,
  non-silent, ±1 dB RMS over 0.5 s bar. Linear oracle hash, exact position/length,
  zero seek and the other parameter expectations remain; contiguity checks added.
- `specs/002-kotlin-decoders/DECISIONS.md`: D-30 trial choices, remaining exact-PCM
  blocker and no phone claim. `HANDOFF.md`: this outcome and corrected Current status.
- `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json`: generated by
  the required gate's `python3 .claude/hooks/graph_refresh.py auto`; never hand-edited.

Inherited task edits retained, with no reimplementation of item (4):

- `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSource.kt`
  and `KotlinDecodingExtractor.kt`: resume at the cache end.
- `androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSourceTest.kt`,
  `KotlinDecodingExtractorTest.kt`, and
  `androidApp/src/test/kotlin/com/amwolfstein/sistrum/analysis/OfflineDecoderKotlinFormatsTest.kt`:
  cache resume/no-prefix-reread and interrupted-reopen regression assertions.
- `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/KotlinFormatsPlaybackTest.kt`:
  inherited DSD frame count, explicit crossfade start, private fixtures and seek timing;
  WMA waiver intentionally remains because correctness still fails.
- `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3PlayerFactory.kt`,
  `scripts/test-corpus/README.md`, and the existing T013-fix2 line in `tasks.md`:
  inherited lint guard, private fixture procedure and authorized task scope.

Other owner changes (`.claude/**`, `CLAUDE.md`, D-31/D-32 and other-milestone text,
`spec.md`, `plan.md`, specs 003/004, `docs/ui-overhaul/`) were not implemented or
reverted here. No other files were needed for the task. No Room, analysis-version,
preferences, provider priority, PlaybackController API/StateFlow, ADR or shipped
001 contract change; the WIP is not safe to commit because its exact-PCM test fails.

Commands/results:

1. `git grep` inspected the four stream callers/seek consumers and the protected
   WMA oracle/contracts/provider tests before editing; impact notes
   `/tmp/t013-fix2-impact-notes.txt`. Saved patch applied with `git apply`.
2. Header/timing diagnostic (not acceptance): `./gradlew -I
   /tmp/t013-fix2-diagnostic.init.gradle :codecs:test --tests
   com.amwolfstein.sistrum.codecs.PacketLosslessDiagnostic` PASS; log
   `/tmp/t013-fix2-current-diagnostic.log`.
3. Attempt 1: `./gradlew :codecs:test --tests '*Wma*OracleTest' --tests
   '*WmaPacketSeekTest'` FAIL on fragmented Standard and old Voice exact expectations;
   log `/tmp/t013-fix2-restart-attempt1.log`.
4. Attempt 2: `./gradlew :codecs:test` FAIL, 1484 cases, 1 new surround Lossless
   work-budget failure, zero errors/skips; all original cases pass. Log
   `/tmp/t013-fix2-restart-attempt2.log`. Focused `./gradlew
   :androidApp:testDevQaUnitTest --tests '*KotlinWma*'` FAIL, 6 cases/1 Voice exact
   mismatch before the 18-object preroll fix; `/tmp/t013-fix2-focused-app.log`.
5. Final attempt 3: `bash .claude/skills/verify-task/scripts/verify.sh --files
   "$(cat /tmp/t013-fix2-gate-files.txt)" --quick ':codecs:test
   :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest
   :androidApp:compileDevQaAndroidTestKotlin'` prints **GATE: FAIL**. Logs
   `/tmp/verify-task.9TJbzp`, console `/tmp/t013-fix2-memory-safe-gate.log`.
   Codecs 1486/1 fail/0 errors/0 skips; app 1381/0 fail/0 errors/1 existing skip;
   unchanged KotlinWmaProviderTest now 6/6 PASS; item (4) suite and offline test PASS;
   sharedLogic 39 cached passes; production compile UP-TO-DATE/PASS, device-test
   Kotlin compile PASS; graph changed 11322 nodes/33426 edges.
6. Required downstream `./gradlew :metadata:test` FAIL before cases run:
   `verifyWaxLabelCorpus` missing/changed `owned/matroska-ffmpeg-aac.mka`.
   Log `/tmp/t013-fix2-final-metadata.log`. Metadata cases NOT RUN; known T021a
   corpus precondition, no fixture/hash/verifier modification to bypass it.

Memory recovery: stopped Gradle daemons and Kotlin compiler daemons. The guard
interrupted `/tmp/verify-task.k8Pxjk`; subsequent restarts were stopped on low
memory (no test verdict). Followed the 20-minute wait, 08:23:21–08:43:29 UTC;
MemAvailable then 2590816 kB (≥2 GB). Final gate and metadata used
`GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.workers.max=1
-Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process'` to cap the
Gradle heap, use one worker and avoid a separate 3 GB Kotlin compiler daemon.
Repository Gradle configuration was not changed.

Self-review: no test deleted/ignored/disabled; only D-30's authorized Voice quality
change, with stronger position/length/finite checks. All other existing assertions
unchanged beyond inherited task edits; no hidden prefix decode in the work counter.
New failing noise-coded test stays intact. Final `git diff --check`: PASS. NOT RUN: `WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures`
(`SISTRUM_CORPUS` unset; unchanged assumption), metadata cases (pinned corpus
precondition), phone/lint/assemble (T013), listening/Bluetooth/headset (owner),
waiver removal/task tick/commit bundle/stage/commit/push (failed gate).


### Resumed D-30 implementation outcome (2026-10-15)

Three attempts, no further retry and no commit preparation:

1. `./gradlew :codecs:test --tests com.amwolfstein.sistrum.codecs.WmaPacketSeekTest
   --tests '*Wma*OracleTest'`: compilation FAIL (`LongArray.zipWithNext` does not
   exist); corrected with an adjacent-index check. Log
   `/tmp/t013-fix2-seek-attempt1.log`.
2. Same focused command: FAIL. It exposed cold Lossless EOF, Voice PCM differences,
   Standard's extra startup frame in the work budget, and incorrect timestamps in
   the original long Voice fixture repetition. Log
   `/tmp/t013-fix2-seek-attempt2.log`. Corrected the Standard audible decision
   offset; regenerated Voice from a complete pinned speech superframe; tried
   three-packet preroll for Lossless/Voice. Existing tests were never edited.
3. Required quick gate: FAIL, `/tmp/verify-task.HvoqOC`, console
   `/tmp/t013-fix2-quick-gate-resumed.log`. Command is `bash
   .claude/skills/verify-task/scripts/verify.sh --files '<the 12 Kotlin paths
   listed below>' --quick ':codecs:test :androidApp:testDevQaUnitTest
   :sharedLogic:testAndroidHostTest :androidApp:compileDevQaAndroidTestKotlin'`.
   Scope PASS. The codecs suite discovers 1481 cases: 1473 pass, 8 fail, no skips.
   App: 1379 cases, 1377 pass, 1 failure, 1 unchanged `SISTRUM_CORPUS`
   assumption skip, 0 errors; sharedLogic: 39 cached passing cases. Production
   and device-test Kotlin compilation PASS. Graph refresh PASS, changed (11302
   nodes / 33360 edges). Final printed result: `GATE: FAIL`.

Final failures (JUnit XML under `codecs/build/test-results/test` and
`androidApp/build/test-results/testDevQaUnitTest`):

- `WmaLosslessOracleTest.exactSeekingAgainstOracleVerifiedLinearPcm`:
  `ll-44100-2ch-24.wma` and `ll-48000-6ch-24.wma` return null before the
  requested target (existing assertions throw NPE).
- `WmaOracleTest` on `container/asf/testdata/frag.wma`:
  `partialSourceAndDirectBufferRegion` requests sample 1 but receives position
  2048; `exactSeekingAgainstOracleVerifiedLinearPcm` requests 11264 but receives
  12288. Selecting the packet by its delayed timestamp can bypass the media
  object's first fragment; packet reassembly/start selection remains incorrect.
- `WmaPacketSeekTest.losslessColdSeekHasExactPositionLengthAndBoundedWork`:
  generated linear length 8388608 frames; target 4194321; expected remaining
  4194287, actual 4186095 (8192 frames lost). A cold Lossless restart can drop
  predictor-dependent frames before a seekable tile; the stream's position does
  not account for that loss. Its earlier first-half-second-only comparison was
  insufficient; the final test additionally hashes the entire Standard/Pro/
  Lossless seek tail against linear decode.
- `WmaVoiceOracleTest.exactSeekingAgainstOracleVerifiedLinearPcm`:
  `voice-8000-4k.wma`, `voice-8000-5k.wma`, `voice-8000-8k.wma` retain different
  PCM after three-packet restart. These tests remain unchanged, as required.

- App `KotlinWmaProviderTest.seeksForwardBackwardAndAfterEofMatchOracleVerifiedPcmForEveryFixture`:
  fragmented WMA at 11264 has expected tail byte length 90112, actual 81920;
  first byte differs (expected -58, actual 4). This is the unchanged downstream
  regression catching the same production defect, not an external corpus issue.

The new long Standard and Pro cases compare every tail sample via raw-float hash
and exact first-half-second values; Voice checks finite/non-silent output and
±1 dB RMS over 0.5 s. All three PASS on the final trial. Frame work comes from
codec counters and is bounded by the trial preroll + 1 packet. This evidence does
not cover all noisy Standard modes, all Lossless predictor layouts, all possible
fragmentation, or valid/corrupt Simple Index edge cases; these remain required
before shipping. No selected final preroll or SC-006 phone PASS is claimed.

Files/extra-file reasons (all within this task's implementation or required log):

- `codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/container/asf/`:
  `Demux.kt` adds Simple Index hints, fixed-packet timestamp search and rewind to a
  chosen packet; `Wma.kt`, `WmaPro.kt`, `WmaLossless.kt`, `WmaVoice.kt` perform
  trial reset/preroll/position/discard seeks. These changes are incomplete; keep
  the failure evidence with them, do not ship.
- `codecs/src/main/kotlin/com/amwolfstein/sistrum/codecs/codec/`:
  `wma/Decode.kt`, `wmapro/Decode.kt`, `wmalossless/Decode.kt`,
  `wmavoice/Decode.kt` add internal monotonically increasing decoded-frame work
  counters. Extra file reason: acceptance requires the decoder's own counters,
  including work discarded at seek; the configuration-only files named in the
  brief are not where synthesis occurs. PCM processing is unchanged there.
- New `codecs/src/test/kotlin/com/amwolfstein/sistrum/codecs/WmaPacketSeekTest.kt`:
  written before production changes; four generated ≥3-minute pinned-payload
  cold-seek acceptance cases, exact positions/length, frame work and quality.
- Retained explicitly authorized inherited files:
  `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/KotlinFormatsPlaybackTest.kt`,
  `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3PlayerFactory.kt`,
  `scripts/test-corpus/README.md`, `specs/002-kotlin-decoders/tasks.md` task line.
  DSD production rounding, crossfade driving, private QA fixtures and lint guard
  remain as previously corrected. No other existing test/assertion changed.
- `specs/002-kotlin-decoders/DECISIONS.md`: retain D-29 and D-18/D-24; D-30
  addendum records the failed trial, not a replacement architecture or acceptance.
  `HANDOFF.md`: revised impact, current blocker and evidence; task stays unchecked.
- `graphify-out/graph.json`, `graphify-out/.graphify_analysis.json`: generated
  only by required graph refresh; inherited/generated changes retained.
  `.claude/agents/pr-test-analyzer.md` and `CLAUDE.md` are unrelated inherited
  owner edits and were left untouched.

Memory gates: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`; `/proc/meminfo`
MemAvailable 3127420 kB initially, 3158416 kB before the final quick gate (≥2 GB).
Scratch timing/seek diagnostics also ran via
`./gradlew -I /tmp/t013-fix2-diagnostic.init.gradle :codecs:test --tests
com.amwolfstein.sistrum.codecs.PacketTimingDiagnostic` and `...PacketSeekDiagnostic`;
these are observations, not acceptance. Logs `/tmp/t013-fix2-timing.log`,
`/tmp/t013-fix2-timing2.log`, `/tmp/t013-fix2-seek-diagnostic.log`. Existing scratch
files are retained for reproduction; complete-superframe remux is now a real test.

Required downstream `./gradlew :metadata:test`: FAIL before test execution in
`verifyWaxLabelCorpus`, `Missing/changed oracle input:
owned/matroska-ffmpeg-aac.mka`. Log `/tmp/t013-fix2-metadata-regression.log`.
Metadata test cases NOT RUN (known D-27/T021a corpus precondition); verification
was not disabled, no corpus hashes or fixtures changed to bypass it. `:metadata`
uses only the unchanged `RandomAccessSource` API, but the ledger-required command
was still attempted. `git diff --check`: PASS. `.codex-commit/`: absent.

Self-review: no tests deleted/ignored/weakened, no public playback API/StateFlow,
Room/schema, provider ordering, normalization or analysis-document changes. The
implementation does **not** pass position/length/test-strength acceptance, so it
must not be committed. D-30's approval is retained; no state-cache/predecode design
or alternative architecture was introduced. Unit failures are reported, not
suppressed. Phone, lint, assemble and manual listening/Bluetooth/headset checks:
NOT RUN (T013/owner scope). Commit/stage/push: NOT RUN (failed task; runner-only).


Resumed pre-edit impact review (2026-10-15, revised D-30 task): retained scope is
ASF packet selection/reassembly and the four WMA stream seek adapters; codec reset
and work counters may need changes to support bounded, position-exact restart.
`git grep` confirms consumers in `KotlinWmaProvider`, codec oracle/contract tests
and benchmarks. Packet metadata must remain advisory during linear decoding.
No Room, analysis-document, preferences, DecoderProvider order or PlaybackController
contracts change. Existing oracle/contract tests, adapter T002 tests and all
non-target device assertions stay unmodified. New acceptance tests are written
before production seek changes. D-30 now permits Voice quality within ±1 dB RMS
instead of exact PCM; Standard/Pro/Lossless retain the task's exact/90 dB limits.
Validation includes codecs, downstream metadata and app decoder/Media3 tests,
sharedLogic and device-source compilation. Device execution remains T013 only.
The inherited `.claude` and CLAUDE.md owner edits are outside this commit.

Pre-edit impact notes (2026-10-15): `.codex-commit/` absent; branch
`feature/media3-migration`. `git grep` finds the four ASF streams consumed by
`KotlinWmaProvider`, the corresponding codecs oracle/contract tests and benchmarks.
`KotlinDecodingExtractor.seek` keeps its stream and cached source; Media3 reopens
the input at byte zero, then the retained codec receives `seekSample`. Required
behavior is exact linear-decode PCM after packet-level seek with bounded preroll.
No Room/schema, analysis document, preferences, provider ordering, or
PlaybackController contract changes are planned. All existing codecs oracle and
contract tests, T002 adapter tests, and non-target device assertions must remain
unchanged. Required validation: `:codecs:test`, app decoder/Media3 tests and
production/device-test Kotlin compilation; device execution belongs to T013.

Retain the already-present T013 edits explicitly named by the task:
KotlinFormatsPlaybackTest private QA fixture path, Media3PlayerFactory lint API
guard, corpus README and D-29. Initial unrelated `.claude/agents/pr-test-analyzer.md`
and `CLAUDE.md` changes are left untouched. Initial task-line and generated graph
changes are preserved. Check Voice's recursive history and WMA noise before
committing a bounded-preroll seek: ASF offsets alone do not restore decoder state.

Implemented partial changes:

- KotlinFormatsPlaybackTest: production-D18 comparisons use the production codec's
  sample count (8821, not Flick's 8820), including the join reference. Other fixture
  expectations and every existing assertion remain intact. The crossfade test waits
  for the prepared incoming player and `positionMs() + fadeLeadMs() >= durationMs()
  - 1000`, calls `beginCrossfade(1000)`, and requires success before its existing
  transition/PCM/underrun assertions. No shipped crossfade logic changed.
- DECISIONS: required D-18/D-24 rounding addendum, retaining the pre-existing D-29.
- HANDOFF: impact, evidence and incomplete task status. These are the only three
  manually edited files in this session. Required graph auto-refresh additionally
  rewrote `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json`;
  neither was edited manually. The inherited factory, README and task-line edits
  remain in the working tree; unrelated owner edits remain untouched.

WMA investigation (scratch code only; codecs and ASF production files unchanged):

- `./gradlew -I /tmp/t013-fix2-diagnostic.init.gradle :codecs:test --tests
  com.amwolfstein.sistrum.codecs.PacketPrerollDiagnostic` uses an extra test source
  directory under `/tmp/t013-fix2-diagnostic/`; it is not a repository test change
  or an acceptance PASS. Source and init script remain available for reproduction.
- `/tmp/t013-fix2-diagnostic-asf.log`: 2 diagnostic assertion failures. A generated
  fixed-packet ASF containing repeated, unchanged pinned Voice media objects
  decodes to 238.97625 seconds (900 ASF packets). Existing cold `seekSample` to
  sample 1911810 preserves the entire linear PCM tail but synthesizes 1915200
  pre-output frames, compared with a two-packet maximum of 16320. Frame counts
  come from the decoder's existing `frameCounter` (160 samples per increment),
  including discarded synthesis; no wall-time proxy. This short stream never
  reaches the counter's 65535 wrap.
- `/tmp/t013-fix2-diagnostic-bounds.log`: 2 diagnostic assertion failures. Two
  media-object restarts disagree in 1669 of 2767 target samples, even with the
  exact frame counter restored. Three earlier restarts **do** recover exact PCM
  within the larger 16320-frame allowance; that trial alone is not evidence that
  all bounded-preroll seeks are impossible.
- `/tmp/t013-fix2-diagnostic-small.log`: 1 diagnostic assertion failure, no skip.
  A complete generated ASF with one unchanged pinned speech superframe per
  media object/ASF packet decodes successfully to 183.06 seconds (6102 packets).
  The 65-byte codec packets each synthesize 480 frames, so two packets allow 960
  frames. At packet 3000, every start within this budget differs from linear PCM:
  480/480 samples at 0 frames of preroll, 480/480 at 480, and 473/480 at 960.
  The exact linear frame counter is restored in each trial. The encoded speech
  bits are extracted from the pinned `voice-16000-12k.wma`, not invented audio.
  Generated files: `/tmp/t013-fix2-voice-concatenated.wma` and
  `/tmp/t013-fix2-voice-small-packets.wma`. Final failing JUnit XML is preserved
  in `/tmp/t013-fix2-small-results/`. An intermediate scratch helper refactor
  had a compilation error (log `/tmp/t013-fix2-diagnostic-small-compile.log`);
  corrected before the final diagnostic ran.
- Relevant state is visible in `wmavoice/Decode.kt` (`exc`, `synth`, `prevLSF`,
  gain prediction and history) and `Postfilter.kt` (`agcMem`, recursive DC state).
  ASF Simple Index/send times give offsets, not those prior numeric states.
  Building a full numeric-state index during open would move unbounded decoding
  outside the seek measurement, rather than establish bounded cold seek work.
  No checkpoint/predecode architecture, approximate PCM fallback, or relaxed
  frame-bound test was introduced. The cold-vs-prior-playback interpretation was
  asked explicitly; no answer received during this work. The natural cold-seek
  interpretation is recorded, not an invented approval for an architecture change.

Verification and self-review: partial quick regression gate PASS on attempt 1.

Command: `bash .claude/skills/verify-task/scripts/verify.sh --files
'androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/KotlinFormatsPlaybackTest.kt
androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3PlayerFactory.kt'
--quick ':codecs:test :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest
:androidApp:compileDevQaAndroidTestKotlin'`. Logs `/tmp/verify-task.rYtiij`, console
`/tmp/t013-fix2-quick-gate.log`. Scope PASS; codecs FROM-CACHE (1477/0 failures,
0 errors, 0 skips), app UP-TO-DATE (1379/0 failures, 0 errors, 1 unchanged
WaxFlowOracleLoudnessTest assumption skip: `SISTRUM_CORPUS` unset), sharedLogic
UP-TO-DATE (39/0 failures/errors/skips). Device-test Kotlin compiled anew; production
compileDevQaKotlin UP-TO-DATE. The normal codecs suite contains no scratch diagnostic
class: it passes existing regressions, not the unmet packet-seek requirement.
Gate's `python3 .claude/hooks/graph_refresh.py auto` PASS, changed both generated
artifacts (11285 nodes / 33313 edges); no commit/staging because task incomplete.
`git diff --check` PASS. Codecs API/decoded output unchanged, so the ledger's
API/output-triggered `:metadata:test` propagation is not applicable.

Memory preflight before the gate:
`./gradlew --stop` reports no running daemons; `pkill -f '[K]otlinCompileDaemon'`
returns 1 (no match); `MemAvailable` 3280140 kB, above 2 GB. Existing codecs tests,
T002 tests and non-target device assertions are unchanged. DSD's changed expectation
has the explicit task/D-18 reason. No features, APIs, Room data/contracts, error
paths or cancellation handling were changed. NOT RUN: device, listening/Bluetooth/
headset, lint/assemble/full milestone gate (T013/owner scope), new four-flavour
packet-seek acceptance tests (product implementation unresolved). Task remains
unchecked; staging/commit/push and `.codex-commit/` preparation NOT RUN because the
whole task is incomplete. Follow-up requires resolving the exact cold-seek/state
constraint, not a change to other milestone tasks.

## T013-fix scope and impact review

Commands and results:

- Before each of the three verification passes: `./gradlew --stop`, `pkill -f '[K]otlinCompileDaemon'`, `MemAvailable` check. No Gradle daemons were running; available memory was 3,525,756 / 3,586,620 / 3,581,728 kB, all above the 2 GB gate threshold.
- `bash .claude/skills/verify-task/scripts/verify.sh --files '<the task Kotlin paths in .codex-commit/files.txt>' --quick ':androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest :codecs:test :androidApp:compileDevQaAndroidTestKotlin'`: all three passes printed `GATE: PASS`. Logs: `/tmp/verify-task.30KNIY`, `/tmp/verify-task.80eDQg`, final `/tmp/verify-task.SDRQjU`. The script also runs `:androidApp:compileDevQaKotlin` and `python3 .claude/hooks/graph_refresh.py auto`; both passed, graph changed. Final app run: 1,379 cases / 0 failures / 0 errors / 1 existing assumption skip. sharedLogic (39) and codecs (1,477) were UP-TO-DATE with passing XML; device-test compilation passed initially and remained UP-TO-DATE on later passes.
- `git diff --check`: PASS. `git diff --quiet HEAD -- .../KotlinDecodingExtractorTest.kt`: exit 0, original T002 unchanged. `git branch --show-current`: `feature/media3-migration`.
- NOT RUN: the original `WaxFlowOracleLoudnessTest.matchesWaxFlowOracleFixtures` because `SISTRUM_CORPUS` is unset (reported by its unchanged assumption); device execution, lint, assemble (T013), listening/Bluetooth/headset (owner), performance (M6). Other module suites are not required by the app-only scope and regression ledger. Staging/commit/push NOT RUN: runner owns those operations.


Impact-first searches used `git grep` for `ExtractorRandomAccessSource`, `recordOutcome`,
`pendingTrackRows`, `writeResult`, `ANALYZER_VERSION`, `RegistryScanGate`, `Media3PlayerFactory`
and `maxOutputChannels`. Consumers: KotlinDecodingExtractor; offline TrackAnalyzer/analysis
workers; LoudnessStore and scheduler progress readers; PlaybackService and album planning;
MediaStoreLibraryScanner/LibraryScanRunner; ProviderExtractorsFactory/ChannelMapper. New
symbols have only their task implementation and regression-test consumers.

Contracts touched: positional cached reads (short reads remain allowed by RandomAccessSource);
additive JSON `decoder_set_version`, `last_failure`, `last_failed_at`; the pending-row DAO's
new decoder-version argument and SQL predicate. Room entities/schema/version/migrations,
ANALYZER_VERSION, preferences, registry/provider order, PlaybackController API/StateFlows,
DSP/codec numeric output and spec 001's shipped playback paths remain unchanged.

Files required (all Kotlin paths under `androidApp/src/.../kotlin/com/amwolfstein/sistrum/`):

| Paths | Required because |
|---|---|
| main/player/decoders/kotlin/ExtractorRandomAccessSource.kt; test counterpart | Non-interruptible owned cache reads/writes; startup stale sweep; constructor cleanup and release logging. Tests retain positions/bounds/unknown lengths/caller ownership and cover interrupted writes/reads, stale files, constructor failure and denied deletion. |
| main/analysis/OfflineDecoder.kt; test/analysis/OfflineDecoderKotlinFormatsTest.kt | Keep a typed failed outcome when its log sink throws, while propagating cancellation. `decodeFile` becomes overridable only to drive `decode()` in the host test. Replace the DSD reflection/count assertion with DSF/DFF sniff results; nonzero first input requests zero, then interrupted load + seek reproduces the pinned float-AIFF PCM hash and exact frame count. |
| test/player/decoders/kotlin/KotlinDecodingExtractorLifecycleTest.kt | Add unknown inexact duration coverage without editing the tests-first T002 file or production extractor. |
| main/analysis/LoudnessModels.kt; main/analysis/LoudnessStore.kt; main/sync/SyncDatabase.kt; test/analysis/LoudnessDecoderVersionTest.kt | D-28 / drift D-01 and D-06: failed-only decoder-version retries and preservation of same-source successful measurements. Extract the existing document builder as a pure host-test boundary; prior measurement JSON is independently authored. Execute the actual DAO SQL against host Python SQLite and verify the actual activeAnalyses read path through a DAO snapshot. |
| main/player/media3/Media3PlayerFactory.kt; test/player/media3/RouteChannelLimitTest.kt | Pure route limit and logged AudioManager failure fallback; all five specified route cases. |
| main/sync/ScanGate.kt; test/sync/ScanGateTest.kt | Each existing fallback logs cause and provider id (or registry/unknown + FormatKey before resolution); preserve ordinary admission/refusal behaviour. Extra test-file reason: enforce cancellation propagation at resolve, skipReason, header and refusal boundaries. |
| androidTest/player/KotlinFormatsPlaybackTest.kt | Expand and rename the existing join test to ten staged fixtures: original WavPack/APE/ALAC/Musepack plus WMA Lossless, PCM/sowt/float AIFF and DSF/DFF; unchanged <=2-frame assertions. DSD uses D-18's approved production converter comparison, not Flick oracle parity. No fixture inventory or hashes changed. |
| androidTest/analysis/LoudnessStoreTest.kt | Extra file required because its exact measured-document key assertion must include the task's new decoder_set_version (D-28); add real transaction, histogram-retention and missing decoder-version retry tests. All prior assertions remain; the only existing expected-value change is that required additive key. |
| tasks.md; HANDOFF.md; DECISIONS.md | Task completion/status, verification and scope report, D-28. |
| graphify-out/graph.json; graphify-out/.graphify_analysis.json | Generated by the required automatic graph refresh; never edited manually. |

Self-review: original T002 KotlinDecodingExtractorTest remains byte-for-byte unchanged; no
test removed, ignored or weakened. The join method rename is explicitly requested by the task.
All new fallbacks log their cause/path and preserve cancellation. Cache buffers stay bounded
and reusable, with direct caller-owned FileChannel behaviour preserved. No follow-up refactor
or other milestone work was included.

## T010 verification and impact review

- Initial state: `feature/media3-migration`, clean working tree, no `.codex-commit/`.
  Read Current status and T010 first; scoped research/spec review to R4 and FR-006.
- Impact before edits (`git grep`): every Kotlin stream provider constructs `CodecStream.Info`;
  provider factories consume `KotlinDecodingExtractor`; playback uses `ProviderExtractorsFactory`.
  DSD rate conversion copies Info, preserving the new mask. Existing adapter/provider/registry/factory
  tests must remain unchanged and did. Room, analysis documents, preferences, provider ordering,
  PlaybackController, audio limiter and imported module APIs/output remain unchanged.
- Required files and reasons (reviewed for smallest diff):
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ChannelMapper.kt`:
    source-mask validation, imported placement/mix coefficients, PCM word placement and float mixing.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractor.kt`:
    mapped Format/PCM packing and typed unsupported attribution.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/CodecStream.kt`:
    optional WAVE speaker mask at the adapter boundary; old overloads retained.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDsdProvider.kt`:
    DSF/DFF Android-to-WAVE mask conversion and WavPack DSD mask propagation.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinWavPackProvider.kt`:
    propagate declared WavPack PCM/float mask.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinWmaProvider.kt`:
    propagate selected WMA Pro/lossless masks; codec implementation untouched.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/ProviderExtractorsFactory.kt`:
    pass playback output limit through provider wrappers in both factory entry points.
  - `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/media3/Media3PlayerFactory.kt`:
    query media route channel counts at stream open on API 33+, explicitly log absent/pre-33 reports.
  - `androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ChannelMapperTest.kt`:
    eight new regression tests; canonical DSF PCM checks DFF order at DSD64/128/256,
    analytic mix-1 coefficients independently check every stereo corpus sample exactly.
  - `specs/002-kotlin-decoders/tasks.md`, `HANDOFF.md`, `DECISIONS.md`: task completion,
    evidence, per-file scope reasons and D-23. Generated `graphify-out/graph.json` and
    `graphify-out/.graphify_analysis.json`: required graph refresh artifacts, not manually edited.
- Before both gates: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `rg MemAvailable /proc/meminfo`: 3,496,380 / 3,577,916 kB, both above 2 GB.
- Gate command: `bash .claude/skills/verify-task/scripts/verify.sh --files '<production/test/docs paths above>'
  --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.player.decoders.*'
  --tests '*ProviderExtractorsFactoryTest'`.
  Attempt 1 `/tmp/verify-task.DpGovp`: 103 tests, two failures in new tests only,
  zero errors/skips, production compilation PASS. The placement fixture incorrectly used
  a complete 4ch envelope; corrected to a 5ch layout missing centre. The analytic reference
  rounded coefficients prematurely; now rounds once from double precision and requires
  exact float equality (stronger assertion, no existing expected values changed).
  Attempt 2 `/tmp/verify-task.gQmtXZ`: **GATE: PASS**, 104 tests, zero failures/errors/skips,
  `:androidApp:compileDevQaKotlin` PASS; graph changed (11,096 nodes, 32,857 edges).
- Final `python3 .claude/hooks/graph_refresh.py auto`: graph changed (11,096 nodes,
  32,857 edges); both generated files included. `git diff --check`: PASS.
- Unrelated changes appeared during the run in `CLAUDE.md`,
  `.claude/skills/delegate-task/SKILL.md`, `.claude/skills/delegate-task/scripts/dispatch.sh`
  and `.claude/skills/delegate-task/scripts/milestone-run.sh`; left untouched and excluded
  from the task commit bundle.
- Self-review PASS: no existing tests weakened/deleted/skipped; no features disabled;
  unsupported layout errors preserve typed causes and existing provider attribution;
  unavailable route reports are logged. Placement preserves original PCM words and gaps
  are zero-filled; mixed peaks are left to the existing limiter. Tests cover integer
  depths, raw/native floats, odd channel limits, 192 kHz seeks, EOS and storage reuse.
- NOT RUN: full app/sharedLogic/module suites, lint, assemble and phone checks (T013);
  benchmarks (M6); listening/Bluetooth/headset checks (owner manual list).
  No module source/API changes, so module downstream gates are not triggered.
  Git staging/commit/push NOT RUN (AGENTS.md step 5: runner consumes `.codex-commit/`).
- Follow-ups outside T010: pre-33 route detection, hot-route reconfiguration and intermediate
  multichannel downmix matrices, if separately assigned. D-23 documents these practical limits.

## T009 verification (superseded attempt history; finished in 81688a3)

- Branch verified as `feature/media3-migration`; initial working tree clean and no pending
  `.codex-commit/` directory. No Git metadata commands were run.
- Memory check before each attempt: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,804,540 / 3,795,516 / 3,766,156 kB, all above 2 GB.
- Required quick gate, all three attempts:
  `bash .claude/skills/verify-task/scripts/verify.sh --files '<T009 paths listed below plus task/graph documents>' --quick ':androidApp:testDevQaUnitTest' --tests '*Kotlin*ProviderTest' --tests '*KotlinDecoderAdapterTestSuite' --tests '*DecoderRegistryTest' --tests '*ProviderExtractorsFactoryTest'`.
  Attempt logs: `/tmp/verify-task.nDFoY9`, `/tmp/verify-task.mzU14t`, `/tmp/verify-task.c0ZOvE`.
  Attempt 1: pool arrays violated Media3's nonnull Kotlin signature; fixed the empty-array casts
  (SimpleDecoder fills them during construction). Attempt 2: test used the old DummyTrackOutput
  name; changed to Media3 1.11.1's DiscardingTrackOutput. Production compile PASS on attempt 2.
  Packet-dump golden inputs with unknown total count now use the indexed durations for the exact
  expected sample-count assertion, following the imported AlacPackets contract.
- Final attempt 3: `GATE: FAIL`; 79 tests, 1 failure, 0 errors/skips; production
  `:androidApp:compileDevQaKotlin` PASS. All four other new ALAC tests PASS: eight pinned
  packet dumps match exact native 16/24/32-bit oracle PCM, repeated output storage is reused,
  packet timestamps/EOS and forward/backward flush replay match, platform priority is retained,
  malformed packets and mono/stereo-only configuration refusals preserve typed causes/messages.
  The unchanged adapter suite and prior provider/registry/extractor-factory regressions PASS.
- Attempt 3's MP4 test fails at default extractor creation, before sniff/demux assertions.
  Exact cause: `NullPointerException: Cannot invoke "android.net.Uri.getLastPathSegment()"
  because "uri" is null` in `FileTypes.inferFileTypeFromUri`, called from Media3's
  DefaultExtractorsFactory and ProviderExtractorsFactory. XML: 5 ALAC tests, 1 failure.
  The default no-URI factory calls its URI overload with `Uri.EMPTY`; the plain host uses Android
  stubs. A subsequent session should inject an ExtractorsFactory returning a real Mp4Extractor,
  retaining the full original-source hash, canonical cookie, packet/timestamp, native PCM hash,
  seekability, platform priority and provider-attribution assertions. Do not skip the test.
- Production changes (under `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinAlacProvider.kt` (new packet SimpleDecoder/renderer/provider),
  `player/decoders/DecoderRegistry.kt` (platform-first ALAC registration),
  `player/decoders/DecoderProvider.kt` and `player/media3/Media3PlayerFactory.kt`
  (additive player-owned sink/event hook), `player/media3/ProviderExtractorsFactory.kt`
  (ALAC renderer attribution after Media3 MP4 demux). These supporting paths are necessary
  to make the named provider usable and keep normalization/DSP/error reporting intact.
- Tests (under `androidApp/src/test/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinAlacProviderTest.kt` (new), `player/decoders/DecoderRegistryTest.kt`,
  `player/decoders/kotlin/KotlinApeMusepackProviderTest.kt`, `KotlinWavPackProviderTest.kt`,
  `KotlinWmaProviderTest.kt`. The three exact provider-list assertions gain kotlin-alac;
  the registry's old ALAC skip test now asserts the fallback required by T009/US1 acceptance 3.
  No tests deleted, ignored, skipped or weakened; no oracle or T002 tests-first changes.
- Documents: HANDOFF.md and DECISIONS.md (D-20). tasks.md remains unchanged/T009 unchecked.
  Gate-generated artifacts: graphify-out/graph.json and graphify-out/.graphify_analysis.json.
- Migration review: PlaybackController, Room, native code, daily apps, ADRs and architecture
  untouched; existing features retained. No other milestone task implemented.
- Graph refresh ran successfully in all gates (final: 11,026 nodes / 32,721 edges),
  followed by the explicit autonomous graph refresh after this handoff update.
  `git diff --check` PASS. Both generated graph artifacts remain in the uncommitted diff.
- NOT RUN: full app/sharedLogic/module suites, lint, assemble and phone checks (T013), phone
  speed/seek-latency/CPU (T013/M6), real listening/Bluetooth/headset (owner manual checks),
  Git staging/commit/push and commit preparation (gate failed; three-attempt limit).

## T008 verification

- Memory checks before all three attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,883,604 / 3,876,456 / 3,816,232 kB (all ≥ 2 GB).
- Final quick gate (attempt 3, logs `/tmp/verify-task.OnGTyU`):
  `bash .claude/skills/verify-task/scripts/verify.sh --files '<nine Kotlin files and five task/graph files below>' --quick ':androidApp:testDevQaUnitTest' --tests '*KotlinPcmVariantsProviderTest' --tests '*KotlinDecoderAdapterTestSuite' --tests '*DecoderRegistryTest' --tests '*ProviderExtractorsFactoryTest' --tests '*AiffExtractorTest'`.
  GATE: PASS; 67 tests, zero failures/errors/skips; `:androidApp:compileDevQaKotlin` PASS.
  Includes eight new provider tests, all 21 unchanged adapter/source/lifecycle contracts, 17 registry,
  17 unchanged legacy AIFF and four extractor-factory checks. Pinned corpus prerequisites passed.
- Broader attempt 2 (`/tmp/verify-task.O8mk8J`) also GATE: PASS: 87 tests, zero
  failures/errors/skips, compilation PASS. Filters were `*Kotlin*ProviderTest`, `*DecoderRegistryTest`,
  `*ProviderExtractorsFactoryTest`, `*AiffExtractorTest`, `*KotlinDecodingExtractorTest` and
  `*ExtractorRandomAccessSourceTest`; these include the existing WavPack/APE/Musepack/WMA/DSD
  provider corpus regressions. The final run adds the complete suite and verifies AIFF-only scoping.
- Original PCM-variant corpus: two MS-ADPCM WAV, six G.711 WAV (mono/7ch/8ch), two ima4 AIFF-C
  and four alaw/ulaw AIFF-C inputs. All source hashes and exact native 16-bit PCM oracle hashes match.
  Checks assert rate/channels, exact frame count/duration, zero encoder delay/padding, increasing
  timestamps, reusable packing storage and forward/backward/after-EOF seek tails for every input.
- Dispatch: real WavExtractor sniffing keeps PCM/float/WAV IMA on the platform. WAV G.711 uses
  the platform only with the matching codec capability; MS-ADPCM always reaches Kotlin. Large
  leading metadata beyond the 4 KiB refusal probe still routes correctly and resets peek position.
  The existing aiff/aiff registry provider also offers an AIFF-C-only compressed extractor first;
  it lifts only ima4/alaw/ulaw refusals. MACE, unknown compression and float refusals are unchanged.
- Failures: unsupported MS channel count retains a named typed unsupported cause and the original
  parser cause; damaged MS predictor data retains a malformed cause, sniff provider recording and
  the same message/provider ID in the decode-failure log.
- Changed production paths (under `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinPcmVariantsProvider.kt` (new), `KotlinDecodingExtractor.kt`;
  `player/decoders/DecoderRegistry.kt`, `player/decoders/aiff/KotlinAiffProvider.kt`,
  `player/media3/ProviderExtractorsFactory.kt`.
  Changed tests (under `androidApp/src/test/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinPcmVariantsProviderTest.kt` (new), `KotlinApeMusepackProviderTest.kt`,
  `KotlinWavPackProviderTest.kt`, `KotlinWmaProviderTest.kt`.
  Required documents: this HANDOFF, tasks.md and DECISIONS.md (D-19).
  Generated artifacts: `graphify-out/graph.json`, `graphify-out/.graphify_analysis.json`.
- Files outside the named provider/test and aiff paths: registry changes register T008 formats;
  extractor-factory routing prevents Media3 from claiming unsupported WAV paths; the shared
  decoding adapter classifies the new typed unsupported cause. Three existing provider-list
  assertions gain exactly the provider required by T008/FR-001. Task/decision/graph files follow
  autonomous mode. No imported codec-module API changes.
- Migration-guard self-review: no tests deleted, renamed away, ignored, skipped or weakened;
  unchanged T002 contracts, codec tests and goldens. PlaybackController, Room, native code and
  daily apps untouched. No feature, ADR, architecture or unrelated shipped 001 behaviour changed.
- Graph refresh via the quick gate and explicit `python3 .claude/hooks/graph_refresh.py auto`
  after documentation updates: PASS, `graph: changed (10925 nodes, 32484 edges)`.
  Both generated artifacts included. Final `git diff --check -- <task files>` PASS.
- NOT RUN: full app/sharedLogic/module suites, lint, APK assembly and device checks (T013);
  phone speed/seek-latency/CPU checks (T013/M6); real listening/Bluetooth/headset checks (owner manual
  list); Git staging/commit/push (runner, AGENTS.md step 5). No phone commands run.
- Concurrent unrelated changes appeared after the initial clean status in
  `.claude/skills/delegate-task/scripts/milestone-run.sh` and
  `specs/001-media3-migration/adr/ADR-002-decoder-registry.md`. Neither was edited by this task;
  both are left untouched and excluded from the T008 commit manifest.

## T007 verification

- Memory checks before all three attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,831,588 / 3,905,048 / 3,977,312 kB (all ≥ 2 GB).
- Final quick gate (attempt 3, logs `/tmp/verify-task.DaigIA`):
  `bash .claude/skills/verify-task/scripts/verify.sh --files '<ten Kotlin files below>' --quick ':codecs:test :androidApp:testDevQaUnitTest' --tests '*Dsd*' --tests 'com.amwolfstein.sistrum.player.decoders.*'`.
  GATE: PASS; app 79 tests, zero failures/errors/skips; `:androidApp:compileDevQaKotlin` PASS.
  `:codecs:test` UP-TO-DATE, with 1,477 cached tests and zero failures/errors/skips. Its imported
  DSD Rust-oracle, production signal/specification and WavPack raw/decimator tests remain unchanged.
  App checks comprise six new DSD tests plus all 73 previous decoder/provider/registry checks,
  including the eleven unchanged T002 tests. Pinned corpus prerequisites passed.
- PCM coverage: all 20 original DSF/DFF source hashes checked; 19 production-playable files compared
  byte-for-byte to the production converter, including the three 6ch DFF files now admitted by Phase A.
  Format/rate/channels, exact sample counts/durations, padding/delay, reusable packing storage and
  forward/backward/end seek tails verified. DST preserves its original message, unsupported typed cause,
  sniff provider recording and failure-log provider ID. The original Rust manifest describes the retained
  test-only Flick converter, as documented by the production section of docs/dsd/ORACLE.md (D-18).
- WavPack DSD: 14 standalone rows checked against committed source hashes. Thirteen playable rows now
  emit float PCM matching the existing converter; all seek tails match uninterrupted PCM. The corrupt
  row retains its typed malformed/CRC failure. Existing WavPack oracle checks now validate DSD PCM
  format/count/hash while preserving all PCM/float, old-version and corrupt-input assertions.
- Rate conversion: explicit 352800 and 705600 Hz DSD256 targets emit 176400 Hz and match direct
  :dsp Profile.HQ output exactly, including delay compensation, drain length and seek tails. Default
  DSD64 stays 88200 Hz; DSD128/256 stay 176400 Hz without a second conversion. HQ conversion restores
  bounded FIR history after codec pre-roll and reuses all streaming buffers.
- Changed production paths (under `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinDsdProvider.kt` (new), `DsdRateConverter.kt` (new),
  `KotlinWavPackProvider.kt`, `KotlinDecodingExtractor.kt`; `player/decoders/DecoderRegistry.kt`.
  Changed test paths (under `androidApp/src/test/kotlin/com/amwolfstein/sistrum/`):
  `player/decoders/kotlin/KotlinDsdProviderTest.kt` (new), `KotlinWavPackProviderTest.kt`,
  `KotlinApeMusepackProviderTest.kt`, `KotlinWmaProviderTest.kt`; `player/decoders/DecoderRegistryTest.kt`.
  Required documents: this HANDOFF, tasks.md and DECISIONS.md (D-18).
  Generated artifacts: `graphify-out/graph.json`, `graphify-out/.graphify_analysis.json`.
- Files outside the two named task paths: the rate-converter helper implements T007's HQ/pre-roll
  requirement; WavPack enables the assigned DSD output path; the registry registers DSF/DFF; the
  extractor classifies named DSD unsupported failures. Existing registration assertions change only
  for the provider T007/FR-001 explicitly adds. Task/decision/graph files follow autonomous mode.
- Migration-guard self-review: no tests deleted, renamed away, ignored, skipped or weakened; T002 and
  imported codec tests/goldens untouched. Temporary WavPack DSD-refusal expectations are replaced with
  PCM assertions because T007 explicitly supplies conversion; intentional corruption remains a failure.
  PlaybackController, Room, native code and daily apps untouched; no feature, ADR, architecture or
  unrelated 001 behaviour changed. `git diff --check` PASS.
- Graph refresh: the quick gate runs `python3 .claude/hooks/graph_refresh.py auto`; final result
  `graph: changed (10834 nodes, 32284 edges)`, both generated artifacts included.
- NOT RUN: full app/sharedLogic/dsp/metadata/analysis suites, lint, assemble and device tests (T013);
  DSD phone speed/seek-latency/CPU checks (T013/M6); real listening/Bluetooth/headset checks (owner
  manual list); Git staging/commit/push (runner, AGENTS.md step 5). Codec suite was cached, not re-executed.

## T006 verification

- Memory checks before all three attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,848,760 / 3,898,716 / 3,867,096 kB (all ≥ 2 GB).
- Quick gate (attempt 3, logs `/tmp/verify-task.5rmcu4`):
  `bash .claude/skills/verify-task/scripts/verify.sh --files '<eight Kotlin files below>' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.player.decoders.*'`.
  GATE: PASS; 73 tests, zero failures/errors/skips; `:androidApp:compileDevQaKotlin` PASS.
  Executed: 11 unchanged T002, 10 T003 source/lifecycle, 6 WavPack, 6 APE/Musepack,
  6 WMA, 17 registry, 17 existing AIFF tests. Pinned corpus prerequisites passed.
- The eight Kotlin files in the gate: `player/decoders/DecoderRegistry.kt`;
  `player/decoders/kotlin/{CodecStream,KotlinDecodingExtractor,KotlinWmaProvider}.kt`;
  `test/player/decoders/DecoderRegistryTest.kt`;
  `test/player/decoders/kotlin/{KotlinWavPackProviderTest,KotlinApeMusepackProviderTest,KotlinWmaProviderTest}.kt`.
  Production paths are under `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`;
  test paths under `androidApp/src/test/kotlin/com/amwolfstein/sistrum/` (the `test/` prefix above denotes that root).
- WMA corpus: 30 WMA 1/2, 6 Lossless, 8 Pro, 8 Voice playable inputs; 15 refusals (7/3/1/4).
  All input hashes and output oracle hashes checked unchanged; exact byte counts, rate, channels,
  PCM depth/encoding, zero additional delay/padding, increasing timestamps and reused packing arrays.
  Each playable fixture compares whole seek tails at halfway, sample 1, last sample, start and EOF.
  One fixture per family additionally declares a 1ms or unknown duration: full PCM and seek tails
  beyond the estimate remain identical. Refusals use the provider's actual bounded header probe and
  normal parser path, preserving unsupported typed ASF causes, codec messages and provider ID in the log.
- Attempt 1 (`/tmp/verify-task.ZaRdN3`): 72 tests, two new WMA failures from growing packing arrays.
  Fixed production capacity reservation, retaining both assertions.
  Attempt 2 (`/tmp/verify-task.wRjo75`): 73 tests, one new WMA encoding assertion failure:
  the oracle script calls multichannel Pro's extensible WAVE outer tag "int"; imported WmaProOracleTest
  hashes the same bytes as raw float bits. Corrected this new assertion per FR-002 (D-17), retaining
  exact hashes and adding exact supported-depth checks; no fixture/reference changed.
- Self-review: no tests deleted, disabled or weakened; original T002 untouched. Existing provider-list
  and WMA unsupported expectations changed only for T006's mandated registration (FR-001).
  CodecStream/adapter edits preserve its four-argument JVM constructor and exact-count default;
  they are required for ASF's explicitly advisory sample count and variable superframe size.
  PlaybackController, Room, native code, imported modules and shipped features untouched.
  `git diff --check` PASS. No architecture or ADR changed.
- Graph: quick gate ran `python3 .claude/hooks/graph_refresh.py auto` and reported changed
  (10,768 nodes, 32,103 edges); both generated artifacts included, none hand-edited.
- Other changed files: this HANDOFF, T006 checkbox in tasks.md and D-17 in DECISIONS.md,
  required by autonomous logging/decision steps; `.codex-commit/` contains the runner's prepared commit.
- NOT RUN: full app/module/sharedLogic suites, lint, assemble and phone checks (T013);
  WMA speed/device memory/seek-latency measurements (T013/M6); stage/commit/push (runner per step 5).
  Real listening, Bluetooth/headset and device crossfade remain owner manual checks, not PASS.

## T005 verification

- Memory checks before both attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,693,688 kB and 3,689,856 kB respectively (both ≥ 2 GB).
- Quick gate (attempt 2, logs `/tmp/verify-task.ftIGJE`):
  ```sh
  bash .claude/skills/verify-task/scripts/verify.sh \
    --files 'androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/DecoderRegistry.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/CodecStream.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractor.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinApeProvider.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinMusepackProvider.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinApeMusepackProviderTest.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinWavPackProviderTest.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/DecoderRegistryTest.kt' \
    --quick ':androidApp:testDevQaUnitTest' \
    --tests 'com.amwolfstein.sistrum.player.decoders.kotlin.*' \
    --tests 'com.amwolfstein.sistrum.player.decoders.DecoderRegistryTest'
  ```
  `GATE: PASS`: scope, 50 tests (0 failures/errors/skips), `:androidApp:compileDevQaKotlin`, graph refresh.
  Oracle prerequisites verified the pinned external corpus and owned vectors. Attempt 1 logs
  `/tmp/verify-task.0wj5f9`: FAIL (zero refusal-header size, last-sample end seek and unexpected float
  method on T002's reflection proxy); production fixes preserve all original T002 source/assertions.
- New tests traverse all 31 APE inputs (8/16/24-bit) and 32 Musepack SV7/SV8 inputs (IEEE-754 float).
  Assert exact committed hashes, bytes/frames, PCM format/rate/channels, duration/seekability,
  zero delay/padding after codec trimming, increasing timestamps and reused PCM packing storage.
  Each fixture's oracle-verified PCM is compared byte-for-byte after seeks to halfway, sample 1,
  final sample, start and end, using the same extractor after EOF. Typed APE version-4000 and Musepack
  SV4 refusals preserve unsupported classification, codec reason and provider ID in the failure log.
- Self-review: no tests deleted/disabled/weakened; original T002 unchanged. Existing APE unsupported
  and exact provider-list expectations updated only for T005's explicitly specified registration (FR-001).
  PlaybackController, Room, native code and shipped features untouched. CodecStream/adapter changes
  are needed to consume Musepack's native reusable FloatBuffer without editing imported module APIs.
  `javap -c -p` confirms no Integer/Long/Float boxing in the adapter; allocations in the new packing
  path occur only for validation failures. `git diff --check` PASS.
- Graph command: `python3 .claude/hooks/graph_refresh.py auto`; generated graph artifacts are included
  because the quick gate refreshed the graph (10,711 nodes, 31,956 edges); no graph file edited by hand.
- NOT RUN: full unit/module/sharedLogic suites, lint, assemble and phone checks (T013 scope);
  APE/Musepack speed benchmarking (no T005 speed requirement; retained WavPack speed test passes);
  commit/stage/push (AGENTS.md step 5 assigns these to the runner).

## T004 verification

- Memory checks before both attempts: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
  `MemAvailable` 3,625,660 kB and 3,664,708 kB, respectively (both ≥ 2 GB).
- Quick gate (attempt 2, logs `/tmp/verify-task.rxLC2d`):
  ```sh
  bash .claude/skills/verify-task/scripts/verify.sh \
    --files 'androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinWavPackProvider.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractor.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/DecoderRegistry.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinWavPackProviderTest.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/DecoderRegistryTest.kt' \
    --quick ':androidApp:testDevQaUnitTest' \
    --tests '*KotlinWavPackProviderTest' --tests '*DecoderRegistryTest' --tests '*KotlinDecoderAdapterTestSuite'
  ```
  `GATE: PASS`: scope, 44 unit tests, `:androidApp:compileDevQaKotlin` and graph refresh passed.
  Oracle prerequisites verified the external corpus, ALAC dumps, DSD and owned vectors against their manifests.
  Attempt 1 logs `/tmp/verify-task.uqJbGf`: FAIL on constructor compatibility, duplicate refusal prefix and
  valid-bit/storage-width distinction; fixes retain the original T002 constructor and all T002 source/assertions.
- Provider tests traverse all 67 original .wv/.wvc inputs, reusing the pinned libwavpack oracle for formats
  that superseded WaxFlow's old subset refusals; all 96 standalone libwavpack rows are checked separately.
  Exact byte hashes, byte/frame counts, PCM encoding/rate/channels, duration, seekability and zero delay/padding
  pass. Fourteen DSD rows have typed conversion refusals until T007; unsupported legacy versions have exact
  named refusals; corrupt PCM and sidecar-only input retain codec errors. The existing sniff recorder and
  decode-failure log preserve the WavPack provider id and refusal text in a host test.
- Speed artifact: `androidApp/build/reports/wavpack-provider-speed.tsv` (generated, not committed).
  AMD Ryzen 5 3500U / OpenJDK 21.0.12.1; 72 unique successful standalone PCM/float files; five full-provider
  warmups and median of five measured decode loops per file. Input loading, sniff/open and hashing excluded;
  cached-source I/O, codec decode, PCM packing and output consumption included. RTF = decode/audio seconds:
  min 0.000485809, median 0.005968458, max 0.021165319. Slowest `generated/8ch-mask63f-b3-c.wv`, 47.247×.
  Every measured file passed the host faster-than-real-time check. Phone performance NOT RUN (T013/M6).
- Migration-guard self-review: no tests deleted, skipped, renamed, weakened or disabled; T002 untouched.
  The existing registry assertion that WavPack was unsupported now asserts its exact provider and null skip
  reason because T004/FR-001 explicitly adds support. No PlaybackController, Room, native code or daily app
  touched, and no feature, ADR, architecture or unrelated 001 behavior changed.
- Files outside the three named task files: `KotlinDecodingExtractor.kt` adds a configurable sniff length
  for the corpus's self-extracting streams while preserving its 4 KiB default and two-argument JVM API;
  `DecoderRegistryTest.kt` updates the obsolete unsupported-format assertion for the specified registration.
  Task/handoff/decision documents and generated graph artifacts are required by autonomous mode.
- NOT RUN: full app/sharedLogic/module suites, lint, assemble and device tests (T013 full/device gate);
  real listening/Bluetooth/headset checks (owner manual list); correction-paired rows (single-source provider,
  standalone hybrid fallback, D-15); Git staging/commit/push (runner, AGENTS.md step 5).

## T003 verification

- Changed: `player/decoders/kotlin/CodecStream.kt`, `ExtractorRandomAccessSource.kt`,
  `KotlinDecodingExtractor.kt`; new `ExtractorRandomAccessSourceTest.kt`,
  `KotlinDecodingExtractorLifecycleTest.kt` and `KotlinDecoderAdapterTestSuite.kt`; task status,
  this handoff, D-13/D-14 and the two generated graph artifacts. `androidApp/build.gradle.kts`
  changes only host-test discovery: the suite supplies strict Media3 parser setup and runs every
  member once. This supporting change is necessary because T002 cannot be edited and its plain-host
  `Build.FINGERPRINT` stub crashes Media3's automatic bounds-check detection (D-14).
- Contracts: magic sniff without opening/decoding or consuming input; 16/24/32-bit and float PCM,
  exact valid block lengths and timestamps, Long duration and sample seeks, rewind and repeated EOF;
  typed refusals preserve their original exception and unsupported parser classification. The T002
  steady-state test measures exactly zero bytes over 512 real reads after 20,000 warm-up reads,
  including its positive allocation-counter control. T002 source is unchanged from commit `f47e86b`.
- New checks: positional reads with gaps/backwards movement and destination bounds; unknown length;
  reopened Media3 input extending cached data; direct-buffer/file reads beyond 2 GiB with caller-owned
  channel position/lifetime preserved; temporary-cache deletion and I/O error identity; seeks before
  initial read and after EOF; idempotent release; thrown typed unsupported vs malformed open errors.
- Memory precheck before each gate: `./gradlew --stop` (no running daemons),
  `pkill -f '[K]otlinCompileDaemon'` (none running), `MemAvailable` in `/proc/meminfo` =
  3,620,480 / 3,605,656 / 3,611,744 kB for attempts 1/2/3, all above 2 GB.
- Final command:
  `bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/build.gradle.kts androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/CodecStream.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSource.kt androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractor.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/ExtractorRandomAccessSourceTest.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecoderAdapterTestSuite.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractorLifecycleTest.kt' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.player.decoders.kotlin.*'`:
  **GATE: PASS**; scope PASS; 21 tests, zero failures/errors/skips; compileDevQaKotlin PASS.
  Logs: `/tmp/verify-task.8r6520`.
- Attempt 1 (`/tmp/verify-task.7CFy77`): 17 tests, 7 failures; fixed the invalid refusal `initCause`
  usage and added suite setup for Media3's null host fingerprint. Attempt 2 (`/tmp/verify-task.in8xud`):
  GATE: PASS, 21 tests through the suite filter. Final attempt 3 verifies package-wide discovery after
  routing suite members away from duplicate standalone discovery. All test methods still execute;
  no tests are skipped or weakened.
- The final gate ran `python3 .claude/hooks/graph_refresh.py auto`:
  `graph: changed (10588 nodes, 31651 edges)`. Both generated artifacts are in the commit manifest;
  no manual graph edits. `git diff --check`: PASS.
- Migration-guard self-review: T002 and every existing test untouched; all 21 tests executed with
  strict parser bounds checks and meaningful assertions. No PlaybackController, Room, registry,
  feature, ADR, architecture or spec 001 shipped-behavior change. The adapter is unregistered until T004.
- Full module/app/sharedLogic suites, lint, APK assembly and device checks NOT RUN: T013 owns the
  full/device gate. Corpus/provider decoding and speed measurement NOT RUN: T004 supplies the first
  provider. No phone commands run. Commit/push NOT RUN: `.codex-commit/` is prepared for the runner.

## T002 verification

- Changed: only the new `androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractorTest.kt`
  plus task status, this handoff, D-12, and the two generated graph artifacts required by autonomous mode.
  Production code and existing tests are untouched.
- Contracts: magic sniff without opening/decoding, wrong magic/truncated prefixes, 16/24/32-bit and float raw PCM
  (including byte content), Long exact duration, sample-exact seek plus rewind, 131-frame decode with a short final
  block and repeated EOF, typed refusal through `DecoderProvider.refusal` and `ParserException` cause, and zero
  allocated bytes over 512 real decode blocks after warm-up (with a counter positive control).
- Memory precheck: `./gradlew --stop` (no running daemons), `pkill -f '[K]otlinCompileDaemon'` (none running),
  `MemAvailable` 3,623,908 kB, above 2 GB.
- `bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/src/test/kotlin/com/amwolfstein/sistrum/player/decoders/kotlin/KotlinDecodingExtractorTest.kt' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.player.decoders.kotlin.KotlinDecodingExtractorTest' --expect-fail 'com.amwolfstein.sistrum.player.decoders.kotlin.KotlinDecodingExtractorTest'`:
  `GATE: PASS`, attempt 1; scope PASS, 11 tests compiled and ran, all 11 failed with `java.lang.AssertionError`
  naming the missing T003 adapter, 0 errors/skips; `:androidApp:compileDevQaKotlin` PASS.
  Logs: `/tmp/verify-task.6TpdFy`. This is tests-first verification, not evidence that decoding or allocation
  behavior already passes; those assertions are not reached until T003 exists.
- Gate executed `python3 .claude/hooks/graph_refresh.py auto`: `graph: changed (10525 nodes, 31477 edges)`.
  Both generated graph artifacts are listed for the runner; no manual graph edits.
- `git diff --check`: PASS. Migration-guard self-review: no existing tests deleted, skipped, modified or weakened;
  meaningful output/seek/refusal/allocation assertions added; no PlaybackController, Room, feature or ADR changes.
- Full module/app/sharedLogic suites, lint, APK assembly and device checks NOT RUN: per-task quick gate only;
  M1's full/device gate is T013. No phone commands run. Commit/push NOT RUN: prepared files for the runner per AGENTS.md.

## T001 verification

- Memory precheck: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`; `MemAvailable` 3,585,920 kB
  initially, 3,668,320 kB before the final quick gate (both above 2 GB).
- `bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/build.gradle.kts androidApp/src/test/kotlin/com/amwolfstein/sistrum/ModuleWiringTest.kt' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.ModuleWiringTest'`:
  `GATE: PASS`; final logs `/tmp/verify-task.BRfxYd`. The gate runs `python3 .claude/hooks/graph_refresh.py auto`:
  graph changed (10,464 nodes, 31,313 edges); both generated graph artifacts await staging with T001.
- `env -u MOBILE_KEYSTORE_FILE -u MOBILE_KEYSTORE_PASSWORD -u MOBILE_KEY_ALIAS -u MOBILE_KEY_PASSWORD ./gradlew :androidApp:assembleProdRelease`:
  first attempt failed on duplicate `META-INF/THIRD-PARTY-NOTICES`; merge added per D-11, second attempt PASS.
  Final log `/tmp/sistrum-t001-prod-release-attempt2.log`. R8 ran; mapping file exists;
  `androidApp/build/outputs/apk/prod/release/androidApp-prod-release-unsigned.apk` produced (16,415,307 bytes).
  ZIP inspection confirms each module's complete notice content is retained.
- `git diff --check`: PASS. Migration-guard self-review: only new meaningful smoke assertions; existing tests
  untouched; no playback, Room, feature or ADR changes. No phone commands run.
- Explicit-path `git add` and `git commit --file /tmp/sistrum-t001-commit.txt`: failed with
  `Unable to create .../worktrees/Sistrum-UI/index.lock: Read-only file system`.
  `findmnt` confirms the worktree metadata mount is `ro`. Nothing staged; push NOT RUN (no commit).
  An unrelated concurrent edit to `AGENTS.md` was observed and left untouched.

## T011 blocked implementation and impact review

- Initial state: correct branch `feature/media3-migration`, no `.codex-commit/`; pre-existing edits in
  `CLAUDE.md` and `.claude/skills/delegate-task/{SKILL.md,scripts/dispatch.sh,scripts/milestone-run.sh}`
  were left untouched and must not be included in a future T011 bundle.
- Impact before edits (source searches, then checked with `git grep`): LoudnessAnalysisWorker constructs
  OfflineDecoder for analysis and foreground passes; device analysis/resume/normalization benchmarks also
  construct it. AnalyzerTrackMeasurer maps the existing DecodeOutcome contract; DecodeFailureLog is shared
  with playback and Settings. ALAC's provider has a sink-aware renderer overload, which offline was not using.
  No Room/schema, analysis-document, preference, provider-order, PlaybackController, or imported-module changes.
  Existing analysis/provider tests must remain unchanged and did.
- Required files: `analysis/OfflineDecoder.kt` wires sink-aware renderers, builds the default registry from
  the same provider instances, uses recorder/registry attribution and the deepest typed refusal, and records
  Failed outcomes in the existing failure sink with only the file name. Cancellation remains Cancelled; release
  cleanup errors now log provider/cause. Internal factory/renderer/outcome seams permit host verification.
  `test/analysis/OfflineDecoderKotlinFormatsTest.kt` covers six pinned fixture families, capture frame counts,
  DSD provider inclusion, injected registry consistency, ALAC sink wiring, failure fields and cancellation.
  HANDOFF records the blocker; graph files were generated by verify.sh, never manually edited.
- Minimal-diff/self-review: all production changes are in OfflineDecoder; no feature removed, no tests deleted,
  renamed, disabled, or weakened. No expected oracle value changed. Defaults preserve platform ordering and the
  current AIFF provider. Float AIFF implementation was not attempted because it belongs to T012a.
- Memory checks before all three gates: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'` (none running);
  MemAvailable respectively 3,564,812 / 3,632,732 / 3,702,704 kB (each above 2 GB).
- Gate command (attempt 3):
  `bash .claude/skills/verify-task/scripts/verify.sh --files 'androidApp/src/main/kotlin/com/amwolfstein/sistrum/analysis/OfflineDecoder.kt androidApp/src/test/kotlin/com/amwolfstein/sistrum/analysis/OfflineDecoderKotlinFormatsTest.kt' --quick ':androidApp:testDevQaUnitTest' --tests 'com.amwolfstein.sistrum.analysis.*' --tests 'com.amwolfstein.sistrum.player.decoders.kotlin.KotlinDsdProviderTest' --tests 'com.amwolfstein.sistrum.player.decoders.kotlin.KotlinAlacProviderTest'`.
  Attempt 1 ran only the analysis filter; attempts 2/3 also ran unchanged DSD/ALAC regression classes.
  `git diff --check` passed. No downstream module command required: no module code/API/output changed.
- Remaining: in a fresh T011 session, review the matrix's AIFF selection against the currently supported envelope
  (test accepted integer AIFF and separately assert the current float refusal, without changing T012a's scope).
  Supply SISTRUM_CORPUS for the existing loudness oracle. Rerun the gate before ticking T011 or preparing a bundle.


## T012 verification and impact review

- Scope: authored `KotlinFormatsPlaybackTest` only; T012a's all-AIFF production changes and
  added plain/float AIFF cases remain undone by design. No phone access occurred.
- Impact inventory gathered before edits with `git grep` / `rg`: consumers are the T013
  instrumented gate and the existing flat corpus push procedure. The new tests call
  Media3Engine/Media3PlayerFactory (prepare, seek, gapless preload, crossfade, events and
  player test seams), OfflineDecoder/PcmConsumer/CaptureAudioSink, and the existing
  FragmentedMp4Extractor/KotlinAlacDecoder/Dsd APIs. No consumer API is changed.
- Data/contracts: a new test-only fixture TSV retains original source and raw PCM hashes,
  frame/rate/channel counts, and independent float capture hashes. Room/schema/version,
  analysis documents, preferences, DecoderProvider order, PlaybackController and spec
  001 behavior are untouched. Original host/device tests and oracle fixtures remain
  unmodified; no imported module API/output change invokes downstream propagation.
- Files required, with final diff scope review:
  - `androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/player/KotlinFormatsPlaybackTest.kt`:
    seven device tests covering playback, offline hashes, full-depth fragmented ALAC,
    D-18 DSD integration, seek target/latency, two-frame gapless PCM alignment and
    two-player crossfade/non-silent PCM/zero underruns. QA-only guard; missing corpus fails.
  - `androidApp/src/androidTest/assets/kotlin-formats.tsv`: the 21 pinned rows the APK
    needs; expectations come from independent WaxFlow output, with explicit D-18 DSD rows.
  - `scripts/test-corpus/prepare-kotlin-formats.py`: required to stage those original
    corpus/cache inputs under unique flat names without deleting the existing corpus.
    It validates every source before writes, refuses repository output and never uses adb.
  - `scripts/test-corpus/push-corpus.sh`: one extension-list addition (`.mpc`); otherwise
    the existing required push procedure would silently omit the Musepack fixture.
  - `scripts/test-corpus/README.md`: reproducible setup, comparison semantics and the
    T013-only push/run procedure; no new phone workflow replaces the existing one.
  - `specs/002-kotlin-decoders/DECISIONS.md`: D-24 logs the comparison/staging choices
    within D-18/D-20, without an architecture or ADR change.
  - `specs/002-kotlin-decoders/tasks.md`: T012 ticked because test authoring is complete.
  - `specs/002-kotlin-decoders/HANDOFF.md`: status, task row and this verification record.
  - `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json`: generated by
    the required graph refresh, included because it reported `graph: changed`; no manual edit.
- Smallest-diff review: production untouched; existing corpus push changes one pattern;
  tests reuse existing engine/offline/decoder seams; no new dependency, helper framework,
  instrumentation runner, build configuration or audio committed. Removed a redundant
  assertion helper during review. Each additional file is required for the test artifact,
  setup, task lifecycle or graph lifecycle as described above.

Commands and results:

- `git status --short`, `git branch --show-current`: clean initial worktree, current branch
  `feature/media3-migration`; `.codex-commit/` absent at start. Read HANDOFF Current status
  and the T012 line first; then scoped spec/plan/D-18/D-20 and existing test/API reads.
- Memory gate: `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'` (no matching daemon);
  `/proc/meminfo` before the quick gate: MemAvailable 3,540,536 kB, above 2 GB.
- Independent fixture derivation in `/tmp/t012-fixtures.py`: existing pinned WaxFlow CLI
  `transcode --force --no-tags --format wav <source> <scratch.wav>` for all 19 non-DSD rows;
  all raw PCM hashes matched the unchanged original oracle manifest before converting
  to float capture hashes. Both DSD source hashes copied from the original DSD manifest;
  production integration remains explicitly separate from the old Flick PCM hash.
- `python3 scripts/test-corpus/prepare-kotlin-formats.py --out /tmp/sistrum-t012-corpus`:
  PASS, 21 sources staged and hashes checked, no device accessed. Additional scratch checks:
  existing staged file preserved, corrupted source rejected before output creation,
  repository output rejected. Scratch sentinel removed; staged real audio remains in `/tmp`.
- `bash -n scripts/test-corpus/push-corpus.sh` and
  `python3 -m py_compile scripts/test-corpus/prepare-kotlin-formats.py`: PASS.
- Quick gate (attempt 1):
  `bash .claude/skills/verify-task/scripts/verify.sh --files '<the ten task paths above>'
  --quick ':androidApp:testDevQaUnitTest'
  --tests 'com.amwolfstein.sistrum.player.decoders.*'
  --tests 'com.amwolfstein.sistrum.analysis.OfflineDecoderKotlinFormatsTest'`:
  **GATE: PASS**, 105 host tests across 13 classes, zero failures/errors/skips;
  `:androidApp:compileDevQaKotlin` PASS. Logs `/tmp/verify-task.fN5RNc`.
- `./gradlew :androidApp:compileDevQaAndroidTestKotlin`: PASS at each authoring checkpoint;
  final compile after all assertions: BUILD SUCCESSFUL in 26 s, 51 tasks (3 executed,
  48 up-to-date). Seven new device tests compile; none has been executed. Final log:
  `/tmp/t012-androidtest-compile-final.log`.
- `python3 .claude/hooks/graph_refresh.py auto` (invoked by the gate): PASS,
  `graph: changed (11214 nodes, 33175 edges)`; both generated artifacts included.
- `git diff --check`: PASS. Self-review: existing tests/assertions/expected values unchanged;
  meaningful new PCM/frame/provider/seek/transition/underrun assertions; no assumption skip,
  swallowed exception, silent fallback, disabled feature, Room or public playback API change.

NOT RUN and remaining work:

- Device test execution and `push-corpus.sh`: NOT RUN. AGENTS.md Autonomous step 8 reserves
  all device use for T013, the milestone phone gate; T012 explicitly authors tests not run
  yet. Stage into the combined corpus directory and push with the README procedure in T013.
  Include `com.amwolfstein.sistrum.player.KotlinFormatsPlaybackTest` in its instrumented
  class list and record per-class instr/logcat log directories. No instrumented PASS claim.
- Full app/sharedLogic/imported-module suites, lint, assemble and benchmarks: NOT RUN;
  no production/module edge changed; full gate/phone is T013, benchmarks M6.
- Listening/Bluetooth/headset checks: NOT RUN; owner manual checks, never automatic PASS.
- Independent production-DSD PCM oracle parity: NOT RUN/unavailable for this filter; D-18
  authorizes direct production integration comparison and retains independent analytic
  validation. Do not substitute the old Flick hash or change production behavior.
- Stage/commit/push: NOT RUN by Codex. `.codex-commit/files.txt` and `message.txt` are prepared
  for the runner to commit on the current feature branch. No Git metadata mutation performed.

### T012a implementation and impact review (2026-10-14; not committed)

- Impact first: `git grep` traced KotlinAiffProvider through defaultDecoderProviders /
  DecoderRegistry into ProviderExtractorsFactory, RegistryScanGate and OfflineDecoder.
  AiffExtractor/aiffRefusal had only their provider and legacy tests as code consumers;
  KotlinPcmVariantsProvider also supplied T008's compressed-AIFF split and oracle tests.
  Aiff.open additionally serves Ima4 and the unchanged AiffOracleTest/AiffBenchmark.
  Contracts preserved: kotlin-aiff id, decoder-table row/order, 4096-byte header request,
  Room schema/version, analysis documents, preferences and PlaybackController API/StateFlows.
  Existing adapter/source/lifecycle, registry, codec-oracle and unrelated tests remain unmodified.
- Required production files: KotlinAiffProvider now adapts every imported Aiff compression;
  AiffExtractor is deleted as explicitly authorized. KotlinPcmVariantsProvider loses its
  AIFF branch/probe, leaving one AIFF decoder path. Extra files required for typed errors:
  `codecs/.../container/aiff/Aiff.kt` adds AiffException while preserving exact IOException
  messages and decoded output; KotlinDecodingExtractor classifies its unsupported flag.
  Metadata imports only RandomAccessSource, but its downstream regression was still attempted.
- Tests: AiffExtractorTest's 17 behaviours migrated to KotlinAiffProviderTest (22 tests),
  preserving endian/bias/width/duration/refusal checks, recording changed expectations in D-25,
  adding all 18 successful pinned AIFF oracle fixtures and exact mono/stereo seek tails/times,
  empty/odd-channel/high-rate boundaries, non-byte-aligned PCM and typed errors. No oracle
  hash changed. T008's 14 original oracle fixtures/counts/seek checks remain, routing AIFF
  through KotlinAiffProvider; only its authorized refusal expectations change (D-25).
  OfflineDecoderKotlinFormatsTest explicitly includes sine-f32.aiff; ScanGateTest admits fl32
  and checks MAC3's exact refusal plus the 4096-byte request with a valid sample-rate header.
- Device cases: KotlinFormatsPlaybackTest now requires six AIFF fixture ids within 24 total
  rows; its existing playback/oracle/seek loops exercise the new plain PCM, sowt and float
  cases. Extra `androidTest/assets/kotlin-formats.tsv` rows are required by that data-driven
  class; `scripts/test-corpus/README.md` updates its inventory. Capture hashes were obtained
  by independent source-byte unpacking verified against pinned raw oracle hashes (D-25),
  never from Kotlin output. THIRD-PARTY-NOTICES removes only the obsolete Choir entry.
- Diff/self-review: scope restricted to these implementation/test/fixture/notice files,
  DECISIONS.md and this handoff, plus generated graph files. No architecture/ADR edits,
  production feature removals, weakened assertions, skipped new cases, swallowed errors,
  fallback-to-default error paths or daily-app/device actions. The mandated legacy extractor
  and test-file deletion are the explicitly authorized migration, not lost test coverage.
  No additional refactors or follow-up implementation was attempted.
- Concurrent external changes: `.claude/agents/` files and `CLAUDE.md` changed after the clean
  starting status; Codex did not edit these and excludes them from T012a's scope. Graph output
  was generated by verify.sh, never hand-edited.
- Commands/results before the final focused gate:
  - `./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`; MemAvailable 3,482,596 kB
    before module tests, 3,493,056 kB before the broad gate (both >= 2 GB).
  - `./gradlew :codecs:test :metadata:test`: FAILED at `:metadata:verifyWaxLabelCorpus`
    before metadata test execution; log `/tmp/t012a-module-tests.log`.
    Missing/changed input first reported: `owned/dsd-tag-dsd-bad-id3.mp3`.
    Read-only verification found 97 missing/mismatched inputs in the default cache; the
    alternate `sistrum-waxlabel-oracle-full` cache also fails, first at
    `owned/matroska-ffmpeg-aac.mka` (113 mismatches). No fixture/manifest edits or skips.
  - `./gradlew :codecs:test`: PASS, 1,477 tests, zero failures/errors/skips;
    `/tmp/t012a-codecs-tests.log`. The original AiffOracleTest remains unchanged.
  - Quick gate with `:androidApp:testDevQaUnitTest`, package filters
    `com.amwolfstein.sistrum.player.decoders.*`, `com.amwolfstein.sistrum.analysis.*`,
    `com.amwolfstein.sistrum.sync.*`: GATE: PASS; 444 tests discovered, zero failures/errors,
    one pre-existing WaxFlowOracleLoudnessTest assumption skip (`SISTRUM_CORPUS` unset,
    corpus test NOT RUN, not PASS). compileDevQaKotlin PASS; logs `/tmp/verify-task.8KgKVn`.
    Graph refresh PASS: changed (11,197 nodes / 33,141 edges).
  - `python3 scripts/test-corpus/prepare-kotlin-formats.py --out /tmp/sistrum-t012a-corpus`:
    PASS, all 24 pinned source hashes checked and files staged outside the repo; no device access.
  - `./gradlew :androidApp:compileDevQaAndroidTestKotlin`: PASS;
    `/tmp/t012a-device-compile.log`.
  - `git diff --check`: PASS.
- Required metadata tests: NOT RUN because their integrity preflight failed; this prevents
  commit preparation. Full app/sharedLogic suites, lint, assemble, device playback/timing,
  listening/Bluetooth/headset checks: NOT RUN (T013/milestone/owner scope). Task checkbox,
  commit preparation, staging, commit and push are deliberately not performed while blocked.

- Final focused quick gate after test cleanup: `--quick ':androidApp:testDevQaUnitTest'`
  with `--tests '*KotlinAiffProviderTest' --tests '*OfflineDecoderKotlinFormatsTest'`
  `--tests '*ScanGateTest'`: GATE: PASS, 35 tests, zero failures/errors/skips;
  compileDevQaKotlin PASS; graph changed (11,197 nodes / 33,141 edges).
  Logs: `/tmp/verify-task.PmYN1k`. Memory preflight: 3,565,332 kB available.
  This rerun covers the final resource-release cleanup in the migrated test; the broader
  unchanged downstream packages retain the preceding 444-test evidence.
- Final status: BLOCKED on external metadata corpus integrity. `.codex-commit/` absent;
  `tasks.md` intentionally unchanged/unchecked. Restore exact pinned inputs and rerun
  `./gradlew :metadata:test` before commit preparation; never regenerate expected hashes
  to match a different cache. The current app quick gate passing does not resolve that
  required downstream regression failure.

## 003 backlog (from 002)

- First task of 003 (owner 2026-10-14, D-26), original 002 text:
  T013a Quality tooling (owner 2026-10-14; report-only, never blocking yet): add, with versions pinned in `gradle/libs.versions.toml` that support AGP 9.4.1 / Kotlin 2.4.20 (D-entry with versions and why): (1) **detekt** on `androidApp`, `sharedLogic`, `codecs`, `metadata`, `analysis`, `dsp` with the Compose rules plugin (`io.nlopez.compose.rules:detekt`) and a committed `config/detekt/detekt.yml` (default rules + complexity, potential-bugs, unused-private-* on; formatting/style rules off) and per-module baseline files so only new findings report; task `./gradlew detekt` must succeed; (2) **R8 usage report**: `prodRelease` writes `usage.txt` (add `-printusage` to `androidApp/proguard-rules.pro` if AGP doesn't already) — the list of code R8 removes; (3) **Android lint `UnusedResources`** (and `UnusedIds`) enabled as warning via `androidApp` `lint {}` (no new lint errors); (4) **Kover** coverage on the same modules with one aggregated XML+HTML report (`./gradlew koverXmlReport`), no thresholds. Document each command, output path and how to read it in `docs/QUALITY.md`; record the first counts (detekt baseline sizes, usage.txt line count, UnusedResources count, Kover line coverage per module) in HANDOFF. No production code changes; no rule fixes in this task (findings become later tasks). The orchestrator wires these into `verify.sh` afterwards (Codex never edits `.claude/`). Quick gate: `:androidApp:compileDevQaKotlin` + `./gradlew detekt` + one `assembleProdRelease` (unsigned) to prove R8 still builds
  Plus: an "after" run of the same reports at 003's end; Claude wires the reports into `verify.sh` full gate.

## M1 deferred and manual list (for T013 and the owner)

- Device (T013, `.qa` only): `KotlinFormatsPlaybackTest` (SC-001 every covered format plays; SC-002 joins ≤ 2 frames for
  every covered lossless format after T013-fix item 7; seek ≤ 300 ms per format incl. WMA), plus 001 regression classes
  `Media3EngineGaplessTest`, `Media3CrossfadeTest`, `Media3EngineCoreTest`, `NormalizationLevelDeviceTest`;
  `phone-log-triage` on the saved per-class logcat.
- Owner listening (deferred, never PASS by Claude): a 5.1 WavPack/WMA Pro file on phone speaker or stereo headphones
  (downmix level vs stereo files, about −3 dB front by D-23 design); a seek while a WMA/APE file plays (no error,
  no long stall); one of each new format in normal use after installing the release APK.
- Commits in the M1 range outside any task (owner requests): 9f85ba0 (D-22 languages), d036c81 (Arabic term).

