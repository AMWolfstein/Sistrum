# HANDOFF — 002-kotlin-decoders

**Branch:** `feature/media3-migration` (no separate branch; owner plan 2026-10-14). Plan: `OWNER-PLAN-2026-10-14.md`.

## Current status

- **Phase:** M1 execution — T001 module wiring and T002 tests-first contracts are committed.
  T003 implements the generic Pattern A adapter: exact raw PCM, duration/seek, typed refusals,
  reusable output buffers, input-backed random access and resource release. All 11 unchanged T002
  contracts and 10 new source/lifecycle tests pass. Execution by Codex in autonomous mode (D-9).
- **Phase A closed:** 46/46 imported; end gate: `testDevQaUnitTest` 1279/0 fail, sharedLogic 39/0, `lintDevQa` 109
  errors, all MissingTranslation (known category); scratch worktree `port-ro` removed.
- **Blocked:** nothing. T003's commit is prepared in `.codex-commit/` for the runner; Git mutations
  and push are NOT RUN here per AGENTS.md step 5.
- **Next step:** runner commits/pushes T003 on `feature/media3-migration`, then T004 implements the
  WavPack provider and its corpus/oracle tests plus the early host speed signal. No provider is registered by T003.
- **New decisions since the last report:** D-13 (bounded-memory temporary-file input cache),
  D-14 (suite setup for the unchanged T002 tests with strict Media3 parser bounds checks).

## Task log (newest first)

| When | Item | Result |
|---|---|---|
| 2026-10-14 | T003; commit: prepared for runner (SHA pending) | GATE: PASS on final attempt 3; 21 tests (11 unchanged T002 + 10 new), 0 failures/errors/skips; compileDevQaKotlin PASS; graph changed (10,588 nodes, 31,651 edges), both artifacts included. D-13/D-14 record cache ownership and suite setup. Attempt 1 FAIL (refusal cause + Media3 host stub); attempt 2 PASS; attempt 3 verifies suite discovery after configuration. Full suites, lint, assemble, corpus/provider speed and device checks NOT RUN: T004 owns the provider/speed signal and T013 owns the full/device gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T002 [TESTS-FIRST]; commit: prepared for runner (SHA pending) | GATE: PASS on attempt 1; 11 expected assertion failures, 0 errors/skips; compileDevQaKotlin PASS; graph changed (10,525 nodes, 31,477 edges), both artifacts included. D-12 records the reflection bridge/API. PCM/seek/refusal/allocation assertions NOT RUN past the missing-adapter assertion: T003 supplies the implementation. Full suites, lint, assemble and device checks NOT RUN: T013 owns the full gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T001 resumed; commit: none (Git metadata still mounted read-only) | Reviewed existing T001 diff and prior verification record; explicit-path `git add` failed creating `index.lock` (read-only filesystem), confirmed by `findmnt`. Implementation unchanged; tests/build/graph refresh NOT RUN again because the prior checks passed and no implementation changed. Commit/push NOT RUN: staging blocked. T001 remains unchecked; unrelated `AGENTS.md` edit left untouched. |
| 2026-10-14 | T001; commit: none (staging blocked by read-only Git metadata) | GATE: PASS; 4 host smoke tests, 0 failures/errors/skips; compileDevQaKotlin PASS; unsigned assembleProdRelease + R8 PASS (4m 7s); graph refreshed. D-11: merge notices after initial release packaging collision; all four notices verified in APK. Push NOT RUN: no commit. Full module/app/sharedLogic suites, lintDevQa and device checks NOT RUN: per-task quick gate; full/device gate belongs to T013. |
| 2026-10-14 | Phase A end gate; 002 spec/plan/tasks | PASS (lint: 109 MissingTranslation only) |
| 2026-10-14 | Imports 2–46 (see `IMPORT-LOG.md`; conflicts at 9, 14, 29, 42, 46 resolved and checked; env fixes at 15, 19, 21, 24, 30, 36) | PASS |
| 2026-10-14 | Import 1/46 `01759c8` → `c4e7401`; README rebrand leftover fixed | PASS |
| 2026-10-14 | Corpora rebuilt (WaxFlow/DSD/WavPack verified; WaxLabel, analysis, dsp oracles built) | D-1, D-2 |
| 2026-10-14 | Graphify scope for the 002 modules | `693a54d`, graph unchanged (no module code yet) |

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
