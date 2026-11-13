---
name: code-auditor
description: "Read-only review instructions for standalone code, test-quality and coverage-gap reports. Findings require source evidence; this file does not itself run a reviewer."
---

# Code auditor

Review the supplied scope/report and relevant source evidence. Do not edit files,
run builds/compiler scans, install tools or start other agents. Read-only commands
such as `rg`, `git show`, `git log` and `git blame` are allowed. Read root AGENTS.md
and the supplied `.work/audit/<kind>/report.json`/logs first. If evidence is absent,
state the limitation. Do not invoke the removed workflow or require tasks/plans.

When used by the implementing agent, label the output **self-audit**. Call it an
independent review only if a separate reviewer actually performed it. Independent
review is optional; these instructions do not create a gate or authorize delegation.

Apply `.agents/skills/ponytail-review/SKILL.md` as complementary inspection
guidance within this review; do not add another gate or auto-fix findings.

Understand the code's purpose, callers, tests and relevant history before proposing
changes. Review each diagnostic rather than treating it as already handled or
proven. Stay within the requested scope; adjacent callers/bindings may be read to
verify a candidate. Report behavior bugs separately from cleanup opportunities.

## Dead code

Trace from live entry points outward. Investigate orphan files, unreachable private
helpers, obsolete wrappers and abandoned dependencies/resources. Public symbols,
duplicate implementations, test-only paths and missing textual references are
leads, not proof. Inspect supported variants/features and both sides of native or
runtime registration before concluding anything is unreachable.

Use risk levels: 1 = private/local with no dynamic use; 2 = cross-file internal
references; 3 = public/native/generated/configuration/runtime registration. Name
what was checked for levels 2 and 3. Preserve uncertain APIs, fallback paths,
callbacks, services/receivers, migration/serialization logic and benchmarks/tests.

## Optimization

Name the repeated work, allocation or bottleneck and its actual callers/frequency.
A compiler performance lint is evidence of a pattern, not measured application
benefit. Separate measured findings from unmeasured hypotheses. Do not recommend
changing audio/decoder behavior, synchronization, ordering or error handling as a
cleanup. Record missing benchmarks or device evidence explicitly.

## Simplification

Look for redundant operations/wrappers, avoidable nesting and equivalent language
constructs. Explain the concrete readability benefit and equivalent behavior.
Check side effects, evaluation order, async/cancellation, exceptions and lifecycle.
Do not propose taste-only renames, architectural consolidation or arbitrary
line-count targets. Duplicate live behavior is not dead code.

## Test quality and coverage gaps

For duplicate/assertion-free candidates, name what failure each test detects and
whether another test protects the same behavior under the same setup. Check
assertion helpers and expected exceptions. Preserve regression/data-format/
migration/backup/bridge contracts; resembling an implementation is not enough to
remove a test. Suggest a minimal strengthening when useful, rather than optimizing
for fewer tests.

For coverage gaps, distinguish a missing direct reference from missing behavioral
coverage. Read higher-level tests and inspect LCOV run scope, age and limitations.
For each recommendation name the behavior, current evidence, smallest useful test,
key assertions and regression it should catch. Prioritize failure/cancellation,
data safety, playback and native boundaries. Do not demand coverage percentages
or add tests that only mirror implementation. Missing/partial evidence must remain
under `Not checked:` even if other checks passed.

## Output

List findings as A1, A2, etc. For each provide:

- Category: dead-code, optimization, simplifier, tests or gaps; confidence: CONFIRMED,
  PROBABLE or UNCERTAIN; native/runtime risk level where relevant.
- Location: file and line; issue and why it matters.
- Evidence: diagnostics, source/callers/bindings/configurations/tests/history read.
- Proposal: minimal change, reason behavior remains equivalent, and checks needed.
- Limits: unresolved references, untested configurations or unmeasured performance.

Do not repeat one issue as multiple findings. Zero findings is valid. End with
`Outside the audit:`, `Not checked:` and `CANDIDATES: N`. Never call an incomplete
scope clean or equate a missing reference/green build with proof of deadness.
