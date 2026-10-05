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

## M2 — in progress

| Task | Model | Brief | Runs / review rounds | Status |
|---|---|---|---|---|
| T007 | orchestrator (tooling, no delegation) | — | 5 acceptance cases with throwaway probe classes (assertion → PASS; `IllegalStateException`, `TODO()`, passing → FAIL; assertion class + other failing classes → FAIL) + missing class → FAIL + full-suite path PASS; probes removed. **Review rounds: n/a** | DONE |

## Exact next step

M2: `/delegate-task T008` (characterize the pure playback policies).
