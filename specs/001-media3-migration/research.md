# Phase 0 research — 001-media3-migration

Date: 2026-10-05. Inputs: `spec.md`, constitution, `research/discovery.md` (architecture, behaviours Q8, risks
R1–R12), the external reports in `research/` (leads, Principle 7), and the desk checks below. Every item is
**Decision / Rationale / Alternatives**. Device-only items are in "Spikes" with their real status.

## Pinned references (read for this plan)

| Source | Pin | License | Used for |
|---|---|---|---|
| Media3 | 1.11.1 (Google Maven latest, 2026-10-05) | Apache-2.0 | engine |
| cromaguy/Rhythm | `ef16e7bd06e53bcbb251772ebabe969911b6dae6` | GPL-3.0-or-later | A/B mechanics (`RhythmPlayerEngine.kt`, `TransitionController.kt`, `PreloadController.kt`), `replaygain/ReplayGainAudioProcessor.kt`, `ReplayGainUtil.kt` |
| AurielSolaris/Choir | `f2e96fd322eba8d62d462c8d5af062e78e431c8d` | GPL-3.0-or-later | `playback/AiffExtractor.kt` (428 lines) |
| WaxFlow fork | `446ca31` (`docs/waxflow/ORACLE.md`) | MIT | not used by 001 code; oracle for 002 |

## D1 — Engine seam name and shape

- **Decision**: Kotlin interface `PlayerEngine` (not `PlaybackEngine`, which is the native struct's name,
  discovery R6). Implementations `LegacyNativeEngine` (wraps `FfmpegDecoder`, zero behaviour change) and
  `Media3Engine`. The seam is what `PlaybackService` calls on `FfmpegDecoder` today minus the dead APIs
  (discovery §7.2), plus an **event flow** (transition started / gapless advanced / ended / output
  disconnected / error) that replaces polling. `LegacyNativeEngine` turns its polls into the same events.
- **Rationale**: keeps `PlaybackService` orchestration (queue, stats, Last.fm, Mood Radio, session) above the
  seam and engine-neutral; Graphify stays unambiguous.
- **Alternatives**: `AudioEngine` (fine, but "audio" also names the DSP layer); keeping polling in the seam
  (rejected: Media3 is event-driven, R7).

## D2 — Who owns the queue

- **Decision**: `PlaybackQueue` stays the only queue (constitution "Do not touch"). Each ExoPlayer holds **at
  most the current item and the next one** (the next only for gapless), fed by the service from
  `peekNext()`. ExoPlayer repeat/shuffle are never used.
- **Rationale**: FR-005/SC-010. Rhythm gives both players the whole queue as ExoPlayer playlists and syncs
  shuffle orders between them (`performOverlapTransition`); that duplicates `PlaybackQueue` and is not ported.
- **Alternatives**: ExoPlayer playlists mirroring the queue (rejected: two sources of truth, resync on every
  edit).

## D3 — Crossfade mechanics (ADR-003)

- **Decision**: port Rhythm's A/B **mechanics** (idle player pre-buffers the next item; roles swap at fade
  start; outgoing player stopped and recreated/released after; explicit repeat-one and previous-during-fade
  handling; a transition state machine like `TransitionController`'s IDLE/SCHEDULED/PREPARING/TRANSITIONING/
  CLEANUP). Our curve (equal-power), our start rule (Kotlin decides, `shouldStartCrossfade`), our snap rules.
  Second player created only around a transition (FR-033).
- **Rationale**: constitution Principle 5; discovery Q4.
- **Differences from Rhythm, deliberate**: one shared audio session (Rhythm's players each have their own and
  it re-publishes the session id on every swap); no ExoPlayer playlists (D2); our curve, not its shaped
  `calculateVolumeIn/Out`.

## D4 — How the fade is applied (spike S1; desk part done)

- **Decision (owner, 2026-10-05; S1 verifies by listening)**: **per-sample fade in each player's gain processor**, keyed to
  stream frame positions, applied **before** the EQ. Abort (pause/seek/stop/queue edit) = stop the outgoing
  player and flush the incoming one at its current position, so no already-faded audio is heard after a snap.
- **Rationale (desk)**:
  - Rhythm's method is a coroutine loop: `delay(16)` then `player.volume = …` (`RhythmPlayerEngine.kt`
    ~l.790–812). `player.volume` reaches `AudioTrack.setVolume`, which applies after the in-app EQ. A
    time-varying gain after the EQ breaks the linearity argument behind exact EQ matching (FR-051): the sum
    differs from EQ-on-the-mix by a smear of the envelope through the filters' memory (largest at 32 Hz with
    a big boost and a short fade). The native engine fades before its EQ.
  - A processor fade is sample-accurate and timer-free (no coroutine jitter, no 16 ms steps), and matches the
    native engine's per-frame `cos/sin` exactly.
  - Its cost: processors run ahead of playback by the sink buffer (Media3 default PCM target 500 ms), so a
    snap needs a flush of the incoming player; and incoming start latency offsets the two curves by the
    incoming player's start time (expected tens of ms; SC-007 allows 200 ms).
- **Alternatives**: timer-stepped `player.volume` (Rhythm) — simpler aborts, but loses exact EQ linearity
  during fades and depends on timer cadence. S1 compares both by listening on a 12 s fade and on snaps.

## D5 — Audio chain and the shared session (ADR-004)

- **Decision**: per player, in order:
  `normalization gain × fade (one gain processor, ramps inside) → stereo width (ChannelMixingAudioProcessor,
  M/S matrix) → 10-band EQ (peaking biquads, Q = 1, native coefficient formula) → preamp` →
  `player.volume = focus duck` (AudioTrack) → AudioFlinger sums both players in the **shared session** →
  **limiter-only DynamicsProcessing** on that session → output.
  User volume is the system stream volume (the app's volume slider uses `STREAM_MUSIC`), so it is not an
  in-app factor; FR-035's product reduces to duck × fade × normalization.
- **Rationale**: EQ/width/preamp are linear and identical on both players, so per-player = on the mix
  (exact, FR-051). Duck after the EQ matches the native engine (it applies focus gain last). The limiter is
  the only non-linear stage and must see the sum (AOSP: `research/dynamics-processing-session.md`).
- **Desk/device facts**: DynamicsProcessing exists on the CPH2307 (`dumpsys media.audio_flinger`: library
  `dynamics_processing`, UUID `e0e6539b-1781-7261-676f-6d7573696340`, `libdynproc.so` in the vendor and odm
  effect configs). Native EQ: `ffmpeg_player.cpp:319-339` (RBJ peaking, Q = 1, 32 Hz…16 kHz); width
  `:391-393`; preamp `:390`; focus gain last `:394-400`.
- **Exactness note**: the native engine resamples to the output rate before its EQ; per-player processors run
  at the track's rate. Band-centre gains are identical either way; shapes are identical when rates match
  (spec US7 sc4). Processing at a fixed output rate (resample first) would make it identical for every file,
  at a CPU cost: measured in S3 before deciding.
- **Alternatives**: DP pre-EQ bands for the EQ (rejected by the owner: no Q, response differs);
  `audiofx.Equalizer` (rejected: auto-attenuation, fixed bands); one ExoPlayer + `DefaultAudioMixer` mixer
  engine (not needed; recorded as a future option); two players into one shared `AudioSink` (rejected: one
  owner, one clock); `CompositionPlayer` (rejected: experimental, overlaps broken, no queue semantics in 1.11).

## D6 — MediaSession (ADR-006)

- **Decision (owner, 2026-10-05)**: keep the **framework `MediaSession` in `PlaybackService`**, fed from the service's
  state as today (one logical player; it switches to the incoming track at fade start). No Media3
  `MediaSessionService` in 001.
- **Rationale**: the service already owns session, notification, queue and focus for both engines; feeding the
  session from service state satisfies FR-024 for both engines without a `ForwardingPlayer` around two
  ExoPlayers or a fake `Player` for the native engine; the output-switcher token (`AndroidPlaybackSession`,
  `MainActivity.kt:531`) keeps its type (R9). "Unknown artist" is fixed in this path (FR-021).
- **Alternatives**: Media3 `MediaSession` over a `ForwardingPlayer` that switches A/B at fade start (as
  RNTP-APM does; Rhythm swaps players via swap listeners). Gives Media3 notification/controller APIs, but needs
  a Player for the native engine too, changes the token type and the foreground model. Its own feature after
  001 and 002 (owner); it does not wait for 003.

## D7 — Audio focus

- **Decision**: manual focus in the service as today (`handleAudioFocus = false` on both ExoPlayers);
  duck = `player.volume` ramp to 0.2 (120 ms down / 240 ms up, as native) on both players; FR-085 rules.
- **Rationale**: preserves transient-pause-and-resume and the 0.2 duck exactly (discovery Q5, R9); Rhythm also
  disables ExoPlayer focus with two players.

## D8 — Decoder Registry (ADR-002)

- **Decision**: a declarative table `(format, codec) → [providers]` in one Kotlin file. Provider kinds in 001:
  - `Platform`: available when a Media3 default extractor reads the container **and** `MediaCodecList` has a
    decoder for the codec MIME on this device (probed once per process; never assumed).
  - `KotlinAiff`: port of Choir's `AiffExtractor` (Pattern A: emits `AUDIO_RAW`), registered in a custom
    `ExtractorsFactory` (Media3 defaults + AIFF). AIFF and AIFF-C `NONE`/`twos`/`sowt`, 8/16/24/32-bit integer.
    Float AIFF-C (`fl32`/`fl64`) and compressed AIFF-C are refused by name (002 may lift).
  - Key: the scanner already stores `codec` per track (`MediaStoreLibraryScanner.kt:285-303` sniffs the real
    codec of M4A via `MediaExtractor`: `alac` vs `aac`; other formats name their one codec). The registry
    keys on it, so ALAC on the CPH2307 (no decoder) is skipped and AAC is not.
- **Scan filter** (FR-065): applied only while the new engine is selected; files with no provider are dropped
  before tag reading and counted. Skipped-files summary stored outside Room (DataStore), shown after the scan
  and in settings. Changing the engine switch triggers a rescan. Admission changes alter which rows exist,
  not how rows are parsed, so `CurrentMetadataSchemaVersion` is not bumped (re-check in the task).
- **Decode failures** (FR-064): ExoPlayer error → service `fail`/skip path with the provider name and format in
  the log; no engine retry.
- **Alternatives**: MIME-only keys (rejected: M4A hides ALAC/AAC); runtime-editable config (not needed:
  "configuration" = the table).

## D9 — Normalization port (ADR-005)

- **Decision**: port Rhythm's `ReplayGainUtil` (tag parsing) and the ramp core of `ReplayGainAudioProcessor`
  into our gain processor (D4/D5), behind a `GainSource` interface (`TagGainSource` only in 001). Our rules
  override Rhythm's where they differ: −18 LUFS reference, R128 +5 dB, target as one global pre-amp, untagged
  pre-amp, album peak in album mode, limiter instead of Rhythm's knee compressor, non-finite guards.
- **Desk findings to settle in the task**:
  - Rhythm adjusts gains by `REPLAYGAIN_REFERENCE_LOUDNESS` (`ReplayGainUtil.kt:291-299`: `-18 - ref`).
    **Owner decision (2026-10-05): honour it**: adjusted gain = tag gain + (−18 − reference); constitution and
    FR-041 updated.
  - Rhythm also reads RVA2/RVAD/RGAD and iTunNORM in comments, ID3 and Vorbis comments: keep (broader tag
    coverage, Principle 10), within the documented precedence (FR-047).
  - Opus header output gain: whether `c2.android.opus` applies it is **UNVERIFIED**; a corpus file with a
    non-zero header gain is in the corpus, and FR-041 is tested on device (S5).
- **Alternatives**: our own parser from scratch (rejected: Rhythm's covers more forms and is tested).

## D10 — Service robustness (FR-080…092)

- **Decision**: implemented where 001 rewrites the service anyway (command path, engine calls, session
  publishing). One command channel with a single consumer (FR-081); queue transport through an in-process
  holder, not Intent extras (FR-083); `CoroutineExceptionHandler` + per-command catch (FR-084); ordered
  shutdown without `runBlocking` (FR-082).
- **Note**: the service is shared by both engines, so these also change the current engine's *service* path
  (not the engine or native code). They only change failure behaviour; US1 parity tests must still pass.
  Accepted by the owner (2026-10-05); characterization tests must not assert the old faulty behaviour.

## D11 — Test builds first (prerequisite)

- **Decision**: the first implementation task adds the separate test-build application ID (`.qa` suffix on a
  test build type or flavor, own app name/icon tint, own data), updates CLAUDE.md's adb commands, and is the
  gate for every device spike and instrumented test (CLAUDE.md rule, 2026-10-05).

## Spikes (status as of 2026-10-05)

| Id | Question | Status |
|---|---|---|
| S1 | Fade method: per-sample processor (chosen) vs timer-stepped `player.volume` (12 s fade, snaps) | Desk analysis done (D4); owner chose the processor fade. Device listening **pending**: first task after the `.qa` build |
| S2 | Shared session + limiter-only DP: both tracks on one output thread, summed tones limited, overshoot, hi-res direct output (SC-015) | DP availability **DONE** (present). Routing/limiting **BLOCKED**: needs `.qa` |
| S3 | Memory/CPU with the second-player lifetime (FR-033, SC-013); processor CPU at track rate vs output rate | **BLOCKED**: needs `.qa` |
| S4 | Platform audio decoders on the CPH2307 | **DONE** (codec XMLs; no ALAC): `research/platform-codecs-cph2307.md`. Instrumented probe committed, NOT RUN (needs `.qa`) |
| S5 | Opus header gain applied once by the platform decoder (FR-041) | **BLOCKED**: needs corpus + `.qa` |
| S6 | MediaSession approach | Desk **DONE** (D6), owner confirmed (framework session in 001) |
| S7 | Rhythm / Choir sources fit | Desk **DONE** (D3, D8, D9) |

No spike result above is invented: device items were not run because the test-build application ID does not
exist yet and the daily app must not be touched.
