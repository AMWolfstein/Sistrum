# HANDOFF — 002-kotlin-decoders

**Branch:** `feature/media3-migration` (no separate branch; owner plan 2026-10-14). Plan: `OWNER-PLAN-2026-10-14.md`.

## Current status

- **Phase:** M1 execution — T001 done: four JVM modules wired into
  the app, host smoke tests pass, unsigned prodRelease builds with R8. T002 tests-first done: 11 generic
  decoding-extractor contract tests compile and fail on the expected missing-adapter assertions.
  Execution by Codex in autonomous mode (D-9).
- **Phase A closed:** 46/46 imported; end gate: `testDevQaUnitTest` 1279/0 fail, sharedLogic 39/0, `lintDevQa` 109
  errors, all MissingTranslation (known category); scratch worktree `port-ro` removed.
- **Blocked:** nothing. (Codex's sandbox keeps Git metadata read-only; since T001 the runner commits what Codex
  prepares in `.codex-commit/` — AGENTS.md step 5. T001 was committed by Claude from Codex's verified work.)
- **Next step:** T003; implement the adapter API recorded in D-12 and make T002 pass without modifying its tests.
  T002's commit files are prepared in `.codex-commit/` for the runner.
- **New decisions since the last report:** D-12 (tests-first reflection bridge and local adapter API).

## Task log (newest first)

| When | Item | Result |
|---|---|---|
| 2026-10-14 | T002 [TESTS-FIRST]; commit: prepared for runner (SHA pending) | GATE: PASS on attempt 1; 11 expected assertion failures, 0 errors/skips; compileDevQaKotlin PASS; graph changed (10,525 nodes, 31,477 edges), both artifacts included. D-12 records the reflection bridge/API. PCM/seek/refusal/allocation assertions NOT RUN past the missing-adapter assertion: T003 supplies the implementation. Full suites, lint, assemble and device checks NOT RUN: T013 owns the full gate. Commit/push NOT RUN: runner handles Git metadata. |
| 2026-10-14 | T001 resumed; commit: none (Git metadata still mounted read-only) | Reviewed existing T001 diff and prior verification record; explicit-path `git add` failed creating `index.lock` (read-only filesystem), confirmed by `findmnt`. Implementation unchanged; tests/build/graph refresh NOT RUN again because the prior checks passed and no implementation changed. Commit/push NOT RUN: staging blocked. T001 remains unchecked; unrelated `AGENTS.md` edit left untouched. |
| 2026-10-14 | T001; commit: none (staging blocked by read-only Git metadata) | GATE: PASS; 4 host smoke tests, 0 failures/errors/skips; compileDevQaKotlin PASS; unsigned assembleProdRelease + R8 PASS (4m 7s); graph refreshed. D-11: merge notices after initial release packaging collision; all four notices verified in APK. Push NOT RUN: no commit. Full module/app/sharedLogic suites, lintDevQa and device checks NOT RUN: per-task quick gate; full/device gate belongs to T013. |
| 2026-10-14 | Phase A end gate; 002 spec/plan/tasks | PASS (lint: 109 MissingTranslation only) |
| 2026-10-14 | Imports 2–46 (see `IMPORT-LOG.md`; conflicts at 9, 14, 29, 42, 46 resolved and checked; env fixes at 15, 19, 21, 24, 30, 36) | PASS |
| 2026-10-14 | Import 1/46 `01759c8` → `c4e7401`; README rebrand leftover fixed | PASS |
| 2026-10-14 | Corpora rebuilt (WaxFlow/DSD/WavPack verified; WaxLabel, analysis, dsp oracles built) | D-1, D-2 |
| 2026-10-14 | Graphify scope for the 002 modules | `693a54d`, graph unchanged (no module code yet) |

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
