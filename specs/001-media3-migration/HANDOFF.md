# HANDOFF — 001-media3-migration

**Current branch:** `feature/media3-migration` (check this out at session start)
**Feature directory:** `specs/001-media3-migration/`
**Phase:** 0 — Discovery + Graphify baseline — **DONE** (2026-09-30)

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

1. Additive migration: Media3 + Jellyfin `media3-ffmpeg-decoder` next to the native player; native
   player and FFmpeg build stay and remain selectable. Deleting the native player is not a migration task.
2. Normalization stage 1 = tag-based gain (port Rhythm's ReplayGainAudioProcessor/ReplayGainUtil; handle
   R128_* for Opus, avoid double Opus output gain). Stage 2 (on-device analyzer) is a later feature.
   The analysis read side is not touched. The constitution's false "scanner stores loudness" premise is corrected.
3. Mood Radio revival is out of scope (returns with the analyzer); its machinery must keep working on Media3.
4. Crossfade = port Rhythm's dual-ExoPlayer A/B technique; the plan spike validates it, doesn't redesign it.
5. Equalizer added to no-feature-loss: `audiofx.Equalizer` on the audio session; preamp/width to be planned.
6. AIFF/APE/WavPack/DSD/WMA deferred; they keep playing through the native player meanwhile.
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

Native-routed path decided (owner, 2026-10-01): FR-049 = no limiter there; tagged peak ≤ 0 dBFS, else
total gain capped at 0 dB. FR-046b = ramp emulated from Kotlin (~10 steps / ~200 ms); instant step
accepted if audibly steppy. APEv2 / DSF/DFF / ASF gain tags out of scope (treated as untagged). All
temporary, tied to the deferred-formats decision. Checklist: 16/16.

## Exact next step

`/speckit-plan` for `specs/001-media3-migration`.
