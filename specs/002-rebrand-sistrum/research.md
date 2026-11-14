# Research: Rebrand Mornye to Sistrum

## R1. Owner decisions (2026-11-14)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Copyright | "© 2026 AMWolfstein" plus "Based on Mornye © 2026 Zarz Eleutherius" (LGPL-3.0-only keeps the original notice) |
| 2 | Theme names in code | Keep `Mornye*` (internal design-system name) |
| 3 | Dart package | Rename `mornye` → `sistrum` |
| 4 | Compatibility identifiers | Revised: Sistrum is independent; app-owned identifiers become Sistrum, third-party ones stay (R5) |
| 5 | Upstream services | Updates from `AMWolfstein/Sistrum`; remote config off; Telegram and donation links removed; contributors list removed (owner); Zarz-LRC kept |
| 6 | What's New | Remove the Mornye 5.3 tour |
| 7 | Description translations | Translate into every supported language |
| — | Version | 0.1.0+1 (owner: test the GitHub update with 1.0.0 later) |

## R2. Android identity

- `android/app/build.gradle.kts:49` `namespace`, `:99` `applicationId` are
  `com.zarz.mornye`; manifest `package` and the cast options provider class name
  (`AndroidManifest.xml:3`, `:46`); label "Mornye Local" (`:34`).
- 144 Kotlin/Java files under `android/app/src/{main,rust,test,androidTest}/**/com/zarz/mornye`.
- JNI: `android/app/src/main/cpp/native_audio.cpp:216` macro and 18 exports in
  `native_audio.map`; one Rust reference (`rust_backend`). The JNI names follow
  the Kotlin package, so they move together.
- Method/event channels `com.zarz.mornye/<name>` on both sides (Dart services and
  Kotlin), e.g. `/backend`, `/cast`, `/covers`, `/haptics`, `/open_audio`.
  Internal: rename on both sides in one task.
- `${applicationId}` already drives the FileProvider and covers authorities.
- **Decision**: change application ID, namespace and Kotlin package together;
  keep the `mornye://` scheme (R5).
- **Consequence**: a different application ID is a different app to Android; it
  installs beside Mornye and does not inherit its data. Version 0.1.0+1 is fine
  because no earlier `com.amwolfstein.sistrum` build exists.

## R3. Dart package

- `pubspec.yaml` `name: mornye`; 1,080 files import `package:mornye/`.
  Mechanical rename in lib, test, integration_test and generated-config files
  (`l10n.yaml` and anything naming the package).

## R4. Visible name, version, description, copyright

- `lib/constants/app_info.dart`: `version`, `buildNumber`, `appName`,
  `copyright`, `mobileAuthor`, `githubRepo`, `remoteConfigApiUrl`, `kofiUrl`,
  `githubSponsorsUrl`, `whatsNewRelease`.
- `pubspec.yaml`: `description`, `version: 5.3.1+167`.
- ARB: `appName` and 43 other strings naming Mornye in `app_en.arb` (16 start
  with it), mirrored in 21 languages; `aboutAppDescription` gets the new
  description. `storageAutomaticDownloadFolder` names `Download/Mornye`.
- Code literals: download folder `/storage/emulated/0/Download/Mornye` and
  `${appDir.path}/Mornye`; exported lyrics credit `"Mornye via Paxsenix API"`
  (`rust_backend/crates/core/src/lyrics/lrc.rs:203`); notification texts.
- About page (`lib/screens/settings/about_page.dart`): contributors list
  (`githubUsername` entries from `:50`), Telegram links (`:182`, `:189`),
  copyright (`AppInfo.copyright`), repository link (`:139`).
- `NOTICE`: "Mornye / Copyright 2026 zarzet".

## R5. Identifiers (revised: owner says Sistrum is independent)

- Owner, 2026-11-14: "the app is still in development; treat me as independent
  of Mornye or SpotiFLAC". No compatibility with their files or data is kept.
- App-owned, renamed to Sistrum (task M002-T008): extension packages
  (`rust_backend/crates/extensions/src/package.rs:35` accepts `.mornye-ext`,
  `.spotiflac-ext`, `.sflx`; `extensions_page.dart:256`; ARB
  `snackbarSelectExtFile`, `extensionsNoExtensionsSubtitle`); callbacks
  `mornye://callback|spotify-callback|session-grant` (manifest intent filters,
  `auth.rs`, `signed_session/protocol.rs:20`, `extension_auth_launcher.dart`);
  user agents (`app_version.rs:37`, `network/src/client.rs:297`,
  `extensions/src/prelude.js:300`); backups (`backup_service.dart:160` magic
  `mornye-backup`, `_legacyMagic` `spotiflac-backup`, `fileExtension` `sflb`,
  file names `mornye_backup_*`; Rust `native_backup`); Cast custom data
  `mornyeToken` (`CastOutputPlugin.kt:131`, `:241`; `cast/src/sender.rs`);
  extension storage HMAC label `SpotiFLAC Mobile extension storage v2`
  (`extensions/src/storage.rs:70`, local key derivation); temp suffixes
  `.mornye-backup-*`, `.mornye-ffmpeg-*`.
- Also app-owned (found by the owner's SpotiFLAC question): the Zarz-LRC
  provider's internal id `mornye_lyrics` with its old alias `spotiflac_lyrics`
  (`providers/src/lyrics/builtin.rs:81`, `core/src/lyrics/config.rs:183`,
  `:207`, `:252`, `:261`; Dart migration `lib/models/settings.dart:714`–`:720`;
  `settings.g.dart:162` default list); the provider's old labels "Mornye Lyrics"
  and "SpotiFLAC-Mobile Lyrics" kept for credits written before a rename
  (`lrc.rs:163`–`:165` "Retain credits embedded before the provider was
  renamed", tests `:263`–`:268`); the equalizer preset format `mornye-equalizer`
  with the accepted old `spotiflac-equalizer` (`equalizer_provider.dart:131`);
  sample app names `SpotiFLAC-Mobile`/`SpotiFLAC Mobile` in lyrics test fixtures
  (`test/fixtures/native_lyrics_parsers.json`, `test/lyrics_parser_test.dart`,
  `test/lyrics_metadata_helper_test.dart`,
  `test/now_playing_lyrics_transition_test.dart`,
  `integration_test/native_data_jobs_test.dart`). The parsers are generic
  (`extract_source` reads any `[by:… (source: …)]`), so fixtures only change text.
- User-visible stale name: Vietnamese `sectionVolumeAndTransitions` ("Trình
  phát tích hợp SpotiFLAC") and `usbVolumeWarning` name SpotiFLAC
  (`app_vi.arb:180`, `:186`).
- Signed sessions (owner, 2026-11-14: remove them and their server, task
  M002-T010): an extension opts in with `signedSession` in its manifest
  (`manifest.rs:236`, `:278`, validated at `:430`); the app runs bootstrap →
  challenge (browser verification returning through `mornye://session-grant`)
  → exchange, stores the session secret, and signs each request with HMAC whose
  first line is the scheme label, default `SPOTIFLAC-HMAC-V1`
  (`signed_session/protocol.rs:22`, `:418`). 1,811 lines in
  `signed_session/`; callers in `runtime.rs`, `manager/downloads.rs:123`,
  `:139`, `environment.rs`, `lib.rs`, `mobile/src/extensions.rs`; Android
  `ExtensionAuthCallbacks.kt`, `MainActivityExtensionIntents.kt`, the backend
  classes; Dart `extension_session_callback.dart`, `platform_bridge`,
  `extension_auth_launcher.dart`. The only sample server in tests is
  `api.zarz.moe/v2/challenge`. `cover.rs:190` and `health.rs:309` send the app
  user agent only to `api.zarz.moe`. Verification UI and notifications
  (`VerificationNotificationIntent.kt`, download pauses) look tied to the
  challenge flow; T010 confirms with the map before deleting.
- Zarz-LRC (owner, 2026-11-14: remove it, task M002-T009): the built-in
  provider `mornye_lyrics` (alias `spotiflac_lyrics`) calls
  `https://api.zarz.moe/apple-music/lyrics` (`builtin/apple.rs:190`), formats
  through `payloads.rs:242`, is listed in `config.rs:119`, labelled in
  `lyrics_provider_names.dart:7`, offered in `lyrics_provider_priority_page.dart`,
  defaulted in `settings.dart:374`, re-added by migration 13
  (`settings_provider.dart:330`), credited on About (`about_page.dart:116`–`:154`,
  three `zarz.moe` links) and described by `lyricsProviderZarzLrcDesc` in 21 ARB
  files. Apple Music through Paxsenix (`AppleLyricsSource::Paxsenix`) is a
  separate path and stays.

- Full SpotiFLAC inventory (2026-11-14, `git grep -i spotiflac` outside specs
  and CHANGELOG): 40 lines in 27 files, all listed in this section, R6 (remote
  config URL and its `SpotiFLAC` regex, deleted with the service) or the
  `native_backup` lines in the backup bullet above. After M002 none remain.
- Consequences: existing extension files must be renamed to `.sistrum-ext`;
  extension sign-in servers must allow `sistrum://`; servers that gate on the
  `Mornye` user agent may need to accept `Sistrum`.
- Not in this mission: 131 files mention legacy/migration paths (settings,
  queue, secure storage, history and library schemas, `backup_rules_legacy.xml`,
  `native_backup/legacy.rs`, `legacy_host`). Many are schema steps a fresh
  install still runs, so removing them needs its own analysis and plan.

## R6. Upstream services

- Update checker: `https://api.github.com/repos/${AppInfo.githubRepo}/releases`,
  arm64 APK chosen by the `arm64` marker in the asset name, tag compared by
  `_isNewerVersion`. Setting `githubRepo = 'AMWolfstein/Sistrum'` is enough; the
  release workflow's APK name (`Mornye-${VERSION}-${label}.apk`,
  `.github/workflows/release.yml:174`) becomes `Sistrum-…` and keeps `arm64`.
- Remote config (`app_remote_config_service.dart`, `api.zarz.moe`) feeds the
  start-up announcement (`main_shell_startup_prompts.dart`) and the donate page
  (`donate_page.dart`, linked from `settings_tab.dart`). Turning it off leaves
  them without a source, so the service, the announcement prompt and the donate
  page are deleted with their strings and tests.
- What's New: `whats_new_screen.dart` (909 lines), route in `lib/app.dart:65`,
  gate `settings.whatsNewSeen == AppInfo.whatsNewRelease` (`app.dart:41`), About
  entry (`about_page.dart:209`), `AppSettings.whatsNewSeen`. With no tour, all of
  it goes.

## R7. Icons

- Source: `AMWolfstein/Private` at `9c7643b` (`desloppify/code-health`): root
  `icon.png`, `icon_android.png`, `icon_foreground_android.png`,
  `icon_monochrome_android.png` (1254×1254), `android/app/src/main/res/mipmap-*`
  and `drawable-*dpi/ic_launcher_{foreground,monochrome}.png`,
  `drawable/ic_stat_sistrum.xml` (notification small icon), `assets/images/logo.png`
  and `logo-transparent.png`; `flutter_launcher_icons` adaptive background
  `#12100E`, inset 16.
- Current: same file layout; notification small icon
  `drawable/ic_notification_mornye.xml`, referenced from
  `MornyeApplication.kt:19`, `music_player_runtime.dart:227` and
  `notification_service.dart:135`, `:261`; `assets/brand/logo/{png,svg}` Mornye
  lockups and badges, one bundled (`pubspec.yaml` assets) and drawn by the mix
  banner (`lib/widgets/mornye_mix_banner.dart:6`,
  `mornye-lockup-horizontal-white@1x.png`).
- **Decision**: copy the files above; point the notification icon to
  `ic_stat_sistrum`; delete `assets/brand/` and its `pubspec.yaml` entry; the mix banner
  draws the Sistrum logo (`assets/images/logo-transparent.png`) with the app name
  in its place. Not taken: Private's `sistrum_splash_icon.xml` and
  styles (not requested; the default splash uses the launcher icon).

## R8. Mornye history (owner, 2026-11-14: delete it)

- `CHANGELOG.md`: 1,868 lines, Mornye 3.0.0 (2026-01-14) to 4.9.6 (2026-09-07);
  nothing reads it (the release workflow builds notes with git-cliff).
- `cliff.toml`: "git-cliff configuration for Mornye", links `mornyeapp/Mornye`
  (`:8`, `owner`/`repo` at `:102`), skips `zarzet` as author (`:25`). Kept,
  pointed at `AMWolfstein/Sistrum`, without the `zarzet` rule.
- `.github/workflows/release.yml`: `notify-telegram` job (`:346`) posts to
  Mornye's Telegram; release name "Mornye <version>" (`:338`).
- `README.md`: FMHY badge (`:13`), Crowdin (`:27`, credit line `:413`), Telegram
  badges (`:36`, `:42`), release badge to `mornyeapp/Mornye` (`:21`); SVGs in
  `assets/readme/` (`featured-on-fmhy.svg`, `badges/badge-crowdin-*`,
  `badges/badge-telegram-*`).
- `crowdin.yml`: Mornye's Crowdin project mapping; `CONTRIBUTING.md:19` links it.
- About page: `_Translator` credits (`about_page.dart:464`–`:493`), Mornye's
  Crowdin translators.
- Not Mornye history and kept: `TASK_HISTORY.md` and `.specify/` (this repo's
  workflow), third-party changelogs, data-migration code that reads older
  formats (see the open question in the planning report).
