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
