---
name: "speckit-implement"
description: "Execute the implementation plan by processing and executing all tasks defined in tasks.md"
compatibility: "Requires spec-kit project structure with .specify/ directory"
metadata:
  author: "github-spec-kit"
  source: "templates/commands/implement.md"
---

## Repository execution policy (local adaptation)

Portable entrypoint: read and follow this file in any agent. References to skill
names or slash/dollar commands below identify other local SKILL.md files; invoke
them using the host's facilities, or read them directly. No specific host is required.

Before using this skill, read `.specify/project-policy.md` and
`.specify/memory/constitution.md`. The owner's current instructions take priority.
Before writing or editing any artifact/code, apply `.agents/skills/ponytail/SKILL.md`
in full mode; include its review checklist within existing rounds, not an extra loop.
The local policy overrides conflicting upstream defaults: one planning commit,
map refresh first in every task, sequential task/review/fix loops, at most three review rounds,
one commit per completed task, TOR### extras and one final cumulative-review task.
Do not add approval gates, automatic commits at intermediate planning stages,
parallel task execution, or unbounded implement/converge cycles. A planning-only
request must not start implementation. The retained audit/navigation skills stay
standalone. These additions are local adaptations, not upstream GitHub requirements.

For planning, follow the **Map-first planning** section of project-policy.md:
refresh/inspect the map before repository investigation or technical plan/task
drafting, query it and read the related source, and record repository evidence in
plan.md. Reuse that map across planning skills while source is unchanged; refresh
after source changes or at a new planning session. If map generation/coverage is
limited, disclose it and use verified manual source tracing; never invent coverage.
This does not replace the mandatory map refresh at the start of every task.



## User input and prerequisites

For a small standalone modification without an explicitly requested plan, use
`.agents/skills/quick-task/SKILL.md` instead of feature prerequisites below. It
creates a direct TOR record, not a fake feature/spec/plan. Existing feature extras
continue as TOR tasks in their authorized feature.

Consider the user's current instructions, especially planning-only requests.
Run `.specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks`
from the repository root; read the feature spec.md, plan.md, tasks.md and relevant
supporting artifacts. Use `SPECIFY_FEATURE_DIRECTORY` to select an existing feature
if the ignored local pointer is missing. Do not infer feature ownership from the
current Git branch name. Preserve unrelated changes.

## Sequential execution

1. Confirm completed planning artifacts are committed together once. If only a
   plan was requested, stop there. Otherwise identify the first unfinished task;
   do not restart completed tasks or rebuild the plan for each task.
2. At the start of **every** allocated task, beginning with M###-T001 and including every TOR
   and [FINAL] task, first run `python3 workflow/codegraph/code_tools.py update`.
   Inspect its scope/coverage and relevant architecture/boundary evidence before
   task analysis or edits. If the refresh fails, stop this task and report the
   limitation; do not silently use an old map.
3. Record concise refresh evidence with the task. The map stays ignored in
   `.work/`; no dedicated map task or map-only commit is created.
4. Read acceptance criteria and exact paths. Implement only the current task.
   Format/check/test affected code using actual project commands. Compare failures
   with the baseline and report unavailable coverage. No APK without authorization.
5. Obtain a read-only independent review using `.agents/reviewers/task-reviewer.md`.
   Fix/revalidate and review again when needed. Record the reviewer, round and
   result in `task-log.md`. Three review results maximum per task; after a third
   NEEDS_FIXES/BLOCKED result stop and notify the owner with remaining issues.
   Never hide an unavailable independent reviewer behind a self-review PASS.
6. After checks/review pass, run `python3 workflow/work.py task complete ACTUAL_ID --reviewer NAME
   --round N --checks "actual check results"`, then validate. Commit code, tests,
   registry, TASK_HISTORY.md, task-log and checkbox together exactly once. No intermediate WIP/fix/review
   commits. Continue sequentially to the next task; no parallel `[P]` execution.
7. Owner extras use `task reserve --mission ACTUAL_MISSION --tor` to become globally numbered M###-TOR### tasks, executed through
   the same loop and committed once each before [FINAL]. Do not reset existing
   review counters or silently expand the current task.
8. Execute the final cumulative-review task last. Review all T/TOR changes against
   spec/plan with full feasible checks. Fix/revalidate/re-review inside the same
   bounded loop; put every final fix and its completion evidence in one commit.
   Do not add an unbounded sequence of convergence tasks.

## Outcome

Report completed tasks and commit IDs, actual checks/review outcomes, remaining
findings and limitations. Publish only when authorized. No automatic merging,
force-pushing, fingerprints, legacy work.py gates or additional approvals.
