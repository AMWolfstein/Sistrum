# Sistrum — instructions for the implementer (Codex)

You are the implementer. You receive one task brief at a time and implement exactly that task.
There are two modes. The brief's first line says which one; without `MODE: AUTONOMOUS` it is single-task mode.
- **Single-task mode:** the orchestrator (Claude Code) decides scope and architecture, reviews your diff and commits.
- **Autonomous mode** (owner, 2026-10-14; spec 002 onward): you implement, verify, commit and push one task of a
  milestone yourself. See "Autonomous mode" below; it replaces the commit/stage/push/Graphify items under "Never".
You never delegate: do not start other agents, `codex`, `opencode` or `relay.mjs`.

## Always
- Follow the brief; its MUST / MUST NOT / TESTS / ACCEPTANCE sections override anything here.
- Run the test command the brief gives you, fix failures, and report the results.
- Report: files changed, commands run with results, anything left undone.

## Never
- Commit, stage or push (single-task mode). Stash, switch branches, rebase, reset --hard or force-push (both modes).
- Touch files outside the brief without saying why in your report.
- Run or edit Graphify, or anything in `graphify-out/` or `.claude/`.
- Edit `local.properties`, `androidApp/src/main/jniLibs/` or the generated font
  `androidApp/src/main/res/font/material_symbols_rounded.ttf`.
- Change the Room schema or version (`sync/SyncDatabase.kt`) unless the brief specifies it.
- Remove or disable a feature; change `player/` or `androidApp/src/main/cpp/` beyond the brief.
- Delete, skip (`@Ignore`) or weaken tests, or change expected values to make them pass.

## Where things are
- App: Kotlin + Jetpack Compose in `androidApp/src/main/kotlin/com/amwolfstein/sistrum/`;
  shared module `sharedLogic/`. Unit tests in `androidApp/src/test/`, device tests in `androidApp/src/androidTest/`.
- Build: `./gradlew :androidApp:assembleDevQa`
- Unit tests: `./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest`
- Playback migration rules: `.specify/memory/constitution.md`
- Strings: `androidApp/src/main/res/values/strings.xml` is the source; every new key also needs
  `values-ar/strings.xml` (`ArabicTranslationCompletenessTest`). Counts use `<plurals>`.
- Numbers and RTL helpers: `ui/components/LatinDigits.kt`, `ui/components/LayoutDirectionSupport.kt`,
  `MirroredInRtlSymbols` in `ui/components/MaterialSymbols.kt`.

## Autonomous mode (`MODE: AUTONOMOUS`)

The brief names ONE task id and the milestone's task list (`specs/<feature>/tasks.md`). Each task runs in a fresh
session: read `specs/<feature>/HANDOFF.md` ("Current status") and the task's line first; read only the spec/plan
sections and files the task needs.

1. **Implement** exactly that task. Tasks marked `[TESTS-FIRST]` write tests only; a later task makes them pass
   without modifying them. High-risk areas (crossfade, queue, state flows, normalization, Room, MediaSession) always
   get their tests first.
2. **Quick gate** (every task): memory check first (`./gradlew --stop`; `pkill -f '[K]otlinCompileDaemon'`;
   `MemAvailable` in `/proc/meminfo` must be ≥ 2 GB; if not, wait 20 min and re-check, at most 3 times, then stop
   with BLOCKED). Then
   `bash .claude/skills/verify-task/scripts/verify.sh --files '<task files>' --quick '<module test tasks>'`
   (`--expect-fail '<FQCN>,…'` for tests-first tasks; `--tests` to narrow). It must print `GATE: PASS`.
   Module test tasks: `:codecs:test`, `:metadata:test`, `:analysis:test`, `:dsp:test`, and for app code
   `:androidApp:testDevQaUnitTest` (narrowed with `--tests`) / `:sharedLogic:testAndroidHostTest`.
   The milestone's last task runs the full gate instead: no `--quick`, plus `--lint`, plus the phone check (8).
3. **Self-review (migration-guard checklist)** when the diff touches tests, `player/PlaybackController.kt` or Room
   (`sync/SyncDatabase.kt`, entities, DAOs, migrations). Any item below = fix it before committing:
   - Tests: none deleted, renamed away, commented out, `@Ignore`d, `assumeTrue(false)`/early return; no assertion
     removed or weakened (exact → range, fewer fields, broader matchers, longer timeouts); no expected value changed
     to match new behaviour without a spec reason; tests marked "make pass without modifying" untouched; new tests
     assert something meaningful.
   - PlaybackController keeps its public API (play, pause, resume, stop, clearQueue, next, previous, shuffle, repeat,
     playNext, append, startMoodRadio, selectQueueTrack) and StateFlows (playback state, queue, artworkCrossfade,
     moodRadioActive, crossfadeSeconds) unless an ADR in the spec says otherwise.
   - No feature removed or disabled (statistics, Last.fm, lyrics, Mood Radio, artwork, metadata, queue, crossfade,
     normalization). Room: no entity/column/index/DAO SQL/version/migration change unless the task specifies it
     together with the migration and a migration test.
4. **Graph:** run `python3 .claude/hooks/graph_refresh.py auto`; if it prints `graph: changed`, stage
   `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json` in the same commit. Never edit graph files.
5. **Commit** by explicit path only (never `git add -A`, `.`, `-u`): `<type>(<area>): <description>`, task id in
   the body, the `Expected-fail:` line for tests-first tasks, and the trailer
   `Co-authored-by: Codex <noreply@openai.com>`. One task = one commit. Then `git push` (current branch only;
   never `main`, never force).
6. **Log:** tick the task in `tasks.md`, add a row to `HANDOFF.md`'s task log (task, commit, gate result, NOT RUN
   items with reason, decisions) and rewrite "Current status". Both files go in the task's commit.
7. **Decisions** the task leaves open: pick the recommended option and log it in `specs/<feature>/DECISIONS.md`
   (`## D-<n>: title` / Case / Options / Chosen / Revisit if). Never change architecture, ADRs or spec 001's shipped
   behaviour: stop with BLOCKED instead.
8. **Device:** only the milestone's last task (phone check) uses the phone, only with the `.qa` build
   (`verify.sh --instrumented`; it refuses other IDs). Never install, instrument or force-stop
   `com.amwolfstein.sistrum.dev` or `me.misa198.airmedy.dev`. Real listening / Bluetooth / headset checks go to the
   owner's manual list in HANDOFF, never PASS. A phone-check failure: `git bisect` over the milestone's commits,
   running only the failing test; fix it in a new commit.
9. **Retries:** up to 3 attempts per task. Still failing → don't commit; write the problem to HANDOFF and end with
   BLOCKED.

End your final message with exactly one line: `RESULT: DONE <commit sha>` or `RESULT: BLOCKED <reason>`.
Never invent results; anything not run is NOT RUN with the reason.
