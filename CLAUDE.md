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
- Flavors `dev` / `prod`. Application IDs (since T062a, 2026-10-13): prod `com.amwolfstein.sistrum`, dev
  `com.amwolfstein.sistrum.dev`; Kotlin packages `com.amwolfstein.sistrum.*`. Exception: `player/FfmpegDecoder.kt`
  stays `me.misa198.airmedy.player` (JNI symbols in `cpp/**`) until 003 removes it. The old
  `me.misa198.airmedy.dev` is the owner's previous daily app and stays installed.
- minSdk 31, targetSdk 36, JDK 21.

## Build and test

- Variants (owner 2026-10-13, T062b): only `devQa` (tests), `devRelease` (the owner's daily app) and `prodRelease`
  (release). devDebug, prodDebug and prodQa are disabled.
- `./gradlew :androidApp:assembleDevQa`
- `./gradlew :androidApp:assembleDevRelease` (unsigned unless MOBILE_KEYSTORE_* env vars are set;
  sign locally with zipalign + apksigner and the saved key `~/.config/sistrum/signing/sistrum-dev.keystore`, never a
  different one: cert SHA-256 `d6b64f6f…f324b2f6`, details in that folder's README.txt; owner 2026-10-15. A different key
  makes Android refuse the update over the owner's installed app. Don't edit signingConfigs)
- `./gradlew :androidApp:testDevQaUnitTest :sharedLogic:testAndroidHostTest`
- `./gradlew :androidApp:lintDevQa`
- **Never run instrumentation, install test APKs, or force-stop anything against the owner's daily
  apps `com.amwolfstein.sistrum.dev` and `me.misa198.airmedy.dev`** (or their `.test` instrumentation packages).
  All on-device tests use the separate test build `com.amwolfstein.sistrum.dev.qa` (build type `qa`, label
  "Sistrum QA"; ADR-007) and its instrumentation package `com.amwolfstein.sistrum.dev.qa.test`. `verify.sh --instrumented` enforces this.
  Never install `devRelease` on the device yourself: it has the daily app's ID.
- With the test build: `./gradlew :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest`
  (`testBuildType = "qa"`), check both APKs' IDs (`aapt2 dump packagename <apk>`), install them with
  `adb install -r` (`-t` for the test APK), and run classes one at a time:
  `adb shell am instrument -w -r -e class <Class> com.amwolfstein.sistrum.dev.qa.test/androidx.test.runner.AndroidJUnitRunner`
  Keep the screen awake (`adb shell svc power stayon usb`). Don't use Gradle
  connected* tasks: they uninstall the app afterwards.

## Local, untracked build inputs

- `local.properties`: `sdk.dir=...` and `python3=<venv python>` (fonttools + brotli).
  The icon font `androidApp/src/main/res/font/material_symbols_rounded.ttf` is generated
  at build time by `tools/font-subset/subset_font.py` from the glyphs used in
  `ui/components/MaterialSymbols.kt`. Icons are font glyphs, not vector drawables.
- FFmpeg shared libraries in `androidApp/src/main/jniLibs/` are not in git. If a build
  fails with "FFmpeg artifacts are missing", run `bash scripts/build-ffmpeg-android.sh arm64-v8a`.
- Build outputs are ignored through `.git/info/exclude`; the tracked `.gitignore` (since 002 import 24) only lists audio
  file types (test audio lives in `~/.cache`, never in git).
  Stage files by explicit path only.
- Graphify: only `graphify-out/graph.json` and `graphify-out/.graphify_analysis.json` are
  tracked. Everything else in `graphify-out/` (build/, cache/, manifest.json, .graphify_root, lock
  and temp files) is machine-local and never committed.

## Playback

- Two engines behind the `player/engine/PlayerEngine` seam, driven by `PlaybackCoordinator`:
  - **Media3** (`player/media3/`, default since T062c): `Media3Engine` + `Media3PlayerFactory`, float sink with our
    DSP chain and session limiter (`FloatChainAudioSink`), crossfade, normalization (`player/normalization/`), extra
    decoders through `player/decoders/` (DecoderProvider registry), offline loudness analysis (`analysis/`).
  - **Native** (`LegacyNativeEngine` over `player/FfmpegDecoder.kt`, `androidApp/src/main/cpp`, NDK/CMake, FFmpeg →
    float PCM → AAudio): kept as the fallback until spec 003 removes it.
- Switch: Settings → Developer → "Playback engine" (`EngineSelectionPreferences`); no saved choice = Media3. Applies at
  the next playback start.
- Device tests and measurements only on the `.qa` build (see Build and test); `regression/Regression001MeasurementTest`
  holds the SC-003/006/013/014 harness (`specs/001-media3-migration/research/regression-001.md`).
- `player/` and native code may change ONLY through migration tasks defined in `specs/` and following
  `.specify/memory/constitution.md`. Outside those tasks: compile fixes only.

## Library scan

- MediaStore-based: `sync/MediaStoreLibraryScanner.kt`, `sync/EmbeddedTagReader.kt`.
  Room database in `sync/SyncDatabase.kt`.
- Bump `CurrentMetadataSchemaVersion` whenever parsing behavior changes for
  already-scanned files.
- EmbeddedTagReader opens each file once per tag read (large scan speedup). Keep it that way.
- For untagged files MediaStore reports the folder name ("Music") as album; intentional. Exception (T033a,
  owner 2026-10-06): when that album equals the file's parent folder name and the file has its own album tag,
  the tag wins. Artist and album artist fall back to embedded tags only when MediaStore has none.

## Known state

- Instrumented suite has a set of test-side failures (missing menu host, stale text
  expectations, tests that set content twice, chart library crashing on empty data).
  Only fix newly failing tests.
- `lintDevQa`: 0 errors since D-22 (2026-10-14; MissingTranslation came from the removed locales).
- Cast / output-switcher button is hidden below Android 14 on purpose.
- Volume normalization works on the Media3 engine only (the setting says so on Native). Mood Radio is still off: its
  analysis returns in spec 002 (M4). Don't patch either on the native engine.
- Media3 crossfade-off CPU is ~1.7× native in the short proxy (SC-013 not met for 001; owner deferred it to the
  cleanup/perf phase; parked attempt in `specs/001-media3-migration/research/park/`).

## Localization

- Two languages (owner, 2026-10-14, D-22): English (`values/strings.xml`, the only English source; no `values-en`)
  and Arabic (`values-ar`, must stay complete: `ArabicTranslationCompletenessTest`). The 11 unreviewed airmedy locales
  were removed; `res/xml/locales_config.xml` lists en, ar. A language is added back only complete and reviewed.
  Agent `translation-reviewer` checks Arabic meaning, placeholders and plurals when strings change.
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

**Codex (`codex exec`, via `/delegate-task`) = coder only** (owner, 2026-10-13; OpenCode before). Implements one bounded task
per brief. Never decides architecture, API contracts, scope, feature removal,
or when legacy code is deleted. Never commits.

## Rules

- **Autonomous mode (owner, 2026-10-14; spec 002 Phase B onward, also 003 and bug-fix phases):** Claude writes the
  spec, plan and tasks only. Codex implements, runs the gate (`verify.sh --quick` per task, full gate + `.qa` phone
  check in each milestone's last task), self-reviews with the migration-guard checklist, refreshes the graph, commits
  and pushes every task itself (`AGENTS.md` "Autonomous mode"), one fresh Codex session per task via
  `.claude/skills/delegate-task/scripts/milestone-run.sh`. Claude checks the commit log and HANDOFF at milestone ends.
- **Whole milestone by Codex (owner, 2026-10-15; 002 M2 onward):** `.claude/skills/delegate-task/scripts/milestone-auto.sh`
  runs the tasks, the milestone review (one fresh Codex `MODE: REVIEW` session per reviewer checklist from
  `.claude/agents/`, reports in `specs/<feature>/review/<M>/`), the `<last>-fix` task, the phone-check task, phone-log
  triage + `<last>-fix2`, and a read-only Claude Haiku final check (`FINAL-CHECK.md`). Claude is not involved; a stop
  waits for the owner. The Claude-run review below applies only when the owner asks for it.
- **Milestone review (owner, 2026-10-14):** run each milestone's tasks except its last (full gate + phone check) task;
  then run `silent-failure-hunter`, `pr-test-analyzer`, `kotlin-reviewer` and `spec-traceability-auditor` in parallel on
  the milestone's commit range (`comment-analyzer` too when cheap), plus `audio-correctness-reviewer` when the range
  touches decoders, the sink, DSP, crossfade, gapless or CUE (M1, M2, M5; M4b now in 004) and `port-fidelity-checker` when it adds
  ported code (M1 imports, M5, every M4b part in 004); for milestones with `[HIGH-RISK]` tasks or Room migrations use skill
  `santa-method` on those diffs. After the last (phone-check) task, run `phone-log-triage` on the gate's `logs:`
  directory recorded in HANDOFF; confirmed CRITICAL/HIGH there become a `<last id>-fix2` task. Check each finding yourself; confirmed CRITICAL/HIGH ones become one fix task
  (`<last id>-fix`, e.g. `T013-fix`) added before the last task in tasks.md; then run the fix task and the last task.
  Record the review result in HANDOFF.
  Where this conflicts with the rules below (coder never commits, Claude re-runs every gate), autonomous mode wins.
- Spec Kit (`specs/`, `.specify/`) is the planning source: spec, plan and tasks come from
  the `speckit-*` skills (specify, clarify, plan, tasks, analyze).
- Don't use superpowers' planning, execution or subagent skills for this migration
  (brainstorming, writing-plans, executing-plans, subagent-driven-development,
  dispatching-parallel-agents, test-driven-development, using-git-worktrees,
  finishing-a-development-branch). Its debugging and verification skills
  (systematic-debugging, verification-before-completion) are fine.
- Feature code is written ONLY by the coder, via `/delegate-task`. Do NOT
  use Spec Kit's `implement` command. You may make trivial review fixes (imports, typos) yourself
  when re-delegating costs more; say so in the status report.
- Always pass `--model` with a model chosen per the Models section below. Use
  `--session <id from result.json>` for review follow-ups on the same task.
- Dispatch ONLY through `dispatch.sh`, which runs `codex exec` via `codex-run.sh`: workspace-write
  sandbox (repo + Gradle/Kotlin/Android caches), approvals never, `AGENTS.md` as the only project doc
  (CLAUDE.md and `.claude/` are not loaded), so the coder can't re-delegate or run Graphify.
  The coder's own instructions are in `AGENTS.md`. The old OpenCode path (`opencode-go/*` models,
  `ses_*` sessions) still runs through the relay with `OPENCODE_DISABLE_CLAUDE_CODE=1`.
- Max 3 review rounds per task; then stop, record the problem in `HANDOFF.md`, ask the user.
- Memory check before every dispatch (fresh or follow-up) and every `verify.sh` gate run (owner, 2026-10-06):
  1. Stop leftover daemons: `./gradlew --stop` and `pkill -f '[K]otlinCompileDaemon'` (the brackets keep
     pkill from matching its own shell).
  2. Read `MemAvailable` from `/proc/meminfo`. If it is below 2 GB, don't start: tell the owner instead.
  If the memory reaper kills a run anyway, report it, wait 20 minutes, re-run this check and resume on your own
  (coder: same session); still below 2 GB → wait another 20 minutes; after 3 failed waits, stop and tell the owner
  (owner, 2026-10-13).
- Run a milestone's tasks back to back (`/delegate-task` → `/verify-task` → commit → push → next task), without
  pausing between tasks. Stop ONLY when: (1) a milestone ends (status report + owner approval), (2) a MANUAL
  task needs the owner or the device, (3) a task fails 3 review rounds, (4) an architectural problem requires
  changing an ADR or the spec, (5) the model quota runs out, (6) anything would touch the owner's daily app.
  Runtime behaviour changes (e.g. T011) are not a reason to stop: characterization tests, `migration-guard` and
  the milestone parity pass cover them. Log each task's result and review rounds in `HANDOFF.md` instead of
  reporting to the owner per task (owner, 2026-10-05).
- A task is DONE only after: diff reviewed against the brief, architecture checked
  against the spec/ADRs, relevant tests run, build run. Never on the coder's word,
  "it compiles", or "looks right".
- Never invent test, build, or Graphify results. Not run = say NOT RUN and why.

## Tooling (`.claude/`, tracked)

- `/delegate-task <id>`: builds the brief from `tasks.md`, picks the model, dispatches through
  `.claude/skills/delegate-task/scripts/dispatch.sh` (Codex via `codex-run.sh`; refuses banned models). Use it for every dispatch and follow-up.
- `/verify-task <id>`: diff review, `migration-guard` when needed, then
  `.claude/skills/verify-task/scripts/verify.sh --files '<task files>'` (refuses when Kotlin files
  outside the task's list changed; then tests, build, optional device tests, graph refresh).
- New machine: `docs/dev-setup.md`.
- Ported from ECC (MIT, `docs/ECC-LICENSE.md`, adapted, all read-only): agents `silent-failure-hunter`,
  `pr-test-analyzer`, `kotlin-reviewer`, `comment-analyzer` (milestone review), `code-simplifier`, `refactor-cleaner`
  (cleanup phases, 003), `opensource-sanitizer` (before releases); skills `change-impact`, `module-regression`
  (`docs/REGRESSION.md`), `loop-design-check` (runner/AGENTS.md changes), `santa-method` (high-risk review),
  `inherit-legacy-style`, `kotlin-coroutines-flows`, `compose-multiplatform-patterns`, `accessibility`,
  `brownfield-retrospective` (full review), `production-audit` (release), `security-scan` (AgentShield, scan-only).
- Ported from agency-agents (MIT, `docs/AGENCY-AGENTS-LICENSE.md`, adapted, read-only): agents
  `codebase-archaeologist` (cross-era drift: units, fallbacks, async ordering, duplicates, dead code; after M1, full
  review, before 003) and `ui-finish-gate-reviewer` (UI phase: design contract + PASS/HOLD); the mobile release
  checklist lives in skill `production-audit`; minimal-change rules in AGENTS.md step 1.
- Own read-only agents (2026-10-14): `port-fidelity-checker` (port vs WaxFlow Go / port/waxflow / Choir source),
  `audio-correctness-reviewer` (PCM formats, channels, gapless, DSP math, render-thread allocation),
  `spec-traceability-auditor` (FR/SC → test → commit, tasks.md ↔ log ↔ HANDOFF), `phone-log-triage` (per-class logcat
  that `verify.sh --instrumented` saves next to `instr-*.log`), `translation-reviewer` (Arabic vs English strings).
- Full-review agents (2026-10-14; partly from the owner's sistrum-review-agents pack, merged): `android-security-reviewer`
  (untrusted files through parsers, exported components, network, secrets, R8, dependencies), `playback-lifecycle-reviewer`
  (PlaybackController contract table, races, MediaSession/foreground service, focus, process death; writes owner manual
  checks), `performance-reviewer`, `architecture-boundary-reviewer`, and `review-synthesizer` (verifies, de-duplicates
  and merges all reports into one fix list). Full review: run reviewers per module in parallel batches, reports to
  `specs/review/full/<agent>.md`, then `review-synthesizer`; confirmed fixes become Codex tasks.
- From claude-code-templates (MIT, `docs/CLAUDE-CODE-TEMPLATES-LICENSE.md`): skill `mobile-design-android` (UI phase).
  Quality tooling (report-only; first task of spec 003, moved from 002 T013a, D-26): detekt + Compose rules, R8
  `usage.txt`, lint UnusedResources, Kover — commands in `docs/QUALITY.md`; Claude wires them into `verify.sh` after it.
- `migration-guard` agent: read-only test-integrity + contract review; only for tasks that touch
  tests, `player/PlaybackController.kt`, or Room.
- Hooks (`.claude/hooks/git_hooks.py`, PreToolUse on Bash): `guard` blocks `git add -A`/`.`/`-u`,
  `commit -a`, `stash`, `clean`, `reset --hard`, force pushes, pushes to or merges into `main`,
  `gh pr merge`; `graph` refreshes the graph before a commit when app code changed and blocks
  until `graphify-out/graph.json` and `.graphify_analysis.json` are staged.
- context7 MCP (`.mcp.json`, pinned): current Media3/ExoPlayer and AndroidX docs.

## Models

Codex models (owner, 2026-10-13; `--effort` sets the reasoning level):
- Default: `gpt-6.1-sol`, `--effort medium`.
- Hard tasks (crossfade, MediaSession, anything flagged high-risk in `tasks.md`): `gpt-6.1-sol`, `--effort high`
  (near-Astra coding results at about a fifth of the cost; owner, 2026-10-13).
- BLOCKED tasks (owner, 2026-10-16): the runner retries once in a fresh `gpt-6.1-sol` session at high effort, then
  once on `gpt-6-astra` (high); only then it stops and Claude (or the owner) finishes the task and restarts the runner.
  `gpt-6-astra` is used nowhere else (`dispatch.sh` refuses it unless the runner's last attempt sets
  `SISTRUM_ALLOW_ASTRA=1`).
- OpenCode (old path, only if the owner asks): never `opencode/*-free` or `opencode-go/glm-5.3`.

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
- Scope: source code of every source set under `androidApp/src/`, `sharedLogic/src/` and (spec 002) `codecs/src/`,
  `metadata/src/`, `analysis/src/`, `dsp/src/` (today
  main, test, androidTest, qa — no code — commonMain, commonTest; future dev/prod/debug/… sets are
  included automatically). Node `source_file`s are repo-relative, so `androidApp/src/test/…`,
  `androidApp/src/androidTest/…` and `sharedLogic/src/commonTest/…` mark test code. The repo-root
  `.graphifyignore` (allowlist) excludes everything else: docs, specs, scripts, tools, `res`,
  `resources`, `assets`, `composeResources`, `jniLibs`, `cpp/third_party`, build outputs and generated code. Scope list duplicated in `.claude/hooks/graph_refresh.py`;
  change both together.
- Refresh with exactly `python3 .claude/hooks/graph_refresh.py auto` (no LLM, ~7–8 s). It refreshes only
  when a source file in the scope (or `.graphifyignore`) differs from HEAD; docs/specs/scripts never
  trigger it. It runs `graphify extract . --code-only --out graphify-out/build` after deleting that build
  dir's previous graph (extract otherwise "heals" from the old graph.json and never prunes external stub
  nodes, so in-place refreshes drift), keeps the AST cache warm there, and copies `graph.json` and
  `.graphify_analysis.json` into `graphify-out/`. Same sources → same graph.
  If the refreshed graph equals HEAD's except the top-level `built_at_commit` field (graphify writes
  the current HEAD there on every run), it resets both graph files to HEAD so nothing is staged.
  `verify.sh` and the commit hook both call it. Do NOT use `graphify update` (with or without a path)
  or `graphify extract androidApp/src/main` (the old, narrower scope).
- When it reports `graph: changed`, stage `graphify-out/graph.json` and
  `graphify-out/.graphify_analysis.json` by explicit path, in the same commit as the task's files.
- Never commit Graphify's cache. Never hand-edit graph files.
- Don't review graph diffs line by line; they are generated. The `.gitattributes`
  entry marks them as generated so diffs stay collapsed.
- The coder never runs or edits Graphify; exclude graph files from its briefs.
  The Graphify hook note that asks to include the graphify rule in subagent prompts
  does not apply to coder briefs.

## Delegation brief template

```
ROLE: You are the IMPLEMENTER (coder), not the orchestrator. The "Delegated workflow"
      section in CLAUDE.md describes the orchestrator's job and does NOT apply to you.
      Do not load or use the opencode-delegate skill, do not run `codex`, `opencode`, `relay.mjs`
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
block as a second guard next to the dispatch isolation: without both, the coder could load
this CLAUDE.md or a delegate skill and re-delegate the brief to another agent run.

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
