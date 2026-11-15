# M001 task log

Baseline: `5edaaac` on `wip` (see plan.md, Repository evidence).

Baseline checks on `5edaaac`: `flutter analyze` no issues; full `flutter test`
4355 passed, 10 skipped, 12 load failures from stale generated l10n; after
`flutter gen-l10n` the 12 files pass (123 tests). Baseline: no failures, 10 skipped.

## M001-T001 Make Mornye the only theme setting

- **Map**: `python3 workflow/codegraph/code_tools.py update` was the first action:
  36109 symbols, 125713 relationships, 1756 extracted sources, 0 partial,
  0 unindexed, 0 rejected cross-language calls. Then searched `ThemeSettings`,
  `ThemeNotifier`, `DynamicColorWrapper`, `AppThemeStyle` and
  `PlaybackNotification` users with `rg` (the map does not resolve provider
  selects as edges).
- **Changed**: `lib/models/theme_settings.dart` (style, dynamic color, seed color,
  AMOLED fields, keys and `AppThemeStyle` removed); `lib/providers/theme_provider.dart`
  (no `DynamicColors`/`initialDynamicColorsProvider`, no `material_ui`, retired keys
  `theme_style`, `use_dynamic_color`, `seed_color`, `use_amoled` removed once with
  the glass-clarity key, whatever their stored type; setters removed);
  `lib/theme/dynamic_color_wrapper.dart` replaced by `lib/theme/mornye_theme_wrapper.dart`
  (`MornyeThemeWrapper`); `lib/app.dart`, `lib/main.dart` (no dynamic-color
  request or wait); `lib/services/playback_notification.dart` and
  `playback_notification_provider.dart` (Mornye drawables unconditional, `mornye`
  field gone); `AppSettings.playerCoverStyleFor()` has no parameter and returns the
  stored style or `default` (callers `now_playing_screen.dart`,
  `playback_settings_page.dart`, `mini_player.dart`);
  `appearance_settings_page*.dart` (style list, dynamic color, palette picker and
  AMOLED switch deleted); `settings_search_catalog.dart` (dynamic color and AMOLED
  entries and the `seed color` keyword deleted); `lib/theme/app_theme.dart` (seed
  constant inlined, settings import dropped); `lib/theme/README.md` (wrapper rows
  only; the full rewrite is T005); `pubspec.yaml` (`dynamic_color` no longer a
  direct dependency; it stays in `pubspec.lock` as transitive through
  `material_3_expressive` until T004); six l10n keys (`appearanceStyle`,
  `appearanceDynamicColor(+Subtitle)`, `appearanceAmoledDark(+Subtitle)`,
  `appearanceSelectAccentColor`) deleted from all 21 ARB files (579 deletions).
- **Tests**: `test/dynamic_color_launch_test.dart` deleted (subject gone);
  `material_theme_roles_test.dart` loses the AMOLED test; `mornye_theme_identity_test.dart`,
  `appearance_settings_test.dart` (style-switch test replaced by "no Material-only
  options"), `theme_provider_persistence_test.dart` (new tests: retired keys
  removed once with the rest kept, and removed when stored with unexpected types),
  `app_settings_model_test.dart`, `player_presentation_settings_test.dart`,
  `music_player_automix_test.dart`, `player_widget_service_test.dart` updated.
- **Checks**: `flutter gen-l10n` ok; `flutter analyze`: no issues; full
  `flutter test`: 4402 passed, 10 skipped, 0 failed (exit 0; baseline 0 failed,
  10 skipped);
  `rg "AppThemeStyle|useDynamicColor|useAmoled|seedColorValue|dynamic_color" lib test`
  returns nothing.
- **Not verified**: rendering on a device; the Android notification on a device.

## M001-T002 Collapse Material branches in shared widgets and theme

- **Map**: `python3 workflow/codegraph/code_tools.py update` was the first
  action of the task: 36035 symbols, 124698 relationships, 1755 extracted
  sources, 0 partial, 0 unindexed, 0 rejected cross-language calls. Scope: every
  `lib/widgets`, `lib/theme`, `lib/utils`, `lib/routes`, `lib/controllers` file with an
  `isMornye` condition (about 110 files, 195 references) plus the `lib/screens`
  callers the task assigns to it.
- **Method**: a throw-away Dart AST tool (scratchpad, not committed) folded
  `context.isMornye` to `true`, folded `&&`, `||`, `!`, ternaries, `if`
  statements and collection `if`s, propagated `final x = <folded>` locals and
  dropped code after a folded early `return`. Each result was then read, the
  analyzer's unused imports, locals and private declarations were removed, and
  private classes referenced only by themselves (`_MiniPlayerWave`,
  `_MaterialContextMenu`) were deleted by hand. A second AST pass removed named
  arguments at call sites.
- **FR-013**: `SettingsSwitchItem.icon` deleted with the `icon:` argument at its
  call sites; `SettingsItem.showIconInMornye` deleted, the 12 `true` callers keep
  `icon:`, the others lost it; `SettingsGroup.connected`, the `connected:` argument
  of `settingsSectionSlivers`, `_ConnectedRowsScope` and `settingsRowsConnected`
  deleted (it was never true on Mornye); `MornyeSettingsTheme` always applies.
- **Dead code removed with the branches**: `TrackCardSegment` (inert on Mornye;
  its callers became `KeyedSubtree`), `TrackCard.isCurrent/isPlaying/leadingArtwork`
  and the current-track pill, `NowPlayingIndicator`, `ExpressiveSeekTrack`,
  `searchCoverShelfExtent`, the Material mini-player progress wave and the
  full-screen artwork warm-up, `SelectionActionButton.colorScheme`, the
  `backgroundColor` parameter of the app bottom-sheet helpers, and the
  `neutralGrayscale` state of `CoverPaletteBuilder`.
- **Left for later tasks (still read by Material-only code in `lib/screens`)**:
  `isMornye` itself and the `lib/screens` branches (T003); `materialExpressiveEnabled`
  (now a constant `false`) and its M3E arms (T004); `PlayerArtworkTheme`,
  `CoverPalette.neutralGrayscale`, `connectedRowRadius`, `catalogTrackPlaybackUiProvider`
  (their only readers are Material arms in `lib/screens`, deleted in T003);
  `lib/theme/README.md` mentions of `context.isMornye` (T005 rewrites the file).
- **Tests**: Material-arm tests deleted (`album_completeness_query_test`,
  `app_dialog_size_test`, `app_progress_indicator_test`, `mini_player_motion_test`,
  `player_lyrics_material_scroll_test`, `expressive_seek_track_test`,
  `now_playing_catalog_rows_test`, and the Material tests inside
  `material_expressive_test`, `material_shared_controls_test`,
  `material_theme_roles_test`, `now_playing_lyrics_transition_test`, `app_icon_test`,
  `app_motion_test`, `design_system_test`, `library_toolbar_test`,
  `mini_player_*`, `player_artwork_theme_test`, `slider_value_header_test`,
  `track_metadata_header_contrast_test`, `player_widget_ownership_test`,
  `decorative_phase_test`). Shared-behaviour tests run on `MornyeTheme.build` with
  a `ProviderScope` and `AppLocalizations` where the Mornye widgets need them, and
  their expectations follow what Mornye draws (Cupertino icons, `AppAlertDialog`,
  `CupertinoTextField`, inline slider labels).
- **Checks (before review)**: `flutter analyze`: no issues. Full `flutter test`:
  4218 passed, 10 skipped, 1 failed (`now_playing_lyrics_transition_test` Go to
  Album icon expectation), fixed and re-run alone: passed. The final full run is
  under "Review round 1".
  `rg -n "isMornye" lib --glob '!lib/screens/**'` returns only the getter in
  `lib/theme/mornye_theme.dart` and three prose mentions in `lib/theme/README.md`
  (rewritten in T005).
- **Not verified**: rendering on a device.

### Review round 1 (NEEDS_FIXES) and fixes

1. `test/album_completeness_query_test.dart` restored from HEAD with only the
   Material widget test removed; the production-queries DB test is kept and
   passes (its now-unused fixtures and imports went with the removed test).
2. Unused parameters deleted with their fields and call-site arguments:
   `PlayerPlaybackModeButton.secondary`, `PlaybackSeekSlider.playing`,
   `AppChoiceChip.singleChoice`, `CollectionHeaderActions.downloadIcon`,
   `AppDialogAction.outlined`. A re-scan of the widget, theme, util, route and
   controller classes found four more, also deleted: `AppAlertDialog.shape`,
   `AppSwitch.adaptive`, `AppSwitchListTile.adaptive`, and the `colorScheme`
   argument of `showPlayerDetailsSheet` / `_MetadataList`.
   `AppShapeClip.kind` is left for T004 (it goes with the M3E package, research R6).
3. `library_search_results.dart`: the always-null `playback` parameter of
   `card(...)` is gone with its dead branches and the unused `Consumer` wrapper.
4. Dead arms and stale comments cleaned: `settings_group.dart` choice chip
   (`unselectedColor`, unreachable `onPrimaryContainer` arm), `track_card.dart`
   (`contentColor` was always null), `mini_player.dart` (`marqueeCycles`, the empty
   `DecoratedBox`, `vertical: 0`), `transportTopPadding + 0`, the one-element `...[`
   in `extension_action_forms.dart`, and the Material wording in the comments of
   `album_detail_header`, `app_bottom_sheet`, `animation_utils`, `track_card`,
   `settings_group`, `app_content_card`, `album_track_tile`, `context_menu`,
   `track_collection_quick_actions`, `library_search_results`, `app_snack_bar`,
   `slider_value_header`, `update_dialog` and `player_playback_controls`. Comments
   that describe code deleted in T003/T004 stay until that code goes.
5. `now_playing_lyrics_transition_test` "Go to Album": only the label assertion
   remains; the icon assertion is gone because that row has no icon on Mornye.
- **Checks**: `flutter analyze`: no issues. Full `flutter test`: 4220 passed,
  10 skipped, 0 failed (exit 0; log
  `/tmp/claude-0/-home-user-Sistrum/6f2a99b7-2c8e-50d8-b242-f10db4d01b53/scratchpad/t002-test3.log`).

## M001-T003 Collapse Material branches in screens and remove isMornye

- **Map**: `python3 workflow/codegraph/code_tools.py update` was the first action:
  35625 symbols, 122940 relationships, 1747 extracted sources, 0 partial,
  0 unindexed. Scope: the 63 `lib/screens` files of plan.md's appendix and the
  `isMornye` extension in `lib/theme/mornye_theme.dart`; callers read with `rg`
  (the map does not resolve provider selects).
- **Method**: the T002 AST tool (scratchpad, not committed; one fix for a dropped
  trailing comma) folded `context.isMornye` in every `lib/screens` file except
  `now_playing_screen.dart`, which was folded by hand. Results were formatted and
  read; unused imports, locals and private declarations were removed with the
  analyzer; then leftovers the analyzer does not report were searched for (named
  arguments that every caller passes as one constant, `this.X` parameters and fields
  with no reader, l10n keys with no reader).
- **Shell**: `isMornye` getter and extension deleted. `shellTabAt`/`shellIndexOfTab`
  and the live style-switch tab remap deleted (their test
  `shell_tab_style_switch_test.dart` with them); the tab-jump fade controller and
  `SingleTickerProviderStateMixin` deleted; `_KeepAliveTabPage` deleted; the Library tab builds
  `MornyeLibraryScreen` directly (`_LibraryTabRoot`). `ShellNavigationService.syncState`
  loses `showSearchTab` (the Search tab always exists).
- **Library (FR-014)**: `QueueTab.librarySection` is required and `isTabActive` is gone;
  the root page controller, filter pager, `_LibraryPageTabs` use, Albums/Singles
  toggle, Artists page, `_buildLibraryTools`, sort/view buttons, sectioned track
  slivers, `rootLibrary` and the `specialEntries` playlists lead are deleted.
- **Home (FR-010)**: `HomeTabMode.combined` deleted (`mode` is required);
  greeting, tagline, shortcuts and mixes-shelf widgets deleted with
  `lib/utils/home_greeting.dart` and `ExploreState.greeting`;
  `HomeSearchProviderPolicy.shouldShowSearchBar` deleted.
- **Now Playing (FR-010)**: `material_player_layout.dart`, `material_player_backdrop.dart`,
  `player_compact_bar.dart`, `lib/theme/player_artwork_theme.dart`, `_FadingAppBar` and
  `PlayerLyricsControlsCollapse` deleted. `AlbumDetailHeader` lost its Material
  app-bar path (`_buildAppBar`) and the parameters only it read (`expandedHeight`,
  `paletteSource`, `blurAndScrimBackground`, `backgroundColor`).
- **Material-only settings (earlier owner answer: remove every Material-only setting)**:
  `landscapeLyricsArtwork` and `compactPlayer` had a permanently disabled row on
  Mornye and no reader; their rows, setters, model fields, search entries and l10n keys
  are deleted. Stored values are ignored on load.
- **Other leftovers deleted**: `catalogTrackPlaybackUiProvider`, `catalogTrackKey`,
  `CurrentTrackIdentity` and `currentTrackIdentityProvider` (their only reader was
  that provider), `CollectionHeaderSkeletonRows`, `appSheetDialogMaxWidth` (inlined in
  the enum), `_EqualizerOverview.onEnabledChanged`, the `greetsInHeader` argument.
  l10n keys with no reader left in all 21 ARB files: `extensionsVersion`,
  `nowPlayingTitle`, `homeShortcuts`, `homeMixesForYou`, `equalizerManagePresets`,
  `libraryArtistsEmpty`, the 13 `homeGreeting*` keys, 3 `homeTagline*` keys,
  `landscapeLyricsArtwork(+Description)`, `compactPlayer(+Description)`.
  Kept: `backdropBlurEnabledProvider` (R7 expected it unread, but
  `mornyeGlassLevelProvider` still reads it); `tool_button_group.dart` (the `ToolButtonGroup` widget
  has no caller left, but the `ToolButton` data class in it is still used by Home
  search results, so T004 must move `ToolButton` before deleting the widget).
- **ExpressiveButton re-count**: 65 call sites in 36 files outside its own file
  (plan said 71 in 38 on the probe tree); T004 inlines them.
- **Integration tests**: `material_performance_test.dart` deleted; the seek scenario moved
  to `player_performance_test.dart` (Mornye only); the artwork and lyrics suites lost
  their `mornye:` parameter and Material arms; README updated.
- **Tests**: Material-arm tests and files deleted (`material_player_backdrop`,
  `player_compact_bar`, `player_artwork_theme`, `album_detail_header` (header blur of the
  deleted app-bar path), `home_continue_listening`, `home_greeting`,
  `shell_tab_style_switch`; Material cases inside `now_playing_lyrics_transition`,
  `library_toolbar`, `album_track_metadata`, `home_search_tabs`, `library_mix_ui`,
  `main_shell_keyboard`, `playback_automation_settings`, `player_presentation_settings`).
  Shared-behaviour tests kept and moved to the Mornye theme and controls
  (`track_metadata_lyrics/cover_search/replaygain`, the Now Playing menu and lyrics
  share tests, `local_library_artwork`, `shell_navigation_service`).
- **Checks**: `flutter gen-l10n` ok; `flutter analyze`: no issues (including
  `integration_test`); full `flutter test`: 4132 passed, 10 skipped, 0 failed (exit 0;
  the count is lower than T002's 4220 because the Material-arm tests above were
  deleted); `rg -n "isMornye" lib test integration_test` returns only three prose lines
  in `lib/theme/README.md` (25, 59, 79), which T005 rewrites.
- **Not verified**: rendering on a device. Test files still carry local `mornye`
  flags and `AppTheme` ternaries; T005 moves them to `MornyeTheme.build`.

### M001-T003 Review round 1 (NEEDS_FIXES) and fixes

- `now_playing_screen.dart`: `_compactPlayerTouched` and its comment, `compactTouch`
  in `_endLyricsInteraction`, the stale Material doc of `_lyricsAutoHideLayout`,
  the no-op `leading: null`, `actions: null` and `extendBodyBehindAppBar: false`
  deleted; `_showQueueSheet(context, colorScheme)` replaced by `_setMornyePage(2)`.
- `lib/widgets/player_queue_sheet.dart` deleted (`PlayerQueueDismissible` lives in
  another file and stays); `player_queue_artwork_test.dart` keeps only its Mornye arm.
- `connectedRowRadius`, `useMaterialNavigationRailForWidth` (with its test cases) and
  the stale "Material theme" doc comments in `adaptive_layout.dart` deleted.
- `main_shell.dart`: dead `_pageController` deleted (the `_currentIndex` reset stays).
  `LibraryDirectoryView.showHeader` (always true) deleted.
- Dead library view settings (all rg-confirmed to have no other reader: no backup or
  export path, no provider, no startup logic): the `historyViewMode` plumbing in
  `queue_tab.dart`, the unreachable songs-grid branch with its two-finger pinch
  handlers (`_libraryGridPinchStartExtent`, three handlers, `TwoFingerPinchListener`
  widget and its test, `_buildDownloadGridItem`, `_confirmCancelDownload`, other
  grid-only helpers the analyzer then reported; `_libraryGridExtent` stays, the
  album and playlist grids use it); settings `historyViewMode`, `historyFilterMode`
  and `defaultLibraryView` with their setters, normalizer, Appearance "Layout"
  section and `_HistoryViewSelector`, the Library "Default view" row and picker,
  the two settings-search entries, model fields and `.g.dart` lines (edited by hand;
  the file is tracked and the change is two removed fields per list). New test in
  `app_settings_model_test.dart`: old JSON that still carries these keys and
  `landscapeLyricsArtwork`/`compactPlayer` loads and does not write them back.
  Tests for the removed rows deleted (`Folders is a default Library view`, the
  Default view sheet case in `settings_choice_sheets_test.dart`).
- l10n keys with no reader left in all 21 ARBs (320 entries): `historyFilterAlbums`,
  `historyFilterSingles`, `appearanceHistoryView`, `sectionLayout`,
  `libraryDefaultView`, `libraryDefaultViewLastUsed`, `nowPlayingLibraryEmpty`,
  `nowPlayingShuffleLibraryFailed`.
- Reader scan beyond identifiers (`this.X` parameters and fields of every changed lib
  file with no reader): found `_SliderTile.indented` (Material-only), deleted with its
  four callers. `QueueItemSliverRow` lost an unused `key` parameter.
- Corrections: the earlier deviation note on `ToolButtonGroup` is fixed (the widget has
  no caller; only the `ToolButton` data class is used) and `tasks.md` T004 now says
  `ToolButton` must survive. `integration_test/player_performance_test.dart` no longer
  says "both styles".
- **Checks after the fixes**: `flutter gen-l10n` ok; `flutter analyze`: no issues;
  full `flutter test`: 4126 passed, 10 skipped, 0 failed (exit 0; log in the
  scratchpad `t003-test4.log`).

### Review round 2 (NEEDS_FIXES) and round 3 (NEEDS_FIXES, documentation only)

- Round 2 (independent): all round-1 fixes verified; one finding, the orphaned doc
  comment of the deleted `defaultLibraryView` in `lib/models/settings.dart`. Deleted.
  After it: `flutter analyze` no issues; `test/app_settings_model_test.dart` 8 passed;
  full run unchanged at 4126 passed, 10 skipped, 0 failed.
- Round 3 (independent): code clean, no stray comments, `work.py validate` PASS; one
  finding, this log lacked the round 2/3 entries. Added here. Third result was
  NEEDS_FIXES, so no further review is run without the owner's permission.

## M001-T004 Remove Material 3 Expressive, Material UI and Material Symbols

- **Map refresh (first action):** `python3 workflow/codegraph/code_tools.py update`
  succeeded: 35061 symbols, 1731 extracted sources, 0 partial, 0 unindexed, 0 rejected
  cross-language calls.
- **Probe re-run on the real tree (differences from the plan):** `materialExpressiveEnabled`
  was already a constant `false` (27 `lib` files, not 34); 67 `ExpressiveButton(` call
  sites in 35 files (plan: 71 in 38; T003 counted 65); `ExpressiveButtonGroup` 1 site;
  `ExpressiveNavigationRail` was still used by `main_shell.dart` on widths >= 1440
  (plan R6 listed only the bar); `ToolButtonGroup` had no caller left (T003 note).
- **Packages:** `material_3_expressive`, `material_ui` and `material_symbols_icons` removed
  from `pubspec.yaml`; `flutter pub get` also dropped the now-unused transitive
  `dynamic_color`, `equatable`, `material_new_shapes` and `motor` from `pubspec.lock`.
  `lib/app.dart` keeps only the `flutter_localizations` Material delegate.
- **Deleted:** `lib/theme/material_expressive.dart`, `lib/theme/material_symbols.dart`,
  `lib/widgets/{expressive_button,expressive_button_group,expressive_icon_button,
  expressive_navigation_bar,expressive_playback_transport,tool_button_group,app_checkbox,
  app_icon_button,app_refresh_indicator}.dart`; tests `app_motion_test`,
  `expressive_playback_transport_test`, `material_expressive_test`,
  `material_shared_controls_test`.
- **Replacements, nothing renamed:** each `ExpressiveButton` became the framework button
  its fallback arm returned (`TextButton`, `OutlinedButton`, `FilledButton`,
  `FilledButton.tonal`, `.icon` variants), by a throw-away script in the scratchpad that
  parsed `text:`/`outlined:`/`tonal:`/`icon:` literals (no non-literal flag existed);
  `ExpressiveButtonGroup` site (`library_tracks_folder_screen.dart`) is a `Row` with a
  `Flexible` Play button and 8 dp gaps; `ExpressiveNavigationRail` is inlined in
  `main_shell.dart` as its fallback `NavigationRail` (scrolling, all labels);
  `AppIconButton` (pure pass-through) and `AppRefreshIndicator` are replaced by
  `IconButton` / `RefreshIndicator` at all callers; `AudioOutputButton.backgroundColor`
  and `width` deleted; `ToolButtonGroup` deleted, the `ToolButton` data class is kept as
  a three-field class at the end of `home_tab_search_results.dart` (its only user).
- **M3E arms deleted, fallback kept:** `AppSwitch`, `AppSlider` (also loses
  `trackThickness`/`thumbLength`/`centered`), `AppChoiceChip`, `AppProgressIndicator`
  (loses `wavy`), `AppMenuButton` (loses `alignStart` and the M3E anchor),
  `AppTextField` (loses `outlined`), `AppShapeClip`/`AppShapeBadge` (lose `kind`;
  `AppShapes` and `fallbackRadius` -> `radius`), `SwipeDeleteDismissible` (now
  stateless), `MetadataTextField`, `StaggeredListItem`, `SettingsGroup`,
  `SettingsSegmentedChoice` (loses `semanticLabel`, which only the M3E group read),
  `MiniPlayer` play button, listening-statistics activity bars (`_ActivityBar` and the
  `_connectedRow` pass-through deleted), `PlayerPlaybackControls`
  (`PlayerPlaybackModeToolbar`, `_PlaybackModeSegment` and `showPlaybackModes` deleted:
  no caller). `AppMotion`/`SpringCurve` and the M3E springs are deleted; every call site
  uses its own `AppMotionSpec` (the fallback) directly, and sheet/menu entrance styles
  (`surfaceEntrance`, always null on Mornye) are gone. `app_motion.dart` keeps only
  `AppMotionSpec`. `AppIcon` lost `fill` and `symbolIconWidget` (it only matters for
  Material Symbols).
- **Setup flow (FR-015):** `setup_screen.dart` keeps the standard
  `FloatingActionButton.extended` next/finish buttons (`_buildExpressiveFab` deleted);
  the step progress ring is a plain `AppProgressIndicator.circular` (the zero-duration
  tween around it was an identity); the language list rows are `AppSheetOption`s with
  the accent icon, bold accent title and trailing check, exactly like
  `_showLanguagePicker` in Appearance settings. New test in
  `test/setup_screen_recovery_test.dart`: `setup language list marks the chosen
  language like Settings` (selected row, accent bold title, check, no
  `Icons.check_circle`, other row plain).
- **Tests:** shared-behaviour tests kept and re-pointed at the framework widgets
  (collection reorder `TextButton`, cache page `TextButton`, lyrics share
  `FilledButton`, lyrics empty state `FilledButton`, hires card `CupertinoButton`,
  audio output button without the removed parameters, equalizer/overflow/design-system
  tests without `M3E*` finders). Material-only cases deleted: `legacy icons keep their
  filled or outlined meaning`, `symbolIconWidget` and `AppIconButton` tests in
  `app_icon_test.dart`, the M3E menu-skin test in `material_theme_roles_test.dart`,
  M3E progress painter and M3E chip/button-group expectations.
- **Leftover scan (T002/T003 lessons):** l10n keys of `app_en.arb` all still have a
  reader (2138 keys, script over `lib`, `test`, `integration_test`); constructor
  parameters of changed files with no reader scanned by script (none new); doc comments
  naming the removed Material/expressive behaviour rewritten in the touched files. Left
  for T005: `AppTheme` (and its "Expressive brand" font comments), `lib/theme/README.md`
  rewrite beyond the removed rows, the `isMornye` prose in that README.
- **Deviations / not done:** `semanticLabel` of `SettingsSegmentedChoice` was only
  passed to the M3E button group, so Mornye never exposed it; removed rather than
  adding new semantics (no Mornye behaviour change). `AppSheetDialog`,
  `duplicateReviewGroupsLoaderProvider` show up in a single-file reader scan but
  predate this task and were left. Nothing was checked on a device.
- **Checks:** `flutter analyze` (whole repo incl. `integration_test`): no issues. Full
  `flutter test`: 4096 passed, 10 skipped, 0 failed, exit 0 (T003 left 4126; the 30
  fewer are the deleted M3E/symbol/motion/expressive-transport/Material-control tests,
  plus one new setup test). `rg -n "material_3_expressive|material_ui|material_symbols_icons|M3E|materialExpressive" lib test pubspec.yaml`
  and `rg -n "class (Expressive|Material)[A-Z]" lib` return nothing. `work.py validate` PASS.
