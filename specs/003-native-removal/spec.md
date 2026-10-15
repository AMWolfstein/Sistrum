# Feature Specification: Remove the native player and FFmpeg (003)

**Feature Branch**: `feature/media3-migration` (no separate branch; 003 merges on its own after 001 + 002 are on `main`)
**Created**: 2026-10-15
**Status**: Draft
**Input**: `NOTES.md` (owner decisions 2026-10-05), constitution Principles 8 and 11, `specs/002-kotlin-decoders/HANDOFF.md`
"003 backlog", `specs/review/drift/REGISTRY.md`.

Governing: Principle 8 (native code ends with 003: the player and our FFmpeg build go together, then the NDK), Principle 10
(built for all users, not the owner's library), Principle 11 (Kotlin end state). Behaviour of Media3, the Kotlin decoders and
every 001/002 feature stays as shipped; this feature only deletes the old path and proves nothing was lost.

## Context

Since 001 the default engine is Media3; since 002 every format the Kotlin modules cover plays on it (WavPack, APE, ALAC,
Musepack, ADPCM/G.711, WMA family, AIFF, DSD). The old native engine (`LegacyNativeEngine` over `FfmpegDecoder`, JNI,
`androidApp/src/main/cpp`, four FFmpeg `.so` files built by `scripts/build-ffmpeg-android.sh`) is only a developer-switch
fallback. 003 removes it in two steps and ends with an app that ships no native code of its own.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know exactly what FFmpeg played today (Priority: P1)
A format inventory lists everything FFmpeg plays now (its enabled demuxers/decoders crossed with what MediaStore marks as
music) and, for each entry, who plays it after 003: platform, Kotlin decoder, or nobody.

**Why this priority**: removal is only safe against a written list. **Independent Test**: the inventory file exists, has no
row without a provider column, and every row whose provider is not "none" has a playing fixture in the device test.

**Acceptance Scenarios**:
1. **Given** FFmpeg built with all decoders/demuxers, **When** the inventory is generated, **Then** every audio format it can
   play has one row with provider platform / Kotlin / none and a fixture name or "no fixture (reason)".
2. **Given** rows with provider none, **When** the inventory is reviewed, **Then** each is marked OWNER DECISION and no code
   removal that would drop it starts before the owner answers.

### User Story 2 - Media3 is the only engine (Priority: P1) — step 1
The developer engine switch, the native engine, JNI decoder, C++ sources, FFmpeg libraries, the FFmpeg build script and the CMake
wiring are gone. Playback, crossfade, gapless, normalization, equalizer and queue behave as on Media3 today.

**Independent Test**: full host suites, lint and the Media3 device classes pass on `.qa`; `git grep` for the removed symbols
is empty; the inventory device test plays every row that has a provider.

**Acceptance Scenarios**:
1. **Given** a build after step 1, **When** Settings → Developer is opened, **Then** there is no "Playback engine" row and no
   saved engine choice has any effect (a stored "Native" value is ignored, no crash).
2. **Given** any library file the scanner admits, **When** it is played, **Then** Media3 plays it or the existing
   skipped-files summary already listed it (no new silent failure).
3. **Given** a user who had normalization switched off because Native was selected, **When** the app starts, **Then**
   normalization is available as for Media3 users (its saved on/off value is kept).

### User Story 3 - No native code at all (Priority: P2) — step 2
`ndkVersion`, `externalNativeBuild`, `ndk.abiFilters`, `jniLibs` and the NDK/CMake setup steps disappear. The APK is built with no
NDK and holds no `.so` produced from our sources.

**Independent Test**: unzip the `devQa` and `prodRelease` APKs; list `lib/`; build with `ndk` absent from `sdk.dir`.

**Acceptance Scenarios**:
1. **Given** step 2 done, **When** the APK is inspected, **Then** `lib/` contains no `libairmedy_player.so`, `libav*.so`,
   `libswresample.so`; any remaining third-party `.so` is listed with its owner dependency (OWNER DECISION if any).
2. **Given** a machine without an NDK or CMake, **When** `assembleDevQa` runs, **Then** it succeeds.

### User Story 4 - Cleaner code and a measured before/after (Priority: P3)
Dead code and resources left behind are removed, and quality reports taken at the start and the end show the change.

**Acceptance Scenarios**:
1. **Given** the refactor-cleaner and code-simplifier reports, **When** cleanup tasks run, **Then** only SAFE findings are
   removed, each with its evidence, and the full suites still pass.
2. **Given** the quality tooling, **When** run before and after, **Then** counts for detekt, R8 `usage.txt`,
   UnusedResources and Kover are recorded in HANDOFF.

### Edge Cases
- A user's library holds a format that only FFmpeg decoded (e.g. TTA, TAK) and no provider exists: Step 1 would stop playing it.
  Resolved only by an owner decision (accept unsupported + skipped-files summary, or port a decoder first).
- A stored `engine` DataStore value `Native` from an old install: ignored.
- Tests, goldens and KDoc that cite `ffmpeg_player.cpp` as source of truth (`eq_golden.json`, `tools/eq-golden/`) lose their
  source file: provenance must be kept (golden data stays, generator documented as historical or removed with its test).
- `audio/ffmpeg` is a MediaStore MIME string seen in real libraries: scanner handling stays (it is data, not our FFmpeg).
- A third-party AndroidX library may ship its own `.so`: not ours, reported, not silently ignored.

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: A format inventory (`specs/003-native-removal/research/format-inventory.md` + `.tsv`) MUST exist before any removal
  and list every FFmpeg-playable audio format with provider platform / Kotlin / none.
- **FR-002**: Formats with provider none MUST be listed for an owner decision; no task decides them.
- **FR-003**: Before step 1: 002 complete (DSD Kotlin provider; WMA 1/2/Lossless/Pro/Voice; all 002 gates PASS) and the owner approves
  starting (MANUAL).
- **FR-004**: Step 1 MUST remove the engine switch (UI, preference, factory), `LegacyNativeEngine`, `FfmpegDecoder` and its
  `me.misa198.airmedy.player` package, `androidApp/src/main/cpp/`, `androidApp/src/main/jniLibs/`,
  `scripts/build-ffmpeg-android.sh`, the CMake wiring, native-only tests and strings (English and Arabic).
- **FR-005**: Media3 MUST remain the engine with no behaviour change; the `PlayerEngine` seam and `PlaybackCoordinator` change only
  where native-only branches (`EngineKind`, `kind` checks, `EngineFactory`) disappear.
- **FR-006**: Step 2 MUST remove `ndkVersion`, `ndk { abiFilters }`, packaging/ABI leftovers and NDK/CMake instructions; the build
  MUST not need an NDK.
- **FR-007**: Step 2 starts only when every inventory format has a provider or an owner-accepted "unsupported" decision.
- **FR-008**: Quality tooling (detekt + Compose rules, R8 usage report, lint UnusedResources/UnusedIds, Kover) is added report-only first
  (D-26 of 002) and re-run at the end.
- **FR-009**: Cleanup removes only SAFE `refactor-cleaner` findings and registry rows planned for "Cleanup/003"; no feature removed,
  no Room schema change.
- **FR-010**: Docs (CLAUDE.md Playback/Build/Known state, `docs/dev-setup.md`, constitution Principle 8 note, ADRs referencing the
  switch) are updated in the last task.
- **FR-011**: English and Arabic strings stay complete (`ArabicTranslationCompletenessTest`); removed keys are removed from both.

### Key Entities
- **Inventory row**: container/codec name, FFmpeg demuxer/decoder names, MediaStore MIME/extension, provider (platform / Kotlin
  module / none), fixture, decision (OK / OWNER DECISION / accepted-unsupported).
- **Removal inventory** (paths of removed code): in `plan.md`.

## Success Criteria *(mandatory)*

- **SC-001**: `git grep -n -E "FfmpegDecoder|LegacyNativeEngine|EngineSelectionPreferences|EngineKind|EngineFactory|airmedy_player|build-ffmpeg-android|externalNativeBuild|ndkVersion|abiFilters"`
  over source, build and scripts (excluding `specs/`, `docs/migration`, graph output, history notes) is empty.
- **SC-002**: The `devQa` and `prodRelease` APKs contain 0 files of ours under `lib/` (`unzip -l`); third-party `.so` listed.
- **SC-003**: Every inventory row with a provider plays on `.qa` through the inventory device test: starts, position advances,
  one seek lands within 300 ms of target, ends or stops cleanly. Result table in HANDOFF.
- **SC-004**: Full host suites (`:androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest` and the four module test tasks)
  pass; test counts do not drop except by recorded native-only deletions (count listed in HANDOFF).
- **SC-005**: `lintDevQa` 0 errors; `ArabicTranslationCompletenessTest` passes.
- **SC-006**: APK size (`devQa` and `prodRelease`) before vs after recorded in HANDOFF (expected: smaller by the four `.so`).
- **SC-007**: 001 device classes `Media3EngineCoreTest`, `Media3EngineGaplessTest`, `Media3CrossfadeTest`,
  `NormalizationLevelDeviceTest` and 002 `KotlinFormatsPlaybackTest` give the same result as the M0 baseline (known baseline
  failures unchanged, no new failure).
- **SC-008**: Quality reports before and after recorded: detekt baseline sizes, `usage.txt` lines, UnusedResources count, Kover line
  coverage per module; none worse except by recorded removals.
- **SC-009**: A clean machine setup (`docs/dev-setup.md`) has no NDK/CMake/FFmpeg step.

## Out of Scope
Tag writing; UI redesign; the pre-v1.0 review-and-fix phase; the perf phase (SC-013 CPU parity for Media3 crossfade-off); publishing
or release signing; changing Room; new formats beyond owner-decided ports; removing the `PlayerEngine` interface itself.

## Assumptions
Decisions taken for lack of an owner answer are in `DECISIONS.md` (D-1…D-12) with "Revisit if". Open owner items are marked
OWNER DECISION in `tasks.md`.
