# Phase 0 — Targeted Discovery + Graphify Baseline

Read `CLAUDE.md` and `.specify/memory/constitution.md` first.
No implementation in this phase. Do not read the whole repository.

## Steps

1. **Minimal context.** Read only: README, root and relevant module Gradle
   files, settings.gradle(.kts), version catalog, AndroidManifest parts related
   to playback/service, and the test directory layout. No source files yet.

2. **Graphify first.** Build the dependency map for the playback path around:
   PlaybackController, FfmpegDecoder, native/JNI playback bridge, MediaSession,
   audio focus, queue, playback state, crossfade, player lifecycle.
   Identify direct deps, reverse deps, shared components, native boundaries.

3. **Targeted search** for: PlaybackController, FfmpegDecoder, FFmpeg, ffmpeg,
   nativeBeginCrossfade, nativeFinishCrossfade, nativeSnapCrossfade,
   kTransitionCrossfadeStarted, MediaSession, AudioFocus, queue, shuffle,
   repeat, playNext, selectQueueTrack, StateFlow, loudness_lufs, true_peak,
   sync_documents, ReplayGain/gain.

   Known starting points: `player/FfmpegDecoder.kt`, `androidApp/src/main/cpp`,
   PlaybackService, `scripts/build-ffmpeg-android.sh`.

4. **Targeted reading**, in this order, only files on the direct path:
   PlaybackController → native player → JNI bridge → queue → state models →
   MediaSession/service → crossfade → audio focus → normalization gain
   application → existing playback tests.
   For Scanner, Room, Lyrics, Last.fm, Stats, Mood Radio, Artwork, Metadata:
   read only the specific functions that consume playback events.

5. **Format inventory.** From code (extensions, MIME filters, native decoder
   config, scanner filters) list every format the app currently accepts.
   Mark each as: supported by Media3/platform decoders / device-dependent /
   not supported without an extension. Note which formats are enabled in
   `scripts/build-ffmpeg-android.sh` vs. which the scanner actually admits.

6. **Broken features.** Volume normalization and Mood Radio are known broken
   because they need native-layer changes. For each: find where it is wired,
   what native capability it lacks, and what data it depends on. Do not fix them
   in this phase.

## Stop condition

Stop discovery once you can answer:
1. Where playback starts and ends.
2. How PlaybackController talks to the native player.
3. How queue/state contracts work.
4. How crossfade works today (begin/finish/snap semantics, timing, who drives it).
5. Where MediaSession and audio focus live.
6. Why volume normalization and Mood Radio fail today, and what each needs.
7. Which files must change.
8. Which behaviors must be preserved.

## Output

Write `specs/<feature>/research/discovery.md` containing:
- Playback architecture summary + current dependency path (fill in the real
  chain between PlaybackController and the native layer)
- Answers to the 8 questions
- Format inventory table
- Broken-feature analysis (normalization, Mood Radio)
- MUST READ / MAY READ / DO NOT READ lists
- Risks
- Recommended migration boundaries

Write the Graphify summary to `specs/<feature>/research/graph-baseline.md`.
Create `specs/<feature>/HANDOFF.md`. Print the status report. Stop.
