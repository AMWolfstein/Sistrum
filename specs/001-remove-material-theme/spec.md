# Feature Specification: Remove the Material theme and Material UI

**Feature Branch**: `wip`

**Created**: 2026-11-14

**Status**: Draft

**Input**: Owner request: "المشروع فيه ثيمين واحد اسمه material و التاني mornye كل
واحد منهم ليه شكل مختلف عن التاني في كل حاجه. كل الي عايزة منك انك تمسح ثيم ال
material و ال material ui من كل حته في الكود و الي هيتفضل بس هو ثيم ال mornye."
Earlier owner answers kept for this mission: remove every Material-only setting,
and move people who use the Material style to Mornye. No dead code is left for later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mornye is the only look (Priority: P1)

Whatever the person chose before, the app opens in Mornye and every screen,
sheet, dialog, player surface and control looks like Mornye. There is no way to
pick a Material look.

**Why this priority**: This is the request itself.

**Independent Test**: Start the app with stored preferences that chose the Material
style, open the main screens and the player, and confirm they render the Mornye
look with the accent, font and glass choices the person already had.

**Acceptance Scenarios**:

1. **Given** a person whose stored style was Material, **When** the app starts,
   **Then** it shows Mornye and keeps their light/dark mode, accent, font and
   glass-outline choices.
2. **Given** any screen that had a Material and a Mornye version, **When** it
   opens, **Then** it shows the version that Mornye showed before this change.
3. **Given** the operating system asks for reduced motion, **When** controls
   animate, **Then** they follow Mornye's reduced-motion behaviour as before.

---

### User Story 2 - No Material settings remain (Priority: P2)

Appearance settings show only Mornye choices. Style, dynamic (wallpaper) color,
seed color and AMOLED options are gone, and settings search no longer finds them.

**Why this priority**: Leftover settings would change nothing and confuse people.

**Independent Test**: Open Appearance settings and search settings for "style",
"dynamic", "AMOLED" and "seed"; nothing Material-only appears.

**Acceptance Scenarios**:

1. **Given** Appearance settings, **When** opened, **Then** they show theme mode,
   Mornye accent, glass outlines, font and lyrics options only.
2. **Given** stored preferences from an older version, **When** the app loads them,
   **Then** the retired Material keys are removed and nothing new is written in
   their place.

---

### User Story 3 - The Material code and packages are gone (Priority: P3)

The codebase keeps one visual system. Material-only screens, widgets, branches,
theme builders, icon sets, translations, tests and the Material design packages
are removed, so nothing Material-only is left to maintain.

**Why this priority**: The owner asked that the Material theme disappear from the
code, not only from the screen.

**Independent Test**: Search the code for the retired style switch, the Material
theme builder, the Material 3 Expressive, Material UI, dynamic color and Material
Symbols packages; none remain, and the analyzer and the test suite pass.

**Acceptance Scenarios**:

1. **Given** the source tree, **When** searched, **Then** there is no style switch,
   no Material-only branch and no import of the removed packages.
2. **Given** the dependency list, **When** read, **Then** the Material 3 Expressive,
   Material UI, dynamic color and Material Symbols packages are not direct
   dependencies.

### Edge Cases

- Stored preferences contain the retired keys with unexpected types: they are
  removed without failing the start-up.
- The person used dynamic (wallpaper) color with Material: Mornye shows their
  Mornye accent (red by default), as Mornye always did.
- A custom font is still loading at start: the theme does not rebuild in a loop
  and shows the default font until the custom one is ready, as Mornye did.
- Media notification: the playback buttons always use the Mornye icons, which
  Mornye showed before.
- Android home-screen widgets placed before the update: the launcher drops them
  or shows that they cannot load; the person removes them (FR-012).
- First launch: the setup and tutorial screens show Mornye. Before, a new install
  defaulted to Material, so first-run setup looked Material.
- The app was open on Material's Settings tab: it opens on Home, since Mornye
  opens Settings as a page and has no Settings tab.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST always use the Mornye theme in light and dark mode.
- **FR-002**: Every screen, sheet, dialog and control that had separate Material
  and Mornye versions MUST show the Mornye version, with the behaviour Mornye had
  before this change.
- **FR-003**: Appearance settings and settings search MUST NOT offer the style,
  dynamic color, seed color or AMOLED options.
- **FR-004**: On load, stored values for the retired keys (`theme_style`,
  `use_dynamic_color`, `seed_color`, `use_amoled`) MUST be removed once; all other
  theme and lyrics preferences MUST be kept.
- **FR-005**: The media notification MUST use the Mornye playback icons.
- **FR-006**: The Material theme builder, the Material-only screens, widgets,
  branches, icon mapping and the retired translations MUST be deleted, with no
  unused leftovers.
- **FR-007**: The Material 3 Expressive, Material UI, dynamic color and Material
  Symbols packages MUST be removed from the app's direct dependencies.
- **FR-008**: Tests that only checked the Material look MUST be deleted; tests that
  check shared behaviour MUST run on the Mornye theme and keep their assertions.
- **FR-009**: Developer documentation MUST describe one theme (Mornye).
- **FR-010**: Home and Now Playing MUST keep only their Mornye look: the Home tab
  and its search, explore, intro and import parts; the shell's tabs (Mornye's
  separate Search tab and Settings page, not Material's search-from-Home and
  Settings tab); the full player, its route, mini player, lyrics, queue, details
  and controls. The Material player layout, backdrop, compact bar and artwork
  colour scheme are deleted.
- **FR-011**: The player cover style MUST resolve as Mornye did: a cover style
  the person chose (card or full screen) is kept; with no stored choice the
  player shows the card, Mornye's default (Material's default was full screen).
- **FR-012**: The Android home-screen widgets (Player and Download queue), which
  exist only in a Material 3 look, MUST be removed with their native code,
  resources, saved widget settings and the app's code that feeds them. The media
  notification and its icons MUST keep working.
- **FR-013**: Settings MUST keep only the Mornye look: Settings opens as a page
  (not a tab), every settings page uses Mornye's inset grouped surfaces, rows,
  switches, section headers and choice chips. The row icons that only the
  Material look drew (all 73 switch rows that pass an icon, and 83 of the 95
  other rows that pass one)
  are removed from the code; the rows that Mornye shows with an icon keep it.
- **FR-014**: The Library MUST keep only the Mornye look: its root is Mornye's
  library screen, and section pages, album and folder screens, mixes, search
  results, directory views, selection bars and track menus show their Mornye
  version. Material's root library page (its tabs, filter header and library
  tools inside the queue tab) is deleted, because Mornye never opens it.
- **FR-015**: The first-run setup MUST look like Mornye: its next/finish button
  is the standard Mornye-themed button, and its language list marks the chosen
  language the way Mornye's Settings language picker does (accent icon and
  title, a check mark), not with Material's filled, pill-shaped row.

### Key Entities

- **Theme settings**: theme mode, Mornye accent, system-font choice, custom font,
  glass outlines and the lyrics display options. The style, dynamic color, seed
  color and AMOLED fields are removed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 places in the app source choose between a Material and a Mornye look.
- **SC-002**: 0 imports of the four removed packages in the app and its tests.
- **SC-003**: The analyzer reports no issues, and the full test suite has no new
  failures compared with the baseline recorded in plan.md.
- **SC-004**: A person whose stored style was Material sees Mornye on the next
  start with their mode, accent and font choices unchanged.

## Assumptions

- "Material UI" means the Material design layer this app adds on top of Flutter:
  the Material theme, its branches and widgets, and the Material 3 Expressive,
  Material UI, dynamic color and Material Symbols packages. Flutter's own core
  widget library (`package:flutter/material.dart`: `ThemeData`, `Scaffold`,
  buttons, `Icons`) stays, because the Mornye theme itself is built on it
  (`MornyeTheme.build` returns a `ThemeData` and the Mornye arms use these
  widgets). Replacing it would mean rewriting Mornye, which the owner did not ask.
- `material_color_utilities` stays: Mornye uses it to take colours from artwork
  (`CoverSourceColorBuilder`, `mornye_player_background.dart`), and Flutter itself
  depends on it. It draws no Material UI.
- The Android home-screen widgets have no Mornye version; the owner chose to
  delete them rather than design one (FR-012).
- Where Mornye's version of a control was the static (non-animated) widget, that
  widget is kept as is; no new Mornye components are designed in this mission.
- No APK build or device test is part of this mission unless the owner asks.
