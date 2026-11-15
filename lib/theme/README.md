# Theme Layer

The app has one visual style, Mornye: grouped system colors, Cupertino
typography and icons, and glass surfaces. Its palette is independent of
wallpaper colors, and the person picks only the theme mode, the Mornye accent,
glass outlines and the font (`lib/models/theme_settings.dart`, loaded by
`lib/providers/theme_provider.dart`).

`MornyeTheme.build` produces a Flutter `ThemeData` with `useMaterial3: true`,
so widgets read `Theme.of(context).colorScheme` and `textTheme` as usual.
Mornye maps its colors onto Material 3 `ColorScheme` roles instead of
replacing them.

## Files

| File | Role |
| --- | --- |
| `lib/theme/mornye_theme_wrapper.dart` | `MornyeThemeWrapper`, used by `lib/app.dart`. Builds the light and dark Mornye `ThemeData` from the stored theme settings |
| `lib/theme/app_tokens.dart` | `AppTokens`, a `ThemeExtension` with the shared radii, spacing, cover sizes and header metrics |
| `lib/theme/mornye_theme.dart` | `MornyeTheme`: the Mornye `ThemeExtension`, its `ThemeData` builder, `MornyeSettingsTheme` |
| `lib/theme/mornye_icons.dart` | `mornyeIconFor` (Material icon to Cupertino icon) and `context.adaptiveIcon` |
| `lib/theme/app_motion.dart` | `AppMotionSpec`, a duration and curve pair for implicit animations |
| `lib/theme/cover_palette.dart` | `CoverPalette`, `HeaderPalette`, `CoverPaletteBuilder` and `CoverSourceColorBuilder`: colors extracted from artwork |

## How the Theme Is Built

`MornyeThemeWrapper` watches `themeProvider` and the custom font provider.
`MornyeTheme.build(brightness, ...)` takes the Mornye accent (`MornyeAccent`,
red by default), the system-font and custom-font choices, glass clarity
(default 0.75) and glass outlines. It seeds a `ColorScheme` from the accent
and overrides the roles with grouped colors (for example `#F2F2F7` and
`#1C1C1E`). Text uses Cupertino styles. The font is the custom font when one
is set; otherwise the Cupertino system font (`CupertinoSystemText`) when the
system font is chosen, and Inter by default. It registers its own
`MornyeTheme.tokens`, a copy of `AppTokens.standard` with different radii
(smaller for most shapes, larger for sheets) and large title sizes, and
removes ink splashes.

`MornyeTheme.build` caches up to 16 base themes; changing only glass clarity
or outlines reuses the cached theme. Use `MornyeTheme.fromContext` to rebuild
a local surface (another brightness or chrome color) without losing the
user's Mornye preferences.

## Using the Layer in Widgets

- Read tokens with `context.tokens`, not `Theme.of(context).extension`. It
  falls back to `MornyeTheme.tokens` outside a themed subtree.
- Keep `Icons.*` at call sites and pass them through `context.adaptiveIcon`;
  Mornye then shows the Cupertino glyph from `mornyeIconFor`.
- Wrap settings pages in `MornyeSettingsTheme` for inset grouped surfaces.

## Mornye Outside This Folder

- Glass rendering level: `MornyeGlassLevel` (`flat`, `frosted`, `liquid`) and
  `mornyeGlassLevelProvider` in `lib/providers/runtime_profile_provider.dart`
  choose how much glass a device renders.
- Mornye widgets live in `lib/widgets/mornye_*.dart`, for example `MornyeGlass`
  in `lib/widgets/mornye_chrome.dart`. Mornye-only screens are
  `lib/screens/mornye_library_screen.dart` and
  `lib/screens/track_metadata_mornye.dart`.

## Tests

`test/design_system_test.dart` checks that `AppTokens` is registered on Mornye
(`MornyeTheme.tokens`) and falls back to it outside a themed subtree, the
Mornye dark accent, and Mornye's font families and letter spacing. Mornye
widgets have their own `test/mornye_*_test.dart` suites.
