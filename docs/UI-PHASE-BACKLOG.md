# UI phase backlog (owner, 2026-10-14)

Items agreed for the UI / bug-fix phase; each becomes a Spec Kit task when that phase is planned.

- **Pseudo-localization:** enable Android pseudolocales on the `qa` build type (`isPseudoLocalesEnabled = true`) and
  add a `.qa` device test that renders the main screens in `en-XA` (long, accented text) and `ar-XB` (forced RTL):
  no truncation, no overlap, mirrored layout correct, transport controls still LTR. Idea from agency-agents
  `i18n-engineer` (its tooling is web; only the method is used).
- **Compose performance evidence:** Compose compiler stability/metrics reports in CI-like local runs, and a
  Macrobenchmark module on `.qa` for scroll jank (library lists) and the now-playing screen.
- **Design review per screen:** `ui-finish-gate-reviewer` design contract before a redesign, PASS/HOLD before ship;
  skills `compose-multiplatform-patterns` and `accessibility`.
- **Glass look (optional):** Android 12+ `RenderEffect` blur (Haze), AGSL refraction on 13+; measure GPU/battery on the
  now-playing screen; contrast over album art; adapted to Material, not an iOS copy.
