# Implementation Plan: Remove the Material theme and Material UI

**Branch**: `wip` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-remove-material-theme/spec.md`

## Changes from M002 (rebrand)

M002 runs first and changes this plan's baseline: the app is Sistrum
(`com.amwolfstein.sistrum`, `package:sistrum/`, version 0.1.0+1). These files
and features M002 removes are no longer M001 work: the What's New tour
(`lib/screens/whats_new_screen.dart`), the donate page and remote config
(`lib/screens/settings/donate_page.dart`, `lib/services/app_remote_config_service.dart`),
the About contributors and translators lists, the Telegram rows, the Zarz-LRC
provider, and signed extension sessions. Where this plan names one of them, the
file is already gone. Test counts are re-taken at M001's first task.

## Summary

Make Mornye the app's only theme and delete the Material design layer: the style
setting and its Material-only options, the Material theme builder, the ~465
`context.isMornye` branches (keeping their Mornye arm), Material-only widgets and
screens, the Material 3 Expressive bridge, Material Symbols, and the packages
`material_3_expressive`, `material_ui`, `dynamic_color` and
`material_symbols_icons`. Flutter's core `package:flutter/material.dart` stays,
because Mornye is built on it (research R1). Home, Now Playing, Settings, Library and
first-run setup are named explicitly (FR-010, FR-013 to FR-015). The two Android
home-screen widgets, Material 3 only, are deleted (FR-012, M001-TOR004).

## Technical Context

**Language/Version**: Dart 3.13.7, Flutter 3.47.8 (`.fvmrc`, SDK patch from
`scripts/prepare_flutter_sdk.dart` applied and verified)
**Primary Dependencies**: Flutter framework, flutter_riverpod, shared_preferences
**Storage**: SharedPreferences (theme keys, research R3)
**Testing**: `flutter analyze`, `flutter test` (568 test files)
**Target Platform**: Android (arm64 app); other Flutter targets compile the same Dart
**Project Type**: Mobile app
**Performance Goals**: No new rebuilds; theme still rebuilds only on the selected fields
**Constraints**: Mornye's look and behaviour stay exactly as before (constitution I)
**Scale/Scope**: 465 `isMornye` references in 134 lib files; 47
`materialExpressiveEnabled` uses in 34 files; M3E imported by 32 lib and 13 test
files; `AppTheme.light/dark` used by 76 test files

## Constitution Check

- I. Preserve behaviour: every collapsed branch keeps the Mornye arm verbatim;
  playback, decoders, native contracts and stored data other than the four
  retired keys are untouched. PASS.
- II. Plan substantial work: theme removal is a feature removal across many
  files; this Spec Kit plan with map-first evidence. PASS.
- III. One task, one reviewed commit: six sequential tasks, each map-first, with
  at most three review rounds and one commit. PASS.
- IV. Honest verification: baseline failures recorded below; no APK/device claim. PASS.
- V. Simple tooling: no new dependency; deletions only, plus renames where a
  surviving name says Material (research R6). PASS.

## Repository evidence

- **Baseline**: `wip` at `5edaaac` (`feat(android): ship an arm64-only Mornye app`),
  clean tree.
- **Map**: `python3 workflow/codegraph/code_tools.py update` succeeded: 1785
  extracted sources, 0 partial, 0 unindexed, 36740 symbols, 127523 relationships,
  0 rejected cross-language calls.
- **Read for this plan**: `pubspec.yaml`; `lib/theme/*` (README, `app_theme.dart`,
  `dynamic_color_wrapper.dart`, `material_expressive.dart`, `material_symbols.dart`,
  `mornye_theme.dart`, `app_motion.dart`, `cover_palette.dart`,
  `player_artwork_theme.dart`); `lib/models/theme_settings.dart`;
  `lib/providers/theme_provider.dart`; `lib/app.dart`; `lib/main.dart`;
  `lib/services/playback_notification.dart`;
  `lib/providers/playback_notification_provider.dart`;
  `lib/widgets/app_shape.dart`, `app_slider.dart`, `app_progress_indicator.dart`,
  `app_icon.dart`, `expressive_button.dart`, `expressive_seek_track.dart`,
  `playback_seek_slider.dart`, `mini_player.dart`; `lib/screens/main_shell.dart`,
  `now_playing_screen.dart`; the M3E and `material_ui` package READMEs and
  pubspecs in the pub cache; Android `PlayerWidgetProvider.kt`,
  `DownloadQueueWidgetProvider.kt`, `res/values*/colors.xml`.
- **Findings**:
  - Style switch: `AppThemeStyle { material, mornye }`, default `material`
    (`theme_settings.dart`), read by `theme_provider.dart`,
    `dynamic_color_wrapper.dart`, `main.dart:320` and
    `playback_notification_provider.dart:23`.
  - `material_ui` users: `lib/app.dart:254` (localization delegate),
    `theme_provider.dart` (dynamic-color `ColorScheme.fromSeed`),
    `dynamic_color_wrapper.dart`, `material_expressive.dart`.
  - `dynamic_color`: only `theme_provider.dart`. `material_symbols_icons`: only
    `lib/theme/material_symbols.dart` (through `AppIcon`).
  - `materialExpressiveEnabled` is false on Mornye; every M3E use is behind it,
    behind `!context.isMornye`, or inside Material-only widgets (research R2).
  - Material-only files expected to lose all callers: `app_theme.dart`,
    `dynamic_color_wrapper.dart`, `material_expressive.dart`,
    `material_symbols.dart`, `player_artwork_theme.dart` (only on Material arms in
    `now_playing_screen.dart`, `main_shell.dart`, `mini_player.dart`,
    `track_card.dart`), `material_player_backdrop.dart`,
    `material_player_layout.dart`, `player_compact_bar.dart`,
    `expressive_seek_track.dart`, `expressive_navigation_bar.dart`,
    `expressive_playback_transport.dart`, `tool_button_group.dart`,
    `expressive_icon_button.dart`. `expressive_button.dart` (71 Mornye call sites)
    and `expressive_button_group.dart` (1) are inlined and deleted. Counts come from
    the probe in research R6; each deletion is still confirmed in its task with
    the map and the analyzer.
  - `isMornye` split: 63 screen files (270 refs), 63 widget files (182 refs), and
    `lib/theme`, `lib/utils/adaptive_layout.dart`,
    `lib/routes/now_playing_route.dart`, `lib/controllers/extension_action_forms.dart`.
  - Home and Now Playing (owner asked for them explicitly; FR-010, FR-011):
    Home: `home_tab.dart` 90 branches, `home_tab_intro.dart` 14,
    `home_tab_search_results.dart` 15, `home_tab_search_wide.dart` 8,
    `home_tab_explore.dart`/`home_tab_widgets.dart` 3 each, `home_tab_import.dart` 1;
    shell tab mapping `shellIndexOfTab`/`shellTabAt` take `mornye:` and remap the
    tab when the style changes live (`main_shell.dart:87`, `:160`). Now Playing:
    `now_playing_screen.dart` 73, `now_playing_route.dart` 4, `mini_player.dart` 47,
    `player_playback_controls.dart` 48, `player_lyrics*.dart` 58,
    `player_details_sheet.dart` 11, `player_queue*.dart` 20,
    `player_volume_control.dart` 10, other `player_*.dart` 11;
    `AppSettings.playerCoverStyleFor({required bool mornye})`
    (`lib/models/settings.dart:412`) defaults to `default` on Mornye and
    `fullscreen` on Material; `mini_player.dart:79` reads the Material default.
  - Retired l10n keys: `appearanceStyle`, `appearanceDynamicColor(+Subtitle)`,
    `appearanceAmoledDark(+Subtitle)`, `appearanceSelectAccentColor` in the 21
    `lib/l10n/arb/app_*.arb` files; `lib/l10n/app_localizations*.dart` are generated
    and ignored.
  - No backup/export path reads the retired preference keys.
  - Android home-screen widgets do not read the in-app style and are Material 3
    only; the owner chose to delete them (research R5, M001-TOR004).
- **Limitations**: The map does not resolve `Theme.of`/extension lookups as edges;
  Material-arm reachability is read from source. Rendering is verified by widget
  tests, not on a device. A previous attempt at the screen/widget collapse turned
  213 tests red, almost all of them asserting the Material arm; those tests are
  part of the tasks below, not baseline failures.
- **Test baseline** on `5edaaac`: `flutter analyze` reports no issues. The full
  `flutter test` run gave 4355 passed, 10 skipped and 12 failed. All 12 were load
  failures from stale ignored `lib/l10n/app_localizations*.dart` left by an earlier
  attempt (for example `appearanceStyle` undefined). After `flutter gen-l10n`, those
  12 files pass (123 tests). Baseline: no failures, 10 skipped. Run
  `flutter gen-l10n` after every ARB change before testing.

## Project Structure

### Documentation (this feature)

```text
specs/001-remove-material-theme/
├── spec.md
├── plan.md
├── research.md
├── tasks.md
├── task-log.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
lib/models/theme_settings.dart, lib/providers/theme_provider.dart   settings and migration
lib/theme/                                                          theme builders, bridge, icons
lib/app.dart, lib/main.dart                                         app wiring
lib/widgets/, lib/screens/, lib/routes/, lib/utils/, lib/controllers/  branches to collapse
lib/services/playback_notification.dart                             notification icons
lib/l10n/arb/                                                       retired strings
test/                                                               affected suites
pubspec.yaml, pubspec.lock                                          removed packages
README.md, CONTRIBUTING.md, lib/theme/README.md                     docs
```

**Structure Decision**: Existing Flutter app layout; nothing new is added.

## Task design

1. **T001 Settings**: drop the style/dynamic/seed/AMOLED fields, keys and UI;
   remove retired keys on load; replace `DynamicColorWrapper` with a Mornye-only
   theme wrapper; Mornye notification icons unconditional; remove `dynamic_color`
   and the `material_ui` use in `theme_provider.dart`; delete retired l10n keys and
   search entries. `AppTheme` stays until T005 only as the test harness theme.
2. **T002 Widgets and theme**: collapse `isMornye` in `lib/widgets`, `lib/theme`,
   `lib/utils`, `lib/routes`, `lib/controllers`; delete widgets left with no callers;
   update the tests of those widgets.
3. **T003 Screens**: collapse `isMornye` in `lib/screens`; delete Material-only
   screens/widgets left unused; delete the `isMornye` getter; update screen tests.
4. **T004 Material 3 Expressive**: with `materialExpressiveEnabled` now always
   false, delete it, every M3E arm, `MaterialExpressiveScope`, `material_expressive.dart`,
   `material_symbols.dart` and the M3E springs in `app_motion.dart`; switch the
   `lib/app.dart` delegate to `flutter_localizations`; remove `material_3_expressive`,
   `material_ui` and `material_symbols_icons`; apply the naming rule (research R6);
   update or delete the M3E tests.
5. **T005 AppTheme, tests, docs**: delete `app_theme.dart`; move the remaining
   tests to `MornyeTheme.build`; delete tests that only checked the Material look;
   rewrite `lib/theme/README.md`, `README.md` and `CONTRIBUTING.md` theme sections.
6. **TOR004 Android home-screen widgets** (owner extra): delete the Player and
   Download queue widgets, their bridge, resources and Dart service; keep the
   notification icons (research R5).
7. **T006 [FINAL]**: cumulative review against spec/plan, full analyzer and test run.

Each task leaves the analyzer clean and the suite at baseline.

## Complexity Tracking

None.

## Appendix: every file with both looks

Generated on `5edaaac`: the 149 Dart files with a line naming `isMornye` or
`materialExpressiveEnabled` (the count is those lines; local `mornye` flags taken
from them add more uses). Every one keeps only its Mornye look (FR-002); the
owner asked for this explicitly for every screen with both looks. Material-only
paths behind another condition, like the Library root (FR-014), go too. Screens are done in
T003; widgets, theme, routes, utils and controllers in T002; M3E-only gates in T004.

- `lib/screens/` (48 files): `main_shell.dart` 26, `network_storage_screen.dart` 15, `repo_tab.dart` 14, `artist_screen_widgets.dart` 9, `track_metadata_cards.dart` 8, `downloaded_album_screen.dart` 8, `album_screen.dart` 8, `queue_tab_navigation.dart` 7, `now_playing_screen.dart` 7, `home_tab_search_results.dart` 7, `home_tab_intro.dart` 7, `whats_new_screen.dart` 5, `local_album_screen.dart` 5, `library_mix_screen.dart` 5, `artist_screen_discography.dart` 5, `track_metadata_edit_sheet.dart` 4, `track_metadata_edit_autofill.dart` 4, `queue_tab_filter_widgets.dart` 4, `queue_tab_collection_items.dart` 4, `listening_statistics_screen.dart` 4, `home_tab.dart` 4, `extension_collection_screens.dart` 4, `equalizer_widgets.dart` 4, `track_metadata_screen.dart` 3, `queue_tab_sections.dart` 3, `listening_rewind_screen.dart` 3, `library_tracks_folder_screen.dart` 3, `home_tab_widgets.dart` 3, `home_tab_search_wide.dart` 3, `home_tab_explore.dart` 3, `artist_screen.dart` 3, `track_metadata_screen_menu.dart` 2, `queue_tab_item_widgets.dart` 2, `playlist_screen.dart` 2, `music_recognition_screen.dart` 2, `listening_rewind_slides.dart` 2, `equalizer_screen.dart` 2, `track_metadata_mornye.dart` 1, `track_metadata_lyrics.dart` 1, `track_metadata_edit_cover.dart` 1, `setup_steps.dart` 1, `setup_screen.dart` 1, `queue_tab.dart` 1, `listening_rewind_widgets.dart` 1, `home_tab_import.dart` 1, `favorite_artists_screen.dart` 1, `concert_detail_screen.dart` 1, `artist_concerts_screen.dart` 1

- `lib/screens/settings/` (16 files): `extension_detail_page_widgets.dart` 15, `extension_detail_page.dart` 6, `settings_tab.dart` 4, `playback_settings_page.dart` 3, `lyrics_ai_translation_page.dart` 3, `donate_page.dart` 3, `appearance_settings_page_widgets.dart` 3, `app_settings_page.dart` 3, `about_page.dart` 3, `lyrics_settings_page.dart` 2, `files_settings_page_widgets.dart` 2, `extensions_page.dart` 2, `cache_management_page.dart` 2, `files_settings_page_reorganize.dart` 1, `extensions_page_widgets.dart` 1, `appearance_settings_page.dart` 1

- `lib/screens/repo/` (1 files): `extension_details_screen.dart` 10

- `lib/widgets/` (75 files): `settings_group.dart` 22, `album_detail_header.dart` 12, `playlist_picker_sheet.dart` 9, `mini_player.dart` 8, `download_service_picker.dart` 8, `animation_utils.dart` 8, `metadata_editor_fields.dart` 7, `update_dialog.dart` 6, `player_playback_controls.dart` 5, `batch_convert_sheet.dart` 5, `app_alert_dialog.dart` 5, `track_collection_quick_actions.dart` 4, `track_card.dart` 4, `selection_bottom_bar.dart` 4, `re_enrich_field_dialog.dart` 4, `player_lyrics_line_style.dart` 4, `player_lyrics.dart` 4, `album_description.dart` 4, `player_queue_dismissible.dart` 3, `metadata_text_field.dart` 3, `lyrics_editor_sheet.dart` 3, `library_directory_view.dart` 3, `audio_info_sheet.dart` 3, `app_switch.dart` 3, `app_sliver_header.dart` 3, `app_content_card.dart` 3, `app_bottom_sheet.dart` 3, `album_track_tile.dart` 3, `player_lyrics_word_sweep.dart` 2, `player_lyrics_section.dart` 2, `player_details_sheet.dart` 2, `lyrics_size_setting.dart` 2, `library_search_results.dart` 2, `extension_health.dart` 2, `expressive_navigation_bar.dart` 2, `app_menu_button.dart` 2, `app_choice_chip.dart` 2, `track_list_tile.dart` 1, `swipe_delete_dismissible.dart` 1, `slider_value_header.dart` 1, `selection_action_button.dart` 1, `re_enrich_review_sheet.dart` 1, `player_volume_control.dart` 1, `player_queue_sheet.dart` 1, `player_compact_bar.dart` 1, `playback_seek_slider.dart` 1, `playback_normalization_settings.dart` 1, `music_haptics_settings.dart` 1, `mornye_glass_preparation.dart` 1, `lyrics_options_button.dart` 1, `library_fast_scroller.dart` 1, `hires_check_card.dart` 1, `extension_repo_card.dart` 1, `expressive_icon_button.dart` 1, `expressive_button_group.dart` 1, `expressive_button.dart` 1, `error_card.dart` 1, `disc_separator_chip.dart` 1, `custom_font_setting.dart` 1, `collection_header_actions.dart` 1, `automix_settings.dart` 1, `audio_analysis_spectrogram.dart` 1, `artist_concerts_button.dart` 1, `app_text_field.dart` 1, `app_snack_bar.dart` 1, `app_slider.dart` 1, `app_shape.dart` 1, `app_search_field.dart` 1, `app_refresh_indicator.dart` 1, `app_progress_indicator.dart` 1, `app_loading_indicator.dart` 1, `app_icon_button.dart` 1, `app_icon.dart` 1, `app_checkbox.dart` 1, `app_action_button.dart` 1

- `lib/widgets/track_lists/` (1 files): `context_menu.dart` 1

- `lib/theme/` (5 files): `app_motion.dart` 6, `mornye_theme.dart` 2, `material_expressive.dart` 2, `mornye_icons.dart` 1, `cover_palette.dart` 1

- `lib/routes/` (1 files): `now_playing_route.dart` 2

- `lib/utils/` (1 files): `adaptive_layout.dart` 2

- `lib/controllers/` (1 files): `extension_action_forms.dart` 1

