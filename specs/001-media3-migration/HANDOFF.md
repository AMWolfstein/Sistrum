# HANDOFF — 001-media3-migration

**Current branch:** `feature/media3-migration` (check this out at session start)
**Feature directory:** `specs/001-media3-migration/`
**Phase:** M8 in progress (2026-10-14). Done: T062a/b/c, T062 parts A/B (`research/regression-001.md`), T062d/T062e (SC-014 PASS), T062f (SC-006 breakdown). Owner 2026-10-14: SC-006 restated as ≤ 300 ms → PASS (Opus-heavy 259 ms, lossless/MP3/AAC 201 ms). T062g closed (owner 2026-10-14): SC-013 NOT MET for 001 (steady CPU 1.7× native), deferred to the cleanup/perf phase; SC-013 1 h run stays an owner check. T062h root cause found (Media3 XingFrame +529 vs self-compensating platform MP3 decoder); owner chose the fix → T062i (tests) → T062j (fix): **SC-003 PASS** (every MP3/FLAC join 1 frame). Next: T064 → T063 → M8 report. M7 owner listening checks deferred. M1–M7 approved/closed.
**Queued after M8 (owner 2026-10-14, verbatim in `specs/002-kotlin-decoders/OWNER-PLAN-2026-10-14.md`):** do NOT start while M8 runs. When M8 completes: Part 1 = normalization "Auto" mode as 001's final task (tests first), then Part 2 = 002 (Phase A: import origin/port/analysis commits one by one with rebrand; Phase B: Spec Kit + milestones M1–M6) immediately, without waiting for the owner; 002 decisions logged in `specs/002-kotlin-decoders/DECISIONS.md`.
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
- Pre-v1.0 item (found 2026-10-06, T039a): `searchFtsMatch` joins words with " AND ", but the platform's FTS4
  standard query syntax treats AND as a literal word, so every multi-word FTS query matches nothing. The search
  screen still shows results only because `LibrarySearchViewModel` falls back to filtering everything by text when FTS
  returns no candidates (slower on big libraries). Fix: join the prefix terms with a space (implicit AND).
- Pre-v1.0 item (owner, 2026-10-06): the decode-failure log page (T032b) gives no feedback when "Save as file"
  fails. The write error is swallowed. Add a visible error message.
- Pre-v1.0 item (found 2026-10-12, T051c): `HiddenTracksTest` is flaky (~1 in 3 runs, also at `9bc9466`): `favoriteSurvivesHideAndReappear` reads a stale replay of the shared `tracks` flow after a rewrite, and the shareIn scope keeps querying after the test closes the database (process crash). Fix test-side (own scope / cancel before close) in the review phase.
- Pre-v1.0 item (T062e guard note, 2026-10-14): a restored single-track session whose file fails to decode at prepare ends in `PlaybackState.Failed` and keeps the saved session, so the error reappears on each launch; consider clearing the session on an unskippable restore-time decode failure.
- Pre-v1.0 item (owner, Checkpoint M5 approval 2026-10-08): `LimiterStatus` keeps its last state in settings after
  the playback service stops (stale "not controlled"/"unavailable" note). Reset it when the session ends.

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
| T003b | orchestrator (no delegation) | — | steps 1–3 below | **STOPPED** (dumpsys can't measure); owner split it 2026-10-05: (c) listening check, (a)/(b) dongle recording or MANUAL in T048. **Closed 2026-10-06 as accepted on evidence (owner)**; device measurement = open follow-up |

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
- [x] ~~T003b (c) listening check~~ — skipped by the owner 2026-10-06; T003b closed on evidence (ADR-004 "T003b closure").
- [x] ~~T003b (a)+(b) dongle recording~~ — no dongle; closed on evidence 2026-10-06. Open follow-up, not a gate: device-level
  measurement (laptop as Bluetooth A2DP sink via PipeWire + `pw-record` is available: Realtek BT 4.2, Audio Sink
  role advertised; judge level reduction, not peaks).
- [ ] M7 listening session (T061, one session): 12 s fade and snaps (pause/seek/next) by ear; SC-015 two tones that clip only
  when summed come out limited during a crossfade; SC-011 loud material through the limiter (clip prevention on/off, max EQ
  boost + preamp, two loud tracks overlapping); "playback doesn't go silent" during `Media3EqAppImitationTest -e audible true
  -e phaseSeconds 15`; crossfade on skip (next/previous/tap) by ear; crossfade on repeat-one on/off.
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
| T031 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T031/brief-1.md` (session `ses_eef048303ffe35vgarmJzn6aTe`) | `distinctUntilChanged()` on the EQ and crossfade settings flows; coordinator skips a repeated `(seconds, blend)` (no flow write / artwork clear / preload) and a repeated EQ value (no `setDsp`; new engines still get the current EQ). `PreferenceChurnTest` (4). Orchestrator fixed test 1 (it passed without the dedup because the preload slot was already full) by emptying the slot again after the first application; mutation-checked: fails without the dedup. migration-guard PASS, gate PASS. **Review rounds: 0** (orchestrator test fix) | DONE |
| T032 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T032/brief-1.md`, `brief-2.md` (session `ses_eeeff447bffes2t07Q9T1DSD4A`) | Every `EngineEvent.Error`: log `Decode failure provider= format= id= error=`, publish TransportState.Error, close the engine, skip to the next distinct track (paused stays paused unless a resume is pending), drop the rest of the closed engine's batch; last track / repeat-one / budget exhausted → `fail`. Budget = consecutive failures < min(queue size, 20), reset on OutputStarted and user play/shuffle/select. No retry. `EngineErrorPathTest` (8). Round 1: paused session started audio after a skip; nested skip recursion bounded (cap 20). The round-1 coder run was killed by the memory reaper after writing the code and tests, before running them; orchestrator reviewed and ran the gate (PASS). migration-guard PASS; orchestrator applied its note (outer start guard also checks engine identity), player tests re-run 236/236. **Review rounds: 1** | DONE |
| T032b | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T032b/brief-1.md` (session `ses_eeef5f457ffe0gPzEUoaHF56Q0`) | `DecodeFailureLog` (JSONL in filesDir, newest 200, six FR-064a fields only, file name not path), coordinator records on the T032 error path (new last ctor param `decodeFailures`, write failures swallowed), Settings → Decode-failure log page (Share via ACTION_SEND, Save via CreateDocument, Clear; existing Folder glyph), strings EN/AR. `DecodeFailureLogTest` (7). Orchestrator fixes: fallback resolve rethrows CancellationException; `forContext` returns one process-wide instance (guard note: UI and service instances did not share a lock). migration-guard PASS; gate PASS; after the two fixes DecodeFailureLogTest + EngineErrorPathTest and assembleDevDebug re-run green. Open: save failure is silent (UX). **Review rounds: 0** | DONE |
| T033 | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T033/brief-1.md` (session `ses_eee5ec0b4ffeonoCY4eLUjU3jl`) | Root cause investigated first (see "T033 root cause"): the session path is correct; the scan fix is T033a. Pure `nowPlayingMetadata(item)` in PlaybackService.kt sets MEDIA_ID, TITLE, ARTIST, ALBUM and ALBUM_ARTIST (blank album and album artist left out; display text trimmed); artwork unchanged. `NowPlayingMetadataTest` (6). Orchestrator fixes: MEDIA_ID is not trimmed (the brief's trim rule was wrong for ids); guard notes added (key order, untrimmed id). migration-guard PASS; gate PASS; test class re-run 6/6 after the additions. **Review rounds: 0** | DONE |
| T033a | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T033a/brief-1.md`, `brief-2.md`, `brief-3.md` (session `ses_eee5a0889ffeCUda4uYR9enmZD`) | Embedded-tag fallback: `EmbeddedTagReader` also returns artist, album artist and album (ID3 incl. WAV/AIFF chunks, Vorbis, MP4; v2.4 multi-values joined with ", "; still one open per file). Pure `scanTagFields`: a MediaStore value wins, the tag fills gaps, and album artist falls back to the resolved artist. Album exception (owner, brief-2): a MediaStore album equal to the parent folder name loses to a non-blank album tag. Raw tag values are kept in rawJson (`tag_artist`, `tag_album_artist`, `tag_album`), read back by `priorScanState`; no Room schema change. `CurrentMetadataSchemaVersion` 6→7. Tests: EmbeddedTagReaderTest +6, TagFallbackTest (12+), LocalLibraryTest +2. Run 1 was killed by the memory reaper mid-edit (reported; resumed on owner OK with Gradle caps in `~/.gradle/gradle.properties` and the new CLAUDE.md memory check). Round 1 (guard + orchestrator): a tag-derived album still used MediaStore's shared folder ALBUM_ID, so different albums merged → `albumFromTag` keys those albums by the artist and album hash. migration-guard NEEDS CHANGES → fixed; gate PASS. Open: SC-005 on device needs a rescan of the `.qa` library (in T035). **Review rounds: 1** | DONE |
| T034 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T034/brief-1.md` (session `ses_eee2bb5dfffeVnBvgxR3B1DTs4`) | `playbackEngineBuilders(native, media3)` in EngineFactory.kt; the service passes Native → `LegacyNativeEngine`, Media3 → `Media3Engine(media3PlayerFactory)` (lazy, created on the first Media3 build). Default still Native. EngineFactoryTest +4. migration-guard PASS; gate PASS. Guard notes for T035/parity: on Media3, OutputStarted arrives on a later poll, so Playing is published slightly later; crossfade, EQ, gains and normalization are no-ops on Media3 until M4+ (Media3 stays opt-in via the developer page); `Media3PlayerFactory.call` blocks the command drainer for up to 5 s (prepare up to 10 s). **Review rounds: 0** | DONE |
| T035a | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T035a/brief-1.md` (session `ses_eee09fdd6ffehRnYZcwbNJag8K`) | Periodic position save every 10 s while Playing, in the shared coordinator. The full session store (one Preferences DataStore, rewritten whole per edit, ~0.5 MB for 20 000 ids) was too heavy for that, so the periodic path writes only `(trackId, positionMs)` to a separate `playback_position` DataStore; full saves update it too; `load()` merges it when the track id matches. Both go through the one ordered saver (full first, then position; latest wins; a full save drops a pending position). `PeriodicSessionSaveTest` (6, fake clock), `PlaybackSessionStoreTest` (4). migration-guard PASS; gate PASS. Orchestrator fix from a guard note: an unreadable position file no longer breaks `load()` (falls back to the session position, rethrows cancellation); both test classes + build re-run green. **Review rounds: 0** | DONE |
| T036 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T036/brief-1.md`, `brief-2.md` (session `ses_eedf6244bffeOAxuyVugvYglx3`) | `player/decoders/`: `FormatKey`, `DecoderProvider`, `DecoderTable`/`TableDecoderRegistry` (first available in table order; skip reasons "unsupported format (X)" / "no decoder on this device (X)"), `DefaultDecoderTable` = the only place format names and MIME types live, keyed by the scanner's labels (codec only sniffed for m4a/mp4); `PlatformProvider` (table + `CodecProbe`, raw PCM needs no codec); `MediaCodecProbe`/`ProcessCodecProbe` (MediaCodecList once per process). `DecoderRegistryTest` (15). Round 1 (guard notes, FR-060 / Principle 10): rows added for m4a/mp4 `*` (sniff fallback), WebM/WEBA, AMR, 3GP/3GA, AC-3, E-AC-3, MP2, E-AC-3 JOC; blank format → "unsupported format (unknown)". Contract's sample table note updated. migration-guard PASS; gate PASS. **Review rounds: 1** | DONE |
| T037 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T037/brief-1.md`, `brief-2.md` (session `ses_eedec1164ffeizWr8X998uRYVs`; upstream copy in `T037/upstream/`) | Port of Choir `AiffExtractor` (f2e96fd, GPL-3.0-or-later; SPDX + attribution kept) to `player/decoders/aiff/`. Changes from upstream: the 8-bit signed→unsigned bias in both byte orders (upstream skipped it for `sowt`); named refusals via the pure `aiffRefusal(header)` and `ParserException.createForUnsupportedContainerFeature` ("float AIFF-C (fl32)", "compressed AIFF-C (ima4)", "unsupported AIFF sample size (n)"); Log swap. `KotlinAiffProvider` ("kotlin-aiff") is in `defaultDecoderRegistry`; THIRD-PARTY-NOTICES entry. `AiffExtractorTest` (17). Deviation: the coder edited the existing T036 test `aiff resolves only when the bundled provider is registered`, which asserted the old default (no AIFF); spec reason (T037 adds it), and coverage is equal (migration-guard confirmed). Round 1 (guard): the chunk walk overflowed on size 0x7FFFFFFF → Long compare; a partly present COMM gave a false refusal → null; the extractor now checks compression before sample size (same reason as `aiffRefusal`); fuzz + stereo-24 seek tests. Gate PASS. **Review rounds: 1** | DONE |
| T038 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T038/brief-1.md`, `brief-2.md` (session `ses_eedd4205cffeaVPY7cAVmRqM4L`) | `defaultDecoderProviders(probe)` shared by the registry and `Media3PlayerFactory` (new `providers` param; `DefaultMediaSourceFactory(ProviderExtractorsFactory)`: Media3's defaults first, then provider extractors; each tagged so `ProviderRecorder` maps URI → provider id on sniff success; provider audio renderers appended in one merged renderers factory, today's path when there are none). `Media3Engine` errors carry the recorded provider id (fallback "platform"). Orchestrator fix: `tagSniff` forwards `getSniffFailureDetails`. Unit `ProviderExtractorsFactoryTest` (4); instrumented `Media3RegistryTest` on `.qa.test`: **OK (3)**: AIFF via kotlin-aiff, ima4 refused by name (path: prepare threw, FR-086), PCM WAV via platform. Round 1 (guard + device run): the WAV fixture wrote big-endian RIFF fields ("Top bit not zero") → little-endian; an unverified "observed" comment replaced by a logged path. migration-guard NEEDS CHANGES → fixed; gate PASS incl. device. **Review rounds: 1** | DONE |
| T039 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T039/brief-1.md` (session `ses_eedc38a9cffehovAMG9tvzlXWA`) | `sync/ScanGate.kt`: `AdmitAllGate` (Native) / `RegistryScanGate` (Media3: registry resolve, then a header refusal only for providers with `refusalHeaderBytes > 0`; never throws, fails open), `SkippedFilesCounter`; `SkippedFilesSummaryStore` (DataStore `skipped_files`). Scanner: format/codec moved before the gate and the tag read; a skipped file is never tag-read or added. Runner: the gate follows the engine selection; the summary is saved on each written scan (empty with Native). No Room change; schema version not bumped (parsing unchanged, guard agreed). `ScanGateTest` (8). Orchestrator fix: `readHeaderBytes` reads min(n, length), so a short AIFF-C is still refused. migration-guard PASS; gate PASS. **Review rounds: 0** | DONE |
| T040 | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T040/brief-1.md` (session `ses_eedbbcea2ffe0zWKvoFjq7RPt8`) | Skipped-files summary on the scan page (after a scan and when idle) and on the scan-filter settings page ("No files were skipped…" when empty). `SkipReason` + `parseSkipReason` in DecoderRegistry.kt generate and parse the reason text in one place (byte-identical), so the two generated forms are localized and provider refusals show as is. Plural `scan_skipped_files` EN + AR. `SkippedSummaryLinesTest` (5). Orchestrator fixes: the composable renamed to `SkippedFilesSummaryText` (it clashed with the data class name); Arabic zero/one/two forms rewritten to the file's convention (ملف واحد / ملفين; the coder's dual "ملفًا" was the 11–99 form). migration-guard PASS; gate PASS. **Review rounds: 0** | DONE |
| T041a | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T041a/brief-1.md` (session `ses_eed99757affeHOwG6gNJ8j7zM5`) | Found by T041: `Media3Engine.currentItem` was set only in prepare, so an error after a gapless advance named the first track (wav_adpcm_ms logged as `provider=platform format=aifc`). Fix: the AUTO transition sets `currentItem = incoming`. Instrumented `Media3ErrorAttributionTest` on `.qa.test`: **OK (2)** (WAV → ima4 AIFF-C error names kotlin-aiff + "aifc"; WAV → WAV control, no error); `Media3RegistryTest` OK (3). migration-guard PASS: it confirmed from the 1.11.1 bytecode that ExoPlayer emits the transition before the error for prepare and renderer failures, and that `audioFormat` is null by then. Open, existing: `format` mixes a MIME type and a file extension (`DecodeFailureEntry.codec`), a pre-v1.0 cleanup. **Review rounds: 0** | DONE |
| T039a | deepseek-v4.1-flash | `~/.local/state/sistrum-delegate/T039a/brief-1.md`, `brief-2.md` (session `ses_eed2e6c6bffedYXOQWEUQ54ATF`) | TESTS FIRST for FR-065a. Instrumented `HiddenTracksTest` (11) on `.qa.test`: 7 fail on AssertionError as intended (views, lookup, search, count, migration, AllMigrations, shared album); play count, prior state, lyrics and favorite pass already as regression guards. Host: `HiddenTrackBasisTest` 4 fail on assertions (`--expect-fail`); LocalLibraryTest `writesDecoderUnavailableReasonWhenSet` fails on assertion (sharedLogic run by hand, since `--expect-fail` covers androidApp only). Stubs only: `AllMigrations` (same 11 migrations, same order), unregistered empty `Migration13To14`, `hiddenTrackBasis` stub, `LocalTrack.decoderUnavailable`; DB version 13. Round 1 (guard): the album/artist search checks could never fail → single-word queries with positive controls; added the shared-album and favorite tests. Orchestrator: changed the search queries to single words after the positive control failed on device (see the FTS note in Pre-v1.0). **Review rounds: 1** | DONE |
| T039b | deepseek-v4-pro | `~/.local/state/sistrum-delegate/T039b/brief-1.md` (session `ses_eed1b0800ffeXWPXkIMfM0ANXx`) | FR-065a implemented; T039a tests pass unmodified. Room: `SyncTrackEntity.unavailableReason` (nullable), DB v14, `Migration13To14` (ALTER TABLE ADD COLUMN, declared above `AllMigrations`). Filters on `observeTracks`, `trackRow`, `activeTrackCount`; the empty-scan guard uses the new `activeTrackRowCount` (hidden rows included); search documents only for visible tracks; play counts, prior state, lyrics, playlists and analysis keep hidden rows. Scanner: a skipped file becomes a hidden row (no tag read, no album artwork; prior values only for an unchanged file; schema = prior or 0). Device gate on `.qa.test`: HiddenTracksTest **OK (11)**, and EmptyScanGuard (3), PriorCodecScanState (1), ArtistArtworkCleanup (5), ListeningRetention (1), PlaylistMutationPersistence (6) all OK. Real-DB check on `.qa`: the existing v13 library migrated to v14 (766 rows kept); a Media3 rescan → 775 rows, 766 visible, 9 hidden with their reasons, none in the search index. Orchestrator fixes: the scan-result counts use visible tracks only (`LibraryScanRunner`, it said "775 tracks"); one stray space. migration-guard PASS; gate PASS. **Review rounds: 0** | DONE |

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

M3: T032b done. T033 root cause found (below); Owner approved T033a + ADR-006 amendment (2026-10-06). M3 DONE: T035 owner checklist ok; **Checkpoint M3 approved (owner, 2026-10-06)**.

M4 started (owner: run to the end per the stop rules). T035a, T036–T041a done; T041 done (owner AIFF listening ok). **Checkpoint M4 approved (owner, 2026-10-06), effective after T039a/T039b.** T039a, T039b done. **M4 complete; Checkpoint M4 approved.** Stopped here at the owner's request (2026-10-06). Next → M5 (DSP chain and session limiter; needs T003/T004; remind the owner of the exact T003b listening steps when M5 reaches T046).

M5 done 2026-10-07 (T042–T048d; see "Checkpoint M5" at the end). **Checkpoint M5 approved (owner, 2026-10-08).**
M6 redesign docs written 2026-10-08 (M6-1…M6-5; section "M6 normalization redesign" at the end). **Next:** the owner
approved the M6 plan 2026-10-08 (decisions at the end); M6 runs T049 ∥ T050 ∥ T050a → … → T054 per the stop rules. Before M7: the crossfade behaviour audit.
**Stopped 2026-10-08 after T051 (owner: OpenCode weekly quota used up; finish T051 on Claude Code, commit, stop).**
Next: T051b (store + planner tests first). Its brief is written: `~/.local/state/sistrum-delegate/T051b/brief-1.md`
(deepseek-v4.1-flash, not dispatched yet). The uncommitted Members-rule edit in `contracts/loudness-analysis.md`
(failed tracks excluded; "from a tag" = not (album == parent folder AND no `tag_album`)) belongs in the T051b commit.
Gates from now on: expect-fail = the 7 GainRampTest methods (until T052) plus T051b's own.

### T033 root cause — "Unknown artist" (2026-10-06, orchestrator, FR-021)

Evidence (QA build `me.misa198.airmedy.dev.qa` only; daily app untouched; `dumpsys media_session`,
SystemUI media dump, QA library DB copied via `run-as`):
- The session metadata path is correct. Live session for a tagged track: `description=<title>, Mohamed Hamaki`.
  SystemUI's `MediaData(artist=Mohamed Hamaki, ...)` matches the in-app player. ARTIST, the notification text and the
  queue subtitle all come from the same `item.artist`.
- In the QA library, only 2 of 729 tracks show "Unknown artist": `local:779` and `local:785`. Both are **WAV**
  copies of tracks whose AAC/AIFF/FLAC/ALAC/MP3 copies have the right artist. Their rows have `artists: []`,
  `album_artists: []` and album "Music" (MediaStore's untagged fallback). They still have year, label, ISRC and
  copyright, which `EmbeddedTagReader` read from the WAV `id3 ` chunk.
- SystemUI showed those items as `Lighters (feat. Bruno Mars), Unknown artist, Music`.
- **Root cause:** MediaStore does not read artist, album or album-artist tags from WAV files. The scanner takes
  artist, album artist and album only from MediaStore. `EmbeddedTagReader` already parses the WAV/AIFF ID3 chunk,
  but returns only date, year, BPM, label, ISRC, copyright and explicit; it never takes TPE1/TPE2/TALB. The app
  itself shows "Unknown artist" for these tracks, so the lock screen is consistent with the app, and the defect is
  in the scan, not in `PlaybackService`.
- **Consequence:** ADR-006 ("Unknown artist is fixed in `publishNowPlaying`") and the T033 task line
  (`PlaybackService.kt` only) are wrong about where the fix goes. Proposed:
  - T033 keeps the pure `nowPlayingMetadata(item)` part (TITLE, ARTIST, ALBUM, ALBUM_ARTIST; FR-020) with
    `NowPlayingMetadataTest`.
  - New task T033a (`sync/EmbeddedTagReader.kt`, `sync/MediaStoreLibraryScanner.kt`): read TPE1/TPE2/TALB (and
    the title) from embedded ID3/Vorbis/MP4 tags, and use them as a fallback when MediaStore reports none. The
    fallback must not be WAV-specific (Principle 10).
  - Bump `CurrentMetadataSchemaVersion`. That re-reads tags for every file on the next scan.
  - Tests in `EmbeddedTagReaderTest` and the scanner tests. ADR-006 is amended to match.
  - Needs the owner's OK: an ADR change, scan code outside `player/`, and a full tag re-read on the next scan.



### T035 — Media3 pass on `.qa`, device parts (2026-10-06, orchestrator, adb only)

Build `assembleDevQa` at `c5ef6cb`, APK id checked (`me.misa198.airmedy.dev.qa`), `adb install -r`. Daily app
`me.misa198.airmedy.dev` untouched: no session, no log lines during the run, its process unchanged (running since the
previous day). The developer page is now unlocked on `.qa` and the engine is set to **Media3** (left that way for the
owner checklist).

| Scenario | Result | Evidence |
|---|---|---|
| US3 sc3 setting hidden | PASS | Settings list has no Developer row before unlock; 7 taps on About → Version show "Developer". |
| US3 sc2 default Native | PASS (partial) | Never-set install shows "Native (FFmpeg)". A fresh install was NOT RUN (no uninstalls); `parseEngineKind(null)` is covered by a unit test. |
| US3 sc1 switch mid-track | PASS | Native (AAudio) playing; switched to Media3; position 52.6 s → 58.7 s over 6 s with no restart. The next start used Media3 (`ExoPlayerImpl Init [AndroidXMedia3/1.11.1]`, `c2.android.opus.decoder`, AudioTrack). |
| US3 sc4 rescan on switch | PASS | All 775 rows at schema 7 after the switch; playback uninterrupted. |
| US2 sc1 start from album | PASS | Session PLAYING with title, artist and album; queue size 2 = the album. |
| US2 sc2 previous rule | PASS | Past 3 s: 28.9 s → 1.7 s, same item. Within 3 s on item 1 → item 0. |
| US2 sc3 end with repeat off | PASS | "Repeat off". Last track ended: PAUSED at 256.0 s on the last item, UI 4:16/4:16 with Play, log "Playback stopped at final queue track"; Play → item 0. |
| US2 sc4 restore | PASS, with a gap | Pause at 14.97 s → force-stop → relaunch: "Restored paused playback positionMs=14970", no audio by itself. Gap (same on Native and on `main`): a hard kill **while playing** restores the position from the last state publish (here 0), because the session is saved only on publish and `onDestroy`; there is no periodic save. → proposed task below. |
| US4 sc1 lock-screen artist | PASS | SystemUI `MediaData(artist=Tamer Hosny, song=في جمال كده)` = the app. |
| SC-005 | PASS | After the rescan, WAV `local:779` and `local:785` have their artists (and albums via the folder-name rule). Remaining empty artists: only the 46 untagged files in `SistrumTestCorpus` (the corpus writes no artist tags). |
| US4 sc2 artwork | PARTIAL | SystemUI artwork = 960×960 bitmap. Visual match with the app → owner. |
| US4 sc6 command to a stopped service | PASS (partial) | QA force-stopped; media keys and `cmd media_session dispatch pause` → no crash, no FGS timeout, the app is not woken (no media-button session). The service is `exported=false`, so an empty-queue intent can't be sent from adb; covered by ForegroundPolicyTest / PlaybackCoordinatorShutdownTest. |
| US2 sc5 lyrics sync, sc6 stats/Last.fm once | OWNER | Needs listening and a Last.fm account; double scrobble covered by unit tests. |
| US2 sc7 Mood Radio | NOT RUN | No on-device analyzer data (spec: supplied by tests); unit tests cover it. |
| US2 sc8 20 000-track request | NOT RUN on device | The library has 775 tracks; QueueHandoff unit tests cover the Binder limit. |
| US4 sc3 focus, sc4 unplug/route, output switcher | OWNER | Hardware/listening. |
| US4 sc5 crossfade | N/A at M3 | Crossfade is a no-op on Media3 until M5. |

Proposed task (needs owner OK, not in tasks.md yet): a periodic session save while playing (e.g. every 10 s via the
existing ordered saver), so a process kill keeps recent progress. Parity issue, present on both engines.

**Owner checklist (T035, `.qa`, engine = Media3):**
1. Play a few albums: audio sounds right; gapless between album tracks.
2. Synced lyrics: the highlighted line follows the audio about as well as on Native.
3. Listen to one track past the completion threshold: one play in Insight; one Last.fm scrobble (if logged in).
4. Lock screen + notification: title, artist, artwork the same as the app; play/pause/next/previous/seek work.
5. Bluetooth or wired headset: play/pause/next buttons work.
6. Focus: start a video in another app (pauses); a call or alarm (pauses, then resumes); a navigation prompt (ducks
   smoothly, then restores).
7. Unplug headphones → pauses. Switch output in the output switcher (Android 14+) → continues at the same position.


### Decision: skipped files are hidden, not deleted (owner, 2026-10-06)

Replaces the earlier "note for the default-engine flip". A file the registry skips on this device keeps its library
row, play count, statistics, history, favorites, playlist membership and lyrics, marked with the reason
(`sync_tracks.unavailableReason`), and is left out of every library view, queue, playback request, restore and search.
It reappears with its history when a decoder becomes available (002, another device, a rescan). Spec: FR-065
amended, new FR-065a, FR-074 amended (one additive column, v13 → 14, `Migration13To14`), SC-002 and US8 sc2 reworded;
plan and data-model updated. Tasks: T039a (tests first) → T039b (implementation, high-risk). Checkpoint M4 approved
by the owner, effective after T039b; M5 starts after it. Owner asked to be reminded of the exact T003b listening steps
when M5 reaches T046.

### T041 — Corpus scan and playback on `.qa` with Media3 (2026-10-06, orchestrator, adb only)

Build `assembleDevQa` at `8cd3d52`, ID checked, `adb install -r`; engine = Media3 (set in T035). Daily app untouched
(no session; its process unchanged). Media volume was lowered with volume keys for the 22-min run and restored to
50/160 afterwards.

| Check | Result | Evidence |
|---|---|---|
| US8 sc2 / SC-016 skipped summary | PASS | Scan page: "6 files skipped: no decoder on this device (ALAC)" (2 corpus + 4 owner-library ALAC; the CPH2307 has no ALAC decoder), "1 file skipped: compressed AIFF-C (ima4)", "1 … float AIFF-C (fl32)", "1 … unsupported format (WMA)". Log `Skipped 9 file(s)`. Library 775 → 766. Each file counted once. |
| SC-002 part 2 (skipped not in library) | PASS | 41 of 46 corpus rows remain; none of the skipped files has a row. |
| WavPack | NOTE | `wavpack_lossless.wv` is on disk but MediaStore does not index `.wv`, so no scan (either engine) ever sees it; out of the gate's reach (scanner is MediaStore-based). corpus.tsv annotated. |
| SC-002 part 1 (admitted files play start to end) | PASS with 1 expected failure | All 41 admitted files ran through in one queue (1360 s; ended "Playback stopped at final queue track"). AIFF ×6 played via kotlin-aiff. `wav_adpcm_ms` → decode failure → skipped to the next track (US8 sc4, expected `decode-failure-path`). |
| `wav_adpcm_ima` | Better than expected | Plays fully: Media3's WavExtractor decodes IMA ADPCM itself. corpus.tsv expectation updated to `platform`. |
| US8 sc4 attribution | FAIL → fixed in **T041a** | The failure was logged as `provider=platform format=aifc` for a WAV: `Media3Engine.currentItem` isn't updated on a gapless advance, so errors name the first track of the run. |
| US8 sc3 (new provider appears after rescan), sc5 (provider order) | Unit-level | No new provider exists in 001; covered by `DecoderRegistryTest` (order as a table change) and the registry/scan-gate design (a new table row admits the format with no scanner change). |
| US8 sc1 crossfade/EQ/normalization on AIFF | N/A at M4 | Those are no-ops on Media3 until M5+. |

**Owner check (T041):** with the engine on Media3, listen to the six AIFF corpus files (aiff_none_8/16/24/32,
aifc_sowt_16, aifc_twos_16): each should sound like a clean tone/signal, with no noise, distortion or wrong pitch.

## T003b closed (2026-10-06, owner)

Owner: no audio dongle, Dolby can't be disabled on the CPH2307. Laptop Bluetooth checked first: a Realtek BT 4.2
adapter, and PipeWire 1.6.9 advertises the Audio Sink (A2DP) role, so the Bluetooth recording route was possible.
(c) listening check with Dolby on was started: two runs on `.qa.test`, both `OK (1 test)` (36 s); logs in
`~/sistrum-limiter/` (machine-local). The owner then skipped the test ("skip this test, I don't care about it").
No answer was recorded.
→ (a)–(c) closed as **accepted on evidence** in ADR-004 "T003b closure": AOSP source review, T003's routing check,
T046's limiter-parameter unit tests, and the effect-state hedge. Device-level measurement is an open follow-up, not
a gate. T003b checked off. T046 no longer waits on it, so the T046 reminder about the listening steps is withdrawn.
Next → M5 (T042 first) when the owner says to start.

## M5 — DSP chain and session limiter (started 2026-10-06, owner: "start M5")

| Task | Model | Rounds | Result |
|---|---|---|---|
| T042 golden generator | deepseek-v4.1-flash (`ses_eecff2515ffe4P21FRfLvFlqON`, brief `~/.local/state/sistrum-delegate/T042/brief-1.md`) | 1 | PASS. `tools/eq-golden/generate.sh` extracts `kEqBands`, `GlobalDspConfig`, `Biquad`, `kEqFrequenciesHz`, `configure_eq`, `filter_sample` and the preamp/width lines by marker, compiles with `c++ -O2 -ffp-contract=off`, writes `eq_golden.json` (5 rates × 15 gain sets: coefficients + 1/12-octave response; 125 width/preamp cases; source commit, sha256, line ranges). `--check` reproducible; renamed marker → exit 1. Coefficient spot-checked against an independent Python calculation. Gate PASS. |
| T043 EQ tests first | deepseek-v4.1-flash (`ses_eecf8456cffeIwbMQj5PhFLPdu`, brief `~/.local/state/sistrum-delegate/T043/brief-1.md`) | 1 | PASS (expect-fail: 11 tests in `EqualizerGoldenTest`, `StereoWidthPreampTest`, `DspLinearityTest` fail on assertions against stubs `BiquadEqualizer.kt`, `StereoWidthProcessor.kt`, `PreampProcessor.kt`). Orchestrator fix before commit: three tolerances loosened after a C float simulation of the native filters showed a correct float implementation exceeds them on rounding alone (EQ linearity 1e-5→1e-3·peak, sim 2.5e-4; width/preamp linearity 1e-6→1e-5·peak; band-change settled match 1e-4→1e-3, sim 1.05e-4). Brief's "13 tests" was a miscount (11 named). migration-guard PASS; notes for T044: preamp/width ramps (FR-056) need tests with the AudioProcessors; A5a enforces "no state reset" strongly but "ramped" only weakly. Gate PASS. **Process slip:** the gate was chained after the memory check in one command and started at MemAvailable 1.7 GB (< 2 GB rule); it completed normally. Memory is now checked as a separate step. |

### Decision: width stage = own ramped processor (owner, 2026-10-07)

Media3 1.11.1 `ChannelMixingAudioProcessor` applies a new matrix only at `onConfigure()` (after a flush), is
documented as 16-bit PCM, and does not ramp, so a live width change would gap/click (FR-056, US7 sc3). Owner chose
our own float `StereoWidthProcessor` with the same mid/side matrix and a ramp. ADR-004 item 2 + "Width stage
amendment", research D5, `research/dynamics-processing-session.md` and T044's task text (plus a processor-level
`DspProcessorRampTest`) updated.

| Task | Model | Rounds | Result |
|---|---|---|---|
| T044 DSP core + processors | deepseek-v4-pro (`ses_eecdabb28ffeWFoxVscPRedGLj`, briefs `~/.local/state/sistrum-delegate/T044/brief-1.md`, `brief-2.md`) | 2 | PASS. `BiquadEqualizer` (native formula, TDF-II, per-sample coefficient interpolation over 20 ms keeping state), `StereoWidth`/`Preamp`, and float `EqualizerProcessor`/`StereoWidthProcessor`/`PreampProcessor` (always active, pass-through fast path when neutral, 16-bit/float in → float out, volatile setter snapshots, 20 ms ramps). T043's 11 tests pass unmodified (max response deviation 1.5e-6 dB) + 7 `DspProcessorRampTest`. Round 1 → migration-guard NEEDS CHANGES: state was set in `onConfigure` (Media3 drains the old stream after configure → reset/click at a format change; partly the brief's wording); two tests checked one frame only. Round 2 moved state to `onFlush`, added `reconfigureDoesNotDisturbTheDrainingStream`. Orchestrator trivial fixes: `hasProcessed` set before the inactive early return (EQ enabled from flat now ramps); boundary-step assertion added to the new test. ADR-004 wording: "bypass when neutral" = stay active + fast path (Media3 decides `isActive` only at configure). migration-guard PASS (round 2). Gate PASS (first run failed only because verify.sh applied `--tests` to sharedLogic; re-run with full suites). OpenCode install had lost its postinstall after a 00:41 update; fixed with its own `node postinstall.mjs` (local link only). Non-blocking notes for T045: `EqualizerProcessor.queueInput` returns without consuming input before the first flush (unreachable under Media3); band going inactive zeroes negligible residual state. |

Stopped after T044 (owner: "stop after T044 commit"). Next → T045 (per-player chain + DSP wiring, high-risk, device test) when the owner says so.

Resumed 2026-10-07 (owner: "continue m5").

| Task | Model | Rounds | Result |
|---|---|---|---|
| T045 per-player chain + DSP wiring | deepseek-v4-pro (`ses_ee9350743ffeLOdwuZKlTjnIEe`, briefs `~/.local/state/sistrum-delegate/T045/brief-1.md`, `brief-2.md`) | 2 | PASS. `Media3PlayerFactory` builds every player with its own custom `AudioProcessorChain` Gain → Width → EQ → Preamp (+ test taps after), float output off; `setDsp(GlobalDspConfig)` applies to every live chain and seeds new ones under one lock; `Media3Engine.setDsp` → `factory.setDsp(equalizerDspConfig(settings))`; `GainProcessor` = unity float pass-through placeholder. Media3 1.11.1 facts found in the bytecode (ADR-004 "Chain wiring"): float output + hi-res input skips custom processors (so float output stays off; 24/32-bit is cut to 16-bit before the chain, pre-existing on Media3, listed for the owner); `setAudioProcessors` appends 16-bit-only SilenceSkipping, which rejects our float output (so a custom chain). Round 1 → migration-guard NEEDS CHANGES: lost-update race between `newPlayer`'s read+apply and a concurrent `setDsp`; round 2 added a lock and asserted the ignored `awaitEnded` results. Device (`.qa`, CPH2307): `Media3DspTest` 5/5 — band-centre tones vs golden ±0.1 dB at 44.1/48 kHz, preamp/width, live change on two players + seeding of a third; CPU no chain 11.2 / neutral 9.6 / full 19.2 points (neutral ≤ no chain + 1). Regression: Media3EngineCoreTest 12, GaplessTest 7, RegistryTest 3, ErrorAttributionTest 2 all OK. migration-guard PASS (round 2). Gate PASS. |
| T046 shared session + limiter | deepseek-v4-pro (`ses_ee90a8746ffemX2TezstMSIB86`, briefs `~/.local/state/sistrum-delegate/T046/brief-1.md`…`brief-3.md`) | 3 | PASS. New `LimiterSession.kt`: pure `LimiterConfigs` (T003 params; "off" = ratio 1 / threshold 0, limiter kept in the chain), `LimiterController` state machine behind a `LimiterEffect` interface, DynamicsProcessing adapter, `LimiterSession` (session id, OPEN/CLOSE broadcasts). `Media3Engine`: one session per engine lifetime, set before `prepare()`, verified after READY (≤ 3 s) or prepare fails; `setNormalization` → clip-prevention flag; `Media3PlayerFactory.limiterState` for T047. `Media3Engine.kt` added to the task's file list (the ready check lives in `prepare`). Round 1 → orchestrator review: unguarded effect calls could throw into `prepare`, regain didn't enable an instance created without control. Round 2 fixed both (+4 tests). migration-guard NEEDS CHANGES: the "late callbacks after release" assertion could not fail. Round 3 strengthened it and added a session-id-generation fallback. migration-guard PASS (round 3). Device (`.qa`): `Media3LimiterTest` 2/2 — player on the shared session, OPEN/CLOSE with session id + package, `LimiterState(available=true, controlled=true)` on the CPH2307; Core 12, Gapless 7, Registry 3, ErrorAttribution 2 OK; `Media3DspTest` 5/5 on the round-2 tree (round 3 touches only the session-generation failure path). `LimiterConfigTest` 15 JVM tests. Gate PASS. Possible-mute detection method recorded in ADR-004 "Session and limiter wiring"; its real-device behaviour with an EQ app is a T048 check. Note: `setNormalization` is not yet called by the coordinator (normalization wiring is M6), so the clip-prevention setting reaches the limiter once M6 wires it; until then the limiter runs with clip prevention on (the setting's default). |
| T047 limiter note | deepseek-v4.1-flash (`ses_ee8e1d79cffemN2wJvCaGfyWT9`, brief `~/.local/state/sistrum-delegate/T047/brief-1.md`) | 1 | PASS. Note under "Prevent clipping" (Volume normalization page): "unavailable" when the limiter can't be created, "not controlled" when another app holds the effect, nothing otherwise (or before any Media3 session reports). File list widened beyond tasks.md (UI + strings): `LimiterState` lives in the service's `Media3PlayerFactory`, so a process-wide `player/media3/LimiterStatus.kt` (fed by the factory) → `MainActivity` → `SettingsDestinationModel.limiterState` → `AppDestinationContent` → `VolumeNormalizationContent`. Strings en + ar. New `LimiterNoteTest` (pure mapping). `ArabicTranslationCompletenessTest` in the full suites. migration-guard PASS. Gate PASS (memory was 1.76 GB at the first check; waited until 4.2 GB before the gate). Not checked on a device (visual). |

### M5 automated tasks done — stopped at T048 (MANUAL)

Open items for the owner / later milestones:
- Hi-res (24/32-bit) is reduced to 16-bit before the DSP chain (Media3 1.11.1 constraint, ADR-004 "Chain wiring"); pre-existing on Media3.
- `Media3Engine.setNormalization` is not called by the coordinator yet → the "Prevent clipping" switch does not reach the Media3 limiter until M6 wires normalization (T052 area); the limiter runs with clip prevention on meanwhile.
- `LimiterStatus` keeps the last state after the service stops (no reset); harmless, note for T048.
- Possible-mute detection on control loss (ADR-004 "Session and limiter wiring") is verified on the device only by T048.

### T048 — in progress (2026-10-07, owner: "Let's do T048 now … run the dumpsys captures yourself")

- Prep: current `.qa` (with T047) built and installed (`aapt2`: `me.misa198.airmedy.dev.qa`); `t048_tone1k_-0.5dBFS_16_48k.wav`
  and `t048_hires_24bit_96k.wav` pushed to `/sdcard/Music/SistrumTestCorpus/` (daily app blocklists it), QA library rescanned.
  The orchestrator drives the QA app over adb (`uiautomator dump` + taps, each tap refuses unless the QA app is in front).
- Engine selection (About → Developer): Media3. Volume normalization page: no limiter note (expected).
- **Hi-res routing + shared session (SC-015 single-track part): PASS.** Playing corpus `flac_24_96`: track 1118, session 9489,
  PCM float, 96 kHz stereo, active on mixer thread `AudioOut_1D` (speaker, 48 kHz, HAL 24-bit) — no direct output. On
  session 9489, DynamicsProcessing (effect 707) on the same thread, enabled, controlled by the QA app (priority 0), float.
- **Finding (FR-052 frame duration):** the thread processes 1920 frames per cycle (40 ms); the limiter's preferred frame
  duration is 4 ms (`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`). Not equal to the real buffer duration.
- SC-015 summed overlap (two clipping tones in a crossfade): not testable until Media3 crossfade (M7) → moved to M7.
- No EQ app installed on the CPH2307 (no Wavelet/Poweramp EQ) → EQ-app interplay not run yet.
- Hi-res analysis for the owner (Media3 1.11.1 sources): see the conversation report; options A (ForwardingAudioSink wrapper,
  float end to end) / B (fork DefaultAudioSink) / C (neutral bypass via player rebuild). Truncation is undithered. Awaiting decision.

### Owner decisions 2026-10-07 (after the hi-res report)

1. **Hi-res: option A** (wrapper sink, float end to end) + ADR-004 amendment ("Float sink amendment"). New M5 tasks
   T048a (tests first) → T048b (implementation, high-risk model). Extra requirements, all in T048b:
   - Routing re-check on the CPH2307 (dumpsys): both players' tracks on the normal mixer thread on the shared session,
     DP in that chain, for 16-bit, 24/96 and 32-bit float. **Float routed to a direct/hi-res output → STOP, tell the owner.**
   - CPU and memory: float chain vs current int chain (T045 method).
   - ADR-004 notes that A removes the hard clip of float decoder output above 0 dBFS before our gain stages. (Done.)
   - FR-052: don't hardcode 40 ms; read the real block size of the session's output thread (differs per device and after
     A) and set the limiter's frame duration from it.
2. **EQ-app check:** automated imitation test now (T048c). Real apps (Wavelet etc.) = optional owner check pre-v1.0.
3. **Listening checks:** batched after A. Orchestrator sets up the app state for each and guides one at a time:
   extreme EQ vs native; no clicks while dragging sliders; loud material through the limiter; "playback doesn't go
   silent" during the imitation test. These close the M5 sign-off. Then Checkpoint M5.

**After the owner's M5 approval, BEFORE running any M6 task — redesign normalization like original Airmedy (loudness
measured on device, not taken from tags):**
- M6-1 Loudness analyzer INTO 001 (M6): Kotlin port of WaxFlow `dsp/loudness` (BS.1770 integrated loudness, true peak)
  with the attribution header per `docs/waxflow/ORACLE.md`; validated with EBU test vectors and the WaxFlow oracle (Go is
  installed: `scripts/waxflow-oracle.sh` on the T006 corpus and WaxFlow's testdata → loudness fixtures; commit only the
  fixtures file). Background job: WorkManager, per-track bounded work (10-minute worker limit), checkpointed, only
  new/changed files, prefers charging/idle, decodes through the Decoder Registry, separate from playback, in float
  (same path as A). Writes `loudness_lufs` and `true_peak` into `sync_documents` in the shape the existing gain lookup
  reads. Mood features stay out of 001.
- M6-2 GainSource precedence: measured loudness first; ReplayGain tags (Rhythm port, with REPLAYGAIN_REFERENCE_LOUDNESS)
  only for tracks not yet analyzed; then unity gain + untagged pre-amp.
- M6-3 Album mode: album loudness measured over the album as a whole (BS.1770 gating across all its tracks), not
  averaged. Store per-track data that allows computing album loudness later without re-decoding; recompute when an
  album's tracks change.
- M6-4 Keep: gain processor, ramps, clip prevention with true peak, NormalizationPreferences as the UI contract, tag
  parsing (now the fallback). Wire "Prevent clipping" off to the engine (currently ignored on Media3).
- M6-5 Update constitution, spec (US6 + FR-04x), ADR-005, data-model.md, gain-source contract, M6 tasks,
  analyzer-future.md (loudness moved into 001; mood stays later).
- **STOP before running any M6 task and show the owner:** the new M6 task list (kept / added / changed); estimated
  first-analysis time for the owner's 775-track library on the CPH2307 and the battery impact; how album loudness is
  stored and recomputed.

**Before M7 starts:** list every crossfade behaviour from the native engine (`ffmpeg_player.cpp`, the coordinator, the
M2 characterization tests) and from Airmedy's `catalog/player/README.md` (curve, swap at fade start, gain staging,
duration rule, repeat-one, artwork blend, last-duration memory, pause/skip/seek during a fade); map each to the M7 test
or task covering it, plus the 0.46–0.69 s fade-lead compensation from S1 and the SC-015 summed-tone check (moved into
M7). Show the owner anything not covered before starting M7.

### M5 float-sink tasks (owner decisions 2026-10-07)

| Task | Model | Rounds | Result |
|---|---|---|---|
| T048a float sink + block-size tests first | deepseek-v4.1-flash (`ses_ee8b29b93ffeaJanqPfK0W66Uo`, briefs `~/.local/state/sistrum-delegate/T048a/brief-1.md`, `brief-2.md`) | 2 | PASS (expect-fail, method-level: 11 of 12 `FloatChainAudioSinkTest` and 6 of 10 `MixerBlockEstimatorTest` fail on assertions against stubs `FloatChainAudioSink.kt` (pure `ForwardingAudioSink`) and `MixerBlockEstimator.kt` (null)). The 5 that pass against the stubs pin behaviour equal to pass-through/null: `floatAboveFullScaleIsNotClipped`, `tooFewStepsGiveNull`, `noDominantStepGivesNull`, `noProgressGivesNull`, `dominantClusterBelowHalfGivesNull` (so tasks.md's whole-class `--expect-fail` was replaced by method entries). Round 1 → orchestrator review: the ≥ 50 % rule untested, non-PCM → PCM re-enable untested, `hasPendingData` false after delivery unchecked; round 2 added `dominantClusterBelowHalfGivesNull`, `clusterAtExactlyHalfIsAccepted` and the two assertions. migration-guard NEEDS CHANGES: two inputs were not whole stereo frames (3 floats, 11 int32), which a correct chain (whole frames only) would fail; **orchestrator trivial fix** (one sample value added to each), migration-guard PASS. Non-blocking notes for T048b: no tests for `timeline`/`mediaPeriodId` copy, `reset()` forwarding, `isEnded()` with pending output, non-PCM format support; implement them per the brief anyway. Gate PASS. |
| T048b float chain end to end | deepseek-v4-pro (`ses_ee8a2ea10ffe2pVz27dzXTfLuR`, briefs `~/.local/state/sistrum-delegate/T048b/brief-1.md`…`brief-3.md`) | 3 (+1 owner-approved) | Rounds 1–3 (round 4 below). Stopped after round 3: 3 review rounds used and the fix needed a T048a test change → owner chose "fix both, one more round". Round 1 → orchestrator: empty pipeline (float input, no active processor) returned true without consuming = silent drop; probe thread leaked per factory. Round 2 fixed both (+`FloatChainAudioSinkPassthroughTest`). migration-guard NEEDS CHANGES + device crash `AudioTrackAudioOutputProvider accessed on multiple threads` (one provider shared by all players' sinks; would also break preload). Round 3: provider per sink, limiter re-create failure → 5 s probe (+test), probes cancelled on release, routing test asserts instead of repairing. migration-guard PASS. Gate PASS. Device (`.qa`): `Media3FloatPathTest` 5/5 — neutral chain bit-exact vs no chain (16/24/32-bit int, float), 24-bit fraction finer than 16-bit 0.996 float vs 0.0 int, 96 kHz golden ±0.1 dB, **routing PASS** (af captures in the scratchpad: both float tracks on `AudioOut_1D` type MIXER, speaker, deep buffer, 48 kHz/24-bit HAL, session 9721, DynamicsProcessing enabled on it with 2 active tracks, for 16/48, 24/96, f32), mixer block measured 40.0 ms → limiter frame duration 40.0 ms. Dsp 5/5, Limiter 2/2, Core 12/12, Registry 3/3, ErrorAttribution 2/2. **Two problems:** (1) `Media3EngineGaplessTest.corpusMp3AdvancesGaplessly` FAILS: silent run 1921 > 1152 frames. Cause: Media3 trims encoder delay/padding in the inner sink, now AFTER our chain and the test tap (int path trimmed before), so the tap sees MP3 priming/padding; AudioTrack output itself is still trimmed. (2) CPU (T045 method, 120 s): float neutral 23.6 / float full 26.1 / int neutral 7.8 / int full 16.7 points; Media3DspTest float noChain 22.2 (T045 int noChain ~10). PSS equal within 1 MB (93.9 / 95.2 / 93.1 / 94.4 MB). simpleperf on `ExoPlayer:Playback`: the cost is Media3 `ToFloatPcmAudioProcessor` per-sample DirectByteBuffer conversion (getShort/putInt/isNaN/floatToIntBits) running largely interpreted in the debuggable build (QA and the daily devDebug are both DEBUGGABLE). Proposed fix (owner decision pending): trim in the wrapper before the chain (TrimmingAudioProcessor is public; inner sink gets delay/padding 0; changes T048a `configureRewritesFormatToFloatAndCopiesConfig`'s delay/padding expectation) + replace ToFloat with our own bulk array conversion (same formulas, bit-exact). |
| T048b round 4 (owner-approved, brief `~/.local/state/sistrum-delegate/T048b/brief-4.md`) | deepseek-v4-pro (same session) | 4 | **PASS.** `FloatChainAudioSink` pipeline = `TrimmingAudioProcessor` → new `player/dsp/PcmToFloatProcessor` (bulk 16/24/32-bit LE → float, Media3's formula, bit-exact) → `ToFloatPcmAudioProcessor` (fallback for 8-bit/BE/double) → chain; encoder delay/padding trimmed before the chain, inner sink gets 0/0. **Orchestrator test change (owner-approved):** `FloatChainAudioSinkTest.configureRewritesFormatToFloatAndCopiesConfig` expects delegate delay/padding 0 (was 576/1000). Orchestrator trivial fixes: `duplicate()` before `order()`, `const FACTOR`. New `PcmToFloatProcessorTest` (6), `FloatChainAudioSinkTrimTest` (2). migration-guard PASS. Gate PASS. Device (`.qa`): Gapless 7/7 (MP3 fixed), FloatPath 5/5, Dsp 5/5, Limiter 2/2, Core 12/12, Registry 3/3, ErrorAttribution 2/2. **Routing re-check PASS** (16/48, 24/96, f32: two float tracks each on `AudioOut_1D` MIXER deep-buffer speaker, shared session 10793, DynamicsProcessing enabled; limiter frame duration 40.0 ms = measured block). CPU points (120 s, T045 method): float neutral 13.7 / full 20.3 vs int neutral 7.8 / full 16.7; Media3DspTest (float) noChain 11.1 / neutral 9.6 / full 18.3 (T045 int: 11.2 / 9.6 / 19.2). PSS float 94.5 / 94.3 MB vs int 91.7 / 92.5 MB. **Open (non-blocking, migration-guard note):** the inner sink's presentation-time check now sees PTS ahead of its frame count by delay + held-back padding (int path subtracted its own trimmed count); < 200 ms for MP3/AAC at 44.1/48 kHz, but low-rate AAC (8–16 kHz podcasts/audiobooks) can exceed it → one non-fatal `UnexpectedDiscontinuityException` + one-time position jump at start. Possible follow-up: wrapper passes a PTS matching delivered frames. Not device-tested (no low-rate AAC in the corpus). |
| T048c EQ-app imitation | deepseek-v4.1-flash (`ses_ee8343287ffeBQCluqeuk3aD60`, brief `~/.local/state/sistrum-delegate/T048c/brief-1.md`) | 1 | PASS. New device test `Media3EqAppImitationTest`: a competing DynamicsProcessing (priority 1000, neutral limiter) on the engine's shared session. Device (`.qa`, silent, phaseSeconds 3): baseline (true,true) no note → competitor takes control (true,false) "not controlled" note (immediate) → competitor disables (false,false) "unavailable" note after 51 ms, still (false,false) after a 7 s hold (probe can't take control under higher priority) → competitor releases → (true,true) no note after 907 ms; playback advanced ≥ 1.5 s per 2 s window in every phase, no engine errors. Args `audible=true` + `phaseSeconds=15` = the owner's "playback doesn't go silent" listening check. migration-guard PASS. Gate PASS. |

### Owner decision 2026-10-07 — M5 listening session skipped

Owner: "Skip the M5 listening session." Per check:
1. Extreme EQ vs native → **closed on evidence**: device band-centre measurements against the native-engine golden,
   ±0.1 dB (T045 `Media3DspTest` 44.1/48 kHz; T048b `Media3FloatPathTest` 96 kHz, float chain).
2. Slider clicks → **closed by an automated test**: no full-chain test existed (`DspProcessorRampTest` drives single
   processors with constant input), so new task T048d `DspSliderDragTest` renders through `FloatChainAudioSink` with the
   real chain while EQ/preamp/width change between buffers; threshold on the second difference vs static renders, plus
   a negative control. Check 2 closes when it passes.
3. Loud material through the limiter (with SC-011) and 4. "playback doesn't go silent" during effect-control loss
   (`Media3EqAppImitationTest -e audible true -e phaseSeconds 15`) → **moved to M7's owner listening session (T061)**,
   together with SC-015's two-tone crossfade check, so the owner does one session for all. Reason: no tool on the
   CPH2307 measures audible output (T003b).
T048 is closed on this basis (device parts done: hi-res routing + shared session, EQ-app interplay by imitation).

| Task | Model | Rounds | Result |
|---|---|---|---|
| T048d slider-drag test (+ EQ fix) | deepseek-v4.1-flash (`ses_ee82b1f45ffeZp3yQlzTl39A3e`, briefs `~/.local/state/sistrum-delegate/T048d/brief-1.md`…`brief-3.md`) | 3 | PASS. New `DspSliderDragTest` (7): renders through `FloatChainAudioSink` with the real chain while EQ/preamp/width change between buffers; metric max \|second difference\|, threshold 1.5× the static renders at the drag's start/end/extreme settings + 1e-5; negative control (unramped 1 dB step) exceeds it 18×. **Round 1 found a real click**: one band ±12 → 0 dB (2.8× static) — `BiquadEqualizer` interpolated all five coefficients toward identity (poles moved mid-ramp) and zeroed state at the end. Round 2: 0 dB transitions blend only the numerator with frozen poles (b == a), then drain. migration-guard NEEDS CHANGES: draining used the live input, so float rounding in low bands (~1e-4) kept bands alive forever (never bit-exact, neutral fast path lost). Round 3: input-free drain recurrence, 1 s cap, draining bands untouched by other bands' drags; new `BiquadEqualizerDrainTest` (3): 32/64/125 Hz bands deactivate in 256/171/85 ms while playing, bit-exact after, no click at drain end. Orchestrator test tightening: drain must finish < 0.6 s (proves decay, not the 1 s cap). Slider table after fix: all scenarios ≤ threshold (fast drag 1.31e-3 vs 2.01e-3). migration-guard PASS. Gate PASS. Device (`.qa`) on round 2: band-centre 44.1/48 kHz, preamp/width, live change, 96 kHz golden, neutral bit-exact all OK (steady state unchanged; round 3 changes only drain internals, covered by JVM tests). **Closes T048 check 2.** Non-blocking notes: drag test's random walk rarely hits exactly 0 dB; a band still blending to 0 dB restarts its 20 ms blend while other sliders move with < 960-frame buffers (CPU only). |

T048 closed 2026-10-07 on the owner's decision above (checks 1–2 closed on evidence/tests; 3–4 moved to T061).

## Checkpoint M5 (2026-10-07) — approved by the owner 2026-10-08

US7 delivered on Media3: EQ (10 bands, native formula), stereo width and preamp as our own float processors with
20 ms click-free ramps; each player runs Gain → Width → EQ → Preamp in float on the decoder's full resolution inside
`FloatChainAudioSink`, float PCM to the AudioTrack; one shared audio session per engine with a DynamicsProcessing
limiter-only stage (T003 parameters; "off" = neutral; control loss handled; frame duration from the measured mixer
block, 40 ms on the CPH2307); limiter state note in settings (en + ar).

Commits: 71a6b73 T042, 30199f3 T043, b048054 T044, 67f2b5e T045, e179a39 T046, fd50a53 T047, a967a47 docs (option A),
36c6c34 T048a, 8188fa8 T048b, 4215147 T048c, db9bbb1 T048d + T048 closure.

Evidence:
- Golden: EQ coefficients/response match the native engine at 44.1/48/88.2/96/192 kHz (JVM, max 1.5e-6 dB); device
  band-centre tones ±0.1 dB at 44.1/48 kHz (int path, T045) and 96 kHz (float path, T048b).
- Float path: neutral chain bit-exact vs no chain for 16/24/32-bit int and float files; 24-bit detail kept (99.6 % of
  samples finer than 16-bit vs 0 % on the old int path); float above 0 dBFS not clipped before our stages.
- Routing (SC-015 single-track part + hi-res check): both players' float tracks on the MIXER thread `AudioOut_1D`
  (deep buffer, speaker) on the shared session with the DynamicsProcessing enabled, for 16/48, 24/96, f32 — no
  direct/hi-res output.
- Effect-control loss: imitation test — not controlled → unavailable (no takeover under higher priority) → regained;
  playback never stopped.
- Slider drags: no discontinuity above threshold through the full chain (after the T048d EQ fix).
- All Media3 device classes pass on `.qa` (Core 12, Gapless 7, Registry 3, ErrorAttribution 2, Dsp 5, Limiter 2,
  FloatPath 5, EqAppImitation 1). Full unit suites + build green at every commit.
- CPU (120 s, debuggable `.qa`): float neutral 13.7 / full 20.3 vs int neutral 7.8 / full 16.7 points; Media3DspTest
  float noChain 11.1 / neutral 9.6 / full 18.3 (T045 int 11.2 / 9.6 / 19.2). PSS within 3 MB.

Moved / open:
- To T061 (M7 listening session, one session): loud material through the limiter incl. SC-011; "playback doesn't go
  silent" during the audible imitation run; SC-015 two-tone summed crossfade.
- `Media3Engine.setNormalization` not yet called by the coordinator → "Prevent clipping" off doesn't reach the Media3
  limiter (on by default) — M6-4.
- `LimiterStatus` keeps the last state after the service stops (cosmetic).
- Low-rate AAC (8–16 kHz): possible one-time non-fatal position jump at start (inner-sink PTS check after trimming in
  the wrapper); not device-tested (no such corpus file).
- Real EQ apps (Wavelet, Poweramp EQ): optional owner check before v1.0.
- Lint not run in M5 gates (not required per task; known MissingTranslation baseline).

Owner approval 2026-10-08 ("Checkpoint M5 approved"), carried forward:
- **M8 / CPU**: float neutral 13.7 vs int 7.8 points (debug build). Measure on a release build in M8 against
  SC-003/SC-006 (T062 note). If Media3 with crossfade off still costs more than native, optimize, e.g. 16-bit input
  with a neutral chain skips the float conversion entirely.
- **Pre-v1.0**: stale limiter state in settings after the service stops (added to "Pre-v1.0 review phase").
- **Corpus**: add an 8–16 kHz AAC file when convenient (low-rate AAC position jump); not a blocker (T062 note).

## M6 normalization redesign (2026-10-08) — STOP for the owner's review

Docs changed (M6-5): constitution ("In scope": measured loudness; "Out of scope": Mood analyzer only; dependencies:
WorkManager, WaxFlow notice with the meter port), spec (US6 rewritten with scenarios 11–14, FR-040…FR-047h,
SC-004, new SC-017/SC-018, edge cases, assumptions, clarification session 2026-10-07), ADR-005 amendment,
`data-model.md`, `contracts/gain-source.md` (GainForm.Measured, GainResolver), new `contracts/loudness-analysis.md`,
`research/analyzer-future.md` (Mood only), `plan.md`/`research.md` D9 cross-references, `tasks.md` M6.

### Facts found while writing it (they shaped the design)

- **Every scan creates a new plan and deletes the old plan's `sync_documents`** (`writeLocalLibrary`:
  `deleteStaleDocuments(planId)`). Analysis rows would vanish at each rescan, so T051c copies them into the new plan
  when the file's fingerprint (`identityHash(path|size|mtime)`, already the audio asset's `sha256`) is unchanged.
- The existing read side wants `true_peak` in **dBTP** (`min(gain, -truePeak)`), and `toFloatOrNull()` would accept
  "-Infinity": silent/short files therefore get a row **without** `loudness_lufs`/`true_peak`, so the reader skips
  them.
- `PlaybackCoordinator.normalizationGain()` (native lookup) runs for both engines and **turns normalization off when
  no analysis exists**. On Media3 a user enabling it before the first analysis would see it switched off at the next
  track. T052 runs that path only for the native engine (call sites; the function itself is not touched).
- `Media3Engine.setNormalization` exists, but nothing calls it; that is why "Prevent clipping" off never reached the
  limiter (T052).
- Native album mode = **average** of track LUFS, only when the next track is on the same album; Media3 uses whole-album
  gating whatever the queue (FR-043). Native code unchanged; once rows exist the native engine normalizes again
  with its old rules.
- WaxFlow's `loudness.Group` measures an album by pooling each member's gated 400 ms block powers (one float64 per
  100 ms). Storing raw blocks would cost ~7 MB for this library and ~190 MB for 20 000 tracks, so we store a
  bounded histogram instead (below).

### New M6 task list (tasks.md)

| Task | Status | What |
|---|---|---|
| T049 | kept | Tag parsing tests (tests first) — tags are now the fallback |
| T050 | changed | Gain math + **GainResolver** precedence + ramp tests; Measured form; "Prevent clipping" off = no cap |
| T050a | new | Meter tests first: generated EBU Tech 3341 cases, WaxFlow's synthetic cases, true peak, chunking, histogram track/album accuracy, checkpoint resume, oracle rows |
| T050b | new | {orchestrator} WaxFlow oracle fixtures (+ album group rows); commit fixtures + script change only |
| T051a | new | Meter port (WaxFlow `dsp/loudness` + `firwin`, `446ca31`) + our histogram; WaxFlow MIT notice — {hard} |
| T051 | changed | TagGainSource (Rhythm port) + **MeasuredGainSource** + **GainResolver** + ItemGainMath — {hard} |
| T051b | new | Store/planner tests first (Room on `.qa` + JVM): existing reader reads our rows; rescan carry-over; album membership/completeness/recompute |
| T051c | new | LoudnessStore, AlbumLoudnessPlanner, DAO queries + rescan carry-over in `SyncDatabase.kt` (no schema change) — {hard} |
| T051d | new | [MANUAL] Offline decode through the registry (float) + **device benchmark** per format, little and big cores; STOP if outside the estimate range |
| T051e | new | Job tests first (WorkManager `work-testing`): constraints, 8-min budget, checkpoint, pending-only, failures, planner run |
| T051f | new | LoudnessAnalysisWorker + scheduler, enqueue after scans; WorkManager notice — {hard} |
| T052 | changed | Engine gain from GainResolver; **coordinator calls `setNormalization`**; native lookup only on native; no mid-track source change — {hard} |
| T053 normalization settings UI | Codex gpt-6.1-sol medium (session `01a134cc-7c60-7b81-b7cc-df32ded9074c`, brief `~/.local/state/sistrum-delegate/T053/brief-1.md`) + orchestrator | 1 | PASS. `NormalizationSettings.untaggedPreampDb` (last field, default 0; −15…+15 dB, 0.5 dB slider, persisted) reaches the engine via setNormalization/StreamGainControl; settings enabled only with Media3 selected (native: disabled + note; auto-disable only on native; null-while-loading never disables); progress line (plurals, loads from the store on open) + "continues while charging"; Analyze now ("Uses battery") ↔ Stop via `nowRunning` (WorkManager unique-work flow); POST_NOTIFICATIONS asked once on tap (13+). Scope added by coder with reason: AppDestinationModels.kt, ui/navigation/AppDestinationContent.kt (callbacks pass through navigation); orchestrator added sharedLogic VolumeNormalization.kt and LoudnessAnalysisScheduler.kt in the brief. migration-guard PASS; orchestrator fixed the Arabic plural nouns (مسار/مسارين/مسارات/مسارًا, as the existing plural). Gate PASS; lint +11 MissingTranslation only (other locales lag, allowed). Device `.qa` (CPH2307, API 35) — also closes the T051h device check: Analyze now → FGS notification on `loudness_analysis` with Cancel, progress 0→1→12/766 (UI line updates), Cancel → notification gone in 67 ms and stays gone, UI back to "Analyze now", 12 tracks kept, background job enqueued (CHARGING/BATNOTLOW/STORENOTLOW, READY). Opus analysis is slow on platform decoding (as T051d). |
| T054 device normalization checks (automated parts) | Codex gpt-6.1-sol medium (session `01a134de-29df-7e72-a0bd-ee5472d61f31`, brief `~/.local/state/sistrum-delegate/T054/brief-1.md`; harness only) + orchestrator | 1 | PASS (automated parts; listening deferred). New harness `androidTest/…/analysis/NormalizationLevelDeviceTest.kt` meters GainProcessor output per corpus file (target −14, clip prevention on). Orchestrator fixed two harness expectations after the first device run (spec-backed, not weakening): the ≤ 0.1 dBTP check applies only when a peak is known (untagged/no-peak items can't be capped; FR-044 leaves them to the session limiter, covered by SC-011/Media3LimiterTest), and `opus_r128_header_plus4` expects −10 LUFS (its R128 tag predates the OpusHead +4 dB patch; decoder applies the header gain once, ADR-005). **SC-004** on `.qa` (CPH2307, API 35): `OK (2 tests)`, 76 file/case measurements. Measured case: every admitted file −14.000 ± 0.001 LUFS, true peak ≤ −5.8 dBTP. Tags case: all tag forms (ReplayGain, R128, SoundCheck, REPLAYGAIN_REFERENCE_LOUDNESS) within −14 ± 0.06 (header-gain fixture −10.2 vs −10); untagged get +4 dB (= untagged pre-amp 0 + target offset, contract). Not measurable on Media3 (scan-filtered on this device, as US8): ALAC ×2 (no decoder), AIFF-C fl32/ima4, MS ADPCM, WMA. **SC-017** (device store vs WaxFlow oracle, matched by file size): every analyzed corpus file PASS within 0.01 LU lossless / 0.1 LU lossy except `opus_r128_header_plus4.opus` (−6.216 vs −6.027 LUFS, TP −1.66 dB): the platform Opus decoder clamps output above full scale after the +4 dB header gain (device TP ≈ 0 dBTP, oracle +1.69 dBTP float). Platform deviation on a synthetic over-full-scale fixture, not our code; candidate for the 002 Kotlin Opus decoder. **Album records:** 50 `album_loudness` documents (loudness, true peak, members, fingerprint, analyzer version). **Rescan:** full "Scan again" (766 tracks) kept all 461 documents, none changed. **SC-018 sample** (in progress, phone on USB charge): 206/775 tracks analyzed (205 ok, 1 failed = MS ADPCM), 11.8 h of 49.8 h audio in 1942 s ⇒ ~22× real time ⇒ ~2.3 h of job time for the whole library; ~995 B stored per track; ALAC/WMA not analyzable on this device. batterystats/discharge NOT RUN (job runs only while charging; owner device session deferred). |
| T055 transition state machine tests first | Codex gpt-6.1-sol high (session `01a13501-881b-78e3-bb8d-94a8019baa83`, brief `~/.local/state/sistrum-delegate/T055/brief-1.md`) | 1 | PASS (expect-fail). Orchestrator-defined API stub `player/media3/TransitionController.kt` (phases, `SnapReason`, `TransitionSettings` with FR-031 hooks, `TransitionCommand` list returned per event; pure Kotlin) + `TransitionControllerTest` (38 tests, bullets 1–20 incl. audit gaps G3/G5): all 38 fail on assertions against the stub. migration-guard PASS (notes: retry suppression keyed by trackId; after an incoming error later ticks may be Idle or Scheduled). Gate PASS. |
| T056 equal-power fade tests first | Codex gpt-6.1-sol high (session in `~/.local/state/sistrum-delegate/T056/run-1/result.json`, brief `brief-1.md`) | 1 | PASS (expect-fail). Orchestrator-defined no-op stubs on `dsp/GainProcessor.kt` (`FadeDirection`, `startFade(direction, durationMs, startOffsetFrames)`, `snapFade()`, `fadeRemainingFrames`, top-level `fadeAudibleEndMs`) + `EqualPowerFadeTest` (26 tests: native float formula ±1e-6, total = max(1, ms·rate/1000), multiply with normalization incl. a ramp during the fade, snap ± flush, plain flush keeps position, 12 s step bound over mixed buffers, ± offsets, remaining frames, audible end = ceil((remaining+lead)·1000/rate), cross-thread start, 16-bit, role-swap reuse, restart). All 26 fail on assertions; existing dsp tests green. migration-guard PASS (note: T058 must keep ramp-advance-before-apply order). Gate PASS. |
| T057 TransitionController | Codex gpt-6.1-sol high (session `01a13510-8ad2-79c0-88dd-8d6761e83f45`, brief `~/.local/state/sistrum-delegate/T057/brief-1.md`) + orchestrator | 1 | PASS. Pure state machine, `TransitionControllerTest` 38/38 unmodified. **Orchestrator fix:** failed-incoming suppression was a set that never cleared (a track that failed once would never be crossfaded into again); now only the last failed trackId, cleared when a different next item appears. Gate PASS (EqualPowerFadeTest still expected-fail until T058). |
| T058 fade in GainProcessor | Codex gpt-6.1-sol high (session `01a13515-b476-7c93-8175-01e8adc453cb`, brief `~/.local/state/sistrum-delegate/T058/brief-1.md`) + orchestrator | 1 | PASS. Immutable fade requests via `AtomicReference` (snap = copy with `snapped`, so even an unobserved start snaps), audio thread owns the curve; snap consumed in `onFlush`, plain flush keeps position; per-frame gain = ramp × curve. EqualPowerFadeTest 26/26, GainRampTest/DspSliderDragTest unchanged and green. **Orchestrator fix:** a finished fade-in now clears the fade so the (new current) player gets the bulk-copy fast path back (it stayed on the per-sample loop for the rest of the track). Gate PASS (full suites). |
| T059 dual-player crossfade | Codex gpt-6.1-sol high round 1 (session `01a1351e-87ac-73b1-a384-2f2a7d471445`, briefs `~/.local/state/sistrum-delegate/T059/brief-1.md`, `brief-1b.md` = resume after a low-memory kill); **round 2 done by the orchestrator** (owner 2026-10-13: "do it here instead of codex", while the Codex 5-hour window was at 85%; `brief-2.md` = the findings list) | 2 rounds | Round 1: migration-guard NEEDS CHANGES (B may never start when A never observes the fade request; 10 s deadline released a ready B during a pause) + device failures (audible start offset 77.8 ms > 50; snap check). Root causes: (a) the 33 ms constant was ADR-003 S1's play→first-processed-buffer time, not play→audible; the real output start latency is 80–140 ms → now measured on every start of a player (probe: play call → audible clock leaves its start, 5 samples) and B plays at lead − latency, posted at the exact time; (b) the snap test counted B's fade-start flush (curve armed) when it landed after the generation capture → helper now checks every window of every stream flushed after the snap. Also: fallback lead when A never observes the request (ENDED / no processor / 200 ms) and `releaseOutgoing` starts a still-pending B; `incomingVerified` exempts a ready B from the deadline; lead clamped 0–1.5 s and `positionOriginMs` rebased by the finished item's duration at a gapless advance; SnapFade leaves a failed player alone (one Error); no OutputStarted at the swap (+ assertion); GainProcessor clock is a seqlock over @Volatile fields (no per-buffer allocation); per-slot mime keyed to its track for error attribution during B's re-prepare. New device test `readyIncomingSurvivesLongPauseAndStartsWhenFadeBegunAtTheEnd`. G2 sum check not added: the chain is linear and per player, and the settings test already proves B's EQ/preamp/width ratio equals A's. migration-guard round 2: NEEDS CHANGES (test only: snap helper too narrow; missing OutputStarted assertion) → fixed. Device (.qa): Media3CrossfadeTest 11/11 (audible start offset 11.2 ms, lead 616 ms), ErrorAttribution 2/2, Gapless 7/7, Core 12/12, Dsp 5/5, FloatPath 5/5, Limiter 2/2, Registry 3/3 (final run: offset 29.2 ms). Gate PASS.  Open notes: the gapless origin rebase assumes no processor flush at the item boundary (a format change flushes; only the clamp catches it); the forced start in `releaseOutgoing` doesn't wait for the FR-036 session check (edge path). |
| T060 coordinator crossfade on Media3 | Codex gpt-6.1-sol high (session `01a1358b-ee9d-7d53-9c6b-71421eb3a8d7`, brief `~/.local/state/sistrum-delegate/T060/brief-1.md`) | 1 round | `setCrossfade(seconds·1000, true)` on every prepared engine and on a seconds change (FR-092: not on identical writes); `maybeStartCrossfade` returns false when the engine refuses (no swallowed Ended); next/previous/pause/seek/stop/queue edits snap first (`snapActiveCrossfade`), previous's 3 s rule reads the incoming position; `preloadNext` guarded by `canPreloadNext`; overlap length from `TransitionStarted.fadeMs` (also right with artwork blend off). Behaviour change, accepted (FR-030 "queue edit"): `ActionAppend` now snaps a running fade on both engines. Fake modelling choice: FakePlayerEngine doesn't log Native `setCrossfade` to `calls` (the real native setter is a no-op), so T015's "settings during a fade cause no engine calls" stays meaningful for the native path; delivery is asserted via `crossfadeSettingsCalls`. New `PlaybackCoordinatorCrossfadeMedia3Test` 15 tests; T015 unchanged, green. migration-guard PASS. Gate PASS. |
| T060a crossfade settings (repeat-one, skip) | Codex gpt-6.1-sol medium (session `01a13596-da66-7e90-98b2-0f5c564ae73b`, brief `~/.local/state/sistrum-delegate/T060a/brief-1.md`) | 1 round | DataStore keys `crossfade_repeat_one` (default true) / `crossfade_on_skip` (default false), pure `crossfadeSettingsOf`/`apply*` helpers; two toggles under the length (visible, disabled at 0 s; skip disabled with "Requires the new playback engine" on native); en + ar strings; coordinator: `onPlaybackSettings(seconds, blend, repeatOne, onSkip)` de-duplicated on all four, `setCrossfade(seconds·1000, repeatOne)` at prepare and on a length/repeat-one change, `maybeStartCrossfade` skips repeat-one when off (native path), `crossfadeOnSkip` stored only (T060c). Tests: `PlaybackPreferencesTest` 4, `PlaybackCoordinatorRepeatOneCrossfadeTest` 7. migration-guard PASS; gate PASS. Note: the skip toggle's availability reuses `normalizationAvailable` (= Media3 selected). |
| T060b [TESTS-FIRST] crossfade-on-skip tests | Codex gpt-6.1-sol high (session `01a135a1-117d-70f1-81d5-705d16709ee0`, brief `~/.local/state/sistrum-delegate/T060b/brief-1.md`) | 1 round + orchestrator fix | API decided by the orchestrator and added to contracts/player-engine.md: `setCrossfade(.., crossfadeOnSkip = false)`, `suspend crossfadeTo(item, durationMs): Boolean` (Media3: on-demand B, ≤ 300 ms ready wait, no TransitionStarted, caller publishes; false → caller hard-cuts; native false); controller `onManualSkip(target)`, `onSkipReadyTimeout()` (stubs). `TransitionControllerSkipTest` 24 tests (all expected-fail), `PlaybackCoordinatorSkipCrossfadeTest` 26 tests (19 expected-fail, 7 pass today: hard-cut cases). migration-guard NEEDS CHANGES: `pausedSelectHardCutsAndStaysPaused` asserted a new semantic (tap while paused stays paused; today it starts playback; spec says paused → today's hard cut) → orchestrator trivial fix: renamed `pausedSelectHardCutsAndStartsPlayingAsToday`, expects `paused = false`. Accepted readings: a previous-past-3 s restart starts the 600 ms debounce window; ActionPlay is not a skip; skipping to the automatic B still preparing reuses it. T060c notes: a Pause-triggered HardCut must not start audio; no host test of Media3Engine.crossfadeTo itself (device test in T060c); restoreCurrent forwarding of crossfadeOnSkip untested. Expected-fail list: scratchpad `t060b_expect.txt` / commit body. Gate PASS (expected-fail). |
| T060c crossfade on skip | Codex gpt-6.1-sol high (session `01a135b2-bcdd-7931-ae44-bfd6b604f245`, briefs `~/.local/state/sistrum-delegate/T060c/brief-1..3.md`) | 3 rounds | Round 1: controller skip path, `Media3Engine.crossfadeTo` (on-demand B, ≤ 300 ms READY wait, HardCut → false, no TransitionStarted), coordinator skip gate (setting, length > 0, Media3, Playing, no fade, 600 ms debounce on elapsedMs) + publication like an automatic fade. T060b classes green unmodified. Orchestrator fixes: T060a test's skip-only expectation updated to the T060b contract (one setCrossfade call; migration-guard: justified); blend-off clears artwork during skip fades too; re-selecting the playing track hard-cuts; armStartProbe re-posts the tick; probe log. Device round 1: automatic timing failed 3/12 (61–69 ms). Root cause (logged): A's start latency (79–129 ms) underestimates B's start after re-prepare (109–145 ms; B probe validated against the test within ~10 ms). Round 2: silent warm-up probe of B when ready+verified (hold fade, cancelled on pause/seek/snap/stop/queue edit) → separate `incomingStartLatencyMs`; fades restart B at 0; `beginCrossfade` may wait ≤ 1 s off-looper for a running warm-up. Automatic timing 7/7 (−24..+19 ms); skip 5/6 (first skip without history 63.6 ms). Round 3: first skip without history warms up B (≤ 250 ms after READY). Device final: first-skip 7/7 (−26..+24), skip 4/4 (+4..+28), automatic −13 ms; all 51 Media3 device tests green (Crossfade 15, Gapless 7, Core 12, Dsp 5, FloatPath 5, Limiter 2, ErrorAttribution 2, Registry 3). migration-guard round 1 PASS; rounds 2–3 NEEDS CHANGES only for the contract's 300 ms line → orchestrator amended. Gate PASS. Rule slip: one device build started at 1.3 GB free (memory check not waited); every later build waited for ≥ 2 GB. |
| T061 [MANUAL] M7 checks (automated parts) | orchestrator | — | **US5 scenarios → evidence:** (1) fade at track end: T015 + `PlaybackCoordinatorCrossfadeMedia3Test` + device curves/events/lifetime; (2) pause/seek/stop/queue edit snap: T015, Media3Test (f), device snap test; (3) next = hard cut to i+2, previous 3 s rule: Media3Test (d), TransitionControllerTest; (4) repeat-one: TransitionControllerTest + `PlaybackCoordinatorRepeatOneCrossfadeTest`; (5) manual change = no fade (skip setting off): T015 + `PlaybackCoordinatorSkipCrossfadeTest`; (6) overlap split: T015 + Media3Test; (7) equal-power curve: device `gainStageTeeMatchesEqualPowerCurvesAndEnvelope` (2 s fade, error 2.5e-5); 12 s by ear → owner; (8) loud overlap limited → owner (below). **SC-015 (device, `dumpsys media.audio_flinger` mid-fade, scratchpad `t061_af_5.txt`):** both players' tracks (1084, 1086) active on the same output thread `AudioOut_1D`, session 25105, effect chain "Dynamics Processing" (AOSP) enabled on that session → PASS for routing + limiter; the "summed tones come out limited" half can't be tapped in-app (the limiter acts in audio_flinger) → owner listening. **SC-007: FAIL by analysis (not by measurement)** — native applies the fade gain in the AAudio callback (`ffmpeg_player.cpp:366`, lead ≈ output buffer, tens of ms); Media3 applies it on processed frames ≈ 600–700 ms ahead of the audible position (measured lead) and delays B by the same lead, so the audible fade starts ≈ 0.6 s later in the track than native (> 200 ms). Consequence (inferred): a fade decided with remaining ≈ N runs ≈ lead past the outgoing track's end; A's audio stops at cos((N−lead)/N·π/2) (≈ 0.24 at N = 4 s, ≈ 0.08 at 12 s) → possible audible cut. Skip fades: the outgoing curve becomes audible ≈ lead after the press. **Proposed fix (T061a, needs owner approval: ADR-003 amendment):** engine contract `fadeLeadMs()` (Media3: current processing lead, clamped 0–1.5 s; native 0); the coordinator decides and clamps with `positionMs() + fadeLeadMs()` (start window, duration, remaining) and the Media3 pre-buffer schedule uses the same; device test: natural end-of-track fade (10 s tone, 4 s fade) with A's tap reaching < −40 dB before A ends, and the audible fade start within 200 ms of the native-equivalent point. T061 stays open until then. |
| T061a [TESTS-FIRST] fade decision on the processed timeline | Codex gpt-6.1-sol high (session `01a1360e-bc3c-7683-b5b1-e3552e31aded`, brief `~/.local/state/sistrum-delegate/T061a/brief-1.md`) | 1 round | ADR-003 amendment 2026-10-13 + contract `fadeLeadMs()`; stubs (native 0 final, Media3 0); `PlaybackCoordinatorFadeLeadTest` 13 tests (8 expected-fail, 5 pass: lead 0, skip, short track). migration-guard PASS; gate PASS (expected-fail). Commit 18ecbe5. | DONE |
| T061b processed-timeline fade decision | Codex gpt-6.1-sol high (session `01a13616-d8ae-7122-953e-3910e363f314`, briefs `~/.local/state/sistrum-delegate/T061b/brief-1..4.md`) | 4 rounds (4th owner-approved 2026-10-13: "A") | Coordinator decides/clamps automatic fades at `positionMs() + fadeLeadMs()`; `Media3Engine.fadeLeadMs()` = max of the current slot's processing-lead readings over the last 500 ms (resets on slot/item change, prepare, seek, pause/resume, fade start/end; the processed clock steps in buffer-sized jumps, so single reads jitter ~100 ms); pre-buffer schedule on the processed timeline; `startFade` clamps automatic fades to A's processed remaining time (event reports the effective length). Round 2 fixed the orchestrator's own wrong tail criterion; round 3 added the end clamp (1/5 tail at −39.7 dB); round 4 the smoothing + test tolerances (audible start vs the decision point ≤ 100 ms; length within 150 ms). Gate PASS (host suites, build, Media3 device classes 16+12+7+5+2). New device test `endOfTrackFadeDecidedOnProcessedTimelineEndsWithTrackA` ×5: 5 PASS — audible start 10–77 ms before the decision point, 0–100 ms after `duration − fade` (200 ms service tick, same as native), no clamping (effective = requested), A fully faded at its end. migration-guard PASS. **SC-007 PASS.** | DONE |
| T062a rebrand | Codex gpt-6.1-sol high (session `01a1366f-a364-7791-934e-a65b2378af5a`, brief `~/.local/state/sistrum-delegate/T062a/brief-1.md`) | 1 round + orchestrator fixes | 422 Kotlin files moved to `com/amwolfstein/sistrum` (main 198, test 134, androidTest 71, shared 19); `Airmedy*` identifiers → `Sistrum*`; IDs/namespaces/rootProject; app_name "Sistrum" in all 14 locales; theme: dark primary #DAAA57 on #19140F (onPrimary dark, 8.6:1), light primary #8A5A1E (5.9:1 on white); notifications use new `ic_notification`. Orchestrator (assets/tooling, not feature code): icons generated from the owner's `branding/logo.png`/`icon.png` (adaptive foreground, themed monochrome, notification, legacy, Play Store, About), old Airmedy vector/PNG logos deleted, QA icon keeps the blue background; verify.sh allowlist → `com.amwolfstein.sistrum.dev.qa`; CLAUDE.md/constitution/AGENTS.md/README; trivial review fixes: User-Agent `Sistrum-Android/…` (outward-facing HTTP header to Last.fm and lyrics providers), animation labels and slider test tag `sistrum-*`; `SistrumPillButtonTest` dark-theme assertion now expects the dark foreground on gold (spec reason: rebrand palette; white on gold is 2.1:1). Kept on purpose: FfmpegDecoder.kt package (JNI) and its `GlobalDspConfig`/`NativeTransition` imports, native lib `airmedy_player`, Last.fm callback scheme `airmedy://lastfm/auth` (registered upstream), Keystore alias `airmedy.mobile.lastfm.session.v1`, About credit/sponsor URLs to upstream misa198 (**owner to decide**: the "Sponsor Sistrum" label now links to the upstream author's pages), DB file `library-sync.db`, DataStore/WorkManager/channel names. Verification: migration-guard PASS (216 test files present, renames only; Room/DataStore/work names unchanged). Gate: scope PASS (847 files), host suites PASS, build PASS, lint 458 = MissingTranslation + UnsafeOptInUsageError (known). Gate run was killed by the memory reaper during device tests (20 min wait, resumed via adb). Device (new `.qa`): analysis 3 classes, PlatformAudioDecoders, Media3 Crossfade/Dsp/EngineCore/Gapless/EqAppImitation/ErrorAttribution, sync ×6, StackPageLayout, Sistrum Dialog/MarqueeText/TextField/TrackSlider/PillButton, RtlLayout, Appearance, Integration: PASS. Failing with the SAME tests on the pre-rename `.qa` build (baseline, known test-side failures): AppNavigationTest 16/39, InsightContentTest 1/4 (chart empty series), SistrumBottomSheetTest 1/4, FullScreenPlayerTest 5/20, MiniPlayerTest 8/14. NormalizationLevelDeviceTest hit the 10-min per-class time-box (no result). NOT RUN (owner agreed to a targeted run, full sweep moves to T063): the other ~33 device classes; scan-and-play smoke on the fresh `.qa` (launch PASS, no crash, new theme visible). 002 note: `port/waxflow` / `port/analysis` need the same mechanical package rename when 002 is integrated. | DONE |
| T062b variants + rebrand follow-up | Codex gpt-6.1-sol medium (session `01a136c4-278f-73f1-8ecf-7b9ec9f81da2`, brief `~/.local/state/sistrum-delegate/T062b/brief-1.md`; run killed by the memory reaper after the edit, verified by the orchestrator) + orchestrator (owner requests 2026-10-13) | 1 round | Variants: devDebug, prodDebug, prodQa disabled (`androidComponents.beforeVariants`); left: devQa (tests), devRelease (daily), prodRelease (release); host tests/lint moved to devQa (`testDevQaUnitTest`, `lintDevQa`), tooling/docs updated (verify.sh, delegate skill, CLAUDE.md, AGENTS.md, quickstart, tasks.md legend). Owner follow-ups to T062a (follow-up commit, not an amend: ea34914 was already pushed and force pushes are blocked): version `1.0` / versionCode 1, X.Y scheme; "Sponsor Airmedy" label restored in all locales with the upstream links; About GitHub/License → `AMWolfstein/Sistrum` (+ GPL-3.0 `LICENSE` added, identical to gnu.org; the License link works once merged to `main`); **in-app theme restored to the original rose/zinc colours, gold only in the app icon** (SistrumTheme, SistrumPillButtonTest and TrackInfoContent comment back to pre-rebrand); adaptive foreground webp now draws the icon-coloured rounded square (69 %, like the original) with the logo inside, all densities; About icon cropped to the rounded square, transparent outside, 1024 px; `branding/` sources removed from the tree (restore from ea34914 to regenerate, see the script header); README rewritten (goal, status, license/credits; build guide moved to docs/dev-setup.md). Gate PASS (scope, host 1199 tests / 1 skipped / 0 failed, build devQa). Lint devQa 459 = 350 UnsafeOptInUsageError + 108 MissingTranslation + 1 UnspecifiedRegisterReceiverFlag in androidTest `Media3LimiterTest` (lint on devQa now covers test code; not app code). Device (.qa, versionCode 1 installed with `-d` over the old 11): About GitHub + sponsor link tests, SistrumPillButtonTest PASS; launch PASS with the original theme. | DONE |
| T062c default engine Media3 | Codex gpt-6.1-sol medium (brief `~/.local/state/sistrum-delegate/T062c/brief-1.md`) | 1 round | Owner 2026-10-13: "Flip default to Media3" (FR-002 amended). `parseEngineKind` null/unknown → Media3; saved Native/Media3 kept; MainActivity and Developer screen start on Media3 (normalization available on a fresh install; a saved Native still disables it as before). EngineFactoryTest updated per the amendment + new no-saved-choice test. migration-guard PASS; gate PASS. Device fresh-state check NOT RUN (qa app has saved state; covered by host tests). Signed prodRelease APK handed to the owner for the main phone (debug keystore). | DONE |
| T062 part A harness + measurements | Codex gpt-6.1-sol medium (session `01a136fd-981a-7683-9bbd-f4a48f7c84fe`, briefs `~/.local/state/sistrum-delegate/T062/brief-2.md`, `brief-3.md`; brief-1/run-1 pre-rebrand, abandoned) + orchestrator device runs | 1 (round 1: native `OutputStarted` fires on prepare return, so a comparable first-position metric was added) | `regression/Regression001MeasurementTest.kt` (4 methods, measurement only; SC-014 asserts attribution). migration-guard PASS; gate PASS (scope, full host suites, build devQa). Device: all four methods run on debug `.qa`; SC-006 + CPU also on a release-like `.qa` (temporary uncommitted `isDebuggable=false`). CPU runs 1–2 contaminated by the `.qa` analysis WorkManager job starting in the test process while charging → clean runs with `dumpsys battery unplug`/`reset`. Results and verdicts: `research/regression-001.md`. sc014 method fails by design on `wma_v2.wma` (blank provider) until T062e. T062 stays open (SC-013 1 h owner run; fixes T062d–h). | PART A DONE |
| T062d prepare-failure tests (tests first) | Codex gpt-6.1-sol high (session `01a1372e-eec6-7af3-bcfa-e07642f450b6`, brief `~/.local/state/sistrum-delegate/T062d/brief-1.md`) | 0 | `PlaybackCoordinatorPrepareFailureTest` (5 tests, expected-fail on assertions: log line, decode-failure entry, skip to b, cap ≤ 21, single-track recorded), `PlaybackCoordinatorPrepareFailureGuardTest` (3 passing guards: plain exception → Failed, engine closed for both kinds); seam `EngineDecodeException` in EngineEvent.kt; fakes: `failPrepareFor`, `prepareAttempts`, `FakeDecodeFailureSink` injected in the harness (previous default was a no-op sink). migration-guard PASS (note: guard asserts `calls.last() == "close"`, T062e must not touch the failed engine after close). Gate PASS with `--expect-fail PlaybackCoordinatorPrepareFailureTest`. | DONE |
| T062e prepare-time decode failures (implementation) | Codex gpt-6.1-sol high (session `01a13737-9446-7ab2-bbd8-411798afa176`, brief `~/.local/state/sistrum-delegate/T062e/brief-1.md`) | 0 | Media3Engine.awaitReady: player error code 3000..4999 before READY → `EngineDecodeException(provider ?: "none", format, cause)` captured before release; coordinator start + restore paths route it through `handleEngineError` (start path keeps a paused hold by setting `Paused(item, …)` when `startPaused`). T062d tests pass unmodified. migration-guard PASS. Gate PASS (scope, full host suites, build, device Media3ErrorAttributionTest 5/5). Device: Regression001 sc014 PASS (4 failures attributed, WMA provider "none", 44 OK). Note (guard, non-blocking, pre-v1.0): a restored single-track session whose file can't decode now ends in `PlaybackState.Failed` (normal error, FR-064) instead of clearing the saved session, so the error shows again on next launch until the user plays something else — same as the existing post-prepare event path. | DONE |
| T062f SC-006 breakdown spike | Codex gpt-6.1-sol medium (session `01a1373f-7028-7c93-b7af-2e8699faedd2`, brief `~/.local/state/sistrum-delegate/T062f/brief-1.md`) | 0 | `sc006StartBreakdown` (5 variants). Gate PASS. Device on release-like `.qa` (temporary uncommitted non-debuggable build; normal debuggable `.qa` reinstalled afterwards): engine fresh 200 ms (limiter 13, build 4, READY 152, play→position 129), warm 154, stock ExoPlayer 257, low buffers no gain. Native position = frames handed to AAudio (POWER_SAVING), not heard → SC-006 metric not like for like. Results + options in `research/regression-001.md`. **STOP for owner decision on SC-006** (spec change). | DONE |
| T062h parts 1–2 MP3 join diagnosis | Codex gpt-6.1-sol medium (session `01a1375d-724a-7e02-b9c9-f553c87cedd8`, briefs `~/.local/state/sistrum-delegate/T062h/brief-1.md`, `brief-2.md`) | 0 | `sc003Mp3JoinPaths`, `sc003Mp3RawDecode` (measurement only). Gate PASS. Device: float sink = int sink = stock ExoPlayer (511 / 55 / 1 frames) → not our sink; stock ExoPlayer leaves the 815-frame LAME end padding (1,680,815 frames vs 1,680,000 from platform MediaCodec and host ffmpeg). **STOP for owner**: fix in our sink (HIGH-RISK) or accept as a Media3 limitation. | DIAGNOSED |
| T062h part 3 sink call log + root cause | Codex gpt-6.1-sol medium (same session as parts 1–2, brief `~/.local/state/sistrum-delegate/T062h/brief-3.md`) | 0 | `sc003Mp3SinkCallLog`. Device: real join = setOutputStreamOffsetUs → handleDiscontinuity → configure(B, 1105/815) (sink trims correctly); decoder buffers 623 / 529 show `c2.android.mp3.decoder` self-compensates its 529-sample delay while Media3 `XingFrame` adds 529 → root cause; fix moved from the sink to the renderer (T062i/T062j, still "our code" per the owner choice). T062i round 0 path-(ii) test dropped. Gate PASS. | DONE |
| T062g part 2 dynamic scheduling + PositionSmoother | Codex gpt-6.1-sol high (session in `~/.local/state/sistrum-delegate/T062g/run-2/result.json`, brief `brief-2.md`) | 0 | Host tests 9/9, migration-guard PASS, gate unit/build PASS. Device: Gapless 7/7, Core 11/12 (drift), **Crossfade 12/16** (EQ inheritance, fade start −100 ms, skip start −62 ms, flush timeout); committed code 16/16 + 12/12 → caused by the change. **Owner decision (2026-10-14): defer SC-013 to the cleanup/perf phase.** Change parked in `research/park/T062g-part2-dynamic-scheduling.diff`, working tree restored, normal `.qa` reinstalled. T062g closed. | PARKED |
| T062g part 1 steady vs transition CPU | Codex gpt-6.1-sol medium (session `01a137db-030c-7013-bd21-aa96be4f21c8`, brief `~/.local/state/sistrum-delegate/T062g/brief-1.md`) | 0 | `cpuSteadyAndTransition` (measurement only). Release-like `.qa`, battery: steady native 10.9 % vs Media3 18.6 % (3-run medians), track change = noise → gap is steady playback. simpleperf: Media3's 10 ms `doSomeWork` loop (position tracking 27 %, AudioTrack write 17 %, loading 14 %); our DSP < 1 % of a core; app AOT code unused under instrumentation. Dynamic scheduling on (temporary toggle): 14.7 %, but `Media3EngineCoreTest` position freshness fails (t=201, p=50) → part 2. Normal debuggable `.qa` reinstalled after. Details: `research/regression-001.md`. Gate PASS. | DONE |
| T062j MP3 platform-trim fix (closes T062h) | Codex gpt-6.1-sol high (session `01a13791-454c-71a3-85d8-f0f89e768e05`, brief `~/.local/state/sistrum-delegate/T062j/brief-1.md`) | 0 | LAME/Info parse in an Mp3Extractor-only wrapper (peeks only; parses before sniff because Media3's sniff consumes ID3), metadata entry on the Format; `PlatformTrimAudioRenderer` replaces Media3's MediaCodecAudioRenderer in playback and `OfflineDecoder`. T062i tests green unmodified; new `Mp3LameProvenanceTest` + MPEG-2 mono parser case. migration-guard PASS (notes: a transient peek IOException disables provenance for that extractor; first-header scan needs 1 header, not 4 — both fail safe to Media3's values). Device `.qa`: SC-003 harness MP3 1→2 / 2→3 **1 frame** (were 511 / 55), FLAC 1 frame; `offlineDecodeMatchesPlaybackCapture` maxDiff 0.0 on 6 files; `Media3EngineGaplessTest` 7/7. **Orchestrator fix:** my brief's MP3 bound of 2 frames failed at 3 — a near-silent run in the music inside the ±1 s window (host ffmpeg: same run 32,376 frames after the join) — so the bound is 3, with that reason in the comment. Gate PASS. | DONE |
| T062i MP3 platform-trim tests first | Codex gpt-6.1-sol high (session `01a13771-af52-75d0-92cf-a9b00be390a2`, briefs `~/.local/state/sistrum-delegate/T062i/brief-1.md`..`brief-3.md`) | 2 | Seams `PlatformMp3Trim.kt` (`LameGaplessInfo`, no-op `parseLameGaplessInfo`, `platformMp3Trim`). Expected-fail `PlatformMp3TrimTest` (2) + `LameTagParserTest` (5); guards `PlatformMp3TrimGuardTest` (9), `LameTagParserGuardTest` (4), `FloatChainAudioSinkGaplessTrimGuardTest` (4). Round 1: path-(ii) sink test dropped after the device log. Round 2: migration-guard showed a "delay ≥ 529" rule would mis-trim iTunSMPB files and non-LAME Info headers and lose padding < 529 → provenance rule (raw LAME values only for AOSP MP3 decoder + LAME/Lavf/Lavc + no iTunSMPB). migration-guard PASS (note: add MPEG-2 mono offset-13 parser case in T062j). Gate PASS (7 expected failures on assertions). | DONE |
| Checkpoint M7 | owner | — | Owner 2026-10-13 on the M7 report: "I will check it later after everything done" → T061a decision (SC-007 lead fix) and the M7 listening session deferred to the end of the migration; work continues. M8 order (orchestrator): T062 automated measurements (≤ 10 min device runs; SC-013 1 h screen-off run deferred to the owner — exceeds the benchmark time-box) → T064 docs → T062a rebrand when the owner's app ID / logo / colours arrive → T063 full regression on the renamed build. **Update 2026-10-13:** owner approved T061a ("go ahead with T061a and finish m7 and then stop …"); T061a/T061b done, M7 automated parts closed. Rebrand moves first in M8 (tasks.md: T062a is the first M8 task): T062a → T062 → T064 → T063. |
| T053 | changed | Settings: untagged pre-amp, Media3-only gate, **analysis progress line** (+ "Analyze now" if approved) |
| T054 | changed | [MANUAL] On `.qa`: first analysis of the 775-track library (time, runs, batterystats), SC-004 before/after analysis, SC-017 spot check, rescan keeps rows; listening batched with T061 |

Order: (T049 ∥ T050 ∥ T050a) → T050b → T051a → T051 → T051b → T051c → T051d → T051e → T051f → T052 → T053 → T054.
14 tasks (was 6): 5 tests-first, 6 implementation ({hard} for the meter, gain sources, store, decode, job, engine),
1 orchestrator fixtures task, 2 device tasks.

### First-analysis estimate: 775 tracks on the CPH2307 (estimate, not measured on the phone)

Inputs (measured):
- Library (`.qa` DB copy, active plan): 775 tracks, **49.8 h** of audio (179,300 s), 7.4 GB; 708 tracks are Ogg/Opus at
  48 kHz (47.3 h), the rest is spread over m4a, FLAC (up to 192 kHz), AIFF, WAV, MP3 and WMA.
- Phone: Snapdragon 778G (`lahaina`): little cores 0–3 at 1.8 GHz, big cores 4–6 at 2.4 GHz and 7 at 2.8 GHz.
  `/dev/cpuset/background` = **0–3**: a background worker runs on the little cores only. Battery: 4800 mAh design,
  but the charge counter reads **3.28 Ah at 100 %** (aged), so about 12.6 Wh usable.
- Meter cost: WaxFlow's own Go benchmark at the pinned commit (`BenchmarkMeterProcess`, 48 kHz stereo) = **6.0 ms per
  second of audio** on this laptop (Ryzen 5 3500U), i.e. 166× real time.

Assumptions (not measured; T051d replaces them):
- The Kotlin meter on an A55 core is 4–12× slower than Go on the laptop: 25–70 ms per audio second.
- Opus decode through `c2.android.opus.decoder` on an A55 core, including the codec IPC: 10–30 ms per audio second.
- Total 35–100 ms per audio second, i.e. 10–28× real time on one little core.

Estimate:

| Run | Wall time for 49.8 h of audio | Energy | Battery |
|---|---|---|---|
| Default: background worker on little cores, **only while charging** | **~1.8–5 h**: one or two nights on the charger. About 13–38 worker runs of 8 min | ~0.3–0.6 W, so 0.6–3 Wh | **0 % of the user's battery** (charging) |
| Same work done on battery (only if the charging constraint is dropped) | ~1.8–5 h | 0.6–3 Wh | ~5–24 % of the 12.6 Wh |
| "Analyze now" as a foreground job (big cores), if approved | ~1–2 h (25–55× real time) | ~1–1.5 W, so 1–3 Wh | ~8–24 % if on battery |

After that: only new or changed files are analyzed. A new 4-minute track costs about 10–25 s of little-core time.
Storage: about 1–2 KB per track (analysis row ~250 B + histogram ~0.5–1.5 KB), roughly 1–1.5 MB for this library.
Principle 10: other libraries differ. FLAC and hi-res files decode faster per sample, but they cost more meter
time at 96/192 kHz, which uses 2× true-peak oversampling, or none above 192 kHz. T051d measures every format family.

### Album loudness: storage and recomputation

- **Per track**, written by the job: an `analysis` row (`loudness_lufs`, `true_peak` dBTP: the existing shape) and a
  `loudness_histogram` row. The histogram counts how many 400 ms gating blocks (100 ms hop) above −70 LUFS fall in
  each 0.1 LU bin from −70 to +5 LUFS. It is stored sparse and base64-encoded, its size is bounded by the bin count
  rather than by track length, and it is typically 0.5–1.5 KB. This is libebur128's histogram method.
- **Per album**: an `album_loudness` row (`loudness_lufs`, `true_peak`, `members`, `members_fingerprint`), keyed by
  album id.
  - **Computation:** merge the members' histograms, then run the BS.1770 gates over the union of blocks:
    - the absolute gate at −70 LUFS;
    - the relative gate at −10 LU below the pooled mean;
    - then the mean of the blocks that pass.
    This is WaxFlow's `Group`, not an average of track values. The album true peak is the highest member true peak.
  - **Accuracy:** the 0.1 LU binning moves the result by less than 0.05 LU. T050a checks this against the exact
    pooled blocks, and the T050b oracle checks it against WaxFlow.
- **Members:** available, non-hidden tracks with the same album id whose album name comes from a tag. Untagged
  files under MediaStore's folder album ("Music") are never an album.
- **Complete only:** an album record is written only when every member has a histogram. Until then, album mode uses
  each track's own measured value.
- **Recompute:** `members_fingerprint` is the SHA-256 of the sorted `trackId:fingerprint:analyzer_version` entries.
  The planner recomputes it after every analysis run and after every scan. When it changes, the album is
  recomputed from the stored histograms in milliseconds, without decoding. Changes include:
  - a track added, removed or re-tagged;
  - a file changed and re-analyzed;
  - an analyzer version bump.
- **Rescans:** rows of unchanged files are copied into the new plan, and the planner re-validates albums right
  after the scan.

### Open questions for the owner (defaults in brackets, already written into the docs)

1. **Constraints:** should the job run only while charging, plus battery-not-low and storage-not-low? [yes]
   - "Idle" is not required. On Android, device-idle means a long screen-off doze, which would usually postpone
     analysis to the early morning.
   - Should it be "charging or idle" instead?
2. **"Analyze now":** add an "Analyze now" action in the normalization settings?
   - It would run without the charging constraint, as a foreground job with a notification, on the big cores.
   - [not added unless you say so; T053 has the progress line only]
3. **Engines:** should the job run whichever engine is selected? [yes]
   - The native engine then also normalizes again from the measurements, with its own old rules (average album,
     next-track-same-album).
4. **Tracks not analyzed yet:** while a track waits for analysis, use its gain tags. [yes, as decided]
   - This means a tagged album can change level slightly once it is measured. Measured levels and the tagger's
     levels usually differ by under 1 dB at the same reference.

### Owner decisions on the M6 plan (2026-10-08) — plan approved

1. Background analysis: charging + battery not low + storage not low; no idle requirement.
2. **"Analyze now"**: foreground work (all cores) with a progress notification and Cancel, labelled as using the
   battery; foreground service type `mediaProcessing` on Android 15+ (the CPH2307 is API 35), `dataSync` on
   Android 14; the 6 h/24 h limit is handled by checkpoint + resume in the background job. The settings progress line
   also says analysis continues automatically while charging. New tasks T051g (tests first) → T051h; button in T053;
   FR-047i; ADR-005 amendment.
3. Analysis runs regardless of the selected engine: yes.
4. Unanalyzed tracks use their gain tags until measured: yes.
Start M6 and run it to the end per the stop rules. T054's listening part is batched into T061.

### M6 tasks

| Task | Model | Rounds | Result |
|---|---|---|---|
| T049 tag parsing tests first | deepseek-v4.1-flash (`ses_ee79c52d9ffezL57064B1uPkq8`, briefs `~/.local/state/sistrum-delegate/T049/brief-1.md`, `brief-2.md`) | 2 | PASS (expect-fail, method-level: 42 of 62 `TagGainSourceTest` fail on assertions against the stub `TagGainSource` (returns null); the 20 null-expecting tests pass: malformed/absent tags, malformed binary frames never throw). New `GainSource.kt` (GainForm incl. Measured, GainInfo, GainSource; `internal` because PlaybackItem is). Orchestrator trivial fixes round 1: bad-peak tests paired with a valid gain (gain kept, peak dropped), test `an out-of-range track gain keeps a valid album gain`, comma removed from a test name (verify.sh splits on commas). migration-guard NEEDS CHANGES: Sound Check vector had equal L/R (a Rhythm-style L/R average would pass) and "reference never applies to R128/Sound Check" untested → round 2 added unequal-word Sound Check (−3.0103 dB), reference vs R128/Sound Check/alone, text-vs-RVA2 order, malformed RVA2/RGAD/RVAD, case-insensitive iTunNORM/R128 keys, −50 dB, RVAD+Sound Check not mixed. Our Sound Check rule = louder channel word (max), not Rhythm's average — T051 must deviate from Rhythm there. Gate PASS. |
| T050 gain math / resolver / ramp tests first | deepseek-v4.1-flash (`ses_ee79236a6ffejFke00nZEGbXAK`, brief `~/.local/state/sistrum-delegate/T050/brief-1.md`) | 1 | PASS (expect-fail, method-level: 34 tests fail on assertions against stubs — ItemGainMathTest 22 of 23, GainResolverTest 5 of 8, GainRampTest 7 of 9; passing guards: disabled → 0, both sources null → None, null/non-finite analysis → null, blank album id never looked up, default bit-exact pass-through, two processors identical). Stubs: `itemGainDb(info, settings, untaggedPreampDb)` → 0, `MeasuredGainSource(AlbumLoudnessLookup)` → null, `GainResolver.resolve` → None, `GainProcessor.setTargetGainDb` no-op. migration-guard NEEDS CHANGES (tautological epsilon test; retarget-at-next-buffer not pinned) → **orchestrator trivial fixes**: deleted the tautology, added `gainOneBufferLater < gainBefore`, KDoc fixed; migration-guard PASS. **Notes for T052:** the ramp must be longer than 100 ms (a linear 100 ms ramp reaches the target at frame 4799 < 4800): use ~150–200 ms; `GainProcessor` class KDoc "no public setters" is stale. The T050 gate also carries T049's 42 expected failures (both tests-first before T051). Gate PASS. |
| T050a loudness meter tests first | deepseek-v4.1-flash (`ses_ee77db88cffeCK92cH26tJu1Zl`, briefs `~/.local/state/sistrum-delegate/T050a/brief-1.md`, `brief-2.md`) | 2 | PASS (expect-fail, method-level: 21 of 24 fail on assertions against stubs `LoudnessMeter`/`LoudnessHistogram`; passing: silence/short-input guard, WAV-reader unit test; oracle test skips without `SISTRUM_CORPUS` and fails on assertions with it against the stub: 101 ok rows, 10 WAV rows + 2 groups compared). Tests: BS.1770 anchors, mono −26.01, dual +3.01, EBU Tech 3341 cases 1–5 (generated), silence gap, chunk invariance 1e-9, 5.1 weights, K-weighting 48 kHz, checkpoint/resume mid-block, invalid construction; true peak (997 Hz, fs/4 π/4, flush tail, resume after flush, 2× at 96/192 kHz, none above 192 kHz); histogram tracks the meter within 0.05 LU, album = WaxFlow Group gating within 0.05 LU and ≠ mean, round trip ≤ 2048 chars, −70 LUFS excluded. migration-guard NEEDS CHANGES (case 2 asserted −23 instead of −33; 16-bit WAV samples not sign-extended; encode test on a one-bin tone) → round 2 fixed all + non-integer levels, oracle robustness; migration-guard PASS. Orchestrator trivial fix: oracle `Assume` when nothing is comparable. Notes for T051a: histogram bin centres on tenths; flush non-terminal (PeakMeter semantics). Gate PASS (carries T049/T050 expected failures). |
| T050b WaxFlow loudness fixtures | orchestrator | — | DONE. `scripts/waxflow-oracle.sh` gained an optional groups file (helper `group` mode = WaxFlow `AnalyzeGroup`; rows with status `group` and a `members` column); groups in `scripts/waxflow-oracle-groups.tsv` (gapless_flac, gapless_mp3, mixed_pcm, mixed_formats, hires_wav). Corpus = `~/.cache/sistrum/oracle-corpus` (`sistrum/` = SistrumTestCorpus pulled from the `.qa` device, `waxflow/` = WaxFlow `testdata/`; outside the repo, 166 MB). Result: 112 files, 106 decoded, 6 refused (HLS segments), 5 groups; pin unchanged `446ca31`. Many WaxFlow testdata files are shorter than one 400 ms block (integrated −Inf). Fixtures 24 KB. Gates for T051a set `SISTRUM_CORPUS=$HOME/.cache/sistrum/oracle-corpus` so the oracle test runs. |
| T051a loudness meter port | deepseek-v4-pro (`ses_ee7647c41ffee6gcfqFdGvSSwn`, briefs `~/.local/state/sistrum-delegate/T051a/brief-1.md`, `brief-2.md`) | 0 (+1 resume) | PASS. Kotlin port of WaxFlow `dsp/loudness` + `firwin` at `446ca31` (LoudnessMeter, KWeighting, TruePeak, Firwin; ORACLE.md headers) and our LoudnessHistogram (751 bins, centres on tenths, libebur128-style gating on bin-centre powers, sparse varint/base64). Deviations (all per brief): interleaved input, flush non-terminal (PeakMeter semantics), no loudness range, nullable results, default channel layouts only. THIRD-PARTY-NOTICES: WaxFlow (MIT) + libebur128 parametrization note. **Run 1 was killed by the system's low-memory reaper** (MemAvailable 1.1 GB) after the loudness tests had passed, during a warning cleanup; stopped and reported per the rule; owner said "continue" → resumed the same session (brief-2) to finish and run everything. All loudness tests pass unmodified; **oracle with the corpus: 10 WAV rows + 2 groups compared, 0 mismatches** (integrated ≤ 0.01 LU, true peak ≤ 0.05 dB; groups ≤ 0.05 LU). No test files touched → no migration-guard. Gate PASS with `SISTRUM_CORPUS` set (oracle ran: 0 skipped at test level). |
| T051 gain sources + item gain math | deepseek-v4-pro (`ses_ee749f78dffewWDp37qLjWrylE`, brief `~/.local/state/sistrum-delegate/T051/brief-1.md`) + orchestrator | 0 | PASS. TagGainSource (Rhythm ReplayGainUtil parsing port at ef16e7bd, GPL-3.0-or-later header + THIRD-PARTY-NOTICES), MeasuredGainSource, GainResolver (measured → tags → None), itemGainDb. **The OpenCode weekly quota ran out mid-run** (coder idle ~47 min after its normalization tests passed); stopped the run and, on the owner's instruction ("continue on claude code this task until it finished and commit then stop"), finished review and fixes as orchestrator: Track mode with only an album gain → base 0, not the untagged pre-amp (contract: pre-amp for None only; untested gap); iTunNORM words parsed as Long (`FFFFFFFF` overflowed Int and shifted word positions; a non-hex word now rejects the tag); RVA2/RVAD peaks pass the 0 < peak ≤ 10 check; binary frame parses wrapped in runCatching (never throws). Deviations from Rhythm (commented `ADR-005:`): bare-number reference loudness, dB reference ignored, Sound Check louder word, malformed frames skipped, RVAD bitLen 0 skipped. No test files touched → no migration-guard. Gate PASS with `SISTRUM_CORPUS` (expect-fail: the 7 GainRampTest methods only, for T052). |
| T051b store + album planner tests first | deepseek-v4.1-flash (`ses_eceaa4140ffeRDFCGyR0sTdP3S`, briefs `~/.local/state/sistrum-delegate/T051b/brief-1.md`…`brief-3.md`; brief-1 was pre-written 2026-10-08, updated for T051 landing) | 2 | PASS (expect-fail, method-level). New `LoudnessModels.kt` (real types: `ANALYZER_VERSION`, `PendingTrack` with format/codec/duration, sealed `TrackLoudnessResult` Measured/Silent/Failed, `AnalysisProgress`, `PlannerTrack`, `AlbumRecord`, `AlbumPlan`), stubs `LoudnessStore` (pending, writeResult, progress, recomputeAlbums, albumLoudness, checkpoints by track+fingerprint) and `AlbumLoudnessPlanner` (isAlbumMember, membersFingerprint, plan). JVM `AlbumLoudnessPlannerTest`: 17 of 28 fail on assertions (11 negative tests pass against the empty stub, each with a failing positive control). Instrumented `LoudnessStoreTest` on `.qa` (CPH2307): 17 tests, 15 fail with `AssertionError`, 2 pass against the stub (`resultsForChangedOrUnknownTracksAreDiscarded`, `folderFallbackAlbumGetsNoRecord`: they expect nothing). Round 1 (orchestrator review): histogram test decoded the whole JSON row instead of `bins`; added checkpoint-cleared-on-finish and histogram-dropped-on-rescan. migration-guard PASS, with 5 non-blocking gaps → round 2 closed them (failed sibling doesn't block an album, album left with only hidden/folder tracks deleted, ok member without histogram blocks, hidden tracks not in progress total, pending-before-write). Contract note added: membership rule (folder name AND no `tag_album` = folder fallback; failed excluded; silent counts). **For T051c:** `sync/SyncDatabase.kt` (`writeLocalLibrary` carry-over before `deleteStaleDocuments`) must be in the brief's file list; fingerprint = lowercase hex. Worktree note: FFmpeg `jniLibs/` and `androidApp/build/ffmpeg` copied from the main checkout (`../Sistrum`, same build script) because this worktree had none. Gate PASS (expect-fail 24: 17 planner + 7 GainRampTest). |
| T051c store + planner + rescan carry-over | deepseek-v4-pro (`ses_ece97dc78ffe8amifn0RvbX7cE`, briefs `~/.local/state/sistrum-delegate/T051c/brief-1.md`, `brief-2.md`) + orchestrator | 1 | PASS. `LoudnessStore` (analysis/histogram/album/progress documents in the active plan; stale-fingerprint and unknown-track results discarded; pending = no current analysis row, current failed rows not retried; progress counts; checkpoints gated on fingerprint + analyzer version; `albumLoudnessSnapshot()` for MeasuredGainSource) and `AlbumLoudnessPlanner` (membership, complete-only, pooled histogram gating, lowercase-hex members fingerprint, max ok peak). `SyncDatabase.kt`: 11 new `SyncDao` queries + 3 row classes, and two carry-over calls in `writeLocalLibrary` after `activatePlan` and before the stale deletes (per-track kinds whose `source_fingerprint` = new audio sha256; `album_loudness` unconditionally). No schema/version change; read side byte-identical. migration-guard round 0 NEEDS CHANGES: SQLite `json_extract` throws on malformed JSON → one bad row would make every rescan roll back → round 1: `CASE WHEN json_valid(...)` guards in the carry-over, pending and analyzed-count queries; histogram used only when its own fingerprint/version match; new `LoudnessStoreMalformedRowTest` (4). **Orchestrator trivial fix:** positive controls (record exists before corruption) in its two album tests. migration-guard PASS. Device (`.qa`): `LoudnessStoreTest` OK (17), `LoudnessStoreMalformedRowTest` OK (4); writeLocalLibrary regressions PriorCodecScanState (1), PlaylistMutationPersistence (6), EmptyScanGuard (3), ArtistArtworkCleanup (5) OK. `HiddenTracksTest` is **flaky, pre-existing**: on T051c 2 of 6 runs had 1 failure (`favoriteSurvivesHideAndReappear`, NoSuchElementException) plus one process crash (`hiddenTrackInAVisibleAlbumLeavesTheAlbumVisible`, Room Flow querying after `database.close()`); the baseline build at `9bc9466` showed the same failure and crash (2 of 6 runs) → test-side race on the shared `tracks` flow, not T051c. Gate PASS (expect-fail: GainRampTest 7 methods only). |
| T051d offline decode + analysis performance | deepseek-v4-pro (`ses_ece7f27a7ffe4yj7beEuDJhHsi`, briefs `~/.local/state/sistrum-delegate/T051d/brief-1.md`…`brief-3.md`, `brief-perf-1.md`…`brief-perf-3.md`) + orchestrator | 2 + perf 2 | PASS. **Decode:** `OfflineDecoder` (own ExoPlayer, own app + playback loopers at background priority, `CaptureAudioSink` with playback's float pipeline) — bit-exact vs the playback tap (flac, opus, mp3, aac, aiff 24, wav 24/96); corpus vs WaxFlow lossless ≤ 5e-5 LU, lossy ≤ 0.003 LU; 300 s/120 s synthesized files complete; cancel < 1 s; no-decoder (ALAC on the CPH2307) fails fast. **Owner decision 2026-10-13 (option 1)** applied: `TrackAnalyzer` (decode and meter on separate threads, bounded pool), `AnalysisConcurrency` (MAX_WORKERS 3; 1 at MODERATE / headroom ≥ 0.85 / ≤ 4 cores; 0 at SEVERE; cap max(1, cores/2)). **Overhead found:** the meter inside the decode callback serialized decode and meter (Opus 408 ms per audio s inline vs 136 pipelined ≈ decode-only 143). A direct MediaCodec decoder was built and measured, then **removed**: no consistent gain (Opus 151 vs 143, MP3 151 vs 121, AAC 118 vs 133), wrong FLAC output, no gapless trimming (MP3/AAC 1024–1920 frames, 0.003 LU). **Rates on the CPH2307** (per worker, ms per audio second, × real time): Opus 136 (7.4×), FLAC 48 (21×), MP3 114 (8.8×), AAC 128 (7.8×); per-worker rate holds at 2 and 3 workers (sample pass 49.5 / 25.2 / 17.4 s for 1 / 2 / 3), thermal status 0, battery ≤ 32.6 °C (short runs; headroom unsupported). **Estimates:** owner library (49.8 h, 47.3 h Opus) ≈ 2.2 h with 3 workers (3.3 h with 2, 6.7 h with 1); a 50 h library with 3 workers ≈ Opus 2.2 h, FLAC 0.75 h, MP3 1.9 h, AAC 2.1 h. Measured with the process in the `/foreground` cpuset (workers mostly on cores 0–3 anyway); the real background worker and sustained heat are measured in T054. Orchestrator fixes (root-caused on device, temporary logging removed): `hasPendingData = started && !ended` (BUFFERING deadlock), IDLE completes on interrupt, `onTracksChanged` fail-fast, `clippedByDecoder` oracle exception (tasks.md), single-core worker cap, TrackAnalyzer meter-loop race (`done` read before poll; incomplete Completed → Failed; values only when Completed), DirectCodecDecoder removal. migration-guard: final PASS. Device (`.qa`): OfflineDecodeBenchmarkTest playback match / cancel / long file / corpus OK; AnalysisThroughputBenchmarkTest overhead ×4 and concurrency 1/2/3 OK, every run < 7 min. Gate PASS (expect-fail: GainRampTest 7 methods). |
| T051e job tests first | deepseek-v4.1-flash (`ses_ecd8225beffeHt5aGYSBIZHveL`, briefs `~/.local/state/sistrum-delegate/T051e/brief-1.md`, `brief-2.md`) + orchestrator | 2 | PASS. `LoudnessAnalysisWorkerTest` (24 JVM tests) against stubs `LoudnessAnalysisRunner` (seams `AnalysisJobStore`, `TrackMeasurer`, worker count, clock; 8 min budget, 9 min hard stop with checkpoint), `LoudnessAnalysisWorker`, `LoudnessAnalysisScheduler` (`AnalysisWorkEnqueuer`; KEEP after scans, APPEND_OR_REPLACE continuation, charging + battery + storage). No Robolectric, so `work-testing` is androidTest-only (tasks.md note). WorkManager 2.12.0. Orchestrator fix: hard-stop test asserts MORE_PENDING. migration-guard round 1 NEEDS CHANGES (Measured/LoudnessHistogram reference equality made one test unpassable; gaps: stop with in-flight, constraints on enqueued requests, periodic checkpoints, CancellationException) → round 2 PASS. For T051f: measurements on Dispatchers.IO or a dedicated pool (3 gated in parallel); unfinished = unstarted or cancelled → MORE_PENDING; CancellationException must propagate out of `run` (async/await or withContext, not a launched child). Gate PASS (expect-fail: LoudnessAnalysisWorkerTest + GainRampTest 7 methods). |
| T051f background analysis job | deepseek-v4-pro (`ses_ecd68fd77ffebnfQZbwkkmdyrz`, briefs `~/.local/state/sistrum-delegate/T051f/brief-1.md`, `brief-1b.md` (resume after a low-memory kill), `brief-2.md`…`brief-4.md`) + orchestrator | part 1: 2, part 2: 2 | PASS. Part 1 (scope added, tasks.md note): decode from a start position (`PcmDecoder.startUs`, `setMediaItem(item, startMs)`, `CaptureAudioSink.startPositionUs` drops frames before it in media time = renderer time − `setOutputStreamOffsetUs`), meter checkpoints on the meter thread every 60 s and on cancel, resume from `MeterResume` (restart from 0 on mismatch/corrupt state, mismatch cancels the first pass at once), `AnalyzerTrackMeasurer` + `MeterResumeCodec`. Device (.qa) resume at 40%: Opus ΔLU 0.0003, AAC/FLAC 0, true peak Δ 0, frame delta 0; MP3 ΔLU 0.0057, 1297 frames (MP3 seeks are estimated; device test allows 2×1152 for MP3, orchestrator change). offlineDecodeMatchesPlaybackCapture + cancel re-run OK. Part 2: `LoudnessAnalysisRunner` (async on Dispatchers.IO, `select` on the first finished — round 2 fixed FIFO head-of-line blocking; `paused` from the last sample), worker (`AndroidSyncRuntime.database()`, store adapter tolerant of checkpoint I/O errors, thermal worker count cached 10 s), scheduler (`get(context)`, KEEP after a completed scan, APPEND_OR_REPLACE continuation), THIRD-PARTY-NOTICES. Orchestrator test fix: T051e fake `resumes` ConcurrentHashMap → synchronized HashMap (null resume; assertion-neutral, found by the coder). App start on .qa with WorkManager: no crash. migration-guard PASS; non-blocking notes carried to T051g/h: write/recompute under NonCancellable when the coroutine is cancelled; sibling measurements' isCancelled should also see the job's cancellation. Gate PASS (expect-fail: GainRampTest 7 methods). |
| T051g+T051h "Analyze now" (merged, owner) | deepseek-v4-pro (`ses_ecc0776b0ffeUzp0kSOT4qpAiQ`, briefs `~/.local/state/sistrum-delegate/T051gh/brief-1.md`, `brief-2.md`; run 1 resumed after a low-memory kill, run 2 cut off by the OpenCode quota) → Codex gpt-6-astra high / gpt-6.1-sol high (session `01a13472-f65d-7233-9a1f-e94c4db60bb6`, `brief-3.md` finish round 2, `brief-4.md` round 3) + orchestrator | 3 | PASS. Foreground "now" run (REPLACE unique work, no budget, default priority, progress notification with Cancel, mediaProcessing/dataSync FGS types, re-run guard), shared `AnalysisClaims` (limit, `seen`, `abandoned`), runner waits while the other run holds every slot, abandoned tracks keep the job pending, cancel hands off via `enqueueContinuation(0)` (APPEND_OR_REPLACE). AnalyzeNowTest a–s + regressions. migration-guard: round 1 NEEDS CHANGES (FULL ends run → busy loop / empty now run), round 2 NEEDS CHANGES (abandoned track counted done; no hand-off on cancel), round 3 NEEDS CHANGES (stale ongoing notification after cancel) → orchestrator trivial fix: no notification updates after stop, cancel the notification in the finally. Orchestrator also fixed the English plural "one" ("track"). Gate PASS (expected-fail: 7 GainRampTest methods, T052). Lint: no new issues in task files (see Known lint debt). Device check NOT RUN: no UI entry point until T053 — after T053, on `.qa`: start Analyze now, notification + Cancel, cancel stops within 1 s, notification disappears, finished tracks kept. Notes: progress/notification updates may arrive out of order (IO threads); a throwing `writeResult` makes the Failed write throw too. |
| T052 gain ramps + Media3 gain resolution + coordinator wiring | Codex gpt-6.1-sol high (session `01a134a6-0476-7240-96b4-906793de8caf`, brief `~/.local/state/sistrum-delegate/T052/brief-1.md`) + orchestrator | 1 | PASS. GainProcessor: 200 ms linear ramp from the applied gain, flush jumps, bit-exact unity. `StreamGainControl` per player; stream-start hook in `FloatChainAudioSink.configure` before the pipeline flush (also on the int sink path) resolves measured → tags → none for the stream's mediaId, under one lock with `setNormalization` retargets; gain source frozen per stream; decoder name logged. Coordinator: `setNormalization` before every prepare and on settings changes; Media3 gets `ItemGain.Unity`, native path unchanged. Scope added (stated in brief): FloatChainAudioSink, Media3PlayerFactory, PlaybackService, StreamGainControl (new), FakePlayerEngine (coder, fake lives there). migration-guard PASS; orchestrator strengthened PlaybackCoordinatorNormalizationTest case a (missing call no longer passes). Gate PASS (GainRampTest 9/9 unmodified, full suites). Device `.qa`: Media3EngineGaplessTest 7, Media3DspTest 5, Media3FloatPathTest 5, Media3LimiterTest 2, Media3EngineCoreTest 12 — all OK. Lint: +34 UnsafeOptInUsageError, same kotlin.OptIn pattern (Known lint debt). Notes: int sink path may ramp a few ms of the old tail toward the next gain before its flush (non-default path); album snapshot taken once per engine; by-ear checks deferred (owner). |

### Low-memory kills (owner, 2026-10-13)

"auto continue after 20 min if you stopped due to low memory": after a low-memory kill the orchestrator waits 20 min,
runs the memory check (≥ 2 GB) and resumes on its own (coder: same session); still low → another 20 min; after 3
failed waits it stops and tells the owner. Replaces CLAUDE.md's "don't restart it on your own; report it and wait".

### Owner device checks deferred; later phases (owner, 2026-10-13)

- "Ignore all device tests with me. I will do it another time." Every check that needs the owner (listening, hands-on
  device, hardware: parts of T054, T061, T062 and the MANUAL CHECKS lists) is deferred to one later owner session and
  does NOT block milestones. Automated device runs on `.qa` that the orchestrator does alone still run. Deferred items are
  collected in "Deferred owner checks" below as they come up; never marked PASS.
- Order after 001: 002 (Kotlin decoders, mostly ported already on `port/waxflow` / `port/analysis`), then 003 (native
  removal), then a full rebranding of the code, then full clean-up plus performance and security optimisation.

#### Known lint debt (found 2026-10-13, T051g/h gate)
- `lintDevDebug`: 510 errors = 94 MissingTranslation (known) + 416 `UnsafeOptInUsageError` (Media3 `@UnstableApi` use
  without opt-in) in CaptureAudioSink, OfflineDecoder, player/media3/*, player/decoders/*, TagGainSource, all from earlier
  M3–M6 commits; none in T051g/h files. CLAUDE.md "Known state" lists only MissingTranslation and
  LocalContextGetResourceValueCall. Fix (module-level opt-in or `@OptIn(UnstableApi::class)`) in M8 T063 or the clean-up
  phase.
  Root cause (T052 review): those files annotate `@OptIn(UnstableApi::class)` with Kotlin's `kotlin.OptIn` (no import),
  which Android lint does not recognise; `androidx.annotation.OptIn` (as in `dsp/GainProcessor.kt`) does. After T052:
  450 (Media3Engine +23, Media3PlayerFactory +10, FloatChainAudioSink +1 from the new code, same pattern). Fix = import
  `androidx.annotation.OptIn` in each file.

#### Deferred owner checks
- T054: listen to slider drags (untagged pre-amp, target) for steps/clicks; album mode across an album; Opus header-gain files
  sound at the expected level (batch with T061 if preferred).
- T054/SC-018: first-analysis wall time on battery + `batterystats` (CPU, wakelocks, discharge) over a real night.

### Coder switched to Codex (owner, 2026-10-13)

OpenCode hit its usage limit (rolling 100%, weekly 86%, monthly 93%) mid T051g/h round 2 (session
ses_ecc0776b0ffeUzp0kSOT4qpAiQ idle from 10:45; relay stopped at 11:44, partial edits kept in the tree). Owner: "use codex
from now on instead of open code". `dispatch.sh` now routes `gpt-*` models and Codex session UUIDs to
`codex-run.sh` (`codex exec`, workspace-write sandbox + Gradle/Kotlin/Android caches, network on, approvals never,
AGENTS.md only; same result.json fields). Models: default `gpt-6.1-sol` medium, hard /
[HIGH-RISK] `gpt-6.1-sol` high (owner note: near-Astra at ~1/5 the cost), fallback after two failures `gpt-6-astra` high.
T051g/h round 2 had already started on `gpt-6-astra` and was left to finish. Smoke test passed (fresh + resume, Gradle runs in the sandbox, no delegation
instructions seen). Round 2 is redone as a fresh Codex session (counts as review round 2).

### T051g/T051h owner decision (2026-10-13)

Owner: "do 1 only" — "Analyze now" (T051g tests + T051h implementation) is delegated as one task and one commit,
not tests-first. AnalyzeNowTest keeps every T051g case; the test diff review and migration-guard still run. All other
rules unchanged (tests-first stays for T052; migration-guard still re-runs on review rounds).

### T051d owner decision (2026-10-13)

Option 1: accept a slower first analysis (resumable, charging-only, tags used until measured). Before committing
T051d: (1) decode and meter on separate threads; (2) analyse several files at once, benchmarking 1/2/3 concurrent
workers on the CPH2307 under background constraints and choosing the best that doesn't throttle or overheat, with
the worker count adaptive to cores and thermal status; (3) find where 408 ms vs 143 + 38 ms goes and remove what
analysis doesn't need (e.g. feed MediaCodec directly); (4) report the full-library estimate and per-format rates
(Opus, FLAC, MP3, AAC). SC-018 relaxed to "first analysis completes within a few charging nights and resumes
correctly" (spec Session 2026-10-13, SC-018, FR-047c; tasks.md under T051d). 002 note: a Kotlin Opus decoder (WaxFlow
port) may serve the analysis path if it beats MediaCodec; Codex benchmarks it separately (specs/002-kotlin-decoders/NOTES.md).
