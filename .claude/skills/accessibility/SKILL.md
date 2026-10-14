---
name: accessibility
description: Android/Compose accessibility checklist (WCAG 2.2 AA applied to native) — TalkBack labels and roles, touch-target size, contrast, text scaling, focus and live regions, plus RTL/Arabic. Use when building or auditing Sistrum UI screens or a full accessibility review.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Accessibility (Compose, WCAG 2.2 AA)

## Checklist
- **Labels**: every icon-only button has a `contentDescription` from string resources (both locales); decorative
  images use `contentDescription = null`. Font-glyph icons (`MaterialSymbols`) need explicit semantics.
- **Roles & state**: custom clickables use `Modifier.semantics { role = Role.Button }` (or `toggleable`/`selectable`);
  toggles expose state (`stateDescription`); the play/pause button announces the current action.
- **Sliders**: seek and volume sliders expose value/range (`progressBarRangeInfo`) and work with TalkBack swipe
  adjustments; announce time in words, not only "1:23".
- **Touch targets**: ≥ 48 × 48 dp (Material; WCAG minimum 24 × 24) — `Modifier.minimumInteractiveComponentSize()`.
- **Contrast**: text ≥ 4.5:1 (large text/icons ≥ 3:1), including text over album art and blurred/glass surfaces.
- **Text scaling**: layouts survive 200% font scale without clipping or overlap; use `sp` for text.
- **Focus & order**: logical traversal (`traversalIndex` / `isTraversalGroup` where needed); dialogs and sheets keep
  focus inside and return it on close.
- **Live updates**: track changes and errors announced via `liveRegion = LiveRegionMode.Polite`, not spammed every second.
- **Not colour alone**: states (selected, error, playing) also shown by icon/text.
- **RTL / Arabic**: semantics text in the right locale; mirrored layouts keep transport controls LTR (CLAUDE.md).

## Verify
Compose UI tests with semantics matchers (`onNodeWithContentDescription`, `assert(hasRole…)`), Android lint
accessibility checks, and a TalkBack pass on the `.qa` build listed as an owner manual check (never marked PASS by
an agent).
