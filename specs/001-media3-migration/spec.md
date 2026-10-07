# Feature Specification: Media3 playback engine and Decoder Registry

**Feature Branch**: `feature/media3-migration` (spec directory `specs/001-media3-migration`)

**Created**: 2026-10-01 · **Revised**: 2026-10-05 (owner's new overall plan, see Clarifications); 2026-10-08
(measured-loudness normalization, owner decisions 2026-10-07)

**Status**: Draft

**Input**: User description: "Migration of Sistrum's playback from the native FFmpeg/JNI engine to Android
Media3/ExoPlayer behind a PlaybackEngine abstraction and a hidden developer engine switch, preserving every
current behavior, with a pluggable Decoder Registry (platform codecs first, then Kotlin providers; 001 ships
the platform and Kotlin AIFF providers). In scope: engine abstraction with LegacyNativeEngine (zero behavior
change), Media3Engine, Rhythm dual-ExoPlayer A/B crossfade with our equal-power curve, tag-based
normalization (ID3 TXXX, REPLAYGAIN_*, R128_*, iTunes Sound Check; −18 LUFS reference; R128 +5 dB; target as
global pre-amp; untagged pre-amp; clip prevention) through a pluggable gain source, equalizer/preamp/stereo
width as in-app per-player processors with the native engine's filters plus a limiter-only DynamicsProcessing
over a shared audio session, MediaSession incl. the
lock-screen Unknown artist fix keeping decodeArtworkBitmaps, scan filtered by the registry with a
skipped-files summary, test builds with a separate applicationId suffix and a test corpus. Out of scope: Kotlin
decoders for the other formats (002), removing the native player/FFmpeg/NDK (003), the on-device analyzer.
PlaybackQueue.kt and ListeningTracker.kt must need zero changes."
Revised 2026-10-08 (owner): normalization measures loudness on the device (Kotlin port of WaxFlow's BS.1770
meter, background job, decode through the Decoder Registry); tags become the fallback. Only the Mood part of the
on-device analyzer stays out of scope.

**Governing documents**: `.specify/memory/constitution.md` (Principles 1–11 and all sections),
`research/discovery.md` (behaviours to preserve §2 Q8, format inventory §3, risks §6, boundaries §7),
`research/dynamics-processing-session.md` (shared-session effect chain), and
`docs/review/2026-10-code-review.md` Part 2 (playback-path findings, turned into FR-080…FR-092).
Where this spec and the constitution disagree, the constitution wins and this spec is corrected.

Terms used below:
- **Current engine**: the existing native player (developer switch only; removed by feature 003).
- **New engine**: the Media3 playback engine added by this feature.
- **Engine switch**: the hidden developer setting that selects the engine.
- **Decoder Registry**: the configuration that maps each format/codec to an ordered list of decoder providers.
- **Provider**: one way to decode a format on the new engine (a platform codec, or a Kotlin decoder).
- **Unsupported format**: a format with no available provider in the registry.
- **Listener**: anyone who installs Sistrum, with any library, tagging tool and device (Principle 10).

Removed on 2026-10-05 (per-track routing to the native engine no longer exists): FR-046b, FR-048, FR-049,
FR-061, FR-062a, SC-012, User Story 6 scenario 9, the native-routed parts of User Story 8, and the related
known limitations and edge cases.

## Clarifications

### Session 2026-10-01

- Q: During a crossfade, what should Next and Previous do? → A: Next ends the fade and jumps straight to
  the track after the incoming one (hard cut). Previous applies the normal rule to the incoming track:
  within its first 3 s it goes back to the outgoing track, restarted from the beginning (hard cut);
  after 3 s it restarts the incoming track. A future "crossfade on skip" setting may turn Next during a
  fade into a crossfade; the design must not rule that out.
- Q: In album mode, when should the album gain be used instead of the track gain? → A: Always, when the
  file has an album gain; otherwise the track gain (standard ReplayGain), regardless of queue order or
  the next track. Future note only (not in this migration): an automatic mode may come later; if built,
  it is based on the queue's source (started from an album page = album gain; playlist, mixed queue or
  shuffle = track gain), not on adjacent tracks, which would repeat the last-track problem of today's rule.
- Q: While the current (native) engine is selected, should the volume normalization settings be usable?
  → A: No: they are enabled only while the new engine is selected; on the current engine they are
  disabled with a short note that they require the new playback engine.
- Q: How much extra battery and memory may the new engine use compared with the current engine? → A:
  With crossfade enabled, at most 10 % more app CPU time, wakelock time and memory over one hour of
  screen-off playback, on at least two library mixes. With crossfade disabled, the second player must not
  exist or pre-buffer, and the limit is 0 % (no worse than the current engine). Power is measured with
  `dumpsys batterystats` (app CPU time and wakelocks), not raw battery percentage.
- Q: When a normalization setting changes during playback, when should the change be heard? → A:
  Immediately, with a short smooth ramp (≈100–300 ms) on the playing track and the already-prepared next
  track; during a crossfade both change together. While a slider is dragged, each change ramps from the
  current actual gain to the new value (no restart from the old setting, no stepping). The end-of-chain
  limiter stays active throughout.

### Session 2026-10-05 (owner's new overall plan)

- No Media3 FFmpeg decoder and no FFmpeg inside Media3. Formats go through a Decoder Registry: platform
  codecs first, then Kotlin providers. 001 ships the platform and Kotlin AIFF (Choir port) providers;
  feature 002 adds Kotlin ports of WaxFlow's decoders and Flick's DSD engine; feature 003 removes the
  native player, our FFmpeg build and the NDK. Nothing merges into `main` until 001 and 002 are done.
- No per-track routing to the native engine. The native player stays selectable only through the
  developer switch, untouched, until 003. A decode failure on the new engine shows the normal error and
  skips; there is no retry on the native engine.
- Unsupported formats are excluded from the library with a skipped-files summary. DSD has no provider in
  001 or until its Kotlin port lands in 002.
- Normalization reads gain through a pluggable gain source; tags are the only source now.
- Two players mean the mix happens in Android: both players share one audio session; a limiter-only
  DynamicsProcessing on that session processes the sum; normalization, stereo width, EQ and preamp are
  per-player processors; fade curve stays equal-power.
- The code review's Part 2 findings become requirements here (FR-080…FR-092); the PlaybackQueue
  1000-track truncation is a known bug, not preserved behaviour. No code fixes from the review before
  the pre-v1.0 review phase.
- Q: Should the scan filter depend on the selected engine? → A: Yes. With the current engine the scan admits
  files as today; with the new engine only formats with a provider; changing the switch rescans.
- Q: EQ on DynamicsProcessing bands can't match the native peaking filters; accept a tolerance? → A: No. EQ
  and preamp are linear, so per-player processing equals processing the mix: EQ, preamp and width run as
  in-app per-player processors with the same filters as the native engine (peaking, Q = 1), matching it
  exactly. DynamicsProcessing on the shared session is used only for the limiter; if it is unavailable,
  EQ still works and only the limiter is lost.
- Q: Relax SC-011 from bit-identical to a tolerance? → A: Yes (the limiter has no lookahead and the effect
  always processes).
- Q: Adopt MPD's "no crossfade for tracks under 20 s"? → A: No. Keep today's rule (fade length = min(N s,
  half the track)); a minimum-length setting is recorded as a possible future setting.
- Q: Which formats must 002 cover? → A: Also Musepack, ADPCM and G.711 from WaxFlow. A full inventory of
  everything FFmpeg plays today gates 003; formats with no WaxFlow source (e.g. TTA) are listed for a
  decision then.
- Q: How is the fade applied on the new engine? → A: A per-sample fade in each player's gain processor, before
  the EQ (not timer-stepped player volume).
- Q: Which MediaSession does 001 use? → A: The framework MediaSession, fed from the service. A Media3 session is
  its own feature after 001 and 002 (it does not wait for 003).
- Q: Do the service-path robustness requirements (FR-080…FR-092) also apply where the shared service drives
  the current engine? → A: Yes, including the current engine's failure cases. Characterization tests must not
  assert the old faulty behaviour as preserved.
- Q: Honour `REPLAYGAIN_REFERENCE_LOUDNESS`? → A: Yes, when present: adjusted gain = tag gain + (−18 −
  reference). Without it, gains are taken against −18, and R128 against −23 with +5 dB.

### Session 2026-10-07 (owner, after the M5 hi-res report; written 2026-10-08)

- Q: Where does normalization get loudness? → A: Measured on the device, as the original Airmedy did (measured,
  not taken from tags): a Kotlin port of WaxFlow's `dsp/loudness` (BS.1770-4 integrated loudness, true peak),
  validated with EBU test cases and the WaxFlow oracle. Mood features stay out of 001.
- Q: How does analysis run? → A: A WorkManager job: bounded work per run under the 10-minute limit, checkpointed,
  only new or changed files, preferring charging/idle, decoding through the Decoder Registry in float, separate
  from playback. Results go into `sync_documents` in the shape the existing gain lookup reads.
- Q: Precedence? → A: Measured loudness, then gain tags (only for tracks not analyzed yet), then unity gain +
  untagged pre-amp.
- Q: Album mode? → A: Album loudness gated across the whole album, not averaged, from stored per-track data so it
  needs no re-decoding; recomputed when an album's tracks change.
- Q: What is kept? → A: The gain processor, ramps, true-peak clip prevention, `NormalizationPreferences` and tag
  parsing. "Prevent clipping" off must reach the engine.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Nothing changes while the current engine is selected (Priority: P1)

A listener updates Sistrum. The engine switch defaults to the current engine. Everything plays, sounds
and behaves exactly as before: library contents, queue, gapless, crossfade, equalizer, focus handling, lock
screen, statistics, Last.fm, lyrics sync, session restore.

**Why this priority**: The migration is staged. Introducing the engine seam must not regress anyone
before the new engine exists. This is the foundation every other story builds on.

**Independent Test**: With the switch on the current engine, run the characterization tests and the
existing test suites unchanged, plus a manual listening pass on the test device, and compare with the
pre-migration build.

**Acceptance Scenarios**:

1. **Given** the switch is on the current engine, **When** a listener plays an album with crossfade off,
   **Then** tracks join gaplessly and the state, queue, statistics and Last.fm reports match the
   pre-migration build.
2. **Given** crossfade is set to 6 s, **When** a track reaches its last 6 s with the next track loaded,
   **Then** the fade starts under the same rule and curve as before, and the queue, now-playing state and
   artwork transition switch to the incoming track at the start of the fade.
3. **Given** any existing automated test, **When** the suite runs after the seam is introduced, **Then**
   it passes without being modified.
4. **Given** the current engine is selected, **When** the library is scanned, **Then** it admits the same
   files as the pre-migration build (no registry filtering, no skipped-files summary entries).

---

### User Story 2 — Core playback on the new engine (Priority: P1)

A developer (and later every listener) selects the new engine. Play, pause, resume, stop, seek, next,
previous, shuffle, repeat off/one/all, play next, append, remove, reorder, select a queue item, clear the
queue and session restore all behave as they do on the current engine. Automatic advance is gapless.
Statistics, Last.fm and synced lyrics keep working.

**Why this priority**: This is the core value of the migration and the gate for flipping the default.

**Independent Test**: Select the new engine, restart playback, and run the playback-contract test suite
plus a manual pass over the test corpus. Crossfade, equalizer and normalization may still be inactive on
the new engine at this stage (constitution, sequencing exception).

**Acceptance Scenarios**:

1. **Given** the new engine is selected, **When** the listener taps a track in an album, **Then**
   playback starts, the mini player and full player show the track, and the queue shows the album.
2. **Given** playback is more than 3 s into a track, **When** the listener presses previous, **Then** the
   track restarts. Within the first 3 s, the previous track plays.
3. **Given** repeat is off and the last track ends, **Then** playback stops and keeps the last track
   selected, shown as finished. Pressing play then restarts the queue from its first track.
4. **Given** the app was closed during playback, **When** it is reopened, **Then** the previous queue and
   track are restored **paused** at the saved position, and audio never starts by itself.
5. **Given** synced lyrics are shown, **When** the track plays, **Then** the highlighted line follows the
   audio as closely as on the current engine.
6. **Given** a track is listened to past the completion threshold, **Then** listening statistics and the
   Last.fm scrobble are recorded exactly once, as on the current engine.
7. **Given** Mood Radio analysis data exists for the library (supplied by tests; no on-device analyzer
   exists yet), **When** Mood Radio is started, **Then** it builds and refills the queue as on the
   current engine.
8. **Given** a library of 20 000+ tracks, **When** the listener starts playback from the full track list,
   **Then** the service receives the request without a Binder/Intent size failure (FR-083). (Which track
   plays beyond position 1000 is the known PlaybackQueue bug, not asserted here.)

---

### User Story 3 — Engine switch for development and rollout (Priority: P1)

A developer opens a hidden developer setting and switches between the current and new engine to compare
them on the same library and device.

**Why this priority**: Both engines must stay selectable until feature 003 (Principles 1 and 8). The
switch is also how the default later flips.

**Independent Test**: Change the switch while a track plays; confirm the running track is unaffected and
the next playback start uses the selected engine.

**Acceptance Scenarios**:

1. **Given** a track is playing on the current engine, **When** the switch is changed to the new engine,
   **Then** the running track keeps playing unchanged, and the next playback start uses the new engine.
2. **Given** a fresh install on this branch, **Then** the current engine is selected.
3. **Given** the setting is hidden, **Then** a listener who doesn't know the unlock gesture never sees it.
4. **Given** the switch is changed, **Then** the library is rescanned for the selected engine's formats
   (FR-065), without interrupting the running track.

---

### User Story 4 — Lock screen, notification and system controls (Priority: P2)

A listener controls playback from the lock screen, the notification, a headset, Bluetooth controls and
the system output switcher. The lock screen shows the right title, artist and artwork, including for
tracks that currently show "Unknown artist".

**Why this priority**: Everyday control surface; also carries the known "Unknown artist" bug.

**Independent Test**: On the new engine, play tracks whose lock screen currently shows "Unknown artist"
and check the lock screen, notification and a Bluetooth/headset control against the checklist.

**Acceptance Scenarios**:

1. **Given** a track whose in-app artist is known, **When** it plays, **Then** the lock screen and
   notification show the same artist text as the app, never "Unknown artist".
2. **Given** a track with album artwork or only embedded artwork, **Then** the lock screen and
   notification show the same artwork the app shows.
3. **Given** another app takes permanent audio focus, **Then** playback pauses. A transient loss pauses and
   resumes afterwards. A "may duck" loss lowers the volume smoothly and restores it afterwards.
4. **Given** headphones are unplugged, **Then** playback pauses. **Given** the output route changes
   manually, **Then** playback continues on the new route at the same position.
5. **Given** a crossfade is running, **When** the lock screen or a Bluetooth control is used, **Then** it
   shows and controls the incoming track (the system sees one player).
6. **Given** a command (pause, seek, repeat, stop) reaches a playback service that is not running and has
   an empty queue, **Then** nothing crashes and no foreground-service timeout occurs (FR-080).

---

### User Story 5 — Crossfade on the new engine (Priority: P2)

A listener with crossfade enabled hears consecutive tracks overlap with a smooth fade on the new engine,
including when repeat-one is on or when they press previous or skip during a fade.

**Why this priority**: Crossfade is on the no-feature-loss list and is the highest-risk behaviour
(Principle 5).

**Independent Test**: Crossfade test cases on the corpus (short tracks, repeat-one, skip and previous
during a fade, pause and seek during a fade), comparing observable state with the current engine.

**Acceptance Scenarios**:

1. **Given** crossfade N s (1–12), **When** a track nears its end with the next track ready, **Then** the
   fade starts when remaining time ≤ min(N s, half the track) and > 0.4 s, and lasts that long.
2. **Given** a fade is running, **When** the listener pauses, seeks, stops or edits the upcoming queue,
   **Then** the fade ends at once and the incoming track continues at full level (or stops on stop).
3. **Given** a fade is running, **When** the listener presses next, **Then** the fade ends and the track
   after the incoming one starts at once (hard cut); no double playback, no stuck volume, and the queue
   shows that track.
3a. **Given** a fade is running and the incoming track is within its first 3 s, **When** the listener
   presses previous, **Then** the fade ends and the outgoing track restarts from its beginning (hard
   cut). If the incoming track is past 3 s, previous restarts the incoming track.
4. **Given** repeat-one is on, **Then** the track fades (or joins gaplessly) into itself.
5. **Given** a manual track change (tap, next, previous), **Then** there is no crossfade: the change is
   immediate, as today.
6. **Given** a fade completes, **Then** listening statistics split the overlap between both tracks as
   they do today, and the artwork transition runs for the fade's duration.
7. **Given** a 12 s fade, **Then** the levels follow the equal-power curve (outgoing cos, incoming sin)
   without audible stepping.
8. **Given** two loud tracks overlap during a fade, **Then** the summed output is limited (no clipping).

---

### User Story 6 — Consistent loudness across a mixed library (Priority: P2)

A listener with normalization enabled plays a library that mixes MP3, FLAC, Ogg, Opus, M4A and other files,
tagged by different tools or not at all. The app measures each track's loudness on the device in the background
and plays every track at one consistent level, without clipping. Until a track is measured, its gain tags are
used, and untagged tracks play at unity plus the untagged pre-amp.

**Why this priority**: Normalization is broken today for every listener, and tags alone would leave most libraries
(untagged, or tagged by different tools against different references) inconsistent. Measuring restores the
original Airmedy behaviour for everyone.

**Independent Test**: Test-corpus tracks of every format, with and without gain tags; after analysis, measure the
output level per track against the target; before analysis, against the tag-based expectation. Meter accuracy
against EBU cases and the WaxFlow oracle (SC-017); analysis cost on the test device (SC-018).

**Acceptance Scenarios**:

1. **Given** analyzed tracks of any format, tagged or not, **When** normalization is on, **Then** each plays at
   the target loudness (gain = target − measured loudness), whatever its tags say.
2. **Given** the target is −14 LUFS, **Then** the target acts as one global pre-amp relative to −18 LUFS (+4 dB)
   on every source: measured tracks land on −14 LUFS; tag gains get +4 dB. The target never differs per format.
3. **Given** an Opus file with R128 gain tags and a non-zero header output gain, **Then** the header gain is
   applied exactly once (by the decoder, so the measured loudness already includes it) and, while the track is not
   yet analyzed, the R128 tag gain is converted by +5 dB.
4. **Given** a track not yet analyzed, **Then** its gain tags apply (ReplayGain, R128, Sound Check, with the tag
   precedence); an untagged one plays at unity gain plus the "untagged pre-amp" setting.
5. **Given** album mode and an album whose tracks are all analyzed, **Then** every track of it plays at the album
   gain from the album's loudness gated across all its tracks, whatever the queue order or the next track
   (including the last track of an album and album tracks inside a shuffled or mixed queue). A track whose album
   is not fully analyzed uses its own measured loudness; a track not analyzed uses its album tag gain, else its
   track tag gain.
6. **Given** a gain that would push peaks above full scale and clip prevention on, **Then** the gain is reduced so
   the true peak (the tagged peak for tag gains) stays at or below full scale, for every source; in album mode the
   album peak is used.
7. **Given** normalization is off, **Then** no gain is applied (the equalizer and pre-amps still apply as
   configured).
8. **Given** the current engine is selected, **When** the listener opens the normalization settings, **Then**
   they are disabled and a short note says they require the new playback engine.
9. *(Removed 2026-10-05: native-routed tracks.)*
10. **Given** a track is playing, **When** the listener drags the target or untagged pre-amp slider, **Then** the
    level follows smoothly without clicks, steps or jumps back to the old value.
11. **Given** new or changed files after a scan, **When** the phone is charging, **Then** they are analyzed in the
    background without interrupting or glitching playback, already analyzed unchanged files are not analyzed again,
    and the settings show how many tracks are analyzed.
12. **Given** a track's analysis finishes while it is playing, **Then** its gain does not change until its next
    start.
13. **Given** clip prevention is turned off, **Then** no peak cap is applied and the session limiter stage goes
    neutral, without a click or a level jump.
14. **Given** a rescan, **Then** measurements of unchanged files are kept and changed files are measured again;
    an album whose tracks changed gets its album loudness recomputed without decoding the unchanged tracks.

---

### User Story 7 — Equalizer on the new engine (Priority: P3)

A listener's existing equalizer settings (10 bands, preamp, stereo width) apply on the new engine to both
crossfading tracks, and Sistrum behaves well next to system-wide EQ apps.

**Why this priority**: On the no-feature-loss list; it can be restored after core playback.

**Independent Test**: Apply extreme EQ, preamp and width settings; compare with the current engine by
listening and by measuring test tones at the band centres.

**Acceptance Scenarios**:

1. **Given** saved equalizer settings, **When** the new engine plays, **Then** they apply without the
   listener re-entering them.
2. **Given** a crossfade, **Then** both overlapping tracks are equalized the same way, and the sum equals
   equalizing the mix (as on the current engine).
3. **Given** preamp and stereo width are changed during playback, **Then** the change is heard within
   a moment and without clicks.
4. **Given** the same band, preamp and width settings, **Then** the new engine's response matches the
   current engine's exactly: same filter design and formula and, at the same sample rate, the same
   coefficients up to float rounding (≤ 1e-6 relative; bit identity is not testable across C and JVM math
   libraries); a test-tone sweep agrees within 0.1 dB from 20 Hz to 20 kHz. "Exactly" means the steady-state
   response: the current engine resets filter state on every EQ change (clicks), the new engine must not (FR-056).
   **Intentional deviation from the current engine (owner, 2026-10-05): no click on EQ change.**
5. **Given** a device where the session limiter can't be created, **Then** playback, EQ, preamp and width
   work as usual; only the limiter is missing, and the clip-prevention setting shows a clear note.
6. **Given** an EQ app (e.g. Wavelet, Poweramp EQ) is installed, **Then** it is told about Sistrum's audio
   session when playback starts and ends; **When** it takes control of the session effect, **Then**
   Sistrum shows a visible note that its limiter is not active, and restores it when control returns.
   Sistrum's in-app EQ keeps working.

---

### User Story 8 — Formats through the Decoder Registry (Priority: P3)

A listener's library contains any mix of formats. On the new engine, every format with a provider plays;
files in formats without a provider are left out of the library, and the listener can see what was skipped
and why. When a provider is added later (feature 002), those files appear after a rescan.

**Why this priority**: Format parity on `main` is a gate (Principle 6), reached by 001 + 002 together;
001 builds the mechanism and ships the platform and AIFF providers.

**Independent Test**: With the new engine selected, scan the test corpus; play every admitted file;
compare the skipped-files summary with the corpus formats that have no provider.

**Acceptance Scenarios**:

1. **Given** the new engine is selected, **When** an AIFF/AIFF-C (PCM) track plays, **Then** it plays through
   the Kotlin AIFF provider, with crossfade, EQ and normalization like any other track.
2. **Given** a corpus with formats that have no provider (e.g. DSD, APE, WavPack, WMA in 001), **When** a
   scan finishes, **Then** those files are hidden from the library (kept with their history, FR-065a), their tags are not read, and a short summary
   says how many were skipped and why (e.g. "12 files skipped: unsupported format (DSD)"), after the scan
   and in settings.
3. **Given** a provider is registered for a previously skipped format, **When** the library is rescanned,
   **Then** those files appear, with no scanner change.
4. **Given** a file an admitted provider fails to decode at playback time, **Then** the normal playback
   error is shown, the failure is logged with the provider name, and playback skips to the next track.
5. **Given** a format with two available providers, **Then** the first in the configured order decodes it;
   changing the order is a configuration change only.

---

### User Story 9 — Safe test builds and a shared test corpus (Priority: P3)

A developer installs test builds on a device next to the owner's daily app, and uses a generated test
corpus covering every format and tag form, without touching the daily app's data.

**Why this priority**: Enables all device verification above without risk to real user data.

**Independent Test**: Install a test build on a device with the daily app installed; confirm both launch
and the daily app's library, statistics and settings are untouched.

**Acceptance Scenarios**:

1. **Given** the daily app is installed, **When** a test build is installed, **Then** both are present,
   and the daily app's data is unchanged. No uninstall happens.
2. **Given** the corpus folder, **Then** it contains every playable format and tag form in scope,
   including untagged files, and never a file that pretends to be a format it isn't.

---

### Edge Cases

- Engine switch changed while paused, while a fade runs, or while a session is being restored: only
  the next playback start changes engine; the library rescan does not interrupt playback.
- Session saved on one engine and restored on the other: the queue, track and position restore the same;
  tracks the selected engine can't play are dropped as missing tracks are today.
- Very short tracks (< 2 s): no crossfade, gapless advance only.
- Track removed from the library or file deleted mid-queue: skipped/dropped as today; no crash.
- Output route disappears during a fade.
- Audio focus lost during a fade.
- Gain tags that are malformed, out of range (e.g. +50 dB), non-finite, have lower-case keys or units
  missing: ignored safely (treated as untagged or clamped), never distorted output or infinite gain.
- A file carrying several tag forms at once (e.g. ReplayGain and Sound Check): one defined precedence.
- Opus without R128 tags but with a header gain.
- Loudness analysis (2026-10-08): a silent or sub-400 ms file (no loudness value → tags → unity); a file
  changed or deleted while it is being analyzed (result discarded by fingerprint); a decode failure (recorded,
  not retried until the file changes); a multi-hour file interrupted mid-way (resumed from its checkpoint); an
  album with some tracks not yet analyzed or hidden (track values until complete; hidden tracks are not members);
  untagged files under MediaStore's folder album (never an album); a rescan during a run; analysis while music
  plays (no glitch); the phone unplugged mid-run (run stops between chunks, resumes later).
- Mood Radio refill while a fade runs.
- Test corpus folder present on a listener device without blocklisting: it simply appears as music.
- A hi-res or multichannel file that the system routes to a direct output, where the session effect does not
  run: playback, EQ, preamp and width still work (they are in-app); only the limiter is missing there
  (checked with SC-015).
- An EQ app attaches to the session mid-track, or takes and releases control of the limiter effect.
- A file whose container is supported but whose codec inside has no provider (e.g. WAV with MS-ADPCM):
  skipped at scan if the scan can tell, otherwise the decode-failure path (FR-064).
- A container whose codec has a platform decoder on some devices but not others (e.g. ALAC in M4A: none on the
  CPH2307, see `research/platform-codecs-cph2307.md`): the registry probes the device's codecs at runtime and
  tells codecs apart inside a container (ALAC vs AAC in M4A), never assuming one device's codec list.
- A seek right after the fade starts, or two automatic transitions within one position tick.

## Requirements *(mandatory)*

### Functional Requirements

**Engine seam and switch**

- **FR-001**: The system MUST route all playback through one engine abstraction, with the current engine
  as its first implementation and zero observable behaviour change.
- **FR-002**: The system MUST offer a hidden developer setting that selects the engine, defaulting to the
  current engine on the migration branch; a change MUST take effect at the next playback start, never
  mid-track.
- **FR-003**: Before any merge into `main`, the default MUST be the new engine, every requirement in
  this spec MUST pass on it, and feature 002 MUST be done (no format lost on `main`).
- **FR-004**: The playback controller's public operations and observable state streams MUST stay as
  listed in constitution Principle 2; any change needs an ADR and tests.
- **FR-005**: The shared queue and listening-tracker components MUST need zero changes; the engines adapt
  to them.
- **FR-006**: The current engine, its native code and the FFmpeg build MUST stay untouched and selectable
  through the engine switch until feature 003. The selected engine plays the whole queue; there is no
  per-track routing between engines.

**Playback behaviour (both engines)**

- **FR-010**: Play, pause, resume, stop, seek, next, previous (> 3 s restarts), shuffle, repeat
  off/one/all, play next, append, remove, reorder, select, clear queue MUST behave as documented in
  discovery §2 Q3/Q8.
- **FR-011**: Automatic advance without crossfade MUST be gapless between tracks.
- **FR-012**: The playback state MUST expose idle / preparing / playing / paused / failed with position
  and duration; position MUST refresh at least every 200 ms while playing (lyrics sync, statistics).
- **FR-013**: Session restore MUST restore queue, track and position paused, never auto-play, drop tracks
  no longer in the library, and yield to a new play request.
- **FR-014**: Listening statistics MUST record start, progress, pause, resume, finish reasons
  (completed/skipped/stopped), survive output-route recovery without splitting, and split crossfade
  overlap, as today.
- **FR-015**: Last.fm MUST receive now-playing on every track start (including automatic transitions and
  restore), seeks and progress, and scrobble as today.
- **FR-016**: Mood Radio start and refill MUST work on the new engine, given analysis data. The analysis
  read side (document shape, analysis lookup, eligibility, gain lookup) MUST NOT change.
- **FR-017**: Playback MUST use local files only (the absolute paths the library scan stores).

**System integration**

- **FR-020**: The lock screen, notification and system media controls MUST show title, artist and
  artwork matching the app, and offer play/pause/next/previous/seek/stop and the queue.
- **FR-021**: The "Unknown artist" lock-screen bug MUST be investigated through the current metadata
  path and fixed; artwork MUST keep coming from the shared artwork loader (album artwork file, then
  embedded picture).
- **FR-022**: Audio focus MUST behave as today: permanent loss pauses; transient loss pauses and resumes;
  duckable loss lowers to 20 % with a smooth ramp and restores; unplugging pauses; a manual route change
  continues at the same position.
- **FR-023**: The system output switcher MUST keep working (it is hidden below Android 14 on purpose).
- **FR-024**: The media session MUST see exactly one player at all times. During a crossfade it switches
  from the outgoing to the incoming player at fade start (the incoming track's metadata owns the system UI
  from fade start, as in the app).

**Crossfade**

- **FR-030**: Crossfade on the new engine MUST follow the decided dual-player technique (constitution
  Principle 5) and keep the current observable contract: start rule, duration clamp, automatic advance
  only, fade ends at once on pause/seek/stop/queue edit, queue and state switch at fade start, artwork
  transition event, overlap split in statistics, next-but-one track prepared only after the fade.
- **FR-031**: Repeat-one and previous/next during a fade MUST have defined, tested outcomes (User Story 5,
  scenarios 3, 3a, 4). Next during a fade is a hard cut to the track after the incoming one; previous
  applies the normal 3 s rule to the incoming track. The design MUST NOT rule out the future settings in
  `research/future-settings.md`: crossfade on manual skip (incl. next during a fade), a repeat-one
  crossfade toggle, and fractional durations (0.5 s steps).
- **FR-032**: A crossfade setting change MUST never alter a running fade.
- **FR-033**: With crossfade disabled, the new engine MUST NOT create or pre-buffer a second player;
  gapless advance uses a single player. With crossfade enabled, the second player MUST be created and
  prepared only shortly before a transition (fade length plus a few seconds of pre-buffer) and released
  right after the fade, so most of the time only one player exists.
- **FR-034**: The fade curve MUST be equal-power, as on the current engine: outgoing gain cos(t·π/2),
  incoming sin(t·π/2), t from 0 to 1 over the fade. It MUST have no audible stepping on a 12 s fade.
- **FR-035**: Per player, the gains MUST multiply, never overwrite each other: user volume × focus duck ×
  fade curve × normalization gain.
- **FR-036**: Both players MUST use identical audio attributes and share one audio session id, which MUST
  be kept when the outgoing player is recreated. Audio offload MUST stay disabled on the new engine. Because the
  player applies a session id asynchronously and may overwrite it with its own (S2, ADR-004), every player MUST be
  checked after it is ready and before it plays: if its session is not the shared one, the id is re-applied and
  re-checked; a player that does not reach the shared session MUST NOT start (otherwise the limiter would silently
  miss it), and the failure is logged and handled like a prepare failure.

**Normalization (measured loudness first, tags as the fallback; revised 2026-10-08)**

FR-040…FR-041 and FR-047 govern tracks that are not analyzed yet (tag gains); FR-047b…FR-047h govern measurement.

- **FR-040**: Gain tags MUST be read in all common forms: ID3v2 TXXX ReplayGain (MP3), REPLAYGAIN_*
  comments (FLAC, Ogg Vorbis), R128_TRACK_GAIN / R128_ALBUM_GAIN (Opus), iTunes Sound Check and
  ReplayGain freeform atoms (M4A). Tag keys are case-insensitive.
- **FR-041**: All tag gains MUST be applied against the −18 LUFS reference. When
  `REPLAYGAIN_REFERENCE_LOUDNESS` is present, the gain MUST be adjusted: tag gain + (−18 − reference);
  without it, gains are taken as written against −18. R128 gains (relative to −23 LUFS) MUST be converted by
  +5 dB. Opus header output gain MUST be applied exactly once.
- **FR-042**: The user's target LUFS MUST act as one global pre-amp relative to −18 LUFS (target −14 →
  +4 dB) applied to every file and every gain source. A measured track's gain before the pre-amp is −18 − L
  (L = its measured integrated loudness), so it plays at target − L.
- **FR-043**: Files with neither a measurement nor gain tags MUST play at unity gain plus a separate
  "untagged pre-amp" setting (default 0 dB). Album mode MUST always use the album value when present and
  otherwise the track value, independent of queue order or the next track (unlike today's "next track on the
  same album" rule and averaged album loudness, which stay unchanged on the current engine). For a measured
  track the album value is the album loudness of FR-047f; for a tag-only track it is the album tag gain.
- **FR-044**: Clip prevention MUST apply to all files: the gain is reduced so that the measured true peak (or,
  for tag gains, the tagged peak) stays at or below full scale (album peak in album mode); in addition, a
  limiter on the shared audio session processes the summed output of both players, after
  normalization, fade, stereo width, equalizer and preamp (each of which can clip). Below its threshold
  the limiter MUST apply no gain reduction (SC-011). If the limiter is unavailable (FR-053), peak reduction
  still applies. With clip prevention off, no peak cap is applied and the limiter stage is neutral (FR-053); the
  setting MUST reach the engine whenever it changes and when an engine is created.
- **FR-045**: The existing normalization settings (enabled, target, track/album mode, clip prevention)
  MUST remain the user-facing settings, extended only by the untagged pre-amp. They MUST be enabled only
  while the new engine is selected (no longer force-disabled for lack of analysis data, and never turned off
  automatically by the current engine's analysis lookup while the new engine is selected); while the current
  engine is selected they MUST be disabled with a short note that they require the new playback engine. The
  settings MUST show the analysis progress (analyzed tracks of the total).
- **FR-046**: Gain changes MUST never cause an audible jump inside a crossfade.
- **FR-046a**: A normalization setting change during playback (on/off, target, untagged pre-amp,
  Track/Album mode) MUST be heard immediately through a smooth ramp of about 100–300 ms, applied to the
  playing track and the already-prepared next track, and to both tracks together during a crossfade.
  Each new value MUST ramp from the gain actually applied at that moment (never restarting from the old
  setting, never stepping), so continuous slider dragging stays smooth. Ramps live in each player's gain
  processor, not in player volume. The limiter stays active throughout.
- **FR-047**: When several tag forms are present, one documented precedence MUST apply
  (ReplayGain → R128 → Sound Check assumed; see Assumptions).
- **FR-047a**: Normalization MUST read gain through a gain-source abstraction with one precedence, chosen per
  track as a whole: measured loudness → gain tags → unity gain + untagged pre-amp. A measured track never mixes in
  tag values. Non-finite or silent-file values MUST never produce infinite or NaN gain. A track's gain source
  MUST NOT change while it plays (a measurement finished mid-track applies from its next start).
- **FR-047b**: Loudness MUST be measured on the device per ITU-R BS.1770-4: gated integrated loudness (400 ms
  blocks, 100 ms hop, absolute gate −70 LUFS, relative gate −10 LU) and true peak (Annex 2 oversampling). The meter
  MUST be a Kotlin port of WaxFlow's `dsp/loudness` at the pinned commit, with the attribution header, and MUST
  match the WaxFlow oracle and EBU test cases (SC-017). Mono MUST NOT read 3 dB hot. Tracks shorter than one block
  or with no block above the absolute gate are recorded as silent, with no loudness value.
- **FR-047c**: Analysis MUST run as a background job (WorkManager): constraints charging, battery not low and
  storage not low; each run bounded below the 10-minute worker limit and re-scheduled while tracks are pending;
  every finished track saved at once; a long track checkpointed so an interrupted run resumes it; only new or
  changed files (audio fingerprint path + size + modification time) or files measured by an older analyzer
  version. Scheduled after every scan.
- **FR-047d**: Analysis MUST decode through the Decoder Registry's provider for the file, with its own decoder
  instances, never the playback players, no audio output and no audio focus, converting to float on the same
  path as playback (ADR-004 float amendment). It MUST run at background priority without glitching playback.
  A decode failure is recorded with the provider and reason and is not retried until the file or the analyzer
  version changes.
- **FR-047e**: Measurements MUST be stored in `sync_documents` as `analysis` documents in the existing shape
  (`loudness_lufs`, `true_peak` in dBTP) so the existing read side reads them unchanged, without Mood features and
  without any Room schema change. They MUST survive rescans for unchanged files (`contracts/loudness-analysis.md`).
- **FR-047f**: Album loudness MUST be BS.1770-gated across all blocks of all the album's tracks (not an average of
  track values), computed from stored per-track data without re-decoding, only when every track of the album is
  measured, and recomputed whenever the album's tracks or their measurements change. Album true peak = the
  highest track true peak. Albums are tracks sharing an album id whose album name comes from a tag; MediaStore's
  folder fallback is not an album.
- **FR-047g**: Stored analysis data MUST stay small and bounded per track regardless of track length (histogram,
  not raw blocks), so large libraries stay practical (Principle 10).
- **FR-047h**: Analysis results MUST be independent of the selected engine; the job runs whichever engine is
  selected (the current engine's unchanged read side then also uses the measurements).

**Equalizer**

- **FR-050**: The saved equalizer settings (10 bands, preamp, stereo width) MUST apply on the new engine
  without re-entry, to each player, so both players are equalized the same way during a crossfade. The
  settings' storage and UI contract stay unchanged.
- **FR-051**: EQ, preamp and stereo width MUST run as in-app audio processors in each player and use the
  same filters as the current engine: 10 peaking biquads with Q = 1 at 32, 64, 125, 250, 500, 1k, 2k, 4k,
  8k and 16 kHz with the same coefficient formula, the same preamp gain, and the same mid/side width.
  Inside each player the order MUST keep linearity with the current engine's mix-then-process chain:
  time-varying gains (normalization, fade) before the EQ, so the sum of both players equals processing
  the mix.
- **FR-052**: The limiter MUST be the only stage of one DynamicsProcessing effect on the shared audio
  session (input/output gain neutral, EQ and compressor stages off). Every limiter parameter (attack,
  release, ratio, threshold, post-gain, link group) MUST be set explicitly; both channels MUST be linked;
  the threshold MUST be below 0 dBFS (about −1 dBFS) with a fast attack; the effect's frame duration MUST
  match the real output buffer duration.
- **FR-053**: If the session limiter can't be created, playback, EQ, preamp and width MUST continue; only
  the limiter is lost, and the clip-prevention setting MUST show that state clearly (no crash, no silent
  no-op). The app MUST NOT disable the DynamicsProcessing effect to turn clip prevention off: the effect stays
  enabled for the session's lifetime and "off" sets the limiter stage to neutral parameters (ADR-004 effect-state
  hedge; on the test device a disabled effect appeared to mute the session).
- **FR-054**: The app MUST broadcast the open/close audio-effect-control-session events with the shared
  session id and its package name when the session starts and ends.
- **FR-055**: When another app takes control of the session effect, the app MUST show that its limiter is
  not active, and restore it when control returns. The in-app EQ is unaffected. Losing control MUST be
  treated as a possible mute: the app MUST detect whether the session is still audible and, if it can't
  confirm it, release its own effect and continue without the limiter (FR-053 state shown) until control
  returns (ADR-004).
- **FR-056**: Equalizer, preamp and width changes MUST be click-free (no filter-state reset; coefficient or
  gain changes ramped or crossfaded).

**Formats and the Decoder Registry**

- **FR-060**: Every format the app plays today MUST play on `main` with the new engine selected. 001
  provides the mechanism and the platform and AIFF providers; feature 002 provides the rest. 001 alone
  is not mergeable (FR-003).
- **FR-063**: Each format/codec MUST map to an ordered list of decoder providers; the first available one
  decodes it. Order: platform codecs first, then Kotlin providers. Changing a format's providers or their
  order MUST be a configuration change, not a code rewrite. Each provider MUST be testable on its own
  against the same files.
- **FR-064**: A provider's decode failure MUST show the normal playback error, be logged with the provider
  name and the file's format, and skip to the next track. No retry on another engine.
- **FR-064a**: Decode failures MUST be kept in a bounded on-device decode-failure log (time, file name,
  format, codec, provider, error; no other library data) that the user can export from settings (share
  sheet as text, or save as a text file) and clear, so a user can send it with the failing file. Real-world
  format problems are fixed from these reports (owner decision 2026-10-05).
- **FR-065**: With the new engine selected, the library scan MUST admit a file only if the registry has a
  provider for its format; other files MUST NOT be tag-read, and are kept as hidden rows (FR-065a). With the
  current engine selected, the scan admits files as today (User Story 1). Changing the engine switch MUST
  trigger a rescan for the newly selected engine.
- **FR-065a** (owner decision 2026-10-06): A file the registry skips MUST be HIDDEN, not deleted. Its library row,
  play count, listening statistics and history, favorites, playlist membership and lyrics MUST be kept, and the row
  is marked with the reason it has no decoder on this device. Hidden tracks MUST be left out of every library view,
  queue, playback request, restore and search. When a decoder becomes available (a later provider, another device,
  or a rescan with the other engine), the track MUST reappear with its history intact. A hidden file's tags are not
  read: it keeps the values from its last admitted scan, or gets MediaStore's values only if it was never admitted.
  Codec support differs per device, so this applies beyond the migration.
- **FR-066**: After each scan, and in settings, the app MUST show a short summary of skipped files by
  format and reason (e.g. "12 files skipped: unsupported format (DSD)"). A newly registered provider MUST
  make its format appear after a rescan, with no scanner change.
- **FR-067**: AIFF and AIFF-C (uncompressed: NONE, sowt, twos) MUST play through a Kotlin provider ported
  from Choir's AiffExtractor, with attribution; compressed AIFF-C is refused by name. No FFmpeg, no NDK.
- **FR-068**: DSD (DSF/DFF) has no provider in this feature: it is unsupported on the new engine and
  appears in the skipped-files summary; it keeps playing on the current engine via the switch.
- **FR-062**: The format decision MUST NOT rely on the owner's library composition (Principle 10).

**Build, licensing and test infrastructure**

- **FR-070**: Test builds MUST install next to the daily app with a separate application ID suffix (not
  `.test`), never uninstall it, and never touch its data.
- **FR-071**: A generated test corpus MUST cover every format and tag form in scope, including untagged
  files, and live in `/sdcard/Music/SistrumTestCorpus` without a `.nomedia` file. No format may be faked;
  APE and DSD samples come from real encoders or the owner.
- **FR-072**: Third-party notices MUST list every added dependency and ported source with its license;
  ported files MUST carry attribution headers.
- **FR-073**: Characterization tests MUST pin today's behaviour before the seam is introduced and MUST NOT
  assert that normalization or Mood Radio work on the current engine, nor assert the known PlaybackQueue
  1000-track truncation (wrong start track in queues over 1000 tracks) as preserved behaviour, nor any
  faulty behaviour that FR-080…FR-092 correct (these apply to the shared service path for both engines).
- **FR-074**: The only Room change in this feature is one additive, nullable column, `sync_tracks.unavailableReason`
  (FR-065a), with database version 13 → 14 and `Migration13To14` (`ALTER TABLE sync_tracks ADD COLUMN
  unavailableReason TEXT`). Existing rows read as available. No other schema or version change (amended by owner
  decision 2026-10-06; was "no Room change").

**Robustness of the playback service (from code review 2026-10, Part 2)**

Each requirement names the review finding it prevents. They apply to the shared playback service path, so
they cover both engines, including the current engine's failure cases (Clarifications 2026-10-05, ADR-006); the
current engine itself, its native code and `FfmpegDecoder` are not modified (FR-006).

- **FR-080** (foreground start): Every path that starts the playback service as a foreground service MUST
  reach foreground state in time, including commands sent to a cold service with an empty queue and early
  returns (file unavailable, focus denied, prepare failure). Commands that don't start playback MUST NOT
  require a foreground start. After stop, failure or clear, the service MUST stop its periodic work and
  stop itself when nothing is playing.
- **FR-081** (command ordering): Commands MUST be executed strictly in arrival order by a single consumer;
  a focus loss and the following focus gain can never swap.
- **FR-082** (shutdown ordering): On service destruction, the service MUST stop accepting commands, cancel
  its work, wait for the in-flight command, and only then release players, effects and the session. No
  main-thread blocking on database or preference I/O in service creation or destruction.
- **FR-083** (queue transport): Queue contents (track ids) MUST NOT travel through Intent extras or other
  Binder transactions; a 20 000-track request MUST work.
- **FR-084** (uncaught exceptions): Failures in the position ticker and in command handling MUST be
  caught, logged and turned into the failed state or a skip, never a process crash; cancellation MUST
  still propagate.
- **FR-085** (focus handling): Audio focus MUST be requested only when audio is about to start (not for a
  paused restore), abandoned on failure and stop, and a pending "resume on focus gain" MUST be cleared by
  stop and by a user pause.
- **FR-086** (resource release): A player, decoder or effect created for a track that fails to prepare
  (play or restore) MUST be released; nothing leaks across failures.
- **FR-087** (work under lock): Publishing the queue to the media session and saving the session MUST NOT
  load the whole library per command; seek drags MUST be coalesced; session saves MUST land in command
  order.
- **FR-088** (end of track): The end of every track MUST be played in full (decoder drained, no dropped
  tail). A malformed file MUST become a playback error, never a native abort or out-of-memory crash.
- **FR-089** (transitions): Every automatic transition MUST be delivered exactly once and in order, even
  when two happen within one position tick; the queue and the playing item never desync.
- **FR-090** (truthful state): "Playing" MUST be reported only after output actually started; a start
  failure MUST surface as the failed state.
- **FR-091** (thread safety): System-control callbacks (e.g. skip to queue item from the media session)
  MUST access queue state only through the serialized command path.
- **FR-092** (preference churn): Equalizer and crossfade preference updates MUST be de-duplicated; a
  preference write MUST NOT re-prepare the next track unless a value that affects it changed.

### Key Entities

- **Playback engine**: something that can prepare, play, pause, seek, preload the next item, crossfade,
  report position/duration and report transitions/errors. Two implementations: current and new.
- **Engine selection**: the stored developer choice (current/new), read at playback start and scan.
- **Decoder Registry**: per format/codec, an ordered list of providers; answers "can this file be played,
  and by which provider".
- **Provider**: a named decoder route (platform codec, Kotlin AIFF, later the 002 ports), with its
  supported formats and refusals.
- **Skipped-files summary**: per scan, counts of files left out by format and reason.
- **Playback item**: a resolved library track (id, title, artist, album, album artist, track number,
  file path, artwork path).
- **Gain source**: something that yields gain information for an item: measured loudness, then tags.
- **Gain information**: per item: track gain, album gain, track peak, album peak, and the source form (measured,
  a tag form, or none).
- **Loudness analysis**: per track: integrated loudness, true peak, sample peak, status, file fingerprint,
  analyzer version, and a loudness histogram for album gating.
- **Album loudness**: per album: gated integrated loudness, true peak, member count and members fingerprint.
- **Normalization settings**: enabled, target LUFS (→ global pre-amp), track/album mode, clip prevention,
  untagged pre-amp.
- **Equalizer settings**: 10 band gains, preamp, stereo width (unchanged).
- **Test corpus file**: format, codec, bit depth/rate, tag form(s), expected gain.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the current engine selected, 100 % of the existing automated tests pass unmodified,
  and the characterization tests pass before and after the seam.
- **SC-002**: With the new engine selected, 100 % of the admitted test-corpus files play from start to end,
  and 100 % of corpus files without a provider appear in the skipped-files summary and are hidden from every
  library view, queue and search, with their rows and play counts kept (FR-065a).
- **SC-003**: Gapless joins on the new engine add no audible gap: measured silence between consecutive
  tracks of a gapless album is ≤ 10 ms.
- **SC-004**: With normalization on and the corpus analyzed, every corpus track of every format plays within
  ±1 dB of the target loudness; before analysis, tagged corpus tracks of every tag form play within ±1 dB of
  each other's expected level; no corpus track clips with clip prevention on.
- **SC-005**: The lock screen and notification show the correct artist for 100 % of corpus tracks with a
  known artist (0 "Unknown artist").
- **SC-006**: Time from tapping a track to hearing audio on the new engine is no worse than on the current
  engine by more than 20 %, measured on at least two different format mixes (Opus-heavy and
  lossless/MP3/AAC-heavy), not only the owner's library.
- **SC-007**: Crossfade on the new engine starts within 200 ms of the same point as on the current engine
  and completes without clicks, stuck volume or double playback across all User Story 5 scenarios.
- **SC-008**: Changing the engine switch never interrupts a playing track (0 interruptions in the test
  runs), and the next playback start uses the selected engine every time.
- **SC-009**: Installing test builds leaves the daily app's library, statistics and settings unchanged
  (verified before/after on the test device).
- **SC-010**: The shared queue and listening-tracker files show zero changes across the whole feature.
- **SC-011**: With clip prevention on, no corpus track produces clipped output with any combination of
  normalization, maximum equalizer boost, preamp and stereo width, including two loud tracks overlapping
  in a crossfade (overshoot above full scale bounded as set in the plan, since the limiter has no
  lookahead). With signals below the limiter threshold and a flat EQ, the limiter applies no gain
  reduction (output level within 0.1 dB of the bypassed effect).
- **SC-012**: *(Removed 2026-10-05; replaced by SC-014.)*
- **SC-013**: Over one hour of screen-off playback, measured on at least two library mixes (as in
  SC-006), the new engine's app CPU time and wakelock time (from `dumpsys batterystats`) and the app's
  memory use (from `dumpsys meminfo`) are no higher than the current engine's with crossfade disabled
  (0 %), and at most 10 % higher with crossfade enabled, measured with the second-player lifetime of FR-033.
  Raw battery percentage is not used.
- **SC-014**: Every decode failure in the test runs is logged with its format, codec, provider name and
  error, and appears in the exported decode-failure log (FR-064a); zero unexplained decode failures remain
  on corpus files when the default flips to the new engine.
- **SC-015**: On the test device, `dumpsys media.audio_flinger` shows both players' tracks on the same
  output thread with the session limiter enabled during a crossfade, and two overlapping test tones that
  clip only when summed come out limited. Hi-res corpus files are checked for direct-output routing.
- **SC-016**: The skipped-files summary after a scan matches the corpus: every skipped file is counted once,
  under its format and reason.
- **SC-017**: The meter matches the references: generated EBU Tech 3341 cases within ±0.1 LU; on identical PCM,
  the WaxFlow oracle within 0.01 LU (integrated) and 0.05 dB (true peak); on the device, lossless corpus files
  within 0.01 LU and lossy ones within 0.1 LU of the oracle; album loudness from stored histograms within 0.05 LU
  of WaxFlow's group measurement.
- **SC-018**: On the test device, the first analysis of the owner's library (775 tracks, ~50 h) completes within
  the estimate recorded in HANDOFF (re-measured in T051d) using only charging time by default, no run exceeds its
  budget, playback during analysis shows no underruns, and stored analysis data stays under 2 KB per track on
  average. Raw battery percentage is reported only for a run on battery, as information.

## Assumptions

- **Mandated technology (constitution, not a design choice of this spec)**: the new engine is Media3 /
  ExoPlayer with no FFmpeg; formats go through a Decoder Registry (platform codecs, then Kotlin providers);
  AIFF ports Choir's AiffExtractor; crossfade ports Rhythm's dual-player (A/B) mechanics with our
  equal-power curve; tag-based gain ports Rhythm's ReplayGain processor, extended as needed; EQ, preamp and
  stereo width are in-app per-player processors with the native engine's filters; the limiter is a
  limiter-only platform DynamicsProcessing effect on the shared audio session. PixelPlayerOSS is a
  secondary reference.
- The developer setting is unlocked by a hidden gesture and is available in all builds (so the owner can
  test on the daily build); exact gesture decided in the plan.
- Tag precedence when several forms exist: ReplayGain tags, then R128 (Opus), then Sound Check.
- Sound Check values are converted to a dB gain with the standard iTunNORM conversion and treated like a
  track gain against −18 LUFS.
- Normalization stays off by default; untagged pre-amp defaults to 0 dB.
- Engine selection, the untagged pre-amp and the skipped-files summary are stored outside the Room database
  (preferences or a small file); the only Room change is the FR-065a visibility column (FR-074).
- Only arm64-v8a is supported, as today.
- Device-only checks (listening, Bluetooth, headset, focus, process death, audio_flinger routing) are done by
  the owner on the test device (CPH2307) and ideally a second device; they are samples, not the target
  (Principle 10).
- The on-device loudness analyzer is part of this feature (2026-10-08). The Mood part (Mood Radio features) is
  a separate later feature (`research/analyzer-future.md`); until then Mood Radio stays unavailable to
  listeners, but its machinery is kept working and tested with supplied data.
- Future note (after the migration, not in scope): a possible automatic Track/Album mode would be based
  on the queue's source (album page = album gain; playlist, mixed queue or shuffle = track gain), never on
  adjacent tracks (`research/future-settings.md`).
- Removing the native player, the FFmpeg build and the NDK is feature 003, not this feature.
