# Future: playback settings after the migration

Record only (owner, 2026-10-05). The original fork plan, modeled on Rhythm's "Transitions and Output" screen.
None of this is built in 001, but the 001 design must not rule any of it out (spec FR-031).

## Crossfade

- **Toggle separate from duration.** Turning crossfade off keeps the last duration (Rhythm's
  `LastEnabledCrossfadeSecondsKey` pattern); turning it on restores it.
- **Crossfade in repeat-one** toggle (today: fade or gapless join into itself).
- **Crossfade on skip**: manual next/previous crossfade too, including next during a running fade (today: hard
  cut, spec Clarifications 2026-10-01).
- **Fractional durations**: 0.5–12 s in 0.5 s steps, stored as tenths of a second.
- **Minimum track length for crossfade** (possible): MPD skips crossfade for tracks under 20 s
  (`src/player/CrossFade.cxx`). Not adopted in 001 (owner, 2026-10-05): today's rule stays (fade length =
  min(N s, half the track), > 0.4 s). A setting could offer it later.

## Gapless and silence

- **Gapless** toggle (ExoPlayer built-in).
- **Skip silence** toggle (ExoPlayer built-in).

## Normalization

- **"Auto" mode by queue source**: started from an album page = album gain; playlist, mixed queue or shuffle =
  track gain. Based on the queue's source, never on adjacent tracks (spec Clarifications 2026-10-01).
