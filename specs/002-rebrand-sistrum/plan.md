# Implementation Plan: Rebrand Mornye to Sistrum

**Branch**: `wip` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-rebrand-sistrum/spec.md`

## Summary

Give the app its own identity before M001 runs: Android ID and Kotlin package
`com.amwolfstein.sistrum`, Dart package `sistrum`, name Sistrum, version
0.1.0+1, the new description, AMWolfstein copyright beside the kept Mornye
notice, updates from `AMWolfstein/Sistrum`, the new icons, and no upstream
remote config, donation, Telegram, contributors or What's New content.
Sistrum is independent of Mornye and SpotiFLAC, so app-owned identifiers
(extension files, sign-in scheme, user agents, backups) become Sistrum; only
third-party protocol and service identifiers and the internal `Mornye*` theme
names stay (research R1, R5).

## Technical Context

**Language/Version**: Dart 3.13.7 / Flutter 3.47.8 (patched SDK), Kotlin
(Android Gradle), C++ (`native_audio.cpp`), Rust (`rust_backend`)
**Primary Dependencies**: Flutter, Riverpod, Android SDK/NDK, Cargo
**Storage**: SharedPreferences, SQLite, app files (unchanged keys; R5)
**Testing**: `flutter analyze`, `flutter test`, Kotlin unit tests and
`compileDebugKotlin` through Gradle, `cargo check`/`cargo test` for the touched
crates, where the toolchain runs here
**Target Platform**: Android arm64
**Project Type**: Mobile app
**Performance Goals**: None affected
**Constraints**: No behaviour change beyond identity (constitution I); keep the
third-party identifiers in FR-011
**Scale/Scope**: `mornye` in 11,705 lines / 1,537 files (1,080 files only for
`package:mornye/` imports); `zarz` in 544 lines / 321 files;
`com.zarz.mornye` in 374 lines; 144 Kotlin/Java files to move

## Constitution Check

- I. Preserve behaviour: identity only; native contracts move in lockstep (JNI
  names, channel names); third-party protocols unchanged; the owner accepted
  dropping Mornye/SpotiFLAC file and data compatibility (T008). PASS.
- II. Plan substantial work: cross-language rename with native boundaries. PASS.
- III. One task, one reviewed commit: seven sequential tasks. PASS.
- IV. Honest verification: Gradle and Cargo checks are reported as run or not
  available here; no APK or device claim. PASS.
- V. Simple tooling: renames and deletions; no new dependency. PASS.

## Repository evidence

- **Baseline**: `wip` at `8cb6f4f` (M001 planning only; source equal to
  `5edaaac`). Test baseline from M001 planning: `flutter analyze` clean; full
  `flutter test` no failures after `flutter gen-l10n`, 10 skipped.
- **Map**: `python3 workflow/codegraph/code_tools.py update` succeeded: 1785
  sources, 0 partial, 0 unindexed, 36740 symbols, 127523 relationships.
- **Icon source**: `AMWolfstein/Private` cloned read-only at `9c7643b`
  (`desloppify/code-health`) to `/home/user/amwolfstein/private`.
- **Read for this plan**: `pubspec.yaml`, `l10n.yaml`,
  `lib/constants/app_info.dart`, `lib/screens/settings/about_page.dart`,
  `lib/services/update_checker.dart`, `lib/services/app_remote_config_service.dart`,
  `lib/app.dart` (What's New route and gate), `android/app/build.gradle.kts`,
  `AndroidManifest.xml`, `native_audio.cpp`/`.map`, `NativeAudio.kt`,
  `MornyeApplication.kt`, `.github/workflows/release.yml`, `NOTICE`, README
  licence section, `rust_backend/crates/core/src/app_version.rs`,
  `rust_backend/crates/core/src/lyrics/lrc.rs`, and the Private repo's
  `pubspec.yaml`, `build.gradle.kts` and resource folders.
- **Findings**: see research R2–R7 for exact locations.
- **Limitations**: The Gradle/NDK and Cargo builds have not been run in this
  session; T001 runs them if they work here and records the result.
  Visual checks of the icons are by image inspection, not on a device.

## Project Structure

### Documentation (this feature)

```text
specs/002-rebrand-sistrum/
├── spec.md
├── plan.md
├── research.md
├── tasks.md
├── task-log.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
android/app/build.gradle.kts, android/app/src/**           Android identity, Kotlin package, JNI, icons
rust_backend/                                              JNI name, exported lyrics credit, docs
pubspec.yaml, l10n.yaml, lib/**, test/**, integration_test/**  Dart package, name, version, services
lib/l10n/arb/                                              app name and description in 21 languages
assets/images/, assets/brand/, icon*.png                   logos and launcher sources
README.md, NOTICE, CONTRIBUTING.md, docs/, .github/, */README.md  docs
specs/001-remove-material-theme/                           M001 paths and names
```

**Structure Decision**: Existing layout; Kotlin moves from
`kotlin/com/zarz/mornye` to `kotlin/com/amwolfstein/sistrum` in each source set.

## Task design

1. **T001 Android identity**: application ID, namespace, manifest package, Kotlin
   package move in all source sets, JNI names (C++, map, Rust), channel names on
   both sides, launcher label. Gradle and Cargo checks.
2. **T002 Dart package**: `sistrum` in `pubspec.yaml` and every import.
3. **T003 Name, version, description, copyright**: `AppInfo`, `pubspec.yaml`,
   ARB strings in 21 languages, download folder, lyrics credit, About copyright,
   contributors and Telegram removed, `NOTICE`.
4. **T004 Updates and upstream services**: `githubRepo`, release workflow APK
   name, remote config, announcement prompt, donate page, What's New removed.
5. **T005 Icons and logos**: copy from Private, notification icon, mix banner
   logo, delete `assets/brand/`.
6. **T006 Docs and M001**: README, CONTRIBUTING, component READMEs, docs/,
   `.github/` (FUNDING, issue templates, workflows), CODE_OF_CONDUCT contact,
   M001 spec/plan/research/tasks paths and names.
7. **T010 Signed sessions** (owner): remove `signedSession`, its HMAC
   signing, `session-grant`, the challenge flow that serves only it, and the
   `api.zarz.moe` rules.
8. **T008 App-owned identifiers** (reserved after the others, runs before the
   final review): `.sistrum-ext`, `sistrum://`, user agents, backup format,
   Cast token key, storage label, temp suffixes.
9. **T009 Zarz-LRC** (owner): remove the provider, its migration, About links
   and strings.
10. **T007 [FINAL]**: cumulative review.

Order: M002 runs before M001's first task. Each task leaves the analyzer clean
and the suite at baseline.

## Complexity Tracking

None.
