# TOR006 Phase 2 - Decisions

- Task: TOR006 (record `.specify/quick-tasks/TOR006.md`)
- Scope: settings and centralized glass policy (audit checklist items 2, 3, 10, 11).
- Status: recommendations. Items marked OWNER were fixed by the owner and are not revisited.
  Items marked RECOMMENDATION were chosen during implementation and should be confirmed.

## Owner decisions (fixed)

1. OWNER. Migration of the retired `mornye_glass_clarity` value: 0 becomes None (`flat`);
   below 0.5 becomes Frosted (`frosted`); 0.5 or above becomes Liquid (`liquid`).
   Implemented in `mornyeGlassLevelFromLegacyClarity` (`lib/models/theme_settings.dart`).
2. OWNER. The stored value is `mornye_glass_mode`, holding the `MornyeGlassLevel` name.
   The one-time marker is `mornye_glass_mode_migrated`, written last, following the
   `retired_theme_prefs_removed` pattern. The clarity key is removed after migration.
3. OWNER. The mode is not added to backup or restore. It lives in `ThemeSettings`
   (SharedPreferences), which backup never exported. The `app_settings` JSON, which backup
   does export, no longer carries `forceBackdropBlur`.
4. OWNER. Exactly three user-selectable modes, reusing the existing `MornyeGlassLevel` enum
   (`flat`, `frosted`, `liquid`). The enum now lives in `lib/models/theme_settings.dart` and
   is re-exported from `runtime_profile_provider.dart`, so there is one definition.
5. OWNER. Persisted selection, effective renderer and surface presets are separate:
   `mornyeGlassModeProvider` (selected), `mornyeGlassLevelProvider` (effective, device
   fallback only), `MornyeGlassPreset.of` (fixed values). Device fallback never changes the
   persisted choice.
6. OWNER. The glass-outlines setting (`mornye_glass_outlines`) is unchanged.
7. OWNER. `forceBackdropBlur` is retired: removed from `AppSettings`, the settings provider,
   the Appearance page row, the ARB keys (`appearanceForceBlur`, `appearanceForceBlurSubtitle`)
   and the search catalog entry. None is never overridden.

## Recommendations taken during implementation

8. RECOMMENDATION. Force-blur folds into the migration. A stored `forceBackdropBlur: true`
   turns a migrated Frosted result into Liquid, because the old switch forced the full
   material. It never turns None into glass. Alternative: ignore the switch entirely. The
   audit proposed leaving the mode unchanged; that would drop the full material for users who
   forced it. Confirm before release.
9. RECOMMENDATION. Fresh installs (no clarity, no mode) start in Liquid Glass, the old default
   clarity of 0.75.
10. RECOMMENDATION. Device fallback (`mornyeGlassLevelProvider`), matching the old behaviour:
    low-end devices render flat for any mode except that None is already flat; Liquid falls
    back to Frosted when the GPU has no Liquid Glass lens and the device is not in the
    high-blur tier. Old: Android standard = frosted, Android high = liquid, iOS = liquid.
11. RECOMMENDATION. High contrast still turns decorative glass off in every mode
    (`mornyeGlassContrastBlockedProvider` is now just the contrast request). The removed
    force override no longer lifts it. The Appearance page shows one note in that case.
12. RECOMMENDATION. One blur sigma per mode in `lib/theme/mornye_glass_presets.dart`: Liquid 2,
    Frosted 3, None 0 (plan 10.3). The plan's per-surface split (floating controls 0-2, menus
    and sheets 2-3) is not implemented yet, because `MornyeGlass.navigation` does not know
    its surface category. Defer the split to the Phase 3 surface migration.
13. RECOMMENDATION. The other preset values reproduce the old outputs at the default clarity:
    tint 0.0625 (was `(1 - 0.75)^2`), lens clear mix 0.6875 (was `(0.75 - 0.2) / 0.8`),
    lens dispersion 0.99 (was `1.32 * 0.75`), dark menu tint 0.65 (was `0.80 - 0.20 * 0.75`).
    The liquid blur drops from 6 to 2, following the plan.
14. RECOMMENDATION. The Appearance page uses a three-option `MornyeSegmentedControl`, in the
    same style as the theme selector. Order: Liquid Glass, Frosted Glass, None. The live
    preview panel from the old slider is removed. Confirm whether a preview should stay.
15. RECOMMENDATION. The old "blur is off on this device" hint is removed. Only the
    high-contrast note remains, because the force switch it pointed to no longer exists.
16. RECOMMENDATION. The selection pill (`mornye_selection_pill.dart`) keeps its full-clarity
    dispersion, 1.32, until Phase 3 replaces the pill. This is an explicit constant, not a
    clarity read.
17. RECOMMENDATION. The clarity-only preview API (`setMornyeGlassClarity(persist: false)`) is
    removed. The mode setter always persists. The old early-preview test is dropped, because it
    covered the removed drag preview; the early-edit ordering test covers the save path.
18. RECOMMENDATION. Localization: nine obsolete keys are removed and six keys are added in all
    21 ARB files: `appearanceGlassMode`, `appearanceGlassModeSubtitle`, `appearanceGlassLiquid`,
    `appearanceGlassFrosted`, `appearanceGlassNone`, `appearanceGlassHighContrast`. Non-English
    strings are my translations and need native review. The es, pt and zh files had no glass
    keys before, so the new keys were appended at the end of those files.
19. RECOMMENDATION. `lib/models/settings.g.dart` is generated, but its two `forceBackdropBlur`
    lines were removed by hand, because `build_runner` was not run. Regenerate it, or confirm
    the hand edit, before the next build.
20. RECOMMENDATION. `forceBackdropBlur` is still present in old `app_settings` JSON until the
    next settings save. It is read once by the migration and then dropped from the JSON.

## Process

21. RECOMMENDATION. Policy (`.specify/project-policy.md`) routes migrations and removals
    through Spec Kit. The owner directed this work on the TOR path, and the policy says owner
    instructions win. Recorded here so the owner can decide whether a full plan is needed
    before merge.

## Phase 3 - Native component adoption (audit checklist items 4, 6, 7, 8)

Scope: the core chrome only (wrappers, bottom bar, tab row, segmented selection, press,
rim/clip/shadow). Settings pages, menus, sheets, dialogs, headers, artwork and desktop
inspector are later phases (see the last section). Decisions 22 onward.

### Wrapper (item 4)

22. `MornyeGlass` keeps its constructor and fields, so the ~60 call sites are unchanged, except
    `highlightBorder` (decision 25). The mode decides the renderer:
    - Liquid: `MornyeLiquidBackdrop` (a `LiquidGlassLens`) in a `LiquidGlassBatch`, default
      `separatePass`. `batchBlurMethod` is not changed: no measurement was possible here (plan 10.1).
    - Frosted: `LiquidGlassLite` with the preset sigma (3). It replaces `BackdropFilter.grouped`.
    - None: a plain fill. No batch, no lens, no lite, no backdrop read, no warm-up (warm-up was already
      gated on Liquid).
    - Liquid without a lens (no shader support on the device, or a partial group) falls back to
      `LiquidGlassLite` with the Liquid preset sigma (2). This matches the old `BackdropFilter`
      fallback, which used the same sigma.
23. Partial group corners in Frosted (first or last only): `LiquidGlassLite` is drawn with a square
    shape and the existing outer clip rounds only the open edges. Reason: `LiquidGlassShape` has a
    single corner radius, so a partial-radius lite would round the wrong corners. Liquid already
    required a full group before this change; that is unchanged.
24. `MornyeLiquidBackdrop` stays a thin lens wrapper. Its interface-scale viewport workaround
    (`AdaptiveUiScale`) is kept: the audit could not verify on Impeller or Skia that the package now
    measures the window itself (audit 6). Requires a device check.

### Rim, blur tables, shadows and clips (item 8)

25. `_MornyeGlassRim` (custom rim painter) is removed after the native rims were confirmed in the
    installed source: `LiquidGlassLens` draws `specular` and `glow` (already used), and
    `LiquidGlassLite` paints its own rim from the same `LiquidGlassLight` values. The shared helpers
    `mornyeRimSpecular` and `mornyeRimGlow` (in `mornye_liquid_backdrop.dart`) give the lens and the
    lite surface identical rim values. Lite's default specular has intensity 1.0, which is much
    stronger than the Mornye rim, so the defaults are not used.
    - None draws no rim any more. The old 0.5 px contact stroke is gone. Accepted: None is a plain
      surface (plan section 4).
    - `highlightBorder` is removed. It only drove the rim painter, and the rim now follows the mode.
      Its two callers (`desktop_player_bar.dart`, `mornye_context_menu.dart`) lose that one line.
26. The `_blurs` table and the `BackdropFilter.grouped` path are removed. Blur comes from the preset
    (`MornyeGlassPreset`), not a clarity or per-surface choice.
27. Kept, with reasons (not confirmed removable on a device):
    - Outer `BoxShadow` (not glass; the lens `style.shadow` is not used by Sistrum).
    - `ClipRRect` / `ClipPath` around the surface. The fill and child ink need containment in every
      mode, and the native lens clip was not verified on a device.
    - `MornyeSquircleBorder`: a non-glass shape utility used by fills, clips and tests. Plan 9 keeps it.
    - `_GlassBackdropScope`: the nesting guard against sampling an already-filtered parent (plan 10.5).

### Selection (item 7)

28. `lib/widgets/mornye_selection_pill.dart` is deleted, with `MornyeSelectionForeground`,
    `_SelectionShaderMask`, `_SelectionRenderShaderMask`, `_SelectionForegroundScope`, the spring and
    pointer code, and the held lens. `assets/shaders/mornye_selection_mask.frag` is deleted, and the
    `shaders:` block in `pubspec.yaml` is removed. It was the only asset in `assets/shaders/`.
29. One private selection row, `_SelectionRow` in `mornye_chrome.dart`, serves
    `MornyeSegmentedControl` and `MornyeTabBar`. It is not a new public widget.
    - Glass (Liquid, shader supported, motion allowed, not desktop, not high contrast): the package
      `LiquidGlassMotionPill` is the thumb. It sits above the items so its lens refracts them. It is
      wrapped in `LiquidGlassBatch.exclude` so it does not join the capsule's batch, the same rule the
      old held lens followed.
    - Frosted and None: a plain tinted `StadiumBorder` under the items. Frosted gets no lens thumb,
      because it must not run full optical refraction (plan section 4).
30. Thumb geometry: `restSize` is the slot, `activeSize` is the slot times 1.28 by 1.24. Those are the
    old held growth factors (0.28 and 0.24), kept as the target size only. No old animation is
    recreated.
31. Motion: the thumb slides with a `TweenAnimationBuilder` over the visual slot index, using
    `AppMotion.surface(context)`. Reduced motion gives an instant move. The old snappy spring and the
    finger-velocity handoff are removed. The native squash and morph are accepted instead.
32. Press: `LiquidGlassMotionPill.active` is true while any pointer is down on the row. It resets on
    up and cancel. This replaces the old held-lens press progress.
33. Drag preview and drag commit are removed. The package pill has no drag input. Taps select
    (`InkWell`), and a cancelled press selects nothing. Accepted native difference; cancellation is
    still tested.
34. The masked foreground recolour is removed. `itemBuilder` now receives the real `selected` flag,
    so the colour changes at selection instead of sliding under a mask. Accepted native difference.
35. `MornyeNavigationIcon` no longer wraps its icon in `MornyeSelectionForeground`.
36. Desktop segmented controls keep the flat thumb (the old code also disabled the held lens on
    desktop). Shader-filter support is read through `MornyeGlass.shaderFilterSupported`, which
    honours the existing test override.

### Tab bar, press and bottom bar (item 6)

37. `LiquidGlassTabBar` is not adopted. The plan asks for it only if it fits the existing layout.
    Verified in the installed source (`bottom_nav_bar/liquid_glass_tab_bar.dart`): it has no fold
    or content-opacity input. Sistrum's bar folds its capsule into a leading circle, moves two side
    capsules and drives the layout from one spring controller. Adopting the package bar would mean
    rebuilding that layout around it. `MornyeTabBar` keeps its API. Its capsule is the native lens
    (via `MornyeGlass`) and its selection is the native motion pill.
38. `LiquidGlassMorph` (plain) was evaluated for the fold and not adopted. It animates its own size
    with its own motion, which would drift from the bottom bar's layout, hit testing and moving icons,
    all driven by the same spring. The fold stays a size interpolation of the existing capsule.
39. `LiquidGlassScaffold` is not adopted. It owns the view pipeline and its own bottom-navigation
    slot. Sistrum's bar needs a custom fold, search capsule and side capsules, so adopting the
    scaffold would mean a layout rewrite (plan section 8 excludes that).
40. `BackdropGroup` is removed from the bottom bar. The `LiquidGlassBatch` around the bar already
    shares one backdrop, and a lite surface joins that batch on Impeller.
41. `MornyeGlassPress` is kept (remaining custom code). `LiquidGlassTouch` is a plain configuration
    class (`liquid_glass_touch.dart:25`). It is passed to a lens's `touch:` parameter and presses only
    that lens. Sites such as chips, avatars, header buttons and the mini player press arbitrary
    children, which the package cannot do. The press is paint-only and layout-neutral, with no
    Listener in the gesture arena. Phase 11 may move lens-only press sites to `touch:`.
42. Mini player: no code change in this phase. Its glass is `MornyeGlass.navigation`, which now
    renders per mode, and its press is `MornyeGlassPress`. The `ClipRRect` at `mini_player.dart:260`
    is untouched and unverified (audit 2.2).

### Tests and integration tests (item 7)

43. Shader-based and drag-based assertions are replaced, not weakened. New native checks: tap selects
    and the glass thumb moves to the tapped slot (both directions), a cancelled press selects nothing,
    the thumb lifts while a finger is down, the selected label is primary, Frosted and None draw no
    native lens thumb, the flat thumb moves at once under reduced motion, the bottom bar's frosted
    surfaces share one batch key at rest, and the moving capsule gets its own key while folding.
    Dropped with no native equivalent: drag preview and commit, velocity carry, mask culling, and the
    selection-colour pixel test.
44. Frost assertions now look for `LiquidGlassLite` (what Frosted and host-side Liquid draw) instead
    of `BackdropFilter`. The blur-per-mode test reads the Lite `blur` value (3 Frosted, 2 Liquid, none
    for None).
45. Integration tests (`integration_test/mornye_liquid_glass_test.dart`,
    `mornye_glass_visual_test.dart`, `mornye_rendering_performance_test.dart`) compile, but were not
    run: there is no device or emulator. Their held-lens pixel and progress checks are replaced with
    native thumb checks.
46. Phase 2 lint fixed: `test/appearance_settings_test.dart` used `'$saved'` where `saved` is already
    a `String`. The analyzer flagged it, and it was the single baseline info in the test folder.

### Out of scope for this phase (remaining custom glass)

47. Not in the core chrome, so not changed here: `lib/widgets/desktop/desktop_inspector.dart`
    (plain `BackdropFilter` with sigma 60), the context menu's custom morph `_LiquidMenuGlass`,
    artist and album headers and their blur, `ImageFiltered` and `ImageFilter.compose` artwork
    effects, the scroll-edge fade, and the settings, menu, dialog and sheet surfaces. They follow in
    later phases (plan 12-13).

### Process

48. Nothing committed, nothing pushed (owner instruction). The workflow's commit-check and task
    complete steps were not run for this reason, and TOR006 stays IN_PROGRESS.
49. Independent read-only review (task-reviewer) was not run at the end of Phase 5 (later rounds: decisions 89-106). No reviewer could be dispatched from
    this session, so it is an open blocker before task completion.
50. A whole-folder `dart format` in this session reformatted 23 Dart files outside this task. Their
    tokens matched HEAD exactly (only layout changed), so they were restored with `git checkout`.
    Nothing unrelated remains modified.

## Phase 4 - Overlays, headers, backgrounds and recursive inventory (decisions 51-66)

Scope: audit items 5, 9, 12 and plan sections 11-12. Full table in `TOR006/inventory.md`.
Most overlays already reach glass through `MornyeGlass` (Phase 2 and 3), so Phase 4 changes
few lines and documents the rest. Each retained renderer has a reason below.

### Overlays and sheets (items 1, 2)

51. Sheets, dialogs and menus stay on the central wrapper (`MornyeGlassPanel.overlay`). Presenter
    APIs are kept (`showAppBottomSheet` family, `AppAlertDialog`/`showAppDialog`,
    `showMornyeContextMenu`), so call sites need no change. `LiquidGlassSheet`,
    `LiquidGlassAlertDialog` and `showLiquidGlassDialog` were read in the installed source and
    not adopted, for three reasons:
    - Liquid: each wraps a bare `LiquidGlassLens` and skips `MornyeLiquidBackdrop`'s
      `LegacyThemeBrightness` and interface-scale viewport handling (decision 24).
    - Geometry: each brings its own margin (10 px), grabber, radius (28) or width (340) and
      padding, which would change Sistrum's sheet and dialog layouts.
    - Frosted: only `style.liteGlass` gives a lite path, and it sits inside the lens. Using it
      would split the mode policy in two. `visibility: false` covers None.
    Recorded for the owner: the package path could be revisited if its lens took the same
    viewport and brightness handling.
52. The non-immersive album header keeps `SettledBlur(sigma: 32)` over a static cover in every
    mode (`album_detail_header.dart:476`). It is an artwork effect, not glass, and it is cached
    once settled (plan 11, static artwork). Owner decision: it runs in None. Change it if None
    must also be free of artwork blur.
53. The artist header collapse blur (`mornye_artist_header.dart:494-502`) stays gated to Liquid
    and reduced motion. The plan's video rule holds by construction: the artist header hosts no
    video (`MotionHeaderBanner` is used only by album and player artwork). The app publishes no
    video-active signal, so none was added. Add one only when an artist video exists.
54. The artwork contrast readback (`mornye_artwork_contrast.dart:195,296`) stays. It measures the
    artwork under text targets to choose a dim (revision-gated, not per frame), and it is not
    glass. It is pre-existing, not introduced here. Plan 11 forbids new `toImage` in live
    rendering, so a device measurement is required before it is accepted.
55. The motion glow (`mornye_motion_glow.dart`) stays. It is Liquid-only (`mornyeLiquidGlassProvider`,
    `:66`) and filters a downscaled texture. In Liquid it does filter video frames continuously,
    which plan 11 asks to avoid. Disabling media features is forbidden, so the cost stays until
    a device measurement shows it matters.

### Renderers changed

56. Desktop inspector (`desktop_inspector.dart`): the fixed sigma-60 `BackdropFilter` now uses
    `MornyeGlassPreset.of(level).blurSigma` (Liquid 2, Frosted 3). None draws no filter, as before.
    It stays a plain `BackdropFilter`, not a lens: the inspector is a flat panel over content and
    needs no refraction. The 0.74 tint is kept. Legibility at sigma 2-3 is unverified on a device.
57. Bottom bar (`mornye_bottom_bar.dart`): both `LiquidGlassBatch` sites take
    `enabled: blurEnabled` (the folding capsule and the main batch). Liquid and Frosted stay
    batched, because a Frosted lite surface joins the batch on Impeller (decision 40). A disabled
    batch passes a null backdrop id, so None registers no backdrop member. Verified by
    `mornye_bottom_bar_test` (60 pass), not by a dedicated None assertion.

### Verified without change

58. Context menu (`mornye_context_menu.dart`): the surface already goes through
    `MornyeGlassPanel.overlay`, with the preset-derived tint, so the three modes apply. The
    `_LiquidMenuGlass` morph is a layout animation, not a renderer, and it stays. `LiquidGlassMorph`
    was not adopted (decision 38). No code change.
59. Shader warm-up (`mornye_glass_warmup.dart`, `mornye_glass_preparation.dart`): gated on the
    effective level being Liquid and not contrast-blocked (`MornyeGlassPreparation` watches
    the level, so a mode change re-runs the gate). It is single-flight through `_pending`. There
    is one app call site (`lib/app.dart:233`). `LiquidGlassShaders.ensureLoaded` is called by
    integration tests only, so no duplicate exists to remove. No code change.
60. Floating action buttons (`app_action_button.dart:80`) never draw glass (`blurEnabled: false`
    in every mode). That predates this phase and is kept. Owner decision: if floating buttons
    should be glass in Liquid, the flag must change.
61. Scroll-edge fade (`scroll_edge_fade.dart`) is a `ShaderMask` gradient over content, not glass.
    `LiquidGlassScrollEdge` is a `BackdropFilter` blur with its own sigma, so it is not a drop-in
    (audit 1.2). Not replaced.
62. Sliders, switches and buttons: no `LiquidGlassSlider`, `LiquidGlassSwitch` or
    `LiquidGlassButton` in `lib/`. Parity with the Material controls is not established, so none
    were adopted. The landscape player has no glass primitive (grep), so there was nothing to
    migrate there.

### Test strategy and process

63. Live propagation: a presented sheet follows a live mode change (None, Frosted, Liquid) without
    reopening. Covered by `test/mornye_overlay_mode_test.dart`, the same way menus and dialogs read
    the provider.
64. Host lens semantics: without Impeller or a `LiquidGlassView`, `LiquidGlassLens` draws its own
    lite fallback. The Liquid assertions therefore check the lens widget and not the absence of
    lite. Frosted must have no lens; None must have no lens, no lite and no `BackdropFilter`.
65. An album video-blur change was tried and reverted. The motion album header is immersive, and
    its build path has no `SettledBlur`, so the change would have been dead code. The reverted
    files match HEAD byte for byte.
66. Independent review (task-reviewer) was not run in this session: no reviewer could be
    dispatched. Later rounds ran (round 1 NEEDS_FIXES, round 2 PASS; decisions 89-106). TOR006 stays IN_PROGRESS, and nothing is committed or pushed.

## Phase 5 - Performance hub (decisions 67-82)

Scope: a Settings page to measure rendering on the phone, with no adb, no network and no
telemetry. Checklist items are not changed; this adds a diagnostic tool. Recommendations are
the implementation's choices and need owner confirmation. None is an owner decision yet.

### Placement and runtime cost

67. RECOMMENDATION. Settings > Performance hub is a destination in the diagnostics group,
    next to Logs and About, with one search entry (the hub) and one for the comparison.
    Labels reuse existing keys where the meaning is the same (Cancel, Duration, Device,
    Interface size, the three mode names).
68. RECOMMENDATION. Zero cost when idle. The timings callback is registered only between
    `start` and `stop` (`FrameTimingRecorder`). The live values refresh by a one-second
    `Timer` that also exists only while recording. The page's display rows read the engine's
    reported refresh rate on build and register nothing. The notifier loads the saved runs
    once from SharedPreferences.
69. RECOMMENDATION. Recording buffer: 3600 frames in three `Uint32List` rings (build, raster,
    vsync interval), about 43 KB, overwritten oldest first. The frame count covers the whole
    recording, but statistics cover only the kept frames. The text export shows both
    ("Frames: N (statistics: K)"), so a long recording is never mistaken for a complete one.

### Metrics

70. RECOMMENDATION. A frame is over budget when its UI (build) or raster time exceeds the
    budget. This is the rule of `integration_test/performance_probe.dart`
    (`summarizeFrameBudget`), so app and probe numbers compare directly. The total span is
    not used, because build and raster are pipelined.
71. RECOMMENDATION. Refresh rate source: the engine's reported `display.refreshRate` on the
    implicit view. On Android the engine's vsync waiter starts from a default of 60 Hz
    (`shell/platform/android/vsync_waiter_android.cc`), so a 90 or 120 Hz phone may report
    60 until the real rate is known. The median vsync interval of each recording is therefore
    recorded beside it ("reported" and "measured" rows). The budget uses the reported rate
    when it is valid, otherwise the measured one. OWNER TO CHECK on the phone: if the two
    disagree, the jank figure follows the reported rate.
72. RECOMMENDATION. Percentiles are nearest-rank on the kept frames, reported in milliseconds
    (P50, P95, P99, max). Empty phases show "Not available", never zero.
73. RECOMMENDATION. Impeller is "Not available". The Android build decides Impeller natively
    (`ImpellerCompatibility.kt`, passed as a shell argument) and Dart has no public API that
    reports it. A method channel would add native code outside this phase. Revisit if the
    owner wants it on the page.
74. RECOMMENDATION. GPU passes and backdrop reads are "Not available". The app has no hook
    that measures them, and inventing a figure is not allowed.
75. RECOMMENDATION. Each session records the selected mode (`mornyeGlassModeProvider`) and the
    effective renderer (`mornyeGlassLevelProvider`, after device fallback), read when the
    recording starts, so a fallback is visible in the results.

### Comparison and persistence

76. RECOMMENDATION. Guided comparison: Liquid, then Frosted, then None, 10 seconds each
    (`performanceCompareWindowProvider`, overridable in tests). Each mode is set through the
    theme setter, which persists, and the original selection is restored in a `finally`, also
    after a cancel. A cancelled comparison is not saved. The owner scrolls during the steps;
    the badge shows the mode and seconds left.
77. RECOMMENDATION. Saved results: SharedPreferences key `performance_hub_runs`, a JSON list of
    at most five runs (one session or one three-mode comparison). Not part of backup, because
    backup exports only the app settings JSON. Unreadable stored data is logged and ignored.
78. RECOMMENDATION. Device model and Android release come from `device_info_plus`, read only
    on Android. Elsewhere they show "Not available". The app version is
    `AppInfo.displayVersion` with the build number. The interface scale is the saved setting,
    read at start.
79. RECOMMENDATION. Export: "Copy as text" and "Copy as JSON" copy the latest run. The text
    uses fixed English labels and the JSON keys are fixed, so a shared report reads the same on
    every device. The page itself is localized. The JSON lists `not_measured` (impeller,
    gpu_passes, backdrop_reads).

### UI and localization

80. RECOMMENDATION. Recording badge: a glass panel above the navigator (in `buildAppContent`),
    so it shows on Home, Library, Player and open sheets. It is a zero-size positioned widget
    when idle, so it takes no touches. Its stop button stops the recording, or cancels a
    comparison. It is not tappable to return to the hub; the owner returns through Settings.
81. RECOMMENDATION. The hub page uses the existing settings primitives (`SettingsInfoCard`,
    `settingsSectionSlivers`, `SettingsItem`, `AppActionButton`), which already follow the
    three modes. The badge is the only new glass surface: `MornyeGlassPanel.overlay`, so it
    follows the selected mode and the device fallback like every other overlay.
82. RECOMMENDATION. 38 new keys (at Phase 5; 42 now, see 103) are in all 21 ARB files, with descriptions, and
    `flutter gen-l10n` was run. The non-English strings are my translations and need native
    review, as Phase 2's were. Placeholders are declared in the English metadata.

## Phase 5 process (decision 83)

83. RECOMMENDATION. The full `flutter test` run took hours in this container and had one silent
    tester (`desktop_library_table_rows_test.dart`, 10-minute per-test timeouts). The tester was
    stopped by hand, and the same file was re-run alone to confirm the cause. The file's failures are
    environmental and listed in TOR006 Phase 4. Nothing was skipped, weakened or re-labelled as passing.
    The `integration_test/mornye_rendering_performance_test.dart` edit is the Phase 2/3 compile
    adaptation and is kept (it is not a Phase 5 change).

## Phase 6 - Dead code, documentation and package-first rule (decisions 84-88)

84. RECOMMENDATION. Removed `MornyeGlassWarmup.isReady` and its backing `_ready` flag
    (`lib/controllers/mornye_glass_warmup.dart`). No reader exists in `lib`, `test` or
    `integration_test`. The getter predates TOR006, but it is glass code with no use. Warm-up
    behaviour is unchanged: `prepare` and the rasterization are the same.
85. RECOMMENDATION. Kept `MornyeFilterChip` (`lib/widgets/mornye_chrome.dart`). It is unused by the app
    and was unused at base too. Only `test/mornye_search_glass_test.dart` builds it. Deleting it also
    means changing that test, which is an owner choice, so it is listed, not removed.
86. RECOMMENDATION. Kept the retired clarity and force-blur symbols (`kLegacyMornyeGlassClarityKey`,
    `mornyeGlassLevelFromLegacyClarity`, the `forceBackdropBlur` read in `theme_provider.dart`). The
    one-time migration (decisions 1-8) and its tests need them.
87. RECOMMENDATION. The package-first rule is a project section in the root `AGENTS.md`, after the
    workflow bootstrap block. It is specific to Sistrum, not a shared workflow instruction, so
    `workflow/assets/` is not changed. `workflow/README.md` mirrors only shared instructions.
88. RECOMMENDATION. `integration_test/README.md`: the `MORNYE_GLASS_FOCUS` row and the two suite rows
    now describe the current behaviour (Frosted and Liquid; each glass mode; native thumb press).
    Not changed, and already stale at base: no Dart file reads `GLASS_PREVIEW` or any `DOWNLOAD_*`
    define, and the `DOWNLOAD_*` rows name `mornye_download_picker_test.dart`, which does not exist.

## Review fixes (independent review NEEDS_FIXES, decisions 89-97)

A read-only review returned NEEDS_FIXES on the Phase 5 hub. Each fix below has a test that
fails before the change and passes after, except where stated. No existing assertion was
weakened: where a test encoded behaviour the review changed, the change is listed here.

### Recording badge placement (review item 1)

89. RECOMMENDATION. The badge sits above the navigator, so it has no Overlay ancestor. A
    tooltip there throws "No Overlay widget found ... RawTooltip" (reproduced by
    `test/performance_recording_badge_test.dart` before the fix). The stop control has no
    tooltip; a `Semantics` label gives it its name. The badge is now an opaque
    `surfaceContainerHigh` pill with a 20 dp radius, not `MornyeGlassPanel`: a glass
    backdrop read each frame would distort the timings it measures.
90. RECOMMENDATION. Cancel is reachable during a comparison. The badge shows a Cancel
    button while a comparison runs, and the hub page shows one under Compare modes. The
    hub page still hides Stop during a comparison, which is intended: Stop only ends a
    single recording.

### Saved selection and compare lifecycle (review item 2)

91. RECOMMENDATION. A comparison never writes the saved glass mode. It renders each mode
    through `mornyeGlassCompareModeProvider` (`lib/providers/theme_provider.dart`), read by
    `mornyeGlassActiveModeProvider`, and `mornyeGlassLevelProvider` renders the active
    mode. Restoring is clearing that override, so there is no restore-by-write to skip.
    `ThemeNotifier.setMornyeGlassLevel` ignores writes while the override is set, so a
    Liquid/Frosted/None tap in Appearance during a comparison is not saved. Tests:
    `performance_hub_page_test.dart` (calls list stays empty, the saved mode is unchanged)
    and `theme_provider_persistence_test.dart` (a write is ignored, then saved after it
    ends). Before the change, the old test expected four setter writes.
92. RECOMMENDATION. A comparison or recording starts only through `_claim()`, which checks
    and sets `performanceRecordingActiveProvider` in one synchronous step, before any await.
    A second start returns at once. Test: "a second comparison cannot start while one runs".
93. RECOMMENDATION. The app-wide lifecycle observer is added when a recording or comparison
    is claimed and removed when it ends. On `paused` or `hidden` it cancels a comparison.
    A single recording keeps running, as before, because it does not change the render
    mode. Test: "backgrounding the app cancels a comparison and saves nothing".
94. RECOMMENDATION. A window below `performanceMinFramesProvider` (60 frames, about one
    second at 60 Hz) is discarded and logged, not saved. A comparison with any short or
    cancelled window saves nothing. The floor is a test seam (the page tests set it to 0)
    and is not calibrated on a device. Tests: "a window below the frame floor is discarded".
95. RECOMMENDATION. The exception path is explicit. `_endComparison` runs in a finally,
    clears the override in an inner finally, and emits the final state in the innermost
    finally, so a failure cannot skip the emit or leave the hub claimed.
96. RECOMMENDATION. Each comparison step now starts measuring at once, without waiting for a
    frame to render the new mode. The first frames of each mode therefore include the
    shader rebuild. The hub text (`perfHubCompareWarmup`) tells the owner to discard a
    warm-up run or scroll before the first window.

### Startup load, fallback, badge laziness, jank note, inspector (review items 3-6)

97. RECOMMENDATION. `_loadMornyeGlassLevel` reads with `prefs.get` and an `is String` check,
    so an int, bool or double at the mode key falls back to the retired-clarity path
    instead of throwing before `runApp`. Test: "a glass mode stored with another type loads
    as if it were absent". NOT CHANGED, flagged: `loadBootstrapThemeSettings` still calls
    `getString` for the theme mode and the accent, and `getDouble`/`getInt`/`getBool` for
    the lyrics values, so a wrong-typed value there can still throw at startup. This review
    item named the glass mode only.
98. RECOMMENDATION, PARTLY FROM THE REVIEW. Review item 4 asked that Liquid fall back to
    Frosted only when blur works, and to None when it does not. Implemented in part:
    the defect is that Liquid stayed Liquid whenever the lens was unsupported and blur
    worked. Now an unsupported lens is Frosted, whatever blur does. The existing assertion
    for that case (blur on, lens off) expected Liquid and was changed to Frosted, as the
    review directed; it failed before with `Actual: liquid`. The review's "blur unsupported
    -> flat" rule is NOT implemented: the existing assertion for standard Android (blur
    off, lens off) expects Frosted, and on Android `deviceSupportsLiquidGlassProvider` is
    `enableBackdropBlur` (`lib/main.dart`), so a flat rule would also flatten every mid-tier
    device's Frosted. The owner must decide that. The app ships on Android only.
99. RECOMMENDATION. `performanceRecordingActiveProvider` is the cheap signal. The badge
    watches only it, so `performanceHubProvider` (and its SharedPreferences read) is built
    when a recording starts or the hub opens. Test: "the hub is not built until a recording
    starts" (`container.read(performanceHubProvider.exists)`).
100. RECOMMENDATION. When the system reports no refresh rate, the budget uses the measured
    interval and the jank figure is marked unreliable: a note above the results table
    (`perfHubJankUnreliable`) and "(unreliable: refresh rate not reported)" on the Over
    budget line of the text export. Test: `frame_timing_stats_test.dart`, "jank from a
    measured interval is flagged as unreliable in the text".
101. RECOMMENDATION. The two new strings, `perfHubCompareWarmup` and `perfHubJankUnreliable`,
    are in all 21 ARB files. The non-English strings are my translations and need native
    review, as in Phase 5.
102. RECOMMENDATION. The desktop inspector's overlay fill is 0.88 for Liquid and Frosted
    (was 0.74); None stays 0.97. The blur itself was already the preset sigma (2 or 3), not
    60, so only the fill changed. Test: `desktop_inspector_legibility_test.dart`; the Liquid
    and Frosted case fails before with `Actual: 0.74`. The value is a judgement, not
    measured on a device, and the owner should check the lyrics and queue legibility there.

## Review round 2 fixes (independent review PASS, decisions 103-106)

Round 1 returned NEEDS_FIXES (decisions 89-102). Round 2 returned PASS with four non-blocking
findings, F1, F2, F3 and F5 (there is no F4). Each fix below has a test that fails before the
change, except where stated. No assertion was weakened.

103. RECOMMENDATION, F1. A recording or comparison window discarded for too few frames now sets
    `PerformanceHubState.tooFewFrames`. The Results section shows one line, `perfHubTooFewFrames`:
    "Too few frames were recorded: keep scrolling or interacting while recording". The flag is
    cleared when a recording starts. Tests: "a recording below the frame floor says so until the
    next one starts" and "a comparison below the frame floor says so" (before: 0 widgets with the
    text). The new keys `perfHubTooFewFrames` and `perfHubModeChanged` are in all 21 ARB files. The
    non-English text is machine-written and needs native review, as in Phase 5. The `perfHub` key
    count is 42, counted in `app_en.arb`.
104. RECOMMENDATION, F2. Marked, not blocked. Blocking writes during a recording would make
    `ThemeNotifier` depend on the hub provider, which already depends on the theme provider, and
    would make a tap in Appearance do nothing without explanation. Marking changes no setting. A
    single recording's session sets `modeChangedDuringRecording` when `mornyeGlassActiveModeProvider`
    changes while it runs. The listener ignores comparisons, which change the mode themselves. The
    result shows a note (`perfHubModeChanged`), and the text export has the line "Glass mode changed
    during recording: yes/no". The JSON gains `mode_changed`, read as false when absent so saved runs
    still load. The session keeps the mode it started in, as before. `setMornyeGlassLevel`, the None
    rule and the persisted choice are unchanged. Tests: "a mode change during a recording marks the
    result" (before: 0 widgets); "a recording with no mode change is not marked" (a control, passes
    before and after); `test/performance_run_mode_change_test.dart` (before: compile error, the field
    did not exist).
105. RECOMMENDATION, F3. The stale statements in this file and in `TOR006.md` are corrected. The status
    is IN_PROGRESS. The review history is round 1 NEEDS_FIXES, then round 2 PASS. The key count is 42
    (38 at Phase 5, 40 after round 1, 42 after round 2). Phase-scoped "not run" notes now say they were
    true at that phase.
106. RECOMMENDATION, F5. The page calls `_runComparison`, not `notifier.compareModes`, from `onPressed`.
    It catches and logs the error, so nothing reaches the zone. The notifier's `finally` still releases
    the hub and restores the rendered mode, unchanged. Test: "a comparison that throws does not leak to
    the zone" (before: the `StateError` is reported as the test failure). The error is logged only, not
    shown, so no string was added for it. The mode restore is covered by the existing comparison tests,
    not by this test, because its throwing fake never touches the mode.

Status: IN_PROGRESS. The task is complete, pending the owner never committing. Nothing is committed or
pushed.

## Sampling audit and sampling reduction (decisions 107-126)

Scope: the static audit requested for the moving-frame cost (Liquid and Frosted raster
P50 about 16 ms on the owner's moto g75, None 1.9 ms) on Home, Library (Songs, Albums,
Artists, Playlists), Artist, Search, and the always-present bottom bar and mini player.
No device was available. Every effect below is an estimate from the source, not a
measurement. Re-measure each mode with the Performance hub before accepting it.

### Sampling audit (before this change)

"Reads" counts backdrop reads per frame on a surface that is built and visible at once.
"Shares" says whether it joined a batch. Counts are from reading the call sites and the
package source; none was measured.

| Screen | Surface | Kind | Liquid before | Frosted before | Sampling now (Liquid / Frosted) |
| --- | --- | --- | --- | --- | --- |
| Home | Profile avatar (header circle) | control | 1 lens, own batch (`HomeProfileButton`) | 1 lite blur 3 | 0 / 0 (rim only: sits on the opaque app bar, so no sample is useful) |
| Home | Search field | chrome, `samplesBackdrop: false` | 0 | 0 | 0 / 0 (unchanged) |
| Home | Recent access, shelves, rows | none | 0 (no glass found) | 0 | 0 / 0 |
| Home, Search, Library, Songs | Bottom bar tab capsule | chrome | 1 lens in the bar's batch | 1 lite in the bar's batch | 1 shared / 1 blur-0 lite (now 0 reads) |
| Home, Search, Library, Songs | Mini player (when a track plays) | chrome | 1 lens in the bar's batch | 1 lite in the batch | 1 shared / 0 reads |
| Home, Search, Library, Songs | Selection pill over the tab capsule | control (package motion pill) | 1 own read (`LiquidGlassBatch.exclude`, overlaps the capsule) | none | 1 / none (unchanged) |
| Home, Search, Library, Songs | Side capsules while folding | control | 1 own batch while crossing the bar | 1 lite each | 1 / 0 |
| Library (section pages) | Sort header circle | control | 1 lens, own batch | 1 lite | 1 / 0 |
| Library | Track and album rows | none | 0 | 0 | 0 / 0 |
| Library | Selection bar (while selecting) | chrome, overlay | 1 lens, own batch | 1 lite | 1 / 1 (unchanged; bar is chrome) |
| Artist | Back button | control | 1 lens, own batch | 1 lite | 1 shared with the capsule / 0 |
| Artist | Toolbar capsule (3 actions) | control | 1 lens, own batch | 1 lite | 1 shared with the back button / 0 |
| Artist | Collapse artwork blur (`ImageFiltered`, sigma 18) | artwork effect, not glass | 1 filter pass while collapsing | none | unchanged (not glass, see decision 53) |
| Any | Settings groups, sheets, dialogs, player details, song analysis, playlist picker, desktop inspector | content | 1 lens or lite each, several when stacked | 1 lite each / inspector backdrop filter | 0 / 0 (plain fill) |

Could not be determined here: whether the bottom bar stays visible on Artist and other
pushed pages (the navigator that `pushPage` uses was not traced), whether the header
avatar or the artwork under the Artist header is scrolled, and how many batches a device
builds per frame. Mini player visibility depends on playback.

Reading of the audit: on these screens the glass set is small. The bottom bar was
already one shared batch in Liquid. The larger cost is the bar's full-width lens and blur,
not the number of controls. The estimate below is therefore modest, and the Performance
hub result decides whether more is needed.

### Changes

107. OWNER (coordinator, approved). Content surfaces carry no glass. `MornyeGlassKind.content`
     is a plain fill in every mode, the same as None. Applied to settings groups (both
     paths), `AppBottomSheet` (`showAppBottomSheet` family), `AppAlertDialog` and
     `showAppSheetDialog`, the playlist picker, the player details sheet, the song analysis
     sheet and the desktop inspector overlay. Presenter APIs are unchanged.
108. OWNER (coordinator, approved). Glass stays on chrome and controls: tab bar, mini player,
     search field, selection bar, snack bar, desktop player bar and its volume popover, context
     menus, header circles and capsules, profile avatar, scroll arrows, bottom bar side capsules.
     Segmented controls stay `chrome`, so their `samplesBackdrop: false` plain fill is unchanged
     (making them `control` drew a rim-only lite on opaque cards, and was reverted).
109. RECOMMENDATION. `MornyeGlassKind` has three values: `chrome` (default), `control`,
     `content`. The per-kind rule is in the preset table (`MornyeGlassPreset.samplesFor`),
     so call sites pass a kind, not a blur value. `chrome` is the default so the existing call
     sites keep their behaviour. The enum is re-exported by `mornye_chrome.dart`.
110. RECOMMENDATION. Control in Frosted, and any control with `samplesBackdrop: false`, draws a
     blur-0 `LiquidGlassLite`. It keeps the rim and tint and reads no backdrop. The package draws
     the rim without a read (`_kRimFromBackdrop = false` in `liquid_glass_lite.dart`). Liquid
     controls keep their lens so they can batch.
111. OWNER (coordinator, approved). Frosted blur is 0 in `MornyeGlassPreset.frosted`. The tint
     carries the frost, so no surface in Frosted adds a backdrop pass. The rim is kept.
     Visible change: Frosted is a tint with no blur, on every glass surface.
112. OWNER (coordinator, approved). Liquid dispersion is 0 in `MornyeGlassPreset.liquid`
     (was 0.99). Visible change: the lens no longer draws a colour fringe on its edges. Liquid
     keeps sigma 2.
113. OWNER (coordinator, approved). One batch per page. The artist page wraps its scroll view in
     one `LiquidGlassBatch(enabled: mornyeLiquidGlassProvider)`, so the back button and the
     toolbar capsule share one read. The bottom bar keeps its one batch, and its selection
     pill stays `LiquidGlassBatch.exclude` because it overlaps the capsule. A batch is not
     placed over the whole app. Home has one glass control, the avatar, which no longer samples,
     so it needs no batch.
114. OWNER (coordinator, approved). `batchBlurMethod` is `LiquidGlassBlurMethod.shader` for Liquid
     (preset field `batchBlurMethod`). The package doc says `separatePass` (the default batch mode)
     adds an engine blur pass per batch, and `shader` folds the blur into the lens shader, so
     the pass is removed. The batch is created with `blurMethod: preset.batchBlurMethod` in the
     wrapper and in the bottom bar. The global `LiquidGlassConfiguration.batchBlurMethod` is not
     changed. MUST BE MEASURED with the Performance hub: no device here, and the shader blur
     may look slightly different at sigma 2.
115. RECOMMENDATION. The batch mode stays `separatePass`. The source documents `singlePass` as
     folding the blur into the member's own pass, which would change the look, and the owner
     asked to keep it out.
116. RECOMMENDATION. The inspector overlay fill is one value in every mode, 0xf7 (0.97), which is
     the existing None value. It was 0.88 with blur before. The test that asserted 0.88 is adapted.
117. Estimate, not measured. Frosted: content and controls stop sampling, and the bar keeps only
     blur-0 lites. Liquid: the artist page saves one read, the avatar saves one read, the
     controls in the bar keep their read, and the selection pill keeps its separate read. The
     shader batch blur may remove one engine blur pass per batch. The owner should check the
     Performance hub for Home, Library, Songs, Artist and Search in Liquid and Frosted.

### Results and checks for decisions 107-117 (owner: targeted tests only, no full suite)

118. CHECK. `flutter analyze` (whole project): 2 issues, one of them the known pre-existing
     info `workflow/codegraph/fixtures/bridge/lib/api.dart:5:47`. The other was an unused import
     in the new test, fixed. `dart format` on every touched file: 0 changed.
119. CHECK. Targeted suites, first run: 524 passed, 18 failed. Every failure was an old contract
     (Frosted blur 3, sheets, dialogs and settings groups as glass, the inspector's 0.88 fill) or
     the segmented-control case below. Each was adapted, not weakened, and the adapted files
     then passed: `app_alert_dialog`, `appearance_settings`, `desktop_inspector_legibility`,
     `equalizer`, `mornye_glass_level`, `mornye_glass_mode_migration`, `mornye_overlay_mode`,
     `settings_shared_helpers`, `mornye_glass_kind` (new, 12 tests) and `mornye_bottom_bar`:
     `+138 All tests passed`.
120. RECOMMENDATION. A segmented control was first made `control`. That drew a rim-only lite
     on opaque cards that pass `samplesBackdrop: false`, which the appearance and equalizer
     tests caught. It is `chrome` again, so its behaviour is unchanged.
121. RECOMMENDATION. The inspector overlay uses `Color(0xff363636).withValues(alpha: 0.97)`.
     `0xf7` quantises to 0.9686 and failed the None test (`>= 0.97`).
122. NOT RUN, per owner instruction: the full `flutter test` suite. The 36 environmental
     failures recorded in earlier phases were not compared again in this round.
123. Visible changes the owner will notice (no device check here):
     - Frosted: every glass surface is a tint with a rim and no blur.
     - Liquid: the lens has no colour fringe on its edges.
     - Settings groups, bottom sheets, dialogs, the playlist picker, the player details and song
       analysis sheets, and the desktop inspector are solid content fills in every mode, the same
       as None. Their glass is gone.
     - Header circles and the artist toolbar capsule keep their lens in Liquid and only a rim in
       Frosted. The profile avatar draws only a rim in every glass mode.
     - Liquid bottom bar selection pill and its shader batch: the batch blur is `shader`, which may
       look slightly different at sigma 2. Measure it.
124. OPEN. Nothing measured. Re-run the Performance hub on Home, Library, Songs, Artist and Search,
     in None, Frosted and Liquid, and compare with the owner's baseline (Liquid P50 15.9 ms, P95
     34.1 ms; Frosted 16.4 / 30.0; None 1.9 / 4.5). The expected effect is an estimate only.

## Phase 8 - Performance tail: artist blur, collection batch (decisions 125-131)

125. OWNER (task). Artist collapse blur must not re-filter on each scroll frame. Replaced the
     `ImageFiltered` whose sigma changed with the collapse (0 to 18). Now a second copy of the
     artwork is drawn through `SmallSettledBlur` (new, `lib/widgets/settled_blur.dart`): a
     fixed sigma (18 at full size) at 1/8 scale, cached once settled, faded in with an `Opacity`
     driven by the collapse. None and Frosted build no blur subtree (Liquid only, as before).
     Visible change: the artwork softens as the header collapses, the same intent, drawn from a
     cached small image. Estimate, not measured.
126. COORDINATOR (approved in task). The artist blur follows the album header's blurred-backdrop
     mechanism (small, cached, scrim kept). The helper is shared in `settled_blur.dart`.
127. FINDING. The album page's blurred backdrop is NOT on the rendered path. Every current
     `AlbumDetailHeader` caller (local album, mix, collection/playlist) passes `immersive: true`,
     and the immersive branch draws `background` with a scrim and no `SettledBlur`. The
     `SettledBlur(sigma: 32)` in `album_detail_header.dart` is reached only by a caller with
     `immersive: false` and no `coverBuilder`, and none exists. So no album small-decode change
     was made: there is no album blur to make cheaper. The album's real cost is its header
     buttons (see 128).
128. OWNER (task). One `LiquidGlassBatch` per page in `CollectionScaffold` (`body`), enabled by
     `mornyeLiquidGlassProvider` and using the preset `batchBlurMethod`. It covers the album,
     mix and playlist pages, which share the shell. The album header buttons and toolbar capsule
     now share one backdrop read in Liquid. Off in Frosted and None.
129. RECOMMENDATION. The artist page's batch also passes the preset `batchBlurMethod`, as the
     policy in decision 114 requires. It previously used the package default. Visible change:
     the artist header lens blur uses the shader path, which may look slightly different.
130. CHECK. `flutter analyze` (whole project): 1 issue, the pre-existing info
     `workflow/codegraph/fixtures/bridge/lib/api.dart:5:47`. `dart format` on touched files:
     0 changed. Targeted tests: `mornye_detail_headers_test`, `mornye_glass_kind_test`
     (new collection-page counts: None 0, Frosted 0, Liquid one batch in shader with two
     lenses sharing one key), `library_artist_screen`, `local_album_screen`,
     `collection_detail_screen`, `settled_blur`, `mornye_bottom_bar`, `mornye_glass_level`,
     `mornye_artist_capsule`: `+115 All tests passed`. No full suite (owner instruction).
131. OPEN. Nothing measured. Expected effect is an estimate: the artist collapse no longer runs a
     changing blur per frame, and the album and playlist headers read the backdrop once.
     Re-run the Performance hub on Artist, Album and Playlist in Liquid to confirm.

## Phase 9 - Collapsing blur as one helper, method 1 (decisions 132-138)

Owner decision (coordinator): replace the fixed-sigma SettledBlur layer of decisions 125-127 with
the progressive blur the artist page had, drawn on a small box. The same mechanism runs on the album
and every collection page.

132. OWNER. The artist artwork blurs progressively as it collapses: sigma 0 to 18, the same curve as
     before (`Curves.easeOut` over collapse). It is enabled only in Liquid, not with reduced motion,
     and only while the blur is above 0 and the fade is below 1.
133. OWNER. `ScaledBlur` (replaces `SmallSettledBlur`, which is removed with the fixed-sigma layer).
     It lays the child out at 1/8 size, applies `ImageFilter.blur` at sigma/8 in that box with clamp
     edges, and scales the box back up 8x with `FilterQuality.medium`. The only ImageFiltered on a
     collapsing header sits inside it.
134. OWNER. The album and collection headers (`AlbumDetailHeader` immersive path, used by local album,
     mix, playlist, genre and loved songs) use the same helper. The scroll position drives sigma
     (full blur at the artwork's detail offset). Sharp at rest, sharp again past full blur. Liquid only.
135. FINDING (dead branch). The `SettledBlur(sigma: 32)` branch in `_buildAppBar` (non-immersive,
     no cover builder) has no caller in the app; every `AlbumDetailHeader` is immersive. The branch is
     removed. `blurAndScrimBackground` still controls the scrim on the non-immersive path.
136. RISK, NOT MEASURED. Transform.scale with filterQuality draws the blurred small box up to full size.
     The filter itself is evaluated in device pixels under that transform, so the saving in filtered
     pixels is not proven. The owner should measure the Performance hub on Artist and Album in Liquid
     before counting it. At small sigma the 1/8 box can show blocks (the blur is still soft at 18).
137. CONSEQUENCE. The artist artwork element is rebuilt when the blur box appears (sharp at rest ->
     blurred). Its test checks that the artwork stays in the tree, not that it is the same element.
138. CHECK. `flutter analyze` (project): the pre-existing info
     `workflow/codegraph/fixtures/bridge/lib/api.dart:5:47` only. `dart format` on touched files.
     Targeted: `collapsing_artwork_blur` (new, 5 tests), `mornye_detail_headers`, `mornye_glass_kind`,
     `library_artist_screen`, `local_album_screen`, `collection_detail_screen`, `settled_blur`,
     `mornye_glass_level`, `mornye_bottom_bar`, `mornye_artist_capsule`, `desktop_header_artwork_scheme`:
     `+156 All tests passed`. No full suite. Nothing measured.

## Phase 10 - Two device bugs in APK v3 (decisions 139-145)

Owner's screen recording of APK v3 (artist page, Liquid) showed two bugs. Fixed with the smallest
change, both with tests that fail before the fix and pass after. Nothing committed, pushed or built.

139. BUG 1 ROOT CAUSE (reproduced, not guessed). `ScaledBlur` laid its 1/8 box out under the
     `ClipRect`'s tight full-size constraints. `Transform.scale` passes constraints to its child, and
     `SizedBox.fromSize(small)` cannot loosen tight constraints, so the "small" box grew to full size.
     The filter then ran at full size and the 8x scale magnified only the box's top-left corner over
     the whole header: the "huge blurred crop" the owner saw. Transform does not change layout size,
     so the 1/8 box was never really small. Reproduced in `test/scaled_blur_test.dart` by pixel
     sampling: before the fix, every quadrant read red (the top-left).
140. BUG 1 FIX. `ScaledBlur` wraps the `Transform.scale` in `Align(topLeft)`, which gives the small box
     loose constraints. The clip stays full size. Sigma 0 and 6 both paint the child over the full
     box, quadrants in place (`scaled_blur_test`). The 1/8 trick works with `Transform.scale` and
     `filterQuality` once the box is really small. The alternative (a cached small `ui.Image`) was not
     needed. Whether the blur is cheaper on device remains unmeasured (decision 136).
141. BUG 1 TESTS. `test/collapsing_artwork_blur_test.dart` now renders a quadrant-coloured child
     through the real artist header and the immersive album header (via `CollectionScaffold`, which
     also covers mix, playlist, genre and loved songs), samples the page at several collapse offsets,
     and checks the left and right halves. Before the fix (mutation: `Align` replaced by
     `SizedBox.expand`), four pixel tests failed; after the fix all pass. `test/scaled_blur_test.dart`
     does the same for `ScaledBlur` at sigma 0 and 6.
142. BUG 2 ROOT CAUSE. `library_artist_screen.dart` built the play circle's colour from the outer
     `Theme.of(context)` in `build()`, outside `MornyeArtistSurface`. Under a light outer theme that
     is white, so the white glyph vanished. The header's actions are rendered inside the surface, so a
     `Builder` there reads the artist scheme.
143. BUG 2 FIX. The play `CupertinoButton` is wrapped in a `Builder` and takes
     `Theme.of(context).colorScheme.surface` (the page background). The glyph stays white.
     The shuffle and star glass circles are unchanged.
144. BUG 2 TEST. `test/library_artist_screen_test.dart` (new "play circle" test, light outer theme):
     the circle equals the artist page's `Scaffold.backgroundColor`, is not white, and the glyph is
     white. Before the fix (mutation: colour forced to white) it failed.
145. CHECK. The album page has no such mistake: `local_album_screen.dart` reads `colorScheme` inside the
     `MornyeArtistSurface` builder, and its neutral white controls are by design. The collection detail,
     mix and playlist pages use the same surface pattern.
     `flutter analyze` (project): only the pre-existing info
     `workflow/codegraph/fixtures/bridge/lib/api.dart:5:47`. Targeted: `scaled_blur`,
     `collapsing_artwork_blur`, `mornye_detail_headers`, `library_artist_screen`, `local_album_screen`,
     `collection_detail_screen`, `settled_blur`, `mornye_glass_kind`, `mornye_glass_level`,
     `mornye_bottom_bar`, `mornye_artist_capsule`, `desktop_header_artwork_scheme`: `+161 All tests passed`.
     NOT VERIFIED on device: the look of the blur and the play circle in APK v3, and the blur's cost.

## Phase 11 - APK v4 header artwork on collapse (decisions 146-151)

Owner's recording of APK v4 (artist, Liquid, release): the play glyph and the magnified-corner crop are
fixed, but on collapse (1) the artwork shifts down with a flat brown strip and a hard top edge, (2) no
blur is visible at any frame, (3) the header goes flat dark. Expected (base 8e4993e): the artwork stays
anchored at the top, is cropped from the bottom as the header shrinks, softens progressively, then fades.

146. REPRODUCTION ON THE REAL HEADER. `test/artist_header_real_test.dart` mounts the real
     `MornyeArtistHeader` in a `CustomScrollView` (real SliverAppBar and FlexibleSpaceBar), with a tall
     decoded 120x160 PNG (red, green, blue, yellow quadrants) through `Image.memory`, at collapse
     offsets 0, 60 and 120, in None, Frosted and Liquid. The base widget is kept as a reference copy in
     `test/support/reference/artist_header_base.dart` (`git show 8e4993e`), not in lib.
     RESULT (actual output): in this headless environment the current header matches the base. The top
     row stays anchored (red left, green right at every offset), the columns match, and Liquid softens
     the edge (base and current both widen it). The owner's device shift, strip and missing blur did NOT
     reproduce here, at devicePixelRatio 1 or 3, with or without the new clip. Not fixed on device by
     evidence: see 151.
147. CAUSE (static, consistent with the owner's hint). `ScaledBlur` lays its child out in a box of the
     shrinking size, while the framed artwork is an `OverflowBox` sized at `maxExtent`, taller than
     that box. Nothing clipped the overflow before the filter and the 8x scale. `ScaledBlur` now wraps
     the framed child in `ClipRect` inside the small box, so the framing is cut to the box before any
     filter or scale-up. This is defensive: it makes no visible difference in the headless tests.
148. ALBUM AND COLLECTION HEADER. The immersive header (all collection pages) is checked the same way
     (`albumMain` in the same test file). The album artwork scrolls with the page rather than pinning
     like the artist header, so the check is the quadrant placement (red left, green right at the top
     edge) and the edge softness in Liquid. Flat and Frosted stay sharp.
149. TESTS. `test/artist_header_real_test.dart`: 6 tests (artist and album, three modes each). Edge
     softness is measured as the count of pixels across x 175..215 that are neither pure red nor pure
     green: a sharp edge gives about 2. Liquid must be at least 6 wider than the sharp edge and close to
     the base. The artist top row must match the base within 12 per channel, outside the edge.
     Synthetic pixel tests (`scaled_blur_test`, `collapsing_artwork_blur_test`) still pass.
150. CHECK. `flutter analyze` (project): see the report; only the pre-existing info
     `workflow/codegraph/fixtures/bridge/lib/api.dart:5:47` is allowed. `dart format` on touched files.
     Targeted: `artist_header_real`, `scaled_blur`, `collapsing_artwork_blur`, `mornye_detail_headers`,
     `library_artist_screen`, `local_album_screen`, `collection_detail_screen`, `settled_blur`,
     `mornye_glass_kind`, `mornye_glass_level`, `mornye_bottom_bar`, `mornye_artist_capsule`,
     `desktop_header_artwork_scheme`: `+167 All tests passed`. No full suite, no APK, no commit, no push.
151. NOT VERIFIED ON DEVICE, STATED PLAINLY. The headless renderer is not Impeller. The device symptoms
     may come from the Impeller path for `Transform(filterQuality)` (an `ImageFilter.matrix` layer) or from
     the real layer bounds of an `ImageFiltered` under a scale. This is a hypothesis and was not tested.
     The owner should check APK v5 on the phone. If the shift persists, the fallback is a cached
     blurred small `ui.Image` cross-faded over the sharp image (no per-frame filter).

## Phase 12 - Cached blurred cover replaces ScaledBlur (decisions 152-158)

The owner's APK v4 recording still showed the artwork shifting down with a hard top edge and no visible
blur. Headless tests could not reproduce it (not Impeller). The `Transform.scale` + `filterQuality` +
`ImageFilter` trick is too fragile on device, so it is removed. Recommendation adopted (owner delegated).

152. REMOVED. `ScaledBlur`, `SmallSettledBlur`, its `ClipRect`/`Align` changes, `test/scaled_blur_test.dart`,
     and the `kCollapsingArtworkBlurSigma` copy in `settled_blur.dart`. `SettledBlur` stays (used by
     listening rewind and its own tests).
153. NEW `lib/widgets/blurred_cover.dart`: `BlurredCoverCache` builds one blurred copy per provider and size.
     It decodes the cover through `ResizeImage` at 1/8 (aspect kept), draws it with BoxFit.cover into a
     picture under `saveLayer` with `ImageFilter.blur` (sigma 18/8, clamp), and `toImage`s it. The cache
     keeps up to 8 entries, LRU, and disposes an evicted image. A widget draws `image.clone()` and
     disposes its clone, so an eviction cannot free an image still on screen. `clear()` is for tests.
154. NEW `BlurredCoverLayer(provider, opacity)`: an opaque `RawImage` of the blurred copy at the same
     box as the sharp artwork, at `Opacity(opacity)`. Its opacity is the collapse (`easeOut`), so the
     sharp artwork cross-fades to its blur. No filter per frame. Framing is the same by construction:
     the layer sits in the same `Stack` as the sharp child, inside the same OverflowBox/Transform/clip.
155. ARTIST. `MornyeArtistHeader(blurSource:)` passes the provider into `_ArtistCollapsingArtwork`, which
     draws the artwork in `Stack(child, BlurredCoverLayer)` inside the unchanged OverflowBox/Transform.
     The layer is built only in Liquid, not with reduced motion, and only while the fade is below 1.
156. ALBUM AND COLLECTION. `AlbumDetailHeader(blurSource:)` draws the blurred layer over the immersive
     background, with its opacity from the scroll position (`_CollapsingCoverBlur`). Liquid only.
     Callers pass a single-cover provider: artist (`library_artist_screen`), local album
     (`local_album_screen`), and single-cover playlists (`collection_detail_screen`, `_HeaderArt.blurSource`).
     Composite covers (mix, genre) and the icon-only loved header have no single source: they stay sharp.
157. TESTS. `test/artist_header_real_test.dart` (6): real header, real decoded PNG, three modes, artist and
     album. The top row is anchored at each offset; Liquid mid-collapse widens the edge beyond the sharp
     edge (+6) and stays within 3x the base (the cross-fade blends a full-sigma copy, so mid-collapse reads
     wider than the base's growing sigma: a difference, stated). `test/blurred_cover_test.dart`: one blurred
     copy per cover and size; eviction is bounded and disposes. `test/collapsing_artwork_blur_test.dart`:
     cross-fade opacity, no ImageFiltered in either header subtree, no layer in None or Frosted.
     `test/mornye_detail_headers_test.dart` artist blur test adapted to the layer's opacity.
158. CHECK. `flutter analyze` (project): see the report. `dart format` on touched files. Targeted:
     `artist_header_real`, `blurred_cover`, `collapsing_artwork_blur`, `mornye_detail_headers`,
     `library_artist_screen`, `local_album_screen`, `collection_detail_screen`, `settled_blur`,
     `mornye_glass_kind`, `mornye_glass_level`, `mornye_bottom_bar`, `mornye_artist_capsule`,
     `desktop_header_artwork_scheme`, `listening_rewind_backdrop`: `+176 All tests passed`.
     NOT VERIFIED ON DEVICE: the shift, the visible blur and the flat dark frame on the phone; the
     Impeller behaviour of `RawImage` with `FilterQuality.medium`; the decode and blur cost of the first
     frame for a cover (asynchronous, so the first collapse may show the sharp artwork briefly); the
     memory of up to 8 blurred copies.

## Phase 13 - Liquid selection pill, measured (decisions 159-166)

Owner's APK v5 recording (Liquid, light): the selection capsule that moves between the tabs (and the
segmented controls) shows the content behind it sharp and displaced; on Library the pill refracts the red
selected glyph; on Home it shows a copy of the artwork. Priority 1 is the pill; the bar surface and the
mini player come next.

159. CONFIGURATION COMPARED. Ours (`_MotionPillState._glassThumb`, mornye_chrome.dart): `LiquidGlassMotionPill`
     with no style, so the package motion-pill defaults: blur 1.5, refractionIndex 1.021, refractionWidth 18,
     colour 0x1CFFFFFF, dispersion 0, inside `LiquidGlassBatch.exclude`, drawn ABOVE the icons (so its lens
     refracts the selected glyph). The package's own tab pill (`LiquidGlassTabPillStyle` defaults, used by
     `LiquidGlassTabBar` and the demo): blur 0, refractionIndex 1.015, refractionWidth 24, dispersion 1.32,
     magnification 1, colour 0x46AEAEB2 at rest. The demo's bar: white 0xADFFFFFF tint, saturation 2, blur 2.5.
160. MEASURED ON THE REAL LENS (test/_probe, headless, LiquidGlassView with a backgroundWidget, which is
     what gives refraction in tests). A red/blue step edge behind a lens, edge position and transition:
       none: edge 199.5, transition 51 px (no lens blur)
       ours shader blur 2 : edge 201.5, transition 54 px
       ours engine blur 2 : edge 201.5, transition 54 px
       ours shader blur 5 : edge 203.5, transition 57 px
       demo index 1.2 width 16 shader 5 : edge 203.5, transition 57 px
     Conclusion: the shader blur and the engine blur give the same result at each sigma, and the lens
     displaces an edge by about 2 to 3 px, not a copy. The sharp look is therefore not a blur-method defect
     and not a strong refraction. A stripe backdrop at blur 2 (shader and engine) left 0.7% of pixels sharp,
     against 50% unblurred.
161. CHANGE (applied). The selection pill takes the package tab pill's refraction and no blur, through the
     central preset: `MornyeGlassPreset.pillBlurSigma` 0, `pillRefractionIndex` 1.015, `pillRefractionWidth` 24
     (Liquid; the other modes use the same values, unused, as the pill exists only in Liquid). The colour
     stays 0x1CFFFFFF and dispersion stays 0. Cost: the pill's blur pass is removed, and the pill keeps its one
     read (it is excluded from the page batch because it refracts the bar, as before). No new read, no new
     pass.
162. WHY THIS IS THE CAUSE OF THE PILL LOOK (reasoned, not measured on device). A blur of 1.5 under a pill
     with refraction 18 pulls the glyph under it in two directions at once; the package's pill is a clear
     lens with refraction only, so the glyph bends without the extra softening that made it read as
     distorted. Whether this matches the demo on the phone is unverified.
163. NOT CHANGED, with reasons. The bar surface (blur 2, tint 6% with the clear fill at 0.69, saturation 1)
     and the mini player: the probe in 160 gives no evidence that the blur method or sigma 2 causes the sharp
     content, because the edge widths already match the demo's sigma. The demo's extra softness comes from
     saturation 2 and a stronger white tint, not from the blur. This is a candidate for the next change; it
     needs a device check first, so it is not applied here.
164. DISPERSION. The package's tab pill uses dispersion 1.32 (a colour split). Policy 112 keeps dispersion 0
     because any non-zero dispersion adds a backdrop read. So the pill is not the demo's dispersed pill, by
     design.
165. TESTS. `test/glass_pill_style_test.dart` (2): Liquid pill is blur 0, index 1.015, width 24, dispersion 0,
     taken from the preset; Frosted shows no motion pill.
166. CHECK. Targeted: `glass_pill_style`, `mornye_control_motion`, `mornye_glass_interaction`,
     `mornye_bottom_bar`, `mornye_glass_kind`, `mornye_glass_level`, `design_system`, `glass_mode_preview`,
     `mornye_artist_capsule`, `mornye_search_glass`: see the report. `flutter analyze` (project): see report.
     NOT VERIFIED ON DEVICE: the pill's look on the phone against the demo, the Library red glyph, the
     Home copy, and the bar surface and mini player (not changed).

## Phase 14 - Package switch in Liquid; Frosted at 60% of Liquid (decisions 167-180)

### Switch (owner request)

167. VERIFIED IN THE PACKAGE. `LiquidGlassSwitch` (`lib/src/widgets/components/switch/liquid_glass_switch.dart`):
     `value`, `onChanged` (required, non-null), `activeColor`, `inactiveColor`, `thumbColor`, `width`/`height`
     (track, default 63x28), `layout`, `style`, `pixelRatio`. It owns a `LiquidGlassView` with
     `realTimeCapture: false` (captured on demand, not per frame), whose background is the switch's own track.
     At rest the thumb is a solid white pill (`SolidWhiteToggleThumb`); its lens is mounted with
     `visibility: _morph > 0`, so the glass is drawn only while the thumb is touched, carried or
     animating. Tap toggles; a drag past `kTouchSlop` toggles on release. It has no semantics of its own,
     no RTL handling (positions are absolute), and a required `onChanged`.
168. COST DECISION. A resting switch draws no glass and reads no backdrop: the lens is hidden at rest.
     Its view captures its own small track once when mounted. So a settings page with many switches pays
     one small capture each, not a backdrop read per frame. The glass appears only while touched, as the
     package intends. If the owner measures a cost on the Settings page, the fallback is the plain switch.
169. IMPLEMENTATION (`lib/widgets/app_switch.dart`). `AppSwitch` is a `ConsumerWidget`. In Liquid (and not
     with reduced motion) it builds `_LiquidAppSwitch`: `LiquidGlassSwitch` with `activeColor` the Mornye
     accent (`colorScheme.primary`), `thumbColor` white, the plain switch's inactive colour. Disabled is
     half opacity with `IgnorePointer` (the package needs a non-null callback, so it gets a no-op). RTL
     mirrors it with `Transform.flip`. Semantics: `Semantics(label, toggled, enabled, onTap)` around an
     `ExcludeSemantics`. Frosted and None keep the plain Mornye switch, which samples nothing.
     Call sites are unchanged (`AppSwitch`, `SettingsSwitchItem`). The `mornye_chrome` glass switch
     does not exist; the switch lives only in `app_switch.dart`.
170. TESTS. `test/app_switch_glass_test.dart` (7): None and Frosted build no package switch and sample 0;
     Liquid shows the package switch and toggles on tap (both ways); disabled takes no input and reads as
     disabled; RTL mirrors and toggles; the switch exposes toggled and enabled semantics in Liquid and None.
     Adapted (old contracts, same intent): the drag test in `mornye_glass_interaction_test` and the thumb
     motion tests in `mornye_control_motion_test` now pin the plain switch with the None mode, since they
     measure the plain thumb.

### Frosted at 60% (owner decision)

171. `kFrostedStrength = 0.6` (`lib/theme/mornye_glass_presets.dart`) is the one named constant. Frosted is
     Liquid at that share: `blurSigma` 2 x 0.6 = 1.2; `tintStrength` and `clearMix` (the clear veil) x 0.6;
     `strength` 0.6, which scales the rim specular and glow and the shadow alpha in `MornyeGlass`. None
     is `strength` 0. Liquid's values are unchanged.
172. CONTROLS AND CHROME SAMPLE IN FROSTED. `samplesFor(control)` is true in Frosted (was false). Every
     glass kind that is glass now draws a Lite with blur 1.2. Content surfaces stay solid. Frosted's
     `batchBlurMethod` is `shader`, as Liquid's: the blur folds into the batch's single read.
173. ONE SHARED BATCH IN FROSTED (cost control). The page batches (`CollectionScaffold`, the artist page)
     are enabled by `mornyeBlurEnabledProvider` (Liquid or Frosted) instead of Liquid only. The bottom bar
     already enabled its batch on `blurEnabled`. Blurred Lite members read the batch's one copy
     (`LiquidGlassBatch.backdropIdOf`), so a page does not read once per control.
174. FROSTED SELECTION THUMB. The segmented-control thumb is a Lite pill at 60% (`MornyeGlass.navigation`,
     kind control), drawn beneath the items, as the flat thumb was. It is not `exclude`d from the batch, so
     it shares the page's read. It is off under high contrast (glass is off there) and under reduced
     motion (a flat thumb, as Liquid's). Liquid's motion pill is unchanged.
175. HIGH CONTRAST. The contrast block still turns Frosted glass off: found and fixed in the thumb
     (decision 174), caught by the existing high-contrast test.
176. COST, STATED PLAINLY. Frosted is no longer blur 0: it now blurs and reads the backdrop once per page
     batch, as Liquid does. Its cost is therefore higher than the blur-0 Frosted of decision 111. The
     earlier measurement put Frosted at about the cost of Liquid before the sampling work. This change
     must be re-measured with the Performance hub: Home, Library, Songs, Artist and Search in Liquid,
     Frosted and None. No performance number is claimed here.
177. SUPPORTING NOTES. Lite has no refraction, dispersion or saturation, so Frosted does not use them.
     Lite's magnification is the default 1. The Frosted look is the Liquid look with less blur, veil and
     rim; the refraction and the colour split are Liquid-only.
178. TESTS (adapted to the new contract, intent kept): `mornye_glass_kind_test` (preset table, samplers and
     the shared batch in Frosted, collection page in Frosted), `mornye_glass_level_test` (Frosted blur 1.2;
     the Lite count includes the Frosted thumb, which reduced motion removes), `mornye_glass_mode_migration_test`
     (blur 2 x 0.6), `glass_mode_preview_test` (Frosted preview: one sampler), `glass_pill_style_test` unchanged.
179. PERFORMANCE HUB TEXT. Checked: the hub's text names the mode and renderer only and says nothing about
     blur 0, so no hub text needed changing. The README's Performance section is unchanged.
180. CHECK. `flutter analyze` (project): see report. Targeted: see report. NOT VERIFIED ON DEVICE: the
     switch's look and feel against the package demo, the cost of its view capture on a long settings page,
     the Frosted look at 60%, and the Frosted cost (re-measure as in 176).

## Phase 15 - Frosted removed; hub report rows removed; package slider in Liquid (decisions 181-191)

181. OWNER. Frosted Glass removed from TOR006. Modes are Liquid and None. `MornyeGlassLevel` is
     {flat, liquid}; `kFrostedStrength`, the Frosted preset and every `LiquidGlassLite` path of ours are
     gone. A device that cannot render Liquid renders None for the effective level only; the saved choice
     stays Liquid. A stored `frosted` value reads as Liquid. The clarity migration is 0 -> None and any
     value above 0 -> Liquid. The retired force-blur never changes the result. Removed from the selector,
     the Performance hub compare (Liquid vs None), the labels, the ARB files (21) and generated l10n.
182. CHECK. `grep` under lib/ and test/: `frosted`/`Frosted`/`kFrostedStrength` remain only in the migration
     reader (`kRetiredFrostedGlassName`) and its tests; `LiquidGlassLite` remains only in the package's own
     lens fallback counts. `flutter analyze` (project): 1 issue, the pre-existing info.
183. PERFORMANCE HUB ROWS REMOVED (owner). The three "not available" lines (Impeller, GPU passes, Backdrop
     reads) are gone from the report, the hub table, the clipboard export, the ARB keys (21 files) and the
     generated l10n. The export's `not_measured` JSON key went with them; nothing read it. No replacement.
184. SLIDER DECISION. The package has `LiquidGlassSlider` (value, minimumValue, maximumValue, onChanged,
     onChangeEnd, isContinuous, activeColor, inactiveColor, thumbColor, width, height, layout). It has no
     divisions and no semantics, so `AppSlider` (lib/widgets/app_slider.dart) provides both: divisions are
     applied by snapping every reported value, and the semantics (label, value, slider, enabled, increase
     and decrease actions) are on the wrapper. In Liquid (and not with reduced motion) `AppSlider` uses the
     package slider in the Mornye accent, mirrored in RTL, with a disabled state that takes no input. In None
     it is the standard Material `Slider`, as before. The package slider has no value label; `label` still
     reaches the Material slider only.
185. NOT SWITCHED (kept on their own widgets, as the owner asked): the Now Playing volume (`MornyePlayerSlider`
     in `player_volume_control`), the progress/seek bars (`PlaybackSeekSlider`, `MornyePlayerSlider` in
     `playback_seek_slider`, the desktop player bar and the lyrics editor's seek bar). `app_slider_glass_test`
     proves `MornyePlayerSlider` draws no package slider in Liquid.
186. EXCEPTION NEEDING THE OWNER. The equalizer's band control (`MornyeLevelSlider`, used by
     `equalizer_widgets`) is a custom iOS-style slider with an `origin` (the gain bands fill from zero) and a
     custom knob radius. The package slider has neither origin nor a radius option, so it stays as it is.
     Switching it would change how the gain bands read. Decision needed.
187. COST. Each package slider builds its own `LiquidGlassView` (a capture of its own track, on demand), not a
     batch and not a backdrop read per frame. A settings page with many sliders therefore builds one small view
     per slider. The package draws its glass only while the thumb is held, so a resting slider reads no
     backdrop. No batch is added per slider. Measure a long settings page on the phone before relying on this.
188. TESTS. `test/app_slider_glass_test.dart` (7): None uses the Material slider and no lens; Liquid uses the
     package slider and snaps to divisions; a disabled Liquid slider takes no input; RTL mirrors; semantics in
     both modes; `MornyePlayerSlider` is not switched. Existing slider suites pinned to None where they
     exercise the Material slider's gestures (autoplay randomness, loud volume confirm, lyrics appearance,
     playback tempo): `+32 All tests passed`.
189. NOT VERIFIED ON DEVICE: the package slider's feel against a finger, the value bubble (absent in
     Liquid), and the per-slider view cost on a long settings page.
190. MIX DETAIL, GENRE AND LOVED HEADERS (composite grid, owner correction: the Home mix cards are unchanged).
     The mix detail header and the genre header take the first available cover of the collection as their blur
     source (`library_mix_screen`: `_paletteSource`; `collection_detail_screen` genre: `_genrePalette`), decoded at
     1/8, blurred once and cached by `BlurredCoverCache`. Rendering the 2x2 grid once and blurring that image was
     not built: the first cover is the owner's allowed fallback. Loved songs has an icon, not a cover, so it has no
     blur source and stays sharp. The cache holds 8 copies; only one detail header is visible at a time, so 8 does
     not thrash for these pages. Home mix cards: not changed.
191. AUTOMATIC BENCHMARK (owner request) NOT IMPLEMENTED in this pass. The plan is recorded but no driver,
     runner, hub button or strings were added: driving Home, Library, Songs, Artist and Search needs navigation
     through the shell's tabs and the library list that could not be checked on a device here. It remains open.

## Phase 16 - Automatic benchmark, mix title legibility (decisions 192-199)

192. SUPERSEDES 191. The automatic benchmark is implemented. Its pure runner is `lib/services/performance_auto_measure.dart`
     (`runAutoMeasure`, `autoMeasureScroll`, `AutoMeasureStep`); the app driver is `lib/services/performance_auto_drive.dart`
     (`buildAutoMeasureSteps`, `primaryScrollPosition`); the hub method is `PerformanceHubNotifier.runAutomaticMeasurement`,
     and the button sits in the hub's compare section with its hint.
193. SPEED. `kAutoMeasureScrollVelocity = kMaxFlingVelocity` (8000 logical px/s, Flutter's constant). Each scroll goes
     down two viewports and back to where it started; each leg lasts distance / 8000 s, at linear curve. This is a
     programmatic scroll, not a finger: its frames are comparable only between the two modes of one run, not with a
     hand-scrolled session.
194. ORDER AND MODES. For each mode (Liquid, then None) the screens run in order: Home, Library, Songs, Artist, Search.
     The mode is rendered through the in-memory compare override, never written to the saved selection, and the override
     is cleared in a `finally`: also on cancel (the hub's cancel and pause use the same flag) and on error.
195. [CORRECTED by round 3, decision 204: Songs IS reachable and is measured.] HOW THE SCREENS ARE DRIVEN (real APIs). Home, Library, Search: `ShellNavigationService.requestTab`. The screen's
     primary scroll position: the first `ScrollableState` under `ShellNavigationService.activeTabNavigator()` that can
     scroll. Artist: the first artist from `libraryBrowsePageLoaderProvider` (one artist page, sorted a-z), opened with
     `MaterialPageRoute` on the Library navigator and popped before the next screen. If the library has no artist, the
     Artist screen is skipped with the "no data" reason. Songs: the Library overview's Songs item calls
     `onOpenSection('all')`, which pushes `QueueTab(librarySection: 'all')`; the driver pushes the same page.
196. SKIPS AND ROWS. A screen with no data, no scroll position (nothing to scroll) or too few frames is skipped in that
     mode, and the run goes on; skipped screens appear as "Skipped:" lines in the report. The table has one row per screen
     and mode: screen, mode, frames, raster P50/P95/P99, UI P50/P95, over budget, using the existing stats and report.
     The run is saved as one comparison (last five kept, as before), and the clipboard export is the existing report text.
197. CANCEL AND PAUSE. The hub's cancel button and the app's pause use the existing `cancelComparison` flag: the current
     scroll stops between legs and the run ends with the mode restored.
198. TESTS. `test/performance_auto_measure_test.dart` (7): the fixed speed; a scroll down and back at the fixed speed with a
     real list; the order Liquid then None, the restore to the saved mode (null) after the run; skips for nothing to scroll
     and too few frames; cancel stops the run and restores; an error stops the run, is reported and restores; the report
     rows, the skip lines and the JSON round trip. Fakes stand for the shell; the scrollables are real `ListView`s.
     NOT COVERED BY A TEST: the shell driver (`buildAutoMeasureSteps`) and the hub method, which need the app shell; they
     are unverified on a device.
199. MIX TITLE LEGIBILITY. `test/mix_header_legibility_test.dart`: in Liquid, with a white cover (the worst case), the
     title's contrast over the blurred cover is at least 3:1 (WCAG large text). Without a scrim it measured 1.0. The fix
     is a black scrim that fades in with the blurred cover in `_CollapsingCoverBlur`, reaching 50% alpha within the first
     15% of the collapse, where the title is still over the cover. The same scrim applies to the album and collection
     headers, which share the widget.

## Phase 17 - Independent review round 3 fixes (decisions 200-215)

200. BLOCKING 1, the hidden route. VERIFIED: `primaryScrollPosition` walked every route, oldest first, so on the Artist
     step it found the hidden Library list, whose tickers are off, and `animateTo` never finished. FIX: only a scrollable
     whose route is current (`ModalRoute.of(context)?.isCurrent`) and whose tickers are enabled is accepted. Test: a route
     pushed over a list; the top list is chosen (`performance_auto_drive_test`).
201. BLOCKING 1, cancel and timeouts. VERIFIED: cancel was checked only between legs. FIX: a 50 ms poll calls
     `jumpTo(pixels)` once cancel is set, stopping the running scroll in place; each leg has a timeout of its duration
     plus 3 s (`kAutoMeasureLegSlack`), after which the scroll is stopped at its position. Test: cancel stops a running
     scroll (`performance_auto_drive_test`).
202. BLOCKING 1, the hub's finally. VERIFIED: `_endComparison` clears the override and releases the hub in its finally,
     and the recorder is stopped by `_finish` (which `_endComparison` calls while a recording is open). The driver's
     cleanup is also called in that finally (decision 205).
203. BLOCKING 2, the frame floor. VERIFIED by arithmetic: 2 viewports at 8000 px/s is about 0.4 s (about 24 frames at 60 Hz),
     below the 60-frame floor, so every screen would have been skipped. FIX: the down-and-back cycle repeats until at least
     2 s of scrolling has elapsed (`kAutoMeasureMinDuration`), capped at 40 cycles (`kAutoMeasureMaxCycles`). The time is
     the sum of the leg durations, so it is deterministic. Test: a fake 60 Hz frame clock proves at least 60 frames
     (`performance_auto_measure_test`).
204. BLOCKING 3, Songs. VERIFIED: `mornye_library_screen.dart` line ~332 calls `onOpenSection('all')`, and
     `queue_tab.dart` ~222-228 pushes `Scaffold(body: QueueTab(librarySection: 'all'))`. FIX: the driver pushes the same
     page on the Library navigator, and is measured like Artist. `perfHubAutoNoSongs` is removed from all 21 ARB files
     and l10n.
205. NON-BLOCKING 6 and 7. VERIFIED: the driver popped to the first route (`popUntil(isFirst)`) and read the hub
     page's `WidgetRef` after the tab change. FIX: the pushed route is kept and removed alone with `removeRoute`, in the
     driver's `cleanup`, which the hub calls in its `finally`. The library loader is read once when the steps are built,
     before any tab change, so no ref is used afterwards.
206. NON-BLOCKING 9, the title scrim. VERIFIED: the scrim sat inside `_CollapsingCoverBlur`, which returns early
     outside Liquid, so it was missing in None and with reduced motion. Measured, the collapsed toolbar title on a
     bright cover had contrast 1.0. FIX: the scrim is now behind the title text itself: a translucent 50% box with
     padding, drawn in every mode, for the details title and the toolbar title (`_titleScrimmed`). A pinned band in the
     toolbar was tried and collapsed to zero height inside the overlay, so it was dropped.
207. SHARED WIDGET, NOTED. `AlbumDetailHeader` is shared by the local album page, the mix page and the collection
     page. The scrim is opt-in through `titleScrim`, which only the mix (`library_mix_screen`) and the collection
     (`collection_detail_screen`) headers set. The album page keeps its default (false), so it is unchanged.
208. NON-BLOCKING 4, discrete sliders. VERIFIED: `isContinuous: divisions == null` reports only on release for discrete
     sliders. FIX: `isContinuous: true`; `_send` forwards the snapped value only when it differs from the last one sent.
     Test: a discrete drag reports no repeated step.
209. NON-BLOCKING 5, slider semantics. VERIFIED: the value and the increase and decrease values used the display label.
     FIX: the value uses `semanticFormatterCallback`, falling back to two decimals; the increase and decrease values use the
     same formatter. Test: the formatter, not the label, appears in the semantic value and steps.
210. NON-BLOCKING 8, the decoded image. CORRECTED BY 220. The round 3 text said the cache's image was cloned
     and disposed by us; that was wrong. See decision 220.
211. NON-BLOCKING 10, tests. Added: the loud-volume confirm in Liquid (`loud_volume_confirm_test`); the driver
     (`performance_auto_drive_test`); the hub's automatic run with no shell (`performance_hub_page_test`). A full fake-shell
     run of the sequence through the hub notifier is not built; the runner and the driver are tested separately.
212. NON-BLOCKING 11, records. TOR006.md: title, Request, Acceptance 2-3, a note on the Phase 3 Frosted parts, the new
     Phase 15, the Phase 16 summary, this Phase 17, and the planned commit subject. This file: the Phase 15 header range
     is 181-191; decision 195 is corrected by 204.
213. NOTE, saved runs. A saved run whose renderer was `frosted` now shows as Liquid, because `mornyeGlassLevelFromString`
     reads the old name as Liquid. The run's numbers are unchanged; only the label is.
214. NOT DONE, stated. The hub notifier's full sequence with a fake shell (tabs, the artist and songs pushes, the frame
     recorder) is not covered by a test; it is covered by the runner tests with fake steps and the driver tests with real
     lists and routes. Device behaviour of the shell driver and the numbers are unverified.
215. ANALYZE AND TESTS. `flutter analyze` (whole project) reports 1 issue, in
     `workflow/codegraph/fixtures/bridge/lib/api.dart`, which this branch does not change. The targeted suites listed in
     TOR006.md Phase 17 all pass. The full suite was not run, by instruction.

## Phase 18 - Independent review round 4 fixes (decisions 216-227)

Owner's grant: review rounds 4 to 6 on top of the normal three, in the owner's words: "معاك ٣ جولات اضافيه".
Round 3 returned NEEDS_FIXES (fixed in Phase 17); round 4 returned NEEDS_FIXES (fixed here); round 5 is next.

216. BLOCKING 1, the frame floor. VERIFIED: `autoMeasureScroll` looped on time and cycles only, so a slow window
     ended at 2 s with few frames. FIX: the loop also continues while `frames() < minFrames`. The hub passes
     `() => _recorder.frameCount` and `performanceMinFramesProvider` (60). The 40-cycle cap still ends it. Tests: a
     2 fps clock runs to the cap (8 s measured, fewer than 60 frames); a 60 fps clock stops at 2 s with 60 frames.
217. NON-BLOCKING 6, a list at its end. VERIFIED: `down = min(start + distance, max)` equals `start` at the end, so
     nothing moved. FIX: the scroll goes towards the side with more room (up at the end). Test: it moves from its end.
218. NON-BLOCKING 5, subscriptions. VERIFIED: `ModalRoute.of` and `TickerMode.valuesOf` register dependencies, so each
     visited scrollable was rebuilt on route changes. FIX: `TickerMode.getValuesNotifier(context).value.enabled`, which
     does not subscribe. The overlay mutes the tickers of entries under an opaque route (`overlay.dart`,
     `tickerEnabled: false`). LIMIT: a transparent route (a dialog) over a list does not mute it, so that list would
     be measured. The driver pushes only opaque `MaterialPageRoute`s, so this does not occur in the run.
219. NON-BLOCKING 5, disposed positions. FIX: `_leg` turns an animation error into a `StateError`, which the runner
     records as the run's error. `_stopAt` swallows its error, so a timer never throws. The timeout stops the leg where
     it is.
220. BLOCKING 2, ImageInfo ownership. VERIFIED in the SDK: `image_stream.dart:561` calls
     `listener.onImage(_currentImage!.clone(), ...)`, so a listener receives a clone it owns. `_decode` already
     returned that clone; the round 3 code cloned it again and disposed only the second, so the first was never
     disposed. CORRECTS decisions 207 and 210. FIX: `_generate` takes the decoded image once and disposes it in a
     `finally` around `_draw`, which also disposes the picture. The cache's own images are not touched.
221. NON-BLOCKING 3, slider echo. VERIFIED: `didUpdateWidget` set `_lastSent = null` whenever the value changed, so
     the parent's echo of the step re-sent it. FIX: the new value's snapped step becomes the last sent. Test: a
     parent that stores each value gets each step once per drag.
222. NON-BLOCKING 4, title colour. VERIFIED: the light theme's toolbar title is black (0,0,0, read at the title
     element). The fixed black 50% scrim made a grey box, and contrast fell from 21:1 to about 5.3:1. That still passes
     3:1, so the earlier test could not catch it. FIX: `_titleScrimmed` reads the title's effective colour; dark text
     gets `colorScheme.surface` at 60%, light text keeps black 50%. TEST: the colour is read with
     `DefaultTextStyle.of(element).style.merge(text.style)`; both themes; a dark cover for black text and a bright one
     for white (the worst case for each); dark text must reach 7:1, light text 3:1. Mutation check: restoring the fixed
     scrim fails the light-theme toolbar cases at offset 400 (both levels), and the fix passes all eight.
223. NON-BLOCKING 7, route cleanup and leg timeout. ADDED: a cancelled run and a run that throws both remove the page
     they pushed (a navigator observer records the pushes and removals). A leg whose ticker is muted ends at its timeout,
     with the list back at its start.
224. TEST DEFECT, found while adding 223. A scroll that was not awaited, on a list that then unmounted, raised its error
     into the unawaited future. The test now cancels and awaits the scroll. In production the scroll is awaited inside
     the run, so the error reaches the runner.
225. TEST EXPECTATIONS. The first draft hardcoded a 100 ms leg; the harness measures 150 ms, so the tests now compute
     the leg from the list.
226. TEST EXPECTATIONS. The first slider echo test required every value across both drags to be unique. The return
     drag legitimately crosses steps the first drag sent. It now checks each drag.
227. VERIFICATION. Analyze: the one pre-existing fixture issue only. Targeted tests: 87 passing across 12 files. The full
     suite was not run, by instruction.

## Phase 19 - Independent review round 5 fixes (decisions 228-235)

Round 5 returned NEEDS_FIXES. Round outcomes: 3 NEEDS_FIXES, 4 NEEDS_FIXES, 5 NEEDS_FIXES. The owner's grant covers rounds
4 to 6. The coordinator called this fix "for the last round"; by the grant, round 6 still follows (to be confirmed).

228. BLOCKING 1, the page picked mid-transition. VERIFIED in the SDK: `routes.dart` `_handleStatusChanged` sets
     `overlayEntries.first.opaque = false` on forward and `opaque` on completed. During the slide the covered screen
     keeps its tickers. `primaryScrollPosition` walks the navigator depth-first, bottom entries first, so the Library
     root list was found, and it muted once the page covered it. Each leg then waited its timeout.
229. FIX. `pushOnLibrary` waits for the pushed page's animation to reach `completed`, bounded by its
     `transitionDuration` plus `kAutoMeasureTransitionMargin` (300 ms). The route is installed synchronously by push
     (`navigator.dart` `_pushEntry` calls `_flushHistoryUpdates`), so its animation exists at that point.
230. FIX. `primaryScrollPosition` takes `within: ModalRoute`. When given, the search starts at
     `within.subtreeContext`, a plain getter on ModalRoute that does not subscribe. The driver passes the pushed page
     whenever one is pushed; a page that is not active or not built gives null, never the root list. Test: 250 ms into
     the slide the untargeted lookup returns the root list (the premise), and the targeted lookup returns the page's
     list or null; 500 ms later, the page's list.
231. TEST MECHANICS. The route cleanup tests pump frames while the run is in flight (`_runPumped`). Their settle and
     transition waits are fake timers, which `tester.runAsync` does not advance. Before this, a skipped step meant the
     cancel never ran.
232. BLOCKING 2. VERIFIED: `runAutomaticMeasurement` saved whenever rows were non-empty, so a run cancelled after its
     Liquid row was saved. A backgrounded run calls `cancelComparison` (`_LifecycleWatch`), so it is cancelled too.
     FIX: save only when `!result.cancelled`, as decision 76 says. Test: the second mode's prepare cancels; nothing is
     saved. Mutation check: the old rule fails this test.
233. ITEM 3, wall-clock cap. ADDED: `kAutoMeasureMaxScreenDuration` (20 s) and `maxDuration`, plus an injectable `clock`
     (default: a stopwatch started with the scroll). Test: a clock that moves 10 s per read stops after the third read,
     two cycles, which is four legs.
234. ANALYZE. Whole project: the one pre-existing fixture issue. The two stale `!` warnings in the new tests were
     removed. Targeted tests: 61 passing across 8 files.
235. NOT VERIFIED on a device: the page transition, the driver, and the benchmark numbers. The full suite was not run.

