# Graphify baseline — playback path (before migration)

Date: 2026-09-30 · Branch: `feature/media3-migration` · HEAD `c4b09cc`
Graph: `graphify-out/graph.json`, last regenerated in `8b59f41`; no commits since then touch
`androidApp/src/main`, so the graph matches the code.

## Scope and size

- Extraction root: `androidApp/src/main` (code-only). **`sharedLogic` is not in the graph**, so
  `PlaybackQueue`, `ListeningTracker`, `VolumeNormalization` and `MoodRadio` appear only as unresolved
  references. Use targeted search for them.
- 2562 nodes, 8228 edges, 117 communities. Nodes by source: kotlin 1438, cpp 103, no source file 1021
  (external symbols such as Compose APIs).

## God nodes (degree)

| Node | Degree | Relevance |
|---|---|---|
| AndroidLibrarySyncStore | 93 | analysis data source (normalization, Mood Radio) |
| LibraryTrack | 77 | item resolution |
| EmbeddedTagReader | 67 | scanner, off-path |
| MaterialSymbol() | 64 | UI, off-path |
| **PlaybackService** | 62 | playback hub |
| SyncDao | 60 | Room |
| AppDestinationContent() | 58 | UI |
| **PlaybackController** | 55 | public playback boundary |
| **FfmpegDecoder** | 51 | JNI boundary |
| AppStackPage | 39 | UI |

## Playback subgraph (queries used)

- `graphify query "How does PlaybackController talk to FfmpegDecoder and the native JNI player, crossfade, MediaSession, audio focus, queue"`
- `graphify explain PlaybackController` / `FfmpegDecoder` / `PlaybackService`
- `graphify path PlaybackService FfmpegDecoder` → 1 hop (`references`, PlaybackService.kt:L64)
- `graphify path PlaybackController PlaybackService` → **no directed path**. The link is runtime
  (Intent actions + companion StateFlows), which static extraction doesn't see as a call edge.

Communities: PlaybackController = 24, PlaybackService = 18, FfmpegDecoder/NativeTransition = 4,
native `ffmpeg_player.cpp`/`audio_callback` = 29, native `PlaybackEngine`/`focus_gain` = 35,
PlaybackState = 53, ArtworkCrossfadeTransition = 57.

### Reverse dependencies (who depends on playback)

- `PlaybackController` ← 8 ViewModels (Insight, LibraryTracks, LibraryAlbums, PlaylistDetails,
  AlbumDetails, ArtistDetails, ComposerDetails, GenreDetails) + `AndroidPlaybackRuntime.controller()`
  + `MainActivity`. None of them reach `PlaybackService` or `FfmpegDecoder` directly.
- `PlaybackService` ← `MainActivity.kt` (import) and the manifest.
- `AndroidPlaybackSession` ← `MainActivity.kt:531` (output switcher token).

### Direct dependencies of PlaybackService

FfmpegDecoder, PlaybackItem, PlaybackPreferences, EqualizerPreferences (+ `equalizerDspConfig`),
NormalizationPreferences, PlaybackSessionStore, LastFmService, AndroidSyncRuntime/AndroidLibrarySyncStore,
AndroidPlaybackRuntime, DeviceIdentity, and (sharedLogic, not in the graph) PlaybackQueue,
ListeningTracker, normalizationGainDb, selectMoodRadio.

### Native boundary

`FfmpegDecoder` (Kotlin, 25 `external` functions) ↔ `ffmpeg_player.cpp` (`Java_me_misa198_airmedy_player_FfmpegDecoder_*`).
Native internal: `PlaybackEngine` struct → `SourceSlot[2]`, `audio_callback`, `decoder_loop`,
`promote_gapless`, `snap_fade`, `configure_eq`. Links against avformat, avcodec, swresample, avutil,
aaudio, android, log (`CMakeLists.txt`).

### Shared components

`PlaybackState`, `PlaybackItem`, `ArtworkCrossfadeTransition` (PlaybackModels.kt) are shared by the
service, the controller and the UI. `GlobalDspConfig` is shared by EqualizerPreferences and FfmpegDecoder.

## Notes for later phases

- After each task, refresh with `python3 .claude/hooks/graph_refresh.py auto` (scope since 2026-10-05: main + test code and sharedLogic, `.graphifyignore`) and compare
  degrees of PlaybackService/FfmpegDecoder: the service's degree should drop as engine calls move behind
  the abstraction, and `FfmpegDecoder` should end up with a single dependant (`LegacyNativeEngine`).
- Name clash: the native struct is already called `PlaybackEngine` (cpp:86). A Kotlin interface with the
  same name makes `graphify explain PlaybackEngine` ambiguous.
