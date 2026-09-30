# Feature Specification: Media3 playback engine (additive migration)

**Feature Branch**: `feature/media3-migration` (spec directory `specs/001-media3-migration`)

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Additive migration of Sistrum's playback from the native FFmpeg/JNI engine to Android Media3/ExoPlayer (with Jellyfin's media3-ffmpeg-decoder), behind a PlaybackEngine abstraction and a hidden developer engine switch, preserving every current behavior. In scope: engine abstraction with LegacyNativeEngine (zero behavior change), Media3Engine, Rhythm dual-ExoPlayer A/B crossfade, tag-based normalization (ID3 TXXX, REPLAYGAIN_*, R128_*, iTunes Sound Check; −18 LUFS reference; R128 +5 dB; target as global pre-amp; untagged pre-amp; clip prevention), equalizer plus preamp/stereo width, MediaSession incl. the lock-screen Unknown artist fix keeping decodeArtworkBitmaps, deferred formats routed to the native player with a hard cut at engine boundaries, test builds with a separate applicationId suffix and a test corpus. Out of scope: the on-device analyzer / Mood Radio revival, deleting the native player or FFmpeg build. PlaybackQueue.kt and ListeningTracker.kt must need zero changes."

**Governing documents**: `.specify/memory/constitution.md` (Principles 1–10 and all sections) and
`research/discovery.md` (behaviours to preserve §2 Q8, format inventory §3, risks §6, boundaries §7).
Where this spec and the constitution disagree, the constitution wins and this spec is corrected.

Terms used below:
- **Current engine**: the existing native player.
- **New engine**: the second playback engine being added.
- **Engine switch**: the hidden developer setting that selects the engine.
- **Deferred formats**: AIFF, APE, WavPack, DSD (DSF/DFF), WMA.
- **Listener**: anyone who installs Sistrum, with any library, tagging tool and device (Principle 10).

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
  disabled with a short note that they require the new playback engine. With the new engine selected,
  tracks routed to the native engine (deferred formats) get the same tag-based gain, computed once in
  shared Kotlin code and passed through the native engine's existing normalization-gain parameter (no
  native code changes).
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
- Q: How should native-routed tracks (deferred formats) handle clip prevention, gain ramps and gain
  tags? → A: No limiter on the native path: gain is reduced so the tagged peak stays ≤ 0 dBFS; with no
  known peak, total gain is capped at 0 dB (no positive gain or pre-amp). Gain ramps are emulated from
  Kotlin by stepping the native gain in small increments (~10 steps over ~200 ms), no native change; if
  that is audibly steppy on the device, an instant step on the native path only is accepted as a
  documented limitation. Gain tags in APEv2, DSF/DFF and ASF are out of scope: those files are treated as
  untagged. All of this is temporary and tied to the deferred-formats decision.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Nothing changes while the current engine is selected (Priority: P1)

A listener updates Sistrum. The engine switch defaults to the current engine. Everything plays, sounds
and behaves exactly as before: queue, gapless, crossfade, equalizer, focus handling, lock screen,
statistics, Last.fm, lyrics sync, session restore.

**Why this priority**: The migration is additive. Introducing the engine seam must not regress anyone
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

---

### User Story 3 — Engine switch for development and rollout (Priority: P1)

A developer opens a hidden developer setting and switches between the current and new engine to compare
them on the same library and device.

**Why this priority**: Both engines must coexist and stay selectable throughout the migration
(Principles 1 and 8). The switch is also how the default later flips.

**Independent Test**: Change the switch while a track plays; confirm the running track is unaffected and
the next playback start uses the selected engine.

**Acceptance Scenarios**:

1. **Given** a track is playing on the current engine, **When** the switch is changed to the new engine,
   **Then** the running track keeps playing unchanged, and the next playback start uses the new engine.
2. **Given** a fresh install on this branch, **Then** the current engine is selected.
3. **Given** the setting is hidden, **Then** a listener who doesn't know the unlock gesture never sees it.

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

---

### User Story 6 — Consistent loudness across a mixed library (Priority: P2)

A listener with normalization enabled plays a library that mixes MP3, FLAC, Ogg, Opus and M4A files,
tagged by different tools or not at all. Tracks play at one consistent level, without clipping.

**Why this priority**: Normalization is broken today for every listener. Stage 1 (tag-based) restores
it for tagged libraries on the new engine.

**Independent Test**: Test-corpus tracks with known gain tags in each tag form, plus untagged tracks;
measure the output level per track against the expected level.

**Acceptance Scenarios**:

1. **Given** tracks with ReplayGain tags in MP3 (ID3 TXXX), FLAC/Ogg Vorbis (REPLAYGAIN_*), Opus
   (R128_*) and M4A (Sound Check or ReplayGain atoms), **When** normalization is on, **Then** they all play
   at the same target level.
2. **Given** the target is −14 LUFS, **Then** every file gets the same global pre-amp of +4 dB on top of
   its tag gain (reference −18 LUFS). The target never differs per format.
3. **Given** an Opus file with R128 gain tags and a non-zero header output gain, **Then** the header gain
   is applied exactly once and the R128 tag gain is converted by +5 dB.
4. **Given** an untagged file, **Then** it plays at unity gain plus the "untagged pre-amp" setting.
5. **Given** album mode, **Then** every track with an album gain plays at its album gain, whatever the
   queue order or the next track (including the last track of an album and album tracks inside a
   shuffled or mixed queue); a track without an album gain uses its track gain.
6. **Given** a gain that would push peaks above full scale and clip prevention on, **Then** the gain is
   reduced so the output doesn't clip, for tagged and untagged files alike.
7. **Given** normalization is off, **Then** no gain is applied (the equalizer and pre-amps still apply as
   configured).
8. **Given** the current engine is selected, **When** the listener opens the normalization settings,
   **Then** they are disabled and a short note says they require the new playback engine.
9. **Given** the new engine is selected and a queue mixes deferred-format tracks (routed to the native
   engine) with other tracks, **Then** they get the same tag-based gain where their tags are read (AIFF
   ID3); native-routed tracks with no known peak are capped at 0 dB total gain, so they may play quieter
   than the rest (temporary, documented limitation tied to the deferred-formats decision).
10. **Given** a track is playing, **When** the listener drags the target or untagged pre-amp slider,
   **Then** the level follows smoothly without clicks, steps or jumps back to the old value.

---

### User Story 7 — Equalizer on the new engine (Priority: P3)

A listener's existing equalizer settings (10 bands, preamp, stereo width) sound the same on the new
engine and apply to both crossfading tracks.

**Why this priority**: On the no-feature-loss list; it can be restored after core playback.

**Independent Test**: Apply extreme EQ, preamp and width settings; compare with the current engine by
listening and by measuring a test tone.

**Acceptance Scenarios**:

1. **Given** saved equalizer settings, **When** the new engine plays, **Then** they apply without the
   listener re-entering them.
2. **Given** a crossfade, **Then** both overlapping tracks are equalized the same way.
3. **Given** preamp and stereo width are changed during playback, **Then** the change is heard within
   a moment and without clicks.

---

### User Story 8 — Every format keeps playing (Priority: P3)

A listener's library contains any mix of the formats Sistrum plays today, including AIFF, APE, WavPack,
DSD and WMA. Every track plays when the new engine is selected.

**Why this priority**: Format parity is a gate (Principle 6); the deferred formats matter equally
(Principle 10).

**Independent Test**: Play every file of the test corpus with the new engine selected.

**Acceptance Scenarios**:

1. **Given** the new engine is selected, **When** a deferred-format track plays, **Then** it plays through
   the current engine.
2. **Given** a queue alternates between a deferred-format track and another track, **Then** the change
   between them is a clean hard cut (no crossfade, no gap guarantee), documented as a temporary known
   limitation.
3. **Given** a file the new engine can't decode at playback time, **When** the native player is still
   present (this migration), **Then** that track is retried once on the current engine (hard cut), and
   the fallback is logged and listed in the developer setting.
4. **Given** the current engine also fails on that track, **Then** the normal playback error is shown and
   playback skips to the next track; there is no retry loop between engines.

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
  the next playback start changes engine.
- Session saved on one engine and restored on the other: the queue, track and position restore the same.
- Very short tracks (< 2 s): no crossfade, gapless advance only.
- Track removed from the library or file deleted mid-queue: skipped/dropped as today; no crash.
- Output route disappears during a fade.
- Audio focus lost during a fade.
- Gain tags that are malformed, out of range (e.g. +50 dB), have lower-case keys or units missing:
  ignored safely (treated as untagged or clamped), never distorted output.
- A file carrying several tag forms at once (e.g. ReplayGain and Sound Check): one defined precedence.
- Opus without R128 tags but with a header gain.
- Repeat-one combined with a deferred-format track (hard cut into itself).
- Mood Radio refill while a fade runs.
- Test corpus folder present on a listener device without blocklisting: it simply appears as music.

## Requirements *(mandatory)*

### Functional Requirements

**Engine seam and switch**

- **FR-001**: The system MUST route all playback through one engine abstraction, with the current engine
  as its first implementation and zero observable behaviour change.
- **FR-002**: The system MUST offer a hidden developer setting that selects the engine, defaulting to the
  current engine on the migration branch; a change MUST take effect at the next playback start, never
  mid-track.
- **FR-003**: Before any merge into `main`, the default MUST be the new engine, and every requirement in
  this spec MUST pass on it.
- **FR-004**: The playback controller's public operations and observable state streams MUST stay as
  listed in constitution Principle 2; any change needs an ADR and tests.
- **FR-005**: The shared queue and listening-tracker components MUST need zero changes; the engines adapt
  to them.
- **FR-006**: The current engine, its native code and the FFmpeg build MUST stay untouched and selectable.
  Removing them is not part of this feature.

**Playback behaviour (both engines)**

- **FR-010**: Play, pause, resume, stop, seek, next, previous (> 3 s restarts), shuffle, repeat
  off/one/all, play next, append, remove, reorder, select, clear queue MUST behave as documented in
  discovery §2 Q3/Q8.
- **FR-011**: Automatic advance without crossfade MUST be gapless between tracks on the same engine.
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

**Crossfade**

- **FR-030**: Crossfade on the new engine MUST follow the decided dual-player technique (constitution
  Principle 5) and keep the current observable contract: start rule, duration clamp, automatic advance
  only, fade ends at once on pause/seek/stop/queue edit, queue and state switch at fade start, artwork
  transition event, overlap split in statistics, next-but-one track prepared only after the fade.
- **FR-031**: Repeat-one and previous/next during a fade MUST have defined, tested outcomes (User Story 5,
  scenarios 3, 3a, 4). Next during a fade is a hard cut to the track after the incoming one; previous
  applies the normal 3 s rule to the incoming track. The design MUST NOT rule out a future "crossfade on
  skip" setting that turns next during a fade into a crossfade.
- **FR-032**: A crossfade setting change MUST never alter a running fade.
- **FR-033**: With crossfade disabled, the new engine MUST NOT create or pre-buffer a second player;
  gapless advance uses a single player. The second player exists only while crossfade is enabled.

**Normalization (tag-based, stage 1)**

- **FR-040**: Gain tags MUST be read in all common forms: ID3v2 TXXX ReplayGain (MP3), REPLAYGAIN_*
  comments (FLAC, Ogg Vorbis), R128_TRACK_GAIN / R128_ALBUM_GAIN (Opus), iTunes Sound Check and
  ReplayGain freeform atoms (M4A). Tag keys are case-insensitive.
- **FR-041**: All tag gains MUST be applied as written against the −18 LUFS reference; R128 gains MUST be
  converted by +5 dB. Opus header output gain MUST be applied exactly once.
- **FR-042**: The user's target LUFS MUST act as one global pre-amp relative to −18 LUFS (target −14 →
  +4 dB) applied to every file, tagged or not.
- **FR-043**: Untagged files MUST play at unity gain plus a separate "untagged pre-amp" setting (default
  0 dB). Album mode MUST always use the file's album gain when present and otherwise the track gain,
  independent of queue order or the next track (unlike today's "next track on the same album" rule,
  which stays unchanged on the current engine).
- **FR-044**: Clip prevention MUST apply to all files, tagged and untagged: where a peak value is tagged,
  the gain is reduced so that peak stays below full scale; in addition, a transparent limiter sits at the
  very end of the new engine's output chain (after normalization, equalizer, preamp and stereo width,
  since each of them can clip). The limiter MUST NOT alter the signal while it stays below its
  threshold. (The current engine is not modified; for native-routed tracks see FR-049.)
- **FR-045**: The existing normalization settings (enabled, target, track/album mode, clip prevention)
  MUST remain the user-facing settings, extended only by the untagged pre-amp. They MUST be enabled only
  while the new engine is selected (no longer force-disabled for lack of analysis data); while the current
  engine is selected they MUST be disabled with a short note that they require the new playback engine.
- **FR-048**: With the new engine selected, tracks routed to the native engine MUST receive the same
  tag-based gain as all other tracks. The gain MUST be computed once in shared Kotlin code (one
  implementation for both engines) and passed to the native engine through its existing
  normalization-gain parameter (dB, applied per source, updatable for the playing and next track).
  No native code changes. For formats the new engine cannot open, the shared Kotlin code reads gain tags
  only where today's tag reader already parses the container (ID3 inside AIFF). Gain tags in APEv2 (APE,
  WavPack), DSF/DFF and ASF (WMA) are out of scope: those files are treated as untagged until the
  deferred-formats decision.
- **FR-049**: The native path has no limiter (temporary, tied to the deferred-formats decision). For
  native-routed tracks, gain MUST be reduced so the tagged peak stays ≤ 0 dBFS; when no peak is known,
  total gain MUST be capped at 0 dB (no positive gain or pre-amp).
- **FR-046**: Gain changes MUST never cause an audible jump inside a crossfade.
- **FR-046a**: A normalization setting change during playback (on/off, target, untagged pre-amp,
  Track/Album mode) MUST be heard immediately through a smooth ramp of about 100–300 ms, applied to the
  playing track and the already-prepared next track, and to both tracks together during a crossfade.
  Each new value MUST ramp from the gain actually applied at that moment (never restarting from the old
  setting, never stepping), so continuous slider dragging stays smooth. The end-of-chain limiter stays
  active throughout.
- **FR-046b**: On native-routed tracks, the FR-046a ramp MUST be emulated from Kotlin by stepping the
  native gain in small increments (~10 steps over ~200 ms), with no native code change. If the owner
  finds that audibly steppy on the device, an instant step on the native path only is accepted and
  documented as a known limitation.
- **FR-047**: When several tag forms are present, one documented precedence MUST apply
  (ReplayGain → R128 → Sound Check assumed; see Assumptions).

**Equalizer**

- **FR-050**: The saved equalizer settings (10 bands, preamp, stereo width) MUST apply on the new engine
  without re-entry and to both players during a crossfade.
- **FR-051**: Preamp and stereo width MUST be supported on the new engine (solution decided in the plan).

**Formats**

- **FR-060**: Every format the app plays today MUST keep playing with the new engine selected.
- **FR-061**: Deferred formats MUST be routed to the current engine; the transition between engines MUST be
  a clean hard cut, documented as a temporary known limitation.
- **FR-062a**: While the native player exists, a track the new engine fails to decode MUST be retried
  once on the current engine (hard cut). Every fallback MUST be logged and visible in the developer
  setting, and during the migration each one is a bug to investigate before the default flips (FR-003).
  If the current engine also fails, the normal error is shown and playback skips to the next track; no
  retry loop between engines. Once the native player is removed (owner decision), failures show the
  normal error.
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
  assert that normalization or Mood Radio work on the current engine.
- **FR-074**: No Room schema or version change is part of this feature.

### Key Entities

- **Playback engine**: something that can prepare, play, pause, seek, preload the next item, crossfade,
  report position/duration and report transitions/errors. Two implementations: current and new.
- **Engine selection**: the stored developer choice (current/new), read at playback start.
- **Playback item**: a resolved library track (id, title, artist, album, album artist, track number,
  file path, artwork path).
- **Gain information**: per item: track gain, album gain, track peak, album peak, source tag form, and
  whether any tag was found.
- **Normalization settings**: enabled, target LUFS (→ global pre-amp), track/album mode, clip prevention,
  untagged pre-amp.
- **Equalizer settings**: 10 band gains, preamp, stereo width (unchanged).
- **Test corpus file**: format, codec, bit depth/rate, tag form(s), expected gain.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the current engine selected, 100 % of the existing automated tests pass unmodified,
  and the characterization tests pass before and after the seam.
- **SC-002**: With the new engine selected, 100 % of the test-corpus files play from start to end (deferred
  formats via the current engine).
- **SC-003**: Gapless joins on the new engine add no audible gap: measured silence between consecutive
  tracks of a gapless album is ≤ 10 ms.
- **SC-004**: With normalization on, tagged corpus tracks of every tag form play within ±1 dB of each
  other's expected level; no corpus track clips with clip prevention on.
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
  normalization, maximum equalizer boost, preamp and stereo width; with signals below the limiter
  threshold, output is bit-identical with and without the limiter.
- **SC-012**: Zero unexplained engine fallbacks remain in the developer-setting log when the default
  flips to the new engine.
- **SC-013**: Over one hour of screen-off playback, measured on at least two library mixes (as in
  SC-006), the new engine's app CPU time and wakelock time (from `dumpsys batterystats`) and the app's
  memory use (from `dumpsys meminfo`) are no higher than the current engine's with crossfade disabled
  (0 %), and at most 10 % higher with crossfade enabled. Raw battery percentage is not used.

## Assumptions

- **Mandated technology (constitution, not a design choice of this spec)**: the new engine is Media3 /
  ExoPlayer with Jellyfin's Media3 FFmpeg decoder; crossfade ports Rhythm's dual-player (A/B) technique;
  tag-based gain ports Rhythm's ReplayGain processor, extended as needed; the equalizer uses the platform
  equalizer effect on the player's audio session. PixelPlayerOSS is a secondary reference.
- The developer setting is unlocked by a hidden gesture and is available in all builds (so the owner can
  test on the daily build); exact gesture decided in the plan.
- Tag precedence when several forms exist: ReplayGain tags, then R128 (Opus), then Sound Check.
- Sound Check values are converted to a dB gain with the standard iTunNORM conversion and treated like a
  track gain against −18 LUFS.
- Normalization stays off by default; untagged pre-amp defaults to 0 dB.
- Engine selection and the untagged pre-amp are stored as preferences, not in the Room database.
- Only arm64-v8a is supported, as today.
- Device-only checks (listening, Bluetooth, headset, focus, process death) are done by the owner on the
  test device (CPH2307) and ideally a second device; they are samples, not the target (Principle 10).
- The on-device analyzer (true LUFS, Mood Radio features) is a separate later feature; until then Mood
  Radio stays unavailable to listeners, but its machinery is kept working and tested with supplied data.
- Future note (after the migration, not in scope): a possible automatic Track/Album mode would be based
  on the queue's source (album page = album gain; playlist, mixed queue or shuffle = track gain), never on
  adjacent tracks. No third mode and no constitution change now.
- Removing the native player or the FFmpeg build is out of scope; after the new engine works fully, a
  report states whether the native player is dead code, and the owner decides.
