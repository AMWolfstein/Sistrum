# M004 tasks

Input: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md).
Follow `.specify/project-policy.md`. Read plan.md's Repository evidence first.

## Execution rules

Sequential tasks in ID order; no parallel markers. The **first action in every task**,
including [FINAL], is `python3 workflow/codegraph/code_tools.py update`; inspect scope
and coverage before edits and stop if it fails. Record refresh evidence in
`task-log.md` inside the task's single commit. At most three review rounds per task and
one commit after its checks and an independent review pass; push to `wip` after that
commit. Whatever a task removes takes its settings fields (`settings.dart`,
`settings.g.dart`, `settings_provider.dart`), settings pages and search-catalog
entries, strings (all 21 ARB files, then `flutter gen-l10n`), assets, tests, native
code and dependencies with it (FR-012). The updater must keep working (FR-013); its
tests (`update_checker_test`, `apk_downloader_test`, `update_progress_notifications_test`,
`native_file_hash_test`, `preference_blob_store_test`) are never deleted.

Common checks: `flutter gen-l10n` (after ARB changes), `flutter analyze`, full
`flutter test` (baseline 3911 passed, 10 skipped, 0 failed), and the task's searches.
Kotlin changes: compile Kotlin and run `android/app/src/test` unit tests (local build
workarounds never committed). Rust changes: `scripts/check_rust_backend.sh`.

## Implementation

- [x] M004-T001 Remove Last.fm, song recognition, motion artwork and weekly releases
  - Acceptance: FR-001 (motion artwork is NOT done here: moved to T007, see task-log.md), FR-012.
  - Last.fm (dead code): `lib/services/lastfm/*`, `lib/widgets/lastfm_settings.dart`,
    `lastfm*` strings (en, id), tests `lastfm_*_test`, `test/support/lastfm_fakes.dart`.
  - Recognition: `lib/services/music_recognition*.dart`, `lib/models/recognized_song.dart`,
    `lib/providers/music_recognition_provider.dart`, `lib/screens/music_recognition_screen.dart`,
    the mic button and `_openMusicRecognition` in `home_tab.dart`, `recognize*` strings,
    `test/music_recognition_test.dart`, dependency `record`, Android `RECORD_AUDIO`.
    Rust recognition (`providers/recognition.rs`, `mobile/data_jobs.rs`) stays until T010.
  - Weekly releases: `services/weekly_releases.dart`, `providers/weekly_releases_provider.dart`,
    `screens/weekly_releases_screen.dart`, the tile in `favorite_artists_screen.dart`,
    `weeklyReleases*` strings, tests `weekly_releases*_test`; the recap test file
    `listening_and_releases_screen_test.dart` is renamed `listening_recap_screen_test.dart`
    and keeps only its recap tests.
  - Search: `grep -rni "lastfm\|recogni\|weeklyRelease\|package:record" lib test integration_test pubspec.yaml`.
  - Moved to T007 (owner-visible change recorded in task-log.md): motion artwork
    (`motion_artwork_*`, `download_motion_artwork_source`, `player_motion_artwork_provider`,
    `player_artwork_video_provider`, `motion_header_banner`, the video cover in the player and
    album pages, `motionArtworkEnabled`, `motionArtwork*` strings, tests `motion_*`) and
    dependency `video_player`. The download queue calls the motion artwork services, so they
    go with the download engine.

- [ ] M004-T002 Remove online lyrics providers and AI lyrics translation
  - Acceptance: FR-002, FR-012. Embedded and sidecar lyrics, the editor, lyrics
    search, lyric display/preview, pronunciation and lyrics sharing stay.
  - Remove: `lyrics_ai_client.dart`, `lyrics_translation*.dart`,
    `lyrics_language_guess.dart`, `lyrics_translation_provider.dart`,
    `settings/lyrics_ai_translation_page.dart`, `settings/lyrics_provider_priority_page.dart`,
    `lyrics_provider_picker.dart`, `utils/lyrics_provider_names.dart`, the translate parts
    of `lyrics_options_button.dart`; online fetch in `player_metadata_controller.dart`,
    `track_metadata_lyrics.dart`, `lyrics_metadata_helper.dart`; translation watches in
    `player_lyrics_section.dart`, `player_lyric_preview.dart`, `now_playing_screen.dart`;
    PlatformBridge `getLyricsLRC*`, `getLyricsFromProvider`, `getEnabledLyricsProviders`,
    `fetchAndSaveLyrics`, `setLyricsProviders`, `setLyricsFetchOptions`.
  - Settings: `lyricsProviders`, Netease/Musixmatch/Apple options, `lyricsAi*`,
    `lyricsTranslationLanguage`, `lyricsAutoTranslate`, `playerShowTranslation` (if only
    for AI translation) and their setters, the AI API key in secure storage.
  - Strings: `lyricsAi*`, `lyricsTranslate*`, `lyricsProvider*`, `nowPlayingTranslate*`,
    `nowPlaying*Translation`, `nowPlayingSearchLyricsOnline`, `nowPlayingNoLyricsOnline`,
    `downloadNetease*`, `downloadMusixmatch*`, `downloadApple*`. Tests: `lyrics_ai_*`,
    `lyrics_translation_*`, `lyrics_language_guess_test`,
    `native_lyrics_translation_reply_test` (+ fixture); edit tests that fake translation.

- [ ] M004-T003 Remove online metadata: autofill, re-enrich, provider lookups and open-on-platform
  - Acceptance: FR-003, FR-012. Manual tag editing, cover from file, local cover pick
    and the FFmpeg tag writes stay.
  - Remove: `metadata_autofill_service.dart`, `batch_metadata_re_enrich.dart`,
    `utils/re_enrich_*`, `re_enrich_field_dialog.dart`, `re_enrich_review_sheet.dart`,
    `controllers/library_re_enrich_all.dart`, `ffmpeg_reenrich.dart`,
    `track_metadata_edit_autofill.dart`, `metadata_candidate_tile.dart`,
    `open_on_platform_sheet.dart`, `utils/service_track_link.dart`,
    `utils/provider_resource_ids.dart`, `constants/music_services.dart` (if unused after),
    online cover search in `track_metadata_actions.dart`/`track_metadata_screen_cover.dart`;
    re-enrich in `track_metadata_lyrics.dart`, `track_metadata_screen_menu.dart`,
    `local_track_batch_actions.dart`, `library_settings_page.dart`; PlatformBridge
    `getProviderMetadata`, `searchDeezerByISRC`, `getDeezerExtendedMetadata`,
    `convertSpotifyToDeezer`, `fetchMusicBrainzTags`, `getTrackPlatformLinks`,
    `reEnrichFile`, `setMetadataLanguage`, `searchTracksWithMetadataProvider(s)`.
  - Settings: `songLinkRegion` and any metadata-language field; strings `trackReEnrich*`,
    `trackCoverOnline`, `trackFetchOnlineLyrics`, `metadataCandidate*`, `region*`,
    `librarySourceOnline`, open-on strings. Tests: `metadata_autofill_service_test`,
    `batch_metadata_re_enrich_test`, `re_enrich_*`, `library_re_enrich_all_test`,
    `network_batch_re_enrich_test` (or with T006), `open_on_platform_sheet_test`,
    `service_track_link_test`, `provider_resource_ids_test`.
  - Kotlin/Rust handlers for these calls stay until T009/T010 if Dart no longer calls
    them; note them in the log.

- [ ] M004-T004 Remove audio format conversion
  - Acceptance: FR-004, FR-012. FFmpeg stays (research R3).
  - Remove: `ffmpeg_conversion.dart`, `conversion_library_service.dart`,
    `utils/audio_conversion_utils.dart` (move any helper still needed by tag writing),
    `screens/track_metadata_convert.dart`, `widgets/batch_convert_sheet.dart`, convert
    entries in `track_metadata_screen*.dart`, `controllers/batch_track_actions.dart`,
    `queue_tab_batch_actions.dart`, `library_track_menu.dart` (`onConvert`) and every
    selection bar/menu caller, `ensureLyricsMetadataForConversion`; Kotlin
    `NativeFlacConversion.kt` (+ `NativeFlacConversionTest.kt`,
    `NativeConversionOutputTest.kt`) if no non-download caller remains (otherwise T007).
  - Settings: `autoConvert*` and setters. Strings: `trackConvert*`, `selectionConvert*`,
    `selectionBatchConvert*`, `downloadAutoConvert*`, `downloadLossyFormat`. Tests:
    `ffmpeg_conversion_output_test`, `conversion_library_service_test`,
    `audio_conversion_utils_test`, `library_track_conversion_test`; edit menu tests.

- [ ] M004-T005 Remove Cast and the local-network permission
  - Acceptance: FR-005, FR-011 (partial), FR-012.
  - Dart: `cast_output.dart`, `cast_playback_deck.dart`, `music_player_cast.dart`,
    `widgets/cast_device_picker.dart`; cast hooks in `music_player_service.dart`,
    `music_player_runtime.dart`, `playback_deck.dart`/`music_playback_deck.dart`;
    the Cast choice in `audio_output_button.dart`; `local_network_access.dart` and its
    uses (setup nearby step, `download_settings_page.dart`, network storage error paths
    stay compiling until T006/T007 by removing only the permission branch).
  - Kotlin: `CastOutputPlugin.kt`, `SistrumCastDialogs.kt`,
    `SistrumCastOptionsProvider.kt`, `LocalNetworkPlugin.kt`, their registration in
    `SharedEngineChannels.kt`, the Cast meta-data in the manifest,
    `play-services-cast-framework` in `build.gradle.kts`, `ACCESS_LOCAL_NETWORK`.
  - Strings `audioOutputCast*`, `setupNearby*`; tests `cast_playback_deck_test`,
    `local_network_access_test`, `integration_test/android_cast_picker_test.dart`;
    edit `audio_output_button_test`. Rust `CastBridge` export stays until T010.

- [ ] M004-T006 Remove network storage (SMB, WebDAV, Subsonic)
  - Acceptance: FR-006, FR-012.
  - Remove `lib/services/network_*` (storage, Subsonic, upload, download, edit, delete,
    error, SMB client, certificate, library scanners, metadata, playback cache, offline
    saver, download staging, file editor, audio analysis input),
    `library_item_lookup.dart` if only used here, `providers/network_{offline,upload}_provider.dart`,
    `screens/network_storage_screen.dart`, `widgets/network_{offline,upload}_actions.dart`,
    and `network://` handling in the player (`music_player_runtime/service/prefetch/
    gapless/autoplay`, `playback_session_restore`, `playback_metadata`, `playable_media`),
    library (`local_library_provider*`, `library_directory_*`, `library_queue_store`,
    `library_duplicates`, `library_cleanup`, `library_mixes`, `media_library_browser`),
    covers (`cover_store`, `cached_cover_image`, `player_artwork`, `metadata_cover_resources`),
    metadata (`metadata_persistence_service`, `player_lyrics_persistence`,
    `lyrics_editor_sheet`), analysis (`audio_analysis_*`), track pages and menus
    (`track_metadata_*`, `album_screen_library`, `artist_screen_library`,
    `library_tracks_folder_screen`, `queue_tab`, `mornye_player_actions_sheet`,
    `audio_info_sheet`, `playback_source_label`), settings (`settings_tab` entry,
    `files_settings_page` NAS folder).
  - Settings `networkDownloadFolder`, `networkDownloadLabel`, `networkCompatibilityMode`,
    `allowLocalNetwork`; secure-storage connection secrets. Strings `network*`,
    `subsonic*`, `offline*`, `settingsDownloadNetwork*`. Tests: `network_*`,
    `subsonic_*`, `collection_network_cover_test`, `network_cover_display_test`,
    `track_metadata_network_analysis_test`, `network_scan_*`, fixtures
    `test/fixtures/network_tls/*`, support files, `integration_test/network_*`,
    `subsonic_playback_test`; edit player/cover/library tests. Dependencies `dart_smb2`,
    `synchronized`, `html`; `assets/licenses/libsmb2.txt` and its registration in
    `main.dart`; `NETWORK_STORAGE.md`; `scripts/smb{2,3}_fixture_server.py`.
  - Native: Kotlin `RemoteSafPlaybackTest` only if it tests network providers; Rust
    exports `network_tags.rs`, `native_listing.rs` stay until T010.

- [ ] M004-T007 Remove the download engine, Wishlist, CSV import and reorganize
  - Acceptance: FR-007, FR-012. Download history reading stays until T008.
  - Motion artwork (moved from T001): `motion_artwork_{store,proxy,download}.dart`,
    `download_motion_artwork_source.dart`, `player_motion_artwork_provider.dart`,
    `player_artwork_video_provider.dart`, `motion_header_banner.dart`, its uses in
    `now_playing_screen.dart`, `mini_player.dart`, `mornye_player_artwork.dart`,
    `album_screen*.dart`, `track_metadata_edit_sheet.dart`, `playback_settings_page.dart`,
    `cache_management_page.dart`, `motionArtwork*` strings, `motionArtworkEnabled`,
    tests `motion_*`, `download_motion_artwork_source_test`, `re_enrich_motion_artwork_test`,
    dependency `video_player` (+ dev `video_player_platform_interface`).
  - Dart: `providers/download_queue_*`, `download_queue_state.dart`,
    `download_queue_policy.dart`, `download_progress.dart`, `models/download_item*.dart`,
    `download_result.dart`, `services/download_*` except history-related readers kept
    for T008, `native_download_worker_monitor.dart`, `native_download_history.dart`,
    `hires_check_service.dart`, `widgets/hires_check_card.dart` (if only for downloads;
    the file Hi-Res check on the track page stays only if it is offline),
    `csv_import_service.dart`, `offline_saver.dart`, `ffmpeg_decryption.dart`,
    `connectivity_monitor.dart`, `retired_explore_cache.dart`,
    `utils/download_error_type.dart`, `widgets/download_service_picker.dart`,
    `download_queue_feedback.dart`, `platform_bridge/bridge_native_worker.dart`,
    `settings/download_settings_page*.dart`, `queue_tab_download_*`, the queue section of
    `queue_tab*.dart`, Home CSV import (`home_tab_import.dart`, button), Wishlist
    (`LibraryTracksFolderMode.wishlist`, collections table/codec/backup entries,
    `collection_track_actions.dart` download action, `queue_tab_selection.dart`
    batch), reorganize (`download_queue_provider_reorganize.dart`, `library_reorganizer.dart`,
    `files_settings_page_reorganize.dart`), download notifications in
    `notification_service.dart` (keep update and scan), queue tables in
    `app_state_database.dart`, PlatformBridge download group, setup download-folder
    step and tutorial download steps; write paths of `download_history_provider.dart`
    (`addToHistory`, `saveOfflineCopy`, `adoptNativeHistoryItem`).
  - Kotlin: `DownloadService*.kt`, `NativeDownloadFinalizer.kt`, `NativeFinalizer*.kt`,
    `NativeFinalizationPolicy.kt`, `NativeHistoryStore.kt`, `NativeReplayGainPolicy.kt`,
    `NativeWorkerPolicy.kt`, `NativeFlacConversion.kt` (if left by T004); split
    `SafDownloadHandler.kt` into a `SafDocumentWriter` with the shared SAF writers used by
    `MainActivitySafCalls`, `MainActivitySafIo`, `SafFileInspection`; remove download
    methods from `MainActivity.kt`, `MainActivityServiceCalls.kt`,
    `MainActivityProgressStreams.kt` (download stream), `MainActivityMetadataCalls.kt`
    (`downloadCoverToFile`, `runPostProcessingV2`), `MainActivitySafIo.kt`
    (`runPostProcessingSafV2`), `CoreBackend.kt`; manifest `DownloadService`,
    `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`; `ffmpeg-kit-audio` compileOnly;
    proguard `DownloadService` rule; Kotlin tests listed in the native inventory
    (`DownloadServiceStartupTest`, `NativeDownloadContainerTest`,
    `NativeFinalizationPolicy*Test`, `CoreFFmpegExecutionTest`,
    `DownloadProgressSequenceTest`, `NativeReplayGainPolicyTest`,
    `NativeWorkerPolicyTest`, `.tsv` fixtures); `MainActivityRoutingTest` edited.
  - Settings: download fields listed in the Dart inventory (`defaultService`,
    `audioQuality`, filename formats, folders, `concurrentDownloads`, network mode,
    worker, embed options, dedup/variants, `saveDownloadHistory`, folder-structure
    flags, `folderOrganization`, `folderPathTemplate`); `storageMode`,
    `downloadDirectory`, `downloadTreeUri`, `useAllFilesAccess` stay only if T008 still
    needs them. Strings `download*` (except kept), `notifDownload*`, `bulkDownload*`,
    `concurrentDownloads*`, `hiResCheck*` (download use), `csvImport*`,
    `homeImportCsvTooltip`, `homeSkipAlreadyDownloaded`, `reorganize*`, Wishlist strings,
    `tutorialDownload*`, setup download strings. Tests: about 60 unit and 15
    integration tests (`download_*`, `queue_*`, `csv_import_service_test`,
    `hires_*`, `connectivity_monitor_test`, `retired_explore_cache_test`,
    `library_reorganize*`, `mornye_download_picker_test`), support baselines; edit
    fixture users. Dependency `connectivity_plus`.

- [ ] M004-T008 Remove the download history source so the library reads scanned files only
  - Acceptance: FR-008, FR-012 (research R2). No migration.
  - Remove `history_database.dart`, `history_schema.dart`, `history_row_mapper.dart`,
    `history_maintenance.dart`, `providers/download_history_provider*.dart`,
    `models/download_history.dart`, `download_file_recovery.dart`,
    `downloaded_embedded_cover_resolver.dart` (keep embedded-cover extraction for
    scanned files if they share it), `library_shadow_sql.dart`,
    `upsertBatchExcludingHistory`, the `history_db` attach in `library_database.dart`,
    history branches in `library_queue_store.dart`, `library_search.dart`,
    `library_lyrics_search.dart`, `library_playlist_search.dart`,
    `library_detail_data.dart`, `library_tag_credits.dart` (`h` source),
    `media_library_browser.dart`, `library_native_adapter.dart`,
    `library_ingestion_store.dart`, `track_file_resolver.dart`, `music_player_runtime.dart`,
    `background_favorites.dart`, `library_cleanup.dart`, `backup_service.dart`,
    `sqlite_helpers.dart`; `LibraryItemSource.downloaded` and `UnifiedLibraryItem.
    historyItem` with every caller (about 38 files: queue tab, album/artist/playlist
    library pages, track page, local playback, batch actions, duplicates, mixes,
    statistics, Home, settings pages, Now Playing); downloaded-album strings
    (`downloadedAlbum*`), the folder-access-lost repair if only for downloads, settings
    `storageMode`, `downloadDirectory`, `downloadTreeUri`, `useAllFilesAccess` if
    unused. `cached_network_image`/`flutter_cache_manager` are removed if no URL cover
    remains (re-check). Tests: every history fixture user is moved to local-library
    fixtures or removed when it only tested the downloaded source.

- [ ] M004-T009 Remove the extension system, repository tab and provider priorities
  - Acceptance: FR-009, FR-011 (partial), FR-012.
  - Dart: `models/extension.dart`, `providers/extension_provider*.dart`,
    `providers/repo_provider.dart`, `screens/repo_tab.dart`, `screens/repo/*`,
    `settings/extensions_page*.dart`, `extension_detail_page*.dart`,
    `download_fallback_extensions_page.dart` (if left), `provider_priority*_page.dart`,
    `metadata_provider_priority_page.dart`, `priority_settings_scaffold.dart`,
    `reorderable_priority_item.dart`, `extension_storage_service.dart`,
    `controllers/extension_{action_forms,auth_launcher}.dart`, `widgets/extension_*.dart`;
    `main.dart` extension init and auth callbacks; `main_shell.dart` repository tab,
    badge and `showExtensionStore`; `shell_navigation_service.dart` `ShellTab.repository`;
    bottom bar destination; Settings Extensions entry; `app_settings_page.dart` store
    switch; backup extension section; search catalog; tutorial extensions step;
    PlatformBridge extension, repo and provider-priority groups and
    `bridge_lookup_cache.dart` provider cache. Settings `useExtensionProviders`,
    `downloadFallbackExtensionIds`, `searchProvider`, `showExtensionStore`,
    `extensionVerificationBrowserMode` and the retired-provider migrations. Strings
    `extension*`, `extensions*`, `store*`, `provider*`, `metadataProvider*`,
    `tutorialExtensions*`, `settingsExtensions*`. Tests listed in the Dart inventory;
    `integration_test/extension_storage_recovery_test.dart`.
  - Kotlin: `ExtensionAuthCallbacks.kt`, `MainActivityExtensionIntents.kt`, the
    `sistrum://callback` and `sistrum://spotify-callback` intent filters, extension
    tests (`ExtensionAuthCallbacksTest`, `ExtensionInputValidationTest`,
    `ExtensionMemoryTest`, `ExtensionMetadataBridgeTest`, `ExtensionMimeTablesTest`);
    `RustCoreBackendApplication.kt` keeps only the data-job dispatch;
    `SelectedCoreBackend.kt` drops extension/lyrics/download glue that has no caller.
  - `flutter_secure_storage` is removed if no user remains.

- [ ] M004-T010 Slim the Rust backend to the offline library, tag and audio parts
  - Acceptance: FR-010, FR-012 (research R4).
  - Move `extensions/src/backend/{library.rs, library/scan.rs, tags.rs}` and the
    file-cover extraction and library cover cache of `cover.rs` into `mobile` (or
    `core`); slim the UniFFI manager to scan, progress, pause/resume/cancel, cover cache
    directory, file/audio/descriptor metadata, CUE, Hi-Res check, tag writes, cover
    extract, filename helpers still used, data jobs, cancellation and USB audio; delete
    exports `ExtensionEnvironment`, `JsExtension*`, `DownloadDirectoryScope`,
    `ExtensionRepository`, `FfmpegCommands`, `DownloadState`, `DownloadProgressSubscription`,
    `CastBridge`, `LyricsRequest`, `LogBuffer` (if unused), `native_recognition.rs`,
    `network_tags.rs`, `native_listing.rs`, `native_lyrics_translation.rs`,
    `native_id3.rs` (if only the FFmpeg download fallback uses it); remove data-job
    operations `matches_network_upload`, `network_metadata`, `promote_network_cover`,
    `parse_network_listing`, `parse_lyrics_translation_reply`, `recognize_song`.
  - Delete crates `extensions`, `providers`, `network`, `cast`, `vendor/cap-primitives-4.0.3`;
    prune `core` online modules (research R4); drop unused workspace dependencies;
    regenerate `Cargo.lock`; update `rust_backend/README.md`, `NOTICE`,
    `vendor/README.md`, `scripts/build_rust_backend.sh` (QuickJS-only env),
    `scripts/check_android_statx.sh` (retarget to `mobile` or delete), the
    `buildRustBackend` input globs in `build.gradle.kts` (`*.js`, `*.pem`).
  - Kotlin: `SelectedCoreBackend.kt`, `CoreBackend.kt`, `NativeDataJobs.kt` follow the new
    bindings; `NativeDataJobsTest.kt` edited.
  - Checks: `scripts/check_rust_backend.sh`, Kotlin compile and unit tests, Dart tests
    that use data-job fixtures (`native_*_test`).

- [ ] M004-T011 Android, pubspec, assets, CI and docs cleanup for an offline app with the updater
  - Acceptance: FR-011, FR-012, FR-013, SC-004.
  - Manifest: only the updater's network pieces remain (INTERNET,
    REQUEST_INSTALL_PACKAGES, FileProvider, `BackgroundWorkService`, notifications);
    drop the loopback exception in `network_security_config.xml`; check
    `backup_rules*.xml` for removed paths; `proguard-rules.pro`; `android/README.md`
    channel contract.
  - pubspec: remove dependencies left without users (research R6), licence assets
    and registrations, README feature art (`assets/readme/features/feature-extensions-*`,
    `feature-network-*`), `README.md` feature list; `third_party/audioplayers_android`
    override if its only reason was network providers.
  - CI: `.github/workflows/{ci,release,workflow-integrity}.yml` steps for removed
    crates, scripts and tests.
  - Final sweeps: settings model/provider/catalog leftovers, l10n keys without a Dart
    user (all 21 ARB files), tutorial and setup copy, `scripts/check_backend_apk.py`
    expectations.
  - Searches (SC-004): `grep -rniE "extension|lastfm|subsonic|smb|webdav|cast|download(Queue|Service|Item)|convert|recogni|deezer|musicbrainz|lyrics.?ai|wishlist" lib test integration_test android rust_backend pubspec.yaml assets README.md scripts .github`
    reviewed by hand (expected hits only: updater download, generic words).

## Final

- [ ] M004-T012 [FINAL] Cumulative review of the offline-only app
  - Acceptance: every FR and SC of spec.md against the baseline `3cb6847`.
  - Map refresh; full checks (gen-l10n, analyze, full flutter test, Rust check script,
    Kotlin compile and unit tests); cumulative review of the diff and the
    Dart/Kotlin/Rust boundaries (MethodChannel names, UniFFI exports, manifest);
    updater review; the SC-004 searches. Release APK build and size comparison with
    the 75.9 MB baseline only when the owner authorizes the build.
