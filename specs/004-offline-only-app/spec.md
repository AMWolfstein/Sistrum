# Feature Specification: Offline-only app

**Feature Branch**: `wip`

**Created**: 2026-11-17

**Status**: Draft

**Input**: Owner request (Arabic, summarized): remove every online feature, network
storage and audio format conversion; the only thing that stays online is the app
updater. Owner answers to the planning questions: nobody has the app yet, so there is
no existing data to keep (the library is offline only and no migration is needed);
Cast is removed; the Wishlist is removed; the local features that use FFmpeg without
the internet (manual tag editing, ReplayGain, CUE split, audio analysis) stay.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An offline music player (Priority: P1)

The person uses Sistrum as a player for the music files on their phone. Nothing in the
app searches, downloads, streams, uploads, scrobbles, fetches lyrics or metadata, or
talks to another device.

**Independent Test**: Install the APK with mobile data and Wi-Fi off, add a music
folder, play, edit tags, browse every screen and every settings page.

**Acceptance Scenarios**:

1. **Given** the app, **Then** there is no Repository (store) tab, no Extensions,
   Downloads or Network storage settings, no song recognition, no CSV import, no
   Wishlist, no weekly releases, no Last.fm, no Cast in the output picker, no
   download, save offline, upload, open on another platform, re-enrich, online
   lyrics, AI translation or convert option anywhere.
2. **Given** a library folder, **Then** scanning, playback (gapless, AutoMix,
   autoplay from the library, USB bit-perfect, DSD, Android Auto), the library pages,
   search, statistics, Rewind, playlists, Loved, tag editing, embedded and local
   lyrics, ReplayGain, CUE split and backups work as before.
3. **Given** the setup and tutorial screens, **Then** they describe only offline use
   (no download folder, no nearby-devices permission, no extensions step).

### User Story 2 - The updater keeps working (Priority: P1)

The app still checks GitHub releases and downloads and installs a new APK.

**Independent Test**: The updater tests pass; with the check enabled, an update offer
appears for a newer release, downloads with progress and opens the installer.

**Acceptance Scenarios**:

1. **Given** Check for updates on, **When** a newer release exists, **Then** the update
   dialog, download notification and installer behave as before (stable and preview
   channels).
2. **Given** the APK, **Then** INTERNET, REQUEST_INSTALL_PACKAGES, the FileProvider
   for the update folder, the background-work service and notifications remain.

### User Story 3 - A smaller, simpler code base (Priority: P2)

No code, strings, settings, assets, permissions, native code or dependencies remain
for the removed features.

**Independent Test**: Searches for the removed features' names find nothing outside
the M001-M004 specs and history; the APK no longer contains the removed libraries.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Remove Last.fm, song recognition (microphone and Shazam lookup), motion
  artwork (video covers) and weekly releases, with their settings, screens, strings,
  tests and dependencies (`record`, `video_player`).
- **FR-002**: Remove online lyrics (provider fetch, provider priority, Netease,
  Musixmatch and Apple options) and AI lyrics translation. Embedded and sidecar
  lyrics, the lyrics editor, lyrics search, pronunciation and lyric display stay.
- **FR-003**: Remove online metadata: autofill and re-enrich (single, batch, library),
  provider and Deezer/MusicBrainz lookups, online cover search, open on another
  platform and song links. Manual tag editing stays.
- **FR-004**: Remove audio format conversion (single, batch and automatic). FFmpeg
  stays for tag writing, ReplayGain, CUE split, probing and audio analysis.
- **FR-005**: Remove Cast and the local-network (nearby devices) permission.
- **FR-006**: Remove network storage (SMB, WebDAV, HTTP(S), Subsonic): sources,
  playback, offline copies, uploads, edits and deletes on servers.
- **FR-007**: Remove the download engine (queue, workers, providers, finalization,
  quality, Hi-Res check of downloads, download settings and notifications), the
  Wishlist, CSV import and the reorganize-downloads feature.
- **FR-008**: The library reads only scanned folders: remove the download history
  database and its library source. No migration of existing data (owner).
- **FR-009**: Remove the extension system: runtime, packages, repository tab and
  store, authentication callbacks, provider priorities and extension settings.
- **FR-010**: The Rust backend keeps only offline work (library scan, tags, CUE,
  covers, Hi-Res analysis of files, USB audio, data jobs); the network, providers,
  extensions and cast crates are removed.
- **FR-011**: Only the updater may use the network: INTERNET stays; network state,
  Wi-Fi, local network and microphone permissions, the extension callback links and
  the download service are removed.
- **FR-012**: Whatever a task removes takes its settings fields, strings (all ARB
  files), assets, tests, native code and dependencies with it; nothing dead remains.
- **FR-013**: The app updater (check, channels, dialog, download, notifications,
  install) is unchanged.

### Out of scope

- New features or redesigns; the offline screens keep their current look.
- Stored-data migration (no installed users).

## Success Criteria *(mandatory)*

- **SC-001**: `flutter analyze` clean and full `flutter test` with 0 failures after
  every task; Rust `cargo check`/tests and Kotlin compile for tasks that touch them.
- **SC-002**: A release APK builds; it contains no extensions, network, cast or SMB
  code, and is smaller than the 75.9 MB baseline (`3cb6847`).
- **SC-003**: The updater tests pass unchanged.
- **SC-004**: Searches for removed feature names (listed per task) find nothing in
  `lib/`, `test/`, `android/`, `rust_backend/`, `pubspec.yaml`, assets or docs, except
  the planning history.
