---
name: module-regression
description: Use the module regression ledger (docs/REGRESSION.md) to decide which module and downstream test commands must run after a change to :dsp, :codecs, :metadata, :analysis, :sharedLogic or androidApp, and to keep that ledger current. Use when choosing gate commands, at milestone review, or when a change in one module may break another.
---
<!-- Adapted from ECC (github.com/affaan-m/ECC @ b92f181), MIT License, Copyright (c) 2026 Affaan Mustafa; see docs/ECC-LICENSE.md. Modified for Sistrum. -->

# Module Regression Ledger

The ledger is `docs/REGRESSION.md`: per module, its downstream consumers (from `project(":…")` edges in the
`build.gradle.kts` files), its acceptance command, and the propagation rule.

## After a change

1. List changed paths since the base (milestone start or last green commit): `git diff --name-only <base> HEAD`
   plus `git status --short`. Map paths to modules.
2. For each changed module, take its acceptance command and every downstream module's command whose propagation rule
   applies (public API/behaviour change → run downstream; internal-only with green module tests → downstream may be
   exempt, state why).
3. Run them through the normal gate (`verify.sh --quick '<tasks>'`, or the full gate at milestone end). Verdict = exit
   codes. A module without an acceptance command is a ledger gap, never a pass.
4. If B fails after only A changed and B was green before, trace B's failure back to A's change; if B was already red,
   record pre-existing debt instead.

## Keeping it current

Refresh edges only from evidence: `grep -o 'project(":[a-zA-Z]*")' */build.gradle.kts`. Record the refresh date.
Every important defect fixed must leave a regression test named in the ledger or HANDOFF.
