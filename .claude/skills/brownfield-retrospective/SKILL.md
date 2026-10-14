---
name: brownfield-retrospective
description: Slice-by-slice audit of the existing app for the owner's full review — present-state audit with the reviewer agents, backward reconstruction of decisions, a four-axis gap register (thinking, coding practice, missing features, technology choices), then fixes forward through Spec Kit tasks and Codex. Use for the full review after 003, or when a module needs a rethink before more features land on it.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Brownfield Retrospective

Artifacts per slice under `specs/review/<slice>/`: `gap-register.md` (state; has a `Current phase:` marker),
`baseline-audit.md`, `reconstructed-spec.md`. The register is the only file needed to resume.

## Phase 0 — Slice
Start from what the owner cares about (a module, a flow, a bug, a dependency); if nothing, propose the 2–3 highest-churn
areas (`git log --format= --name-only | sort | uniq -c | sort -rn | head`). Budget ≈ 15 files / 2,000 lines; split bigger
slices. Record the perimeter (paths, entry points, callers via Graphify) first.

## Phase 1 — Present-state audit (no fixes)
Run in parallel, read-only: `kotlin-reviewer`, `silent-failure-hunter`, `pr-test-analyzer`, `comment-analyzer`
(+ `compose-multiplatform-patterns` / `accessibility` checklists for UI slices). Every finding has `file:line`.
Snapshot: unit/instrumented test counts, lint, TODO/FIXME inventory. Write `baseline-audit.md`.

## Phase 2 — Backward reconstruction
What the code does today (WHEN → THEN requirements, invariants) → `reconstructed-spec.md`. Git archaeology: age,
churn hotspots, reverted/large commits. Decision inventory: each decision frozen in code vs ADRs/DECISIONS; an
unrecorded decision is a "thinking" gap.

## Phase 3 — Gap register
| ID | Axis | Gap | Evidence | Forward fix | Severity | Effort | Status |
Axes: thinking / practice / missing features (input validation, observability, migrations+rollback, a11y, i18n/RTL,
docs, CI gates) / technology. Severity P0 data loss or security, P1 correctness, P2 practice/observability, P3
cleanup. Effort S/M/L. No evidence pointer = opinion, drop it.

## Phase 4 — Redesign
Triage each row: fix-forward / rewrite / wont-fix (with reason). Undocumented decisions → reconstructed ADR marked
"proposed (reconstructed)"; the owner confirms before "accepted". Changes go through the owner's approval rules.

## Phase 5 — Fix forward
Rows become Spec Kit tasks (spec/plan/tasks by Claude) executed by Codex in autonomous mode with the usual gates;
update register statuses as commits land.

## Phase 6 — Re-audit
When all rows are done/wont-fix, re-run Phase 1 on the same perimeter, diff against the baseline, record the delta.
