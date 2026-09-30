# Phase 0 — Discovery: playback path (native FFmpeg engine)

Branch: `feature/media3-migration` · Date: 2026-09-30 · Graph: `graphify-out/graph.json`
(2562 nodes, current with `androidApp/src/main` at HEAD `c4b09cc`).
Method: Graphify query/explain/path first, then targeted reads of files on the direct path only.
All claims below cite code that was read. Anything not verified is marked **UNVERIFIED**.

Paths are abbreviated: `player/` = `androidApp/src/main/kotlin/me/misa198/airmedy/player/`,
`sync/` = `…/airmedy/sync/`, `shared/` = `sharedLogic/src/commonMain/kotlin/me/misa198/airmedy/`,
`cpp/` = `androidApp/src/main/cpp/`.

---

## 1. Architecture summary

Playback is a single started (not bound) foreground `Service`, `PlaybackService`, that owns
everything: the queue, the transport state, the MediaSession, audio focus, the notification,
listening statistics, Last.fm reporting, Mood Radio refill, normalization gain lookup and crossfade
timing. It drives a native engine through a thin JNI class, `FfmpegDecoder`. The native engine
(`cpp/ffmpeg_player.cpp`, `libairmedy_player.so`) has two decode "source slots" and one AAudio
output stream. It decodes with FFmpeg, mixes (gapless and crossfade) in the AAudio callback, and
applies per-slot normalization gain, a 10-band EQ, preamp, stereo width and the focus/duck gain.

The native side never calls back into the JVM. Kotlin **polls** it every 200 ms.

### Dependency chain (real, verified)

```
UI (MainActivity, *ViewModel)                      MainActivity.kt:206, ui/screens/*ViewModel.kt
  │  AndroidPlaybackRuntime.controller()           player/AndroidPlaybackRuntime.kt:69
  ▼
PlaybackController                                 player/PlaybackController.kt:16
  │  commands:  context.startForegroundService(Intent(action, extras))
  │  state:     getters returning PlaybackService.companion MutableStateFlows (process-global)
  ▼
PlaybackService.onStartCommand → dispatch()        player/PlaybackService.kt:233, :294
  │  scope(Dispatchers.IO) + restored.await() + commandMutex
  ├─► PlaybackQueue (sharedLogic, pure)            shared/player/PlaybackQueue.kt:47  → QueueTransition
  ├─► handleTransition → playCurrent/stop…         PlaybackService.kt:392, :409
  │     ├─ PlaybackItemResolver (Room → file path) AndroidPlaybackRuntime.kt:26
  │     ├─ normalizationGain() → normalizationGainDb()  PlaybackService.kt:753, shared/player/VolumeNormalization.kt:17
  │     └─ FfmpegDecoder (JNI, Long handle)        player/FfmpegDecoder.kt:11
  │           ▼  System.loadLibrary("airmedy_player")
  │        ffmpeg_player.cpp  struct PlaybackEngine { SourceSlot slots[2]; AAudioStream* }   cpp/ffmpeg_player.cpp:86
  │           ├─ per slot: decode thread → avformat/avcodec → swr (float stereo @ AAudio rate) → ring buffer
  │           └─ AAudio data callback: gapless promote / equal-power crossfade / EQ / preamp / width / focus gain
  ├─► 200 ms ticker (PlaybackService.kt:203): position, ListeningTracker.tick, consumeNativeTransition,
  │     crossfade start, finished detection, Mood Radio refill, preload
  ├─► MediaSession (framework android.media.session) + MediaStyle notification
  ├─► AudioFocusRequest + ACTION_AUDIO_BECOMING_NOISY receiver
  ├─► ListeningTracker (shared/player/ListeningTracker.kt) → Channel → syncStore.recordListening (Room)
  ├─► LastFmService.startPlayback / reportPlayback / seek   lastfm/LastFmService.kt:156-165
  └─► PlaybackSessionStore (DataStore "queue_snapshot" + position)  player/PlaybackSessionStore.kt
```

Direct consumers outside the controller: `MainActivity.kt:531` reads `AndroidPlaybackSession.tokenOrNull()`
(output switcher). `PlaybackModels.kt:111` references service action constants. No UI code touches
`FfmpegDecoder` or `PlaybackService` directly (Graphify: all 8 ViewModels that use playback reach playback through
`PlaybackController` only).

---

## 2. Answers to the stop-condition questions

### Q1. Where playback starts and ends

**Starts**
- User play: `PlaybackController.play/shuffle/selectQueueTrack/startMoodRadio` → intent →
  `dispatch` → `queue.play(...)` → `handleTransition(Play)` → `playCurrent()` (`PlaybackService.kt:409`):
  resolve item → `state = Preparing` → MediaSession BUFFERING → `requestAudioFocus` (failure → `fail`) →
  close old decoder, `new FfmpegDecoder()` → set DSP + focus gain → `prepare(file, gainDb)` →
  optional seek → `play()` (or `pause()` when `startPaused`) → `state = Playing|Paused` →
  `lastFm.startPlayback` → `listeningTracker.start` → `preloadNext()` → `startForeground`.
- Every manual track change (next/previous/select/remove) goes through `playCurrent`, which **destroys
  and recreates the whole native engine and AAudio stream** (hard cut, no crossfade).
- Cold start: `onCreate` restore job loads the saved session and calls `restoreCurrent()`
  (`:456`): prepares **paused** at the saved position; never auto-plays. `ActionPlay`/`ActionShuffle`
  cancel the restore (`playbackActionReplacesRestoredQueue`).
- Automatic advance: native gapless promotion or crossfade (see Q4), consumed by
  `consumeNativeTransition()` (`:721`).
- Resume: `resumeCurrent()` (`:521`) — re-requests focus; if the engine is gone or its output was
  disconnected it calls `playCurrent(position)`; after natural repeat-off completion it restarts the queue
  (`shouldRestartQueueOnResume`).

**Ends**
- `stopPlayback()` (`:603`): ActionStop / queue `Stop` → listening finish STOPPED, snap fade, close engine,
  abandon focus, `state = Idle`, session STOPPED + inactive, `stopForeground(REMOVE)`.
- `stopAtCurrentTrack()` (`:617`): repeat-off queue exhausted (ticker sees `decoder.isFinished()` with
  no preload) or manual next past end → engine closed, focus abandoned, `state = Paused(item,
  duration|0)`; notification stays.
- `fail()` (`:644`): resolve/decode/focus errors → `state = Failed`, session ERROR, `stopForeground`.
- `onDestroy()` (`:273`): flushes listening (2 s timeout), saves session, closes engine, releases session,
  abandons focus, `state = Idle`. Service is `START_NOT_STICKY`; it never calls `stopSelf()`.

### Q2. How PlaybackController talks to the native player

Two hops, no binder:
1. `PlaybackController` → `PlaybackService`: fire-and-forget `startForegroundService(intent)` with
   string actions (`PlaybackService.kt:953-981`). State flows the other way through **static**
   `MutableStateFlow`s in `PlaybackService.companion` (`:992-997`); the controller returns them as
   read-only views. Crossfade seconds and artwork-blend preferences bypass the service (written to
   DataStore; the service observes them).
2. `PlaybackService` → native: synchronous JNI calls on `FfmpegDecoder` from `Dispatchers.IO`, always
   under `commandMutex`. `prepare`/`preload` pass a **detached file descriptor** of the absolute file
   path (`ParcelFileDescriptor.open(File)`); native opens `/proc/self/fd/N`. Errors come back as
   `IllegalStateException`. Native → Kotlin is **poll only**: `consumeTransition()` (atomic
   exchange of a `GaplessPromoted=1 | CrossfadeStarted=2` event), `isFinished`, `isCrossfading`,
   `isOutputDisconnected`, `positionMs`, `durationMs`, `hasPreloaded`.

Full JNI surface (`FfmpegDecoder.kt:72-96`): create/destroy, prepare, preload, setNormalizationGains,
setFocusGain, clearPreloaded, hasPreloaded, beginCrossfade, finishCrossfade, snapCrossfade,
isCrossfading, consumeTransition, setGlobalDspConfig, play, pause, stop, seekTo, durationMs, positionMs,
preloadedDurationMs, preloadedPositionMs, isFinished, isOutputDisconnected.

### Q3. Queue / state contracts

- **Queue** = `PlaybackQueue` in sharedLogic (pure Kotlin, not thread-safe; callers serialize via
  `commandMutex`). Fields: `original`, `active` (shuffled order), `currentIndex`, `shuffle`,
  `repeatMode` (Off/One/All). Operations return `QueueTransition` = `Play(trackId) | StopAtCurrent | Stop
  | Unchanged`; the service interprets them. `peekNext()` is the look-ahead used for preloading
  (Repeat One → the current track; Repeat All wraps).
- Published as `PlaybackQueueSnapshot` (`@Serializable`) to `queueState`, to `MediaSession.setQueue`
  (queue item id = active index, `activeQueueItemId`), and persisted async to DataStore on every
  `publishQueue()`.
- **State** = `PlaybackState` sealed interface: `Idle | Preparing(item) | Playing(item, pos, dur) |
  Paused(item, pos, dur) | Failed(trackId, reason)` (`PlaybackModels.kt:101`). Position is refreshed by
  the 200 ms ticker only while `Playing` and only when changed; each refresh also updates the
  MediaSession and calls `lastFm.reportPlayback`.
- Other flows: `artworkCrossfade: StateFlow<ArtworkCrossfadeTransition?>`, `moodRadioActive`,
  `crossfadeSeconds` (exposed as `Flow<Int>`, backed by a MutableStateFlow), `blendArtworkDuringCrossfade`.
- Command semantics that must be preserved:
  - Previous restarts the current track when position > 3000 ms (`PreviousRestartThresholdMs`).
  - Next/Previous keep the paused state (`preservePlaybackState = true`); Play/Select/Remove start playing.
  - `MoodRadioStoppingActions` (Play, Shuffle, Stop, ClearQueue, PlayNext, Append, Remove, Reorder) end Mood Radio.
  - `PreloadResyncActions` (SetShuffle, SetRepeat, PlayNext, Remove, Reorder, StartMoodRadio) snap a
    running crossfade and re-preload.
  - Every command first consumes any pending native transition, so the queue never lags the audio.
  - Restored queues drop tracks that are no longer in the library (`queueForAvailableTracks`).
- Note: the controller also exposes `setShuffle`, `setRepeatMode`, `removeFromQueue`, `reorderQueue`,
  `seekTo`, `setCrossfadeSeconds`, `setBlendArtworkDuringCrossfade`, `resolve` — wider than the
  constitution's list. All of them are part of the contract. `ActionSetCrossfade` exists in the service
  but nothing sends it (**dead path**).

### Q4. How crossfade works today

**Who drives it:** Kotlin decides *when* and *how long*; native does the mixing.

1. Preload: `preloadNext()` opens `queue.peekNext()` into the idle native slot, with its own
   normalization gain. It is not reloaded while a fade runs (`canPreloadNext(isCrossfading)`), because both
   slots are occupied.
2. Start (ticker, ≤200 ms granularity; `maybeStartCrossfade`, `PlaybackService.kt:779`): requires
   `Playing`, a preloaded item, no fade running, and `shouldStartCrossfade`: seconds > 0, duration ≥ 2000 ms,
   `remaining ∈ 401..min(seconds·1000, duration/2)`. Duration = `min(seconds·1000, duration/2, remaining)`.
   Settings: 0 (off) … 12 s, default when enabled 4 s (`PlaybackPreferences.kt:62-64`).
3. **begin** (`nativeBeginCrossfade`, cpp:560): preloaded → active, old active → outgoing,
   `fade_frames_total = ms·rate/1000`, `crossfading = true`, event `CrossfadeStarted`. No-op if a fade is
   running or nothing is preloaded.
4. Mixing (audio callback, cpp:361): per frame, equal-power `out·cos(φ) + in·sin(φ)`,
   φ = 0…π/2 over the fade length. Each slot keeps its own normalization gain. EQ/preamp/width/focus
   apply after the mix. When the fade length is reached the callback retires the outgoing slot and clears
   `crossfading` (`finish_fade_in_callback`).
5. Kotlin, same tick: `consumeNativeTransition()` advances `queue.next()` (fails playback if it doesn't
   match the preloaded item), switches `state` to `Playing(incoming)` right at fade **start**, finishes the old
   listening session as COMPLETED, starts the new one, Last.fm `startPlayback`, Now Playing, notification,
   publishes the queue. It also emits `artworkCrossfade(id, from, to, durationMs)` for the UI artwork blend.
6. After the fade ends (next ticks): `finishListeningCrossfade()` → `ListeningTracker.splitCrossfadeOverlap`
   (overlap time attributed correctly), `clearArtworkCrossfade()`, then preload i+2.

**Snap semantics:** `nativeSnapCrossfade` and `nativeFinishCrossfade` are identical (`snap_fade`:
drop the outgoing source at once; incoming continues at full level). Kotlin never calls
`finishCrossfade()` (**dead API**). Snap happens on: PreloadResyncActions during a fade, `stopPlayback`,
`stopAtCurrentTrack`, and natively inside `pause`, `stop` and `seekTo`. Changing the crossfade setting
never alters a running fade.

**Gapless (crossfade off, or track too short):** when the active ring is empty *and* its demuxer hit EOF,
the callback promotes the preloaded slot in the same buffer (`promote_gapless`) and posts
`GaplessPromoted`. Kotlin consumes it the same way (without the listening-overlap and artwork parts).
Repeat One preloads the same file, so it loops gaplessly or crossfades into itself.

### Q5. Where MediaSession and audio focus live

Both are in `PlaybackService.onCreate` (`:145-179`). There is no Media3 or MediaSessionCompat anywhere.
- **MediaSession**: framework `android.media.session.MediaSession("AirmedyPlayback")`. Callbacks
  play/pause/next/previous/skipToQueueItem/seekTo/stop → `dispatch`. Metadata (`publishNowPlaying`, `:838`):
  MEDIA_ID, TITLE, ARTIST, DURATION, and 512 px ALBUM_ART/ART/DISPLAY_ICON bitmaps (LRU 20). It does
  **not** set ALBUM or ALBUM_ARTIST. PlaybackState actions: play/pause/playPause/next/prev/seekTo/stop,
  active queue item id. Token published through `AndroidPlaybackSession` for `MainActivity`'s output
  switcher. Notification: `Notification.MediaStyle` with the session token, channel `playback`, id 2002,
  foreground type `mediaPlayback`. The root cause of the lock-screen "Unknown artist" was **not
  investigated** in this phase: `ARTIST` is set from `item.artist`, so the fault is somewhere else
  (**UNVERIFIED**; deferred to the MediaSession task per the constitution).
- **Audio focus**: `AudioFocusRequest(GAIN, USAGE_MEDIA/CONTENT_TYPE_MUSIC)`. Listener → `audioFocusChangeAction`
  (`PlaybackModels.kt:29`): LOSS → pause; LOSS_TRANSIENT → pause and resume on gain; CAN_DUCK → native focus
  gain 0.2 (120 ms ramp down, 240 ms up, done in the native callback); GAIN → restore (+ resume if flagged).
  Focus is requested in `playCurrent` and `resumeCurrent` and abandoned in stop/stopAtCurrent/onDestroy.
  It is **not** abandoned on a user pause.
- **Becoming noisy**: a receiver for `ACTION_AUDIO_BECOMING_NOISY` → pause.
- **Output route change**: an AAudio `DISCONNECTED` error sets a flag; the ticker calls
  `recoverAfterOutputDisconnect()`, which rebuilds the engine at the same position without splitting the
  listening session.

### Q6. Why volume normalization and Mood Radio fail today

**Both fail for lack of analysis *data*, not because of the playback engine.** The native engine already
applies normalization gain correctly, and Mood Radio has no dependency on the native layer at all. This
contradicts the constitution's premise (see §4 and Risks R1/R2).

**Volume normalization**
- Wiring is complete: `NormalizationPreferences` (enabled, Track/Album, target −14 LUFS, preventClip) →
  `PlaybackService.normalizationGain()` → `normalizationGainDb()` (`gain = target − LUFS`, clipped to
  `−true_peak`; Album mode averages album LUFS only when the next track is on the same album) →
  `FfmpegDecoder.prepare/preload(gainDb)` and `setNormalizationGains` → native linear gain per slot
  (`read_frame`, cpp:295).
- Data source: `AndroidLibrarySyncStore.activeAnalyses()` (`sync/SyncDatabase.kt:553`) reads
  `sync_documents` rows with `kind = 'analysis'` (JSON fields `loudness_lufs`, `true_peak`) for the active
  plan. Those rows came from the **removed desktop sync**.
- The local scan never writes them: `writeLocalLibrary()` (`SyncDatabase.kt:840`) inserts a plan, assets,
  tracks and search documents, but **no `sync_documents`**. The DAO has no insert method for them, and
  `deleteStaleDocuments(planId)` deletes the old desktop rows on the first local scan. The scanner and
  `EmbeddedTagReader` read no ReplayGain/R128 tags (grep: no matches).
- Result: `activeAnalyses()` is always empty → gain = 0, and the setting is **force-disabled** in two places:
  `PlaybackService.kt:756` (`normalizationPreferences.disable()`) and `MainActivity.kt:199-201`
  (`analysisAvailable == false`).
- Missing native capability: loudness *measurement*. FFmpeg is built with `--disable-avfilter`
  (no `ebur128`), and no code computes LUFS/true peak.
- What it needs: (a) a source of per-track `loudness_lufs` + `true_peak` (read ReplayGain/R128 tags during
  the scan, and/or measure EBU R128 on device); (b) storage (the existing `sync_documents` kind `analysis`
  table can hold it **without a Room schema change**, but it must be written by `writeLocalLibrary` and
  kept across rescans); (c) per-item gain on the Media3 engine that is correct during a crossfade (a single
  `ExoPlayer.volume` is not per-item if two players overlap). The spec must decide (a) and (c). Owner input
  is needed on whether to measure on device (cost and time for a whole library).

**Mood Radio**
- Wiring is complete: `startMoodRadio` / `refillMoodRadioIfNeeded` (`PlaybackService.kt:354-388`) call
  `selectMoodRadio` (`shared/mood/MoodRadio.kt`, nearest neighbour over energy/danceability/brightness/tempo,
  album/artist diversity). The UI offers the action only for `moodRadioEligibleTrackIds`.
- Two blockers, both data:
  1. `library_analysis_enabled` is hard-coded `false` in `localLibraryManifest()` (`SyncDatabase.kt:1406`) →
     `startMoodRadio` returns at its first check, and the eligible set is empty, so the menu item is hidden.
  2. No track has the four features: they were desktop-computed `analysis` documents (same table as above),
     which the local scan never writes.
- Missing native capability: audio **feature extraction** (decode to PCM + DSP for energy, danceability,
  spectral brightness and tempo/BPM). Neither the current engine nor ExoPlayer playback provides this; it is
  an offline analysis pipeline. The desktop algorithms are not in this repo, so parity with them cannot be
  verified here (**UNVERIFIED**).
- What it needs: an on-device analysis job that writes the four features (and could write loudness at the
  same pass), flips `library_analysis_enabled`, and survives rescans. Media3 migration by itself fixes
  **nothing** here.

### Q7. Which files must change

| File | Why |
|---|---|
| `player/PlaybackService.kt` | Split: transport/engine calls move behind the engine abstraction; MediaSession → Media3 `MediaSessionService`/`MediaSession` (later phase); focus → ExoPlayer `handleAudioFocus` or kept manual; ticker polling → listener events |
| `player/FfmpegDecoder.kt` | Wrapped by `LegacyNativeEngine` (unchanged behaviour), later removed |
| `player/PlaybackController.kt` | Public API and flows unchanged; only the backing source may change (keep intent path or move to a MediaController) |
| `player/PlaybackModels.kt` | Crossfade policy functions stay; `canPreloadNext` / two-slot assumptions become engine-specific |
| `player/AndroidPlaybackSession.kt`, `MainActivity.kt:531` | Token type changes if the session becomes Media3 (output switcher) |
| `player/EqualizerPreferences.kt` (`GlobalDspConfig` producer) | EQ/preamp/width must be re-implemented as a Media3 `AudioProcessor` |
| `player/PlaybackPreferences.kt` | Only if a crossfade setting or engine selector is added |
| New: `player/PlaybackEngine.kt` (name TBD — see R6), `player/LegacyNativeEngine.kt`, `player/Media3Engine.kt`, audio processors | Strangler target shape |
| `gradle/libs.versions.toml`, `androidApp/build.gradle.kts` | Media3 dependencies (exoplayer, session; maybe the ffmpeg decoder extension) |
| `AndroidManifest.xml` | `MediaSessionService` intent filter if Media3 session is adopted |
| `sync/SyncDatabase.kt` (writeLocalLibrary, manifest flag), `sync/MediaStoreLibraryScanner.kt` / `EmbeddedTagReader.kt` | Only for normalization / Mood Radio data (Q6); `CurrentMetadataSchemaVersion` bump if parsing changes |
| `cpp/*`, `scripts/build-ffmpeg-android.sh`, `CMakeLists.txt` | Untouched until legacy removal; kept if FFmpeg is kept for analysis or the Media3 FFmpeg decoder |
| Tests: `androidApp/src/test/.../player/*`, `sharedLogic/src/commonTest/.../player/*` | Characterization + engine contract tests |

Unaffected: `shared/player/PlaybackQueue.kt`, `ListeningTracker.kt`, `VolumeNormalization.kt`,
`shared/mood/MoodRadio.kt`, `lastfm/*`, UI screens (they only see `PlaybackController`).

### Q8. Behaviours that must be preserved

1. Controller API + flows exactly as in Q3 (incl. the extra methods), static-flow semantics (state visible
   before/after service lifetime; `Idle` on destroy).
2. Queue semantics: shuffle/original order, repeat Off/One/All, previous > 3 s restarts, next/prev keep
   paused, stop-at-end retains the last item (`Paused` at duration; manual exhaustion → 0), resume after
   natural end restarts the queue, play-next/append/remove/reorder/select, Mood Radio stopping actions.
3. Session restore: paused, at the saved position, never auto-plays; a new Play cancels restore; missing
   tracks are dropped.
4. Gapless transitions between consecutive tracks (sample-accurate today).
5. Crossfade: start rule, duration clamp, equal-power curve, only on automatic advance, snap on
   pause/seek/stop/queue-edit, queue/state/Last.fm/listening switch at fade **start**, artwork crossfade
   event with id/from/to/duration, overlap split in listening stats, i+2 preloaded only after the fade.
6. Listening stats: start/tick/pause/resume/finish reasons (COMPLETED/SKIPPED/STOPPED),
   suspend/resume across output-route recovery, overlap split, 180-day retention, open-attempt recovery on start.
7. Last.fm: `startPlayback` on every track start (incl. restore and native transitions), `seek`,
   `reportPlayback` each position tick.
8. Audio focus: pause / transient pause + auto-resume / duck to 0.2 with smooth ramps / restore; noisy → pause;
   route change → seamless recreate at the same position.
9. DSP: 10-band EQ (32 Hz–16 kHz peaking, Q = 1), preamp, stereo width, applied after the mix.
10. Seek accuracy (discard pre-roll to the exact timestamp), position clamping.
11. MediaSession: transport actions, queue with active item id, artwork bitmaps, notification and
    content intent, foreground-service rules (a preference edit must not start the service).
12. Output format: 48 kHz-or-device-rate float stereo is internal; there is no user-visible requirement
    beyond "all admitted files play" (see §3).
13. Features on the constitution list: Stats, Last.fm, Lyrics sync (driven by `PlaybackState.positionMs`
    in `FullScreenPlayer.kt`, so ~200 ms resolution must be kept), Mood Radio, Artwork, Metadata, Queue,
    Crossfade, Normalization. Plus **Equalizer**, which is not on the constitution's list but exists
    (Risk R4).

---

## 3. Format inventory

What the app admits: `MediaStoreLibraryScanner` queries `MediaStore.Audio.Media` with
`IS_MUSIC != 0` and duration ≥ 30 s (or unknown/<1 s) plus the user's folder filter. **There is no
MIME or extension allow-list**: anything MediaStore flags as music is admitted and handed to FFmpeg.
Playback uses the **absolute file path** from `MediaStore…DATA` (no content URIs). The format names below
come from the scanner's `FormatBySubtype`/`FormatByExtension` tables (`MediaStoreLibraryScanner.kt:536-551`),
i.e. the formats the app knows how to label.

FFmpeg build (`scripts/build-ffmpeg-android.sh:56-60`): `--disable-everything` then `--enable-decoders
--enable-demuxers --enable-parsers --enable-protocol=file` (all decoders/demuxers), `--disable-avfilter`,
arm64-v8a only. So **every format FFmpeg supports decodes today**, including everything in the table.

Media3 column: based on the Media3 supported-formats docs (context7, `/websites/developer_android_media_media3`):
container list = MP4/M4A/FMP4, WebM, Matroska, MP3, Ogg (Vorbis/Opus/FLAC), WAV, MPEG-TS/PS, FLV, ADTS, FLAC, AMR.
Sample decoding uses platform decoders by default. The FFmpeg extension is **decoder-only**: it needs a
Media3 extractor for the container.

| Format (scanner label) | Typical codec | Media3 extractor | Decoder | Classification |
|---|---|---|---|---|
| mp3 | MP3 | Mp3Extractor | platform | **Supported** (VBR without Xing/VBRI: seeking needs constant-bitrate seeking) |
| aac (ADTS) | AAC | AdtsExtractor | platform | **Supported** (seek only with CBR seeking enabled) |
| m4a / mp4 (AAC) | AAC-LC/HE | Mp4Extractor | platform | **Supported** |
| m4a (ALAC) | ALAC | Mp4Extractor | platform ALAC decoder, or FFmpeg ext `alac` | **Device-dependent** (verify on the owner's device), covered by the FFmpeg ext |
| flac | FLAC | FlacExtractor | platform (API 27+) | **Supported** (check 24-bit/hi-res and 32-bit on device) |
| wav | PCM int/float, IMA ADPCM | WavExtractor | raw PCM | **Supported** for PCM; other codecs inside WAV (MS-ADPCM, etc.) **not supported** |
| ogg (Vorbis) | Vorbis | OggExtractor | platform | **Supported** |
| ogg/opus | Opus | OggExtractor | platform (API 29+) | **Supported** |
| aiff / aif / aifc | PCM big-endian | none | — | **Not supported without an extension** (needs a custom extractor, even with the FFmpeg ext) |
| ape | Monkey's Audio | none | — | **Not supported without an extension** (no extractor; FFmpeg ext doesn't ship an `ape` mapping) |
| wv | WavPack | none | — | **Not supported without an extension** |
| dsf / dff | DSD | none | — | **Not supported without an extension** |
| wma | WMA in ASF | none | — | **Not supported without an extension** |
| mka / webm (if present) | Opus/Vorbis/FLAC… | MatroskaExtractor | platform | Supported (not labelled by the scanner, but admitted if MediaStore marks it as music) |

"Not supported without an extension" here means the Media3 FFmpeg *decoder* extension alone is **not
enough**. These need a custom `Extractor` or a custom FFmpeg-demuxing `MediaSource`/renderer, or a documented
drop (Principle 6: owner approval).

**Not done (needs the owner):** the inventory of the owner's actual library (counts per format). Next step:
a read-only query of the owner's scanned DB (`codec` / format fields per track) on device, or a MediaStore
MIME histogram. Not run in this phase — no device query was made.

---

## 4. Broken-feature analysis (summary)

| | Normalization | Mood Radio |
|---|---|---|
| Wired end-to-end in code | Yes (prefs → gain calc → native per-slot gain) | Yes (selection → queue → refill → UI gating) |
| Native engine lacks | Nothing for *applying* gain; lacks loudness **measurement** (no avfilter/ebur128) | Nothing for playback; there is no feature extraction anywhere |
| Data it depends on | `sync_documents(kind='analysis').loudness_lufs, true_peak` | same rows: `energy, danceability, brightness, tempo` + manifest `library_analysis_enabled` |
| Why it fails | Rows never written by the local scan (and old ones are deleted) → gain 0 + setting force-disabled | Flag hard-coded false + no features → start returns early, menu hidden |
| Fixed by Media3 alone? | **No** — needs a data source; Media3 only changes how gain is applied | **No** |
| Room schema change needed? | Not necessarily (existing table + kind) | Not necessarily (existing table + manifest flag) |

The constitution says "the loudness data the scanner already stores (e.g. `loudness_lufs`, `true_peak`)".
That is **not true for the current code**: the fields are *read* but never *stored* by the local scanner.
The constitution/spec must be corrected before `/speckit-specify` defines acceptance criteria (owner decision).

---

## 5. Reading lists for the next phases

**MUST READ** (direct path)
- `player/PlaybackService.kt`, `player/FfmpegDecoder.kt`, `player/PlaybackController.kt`,
  `player/PlaybackModels.kt`, `player/AndroidPlaybackRuntime.kt`, `player/AndroidPlaybackSession.kt`
- `cpp/ffmpeg_player.cpp` (crossfade, gapless and DSP semantics — reference for parity)
- `shared/player/PlaybackQueue.kt`, `shared/player/VolumeNormalization.kt`, `shared/player/ListeningTracker.kt`
- `player/PlaybackPreferences.kt`, `player/EqualizerPreferences.kt` (`equalizerDspConfig`), `player/NormalizationPreferences.kt`
- Tests: `androidApp/src/test/.../player/PlaybackModelsTest.kt` (22 tests), `AndroidPlaybackRuntimeTest.kt` (3),
  `sharedLogic/src/commonTest/.../player/PlaybackQueueTest.kt` (12), `ListeningTrackerTest.kt` (4),
  `VolumeNormalizationTest.kt` (3), `mood/MoodRadioTest.kt` (2)

**MAY READ** (only the named function)
- `sync/SyncDatabase.kt`: `activeAnalyses`, `moodRadioTracks`, `moodRadioEligibleTrackIds`,
  `writeLocalLibrary`, `localLibraryManifest`
- `lastfm/LastFmService.kt`: `startPlayback`, `seek`, `reportPlayback`
- `MainActivity.kt`: lines ~151-210 (playback flows, normalization gating), ~531 (session token)
- `player/PlaybackSessionStore.kt`, `shared/mood/MoodRadio.kt`
- `sync/MediaStoreLibraryScanner.kt`: `Selection`, `audioFormatOf`, format tables (format parity only)
- androidTest: `FullScreenPlayerTest`, `MiniPlayerTest`, `QueueTransportAvailabilityTest`, `CrossfadeDurationSliderTest`

**DO NOT READ** (not on the playback path)
- UI screens/components beyond the lines above, `ui/components/MaterialSymbols.kt`, `res/`, localization
- Library, playlist and search code in `sync/` (except the functions listed above), `EmbeddedTagReader.kt`
  (unless the ReplayGain-tag option is chosen), artwork storage
- `tools/`, `graphify-out/` contents, `build/`, `jniLibs/`, the FFmpeg source tree

---

## 6. Risks

- **R1 (high) — normalization premise is wrong.** No loudness data exists on device. Media3 can't "fix"
  normalization without a new data source. Constitution Principle 7 forbids "a new normalization
  system"; a loudness *source* may or may not count as one → owner decision.
- **R2 (high) — Mood Radio premise is wrong.** It needs an on-device audio-analysis pipeline
  (four features, desktop-parity algorithms that are not available), which is a new feature, not a playback
  fix. Scope decision needed.
- **R3 (high) — format parity.** AIFF, APE, WavPack, DSF/DFF and WMA have no Media3 extractor; the FFmpeg
  decoder extension doesn't help. Retiring the native engine drops them unless a custom extractor/source
  is built. The owner's library inventory is still needed (not run).
- **R4 (medium) — Equalizer not on the no-feature-loss list.** A 10-band EQ, preamp and stereo width run in
  the native callback. Media3 needs a custom `AudioProcessor` for parity; the constitution should list the EQ.
- **R5 (high) — crossfade on ExoPlayer.** Today it's one output stream with two decoders mixed in the
  callback (sample-accurate, equal-power, per-source gain). ExoPlayer is one player → one decoder
  pipeline; crossfade needs either two ExoPlayer instances (volume ramps, focus/session attached to
  one of them, audio-session sharing) or a custom mixing `AudioProcessor`/renderer. It must be decided by
  the spike (Principle 5) before any engine interface is fixed. Gapless comes free with ExoPlayer playlists;
  the two-player approach complicates it.
- **R6 (low) — naming clash.** The native struct is already called `PlaybackEngine` (cpp:86). A Kotlin
  interface `PlaybackEngine` is legal (different language/namespace) but confusing in Graphify results;
  consider `AudioEngine`/`PlayerEngine` or renaming nothing and accepting it (spec decision).
- **R7 (medium) — polling contract.** State, stats, Last.fm and lyrics depend on the 200 ms ticker and on
  "queue switches at fade start". Media3 is event-driven (`onMediaItemTransition` fires at the *end* of a
  crossfade if one is built from two players). Characterization tests must pin the current timing.
- **R8 (medium) — no service-level tests.** `PlaybackService` (1003 lines) has no unit or characterization
  tests; the existing tests cover pure functions and the queue only. Characterization tests are needed before
  the abstraction is introduced (tests-first, per CLAUDE.md).
- **R9 (medium) — MediaSession migration.** Moving to Media3 `MediaSessionService` changes the token type
  (`MainActivity` output switcher), notification ownership and foreground rules, and may change how
  `PlaybackController` reaches the service (intents vs `MediaController`). ExoPlayer's built-in focus
  handling differs (it doesn't do transient-pause-and-resume the same way, and ducking is done by the
  system on API 26+ unless disabled).
- **R10 (low) — dead code.** `finishCrossfade`, `ActionSetCrossfade`, `preloadedDurationMs/PositionMs`,
  `stop` are unused from Kotlin; the Legacy engine wrapper doesn't need to expose them.
- **R11 (low) — paths not URIs.** Playback opens absolute paths from `MediaStore.DATA`; Media3 can play
  `file://` URIs directly. Content URIs are not used (Principle 4: "whichever the app actually uses" → file paths).
- **R12 (low) — single ABI.** The native lib is arm64-v8a only; a Media3 FFmpeg extension (if chosen) must
  follow the same build script/ABI and the 16 KB page-size link flag.

---

## 7. Recommended migration boundaries

1. **Characterization first (tests-only task).** Pin the pure policies that already exist
   (`shouldStartCrossfade`, `crossfadeDurationMs`, `stoppedCurrentPosition`, `shouldRestartQueueOnResume`,
   `audioFocusChangeAction`, …) and add service-level characterization with a fake engine *after* the seam
   exists. They must not assert that normalization or Mood Radio work.
2. **Seam = what `PlaybackService` calls on `FfmpegDecoder`, minus the dead APIs**, expressed as
   engine-neutral operations: prepare(item, gain, startPos, paused), preloadNext(item, gain),
   clearPreloaded, play/pause/seek, position/duration, focus gain, DSP config, normalization gains,
   beginCrossfade(ms)/snap/isCrossfading, and an **event stream** (transition started/gapless/finished,
   output disconnected, error) replacing polling. `LegacyNativeEngine` adapts polling to events with
   zero behaviour change. The crossfade spike decides whether "two slots" survives as a concept.
3. **Keep above the seam, unchanged:** `PlaybackQueue`, `ListeningTracker`, Last.fm calls, Mood Radio
   selection, `normalizationGainDb`, `PlaybackController` API and flows, session persistence.
4. **MediaSession/notification/focus = a separate boundary** from the audio engine, migrated after the
   crossfade ADR (Principle 5), and where "Unknown artist" is fixed.
5. **Analysis data (normalization + Mood Radio) = its own track**, independent of the engine: a data-source
   decision (tags vs on-device measurement), written into the existing `sync_documents` kind `analysis`,
   with no Room schema change. Gain *application* on Media3 goes with the engine work.
6. **Format parity gate** before any default switch: the owner's library inventory + per-format decision for
   AIFF/APE/WV/DSD/WMA.
7. **Native code stays** until the legacy engine is retired; FFmpeg may stay afterwards if it is chosen for
   analysis or for demuxing unsupported containers (Principle 8).
