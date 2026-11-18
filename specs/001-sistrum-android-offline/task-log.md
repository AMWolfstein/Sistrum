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
