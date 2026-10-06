# HANDOFF — 001-media3-migration

**Current branch:** `feature/media3-migration` (check this out at session start)
**Feature directory:** `specs/001-media3-migration/`
**Phase:** M2 — Characterization and the engine seam (M1 approved by the owner 2026-10-05; T003b open, gates T046 only)
**Last commit:** see `git log` (M1 closed with "docs(playback): record M1 owner decisions")

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

- *(Closed 2026-10-05, owner: no real APE/DSD samples. 002 validates APE against the WaxFlow oracle on WaxFlow's
  testdata and DSD on synthetic DSF/DFF with Flick's `dsd_engine` as oracle; the corpus has no APE/DSD rows.
  Real-world problems are fixed from user reports via the exportable decode-failure log, FR-064a / T032b.)*

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

## M1 — DONE, approved by the owner 2026-10-05 (T003b open: gates T046 only)

| Task | Model | Brief | Runs / review rounds | Status |
|---|---|---|---|---|
| T001 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T001/brief-1.md` (session `ses_ef3c61449ffec4zSY8gEWw8APM`) | run-1 failed before dispatch (OpenCode postinstall missing; fixed with the owner's OK, 1.18.34); run-2 accepted. **Review rounds: 0** | DONE (gate PASS); SC-009 confirmed by the owner 2026-10-05 |
| T002 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T002/brief-1.md`, `brief-2.md` (session `ses_ef3bcdfc7ffe2zlMyWtL8UFIul`) | run-1: device test failed (player built off its looper thread); round 1 (fix + outgoing-lead measurement) accepted. **Review rounds: 1** | DONE: S1 PASS, processor fade kept (ADR-003); snaps not checked by ear |
| T003 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T003/brief-1..4.md` (session `ses_ef3919481ffeGH6Cbfh1VVnSH3`) | **Review rounds: 3 (limit reached)** + one orchestrator one-line fix (Visualizer → output mix, session 0). See "T003 blocked" below | DONE as **PARTIAL** (owner option A, 2026-10-05); remainder → T003b (orchestrator / small steps), blocks M5 |
| T004 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T004/brief-1.md` | run-1 accepted, gate PASS; 80-min measurement done (session `ses_ef30d95eaffeOhRK0atiEF7J3p`). **Review rounds: 0** | DONE: S3 PASS (ADR-004 "S3 result") |
| T005 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T005/brief-1.md` | run-1 accepted (session `ses_ef306333affek0as11Gvi0fYpX`); fixtures independently checked with ffmpeg (−26/−20/−32 dB = ±6 dB); device run PASS (after the owner approved a Play Protect prompt for the QA install). **Review rounds: 0** | DONE: S5 PASS (ADR-005) |
| T006 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T006/brief-1.md` | run-1 accepted (session `ses_ef2ffc3fdffec6cR0qadqLACm8`); regenerated + verified by the orchestrator; gains cross-checked vs ffmpeg ebur128 (5 forms exact); 47 files pushed to `/sdcard/Music/SistrumTestCorpus`, 46 indexed by MediaStore (`.wv` not indexed — 002 NOTES). **Review rounds: 0** | DONE |
| T003b | orchestrator (no delegation) | — | steps 1–3 below | **STOPPED** (dumpsys can't measure); owner split it 2026-10-05: (c) listening check, (a)/(b) dongle recording or MANUAL in T048 |

T001 notes: AGP 9.4.1 `testBuildType = "qa"` also removed `testDevDebugUnitTest`; the coder re-enabled host tests for
`debug` with `androidComponents.beforeVariants { enableUnitTest = true }` (stated in its report; verified: 378 devDebug
unit tests ran, 0 failures). Device has a pre-existing `me.misa198.airmedy.dev.test` package from before this session;
left alone (no uninstalls). T001 orchestrator part: CLAUDE.md adb section, verify.sh (QA APKs; allowlist: only `.qa` / `.qa.test`
may be installed), tasks.md T001 file list (manifest overlay for the label).

## T003 (2026-10-05) — S2 partially answered; owner chose option A

Confirmed on the CPH2307 (`.qa`, Media3 1.11.1):
- **Shared session works only if re-applied after the player is ready.** `ExoPlayer.setAudioSessionId` is async and the
  player's own initial session can overwrite it (rounds 1–2: tracks on 4873/4881, DP/Visualizer orphaned on 4865).
  With `ensureSharedSession` (re-set + poll `player.audioSessionId` after STATE_READY, before play) both players and
  their tracks use the shared session. → Requirement for T046/T059 (and the T002 rig has the same race; S1 results
  don't depend on it).
- **The session's effect chain runs on the MIXER music thread** (`AudioOut_1D`) during the two-player overlap, and a
  24-bit/96 kHz WAV also stays on that mixer thread (no direct output on this device).
- DP limiter-only config is accepted; frame duration used 4 ms (`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`/rate).

Not established:
- **Limiter output level / overshoot (SC-011) and "no reduction below threshold".** Visualizer is unusable here: on
  the shared session it is inserted *first* (measures the DP input: quiet sum −3.4 dBFS, loud sum +2.6 dBFS); on the
  output mix it reads near-silence (RMS ≈ −95 dB) whenever the DP is disabled and −50…−60 dB peaks otherwise.
- Open question raised by that anomaly: does disabling the DP mute the session? (Relevant to FR-053/055.)

Withdrawn: earlier S2 readings ("DP on mixer with both players", "limiter acts pre-volume").

Options for the owner:
- **A (recommended)**: accept S2 as partial now (session + routing PASS), continue M1 (T004, T005, T006); measure the
  limiter later with a new short task that reads AudioFlinger's per-stream "Signal power history" from
  `dumpsys media.audio_flinger` (post-mix HAL power, no Visualizer, no permission), plus the DP-disabled mute check.
- B: same new task now, before T004.
- C: owner listening check of limiter on/off (deferred list).

## T003b (2026-10-05) — stopped: dumpsys power history cannot measure the limiter

Done by the orchestrator, no code changes (host-side parsing of `dumpsys media.audio_flinger`; the S2 test unchanged).
- Step 1: the speaker thread (`AudioOut_1D`, 24-bit HAL, `AUDIO_DEVICE_OUT_SPEAKER`) has a post-mix "Signal power
  history" at 1000 ms and 50 ms resolution. The DP descriptor says **"volume mgmt: implements control"** (AudioFlinger
  hands the stream volume to the effect, so the limiter sees the pre-volume full-scale sum). A vendor **Dolby DAP**
  effect is enabled on the output mix (session 0) after our session chain; the power history is measured after it.
- Step 2: one dump after a run: the 50 ms ring holds only ~100 samples; the 1000 ms ring has one entry per DP-on
  phase and none for DP-off phases.
- Step 3: polled dumps (~0.2 s apart, 200 dumps) during a run: still only 8 samples of 50 ms power inside the 3 s
  `singleOn` window; the log records short bursts per stream start, not continuous power. **(a) and (b) cannot be
  measured this way.**
- Consistent observation (3 independent sources: output-mix Visualizer, 1000 ms ring, polled 50 ms ring): **no output
  power is ever logged in a DP-disabled phase, although our track is listed active** (`singleOff`). Strong hint, not
  proof, that disabling the DP mutes the session on this device (plausible because it implements volume control).

Design hedge that does not depend on the measurement (proposed, for the owner): never use `DynamicsProcessing.enabled
= false` as "limiter off"; keep the effect enabled and make it neutral (limiter stage `inUse`/`enabled` false or
threshold 0 dB, ratio 1), and treat losing control of the effect (FR-055) as a possible mute to detect and handle.

### Owner decisions at M1 approval (2026-10-05)

1. Effect-state hedge approved → ADR-004 "Decision — effect-state hedge", FR-053/FR-055, T046: never
   `DynamicsProcessing.enabled = false` for "off"; neutral limiter instead; control loss = possible mute, detected and
   handled.
2. Measurement split: (c) owner listening check (steps below); (a)+(b) dongle recording analysed by Claude (procedure
   below), else `[MANUAL]` in T048. M5 may start; **T046 cannot close until (a)–(c) pass.** No max-volume headphone tests.
3. Fade lead (0.46–0.69 s) compensated for every processor fade incl. pause/skip → ADR-003 "S1 result", T056/T058/T059.
4. 002 NOTES: top-level design item "scan path for formats the device doesn't know" (MediaStore skips `.wv` here).
5. Owner blocklists `/sdcard/Music/SistrumTestCorpus` in the daily app (owner, 2026-10-05).
6. No APE/DSD samples: corpus rows removed; 002 validation via the WaxFlow oracle (APE) and synthetic DSF/DFF + Flick
   `dsd_engine` (DSD); exportable decode-failure log added (spec FR-064a, SC-014; task T032b; 002 NOTES).

### T003b (c) — listening check (owner, ~1 minute, phone speaker, no headphones)

1. Phone on USB as usual. Turn **Dolby Atmos off** (Settings → Sound & vibration → Dolby Atmos). Media volume at your
   normal listening level. Nothing plugged into the headphone/USB-C audio path.
2. On the laptop (the `.qa` build and its test APK are already installed):
   ```
   adb shell svc power stayon usb
   adb shell am instrument -w -e class me.misa198.airmedy.spikes.SharedSessionLimiterSpikeTest me.misa198.airmedy.dev.qa.test/androidx.test.runner.AndroidJUnitRunner
   ```
3. What plays (about 40 s, all generated test tones, each block ~4 s with a short gap between blocks):
   - ~5 s silence;
   - **block 1**: 1 kHz tone, limiter effect **on**;
   - **block 2**: the same 1 kHz tone, effect **disabled** ← the question;
   - blocks 3/4: a soft two-tone chord, effect on / disabled;
   - blocks 5/6: a louder two-tone chord, effect on / disabled;
   - block 7: a short tone (hi-res file), effect on.
4. Tell Claude: is block 2 **as loud as block 1 / quieter / silent**? Same question for blocks 4 and 6 vs 3 and 5.
   (Optional, if you have a moment: the same run with Dolby Atmos on — earlier automated runs had Dolby on.)

### T003b (a)+(b) — recording procedure (only if you have the hardware; otherwise say so → T048 MANUAL)

Hardware: a USB-C audio dongle (USB-C → 3.5 mm) on the phone, and a cable from the dongle to the laptop's 3.5 mm
jack. This laptop (`HD-Audio Generic`, ALC236, card 1) has a **combo headset jack (mono mic input)**: use a stereo-to-
TRRS cable wired for mic, or a headset splitter (mic plug) — a plain stereo TRS-TRS cable is detected as headphones and
records nothing. **No headphones anywhere**; the phone's output only goes into the laptop.

1. Wireless adb (the dongle takes the phone's only USB-C port). While still on USB:
   `adb tcpip 5555`, then `adb shell ip -f inet addr show wlan0` (note the IP), unplug, plug in the dongle,
   `adb connect <IP>:5555`, `adb devices` shows one device. (adb stays open on the network until you run `adb usb`
   or reboot — do that afterwards.)
2. Phone: Dolby Atmos off; note the dongle model.
3. Laptop input, no boost, no processing:
   `amixer -c 1 sset 'Mic Boost' 0` and `amixer -c 1 sset 'Capture' 40%`.
4. Level check (one run, nothing saved): start the meter
   `arecord -D hw:1,0 -f S16_LE -r 48000 -c 2 -V stereo /dev/null`
   (if it says "busy", use `-D default`), run the instrumentation command from (c) step 2 with `adb -s <IP>:5555 shell …`,
   and watch block 1. Adjust the **phone's media volume** (and `Capture` only if needed) until block 1 peaks around
   **−12 dB** (meter about 25 %) and nothing ever hits 100 %. Ctrl-C the meter. Note the phone volume step.
5. Two recordings, ~75 s each, files in `~/sistrum-limiter/`:
   ```
   mkdir -p ~/sistrum-limiter && cd ~/sistrum-limiter
   adb logcat -c; adb logcat -v epoch -s S2 > run1.log &
   arecord -D hw:1,0 -f S16_LE -r 48000 -c 2 -d 75 run1.wav &
   sleep 2; adb shell am instrument -w -e class me.misa198.airmedy.spikes.SharedSessionLimiterSpikeTest me.misa198.airmedy.dev.qa.test/androidx.test.runner.AndroidJUnitRunner > run1-instr.txt
   wait; kill %1
   ```
   Run 1 at the volume from step 4; run 2 (`run2.*`) at the highest volume step where the meter still stayed below
   100 % in step 4 (try 2–3 steps up; skip run 2 if it clips).
6. Send Claude: the folder path (`~/sistrum-limiter/`: `run*.wav`, `run*.log`, `run*-instr.txt`), the phone volume
   step for each run, the dongle model, and whether Dolby was off. Afterwards: `adb usb` (or reboot).

What Claude will measure: calibration from block 1 (known −3 dBFS tone) → phone-dBFS per block; (a) block 5's peak vs
the unlimited sum (+3 dBFS expected, ≈ −1 dBFS when limited) and flat-topping/distortion in block 6; (b) block 3 vs
block 4 and vs the calibrated expectation (within 0.1 dB, SC-011); (c) block 2 vs block 1 (recording answers it too,
so the listening check becomes a cross-check).

## Deferred owner checks (owner prefers minimal live testing, 2026-10-05)

Do these when convenient; Claude never marks them PASS.
- [x] Blocklist `/sdcard/Music/SistrumTestCorpus` in the daily app (owner, 2026-10-05).
- [ ] T003b (c) listening check (steps above).
- [ ] T003b (a)+(b) dongle recording (procedure above), or tell Claude it isn't possible → T048 MANUAL.
- [ ] S1 snaps by ear (T002): rerun with `-e snapAt 0.3 -e snapKind pause|seek`, `-e snapAt 0.7 -e snapKind next`
  (commands in ADR-003 "S1 result" / tasks T002).

## M2 — DONE, approved by the owner 2026-10-06 (T017 ok)

| Task | Model | Brief | Runs / review rounds | Status |
|---|---|---|---|---|
| T007 | orchestrator (tooling, no delegation) | — | 5 acceptance cases with throwaway probe classes (assertion → PASS; `IllegalStateException`, `TODO()`, passing → FAIL; assertion class + other failing classes → FAIL) + missing class → FAIL + full-suite path PASS; probes removed. **Review rounds: n/a** | DONE |
| T008 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T008/brief-1.md` (session `ses_ef293bccafferManCrqv1f4aqD`) | run-1 accepted; 36 tests; migration-guard PASS; gate PASS (full suites, build). **Review rounds: 0** | DONE |
| T009 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T009/brief-1.md` (session `ses_ef2917293ffeKez148R4f8hPKD`) | run-1 accepted; signatures match `contracts/player-engine.md`; gate PASS. **Review rounds: 0** | DONE |
| T010 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T010/brief-1.md`, `brief-2.md` (session `ses_ef27e7545ffevjrOgyBMR1N1jq`) | run-1: migration-guard NEEDS CHANGES (4 weak test cases + engine bug: buffered events dropped after a failed prepare); round 1 fixed all; migration-guard PASS; gate PASS (437 tests). **Review rounds: 1** | DONE |
| T011 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T011/brief-1.md` (session `ses_ef2715228ffeh9OyRSlYEVslh3`) | run-1 accepted after line-by-line review; gate PASS (unit, build, lint 71 = MissingTranslation only); only `LegacyNativeEngine` depends on `FfmpegDecoder`. Contract refinement: `pollEvents()` on `PlayerEngine` (contracts/player-engine.md). Reviewed differences, none regressions: `restoreCurrent` no longer leaks a decoder on failure nor exposes a closed decoder to `onDestroy`'s `currentSession()`; the end of track is read at the tick's poll (an end in the few ms between poll and the chain is handled one tick later, ≤200 ms); log text interpolates the EngineEvent. **Review rounds: 0** | DONE |
| T012 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T012/brief-1.md` (session `ses_ef260cda0ffePAEyWNB6TVdfz4`) | run-1 accepted; every moved function diffed against HEAD with the brief's substitutions applied: only the listed substitutions differ. Deviations reviewed: `tick(refillMoodRadio)` lambda keeps Mood Radio in the service (T013) at the same position; `clearRestoredSession` publishes the empty queue through the port (one extra library read); now-playing log prints the TransportState name; collectors start after the MediaSession setup but still before restoreJob. No android imports in coordinator/ports; gate PASS. **Review rounds: 0** | DONE |
| T013 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T013/brief-1.md` (session `ses_ef24da0c3ffe9tYahe3ugMjLtf`) | run-1 accepted; dispatch, Mood Radio start/refill/stop, focus pause/restore, command lock and restore gate moved verbatim into the coordinator; substitutions only: `PlaybackService.ActionX` qualified names, `store.*` → `LibraryPort.libraryAnalysisEnabled()/moodRadioTracks()`, `Log.d(tag, …)` → `log.d(…)`, flows via `PlaybackFlows`; `tick()` lost its lambda. Focus action constants now `internal`. Service 483 lines (target ≈450; rest is adapters/notification glue). No android imports in coordinator/ports; gate PASS. **Review rounds: 0** | DONE |
| T014 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T014/brief-1.md`, `brief-2.md` (session `ses_ef24293edffelHlT6YUM7LjCsQ`) | 27 tests in `PlaybackCoordinatorQueueCharacterizationTest`; reusable `FakePlayerEngine`/`FakeEngineFactory`, fake ports and `PlaybackCoordinatorHarness` in `AT/player/fakes/`. migration-guard NEEDS CHANGES round 1: repeat-One and reorder-preload tests could not detect a regression, several loose matchers, fake `beginCrossfade` ignored the contract's no-preload/fade-running no-op; all fixed in run-2 and checked. Observed: `ActionShuffle` reshuffles and selects the first item (only `ActionSetShuffle` keeps the current track). No production change; gate PASS. **Review rounds: 1** | DONE |
| T015 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T015/brief-1.md`, `brief-2.md` (session `ses_ef237b962ffe1Una7gG3cmCpB8`) | 19 tests in `PlaybackCoordinatorCrossfadeCharacterizationTest`; fake engine gains opt-in `emitTransitionOnCrossfade` (successful `beginCrossfade` queues `TransitionStarted` and retires the preloaded slot) and a `crossfades` record. migration-guard NEEDS CHANGES round 1: manual next/select tests never ticked, effective-duration fixture coincided with the raw setting, loose `Failed` matcher; fixed in run-2 and checked. Note: the coordinator does not call `snapCrossfade` on pause/seek (native engine handles it); tests do not pin either way. No production change; gate PASS. **Review rounds: 1** | DONE |
| T016 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T016/brief-1.md`, `brief-2.md` (session `ses_ef228d16cffeUcLVy8wfLdrMnV`) | 24 tests in `PlaybackCoordinatorSessionCharacterizationTest` (restore, restore gate, focus/duck, output recovery, Last.fm, listening finish reasons); harness gains `markRestoredOnInit`, `restore()`, `advance()`; factory `failPrepare`. migration-guard NEEDS CHANGES round 1: three tests pinned spec-contradicting native defects (duck reset on new track, wrong-track restore, Last.fm restart on recovery) — removed/neutralized, defects recorded below; no-split recovery test strengthened; exact matchers. No production change; gate PASS. **Review rounds: 1** | DONE |

### Notes for T011 (switching PlaybackService to the engine)

- `LegacyNativeEngine.pollEvents()` delivers events synchronously (its `events` flow is empty). It drains
  pending native transitions before prepare/preloadNext/clearPreloaded/beginCrossfade (the native slot is
  single and overwritable). Call it wherever the service calls `consumeNativeTransition()` today.
- `Ended` / `OutputDisconnected` fire once per false→true edge (re-armed by prepare). Today the ticker re-checks
  `isFinished()` every tick but only acts while Playing: the service must not drop an `Ended` it receives in
  another state (keep it until Playing, or handle it), or behaviour changes.
- `preloadNext` rethrows preload exceptions; today's service wraps the call in `runCatching` (keep that at the
  call site). The engine does not call `clearPreloaded()` inside `preloadNext`; the service still does first.
- Open design point: add `pollEvents()` to the `PlayerEngine` interface (uniform synchronous drain for Media3
  too) vs. a flow. Decide in T011/T013; contract amendment needed either way.

### Existing coordinator defects found by T016 review (not pinned by tests; fix on the shared path)

- **Duck lost on a new track** (FR-022/FR-035): `playCurrent` calls `restoreFocusGain()` before creating the engine, so a
  manual next while another app ducks us plays at full volume. Natural fix point: the focus-duck factor of T026/T027.
- **Restore picks the wrong track** (FR-013): `queueForAvailableTracks` filters ids but keeps `currentIndex`, so a missing
  track before the current one makes restore select a different track (and apply the saved position to it).
- **Last.fm re-armed on output recovery** (FR-015, US1 sc6): `recoverAfterOutputDisconnect` → `playCurrent` calls
  `scrobble.startPlayback` again, which resets the scrobble tracker; a route change after the threshold can scrobble twice.
- Owner decision 2026-10-06: duck defect → T026/T027 (placed in T027, focus); restore defect → T030 (session/restore,
  `PlaybackModels.kt`); Last.fm defect → T027 (output recovery). Each gets a test first: `PlaybackCoordinatorDefectTest`
  in T018 (`--expect-fail`), fixed "without modifying them".

### T017 — US1 parity pass (MANUAL, owner) — prepared 2026-10-06

Automated part, done: `git diff main -- '**/PlaybackQueue.kt' '**/ListeningTracker.kt'` is empty (SC-010); full unit suites
green unmodified at every M2 commit (SC-001, US1 sc3; androidApp 507 + sharedLogic 35 tests at `c4ee2b2`).
`.qa` build of `c4ee2b2` installed on the CPH2307 ("Sistrum QA", `me.misa198.airmedy.dev.qa`). Baseline = the daily app
(pre-migration code). Use normal volume; nothing here needs max volume. Tick each item or note the difference:

1. [ ] Album gapless, crossfade OFF (Settings → Playback): play a gapless album across 2–3 track joins — no gap/click,
       same as the daily app. Now-playing, queue highlight and lock screen switch to the next track at the join.
2. [ ] Crossfade 6 s: let a track end — fade starts ~6 s before the end, sounds like the daily app; artwork blends and the
       title/queue switch at the START of the fade. Skip/pause during a fade: no stuck double audio.
3. [ ] EQ on (any preset with obvious bass change): audible, same as the daily app; toggling off restores.
4. [ ] Focus: start a video in another app → Sistrum QA pauses; a notification sound/navigation prompt → ducks briefly and
       comes back; a phone call (or voice note) → pauses and resumes after.
5. [ ] Lock screen / notification: title, artwork, play/pause/next/previous work; ("Unknown artist" is a known issue, US4).
6. [ ] Statistics: after listening > half of a track, it shows in Stats in the QA app as in the daily app.
7. [ ] Last.fm (only if you log in to Last.fm in the QA app): now-playing appears and the scrobble arrives once.
8. [ ] Lyrics sync: synced lyrics follow the song as smoothly as in the daily app.
Known, not regressions: Volume normalization and Mood Radio are broken on the native engine (fixed on Media3).
Reply with the ticks, or "T017 ok" if all match; any difference → describe it and I will investigate before M3.

## M3 — in progress (started 2026-10-06)

Contract refinements decided at T018 (no ADR change; same kind as the T011 `pollEvents` refinement):
- `EngineEvent.OutputStarted` (FR-090): the coordinator reports Playing only after it (contracts/player-engine.md).
- `PlaybackCoordinator.selectQueueItem(index): Job` (FR-091): the media session's skip-to-queue-item goes through the
  command path; the index is resolved when the command runs. T018 adds a no-op stub; T026 implements it.
- Gate note: until T027/T030 every `verify.sh` run needs `--expect-fail 'me.misa198.airmedy.player.PlaybackCommandPathTest,me.misa198.airmedy.player.PlaybackCoordinatorDefectTest'`.
  T026 turns only part of `PlaybackCommandPathTest` green, so before T026's gate `verify.sh --expect-fail` must accept
  method-level entries (`Class#method`) — extend verify.sh then.
- `dispatch(...)` keeps its signature; after T026 its `Job` completes when the command has been handled (tests `join()` it).

| Task | Model | Brief / session | Result | Status |
|---|---|---|---|---|
| T018 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T018/brief-1.md`, `brief-2.md` (session `ses_ef1e5cb7effe6Ujo4ZVBomBUik`) | Tests first: `PlaybackCommandPathTest` 14 + `PlaybackCoordinatorDefectTest` 3 fail on assertions today (`verify.sh --expect-fail` PASS); `PlaybackCommandPathGuardTest` 6 already-holding guards (FR-084c, FR-085 a/d/f, FR-086a, FR-089). Seams: `EngineEvent.OutputStarted` (no-op arm), `selectQueueItem` stub; fakes: `autoOutputStarted` (default on), `failPosition`, resolver `failWith`/`suspendForever`, `coordinatorScope`, `ReversingDispatcher`. migration-guard NEEDS CHANGES round 1: FR-091 ordering test did not race, `join()` on custom-scope Jobs could time out; fixed in run-2; I replaced one remaining `join()` myself (trivial). Reviewer notes folded into T026/T027 task text (drain events in the command; Error before OutputStarted → fail; catch inside `tick()`). **Review rounds: 1** | DONE |
| T019 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T019/brief-1.md` | Media3 exoplayer/common 1.11.1 → `implementation`, `media3-test-utils` → `testImplementation`, androidTest lines dropped, notices updated. The coder's run was killed by the OS memory reaper after its edits (before its own test run); diff reviewed complete. Gate PASS with `--expect-fail` (T018 classes), assembleDevQaAndroidTest PASS; runtime classpath: core media3 modules only, no FFmpeg/decoder extension; abiFilters unchanged. **Review rounds: 0** | DONE |
| T020 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T020/brief-1.md` … `brief-5.md` (session `ses_ef1b447f3ffeZSrv7tsGzK5l9P`) | `Media3Engine` single-player core + `Media3PlayerFactory` (shared `sistrum-player` looper, synchronous marshalling, live-player count); `Media3EngineCoreTest` 12/12 on the CPH2307 `.qa` build (3 runs). Contract: Media3 events via `pollEvents()`, looper threading. Orchestrator fixes: `check(!closed)` in prepare; dynamic scheduling off (root cause of coarse positions). Not selectable yet. **Review rounds: 4** (3 + 1 owner-approved; see "T020 stop") | DONE |
| T021 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T021/brief-1.md` (session `ses_ef18ca039ffeVnlGX0Pyhvxa0D`) | Gapless on one ExoPlayer (two-item playlist; one `GaplessAdvanced` per AUTO transition; previous item removed; position extrapolation reset at the advance); factory test hook for audio processors. Device: `Media3EngineGaplessTest` 7/7, `Media3EngineCoreTest` 12/12. Measured joins: generated WAV split ≤ 10 ms and frame count exact; FLAC corpus 2 frames; **MP3 corpus 529 frames ≈ 11 ms** (see open item). **Review rounds: 0** | DONE |
| T022 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T022/brief-1.md` (session `ses_ef17aabc7ffenJTcZXQOP5Ek16`) | `FocusVolumeRamp` (linear, full scale 120 ms down / 240 ms up as native `next_focus_gain`, retarget continues) + 7 host tests; Media3Engine drives `player.volume` through it with a 10 ms looper stepper, new players seeded from the ramp. Gate PASS; device regression Core 12/12, Gapless 7/7. **Review rounds: 0** | DONE |
| T023 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T023/brief-1.md` (session `ses_ef1733959ffe6qaKEZWUPjyPd4`) | `EngineSelectionPreferences` (DataStore `engine_preferences`, default Native, unknown → Native); `EngineFactory` class reads the selection on every `create()` (suspend; coordinator `engineFactory` is now `suspend () -> PlayerEngine`), falls back to Native until T034 adds the Media3 builder. `EngineFactoryTest` green; characterization tests unmodified; gate PASS. **Review rounds: 0** | DONE |
| T024 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T024/brief-1.md` (run killed by the memory reaper after writing every file; no session id) | Version row ×7 (≤1 s gaps) unlocks a persisted `developer_unlocked` flag in `engine_preferences`; Developer row/page (`AppStackPage.SettingsDeveloper`) only when unlocked; engine `Selection` Native/Media3 + "applies at next playback start" note; EN + AR strings. Diff reviewed, migration-guard PASS, gate PASS (expect-fail classes unchanged). **Review rounds: 0** | DONE |
| T025 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T025/brief-1.md` (session `ses_eef6a72b8ffelioyl04eIE9vi4`) | `sync/LibraryScanRunner` (scan body moved verbatim from `performScan`; Mutex-serialized; `scanInBackground` on a process-lifetime scope, skipped without read-media permission) used by the Scan page, Tag separators (via `launchScan`) and the engine change. Trigger wired in `AppDestinationContent` (the engine callback lives there; `DeveloperContent` unchanged); same-engine selection is a no-op. Uninterrupted playback during the rescan is checked on device in T035. Gate PASS. **Review rounds: 0** | DONE |
| T026 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T026/brief-1.md`, `brief-2.md` (session `ses_eef66fbe7ffeQMUPCjA1d9TV3R`) | Serialized command path: one FIFO (`ArrayDeque` + lock) drained by a single on-demand drainer coroutine that exits when empty, instead of the task line's `Channel` + permanent consumer (a permanent consumer would hang every harness test, whose coordinator scope is the `runTest` scope; same single-consumer semantics, no ADR/spec change). Actions held until `markRestored()` (completed under the lock — orchestrator fix); `withCommandLock` = Block command on the same queue; per-command catch → `fail`; `tick()` catch; `selectQueueItem` resolves the index at run time; service `onSkipToQueueItem` uses it; service scope has a `CoroutineExceptionHandler`. FR-081/084/091 tests green, the rest of PlaybackCommandPathTest still expected-fail (T027). Round 1 (migration-guard NEEDS CHANGES): drainer cancelled before start stranded the queue → `invokeOnCompletion` cancel-all, scope-end handler, `ensureActive` before enqueue, non-cooperative CancellationException treated as failure. Notes for T029: `onDestroy` calls `closeEngine()` outside the command path; a Block already running finishes even if its caller is cancelled. Gate PASS. **Review rounds: 1** | DONE |
| T027 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T027/brief-1.md`, `brief-2.md`, `brief-3.md` (session `ses_eef56e801ffe6AW4fFbKxhAvy9`) | Focus requested only when audio is about to start (`focusHeld`; re-request keeps a duck → FR-022 defect fixed), abandoned on stop / stop-at-end / fail; stop clears resume-on-gain; failed restore prepare closes its engine; `PendingStart` (fresh / recovery / resume): Playing, nowPlaying Playing, Last.fm start and listening bookkeeping only on `OutputStarted`, drained inside the same command; Error before start → `fail`; Ended before start reports Playing first; gapless/crossfade don't wait; recovery sends no Last.fm start (FR-015 defect fixed). Native engine emits `OutputStarted` after unpaused prepare / `play()`. Round 1: coder stopped on 10 `LegacyNativeEngineTest` exact-event assertions → orchestrator decision (contract refinement 2026-10-06): insert one `OutputStarted` poll after each unpaused prepare, no existing assertion changed, + 3 new engine tests; fix seek dropping a pending start + `PlaybackPendingStartTest`. Round 2 (migration-guard NEEDS CHANGES): Error in the starting drain re-showed the foreground; pause before start lost the Last.fm start; also seek during a pending fresh start and transient focus loss during a pending start (async engines only) — 4 tests. migration-guard PASS, gate PASS (only DefectTest#FR-013 expected-fail, T030). Open (T032): no timeout if an engine never reports OutputStarted/Error. **Review rounds: 2** | DONE |
| T028 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T028/brief-1.md` (session `ses_eef373916ffed4wFifM9jvkCaQ`) | `QueueHandoff` (in-process, token = UUID, consumed once, 60 s TTL, max 16 entries, stores a copy; `shared` lazy instance); controller play/shuffle/playNext/append/reorder put a `queue_token` extra, service takes it once (missing/expired → logged, empty list). `TrackIdsExtra` removed; grep for id-array extras empty. Public `PlaybackController` API unchanged. `QueueHandoffTest` (6). migration-guard PASS; orchestrator added `require(maxEntries > 0)` (guard note; QueueHandoffTest re-run 6/6). Gate PASS. **Review rounds: 0** | DONE |
| T029 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T029/brief-1.md`…`brief-3.md` (session `ses_eef316a11ffeEU1HJknoXhfhiI`) | `requiresForegroundStart` / `serviceShouldStopWhenSettled` (pure, `ForegroundPolicyTest`); controller uses `startForegroundService` only for actions that may start audio, else `startService`; service reaches foreground (placeholder notification) on EVERY `onStartCommand`; stop-self only via `settle()` when settled AND coordinator `isIdle()` (queue/preRestore empty, not draining, restored), triggered by a coordinator `onIdle` hook; ticker runs only while not settled (`distinctUntilChanged`); onCreate DB work moved off main; onDestroy without runBlocking: stop intake, cancel jobs + restore, `coordinator.shutdown()` (cancels + joins in-flight), listening finish, session save (only if restored before destroy), engine close, writer join, then on main (only if no newer instance: generation guard; token-matched `AndroidPlaybackSession.clear(token)`) state/artwork reset; a new instance's restore joins the previous teardown. Round 1: stop-then-play could kill the queued Play (stopSelf(latestStartId) in stopForeground) + ticker restarting on every state change. Round 2 (migration-guard): old teardown overwrote a new instance's global state; cold settle before restore could save an empty session. Orchestrator fix after the round-2 PASS: capture `isRestored()` before cancelling the restore job (its finally marks restored). Notes: a Play to a new instance skips waiting for the old teardown's save (harmless); the service is `exported=false`, so the `adb shell am start-foreground-service` acceptance check (T035) needs another way to send the intent. Gate PASS. **Review rounds: 2** | DONE |
| T030 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T030/brief-1.md`, `brief-2.md` (session `ses_eef1bc10effe0AElEgp3z3cRlt`) | Media-session queue = window `[current-25, current+75)` (≤ 100, absolute indices) resolved through the resolver only for ids new to the window, not republished when unchanged (`NowPlayingPort.publishQueue(snapshot, window)`; fake follows); resolver looks tracks up by id (new DAO `@Query trackRow`, same SELECT as `observeTracks`; shared row mapping; no schema/version change) instead of loading the whole library per resolve; consecutive seeks coalesced in the drainer; on-demand ordered session saver + `saveSessionNow()` used by teardown; restore keeps the saved current track (FR-013 defect fixed), falls back to the next available, then the previous, and a fallback track starts at 0 (orchestrator decision). FakePorts: only the new publishQueue signature + `windows`, `FakeResolver.resolveCalls`. Round 1 (migration-guard NEEDS CHANGES): saved `currentIndex = -1` crashed restore (`take(-1)`); plus fallback position 0, waiter completion, head window when there is no current track, seek jobs completed. All T018 tests-first classes green now: full suites PASS with no expected failures. Notes: first publish of a new window resolves up to 100 ids (one DB query + file check each) at the end of the command, after playback started. **Review rounds: 1** | DONE |

### T020 stop (2026-10-06): 3 review rounds used — resolved (owner approved one more round)

Uncommitted: `A/player/media3/Media3Engine.kt`, `A/player/media3/Media3PlayerFactory.kt`,
`AI/player/media3/Media3EngineCoreTest.kt`, and the contract amendment in `contracts/player-engine.md` (Media3 events via
`pollEvents()`, playback-looper threading). Session `ses_ef1b447f3ffeZSrv7tsGzK5l9P`, briefs 1–4.
Host gate PASS (`--expect-fail` T018 classes), builds PASS. Device (CPH2307, `.qa`): 11/12 instrumented tests PASS.
Rounds: (1) my brief used `flac_malformed.flac` (only a bad RG tag, valid audio) and a per-step minimum → fixed with
generated garbage/empty files; (2) real finding: ExoPlayer's `currentPosition` moves in ~250 ms steps on this device
(some 200 ms ticks unchanged) → engine now extrapolates while playing (monotonic, capped 500 ms, reset on prepare/
pause/seek); zero-delta ticks gone; (3) the freshness test (|Δposition − Δwall| ≤ 150 ms from OutputStarted) still fails:
3 reruns show the position advancing slowly for ~200 ms after the first rendered advance, then at normal speed ~200 ms
behind wall time (e.g. t=1012 ms → p=798 ms; t=202 → p=21). That is the audio pipeline's start-up: the position follows
the audio actually rendered, which is the right reference for lyrics sync, so the engine looks correct and the test's
"wall time from the first advance" assumption is wrong.
Resolution: owner approved a 4th round (warm-up freshness test); it still showed a stalled tick in steady state. Root
cause found: Media3 1.11.1 enables *dynamic scheduling* by default (`ExoPlayer.Builder.dynamicSchedulingEnabled = true`,
read from the bytecode), so the playback loop sleeps ~250 ms between position updates. Orchestrator one-line fix:
`.experimentalSetDynamicSchedulingEnabled(false)` in `Media3PlayerFactory` (trade-off: more playback-thread wake-ups,
comparable to the native engine's continuous audio thread). Result: readings track wall time within ±7 ms at every
200 ms tick; Media3EngineCoreTest 12/12 on the device, three consecutive runs. The engine's extrapolation stays (harmless
safety net; revisit if it ever masks a stall). Earlier proposal (kept for the record): accept the engine; change the test to measure freshness after a 1 s warm-up (per-tick: strictly increasing,
step ≤ 450 ms, |Δp − Δt| ≤ 150 ms between consecutive readings) — one more delegated round (needs owner OK, max rounds
reached) or an orchestrator test edit.

### Open: MP3 gapless join ≈ 11 ms on Media3 (found at T021, 2026-10-06)

`gapless_mp3_1.mp3 → gapless_mp3_2.mp3` (ffmpeg libmp3lame 192k, LAME/Xing header) leaves a 529-frame near-silent run at
the join on the CPH2307 (FLAC: 2 frames). 529 samples = the MP3 decoder delay, so the encoder-delay/padding trimming is
incomplete for these files (extractor gapless info vs the platform MP3 decoder). SC-003 limit is 10 ms (480 frames).
T021's test allows one MP3 frame (1 152) and logs the value. To do before T035/T062: check `Format.encoderDelay/
encoderPadding` ExoPlayer reads for these files, compare with the native engine on the same pair, and decide (fix in the
engine/extractor setup, or a spec note if the files' header is the cause). Not an ADR/spec change yet.

## Exact next step

M3: T030 done (no --expect-fail needed any more). Next → T031 (brief `~/.local/state/sistrum-delegate/T031/brief-1.md`), T032, T032b, T033, T034, T035 [MANUAL].
