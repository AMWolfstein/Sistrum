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
