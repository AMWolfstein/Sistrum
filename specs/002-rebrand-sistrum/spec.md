# Feature Specification: Rebrand Mornye to Sistrum

**Feature Branch**: `wip`

**Created**: 2026-11-14

**Status**: Draft

**Input**: Owner request (2026-11-14), before the Mornye-theme plan (M001) runs:
the app becomes **Sistrum** instead of "Mornye" / "Mornye Local"; package
`com.amwolfstein.sistrum`; description "Offline music player with some online
features"; icons from `AMWolfstein/Private` branch `desloppify/code-health`; the
About copyright under "Zarz Eleutherius" becomes AMWolfstein. Follow-up answers:
version **0.1.0** (so a later 1.0.0 GitHub release can test the in-app update);
repository `AMWolfstein/Sistrum`; remove the contributors list; accept every
recommendation from the analysis (recorded in research.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The app is Sistrum (Priority: P1)

A person installs the app and sees Sistrum everywhere: launcher name and icon,
notification, in-app text, About page, version 0.1.0 and the new description.

**Why this priority**: This is the request itself.

**Independent Test**: Install the build; check the launcher label and icon, the
notification icon, the About page (name, version, description, copyright) and a
sample of screens and translations for the old name.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the launcher lists apps, **Then** it shows
   "Sistrum" with the new icon, and Android identifies it as
   `com.amwolfstein.sistrum`.
2. **Given** the About page, **When** opened, **Then** it shows Sistrum, version
   0.1.0, "© 2026 AMWolfstein" with "Based on Mornye © 2026 Zarz Eleutherius"
   under it, and no contributors list, Telegram or donation links.
3. **Given** any language, **When** text names the app, **Then** it says Sistrum.

---

### User Story 2 - Updates come from AMWolfstein/Sistrum (Priority: P2)

The in-app update check looks only at `AMWolfstein/Sistrum` releases, so a 1.0.0
release there is offered to a 0.1.0 install.

**Why this priority**: The owner wants to test the update flow with 1.0.0.

**Independent Test**: Feed the update checker a release list with a newer tag and
an arm64 APK asset; it reports the update and the asset URL.

**Acceptance Scenarios**:

1. **Given** 0.1.0 installed and a `v1.0.0` release with an arm64 APK on
   `AMWolfstein/Sistrum`, **When** the app checks, **Then** it offers 1.0.0.
2. **Given** releases on the old `mornyeapp/Mornye` repository, **When** the app
   checks, **Then** they are never consulted.

---

### User Story 3 - Nothing from the upstream maintainer runs (Priority: P3)

The app no longer fetches the original maintainer's remote config, announcements
or donation goals, and shows no What's New tour of Mornye 5.3.

**Why this priority**: Those belong to Mornye, not Sistrum.

**Independent Test**: Search the code for `api.zarz.moe`, the Telegram and
donation links and the What's New screen; none remain, and start-up shows no
announcement or tour.

**Acceptance Scenarios**:

1. **Given** start-up, **When** the app runs, **Then** no request goes to
   `api.zarz.moe` and no remote announcement or What's New tour is shown.
2. **Given** Settings, **When** browsed, **Then** there is no donate page.

### Edge Cases

- Mornye and Sistrum on one phone: they install side by side; nothing is shared.
- Data from Mornye or SpotiFLAC: not carried over, and their backup files are
  not accepted.
- An installed extension that declares `signedSession` no longer loads; its
  author must drop the field.
- Extension files published as `.mornye-ext` or `.spotiflac-ext` are refused;
  the same package renamed to `.sistrum-ext` installs. Extension sign-in services
  that only allow the `mornye://` redirect fail until they accept `sistrum://`.
- Lyrics exported by the app credit Sistrum, not Mornye.
- Downloads land in `Download/Sistrum` when writable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Android application ID and namespace MUST be
  `com.amwolfstein.sistrum`, and the Kotlin sources MUST live in that package.
- **FR-002**: The launcher label and every user-visible app name (all 21
  languages, notifications, exported lyrics credit, the vendor string written
  into audio files, download folders, shared image names, the name sent to
  Subsonic servers and AI lyrics services) and every log/wake-lock/thread name
  MUST be Sistrum.
- **FR-003**: The version MUST be 0.1.0, build 1, everywhere the app reads it.
- **FR-004**: The description MUST be "Offline music player with some online
  features", translated in each supported language.
- **FR-005**: The About page MUST show "© 2026 AMWolfstein" and "Based on Mornye
  © 2026 Zarz Eleutherius"; `NOTICE` and the README licence section MUST keep the
  original copyright beside AMWolfstein's (LGPL-3.0 requires keeping it).
- **FR-006**: The About page MUST NOT show the contributors list, the
  translators list, the Telegram links or donation links.
- **FR-007**: The update checker MUST use `AMWolfstein/Sistrum`; the repository
  link on About MUST point there.
- **FR-008**: The remote config (announcements, donation goals) from
  `api.zarz.moe`, the donate page and the What's New tour MUST be removed with
  their settings, strings and tests.
- **FR-009**: The launcher icon, notification small icon and in-app logos MUST be
  the ones from `AMWolfstein/Private` (`desloppify/code-health`); the Mornye
  brand images MUST be deleted.
- **FR-010**: The Dart package MUST be named `sistrum`.
- **FR-011**: Sistrum is independent of Mornye and SpotiFLAC (owner,
  2026-11-14): the identifiers the app itself owns MUST become Sistrum and stop
  accepting the old ones: extension packages `.sistrum-ext` only (not
  `.mornye-ext`, `.spotiflac-ext`, `.sflx`); sign-in callbacks `sistrum://`; user
  agents `Sistrum/<version>` and `Sistrum-Extension/1.0`; backups `sistrum-backup`
  with their own file extension (not `mornye-backup`, `spotiflac-backup`, `.sflb`);
  the Cast token key; the local extension-storage key label; temporary-file
  suffixes; the Zarz-LRC provider's internal id and the equalizer preset
  format, without their SpotiFLAC/Mornye aliases. Signed sessions are removed (FR-015).
- **FR-012**: Docs and GitHub files MUST describe Sistrum and its repository;
  the M001 plan MUST use the new paths and names.
- **FR-013**: Mornye's history MUST be removed: `CHANGELOG.md` (Mornye 3.0.0 to
  4.9.6), the About translators list, the README "featured on FMHY", Crowdin and
  Telegram badges and the Crowdin credits, `crowdin.yml` (Mornye's Crowdin
  project) and the release workflow's Telegram job. Release notes come from
  Sistrum's own commits (`cliff.toml` pointed at `AMWolfstein/Sistrum`).

- **FR-014**: The Zarz-LRC lyrics provider (run on the original maintainer's
  server) MUST be removed with its settings entries, About links (API docs,
  lyrics site, status page), strings and tests; the other lyrics providers,
  including Apple Music via Paxsenix, MUST keep working.

- **FR-015**: Signed extension sessions (`signedSession` manifests, the
  `SPOTIFLAC-HMAC-V1` signing, the `session-grant` callback and the challenge
  flow that serves only them) MUST be removed, with the rules that treat
  `api.zarz.moe` specially. An extension manifest that declares
  `signedSession` MUST be refused with a clear error. Extension OAuth sign-in
  (`callback`, `spotify-callback`) MUST keep working.

### Key Entities

- **App identity**: name, application ID, version, description, copyright,
  repository, icons.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 occurrences of `com.zarz.mornye` and `package:mornye/` in the
  repository, outside history records.
- **SC-002**: 0 "Mornye"/"SpotiFLAC" app identifiers or user-visible strings,
  except the "Based on Mornye" credit and the internal `Mornye*` theme names;
  0 occurrences of "spotiflac" or "zarz" outside specs, history and the
  copyright credits.
- **SC-003**: The analyzer is clean and the full test suite has no failures beyond
  the baseline; Kotlin and Rust compile where the toolchain runs here.
- **SC-004**: A 1.0.0 release on `AMWolfstein/Sistrum` is offered to 0.1.0.

## Assumptions

- The Mornye theme keeps its internal name in code (`MornyeTheme`, `Mornye*`
  widgets, `mornye_*` preference keys and file names). It is the design system's
  name, never shown as the app name, and M001 works on those names.
- Upgrade and migration code that reads older Mornye or SpotiFLAC data
  (settings, queue, secure storage, backups, database schemas) is outside this
  mission; the owner is offered a separate cleanup mission. This mission only
  stops accepting the old backup and extension formats (FR-011).
- Internal build and native names follow the Sistrum name (TOR006 and TOR008):
  Rust crates `sistrum-*`, native libraries such as `sistrum_audio`, the
  `SISTRUM_ALLOW_DEBUG_SIGNING` build variable, `third_party/*/SISTRUM_PATCHES.md`
  file names. The workflow tooling and the Mornye theme names are not app names.
- Sistrum's own workflow records (`TASK_HISTORY.md`, `.specify/quick-tasks/`,
  completed task titles) are this repository's history, not Mornye's, and keep
  their wording. Third-party changelogs (`third_party/*/CHANGELOG.md`,
  `rust_backend/vendor/**` package source) belong to those packages and stay; the
  vendor README's own commands follow the Sistrum crate names. The portable `workflow/` folder is out of scope.
- Releases on `AMWolfstein/Sistrum` need the signing secrets the release
  workflow reads (`KEYSTORE_BASE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD`); the owner adds them in GitHub before the 1.0.0 update test.
  The same key must sign 0.1.0 and 1.0.0 for Android to install the update.
- No APK build or device test unless the owner asks.
