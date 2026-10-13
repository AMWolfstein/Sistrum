<p align="center"><img src="branding/logo.png" alt="Sistrum logo" width="160"></p>

# Sistrum

Android music player for your local library. Sistrum is a GPL-3.0 hard fork of
[misa198/airmedy](https://github.com/misa198/airmedy); desktop sync was removed and
it plays only files on the device.

## Status

- Android development is active and uses native Jetpack Compose UI.
- Android supports API 31 (Android 12) and newer; this baseline allows the
  floating navigation to use true backdrop blur.
- `sharedLogic` is the future cross-platform business-logic module.

Detailed architecture docs (engineering rules, the mobile UI catalog) live in
the `catalog/` folder of the original misa198/airmedy monorepo, not in this repo.

## Module boundaries

```text
androidApp
  Android-only Compose screens, ViewModels, navigation, resources, and adapters
      |
      v
sharedLogic
  Common models, use cases, ports, validation, and business rules
```

## Navigation

The floating Home, Insight, Library, and Settings destinations each own an
independent Android UI stack. Switching destinations preserves the currently
open page in every other stack. Stack changes within a destination use the
standard page transition; switching destination stacks changes the title and
content together while the floating navigation remains in place.

`sharedLogic` must remain UI- and platform-neutral. Android implements its ports
inside `androidApp`.

The mobile app is standalone and never pairs with the desktop app. Its music
library is built by a device-local MediaStore scan instead of pulling desktop
assets. The scan reads audio and album artwork through MediaStore on
`Dispatchers.IO`, then writes the result into the Room library in one pass
(`writeLocalLibrary` with a `local:` sync plan) using track ids of the form
`local:<mediaStoreId>`, album ids `local:album:<albumKey>`, and derived ids for
artists, genres, and composers. Album artwork is cached as JPEG files below
`filesDir/artwork/`. Scanning is a foreground, user-initiated action on the
Settings > Scan screen. See `sync/MediaStoreLibraryScanner.kt` and
`ui/screens/LibraryScanContent.kt`.

Each install has a stable `DeviceIdentity` created on first run and persisted in
SharedPreferences. Listening sessions and daily aggregates are tagged with this
device id (`sourceDeviceId`), which is what the Insights source filter uses to
distinguish This phone from Other devices; the Insights screen hence offers
three source filters: All, This phone, and Other devices.

## Prerequisites

- JDK 11, as configured by the Gradle modules.
- Android SDK Platform 36 and an Android device or emulator for runtime testing.
- Android Studio is recommended for running and inspecting the Android app.

## Commands

Run from `mobile/`:

```bash
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleProdDebug
./gradlew :sharedLogic:testAndroidHostTest
```

The Android app has two installable variants: `dev` uses application ID
`com.amwolfstein.sistrum.dev`; `prod` uses `com.amwolfstein.sistrum`. This allows both
variants to be installed on the same device. The `qa` build type (device tests) adds
`.qa`. Icons are generated from `branding/` with `bash tools/brand/generate_icons.sh`.

### Android versioning

Android releases use a three-part `versionName` and a monotonically increasing
`versionCode`. To bump both values, run this from the repository root:

```bash
task bump-mobile-version VERSION=0.0.2
```

The command accepts only `X.Y.Z` versions and increments `versionCode` by one.

### FFmpeg player build

Android playback uses FFmpeg directly for local synced audio: FFmpeg demuxes and
decodes every enabled music format, then the native player sends float PCM to
AAudio. It does not use a Media3 or platform-decoder fallback. All Android app
variants require `../scripts/build-ffmpeg-android.sh arm64-v8a` to have run
first. It downloads the pinned FFmpeg 8.1 tarball to a temporary cache and writes generated
headers and `.so` files below `androidApp/build/` and `androidApp/src/main/jniLibs/`.
Those artifacts are ignored by Git. The build requires Android NDK 30.0.15729638.

For a manual rebuild, run from the repository root:

```bash
bash scripts/build-ffmpeg-android.sh arm64-v8a
```

The FFmpeg build enables the complete upstream decoder/demuxer/parser registry,
so music never falls back to Android decoding. It stays smaller than a full
distribution by excluding programs, encoders, muxers, filters, devices and
network protocols. It is LGPL-only: it does not enable GPL or nonfree components.
Release attribution must provide the matching FFmpeg source archive, configure
line, and any patches.

Or, from the repository root:

```bash
./mobile/gradlew :androidApp:assembleDevDebug
./mobile/gradlew :androidApp:assembleProdDebug
./mobile/gradlew :sharedLogic:testAndroidHostTest
```

The repository-root `task verify` command covers desktop Go/Vue code only.
Mobile changes must run the relevant Gradle build and tests above, plus any
feature-specific Android host or UI test task.

## Last.fm

Android authenticates independently from desktop through the browser and the
`airmedy://lastfm/auth` callback (the scheme keeps the upstream name: it is the
callback registered for the Last.fm API account). Supply its dedicated API credentials in the
Git-ignored `mobile/local.properties` file (environment variables remain the CI
fallback):

```properties
LASTFM_API_KEY=your_key
LASTFM_API_SECRET=your_secret
```

Builds without them remain usable, but the Last.fm Connect action is disabled.

The session key is encrypted with Android Keystore before DataStore persistence.
Now Playing, scrobbling, and individual favorite love/unlove requests are sent
directly from Android; they never depend on a paired desktop.

## Listening tracking

Android independently records actual playing time, qualified plays, and
completed/skipped/stopped attempts in Room. Raw records are retained for 180
days and daily origin-tagged aggregates (keyed by `sourceDeviceId`) are
retained for all-time totals. Recording is local-only: there is no exchange, and
the tag is the install's stable `DeviceIdentity`.

## Playlist edits

Playlist edits are queued locally in Room as mutations (`CREATE`, `UPDATE`,
`DELETE`, `ADD_TRACK`, `REMOVE_TRACK`, `MOVE_TRACK`, `SET_ARTWORK`,
`REMOVE_ARTWORK`, `SET_FAVORITE`) in `playlist_mutations`. Each pending mutation
is applied to the projected local library immediately. There is no desktop to
reconcile with, so mutations stay local and are never acknowledged; repeated
edits to the same target collapse into one row (see `queuePlaylistMutation`).
Artwork for `SET_ARTWORK` is staged in Room with its SHA-256, MIME type, and
byte size, and cached below `filesDir`.
