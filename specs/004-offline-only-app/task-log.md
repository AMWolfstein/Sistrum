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
