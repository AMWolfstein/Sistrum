---
name: loop-design-check
description: Review an autonomous agent loop for runaway failure modes — undecidable goals, the agent judging itself, passing the gate by weakening tests, mid-run clarification it never asks, stale instructions. Use on the Codex milestone runner (milestone-run.sh + AGENTS.md "Autonomous mode") whenever either changes, and before 003 or bug-fix phases reuse it.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Loop Design Check

An agent loop grinds toward whatever its checker accepts. The human owns judgment (is the goal right, ship or
not); the loop owns execution. Check the loop against the five failure modes and three red lines.

## Five failure modes (any hit = fix before the loop runs)

| # | Failure | Question | Antibody |
|---|---|---|---|
| 1 | Goal is a platitude → spins | Can each task's exit be judged yes/no by a command? | Decidable acceptance in tasks.md (tests, oracle hashes, gate PASS) |
| 2 | Agent judges itself → "looks fine" | Is the verdict from deterministic commands and an independent reviewer, not the coder's word? | `verify.sh` exit codes + milestone reviewers (different model) |
| 3 | Gate gamed → tests deleted/weakened, expected values edited, cases skipped | Are boundaries ("must NOT") stated next to the done-criterion? | AGENTS.md never-weaken rules, tests-first + "make pass without modifying", migration-guard checklist, pr-test-analyzer |
| 4 | Ambiguity resolved by guessing mid-run | Are open decisions front-loaded? | Recommended-option rule + D-entries; BLOCKED instead of changing ADRs/001 behaviour |
| 5 | Stale or bloated instructions → faster errors | Are AGENTS.md / HANDOFF / tasks.md current and short? | HANDOFF "Current status" rewritten each task; prune AGENTS.md |

Prefer reconciliation against external truth (oracle PCM hashes, reference implementations, measured device numbers)
over assertions the coder wrote itself.

## Three red lines

- The human flips the last switch: no merge to `main`, no owner-device decisions, no PASS on listening checks.
- Retry caps and stops: per-task attempts ≤ 3, one fallback model, runner stops on the first not-DONE task.
- The more a loop rewrites its own rules, the stricter the review: changes to AGENTS.md, the runner or verify.sh are
  made by the orchestrator, reviewed with this checklist, and committed separately.

## Output

Table of the five modes (PASS/RISK + evidence file:line) and the red lines, then concrete fixes.
