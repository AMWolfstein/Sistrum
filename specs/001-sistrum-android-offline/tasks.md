# M001 tasks

Run sequentially after implementation is authorized. Each task starts with `python3 workflow/codegraph/code_tools.py update` and scope inspection. Record checks and independent review in [task-log.md](task-log.md). Follow the existing three-round limit and one commit per completed task. T001–T003 form the rebrand checkpoint before T004 begins.

- [x] M001-T001 Apply Sistrum identity across app and native contracts
  - Paths: `pubspec.yaml`, `lib/`, `android/app/`, `rust_backend/`, Android phone scripts, CI, docs, tests and generated bindings. Not Android TV, iOS, macOS, Windows or Linux.
  - Design app-owned identifiers, packages and version from current code. Use WIP only for artwork. Record and verify the new values against current cross-language callers. Classify references as product, Mornye theme, attribution or external API. Keep theme selector/state keys, theme symbols and existing behavior. Preserve external IDs unless the provider contract explicitly supports the new value.
  - Checks: `git grep -in mornye` with reviewed exceptions (theme, attribution, external, and untouched non-Android/TV paths), `flutter analyze`, `flutter test`, `cargo check --manifest-path rust_backend/Cargo.toml --workspace --locked` and `python3 workflow/work.py quality bridge verify`. Verify generated JNI/UniFFI registration and loader names in an authorized device run.

- [x] M001-T002 Match Sistrum artwork to original asset layouts
  - Paths: `icon*.png`, `logo.svg`, `assets/images/`, `assets/brand/`, `README.md`, `pubspec.yaml`, Android `res/`, `apps.json`. Not TV, iOS, macOS, Windows or Linux resources.
  - Copy the approved images from `origin/wip` `ba0e973` byte for byte. Derive missing brand variants still referenced by shared or Android code from them with the baseline geometry and alpha; delete Mornye brand files that end up unreferenced, keeping those non-Android shells still use. Point the README and the Top Picks card at the `sistrum-horizontal-*` images. Update launcher background colors to match the WIP icon. Keep Mornye theme design assets, tokens and layouts.
  - Checks: copied files match `ba0e973` blob hashes; every changed destination keeps its baseline dimensions and alpha mode; no asset reference is broken (`git grep` for removed paths). Inspect rendered splash, adaptive and monochrome launcher, About/onboarding, share cards, notification and Top Picks card; run `flutter analyze` and affected Flutter tests.

- [x] M001-T003 Verify complete rebrand before removing features
  - Paths: cumulative rebrand diff, app startup, existing feature routes and `integration_test/`.
  - Clean install displays Sistrum. Exercise first run, local playback, a representative existing online route where service access permits, and Mornye theme selection, persistence and rendering. Verify device registration and keep behavior/feature removals for later tasks.
  - Checks: `flutter analyze`, `flutter test`, Android manifest/loader checks, `flutter test integration_test/android_background_playback_test.dart -d "$ANDROID_SERIAL"` and a device theme walkthrough when authorized. Record external services that could not be exercised.

- [x] M001-T004 Make local library and phone playback self-contained
  - Paths: `lib/main.dart`, `lib/app.dart`, local library providers/services, phone screens, playback decks, Kotlin SAF and media session.
  - Fresh schema and local flow work in airplane mode. Preserve queue and local state across restart. Handle denied/revoked grants, unavailable documents and removable storage. No Mornye upgrade or migration work.
  - Checks: `flutter analyze`, `flutter test`, first-launch airplane mode walkthrough and Android background playback test with a local fixture.

- [ ] M001-T005 Remove online product features and dependencies
  - Paths: Flutter navigation/startup, manifest and Android services, Dart/native bridges, `rust_backend/crates/`, pub/Cargo manifests and locks, resources and tests.
  - Remove online features and permissions after callers are detached. Verify local codecs, metadata, storage and playback fallback stay wired. Keep Mornye theme and theme-specific assets.
  - Checks: `flutter analyze`, `flutter test`, `cargo tree --manifest-path rust_backend/Cargo.toml -p "$MOBILE_CRATE" --target aarch64-linux-android`, bridge verification and merged release manifest inspection. Set MOBILE_CRATE to the name chosen in T001.

- [ ] M001-T006 Remove other platforms and enforce ARM64 packaging
  - Paths: Gradle, Android CMake, `scripts/build_android.sh`, `scripts/build_rust_backend.sh`, APK audit, CI/release jobs and retired platform runners and docs.
  - Remove non-phone products together with their remaining Mornye identity, so no app-owned Mornye name remains. Enforce ARM64 as the only ABI at every layer. Verify package name, merged manifest, release signature, ELF architecture/required libraries and 16 KB compatibility.
  - Checks: `bash -n scripts/build_android.sh scripts/build_rust_backend.sh`, `python3 scripts/test_build_android.py`, `python3 scripts/test_check_backend_apk.py`, `apkanalyzer manifest permissions "$APK"`, `zipalign -c -P 16 -v 4 "$APK"`, ELF segment inspection and `apksigner verify --print-certs "$APK"`. Run artifact checks when authorized.

- [ ] M001-T007 [FINAL] Verify Sistrum Android offline release
  - Paths: cumulative diff, retained integration tests and task records.
  - Validate clean install, local backup/restore, sustained offline playback with background controls, Bluetooth/headset and focus handling, and representative 4 KB/16 KB ARM64 environments. Check that restored playback state and Mornye theme persist. Verify no release network capability, no obsolete platform artifacts, and required upstream notices.
  - Checks: workflow validation, both workflow test suites, Flutter analysis and tests, authorized device tests and artifact audit from T006. Record anything untested.

## Owner extras

None reserved.
