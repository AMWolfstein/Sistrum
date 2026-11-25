# Theme Layer

Mornye is the only visual style. Its code is the Flutter counterpart of
Mornye's iPhone visual system: iOS system colors, Cupertino typography and
icons, and glass surfaces. Its palette is kept independent of wallpaper colors.

Mornye still produces a Flutter `ThemeData` on Material widgets (`material_ui`),
so widgets read `Theme.of(context).colorScheme` and `textTheme`. Mornye maps its
colors onto Material 3 `ColorScheme` roles.

Settings persisted by the removed Material style (`theme_style`, dynamic
color, seed color, AMOLED) are dropped once on load (`lib/models/theme_settings.dart`,
`lib/providers/theme_provider.dart`). Mode, accent, font and glass choices are kept.

## Files

| File | Role |
| --- | --- |
| `lib/theme/mornye_theme.dart` | `MornyeTheme`: the Mornye `ThemeExtension`, its `ThemeData` builder (`MornyeTheme.build`), `MornyeSettingsTheme` |
| `lib/app.dart` | Builds `MaterialApp.router` with `MornyeTheme.build` for light and dark |
| `lib/theme/app_tokens.dart` | `AppTokens`, a `ThemeExtension` with the shared radii, spacing, cover sizes and header metrics |
| `lib/theme/mornye_icons.dart` | `mornyeIconFor` (Material icon to Cupertino icon) and `context.adaptiveIcon` |
| `lib/theme/app_motion.dart` | `AppMotionSpec`, `SpringCurve`, `MornyeMotion` springs and the `AppMotion` presets |
| `lib/theme/cover_palette.dart` | `CoverPalette`, `HeaderPalette`, `CoverPaletteBuilder` and `CoverSourceColorBuilder`: colors extracted from artwork |
| `lib/theme/page_transitions.dart` | Mornye page transitions and the reduced-motion wrapper |
| `lib/theme/mornye_glass_presets.dart` | `MornyeGlassPreset`: the one table of fixed glass values per mode |

## How a Theme Is Built

`MornyeTheme.build(brightness, ...)` takes the Mornye accent (`MornyeAccent`,
red by default) and glass outlines. It seeds a `ColorScheme` from the accent and
overrides the roles with iOS grouped colors (for example `#F2F2F7` and
`#1C1C1E`). Text uses the Cupertino system font on every platform. It registers `MornyeTheme.tokens`,
a copy of `AppTokens.standard` with different radii and iOS title sizes.

`MornyeTheme.build` caches up to 16 base themes; changing only glass
outlines reuses the cached theme.

## Using the Layer in Widgets

- Read tokens with `context.tokens`, not `Theme.of(context).extension`. It
  falls back to `MornyeTheme.tokens` outside a themed subtree.
- Keep `Icons.*` at call sites and pass them through `context.adaptiveIcon`;
  Mornye then shows the Cupertino glyph from `mornyeIconFor`.
- `AppMotion` presets return the call site's fallback spec under reduced
  motion, and the call site's `mornye:` spec when it passes one.
- Wrap settings pages in `MornyeSettingsTheme` for iOS inset grouped surfaces.

## Liquid Glass Policy

There is one glass policy. Every glass surface reads it; none keeps its own.

- Two modes, chosen in Settings > Appearance: **Liquid** (default) and **None**.
  The enum is `MornyeGlassLevel` (`liquid`, `flat` for None) in
  `lib/models/theme_settings.dart`. The choice is persisted under
  `mornye_glass_mode`. Frosted Glass was removed at the owner's request; a stored
  `frosted` value reads as Liquid.
- Selected mode: `mornyeGlassModeProvider` (`lib/providers/runtime_profile_provider.dart`).
  It is what the user chose and is never changed by the device.
- Comparison mode: `mornyeGlassCompareModeProvider` (`lib/providers/theme_provider.dart`)
  holds, in memory only, the mode a Performance hub comparison renders.
  `mornyeGlassActiveModeProvider` returns it while set, else the selected mode.
  `ThemeNotifier.setMornyeGlassLevel` ignores writes while it is set, so a
  comparison never changes the saved mode, even after a crash.
- Effective renderer: `mornyeGlassLevelProvider` (same file). It applies device
  fallback to the active mode. Low-end devices render None, and Liquid renders
  None when the lens is not supported. Fallback can only downgrade, never upgrade,
  and it never changes the saved choice.
- Per-surface values: `MornyeGlassPreset.of(level)` in
  `lib/theme/mornye_glass_presets.dart` is the only table of blur sigma, tint,
  clear mix, dispersion, control sampling and batch blur method. Liquid uses
  sigma 2, dispersion 0 and a shader batch blur. None has no blur, no sampling and
  no batch.
  Surfaces must not hard-code blur, tint or dispersion values.
- Surface kinds: `MornyeGlassKind` (the same file, re-exported by
  `mornye_chrome.dart`) says what a surface is, and the preset decides whether
  it reads the backdrop:
  - `chrome` (default): navigation that floats over content, such as the tab bar,
    the mini player, the search field, segmented controls, menus and popovers while
    presented. It samples in Liquid, and `samplesBackdrop: false` keeps it a plain
    fill.
  - `control`: small controls, such as header circles, action capsules, the
    profile avatar and the bottom bar's side capsules. Liquid keeps its lens in a
    batch; a control with `samplesBackdrop: false` draws a plain fill.
  - `content`: settings groups, sheets, dialogs, playlist picker, player details,
    song analysis and the desktop inspector. Always a plain opaque fill, the same
    as None, in every mode. Content never samples.
- Rendering: `MornyeGlass` (`lib/widgets/mornye_chrome.dart`) is the single entry
  point. Liquid draws the native lens (`MornyeLiquidBackdrop`, package
  `LiquidGlassLens`) inside a `LiquidGlassBatch` whose blur method comes from the
  preset (`shader`, so the blur adds no engine pass). Without a lens a surface draws
  a plain fill, and None draws a plain fill everywhere.
  A batch is made only when a surface is not already inside one, so a page or
  the bottom bar shares one read: the artist page and the album, mix and playlist
  pages (`CollectionScaffold`) batch their header buttons and toolbar capsule, each
  with the preset's `batchBlurMethod`; the bottom bar batches its tab bar and mini player. Batches
  are never placed over the whole app, and a glass pop-up above other glass
  uses `LiquidGlassBatch.exclude`.
- **None never samples the backdrop.** It has no `BackdropFilter`, no lens and no
  batch. This holds for every surface, including overlays and the Performance hub
  comparisons.
- Accessibility: high contrast turns decorative glass off
  (`mornyeGlassContrastBlockedProvider`), without changing text or motion settings.

### Migration

The retired continuous Glass Clarity slider (`mornye_glass_clarity`) and the
`forceBackdropBlur` setting are migrated once by `lib/providers/theme_provider.dart`.
Clarity 0 becomes None, and any value above 0 becomes Liquid. A stored `frosted`
mode becomes Liquid. The stored force-blur never changes the result. The migration
writes the mode,
removes the clarity key and sets `mornye_glass_mode_migrated`, so it is idempotent.
Covered by `test/mornye_glass_mode_migration_test.dart` and
`test/theme_retired_prefs_test.dart`.

### Warm-up

`MornyeGlassPreparation` (`lib/widgets/mornye_glass_preparation.dart`) starts
`MornyeGlassWarmup` once, when Liquid is rendered. It prepares the backdrop
pipeline without competing with animations or presenting a frame.

### Performance Hub

Settings > Performance hub (`lib/screens/settings/performance_hub_page.dart`)
records real frame timings while the user uses the app, from
`SchedulerBinding.addTimingsCallback` (`lib/services/frame_timing_recorder.dart`).
It reports percentiles, jank and the selected mode and effective renderer. Its
guided Compare renders Liquid and None for 10 s each, from memory, so the
saved mode is never written. A window is discarded when it is cancelled, when the app
is paused or hidden, or when it has fewer than 60 frames. The first seconds after a mode
switch include shader warm-up, so discard a first run or scroll before measuring. When
the system reports no refresh rate, the jank figure uses the measured interval and is
marked unreliable. Results are plain text or JSON to the clipboard, with the last five
runs kept locally. Impeller, GPU passes and backdrop reads are marked "Not available"
because they are not measured. Nothing is sent over the network.

## Mornye Outside This Folder

- Mornye widgets live in `lib/widgets/mornye_*.dart`, for example `MornyeGlass`
  in `lib/widgets/mornye_chrome.dart`.

## Tests

`test/design_system_test.dart` checks that `AppTokens` is registered on Mornye
(`MornyeTheme.tokens`), the Mornye dark accent, and Mornye's font families and
letter spacing.

`test/mornye_glass_kind_test.dart` checks the preset table (Liquid dispersion 0, shader
batch blur, which kinds sample) and counts the backdrop samplers of one page in each
mode: None 0, Liquid one lens in one shared batch, and content panels none in every mode.

Collapsing header artwork fades into a blurred copy of itself. `BlurredCoverLayer`
(`lib/widgets/blurred_cover.dart`) draws a cached blurred copy over the sharp artwork,
in the same box, so the framing is the same. The copy is built once per cover and
size by `BlurredCoverCache` (up to 8 entries, least recently used evicted and disposed):
the cover is decoded at 1/8 size, blurred at sigma 18/8 in small pixels with clamp
edges, and drawn scaled up. The layer's opacity follows the collapse (`easeOut`), so
no filter runs per frame. It is built only in Liquid, not with reduced motion, and only
when the header has a `blurSource` (a single cover file: the artist, local album and
single-cover playlist headers). Composite covers (mix, genre) and icons stay sharp.
`test/artist_header_real_test.dart` checks the framing and the softening on the real
headers; `test/blurred_cover_test.dart` checks the cache.

The Liquid selection pill (the moving thumb of the tab bar and the segmented controls) takes its
blur and refraction from the preset: `pillBlurSigma` 0 (a clear lens, no blur pass),
`pillRefractionIndex` 1.015 and `pillRefractionWidth` 24, the package's tab pill values. It stays
excluded from the page batch, since it refracts the bar under it. Dispersion stays 0 (a read).
