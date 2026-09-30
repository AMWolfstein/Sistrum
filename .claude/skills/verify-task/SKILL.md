---
name: verify-task
description: Final gate for a task OpenCode implemented in the playback migration. Reviews the diff against the brief, calls the migration-guard reviewer when tests, PlaybackController or Room are touched, re-runs tests and build yourself, refreshes the Graphify graph, stages by explicit path and prints the status block. Use after every /delegate-task run, before any commit.
argument-hint: "<task-id>"
---

# /verify-task <task-id>

A task is DONE only when this gate passes. The coder's report never counts as evidence.

## 1. Review the diff against the brief
- Load the brief and `result.json` for the task (paths are in HANDOFF.md, under
  `${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/<task-id>/`).
- `git status --short` and `git diff` on the touched files. Check: everything the brief
  requires is there, nothing outside RELEVANT FILES changed without a stated reason, no
  feature removed, architecture matches the spec/ADRs.
- If the diff touches test files, `player/PlaybackController.kt`, or Room
  (`sync/SyncDatabase.kt`, entities, DAOs, migrations), spawn the `migration-guard` agent with
  the task id, the brief path and the list of changed files. A NEEDS CHANGES verdict from it
  blocks the task.
- Problems: write a delta brief and run `/delegate-task <task-id> --followup` (max 3 rounds).

## 2. Run the gate yourself
```bash
bash .claude/skills/verify-task/scripts/verify.sh \
  --files '<RELEVANT FILES and test files from the brief, space-separated>' \
  [--tests '<filter>'] [--lint] [--instrumented <FQCN>[,<FQCN>]]
```
- `--files` is required. If any Kotlin file outside that list is modified, deleted or new, the
  script prints `REFUSED`, lists the files and exits 3 without running anything. Then STOP:
  do not continue this skill, do not stage or commit. Treat it as NEEDS CHANGES: review the
  extra files, and either revert them (`git checkout -- <file>` for tracked, delete untracked
  ones the coder created) or send a delta brief, then run `/verify-task` again. Never widen
  `--files` just to make the check pass; a file belongs in the list only if the brief names it.
- Use the TESTS from the brief as `--tests` filters, plus the full suites before a
  milestone commit. Pass `--instrumented` only for device tests the spec requires.
- It prints one line per step (PASS / FAIL / NOT RUN) and `GATE: PASS|FAIL`, then refreshes
  the graph. Read only that output; open a log only for a FAIL.
- Anything that needs real listening or hardware (Bluetooth, headset, audio focus, process
  death) is never PASS here: list it under MANUAL CHECKS FOR OWNER.

## 3. Stage by explicit path
Only after `GATE: PASS` and a clean review:
```bash
git add <each task file> graphify-out/graph.json graphify-out/.graphify_analysis.json
```
Never `git add -A` / `.` (the git guard hook blocks it). Check the task off in `tasks.md` and
stage that too. The commit itself follows CLAUDE.md "Commits"; the graph hook re-checks the
graph when you commit.

## 4. Report
Print the CLAUDE.md status block. Mark every step you did not run as NOT RUN with the reason.
Never write PASS for a result you did not see.
