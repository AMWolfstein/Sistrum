# Sistrum Constitution — Playback Migration

Revised 2026-10-01 with the owner's Phase 0 decisions
(evidence: `specs/001-media3-migration/research/discovery.md`).
Revised 2026-10-05 with the owner's new overall plan: Kotlin decoders replace FFmpeg,
the native player and all native code are removed at the end (features 001–003 below).
Revised 2026-10-08 with the owner's normalization decisions of 2026-10-07: loudness is
measured on the device in 001 (WaxFlow meter port), tags become the fallback.

## Goal

Move Sistrum's playback from the current FFmpeg/native (JNI) player to Android
Media3 + ExoPlayer, with a pluggable decoder architecture, and end with **no
native code at all**. Local/offline playback only. Preserve current app
behavior. This is a staged architectural migration, not a rewrite and not
"add ExoPlayer".

Target shape during the migration:

```
PlaybackController
      ↓
PlaybackEngine (abstraction; name may follow repo conventions —
                note the native struct is already called PlaybackEngine)
      ↓
LegacyNativeEngine (developer switch only, until 003)  |  Media3Engine
                                                            ↓
                                                     Decoder Registry
                                                            ↓
                                     platform codecs (MediaCodec) → Kotlin providers
```

End state (after 003): `PlaybackController → Media3Engine → Decoder Registry →
platform codecs + Kotlin decoders`. No FFmpeg, no native player, no NDK, no
native code. Every decoder and all audio analysis are Kotlin/Java.

## Features

The migration is three Spec Kit features, in order:

- **001-media3-migration**: the Media3 engine, the engine seam and switch, the
  Decoder Registry with two provider kinds (platform codecs and a Kotlin AIFF
  provider ported from Choir), crossfade, normalization (loudness measured on
  the device, gain tags as the fallback), equalizer, MediaSession.
- **002-kotlin-decoders**: Kotlin ports of WaxFlow's decoders (and a Kotlin
  port of Flick's DSD engine) as Decoder Registry providers, each verified
  against WaxFlow as the test oracle. Gated first by a performance gate on
  WavPack.
- **003-native-removal**: remove the old native player and our FFmpeg build
  together, then the NDK. Gated by full regression tests and the owner's
  approval.

**Nothing merges into `main` until 001 and 002 are both done**, so `main` never
loses a format. 003 merges separately, after its own gate.

## Principles

1. **Staged, not destructive.** The engine abstraction is introduced first with
   the existing native player as its first implementation, with zero behavior
   change. The Media3 engine is built behind the same interface. PlaybackController
   moves to Media3 behind the engine switch. There is **no per-track routing
   between engines**: the selected engine plays the whole queue. The native
   player stays selectable only through the developer switch, untouched, until
   003 removes it.

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
   **mechanics** from `RhythmPlayerEngine.kt` / `TransitionController.kt`:
   pre-buffer the next track on the idle player, swap roles, explicit handling
   of repeat-one and skip-previous during a transition, recreate the outgoing
   player afterward. **Keep our own fade curve**, not Rhythm's shaped curve:
   equal-power, as the native engine does today (`ffmpeg_player.cpp`, outgoing
   `cos(t·π/2)`, incoming `sin(t·π/2)`; Airmedy `catalog/player/README.md`).
   The plan-phase spike validates the design against our contract (MediaSession
   with two players, queue sync, listening stats, Last.fm, artwork crossfade
   event) and picks how the fade is applied (timer-stepped player volume vs a
   per-sample fade in each player's processor); it does not redesign the A/B
   approach. Both players share one audio session (see "Equalizer on Media3").
   MediaSession integration follows this design.

6. **Format parity through the Decoder Registry.** Every format the app plays
   today keeps playing on `main`. On Media3, each format maps to an ordered
   list of decoder providers; the first available one decodes it. Order:
   platform codecs first, then Kotlin providers. Switching a format's provider
   is configuration, not a rewrite. A format with no provider is not in the
   library (the scan skips it and reports it); a new provider makes it appear
   after a rescan, with no scanner change. 001 ships the platform and Kotlin AIFF
   providers; 002 adds the rest. Because nothing merges before 002 is done,
   listeners on `main` never lose a format. The deferred formats (APE, WavPack,
   DSD, WMA family) and every other format FFmpeg plays today matter equally,
   whatever any one library contains (Principle 10).

7. **Evidence over assumption.** Supported formats, normalization behavior,
   and crossfade semantics are determined from the actual code and data, not
   assumed. Do not add formats or features beyond what this constitution
   names. External research reports in `specs/*/research/` are leads to
   verify, not facts.

8. **Native code ends with 003.** Until 003, the native player,
   `androidApp/src/main/cpp`, `androidApp/src/main/jniLibs` and
   `scripts/build-ffmpeg-android.sh` stay untouched and selectable through the
   developer switch. 003 removes the native player and our FFmpeg build
   **together**, once Media3 + the Kotlin decoders pass full regression and the
   owner approves; DSD must have its Kotlin provider first. The NDK goes once
   every format in the **full format inventory** (everything FFmpeg plays
   today, written before 003) has a Kotlin decoder, including the whole WMA
   family. Inventory formats with no WaxFlow source (e.g. TTA) are listed for
   an owner decision then.

9. **Honest verification.** Tests, builds, and Graphify results are reported
   exactly as run. Device-only checks belong to the owner. Test device:
   **CPH2307** (a test sample, not the target; see Principle 10).

10. **Built for all users.** Sistrum is built for everyone who installs it,
    not only the owner. The owner's library and the CPH2307 are test
    samples, not the scope. Decisions, specs, acceptance criteria and
    defaults must not assume the owner's formats, tagging tools, library
    composition or device. In particular:
    - The deferred formats (APE, WavPack, DSD, WMA family) and AIFF matter
      equally, regardless of what the owner's library contains.
    - Tag-based normalization must support every common gain-tag form (see
      "In scope"), and behave sensibly for untagged files.
    - Performance claims measured on the owner's Opus-heavy library must be
      checked against other format mixes (e.g. FLAC/ALAC/hi-res PCM, MP3,
      AAC) before they are treated as general.
    - Owner-library facts (inventory, tagging) may be cited as evidence for
      one case, never as the reason to drop or narrow support.

11. **Kotlin end state; WaxFlow is a reference.** Every decoder and all
    analysis end up as Kotlin/Java. WaxFlow (the owner's fork, pinned in
    `docs/waxflow/ORACLE.md`, not vendored) is a **reference and test oracle**:
    source to port from, golden PCM and loudness numbers to verify against
    (`scripts/waxflow-oracle.sh`, small committed fixtures, no audio in git).
    It is not a shipped dependency and no app code links it. A gomobile bind of WaxFlow is
    allowed **only** as the fallback if 002's performance gate fails, and only
    with the owner's approval.

## Engine switch

- A **hidden developer setting** selects the engine. Native is the default on
  `feature/media3-migration` until the regression tests pass on Media3.
- A change takes effect at the **next playback start**, never mid-track.
- The default **flips to Media3 before any merge** into `main`.
- The switch exists until 003 removes the native player.

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

- Add `androidx.media3` (ExoPlayer, session as needed). No FFmpeg inside
  Media3, no Media3 FFmpeg decoder extension.
- Porting from **cromaguy/Rhythm** (primary reference) and
  **PixelPlayerHQ/PixelPlayerOSS**, both GPL-3.0, is allowed with attribution
  (SPDX header in ported files, as in `sync/MediaScanFilter.kt`).
- **AurielSolaris/Choir** (GPL-3.0-or-later): its `AiffExtractor` is ported
  with attribution (001).
- **WaxFlow** (MIT; owner's fork AMWolfstein/WaxFlow at a pinned commit,
  procedures in `docs/waxflow/ORACLE.md`): every ported Kotlin file carries an
  attribution header; WaxFlow (MIT) goes into the third-party notices with
  the first port (the loudness meter, 001), and its FFmpeg-derived WMA tables
  (LGPL-2.1+, GPL-compatible) with the WMA port (002).
- **AndroidX WorkManager** (`androidx.work`, Apache-2.0) for the loudness
  analysis job (001, 2026-10-08).
- **moss-apps/Flick** (MIT): its `dsd_engine` is ported to Kotlin (002).
- Never use: JustDSD (no license), JMAC (license unclear), MediaChest (no
  license).
- Third-party notices must list every added dependency and ported source. No
  notices file exists yet; the task that adds the first dependency or port
  creates it.

## In scope during migration (001)

- **Decoder Registry.** Ordered providers per format/codec (platform codecs,
  then Kotlin providers), the scan filter driven by it, a skipped-files
  summary, and provider-named error logging. 001's providers: platform codecs
  and the Kotlin AIFF provider (port of Choir's `AiffExtractor`). No FFmpeg, no
  NDK in any provider. DSD has no provider until 002 (it still plays on the
  native player via the developer switch).
- **Volume normalization: measured loudness (owner, 2026-10-07).** As in the
  original Airmedy, gain comes from loudness measured on the device, not from
  tags:
  - **Analyzer (loudness only):** a Kotlin port of WaxFlow's `dsp/loudness`
    (BS.1770-4 gated integrated loudness, true peak) with the attribution
    header of `docs/waxflow/ORACLE.md`, validated against EBU test cases and
    the WaxFlow oracle (`scripts/waxflow-oracle.sh`; only the fixtures file is
    committed). It decodes through the Decoder Registry, separately from
    playback, in float (the float path of ADR-004).
  - **Background job:** WorkManager, bounded work per run (under the
    10-minute worker limit), checkpointed per track, only new or changed
    files, preferring charging. Playback is never disturbed.
  - **Storage:** `loudness_lufs` and `true_peak` go into `sync_documents` in
    the existing analysis shape, so the existing gain lookup reads them
    unchanged; extra rows (histograms, album loudness) are new document kinds
    in the same table, with no Room schema change. Results survive rescans
    for unchanged files.
  - **Precedence:** measured loudness; gain tags only for tracks not analyzed
    yet; then unity gain + untagged pre-amp.
  - **Album mode:** album loudness is measured over the album as a whole
    (BS.1770 gating across all its tracks), never averaged, from stored
    per-track data so it never needs re-decoding; recomputed when an album's
    tracks change.
  - **Kept:** the gain processor and its ramps, clip prevention with true
    peak, `NormalizationPreferences` as the UI contract, tag parsing (now the
    fallback). "Prevent clipping" off reaches the engine.
  - **Mood features stay out of 001** (see "Out of scope").
  The rules below for tags apply to tracks not analyzed yet.
- **Volume normalization, tag-based gain (the fallback).** Port Rhythm's
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
    against the ReplayGain reference, −18 LUFS. When a file carries
    `REPLAYGAIN_REFERENCE_LOUDNESS`, the gain is adjusted to −18:
    adjusted gain = tag gain + (−18 − reference). Without that tag, gains are
    taken as written against −18. `R128_*` gains (reference −23 LUFS) are
    converted by adding **+5 dB** so Opus lines up with the other formats. The user's target LUFS is a single **global
    pre-amp relative to −18 LUFS** (target −14 → +4 dB), applied to every
    file. The target is never used differently per format: a mixed library
    plays at one level.
  - **Untagged files:** unity gain by default, plus a separate **"untagged
    pre-amp"** setting (new preference). A missing album gain in album mode
    falls back to track gain.
  - **Clip prevention** applies to all files (tagged and untagged): tagged
    peaks reduce gain, and the session limiter protects the mixed output (see
    "Equalizer on Media3").
  - **Pluggable gain source.** Normalization reads gain through a gain-source
    interface. Precedence (revised 2026-10-08): measured loudness, then tags,
    then unity gain + untagged pre-amp, chosen per track as a whole. ADR-005
    records this.
  - Gain is a per-player processor (before the session effect chain), with
    its ramps inside the processor, not in player volume.
  - Never silence, error, or a jump in level mid-crossfade.
  The owner's library (rsgain at −14 LUFS, Opus with R128_*) is one test
  sample, not the design target.
  `NormalizationPreferences` (enabled, target LUFS, track/album mode, clip
  prevention) stays the UI contract, extended only by the untagged pre-amp;
  the stored target LUFS is interpreted as the global pre-amp above.
- **Equalizer on Media3: in-app per player; limiter on the shared session.**
  EQ, preamp and stereo width are linear, so processing each player and summing
  equals processing the mix. They run as in-app Media3 AudioProcessors in each
  player, using the **same filters as the native engine** (10 peaking biquads,
  Q = 1, at 32 Hz … 16 kHz; preamp gain; mid/side width), so the response
  matches the native engine exactly. `EqualizerPreferences` stays the UI
  contract. Time-varying gains (fade, normalization) are applied before the EQ
  in each player, so linearity keeps the sum identical to EQ on the mix.
  The **limiter** is the one non-linear stage that must see the sum: both A/B
  players share ONE audio session id (kept when the outgoing player is
  recreated), and one `android.media.audiofx.DynamicsProcessing` on that
  session runs **only its limiter stage** over the sum (AOSP evidence:
  `research/dynamics-processing-session.md`; verified on device, see the spec).
  If DynamicsProcessing is unavailable, EQ, preamp and width still work and
  only the limiter is lost. `audiofx.Equalizer` is not used.
- **Lock screen "Unknown artist"**: cause unknown (Phase 0 found that
  `publishNowPlaying` does set `METADATA_KEY_ARTIST` from `item.artist`).
  Investigate it while reading PlaybackService's current metadata path, and
  fix it in the MediaSession task. The notification / lock-screen artwork
  must keep using the shared `decodeArtworkBitmaps` loader (the earlier
  artwork-key fix: album artwork file, then embedded picture), not a separate
  Media3 artwork path.
- **Mood Radio machinery:** `startMoodRadio` and the queue refill logic must
  keep working on Media3 (given analysis data, it would behave as today).
- **Review findings for the playback path** (`docs/review/2026-10-code-review.md`
  Part 2) are requirements for the Media3 engine so it does not repeat them.
  No code fixes from the review happen before all three features are done;
  a full review-and-fix phase precedes v1.0.

## Out of scope for 001 (later features)

- **Kotlin decoders** for APE, WavPack, DSD (DSF/DFF), the WMA family and any
  other format FFmpeg plays today: feature 002.
- **Removing the native player, our FFmpeg build and the NDK**: feature 003.
- **On-device Mood analyzer** (Mood Radio revival), all in Kotlin/Java, no
  bridge, no native: reuses 001's decode path, job and document writer;
  FFT/onsets/tempo from TarsosDSP core or a WaxFlow `dsp/fft` port; feature
  definitions matching Airmedy's `ffmpeg_analyzer.h` and `formulas.go`. It adds
  `energy`, `danceability`, `brightness` and `tempo` to the existing analysis
  documents. Loudness moved into 001 (2026-10-08). Recorded in
  `research/analyzer-future.md`.
- **Media3 MediaSession** (replacing the framework `MediaSession` that 001
  keeps): its own feature after 001 and 002; it does not wait for 003.
- Settings beyond today's (crossfade toggle, crossfade on skip, repeat-one
  crossfade toggle, fractional durations, gapless/skip-silence toggles, Auto
  normalization by queue source): `research/future-settings.md`. The 001
  design must not rule them out.
- Home-screen auto playlists: `research/home-mixes.md`.

## Do not touch

- The analysis read side stays exactly as it is: the `sync_documents` analysis
  document shape, `activeAnalyses()`, `moodRadioEligibleTrackIds`,
  `moodRadioTracks()`, and the gain lookup (`normalizationGain()` /
  `normalizationGainDb()`). 001's loudness analyzer only *writes* that shape
  (2026-10-08); none of these readers change.
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

(2026-10-08) 001's loudness analyzer will write `loudness_lufs` / `true_peak`
locally. Mood features stay absent until the Mood analyzer feature.

Neither failure is caused by the playback engine. Characterization tests
describe the native engine as it is: they must not assert that normalization
or Mood Radio work, and their failure there is not a regression. Acceptance
for normalization is defined against the Media3 engine only; Mood
Radio acceptance belongs to the analyzer feature.

Known bug (code review 2026-10, Part 3 blocker): `PlaybackQueue.play()` keeps
only the first 1000 distinct ids and clamps the start index, so in larger
libraries the wrong track can play. It is recorded as a known bug for the
pre-v1.0 review phase. Characterization tests must not assert it as preserved
behavior.
