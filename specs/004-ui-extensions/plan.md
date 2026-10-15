# Implementation Plan: UI overhaul and chosen integrations (004, UI part)

**Branch**: `feature/media3-migration` (D-2, owner) | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

## Summary
Restyle Sistrum on shared design tokens and one glass policy, redo the library screens, build the Wikipedia / Deezer / animated-cover
integrations, then navigation, mini-player and the three player styles, and finish with the lyrics UI, an
accessibility pass and the release gate. The UI reads `PlaybackController` only; `player/` is never edited. Execution as 002/003:
Codex, autonomous mode (`AGENTS.md`), one fresh session per task via `milestone-run.sh`; quick gate per task; ONE full gate + `.qa`
phone check ends each milestone; Claude steps and MANUAL tasks stop the runner.

## Technical Context
Kotlin (JDK 21), Compose (Compose Multiplatform artifacts), Android minSdk 31 / target 36, Media3 1.11.1 (already present). Tests: JUnit
host tests, Compose UI tests on `com.amwolfstein.sistrum.dev.qa` only. Haze wrapper exists (`ui/components/LiquidGlass.kt`).
Network: `HttpURLConnection` behind an `HttpFetcher` interface (D-7). Storage: DataStore + `cacheDir` files (D-8). No new
dependencies (D-10), no Room change, English + Arabic strings only.

## Constitution Check (`.specify/memory/constitution.md`)
- P2 PlaybackController contract: UI uses it only; API snapshot test (T042).
- P3 No feature loss: presentation-only restyle, existing screen tests unmodified (D-4).
- P4 No online playback: integrations fetch metadata, images and silent video for display only; music stays local; the motion player
  has no audio, focus or session (FR-016, SC-008).
- P8/P11 native end state: no native code added; no JS runtime (extension runtime is out of scope, D-1).
- P10 all users: no assumption about the owner's library/device; fixtures are synthetic; frame budget is relative to a baseline.
- Coder rules: tests-first for isolation/state (D-16); no weakened tests; no edits under `player/`.

## Milestones
| M | Goal | First .. last task | Last task (full gate + phone check) |
|---|------|--------------------|------|
| M1 | Characterization, tokens, theme, glass | T001 .. T008 | T008 |
| M2 | Home, Library, Album, Artist, Playlist, Search restyle; default artist photo; glass toggle | T009 .. T020 | T020 |
| M3 | Integrations: HTTP fetcher, Wikipedia, Deezer, animated covers, Settings -> Integrations | T021 .. T041 | T041 |
| M4 | Navigation, mini-player, DEFAULT/LARGE/IMMERSIVE, Hide Volume Bar | T042 .. T055 | T055 |
| M5 | Lyrics UI, accessibility, release evidence | T056 .. T066 | T066 |

Runs: one `milestone-auto.sh` run per milestone (tasks, Codex reviews, fix task, phone check, Haiku final check).
No Library Settings page and no Settings restyle (owner, D-20). T067 (MANUAL, merge) is the owner's. Before T001 Claude records the 004 start SHA in HANDOFF (D-2).

## Reuse of reference code and licenses
| Source (pinned) | License | Use | Obligation |
|---|---|---|---|
| Exhale `166e2cc7...` | GPLv3 | player styles V7/V8, glass, library/detail layouts, lyrics look, `canvas/` module + `CanvasArtworkPlayer` | per-file header (path, SHA, "modified"), THIRD-PARTY-NOTICES line, corresponding source stays in the repo; no icons/provider art copied |
| Rhythm `6f26f963...` | GPLv3 | `WikipediaProvider`, `DeezerApiService`, `DeezerArtistImageCache` | same; no credentials; weak error handling not copied (typed failures, CancellationException propagates) |
| BitChord `fe95bb60...` | GPLv3 | Hide Volume Bar idea | implement independently; header only if code is adapted |
| SpotiFLAC-Mobile `5a895265...` | MIT | Home look only: its Apple Music home-provider layout (D-22) | notice if adapted; no streaming/download/feed code |
Not used: SpotiFLAC-Extension packages, MusicBeat, Rhythm lyrics/YTM/Apple canvas (owner scope). Rights notes in
`docs/ui-overhaul/06-licenses.md` and D-9.

## Approach per milestone
1. **M1**: characterization first (T001/T002 pass now, never edited later), the test matrix helper and frame baseline (T003), then
   tokens, reduced motion, glass policy and opaque fallback.
2. **M2**: one screen group per task; slots for portrait, About and header background are left empty-capable so M3 only fills them.
3. **M3**: bottom-up and tests-first: fetcher, preferences, Deezer, Wikipedia, enrichment repository, artist wiring; then Apple token,
   motion providers, repository, silent player with isolation tests, Integrations screen, device isolation test.
4. **M4**: boundary guard first, nav and mini-player visuals under the characterization tests, style preference, dispatch host
   (DEFAULT identical), parity tests, LARGE, IMMERSIVE, Hide Volume Bar, motion in LARGE/IMMERSIVE.
5. **M5**: lyrics panel, accessibility audits, long-name sweep, player boundary check,
   release evidence, full gate.

## Integration boundary
UI -> `PlaybackController` (state, queue, artworkCrossfade, moodRadioActive, crossfadeSeconds, commands) -> player internals (not
visible to UI). Style host gets an immutable `PlayerUiState`. The motion player is UI-owned and independent (own ExoPlayer, no
MediaSession/service/focus/DSP). Expected conflicts when merging with 002/003: `MainActivity`/`App.kt`/`AppDestinationContent.kt`
wiring, strings files, `THIRD-PARTY-NOTICES` (ledger in `docs/ui-overhaul/09`).

## Verification and review
Per-task `verify.sh --quick`; milestone gate = full suites + `--lint` + `assembleDevQa` + `.qa` device classes + `phone-log-triage`.
Milestone review set per D-18. Confirmed CRITICAL/HIGH findings become `<last id>-fix` tasks before the gate task.

## Risks
Rights/terms of Apple, Tidal, Deezer endpoints (D-9, owner accepted); token scraping breaks (fail closed to static); battery and
frame cost of glass and video (SC-011, visible-only playback, bounded cache); RTL double mirroring; DEFAULT player drift (T001/T002,
T048); font-scale clipping; flaky Compose device tests (known baseline list in HANDOFF); shared branch with 002 (004 never edits `player/`, SC-012).

## Owner decisions (2026-10-15)
D-2 same branch; D-9 no fallback token; D-10 Wikipedia on, Deezer and animated covers off; D-11 wiki by app language; D-12 LARGE
mapping (owner reviews the look); D-19 default artist photo = newest song's cover.
