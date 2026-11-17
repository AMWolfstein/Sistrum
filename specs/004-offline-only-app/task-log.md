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
