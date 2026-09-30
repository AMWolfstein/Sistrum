---
name: delegate-task
description: Delegate one Spec Kit task (by id, e.g. T012) of the playback migration to OpenCode. Fills the CLAUDE.md brief template from tasks.md and the spec, picks the model per the Models section, and dispatches with OPENCODE_DISABLE_CLAUDE_CODE=1. Use whenever a task from specs/<feature>/tasks.md is ready to implement, and for review follow-ups on a delegated task.
argument-hint: "<task-id> [--followup]"
---

# /delegate-task <task-id>

Implementation in this repo happens only through OpenCode. This skill turns one task from
`specs/<feature>/tasks.md` into a brief and dispatches it. Review and verification come after,
with `/verify-task`.

## 1. Find the task
- Read `specs/<feature>/HANDOFF.md` first, then the task line in `specs/<feature>/tasks.md`
  (format: `- [ ] T012 [P] [US1] Description with file path`). If the id is missing or already
  checked off, stop and tell the user.
- Read only what the task needs: its section of `spec.md` / `plan.md`, relevant ADRs, and the
  files named in the task. Use `graphify query` / `graphify explain` to find callers and
  contracts instead of reading broadly.

## 2. Pick the model (CLAUDE.md "Models")
- `opencode-go/deepseek-v4-pro` if the task is crossfade, MediaSession, or its line carries
  `[HIGH-RISK]`.
- Otherwise `opencode-go/deepseek-v4.1-flash`.
- `opencode-go/glm-5.3-flash` only after the default has failed twice on this same task.
- Never `opencode/*-free` or `opencode-go/glm-5.3` (dispatch.sh refuses them).

## 3. Write the brief
Copy the template from CLAUDE.md "Delegation brief template" exactly, ROLE block first, and fill
every field. The brief must stand alone: the coder sees no chat, no CLAUDE.md, no skills.
- RELEVANT FILES: exact paths; never `graphify-out/` or `.claude/`.
- CONTEXT: the interfaces/contracts to use, with paths and signatures.
- MUST NOT: always include the fixed list from the template.
- TESTS: the tests the spec requires (you choose them, never the coder) and the exact command,
  e.g. `./gradlew :androidApp:testDevDebugUnitTest --tests '<Class>'`. For a high-risk area
  whose tests were committed earlier, add "make these tests pass without modifying them".
- ACCEPTANCE: objective, checkable criteria.
Save it outside the repo: `${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/<task-id>/brief-<n>.md`.

## 4. Dispatch (background)
```bash
D="${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/<task-id>"
bash .claude/skills/delegate-task/scripts/dispatch.sh --brief "$D/brief-1.md" \
  --model <model> --out-dir "$D/run-1"
```
Run it with `run_in_background: true`; the run is done when `$D/run-1/result.json` exists.
`dispatch.sh` always sets `OPENCODE_DISABLE_CLAUDE_CODE=1`; never call `relay.mjs` directly.

## 5. Follow-ups (`--followup`)
For review rounds on the same task, write a delta brief (only what must change) and resume the
same session, still through dispatch.sh:
```bash
bash .claude/skills/delegate-task/scripts/dispatch.sh --brief "$D/brief-2.md" \
  --session <sessionId from result.json> --out-dir "$D/run-2"
```
Maximum 3 review rounds per task; then stop, record the problem in HANDOFF.md, ask the user.

## 6. After the run
Read `result.json` (`status`, `finalMessage`, `touchedFiles`). Never trust the report: continue
with `/verify-task <task-id>`. Record the task id, model, session id and brief path in HANDOFF.md.
