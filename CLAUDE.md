# Sistrum — notes for Claude Code

Android music player. Hard fork of misa198/airmedy, local-library only (desktop
sync removed). Repository: AMWolfstein/Sistrum, main branch `main`. GPL-3.0.
Kotlin + Jetpack Compose (Compose Multiplatform artifacts).

## Modules
- `androidApp` — Compose UI, ViewModels, player, MediaStore scan, Room.
- `sharedLogic` — Kotlin Multiplatform module, Android-only target (iOS removed).
- Flavors `dev` / `prod`. Dev application id: `me.misa198.airmedy.dev`.
- minSdk 31, targetSdk 36, JDK 21.

## Build and test
- `./gradlew :androidApp:assembleDevDebug`
- `./gradlew :androidApp:assembleDevRelease` (unsigned unless MOBILE_KEYSTORE_* env vars are set;
  sign locally with zipalign + apksigner and `~/.android/debug.keystore`; don't edit signingConfigs)
- `./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest`
- `./gradlew :androidApp:lintDevDebug`
- `./gradlew :androidApp:assembleDevDebugAndroidTest`, then install both APKs with
  `adb install -r` and run classes one at a time:
  `adb shell am instrument -w -r -e class <Class> me.misa198.airmedy.dev.test/androidx.test.runner.AndroidJUnitRunner`
  Keep the screen awake (`adb shell svc power stayon usb`). Don't use Gradle
  connected* tasks: they uninstall the app afterwards.

## Local, untracked build inputs
- `local.properties`: `sdk.dir=...` and `python3=<venv python>` (fonttools + brotli).
  The icon font `androidApp/src/main/res/font/material_symbols_rounded.ttf` is generated
  at build time by `tools/font-subset/subset_font.py` from the glyphs used in
  `ui/components/MaterialSymbols.kt`. Icons are font glyphs, not vector drawables.
- FFmpeg shared libraries in `androidApp/src/main/jniLibs/` are not in git. If a build
  fails with "FFmpeg artifacts are missing", run `bash scripts/build-ffmpeg-android.sh arm64-v8a`.
- The repo has no .gitignore; build outputs are ignored through `.git/info/exclude`.
  Stage files by explicit path only.

## Playback
- Still on the native FFmpeg engine (`player/FfmpegDecoder.kt`, `androidApp/src/main/cpp`,
  NDK/CMake). A later phase replaces it with Media3/ExoPlayer. Until then, don't change
  `player/` or native code except for compile fixes.

## Library scan
- MediaStore-based: `sync/MediaStoreLibraryScanner.kt`, `sync/EmbeddedTagReader.kt`.
  Room database in `sync/SyncDatabase.kt`.
- Bump `CurrentMetadataSchemaVersion` whenever parsing behavior changes for
  already-scanned files.
- EmbeddedTagReader opens each file once per tag read (large scan speedup). Keep it that way.
- For untagged files MediaStore reports the folder name ("Music") as album; intentional.

## Known state
- Instrumented suite has a set of test-side failures (missing menu host, stale text
  expectations, tests that set content twice, chart library crashing on empty data).
  Only fix newly failing tests.
- Lint errors are MissingTranslation (non-English locales lag behind) and
  LocalContextGetResourceValueCall.
- Cast / output-switcher button is hidden below Android 14 on purpose.
- Lock screen can show "Unknown artist" for a track (PlaybackService metadata);
  deferred to the Media3 phase.

## Localization
- `values/strings.xml` is the source. `values-ar` must stay complete
  (`ArabicTranslationCompletenessTest`); the other locales are allowed to lag.
- Counts use `<plurals>`; Arabic needs zero/one/two/few/many/other.
- Numbers use Western digits in every locale: `MainActivity` wraps its context with
  `-u-nu-latn`, and non-resource formatting goes through `formatDisplay()` (ui/components/LatinDigits.kt).
- RTL: text takes its own direction (theme typography sets `TextDirection.Content`) and aligns to
  the layout's start edge. Media transport controls, the seek bar and the volume slider stay LTR
  (`LeftToRight`). Directional icon glyphs are listed in `MirroredInRtlSymbols`.
  Physical drag deltas and graphics-layer x values need `towardsEnd()`; `Modifier.offset` already mirrors.

## Don't
- Don't read or port anything from koiverse/ArchiveTune (its NO_AI policy).
- Don't change the Room schema/version without a migration plan.
