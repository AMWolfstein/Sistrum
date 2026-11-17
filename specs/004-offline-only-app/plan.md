# Implementation Plan: Offline-only app

**Branch**: `wip` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

## Summary

Remove every online feature, network storage, Cast and audio format conversion from
Dart, Kotlin and Rust, keeping only the app updater online. The library becomes
scanned folders only (no download history source). Eleven removal tasks, each leaving
a green build, then a cumulative final review.

## Technical Context

**Language/Version**: Dart 3.13.7, Flutter 3.47.8; Kotlin (Android Gradle); Rust 1.99
(edition 2024) via UniFFI; C++ (FLAC/Oboe/WavPack decode, untouched)
**Primary Dependencies**: flutter_riverpod, sqflite, ffmpeg_kit_flutter_new_audio
(kept), audio_service, http (updater only)
**Storage**: library database, collections database, listening statistics, settings;
`history.db` is removed (T008)
**Testing**: `flutter gen-l10n`, `flutter analyze`, full `flutter test`; for Kotlin
tasks `./gradlew :app:compileReleaseKotlin` and the unit tests in `android/app/src/test`
(`testReleaseUnitTest`) when the toolchain is available; for Rust `cargo check`,
`cargo clippy`, `cargo test` (`scripts/check_rust_backend.sh`)
**Target Platform**: Android arm64
**Constraints**: no installed users, so no data migration; the updater unchanged;
Mornye look unchanged.

## Constitution Check

- I. Preserve behaviour: offline features and the updater keep working; only the
  requested features go. PASS.
- II. Plan substantial work: multi-language feature removal, this plan. PASS.
- III. One task, one reviewed commit: twelve sequential tasks. PASS.
- IV. Honest verification: baseline recorded; device behaviour not claimed. PASS.
- V. Simple tooling: dependencies only removed. PASS.

## Repository evidence

- **Baseline**: `wip` at `3cb6847`, clean tree. Last full run on this tree: `flutter
  analyze` no issues; full `flutter test` 3911 passed, 10 skipped, 0 failed (JSON count
  excluding hidden and `loading` entries). Release APK from this commit: 75.9 MB
  (75,925,628 bytes), arm64-v8a, `libsistrum_mobile.so` 18.6 MB and FFmpeg libraries
  about 19.5 MB inside it.
- **Map**: `code_tools.py update` succeeded: 33494 symbols, 1675 sources, 0 partial,
  0 unindexed, 0 rejected cross-language calls. Two read-only inventories (Dart side;
  Android/Rust side) were made from the map, imports and greps; key claims were
  re-read by hand (workspace members, extensions backend files, updater imports).
- **Updater (kept)**: `lib/services/update_checker.dart` (http, shared_preferences,
  app_info, preference_blob_store, logger), `apk_downloader.dart` (http, open_filex,
  path_provider, native_file_hash), `update_progress_notifications.dart`,
  `lib/widgets/update_dialog.dart`, call site `main_shell_startup_prompts.dart`
  `_checkForUpdates`, settings `checkForUpdates`/`updateChannel` in
  `app_settings_page.dart`; natively INTERNET, REQUEST_INSTALL_PACKAGES, FileProvider
  `${applicationId}.fileProvider` with `res/xml/file_paths.xml`, `BackgroundWorkService`
  (kind `app_update`), notifications. `PlatformBridge.runNativeDataJob` (file hash) and
  `start/update/stopBackgroundWork` stay.
- **Shared hubs** (edited by several tasks): `lib/services/platform_bridge.dart`
  (2382 lines, extension/download/lyrics/metadata groups), `lib/providers/
  settings_provider.dart`, `lib/models/settings.dart` (+ hand-edited `.g.dart`),
  `lib/main.dart`, `lib/screens/main_shell.dart`, `lib/utils/settings_search_catalog.dart`,
  `lib/screens/settings/*`, `lib/screens/track_metadata_*`, `lib/services/
  notification_service.dart`, `lib/services/backup_service.dart`,
  `lib/services/music_player_*`, `android/.../MainActivity*.kt`,
  `RustCoreBackendApplication.kt`, `SelectedCoreBackend.kt`, `CoreBackend.kt`.
- **Feature inventories**: see each task in tasks.md (paths come from the inventories);
  sizes of the largest removals: download queue providers about 9,600 lines,
  network storage services about 7,000, extension Dart UI/providers about 7,000,
  Kotlin download/finalizer files about 6,100, Rust extensions 28,200 (of which about
  3,500 move), providers 9,776, network 2,990, cast 2,065.
- **Rust**: workspace members `core, network, providers, extensions, mobile, cast,
  bindgen`; the offline library scan, tag writes and cover extraction live in
  `crates/extensions/src/backend/{library.rs, library/scan.rs, tags.rs, cover.rs}` and
  must move before the crate goes (R4). Built by `scripts/build_rust_backend.sh` from
  the Gradle task `buildRustBackend`; APK audited by `scripts/check_backend_apk.py`.
- **Limitations**: the inventories may miss callers; each task refreshes the map, greps
  for the removed names and relies on analyze/tests/compile to find the rest. Kotlin
  and Rust builds need the local workarounds used for APK builds in this environment
  (Maven mirror, git-cloned native sources), which are never committed. Device
  behaviour (Android Auto, USB, updater install) is not tested here.

## Phases

1. Leaf online features (T001-T004): Last.fm, recognition, motion artwork, weekly
   releases; online lyrics and AI translation; online metadata; conversion.
2. Network (T005-T006): Cast and the local-network permission; network storage.
3. Downloads (T007-T008): download engine, Wishlist, CSV import, reorganize; then the
   history source.
4. Extensions (T009): runtime bridge, repository tab, provider priorities, auth links.
5. Native (T010-T011): Rust slimming; Android manifest/Gradle/proguard, pubspec,
   assets, licences, CI, README and docs.
6. Final (T012): cumulative review, full checks, APK build and size when the owner
   authorizes the build.

## Validation per task

Common: `flutter gen-l10n` after ARB changes (all 21 ARB files), `flutter analyze`,
full `flutter test` (0 failed), and the task's removal greps. Kotlin-touching tasks
also compile Kotlin and run its unit tests; Rust-touching tasks run
`scripts/check_rust_backend.sh`. A release APK is built only in T012 when authorized.
