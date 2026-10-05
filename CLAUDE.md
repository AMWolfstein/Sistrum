# Sistrum — notes for Claude Code

Android music player. Hard fork of misa198/airmedy, local-library only (desktop
sync removed). Repository: AMWolfstein/Sistrum, main branch `main`. GPL-3.0.
Kotlin + Jetpack Compose (Compose Multiplatform artifacts).

Built for all users, not only the owner: the owner's library and the CPH2307 are test
samples, not the scope. Never assume the owner's formats, tagging tools, library mix or
device (constitution Principle 10).

## Modules

- `androidApp` — Compose UI, ViewModels, player, MediaStore scan, Room.
- `sharedLogic` — Kotlin Multiplatform module, Android-only target (iOS removed).
- Flavors `dev` / `prod`. Dev application id: `me.misa198.airmedy.dev`.
- minSdk 31, targetSdk 36, JDK 21.

## Build and test

- `./gradlew :androidApp:assembleDevDebug`
- `./gradlew :androidApp:assembleDevRelease` (unsigned unless MOBILE_KEYSTORE_* env vars are set;
  sign locally with zipalign + apksigner and `~/.android/debug.keystore`; don't edit signingConfigs)
- `./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest`
- `./gradlew :androidApp:lintDevDebug`
- **Never run instrumentation, install test APKs, or force-stop anything against the owner's daily
  app `me.misa198.airmedy.dev`** (or its `.test` instrumentation package). All on-device tests use the
  separate test build `me.misa198.airmedy.dev.qa` (build type `qa`, label "Sistrum QA"; ADR-007) and
  its instrumentation package `me.misa198.airmedy.dev.qa.test`. `verify.sh --instrumented` enforces this.
  Never install `devDebug` on the device: it has the daily app's ID.
- With the test build: `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest`
  (`testBuildType = "qa"`), check both APKs' IDs (`aapt2 dump packagename <apk>`), install them with
  `adb install -r` (`-t` for the test APK), and run classes one at a time:
  `adb shell am instrument -w -r -e class <Class> me.misa198.airmedy.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`
  Keep the screen awake (`adb shell svc power stayon usb`). Don't use Gradle
  connected* tasks: they uninstall the app afterwards.

## Local, untracked build inputs

- `local.properties`: `sdk.dir=...` and `python3=<venv python>` (fonttools + brotli).
  The icon font `androidApp/src/main/res/font/material_symbols_rounded.ttf` is generated
  at build time by `tools/font-subset/subset_font.py` from the glyphs used in
  `ui/components/MaterialSymbols.kt`. Icons are font glyphs, not vector drawables.
- FFmpeg shared libraries in `androidApp/src/main/jniLibs/` are not in git. If a build
  fails with "FFmpeg artifacts are missing", run `bash scripts/build-ffmpeg-android.sh arm64-v8a`.
- The repo has no .gitignore; build outputs are ignored through `.git/info/exclude`.
  Stage files by explicit path only.
- Graphify: only `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json` are
  tracked. Everything else in `graphify-out/` (cache/, manifest.json, .graphify_root, lock
  and temp files) is machine-local and never committed.

## Playback

- Currently on the native FFmpeg engine (`player/FfmpegDecoder.kt`, `androidApp/src/main/cpp`,
  NDK/CMake; FFmpeg decodes every format, native player outputs float PCM to AAudio).
- The Media3/ExoPlayer migration is active. `player/` and native code may change ONLY
  through migration tasks defined in `specs/` and following
  `.specify/memory/constitution.md`. Outside those tasks: compile fixes only.

## Library scan

- MediaStore-based: `sync/MediaStoreLibraryScanner.kt`, `sync/EmbeddedTagReader.kt`.
  Room database in `sync/SyncDatabase.kt`.
- Bump `CurrentMetadataSchemaVersion` whenever parsing behavior changes for
  already-scanned files.
- EmbeddedTagReader opens each file once per tag read (large scan speedup). Keep it that way.
- For untagged files MediaStore reports the folder name ("Music") as album; intentional.

## Known state

- Instrumented suite has a set of test-side failures (missing menu host, stale text
  expectations, tests that set content twice, chart library crashing on empty data).
  Only fix newly failing tests.
- Lint errors are MissingTranslation (non-English locales lag behind) and
  LocalContextGetResourceValueCall.
- Cast / output-switcher button is hidden below Android 14 on purpose.
- Lock screen can show "Unknown artist" for a track (PlaybackService metadata);
  deferred to the Media3 phase.
- Volume normalization and Mood Radio are currently broken: fixing them needs changes
  in the FFmpeg/native layer, so they are deferred to the Media3 phase and fixed there
  on the new engine. Don't patch them on the native engine.

## Localization

- `values/strings.xml` is the source. `values-ar` must stay complete
  (`ArabicTranslationCompletenessTest`); the other locales are allowed to lag.
- Counts use `<plurals>`; Arabic needs zero/one/two/few/many/other.
- Numbers use Western digits in every locale: `MainActivity` wraps its context with
  `-u-nu-latn`, and non-resource formatting goes through `formatDisplay()` (ui/components/LatinDigits.kt).
- RTL: text takes its own direction (theme typography sets `TextDirection.Content`) and aligns to
  the layout's start edge. Media transport controls, the seek bar and the volume slider stay LTR
  (`LeftToRight`). Directional icon glyphs are listed in `MirroredInRtlSymbols`.
  Physical drag deltas and graphics-layer x values need `towardsEnd()`; `Modifier.offset` already mirrors.

## Workflow

- Work on a feature branch, never directly on `main`. For the playback migration use
  `feature/media3-migration` (create it from `main` if it doesn't exist).
- Commit on the feature branch and push it normally (`git push -u origin <branch>`
  the first time, then `git push`). Push after each verified task commit.
- Never push to `main`, and never merge into `main`: merging is the owner's decision.
- Record the current branch in `HANDOFF.md`; at session start, check out that branch.

## Don't

- Don't change the Room schema/version without a migration plan.

---

# Delegated workflow (orchestrator + coder)

## Roles

**Claude Code (you) = orchestrator.** Discovery, Graphify analysis, specs,
architecture decisions, task breakdown, delegation briefs, review, running
tests/builds, verification, commits.

**OpenCode (via `opencode-delegate`) = coder only.** Implements one bounded task
per brief. Never decides architecture, API contracts, scope, feature removal,
or when legacy code is deleted. Never commits.

## Rules

- Spec Kit (`specs/`, `.specify/`) is the planning source: spec, plan and tasks come from
  the `speckit-*` skills (specify, clarify, plan, tasks, analyze).
- Don't use superpowers' planning, execution or subagent skills for this migration
  (brainstorming, writing-plans, executing-plans, subagent-driven-development,
  dispatching-parallel-agents, test-driven-development, using-git-worktrees,
  finishing-a-development-branch). Its debugging and verification skills
  (systematic-debugging, verification-before-completion) are fine.
- Feature code is written ONLY through `opencode-delegate`, via `/delegate-task`. Do NOT
  use Spec Kit's `implement` command. You may make trivial review fixes (imports, typos) yourself
  when re-delegating costs more; say so in the status report.
- Always pass `--model` with a model chosen per the Models section below. Use
  `--resume-last` / `--session` for review follow-ups on the same task.
- Run EVERY relay invocation with `OPENCODE_DISABLE_CLAUDE_CODE=1` in its environment:
  fresh runs and `--resume-last` / `--session` follow-ups alike, e.g.
  `OPENCODE_DISABLE_CLAUDE_CODE=1 node <skill-dir>/scripts/relay.mjs --brief … --model …`.
  It stops OpenCode from loading this CLAUDE.md and every `.claude/skills` folder
  (`opencode-delegate`, `graphify`), so the coder can't re-delegate or run Graphify.
  The coder's own instructions are in `AGENTS.md`.
- Max 3 review rounds per task; then stop, record the problem in `HANDOFF.md`, ask the user.
- A task is DONE only after: diff reviewed against the brief, architecture checked
  against the spec/ADRs, relevant tests run, build run. Never on the coder's word,
  "it compiles", or "looks right".
- Never invent test, build, or Graphify results. Not run = say NOT RUN and why.

## Tooling (`.claude/`, tracked)

- `/delegate-task <id>`: builds the brief from `tasks.md`, picks the model, dispatches through
  `.claude/skills/delegate-task/scripts/dispatch.sh` (always sets `OPENCODE_DISABLE_CLAUDE_CODE=1`,
  refuses banned models). Use it for every dispatch and follow-up.
- `/verify-task <id>`: diff review, `migration-guard` when needed, then
  `.claude/skills/verify-task/scripts/verify.sh --files '<task files>'` (refuses when Kotlin files
  outside the task's list changed; then tests, build, optional device tests, graph refresh).
- New machine: `docs/dev-setup.md`.
- `migration-guard` agent: read-only test-integrity + contract review; only for tasks that touch
  tests, `player/PlaybackController.kt`, or Room.
- Hooks (`.claude/hooks/git_hooks.py`, PreToolUse on Bash): `guard` blocks `git add -A`/`.`/`-u`,
  `commit -a`, `stash`, `clean`, `reset --hard`, force pushes, pushes to or merges into `main`,
  `gh pr merge`; `graph` refreshes the graph before a commit when app code changed and blocks
  until `graphify-out/graph.json` and `.graphify_analysis.json` are staged.
- context7 MCP (`.mcp.json`, pinned): current Media3/ExoPlayer and AndroidX docs.

## Models

- Default: `opencode-go/deepseek-v4.1-flash`.
- Fallback, if the default fails twice on a task: `opencode-go/glm-5.3-flash`.
- Hard tasks only (crossfade, MediaSession, anything flagged high-risk in `tasks.md`):
  `opencode-go/deepseek-v4-pro`.
- Never use `opencode/*-free` models or `opencode-go/glm-5.3`.

## Testing split

- **You decide what is tested.** Test cases and acceptance criteria come from the
  spec and go in the brief. The coder never chooses its own acceptance tests.
- **The coder writes and runs tests.** Every brief lists the tests to write/update
  and the exact command. The coder runs them, fixes failures, and reports results
  before returning.
- **You re-run the final gate yourself** before any commit: the task's tests plus
  the build. Read only the summary and failures (`-q`, `tail`, grep for FAILED),
  not full logs. The coder's report is never sufficient on its own.
- **Review test diffs for cheating:** deleted or skipped tests, weakened assertions,
  changed expected values, `@Ignore`, tests edited to match new behavior without a
  spec reason. Any of these = NEEDS CHANGES.
- **Tests first for high-risk areas** (crossfade, queue, state flows, normalization):
  delegate the tests as their own task before implementation, review and commit
  them, then delegate the implementation with "make these tests pass without
  modifying them" in MUST NOT.
- Instrumented tests: run them with the adb method above only if a device is connected and the
  test-build application ID exists (never against the daily app).
  Anything needing real listening or hardware (Bluetooth, headset, audio focus,
  process death) goes on the manual checklist for the owner. Never mark it PASS yourself.
- If an architectural problem appears mid-task: stop delegating, analyze, update
  the spec/ADR, then resume.

## Context discipline

- Never read the whole repository. Graphify + targeted search first, then only
  files on the direct path of the current task.
- One phase or milestone per session. End every session by updating
  `specs/<feature>/HANDOFF.md` (phase, done, open issues, exact next step);
  start every session by reading it.
- Save useful Graphify summaries as Markdown under `specs/<feature>/research/` (committed).

## Graph updates

- The Graphify graph lives in the repo and must match the code at every commit.
- After a task passes verification and before committing, refresh the graph with
  exactly: `graphify extract androidApp/src/main --code-only --out .`
  (incremental, code-only, no LLM). Do NOT use `graphify update .` (widens the graph
  to the whole repo) or `graphify update` without a path (writes a second graph into
  `androidApp/src/main/graphify-out/`). Stage `graphify-out/graph.json` and
  `graphify-out/.graphify_analysis.json` by explicit path, in the same commit as the
  task's files.
- Never commit Graphify's cache. Never hand-edit graph files.
- Don't review graph diffs line by line; they are generated. The `.gitattributes`
  entry marks them as generated so diffs stay collapsed.
- The coder never runs or edits Graphify; exclude graph files from its briefs.
  The Graphify hook note that asks to include the graphify rule in subagent prompts
  does not apply to `opencode-delegate` briefs.

## Delegation brief template

```
ROLE: You are the IMPLEMENTER (coder), not the orchestrator. The "Delegated workflow"
      section in CLAUDE.md describes the orchestrator's job and does NOT apply to you.
      Do not load or use the opencode-delegate skill, do not run `opencode`, `relay.mjs`
      or any other agent, and do not write brief files. Make the changes yourself with
      your own file-editing tools, then report.
TASK: <id> — <one line>
OBJECTIVE: <what must exist after this task>
RELEVANT FILES: <exact paths; read only these unless blocked>
CONTEXT: <interfaces/contracts to use, with paths>
MUST: <bullets>
MUST NOT: <bullets — always: no commits; no git staging; no branch switching; no push; no files outside the list
          without stating why; no feature removal; no Room schema/version
          changes unless this brief explicitly specifies them together with
          the Room migration>
TESTS: <tests to add/update, or existing tests that must pass unmodified;
       exact command; run them and include results in REPORT>
ACCEPTANCE: <objective, checkable criteria>
REPORT: files changed, commands run with results, anything left undone.
```

Briefs must be self-contained: the coder has no chat history. Always keep the ROLE
block as a second guard next to the `OPENCODE_DISABLE_CLAUDE_CODE=1` rule: without
both, OpenCode can load this CLAUDE.md and the delegate skill and re-delegate the brief
to another OpenCode run.

## Commits

- One task = one atomic, reviewable, buildable commit. Stage by explicit path.
- Format: `<type>(playback): <short description>`, task id in the body.
- Commit only after verification passes.
- Planning/docs changes: record decisions in the constitution or `HANDOFF.md` as they happen,
  but don't commit after every answer. Commit granularity:
  - All pre-plan docs (discovery, constitution decisions, spec, clarify) = one commit.
  - The plan phase = its own commit.
  - After that, one commit per task.

## Status format (end of every phase / milestone)

```
CURRENT PHASE:
OBJECTIVE:
FILES ANALYZED:
FILES EXCLUDED:
FINDINGS:
GRAPHIFY:
SPEC STATUS:
DELEGATED TASK: <id or NONE>
VERIFICATION: <what ran and results; NOT RUN items with reason>
MANUAL CHECKS FOR OWNER: <list or NONE>
RESULT: PASS / BLOCKED / NEEDS CHANGES
NEXT STEP:
```

Do not move on unless RESULT is PASS.
