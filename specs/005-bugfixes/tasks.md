# 005 — Bug fixes from the owner's listening tests

Owner, 2026-10-15: "اعملها و صلحهم" (Claude implements; not Codex). Each fix has a host test; device behaviour is checked
on the `.qa` build; listening checks are the owner's.

- [x] B1 Diagnostics log. Settings → "Decode-failure log" becomes "Diagnostics log": decode failures (FR-064a, unchanged)
  plus app events in `diagnostics.jsonl` (`diagnostics/DiagnosticsLog.kt`, 500 entries): crashes (uncaught-exception
  handler, stack trace written synchronously), audio events (mixer-block measurements, route changes, limiter waiting for
  a measurement, AudioTrack underruns at most one entry per 5 s with a count). Share / Save as file always available and
  add a device header (app version, model, Android) and this process's recent warnings/errors from logcat (an app may
  read its own log lines). Clear empties both logs. Nothing leaves the device unless the user shares or saves it.
  Tests: `diagnostics/DiagnosticsLogTest`, existing `player/DecodeFailureLogTest` unchanged.
- [x] B2 Buzz in the first 1–2 s of the first song (any output, normalization/crossfade/DSP off). Cause: the session
  limiter (DynamicsProcessing, FR-052) was created with Android's frame-duration guess (`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`,
  4 ms on the CPH2307) and re-created only after the 1.5 s mixer-block probe measured the real block (40 ms). Fix:
  the guessed value never creates the effect (audio passes untouched until a real value); every measurement is stored per
  output route (`MixerBlockStore`, type + product name) and applied the moment a track starts or is re-routed; an unknown
  route removes the effect until its measurement; a silent 1.5 s probe (`SilentMixerProbe`, no focus, no session effect,
  skipped while music plays) measures the current output at app start and when an output device is added. When a player
  is created, the stored value for the output media will play on (API 33+ `getAudioDevicesForAttributes`) is applied
  before playback, so the effect is not created on the playback thread when sound starts (that cost WavPack's seek test
  50–80 ms: 336 ms, then 261 ms after this change). Phone log: 63 players, 63 limiter creations, no re-creation.
  Gate `/tmp/verify-task.geScyn`: unit, build, lint 0 errors, Media3EngineCore 12/12, Gapless 7/7, Crossfade 16/16;
  KotlinFormats seek 336 ms → fixed above, seek test rerun OK, Core rerun 12/12.
  Tests: `player/media3/LimiterFrameDurationTest` (4 new: guess creates nothing, measured-before-start, unknown route
  removes the effect, same value on a new route recreates it); existing limiter tests unchanged.
- [x] B3 Wrong duration before playback starts ("3545:41" for a 3:32 song). `FullScreenPlayer` multiplied the library
  duration (`LocalTrack.durationMillis`, already ms) by 1000, a leftover from airmedy's desktop sync (seconds).
  Test: `ui/navigation/FullScreenPlayerDurationTest`.
- [ ] B4 Found by the owner's diagnostics log (moto g75, 2026-10-15): `PlaybackService.onCreate` starts the
  `libraryAnalysisEnabled` collector before `coordinator` is assigned; with analysis off it throws "lateinit property
  coordinator has not been initialized" and the collector dies (Mood Radio would not stop when analysis is turned off).
  Pre-existing (airmedy). Fix: start the collector after the coordinator is created. Patch saved at
  `~/.local/state/sistrum-delegate/005-B4-playbackservice-order.patch`; applied after M2 so Codex's gates see a clean tree.
  Also (owner, 2026-10-15): the export's logcat section drops known-harmless platform lines (HWUI "Image decoding
  logging dropped", libdolphin.so, AudioSystem-JNI unknown native audio format, WindowOnBackDispatcher, "Missing inline
  cache", VideoCapabilities unsupported image mime, SpeakerLayoutUtil, LoudnessCodecController, MediaCodec "Media
  Quality Service not found", "Failed to query component interface", AIBinder_linkToDeath, AudioTrack retrograde
  timestamp) and ends with "(N known-harmless lines hidden)"; anything else stays. Host test on a sample export.

## Decisions

- **D-1 (owner):** the decode-failure page becomes a general diagnostics log. This widens spec 001 FR-064a's "only these
  fields" rule for the new events and the shared export (logcat lines can contain file names); the decode-failure entries
  themselves are unchanged. Revisit if: a public release needs a privacy review of the export.
- **D-2 (owner, after Claude's explanation):** while a route's frame duration is unknown the limiter is off (no effect)
  rather than running on a guess. Risk: up to ~2 s without clip prevention on a never-measured output; normalization gain
  is the only source of overs then. Revisit if: the owner hears clipping right after connecting new headphones.

## Manual checks for the owner

- First song after opening the app, on the phone speaker and on Bluetooth: no buzz.
- Connect Bluetooth while a song plays (first time for that headset): no buzz.
- Before a song starts, the full-screen player shows its real length.
- Settings → Diagnostics log → Share: the text has the header, events and recent warnings.
