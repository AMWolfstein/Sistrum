# Sistrum Constitution — Playback Migration

## Goal

Migrate playback from the current FFmpeg/native (JNI) player to
Android Media3 + ExoPlayer, preserving current app behavior. Local/offline
playback only. This is an architectural migration, not "add ExoPlayer".

Target shape:

```
PlaybackController
      ↓
PlaybackEngine (abstraction; name may follow repo conventions)
      ↓
LegacyNativeEngine  |  Media3Engine (ExoPlayer)
```

## Principles

1. **Strangler, not rewrite.** The abstraction is introduced first with the
   existing native player as its first implementation, with zero behavior
   change. The Media3 engine is built behind the same interface. Both coexist,
   selectable, until the legacy engine is explicitly retired.

2. **Preserve contracts.** PlaybackController keeps its responsibilities and
   public API: play, pause, resume, stop, clearQueue, next, previous, shuffle,
   repeat, playNext, append, startMoodRadio, selectQueueTrack; and its
   StateFlows (playback state, queue, artworkCrossfade, moodRadioActive,
   crossfadeSeconds). Any contract change must be necessary, documented in an
   ADR, and covered by tests.

3. **No feature loss.** Never remove or disable: Listening Statistics, Last.fm
   (start, completion, scrobble), Lyrics (including sync), Mood Radio, Artwork,
   Metadata, Queue, Crossfade, Volume normalization.

4. **No online playback.** Local files, MediaStore URIs, content URIs, and file
   URIs only (whichever the app actually uses).

5. **Decide risky things early.** Crossfade architecture is decided by a spike
   and recorded in an ADR before any engine code depends on single-player
   assumptions. MediaSession integration must follow that decision.

6. **Format parity is a gate.** The current engine decodes every format FFmpeg
   supports, with no platform fallback. Before retiring it, inventory what the
   app accepts and what the owner's library actually contains, and decide per
   format: platform decoder, Media3 FFmpeg decoder extension, or documented
   drop (owner approval required).

7. **Evidence over assumption.** Supported formats, normalization behavior, and
   crossfade semantics are determined from the actual code and data, not
   assumed. Do not add formats, a new normalization system, or new features
   during the migration.

8. **Legacy removal is last and gated.** The native playback path is removed
   only after the Media3 engine is the default, all gates in the spec pass,
   the user completes the manual device checklist, and the user approves.
   Native code with non-playback uses stays.

9. **Honest verification.** Tests, builds, and Graphify results are reported
   exactly as run. Device-only checks belong to the user.

## In scope during migration

These are known-broken today and are fixed on the Media3 engine, not the native one:

- **Volume normalization** — currently not working because it needs native-layer
  changes. Implement it on the Media3 engine using the loudness data the scanner
  already stores (e.g. `loudness_lufs`, `true_peak`); discovery confirms which
  fields exist and how they were meant to be applied. Must be specified in the spec
  with acceptance criteria.
- **Mood Radio** — currently not working because it needs native-layer changes.
  Discovery identifies the exact native dependency; the spec defines the working
  behavior on the Media3 engine.
- **Lock screen "Unknown artist"** (PlaybackService metadata) — fixed as part of the
  MediaSession work.

Characterization tests describe the native engine as it is: they must not assert
that normalization or Mood Radio work, and their failure there is not a regression.
Acceptance for these two is defined against the Media3 engine only.
