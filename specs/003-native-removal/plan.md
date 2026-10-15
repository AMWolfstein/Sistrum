# Implementation Plan: Remove the native player and FFmpeg (003)

**Branch**: `feature/media3-migration` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

## Summary
Delete the old native path in two steps behind a written format inventory and an owner gate: step 1 = engine switch, native
engine, JNI decoder, C++/FFmpeg files and build script; step 2 = remaining NDK settings, proof of zero `.so`. Then clean what the
removal exposed, re-run the quality reports, and finish with docs and a full regression. Execution as 002: Codex, autonomous mode
(`AGENTS.md`), one fresh session per task via `milestone-run.sh`; quick gate per task; ONE full gate + `.qa` phone check ends each
milestone; Claude steps and MANUAL tasks stop the runner.

## Technical Context
Kotlin (JDK 21), Android minSdk 31 / target 36, AGP 9.4.1, Media3 1.11.1. Tests: JUnit host tests per module, androidApp host tests,
instrumented tests on `com.amwolfstein.sistrum.dev.qa` only (never the owner's daily apps). Measurement device CPH2307 (a sample, not
the target). No new dependencies except the report-only quality tooling (T001). No Room change. No new strings; strings only removed
(English + Arabic together).

## Constitution Check
- P8 native code ends with 003: this feature; gates (regression + owner approval, DSD provider, inventory) are tasks T002/T008/T016.
- P10 all users: inventory comes from FFmpeg's full format set, not the owner's library; "none" rows go to the owner.
- P11 Kotlin end state: no native decoder remains; WaxFlow stays reference only.
- Coder rules: tests-first where behaviour must be preserved (T006, T009, T010); no weakened tests; native-only tests are deleted
  with a recorded count, not edited to pass.

## Milestones
| M | Goal | Last task (full gate + phone check) |
|---|------|------|
| M0 | Quality baseline (report-only), preconditions, "before" reports, device baseline | T004 |
| M1 | Format inventory + Media3 playback proof for every row with a provider; owner decisions on "none" rows | T007 |
| M2 | Step 1: Media3 only, native engine, JNI, cpp, jniLibs, FFmpeg script, CMake blocks gone | T016 |
| M3 | Step 2: no NDK settings, zero `.so`, build without NDK | T020 |
| M4 | Cleanup (SAFE only), dead resources, "after" quality reports | T025 |
| M5 | Docs, final full regression, Checkpoint 003 | T028 |

Boundary gates (runner stops): T002 (MANUAL, 002 complete + start approval), T008 (MANUAL, OWNER DECISION on "none" rows + step 1
approval), T017 (CLAUDE, step 2 gate), T029 (MANUAL, merge is the owner's).

## Removal inventory (paths only; from `git grep -l` on 2026-10-15)
Delete:
- `androidApp/src/main/kotlin/me/misa198/airmedy/player/FfmpegDecoder.kt` (whole `me/misa198` source tree if it becomes empty)
- `androidApp/src/main/kotlin/com/amwolfstein/sistrum/player/engine/LegacyNativeEngine.kt`
- `.../player/engine/EngineSelectionPreferences.kt`, `EngineFactory.kt`, `EngineEvent.kt` only if native-only (check)
- `androidApp/src/main/cpp/` (`CMakeLists.txt`, `ffmpeg_player.cpp`)
- `androidApp/src/main/jniLibs/arm64-v8a/` (libavcodec, libavformat, libavutil, libswresample; untracked)
- `scripts/build-ffmpeg-android.sh`
- `tools/eq-golden/` (generator, D-9)
- `androidApp/src/test/kotlin/.../player/engine/LegacyNativeEngineTest.kt`, `EngineFactoryTest.kt`
Edit:
- `androidApp/build.gradle.kts` (ndkVersion, defaultConfig externalNativeBuild + ndk block, android externalNativeBuild)
- `.../player/engine/PlayerEngine.kt` (drop `EngineKind`, `kind`), `.../player/PlaybackCoordinator.kt` (five `kind` branches),
  `.../player/PlaybackService.kt` (factory wiring), `.../MainActivity.kt` (normalization availability),
  `.../sync/LibraryScanRunner.kt` (scan gate), `.../ui/navigation/AppDestinationContent.kt` (two preference reads),
  `.../ui/screens/DeveloperContent.kt` (row or whole screen, D-8), `.../player/media3/Media3PlayerFactory.kt`,
  `.../player/media3/Media3Engine.kt`, `.../player/EqualizerPreferences.kt`, `.../player/media3/FocusVolumeRamp.kt`,
  `.../player/dsp/BiquadEqualizer.kt` (comments citing cpp / airmedy package)
- `androidApp/src/main/res/values/strings.xml`, `values-ar/strings.xml` (`developer_engine`, `developer_engine_native`,
  `developer_engine_media3`, `developer_engine_note`, and the Developer screen title if D-8 removes it)
- Tests: `androidApp/src/test/.../player/fakes/FakePlayerEngine.kt`, `FakePorts.kt`, `PlaybackCoordinator*Test.kt` (crossfade,
  skip-crossfade, auto-mode, fade-lead, repeat-one), `decoders/kotlin/ChannelMapperTest.kt`, `KotlinAlacProviderTest.kt` (check
  native mentions only), `sync/AudioFormatTest.kt` (keep, D-11),
  `androidApp/src/androidTest/.../regression/Regression001MeasurementTest.kt` (native arm), `media3/Media3DspTest.kt`,
  `media3/Media3FloatPathTest.kt` (airmedy package mentions), `analysis/LoudnessStoreTest.kt` (check label), `test/resources/golden/eq_golden.json` (note only)
- Docs/tooling (Claude or last task): `CLAUDE.md`, `AGENTS.md`, `docs/dev-setup.md`, `docs/REGRESSION.md`, `.graphifyignore`,
  `.claude/hooks/graph_refresh.py`, `.claude/agents/{refactor-cleaner,opensource-sanitizer,comment-analyzer}.md`,
  `.specify/memory/constitution.md` (Principle 8 text), third-party notices naming FFmpeg.

## Approach per step
1. **M0** tooling per T001; Claude runs `codebase-archaeologist` and `refactor-cleaner` on the unchanged tree (reports in
   `research/before-*.md`); the device baseline (SC-007) is recorded so later failures can be told from known ones.
2. **M1** `scripts/format-inventory.py` generates `research/format-inventory.tsv` from the FFmpeg source lists (downloaded the way the
   build script does, cache under `~/.cache`) and the registry; a host test checks the file's shape; a device test plays one fixture
   per row with a provider on Media3 (corpus from `scripts/test-corpus`, `~/.cache/sistrum/oracle-corpus`; missing corpus fails
   with the rebuild command, never skips).
3. **M2** order: characterization tests and `check-no-native.sh` first; then remove UI/preferences/scan-gate coupling; then the Kotlin
   engine classes and `kind` branches; then native files and `externalNativeBuild` blocks. Every task leaves the build green.
4. **M3** NDK settings off; `check-no-native.sh --apk` proves `lib/` clean; `./gradlew assembleDevQa --dry-run` lists no
   `configureCMake*`/`buildCMake*` task.
5. **M4** cleanup from `research/cleanup-candidates.md` (Claude writes it from refactor-cleaner + code-simplifier + registry rows
   D-09/D-11/D-12); lint `UnusedResources` list; "after" reports.
6. **M5** docs, notices, full regression like 002 T042.

## Verification and review
Per-task `verify.sh --quick`; milestone gate = full suites + `--lint` + `assembleDevQa` + (M2, M3, M5) unsigned `assembleProdRelease`
+ `check-no-native.sh` + `.qa` device classes + `phone-log-triage` on the saved logs. Milestone review (Claude): `silent-failure-hunter`,
`pr-test-analyzer`, `kotlin-reviewer`, `spec-traceability-auditor`, `comment-analyzer`, `architecture-boundary-reviewer` (M2),
`playback-lifecycle-reviewer` (M2: service wiring changed), `santa-method` on M2's `[HIGH-RISK]` diffs. Confirmed CRITICAL/HIGH ->
`<last id>-fix`.

## Risks
Formats lost silently (D-3); third-party `.so` (D-10); hidden reflection or manifest use of removed classes (grep + R8
`usage.txt`); fixtures missing on the machine; Regression001 harness deleted by accident (its Media3 arm must stay); graph scope
lists change with the deleted directories (two places).
