# Theme Layer

The app has two visual styles, chosen in Appearance settings and stored as
`AppThemeStyle { material, mornye }` (`lib/models/theme_settings.dart`,
preference key `theme_style`, loaded by `lib/providers/theme_provider.dart`):

- **Material**: Material 3 with dynamic color or a seed color, optional AMOLED
  surfaces, and Material 3 Expressive motion and components.
- **Mornye**: Mornye's own visual system: grouped system colors, Cupertino
  typography and icons, and glass surfaces. Its
  palette is kept independent of wallpaper colors.

Both styles still produce a Flutter `ThemeData` with `useMaterial3: true`, so
widgets read `Theme.of(context).colorScheme` and `textTheme` the same way.
Mornye maps its colors onto Material 3 `ColorScheme` roles instead of
replacing them.

## Files

| File | Role |
| --- | --- |
| `lib/theme/mornye_theme_wrapper.dart` | `MornyeThemeWrapper`, used by `lib/app.dart`. Builds the light and dark Mornye `ThemeData` from the stored theme settings |
| `lib/theme/app_theme.dart` | `AppTheme.light` and `AppTheme.dark`: Material 3 component themes over a `ColorScheme` |
| `lib/theme/app_tokens.dart` | `AppTokens`, a `ThemeExtension` with the shared radii, spacing, cover sizes and header metrics |
| `lib/theme/mornye_theme.dart` | `MornyeTheme`: the Mornye `ThemeExtension`, its `ThemeData` builder, `context.isMornye`, `MornyeSettingsTheme` |
| `lib/theme/mornye_icons.dart` | `mornyeIconFor` (Material icon to Cupertino icon) and `context.adaptiveIcon` |
| `lib/theme/app_motion.dart` | `AppMotionSpec`, a duration and curve pair for implicit animations |
| `lib/theme/cover_palette.dart` | `CoverPalette`, `HeaderPalette`, `CoverPaletteBuilder` and `CoverSourceColorBuilder`: colors extracted from artwork |

## How a Theme Is Built

`MornyeThemeWrapper` watches `themeProvider` and the custom font provider.

- Material (`AppTheme`): only the test harness theme until M001-T005 deletes it.
- Mornye: `MornyeTheme.build(brightness, ...)` takes the Mornye accent
  (`MornyeAccent`, red by default), the system-font and custom-font choices,
  glass clarity (default 0.75) and glass outlines. It seeds a `ColorScheme`
  from the accent and overrides the roles with grouped colors (for
  example `#F2F2F7` and `#1C1C1E`). Text uses Cupertino styles. The font is
  the custom font when one is set; otherwise the Cupertino system font
  (`CupertinoSystemText`) when the system font is chosen, and Inter by
  default. It registers its own
  `MornyeTheme.tokens`, a copy of `AppTokens.standard` with different radii
  (smaller for most shapes, larger for sheets) and large title sizes, and
  removes ink splashes.

`MornyeTheme.build` caches up to 16 base themes; changing only glass clarity
or outlines reuses the cached theme. Use `MornyeTheme.fromContext` to rebuild
a local surface (another brightness or chrome color) without losing the
user's Mornye preferences.

## Using the Layer in Widgets

- Read tokens with `context.tokens`, not `Theme.of(context).extension`. It
  falls back to `AppTokens.standard` outside a themed subtree.
- Branch on `context.isMornye`, which is true when the nearest `Theme` carries
  a `MornyeTheme` extension. Because it follows the nearest `Theme`, a
  subtree can be switched by wrapping it in another theme.
- Keep `Icons.*` at call sites and pass them through `context.adaptiveIcon`;
  Mornye then shows the Cupertino glyph from `mornyeIconFor`.
- Wrap settings pages in `MornyeSettingsTheme` for inset grouped
  surfaces; it does nothing in the Material style.

## Mornye Outside This Folder

- Glass rendering level: `MornyeGlassLevel` (`flat`, `frosted`, `liquid`) and
  `mornyeGlassLevelProvider` in `lib/providers/runtime_profile_provider.dart`
  choose how much glass a device renders.
- Mornye widgets live in `lib/widgets/mornye_*.dart`, for example `MornyeGlass`
  in `lib/widgets/mornye_chrome.dart`. Mornye-only screens are
  `lib/screens/mornye_library_screen.dart` and
  `lib/screens/track_metadata_mornye.dart`; most other screens branch on
  `context.isMornye`.

## Tests

`test/design_system_test.dart` checks that `AppTokens` is registered on the
Material themes (`AppTokens.standard`) and on Mornye (`MornyeTheme.tokens`),
the Mornye dark accent, and Mornye's font families and letter spacing. Mornye
widgets have their own `test/mornye_*_test.dart` suites.
