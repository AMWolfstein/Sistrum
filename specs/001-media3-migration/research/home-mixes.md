# Future: auto playlists on the home screen

Record only (owner, 2026-10-05). A later feature, independent of the playback migration.

## Mixes

- **From metadata**: per genre, per decade, recently added, "similar to <artist>" through shared genres.
- **From listening statistics**: on repeat, forgotten favorites, never played, "because you listened to X".
- **Later, with the analyzer** (`analyzer-future.md`): mood-based mixes.

## Behaviour

- Refresh cadence: mixes stay stable between refreshes (no reshuffle on every visit).
- "Save as playlist".
- A minimum size; a mix below it isn't shown.
- Computed in the background, never on the UI thread.

## Ideas from

- AFinity (github.com/MakD/AFinity, GPL-3.0): `RadioManager.kt`.
- Flick Replay (github.com/moss-apps/Flick): listening recaps.
