# HANDOFF — 002-kotlin-decoders

**Branch:** `feature/media3-migration` (no separate branch; owner plan 2026-10-14). Plan: `OWNER-PLAN-2026-10-14.md`.

## Current status

- **Phase:** M1 execution — T001–T011 committed (T011 finished by Claude after Codex BLOCKED); runner next: T012, T012a.
- **Phase A closed:** 46/46 imported; end gate: `testDevQaUnitTest` 1279/0 fail, sharedLogic 39/0,
  `lintDevQa` 109 errors, all MissingTranslation (known category); scratch worktree `port-ro` removed.
- **Next step:** T012,
  T012a (all AIFF through WaxFlow's decoder,
  owner-approved); then the milestone review (CLAUDE.md: silent-failure-hunter, pr-test-analyzer, kotlin-reviewer,
  spec-traceability-auditor, audio-correctness-reviewer, port-fidelity-checker on the imported decoders;
  confirmed findings → `T013-fix`; plus a one-time `codebase-archaeologist` drift audit across eras, findings to
  `specs/review/drift/REGISTRY.md`), then T013 (M1 full gate + phone check; phone-log-triage on its saved logcat), then T013a (quality tooling,
  report-only) after which Claude wires detekt/usage.txt/lint/Kover reports into `verify.sh`, then M2.
- **Early host speed signal (T004):** 72 PCM/float files, five warmups/five measured loops per file;
  RTF min/median/max 0.000485809 / 0.005968458 / 0.021165319 (slowest 47.247× real time).
  This is the provider extraction path on a laptop, not SC-005's CPH2307 performance result.
- **New decision:** D-23 records adapter speaker masks, placement and device-count downmix limits.
  Execution by Codex in autonomous mode (D-9).

## Task log (newest first)

| When | Item | Result |
|---|---|---|
| 2026-10-14 | T011 finished by Claude (owner rule 2026-10-14: no Astra fallback, Claude takes failed tasks) | Kept Codex's OfflineDecoder change (registry built from the injected providers, decode-failure log with provider + typed refusal, cancellation not logged) after diff review. Fixes: offline matrix picks integer AIFF (float AIFF joins in T012a, task line extended); `sync/ScanGateTest` had 3 stale expectations since T006–T009 (DSD/WMA/IMA4/ALAC now admitted) that the filtered quick gates never ran: updated with spec reason, skip/no-device intents kept via TTA and AC-3; `docs/REGRESSION.md` now sends decoder changes to `sync.*` + `analysis.*`. GATE: PASS, full `testDevQaUnitTest` 1354 tests 0 fail (1 corpus skip), sharedLogic PASS, `assembleDevQa` PASS, graph changed. migration-guard: PASS (non-blocking: one always-true assert in the corpus test, root-cause-only failure message). Lint/device NOT RUN: T013 scope. |
| 2026-10-14 | T011; commit: none (BLOCKED, retry limit) | GATE: FAIL on attempt 3; 164 tests discovered: 162 pass, 1 failure, 1 existing assumption skip; compileDevQaKotlin PASS. New offline matrix reaches `waxflow-testdata/sine-f32.aiff`, refused by the current AiffExtractor with `float AIFF-C (fl32)`; float AIFF support is T012a, outside this task. Attempt 1: new-test AudioSink configuration constructor mismatch. Attempt 2: host Uri.EMPTY stub caused a null pointer in Media3's default extractor factory; added an empty-base test seam (production default unchanged). Attempt 3 logs: `/tmp/verify-task.zwi8L9`; earlier logs: `/tmp/verify-task.mAsA5G`, `/tmp/verify-task.1WSE1O`. Existing WaxFlowOracleLoudnessTest was NOT RUN because SISTRUM_CORPUS is unset (assumption skip, not PASS). Graph refreshed by the gate: changed (11,129 nodes, 32,980 edges). Full suites/sharedLogic, lint, assemble, ExoPlayer scheduling/device checks NOT RUN: T013 scope. Commit preparation/staging/commit/push NOT RUN: gate failed. Existing tests untouched. |
| 2026-10-14 | T010; commit: prepared (runner pending) | GATE: PASS on attempt 2; 104 host tests, zero failures/errors/skips; compileDevQaKotlin PASS. Eight new ChannelMapperTest tests cover 3ch/5.1/reordered 6ch corpora, placement, analytic downmix gains, integer/float conversion, refusals, seeks/EOS/storage and wrapped-provider output limits. Existing tests unchanged. D-23 records API 33+ route counts, logged unknown/pre-33 limits, and stereo reductions for smaller intermediate layouts. Graph changed; both generated artifacts included. Files/impact/commands detailed below. Full suites, lint, assemble, phone, listening and benchmarks NOT RUN: T013/M6/owner scope. Commit/push NOT RUN: runner handles Git metadata. |
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
