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
