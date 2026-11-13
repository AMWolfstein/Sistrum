# Project Constitution

## I. Preserve behavior and scope

Current owner instructions define the goal. Preserve observable behavior outside
that scope, particularly audio playback, decoder/fallback selection, rendering,
stored data and native contracts. Never remove uncertain dynamic/native APIs on
the basis of missing static references. Keep generated/vendor code protected.

## II. Plan substantial work

Use GitHub Spec Kit spec → plan → tasks for substantial/high-risk changes or when
requested. Small isolated low-risk edits use standalone TOR tasks without spec/plan generation. Planning-only
requests stop at the plan. Before repository investigation/technical planning,
refresh and inspect the map, use its queries and read the relevant source/build
boundaries. Record repository evidence/limitations in plan.md; reuse the map while
source stays unchanged during that planning session. On missing coverage, disclose
it and use verified manual source evidence rather than assumptions. Completed
planning artifacts form one commit.

## III. One task, one reviewed commit

Follow [project policy](../project-policy.md): missions/tasks use allocated M###-T### IDs; the first
action of every task (including TOR and final review) is map refresh, followed by
sequential implementation/check/review/fix
loops and one commit after each task passes. After three unsuccessful review
rounds stop and report. Feature extras are M###-TOR###; their numeric TOR pool is shared with standalone TOR###. The final task reviews the cumulative
change and contains all its fixes in one commit. A standalone small TOR follows
the same map/check/independent-review/three-result/one-commit loop using a short
record in `.specify/quick-tasks/`, with no separate final task or planning commit.
Reserve identities through workflow/work.py, never by hand. After real
checks/review PASS use its completion helper to update registry/record and root
TASK_HISTORY.md together in the task commit. Reserved IDs are never reused.

## IV. Honest verification

Use meaningful checks for the affected languages and boundaries. Distinguish
baseline failures from regressions, unsupported coverage from clean results,
inferred links from verified references, and self-review from independent review.
No device testing, benchmark benefit or cross-ABI success without actual evidence.
Do not build APKs unless requested. No new app dependencies solely for analysis.

## V. Simple tooling and Git safety

Apply Ponytail full mode to every modification, including standalone small TOR tasks and
Spec Kit documents/tasks. Reuse before adding code; keep the smallest complete
behavior-preserving change and meaningful checks. Incorporate its review checklist
within existing review rounds, never as an additional loop.

Keep CodeGraph/adapters/Serena and the combined audit independent. No legacy
work.py/task-state/fingerprint gates. Preserve unrelated changes. No speculative
cleanup, automatic merging or history rewriting. Publishing follows authorization.

## Governance

Owner instructions override this constitution; it and project-policy.md override
conflicting upstream examples, parallel markers and converge defaults. Amend
policy only for an actual agreed change, not to evade a failed check/review.

**Version**: 1.1.0 | **Ratified**: 2026-11-12 | **Last amended**: 2026-11-12
