# Implementation Plan: Media3 playback engine and Decoder Registry

**Branch**: `feature/media3-migration` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-media3-migration/spec.md` (revised 2026-10-05)

## Summary

Put both engines behind a `PlayerEngine` seam (ADR-001) with the native player as the first implementation (zero
behaviour change), then build `Media3Engine` on Media3 1.11.1: a Decoder Registry of platform codecs and a Kotlin
AIFF provider (ADR-002), Rhythm's A/B crossfade mechanics with our equal-power curve on two ExoPlayers that share
one audio session (ADR-003), per-player gain/width/EQ/preamp processors matching the native filters plus a
limiter-only DynamicsProcessing on the shared session (ADR-004), tag-based normalization through a pluggable gain
source (ADR-005), and the existing framework MediaSession fed from service state with the review's service-path
fixes (ADR-006). A separate `.qa` test build comes first (ADR-007), then the device spikes S1–S3, S5.

## Technical Context

**Language/Version**: Kotlin 2.4.20, JDK 21; AGP 9.4.1; Compose Multiplatform 1.12.1 (Android target only)

**Primary Dependencies**: new `androidx.media3:media3-exoplayer` 1.11.1 (+ `media3-common`; `media3-extractor`
via exoplayer). Existing: coroutines 1.11.0, DataStore 1.2.1, Room 2.8.5. No FFmpeg in Media3, no NDK changes.

**Storage**: DataStore for engine selection, untagged pre-amp, skipped-files summary; Room unchanged (FR-074)

**Testing**: JUnit host tests (`testDevDebugUnitTest`, `sharedLogic:testAndroidHostTest`), instrumented tests on the
`.qa` build only, device spikes S1–S3/S5 on the `.qa` build, owner's manual checklist for hardware/listening

**Target Platform**: Android minSdk 31, targetSdk 36, arm64-v8a; test device CPH2307 (Android 15) as a sample

**Project Type**: mobile app (`androidApp` + `sharedLogic`)

**Performance Goals**: SC-006 start latency ≤ +20 %; SC-013 CPU/wakelock/memory 0 % (crossfade off) and ≤ +10 %
(on); SC-003 gapless ≤ 10 ms; SC-007 fade start within 200 ms of native

**Constraints**: `PlaybackQueue.kt` / `ListeningTracker.kt` zero changes; native code and `FfmpegDecoder` untouched;
no Room change; offload disabled on Media3; never touch the daily app on device

**Scale/Scope**: libraries of 20 000+ tracks (FR-083); every format FFmpeg plays today reaches parity with 002

## Constitution Check

*Gate before Phase 0 and re-checked after Phase 1.*

| Principle / rule | Plan | Status |
|---|---|---|
| 1 Staged; no per-track routing | Seam first, native first impl; selected engine plays the whole queue | PASS |
| 2 Preserve contracts | `PlaybackController` API/flows unchanged; seam below the service (ADR-001) | PASS |
| 3 No feature loss | Crossfade, EQ (exact filters), normalization, stats, Last.fm, lyrics, Mood Radio machinery all on Media3; merge gated | PASS |
| 4 Local files only | File paths → `file://` MediaItems | PASS |
| 5 Rhythm A/B mechanics, our curve | ADR-003; Rhythm's curve/sessions/playlists not ported; processor fade | PASS (S1 pending) |
| 6 Format parity via registry | ADR-002; merge waits for 002 | PASS |
| 7 Evidence over assumption | Research pins sources; device facts measured or marked BLOCKED | PASS |
| 8 Native code until 003 | No change to `cpp/`, `jniLibs/`, FFmpeg script, `FfmpegDecoder` | PASS |
| 9 Honest verification | Spikes reported with real status (research "Spikes") | PASS |
| 10 Built for all users | Runtime codec probing; corpus covers all forms; two library mixes for perf | PASS |
| 11 Kotlin end state, WaxFlow reference | No WaxFlow code in 001; oracle only for 002 | PASS |
| Do not touch: analysis read side, queue, tracker | Not in any planned file list | PASS |
| Test builds / corpus | ADR-007 first; corpus generator task | PASS |
| Licensing | Rhythm, Choir ports with SPDX headers; notices file created by the first dependency task | PASS |

Post-design re-check (after Phase 1): unchanged, all PASS. One tension is recorded, not a violation: the
service-path robustness requirements (FR-080…092) live in the shared service and so also alter the native
engine's *service* path in failure cases (ADR-006; accepted by the owner).

## Project Structure

### Documentation (this feature)

```text
specs/001-media3-migration/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── adr/ADR-001 … ADR-007
├── contracts/ player-engine.md, decoder-registry.md, gain-source.md
├── research/ (discovery, external reports, platform codecs, future records)
├── checklists/requirements.md
└── HANDOFF.md
```

### Source Code (repository root)

```text
androidApp/src/main/kotlin/me/misa198/airmedy/
├── player/
│   ├── PlaybackService.kt            # orchestration stays; engine calls move behind PlayerEngine
│   ├── PlaybackController.kt         # API unchanged; queue handoff in process (FR-083)
│   ├── engine/                       # NEW: PlayerEngine, EngineEvent, EngineSelection, LegacyNativeEngine
│   ├── media3/                       # NEW: Media3Engine, TransitionController, player factory, LimiterSession
│   ├── dsp/                          # NEW: GainProcessor, StereoWidthProcessor, EqualizerProcessor (biquads)
│   ├── decoders/                     # NEW: DecoderRegistry, PlatformProvider, aiff/AiffExtractor (Choir port)
│   ├── normalization/                # NEW: GainSource, TagGainSource (Rhythm ReplayGainUtil port), gain math
│   └── FfmpegDecoder.kt              # untouched
├── sync/MediaStoreLibraryScanner.kt  # registry gate before tag read (Media3 engine only), skipped summary
└── settings/…                        # hidden engine switch, untagged pre-amp, skipped summary, notes
androidApp/src/test/…/player/…        # characterization + unit tests
androidApp/src/androidTest/…          # instrumented (run on .qa only)
sharedLogic/…/player/                 # PlaybackQueue.kt, ListeningTracker.kt: zero changes
gradle/libs.versions.toml, androidApp/build.gradle.kts   # media3 deps, .qa build
THIRD-PARTY-NOTICES (new, first dependency task)
```

**Structure Decision**: new code in subpackages of `player/` so the seam, engine, DSP, decoders and normalization
are separate units with their own tests; the scanner change is one gate call.

## Implementation phases (input for `/speckit-tasks`, not tasks)

0. **Test build + corpus** — `.qa` build (ADR-007); corpus generator for every format/tag form available locally;
   APE/DSD samples from the owner (HANDOFF open item).
1. **Device spikes, first tasks after the `.qa` build** — S1 (processor fade by listening, 12 s fade and snaps;
   ADR-003), S2 (shared-session routing and summed-tone limiting, hi-res direct output; ADR-004), S3 (memory/CPU
   with the second-player lifetime, processor rate; ADR-004), S5 (Opus header gain once; ADR-005). Minimal
   Media3 harness in androidTest, not app code. Results go into the ADRs; a result that contradicts an ADR stops
   the plan for an owner decision.
2. **Characterization (tests only)** — pure policies (`shouldStartCrossfade`, `crossfadeDurationMs`,
   `audioFocusChangeAction`, …); must not assert normalization/Mood Radio, the 1000-track truncation, or any
   faulty behaviour FR-080…092 correct (FR-073).
3. **Seam** — `PlayerEngine`, `LegacyNativeEngine`, events, engine switch (default native); orchestration moved
   from `PlaybackService` into a plain-Kotlin `PlaybackCoordinator` (ADR-001 amendment, host testability), then
   service-level characterization with a fake engine. US1/US3.
4. **Media3 core** — single player, gapless via two-item playlist, events, errors with provider name; service
   robustness FR-080…092 on the shared command path (both engines). US2, US4 (framework session fed from state;
   Unknown artist fix).
5. **Decoder Registry + AIFF + scan gate + skipped summary**. US8.
6. **DSP chain + limiter session** (tests first for gain/EQ math). US7.
7. **Normalization** (tests first: tag parsing per form incl. `REPLAYGAIN_REFERENCE_LOUDNESS`, gain math). US6.
8. **Crossfade** (tests first: transition state machine, snaps, next/previous/repeat-one; processor fade). US5.
9. **Regression and resources** — SC-001…016 on `.qa`, two library mixes; owner's manual checklist. Default stays
   native until 002 is also done.

High-risk (tests delegated first, `deepseek-v4-pro` for implementation per CLAUDE.md): phases 4 (service
command path), 6, 7, 8.

## Owner decisions at STOP 2 (2026-10-05)

1. Fade: per-sample processor fade before the EQ (ADR-003; S1 verifies by listening).
2. MediaSession: framework session in 001; a Media3 session is its own feature after 001 and 002, not waiting
   for 003 (ADR-006, constitution "Out of scope").
3. FR-080…092 apply to the shared service path, including the native engine's failure cases;
   characterization tests must not assert the old faulty behaviour (FR-073).
4. `REPLAYGAIN_REFERENCE_LOUDNESS` honoured when present: tag gain + (−18 − reference) (constitution, FR-041,
   ADR-005).
5. ADR-003/004/005 are "pending device spike" (S1; S2, S3; S5); the spikes are the first tasks after the `.qa`
   build (phase 1).

## Complexity Tracking

No constitution violations to justify.
