# M002 tasks: Rebrand Mornye to Sistrum

Input: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md).
Follow `.specify/project-policy.md`. Read plan.md's Repository evidence first.
M002 runs before M001's first task.

## Execution rules

Sequential tasks; no parallel markers. The **first action in every task**,
including [FINAL], is `python3 workflow/codegraph/code_tools.py update`; inspect
scope and coverage before edits and stop if it fails. Record refresh evidence in
`task-log.md` inside the task's single commit. At most three review rounds per
task and one commit after its checks and an independent review pass; push to
`wip` only after that commit. No dead code is left for a later task: whatever a
task removes takes its strings, settings, assets and tests with it.

Sistrum is independent of Mornye and SpotiFLAC: app-owned identifiers are
renamed in M002-T008 (spec FR-011, research R5). Never change third-party
services. Every SpotiFLAC name becomes Sistrum; signed extension sessions
(`SPOTIFLAC-HMAC-V1`) and the `api.zarz.moe` rules are removed (T010) and
Zarz-LRC is removed (T009) (owner). Never rename the internal `Mornye*` theme
names, Rust crate names, native library names or `MORNYE_*` build variables
(spec Assumptions). Legacy-data migration code is out of scope.

Common checks: `flutter gen-l10n` (after ARB changes), `flutter analyze`,
`flutter test` (full suite; baseline no failures, 10 skipped), and the task's
searches below. Android checks: `./gradlew :app:compileDebugKotlin` and
`:app:testDebugUnitTest` from `android/` if the Gradle/NDK toolchain runs here;
Rust: `cargo check` for touched crates. Record each as run or unavailable.

## Implementation

- [x] M002-T001 Move the Android identity to com.amwolfstein.sistrum
  - Start: refresh the map; query `NativeAudio`, `MornyeApplication`,
    `MainActivity`, `SharedEngineChannels`.
  - Acceptance: FR-001. `applicationId`, `namespace` and the manifest `package`
    are `com.amwolfstein.sistrum`; every Kotlin/Java file under
    `android/app/src/*/kotlin|java/com/zarz/mornye` moves to
    `com/amwolfstein/sistrum` with its `package`/`import` lines; the cast options
    provider class name in the manifest follows; JNI names
    `Java_com_zarz_mornye_*` become `Java_com_amwolfstein_sistrum_*` in
    `native_audio.cpp`, `native_audio.map` and the Rust reference; every
    `com.zarz.mornye/<channel>` method/event channel and intent action is renamed
    on both the Dart and Kotlin side in this task; the launcher label is
    "Sistrum". `mornye://` intent filters stay.
  - Paths: `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`,
    `android/app/src/{main,rust,test,androidTest}/**`, `android/app/src/main/cpp/`,
    `rust_backend/` (JNI reference), `lib/services/**` and other Dart files naming
    a channel, their tests, `android/app/src/test/resources/` if they name the
    package.
  - Validation: common and Android checks;
    `git grep -n "com\.zarz\.mornye\|com/zarz/mornye\|Java_com_zarz" -- ':!specs' ':!.specify' ':!TASK_HISTORY.md'`
    returns nothing.
  - Dependencies: planning commit.

- [ ] M002-T002 Rename the Dart package to sistrum
  - Start: refresh the map.
  - Acceptance: FR-010. `pubspec.yaml` `name: sistrum`; every `package:mornye/`
    import in `lib`, `test`, `integration_test` and tools becomes
    `package:sistrum/`; `pubspec.lock`/generated files regenerated. Nothing else
    changes in this task.
  - Paths: `pubspec.yaml`, `pubspec.lock`, `lib/**`, `test/**`,
    `integration_test/**`, `scripts/**` and `test_driver/**` if they import it.
  - Validation: common checks; `git grep -n "package:mornye/"` returns nothing.
  - Dependencies: M002-T001.

- [ ] M002-T003 Rename the app, version, description and copyright
  - Start: refresh the map; query `AppInfo`.
  - Acceptance: FR-002 to FR-006. `AppInfo.appName = 'Sistrum'`,
    `version = '0.1.0'`, `buildNumber = '1'`, copyright "© 2026 AMWolfstein" with
    the "Based on Mornye © 2026 Zarz Eleutherius" credit on About;
    `mobileAuthor`, the contributors list, the translators list (`_Translator`)
    and the Telegram rows are deleted.
    `pubspec.yaml` `version: 0.1.0+1` and `description: Offline music player with
    some online features`. In all 21 ARB files `appName` and every string that
    names the app say Sistrum (the extension-file strings change in T008);
    `aboutAppDescription` carries the new description translated into each
    language; `storageAutomaticDownloadFolder` names `Download/Sistrum`; the
    Vietnamese `sectionVolumeAndTransitions` and `usbVolumeWarning` no longer say
    SpotiFLAC (the first is retranslated from English "Volume and transitions"). The
    download folder literals become `Sistrum`; shared images are named
    `sistrum_lyrics.png` and `sistrum_rewind.png`
    (`lyrics_share_screen.dart:148`, `listening_rewind_story.dart:217`); the
    exported lyrics credit in
    `lrc.rs` says Sistrum. `NOTICE` and the README licence section keep both
    copyrights. Every other literal that names the app becomes Sistrum:
    Dart (`download_queue_provider.dart:106` default output folder,
    `files_settings_page.dart:680`–`:704` folder examples,
    `cover_download_service.dart:77`, `lyrics_ai_client.dart:362` `X-Title`,
    `network_storage_subsonic.dart:61` client id `c`, `offline_saver.dart:204`,
    `:476` service name); Kotlin (66 literals: log tags, wake-lock tags, thread
    names, the background-work notification's default title
    `BackgroundWorkService.kt:140`); Rust (`tags/write.rs:347` FLAC vendor
    string written into files, `lrc.rs:203`, `:205`); XML comments
    (`res/xml/backup_rules.xml`). Theme identifiers (`MornyeGlass` logger,
    `Mornye*` classes) stay; identifiers in T008 wait for it. Tests asserting
    the old version, name or copyright are updated.
  - Paths: `lib/constants/app_info.dart`, `lib/screens/settings/about_page.dart`,
    `pubspec.yaml`, `lib/l10n/arb/app_*.arb`, the files holding the download
    folder literals, `rust_backend/crates/core/src/lyrics/lrc.rs`, `NOTICE`,
    `README.md` (licence section only), the Dart, Kotlin, Rust and XML files
    listed above, affected tests.
  - Validation: common checks; Rust `cargo test -p mornye-core` lyrics tests if
    available; `git grep -n "Zarz Eleutherius"` only in the About credit,
    `NOTICE` and README licence; no ARB value names Mornye except `.mornye-ext`
    and the credit; `git grep -n "5\.3\.1\|+167"` only in history records;
    `git grep -n -E "[\"']Mornye[^\"']*[\"']" -- lib android rust_backend/crates ':!lib/l10n'`
    shows only theme identifiers and T008 identifiers.
  - Dependencies: M002-T002.

- [ ] M002-T004 Point updates to AMWolfstein/Sistrum and drop upstream services
  - Start: refresh the map; query `AppRemoteConfigService`, `DonatePage`,
    `WhatsNewScreen`, `UpdateChecker`.
  - Acceptance: FR-007, FR-008. `AppInfo.githubRepo = 'AMWolfstein/Sistrum'`;
    the release workflow names APKs `Sistrum-${VERSION}-${label}.apk` (the
    `arm64` marker stays), names the release "Sistrum <version>" and loses its
    `notify-telegram` job; `cliff.toml` points at `AMWolfstein/Sistrum` without the
    `zarzet` author rule. `app_remote_config_service.dart`, `remoteConfigApiUrl`,
    `kofiUrl`, `githubSponsorsUrl`, the start-up announcement prompt, the donate
    page and its Settings entry are deleted. The What's New screen, its route and
    gate in `lib/app.dart`, the About entry, `AppInfo.whatsNewRelease` and
    `AppSettings.whatsNewSeen` (with its provider writes) are deleted. Strings,
    settings-search entries and tests used only by them go too. A test feeds the
    update checker a `v1.0.0` release with an arm64 asset against 0.1.0 and
    expects the update.
  - Paths: `lib/constants/app_info.dart`, `lib/services/update_checker.dart`,
    `lib/services/app_remote_config_service.dart`,
    `lib/screens/main_shell_startup_prompts.dart`,
    `lib/screens/settings/donate_page.dart`, `lib/screens/settings/settings_tab.dart`,
    `lib/screens/whats_new_screen.dart`, `lib/app.dart`,
    `lib/screens/settings/about_page.dart`, `lib/models/settings.dart`
    (+ generated), `lib/providers/settings_provider.dart`,
    `lib/utils/settings_search_catalog.dart`, `lib/l10n/arb/app_*.arb`,
    `.github/workflows/release.yml` (also its `TELEGRAM_BOT_TOKEN` secret use),
    `cliff.toml`, `test/app_remote_config_service_test.dart`,
    `test/app_remote_config_filter_test.dart`, `test/whats_new_screen_test.dart`,
    `test/settings_search_test.dart`, `test/adaptive_overlay_test.dart`, update
    checker tests.
  - Validation: common checks;
    `git grep -n "api\.zarz\.moe\|mornyeapp\|t\.me/mornye\|ko-fi\.com/zarzet\|sponsors/zarzet\|WhatsNew\|whatsNew"`
    returns nothing outside history records.
  - Dependencies: M002-T003.

- [ ] M002-T005 Replace the icons and logos
  - Start: refresh the map.
  - Acceptance: FR-009. From `AMWolfstein/Private` `9c7643b`: root `icon.png`,
    `icon_android.png`, `icon_foreground_android.png`,
    `icon_monochrome_android.png`; `android/app/src/main/res/mipmap-*/` and
    `drawable-*dpi/ic_launcher_{foreground,monochrome}.png`;
    `assets/images/logo.png`, `assets/images/logo-transparent.png`;
    `drawable/ic_stat_sistrum.xml`. `flutter_launcher_icons` uses background
    `#12100E` and inset 16. The notification small icon is `ic_stat_sistrum` in
    `MornyeApplication.kt`, `music_player_runtime.dart` and
    `notification_service.dart`; `ic_notification_mornye.xml` is deleted.
    `assets/brand/` and its `pubspec.yaml` entry are deleted; the mix banner
    (`mornye_mix_banner.dart`) shows the Sistrum logo and name instead of the
    Mornye lockup.
  - Paths: the files above, `pubspec.yaml`, `lib/widgets/mornye_mix_banner.dart`,
    `lib/services/music_player_runtime.dart`, `lib/services/notification_service.dart`,
    the Kotlin application class, tests of the banner and notification icon.
  - Validation: common and Android checks; copied files byte-equal the source
    (`cmp`); `git grep -n "ic_notification_mornye\|assets/brand"` returns
    nothing; the icons are inspected as images.
  - Dependencies: M002-T004.

- [ ] M002-T006 Update the docs, GitHub files and the M001 plan
  - Start: refresh the map.
  - Acceptance: FR-012. `README.md`, `CONTRIBUTING.md`, `android/README.md`,
    `rust_backend/README.md`, `integration_test/README.md`, `test/README.md`,
    `NETWORK_STORAGE.md`, `docs/`, `lib/theme/README.md`,
    `third_party/*/README.md`/`MORNYE_PATCHES.md` text and
    `android/app/src/androidTest/assets/README.md` name Sistrum, its paths and
    repository (the Mornye theme keeps its name where the text means the theme);
    README no longer lists Zarz-LRC (`README.md:122`, `:337`). `.github/FUNDING.yml` is deleted; issue templates and
    workflows name Sistrum and `AMWolfstein/Sistrum`; `CODE_OF_CONDUCT.md`
    contact is AMWolfstein. FR-013: `CHANGELOG.md`, `crowdin.yml` and the
    README FMHY, Crowdin and Telegram badges, their SVGs in `assets/readme/` and
    the Crowdin credit line are deleted; the README release badge points at
    `AMWolfstein/Sistrum`; `CONTRIBUTING.md` drops the Crowdin link.
    `specs/001-remove-material-theme/` drops references to files M002 deletes
    (What's New, donate page, remote config, About contributors, Zarz-LRC,
    signed sessions) and its baseline/test counts are re-taken at M001's start;
    it uses
    `com/amwolfstein/sistrum` paths, `package:sistrum/` and the Sistrum name
    (its findings about the Mornye theme stay).
  - Paths: the files above, `CHANGELOG.md`, `crowdin.yml`, `assets/readme/`.
  - Validation: links resolve; `git grep -n -i "fmhy\|crowdin\|t\.me/mornye"`
    returns nothing outside history records; `git grep -n -i "zarz" -- ':!specs/002*' ':!.specify' ':!TASK_HISTORY.md' ':!workflow'`
    shows only the copyright credits and the T009/T010 sites (removed later); `python3 workflow/work.py validate` passes.
  - Dependencies: M002-T005.

- [ ] M002-T010 Remove signed extension sessions and the api.zarz.moe rules
  - Request: owner, 2026-11-14: remove `SPOTIFLAC-HMAC-V1` and its server
    (FR-015).
  - Start: refresh the map; query `SignedSessionClient`,
    `preflight_signed_session`, `signed_session`, `session-grant`,
    `ExtensionAuthCallbacks`, `extension_session_callback`,
    `VerificationNotificationIntent`; trace every verification path to its
    source before deleting it.
  - Acceptance: the signed-session feature is gone: `rust_backend/crates/extensions/src/signed_session/`
    (coordinator, exchange, fetch, protocol, store) and `signed_session.rs`, the
    `SignedSession` manifest type and `signedSession` field (a manifest that
    declares it is refused with a clear error), the runtime session client and
    download preflight (`runtime.rs`, `manager/downloads.rs`), wiring in
    `environment.rs`, `lib.rs`, `mobile/src/extensions.rs`; the `session-grant`
    callback (manifest intent filter, `ExtensionAuthCallbacks.kt`,
    `MainActivityExtensionIntents.kt`, `MainActivity.kt`,
    `MainActivityInstallState.kt`, `CoreBackend.kt`, the Rust backend classes,
    `lib/utils/extension_session_callback.dart`, `platform_bridge` session-grant
    events, `extension_auth_launcher.dart` session parts). Verification UI,
    notifications and download pauses go too if the map shows they serve only
    signed-session challenges; anything serving other flows stays and is
    recorded in `task-log.md`. The `api.zarz.moe` user-agent rules in
    `backend/cover.rs:190` and `backend/health.rs:309` go (every host gets the
    random user agent), and test hosts use `example.test`. Strings, settings
    and tests used only by removed code go. OAuth `callback`/`spotify-callback`
    stay (renamed in T008).
  - Paths: the files above,
    `rust_backend/crates/extensions/tests/signed_session_verification.rs`,
    `android/app/src/androidTest/.../ExtensionAuthCallbacksTest.kt`,
    `test/extension_session_callback_test.dart`,
    `test/extension_verification_browser_test.dart`,
    `test/extension_verification_feedback_test.dart`,
    `test/platform_bridge_session_grant_test.dart`,
    `test/app_remote_config_filter_test.dart` (if still present), `lib/l10n/arb/app_*.arb`.
  - Validation: common, Android and Rust checks (`cargo test -p mornye-extensions`
    and `mornye-mobile` if available); `git grep -n -i "signed_session\|signedSession\|SignedSession\|session-grant\|sessionGrant\|HMAC-V1\|zarz\.moe"`
    returns nothing outside specs and history.
  - Dependencies: M002-T006.

- [ ] M002-T008 Rename the app-owned protocol identifiers to Sistrum
  - Request: owner, 2026-11-14: Sistrum is independent of Mornye and SpotiFLAC
    (FR-011, research R5).
  - Start: refresh the map; query `BackupService`, `CastOutputPlugin`, the
    extensions `package` and `storage` modules, `extension_auth_launcher`.
  - Acceptance: extension packages are `.sistrum-ext` only (Rust `package.rs`,
    `extensions_page.dart`, error text, ARB `snackbarSelectExtFile` and
    `extensionsNoExtensionsSubtitle` in 21 languages); callbacks are
    `sistrum://callback|spotify-callback` (manifest, Rust `auth.rs`, Dart
    launcher and tests; `session-grant` is already gone with T010); user agents
    `Sistrum/<version>` and `Sistrum-Extension/1.0` (`app_version.rs`,
    `client.rs`, `prelude.js`); backups use magic `sistrum-backup`, a Sistrum file
    extension and `sistrum_backup_*` names, and no longer read `mornye-backup` or
    `spotiflac-backup` (Dart `backup_service.dart` and Rust `native_backup`);
    Cast custom data key `sistrumToken` on both sides; extension storage HMAC
    label `Sistrum extension storage v1`; temp suffixes `.sistrum-backup-*`,
    `.sistrum-ffmpeg-*`. (The Zarz-LRC provider ids and labels go with the
    provider in T009.) The equalizer preset format is `sistrum-equalizer` and the old formats are
    refused. Lyrics test fixtures use "Sistrum" as the sample app name. Tests
    follow. Third-party identifiers stay (Execution rules).
  - Paths: `rust_backend/crates/extensions/src/{package,auth,storage,prelude.js,repository}.rs|js`,
    `rust_backend/crates/extensions/src/manager/tests.rs`,
    `rust_backend/crates/core/src/app_version.rs`,
    `rust_backend/crates/network/src/client.rs`,
    `rust_backend/crates/cast/src/sender.rs`, `rust_backend/crates/mobile/src/native_backup/**`,
    `android/app/src/main/AndroidManifest.xml`, `CastOutputPlugin.kt`,
    `CoreBackend.kt`, `NativeFinalizerFFmpeg.kt`, `lib/services/backup_service.dart`,
    `lib/services/ffmpeg_support.dart`, `lib/controllers/extension_auth_launcher.dart`,
    `lib/screens/settings/extensions_page.dart`, `lib/l10n/arb/app_*.arb`,
    `lib/providers/equalizer_provider.dart`, the tests and fixtures
    naming them (Dart, Kotlin `androidTest`, Rust, `test/fixtures/`).
  - Validation: common, Android and Rust checks (`cargo test` for
    `mornye-extensions`, `mornye-core`, `mornye-cast`, `mornye-mobile` if
    available); `git grep -n -i "mornye-ext\|spotiflac-ext\|\.sflx\|\.sflb\|mornye://\|mornye-backup\|spotiflac-backup\|mornyeToken\|Mornye-Extension\|\"Mornye/\|SpotiFLAC Mobile extension storage"`
    returns nothing outside specs and history; `git grep -n -i spotiflac -- ':!specs' ':!.specify' ':!TASK_HISTORY.md'`
    returns nothing.
  - Dependencies: M002-T010.

- [ ] M002-T009 Remove the Zarz-LRC lyrics provider
  - Request: owner, 2026-11-14: remove Zarz-LRC (FR-014).
  - Start: refresh the map; query `zarz_lrc_lyrics`, `format_zarz_lrc_apple`,
    `AppleLyricsSource`, `lyricsProviderZarzLrcDesc`.
  - Acceptance: the provider `mornye_lyrics` and its alias `spotiflac_lyrics`
    are gone from Rust (`providers/src/lyrics/builtin.rs:81`,
    `builtin/apple.rs` `ZarzLrc` arm and its tests,
    `core/src/lyrics/payloads.rs` `format_zarz_lrc_apple` and its tests,
    `core/src/lyrics/config.rs` provider entry and aliases, `lrc.rs` labels
    "Zarz-LRC", "Mornye Lyrics", "SpotiFLAC-Mobile Lyrics" and their source
    prefixes, `cache.rs` fixture) and from Dart (`AppSettings` default providers
    in `settings.dart` and generated code, the `spotiflac_lyrics` migration at
    `settings.dart:714`–`:720`, migration 13 in `settings_provider.dart:330`–`:349`
    which re-adds it, `lyrics_provider_priority_page.dart`,
    `lyrics_provider_names.dart`); About loses the Zarz-LRC row and the
    `api.zarz.moe/apple-music/docs`, `lyrics.zarz.moe` and `status.zarz.moe` links;
    `lyricsProviderZarzLrcDesc` goes from the 21 ARB files. A stored provider list
    naming `mornye_lyrics` loads without it. The Apple Music provider via
    Paxsenix (`apple_music`) and the other providers are unchanged. Tests follow.
  - Paths: the files above, `lib/screens/settings/about_page.dart`,
    `lib/l10n/arb/app_*.arb`, `test/app_settings_model_test.dart`,
    `test/lyrics_provider_migration_test.dart`, Rust tests.
  - Validation: common and Rust checks (`cargo test` for `mornye-core` and
    `mornye-providers` if available); `git grep -n -i "zarz.lrc\|zarzlrc\|mornye_lyrics\|spotiflac_lyrics\|lyrics\.zarz\|status\.zarz\|apple-music/docs"`
    returns nothing outside specs and history.
  - Dependencies: M002-T008.

## Owner extras

None yet.

## Final cumulative review

- [ ] M002-T007 [FINAL] Cumulative review
  - Start: refresh the map and inspect coverage.
  - Acceptance: every FR and SC in spec.md holds on the cumulative diff from
    `8cb6f4f`; all actionable findings fixed.
  - Paths: all files changed in this mission, `task-log.md`, `tasks.md`.
  - Validation: `flutter analyze`; full `flutter test`; Gradle and Cargo checks
    where available; the searches of T001–T006; `python3 workflow/work.py
    validate`; a check that every remaining "Mornye"/"mornye" is the theme name,
    a compatibility identifier, an internal build name, the credit or history.
    No APK unless the owner asks.
  - Dependencies: M002-T001 to M002-T006, M002-T008 to M002-T010 and any extras.
  - Review/fix loop: at most three rounds; all fixes and evidence in one commit.

## Dependencies and evidence

T001 → T002 → T003 → T004 → T005 → T006 → T010 → T008 → T009 → T007, then M001. Log map, checks and
review outcomes per task in `task-log.md`. Stop and report after a third review
that is not PASS.
