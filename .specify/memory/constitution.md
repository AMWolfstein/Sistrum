# Sistrum Constitution — Playback Migration

Revised 2026-10-01 with the owner's Phase 0 decisions
(evidence: `specs/001-media3-migration/research/discovery.md`).

## Goal

Add Android Media3 + ExoPlayer as a second playback engine next to the current
FFmpeg/native (JNI) player, and move PlaybackController onto it gradually,
preserving current app behavior. Local/offline playback only. This is an
additive architectural migration, not a rewrite and not "add ExoPlayer".

Target shape:

```
PlaybackController
      ↓
PlaybackEngine (abstraction; name may follow repo conventions —
                note the native struct is already called PlaybackEngine)
      ↓
LegacyNativeEngine  |  Media3Engine (ExoPlayer + Jellyfin FFmpeg decoder)
```

## Principles

1. **Additive, not destructive.** The abstraction is introduced first with the
   existing native player as its first implementation, with zero behavior
   change. The Media3 engine is built behind the same interface. Both engines
   coexist and stay selectable (engine switch) for the whole migration.
   PlaybackController moves to ExoPlayer gradually, behind that switch.

2. **Preserve contracts.** PlaybackController keeps its responsibilities and
   public API: play, pause, resume, stop, clearQueue, next, previous, shuffle,
   setShuffle, setRepeatMode, playNext, append, startMoodRadio,
   selectQueueTrack, removeFromQueue, reorderQueue, seekTo,
   setCrossfadeSeconds, setBlendArtworkDuringCrossfade; and its StateFlows
   (playback state, queue, artworkCrossfade, moodRadioActive,
   crossfadeSeconds). Any contract change must be necessary, documented in an
   ADR, and covered by tests.

3. **No feature loss.** Never remove or disable: Listening Statistics, Last.fm
   (start, completion, scrobble), Lyrics (including sync), Mood Radio
   machinery, Artwork, Metadata, Queue, Crossfade, Volume normalization, and
   the **Equalizer (10-band EQ, preamp, stereo width)**.
   Sequencing exception (branch only): on `feature/media3-migration`,
   crossfade, equalizer and normalization may be temporarily inactive **on
   the Media3 path** and restored in later tasks. The native path keeps them
   working. Nothing merges into `main` until all of them work on Media3.

4. **No online playback.** Local files only. The app plays absolute file paths
   from MediaStore (`MediaStore.Audio.Media.DATA`); no content URIs, no
   network sources.

5. **Crossfade approach is decided.** Port Rhythm's dual-ExoPlayer (A/B)
   technique from `RhythmPlayerEngine.kt`: pre-buffer the next track on the
   idle player at volume 0, swap early, shaped fade curve, explicit handling
   of repeat-one and skip-previous during a transition, recreate the outgoing
   player afterward. The plan-phase spike validates it against our contract
   (MediaSession with two players, queue sync, listening stats, Last.fm,
   artwork crossfade event); it does not redesign it. MediaSession
   integration follows this design.

6. **Format parity.** Keep every format the app plays today (FFmpeg decodes
   everything MediaStore marks as music). On Media3, decoding uses platform
   decoders plus Jellyfin's Media3 FFmpeg decoder. AIFF, APE, WavPack, DSD
   (DSF/DFF) and WMA have no Media3 extractor: they are a **deferred open
   item**. They matter equally, whatever any one library contains
   (Principle 10). Until the owner decides, these formats keep playing
   through the native player (per-item engine routing or equivalent,
   decided in the plan). **Known limitation (temporary):** a transition
   between a native-routed and a Media3-routed item is a hard cut (no
   crossfade, no gapless). It is documented as a known limitation and tied
   to the deferred-formats decision.

7. **Evidence over assumption.** Supported formats, normalization behavior,
   and crossfade semantics are determined from the actual code and data, not
   assumed. Do not add formats or features beyond what this constitution
   names.

8. **Native player and FFmpeg build stay.** The native player,
   `androidApp/src/main/cpp`, and `scripts/build-ffmpeg-android.sh` stay
   untouched and selectable throughout. Deleting the native player is **not**
   a task in this migration. After Media3 works fully and regression tests
   pass, report whether the native player has become dead code; the owner
   decides. Removing the native player and removing our FFmpeg build are
   separate decisions (the FFmpeg build is also needed by the future analyzer,
   see below).

9. **Honest verification.** Tests, builds, and Graphify results are reported
   exactly as run. Device-only checks belong to the owner. Test device:
   **CPH2307** (a test sample, not the target; see Principle 10).

10. **Built for all users.** Sistrum is built for everyone who installs it,
    not only the owner. The owner's library and the CPH2307 are test
    samples, not the scope. Decisions, specs, acceptance criteria and
    defaults must not assume the owner's formats, tagging tools, library
    composition or device. In particular:
    - The deferred formats (AIFF, APE, WavPack, DSD, WMA) matter equally,
      regardless of what the owner's library contains.
    - Tag-based normalization must support every common gain-tag form (see
      "In scope"), and behave sensibly for untagged files.
    - Performance claims measured on the owner's Opus-heavy library must be
      checked against other format mixes (e.g. FLAC/ALAC/hi-res PCM, MP3,
      AAC) before they are treated as general.
    - Owner-library facts (inventory, tagging) may be cited as evidence for
      one case, never as the reason to drop or narrow support.

## Engine switch

- A **hidden developer setting** selects the engine. Native is the default on
  `feature/media3-migration` until the regression tests pass on Media3.
- A change takes effect at the **next playback start**, never mid-track.
- The default **flips to Media3 before any merge** into `main`.

## Test builds and test corpus

- Test/debug builds use a **separate application ID suffix** so they install
  next to the owner's daily app (`me.misa198.airmedy.dev`) and never touch
  its data. No uninstalls. The suffix must not be `.test`: the
  instrumentation APK is already `me.misa198.airmedy.dev.test`; use e.g.
  `.qa`. CLAUDE.md's adb test commands are updated in the same task.
- A generated test corpus (all formats and tag forms of Principle 10,
  including untagged files) lives in `/sdcard/Music/SistrumTestCorpus`; the
  owner blocklists it in the daily app. No `.nomedia`.
- Never fake a format. APE and DSD (DSF/DFF) cannot be produced by ffmpeg;
  they need real encoders or owner-supplied samples (see HANDOFF).

## Dependencies and licensing

- Add `androidx.media3` (ExoPlayer, session as needed) and Jellyfin's
  `org.jellyfin.media3:media3-ffmpeg-decoder` (GPL-3.0).
- Porting from **cromaguy/Rhythm** (primary reference) and
  **PixelPlayerHQ/PixelPlayerOSS**, both GPL-3.0, is allowed with attribution
  (SPDX header in ported files, as in `sync/MediaScanFilter.kt`).
- Third-party notices must list every added dependency and ported source. No
  notices file exists yet; the task that adds the first dependency creates it.

## In scope during migration

- **Volume normalization, stage 1 (tag-based gain).** Port Rhythm's
  `ReplayGainAudioProcessor` / `ReplayGainUtil`, which read gain tags at
  playback time from Media3 metadata. It must support every common form
  (Principle 10), extending the port where it falls short:
  - MP3: ID3v2 `TXXX` frames `REPLAYGAIN_TRACK_GAIN` / `_ALBUM_GAIN` /
    `_TRACK_PEAK` / `_ALBUM_PEAK` (case-insensitive descriptions).
  - FLAC / Ogg Vorbis (Vorbis comments): `REPLAYGAIN_*`.
  - Opus: `R128_TRACK_GAIN` / `R128_ALBUM_GAIN` (Q7.8 integer dB, relative
    to −23 LUFS). Guard against applying the Opus header output gain twice.
  - M4A: iTunes Sound Check (`iTunNORM`), and the freeform
    `com.apple.iTunes:replaygain_*` atoms where present.
  - **One consistent reference (owner decision):** every gain tag is applied
    as written against the ReplayGain reference, −18 LUFS. `R128_*` gains
    (reference −23 LUFS) are converted by adding **+5 dB** so Opus lines up
    with the other formats. The user's target LUFS is a single **global
    pre-amp relative to −18 LUFS** (target −14 → +4 dB), applied to every
    file. The target is never used differently per format: a mixed library
    plays at one level.
  - **Untagged files:** unity gain by default, plus a separate **"untagged
    pre-amp"** setting (new preference). A missing album gain in album mode
    falls back to track gain (spec to confirm).
  - **Clip prevention** applies to both paths (tagged and untagged). Where no
    peak tag exists (e.g. R128_* has none, untagged files), the spec defines
    how clipping is prevented.
  - Never silence, error, or a jump in level mid-crossfade.
  The owner's library (rsgain at −14 LUFS, Opus with R128_*) is one test
  sample, not the design target.
  `NormalizationPreferences` (enabled, target LUFS, track/album mode, clip
  prevention) stays the UI contract, extended only by the untagged pre-amp;
  the stored target LUFS is interpreted as the global pre-amp above.
- **Equalizer on Media3.** Use `android.media.audiofx.Equalizer` on the
  player's `audioSessionId` (both A/B players); `EqualizerPreferences` stays
  as-is. Preamp and stereo width need their own solution, to be planned.
- **Lock screen "Unknown artist"**: cause unknown (Phase 0 found that
  `publishNowPlaying` does set `METADATA_KEY_ARTIST` from `item.artist`).
  Investigate it while reading PlaybackService's current metadata path, and
  fix it in the MediaSession task. The notification / lock-screen artwork
  must keep using the shared `decodeArtworkBitmaps` loader (the earlier
  artwork-key fix: album artwork file, then embedded picture), not a separate
  Media3 artwork path.
- **Mood Radio machinery:** `startMoodRadio` and the queue refill logic must
  keep working on Media3 (given analysis data, it would behave as today).

## Out of scope (later, separate features)

- **Volume normalization, stage 2 + Mood Radio revival — the on-device
  analyzer.** Port Airmedy's desktop analyzer (misa198/airmedy,
  `catalog/analysis`: `ffmpeg_analyzer.h` with libavfilter
  ebur128/aspectralstats/astats + aubio tempo, and `formulas.go`). It writes
  `loudness_lufs`, `true_peak`, `energy`, `danceability`, `brightness` and
  `tempo` into `sync_documents`, restoring true LUFS normalization and Mood
  Radio together. It needs libavfilter + aubio, which the Jellyfin decoder
  doesn't provide.
- **Deleting the native player / our FFmpeg build** (owner decision, see
  Principle 8).
- **Deferred formats** (AIFF, APE, WavPack, DSD, WMA on Media3).

## Do not touch

- The analysis read side stays exactly as it is: the `sync_documents` analysis
  document shape, `activeAnalyses()`, `moodRadioEligibleTrackIds`,
  `moodRadioTracks()`, and the gain lookup (`normalizationGain()` /
  `normalizationGainDb()`).
- `sharedLogic/.../player/PlaybackQueue.kt` and
  `sharedLogic/.../player/ListeningTracker.kt` need **zero changes**. The
  Media3 wrapper adapts to their contracts (QueueTransition, peekNext, the
  tracker's start/tick/pause/resume/finish/splitCrossfadeOverlap calls and
  end reasons), not the reverse. A task whose diff touches either file fails
  review.

## Known facts (corrected premise)

The earlier version of this constitution said the scanner already stores
`loudness_lufs` / `true_peak`. **It does not.** Those fields are only *read*,
from `sync_documents` rows of kind `analysis` that the removed desktop sync
used to write. The local MediaStore scan (`writeLocalLibrary`) writes no
analysis documents and deletes stale ones, and the local manifest hard-codes
`library_analysis_enabled = false`. So today:

- Normalization computes gain 0 and the setting is force-disabled
  (`PlaybackService.normalizationGain`, `MainActivity` `analysisAvailable`
  gate). The native engine itself applies gain correctly.
- Mood Radio has no features to select from and its menu entry is hidden.

Neither failure is caused by the playback engine. Characterization tests
describe the native engine as it is: they must not assert that normalization
or Mood Radio work, and their failure there is not a regression. Acceptance
for tag-based normalization is defined against the Media3 engine only; Mood
Radio acceptance belongs to the analyzer feature.
