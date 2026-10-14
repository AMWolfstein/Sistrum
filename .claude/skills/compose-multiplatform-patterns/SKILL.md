---
name: compose-multiplatform-patterns
description: Jetpack Compose (Compose Multiplatform artifacts) patterns for Sistrum's UI — screen state, events, composable design, recomposition performance, theming — plus this app's own UI rules (RTL, Arabic, Western digits, font-glyph icons). Use when building or reviewing Compose UI or chasing jank.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Compose Patterns (Sistrum)

## App rules first (CLAUDE.md "Localization", always)
- Strings in `values/strings.xml` and `values-ar` (complete); counts via `<plurals>` (Arabic zero…other).
- Numbers through `formatDisplay()` / Western digits; RTL: text follows content direction and aligns to start;
  transport controls, seek bar, volume slider stay LTR; directional glyphs listed in `MirroredInRtlSymbols`;
  drag deltas and graphics-layer x need `towardsEnd()`.
- Icons are font glyphs (`ui/components/MaterialSymbols.kt`), not vector drawables.

## State
- One immutable screen-state data class in a ViewModel `StateFlow`; collect with `collectAsStateWithLifecycle()`.
- Stateful wrapper (gets the ViewModel) + stateless content composable (takes state + callbacks) — previewable, testable.
- Many callbacks → one `onEvent(Event)` with a sealed interface.

## Composable design
- Accept `modifier: Modifier = Modifier` and apply it to the root; slot parameters (`content: @Composable () -> Unit`)
  for flexible containers.
- Modifier order matters: layout (padding/size) → shape/clip → drawing (background/border) → interaction (clickable).
- Pass lambdas, not `NavController`, down the tree.

## Performance (jank)
- Stable inputs: immutable data classes (`@Immutable` / `@Stable` where the compiler can't infer); no mutable lists
  as parameters.
- Lazy lists: `items(list, key = { it.id })`; avoid nested scrolling of the same orientation.
- `remember(keys) { … }` for derived values; `derivedStateOf` for values computed from frequently changing state
  (scroll position, playback position).
- Read fast-changing state (playback position, waveform) as late as possible — in `Modifier.drawBehind {}` /
  `graphicsLayer {}` lambdas or a small child composable — so the whole screen doesn't recompose every frame.
- No heavy work (sorting, filtering, decoding artwork) in composition: ViewModel or `remember`.
- Side effects only in `LaunchedEffect` / `DisposableEffect` / `SideEffect` with correct keys.

## Theming
Material 3 color scheme from the app theme; dynamic color on Android 12+ if the app opts in; never hardcode colors
in screens.

## Anti-patterns
`mutableStateOf` in ViewModels instead of StateFlow; new objects/lambdas allocated per recomposition in hot lists;
`LaunchedEffect(Unit)` as ViewModel init; heavy computation in composables.
