---
name: santa-method
description: Dual independent review for high-risk work — two fresh reviewer agents with the same rubric, both must pass, fix-and-re-review at most 3 rounds, then escalate to the owner. Use at milestone review for [HIGH-RISK] tasks (crossfade/transition timing, Room migrations, normalization/gain, MediaSession, analysis decoder switching) and before anything irreversible.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Santa Method (dual independent review)

One reviewer shares the blind spots of whoever framed the review. Two isolated reviewers with the same rubric break
that; an issue only one of them finds is still real.

## When

- Milestone review covering a `[HIGH-RISK]` task, a Room migration, or changes to gain/limiter/crossfade/transition
  timing. Not for tasks whose correctness is fully decided by deterministic oracle tests (use the gate).

## How

1. **Rubric** (objective pass/fail lines), built from the task line, spec FR/SC, contracts and ADRs, e.g.
   - behaviour matches the spec text and contracts; 001 shipped behaviour unchanged unless a D-entry/owner says so;
   - no silent failure on the changed paths; errors carry provider/cause;
   - tests would fail if the behaviour broke (no self-derived expectations, no skipped cases);
   - thread/coroutine safety on the changed paths; no per-block allocation in audio loops;
   - Room: migration tested from the previous version with real rows; no data loss.
2. **Two reviewers in parallel**, fresh agents, no shared context, same inputs (task line, rubric, diff range). Use
   `kotlin-reviewer` and `silent-failure-hunter` with the rubric appended, or two `general-purpose` agents. Each
   returns per-criterion PASS/FAIL with file:line, then `VERDICT`.
3. **Gate**: both PASS → done. Otherwise merge and dedupe the issues → one Codex fix task listing them ("fix only these;
   no refactors") → re-run BOTH reviewers fresh.
4. **Cap**: 3 rounds; then record in HANDOFF and stop for the owner (CLAUDE.md stop rule).

Cost ≈ two reviews per round; use it only where a wrong result is expensive.
