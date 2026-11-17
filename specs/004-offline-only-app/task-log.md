# M004 task log

Baseline: `3cb6847` on `wip` (see plan.md, Repository evidence).

Baseline checks on `3cb6847`: `flutter analyze` no issues; full `flutter test` 3911
passed, 10 skipped, 0 failed (JSON count excluding hidden and `loading` entries).
Release APK 75.9 MB (75,925,628 bytes, arm64-v8a).

Planning: map refresh 33494 symbols, 1675 sources, 0 partial, 0 unindexed. Owner
answers: no existing users (no data to keep, the library is offline only), Cast
removed, Wishlist removed, local FFmpeg features (tag editing, ReplayGain, CUE split)
kept. Planning only; implementation not authorized by the planning request.

## M004-T001 Remove Last.fm, song recognition and weekly releases

Map refresh first: 33494 symbols, 1675 sources, 0 partial, 0 unindexed.

Changed: deleted `lib/services/lastfm/*`, `lib/widgets/lastfm_settings.dart`, Last.fm tests and fakes (`test/support/lastfm_fakes.dart`), recognition (`music_recognition*.dart`, `recognized_song.dart`, its provider, screen, test), weekly releases (service, provider, screen, two tests) and the tile in `favorite_artists_screen.dart`; removed the mic button and `_openMusicRecognition` from `home_tab.dart`; removed `record` from `pubspec.yaml` (and `pubspec.lock` via `pub get --offline`) and `RECORD_AUDIO` from the manifest; removed `lastfm*` keys from `app_en.arb` and `app_id.arb`, `recognize*` keys from the 17 ARB files that had them, `weeklyRelease*` keys from the ARB files that had them; renamed `listening_and_releases_screen_test.dart` to `listening_recap_screen_test.dart` and kept its two recap tests; removed the Last.fm fakes row from `test/README.md`.

Scope change (recorded here, tasks.md updated): motion artwork is not in T001. The download queue calls the motion artwork services, so they are removed with the download engine (T007), together with `video_player`.

Left for later tasks: `RECORD_AUDIO`'s `tools:node="remove"` guard for FOREGROUND_SERVICE_MICROPHONE in the manifest (T011); Rust recognition (`providers/recognition.rs`, `mobile/data_jobs.rs`) (T010).

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; full `flutter test` 3662 passed, 8 skipped, 0 failed (JSON count excluding hidden and `loading` entries; baseline was 3911 passed, 10 skipped). The drop is the removed tests: Last.fm, recognition and weekly tests (loop-generated cases are counted at run time, so the static count of test declarations is lower than the run count).

## M004-T002 Remove online lyrics providers and AI lyrics translation

Map refresh first: 32978 symbols, 1653 sources, 0 partial, 0 unindexed.

Removed: AI translation (`lyrics_ai_client.dart`, `lyrics_translation*.dart`, `lyrics_language_guess.dart`, `lyrics_translation_provider.dart`, its settings page, its tests and the native reply test); the lyric provider priority page and picker, provider names; online lyrics search in the player (`PlayerMetadataController.searchOnline`, its state and fetcher parameter) and in the track metadata card (fetch replaced by a local read of the file's embedded lyrics); the "find lyrics online" button in the edit sheet; the Netease, Musixmatch and Apple options (settings fields, setters, search entries); the AI settings (fields, setters, search entry); 97 ARB keys that no Dart code uses any more, in all 21 ARB files; the dead `_saveLyrics` fetch was replaced by a local save of the shown lyrics to a sidecar .lrc.

Kept on purpose (owner: embedded and local lyrics stay): the translation lines that a lyric file already carries (`x-translation`), the pronunciation toggle, the lyric preview, the lyrics editor, the "lyrics source" credit, the lyrics mode setting (embedded, sidecar, both) and the embed-lyrics switch.

Deferred, with the reason:
- `lyricsProviders` setting and its backend sync: read by the extension system, removed with T009.
- PlatformBridge lyric functions (`getLyricsLRC*`, `getLyricsFromProvider`, `getEnabledLyricsProviders`, `fetchAndSaveLyrics`, `setLyricsProviders`): still called by download, metadata autofill and network paths, removed with T003 and T007.
- `initExtensionSystem` lyric arguments and the Kotlin and Rust lyric handlers: T009 and T010.
- Integration tests that pass extension or download fixtures: T007 and T009.

Changed tests: `track_metadata_lyrics_test` lost its embed-of-fetched-lyrics group (the embed button only shows for lyrics not in the file now; the group tested an online fetch that no longer exists); `player_lyrics_empty_state_test` now checks the no-lyrics message instead of the search button; `player_metadata_controller_test` lost the online search group; `app_settings_model_test` and `extension_initialization_test` lost the removed lyric fields. `native_backend_migration_test` (integration) lost its translation test and keeps the file-hash test.

Generated: `settings.g.dart` regenerated with build_runner (only the removed fields changed).

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; full `flutter test` 3707 passed, 10 skipped, 0 failed.

## M004-T003 Remove online metadata: autofill, re-enrich, provider lookups and open-on-platform

Map refresh first: 32369 symbols, 1633 sources, 0 partial, 0 unindexed.

Removed: the metadata autofill service, its part file and candidate tile in the editor (with the MusicBrainz button and the autofill section); the single-track and whole-library re-enrich (`batch_metadata_re_enrich`, `ffmpeg_reenrich`, `re_enrich_*` policy, result, field dialog and review sheet, `library_re_enrich_all`, the re-enrich-all setting row, and the Re-enrich menu entry); the "Open on" platform sheet, `service_track_link` and their menu entry and header button; the provider-ID rows in the metadata cards; the song-link and language-sync calls (`PlatformBridge` `fetchMusicBrainzTags`, `getTrackPlatformLinks`, `setMetadataLanguage`, `reEnrichFile`); the About page's MusicBrainz credit; the online lookup fallback in `ensureLyricsMetadataForConversion` (a sidecar .lrc is still read); the editor's lookup-generation and autofill-field state in `MetadataEditorController`; 62 ARB keys no Dart code uses any more; tests for those features (`batch_metadata_re_enrich`, `re_enrich_release_policy`, `re_enrich_result`, `re_enrich_feedback`, `library_re_enrich_all`, `metadata_autofill_service`, `open_on_platform_sheet`, `service_track_link`, `track_metadata_cover_search`, `local_track_batch_actions`, `downloaded_album_reenrich`, `network_batch_re_enrich`, and the re-enrich case in `mornye_library_actions`). The test for the main shell no longer asserts the language call.

Deferred, with the reason:
- `getProviderMetadata`, `searchDeezerByISRC`, `getDeezerExtendedMetadata`, `convertSpotifyToDeezer`, `searchTracksWithMetadataProvider`: still called by the download pipeline and CSV import (T007).
- `provider_resource_ids.dart`, `MusicServices`, the artist page import: used by motion artwork and the download source (T007).
- `songLinkRegion` setting and its download settings row: T007.
- Metadata provider priority settings and their strings (`metadataProvider*`, `sectionMetadataProviders`, `snackbarMetadataProviderSaved`, `extensionMetadataProvider`): T009.
- Kotlin `reEnrichFile`, `fetchMusicBrainzTags`, `getTrackPlatformLinks`, `setMetadataLanguage` handlers and the Rust providers: T009 and T010.
- `test/support/re_enrich_settings.dart` keeps its name (it is a generic settings fake used by other tests); renamed in T011.
- `re_enrich_motion_artwork_test` and the motion-artwork save helper: T007 (motion artwork).

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; full `flutter test` 3630 passed, 10 skipped, 0 failed.

## M004-T004 Remove audio format conversion

Map refresh first: 31941 symbols, 1609 sources, 0 partial, 0 unindexed.

Removed (library and track pages): the batch conversion sheet and its runner (`BatchConvertSheet`, `showBatchConvertSheet`, `convertLibraryTrack`, `ConversionLibraryService`), the single-track convert sheet (`track_metadata_convert.dart`; its ReplayGain and CUE-split code is kept in `track_metadata_cue_replaygain.dart`), the Convert entries in the track page menu, the library track menu and the selection bars (`onConvert` on `LibraryTrackSelectionBar`, `LibraryTrackMenu` and every caller), the queue's batch convert method and the mix screen's convert method; 25 ARB keys (`trackConvert*` and `selectionConvert*`/`selectionBatchConvert*` that no code uses) in all ARB files; tests `conversion_library_service_test`, `library_track_conversion_test`, `batch_track_actions_test` (conversion only); the convert cases in `mornye_library_actions_test`, and the convert arguments and checks in the selection and menu tests.

Deferred, with the reason (these are download-side, removed with T007):
- `ffmpeg_conversion.dart` and `audio_conversion_utils.dart`: used by the download finalizer and download settings.
- `NativeFlacConversion.kt` (and its unit test): used by the native finalizer of downloads.
- The `autoConvert*` settings, the `downloadAutoConvert*` and `downloadLossyFormat` strings, and the download settings rows: T007.
- `trackConvertBitrate`, `trackConvertOriginal`, the dither and resampler strings and the quality label strings: used by the remaining conversion helpers, removed with T007.

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; full `flutter test` 3621 passed, 10 skipped, 0 failed.

## M004-T005 Remove Cast and the local-network permission

Map refresh first: 31840 symbols, 1604 sources, 0 partial, 0 unindexed.

Removed: the Cast output (`cast_output.dart`, `cast_playback_deck.dart`, `music_player_cast.dart`, `cast_device_picker.dart`, the integration test for the Cast picker, and the Cast playback test); the playback engine's Cast branches (the `castOutput` constructor argument, the `_MusicCast` field and every `_cast.active` condition, which is always false now; the audio-session interruption and noisy handlers keep their behaviour); `createCastDeck` in the runtime; the Android Cast plugin and its dialogs and options provider, its registration, the Cast manifest entry and `play-services-cast-framework`; the audio output button now opens the system picker directly (its sheet with the Cast choice is gone); the setup wizard's Nearby devices step (its state, request, validation and draft fields); 11 ARB keys (`audioOutputCast*`, `audioOutputStopCasting`, `audioOutputSystem`, `setupNearby*`, `permissionNearbyDevices`) in all ARB files; the Cast playback and the picker tests, and the output button test is rewritten for the system picker only; the paused-song-on-TV test.

The remote-volume callbacks of the audio handler (`androidSetRemoteVolume`, `androidAdjustRemoteVolume`) are kept as no-ops: they are inherited from the audio service and only ever controlled a Cast receiver. The phone's volume keys keep working through the system.

Deferred, with the reason:
- `LocalNetworkAccess` (Dart), `LocalNetworkPlugin.kt` and the `ACCESS_LOCAL_NETWORK` permission: still used by network storage and the download settings page; removed with T006 and T007.
- The Rust `cast` crate and the `CastBridge` export: removed with T010.
- `AudioRouteInfo.kt` labels the remote-submix route as "cast": a route classification, not the Cast feature; not changed.

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; full `flutter test` 3612 passed, 10 skipped, 0 failed after setup_screen_recovery_test moved to 5 steps (3/5, 4/5) on Android 17.

## M004-T006 Remove network storage (SMB, WebDAV, Subsonic)

Removed: the network storage services and their helpers (`lib/services/network_*`, `local_network_access.dart`), the network providers, screen and action widgets, the offline and upload providers, and the player and library branches for `network://` sources (playback, prefetch, cover fetch, tag reads, metadata saves, audio analysis, ISRC duplicates, directory roots, queue filters, delete paths). `CoverStore` is now a local-only resolver (`file()`, `localCoverPath`, `durableCoverReference`); the network fetch, index, trim and adopt machinery is gone. The track page menu lost save-offline, upload and delete-server-copies. Setup and settings lost the NAS folder entries and the network storage page. The Android `LocalNetworkPlugin.kt`, its registration, `ACCESS_LOCAL_NETWORK`, and the `libsmb2` license entry and asset are removed. Packages `dart_smb2`, `synchronized` and `html` are removed from `pubspec.yaml` (no import left). The SMB fixture scripts, `NETWORK_STORAGE.md` and the SMB rows of `test/README.md` are removed. 82 unreferenced `network*`, `subsonic*`, `offline*` and related ARB keys are removed in all ARB files. Network-only tests are removed (SMB listing, NAS scan and reconnect, cover store, network cover, subsonic and offline-copy behaviour); surviving tests are updated for the new behaviour (download failure counts, playlist deletion, directory index, ISRC duplicates, schema v19 rows).

Deferred, with the reason:
- `networkDownloadFolder`, `networkDownloadLabel` on `AppSettings` and the `DownloadItem` field, and the download queue's staging reads: download engine, T007.
- `networkCompatibilityMode` and `allowLocalNetwork`: still synced to the backend as extension HTTP options (`setNetworkCompatibilityOptions`, `setAllowPrivateNetwork`), removed with the extension system and the Rust network crate, T009 and T010.
- `originFilePath`, `originAvailable`, `OfflineCopy` fields in the history model and the `originSource` field of `PlayableMedia`: download history, T008.
- `test/fixtures/network_tls` and `motion_artwork_proxy_test`: motion artwork, T007.
- `library_schema.dart` migration columns (`network_file_version`) and `library_row_mapper` mapping: schema history, removed with the schema reset in T011.
- Rust `network_tags.rs`, `native_listing.rs`, and the `network` crate: T010.
- Existing user databases may still hold `network://` rows and `network_file_version` values. The guards that hid them are gone (availability probe, file existence check, queue filters), so such rows read as missing. Cleanup is left to the T011 schema reset (no existing users).
- The retry gate in `music_player_failures.dart` no longer excludes local files with a download-history origin (`originSource`). Retrying those is harmless; the field itself goes with T008.
- Kotlin unit test `CoverProviderFilesTest` updated for the two remaining folders. It could not run in this container (Gradle plugins not cached offline, no Android SDK), so it is unverified here and must run in CI.

Checks: `flutter gen-l10n` exit 0; `flutter analyze` no issues; `flutter pub get --offline` ok; full `flutter test` 3183 passed, 0 failed (JSON count excluding hidden and `loading` entries).
