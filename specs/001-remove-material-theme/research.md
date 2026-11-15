# Research: Remove the Material theme and Material UI

## R1. What "Material UI" covers

- **Decision**: Remove the app's Material design layer: the `AppThemeStyle`
  switch and its settings, `AppTheme`, `DynamicColorWrapper`, every
  `context.isMornye` Material arm, Material-only widgets and screens, the
  Material 3 Expressive bridge, Material Symbols, and the packages
  `material_3_expressive`, `material_ui`, `dynamic_color` and
  `material_symbols_icons`. Keep Flutter's core `package:flutter/material.dart`.
- **Rationale**: `material_ui` 1.9.0 is the decoupled copy of Flutter's Material
  library; this app uses it only to feed `material_3_expressive` widgets
  (`lib/theme/material_expressive.dart`), for `ColorScheme.fromSeed` in the
  dynamic-color provider and for one localization delegate in `lib/app.dart`.
  Mornye itself is a `ThemeData` built by `MornyeTheme.build`
  (`lib/theme/mornye_theme.dart`) on `package:flutter/material.dart`, and its
  arms use the framework's `FilledButton`, `TextButton`, `Slider`, `Scaffold`
  and `Icons`. Removing the framework library would mean rewriting Mornye.
- **Alternatives considered**: Rebuilding Mornye on `WidgetsApp`/Cupertino only:
  rejected, it is a redesign the owner did not ask for and it changes Mornye.

## R2. Material 3 Expressive is Material-only today

- **Decision**: Delete every M3E arm and the bridge; keep the fallback arm.
- **Rationale**: `materialExpressiveEnabled(context)` is
  `!context.isMornye && !MediaQuery.disableAnimationsOf(context)`, so it is false
  on Mornye. Every M3E widget sits behind it, behind `!context.isMornye`
  (`AppShapeClip`), or in a Material-only widget (`ExpressiveSeekTrack`, used
  only on the non-Mornye arm of `PlaybackSeekSlider`). The fallback arm is what
  Mornye already renders. `AppMotion` presets already return the caller's
  fallback spec on Mornye. `M3EShapeKind` is used as a parameter type by
  `AppShapeClip`/`AppShapeBadge`, whose Mornye path ignores it; the parameter
  goes with the package.

## R3. Stored preferences

- **Decision**: Remove `theme_style`, `use_dynamic_color`, `seed_color` and
  `use_amoled` once when theme settings load; write nothing in their place.
- **Rationale**: Mornye is the only theme, so the values have no reader. Only
  `SharedPreferences` holds them (no backup/export path reads these keys:
  checked `lib/services`, no `getKeys()` user). Removing them is idempotent and
  needs no version flag.

## R4. Packages that stay

- `material_color_utilities`: Mornye's artwork colours (`cover_palette.dart`,
  used by `CoverSourceColorBuilder` in `mornye_player_background.dart` and
  `mornye_player_actions_sheet.dart`). It is also a Flutter dependency.
- `cupertino_icons`: Mornye glyphs.
- `flutter: uses-material-design: true`: ships the `Icons` font that Mornye still
  draws for icons without a Cupertino counterpart in `mornyeIconFor`.

## R5. Native surfaces

- Media notification: `PlaybackNotification` picks Mornye drawables only when the
  stored style is Mornye; they become unconditional.
- Android home-screen widgets (`PlayerWidgetProvider.kt`,
  `PlayerWidgetConfigurationActivity.kt`, `DownloadQueueWidgetProvider.kt`) never
  read the in-app style; their only look is Material 3 (tonal/dynamic colour
  roles, an Expressive squircle play button, a wavy progress bar).
  **Decision**: delete them (owner, 2026-11-14), task M001-TOR004. Keep
  `ic_widget_play/pause/next/previous`: the Mornye notification uses them
  (`playback_notification.dart`, `SistrumApplication.kt`). Delete the native
  `player_widget` and `download_widget_state` preference files once at start,
  as FR-004 does for the retired theme keys.

## R6. Naming of surviving wrappers

- **Decision**: No widget is renamed. Every `Expressive*`/`Material*` widget is
  either deleted, where Mornye never renders it, or replaced at its call sites by
  the framework widget Mornye already renders.
- **Probe** (2026-11-14, scratch copy of `e5a6105`, not the repository): every
  `context.isMornye` folded to true and every `materialExpressiveEnabled(...)`
  folded to false with the collapse tool, then declarations the analyzer reports
  unused removed until stable (11 unrelated leftovers remain). Call sites that
  survive are the ones Mornye can reach:

  | Widget | Call sites now | Reached on Mornye | Plan |
  | --- | --- | --- | --- |
  | `ExpressiveButton` | 94 | 71 in 38 files | each becomes the `FilledButton`, `FilledButton.tonal`, `OutlinedButton` or `TextButton` (or `.icon`) its fallback arm returned; file deleted |
  | `ExpressiveIconButton` | 16 | 0 | deleted. The 3 sites in `player_compact_bar.dart` are only used by `MaterialPlayerLayout`; the site in `audio_output_button.dart` runs only with `backgroundColor`, which neither caller (`now_playing_screen.dart`, `mornye_landscape_player.dart`) passes, so that parameter goes too |
  | `ExpressiveButtonGroup` | 5 | 1 (`library_tracks_folder_screen.dart`) | that site becomes a `Row` with the same spacing and `Flexible` child; file deleted |
  | `ToolButtonGroup` | 2 | 0 | deleted. It has no fallback arm, but both callers (`queue_tab_sections.dart` `_buildLibraryTools`, `home_tab_search_results.dart`) are behind `!materialExpressiveEnabled`/`!mornye` |
  | `ExpressivePlaybackTransport` | 2 | 0 | deleted (Mornye: `MornyePlaybackButton`) |
  | `ExpressiveNavigationBar` | 1 | 0 | deleted (Mornye: `MornyeBottomBar`) |
  | `ExpressiveSeekTrack` | 1 | 0 | deleted (Mornye: `MornyePlayerSlider`) |
  | `MaterialPlayerLayout`, `MaterialPlayerBackdrop` | 1 each | 0 | deleted with `player_compact_bar.dart` (Mornye: `MornyePlayerLayout`, `MornyePlayerBackground`) |

  Left for the task to read by hand: `PlayerArtworkTheme` in `track_card.dart`
  (`_CurrentTrackArtworkTheme`, whose constructor has no caller in the probe) and
  `MaterialExpressiveScope` in `app_menu_button.dart` and `app_text_field.dart`.
  - `App*` widgets (`AppIconButton`, `AppSlider`, ...) keep their names; any
    whose only remaining body is a pass-through to one framework widget is
    inlined at its callers too. Doc comments describing the Material arm are
    rewritten for what remains.
- **Rationale**: The owner asked why a renamed wrapper should stay when Mornye
  already has, or already shows, the replacement. Using Mornye's own widget or
  the framework widget it renders leaves nothing Material in the code.

## R7. Leftovers the analyzer does not report

- **Probe** (same scratch copy as R6): public declarations, strings and assets
  read today but not after the collapse.
- Public declarations: `backdropBlurEnabledProvider`
  (`runtime_profile_provider.dart`, read only by the Material shell bar),
  `catalogTrackPlaybackUiProvider` (`music_player_provider.dart`),
  `CollectionHeaderSkeletonRows` (`animation_utils.dart`),
  `appSheetDialogMaxWidth` (`app_bottom_sheet.dart`).
- Strings: `extensionsVersion`, `nowPlayingTitle`, `homeShortcuts`,
  `homeMixesForYou`, `equalizerManagePresets`.
- Font: Google Sans Flex (571 KB) is `AppTheme.bundledFontFamily`; Mornye uses
  Inter, the Cupertino system font or a custom font, so it goes with `AppTheme`.
- Tokens: `context.tokens` falls back to `AppTokens.standard`, the Material
  values; the fallback becomes `MornyeTheme.tokens`.
- The probe does not fold the Library-root condition (FR-014), so each task
  re-checks after its own collapse; the lists above are a floor, not the total.
- Checked and not affected: no `AppSettings` field loses all its readers;
  Android `LaunchTheme`/`NormalTheme` use platform themes, not Material
  Components; no Gradle Material dependency; CI workflows name no removed test.
