---
description: "Task list for 001-media3-migration"
---

# Tasks: Media3 playback engine and Decoder Registry

**Input**: `specs/001-media3-migration/` — spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md,
adr/ADR-001…007, `.specify/memory/constitution.md`
**Branch**: `feature/media3-migration` · **Generated**: 2026-10-05

**Tests**: required. The spec mandates characterization tests (FR-073), the owner mandates tests-first tasks for
crossfade, queue/state, normalization and the EQ match, and every task carries its own test command.

## Conventions

**Path roots** (used in every task):
- `A/` = `androidApp/src/main/kotlin/me/misa198/airmedy/`
- `AT/` = `androidApp/src/test/kotlin/me/misa198/airmedy/`
- `AI/` = `androidApp/src/androidTest/kotlin/me/misa198/airmedy/`
- `RES/` = `androidApp/src/main/res/`

**Commands**:
- `UT <Class…>` = `./gradlew :androidApp:testDevDebugUnitTest --tests '<fully.qualified.Class>'` (one `--tests` per class)
- `GATE` = `./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest && ./gradlew :androidApp:assembleDevDebug`
  (from T001 on, also `./gradlew :androidApp:assembleDevQa`)
- `QA-I <Class>` = `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest`; check both IDs with
  `aapt2 dump packagename` (`me.misa198.airmedy.dev.qa` / `me.misa198.airmedy.dev.qa.test`); `adb install -r` both;
  `adb shell svc power stayon usb`; `adb shell am instrument -w -r -e class <Class> me.misa198.airmedy.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`.
  Never against `me.misa198.airmedy.dev`. No device → NOT RUN, task stays open for that part.

**Markers** (after the description):
- `[P]` parallelizable (different files, no dependency on an open task) · `[USn]` user story
- `{default}` = `opencode-go/deepseek-v4.1-flash` · `{hard}` + `[HIGH-RISK]` = `opencode-go/deepseek-v4-pro`.
  The fallback `opencode-go/glm-5.3-flash` is not pre-assigned: it replaces `{default}` on any task where the
  default has failed twice (CLAUDE.md Models).
- `{orchestrator}` = done by Claude Code itself (workflow tooling under `.claude/`, CLAUDE.md, docs, device runs);
  not delegated.
- `[MANUAL]` = needs the device (`.qa` build) and/or the owner's ears; device parts are run by Claude Code via adb
  on `.qa`, listening/hardware parts go to the owner's checklist and are never marked PASS by Claude.
  Owner decision 2026-10-05: minimal live testing. Prefer automated device measurements; listening/hardware parts
  are not run live but collected in `HANDOFF.md` "Deferred owner checks" and the task completes on automated evidence.
- `[TESTS-FIRST]` = tests committed before their implementation task. They fail when committed, for the reason
  stated (missing implementation), and are verified with `verify.sh --expect-fail '<classes>'` (added in T007;
  owner-approved 2026-10-05): the named classes must compile, and **every test in them must fail on an assertion
  about the missing behaviour** (`AssertionError` / `AssertionFailedError` / `ComparisonFailure`), never on a crash,
  an exception or an unrelated error; everything else must pass. The commit message lists the expected-fail classes.
  The following implementation task turns them green without modifying them (MUST NOT: "make these tests pass
  without modifying them").

**Every task**: one atomic commit `<type>(playback): …` with the task id in the body; staged by explicit path with
`graphify-out/graph.json` + `.graphify_analysis.json`; `/verify-task` before commit; `migration-guard` when the
diff touches tests, `PlaybackController.kt` or Room. Fixed MUST NOTs (every brief): no change to
`sharedLogic/…/PlaybackQueue.kt`, `sharedLogic/…/ListeningTracker.kt`, `androidApp/src/main/cpp/**`,
`androidApp/src/main/jniLibs/**`, `scripts/build-ffmpeg-android.sh`, `A/player/FfmpegDecoder.kt`, the analysis
read side (`activeAnalyses`, `moodRadioEligibleTrackIds`, `moodRadioTracks`, `normalizationGain*`), the Room
schema/version, `signingConfigs`.

**Spike gates**: tasks that depend on ADR-003 (crossfade) wait for T002 (S1); on ADR-004 (session, limiter, DSP
chain) for T003 (S2) and T004 (S3); on ADR-005 (normalization) for T005 (S5). A spike result that contradicts its
ADR stops the plan for an owner decision.

**Milestones**: M1…M8. Each ends with the CLAUDE.md status block, a `HANDOFF.md` update, and the owner's approval
before the next milestone starts.

---

## Milestone M1 — Phase 1: Test build, device spikes, corpus (Setup, US9)

**Goal**: a safe `.qa` build next to the daily app; ADR-003/004/005 confirmed or stopped by device evidence; the
test corpus on the device.

- [x] T001 [US9] Add the `.qa` test build (ADR-007) in `androidApp/build.gradle.kts`, `androidApp/src/qa/AndroidManifest.xml`, `androidApp/src/qa/res/values/ic_launcher_background.xml` {default}
  - Do: build type `qa` = `initWith(debug)`, `applicationIdSuffix = ".qa"`, `isDebuggable = true`, no minify,
    `matchingFallbacks += "debug"`; `testBuildType = "qa"`; `qaImplementation` of ui-tooling/ui-test-manifest; label
    "Sistrum QA" through a `qa` manifest overlay (`tools:replace="android:label"`; `app_name` exists in 14 locales, so a
    resource override would lose) and a distinct `ic_launcher_background` colour in the `qa` source set.
    `signingConfigs` untouched. `{orchestrator}` part in the same commit: CLAUDE.md adb section (`assembleDevQa`,
    `assembleDevQaAndroidTest`, package `me.misa198.airmedy.dev.qa.test`), `.claude/skills/verify-task/scripts/verify.sh`
    (QA APK paths `apk/dev/qa/…`, `apk/androidTest/dev/qa/…`, gradle task names), `docs/dev-setup.md`, `quickstart.md`.
  - Tests: `GATE`; `aapt2 dump packagename` on devDebug (= `me.misa198.airmedy.dev`, unchanged), devQa
    (= `…dev.qa`), devQa androidTest (= `…dev.qa.test`); `QA-I me.misa198.airmedy.diagnostics.PlatformAudioDecodersTest`
    (first run of the S4 probe; record result in `research/platform-codecs-cph2307.md`).
  - Accept: both apps installed side by side; `adb shell dumpsys package me.misa198.airmedy.dev | grep -E 'versionName|lastUpdateTime'`
    identical before/after; no uninstall issued. `[MANUAL]` owner confirms the daily app's library, statistics and
    settings are unchanged (SC-009).

- [x] T002 [MANUAL] Spike S1 — processor fade vs `player.volume` stepping, decision into `specs/001-media3-migration/adr/ADR-003-dual-player-crossfade.md`; harness in `AI/spikes/FadeSpikeTest.kt`, `AI/spikes/SpikeFadeProcessor.kt` {hard} [HIGH-RISK]
  - Do: androidTest-only harness (not app code): two ExoPlayers with one shared audio session id and identical
    `AudioAttributes`, offload off, `handleAudioFocus = false`; A plays track A, B prepared with track B; mode
    `processor` = per-sample equal-power gain processor (outgoing `cos(t·π/2)`, incoming `sin(t·π/2)`, keyed to
    frame position) before the sink; mode `volume` = 16 ms `player.volume` steps. Fade length and mode via
    instrumentation args (`-e fadeMs 12000 -e mode processor -e trackA <path> -e trackB <path>`); snap tests
    (pause, seek, next at 30 % and 70 % of the fade) with the flush of the incoming player. Add `androidx.media3`
    1.11.1 (`media3-exoplayer`, `media3-common`) to `gradle/libs.versions.toml` and as `androidTestImplementation`;
    create `THIRD-PARTY-NOTICES` (Media3, Apache-2.0) — the first dependency task (constitution).
  - Tests: `QA-I me.misa198.airmedy.spikes.FadeSpikeTest` (asserts per-frame curve values in the processor and that
    no audio after a snap carries the faded gain, via a `TeeAudioProcessor` capture).
  - Accept: `[MANUAL]` owner listens to a 12 s fade and the snaps in both modes; decision (processor fade kept, or
    stop and update ADR-003) with date and evidence written into ADR-003 "Status"; measured incoming start offset (ms)
    recorded (SC-007 budget 200 ms).

- [x] T003 [P] [MANUAL] Spike S2 — shared session routing + limiter-only DynamicsProcessing, decision into `specs/001-media3-migration/adr/ADR-004-audio-chain-shared-session.md`; harness in `AI/spikes/SharedSessionLimiterSpikeTest.kt` {default}
  - Do: two ExoPlayers on one session play generated sine WAVs (written to the app cache by the test) that clip only
    when summed (each −3 dBFS, same frequency, in phase); one `DynamicsProcessing` on the session, limiter stage only
    (input/output gain 0, pre-EQ/MBC/post-EQ off, threshold −1 dBFS, attack 1 ms, explicit release/ratio/post-gain,
    channels linked, `setPreferredFrameDuration` = sink buffer). Measure the output peak with `Visualizer` on the
    output mix (session 0; `RECORD_AUDIO` granted to `.qa` with `adb shell pm grant`), limiter on vs off. Repeat
    with a 24-bit/96 kHz file to check direct-output routing.
  - Tests: `QA-I me.misa198.airmedy.spikes.SharedSessionLimiterSpikeTest`; during the run
    `adb shell dumpsys media.audio_flinger` (both tracks on one output thread, DP enabled).
  - Accept: ADR-004 records: same thread yes/no, peak with/without limiter, overshoot bound (feeds SC-011), hi-res
    route (mixer vs direct), chosen limiter parameters. Contradiction → stop for owner.
  - Result (2026-10-05): **PARTIAL** — shared session (with re-apply + check) and routing confirmed; limiter level,
    SC-011 and the DP-disabled mute question moved to T003b (owner decision, option A). 3 review rounds.

- [x] T004 [P] [MANUAL] Spike S3 — memory/CPU of the second-player lifetime and processor rate, decision into `specs/001-media3-migration/adr/ADR-004-audio-chain-shared-session.md` and `ADR-003-dual-player-crossfade.md`; harness in `AI/spikes/ResourceSpikeTest.kt` {default}
  - Do: 10-minute loops on `.qa`: (a) one player; (b) one player + B created at fade + 5 s and released after the
    fade, every 60 s; (c) as (b) with four pass-through processors per player; (d) processors after a resampler to
    the output rate. Sample `dumpsys meminfo me.misa198.airmedy.dev.qa` and `dumpsys batterystats --charged` (CPU,
    wakelocks) after `batterystats --reset`.
  - Tests: `QA-I me.misa198.airmedy.spikes.ResourceSpikeTest` + the dumpsys captures.
  - Accept: numbers per mode in ADR-004; pre-buffer length chosen (ADR-003 "≈5 s, tuned in S3"); decision on
    track-rate vs fixed output-rate processing. Two format mixes (Opus-heavy and FLAC/hi-res) per Principle 10.

- [x] T005 [P] [MANUAL] Spike S5 — Opus header output gain applied once, decision into `specs/001-media3-migration/adr/ADR-005-normalization-gain-source.md`; harness in `AI/spikes/OpusHeaderGainSpikeTest.kt`, fixture tool `scripts/spikes/opus-header-gain.py` {default}
  - Do: the script encodes a 1 kHz −20 dBFS tone to Opus with ffmpeg, then writes copies with OpusHead output gain
    0 and +6 dB (Q7.8 at byte offset 16, Ogg page CRC recomputed); pushed to the device cache by the test setup.
    The test decodes both through ExoPlayer with a `TeeAudioProcessor` and compares RMS.
  - Tests: `python3 scripts/spikes/opus-header-gain.py --self-check` (parses back the gain field);
    `QA-I me.misa198.airmedy.spikes.OpusHeaderGainSpikeTest`.
  - Accept: ADR-005 records the measured difference (≈6 dB = applied once by the platform decoder → `GainProcessor`
    adds nothing; ≈0 dB → `GainProcessor` adds it; anything else → stop for owner).

- [x] T006 [US9] Test corpus generator in `scripts/test-corpus/generate-corpus.sh`, `scripts/test-corpus/generate_corpus.py`, `scripts/test-corpus/corpus.tsv`, `scripts/test-corpus/push-corpus.sh`, `scripts/test-corpus/README.md` {default}
  - Do: generates locally every format/tag form available (MP3 ID3 TXXX, FLAC/Ogg Vorbis REPLAYGAIN_* incl. lower-case
    keys and `REPLAYGAIN_REFERENCE_LOUDNESS`, Opus R128_* with/without header gain, M4A AAC with iTunNORM and freeform
    replaygain atoms, M4A ALAC, WAV PCM 16/24/float, AIFF/AIFF-C NONE/twos/sowt 8–32 bit, float/compressed AIFF-C,
    WavPack, WMA, untagged copies of each, malformed/out-of-range/non-finite tags, several forms in one file, short
    tracks < 2 s, a gapless album, hi-res 24/96 and 24/192, multichannel); `corpus.tsv` lists file, format, codec,
    bit depth/rate, tag forms, expected gain dB (per spec rules), expected registry outcome in 001. No faked formats:
    APE/DSF/DFF are not in the corpus (owner decision 2026-10-05: no real samples; 002 validates APE against the
    WaxFlow oracle on WaxFlow's testdata and DSD on synthetic DSF/DFF with Flick's `dsd_engine` as oracle).
    `push-corpus.sh` pushes to `/sdcard/Music/SistrumTestCorpus` (no `.nomedia`), never touches app data. No audio in git.
  - Tests: `bash scripts/test-corpus/generate-corpus.sh --out "$SCRATCH/corpus" && bash scripts/test-corpus/generate-corpus.sh --verify "$SCRATCH/corpus"`
    (ffprobe confirms each file's real codec matches `corpus.tsv`).
  - Accept: every row generated; push done on the device. `[MANUAL]` owner blocklists the folder in the daily app
    (owner, 2026-10-05).

- [ ] T003b [MANUAL] {orchestrator} Spike S2 follow-up — limiter level via AudioFlinger's post-mix power history, decision into `specs/001-media3-migration/adr/ADR-004-audio-chain-shared-session.md`; harness steps in `AI/spikes/SharedSessionLimiterSpikeTest.kt`
  - Owner decision 2026-10-05: done by the orchestrator itself or as small steps (T003 used 3 review rounds), not one
    delegation. Measurement source: `dumpsys media.audio_flinger` per-stream "Signal power history" of the output
    thread carrying the shared session (post-mix, post-effect HAL power; no Visualizer, no permission), sampled per
    phase, with the session's tracks verified on that thread.
  - Must answer: (a) does the limiter reduce a summed overlap that would clip (two-tone loud sum, DP on vs off);
    (b) no reduction below threshold (quiet sum and single tone, DP on vs off within 0.1 dB, SC-011);
    (c) does disabling the limiter stage, or the whole DP effect, mute or attenuate the session (FR-053/055).
  - Accept: (a)–(c) answered with numbers in ADR-004. If the dumpsys method cannot measure them either, STOP and ask
    the owner before trying anything else; fallback = owner listening check (option C).
  - Status 2026-10-05: **STOPPED** (owner condition 3): the post-mix power history is too sparse to measure (a)/(b);
    (c) hinted (no power logged with the DP disabled while the track is active). See `HANDOFF.md` "T003b".
  - Owner decision 2026-10-05 (M1 approval): effect-state hedge approved (ADR-004: never `enabled = false`; neutral
    limiter for "off"; control loss = possible mute). (c) → owner listening check on `.qa`, normal volume, Dolby off.
    (a)+(b) → owner recording via USB-C audio dongle into the laptop, analysed by the orchestrator; if not possible,
    `[MANUAL]` in T048. Steps for both in `HANDOFF.md` "T003b". No max-volume headphone tests.
  - **Gates T046's completion** (not M5's start): T046 cannot close until (a)–(c) pass.

**Checkpoint M1** — status report; ADR-003/004/005 statuses updated from "pending spike"; owner approval. **Approved by the owner 2026-10-05** (T003b open as above; it gates only T046).

---

## Milestone M2 — Phase 2: Characterization and the engine seam (Foundational, US1)

**Goal**: both engines can sit behind `PlayerEngine`; the native engine runs through it with zero behaviour change;
today's behaviour is pinned by host tests on a fake engine.
**Independent test (US1)**: all existing tests unchanged + new characterization tests green, native engine selected;
owner parity pass on `.qa`.

- [x] T007 {orchestrator} Add `--expect-fail '<classes>'` to `.claude/skills/verify-task/scripts/verify.sh` for `[TESTS-FIRST]` tasks; document it in `.claude/skills/verify-task/SKILL.md`
  - Do: run the named classes separately; parse the JUnit XML results: each test in them must fail with an assertion
    failure type (`java.lang.AssertionError`, `junit.framework.AssertionFailedError`, `org.junit.ComparisonFailure`,
    `kotlin.test` assertion errors); any pass, error (`<error>`), crash or other exception type → FAIL. All other tests
    must pass. Print an `Expected-fail: <classes>` line for the commit message.
  - Tests: throwaway test classes (assertion failure / thrown `IllegalStateException` / `TODO()` / passing) in a
    scratch working state, then removed.
  - Accept: only the assertion-failure class is accepted; the other three make the script report FAIL; any other
    failing test reports FAIL.

- [ ] T008 [US1] Characterize the pure playback policies in `AT/player/PlaybackPolicyCharacterizationTest.kt` {default}
  - Do: pin today's results of `shouldStartCrossfade` (401 ms lower edge, `durationMs < 2000`, half-track clamp,
    no preloaded next), `crossfadeDurationMs`, `canPreloadNext`, `shouldRestartQueueOnResume`,
    `stoppedCurrentPosition`, `playbackActionReplacesRestoredQueue`, `audioOutputDisconnectRequiresRecovery`,
    `audioBecomingNoisyRequiresPause`, `audioFocusChangeAction` (all in `A/player/PlaybackModels.kt`). Must NOT assert
    normalization, Mood Radio, the 1000-track truncation, or any behaviour FR-080…092 correct (FR-073).
  - Tests: `UT me.misa198.airmedy.player.PlaybackPolicyCharacterizationTest`; `GATE`. migration-guard.
  - Accept: green on the unchanged production code; no production file in the diff.

- [ ] T009 [P] [US1] Define the seam types in `A/player/engine/PlayerEngine.kt` and `A/player/engine/EngineEvent.kt` per `contracts/player-engine.md` {default}
  - Do: `PlayerEngine` (with `setNormalization(settings)`, `ItemGain` native-only, as in the contract), `EngineKind`,
    `EngineEvent` (TransitionStarted, GaplessAdvanced, Ended, OutputDisconnected, Error(provider, format, cause)),
    `ItemGain`. No callers yet.
  - Tests: `GATE`.
  - Accept: compiles; signatures match the contract; KDoc states the FR-089/FR-086/FR-090 rules.

- [ ] T010 [US1] Implement `A/player/engine/LegacyNativeEngine.kt` (wraps `FfmpegDecoder` 1:1 through an internal `NativeDecoderPort`) with `AT/player/engine/LegacyNativeEngineTest.kt` {default}
  - Do: `FfmpegDecoderPort` adapts `FfmpegDecoder` (unchanged); `pollEvents()` (called from the service ticker)
    turns `consumeTransition` / `isFinished` / `isOutputDisconnected` into events exactly once each; `ItemGain` →
    existing native dB parameter; `setDsp` → `GlobalDspConfig`; dead APIs (`finishCrossfade`, `stop`,
    `preloadedDurationMs/PositionMs`) not exposed.
  - Tests: `UT me.misa198.airmedy.player.engine.LegacyNativeEngineTest` (fake port: each poll result → one event, in
    order; two transitions within one poll interval → two events; no event twice); `GATE`. migration-guard.
  - Accept: `FfmpegDecoder.kt` and native code untouched (`git diff --stat`).

- [ ] T011 [US1] Route `A/player/PlaybackService.kt` through `PlayerEngine` with `A/player/engine/EngineFactory.kt` (always native for now) {hard} [HIGH-RISK]
  - Do: replace every direct `FfmpegDecoder` use (`decoder` field, `FfmpegDecoder()` at prepare/restore,
    `consumeNativeTransition`) by `PlayerEngine` calls; behaviour identical, including timing of the 200 ms ticker.
  - Tests: `GATE` (T008 + all existing tests unmodified); `graphify query "who depends on FfmpegDecoder"` shows only
    `LegacyNativeEngine`.
  - Accept: zero behaviour change by review against discovery §2 Q4/Q8; diff limited to the two files.

- [ ] T012 [US1] Extract the playback path into `A/player/PlaybackCoordinator.kt` behind ports in `A/player/PlaybackPorts.kt`; `A/player/PlaybackService.kt` delegates (ADR-001 amendment "Host testability") {hard} [HIGH-RISK]
  - Do: move `playCurrent`, `restoreCurrent`, `preloadNext`, transition handling, `maybeStartCrossfade`, the ticker
    body, pause/resume/seek/stop/fail, listening + Last.fm calls into `PlaybackCoordinator` (plain Kotlin, no Android
    types). Ports: `NowPlayingPort` (session/notification/foreground), `FocusPort`, `ListeningSink`, `ScrobbleSink`,
    `SessionStorePort`, `Clock`, `PlaybackItemResolver` (existing). Mechanical move, no logic change.
  - Tests: `GATE` unmodified.
  - Accept: `PlaybackCoordinator` has no `android.*` imports except annotations; diff is a move (review with
    `git diff --color-moved`).

- [ ] T013 [US1] Move command dispatch (queue ops, shuffle/repeat, Mood Radio start/refill/stop, focus actions) from `A/player/PlaybackService.kt` into `A/player/PlaybackCoordinator.kt` {hard} [HIGH-RISK]
  - Do: `PlaybackService` keeps only Android glue: intent parsing → coordinator calls, MediaSession callbacks,
    notification, focus listener registration, noisy receiver, lifecycle. No logic change.
  - Tests: `GATE` unmodified.
  - Accept: service ≤ ~450 lines; every `PlaybackController` action maps to one coordinator call.

- [ ] T014 [US1] Fake engine + fake ports and queue/transport characterization in `AT/player/fakes/FakePlayerEngine.kt`, `AT/player/fakes/FakePorts.kt`, `AT/player/PlaybackCoordinatorQueueCharacterizationTest.kt` {default}
  - Do: pin discovery §2 Q8 items 1–3 on the native-engine contract: play/pause/resume/stop, next/previous (> 3 s
    restarts, keep paused), shuffle order, repeat Off/One/All, play-next/append/remove/reorder/select/clear,
    stop-at-end keeps last item `Paused` at duration, manual exhaustion → 0, resume after natural end restarts the
    queue, state flow sequence `Preparing → Playing → Paused`; Mood Radio machinery with analysis data supplied by
    the fake resolver (start builds the queue, refill tops it up, stopping actions end it — FR-016, US2 sc7; this
    tests the engine-neutral machinery, not that Mood Radio works with real data). Otherwise same exclusions as T008.
  - Tests: `UT me.misa198.airmedy.player.PlaybackCoordinatorQueueCharacterizationTest`; `GATE`. migration-guard.
  - Accept: green without production changes.

- [ ] T015 [P] [US1] Characterize crossfade orchestration in `AT/player/PlaybackCoordinatorCrossfadeCharacterizationTest.kt` {hard} [HIGH-RISK]
  - Do: Q8 item 5–6 on the fake engine: fade starts only on automatic advance inside the window; queue, state,
    Last.fm `startPlayback` and listening switch at fade **start**; artwork event id/from/to/duration; overlap split
    (`splitCrossfadeOverlap`); i+2 preloaded only after the fade; snap on pause/seek/stop/queue edit; manual change
    → no fade; crossfade setting change does not alter a running fade.
  - Tests: `UT me.misa198.airmedy.player.PlaybackCoordinatorCrossfadeCharacterizationTest`; `GATE`. migration-guard.
  - Accept: green without production changes.

- [ ] T016 [P] [US1] Characterize restore, focus, output recovery, statistics and Last.fm in `AT/player/PlaybackCoordinatorSessionCharacterizationTest.kt` {default}
  - Do: restore paused at saved position, never auto-plays, drops missing tracks, a new Play cancels restore; focus
    pause / transient pause + resume / duck 0.2 / restore; noisy → pause; output disconnect → recreate at the same
    position without splitting statistics; Last.fm start on every start incl. restore, seek, progress per tick.
    Exclusions as T008 (no assertions of focus-request-on-restore or other FR-085 faults).
  - Tests: `UT me.misa198.airmedy.player.PlaybackCoordinatorSessionCharacterizationTest`; `GATE`. migration-guard.
  - Accept: green without production changes.

- [ ] T017 [US1] [MANUAL] {orchestrator} US1 parity pass on `.qa` (native engine): album gapless, 6 s crossfade, EQ on, focus, lock screen, statistics, Last.fm, lyrics sync vs the pre-migration build; `git diff main -- '**/PlaybackQueue.kt' '**/ListeningTracker.kt'` empty; record in `specs/001-media3-migration/HANDOFF.md`
  - Accept: owner checklist signed off (US1 scenarios 1–3); SC-001 and SC-010 hold.

**Checkpoint M2** — status report; owner approval.

---

## Milestone M3 — Phases 3–5: Media3 core, engine switch, service robustness (US2, US3, US4)

**Goal**: the Media3 engine plays the whole queue gaplessly behind the switch (crossfade/EQ/normalization still
inactive on Media3, constitution sequencing exception); the shared service path meets FR-080…092.
**Independent test**: Media3 selected → playback-contract tests + corpus pass (US2); switching mid-track never
interrupts (US3); lock screen shows the right artist (US4).

- [ ] T018 [US2] [TESTS-FIRST] Command-path tests in `AT/player/PlaybackCommandPathTest.kt` {hard} [HIGH-RISK]
  - Do: on the coordinator + fake engine: FR-081 strict arrival order (focus loss then gain never swap), FR-091
    media-session queue selection goes through the command path, FR-084 a throwing tick/command → failed state or
    skip, no crash, cancellation propagates; FR-085 focus requested only when audio starts (not for a paused
    restore), abandoned on failure/stop, pending resume cleared by stop and user pause; FR-086 a failing `prepare`
    leaves no engine resources (fake counts open/close); FR-089 two automatic transitions in one tick → two ordered
    deliveries, queue and playing item in sync; FR-090 `Playing` only after the engine reports output started; start
    failure → `Failed`.
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.PlaybackCommandPathTest'`. migration-guard.
  - Accept: each FR has ≥ 1 named test; failures are for missing behaviour, not compile errors.

- [ ] T019 [US2] Add Media3 to the app in `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`, `THIRD-PARTY-NOTICES` {default}
  - Do: `implementation` `media3-exoplayer` + `media3-common` 1.11.1; `testImplementation` `media3-test-utils`; notices
    entry checked.
  - Tests: `GATE`.
  - Accept: no FFmpeg Media3 extension; APK still arm64-only.

- [ ] T020 [US2] Implement the single-player core of `A/player/media3/Media3Engine.kt` and `A/player/media3/Media3PlayerFactory.kt` with `AI/player/media3/Media3EngineCoreTest.kt` {hard} [HIGH-RISK]
  - Do: `file://` MediaItems from absolute paths; offload disabled; `handleAudioFocus = false`; prepare / play /
    pause / seek / position / duration; `Ended`; `Error(provider = "platform", format, cause)`; failing prepare
    releases the player (FR-086); output-started signal for FR-090 (`onIsPlayingChanged` after first rendered
    position advance); malformed file → `Error`, never a crash (FR-088). `beginCrossfade` returns false (no fade yet).
    `positionMs()` advances monotonically and is fresh at the coordinator's 200 ms tick (FR-012).
  - Tests: `QA-I me.misa198.airmedy.player.media3.Media3EngineCoreTest` (generated WAV/MP3/FLAC fixtures in the app
    cache; truncated file → Error); `GATE`.
  - Accept: no service wiring yet; engine not selectable.

- [ ] T021 [US2] Gapless preload in `A/player/media3/Media3Engine.kt` with `AI/player/media3/Media3EngineGaplessTest.kt` {hard} [HIGH-RISK]
  - Do: `preloadNext` appends to the player's two-item playlist; exactly one `GaplessAdvanced` per advance (FR-089),
    in order even for two short items; `clearPreloaded` removes it; the outgoing tail fully played (FR-088). ExoPlayer
    repeat/shuffle never used (research D2).
  - Tests: `QA-I me.misa198.airmedy.player.media3.Media3EngineGaplessTest` (TeeAudioProcessor capture: gap between a
    gapless pair ≤ 10 ms, SC-003 in-app part; event count).
  - Accept: one player only (FR-033 with crossfade off).

- [ ] T022 [P] [US2] Focus duck ramp for Media3 in `A/player/media3/FocusVolumeRamp.kt` with `AT/player/media3/FocusVolumeRampTest.kt` {default}
  - Do: `setFocusGain(0.2|1.0)` → `player.volume` ramp, 120 ms down / 240 ms up (as native `next_focus_gain`),
    applied to every live player.
  - Tests: `UT me.misa198.airmedy.player.media3.FocusVolumeRampTest`.
  - Accept: monotonic ramp, ends exactly at target, retarget mid-ramp continues from the current value.

- [ ] T023 [US3] Engine selection in `A/player/engine/EngineSelectionPreferences.kt` and `A/player/engine/EngineFactory.kt` with `AT/player/engine/EngineFactoryTest.kt` {default}
  - Do: DataStore `engine: Native | Media3`, default `Native` (data-model "EngineSelection"); the coordinator asks the
    factory at each playback start (play, select, restore) and never swaps a running engine.
  - Tests: `UT me.misa198.airmedy.player.engine.EngineFactoryTest` (change mid-track → same engine until next start;
    next start → new engine; fresh install → Native).
  - Accept: SC-008 logic covered on the host.

- [ ] T024 [US3] Hidden developer setting in `A/ui/screens/AboutContent.kt`, `A/ui/screens/DeveloperContent.kt`, `RES/values/strings.xml`, `RES/values-ar/strings.xml` with `AT/ui/screens/DeveloperUnlockTest.kt` {default}
  - Do: 7 taps on the version row unlock a "Developer" entry (state in DataStore) with the engine picker and a note
    that the change applies at the next playback start; Arabic strings complete; no other locale required.
  - Tests: `UT me.misa198.airmedy.ui.screens.DeveloperUnlockTest` (pure tap counter); `ArabicTranslationCompletenessTest`; `GATE`.
  - Accept: invisible without the gesture (US3 sc3).

- [ ] T025 [US3] Rescan on engine change: move `performScan` from `A/ui/screens/LibraryScanContent.kt` into `A/sync/LibraryScanRunner.kt`; trigger it from `A/ui/screens/DeveloperContent.kt` on change {default}
  - Do: scan runs off the main thread without touching playback; the UI scan button uses the same runner.
  - Tests: `GATE`.
  - Accept: playing track not interrupted (checked on device in T035).

- [ ] T026 [US2] Serialized command path in `A/player/PlaybackCoordinator.kt`, `A/player/PlaybackService.kt` (FR-081, FR-084, FR-091) {hard} [HIGH-RISK]
  - Do: one `Channel<Command>` with a single consumer; ticker and media-session callbacks enqueue commands;
    `CoroutineExceptionHandler` + per-command catch → failed/skip, rethrow `CancellationException`.
  - Tests: make the FR-081/084/091 tests of `PlaybackCommandPathTest` pass without modifying them; `GATE`.
    migration-guard (service command path).
  - Accept: T014–T016 still green unmodified.

- [ ] T027 [US2] Focus, release and truthful state on the shared path in `A/player/PlaybackCoordinator.kt` (FR-085, FR-086, FR-089, FR-090) {hard} [HIGH-RISK]
  - Tests: remaining `PlaybackCommandPathTest` cases pass without modifying them; `GATE`.
  - Accept: whole `PlaybackCommandPathTest` green; characterization tests unmodified.

- [ ] T028 [US2] In-process queue handoff (FR-083) in `A/player/PlaybackController.kt`, `A/player/QueueHandoff.kt`, `A/player/PlaybackService.kt` with `AT/player/QueueHandoffTest.kt` {hard} [HIGH-RISK]
  - Do: track ids no longer travel in `TrackIdsExtra`; the intent carries a handoff token; public `PlaybackController`
    API and flows unchanged (Principle 2).
  - Tests: `UT me.misa198.airmedy.player.QueueHandoffTest` (20 000 ids round-trip, token consumed once, stale token
    ignored); `GATE`. migration-guard (PlaybackController).
  - Accept: no `putExtra` with id arrays left (`grep`).

- [ ] T029 [US2] Foreground start and ordered shutdown (FR-080, FR-082) in `A/player/PlaybackService.kt`, `A/player/PlaybackController.kt`, `A/player/PlaybackModels.kt` with `AT/player/ForegroundPolicyTest.kt` {hard} [HIGH-RISK]
  - Do: pure `requiresForegroundStart(action)`; every foreground-started path reaches `startForeground` (incl. cold
    service + empty queue, early returns); non-playing commands use `startService`; stop/fail/clear stop the ticker
    and `stopSelf` when idle; `onDestroy` stops intake, cancels, awaits the in-flight command, then releases engine and
    session; no `runBlocking` on the main thread.
  - Tests: `UT me.misa198.airmedy.player.ForegroundPolicyTest`; `GATE`. migration-guard (PlaybackController; public API unchanged).
  - Accept: `[MANUAL]` on `.qa`: `adb shell am start-foreground-service` with pause/seek/stop to a cold service → no
    crash, no `ForegroundServiceDidNotStartInTimeException` in logcat (US4 sc6).

- [ ] T030 [US2] Session publishing without whole-library loads, coalesced seeks, ordered saves (FR-087) in `A/player/PlaybackCoordinator.kt`, `A/player/PlaybackSessionStore.kt` with `AT/player/SessionPublishingTest.kt` {hard} [HIGH-RISK]
  - Tests: `UT me.misa198.airmedy.player.SessionPublishingTest` (resolver call count per command bounded by queue
    window, 50 seek commands → ≤ 2 engine seeks, saves land in command order); `GATE`.
  - Accept: no full-library resolve per command.

- [ ] T031 [P] [US2] Preference churn (FR-092) in `A/player/EqualizerPreferences.kt`, `A/player/PlaybackPreferences.kt`, `A/player/PlaybackCoordinator.kt` with `AT/player/PreferenceChurnTest.kt` {default}
  - Do: `distinctUntilChanged` on EQ/crossfade flows; re-prepare the next track only when a value affecting it changed.
  - Tests: `UT me.misa198.airmedy.player.PreferenceChurnTest`; `GATE`.

- [ ] T032 [US2] Decode-error path (FR-064) in `A/player/PlaybackCoordinator.kt` with `AT/player/EngineErrorPathTest.kt` {default}
  - Do: `EngineEvent.Error` → normal error state, log line with provider id and format, skip to next; no engine retry.
  - Tests: `UT me.misa198.airmedy.player.EngineErrorPathTest`; `GATE`.

- [ ] T032b [US2] Exportable decode-failure log (FR-064a, SC-014) in `A/player/DecodeFailureLog.kt`, `A/player/PlaybackCoordinator.kt`, `A/ui/screens/SettingsContent.kt`, `RES/values/strings.xml`, `RES/values-ar/strings.xml` with `AT/player/DecodeFailureLogTest.kt` {default}
  - Do: bounded log (last 200 entries; JSON lines in `filesDir`, not Room) of time, file name, format, codec, provider,
    error; T032's error path appends to it; settings entry "Decode-failure log": export as text via the share sheet
    (`ACTION_SEND`, `EXTRA_TEXT`) or save via `ACTION_CREATE_DOCUMENT` (no FileProvider, no manifest change), clear.
    No paths beyond the file name, no other library data.
  - Tests: `UT me.misa198.airmedy.player.DecodeFailureLogTest` (append, bound, persistence across instances, export
    text format, clear); `ArabicTranslationCompletenessTest`; `GATE`.

- [ ] T033 [US4] Fix lock-screen "Unknown artist" in `A/player/PlaybackService.kt` with `AT/player/NowPlayingMetadataTest.kt` {hard} [HIGH-RISK]
  - Do: `{orchestrator}` first investigates the metadata path and writes the root cause into the brief and
    `HANDOFF.md`; then pure `nowPlayingMetadata(item)` sets TITLE, ARTIST, ALBUM, ALBUM_ARTIST (+ DISPLAY_* if the
    cause needs it); artwork stays on `decodeArtworkBitmaps`.
  - Tests: `UT me.misa198.airmedy.player.NowPlayingMetadataTest`; `GATE`.
  - Accept: artist text equals the in-app artist for every item with a known artist.

- [ ] T034 [US2] Wire `Media3Engine` into `A/player/engine/EngineFactory.kt` so the switch selects it at the next playback start {default}
  - Tests: `UT me.misa198.airmedy.player.engine.EngineFactoryTest`; `GATE`.
  - Accept: default still Native.

- [ ] T035 [US2] [US3] [US4] [MANUAL] {orchestrator} Media3 pass on `.qa`: US2 scenarios 1–8 (incl. 20 000-track request), US3 1–4 (switch mid-track, rescan), US4 1–6 (lock screen, notification, Bluetooth/headset, focus, unplug, route change, output switcher on Android 14+ —
  FR-023) over the corpus; SC-005 (0 "Unknown artist" on corpus tracks with a known artist)
  - Accept: device parts run via adb and recorded; owner checklist for listening/hardware; failures become tasks.

**Checkpoint M3** — status report; owner approval.

---

## Milestone M4 — Phase 6: Decoder Registry, AIFF, scan gate (US8)

**Goal**: on Media3, formats go through the registry; unsupported files are skipped with a summary.
**Independent test**: Media3 selected, scan the corpus: admitted files play, skipped summary matches `corpus.tsv`.

- [ ] T036 [US8] Registry in `A/player/decoders/DecoderRegistry.kt`, `A/player/decoders/DecoderProvider.kt`, `A/player/decoders/PlatformProvider.kt`, `A/player/decoders/CodecProbe.kt` with `AT/player/decoders/DecoderRegistryTest.kt` {default}
  - Do: per `contracts/decoder-registry.md`; the full table from the scanner's labels (`audioFormatOf`,
    `realCodec` in `A/sync/MediaStoreLibraryScanner.kt`); `PlatformProvider` available when a default extractor
    reads the container and `CodecProbe` (MediaCodecList, probed once per process) has a decoder for the MIME.
  - Tests: `UT me.misa198.airmedy.player.decoders.DecoderRegistryTest` (first available wins; reorder = table change
    only; m4a/aac vs m4a/alac with and without an ALAC decoder; DSD/APE/WV/WMA → null + skip reason).
  - Accept: no format names outside the table.

- [ ] T037 [US8] Port Choir's AIFF extractor to `A/player/decoders/aiff/AiffExtractor.kt` and add `A/player/decoders/aiff/KotlinAiffProvider.kt` with `AT/player/decoders/aiff/AiffExtractorTest.kt`; `THIRD-PARTY-NOTICES` entry {default}
  - Do: port from AurielSolaris/Choir `f2e96fd`, `playback/AiffExtractor.kt` (GPL-3.0-or-later, SPDX + attribution
    header); AIFF and AIFF-C NONE/twos/sowt, 8/16/24/32-bit integer → `AUDIO_RAW`; float and compressed AIFF-C refused
    by name.
  - Tests: `UT me.misa198.airmedy.player.decoders.aiff.AiffExtractorTest` (media3-test-utils `FakeExtractorInput` on
    generated byte arrays: sample values, sample rate, channel count, seek; `fl32`/`ima4` refusal strings).

- [ ] T038 [US8] Build `Media3Engine`'s extractors/renderers from the registry and report the provider id in `A/player/media3/Media3PlayerFactory.kt`, `A/player/media3/Media3Engine.kt` with `AI/player/media3/Media3RegistryTest.kt` {default}
  - Tests: `QA-I me.misa198.airmedy.player.media3.Media3RegistryTest` (AIFF fixture plays through `kotlin-aiff`;
    decode error carries the provider id); `GATE`.

- [ ] T039 [US8] Scan gate and skipped-files summary in `A/sync/MediaStoreLibraryScanner.kt`, `A/sync/SkippedFilesSummaryStore.kt`, `A/sync/LibraryScanRunner.kt` with `AT/sync/ScanGateTest.kt` {default}
  - Do: with Media3 selected, after the codec sniff and before any tag read, `registry.resolve(FormatKey(format,
    codec))`; null → skip and count by (format, codec, reason); store `SkippedFilesSummary(scanAt, entries)` in DataStore
    ("Written at the end of each Media3-engine scan; empty with the native engine"). Native: unchanged path. Room
    untouched; `CurrentMetadataSchemaVersion` not bumped (admission changes rows, not parsing — confirm in review).
  - Tests: `UT me.misa198.airmedy.sync.ScanGateTest` (pure admission + aggregation: each file counted once; native →
    empty summary); `GATE`. migration-guard only if Room files appear in the diff (they must not).
  - Accept: session restore drops tracks no longer in the library (existing rule) when switching engines.

- [ ] T040 [US8] Show the skipped-files summary after a scan and in settings in `A/ui/screens/LibraryScanContent.kt`, `A/ui/screens/ScanFilterContent.kt`, `RES/values/strings.xml`, `RES/values-ar/strings.xml` {default}
  - Do: "12 files skipped: unsupported format (DSD)" style, `<plurals>` (Arabic zero/one/two/few/many/other), Western
    digits via `formatDisplay()`.
  - Tests: `ArabicTranslationCompletenessTest`; `GATE`.

- [ ] T041 [US8] [MANUAL] {orchestrator} Corpus scan on `.qa` with Media3: SC-002, SC-016, US8 scenarios 1–5 (adb + logcat provider names); owner listens to AIFF variants

**Checkpoint M4** — status report; owner approval.

---

## Milestone M5 — Phase 7: DSP chain and session limiter (US7) — needs T003, T004; T046 closes only after T003b (a)–(c)

**Goal**: EQ/preamp/width on Media3 match the native filters; limiter on the shared session.
**Independent test**: golden checks green; band-centre tones on device match; SC-015.

- [ ] T042 [US7] Golden generator in `tools/eq-golden/generate.sh`, `tools/eq-golden/harness.cpp.in` producing `androidApp/src/test/resources/golden/eq_golden.json` {default}
  - Do: the script extracts `kEqFrequenciesHz`, the body of `configure_eq` and `filter_sample` from
    `androidApp/src/main/cpp/ffmpeg_player.cpp` by marker (no copy-paste, native file untouched), compiles them with
    the host C++ compiler, and writes coefficients for 44.1/48/88.2/96/192 kHz × gain sets (−12, −3, +3, +12 dB, mixed),
    the magnitude response at 1/12-octave points 20 Hz–20 kHz from impulse responses, and width/preamp cases
    (mid/side formula `ffmpeg_player.cpp:388-393`). The JSON records the source commit and line ranges.
  - Tests: `bash tools/eq-golden/generate.sh --check` (regenerates and diffs against the committed JSON).
  - Accept: generator fails loudly if the markers move.

- [ ] T043 [US7] [TESTS-FIRST] EQ match golden tests in `AT/player/dsp/EqualizerGoldenTest.kt`, `AT/player/dsp/StereoWidthPreampTest.kt`, `AT/player/dsp/DspLinearityTest.kt` {default}
  - Do: coefficients vs golden (relative error ≤ 1e-6, see spec US7 sc4 note on libm rounding); response within
    0.1 dB 20 Hz–20 kHz (target ≤ 0.01 dB); width/preamp vs native formula; changing a band mid-stream never resets
    filter state and the output step stays below a bound (FR-056); linearity: process(A)+process(B) == process(A+B)
    with the time-varying gain before the EQ (FR-051).
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.dsp.EqualizerGoldenTest,me.misa198.airmedy.player.dsp.StereoWidthPreampTest,me.misa198.airmedy.player.dsp.DspLinearityTest'`. migration-guard.

- [ ] T044 [US7] Implement `A/player/dsp/BiquadEqualizer.kt`, `A/player/dsp/EqualizerProcessor.kt`, `A/player/dsp/StereoWidthProcessor.kt`, `A/player/dsp/PreampProcessor.kt` {hard} [HIGH-RISK]
  - Do: pure-Kotlin DSP core + Media3 `AudioProcessor` wrappers (ADR-004 order 2–4; width via
    `ChannelMixingAudioProcessor` matrix); coefficient changes crossfaded over a short block, no state reset; bulk
    float-array processing (no per-sample `ByteBuffer` access); inactive/bypassed when neutral (flat bands skipped as
    native, preamp 0 dB, width 1) — ADR-004 "S3 result".
  - Tests: T043 classes pass without modifying them; `GATE`.

- [ ] T045 [US7] Per-player processor chain and DSP wiring in `A/player/media3/Media3PlayerFactory.kt`, `A/player/media3/Media3Engine.kt`, `A/player/dsp/GainProcessor.kt` (unity pass-through placeholder) with `AI/player/media3/Media3DspTest.kt` {hard} [HIGH-RISK]
  - Do: `buildAudioSink` override: Gain → Width → EQ → Preamp; `setDsp` from `EqualizerPreferences` reaches every live
    player; processing rate per T004's decision.
  - Tests: `QA-I me.misa198.airmedy.player.media3.Media3DspTest` (tee capture: band-centre tones vs golden ±0.1 dB;
    process CPU with the full chain active vs neutral vs no chain over 2 min each, reported; neutral chain within
    +1 CPU point of no chain); `GATE`.

- [ ] T046 [US7] Shared session and limiter in `A/player/media3/LimiterSession.kt`, `A/player/media3/Media3PlayerFactory.kt` with `AT/player/media3/LimiterConfigTest.kt` {hard} [HIGH-RISK]
  - Do: one audio session id per engine lifetime, reused for every player (FR-036); limiter-only DynamicsProcessing
    with every parameter set explicitly from T003/T003b's decision, linked channels, frame duration = sink buffer (FR-052);
    every player verified on the shared session after ready and before play, re-applied if needed, never started
    otherwise (FR-036, ADR-004 "S2 result");
    creation failure → `LimiterState(available = false)` (FR-053); open/close effect-control broadcasts with session id
    and package (FR-054); `OnControlStatusChangeListener` → `LimiterState.controlled` (FR-055).
    Effect-state hedge (ADR-004): the effect is never set `enabled = false`; clip prevention off = neutral limiter
    parameters (ratio 1, threshold 0 dBFS, gains 0 dB); released only with the session. Control loss (or an
    `enabled` change we did not make) = possible mute: check the session is still audible, otherwise release our
    effect, show the FR-053 state, re-create on regain.
  - Tests: `UT me.misa198.airmedy.player.media3.LimiterConfigTest` (pure config builder: all stages but limiter off,
    neutral gains, threshold ≈ −1 dBFS; the "off" config keeps the limiter in the chain with neutral parameters and
    the controller never calls `setEnabled(false)`; control-loss state machine: lost → check → release/keep → regain
    → re-create); `GATE`.
  - Closes only after T003b (a)–(c) pass (recording analysis or T048's manual check).

- [ ] T047 [P] [US7] Limiter state note in the clip-prevention setting in `A/ui/screens/PlaybackSettingsContent.kt`, `RES/values/strings.xml`, `RES/values-ar/strings.xml` {default}
  - Tests: `ArabicTranslationCompletenessTest`; `GATE`.

- [ ] T048 [US7] [MANUAL] {orchestrator} SC-015 (`dumpsys media.audio_flinger`, two clipping tones), SC-011 overshoot, hi-res direct output, EQ app interplay (Wavelet/Poweramp EQ) incl. control loss not muting playback (FR-055 hedge), extreme EQ by ear vs native (owner); T003b (a)/(b) here as a manual check if the dongle recording was not possible

**Checkpoint M5** — status report; owner approval.

---

## Milestone M6 — Phase 8: Tag-based normalization (US6) — needs T005, M5

**Goal**: tagged and untagged files play at one level on Media3, without clipping.
**Independent test**: corpus loudness within ±1 dB of `corpus.tsv` expectations (SC-004).

- [ ] T049 [US6] [TESTS-FIRST] Tag parsing tests in `AT/player/normalization/TagGainSourceTest.kt` {default}
  - Do: Media3 metadata objects built in the test: ID3 `TXXX` REPLAYGAIN_* (any case), Vorbis comments, `R128_*`
    (Q7.8/256, +5 dB), iTunNORM (standard conversion), `com.apple.iTunes:replaygain_*`, RVA2/RVAD/RGAD;
    `REPLAYGAIN_REFERENCE_LOUDNESS` → gain + (−18 − ref); precedence ReplayGain → R128 → Sound Check; malformed,
    missing "dB", +50 dB, NaN/Inf → ignored/treated as untagged.
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.normalization.TagGainSourceTest'`. migration-guard.

- [ ] T050 [P] [US6] [TESTS-FIRST] Gain math and ramp tests in `AT/player/normalization/ItemGainMathTest.kt`, `AT/player/dsp/GainRampTest.kt` {default}
  - Do: `itemGainDb` rules from `contracts/gain-source.md` (disabled → 0; target −14 → +4 dB on every file; untagged
    → 0 + untagged pre-amp; album gain else track gain; clip cap with album peak in album mode; NaN → absent; clamp
    −30…+20 dB); ramp: 100–300 ms, starts from the current actual gain, retarget mid-ramp is continuous, no per-sample
    step above ε, both players retargeted together.
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.normalization.ItemGainMathTest,me.misa198.airmedy.player.dsp.GainRampTest'`. migration-guard.

- [ ] T051 [US6] Implement `A/player/normalization/GainSource.kt`, `A/player/normalization/TagGainSource.kt`, `A/player/normalization/ItemGainMath.kt`; `THIRD-PARTY-NOTICES` entry {hard} [HIGH-RISK]
  - Do: port Rhythm `ReplayGainUtil.kt` (`ef16e7b`, GPL-3.0-or-later, SPDX + attribution) with our rules (ADR-005).
  - Tests: `TagGainSourceTest` and `ItemGainMathTest` pass without modifying them; `GATE`.

- [ ] T052 [US6] Implement gain ramps in `A/player/dsp/GainProcessor.kt` and gain resolution in `A/player/media3/Media3Engine.kt` {hard} [HIGH-RISK]
  - Do: on each player's track format, `TagGainSource` → `itemGainDb` → `GainProcessor` target; `setNormalization`
    retargets the current and prepared player with ramps; Opus header gain per T005's decision: never added by
    `GainProcessor` (the platform decoder applies it once); log the active Opus decoder name.
  - Tests: `GainRampTest` passes without modifying it; `GATE`.

- [ ] T053 [US6] Normalization settings on the engine switch in `A/player/NormalizationPreferences.kt`, `A/ui/screens/PlaybackSettingsContent.kt`, `A/MainActivity.kt`, `RES/values/strings.xml`, `RES/values-ar/strings.xml` {default}
  - Do: `untaggedPreampDb` (default 0); settings enabled only with Media3 selected, disabled with a note on native
    (replaces the `analysisAvailable` gate for the UI; the native analysis gain lookup itself unchanged).
  - Tests: `ArabicTranslationCompletenessTest`; `GATE`.

- [ ] T054 [US6] [MANUAL] {orchestrator} SC-004 loudness measurement over the corpus on `.qa`; owner listens to slider drags, album mode, Opus header gain

**Checkpoint M6** — status report; owner approval.

---

## Milestone M7 — Phase 9: Crossfade on Media3 (US5) — needs T002, T003, T004, M5, M6

**Goal**: dual-player crossfade with our curve and contract.
**Independent test**: US5 scenarios on the corpus vs the native engine (SC-007).

- [ ] T055 [US5] [TESTS-FIRST] Transition state machine tests in `AT/player/media3/TransitionControllerTest.kt` {hard} [HIGH-RISK]
  - Do: Idle → Scheduled → Preparing → Transitioning → Cleanup → Idle; any snap → Cleanup; B not ready at fade start →
    gapless on A; B scheduled only when remaining ≤ fade + pre-buffer (T004) and never with crossfade off (FR-033);
    next during a fade → hard cut to i+2; previous ≤ 3 s into incoming → outgoing restarts, > 3 s → incoming restarts;
    repeat-one → B prepares the same item; a setting change never alters a running fade (FR-032); hooks for the
    future settings exist (FR-031).
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.media3.TransitionControllerTest'`. migration-guard.

- [ ] T056 [P] [US5] [TESTS-FIRST] Fade curve tests in `AT/player/dsp/EqualPowerFadeTest.kt` {hard} [HIGH-RISK]
  - Do: per-frame gain = native formula (`phase = at/total·π/2`, out `cos`, in `sin`); multiplies with normalization
    (FR-035); snap forces incoming 1.0 and outgoing 0 at once; 12 s fade has no step above ε; the outgoing curve can
    start at a frame offset in the past/future (lead compensation, ADR-003 "S1 result"); the same holds for every
    processor fade, including fades on pause/skip: a pause after a fade-out is released only at the fade's audible
    end (fade length + lead), and a snap on pause/seek/skip leaves no frame at the old gain after the flush.
  - Tests: `verify.sh --expect-fail 'me.misa198.airmedy.player.dsp.EqualPowerFadeTest'`. migration-guard.

- [ ] T057 [US5] Implement `A/player/media3/TransitionController.kt` {hard} [HIGH-RISK]
  - Tests: `TransitionControllerTest` passes without modifying it; `GATE`.

- [ ] T058 [US5] Implement the fade in `A/player/dsp/GainProcessor.kt` {hard} [HIGH-RISK]
  - Do: curves keyed to the audible timeline (processed frame − sink lead, 0.46–0.69 s measured in S1) for the
    crossfade and for fades on pause/skip (ADR-003 "S1 result").
  - Tests: `EqualPowerFadeTest` and `GainRampTest` pass without modifying them; `GATE`.

- [ ] T059 [US5] Dual-player crossfade in `A/player/media3/Media3Engine.kt`, `A/player/media3/Media3PlayerFactory.kt` with `AI/player/media3/Media3CrossfadeTest.kt` {hard} [HIGH-RISK]
  - Do: B lifecycle driven by `TransitionController`; shared session id + identical attributes; `beginCrossfade`
    starts B, swaps roles, emits one `TransitionStarted`; `snapCrossfade` stops outgoing and flushes incoming at its
    position; B released after the fade; port of Rhythm `RhythmPlayerEngine`/`TransitionController` mechanics
    (`ef16e7b`, attribution) without its curves, sessions or playlists.
  - Tests: `QA-I me.misa198.airmedy.player.media3.Media3CrossfadeTest` (event once per fade, one player when crossfade
    off, B gone after fade, tee curve check, both curves' audible starts within 50 ms after lead compensation — ADR-003
    "S1 result"; pause and skip during/after a fade: no audible frame at the old gain after the snap, pause lands at
    the fade's audible end); `GATE`.

- [ ] T060 [US5] Crossfade orchestration on the shared path in `A/player/PlaybackCoordinator.kt` with `AT/player/PlaybackCoordinatorCrossfadeMedia3Test.kt` {hard} [HIGH-RISK]
  - Do: next/previous during a fade per FR-031; Mood Radio refill during a fade; artwork event and overlap split for
    Media3 transitions; the media session switches to the incoming track at fade start and sees one player (FR-024);
    T015 stays green unmodified.
  - Tests: `UT me.misa198.airmedy.player.PlaybackCoordinatorCrossfadeMedia3Test`; `GATE`. migration-guard.

- [ ] T061 [US5] [MANUAL] {orchestrator} US5 scenarios 1–8 on `.qa` vs native (SC-007 start within 200 ms), 12 s fade and snaps by ear (owner), SC-015 during a fade

**Checkpoint M7** — status report; owner approval.

---

## Milestone M8 — Phase 10: Regression, resources, polish

- [ ] T062 [MANUAL] {orchestrator} Measurements on `.qa`, two library mixes: SC-003 gapless ≤ 10 ms, SC-006 start latency ≤ +20 %, SC-013 1 h screen-off `batterystats`/`meminfo` (crossfade off 0 %, on ≤ 10 %), SC-014 provider-named failures; results in `specs/001-media3-migration/research/regression-001.md`
- [ ] T063 {orchestrator} Full regression: `GATE`, `./gradlew :androidApp:lintDevDebug` (only known lint classes), every instrumented class on `.qa` one at a time (known failures listed in CLAUDE.md only), SC-001, SC-010 diff check; results in `specs/001-media3-migration/research/regression-001.md`
- [ ] T064 {orchestrator} Docs: `THIRD-PARTY-NOTICES` completeness (Media3, Choir, Rhythm), CLAUDE.md "Playback" section (seam, switch, `.qa`), `quickstart.md`, `HANDOFF.md` (default stays Native until 002 is done; FR-003 flip is a pre-merge task)

**Checkpoint M8** — final status report for 001.

---

## Dependencies

- M1 → everything. T001 blocks all device work and T002–T006. T002 (S1) blocks M7; T003/T004 (S2/S3) block M5
  and M7; T003b (a)–(c) gates T046's completion (owner decision 2026-10-05); T005 (S5) blocks M6. T032 → T032b.
- M2 order: T007 → T008 → T009 → T010 → T011 → T012 → T013 → T014 → (T015 ∥ T016) → T017. No fake-engine test
  before T013 (needs the coordinator).
- M3: T018 before T026/T027; T019 → T020 → T021 → T034; T023 → T024 → T025; T026 → T027 → T028 → T029 → T030
  (all edit the coordinator/service, so sequential); T031, T022 parallel; T032 after T026; T033 after T013.
- M4: T036 → T037 → T038; T036 + T025 → T039 → T040 → T041.
- M5: T042 → T043 → T044 → T045 → T046 → T047 → T048.
- M6: T049 ∥ T050 → T051 → T052 → T053 → T054. T052 needs T045 (chain exists).
- M7: T055 ∥ T056 → T057 → T058 → T059 → T060 → T061.
- User stories: US9 → US1 → (US2, US3, US4) → US8 → US7 → US6 → US5. US5 is last because it needs the chain (US7)
  and the gain processor (US6) for per-sample fades before the EQ.

## Parallel examples

- M1: T003, T004, T005 harnesses can be briefed together after T001 (separate files); device runs one at a time.
- M2: T015 and T016 (separate test files on the same fakes).
- M3: T022 and T031 next to the service chain.
- M6: T049 and T050.
- M7: T055 and T056.

## Implementation strategy

MVP = M1 + M2 (US9, US1): the seam with zero behaviour change and the safety net of characterization tests. Each
later milestone is an independently testable increment on the Media3 path behind the switch; the default stays
Native, and nothing merges into `main` until 001 and 002 are done (FR-003).
