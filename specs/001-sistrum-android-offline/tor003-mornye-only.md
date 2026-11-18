# M001-TOR003 — Mornye is the only theme; the Material design layer is removed

Owner extra of M001, run **after M001-T006 and before M001-T007**, as **one TOR, one
reviewed commit**. Updates the earlier plan `eada690` (`specs/001-remove-material-theme/`,
written on `wip@5edaaac`, Flutter 3.47) for this base (`alpha1` lineage, Flutter 3.50,
after M001-T001..T005). Planning only: this file authorizes nothing until T006 is done.

Owner request (Egyptian Arabic, paraphrased): after T006, open a TOR that removes Material
UI, Expressive, the Material theme and everything related to Material. Mornye stays the
main theme. Same plan as `eada690`, refreshed for the newer base, executed in one TOR.

## Findings on this base (map-first, `code_tools.py update`: 1,504 sources, 32,086 symbols, 0 partial)

| Measure | `eada690` base | This base |
| --- | --- | --- |
| `context.isMornye` lines / files in `lib/` | 465 / 134 | **388 / 121** |
| `materialExpressiveEnabled` lines / files | 47 / 34 | **46 / 38** |
| `material_3_expressive` imports (lib / test) | 32 / 13 | **38 / 18** |
| `dynamic_color`, `material_symbols_icons` | 1 file each | 1 lib + 1 test each |
| Tests naming `AppTheme.`; `AppThemeStyle` files | 76 | **117**; 13 |
| Other Android home-screen widgets | Player + Download queue | **Player only** (Download widget left with M001-T005) |
| Dart/Flutter | 3.13.7 / 3.47.8 | **3.14 / 3.50.0** |

### Decision that differs from the old plan (owner confirmed: keep `material_ui`)

The old plan removed the package `material_ui` and kept `package:flutter/material.dart`.
**On Flutter 3.50 that import is `@Deprecated('Use package:material_ui/material_ui.dart')`**
(`/opt/toolchain/flutter/packages/flutter/lib/material.dart`). This app already imports
`package:material_ui/material_ui.dart` in **237 `lib/` and 251 test files** (and
`cupertino_ui` in 51): `material_ui` *is* the framework's Material widget library
(`ThemeData`, `Scaffold`, buttons, `Icons`) that `MornyeTheme.build` and the Mornye arms
use. Removing it would mean rewriting Mornye or switching 488 files to a deprecated
import (analyzer warnings, which breaks the clean-analyze gate).
**Owner confirmed this recommendation. This TOR therefore keeps `material_ui` as the framework layer and removes only the
Material *design* layer.** Same rationale as old R1; if the owner wants `material_ui`
gone anyway, that is a separate, much larger task (rebuild Mornye on `widgets` +
`cupertino_ui`).

## Scope (all in this one TOR)

Delete, keeping only the Mornye arm of every branch (Mornye looks and behaviour unchanged):

1. **Style setting and options**: `AppThemeStyle`, `theme_style`, dynamic colour, seed
   colour, AMOLED (`lib/models/theme_settings.dart`, `lib/providers/theme_provider.dart`,
   Appearance page, settings search, 12+ l10n keys in 21 ARB files); on load remove the
   four retired preference keys once, keep every other theme/lyrics preference. Anyone
   on Material opens in Mornye with mode, accent, font and glass choices kept.
2. **Theme code**: `lib/theme/app_theme.dart`, `dynamic_color_wrapper.dart`,
   `material_expressive.dart`, `material_symbols.dart`, `player_artwork_theme.dart`
   (Material arms only), the Google Sans Flex font (`AppTheme.bundledFontFamily`),
   `AppTokens.standard` fallback becomes `MornyeTheme.tokens`.
3. **Branches**: every `context.isMornye` / `materialExpressiveEnabled` (388 + 46 lines),
   then the getters themselves. Home, Now Playing (card default cover style, not Material's
   full screen), Settings as a page (not a tab), Library root (Mornye's), first-run setup.
4. **Material-only widgets/screens**: `Expressive*` (button, button group, icon button,
   navigation bar, playback transport, seek track), `ToolButtonGroup`,
   `material_player_backdrop/layout`, `player_compact_bar`, Material root Library page and
   its tools; each `ExpressiveButton` call becomes the framework button its fallback arm
   returned. Re-check each deletion with the map and analyzer (old R6 table is a floor).
5. **Packages**: remove `material_3_expressive`, `dynamic_color`, `material_symbols_icons`
   (`pubspec.yaml`, `pubspec.lock`). Keep `material_ui`, `cupertino_ui`,
   `cupertino_icons`, `material_color_utilities` (artwork colours, Mornye uses it),
   `flutter_localizations`, `uses-material-design: true` (Icons font Mornye still draws).
6. **Native**: media-notification icons always Mornye; delete the Android **Player
   home-screen widget** (`PlayerWidgetProvider.kt`, `PlayerWidgetConfigurationActivity.kt`,
   `widget_player*.xml`, `widget_player_info.xml`, manifest entries, `player_widget_service.dart`,
   its feed in `music_player_runtime.dart`/`main.dart`, saved widget prefs); keep
   `ic_widget_play/pause/next/previous` used by the notification.
7. **Tests/docs**: delete tests that only checked the Material look; move the rest to
   `MornyeTheme.build` (117 files use `AppTheme.`) keeping their assertions; rewrite
   `lib/theme/README.md`, `README.md`, `CONTRIBUTING.md` theme sections; leftover strings,
   providers and assets the analyzer does not report (old R7).

Out of scope: playback/native audio, data formats other than the four theme keys, other
platforms (T006), device testing.

## Execution inside the one TOR

One reserved ID, one commit, one independent review (up to 3 rounds). Work in order, each
step keeping `flutter analyze` clean: (a) settings, migration, theme wiring, notification;
(b) widgets/theme/routes/utils/controllers collapse; (c) screens collapse and Material-only
screens; (d) M3E removal and packages; (e) AppTheme removal, tests, docs, Android widget.
Parallel Haiku workers on disjoint file sets as in M001-T005; a coordinator runs the
comparison and fixes shared regressions. Record at the end: 213 tests turned red in the
earlier attempt because they asserted the Material arm; those are part of this TOR.
Lesson from M001-T005: when folding a branch, check removed `&&`/`||` guards, list
indices and now-unconditional blocks, not only deleted lines.

## Acceptance and checks

- 0 places choose between a Material and a Mornye look; 0 imports of the three removed packages
  in `lib/`, `test/`, `integration_test/`; no `isMornye`, `AppThemeStyle`, `AppTheme`,
  `materialExpressiveEnabled`, `Expressive*`, `MaterialExpressiveScope` left.
- `flutter analyze --no-pub`: only the known baseline info; `flutter gen-l10n` after ARB edits.
- `flutter test`: no new failures against the M001-T005 final run (4,581 pass; 45 baseline
  environment failures), measured with the chunked JSON method used in T005.
- Stored-preference migration tested (retired keys removed once; others kept; unexpected types
  tolerated); cold start opens on Home; notification uses Mornye icons.
- `:app:compileReleaseKotlin` after the widget removal; merged manifest has no widget receiver;
  a release APK arm64 build if the owner asks (not part of the TOR by default).
- Record in `task-log.md`: counts before/after, deleted file list, decisions
  (`material_ui` kept, Player widget deleted), not verified on device.

## Risks

- Large mechanical diff (about 121 `isMornye` files plus 38 M3E files plus 117 test files):
  mitigated by disjoint worker file sets and the T005 comparison method.
- Mornye arms that depended on `!context.isMornye` guards elsewhere (hidden conditions).
- Fonts: custom-font loading must not rebuild the theme in a loop (old edge case).
- First-run setup and tutorial now show Mornye; Settings tab index and persisted shell tab
  need remapping (old edge case: app opens on Home).
