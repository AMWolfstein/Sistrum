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

## How a Theme Is Built

`MornyeTheme.build(brightness, ...)` takes the Mornye accent (`MornyeAccent`,
red by default), the system-font and custom-font choices, glass clarity
(default 0.75) and glass outlines. It seeds a `ColorScheme` from the accent and
overrides the roles with iOS grouped colors (for example `#F2F2F7` and
`#1C1C1E`). Text uses Cupertino styles. The font is the custom font when one
is set; otherwise the Cupertino system font on Apple platforms or when the
system font is chosen, and Inter elsewhere. It registers `MornyeTheme.tokens`,
a copy of `AppTokens.standard` with different radii and iOS title sizes.

`MornyeTheme.build` caches up to 16 base themes; changing only glass clarity
or outlines reuses the cached theme.

## Using the Layer in Widgets

- Read tokens with `context.tokens`, not `Theme.of(context).extension`. It
  falls back to `MornyeTheme.tokens` outside a themed subtree.
- Keep `Icons.*` at call sites and pass them through `context.adaptiveIcon`;
  Mornye then shows the Cupertino glyph from `mornyeIconFor`.
- `AppMotion` presets return the call site's fallback spec under reduced
  motion, and the call site's `mornye:` spec when it passes one.
- Wrap settings pages in `MornyeSettingsTheme` for iOS inset grouped surfaces.

## Mornye Outside This Folder

- Glass rendering level: `MornyeGlassLevel` (`flat`, `frosted`, `liquid`) and
  `mornyeGlassLevelProvider` in `lib/providers/runtime_profile_provider.dart`
  choose how much glass a device renders.
- Mornye widgets live in `lib/widgets/mornye_*.dart`, for example `MornyeGlass`
  in `lib/widgets/mornye_chrome.dart`.

## Tests

`test/design_system_test.dart` checks that `AppTokens` is registered on Mornye
(`MornyeTheme.tokens`), the Mornye dark accent, and Mornye's font families and
letter spacing.
