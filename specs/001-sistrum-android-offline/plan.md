# M001 — Implementation plan

## Summary

Apply the Sistrum identity to the imported Mornye v5.4.0 codebase first. Verify that branded baseline with existing features still present. Then reduce the app to an Android ARM64 offline player. Clean installs are the development baseline.

## Repository evidence

- Current baseline `2b5332c` keeps the full import. `origin/wip` at `ba0e973` holds the approved Sistrum artwork. Use that commit only as the image source; its other commits, code, identifiers and versioning stay out. Design the rebrand against the current checkout.
- The product name is Sistrum. Derive Android namespace/application ID, Flutter package, version, Rust/UniFFI names and app-owned protocol identifiers from current code and record the chosen values during T001. Check callers, generators and external contracts before selecting or renaming each identifier. Preserve upstream attribution and third-party notices.
- Approved WIP images already match the baseline geometry and alpha (verified from PNG headers and a visual side-by-side review):

  | WIP file (`ba0e973`) | Baseline destination | Size | Alpha |
  |---|---|---:|---|
  | `icon.png`, `assets/images/logo.png` | same paths | 1024×1024 | opaque |
  | `icon_android.png` | same path | 512×512 | opaque |
  | `icon_foreground_android.png`, `icon_monochrome_android.png` | same paths | 432×432 | transparent |
  | `assets/images/logo-transparent.png` | same path | 608×800 | transparent |
  | `assets/images/sistrum-horizontal-{light,dark,white}.png` | replace `mornye-lockup-horizontal-{light,dark,white}@1x.png` uses | 703×200 | transparent |
  | Android `mipmap-*`/`drawable-*` launcher resources | same paths | per density | as in WIP |

- Brand images in the baseline with no WIP counterpart, to derive from the approved artwork with the same geometry: `assets/brand/logo/` set (badge, mark, wordmark, stacked lockup, @2x/@4x and SVG variants), `logo.svg`, `icon_macos.png`, `android/.../drawable-nodpi/tv_wordmark.png`, iOS/macOS/Windows/Linux icon sets and `linux/packaging/icons/`. The Sistrum mark is raster; replace vector brand SVGs with PNG references or SVGs that embed the approved raster, and update callers (`README.md`, `pubspec.yaml`, `linux/CMakeLists.txt`, `scripts/package_linux.sh`). Remove unused Mornye brand files instead of recreating them. Platform-specific icons that T006 deletes are only rebranded to keep phase one consistent.
- Known references: `lib/widgets/mornye_mix_banner.dart` (Top Picks card), `lib/screens/setup_steps.dart`, `lib/screens/settings/about_page.dart`, `lib/screens/lyrics_share_screen.dart`, `lib/screens/listening_rewind_share_card.dart`, `pubspec.yaml` launcher-icon config (`adaptive_icon_background`/`background_color_ios` still `#1E4E4F` teal; set to the WIP icon background), `apps.json` upstream `iconURL`, Android `tv_banner.xml`.
- Reused the existing pre-edit full CodeGraph snapshot for the same application source. It included 2,569 paths and extracted 2,421 sources. Its scope predates the editor-ignore change. Manually inspect computed channel names, JNI registrations, generated bindings, resource XML and Gradle/CMake packaging. No build or device verification ran during planning.

## Phase one — whole-codebase identity

Classify each Mornye occurrence before editing. Sistrum replaces app-owned identity, including app labels inside a theme-rendered screen. Mornye remains the theme name, including its Dart types and files, theme selection keys, tokens, icons and Glass helpers. Examples include `lib/theme/mornye_theme.dart`, `lib/theme/mornye_icons.dart`, `lib/controllers/mornye_glass_warmup.dart` and `lib/widgets/mornye_glass_*.dart`. Keep upstream attribution and external contracts separately. Review the change list for accidental theme renames or visual changes. Keep theme changes outside the rebrand.

1. Inventory current ownership and dependencies and map old names to independently chosen Sistrum identifiers. Use WIP only for image assets. Skip its commits, code, configuration, identifiers and versioning. Rename app-owned Dart packages/imports, Kotlin/Java packages, manifest components, providers and authorities, URL schemes, intent actions, notification/channel identifiers, preferences, backup metadata, Rust crate names, UniFFI packages, JNI symbols and library loader names. Update generated sources through their generators, test fixtures, native packaging, scripts, CI, docs and all runners still present at this point. Keep externally defined IDs, upstream attribution and compatibility contracts where renaming would break existing integrations. Record any remaining Mornye occurrences with evidence for why they are external or attribution.
2. Inventory image destinations in Flutter, Android and the existing platform shells. Use WIP artwork as the visual reference. Match target pixel dimensions, aspect ratio or SVG viewBox, safe area, transparent margins and color modes. Preserve the original layout format and WIP artwork proportions, using canvas padding or recomposition as appropriate. Regenerate density resources and render launcher masks, dark/light splash, themed launcher icon, notification and About/onboarding. Replace any brand image that remains off-size, distorted or clipped. Retain unrelated illustrations.
3. Freeze the rebrand as a review checkpoint. Prove clean install as Sistrum, no missing native symbols, and working existing local and representative online app paths. Verify expected identifiers in built manifests and packages before removing features. No startup or storage migration from the old package is needed.

### Horizontal Sistrum logo and wordmark

Use the approved `sistrum-horizontal-light/dark.png` for the README header (both color modes) and `sistrum-horizontal-white.png` for the Top Picks for You card in `lib/widgets/mornye_mix_banner.dart`, replacing the Mornye lockup asset in code and `pubspec.yaml`. All are 703×200 transparent. Keep the card's existing layout and logo width; do not add a separate text label. Keep the portrait logo as its own asset. Preserve the Mornye theme.

## Phase two — Android phone, ARM64, offline

4. Make local first run, scan/browse/search and playback independent of network state. Keep local files, MediaStore and SAF grants, queues, playlists, favorites, local lyrics/art, Media3 and required fallback paths. Check SD removal, missing/invalid files, revoked grants, persisted queue position, background controls and process restart.
5. Remove online routes, services, scheduled work, permissions, plugins and native code in dependency order. Cover accounts, discovery/search providers, extensions, streaming/downloads, remote lyrics/metadata, scrobbling, app updates, telemetry, Cast/Link and network shares. Check combined Dart/Kotlin/Rust callers before removing shared code. Keep local playback libraries and codec fallbacks.
6. Drop desktop/iOS and TV/car-specific product paths, release jobs, tests and assets where unused. Enforce `arm64-v8a` across Gradle, Flutter, Rust, CMake and packaged plugins. Keep the workflow CI check. Verify ELF headers, expected libraries, merged manifest, signing and 16 KB alignment. Treat APK as the default and apply equivalent checks to AAB-derived APKs if needed.

## Constitution and validation

Each task follows map-first execution, explicit checks, independent review and the existing bounded review loop. Preserve existing supported audio behavior and SDK floor unless the implementation demonstrates a dependency. Final review covers clean install, offline device flows, package/ABI checks and licensing. No production compatibility or historical migrations are planned. [Android shared storage](https://developer.android.com/training/data-storage/shared/media) · [Android 16 KB support](https://developer.android.com/guide/practices/page-sizes)
