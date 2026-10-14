---
name: mobile-design-android
description: Android mobile design principles for Sistrum's UI phase — Material 3 type scale, color roles and dark theme, 8dp spacing, window size classes and navigation (bottom bar, rail, back/predictive back), touch psychology (48dp targets, thumb zone, gestures with button alternatives, haptics), text scaling and edge-to-edge — applied to a local music player. Use when designing or reviewing screens, with compose-multiplatform-patterns, accessibility and ui-finish-gate-reviewer.
---
<!-- Written for Sistrum from the Android parts of claude-code-templates' mobile-design skill (github.com/davila7/claude-code-templates @ f497abc), MIT License, Copyright (c) 2025 Daniel (San) Ávila; see docs/CLAUDE-CODE-TEMPLATES-LICENSE.md. iOS, React Native and Flutter content left out. -->

# Mobile Design — Android (Sistrum)

Principles, not fixed values: the app's own theme, fonts and RTL rules (CLAUDE.md "Localization") win over generic
Material defaults. Design for the worst case: one hand, moving, bright sun, large font, Arabic.

## Before designing a screen
State: who uses it and for what (skill `ui-finish-gate-reviewer` design contract), window size class, which states
exist (loading, empty library, permission denied, scanning, analysis pending, error), and what it does at 200% font
and in RTL. Don't reach for default patterns (card grids, FAB, drawer) without a reason from the job.

## Touch
- Targets ≥ 48 × 48 dp even when the visual is smaller (24dp icon → padded 48dp hit area); ≥ 8dp between targets.
  Transport controls and the mini player deserve more than the minimum.
- **Thumb zone** (one-handed, bottom of the screen is easy, top is a stretch): primary playback actions, the mini
  player and the main navigation belong at the bottom; rare or destructive actions (clear queue, delete playlist, rescan)
  away from the easy zone and confirmed or undoable.
- Feedback within 100 ms on every touch (ripple/indication); show progress for anything longer and disable the control
  to stop double taps.
- Gestures (swipe to skip, swipe to dismiss, drag to reorder the queue, scrubbing) always have a visible button or
  menu alternative; precision tasks (seek scrubbing) need a large hit area and feedback that isn't hidden by the finger
  (show the time above the thumb).
- Haptics: light, meaningful (reorder drop, long-press menu, scrub snap to start); respect the system setting; never on
  every tick of a slider.

## Layout & navigation
- 8dp spacing grid (4dp inside components); phone margins 16dp, 24dp on medium+.
- Window size classes: compact (< 600dp) single column + bottom navigation; medium (600–840dp) consider two panes and
  a navigation rail; expanded (> 840dp) list–detail (library | album/now playing). Landscape phones are compact height:
  the now-playing screen must not clip controls.
- Bottom navigation: 3–5 destinations, labels always visible, active = filled icon + indicator. Top app bar: at most
  3 actions, the rest in overflow.
- Back: system back/gesture pops one level; support predictive back (Android 14+); never hijack back except to close a
  sheet/dialog or confirm discarding work. Remember scroll position and selected tab per destination.
- Edge-to-edge (default on targetSdk 35+): content draws behind system bars with correct insets; the mini player and
  bottom bar respect the gesture area.

## Type & color
- Material 3 type scale roles (display / headline / title / body / label) through the theme's typography, `sp` for
  text, no fixed heights around text; test 85%–200% font scale.
- Color through semantic roles (surface, onSurface, surfaceVariant, primary/primaryContainer, error…), never literal
  colors in screens. Dark theme: near-black surfaces with elevation tone, reduced saturation; check contrast on every
  surface, especially text over album art (scrim or extracted, contrast-checked color).
- Dynamic color (Android 12+) only if the app's theme opts in; album-art-derived accents must still pass contrast.
- Meaning never by color alone (playing, selected, error also get an icon or text).

## Motion
Meaningful and short (≈ 150–300 ms), shared-element or container transforms for mini player ↔ now playing; respect
"remove animations" (animator duration scale 0) by skipping or shortening; avoid animating layout size every frame —
prefer transform/alpha (`graphicsLayer`).

## Checklist (per screen, before HOLD/PASS)
- [ ] Targets ≥ 48dp, primary actions in the thumb zone, destructive ones away and undoable/confirmed
- [ ] Every gesture has a button/menu alternative; touch feedback everywhere
- [ ] All states designed (loading, empty, permission denied, scanning, analysis pending, error with retry)
- [ ] Compact, medium/expanded and landscape checked; edge-to-edge insets correct
- [ ] 200% font, dark theme, RTL Arabic, TalkBack (skill `accessibility`) checked
- [ ] Semantic colors only; contrast ≥ 4.5:1 text / 3:1 icons, including over artwork
- [ ] Back and predictive back behave; state (scroll, tab) survives navigation and rotation
