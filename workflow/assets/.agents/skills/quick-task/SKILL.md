---
name: quick-task
description: "Execute a small low-risk modification as a direct TOR task without spec/plan generation, retaining map-first execution, Ponytail, checks, independent review (max three rounds) and one commit."
---

# Direct TOR task

Read root AGENTS.md, `.specify/project-policy.md` and Ponytail. Use for a small,
isolated modification when no planning was requested and no high-risk boundary is
affected. No new feature spec.md, plan.md, feature pointer or planning commit.
Read-only questions/audits/navigation and Git-only fetch/push do not create tasks.

1. **First action:** run `python3 workflow/codegraph/code_tools.py update` and inspect
   scope/coverage before task analysis or edits. A failed refresh stops this task;
   record/report the limitation rather than substituting an old map. Keep caches
   ignored. Reuse the map for unchanged-code inspection during this task.
2. Use `python3 workflow/work.py task reserve --title "Concrete task name"`
   for a standalone task. For an active-feature extra add `--mission M### --tor`.
   Use its returned ID/path: the shared TOR pool forbids reusing a number across
   standalone and nested IDs. Never assign an ID by hand. Reconcile latest Git
   registry first. For the same unfinished task reuse its ID/review count.
3. No active authorized feature: fill the allocated `.specify/quick-tasks/TOR###.md` using
   `.specify/templates/quick-task-template.md`. Fill only the concrete request,
   baseline, expected paths, acceptance, checks, map evidence and review outcomes.
   If this is an owner-requested extra inside an active authorized feature, record
   it in that feature's tasks/log instead, before its final task. A stale feature
   pointer or a completed plan does not make an unrelated request a feature extra.
4. Read the affected code/callers/config/tests; apply Ponytail full mode. Implement
   only the requested task. Use meaningful targeted checks. Documentation-only
   edits need link/template/diff checks, not artificial app tests/APK builds.
5. Obtain an independent read-only review using
   `.agents/reviewers/task-reviewer.md`. Supply the TOR record, request, diff,
   baseline and actual check evidence. No spec/plan is required. Include Ponytail
   review in this same round. Fix/revalidate and review again when needed.
6. Three results maximum per task. PASS in rounds 1–3 allows completion; if round
   3 remains NEEDS_FIXES/BLOCKED stop and report remaining issues/uncommitted state.
   Do not begin round 4, reset the count or claim self-review is independent PASS.
   If essential review/tooling is unavailable, report the blocker; do not mark done.
7. After acceptance/checks/independent review pass, run `python3 workflow/work.py
   task complete ACTUAL_ID --reviewer NAME --round N --checks "actual results"`,
   then `validate`. Commit edits, record, registry and TASK_HISTORY.md together once. No separate task-creation, map-update,
   WIP, fix, review or final-review commit. Do not guess the commit's own hash in
   its record; Git history supplies it. Push only when authorized.

There is **no separate final task for one standalone TOR**: review its complete
diff in its existing loop. Preserve unrelated edits. If it grows beyond a small
low-risk task, stop expansion, retain current evidence and move to full Spec Kit
planning, identifying what remains; never relabel a high-risk change to skip a plan.
