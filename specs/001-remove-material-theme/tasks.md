# M001 tasks: Remove the Material theme and Material UI

Input: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md).
Follow `.specify/project-policy.md`. Read plan.md's Repository evidence first.

## Execution rules

Sequential tasks; no parallel markers. The **first action in every task**,
including [FINAL], is `python3 workflow/codegraph/code_tools.py update`; inspect
scope and coverage before edits and stop if it fails. Record refresh evidence in
`task-log.md` inside the task's single commit. Each task has at most three review
rounds and one commit after its checks and an independent review pass; push to
`wip` only after that commit. Each task leaves `flutter analyze` clean and
`flutter test` with no failures beyond the baseline in `task-log.md`. No dead code
is left for a later task except where a task below says so explicitly.
The analyzer flags only unused private code. After each collapse, also delete
public declarations, l10n strings and assets that lost their last reader: query
the map (`code_tools.py query --symbol`) and search before deleting, and keep
anything with a native, generated or reflective reader. Known from the planning
probe (research R7): `backdropBlurEnabledProvider` (`main_shell.dart:996`),
`catalogTrackPlaybackUiProvider`, `CollectionHeaderSkeletonRows`,
`appSheetDialogMaxWidth`, and the strings `extensionsVersion`, `nowPlayingTitle`,
`homeShortcuts`, `homeMixesForYou`, `equalizerManagePresets` (all 21 ARB files).

Common checks: `flutter gen-l10n` (after ARB changes), `flutter analyze`,
`flutter test` (full suite; baseline no failures, 10 skipped), and the task's
searches below.

## Implementation

- [x] M001-T001 Make Mornye the only theme setting
  - Start: refresh the map; query `ThemeSettings`, `ThemeNotifier`,
    `DynamicColorWrapper`, `AppThemeStyle`, `PlaybackNotification`.
  - Acceptance: FR-001, FR-003, FR-004, FR-005. No `AppThemeStyle`,
    `useDynamicColor`, `seedColorValue`, `useAmoled`, `DynamicColors`, dynamic-color
    provider or Material arm in the theme wrapper. Retired keys are removed once on
    load. Notification icons are the Mornye drawables. `dynamic_color` is not a
    direct dependency. Retired l10n keys and settings-search entries are gone.
    `AppTheme` remains only as the test harness theme until T005.
    FR-011: `AppSettings.playerCoverStyleFor` loses its `mornye` parameter and
    returns the stored style or `default`; its callers (`now_playing_screen.dart`,
    `playback_settings_page.dart`, `mini_player.dart`) are updated.
  - Paths: `lib/models/theme_settings.dart`, `lib/providers/theme_provider.dart`,
    `lib/theme/dynamic_color_wrapper.dart` (replaced by a Mornye-only wrapper),
    `lib/theme/app_theme.dart` (drop settings imports only), `lib/theme/mornye_theme.dart`
    (settings references only), `lib/app.dart`, `lib/main.dart`,
    `lib/services/playback_notification.dart`,
    `lib/providers/playback_notification_provider.dart`,
    `lib/screens/settings/appearance_settings_page*.dart`,
    `lib/screens/settings/settings_tab.dart`, `lib/utils/settings_search_catalog.dart`,
    `lib/l10n/arb/app_*.arb`, `pubspec.yaml`, `pubspec.lock`, and their tests
    (`test/appearance_settings_test.dart`, `test/theme_provider_persistence_test.dart`,
    `test/app_settings_model_test.dart`, `test/dynamic_color_launch_test.dart`,
    `test/music_player_automix_test.dart`, `test/player_widget_service_test.dart`,
    others found by the analyzer).
  - Validation: common checks; a test that stored `theme_style=material` plus the
    other retired keys loads Mornye settings and removes the keys; search
    `rg "AppThemeStyle|useDynamicColor|useAmoled|seedColorValue|dynamic_color" lib test`
    returns nothing.
  - Dependencies: planning commit.

- [x] M001-T002 Collapse Material branches in shared widgets and theme
  - Start: refresh the map; query each file in scope for callers.
  - Acceptance: FR-002, FR-006 for `lib/widgets`, `lib/theme`, `lib/utils`,
    `lib/routes`, `lib/controllers`: every `isMornye` condition there is replaced
    by its Mornye arm; Material-only locals, parameters, fields and helpers that
    lose their last reader are deleted; widgets with no remaining callers are
    deleted. Screens keep compiling; `isMornye` still exists for T003.
    FR-013 Settings widgets: `lib/widgets/settings_group.dart` (20 branches:
    `SettingsGroup`, `SliverSettingsGroup`, `SettingsItem`, `SettingsSwitchItem`,
    `SettingsSectionHeader`, `SettingsChoiceChip`) keeps only the Mornye arm;
    `MornyeSettingsTheme` always applies. `SettingsSwitchItem.icon` is deleted
    (Mornye never draws it) with the `icon:` argument at its 73 callers that pass
    one; `SettingsItem.showIconInMornye` is deleted, its 12 `true` callers keep
    `icon:`, and the 83 callers whose icon Mornye never drew lose `icon:`. These caller edits in `lib/screens` belong to this task.
    FR-014 Library widgets: `library_directory_view.dart` (42),
    `library_search_results.dart` (34), `library_fast_scroller.dart`,
    `library_mix_cover.dart`, `library_selection_playback_actions.dart`,
    `library_track_selection_bar.dart`, `track_lists/context_menu.dart` and
    `track_lists/library_track_menu.dart` keep the Mornye arm.
    This includes the Now Playing widgets (`mini_player.dart`,
    `player_playback_controls.dart`, `player_lyrics*.dart`,
    `player_details_sheet.dart`, `player_queue*.dart`, `player_volume_control.dart`
    and the other `player_*.dart`) and `lib/routes/now_playing_route.dart`.
  - Paths: the `isMornye` files listed in plan.md outside `lib/screens`, and the
    tests that cover them.
  - Validation: common checks; `rg -n "isMornye" lib --glob '!lib/screens/**'`
    returns only the getter's definition in `lib/theme/mornye_theme.dart`.
  - Dependencies: M001-T001.

- [x] M001-T003 Collapse Material branches in screens and remove isMornye
  - Start: refresh the map.
  - Acceptance: FR-002, FR-006 for `lib/screens`: all branches collapsed to the
    Mornye arm; Material-only screens, widgets and files left without callers are
    deleted (for example `material_player_layout.dart`,
    `material_player_backdrop.dart`, `player_artwork_theme.dart` once unused); the
    `isMornye` getter and its extension are deleted. FR-010 for Home and Now
    Playing explicitly: every `home_tab*.dart` and `now_playing_screen.dart`
    branch keeps the Mornye arm; `shellIndexOfTab`/`shellTabAt` lose `mornye:`
    and the live style-switch tab remap in `main_shell.dart` is deleted;
    `material_player_layout.dart`, `material_player_backdrop.dart`,
    `player_compact_bar.dart` and `player_artwork_theme.dart` are deleted once
    unused. The Now Playing widgets in `lib/widgets/player_*.dart` and
    `mini_player.dart` are done in T002. FR-013 Settings pages: the 16
    `lib/screens/settings` files with branches (53 references, most in
    `extension_detail_page_widgets.dart`, `extension_detail_page.dart`,
    `settings_tab.dart`) keep the Mornye arm; Settings stays a page.
    FR-014 Library: `queue_tab.dart` (85 branches), `queue_tab_*.dart`,
    `library_tracks_folder_screen.dart` (48), `downloaded_album_screen.dart` (49),
    `local_album_screen.dart` (46), `library_mix_screen.dart` (30),
    `library_directory_screen.dart` and `mornye_library_screen.dart` (23) keep the
    Mornye arm. On Mornye `QueueTab` with no `librarySection` returns
    `MornyeLibraryScreen` before anything else (`queue_tab.dart:517`), so every
    path that runs only when `librarySection == null` (`rootLibrary` in
    `queue_tab_filter_widgets.dart`, the root page controller, tabs, filter header
    and `_buildLibraryTools`) is Material-only and deleted; `main_shell.dart:1134`
    builds `MornyeLibraryScreen` directly and `QueueTab.librarySection` becomes
    required, with `isTabActive` deleted if only the root used it. The probe in
    research R6 did not fold this condition; re-count the `ExpressiveButton`
    sites afterwards.
  - Paths: the 63 `lib/screens` files in plan.md, files that become unused, and
    their tests (including `test/main_shell_keyboard_test.dart`,
    `test/shell_tab_style_switch_test.dart`, the now-playing and home suites).
  - Validation: common checks; `rg -n "isMornye" lib test` returns nothing; every
    `lib/screens` file in plan.md's appendix is done.
  - Dependencies: M001-T002.

- [x] M001-T004 Remove Material 3 Expressive, Material UI and Material Symbols
  - Start: refresh the map; query `materialExpressiveEnabled`,
    `MaterialExpressiveScope`, `AppMotion`, `AppShapeClip`, `AppIcon`.
  - Acceptance: FR-006, FR-007. `materialExpressiveEnabled`, every M3E arm,
    `MaterialExpressiveScope`, `lib/theme/material_expressive.dart`,
    `lib/theme/material_symbols.dart` and the M3E springs are deleted; widgets keep
    their former fallback arm. `lib/app.dart` uses the `flutter_localizations`
    Material delegate. `material_3_expressive`, `material_ui` and
    `material_symbols_icons` are not dependencies. No `Expressive*` or
    `Material*` widget remains and nothing is renamed (research R6): the 71
    Mornye `ExpressiveButton` sites use the framework button its fallback arm
    returned; the one `ExpressiveButtonGroup` site uses a `Row`;
    `ExpressiveIconButton`, `ToolButtonGroup`, `ExpressivePlaybackTransport` and
    `AudioOutputButton.backgroundColor` are deleted; the `ToolButton` data class
    in `tool_button_group.dart` is still used by Home search results and must
    survive (move it out of the deleted file). Re-run the probe's counts on
    the real tree first and note any difference in `task-log.md`.
    Setup flow explicitly: `setup_screen.dart` and `setup_steps.dart` keep the
    standard `FloatingActionButton.extended` next/finish button (the M3E
    `_buildExpressiveFab`/`M3EExtendedFab` goes). Owner request: the setup
    language list (`_buildLanguageList`, the only selectable list in setup) shows
    its selected row the way Mornye's Settings language picker does
    (`appearance_settings_page_widgets.dart` `_showLanguagePicker`): the existing
    `AppSheetOption` with the accent-coloured icon and bold accent title and a
    trailing check. The Material look goes entirely: the `primaryContainer`
    fill, the 16-to-28 pill morph and its `AppMotion` tweens, and
    `Icons.check_circle`. Add a widget test for the selected row.
  - Paths: the 34 `materialExpressiveEnabled` files and 32 M3E files in plan.md,
    `lib/app.dart`, `lib/theme/app_motion.dart`, `lib/widgets/app_shape.dart`,
    `lib/widgets/app_icon.dart`, `pubspec.yaml`, `pubspec.lock`, and the M3E tests
    (`test/material_expressive_test.dart`, `test/app_motion_test.dart`,
    `test/material_shared_controls_test.dart`, `test/app_icon_test.dart`, others
    found by the analyzer).
  - Validation: common checks;
    `rg -n "material_3_expressive|material_ui|material_symbols_icons|M3E|materialExpressive" lib test pubspec.yaml`
    and `rg -n "class (Expressive|Material)[A-Z]" lib` return nothing.
  - Dependencies: M001-T003.

- [x] M001-T005 Delete AppTheme, move tests to Mornye and update docs
  - Start: refresh the map.
  - Acceptance: FR-006, FR-008, FR-009. `lib/theme/app_theme.dart` is deleted;
    tests that pumped `AppTheme.light/dark` use `MornyeTheme.build` and keep their
    assertions; tests that only checked the Material look are deleted.
    `lib/theme/README.md`, `README.md` (features list) and `CONTRIBUTING.md`
    describe one Mornye theme; `pubspec.yaml` comments no longer mention removed
    packages.
    Also: the Material theme's bundled font goes: `assets/fonts/GoogleSansFlex.ttf`,
    `assets/fonts/GoogleSansFlex-LICENSE.txt`, its `pubspec.yaml` font and asset
    entries and its licence registration in `lib/main.dart:89` (Mornye uses
    Inter, the system font or a custom font). `context.tokens` falls back to
    `MornyeTheme.tokens` instead of the Material `AppTokens.standard`; `standard`
    stays only if `MornyeTheme.tokens` still derives from it. Material-only test
    files are deleted or rewritten for Mornye: `dynamic_color_launch_test.dart`,
    `material_expressive_test.dart`, `material_player_backdrop_test.dart`,
    `material_rounded_type_test.dart`, `material_shared_controls_test.dart`,
    `material_theme_roles_test.dart`, `player_lyrics_material_scroll_test.dart`,
    `expressive_playback_transport_test.dart`, `expressive_seek_track_test.dart`
    (each in the task that deletes its subject; `player_widget_service_test.dart`
    and `player_widget_ownership_test.dart` in TOR004).
  - Paths: `lib/theme/app_theme.dart`, `lib/theme/app_tokens.dart`, `lib/main.dart`,
    `assets/fonts/GoogleSansFlex*`, `test/**` (the 76 `AppTheme` users),
    `lib/theme/README.md`, `README.md`, `CONTRIBUTING.md`, `pubspec.yaml`.
  - Validation: common checks; `rg -n "AppTheme\b|AppTheme\.|GoogleSansFlex|Google Sans Flex" lib test pubspec.yaml`
    returns nothing; doc links resolve.
  - Dependencies: M001-T004.

## Owner extras

- [x] M001-TOR004 Remove the Android home-screen widgets
  - Request: the owner chose to delete both Android home-screen widgets (Player
    and Download queue), whose only look is Material 3, rather than keep or
    redesign them (FR-012).
  - Start: refresh the map; query `PlayerWidgetProvider`, `PlayerWidgetBridge`,
    `DownloadQueueWidgetProvider`, `PlayerWidgetService`.
  - Acceptance: neither widget is offered by the launcher; the widget providers,
    configuration activity, bridge, layouts, provider info, widget-only drawables,
    `WidgetConfigTheme` styles and widget strings are deleted; `DownloadService`
    no longer pushes widget state; the Dart `PlayerWidgetService` and its calls
    are deleted; the native `player_widget` and `download_widget_state`
    preference files are deleted once at start. The notification keeps its icons:
    `ic_widget_play`, `ic_widget_pause`, `ic_widget_next` and `ic_widget_previous`
    stay (used by `playback_notification.dart` and `MornyeApplication.kt`).
  - Paths: `android/app/src/main/AndroidManifest.xml`;
    `android/app/src/main/kotlin/com/amwolfstein/sistrum/PlayerWidgetProvider.kt`,
    `PlayerWidgetConfigurationActivity.kt`, `DownloadQueueWidgetProvider.kt`,
    `DownloadService.kt` (widget push only), `MainActivity.kt`
    (`PlayerWidgetBridge.handleIntent`), `SharedEngineChannels.kt`
    (`PlayerWidgetBridge.attach`), `MornyeApplication.kt` (one-time prefs cleanup);
    `res/layout/widget_*.xml`, `res/xml/widget_*_info.xml`,
    `res/drawable/widget_*.xml` and widget-only `ic_widget_*` drawables,
    `res/values*/player_widget_strings.xml`, widget strings in other `values*`,
    `res/values*/styles.xml` (`WidgetConfigTheme`), widget colours in
    `res/values*/colors.xml`; `lib/services/player_widget_service.dart`,
    `lib/main.dart`, `lib/services/music_player_runtime.dart`;
    `test/player_widget_service_test.dart`.
  - Validation: common checks; `rg -n -i "appwidget|PlayerWidget|DownloadQueueWidget|player_widget|download_widget" android lib test`
    returns nothing; every remaining `R.drawable`/`R.string`/`R.color` reference
    resolves (`./gradlew :app:compileDebugKotlin` or the lint resource check, if
    the Android toolchain runs here; otherwise say so in `task-log.md`). No APK.
  - Dependencies: M001-T005.

## Final cumulative review

- [ ] M001-T006 [FINAL] Cumulative review
  - Start: refresh the map and inspect coverage.
  - Acceptance: every FR and SC in spec.md holds on the cumulative diff from
    `5edaaac`; all actionable findings fixed.
  - Paths: all files changed in this mission, `task-log.md`, `tasks.md`.
  - Validation: `flutter analyze`; full `flutter test`; the searches of T001–T005 and TOR004;
    `python3 workflow/work.py validate`; review of the cumulative diff and the
    notification/native boundary. No APK unless the owner asks.
  - Dependencies: M001-T001 to M001-T005 and any extras.
  - Review/fix loop: at most three rounds; all fixes and evidence in one commit.

## Dependencies and evidence

T001 → T002 → T003 → T004 → T005 → TOR004 → T006. Log map, checks and review outcomes per
task in `task-log.md`. Stop and report after a third review that is not PASS.
