# Regression 001 — measurements (T062) and full regression (T063)

Device: CPH2307 (Android 14), corpus `/sdcard/Music/SistrumTestCorpus` (49 files). Harness:
`androidApp/src/androidTest/kotlin/com/amwolfstein/sistrum/regression/Regression001MeasurementTest.kt`, run one method
at a time on `com.amwolfstein.sistrum.dev.qa` (never the daily app). Raw rows: instrumentation key `T062`, logcat tag
`T062`.

Two builds were measured (2026-10-13/14, commit `c6f5e7f` + the harness):

- **debug** = the normal `.qa` build (debuggable). Relative numbers only.
- **release-like** = the same `.qa` build with `isDebuggable = false`, `isJniDebuggable = false` (temporary local edit
  of the `qa` build type, reverted right after the build, never committed; minify off so the test APK can reach
  internal classes). Same application ID `.dev.qa`. This is part B ("measure CPU on a release build") without
  installing a release variant, whose IDs belong to the owner's apps.

## Measurement hygiene

- The `.qa` app's background loudness-analysis job (WorkManager, charging-only, T051f) runs inside the test process
  when the phone charges over USB. In two CPU runs it started about 3 minutes in and pushed every later mode, native
  included, to ~335 % CPU (job history: START/STOP of `#u0a281/17`, `/18` during those runs; live-thread deltas
  outside the job ~20 %). Those runs are discarded. Clean runs use `adb shell dumpsys battery unplug` for the run and
  `dumpsys battery reset` after.
- `wavpack_lossless.wv` is invisible to the app: MediaStore does not treat `.wv` as audio, so under scoped storage
  `File.listFiles()` (and the library scan) never see it. 48 of 49 corpus files were exercised.

## SC-003 gapless (Media3, gain tap, near-silent = |x| < 1e-4, 48 kHz)

| Join | Longest near-silent run | Verdict (≤ 10 ms) |
|---|---|---|
| gapless_flac_1 → 2 | 1 frame (0.02 ms) | PASS |
| gapless_flac_2 → 3 | 1 frame (0.02 ms) | PASS |
| gapless_mp3_1 → 2 | 511 frames (10.65 ms) | **FAIL (marginal)** |
| gapless_mp3_2 → 3 | 55 frames (1.15 ms) | PASS |

ExoPlayer reads `encoderDelay = 1105`, `encoderPadding = 815` for all three MP3s (LAME 576 + decoder 529: correct).
Host ffmpeg (LAME gapless honoured) decodes the same pair with a 1-frame join; each file is exactly 35.000 s. So the
files are fine and the extra ~511 frames come from the engine side (decoder-delay trimming at the item transition).
This closes the "Open: MP3 gapless join" note from T021: cause is in the engine/decoder setup, not the files.

### MP3 join diagnosis (T062h parts 1–2, 2026-10-14)

Deterministic over 3 more runs (511 / 55 / 1 frames). Same pairs through three paths (`sc003Mp3JoinPaths`):

| Path | mp3 1→2 | mp3 2→3 | flac 1→2 |
|---|---|---|---|
| our float sink (`FloatChainAudioSink`) | 511 | 55 | 1 |
| our int sink path (stock `DefaultAudioSink` trimming) | 511 | 55 | 1 |
| stock `ExoPlayer.Builder` + tap (no app code) | 511 | 55 | 1 |

So the gap is not caused by our sink. Raw decode per file (`sc003Mp3RawDecode`; 48 kHz stereo, 1460 MP3 frames):

| Decoder | frames | lead / tail near-silent |
|---|---|---|
| host ffmpeg, gapless-trimmed | 1,680,000 | 0 / 1 |
| platform `MediaExtractor` + `c2.android.mp3.decoder` (MediaFormat delay 576 / padding 1344) | **1,680,000** | 0 / 0 |
| stock ExoPlayer (Media3 1.11.1), single file | **1,680,815** | 0 / 1326 (file 1), 619 (file 2) |

ExoPlayer trims the 1105-frame start delay but leaves the 815-frame LAME end padding in the output; the padding's
near-silent part is what the join measures. This is Media3 behaviour (its `TrimmingAudioProcessor` outputs the held-back
end buffer unless a reconfiguration is pending before end-of-stream) and our engine inherits it. MP3 SC-003 miss: 0.65 ms
on one join; up to 815 frames (~17 ms) of padding per MP3 boundary in the worst case.

**Root cause (T062h part 3, device AudioSink call log `sc003Mp3SinkCallLog`, 2026-10-14):** a real join reconfigures
the sink (`setOutputStreamOffsetUs` → `handleDiscontinuity` → `configure(B, delay 1105, padding 815)`), and our sink
trims correctly on that path. But the platform decoder's first buffer of B is 623 frames (= 1152 − 529) and A's last
is 529 frames: `c2.android.mp3.decoder` already compensates its 529-sample decoder delay (the framework hands it the
raw LAME 576 / 1344). Media3 1.11.1's `XingFrame` (`extractor/mp3/XingFrame.java:33,135-138`) converts the LAME
values for a decoder *with* that delay (+529 delay, −529 padding), so with the platform decoder each MP3 loses 529 real
samples at its start and keeps 529 padding samples at its end — the 511 / 55-frame near-silent joins. Fix (T062i/T062j):
for MP3 decoded by MediaCodec, trim delay − 529 / padding + 529.

## SC-006 start latency (prepare call → first `positionMs() > 0`; 3 runs per file, medians)

`OutputStarted` is not comparable across engines: native queues it as soon as `prepare()` returns
(`LegacyNativeEngine.kt:136`), Media3 only on rendered-position advance. The position metric is used for the verdict.
Native's position may count frames written to AAudio rather than heard, which flatters native by the output latency
(tens of ms), not by the measured gap.

| Build | Mix | native ms | Media3 ms | ratio | Verdict (≤ 1.20) |
|---|---|---|---|---|---|
| debug | Opus-heavy (6 files) | 79 | 327 | 4.13 | FAIL |
| debug | lossless/MP3/AAC (6 files) | 84 | 241 | 2.85 | FAIL |
| release-like | Opus-heavy | 72 | 264 | 3.68 | **FAIL** |
| release-like | lossless/MP3/AAC | 78 | 167 | 2.15 | **FAIL** |

Per file (debug): ALAC 139 ms and WAV 163 ms are the fastest on Media3 (WAV is the one file where Media3 beats native:
171 ms native). Opus/Ogg 278–370 ms, FLAC/MP3/AAC 238–294 ms. Every Media3 start builds a new ExoPlayer
(`Media3Engine.prepare` → `factory.newPlayer`), checks the limiter session and waits for READY before playing; the
breakdown is not measured yet (follow-up task).

## SC-006 breakdown (T062f spike, release-like `.qa`, 5 runs per file, medians; files Opus / FLAC / MP3)

Method `sc006StartBreakdown`. "direct" = the same steps as `Media3Engine.prepare` driven from the test with listeners
(adds thread hops, so its total is higher than the engine's own).

| Variant | Total to position > 0 | READY | play → position > 0 | Other |
|---|---|---|---|---|
| `engine_fresh` (what the app does: new engine per tap) | **200 ms** (Opus 206, FLAC 193, MP3 200) | 152 | 129 | limiter 13 ms, player build 4 ms, session check < 1 ms; AudioTrack is created before `play` |
| `engine_warm` (second start on the same engine) | 154 ms (Opus 243, FLAC 154, MP3 150) | — | — | |
| `exo_plain` (stock `ExoPlayer.Builder`, no DSP, no limiter, default sink) | **257 ms** | 129 | 119 | |
| `exo_plain_lowbuf` (load control 500/2000/50/50) | 249 ms | 117 | 123 | no gain |
| `factory_nodsp` (our factory, DSP chain off, direct drive) | 289 ms | 150 | 139 | |

Findings:

1. Our own additions (limiter session, DSP chain, float sink, session check) cost ~15–20 ms. A stock ExoPlayer is not
   faster than our engine. Smaller load-control buffers do nothing. ~200 ms is ExoPlayer's own floor on this device:
   ~130–150 ms to READY (extractor + MediaCodec set-up + first buffers), ~120 ms from `play` until the AudioTrack's
   played position moves.
2. **The SC-006 comparison is not like for like.** Native's `positionMs()` counts frames handed to AAudio in the data
   callback (`ffmpeg_player.cpp:299`, `rendered_frames`), with AAudio in `POWER_SAVING` (deep-buffer) mode, so native's
   ~75 ms is "first buffer handed over", not "heard". Media3's position is the AudioTrack's played position. Native's
   true time-to-sound is ~75 ms + its deep-buffer output latency (not measurable without changing native code, which
   is frozen outside migration tasks). The real audible gap is therefore smaller than 2.1–3.7×, but Media3 is very
   likely still slower.
3. Remaining levers, none cheap: keep one engine/player alive across taps (warm start ~150 ms for FLAC/MP3, Opus not
   better), pre-prepare the tapped item (only possible when it's the preloaded next), or a lower-latency output path
   (AudioTrack performance mode / offload are not options with the float DSP chain and shared session).

Recommendation (orchestrator): SC-006's "≤ +20 % vs native" can't be met by any ExoPlayer configuration measured here.
Owner decision needed: (a) restate SC-006 as an absolute target (e.g. ≤ 250 ms tap-to-position on the release build,
which Media3 meets: 200 ms) or "no worse than stock ExoPlayer"; or (b) keep it and add an engine-reuse task (warm
engine across taps; expected ~150 ms, still ~2× native's handover number); or (c) first measure audible latency fairly
on both engines (acoustic loopback or AAudio timestamps, the latter needs a native change).

### SC-006 against the amended target (owner 2026-10-14: option (a), ≤ 300 ms)

Release-like `.qa`, `sc006StartLatencyNativeVsMedia3` three times (charging faked off), Media3 tap-to-position mix medians:
Opus-heavy 256 / 259 / 264 → **259 ms**; lossless/MP3/AAC 178 / 201 / 211 → **201 ms**. Per Opus/Ogg file 190–301 ms
(Ogg Vorbis-tagged files slightly faster). Both ≤ 300 ms: **PASS**.

## SC-014 failure attribution (Media3, every visible corpus file, prepare paused + 1 s play)

44 OK, 4 FAIL — all four fail inside `prepare()`:

| File | Provider | Format | Cause |
|---|---|---|---|
| aifc_fl32.aifc | kotlin-aiff | aifc | IOException |
| aifc_ima4.aifc | kotlin-aiff | aifc | IOException |
| wav_adpcm_ms.wav | platform | wav | IOException |
| wma_v2.wma | *(none: no extractor accepted it)* | wma | IOException |

These formats are expected to fail in 001 (ADPCM-MS, IMA4 AIFC, float AIFC, WMA are 002 decoders). **But the app path
is broken for them:** a failure thrown by `prepare()` makes `PlaybackCoordinator` call `fail()`
(`PlaybackCoordinator.kt:931`), which stops playback and writes nothing to the decode-failure log; only failures
reported as `EngineEvent.Error` after prepare go through FR-064 (log + skip). So these files are neither logged nor
skipped (FR-064, FR-064a, SC-014). Also, when no extractor accepts a file the provider is blank in the prepare path,
while the event path falls back to "platform". → fixed by T062d/T062e: rerun after T062e (debug `.qa`) — aifc_fl32/aifc_ima4
kotlin-aiff, wav_adpcm_ms platform, wma_v2 "none", every cause an `ExoPlaybackException` (3xxx/4xxx), method PASS.

## CPU and memory (60 s per mode, silent, two FLAC files, last 10 s of each track; process utime+stime)

Crossfade-on schedules a 4 s fade every ~10 s (6 fades a minute): a stress case, far heavier than real listening.

| Build | native | Media3 crossfade off | Media3 crossfade on | PSS native / off / on (MB) |
|---|---|---|---|---|
| debug (2 clean runs × 2 sets) | 8.2–8.7 % | 22.5–30.8 % | 50.8–56.5 % | ~86–97 / ~94–96 / ~96–98 |
| release-like (1 run × 2 sets) | 7.8–8.0 % | 14.2–17.6 % | 27.7–30.2 % | 33–49 / 45 / 48 |

SC-013's 1 h screen-off `batterystats` run is not done (exceeds the 10-minute device time-box; deferred to the owner).
But the short runs already show Media3 with crossfade off at about **2× native CPU** on the release-like build, so
SC-013's "no higher than native" would fail. The owner's Checkpoint M5 note applies: optimize (e.g. skip the float
conversion for 16-bit input with a neutral chain). → follow-up task.

## Summary

| Criterion | Result |
|---|---|
| SC-003 | FLAC PASS; MP3 one join 10.65 ms (limit 10) — FAIL, engine-side |
| SC-006 | Original ≤ +20 % FAIL (2.1–3.7× native's handover time; metric not like for like). **Amended by the owner 2026-10-14 to ≤ 300 ms → PASS**: Opus-heavy 259 ms, lossless/MP3/AAC 201 ms (release-like, 3-run medians) |
| SC-013 | NOT RUN (1 h, owner); short runs predict FAIL (crossfade off ≈ 2× native CPU) |
| SC-014 | FAIL at measurement → **PASS after T062e** (2026-10-14): all 4 prepare-time failures attributed (WMA provider "none"), logged and skipped by the coordinator; 44 OK |
