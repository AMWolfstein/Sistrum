# Research: Offline-only app

## R1. Order of removal

- **Decision**: remove leaf features first (Last.fm, recognition, motion artwork,
  weekly releases), then online lyrics, online metadata, conversion, Cast, network
  storage, the download engine, the download history source and the extension system;
  then slim the Rust backend; then the platform/dependency sweep.
- **Rationale**: each step leaves a working app with analyze and tests green. The
  download engine and the extension system touch the most shared files
  (`platform_bridge.dart`, `settings_provider.dart`, `main.dart`, `main_shell.dart`,
  the track pages), so they go after the features that call into them. Rust goes after
  every Dart/Kotlin caller of the removed exports is gone, so the binding change does
  not break a half-removed caller.

## R2. The download history source

- **Decision**: remove it entirely (owner: nobody has the app; the library is offline).
- **Consequence**: the library database stops attaching `history.db`; the shadow logic
  (`library_shadow_sql.dart`, `upsertBatchExcludingHistory`), `LibraryItemSource.
  downloaded`, the history provider, model, maintenance, recovery and the downloaded
  embedded-cover resolver go. `UnifiedLibraryItem` keeps only the local (scanned) item;
  queries, search, lyrics search, mixes, statistics joins and the media browser read
  only `library_visible`. Backups drop the history section.
- **Risk**: many files and tests use history fixtures (about 38 consumers). This is
  its own task (T008) after the download engine is gone.

## R3. FFmpeg stays

- **Decision**: keep `ffmpeg_kit_flutter_new_audio` and its patched copy in
  `third_party/`.
- **Rationale**: owner kept tag editing, ReplayGain, CUE split; FFmpeg also serves
  probing, audio analysis (AutoMix, haptics, features) and tag-write fallbacks.
  Removed with conversion: `ffmpeg_conversion.dart`, `conversion_library_service.dart`,
  `audio_conversion_utils.dart`, convert UI; with downloads: `ffmpeg_decryption.dart`;
  with re-enrich: `ffmpeg_reenrich.dart`. Kotlin's FFmpegKit users are download
  finalization only and go with T007.

## R4. Rust: the offline backend lives in the extensions crate

- **Finding**: the library scanner (`backend/library.rs` 715, `library/scan.rs` 1103),
  tag writes (`backend/tags.rs` 378) and cover extraction/cache (parts of
  `backend/cover.rs` 1271) are inside `crates/extensions`, reached by Kotlin through the
  UniFFI `ExtensionManager` of `crates/mobile`.
- **Decision**: move those modules into `mobile` (or `core`), rename/slim the manager to
  the offline methods (scan, progress, pause/cancel, tags, CUE, Hi-Res, cover extract,
  data jobs, USB audio), then delete `extensions`, `providers`, `network`, `cast` and
  `vendor/cap-primitives-4.0.3`, and prune `core`'s online modules
  (`metadata/{musicbrainz,deezer,catalog,share,reenrich}`, `resolver`, `downloads`,
  provider cover sizing, `music_signature`, lyrics provider parts; the LRC/TTML parsers
  stay). The C++ audio library (FLAC/Oboe/WavPack decode) is untouched.
- **Expected effect**: `libsistrum_mobile.so` (18.6 MB in the APK) loses QuickJS, the
  TLS/HTTP stack, the HTML parser, crypto and mDNS. The size is measured in the final
  task, not assumed.

## R5. Kotlin

- Deleted with downloads: `DownloadService*.kt`, `NativeDownloadFinalizer.kt`,
  `NativeFinalizer*.kt`, `NativeFinalizationPolicy.kt`, `NativeHistoryStore.kt`,
  `NativeReplayGainPolicy.kt`, `NativeWorkerPolicy.kt`, the download progress stream and
  backend methods. `SafDownloadHandler.kt` is split: its shared SAF writers move to a new
  `SafDocumentWriter`; the download strategies go.
- With conversion: `NativeFlacConversion.kt`. With Cast: `CastOutputPlugin.kt`,
  `SistrumCastDialogs.kt`, `SistrumCastOptionsProvider.kt`, `play-services-cast-framework`.
  With network storage/Cast: `LocalNetworkPlugin.kt`. With extensions:
  `ExtensionAuthCallbacks.kt`, `MainActivityExtensionIntents.kt` and the `sistrum://`
  intent filters; `RustCoreBackendApplication.kt` keeps only the data-job dispatch.
- Kept for the updater: `BackgroundWorkService.kt`, `ForegroundWorkLock.kt`,
  `CoalescingWorkRefresh.kt`, the FileProvider and `file_paths.xml`.

## R6. Dependencies

- Removed: `dart_smb2`, `synchronized`, `html`, `record`, `connectivity_plus`,
  `video_player` (+ dev `video_player_platform_interface`), `app_links` (unused), and
  `flutter_secure_storage` if no user remains after T002/T006/T009; the
  `audioplayers_android` override only if its network-provider patch is the sole reason.
- Kept: `http` (updater only), `open_filex`, `path_provider`, `crypto`, `xml`,
  `url_launcher`, `flutter_local_notifications`, `permission_handler`,
  `receive_sharing_intent` (local audio share-in), `cached_network_image` and
  `flutter_cache_manager` only while something still renders a URL cover (re-check in
  T008/T011; after T008 covers are files).

## R7. Settings

- No migration (no users): removed fields are deleted from `AppSettings`, its JSON and
  `settings.g.dart` (hand-edited as in M003), with their setters, settings pages,
  search-catalog entries and backup fields. Tolerant reading of old backups is not
  required.
