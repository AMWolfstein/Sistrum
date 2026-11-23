# M001 — Task log

Planning baseline `2b5332c`. Artwork reference `origin/wip` at `ba0e973` (approved Sistrum images). Execution and independent review evidence will be added per task.

## M001-T001 — Apply Sistrum identity across app and native contracts

Baseline `c5b2786` (product baseline `2b5332c`). Worker: Haiku 5.5 (two sessions; a container restart interrupted the first, the working tree survived). Coordinator completed verification and the pinyin fix below.

**Map refresh.** `python3 workflow/codegraph/code_tools.py update` ran before and after the edits: 2,421 sources extracted, 55,578 symbols, 208,229 relationships, 0 partial, 0 unindexed. The first-action refresh output of the worker session itself was not captured; the pre-edit snapshot is the session-start run with identical counts. Android/phone and shared Dart/Kotlin/Rust paths were inspected; TV, iOS, macOS, Windows, Linux shells were excluded.

### Chosen identifiers (owner rule: `zarz`→`amwolfstein`, `mornye`→`sistrum`, case/shape preserved, identity only; the Mornye theme is unchanged)

| Area | Old | New |
|---|---|---|
| Product / display name | Mornye | Sistrum |
| Dart package | `mornye` | `sistrum` (`package:sistrum/…`) |
| Android namespace + applicationId | `com.zarz.mornye` | `com.amwolfstein.sistrum` |
| Kotlin/Java source dirs | `com/zarz/mornye` | `com/amwolfstein/sistrum` (`git mv`) |
| Rust crates | `mornye-{core,network,providers,extensions,link,cast,mobile,bindgen,desktop}` | `sistrum-*` (v0.1.0 in Cargo.lock) |
| Native libs | `mornye_audio`, `mornye_discord`, `mornye_mobile` | `sistrum_audio`, `sistrum_discord`, `sistrum_mobile` |
| JNI symbols | `Java_com_zarz_mornye_*` | `Java_com_amwolfstein_sistrum_*` |
| App class | `MornyeApp` | `SistrumApp` |
| Deep-link scheme | `mornye` | `sistrum` |
| Build env | `MORNYE_*` (Android/Rust path) | `SISTRUM_*` |
| pubspec version | `5.4.0+169` | unchanged (owner has not chosen a new one) |

Kept as Mornye (theme or external): all theme Dart identifiers/files (`MornyeTheme*`, `MornyeGlass*`, `MornyeAccent*`, `lib/theme/mornye_*.dart`, `context.isMornye`), theme selection keys and stored values (`theme_style`, `mornye_accent`, `mornye_glass_*`, theme value `mornye`), test labels `Mornye=$mornye`, performance suite names `mornye_*`; Libre.fm secret, release secret and SignPath slug, `.mornye-ext` extension format, GitHub/URL/domain references, `zarzet` owner handle, copyright holder, Zarz-LRC service; format magic `mornye-pinyin 1` (see below).

### Findings fixed during verification
- First pass over-renamed theme meanings (523 lines in lib/test/integration_test, e.g. `(Mornye=$mornye)` test labels and `mornye_chrome_` suite names); restored to the baseline text.
- **Regression found by the baseline comparison:** `lib/services/romanization/pinyin.dart` checked the data header `sistrum-pinyin 1`, but `assets/romanization/pinyin.txt.gz` (binary, not rewritten) starts with `mornye-pinyin 1`, so 6 lyrics tests failed (`Unknown pinyin data`). The header is a data-format magic string, so the code and `scripts/build_pinyin_data.dart` keep `mornye-pinyin 1`. Scan of the other binary/compressed assets and fixtures found no other embedded old names.

### Remaining `mornye`/`zarz` classification (heuristic, spot-checked)
`mornye` ≈6,391 lines in 644 files: theme identifiers/keys/values/files/comments (most); untouched non-Android shells and desktop/iOS scripts (~350, removed in T006); external contracts and URLs (~94); workflow/specs/agents not edited (~54). `zarz` 213 lines in 73 files: owner handle `zarzet`, copyright holder, Zarz-LRC service, and `com.zarz.mornye` inside iOS/Windows/Linux/macOS shells and `apps.json` (T002/T006).

### Known leftovers (out of T001 scope)
- The workspace `mornye-desktop` crate is now `sistrum_desktop`; the Linux/Windows/macOS shells and desktop CI jobs still use `MORNYE_*`/`libmornye_desktop` names and will not match until T006 removes them.
- iOS podspec/Podfile still reference `mornye_discord`/`MornyeBackend`; the `mornye_discord` plugin directory and package name are kept because iOS references them. Brand asset filenames belong to T002.

### Checks (real results, working tree vs baseline `c5b2786` worktree)
- `flutter analyze`: new 1 info, baseline the same 1 info (`workflow/codegraph/fixtures/bridge/lib/api.dart:5`); 0 errors/warnings.
- `flutter test` full suite, JSON reporter: baseline 7885 pass / 20 failure / 38 error (7943); new 7880 / 20 / 43 (7943). After matching tests by file+name: 57 common failures; 1 baseline-only failure (`link_phone_sources_test`, passes in the new tree); 6 new-only failures all caused by the pinyin header and fixed; `test/lyrics_romanization_test.dart` + `test/lyrics_translation_provider_test.dart` rerun: 55 passed. The 57 common failures are baseline failures (native plugin/SQLite/timeouts in this environment). The full suite was not rerun after the one-line pinyin fix.
- `cargo check --manifest-path rust_backend/Cargo.toml --workspace --locked` with `RUSTUP_TOOLCHAIN=1.100.0`: PASS, 0 warnings (without the pinned toolchain it fails on a rustc version mismatch, an environment issue).
- `python3 workflow/work.py quality bridge verify`: baseline and new tree both FAILED with 143 contracts, 0 verified, 3 failed (same IDs); the 3 are intentional negative fixtures under `workflow/codegraph/fixtures`. Static coverage 4.5 %; 141 contracts unresolved at both trees. App JNI/channel contracts were not newly failed.
- Static JNI/loader review: NativeAudio 18 `external fun` ↔ 18 JNI definitions; Discord 5 ↔ 5; `System.loadLibrary("sistrum_audio")`/`sistrum_discord` match CMake targets; UniFFI `cdylib_name = sistrum_mobile`; Dart FFI lookups match Rust exports; manifest components and scheme resolve; ProGuard uses the new packages.
- `python3 -m unittest discover -s workflow -p 'test_*.py'`: 47 OK. `scripts/test_build_android.py` 10 OK, `scripts/test_check_backend_apk.py` 9 OK. `bash -n` on changed shell scripts OK. `python3 workflow/work.py validate` PASS.

### Compatibility and divergence notes (review round 1, advisory)
- **Data-format break (intended, no Mornye migration per spec):** equalizer presets now use the `sistrum-equalizer` magic (legacy `spotiflac-equalizer` still accepted; `mornye-equalizer` files from Mornye are no longer importable) in `lib/providers/equalizer_provider.dart`; backups use `sistrum-backup` in `lib/services/backup_service.dart` and `rust_backend/crates/mobile/src/native_backup/archive.rs` (Dart and Rust sides are consistent). The lyrics provider id is now `sistrum_lyrics` (legacy `spotiflac_lyrics` still accepted).
- **External service risk:** the User-Agent sent to `api.zarz.moe` (`rust_backend/crates/extensions/src/backend/cover.rs`) changed from `Mornye/<ver>` to `Sistrum/<ver>`; whether that server filters on it was not verified.
- **Dart-side contracts now diverge from untouched shells:** Dart channels `com.amwolfstein.sistrum/{link_now_playing,playback_notification,taskbar,window}` have no native counterpart in the iOS/desktop shells, which still use `com.zarz.mornye/…` (e.g. `ios/Runner/LinkNowPlayingBridge.swift`, `third_party/mornye_discord/ios/Classes/DiscordPresencePlugin.mm`), plus `scripts/check_backend_ios.py` (`_uniffi_mornye_mobile_`) and the `MornyeBackend` podspec. T006 removes these shells.

### Not verified
Device registration of JNI/UniFFI names, notifications and native behavior on hardware (no device here); external services; desktop/iOS shells; full `flutter test` after the pinyin fix (affected files rerun only); bridge static coverage beyond 4.5 %.

### Review
Round 1 (independent, read-only): NEEDS_FIXES with one blocking item, the planned commit subject (86 chars; limit 72). No code issues found. Fix: subject `refactor(android)!: apply Sistrum identity (M001-T001)` (passes `commit-check`); advisory notes above added to this log. Round 2 (independent, read-only, same reviewer resumed): PASS. Verified the subject, log notes and `PlayerWidgetConfigurationActivity`; no other change since round 1. Reviewer caveats: device/JNI registration, `api.zarz.moe` behavior, desktop/iOS shells and a full `flutter test` rerun after the pinyin fix remain unverified.

## M001-T002 Match Sistrum artwork to original asset layouts

### Map refresh
- `python3 workflow/codegraph/code_tools.py update` at start: 55,578 symbols, 208,229 relationships, 2,421 extracted sources, 0 partial, 0 unindexed.

### Copied from `ba0e973` (image source only, byte-for-byte)
- 26 files: `icon.png`, `icon_android.png`, `icon_foreground_android.png`, `icon_monochrome_android.png`, `assets/images/logo.png`, `assets/images/logo-transparent.png`, `assets/images/sistrum-horizontal-{light,dark,white}.png` (new), 5 `mipmap-*/ic_launcher.png`, 5 `drawable-*/ic_launcher_foreground.png`, 5 `drawable-*/ic_launcher_monochrome.png`, `drawable-nodpi/ic_launcher_background.png` (new), `mipmap-anydpi-v26/ic_launcher.xml`, `drawable/launch_background.xml`.
- `drawable/ic_notification_sistrum.xml`: content of WIP `ic_stat_sistrum.xml` under the existing referenced name (6 references in Kotlin, Dart, and tests kept unchanged).
- Hash check: all 26 blob hashes equal `git rev-parse ba0e973:<path>`; the notification file equals the WIP `ic_stat_sistrum.xml` blob (`90f5ee4`).

### Dimension and alpha table (HEAD → new)
- Unchanged size and alpha: `icon.png` 1024² RGB, `icon_android.png` 512² RGB, `icon_(foreground|monochrome)_android.png` 432² RGBA, `logo.png` 1024² RGB, `logo-transparent.png` 608×800 RGBA, every foreground/monochrome density (162/108/216/324/432 px) RGBA.
- **Alpha mode changed (accepted, plan says "as in WIP"):** the five `mipmap-*/ic_launcher.png` files were RGB at HEAD and are RGBA in WIP. Their alpha channel is 255 everywhere, so the pixels are fully opaque. Sizes 72/48/96/144/192 px are unchanged.
- New files: `sistrum-horizontal-{light,dark,white}.png` 703×200 RGBA (same as the Mornye lockups they replace); `drawable-nodpi/ic_launcher_background.png` 1080² RGB.

### Derived variants and deletions
- No variants needed deriving. Every shared or Android reference is now satisfied by an approved WIP PNG, or the remaining file is for a non-Android shell.
- Deleted 58 unreferenced Mornye brand files under `assets/brand/logo/{png,svg}` (lockups, marks, wordmarks, badge@2x/@4x, and the stacked and black variants).
- Kept `assets/brand/logo/png/mornye-badge@1x.png` and `assets/brand/logo/svg/mornye-badge.svg`, which the Windows (`pubspec.yaml`) and Linux (`linux/CMakeLists.txt`, `scripts/package_linux.sh`) packaging still use until T006.
- `assets/brand/README.md` rewritten to match the remaining files.
- Root `logo.svg` left unchanged: unreferenced, byte-identical to WIP, and already the Sistrum glyph.

### Reference updates
- `README.md` header: light/dark `<picture>` now uses `assets/images/sistrum-horizontal-{light,dark}.png`.
- `lib/widgets/mornye_mix_banner.dart`: only the asset path changed to `assets/images/sistrum-horizontal-white.png`. Class and const names, layout and logo width are unchanged. The const keeps its name `mornyeLockupAsset` (theme-named).
- `pubspec.yaml`: launcher `adaptive_icon_background` and `background_color_ios` set to `#12100E`. Flutter asset entry now `assets/images/sistrum-horizontal-white.png`. Windows badge config unchanged.
- `android/app/src/main/res/values/colors.xml`: `ic_launcher_background` set to `#12100E`. It is now unused by the adaptive icon (which uses the drawable PNG) and kept for consistency.
- `apps.json` `iconURL` points at `raw.githubusercontent.com/mornyeapp/Mornye/main/assets/images/logo.png` (an upstream repo URL, not a local file). Kept unchanged.
- Dart callers (`setup_steps.dart`, `about_page.dart`, `lyrics_share_screen.dart`, `listening_rewind_share_card.dart`) keep their paths; the files behind those paths changed.

### Background color measurement
- `ba0e973:icon.png` (and the WIP `icon_android.png`): dominant pixel `(18,16,14)` = `#12100E`, covering about 86% of the 1024² image. Chosen value: `#12100E`.
- WIP `drawable-nodpi/ic_launcher_background.png` is not flat: a 1080² RGB gradient, roughly `#080809` to `#241800`, mean about `#110D0B`. It is near black, close to `#12100E`, but not identical. The adaptive icon uses this PNG; the pubspec color is only used by the generator and iOS.

### Visual inspection (PIL composites, rendered and viewed)
- Launcher masks (circle and squircle) over `ic_launcher_background.png` with `icon_foreground_android.png`: gold mark centered, within the safe zone, clean edges. Monochrome renders as a white silhouette on a dark tile.
- `sistrum-horizontal-light/dark/white` on white, dark (`#121616`) and teal (`#1E4E4F`): legible in each mode. Light uses dark ink; dark uses cream; the white variant suits the teal Top Picks card.
- `icon.png`, `icon_android.png`, `logo-transparent.png`: consistent gold mark on near-black tile and transparent background.
- Notification small icon: the WIP `ic_stat_sistrum` is a full traced mark, not a simple glyph. It reads as a white silhouette at 24 dp but is more detailed than typical status-bar icons. The T001 stroke glyph it replaces was simpler. Worth checking on a device.

### Checks
- Blob hash match for all 26 copied paths and the notification content: PASS.
- Static Android resource scan (`@mipmap/`, `@drawable/`, `@color/` in main manifest, res and Kotlin): 103 references, all resolve. The one flagged name was the wildcard keep rule `@drawable/ic_notification_*` in `raw/notification_keep.xml` (not a missing reference).
- Removed-path `git grep`: no references to deleted `mornye-lockup`, `mornye-mark`, `mornye-wordmark` or `mornye-badge@2x/4x` anywhere outside `specs/`, `workflow/`. Remaining badge references listed above.
- `flutter pub get` PASS. `flutter analyze`: 1 issue, the baseline info at `workflow/codegraph/fixtures/bridge/lib/api.dart:5`. PASS against baseline.
- Targeted `flutter test` PASS, 271 tests: `library_mix_ui_test`, `listening_rewind_backdrop_test`, `listening_rewind_test`, `lyrics_share_screen_test`, `notification_service_test`, `mornye_detail_headers_test`, `settings_search_test`, `now_playing_lyrics_transition_test`.
- Full `flutter test` (JSON): 7,865 pass, 25 skipped, 19 failures, 39 errors (7,948 total). T001 baseline was 7,880 pass, 20 fail, 43 error (7,943). 58 failing names.
- Failure comparison against a detached HEAD worktree (since removed): 57 of the 58 fail identically at HEAD (verified by file-scoped runs over library search, SAF, native directory statistics, folder templates, Audio info, hires check card, desktop Library table, semantics geometry, band mask and link-phone tests). The band-mask cases stall in isolation at HEAD and in the working tree, so their result is from the full run. `recover asks a connected phone again for a pruned received song` (in `link_phone_sources_test.dart`) passes in isolation in both trees. It failed only in the full run, so it is load-sensitive. No new failure touches brand, icon, banner, notification or share-card code.
- `python3 workflow/work.py validate`: PASS (`missions 1, reserved 6, completed 3`). `python3 -m unittest discover -s workflow -p 'test_*.py'`: 47 OK.

### Not verified
- No device or emulator: splash (`launch_background`), adaptive and monochrome icon on a real launcher, notification rendering in the status bar, and widget/Top Picks layout on the phone.
- The launcher-generator config (`flutter_launcher_icons`) was not rerun; resource files were copied from WIP directly.
- The WIP background PNG is not the same color as `icon.png`. Both are near-black, so the difference should not be visible, but this was judged from pixels, not on a device.
- Five legacy `mipmap` PNGs are RGBA (fully opaque) rather than RGB. Harmless, but not identical to HEAD's alpha mode.

### Review (M001-T002)
Round 1 (independent, read-only; same reviewer as T001, resumed): PASS. Verified 26 copied blobs against `ba0e973` (identical), notification drawable equals WIP `ic_stat_sistrum.xml` with all references resolving, IHDR sizes unchanged, zero dangling references for the 58 deleted files, scope clean (no TV/iOS/macOS/Windows/Linux, theme, workflow changes). Advisory: the five legacy mipmap PNGs moved RGB to RGBA by design ("as in WIP", byte-for-byte copy); `launch_background.xml` now uses `?android:colorBackground` instead of white (WIP file copied as-is); `colors.xml` `ic_launcher_background` is unused by the adaptive icon (which uses the PNG background); `apps.json` `iconURL` is an upstream URL left unchanged; rerunning `flutter_launcher_icons` would overwrite `ic_launcher.xml` and needs the inset checked. Unverified: device rendering, notification glyph legibility at 24 dp, Top Picks card on a phone.

## M001-T003 — Verify complete rebrand before removing features

Baseline `e398328` (T001 `c92c731`, T002 `e398328` committed). Worker: Haiku 5.5 (analysis, test comparison, build orchestration; the owner stopped that agent before it wrote this section). The coordinator ran the APK checks below and wrote this record. Map refresh: `python3 workflow/codegraph/code_tools.py update` first action of the worker (2,421 sources extracted, 0 partial, 0 unindexed, as in T001/T002).

### Owner-requested artifact
Release APK, **arm64-v8a only, signed with the development (Android Debug) key** (`android/key.properties` absent, so `release` uses the debug signing config unchanged).
- Command: `bash scripts/build_android.sh --lite --target lib/main.dart` with `SISTRUM_RUST_ANDROID_ABIS=arm64-v8a`, `SISTRUM_DISCORD_SDK=0` (**Lite**: the Discord SDK archive is gpg-encrypted and unavailable here; `libsistrum_discord.so` is a 4 KB stub), `RUSTUP_TOOLCHAIN=1.100.0`, `ANDROID_NDK_HOME` = NDK 30.0.16248370, Flutter 3.50.0, JDK 25.
- Result: `Built build/app/outputs/flutter-apk/app-arm64-v8a-release.apk (87.3MB)`; the script audit `scripts/check_backend_apk.py --backend rust --abis arm64-v8a` printed `OK` for both produced APKs. Copy kept at `build/Sistrum-arm64-release-devkey.apk` (git-ignored), 87,279,973 bytes, sha256 `2e97a4e0381a3ebc3e17527a26319a336e016420cb6cf57933fe9c29312c0f2f`. Gradle `assembleRelease` took 5,136 s on a cold cache.
- Environment workarounds (outside the repo): github.com archive downloads return 403, so a Gradle init script in `~/.gradle/init.d/sistrum-local-sources.gradle` passes `-DFETCHCONTENT_SOURCE_DIR_{FLAC,OBOE,WAVPACK}` to the local clones at the exact tags (FLAC 1.5.0, Oboe 1.11.0, WavPack 5.9.0), and the proxy closes Maven tunnels, so a local retrying Maven mirror (`127.0.0.1:8765`) fronted Gradle dependency resolution. No repo source was edited for the build.

### APK checks (real output)
- `apkanalyzer manifest application-id` = `com.amwolfstein.sistrum`; `aapt2 dump badging`: `application-label:'Sistrum'`, `versionName='5.4.0'` (unchanged), `versionCode='2169'` (Flutter code plus the arm64 split offset), `targetSdkVersion='37'`, `native-code: 'arm64-v8a'`, launchable activity `com.amwolfstein.sistrum.MainActivity` (a leanback launchable entry also exists; TV is removed in T006).
- Dex: packages `com.amwolfstein.sistrum` and `...backend` (UniFFI `UniffiLib`) present; 0 `com.zarz` packages; 0 `mornye` strings in the resource string pool.
- Native libs: all 19 under `lib/arm64-v8a/` only; `llvm-readelf`: every library `Machine: AArch64`, LOAD alignment `0x4000` (16 KB) or `0x10000`; `zipalign -c -P 16 -v 4`: `Verification successful`; 18 `Java_com_amwolfstein_sistrum_NativeAudio_*` exports in `libsistrum_audio.so` (matching the 18 `external fun`s), 5 `Java_..._discord_DiscordPresencePlugin_*` in `libsistrum_discord.so` (matching the 5 JNI definitions), 357 `uniffi` exports in `libsistrum_mobile.so`; no `com.zarz`/`Java_com_zarz` strings in the three `libsistrum_*` libraries (a case-insensitive `mornye|zarz` search of `libsistrum_mobile.so` still finds the intentionally kept external contracts: the `.mornye-ext` extension-format messages and the `https://api.zarz.moe/apple-music/lyrics` URL).
- Signature: `apksigner verify --verbose --print-certs`: Verifies; v2 scheme true (v1/v3/v4 false for a debug-signed APK); signer `CN=Android Debug, O=Android, C=US`, SHA-256 `bb14c2aeed85d34094668b45b136be63a80080256a1d2c6fad78a2f6110c0245`. **Development key only; not a release/production signature.**
- Merged-manifest permissions recorded, nothing removed in this task (T005 removes online permissions): `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK`, `CHANGE_WIFI_MULTICAST_STATE`, `RECORD_AUDIO`, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `READ_MEDIA_{AUDIO,VIDEO,IMAGES}`, `READ_EXTERNAL_STORAGE` (max 32), `WRITE_EXTERNAL_STORAGE` (max 29), `FOREGROUND_SERVICE{,_MEDIA_PLAYBACK,_DATA_SYNC,_CONNECTED_DEVICE}`, `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`, `ACCESS_WIFI_STATE` and the app's own `...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

### Source checks
- `flutter analyze`: 1 info, identical to baseline (`workflow/codegraph/fixtures/bridge/lib/api.dart:5`); 0 errors/warnings.
- Full `flutter test` (JSON): 7891 pass, 20 failure, 37 error = 57 failing vs 58 at baseline `c5b2786`. After name normalization: no new failure except `test/link_phone_sources_test.dart: metadata asks the phone for embedded lyrics missing from the catalog`, which passes alone (`+37: All tests passed!`, load-sensitive; the same file's `resolve fails when the phone cannot send the song` failed at baseline and passes now); two baseline failures now pass. The remaining 56 are the same environment failures as at baseline (native plugins, SQLite, timeouts).
- `cargo check --manifest-path rust_backend/Cargo.toml --workspace --locked` with `RUSTUP_TOOLCHAIN=1.100.0`: PASS. The working tree has one tracked change from the build, `rust_backend/Cargo.lock`: cargo re-sorted the renamed `sistrum-*` packages (219 lines re-sorted, same 399 packages with identical name/version/source/checksum and dependency sets, no version changes); included in this task's commit.
- Theme persistence keys/values vs `c5b2786` (`lib/models/theme_settings.dart` and the key constants in `lib`): identical except the import prefix; the only differing constants are the retired upgrade-intro key and the desktop env name `SISTRUM_DESKTOP_LIB`, outside the theme.
- `python3 workflow/work.py validate`, `python3 -m unittest discover -s workflow -p 'test_*.py'`, `scripts/test_build_android.py` and `scripts/test_check_backend_apk.py`: PASS (worker logs `t003_*.log`).

### Rebrand sweep (user-visible `Mornye` remaining)
- English ARB has only theme strings and the `.mornye-ext` extension-format text (`lib/screens/extensions_page.dart` functional/external; report only); About page links `t.me/mornyeupdates`, `t.me/mornyecommunity` are external; tracked `GeneratedPluginRegistrant.java` logs the `mornye_discord` plugin package name (the plugin directory keeps its name for iOS). No clear identity leftovers found in Android phone/shared code; no source edits in T003.

### Not run / not verified
- `flutter test integration_test/android_background_playback_test.dart -d "$ANDROID_SERIAL"` and the device theme walkthrough: **not run**, no device or emulator in this container.
- Clean install showing Sistrum on a launcher/splash/notification, first run, local playback, theme selection/persistence and rendering, and JNI/UniFFI registration at runtime were not exercised on a device; only the static APK, manifest, dex and ELF evidence above exists.
- Online routes and external services (providers, `api.zarz.moe`, update checks) were not exercised.
- Production Discord (Lite build), release signing and a production keystore are out of scope.

### Deferred to M001-T007 [FINAL]
The device-only acceptance of T003 (background playback integration test, theme walkthrough, clean-install launcher/splash/notification check, runtime JNI/UniFFI registration, first run and local playback) was **not performed** because no device or emulator is available; it is deferred to the authorized device tests T007 already requires, not claimed as done.

### Review
Round 1 (independent, read-only; same reviewer as T001/T002, resumed): PASS. The reviewer re-ran its own APK checks (sha256/size, application-id, label, version, arm64-only libs, ELF machine/alignment, `zipalign -c -P 16`, apksigner v2/debug key, JNI exports, dex strings), recomputed the test comparison from the JSON (57 failing vs 58 at baseline; one load-sensitive new name; two baseline names now pass), and compared `Cargo.lock` programmatically (pure reorder). Advisory corrections applied above (Discord export count 5, external strings in `libsistrum_mobile.so`, deferral record). Unverified by the reviewer: device install/launch, theme rendering, runtime registration, online services, a production-signed release, the Discord SDK path.

## M001-T004 — Make local library and phone playback self-contained

Baseline `2ee4842` (T001-T003 committed). Worker: Haiku 5.5. Map refresh first: `python3 workflow/codegraph/code_tools.py update` (55,578 symbols, 208,229 relationships; 2,421 sources extracted, 0 partial, 0 unindexed). Not committed; `task complete` not run.

### Audit: what waits on the network, and where
Startup before `runApp` (`lib/main.dart`) awaits only local work: preferences (196), install marker and restored-install reset (441-456), startup SAF tree check bounded to 2 s with errors mapped to accessible (476-487), runtime profile from device info (511). No network call precedes the first frame. **No change needed.** Read, not exercised under airplane mode.

After the first frame, network work is detached or bounded:
- `lib/main.dart:595-650` (`_initializeAppServices`, `_initializeExtensions`, deferred warmups): unawaited, local/native. **No change.**
- `lib/screens/main_shell.dart:~318-340`: playback restore needs no network. `_restorePlayback` waits only for native extension init before artwork recovery. Extension store refresh (`extension_auto_update_provider.dart:84-123`, network) is detached. **Deferred to T005** (Store/online).
- `lib/screens/main_shell_startup_prompts.dart:94-118`: update check (`lib/services/update_checker.dart:290`, 10 s timeout, errors caught at 320) gates only the update dialog and the announcement after it (`app_remote_config_service.dart:405`, 8 s timeout, errors caught 431/485). Local UI does not wait. **Deferred to T005.** A never-answering-network test was added (below).
- `lib/screens/setup_screen.dart:355-377` and `setup_steps.dart:333`: the Link nearby-devices permission is refusal-tolerant (Next enables after refusal). **Deferred to T005.**
- `lib/screens/settings/settings_tab.dart:155-166` (donate, remote config): online display only. **Deferred to T005.**
- Local library covers and autoplay skip `http(s)` sources (`local_library_provider_covers.dart:76-85`, `music_player_autoplay.dart:87`, `library_mixes.dart:576`). **No change.**

### Fresh schema and settings
- Library database: fresh-schema test exists (`test/library_schema_test.dart:198`). Legacy upgrade tests (v1 to v20, same file) are kept: they are covered by tests and were not proven dead. Upgrade code is not removed in T004.
- App state (playback session, resume) opens fresh through `AppStateDatabase` (`lib/services/app_state_database.dart:47-70`). Settings defaults and setup-completion persistence: `test/settings_persistence_test.dart:86-121`. **No change.**

### Persistence across restart
- Existing: download-queue restart tests (`test/download_queue_persistence_test.dart:87`, `:158`, `:621`), library queue store tests, playback session text round-trip (`test/playback_session_storage_test.dart:103`).
- Added `test/playback_session_restart_test.dart`: a saved queue, index, position, shuffle and repeat written to a file-backed store, closed, reopened and restored through `prepareRestoredPlaybackMedia`. A second case restores with an unreadable card and keeps every entry.

### Removable storage: the decision
Rule: a file whose storage is not mounted is **unavailable**, never missing. Unavailable entries are kept (not dropped from the queue, no history row removed, no library row removed, no file reported deleted). Only a confirmed missing file on available storage is removed, as before. Primary storage keeps its behaviour.

Plain paths (`/storage/<id>/...`, `/mnt/media_rw/<id>/...`): the existing helper `_storageAvailableFor` (`lib/utils/file_access.dart:389-410`) walks up to the nearest existing folder. If that folder is a mount container (`isStorageMountContainer`, `:412-440`: `/storage`, `/mnt`, `/media`, `/Volumes`, `/run/media`, `/storage/emulated`), the storage is gone. `/storage/emulated/0/...` stops at an existing folder under primary storage and stays available. The public wrapper `storageUnavailableFor` (`file_access.dart:419-432`) uses it. This is pure Dart, no channel method. **Assumption: Android removes `/storage/<volume-id>` on unmount; see Not verified.**

SAF documents (`content://.../document/<id>:...`): `android/app/src/main/kotlin/com/amwolfstein/sistrum/SafVolumePolicy.kt` `safVolumeUuidToCheck(authority, documentId)` decides whether a volume must be checked:
- Only authority `com.android.externalstorage.documents` names a volume by uuid before the colon. Every other authority (media `audio:`/`document:`, `msf:`, Downloads `raw:`, `home:`, any other provider) returns no check, so its answer is the provider query exactly as before.
- Volume ids `primary`, `emulated`, `home`, `raw`, empty, or no colon: no check.
- Any other id (for example `1234-ABCD:Music/a.mp3`) is checked against mounted `StorageVolume`s (`MainActivitySafCalls.kt` `safVolumeMounted`, Android 11+ only; earlier versions keep the provider answer).
- `handleSafExists` (`MainActivitySafCalls.kt`) still runs the query. On an unverifiable (unmounted) volume a found row answers **true**; an empty result or a loading provider answers unknown (`null`); a query exception answers unknown. Only a completed empty query on a verifiable volume answers false. So a live document is never reported missing because of the volume guard.
- The Dart bool wrapper `PlatformBridge.safExists` maps null to false. Its callers (`file_access.dart:307` `fileExists`, and the `playback_session_restore.dart`, `music_player_service.dart:1317`, `local_playback.dart:38`, `track_file_resolver.dart:83` callers) get the same false as before for unknown answers; they are not destructive. The destructive callers use the tri-state `safExistsState` (`file_access.dart:360` `storedFileState`, `:423` `storageUnavailableFor`, `:509` `deleteFile`).

Where the decision is applied:
1. Restore: `lib/services/playback_session_restore.dart` `_isKeptUnlessMissing` keeps a file whose check is missing when `storageUnavailable` is true (default `files.storageUnavailableFor`). Keeping it also skips recovery, so the queue is not rewritten and the session is not cleared.
2. Play-time skip: `lib/services/music_player_failures.dart:91-93` returns false (keep, report a playback error) when the storage is unavailable. The dependency is `storageUnavailable` on `MusicPlayerRuntime` (`music_player_runtime.dart:84`, `:152`), injectable for tests.
3. Deletion: `lib/utils/file_access.dart` `deleteFile` local branch (~:518) returns false for an absent file on unavailable storage, so nothing is published as deleted. SAF branch: a null `safExistsState` keeps `confirmedAbsent` false.
4. History rows: `storedFileState` (`file_access.dart:360`, `_localFileState` `:368-387`) already returned unknown through `_storageAvailableFor`. Unchanged; covered by `test/stored_file_state_test.dart` "cannot be told".
5. Library cleanup: `cleanupMissingLibraryRows` (`lib/services/library_cleanup.dart:439-444`) uses `fileExistenceByPath`, which uses the same `_localFileState` (unknown on unmounted storage). Unchanged; covered by `test/stored_file_state_test.dart` (unplugged path returns null).

Restore on remount: nothing was dropped, so the same saved entries resolve again when the card returns (test below). No re-add is needed.

### Findings and changes
1. **Unreadable saved file dropped from the restored queue** (kept). `playback_session_restore.dart` used `File.exists()`, which reads EACCES/I/O errors as missing. Now only `FileAccessMissing` counts as absent (`checkFileAccess`). Test: "an unreadable saved file stays queued while a confirmed missing one is dropped".
2. **SAF existence read as "missing" on a lost grant** (kept, tightened in review). `handleSafExists` used `DocumentFile.exists()`, which swallows provider and security exceptions. It now returns `null` for unknown answers as described above. `lib/services/platform_bridge.dart`: `safExistsState` (`bool?`); `safExists` keeps its `bool` contract (`?? false`).
3. **Unplugged card: restore and play-time skip dropped entries** (this change). Restore and `music_player_failures.dart` treated `FileAccessMissing` as removable. An unplugged card gives ENOENT, so entries were dropped and the session could be cleared. Both now consult `storageUnavailable`.
4. **Review regression, fixed.** The first SAF guard treated the text before ':' of every document id as a volume uuid and answered unknown for every other provider (`audio:`, `msf:`, `home:`, `raw:`, other authorities), so live documents came back unknown/false. Now only the ExternalStorage authority is checked, and the policy is a pure function with a JVM test (see Tests).
5. Test-only correction: `test/music_player_unsupported_formats_test.dart` "Android SAF skips only confirmed absence". The mock never answered `safExists`, so the new unknown kept the entry. The mock now answers `safExists` as false for `missing` (provider-confirmed absence, as on mounted storage) and null for a new `unmounted` case, which must stay queued. All 5 cases pass.

Earlier worker reports wrongly said `storedFileState` and library cleanup dropped unplugged entries. Re-reading showed the local path already returned unknown through `_storageAvailableFor`; the real gaps were items 3 and 2.

### Tests added or changed
- `android/app/src/test/kotlin/com/amwolfstein/sistrum/SafVolumePolicyTest.kt` (new, 4 JUnit tests): only the ExternalStorage authority yields a volume to check (`1234-ABCD:Music/a.mp3`); `primary:Music/a.mp3`, `primary:`, `emulated:0/...`, `home:`, `home:Music/a.mp3`, `raw:/storage/emulated/0/x`, `:x` and a bare `1234-ABCD` yield none; `audio:123`, `msf:123`, `document:123`, `raw:/storage/emulated/0/x`, `1234-ABCD:Music/a.mp3` and `home:` yield none under media, downloads and other authorities; a null authority or id yields none. Run: see Kotlin verification.
- `test/removable_volume_availability_test.dart` (new, 6): unplugged local path is unavailable (with CUE suffix); missing file in a present folder is not unavailable; delete of an unplugged file is not a deletion and publishes nothing; SAF unknown (unmounted) is unavailable; SAF confirmed missing on mounted storage is not; SAF unknown is never reported deleted.
- `test/playback_session_restore_test.dart`: "entries on an unmounted card stay queued; they play again once it returns" (keeps all three, no recovery, no rewrite; then remount resolves the same entries) and "a truly missing file on available storage is still dropped". The earlier unreadable-file test now injects `storageUnavailable`.
- `test/music_player_unsupported_formats_test.dart`: SAF group extended with the `unmounted` case.
- `test/playback_session_restart_test.dart` (new, 2), `test/saf_exists_state_test.dart` (new, 3; Dart contract for the channel's `bool?` answer, including null staying unknown) and `test/update_checker_test.dart` never-answers (10 s): from the previous passes.

### Kotlin verification
- `cd android && ./gradlew :app:compileReleaseKotlin --offline -Ptarget-platform=android-arm64`: **BUILD SUCCESSFUL**, task executed, after the final `MainActivitySafCalls.kt` edit (`t004d_compile2.log` in the worker scratchpad).
- `SafVolumePolicyTest` (4 tests): **PASS, run directly with JUnit 4.12** (`OK (4 tests)`). The project's Gradle debug unit-test task (`:app:testDebugUnitTest`) cannot resolve its classpath offline here (`io.flutter:flutter_embedding_debug` not cached), and there is no release unit-test task. The test was compiled with the cached Kotlin 2.4.21 compiler and run with `org.junit.runner.JUnitCore`. Gradle's own test run is not executed.
- The `StorageManager` check and the SAF behaviour on real volumes are **compiled only, not run on a device**.

### Checks (real output)
- `flutter analyze` (full project): 1 issue, the baseline info at `workflow/codegraph/fixtures/bridge/lib/api.dart:5`. PASS against baseline.
- Targeted `flutter test` (9 files: the new and changed tests above, plus `playback_path_resolution_test.dart` and `track_collection_action_policy_test.dart`, which use `local_playback`/`track_file_resolver` paths): **94 tests, 0 failing** (`t004d_targeted.json`).
- Final full `flutter test --reporter json` over 803 files, excluding `test/desktop_library_table_rows_test.dart` (see below), file `t004e_full.json`: 7,961 non-hidden test results = 7,883 pass, 53 fail, 25 skip. Excluding 3 loader/setUp pseudo-entries: 7,958 = 7,880 pass, 53 fail, 25 skip. Distinct test names: 7,951, of which 53 have a failing result.
- Versus T003's 57 failing names (`t003_test.json`): **no failing name in `t004e_full.json` is new**. All 53 are in T003's failing set. Of the 57 T003 names, 4 are not failing in this run: 1 executed and now passes (`link_phone_sources_test.dart` "metadata asks the phone for embedded lyrics missing from the catalog", load-sensitive, also passes alone), and 3 were **not executed** (the `desktop Library Songs table` tests below). 57 - 4 = 53.
- **`desktop_library_table_rows_test.dart` was not executed in this run.** The three `desktop Library Songs table` tests do not appear in `t004e_full.json`. The file was excluded because it stalls: run alone with a 600 s bound it reported one result and then sat on "desktop Library Songs table a click, a resize and playback keep every loaded row" until killed. T003 recorded those three tests as errors. They are not shown as passing.
- `python3 workflow/work.py validate`: PASS (`missions 1, reserved 4, completed 5`).
- `python3 -m unittest discover -s workflow -p 'test_*.py'`: 47 OK.
- `cargo check`: not run; no Rust changed.
- `flutter test integration_test/android_background_playback_test.dart -d "$ANDROID_SERIAL"`: **NOT RUN**. `adb devices` lists no device.

### Not verified (device-only or assumed)
- **Assumption:** Android removes `/storage/<volume-id>` on unmount. Plain-path unavailable detection (`_storageAvailableFor`, `isStorageMountContainer`) depends on it. If the directory stays as an empty folder, a file on an unplugged card reads as missing and is dropped, as before this change. Not observed on a device.
- The 2 s startup SAF timeout (`main.dart:476`) and the 10 s update-check bound (`update_checker.dart:290`) were **read, not exercised** under airplane mode.
- First-launch airplane-mode walkthrough: NOT RUN (no device or emulator).
- Real SD card or USB drive removal and remount: not observed.
- SAF `StorageManager` volume check on a device (volume uuid match against `StorageVolume.uuid`, `Environment.getExternalStorageState`): compiled only. The uuid match is assumed to equal the document id's volume part (for example `1234-ABCD`).
- Provider behaviour for a document on an unmounted volume (null cursor, exception, or empty cursor): not observed; each case maps to unknown or to a found row as described.
- Revoked grant and restore on a real device: not run.
- `desktop_library_table_rows_test.dart`: not executed in the final run; the stall is environmental and unexplained.

### Deferred to M001-T005
Update check and announcement (`main_shell_startup_prompts.dart:94-140`, `update_checker.dart`, `app_remote_config_service.dart`), donate and remote config (`settings_tab.dart:155-166`), extension store refresh and auto-update (`main_shell.dart` post-frame, `extension_auto_update_provider.dart`), Link nearby-devices permission step (`setup_screen.dart:355-377`), network storage and Subsonic sources (`network_storage_*`). None removed in T004.

### Review
Round 1 (independent, read-only; same reviewer as T001-T003, resumed): NEEDS_FIXES. Blocking: the Kotlin volume guard turned documents of every non-ExternalStorage provider (`audio:`, `msf:`, `document:`, `home:`) into "unknown", which the bool wrapper read as missing (a regression on a supported path); also the planned commit subject was 76 characters. Advisory: log counts and the excluded-file wording. Fixes: authority-scoped `safVolumeUuidToCheck`, JUnit tests, corrected counts and Not-verified list, subject `fix(android): keep unavailable files on offline restart (M001-T004)`.
Round 2 (independent, read-only): PASS. The reviewer re-grepped every `safExists`/`safExistsState` caller, checked uuid edge cases, recounted `t004e_full.json` (7,961 results: 7,883 pass / 25 skip / 53 fail) and confirmed the desktop-table file was not executed. Unverified by the reviewer: device/SD-removal behavior, the Gradle JUnit run, airplane-mode startup, `desktop_library_table_rows_test.dart`, `android_background_playback` integration test.

## M001-T005 — Remove online product features and dependencies

Baseline `T004` commit. Workers: Haiku 5.5 (Dart/Kotlin/Rust/test workers on disjoint file sets, several rounds); the coordinator fixed a few regressions directly after rate-limit stops. Map refresh first: `python3 workflow/codegraph/code_tools.py update`. Not committed before the independent review.

### What was removed
- **Dart (`lib/`)**: downloads (queue, history, finalizer, folders, containers), extensions/Store/explore/search/online album and artist screens, Link (devices, sync), Cast, NAS/SMB/WebDAV/Subsonic, Discord, Last.fm/scrobbling, update checker and remote config, online lyrics (providers, translation, AI), metadata auto-fill and re-enrich, online previews, network cover loading/caches, `setNetworkSource` and network playback deck paths, About links/credits/social, desktop Help menu, settings fields and migrations for the removed features, 42+ unused l10n keys.
- **Kotlin/Android**: Cast, Link, local-network, download/extension channels and arms, mDNS/NSD, share/deep-link intent filters for removed features, permissions for network/nearby devices. Merged release manifest checked (below).
- **Rust (`rust_backend/`)**: crates `cast`, `link`, `network`, `providers` deleted; `extensions` reduced to a local-only backend (library scan, tags, cover, lyrics read/embed, logging; no JS runtime, no HTTP/TLS/socket code). 27 unused workspace dependencies removed; `Cargo.lock` updated (`--locked` passes). `crates/desktop` left the workspace (T006 deletes it).
- **Packaging/CI/scripts**: Discord SDK action and `third_party/mornye_discord`, `third_party/discord` removed; CI Discord steps removed.
- **Tests**: about 330 unit test files and 32 integration tests whose subject was removed were deleted (`git rm`); about 130 unit tests were adapted to local behaviour with their local assertions kept.

### Kept on purpose (local behaviour)
Local library scan, SAF/MediaStore, tag reading/editing, ReplayGain, local lyrics read (`getLyricsLRCWithSource(file_path)`, embedded and sidecar) and FLAC embed, sidecar export, playlists, loved, listening statistics, backup/restore, Media3/media_kit playback, system audio output picker, Mornye theme.

### Regressions found by the test comparison and fixed
The post-removal run showed many HEAD-passing tests failing. Real lib regressions fixed: ExpressiveButtonGroup `flexible` index after Download removal (playlist header overflow), a `_cast.active` guard removed leaving an unconditional `_userPaused = false` (Autoplay pause), Edit button before the file check answers, `_canDeleteFile`, `androidPlaybackInfo` init, Mornye track-sheet icon mapping, `library_cleanup` reading the removed download table, `durableCoverReference` (web URLs read as none), local `.lrc` export via the lyrics the screen reads, the legacy-source sentinel probe, Mornye mix banners restored on Home.

### Product decisions the user should know
- Files settings "reorganize files by rule" removed: its rule engine lived only in the download code.
- No online lyrics search and no lyrics-provider settings. The lyrics-embed action had no local source at HEAD (its only non-embedded source was the online lookup), so its 6 embed-flow tests were removed; the local embed export call remains.
- Collection header: Love and Add-to-playlist only; Download removed. Playlist selection bar had only Download and was removed.
- About page: credits for online services, links and social removed; contributor rows not tappable.
- 30 s previews, online covers, queue-count snackbar for downloads removed.
- Cache settings: track-lookup, explore-feed and cover-cache rows removed.

### Verification
- `flutter analyze --no-pub`: exactly 1 issue, the known baseline info at `workflow/codegraph/fixtures/bridge/lib/api.dart:5`.
- `flutter test` in 13 chunks of 40 files (490 files; `desktop_library_table_rows_test.dart` excluded, it stalls at baseline): 4,581 passed, 46 failed on the first full run; 45 are in the baseline failure set of `t004e_full.json` (53 environment/load failures, e.g. SQLite/fts5, path_provider, running as root); the one new failure (`listening_statistics_test` older web covers) was fixed with `durableCoverReference` and its tests adapted; the affected files pass when rerun.
- Rust: `cargo check --workspace --all-targets --locked` rc=0; `cargo clippy --workspace --all-targets --locked -- -D warnings` rc=0; `cargo test --workspace --locked --no-fail-fast`: all pass (118+183+13+... 0 failed).
- `cargo tree -p sistrum-mobile --target aarch64-linux-android --locked`: 317 lines, 0 matches for hyper, rustls, reqwest, tokio, hickory, mdns, smb, webdav, rquickjs, openssl, native-tls, socket2, h2, http, cookie.
- Kotlin: `:app:compileReleaseKotlin --offline -Ptarget-platform=android-arm64 -x :app:buildRustBackend` BUILD SUCCESSFUL (JDK 25). UniFFI Kotlin bindings regenerated with `sistrum-bindgen`; removed exports absent, `getLyricsLrcWithSource` and `embedLyricsToFile` present.
- Merged release manifest (`:app:processReleaseMainManifest`): no `INTERNET`, no Wi-Fi/Nearby/multicast permissions, no network share/deep-link intent filters. `ACCESS_NETWORK_STATE` is contributed by a dependency (Media3) and is accepted: it grants no network access. `FOREGROUND_SERVICE_DATA_SYNC` stays for the local background-work service (library scan).
- `python3 workflow/work.py quality bridge verify`: FAILED with exactly 3 failed contracts, the same count as baseline (negative fixtures); 114 unresolved (no runtime tests supplied).

### Residuals and not verified
- Legacy-row guards for `network://` / `http(s)` in library SQL and `PlayableMedia.isNetwork`, Track online JSON fields (`deezerId`, `ServiceAvailability`, `headerVideoUrl`) and library album-artwork provider matching (`library_album_artwork.dart`, `album_artwork_match.dart`) remain; they need a schema/model decision.
- Settings fields `downloadDirectory*` and `flutter_secure_storage` cleanup of retired secrets remain (privacy-positive).
- Dead Kotlin plumbing `prepareRuntimeState`/`setRuntimeState`; `initExtensionSystem` still carries its name and a master key the backend now ignores.
- `.gitignore` mentions `crates/network/src/roots.pem`; `scripts/build_rust_backend.sh` and desktop CI still build `crates/desktop`: T006.
- Shells for non-Android platforms (iOS/macOS/Windows/Linux) still exist and were not built: T006.
- Integration tests (`integration_test/`) analyze clean but were not run (no device, no Linux GTK).
- Device behaviour (airplane mode, removable media, focus) NOT RUN: no device.
- A stray empty file `/t005_tests_1_progress.md` was created at the filesystem root by a worker; deletion was blocked by the safety check and is left to the user.

### Independent review (task-reviewer, round 1): PASS with advisory findings
- Fixed before commit: `lib/widgets/motion_header_banner.dart` no longer opens `VideoPlayerController.networkUrl` (only local files play); dead `LocalNetworkPlugin.kt` and its registration deleted (`:app:compileReleaseKotlin` BUILD SUCCESSFUL again); stale `NETWORK_STORAGE.md`, the SMB fixture servers and their README rows removed.
- Recorded residuals: `lib/utils/file_access.dart` keeps an `http(s)` open-with-OS branch for legacy rows; `lib/services/library_album_artwork.dart` (local SQLite artwork store) lost its test file with the provider-lookup tests (local-only cases such as "a removed cover stays away until the album is looked up" should be restored in a later task); adapted tests lost many assertions that belonged to removed behaviour (counts: `music_player_runtime_test` 118 to 85, `home_shelves_test` 8 to 3, `source_contracts_test` 9 to 1), none skipped.
- A second stray file `/t005_tests_requests.md` exists at the filesystem root next to `/t005_tests_1_progress.md`; both are outside the repository and left to the user to delete.

## M001-T006 — Remove other platforms and enforce ARM64 packaging

Baseline `32965cc` (M003 planning commit, tree identical to the M001-T005 result plus M003 documents). Workers: Haiku 5.5 on five disjoint file sets (shells and tooling; ABI enforcement; CI and release; Android TV and docs; stale references), plus two follow-ups; the coordinator verified every report against the tree and fixed one stale CI path filter. Map refresh first: `python3 workflow/codegraph/code_tools.py update` PASS, 1,638 files in scope, 1,219 excluded, 1,504 extracted, 0 partial, 0 unindexed. `third_party/` is excluded from the map, so its plugins were read directly.

### What changed
- **Removed shells and tooling**: `ios/`, `macos/`, `linux/`, `windows/`, `rust_backend/crates/desktop`, `rust_backend/MornyeBackend.podspec`, `apps.json` (AltStore source), `icon_macos.png`, `.github/signpath/`, `.github/workflows/desktop.yml`, `third_party/audioplayers_darwin`, `third_party/media_kit_libs_windows_audio`, 17 desktop/iOS scripts, five desktop/Windows/TV integration tests (`desktop_backend_smoke`, `desktop_ffmpeg_bundle`, `desktop_playback_deck`, `wasapi_exclusive_playback`, `android_tv_navigation`).
- **ARM64 only**: `android/app/build.gradle.kts` uses a fixed `arm64-v8a` list (no ABI environment variable); `scripts/build_android.sh` always builds `android-arm64` and rejects `--target-platform`/`--split-per-abi`; `scripts/build_rust_backend.sh` accepts only `android`, builds `aarch64-linux-android`, and recreates the `jniLibs` staging directory so a stale ABI cannot be packaged; `scripts/check_backend_apk.py` fails on any `lib/<abi>/` other than `arm64-v8a`; `check_android_statx.sh` accepts only `arm64-v8a`.
- **CI/release**: `ci.yml` keeps `changes`, `flutter`, `rust`, `android` (arm64 only); `release.yml` keeps `get-version`, `build-android`, `create-release`, `notify-telegram` (arm64 APK only). iOS, macOS, Linux, Windows, desktop release and AltStore jobs removed.
- **Android manifest**: Leanback feature, TV banner and LEANBACK_LAUNCHER removed; `tv_banner.xml` and `tv_wordmark.png` deleted.
- **Dependencies**: `media_kit_libs_windows_audio`, `media_kit_libs_linux`, `media_kit_libs_macos_audio` and the `audioplayers_darwin`/Windows overrides removed from `pubspec.yaml`; `pubspec.lock` regenerated by `flutter pub get`.
- **Docs**: README, CONTRIBUTING, `android/README.md`, `rust_backend/README.md`, `integration_test/README.md`, `test/README.md`, `docs/`, issue template and stale comments updated to the Android arm64 phone product.

### Kept on purpose
- **Android Auto**: the `MediaBrowserService` filter and `automotive_app_desc` stay; M001 plan item 6 says "TV/car-specific product paths", read here as the TV and separate car products, because the owner later asked to keep Android Auto (M003).
- `android.hardware.usb.host` and `touchscreen` features, `isTelevisionDevice()` in `MainActivity.kt` (also used by orientation, keyboard and the `isAndroidTv` channel call).
- Shared Dart desktop code (`lib/widgets/desktop`, `ffmpeg_desktop.dart`, `desktop_folders.dart`, `media_session_smtc.dart`, TV widgets) and its packages (`smtc_windows`, `dbus`, `desktop_drop`, `path_provider_windows`): imported by shared code, left for M001-TOR004.

### Verification
- `bash -n` on the four shell scripts: OK. `python3 scripts/test_build_android.py` 6 OK, `python3 scripts/test_check_backend_apk.py` 9 OK (includes new non-arm64 rejection tests), `python3 scripts/test_flutter_git_commit.py` 6 OK (the iOS-only test was removed with its input), `python3 -m unittest discover -s workflow -p 'test_*.py'` 47 OK.
- `flutter analyze --no-pub`: exactly 1 issue, the known baseline info at `workflow/codegraph/fixtures/bridge/lib/api.dart:5`.
- `flutter test` in 13 chunks of 40 files (489 files; `desktop_library_table_rows_test.dart` excluded, it stalls at baseline): 4,566 passed, 46 failed, 15 skipped. The same 17 failing files were run on a clean `git archive` export of the baseline commit: the same 46 tests fail there (0 new failures, 0 fixed). The failures are environment ones (SQLite/FTS5, path_provider) plus source-contract tests of features removed by T005.
- `cargo check --workspace --all-targets --locked` rc=0. `bash scripts/build_rust_backend.sh android` rc=0: only `jniLibs/arm64-v8a` is staged; `libsistrum_mobile.so` is ELF64 AArch64, all LOAD segments aligned 0x4000 (16 KB), NEEDED only `libdl`, `libm`, `libc`.
- Gradle (JDK 25, local Maven retry proxy): `:app:processReleaseMainManifest` and `:app:compileReleaseKotlin` BUILD SUCCESSFUL with `-x :app:buildRustBackend`. Merged release manifest: package `com.amwolfstein.sistrum`, no `INTERNET`, no Leanback or TV banner, `MediaBrowserService` and `automotive_app_desc` present.
- `python3 workflow/work.py validate`: PASS (see completion below).

### Residuals and not verified
- No APK was built (not authorized), so `apkanalyzer`, `zipalign -c -P 16`, `apksigner verify` and the ELF audit of the packaged APK were NOT run; `scripts/check_backend_apk.py` is covered only by its unit tests. This is for M001-T007.
- The CI and release workflows were checked by YAML parse and a job-graph script (every `needs` resolves, no removed-job references). `actionlint` is not installed; the workflows were not run.
- `rust_backend/README.md` still describes `crates/network` and `crates/providers` (removed in T005); `rust_backend/crates/mobile/Cargo.toml` still lists `staticlib` and `mobile/src/lib.rs` keeps re-exports that existed for the desktop crate; `lib/services/ffmpeg_desktop.dart` comment still names a deleted script. All left for M001-TOR004.
- `release.yml` `notify-telegram` still targets the upstream channel `@mornyeupdates` through repository secrets; README, NOTICE and issue-template links to the upstream Mornye project were not part of this task.
- Device behaviour NOT RUN: no device.

### Independent review (task-reviewer, round 1): PASS
Independent read-only reviewer, not self-review. It re-grepped the working tree for every deleted script, crate, plugin, test and resource, read the scripts, Gradle, both workflows, manifest, pubspec/lock and the Python tests, and found no task-scoped defects. Non-blocking notes, left for M001-TOR004: `CONTRIBUTING.md` lines 85-101 still describe `build_android.sh --production`/`--lite` and the Discord SDK, with a dead link to `third_party/mornye_discord/README.md` (T005 leftover; the script never supported those flags); `.github/actionlint.yaml` lists the stale self-hosted label `xcode-27`; `pubspec.yaml` keeps the Apple `flutter_secure_storage_darwin` override and `third_party/ffmpeg_kit_flutter_new_audio/{linux,windows}` CMake files. Unverified by the reviewer: the packaged APK checks, a Gradle build with the Rust task, executing the workflows, Android Auto and device behaviour.
