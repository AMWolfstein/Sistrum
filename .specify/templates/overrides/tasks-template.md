# Tasks: [FEATURE NAME]

Input: spec.md, plan.md and necessary supporting design artifacts in this directory.
Follow `.specify/project-policy.md`. Replace all bracketed examples before committing.
This is a planning artifact, not authorization to start implementation.
Read plan.md's Repository evidence before choosing task paths/dependencies/checks.
Reuse that session's inspected map while source is unchanged; refresh if source
changed or no planning-session map was inspected. Disclose/manual-trace uncovered
areas instead of inventing repository facts.

## Execution rules

Sequential tasks, using allocated M###-T### IDs; no parallel markers. One planning commit
contains this list. The **first action in every task**, including the first task, nested/standalone TOR and
[FINAL], is `python3 workflow/codegraph/code_tools.py update`. Inspect its scope and
coverage before task analysis or edits; stop/report if refresh fails. There is no
dedicated map task or map-only commit. Keep `.work/` ignored and record concise
refresh evidence in `task-log.md` within the task's single commit.

Each task has at most three review rounds and one commit only after checks/review
pass. Add owner extras as TOR### before [FINAL]. The last task reviews all T/TOR work.

## Implementation

[Reserve the first task with workflow/work.py task reserve --mission
ACTUAL_MISSION --title "Concrete outcome"; use its actual checkbox/ID here.
Do not commit example IDs as real tasks.]
  - Start: refresh the map first, inspect coverage and relevant boundaries.
  - Acceptance: [observable behavior/result].
  - Validation: [actual targeted commands and baseline expectations].
  - Dependencies: planning commit.

[Reserve additional M###-T### tasks as needed, with the same start/outcome/paths/checks/dependencies.
Every task begins with its own map refresh, within that task's single commit.]

## Owner extras

[Initially none. Reserve M###-TOR### extras from the global TOR pool here or in the required dependency
position, always before the final task. Use the same first-action map refresh,
acceptance/paths/checks and review/commit rules. Do not invent extras during planning.]

## Final cumulative review

[Reserve the final task with --final; use its allocated M###-T### [FINAL]
checkbox here. Review all completed mission tasks/extras against spec.md/plan.md.]
  - Start: refresh the map first and inspect its coverage/boundaries.
  - Acceptance: cumulative review passes; all relevant actionable findings resolved.
  - Paths: all files changed for this feature plus `task-log.md`, `tasks.md`.
  - Validation: [full feasible feature-appropriate commands]; cumulative diff and
    boundary review; APK/device checks only when authorized.
  - Dependencies: every implementation and TOR task.
  - Review/fix loop: at most three rounds; all fixes/evidence in one final commit.

## Dependencies and evidence

[State actual dependency order, affected configurations and known limitations.]
Log task/map/review/check outcomes in `task-log.md`. Only mark a task complete after
its required checks/review pass. Stop and notify the owner after a third dirty
review; do not start a fourth round or silently skip findings.

After real independent PASS and checks, use task complete and validate. Include
registry, root TASK_HISTORY.md and the task record in the same task commit.
