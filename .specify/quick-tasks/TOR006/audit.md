# TOR006 Phase 1 - Read-only audit: native Liquid Glass Easy adoption

- Task: TOR006 (reserved; record `.specify/quick-tasks/TOR006.md`)
- Branch / base: `glass-native-adoption`, base commit `8e4993e`
- Source plan: `/root/.claude/uploads/9d293f2a-980b-503e-90a8-6a595050bc28/6d0bfb06-Sistrum_Liquid_Glass_Optimization_Plan.md`
- Scope of this phase: read-only. No source under `lib/`, `test/`, `integration_test/`, `android/`, `rust_backend/` was modified. No commit, no push.
- Notation: `path:line` refers to repository files unless a path is absolute. Package references use `pkg:` relative to `/root/.pub-cache/hosted/pub.dev/liquid_glass_easy-5.2.0/`.

Contents: 0 environment and map, 1 installed API, 2 repository usage and classification, 3 overlay and navigation inventory, 4 clarity semantics and persistence, 5 baseline and infrastructure, 6 limitations, 7 summary and ordered checklist.

---

## 0. Environment and map

- Map refresh (first action, per `workflow/README.md`): `python3 workflow/codegraph/code_tools.py update` returned `{"symbols": 28267, "relationships": 96665, "unindexed_sources": 0, "rejected_cross_language_calls": 0}`, exit 0. The path in the task brief (`workflow/code_tools.py`) does not exist; the real path is `workflow/codegraph/code_tools.py`.
- Plan-versus-repo discrepancies:
  - The plan names base branch `ccr-627ec7dd-omzhib`. This task names `glass-native-adoption` at `8e4993e`. This audit follows the task brief.
  - Plan says `liquid_glass_easy: 5.2.0`. Confirmed: `pubspec.yaml:45` pins `liquid_glass_easy: 5.2.0`, and `pubspec.lock` matches.
- Toolchain: `.fvmrc` pins Flutter `3.50.0`. `/opt/flutter/bin/flutter --version` reports `Flutter 3.50.0 - channel stable`, which matches. `flutter` is not on `PATH`. Dart SDK constraint is `^3.10.0` (`pubspec.yaml:7`). Package requires `sdk >=3.6.2`, `flutter >=3.3.0` (`pkg:pubspec.yaml`).
- Container hardware: 4 CPUs, 15 GiB RAM, no `/dev/dri`, no `adb`, no Android device. Device frame measurements are impossible here.

---

## 1. Installed API of liquid_glass_easy 5.2.0

### 1.1 Public barrel

Source: `pkg:lib/liquid_glass_easy.dart`. Everything below is exported unless marked NOT EXPORTED.

Core and lens:
- `LiquidGlassView` (`pkg:lib/src/widgets/liquid_glass_view.dart:203`): `{controller, backgroundWidget (required), child, pixelRatio=1.0, realTimeCapture=true, useSync=true, refreshRate, useImpellerBackdrop, regionCapture=false, batch=true, batchMode, foregroundBatchId, adaptiveSampling}`.
- `LiquidGlassLens` (`pkg:lib/src/widgets/lens/liquid_glass_lens.dart:174`): `{style=LiquidGlassStyle(), visibility=true, opacity=1, useImpellerBackdrop, touch, deform, restSize, honorBackdropAlpha=false, shaderClip=outlineTracked, child}`.
- `LiquidGlassStyle` (`pkg:lib/src/widgets/liquid_glass_style.dart:126`): flat fields `shape, specular, glow, refractionIndex=1.1, refractionWidth=12, magnification=1, dispersion=0, saturation=1, blur=0, color, shadow, adaptivity, liteGlass=false`.
- `LiquidGlassShape` (`pkg:lib/src/widgets/utils/liquid_glass_shape.dart:51`): `{cornerStyle=continuousRounded, cornerRadius=50, clipQuality=roundedRectangle}`. Corner styles include `squircle` and `roundedRectangle`, as used by `lib/widgets/mornye_liquid_backdrop.dart:61-66`.
- `LiquidGlassLight` (`pkg:lib/src/widgets/utils/liquid_glass_light.dart`): `.specular(...)` and `.glow(...)`, used by `lib/widgets/mornye_liquid_backdrop.dart:69-81`.
- `LiquidGlassBatch` (`pkg:lib/src/widgets/lens/liquid_glass_batch.dart:142`): `{child, enabled=true, mode, blurMethod, shaderBlurDispersion}`; `LiquidGlassBatch.exclude({child})`; `LiquidGlassBatchMode { singlePass, separatePass }` (`:237`).
- `LiquidGlassBlender` (`pkg:lib/src/widgets/lens/liquid_glass_blender.dart`): merges 2 to 8 member lenses into one metaball surface. `LiquidGlassGroup` is exported but deprecated in favour of `LiquidGlassBlender(smoothness: 0)`.
- `LiquidGlassShaders` (`pkg:lib/src/widgets/lens/liquid_glass_shaders.dart`): `ensureLoaded()` (documented at `:30-40`).
- `LiquidGlassEngine` (`pkg:lib/src/widgets/liquid_glass_engine.dart`): static `liteGlassOnSkia`, `liteGlassOnImpeller`, getter `liteGlass`.
- `LiquidGlassConfiguration` and `LiquidGlassBlurMethod { engine, shader }` (`pkg:lib/src/widgets/liquid_glass_configuration.dart:13-21`). Static fields: `blurMethod=engine` (`:41`), `batchBlurMethod=engine` (`:51`), `batchMode=separatePass` (`:56`), `shaderBlurDispersion=false` (`:67`), `batchShaderBlurDispersion=false` (`:72`), `shapeCache` get/set (`:84-85`), `blurOverride` (`:91`), `dispersionOverride` (`:96`), `shadowColorOverrideLight/Dark` (`:101,104`), `rimLiftOnDark=true` (`:114`).
- `LiquidGlassLite` (`pkg:lib/src/widgets/components/liquid_glass_lite.dart:85`): `{shape, specular, glow, borderAlpha=1, blur=3.0, magnification=1, color, shapeScale, child}`. Frost, tint and a painted rim; no refraction shader. CHANGELOG 5.0.0 notes "the frost's read is the only one a lite surface costs" (`pkg:CHANGELOG.md:51`), so lite glass is not backdrop-free.
- Controllers: `LiquidGlassController`, `LiquidGlassViewController` (with `captureOnce`, `pkg:lib/src/controllers/liquid_glass_view_controller.dart`).
- Adaptivity family: `LiquidGlassAdaptivity`, `LiquidGlassAdaptiveSampling`, `LiquidGlassAdaptivityLink`, `LiquidGlassAdaptivityController`, `LiquidGlassAdaptiveArea`, `LiquidGlassAdaptiveContent`, `LiquidGlassBrightnessFallback`, `LiquidGlassSystemChrome`.

Gesture and motion:
- `LiquidGlassTouch({flex})` (`pkg:lib/src/widgets/utils/liquid_glass_touch.dart:33`); `LiquidGlassFlex` (`pkg:lib/src/widgets/utils/liquid_glass_flex.dart:196`): `{stretch=8, squeeze=0.70, lean=0.50, grip=0.70, compressInward=true, holdScale=0.030, tapScale=0.020, maxPull=48, lockAxis, advanced}`.
- `LiquidGlassDraggable`, `LiquidGlassSpring`, `LiquidGlassPosition`, `LiquidGlassRefreshRate`, `LiquidGlassLensMotion` / `LiquidGlassLensMotionSpec`.
- `LiquidGlassMorph` (`pkg:lib/src/widgets/components/morph/liquid_glass_morph.dart:274`): `{width, height, alignment, motion=LiquidGlassMorphMotion.plain, style, smoothness=40, onEnd, opacity=1, touch, debugClipBounds, child}`.
- `LiquidGlassMorphMotion` (`pkg:.../morph/liquid_glass_morph_motion.dart`): presets `fluid`, `anchoredPop`, `plain` (`:175`), `droplet`, `calm`; factory `.spring(...)`. Shared-element `tag` on `LiquidGlassMorphShared` (5.2.0 changelog).
- `LiquidGlassMotionPill` (`pkg:.../liquid_glass_motion_pill.dart:134`): `{center, active, restSize, activeSize, style, motion, expandStiffness=247, expandDamping=18.9, contractStiffness=110, contractDamping=14.7, cover, shadow, honorBackdropAlpha=true, onGlassVisibilityChanged}`.
- `LiquidGlassShadow` (`pkg:.../liquid_glass_shadow.dart:122`): `{blur=3.5, opacity=0.2, color=black, offset, cornerRadius, scale, inset=0, visible=true, insideGlass=true, child}`.
- `LiquidGlassScrollEdge` (`pkg:.../liquid_glass_scroll_edge.dart:160`): `{edge=bottom, height, color, adaptivity, blur=5, curve, blurCurve, style=soft, child}`. Exactly one `BackdropFilter` at any sigma (CHANGELOG 4.2.0, `pkg:CHANGELOG.md:119`).

Components:
- `LiquidGlassButton` (`pkg:.../liquid_glass_button.dart:35`): `{label, icon, onPressed, width, height=48, padding, style, visibility, foregroundColor, fontSize=16, fontWeight, iconSize=20, touch, child}`.
- `LiquidGlassFab` (`:33` of `liquid_glass_fab.dart`): `{icon, child, onPressed, size=56, padding, style, visibility, foregroundColor, iconSize=24, heroTag, tooltip}`.
- `LiquidGlassAppBar` (`pkg:.../liquid_glass_app_bar.dart:38`): `{leading, title, actions, centerTitle=true, height=56, width=360, horizontalPadding=14, actionSpacing=8, style, visibility, foregroundColor, fontSize=18, touch}`.
- `LiquidGlassSwitch` (`pkg:.../switch/liquid_glass_switch.dart:176`): `{value, onChanged, activeColor, inactiveColor, thumbColor, width, height, layout, style, reserveSwellRoom, pixelRatio}`.
- `LiquidGlassSlider` (`pkg:.../slider/liquid_glass_slider.dart:180`): `{value, onChanged, onChangeStart, onChangeEnd, minimumValue, maximumValue, isContinuous, activeColor, inactiveColor, thumbColor, width, height, layout, motion, minimumIcon, maximumIcon, style, pixelRatio}`.
- `LiquidGlassTabBar` (`pkg:.../bottom_nav_bar/liquid_glass_tab_bar.dart:75`): `{items (LiquidGlassTabBarItem list), selectedIndex, onChanged, onReselected, itemStyle, pillStyle, style, visibility, width=328, height=62, margin, itemPadding=4, alignment, swell, joinBatch=false}`. Items: `LiquidGlassTabBarItem({icon, selectedIcon, label, iconBuilder, labelBuilder, enabled=true})` (`liquid_glass_tab_item.dart`). Also exports `LiquidGlassTabBarAction`, `LiquidGlassTabBarSwell`, `LiquidGlassTabPillBehavior`, `LiquidGlassTabMagnifierPillStyle`.
- `LiquidGlassScaffold` (`pkg:.../liquid_glass_scaffold.dart:289`): `{body, appBar, bottomNavigationBar, bottomNavigationBarAction, floatingActionButton, floatingActionButtonAlignment, floatingActionButtonClearance, lenses, backgroundColor, safeArea=true, appBarTopMargin, bottomNavigationBarActionMargin, floatingActionButtonMargin, controller, pixelRatio, realTimeCapture, useSync, refreshRate, useImpellerBackdrop, batch=true, batchMode, adaptivity}`.
- `LiquidGlassSheet` (`pkg:.../liquid_glass_sheet.dart:203`): `{child, header, anchor=floating, grabber=true, grabberColor, grabberSize, padding, style, visibility, foregroundColor, safeArea=true, touch}`; `LiquidGlassSheetAnchor`; presenter `showLiquidGlassSheet` (forwards all `showModalBottomSheet` parameters).
- `LiquidGlassDialog` (`pkg:.../liquid_glass_dialog.dart:159`): `{child, width=340, padding=24, style, visibility, alignment}`; `LiquidGlassAlertDialog` (same file): `{icon, iconColor, title, content, actions, style, width=340, scrollable, visibility, ...}`; presenter `showLiquidGlassDialog<T>` (`:51`, uses `showGeneralDialog`).
- `LiquidGlassMorph` / `LiquidGlassBlender` / `LiquidGlassGroup` as above.

NOT EXPORTED by the barrel (verified by absence in `pkg:lib/liquid_glass_easy.dart`):
- `LiquidGlassWarmUp` (`pkg:lib/src/widgets/utils/liquid_glass_warm_up.dart:49`).
- `LiquidGlassSegmented`, `LiquidGlassSegmentedControl`, `LiquidGlassMorphSegmented`, `LiquidGlassMorphPill`, `LiquidGlassMorphPillStatic` (barrel comment: "they animate, and these stay hidden until their motion work is finalized").
- `LiquidGlassDock`, `LiquidGlassAppIcon`, `LiquidGlassControlTile`, `LiquidGlassAnimatedNavBar`.
- `LiquidGlassShapeCache` (only reachable through `LiquidGlassConfiguration.shapeCache`).
- The raw `LiquidGlass` / `LiquidGlassGeometry` / `LiquidGlassBehavior` engine configuration.

Not present anywhere in the package:
- No menu, popup-menu or context-menu component (grep for `class .*Menu` returned nothing).
- No `samplesBackdrop` parameter (grep returned nothing). Equivalent controls are `LiquidGlassBatch.exclude`, `honorBackdropAlpha`, `LiquidGlassView.adaptiveSampling`, and `enabled:` on a batch.

### 1.2 Plan section 6 table: availability in 5.2.0

| Plan row | Target | Status | Exact name and evidence |
|---|---|---|---|
| MornyeLiquidBackdrop | LiquidGlassLens | AVAILABLE | `LiquidGlassLens(style, visibility, opacity, touch, deform, restSize, honorBackdropAlpha, shaderClip, child)` (lens file `:174-186`). Standalone on Impeller; needs a `LiquidGlassView` on Skia/web (barrel comment). |
| Custom frosted renderer | LiquidGlassLite | AVAILABLE | `LiquidGlassLite(shape, specular, glow, borderAlpha, blur=3, magnification, color, shapeScale, child)`. Also `LiquidGlassEngine.liteGlassOnImpeller/liteGlassOnSkia` and `LiquidGlassStyle.liteGlass`. Still performs one backdrop read for frost. |
| MornyeGlassPress | LiquidGlassFlex or equivalent | AVAILABLE (equivalent) | `LiquidGlassTouch(flex: LiquidGlassFlex(...))` passed as `touch:` to Lens, Button, AppBar, Sheet, Morph, TabBarAction. Transform deformation of the glass, not a paint-only press; behaviour differs (accepted by plan section 2 and 7). |
| MornyeSelectionPill | Native Motion Pill | AVAILABLE (partial) | `LiquidGlassMotionPill(...)` exported standalone (`:134`). Segmented-control path (`LiquidGlassSegmentedControl`) is NOT exported, so segmented controls have no native component. |
| MornyeTabBar | LiquidGlassTabBar | AVAILABLE | See 1.1. `joinBatch: false` default; `items` takes `LiquidGlassTabBarItem`. |
| Custom morphing | LiquidGlassMorph | AVAILABLE | See 1.1; `LiquidGlassMorphMotion.plain` exists (`morph_motion.dart:175`). |
| Custom buttons | LiquidGlassButton | AVAILABLE | See 1.1. |
| Custom switches | LiquidGlassSwitch | AVAILABLE | See 1.1. |
| Custom sliders | LiquidGlassSlider | AVAILABLE | See 1.1. |
| Custom rim and glow | Native optical lighting and glow | AVAILABLE | `LiquidGlassLight.specular/glow`, `LiquidGlassStyle.specular/glow`, `LiquidGlassLite` rim, `LiquidGlassConfiguration.rimLiftOnDark`. |
| Custom shadow | Native glass shadow | AVAILABLE | `LiquidGlassShadow(...)` (`:122`), `LiquidGlassStyle.shadow`, `LiquidGlassConfiguration.shadowColorOverrideLight/Dark`. |
| Custom geometry | LiquidGlassShape | AVAILABLE | `LiquidGlassShape(cornerStyle, cornerRadius, clipQuality)` (`:51`). Squircle via `LiquidGlassCornerStyle.squircle`. |
| Redundant clipping | Native clipping | PARTIAL | `LiquidGlassShape.clipQuality` (`LiquidGlassClipQuality`), `LiquidGlassLens.shaderClip` (`LiquidGlassShaderClip.outlineTracked/snapped`). Equivalence to current `ClipRRect`/`ClipPath` (`lib/widgets/mornye_chrome.dart:373-377`) must be verified visually. |
| Header edge blur | Native scroll-edge | AVAILABLE (semantics differ) | `LiquidGlassScrollEdge(...)`. The current header blur is a collapse-driven `ImageFiltered` (`lib/widgets/mornye_artist_header.dart:502`), not an edge scrim. Equivalence not established. |
| Custom menus | Native menu | NOT AVAILABLE | No menu, popup or context-menu component in 5.2.0. |
| Custom dialogs | Native dialogs | AVAILABLE | `LiquidGlassDialog`, `LiquidGlassAlertDialog`, `showLiquidGlassDialog`. |
| Custom sheets | Native sheets | AVAILABLE | `showLiquidGlassSheet`, `LiquidGlassSheet`, `LiquidGlassSheetAnchor`. |
| Floating chrome | LiquidGlassScaffold | AVAILABLE (conditional) | `LiquidGlassScaffold(body, appBar, bottomNavigationBar, floatingActionButton, batch, ...)`. It owns the view pipeline; Sistrum's bottom bar has custom fold and search behaviour (`lib/widgets/mornye_bottom_bar.dart:296-340`), so adoption would need a layout rewrite. |
| Plan 10.2 / 10.6 | `LiquidGlassConfiguration` blur method, batch mode, shape cache | AVAILABLE | See 1.1. |
| Plan 10.5 | `samplesBackdrop` | NOT AVAILABLE by that name | Use `LiquidGlassBatch.exclude`, `honorBackdropAlpha`, or the app's own `samplesBackdrop` (in-house, `lib/widgets/mornye_chrome.dart:133`). |
| Plan 10.8 | Native shader warm-up | PARTIAL | `LiquidGlassShaders.ensureLoaded()` is exported; `LiquidGlassWarmUp` is NOT exported. |
| Plan 3 / 6 | Native segmented control | NOT AVAILABLE | `LiquidGlassSegmentedControl` is not exported (barrel comment). |
| Plan 6 | Native dock or app icon | NOT AVAILABLE | `LiquidGlassDock`, `LiquidGlassAppIcon` not exported. |

### 1.3 Efficiency guide and composed-blur limitation

Official guide read: `https://ahmeedgamil.github.io/liquid_glass_easy/en/efficiency.html` (fetched in this session).

Key points:
- "The most efficient glass is glass inside a batch with no blur" (`blur: 0`). "Blur is what affects performance the most." Lower or disable blur first when glass must be faster.
- Impeller cost order: batch with blur 0 (cheapest); batch with shader blur (`LiquidGlassConfiguration.batchBlurMethod = shader`, "keeps its single read"); batch with engine blur, separate pass (the default).
- On engine-blurred batches: separate pass is the default, "not a rule, this can change once the Flutter team fixes the single pass bug"; "with many glasses it is not effective"; for a few glasses "it has not been tested yet".
- Blur sigma guidance: 2 to 5 in every case, 2 to 3 preferred; "many glasses: 2" (tested on the Social Feed page).
- Skia: static backgrounds use `realTimeCapture: false` with `captureOnce()`; scrolling, video or animated backgrounds stay live.
- Lite glass: "no shader, no capture, no lens budget".
- Shape cache: on Impeller Android and iOS a lens bakes its outline; turned off with `LiquidGlassConfiguration.shapeCache = false`.
- Numbers: `pixelRatio 0.7` "usually invisible and meaningfully cheaper" (default 1.0); engine blur above roughly 7 on Skia "stops looking like a real backdrop blur".

Composed-blur limitation, as documented in the package:
- `LiquidGlassBatchMode.singlePass` folds an engine blur into the member's shader pass with `ImageFilter.compose(outer: shader, inner: blur)` (`pkg:lib/src/widgets/lens/liquid_glass_batch.dart:237-241`). This is the mode the 5.0.0 release used.
- `separatePass` (the 5.1.0 default, `pkg:CHANGELOG.md:25`) makes the blur a keyed pass of its own.
- CHANGELOG `pkg:CHANGELOG.md:91`: batched blurred lenses needed shader changes for Flutter 3.47, and "on 3.47 a batch saves less than on earlier versions, because of an engine bug in backdrop sharing".
- The package does not name a Flutter issue number. The guide refers only to "the single pass bug".
- Consequence for Sistrum: do not force `singlePass`. Either keep the default `separatePass`, or set `batchBlurMethod = shader` for many glasses. Do not patch Flutter (plan section 10.2 forbids it).
- Separate, app-level: `ImageFilter.compose` is also used in a non-backdrop artwork path, `lib/widgets/mornye_motion_glow.dart:151-162`, inside `ImageFiltered`. It is outside the lens batch but is an engine composed filter, so it belongs to the Phase 8 and 9 review.

Flutter pin: `.fvmrc` 3.50.0 (`/opt/flutter` 3.50.0). The package's 3.47 workarounds apply to 3.47 and older; behaviour on 3.50 is not verified in this session.

---

## 2. Repository usage and classification

### 2.1 Usage inventory (lib/, test/, integration_test/)

Image-filter and backdrop primitives:

| Primitive | Sites (lib/) | Notes |
|---|---|---|
| `BackdropFilter.grouped` (Flutter API) | `lib/widgets/mornye_chrome.dart:348` (in `MornyeGlass.build`) | The only backdrop-sampling path for frosted tier and the non-lens liquid fallback. |
| `BackdropGroup` (Flutter API) | `lib/widgets/mornye_bottom_bar.dart:358` | Inside a `LiquidGlassBatch` at `:354`. |
| `BackdropKey` | none in lib/ | Batch uses the package's internal key. |
| `BackdropFilter` (plain) | `lib/widgets/desktop/desktop_inspector.dart:232-233` | Fixed sigma 60, desktop only. |
| `ImageFiltered` | `lib/widgets/mornye_motion_glow.dart:150,227`; `lib/widgets/lyric_blur.dart:41`; `lib/widgets/mornye_artist_header.dart:502`; `lib/screens/lyrics_share_screen.dart:902`; `lib/widgets/lyrics_size_setting.dart:295` | Artist header is gated by the liquid level (`:494`). |
| `ImageFilter.blur` (render objects, Paint) | `lib/widgets/settled_blur.dart:142`; `lib/widgets/lyric_blur.dart:163`; `lib/widgets/player_lyrics_word_sweep.dart:813` | Live-then-cached lyric blur. |
| `ImageFilter.compose` | `lib/widgets/mornye_motion_glow.dart:151` only | Non-backdrop; see 1.3. |
| `LiquidGlassLens` / `LiquidGlassBatch` / `LiquidGlassBatch.exclude` | `lib/widgets/mornye_liquid_backdrop.dart:58`; `lib/widgets/mornye_chrome.dart:333-345` (via `LiquidGlassBatch(child:)`); `lib/widgets/mornye_bottom_bar.dart:310,354`; `lib/widgets/mornye_selection_pill.dart:495` | Only four lib/ files import `liquid_glass_easy` for rendering. `mornye_squircle_border.dart` also imports it (shape types). |
| Other package components (Button, Switch, Slider, TabBar, Sheet, Dialog, Scaffold, Morph, ScrollEdge, MotionPill, Flex) | none in lib/ | The package's components are unused. Every glass surface is built from the in-house wrapper. |
| Custom shaders | `assets/shaders/mornye_selection_mask.frag` (19 lines), loaded at `lib/widgets/mornye_selection_pill.dart:160-161` | Registered in `pubspec.yaml:147-148`. |
| `FragmentProgram` / `ShaderMask` | `lib/widgets/mornye_selection_pill.dart:13-14,93,160,550-680`; `lib/widgets/mornye_landscape_player.dart:407-428` (`MornyeLyricsFadeMask`, lyrics fade, not glass) | |
| `toImageSync` (cached blur snapshot) | `lib/widgets/settled_blur.dart:171`; `lib/widgets/lyric_blur.dart:150` | Not per frame; once after settle. |
| `toImage` | `lib/controllers/mornye_glass_warmup.dart:118` (one-time); `lib/utils/widget_capture.dart:21`; `lib/widgets/mornye_artwork_contrast.dart:195,296`; `lib/widgets/audio_analysis_spectrogram.dart:435`; `lib/theme/cover_palette.dart:344` | Only the warm-up is glass-related. |
| Repaint boundaries | `lib/widgets/mornye_bottom_bar.dart:184,203`; `lib/widgets/mornye_motion_glow.dart:91`; `lib/widgets/mornye_artist_header.dart:528`; `lib/widgets/album_detail_header.dart:609` | Plan 10.7 requires justification per site. |
| Opacity layers near backdrop | `lib/widgets/app_alert_dialog.dart:140` (comment on BackdropFilter isolation); `lib/widgets/mornye_context_menu.dart:39,267` (`Opacity(opacity: fade)`) | `Opacity` wrapping glass is the case the package docs warn about (lens `opacity` doc). |
| `saveLayer` | `lib/widgets/player_lyrics_word_sweep.dart:809,852` | Lyrics, not glass. |

Glass wrapper call sites (in-house `MornyeGlass*`, `MornyeSelectionPill`, `MornyeGlassPress`, `MornyeTabBar`, `MornyeSegmentedControl`, `MornyeFilterChip`): about 60 call sites across roughly 25 files. Principal consumers:
- Navigation and chrome: `lib/widgets/mornye_bottom_bar.dart:186,204`; `lib/widgets/mini_player.dart:356,358`; `lib/widgets/mornye_chrome.dart:16,50-56,90,141,163,330,494,546,576,632,672`.
- Overlays and sheets: `lib/widgets/app_alert_dialog.dart:95,195`; `lib/widgets/app_bottom_sheet.dart:402,501`; `lib/widgets/selection_bottom_bar.dart:342`; `lib/widgets/playlist_picker_sheet.dart:53`; `lib/widgets/song_analysis_sheet.dart:75`; `lib/widgets/mornye_player_details_sheet.dart:34`; `lib/widgets/mornye_context_menu.dart:541-559`.
- Settings: `lib/screens/settings/appearance_settings_page_widgets.dart:69,211`; `lib/screens/settings/playback_settings_page.dart:697,733`; `lib/widgets/settings_group.dart:274,909`; `lib/widgets/app_search_field.dart:121`; `lib/widgets/app_action_button.dart:80`.
- Playback and artist surfaces: `lib/widgets/album_detail_header.dart:578,580,919,958,961,1029`; `lib/widgets/profile_avatar.dart:107,109`; `lib/widgets/desktop/desktop_player_bar.dart:99`; `lib/widgets/desktop/desktop_scroll_arrows.dart:136,138`; `lib/screens/equalizer_widgets.dart:259`; `lib/widgets/audio_compare_view.dart:950`.

### 2.2 Classification (REPLACE / SIMPLIFY / KEEP / REMOVE)

Classifications are proposals for Phase 2. Items marked VERIFY need a source read or a visual check before a decision.

Glass core:

| File and symbol | Class | Justification |
|---|---|---|
| `lib/providers/runtime_profile_provider.dart` (`MornyeGlassLevel`, `:35-41`; `mornyeGlassLevelProvider`, `:46-54`; `backdropBlurEnabledProvider`, `:20-25`) | SIMPLIFY | The enum `flat/frosted/liquid` already exists and should be the single enum (plan section 4). It is currently derived from device tier, not a user choice. Split into selected mode (persisted) and effective renderer (device fallback). `forceBackdropBlur` must stop being a separate renderer override. |
| `lib/widgets/mornye_chrome.dart` `MornyeGlass` build (`:215-380`) and `MornyeGlassPanel` (`:89-160`) | SIMPLIFY | Holds the shared surface wrapper; it is the right place for surface presets. Remove clarity thresholds, clarity-dependent tint, sigma selection by clarity (`:281-298`), and the `BackdropFilter.grouped` path (`:348`). Keep the surface presets and the nesting guard (`_GlassBackdropScope`, `:383`). |
| `lib/widgets/mornye_chrome.dart` `_MornyeGlassRim` (`:391-490`) | REPLACE (VERIFY) | Custom rim painter. Replace with `LiquidGlassLight` / `LiquidGlassShadow` / `LiquidGlassLite` rim, then compare visually. |
| `lib/widgets/mornye_chrome.dart` `_blurSigmas` / `_blurs` (`:183-187`) | REMOVE | Replace with fixed presets (plan 10.3). |
| `lib/widgets/mornye_chrome.dart` `MornyeTabBar` (`:575-660`) | SIMPLIFY | Replace the `MornyeSelectionPill` path with `LiquidGlassTabBar`. Keep the layout and destinations adapter. `usesLiquidGlass` (`:606-615`) and `liquidInteraction` (`:637`) are clarity gates to remove. |
| `lib/widgets/mornye_liquid_backdrop.dart` (144 lines) | SIMPLIFY | Wraps `LiquidGlassLens`. Replace the refraction-index math (`:44-51`), clarity-derived dispersion (`:86`), and the UI-scale viewport workaround (`:52-56,96-143`, VERIFY whether still needed on 5.2.0) with direct `LiquidGlassStyle` values. |
| `lib/widgets/mornye_glass_press.dart` (221 lines) | REPLACE | Paint-only spring press with `_GlassLightPainter` (`:184`). Plan maps it to `LiquidGlassFlex` / `LiquidGlassTouch`. Keep the Reduce Motion and hit-target guarantees (its tests `test/mornye_glass_press_test.dart` cover them). |
| `lib/widgets/mornye_selection_pill.dart` (709 lines) | REPLACE and REMOVE | Custom selection pill, spring controllers (`:74-82`), shader loading (`:13-14,160`), shader mask (`:550-680`), held lens via `LiquidGlassBatch.exclude` (`:495-501`). Plan section 7: remove the pill and shader. Keep only layout and selection state adapter. |
| `assets/shaders/mornye_selection_mask.frag` | REMOVE | Plan section 7 and 12. Registration at `pubspec.yaml:147-148`. Referenced by `test/mornye_glass_interaction_test.dart:57,126,182,239` and `integration_test/mornye_liquid_glass_test.dart:50`, `integration_test/mornye_rendering_performance_test.dart:45`, `integration_test/mornye_glass_visual_test.dart:250`. Those assertions must be replaced, not deleted. |
| `lib/widgets/mornye_squircle_border.dart` (34 lines) | SIMPLIFY (VERIFY) | Matches the lens outline. If `LiquidGlassCornerStyle.squircle` plus `LiquidGlassShape` clip covers fill, shadow, clip and rim, the custom border can go. Used in 8 places. |
| `lib/widgets/mornye_bottom_bar.dart` (423 lines) | SIMPLIFY | Keep fold and layout orchestration (`:110-130`, `:296-340`). Replace `LiquidGlassBatch` / `BackdropGroup` (`:310,354,358`) with batch from the package; evaluate `LiquidGlassMorph` (`motion: plain`) for fold, and `LiquidGlassScaffold` only if it preserves the layout (plan 8). `BackdropGroup` is likely redundant with a batch (VERIFY). |
| `lib/widgets/mini_player.dart` (367 lines) | SIMPLIFY | Glass via `MornyeGlass.navigation` (`:356-358`). `ClipRRect` at `:260` (VERIFY). Keep playback, gestures and artwork layout. |
| `lib/widgets/mornye_glass_preparation.dart` (29 lines) | SIMPLIFY | Gated on `liquid && clarity > 0.2` (`:18-19`). Clarity gate to remove; the trigger should be the selected mode. |
| `lib/controllers/mornye_glass_warmup.dart` (135 lines) | SIMPLIFY (VERIFY, measure) | One-time offscreen rasterize of a `MornyeGlassPanel` at liquid level plus `LiquidGlassShaders.ensureLoaded()`. Plan 10.8: keep only if the detached raster is shown to help. Must be skipped in None and Frosted (VERIFY current Frosted behaviour). `toImage` is one-time (`:118`). |
| `lib/theme/mornye_theme.dart` `glassClarity`, `glassOutlines` (`:17-31`, `:132-217`, `:411-442`) | REMOVE `glassClarity`; decide `glassOutlines` | Clarity is the continuous value to remove. Outlines are a separate boolean; decide whether outlines survive as a per-mode decoration or are removed (see section 4.5). |
| `lib/widgets/mornye_context_menu.dart` (756 lines) | KEEP layout, REPLACE surface | No native menu exists (section 1.2), so the menu placement, route and render objects (`_MenuRoute` `:73`, `_MenuFrame` `:278`, `_MenuLayout` `:458`) are KEEP with justification. The glass is `MornyeGlassPanel.overlay` (`:541`) with tint `0.80 - 0.20 * glassClarity` (`:552`), which must go. `_LiquidMenuGlass` (`:149-277`) is a custom morph-like glass transition; SIMPLIFY or replace with `LiquidGlassMorph` (VERIFY). |
| `lib/widgets/app_alert_dialog.dart` (300 lines) | REPLACE glass, KEEP API | `AppDialogSurface` (`:70`) and `_MornyeDialogRoute` (`:103`) use `MornyeGlassPanel.overlay` (`:95,195`). Replace the surface with `LiquidGlassDialog` / `LiquidGlassAlertDialog`; keep `AppAlertDialog` as the app API (9 call sites). The opacity-layer note at `:140` must be re-checked. |
| `lib/widgets/app_bottom_sheet.dart` (presenters `:160-436`; glass `:402,501`) | REPLACE glass, KEEP presenters | 35 call sites use `showAppBottomSheet`, `showAppChoiceSheet`, `showAppScrollableBottomSheet`, `showAppModalBottomSheet`, `showAppSheetDialog`. Keep these as the app API and drive their surface with `LiquidGlassSheet` (or `showLiquidGlassSheet`). |
| `lib/widgets/selection_bottom_bar.dart` (`OverlayEntry` `:139-152`; glass `:342`) | KEEP mechanism, REPLACE glass | Overlay insertion is application logic. Glass surface to native. |
| `lib/widgets/song_analysis_sheet.dart:75`, `lib/widgets/playlist_picker_sheet.dart:53`, `lib/widgets/mornye_player_details_sheet.dart:34` | SIMPLIFY | Route through the shared surface preset, not local glass. |
| `lib/widgets/desktop/desktop_inspector.dart:232-233` | REPLACE | Fixed `BackdropFilter` sigma 60 is far above the plan's 2 to 3 presets. Use the presets or None (VERIFY visual on desktop). |
| `lib/widgets/desktop/desktop_player_bar.dart:99,731` | SIMPLIFY | Glass through presets. `MenuAnchor` at `:717` is Material and KEEP. |
| `lib/widgets/mornye_artist_header.dart` (558 lines; blur `:494-502`) | SIMPLIFY | Blur gated only on liquid level and reduced motion. It is not gated on active video (plan 11 requires that). Replace with a preset; add the video rule. |
| `lib/widgets/album_detail_header.dart` (1135 lines) | KEEP (glass sites VERIFY) | No backdrop or blur primitives. Glass wrappers at `:578-1029` need the surface preset migration. |
| `lib/widgets/mornye_motion_glow.dart` (257 lines) | KEEP, SIMPLIFY later | Artwork background, not a glass control. Its gate is `mornyeLiquidGlassProvider` (`:66`). The `ImageFilter.compose` path (`:151-162`) is an engine composed filter; measure before any change (Phase 8/9). |
| `lib/widgets/settled_blur.dart` (246), `lib/widgets/lyric_blur.dart` (256) | KEEP | Lyric defocus, not glass. Their caching (`toImageSync` once after settle, `:171`, `lyric_blur.dart:150`) is deliberate and justified by their docs. Plan 11 says avoid continuous text blur; these already do. |
| `lib/widgets/player_lyrics_word_sweep.dart` (`saveLayer`, `ImageFilter.blur`, `:809-852`) | KEEP (VERIFY) | Lyric rendering. |
| `lib/widgets/lyrics_size_setting.dart:295`, `lib/screens/lyrics_share_screen.dart:902` | KEEP | Preview and share card content effects, not glass. |
| `lib/main.dart` runtime profile and overrides (`:167-189`, `_RuntimeProfile` `:352-398`) | KEEP (rename and simplify) | Device fallback is required (plan section 4 "device fallback"). `deviceSupportsLiquidGlassProvider` and `deviceSupportsBackdropBlurProvider` map to effective-renderer inputs. |

Settings and persistence:

| File and symbol | Class | Justification |
|---|---|---|
| `lib/screens/settings/appearance_settings_page_widgets.dart` `_MornyeGlassClaritySlider` (`:3-~150`; `adjustable` gate `:18`; `Slider` divisions 20 at `:100`) | REMOVE | Replace with a three-option selector (Liquid, Frosted, None). Preview panel in the widget (`:69`) uses `MornyeGlassPanel`; keep it as the mode preview with the new renderer. |
| `lib/screens/settings/appearance_settings_page.dart:53` (clarity), `:57-62` (outlines switch), `:85-91` (force blur switch) | REMOVE clarity and force blur; decide outlines | Force-blur is a renderer override that conflicts with None (plan section 4 "None must never be overridden by force-blur settings"). Outlines: decide, see 4.5. |
| `lib/models/theme_settings.dart` (`kMornyeGlassClarityKey` `:25`, `kMornyeGlassOutlinesKey` `:26`, `kDefaultMornyeGlassClarity` `:27`, `normalizeMornyeGlassClarity` `:29-32`, field `:62`, json `:107,120`) | REMOVE clarity, add migration | Migration input must be read before removal (section 4.4). |
| `lib/providers/theme_provider.dart` (`loadBootstrapThemeSettings` `:86-100`; save `:152-163`; setters `:194-205`) | REMOVE clarity; keep outlines or remove | As above. |
| `lib/models/settings.dart` `forceBackdropBlur` (`:261,360,442,526`); `lib/models/settings.g.dart` (`:107,186`); `lib/providers/settings_provider.dart` (`setForceBackdropBlur` `:547-548`) | REMOVE (migrate) | Persisted in the `app_settings` JSON (`settings_provider.dart:14`). Map to the mode migration (section 4.4), then remove. |
| `lib/app.dart:261-270` | REMOVE clarity | Wires `mornyeGlassClarity` into `MornyeTheme`. |
| `lib/l10n/arb/app_en.arb:269-287` and 18 locale ARBs | REMOVE clarity keys; ADD three-mode keys | Keys `appearanceGlassClarity`, `appearanceGlassClaritySubtitle`, `appearanceGlassClearPerformance`, `appearanceGlassClarityBlurOff`, `appearanceGlassClarityHighContrast`, `appearanceGlassTinted`, `appearanceGlassClear`. Keys `appearanceForceBlur`, `appearanceForceBlurSubtitle` (en ARB) become unused. `appearanceGlassOutlines` and subtitle depend on the outlines decision. Generated `lib/l10n/app_localizations*.dart` must be regenerated, not hand edited. |
| `lib/utils/settings_search_catalog.dart:61-62` | UPDATE | Search entry for outlines; add entries for the mode selector. |
| `lib/screens/settings/log_screen.dart:12,135` | KEEP (rename log label) | Logs `mornyeGlassLevelProvider.name`. |

Tests and integration tests that encode the old behaviour (must be replaced, not weakened):
- Clarity-driven: `test/mornye_glass_level_test.dart` (625 lines; tests at `:47,145,175,233,282,346,421,456`), `test/appearance_settings_test.dart` (many `kMornyeGlassClarityKey` assertions, `:31-42,138-210,245-324`), `test/theme_provider_persistence_test.dart`, `test/theme_retired_prefs_test.dart`, `test/mornye_theme_identity_test.dart`, `test/mornye_context_menu_test.dart` (`:164` loops clarity 0, default, 1), `test/mornye_bottom_bar_test.dart`, `test/now_playing_lyrics_transition_test.dart`, `test/mornye_menu_motion_test.dart`.
- Shader-enforcing: `test/mornye_glass_interaction_test.dart` (selection shader, `:57-239`).
- Integration: `integration_test/mornye_glass_visual_test.dart:185-191` (clarity and outlines matrix), `integration_test/mornye_rendering_performance_test.dart:146,238-241` (clarity sweep; `MORNYE_GLASS_FOCUS` from `integration_test/README.md`), `integration_test/mornye_liquid_glass_test.dart:25,232`, `integration_test/mornye_menu_presentation_test.dart:31-35`.

### 2.3 Settings rows and renderer selection, summarized

- The current renderer tier is `mornyeGlassLevelProvider` (`lib/providers/runtime_profile_provider.dart:46-54`):
  - `liquid` if not low-end and `deviceSupportsLiquidGlass`;
  - else `liquid` if `backdropBlurEnabled` (device blur tier or user force-blur);
  - else `flat` if low-end, otherwise `frosted`.
- Startup tier from `lib/main.dart:309-337`: `low` on arm32-only or low-RAM Android; `high` at 6000 MB or more; otherwise `standard`. `_RuntimeProfile` (`:352-398`): `low` disables overscroll and blur, `standard` enables neither, `high` enables blur.
- Net effect, by platform:
  - Non-Android (iOS, desktop): `deviceSupportsLiquidGlass = true`, so liquid unless low-end (low-end is only set by the Android tier, so practically liquid).
  - Android standard: frosted (`deviceSupportsLiquidGlass` false and blur off).
  - Android high: liquid.
  - Android low: flat unless force-blur is on, which forces liquid.
- High-contrast requests block glass unless force-blur is on (`mornyeGlassContrastBlockedProvider`, `runtime_profile_provider.dart:28-32`).

---

## 3. Recursive inventory of overlays, routes and settings

Full per-call-site list: scratch file `/tmp/claude-0/-home-user-Sistrum/9d293f2a-980b-503e-90a8-6a595050bc28/scratchpad/inv.txt` (84 lines, produced by `scratchpad/inv.py`). Summary by category:

### 3.1 Routes

- GoRouter is shallow. `lib/app.dart:53-73` registers only `/`, `/setup`, `/tutorial`, plus an error builder that renders `MainShell`. No glass is in the routes themselves.
- Most navigation is imperative `Navigator` or `MaterialPageRoute`/`PageRouteBuilder`:
  - Main shell and tabs: `lib/screens/main_shell.dart:224,461,505,912`.
  - Settings detail navigator: `lib/screens/settings/settings_tab.dart:451-454` (inner `Navigator` with `PageRouteBuilder`, zero transition duration). Detail pages `:160-244`: Appearance, Playback, Lyrics, Library, Keyboard shortcuts, Cache management, Backup and restore, Log, About (nine `pageBuilder` entries), plus the profile page (`:356-362`, `:717`). That is ten pages.
  - Queue and library: `lib/screens/queue_tab_navigation.dart:114,172,178,184`.
  - Player: `lib/widgets/mini_player.dart:118` (`rootNavigator` push), `lib/screens/listening_rewind_screen.dart:87-88`, `lib/screens/lyrics_share_screen.dart:102-103`.
  - Helpers: `lib/widgets/animation_utils.dart:109` (`StaggeredListItem` route builder); `lib/controllers/clickable_metadata.dart:117,145`.

### 3.2 Overlays, menus, sheets and dialogs

| Component | Source (file:class) | Entry point | Current renderer |
|---|---|---|---|
| Mornye context menu (general) | `lib/widgets/mornye_context_menu.dart:18` `showMornyeContextMenu`, `:587` `MornyeContextMenu`, `:73` `_MenuRoute` | `lib/widgets/song_menu.dart:52,89`; `lib/screens/now_playing_screen.dart:1027,1095`; `lib/widgets/lyrics_options_button.dart:269-274`; `lib/widgets/desktop/context_menu.dart:59-67`; `lib/widgets/desktop/desktop_now_playing.dart:583-588`; `lib/widgets/mornye_player_actions_sheet.dart:22,81` | In-house `MornyeGlassPanel.overlay` plus `_LiquidMenuGlass` |
| Song menu | `lib/widgets/song_menu.dart:29` `showSongMenu` | About 8 call sites: `collection_detail_screen.dart:702,805`; `local_album_screen.dart:190`; `library_directory_view.dart:186,285`; `library_search_results.dart:304`; `queue_tab_navigation.dart:105`; `desktop/track_list_table.dart:168` | Context menu (above) |
| Playlist and selection menus | `lib/widgets/collection_track_sort_menu.dart`; `lib/widgets/album_track_sort_menu.dart:48` (`showAppScrollableBottomSheet`) | | Sheet, in-house glass |
| Lyrics options menu | `lib/widgets/lyrics_options_button.dart:174,219-274` | Button | Context menu |
| Player navigation menu | `lib/widgets/mornye_player_actions_sheet.dart:13,49,204` | `mornye_player_layout.dart:577` (`mornyeMenuAnchor`) | Context menu, in-house glass |
| Desktop volume menu | `lib/widgets/desktop/desktop_player_bar.dart`, `desktop_player_bar.dart:717` `MenuAnchor` | Button | Material `MenuAnchor` (no glass) |
| App popup menu | `lib/widgets/app_menu_button.dart:44` `PopupMenuButton` | Various | Material (no glass) |
| Dropdown | `DropdownButton` not found in lib/ | | |
| App alert dialog | `lib/widgets/app_alert_dialog.dart:161` `AppAlertDialog`, `:70` `AppDialogSurface`, `:103` `_MornyeDialogRoute` | 9 call sites: `controllers/song_cue_split.dart:244`; `screens/equalizer_screen.dart:164`; `screens/equalizer_widgets.dart:613`; `screens/setup_screen.dart:344`; `widgets/batch_progress_dialog.dart:122`; `widgets/playlist_picker_sheet.dart:395`; `widgets/app_alert_dialog.dart:47` | In-house `MornyeGlassPanel.overlay` |
| Bottom sheets (presenters) | `lib/widgets/app_bottom_sheet.dart:160,289,321,362,436` | 35 call sites, including `controllers/song_file_actions.dart:161`; `screens/now_playing_screen.dart:1175`; `screens/listening_rewind_screen.dart:59`; `screens/equalizer_screen.dart:107`; `screens/settings/playback_settings_page.dart:800`; `screens/settings/lyrics_settings_page.dart:121`; `screens/settings/library_settings_page.dart:308`; `screens/settings/library_settings_page_widgets.dart:60,122`; `screens/settings/appearance_settings_page_widgets.dart:336`; `screens/settings/log_screen.dart:268`; `screens/setup_screen.dart:453`; `widgets/desktop/desktop_window_controls.dart:23`; `widgets/app_crash_banner.dart:138`; `widgets/audio_compare_view.dart:103`; `widgets/automix_settings.dart:28`; `widgets/lyrics_size_setting.dart:221`; `widgets/playback_tempo_sheet.dart:90`; `widgets/duplicate_review_sheet.dart:34`; `widgets/create_playlist_sheet.dart:30,98`; `widgets/audio_info_sheet.dart:34`; `widgets/lyrics_editor_sheet.dart:52`; `widgets/ios_library_folder_sheet.dart:13`; `widgets/playlist_picker_sheet.dart:40`; `screens/track_metadata_edit_sheet.dart:154` (`DraggableScrollableSheet`) | In-house `MornyeGlassPanel.overlay` at `app_bottom_sheet.dart:402,501` |
| Raw `showModalBottomSheet` | `lib/widgets/player_details_sheet.dart:42`; `lib/widgets/song_analysis_sheet.dart:42` | Player details; song analysis | `MornyeGlass.navigation` at `song_analysis_sheet.dart:75`; player details glass via its own content (VERIFY) |
| `showSearch`, date and time pickers, Material menus | Not found in lib/ | | |
| Selection overlay | `lib/widgets/selection_bottom_bar.dart:106,139-152` `SelectionOverlayController` (`OverlayEntry`) | Selection state | `MornyeGlassPanel.overlay` at `:342` |
| Settings choice sheets | `showAppChoiceSheet` callers listed above | Settings pages | In-house glass |
| Settings category list and detail | `lib/screens/settings/settings_tab.dart:160-244` | Category tiles | Inner navigator; glass via `settings_group.dart:274,909` |
| Settings group surfaces | `lib/widgets/settings_group.dart:274` (`samplesBackdrop: false`), `:909` (`overlay`) | Every settings page | In-house glass |
| Mini player, bottom bar, tab bar | `lib/widgets/mini_player.dart:356-358`; `lib/widgets/mornye_bottom_bar.dart:186-210,354-358` | Main shell | In-house glass plus `LiquidGlassBatch` |
| Full-screen player / landscape | `lib/widgets/mornye_landscape_player.dart` (452 lines), `lib/widgets/mornye_player_details_sheet.dart` | | Not individually traced in this phase (VERIFY in Phase 2) |
| Desktop inspector | `lib/widgets/desktop/desktop_inspector.dart:232-233` | Desktop panel | Plain `BackdropFilter` sigma 60 |

Totals from the scratch inventory: 84 call-site lines across overlay, navigation and presenter primitives. Items marked VERIFY need a read of the enclosing widget.

### 3.3 Mode propagation

- Clarity reaches surfaces via `MornyeTheme.glassClarityOf(context)` (theme extension, `lib/theme/mornye_theme.dart:26-28`), so already-mounted overlays read it through `Theme.of`. This is a theme-level value, so a change propagates through the theme.
- Renderer level reaches surfaces via `ref.watch(mornyeGlassLevelProvider)` in `MornyeGlass` (`lib/widgets/mornye_chrome.dart:218`). Also a provider, so it updates mounted widgets.
- Stale state risk to check in Phase 2: `MornyeTheme.extension` is cached (`mornye_theme.dart:442` hashes clarity), and the warm-up overrides the level to liquid (`lib/controllers/mornye_glass_warmup.dart:79`).

---

## 4. Current settings semantics and migration design

### 4.1 Clarity: values, default, range

- Key `mornye_glass_clarity` (`lib/models/theme_settings.dart:25`); default `0.75` (`:27`); normalization clamps to `[0, 1]` and maps null or non-finite to the default (`:29-32`).
- Slider: `divisions: 20`, label `round(clarity * 100)%` (`lib/screens/settings/appearance_settings_page_widgets.dart:100-102`). Disabled when `!adjustable` where `adjustable = !contrastBlocked && mornyeBlurEnabledProvider` (`:17-19`).
- Stored as `double` in SharedPreferences (`theme_provider.dart:157`).

### 4.2 Clarity to rendering (current, shader-supported platform)

Source: `lib/widgets/mornye_chrome.dart` `MornyeGlass.build`.

| Input | Rule | Evidence |
|---|---|---|
| Glass present | `translucent` iff `reveal > 0 && blurEnabled && level != flat && clarity > 0 && !contrastBlocked` | `:218-227` |
| Tint strength | `tintStrength = (1 - clarity)^2`, blended into the tint | `:270` |
| Sampling threshold | sample if `translucent && samplesBackdrop && !insideGlass && clarity > blurThreshold`, where `blurThreshold = 0.5` for frosted and `0.2` for liquid | `:281-285` |
| Lens path (liquid) | lens iff `sampleBackdrop && firstInGroup && lastInGroup && level == liquid && shader supported` | `:287-293` |
| Blur table index | frosted: index 1. Liquid: `clarity >= 0.9` gives 0; `>= 0.6` gives 2; otherwise 3 | `:295-299` |
| Sigma table | `[2, 3, 6, 16]` | `:183` |
| Lens sigma | `_blurSigmas[filterIndex]`, passed as `blurSigma` | `:333` |
| Non-lens sampling | `BackdropFilter.grouped(filter: _blurs[filterIndex])` | `:348` |
| Clear fill | when lens and not strong tint: fill lerped toward a light fill by `(clarity - 0.2) / 0.8` | `:302-310` |
| Lens dispersion | `1.32 * clarity * effect` | `lib/widgets/mornye_liquid_backdrop.dart:86` |
| Outlines | `outlines` gates specular thickness and glow intensity | `mornye_liquid_backdrop.dart:69-80`, rim painter `mornye_chrome.dart:330-337` |
| Tab bar floating | `clarity > 0` and liquid | `mornye_chrome.dart:614-618` |
| Tab bar press lens | `clarity > 0.2` and liquid and not desktop | `mornye_chrome.dart:637` |
| Warm-up | liquid and `clarity > 0.2` | `mornye_glass_preparation.dart:18-19` |
| Context menu tint (dark) | `0.80 - 0.20 * clarity` | `mornye_context_menu.dart:552` |
| Selection pill lens | `liquidInteraction && shader supported && !reduce motion && !contrastBlocked` | `mornye_selection_pill.dart:241-246` |

Behaviour, worked through at the defaults:
- Default `0.75` on liquid: lens sigma 6 (index 2), sampling on, dispersion `0.99`, tint strength `0.0625`.
- Note the inversion: lower clarity gives more blur (sigma 16 below `0.6`) and more tint (`(1 - c)^2`). The label "clarity" is the inverse of blur strength in the 0.2 to 0.6 band.
- At `clarity <= 0.2` on liquid: translucent tint, no sampling (no lens, no backdrop pass).
- At `clarity == 0`: opaque surfaces, no glass path at all.
- Frosted tier at default `0.75`: `BackdropFilter.grouped` sigma 3 (index 1); sampling on because `0.75 > 0.5`. Frosted at `clarity <= 0.5`: no sampling, tinted fill only.
- Flat tier: no translucency regardless of clarity.

Note on frosted: the plan's Frosted target is `LiquidGlassLite`. `LiquidGlassLite` does one backdrop read for frost (`pkg:CHANGELOG.md:51`), so Frosted via LiquidGlassLite is not backdrop-free. Frosted's current path is a `BackdropFilter` plus fill, which is a comparable cost. Confirm cost in Phase 8.

### 4.3 Outlines

- Key `mornye_glass_outlines` (`theme_settings.dart:26`), default `true` (`:72`).
- Label `appearanceGlassOutlines` with subtitle "Show decorative edges around Mornye glass. Turning this off keeps blur and refraction." (`app_en.arb:270-271`). The subtitle itself states that outlines are decorative and independent of blur and refraction, so it does not depend on the clarity axis.
- Read via `MornyeTheme.glassOutlinesOf` (`mornye_theme.dart:30-31`) in `mornye_liquid_backdrop.dart:40`, `mornye_chrome.dart:231`, `mornye_selection_pill.dart:353,516`.

### 4.4 Force-blur and migration inputs

- `AppSettings.forceBackdropBlur` (`lib/models/settings.dart:261`), default false, persisted in JSON `app_settings` (`lib/providers/settings_provider.dart:14,86`).
- Effect: `backdropBlurEnabledProvider` becomes true (`runtime_profile_provider.dart:20-22`), which forces `liquid` even on low-end devices (`:46-53`). It also overrides high-contrast blocking (`:28-32`).
- Plan section 4 requires None never be overridden by force-blur. Under the new model force-blur should not exist as a separate axis.

### 4.5 Proposed deterministic migration (for Phase 2 to finalize)

Inputs read from storage:
- `mornye_glass_clarity` (double, default 0.75 if absent or invalid).
- `mornye_glass_outlines` (bool, default true).
- `app_settings.forceBackdropBlur` (bool, default false).

Proposed rule for the persisted selected mode (idempotent, run only when the new key is absent):
1. If clarity is exactly `0`: `none`.
2. Else if clarity `< 0.5`: `frosted`. Rationale: below 0.5 the tint is at least `0.25` strength (`(1-c)^2 >= 0.25`), and the existing frosted sampling threshold is 0.5. This is the "predominantly tinted" band.
3. Else: `liquid`. Covers the default 0.75 and clear settings.
4. `forceBackdropBlur` is folded in and then removed. If true and the mode computed above is `none`, keep `none`, because None must not be overridden. If true and mode is `frosted` or `liquid`, keep it unchanged; the device fallback applies at render time.

Caveats:
- The threshold `0.5` is a proposal aligned with existing code boundaries, not a value from the owner. Phase 2 needs owner sign-off or a measured rule.
- Migration must map the persisted preference, not the effective renderer. Current effective renderer is device-dependent (section 2.3); the migration must not move an Android-standard user from frosted to liquid because of platform.
- Outlines: keep as a separate boolean if the owner wants it, or drop it. The plan does not mention outlines explicitly; section 4.3 shows they are decorative and independent. Decision needed.
- Backup and restore: `lib/screens/settings/backup_restore_page.dart:44-46,55` exports only `ref.read(settingsProvider).toJson()`, which includes `forceBackdropBlur` but not `mornye_glass_clarity` or `mornye_glass_outlines` (those live in `themeProvider`, `lib/providers/theme_provider.dart`). Current clarity and outlines do not round-trip through backup. The new mode key should be added to whichever store is chosen, and the owner should decide whether it is backed up.
- Tests `test/theme_retired_prefs_test.dart` show the project's pattern for one-time key removal (`kRetiredThemePrefsRemovedKey`); the clarity migration should follow it.

### 4.6 Other blur settings that are not glass

- `lyrics_blur` (`kLyricsBlurKey = 'lyrics_blur'`, `lib/models/theme_settings.dart:10`, default 1) controls lyric defocus, not Mornye glass. It is outside this migration; the plan's Phase 9 "lyrics" applies.

---

## 5. Baseline and performance infrastructure

### 5.1 Existing infrastructure

Host-runnable (`flutter test`, no device):
- `test/performance_frame_budget_test.dart`: 60, 90, 120 Hz budget (16.67, 11.11, 8.33 ms), two tests.
- `test/mornye_glass_level_test.dart` (625 lines): runtime tier mapping (`:145`), frosted drops shaders (`:175`), clear glass reduces blur (`:233`), tab and segment clarity (`:282`), plain-page panels without a backdrop pass (`:346`), one blur and no blur on switches (`:421`), every lens composes into one batch read (`:456`). Clarity-specific tests will be obsolete under the new model; the batch-read and shader-free frosted tests remain relevant.
- `test/mornye_glass_press_test.dart`, `test/mornye_glass_interaction_test.dart` (571 lines; includes shader assertions), `test/mornye_glass_layout_test.dart`, `test/mornye_search_glass_test.dart`, `test/mornye_motion_glow_test.dart`, `test/lyric_blur_test.dart`, `test/mornye_menu_motion_test.dart`, `test/mornye_context_menu_test.dart`, `test/mornye_bottom_bar_test.dart`, `test/selection_overlay_lifecycle_test.dart`, `test/selection_bottom_bar_drag_test.dart`.

Device-only (`integration_test/`, need an Android emulator or device; per `integration_test/README.md`, CI analyzes but does not run them):
- `mornye_rendering_performance_test.dart` (351 lines): clarity sweep, `glass_clarity` metric (`:146`), `MORNYE_GLASS_FOCUS` define (clarity 0.6 and 1.0 only).
- `mornye_performance_test.dart` (13 lines): aggregator.
- `mornye_screens_performance_test.dart` (296 lines): production screens with 2000 tracks.
- `mornye_artwork_performance_test.dart` (196 lines), `mornye_lyrics_performance_test.dart` (247 lines), `material_performance_test.dart`, `player_performance_test.dart`.
- `mornye_glass_visual_test.dart` (436 lines): clarity and outlines visual matrix (`:185-191`).
- `mornye_liquid_glass_test.dart` (407 lines), `mornye_menu_presentation_test.dart` (197 lines).
- Performance driver: `integration_test/performance_driver.dart`, with `performance_probe.dart` (records frame budgets, `integration_test/README.md`). Report saved via `SISTRUM_PERFORMANCE_REPORT`.

### 5.2 What can and cannot run here

Can run in principle (not executed in Phase 1, because this phase is read-only and running `flutter test` writes build caches and may run `pub get`):
- `flutter analyze` and `dart format --set-exit-if-changed` (plan section 16).
- Host `flutter test` for the glass-related suites above.

Cannot run in this container, and therefore cannot produce these measurements:
- Any `integration_test` suite (no device, no emulator, no `adb`).
- Frame timings, jank, raster P50/P95/P99, backdrop reads or copies, GPU passes, GPU memory, RAM, idle frame scheduling, Perfetto traces, cold or warm shader preparation, animated artwork or video playback, Bottom Bar morph performance, dialog, sheet or menu open timing, mode switching cost.
- Visual baselines on a real GPU (no `/dev/dri`). Host tests cannot run shader filters; `MornyeGlass.debugShaderFilterSupport` (`mornye_chrome.dart:189-191`) exists only to build the lens on host.
- Impeller or Skia behaviour on real hardware. Host tests run on the Flutter test engine, which does not represent Impeller.

No baseline numbers are recorded in this audit. Per the plan, none are fabricated.

---

## 6. Limitations and unverified items

- Package behaviour verified by reading source and docs only. No runtime execution of `liquid_glass_easy` in this container.
- Efficiency guide and batch page content were read through WebFetch summarization. Quotes in 1.3 are from that summary plus CHANGELOG and source comments. The exact Flutter issue behind "single pass bug" is not identified.
- `lib/widgets/mornye_landscape_player.dart`, `lib/widgets/mornye_player_details_sheet.dart`, `lib/widgets/album_detail_header.dart` (1135 lines), `lib/widgets/mornye_artist_header.dart` were inventoried by grep, not read in full. Their glass classifications are marked VERIFY.
- Settings pages other than appearance were not read line by line; their glass sites come from grep (section 2.1).
- Whether the UI-scale viewport workaround in `mornye_liquid_backdrop.dart:52-56,96-143` is still needed on 5.2.0 is not verified.
- `material_ui` (dependency `pubspec.yaml:49`) provides `MornyeGlassPanel`'s building blocks through its own API. Its internals were not inspected.
- The package's CHANGELOG 5.2.0 says the tab bar moved and the slider width changed; visual parity with current Mornye design is untested.
- Recursive traversal is complete for routes, `Navigator`, `showModalBottomSheet`, `showDialog`, `OverlayEntry`, and `Menu` helpers by grep. Dynamically built widgets that open overlays through indirect helpers may still be missed. Plan 12.1 requires that to be closed in Phase 2.
- No independent reviewer was run. TOR006 review is pending (task record).

---

## 7. Summary and proposed ordered implementation checklist

### 7.1 Findings

1. The package does not provide what the plan's table assumes for menus, segmented controls, dock, app icon, samplesBackdrop and warm-up (section 1.2). Menus remain custom with native glass surfaces underneath.
2. Sistrum uses almost none of the package's components. Only `LiquidGlassLens` and `LiquidGlassBatch` (plus `LiquidGlassStyle`, `LiquidGlassShape`, `LiquidGlassLight`) are used. The remaining work is a surface and renderer swap, not an API migration for most sites.
3. The existing `MornyeGlassLevel` enum (`runtime_profile_provider.dart:35-41`) already matches the target three modes. It is derived from device tier, not user choice. Phase 2 must split selected and effective.
4. Clarity is an inverse-blur axis with a non-monotonic blur schedule (sigma 2, 6, 16 across 0.9, 0.6 bands) and several gates at 0, 0.2, 0.5 and 0.6. Migration rules must reproduce the persisted intent, not the device behaviour.
5. Force-blur overrides both low-end device fallback and high-contrast blocking. That conflicts with the plan's requirement that None is never overridden.
6. Backup/restore carries force-blur but not clarity or outlines (`backup_restore_page.dart:52-55`). The owner must decide whether the new mode is backed up.
7. Selection pill shader and its registrations are referenced by source, tests and integration tests. Removing them touches 8 test files.
8. `ImageFilter.compose` in app code is limited to `mornye_motion_glow.dart:151`. The package's composed-blur risk applies to `singlePass` batches only, which Sistrum does not use today.
9. Artist header blur is gated on level and reduced motion, not active video (`mornye_artist_header.dart:494-502`). Plan 11 requires the video rule.
10. The desktop inspector uses a fixed sigma 60 backdrop (`desktop_inspector.dart:233`), well outside the plan's presets.
11. There is no device or GPU in this container. No performance measurement is possible here.

### 7.2 Proposed ordered checklist (Phase 2 onward)

1. Owner decisions: mode migration threshold (proposed 0.5), fate of outlines, whether the mode is backed up, and whether force-blur is retired.
2. Define the single policy: one selected-mode provider (persisted), one effective-renderer provider (device fallback), one `GlassPreset` table (sigma, tint, radius per surface category), and no clarity anywhere.
3. Add the persisted three-mode key, idempotent migration from `mornye_glass_clarity` and `forceBackdropBlur`, and a one-time removal of the old keys following the `theme_retired_prefs` pattern. Add tests for migration and idempotence.
4. Replace `MornyeGlass` and `MornyeGlassPanel` internals with the policy: Liquid uses `LiquidGlassLens` in a `LiquidGlassBatch` (default `separatePass`, or `batchBlurMethod = shader` after measurement); Frosted uses `LiquidGlassLite`; None uses plain `Container`/`DecoratedBox` with no backdrop scope.
5. Replace the three in-house sheet, dialog and menu surfaces with `LiquidGlassSheet`, `LiquidGlassDialog` and `LiquidGlassMorph`/`LiquidGlassLens`. Keep `AppAlertDialog`, `showAppBottomSheet*` and `showMornyeContextMenu` as the app APIs.
6. Replace `MornyeTabBar` and `MornyeBottomBar` selection and press with `LiquidGlassTabBar` and `LiquidGlassFlex`/`LiquidGlassTouch`. Evaluate `LiquidGlassMorph` (`LiquidGlassMorphMotion.plain`) for the fold. Decide on `LiquidGlassScaffold` only after verifying layout parity.
7. Remove `mornye_selection_pill.dart`, `assets/shaders/mornye_selection_mask.frag`, its `pubspec.yaml` entry and the shader-based tests. Replace with native pill tests.
8. Remove `_MornyeGlassRim`, `_blurs` tables and `MornyeSquircleBorder` only after visual comparison in all three modes.
9. Gate artist header blur on the selected mode and active video (plan 11). Replace the desktop inspector sigma 60 with a preset or None.
10. Remove the clarity slider, `mornyeGlassClarity` from `ThemeSettings`, `MornyeTheme`, `app.dart`, providers, ARB keys (all 18 locales plus generated files), and settings search catalog.
11. Retire `forceBackdropBlur` from `AppSettings` and its settings row, after migration.
12. Warm-up: keep only when the selected mode is Liquid, and measure before keeping it at all (plan 10.8).
13. Verification on host: `dart format`, `flutter analyze`, host `flutter test` for affected suites.
14. Verification on device (not available in this container): the integration suites listed in 5.1, in all three modes, on a physical device or dedicated emulator, with `--profile` and the performance driver. Record missing measurements explicitly.
15. Documentation: update `AGENTS.md` and architecture docs with the package-first rule and the single glass policy (plan 16).
