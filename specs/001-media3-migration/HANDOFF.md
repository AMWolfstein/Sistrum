# HANDOFF — 001-media3-migration

**Current branch:** `feature/media3-migration` (check this out at session start)
**Feature directory:** `specs/001-media3-migration/`
**Phase:** 3 — Tasks (generated and analyzed 2026-10-05, uncommitted; awaiting owner review)
**Last commit:** docs(playback): add migration plan, ADRs and research (plan-phase commit; the WaxFlow
vendoring and its revert were squashed away, WaxFlow is pinned as an oracle, not vendored)

## Spec Kit feature directory

- Name: `001-media3-migration` (sequential numbering from `.specify/init-options.json`; first feature → `001`).
- `/speckit-specify` builds its directory name itself (next free number + short name), so a plain run would
  create `002-…`. To reuse this folder, run it with
  **`SPECIFY_FEATURE_DIRECTORY=specs/001-media3-migration`**, short name `media3-migration`
  (the script equivalent is `create-new-feature.sh --number 1 --short-name media3-migration --allow-existing-branch`).
- `.specify/feature.json` is git-ignored (machine-local), so it was not created in this phase. `/speckit-specify` writes it.

## Done

- `research/discovery.md`: architecture + real dependency chain, answers to all 8 stop-condition questions,
  format inventory, broken-feature analysis, MUST/MAY/DO NOT READ lists, risks R1–R12, migration boundaries.
- `research/graph-baseline.md`: Graphify baseline for the playback path.
- No code changes, no delegation, no builds or tests (none were needed for a read-only phase).

## Owner decisions (2026-10-01) — recorded in `.specify/memory/constitution.md`

1. *(Superseded 2026-10-05, see below.)* Additive migration: Media3 + an FFmpeg decoder extension next to the native player; native
   player and FFmpeg build stay and remain selectable. Deleting the native player is not a migration task.
2. Normalization stage 1 = tag-based gain (port Rhythm's ReplayGainAudioProcessor/ReplayGainUtil; handle
   R128_* for Opus, avoid double Opus output gain). Stage 2 (on-device analyzer) is a later feature.
   The analysis read side is not touched. The constitution's false "scanner stores loudness" premise is corrected.
3. Mood Radio revival is out of scope (returns with the analyzer); its machinery must keep working on Media3.
4. Crossfade = port Rhythm's dual-ExoPlayer A/B technique; the plan spike validates it, doesn't redesign it.
5. Equalizer added to no-feature-loss: `audiofx.Equalizer` on the audio session; preamp/width to be planned.
6. *(Superseded 2026-10-05.)* AIFF/APE/WavPack/DSD/WMA deferred; they keep playing through the native player meanwhile.
7. Crossfade/EQ/normalization may be inactive on the Media3 path on this branch; nothing merges until restored.
8. References: cromaguy/Rhythm (primary), PixelPlayerOSS (GPL-3.0, porting with attribution).
9. Test device: CPH2307.

## Owner decisions, round 2 (2026-10-01) — in the constitution

- Normalization: every tag applied against −18 LUFS; R128 +5 dB; target LUFS = global pre-amp vs −18;
  untagged = unity + "untagged pre-amp"; clip prevention on both paths.
- Engine switch: hidden developer setting, native default on this branch, applies at next playback start,
  flips to Media3 before merge.
- Native↔Media3 boundary = hard cut (temporary known limitation).
- Test builds: separate applicationId suffix (not `.test`, which collides with the instrumentation APK;
  e.g. `.qa`). Corpus in `/sdcard/Music/SistrumTestCorpus`, no `.nomedia`.

## Still open

- APE and DSD test files: ffmpeg can't encode them and no `mac` (Monkey's Audio) encoder is installed here.
  Need from the owner: either install Monkey's Audio `mac` + a DSD encoder (e.g. sox-dsd / a DSF writer),
  or supply a few real APE/DSF/DFF samples (ideally with and without ReplayGain tags).
  WavPack (`wavpack`), WMA (`wmav2`), AIFF, ALAC, FLAC can be generated locally.

- Library inventory DONE 2026-10-01 on CPH2307 (MediaStore, scanner selection; app folder filter not applied):
  729 tracks = Opus 702 (all with R128_TRACK_GAIN), AIFF 5 (PCM 16/24-bit), FLAC 5, WAV 5 (PCM 16/24-bit),
  M4A 8 (AAC 4, ALAC 4), MP3 4. Reported only; no decisions taken from it.
- Deferred-format decision (AIFF etc.).
- Lock-screen "Unknown artist" root cause (MediaSession task).
- No third-party notices file exists yet; the first dependency task creates it.

## Phase 1 — Specify: DONE (2026-10-01)

- `spec.md` written (9 user stories, FR-001…FR-074, SC-001…SC-012); clarifications Q1 (engine fallback,
  option C) and Q2 (clip prevention: tagged peaks + end-of-chain limiter) resolved; checklist all pass.
- Aside (not part of the spec, unfinished): an investigation of normalization was started and interrupted.
  Established so far: the normalization settings are greyed out on the CPH2307 because
  `analysisAvailable` is false (`PlaybackSettingsContent.kt:165`, `MainActivity.kt:199`); loudness data only
  ever came from the desktop sync (`manifest.analysis` → `insertDocuments`, removed in `7ca9aaa`); no
  on-device analyzer ever existed. The device tag-count scan was NOT RUN (cancelled by the owner).

## Phase 1b — Clarify: DONE (2026-10-01)

5 questions asked and answered (spec `## Clarifications`, Session 2026-10-01):
1. Next during a fade = hard cut to the track after the incoming one; Previous = normal 3 s rule on the
   incoming track. Keep a future "crossfade on skip" setting possible.
2. Album mode = album gain whenever present, else track gain (no adjacency rule). Future note only: a
   source-based Auto mode.
3. Normalization settings enabled only with the new engine selected (note on the current engine);
   native-routed tracks get the same Kotlin-computed tag gain via the existing native gain parameter.
4. Resources: 0 % extra with crossfade off (no second player), ≤10 % with it on; batterystats CPU and
   wakelock time, meminfo for memory.
5. Live setting changes: immediate 100–300 ms ramp from the actual current gain; limiter always on.

*(Superseded 2026-10-05: no native-routed path.)* Native-routed path decided (owner, 2026-10-01): FR-049 = no limiter there; tagged peak ≤ 0 dBFS, else
total gain capped at 0 dB. FR-046b = ramp emulated from Kotlin (~10 steps / ~200 ms); instant step
accepted if audibly steppy. APEv2 / DSF/DFF / ASF gain tags out of scope (treated as untagged). All
temporary, tied to the deferred-formats decision. Checklist: 16/16.

## Plan revision (owner, 2026-10-05) — constitution and spec updated

- Three features: **001** Media3 + Decoder Registry (platform + Kotlin AIFF from Choir); **002**
  Kotlin ports of WaxFlow's decoders (+ Flick DSD), WaxFlow as oracle (`specs/002-kotlin-decoders/NOTES.md`);
  **003** remove native player + FFmpeg build, then the NDK (`specs/003-native-removal/NOTES.md`).
  Nothing merges into `main` until 001 and 002 are done.
- No Jellyfin/FFmpeg decoder in Media3; no per-track routing to native (FR-046b/048/049/061/062a removed).
- WaxFlow is NOT vendored: the owner's fork github.com/AMWolfstein/WaxFlow is pinned at `446ca31` as port
  source and test oracle (`docs/waxflow/ORACLE.md`, `scripts/waxflow-oracle.sh`).
- Shared audio session for A/B; EQ, preamp and width in-app per player with the native filters (exact match);
  DynamicsProcessing on the session for the limiter only; equal-power fade.
- Engine-dependent scan filter; SC-011 relaxed; no MPD 20 s rule (future setting); 002 adds Musepack, ADPCM,
  G.711; a full format inventory gates 003.
- Research reports moved into `research/` (index: `research/README.md`).

## Pre-v1.0 review phase

`docs/review/2026-10-code-review.md` (static review, 2026-10, unverified claims). Decision: no code fixes
from it until all three features are done; a full review-and-fix phase runs before v1.0. Part 2 findings
for the playback path are already 001 requirements (FR-080…FR-092). Known bug: PlaybackQueue keeps only
1000 tracks and clamps the start index (Part 3 blocker); characterization tests must not assert it.

## Phase 2 — Plan: DONE (2026-10-05)

- `plan.md`, `research.md` (D1–D11 + spike table), `adr/ADR-001…007`, `contracts/` (player-engine,
  decoder-registry, gain-source), `data-model.md`, `quickstart.md`.
- Parts F and B done: `specs/002-kotlin-decoders/NOTES.md`, `specs/003-native-removal/NOTES.md`,
  `research/analyzer-future.md`, `future-settings.md`, `home-mixes.md`.
- Device facts: DynamicsProcessing present on the CPH2307; no ALAC decoder.
- Spikes S1–S3, S5 BLOCKED: no `.qa` test-build application ID yet (CLAUDE.md rule: never the daily app).

Owner decisions at STOP 2: processor fade; framework MediaSession in 001 (Media3 session = own feature after
001 + 002); FR-080…092 in the shared service incl. native failure cases; honour `REPLAYGAIN_REFERENCE_LOUDNESS`;
ADR-003/004/005 pending device spikes, which are the first tasks after the `.qa` build. See `plan.md`.

## Phase 3 — Tasks: generated + analyzed (2026-10-05), NOT committed

- `tasks.md`: 64 tasks in milestones M1–M8 (T001 `.qa` build, T002–T005 spikes S1/S2/S3/S5, T006 corpus first).
- `/speckit-analyze` fixes applied: spec FR-080…092 preamble (shared path), US7 sc4 (float-rounding tolerance,
  steady-state match), data-model `ItemGain`, ADR-004 order note, plan phase 3, contract gain resolution (Media3
  resolves its own gain; `setNormalization`).
- Owner approvals (2026-10-05): ADR-001 amendment (`PlaybackCoordinator`, pure move, T017 confirms);
  `verify.sh --expect-fail` (each expected-fail test must fail on an assertion about the missing behaviour, not a
  crash/exception; verify.sh checks the failure type; commit message lists the classes; the next task turns them
  green unmodified); US7 sc4 tolerance + "no click on EQ change" recorded as an intentional deviation.
- Owner request: report review rounds per task in every status report.

## Exact next step

M1: `/delegate-task T001` (`.qa` build).
