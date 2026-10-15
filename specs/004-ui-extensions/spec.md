# Feature Specification: UI overhaul and chosen integrations (004, UI part)

**Feature Branch**: `feature/media3-migration` (owner, D-2; no new branch).
**Created**: 2026-10-15
**Status**: Draft
**Input**: `OWNER-PROMPT-2026-10-15.md` (master prompt), `NOTES.md` (owner corrections override the prompt), `PHASE0-REPORT.md`,
`docs/ui-overhaul/00..09` (Phase 0 audit and U-xxx task list), owner scope decision 2026-10-15 (D-1).

Governing: constitution Principle 2 (PlaybackController contract), 3 (no feature loss), 4 (local playback only), 10 (all users,
not the owner's library or device). CLAUDE.md: English + Arabic only, Western digits, RTL rules, `devQa` test build, never the
owner's daily apps.

## Context

004 has two parts. This spec covers the **UI part**: a new look (tokens, glass, individual screens, navigation, mini-player,
three full-screen player styles, lyrics screen, settings) plus the integrations the owner chose (Wikipedia artist info, Deezer
artist images, animated album covers, all behind Settings → Integrations). The second part (Kotlin decoders for analysis,
formerly M4b) comes later as its own spec/plan. The UI talks to `PlaybackController` only; nothing under `player/` changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One consistent look in every theme (Priority: P1)
Every screen draws colors, spacing, corner shapes and glass surfaces from shared design tokens. Light, Dark and System themes
work. With Reduce Transparency on, glass surfaces become plain opaque ones that stay readable.

**Why this priority**: all other screens build on it. **Independent Test**: token and glass tests pass; contrast checks pass for
Light/Dark with and without Reduce Transparency, English and Arabic.

**Acceptance Scenarios**:
1. **Given** Reduce Transparency is on, **When** any glass surface is shown, **Then** no blur layer is composed and text keeps at
   least 4.5:1 contrast against its background.
2. **Given** the system theme changes while the app is open, **When** the screen recomposes, **Then** tokens switch with no
   restart and no unreadable text.
3. **Given** Reduced Motion is on at system level (animation scale 0), **When** screens animate, **Then** looping or large
   animations are replaced by static states.

### User Story 2 - The library looks new, works the same (Priority: P1)
Home, Library, Album, Artist, Playlist and Search screens get the new layout. Every action that exists today still exists:
play, shuffle, queue, favorites, context menus, sorting, filters, genres, composers, statistics, manual artwork, playlist
create/rename/delete/reorder, search.

**Independent Test**: each screen has a Compose test for content, empty, loading and error states and for its actions; the
existing screen tests pass unchanged.

**Acceptance Scenarios**:
1. **Given** an empty library, **When** Home or Library opens, **Then** the choose-folders / empty state is shown and no remote
   feed is requested.
2. **Given** a large library, **When** a list is scrolled, **Then** it stays virtualized (items composed stay bounded) and
   restores its scroll position after back navigation.
3. **Given** font scale 2.0 or a long Arabic name, **When** a screen is shown, **Then** primary actions are not clipped.

### User Story 3 - Navigation and mini-player keep their behavior (Priority: P1)
The floating navigation still collapses into the circle, the mini-player still moves next to it, destinations restore their
state and back works as before; only the look changes.

**Independent Test**: characterization tests written before any change (T001, T002) pass unmodified at every milestone end.

**Acceptance Scenarios**:
1. **Given** a list is scrolled down, **When** the navigation collapses, **Then** it becomes the circle and the mini-player moves
   beside it, exactly as before.
2. **Given** RTL language, **When** the mini-player is dragged to skip, **Then** direction is correct (no double mirroring).

### User Story 4 - Three player styles, DEFAULT stays (Priority: P1)
Settings → Appearance offers DEFAULT (today's player, selected by default, unchanged), LARGE and IMMERSIVE. The choice is
stored and survives restart. A Hide Volume Bar setting (default off) works in all three styles.

**Independent Test**: parity test: every style exposes the same commands; DEFAULT characterization tests pass unmodified.

**Acceptance Scenarios**:
1. **Given** a fresh install or an unknown stored value, **When** the player opens, **Then** DEFAULT is shown.
2. **Given** playback is running, **When** the style is changed, **Then** position, queue and audio are untouched (no player
   rebuild).
3. **Given** Hide Volume Bar is on, **When** any style is shown, **Then** the on-screen volume bar is hidden and hardware volume
   keys still work.

### User Story 5 - Optional artist info, artist images and animated covers (Priority: P2)
Artists show the cover of their newest song as their photo by default (D-19). Settings → Integrations lists Wikipedia artist
info (ON by default), Deezer artist images and animated album covers (both OFF by default), each with its own switch. When on, artist pages show a portrait and a short attributed About text; animated covers play silently in
LARGE and IMMERSIVE and on artist headers. A Wi-Fi only / Wi-Fi and cellular choice applies to animated covers. When a lookup
fails or is ambiguous the app shows what it had before (no wrong or invented picture or text).

**Independent Test**: provider tests with fake HTTP responses (no live network in any test); isolation test: music keeps
playing, position advances and no audio focus change while an animated cover with an audio track plays.

**Acceptance Scenarios**:
1. **Given** all three switches off, **When** the app runs, **Then** it makes no network request for these features.
2. **Given** Deezer images on and a manual artist image set, **When** the artist screen opens, **Then** the manual image wins.
3. **Given** Wikipedia on and a name that matches a disambiguation page, **When** About is requested, **Then** nothing is shown
   (no text for a wrong artist).
4. **Given** animated covers on, Wi-Fi only, device on cellular, **When** the player opens, **Then** the static cover is shown.
5. **Given** an animated cover is playing, **When** the app goes to the background or Reduced Motion is on, **Then** the video
   stops and its player is released; music is unaffected.

### User Story 6 - Lyrics screen (Priority: P2)
The lyrics view gets a legible album-color backdrop and stronger current-line emphasis; tap-to-seek, browse mode, offset and
bilingual lines work as today.

**Acceptance Scenarios**:
1. **Given** synced lyrics, **When** the song plays, **Then** the current line is emphasized and neighbors de-emphasized with
   text contrast at least 4.5:1 on bright and dark artwork.
2. **Given** Reduced Motion, **When** the line changes, **Then** the view jumps without animated scroll or blur.

### User Story 7 - Accessible and localized (Priority: P2)
Every interactive element has a 48 dp target and a spoken label; every new string exists in English and Arabic; digits are
Western; transport controls, seek bar and volume slider stay left-to-right in RTL.

**Acceptance Scenarios**:
1. **Given** the audit test, **When** it walks all screens and the three player styles, **Then** it finds no icon-only control
   without a label and no target under 48 dp.
2. **Given** Arabic, **When** new strings are added, **Then** `ArabicTranslationCompletenessTest` passes.

### Edge Cases
- Stored player style unknown (old/newer build) -> DEFAULT.
- Artist name shared by several artists, or featured-artist strings ("A feat. B"): matching uses the library artist entity;
  ambiguous result -> nothing shown, never a guess.
- Deezer returns a placeholder silhouette image: treated as no result.
- Apple web token cannot be read, or the service refuses: motion stays off for the session with a 10 minute retry back-off; no
  built-in fallback token (D-9).
- Motion video has an audio track, requests focus or fails to decode: music is unaffected, static cover stays.
- Device without Haze support or very slow frames: opaque fallback.
- Keyboard open / landscape / display cutout: nav and mini-player respect insets.
- The same switch turned off while a request is in flight: late result is dropped.
- Cellular data off, airplane mode, HTTP 4xx/5xx, timeouts, oversized responses, redirect to http://: typed failure, static UI.

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: Semantic design tokens (spacing 4/8/12/16/24/32 dp, corner shapes, colors for page, glass tint, opaque fill, scrim,
  text, muted text, accent, divider, disabled, focus ring) MUST exist for Light, Dark and System, derived from the existing
  Sistrum theme.
- **FR-002**: Glass MUST go through one policy (Reduce Transparency, performance fallback, Haze availability); with it off or
  unavailable, surfaces are opaque and no blur is composed. No new backdrop library is added.
- **FR-003**: Home, Library overview and lists, Album, Artist, Playlist and Search MUST use the tokens and
  keep every existing action and state (D-4).
- **FR-004**: Home MUST show only local data (recent, quick picks, recently added, existing statistics sections); no remote feed.
- **FR-005**: Floating navigation and the mini-player MUST keep collapse, circle state, paired relocation, state restoration and
  back order; geometry comes from one source and respects insets and keyboard.
- **FR-006**: Player style MUST be a stored enum `DEFAULT | LARGE | IMMERSIVE` (stable strings in DataStore); missing or unknown
  = DEFAULT; DEFAULT is today's player unchanged; no separate full-screen-cover toggle.
- **FR-007**: LARGE (floating large square cover, bottom transport) and IMMERSIVE (full-cover art, scrim, bottom controls) MUST
  expose every command and panel DEFAULT has (transport, seek, queue, lyrics, favorite, sleep/other actions present today).
- **FR-008**: Hide Volume Bar MUST be a stored setting, default false, applied to all three styles; hardware volume unaffected.
- **FR-009**: Lyrics UI MUST add emphasis and backdrop without changing the lyrics parser, offset or bilingual handling; Reduced
  Motion removes animated scrolling and blur.
- **FR-010**: (removed by the owner, 2026-10-15, D-20: no Library Settings page and no restyle of the existing Settings pages;
  Settings changes are only the new Integrations rows (FR-011), the Appearance rows (FR-006, FR-008) and Exhale's glass toggle
  replacing every switch (D-21).)
- **FR-011**: Settings → Integrations MUST list Wikipedia artist info, Deezer artist images and animated covers with a switch
  each (defaults: Wikipedia ON, Deezer OFF, animated covers OFF; D-10) and, for animated covers, a Wi-Fi only / Wi-Fi and cellular choice (default Wi-Fi only). No warning text.
- **FR-012**: Integration network calls MUST use one HTTPS-only fetcher with timeouts, size limits, no redirect to http, request
  cancellation, and an injectable interface so tests never touch the network.
- **FR-013**: Artist portrait priority MUST be: manual image, then Deezer (if on; cached), then the cover of the artist's newest
  song (D-19), then the placeholder. Wrong or
  ambiguous matches MUST yield no image.
- **FR-014**: About text MUST come from the Wikipedia of the app language (Arabic app: Arabic first, then English once; D-11)
  and only for a confident match, show the page title, link and license
  (CC BY-SA) attribution, and be cached with its revision/date.
- **FR-015**: Integration results MUST be cached on disk (cacheDir, bounded, with time-to-live and a short negative cache); the
  Room schema MUST NOT change (D-8).
- **FR-016**: Animated covers MUST be fetched by a provider chain Apple Music then Tidal (ported from Exhale `canvas/`), played by
  a separate UI-owned silent looping player (volume 0, audio track disabled, no audio focus, no MediaSession, REPEAT_ONE),
  only while visible and foregrounded and not in Reduced Motion, with static fallback. No change under `player/`.
- **FR-017**: Strings: every new key in `values/strings.xml` and `values-ar/strings.xml` (plurals for counts);
  `ArabicTranslationCompletenessTest` passes. No hard-coded user-visible text.
- **FR-018**: Accessibility: 48 dp targets, labels on icon-only controls, no information by color alone, focus kept through
  expansion and style change; transport, seek bar and volume slider LTR in RTL; Western digits.
- **FR-019**: The UI MUST depend only on `PlaybackController` state/flows/commands (and library/settings data). No file under
  `androidApp/.../player/` is edited; no new dependency in `gradle/libs.versions.toml` without a D-entry.
- **FR-020**: Code adapted from GPL references (Exhale, BitChord, Rhythm) carries a per-file attribution header (source path,
  pinned SHA, license, "modified") and a `THIRD-PARTY-NOTICES` line; MIT sources (Flick) keep their notice. No upstream
  credentials, tokens or protected assets are copied.

### Key Entities
- **Design tokens / GlassPolicy**: semantic values and the single rule for blur vs opaque.
- **PlayerStyle**: `DEFAULT | LARGE | IMMERSIVE`.
- **IntegrationSettings**: three booleans (Wikipedia true, Deezer false, motion false) and `MotionNetworkMode` (`WIFI_ONLY` default, `WIFI_AND_CELLULAR`).
- **ArtistEnrichment**: portrait result (Manual / Deezer / NewestTrackCover / None) and About result (Found with attribution / Missing /
  Ambiguous), cached.
- **MotionArtwork descriptor**: video URL(s), source (Apple/Tidal), fetched-at, expiry.

## Success Criteria *(mandatory)*

- **SC-001**: Characterization tests T001/T002 (navigation, mini-player, DEFAULT player, saved destination, back order) pass
  unmodified at the end of M1, M2, M3, M4 and M5 (recorded in HANDOFF).
- **SC-002**: With Reduce Transparency on or off, Light and Dark, English and Arabic, every token text/background pair used by
  screens has contrast >= 4.5:1 (>= 3:1 for large text), checked by a test; with Reduce Transparency on, zero blur layers.
- **SC-003**: Each of the seven screen groups has Compose tests for content/empty/loading(error where it exists) states and each
  keeps all pre-existing actions; font scale 1.0, 1.3 and 2.0 and long names show no clipped primary action (bounds inside the
  window).
- **SC-004**: Player-style parity test passes for 3 styles x full command list; unknown stored value gives DEFAULT; switching
  style leaves position and queue unchanged.
- **SC-005**: Hide Volume Bar: default false, persisted across process restart, bar absent in all 3 styles when true.
- **SC-006**: With all integration switches off, 0 network requests are made by these features (fake fetcher records 0 calls;
  also confirmed by a `.qa` run with the fake endpoint unreachable).
- **SC-007**: Integration provider suites (fake responses) cover: success, empty, ambiguous, placeholder image, HTTP error,
  timeout, oversized body, redirect to http, cancellation, cache hit, expired cache, negative cache; all pass.
- **SC-008**: Isolation: during playback on `.qa`, a motion video that contains an audio track plays for >= 30 s while music
  position advances monotonically, music requests/loses no audio focus and `PlaybackController` state is unchanged.
- **SC-009**: Accessibility audit test finds 0 unlabeled icon-only controls and 0 interactive targets under 48 dp across all
  screens and the 3 styles.
- **SC-010**: `ArabicTranslationCompletenessTest` passes; `lintDevQa` 0 errors; full host suites pass with test counts not
  lower than the baseline recorded in T001.
- **SC-011**: Frame stats on `.qa` (`scripts/ui-frame-stats.sh`, Home scroll, Library scroll, player open): janky-frame share at
  M5 is not more than 5 percentage points above the T003 baseline, with Reduce Transparency on it is not worse than off.
- **SC-012**: `git diff <base>..HEAD --stat -- androidApp/src/main/kotlin/com/amwolfstein/sistrum/player` is empty and
  `PlaybackController` public API is unchanged (guard test T042); no Room change.
- **SC-013**: Lyrics: existing parser/browse tests pass unmodified; contrast >= 4.5:1 for the current line on bright and dark
  test artwork; with Reduced Motion no animated scroll is started (test hook).

## Out of Scope / later
- Kotlin decoders for analysis (U-101..U-107, formerly M4b), their wiring and benchmark: separate step, own spec/plan.
- SpotiFLAC extension runtime, registry, package install/update, Settings → Extensions, runtime POC, security model for scripts.
- Better Lyrics, Lyrically, YouTube Music, Rhythm's Apple canvas provider and lyrics priority sheet, Apple/YTM lyrics providers.
- Lyrics provider search changes, auto-fetch policy changes, cached-lyrics model changes, word-by-word timing model.
- Writing lyrics into audio files or sidecars, tag writing, storage coordinator.
- LRCLIB from Rhythm (Sistrum already has it).
- Spatial Audio, 8D, batch embedding, custom registries, streaming/download features, Flick biography/About.
- Library Settings page and a restyle of the existing Settings pages (owner, D-20).
- Any change to playback, decoders, DSP, queue, MediaSession service, Room schema, scanner.
- Performance tuning beyond the SC-011 budget (SC-013 of 001 stays deferred to the perf phase).

## Assumptions
- Phase 0 numbers (spacing scale, 12-28 dp corners) are proposals; existing Sistrum geometry wins where characterized.
- Reduced Motion follows the system animation scale (no new in-app setting, D-6).
- Reference checkouts exist read-only under `/tmp/sistrum-refs/<name>` at the pinned SHAs; the coder reads, never runs them.
- Decisions taken without an owner answer are in `DECISIONS.md` (D-1..); open owner items are in the plan.
