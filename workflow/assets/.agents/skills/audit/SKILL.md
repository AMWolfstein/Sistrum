---
name: audit
description: "Inspect a requested scope for dead code, optimization, simplification, test quality and coverage gaps using standalone diagnostic scans and verified source evidence. Report only unless implementation was requested."
---

# Code audit

Use for requests to audit code, performance, simplification, tests or coverage gaps. Follow the owner's scope and current instructions. This skill creates no
plan, tasks, review gate or automatic commit/push requirement.

## Choose and scan the scope

Run from the repository root with its Flutter/Rust toolchains available:

```sh
python3 workflow/codegraph/code_tools.py audit --lang dart
python3 workflow/codegraph/code_tools.py audit --kind dead-code --lang rust
python3 workflow/codegraph/code_tools.py audit --kind optimization --path lib/services
python3 workflow/codegraph/code_tools.py audit --kind simplifier --lang dart --base HEAD~1
```

`audit` defaults to `--kind all`: code diagnostics, test quality and coverage-gap
hints in one report. `--kind code` selects only dead-code/optimization/simplifier;
`--kind tests` or `--kind gaps` isolates those checks. With no scope options the
CLI scans the checkout; narrow using languages, repeated `--path`, or `--base REV`.
Filters intersect. Do not silently narrow a repository-wide request.
Check that example paths/revisions exist before using them.

Read `.work/audit/<kind>/report.json`, `report.md` and the relevant runner logs.
Same-kind scans overwrite earlier reports: save evidence locally if comparing runs.
Exit 0 means no selected allowlisted diagnostics, 1 means candidates, and 2 means
incomplete coverage/no matching files/invalid invocation. An unavailable tool or
compiler failure is a limitation, not a clean scan. Kotlin/Java share one Android
Lint run for the configured variant/source roots, using the Gradle wrapper or the
`gradle` on PATH; Gradle scripts/other source sets remain uncovered. C++ uses
clang-tidy and an actual compilation database; missing
commands/headers/toolchains are explicit gaps. Python/shell use ruff/shellcheck and
stay `UNAVAILABLE` until installed. Dart depends on enabled lints;
Cargo checks only the invoked features/targets. The allowlists are not exhaustive.

See [tool documentation](../../../workflow/codegraph/README.md) and
[audit configuration](../../../workflow/codegraph/audit-config.json). These commands
write reports/build caches, do not fix sources, and may run Cargo/Gradle build
scripts or compile dependencies. Android Lint requests no APK task. Activate JDK 25
and the repository Android/NDK toolchains. C++ configuration may be selected with
`AUDIT_CPP_COMPILATION_DATABASE` (directory or canonical compile_commands.json) and
`AUDIT_CLANG_TIDY` (executable). Never substitute synthetic compiler flags or a host
build for unverified Android coverage.
No security or leak CLI was restored; do not invent one.

## Verify the evidence

Use [code navigation](../code-navigation/SKILL.md) for definitions, callers,
resource relationships and native-boundary candidates. Refresh the map after
source changes; do not rebuild it after every read. Consult source and relevant
build configurations where graph/semantic coverage is absent.

Classify findings:

- **CONFIRMED:** evidence establishes the issue in the relevant configurations,
  with no unresolved runtime references. A tool diagnostic alone is insufficient
  authorization to delete an externally/dynamically reachable declaration.
- **PROBABLE:** evidence suggests an issue but verification remains incomplete.
- **UNCERTAIN:** dynamic/generated/native boundaries or missing tooling prevent a
  reliable conclusion. Retain the code and name the missing evidence.

For dead code, trace from entry points and inspect callers, imports/exports,
registration, manifest components, Method/EventChannels, JNI/FFI/UniFFI, generated
bindings, reflection, Cargo features, Gradle/CMake targets and supported Android
variants/ABIs as relevant. Tests, callbacks, fallback paths and public native APIs
are not dead just because ordinary static calls are absent. Duplicate live code
is not automatically dead. Passing compilation alone does not prove deadness.

For optimization, name the cost and the path on which it occurs. Inspect allocation,
repeated work, lifecycle and hot-path usage. Without measurements, describe an
opportunity, not a proven speedup. Preserve ordering, error/cancellation handling,
audio behavior and observable behavior.

For simplification, identify a concrete reduction in complexity and explain why
behavior, side effects, nullability, exceptions and lifecycle stay equivalent.
Style preferences, arbitrary length thresholds and fewer lines alone are not
findings. Read existing tests before recommending new ones.

## Test quality and behavioral gaps

Test checks report exact-body duplicate candidates and tests without recognized
assertions. Read setup, fixtures, parameters, expected exceptions and assertion
helpers before deciding. Keep regression, migration, serialization, backup and
native-boundary contract tests unless equivalent protection is demonstrated.
Similar bodies alone never authorize deleting tests. Custom/multiline test syntax
and unsupported languages are explicit partial inspection, not clean results.

Gap checks look for direct test references (across the discovered test corpus,
even when source scope is narrow) and read existing LCOV. They do not run tests or
produce coverage. A source can be exercised indirectly; a covered line can still
lack useful assertions. Read existing tests before proposing additions. Name the
behavior, regression and expected assertions, not a percentage target.

Missing/malformed/older LCOV yields incomplete evidence. Reports list the supplied
coverage file/time; even a newer file does not establish matching source revision,
configuration or full-suite coverage. Do not claim current behavioral coverage
from it alone. Generating coverage or editing tests is separate requested work,
not an automatic side effect of the audit.

## Review and action

Use the [code-auditor instructions](../../reviewers/code-auditor.md) when a reviewer
is requested. A fresh independent reviewer is optional, not a completion gate.
If no independent review was performed, label the review a self-audit; never
present these written instructions as an independently executed review.

An audit request is report-only. When the owner also requested implementation,
change only verified items in scope, run checks appropriate to affected languages
and boundaries, and distinguish existing failures from regressions. Restore only
the affected edits if a regression cannot be resolved. Do not auto-delete probable
or uncertain findings, widen scope, or introduce dependencies just for the scan.
Commit/push only as authorized by the task; this skill imposes no task loop.

## Report

State scope, tools/configurations actually checked, findings with file/line,
evidence, confidence and suggested validation. Separate dead code, optimization,
simplification, test quality and coverage gaps. Include retained candidates, reasons, unverified areas and
coverage gaps. If edits were authorized, list changes and actual check results.
Zero findings is valid; never claim exhaustive absence from a limited scan.
