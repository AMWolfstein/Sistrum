# Code navigation tools

Standalone **CodeGraph 1.6.2**, resource/Bash/native adapters, and optional
**Serena 2.1.0** verification. No tasks, planning, review gates, fingerprints,
hooks or application dependencies. Run commands from the repository root.

## Installation

Node/npm and Python 3.10+ are required for the graph adapter. Python 3.11+
is recommended; Serena 2.1.0 requires Python 3.11+.

```sh
npm ci --prefix workflow/codegraph --ignore-scripts --no-audit --no-fund
python3 -m venv .work/code-tools-python
.work/code-tools-python/bin/python -m pip install -r workflow/codegraph/requirements.txt
python3 workflow/codegraph/codegraph_adapter.py version
```

Optional semantic verifier, in a separate environment:

```sh
python3 -m venv .work/serena-python
.work/serena-python/bin/python -m pip install -r workflow/codegraph/requirements-serena.txt
export SERENA_PYTHON="$PWD/.work/serena-python/bin/python"
python3 workflow/codegraph/code_tools.py serena version
```

Dependencies never download during an ordinary graph update. Serena language
servers can download SDKs/server dependencies on first use; they require compatible
toolchains and network access. Language support is reported by Serena, not presumed.
No global agent/MCP configuration is installed. On this Android project use the
configured Flutter/Dart SDK, JDK 25 and Rust 1.99.1; avoid Serena's older default
Dart SDK. A new isolated home defaults to Dart 3.13.7 (override with
`SERENA_DART_SDK_VERSION` before its first use) and disables Rust Cargo build scripts
and proc macros. Existing home settings are preserved; verify these settings in
`SERENA_HOME/serena_config.yml` if reusing a home. Semantic references are separate from a complete
call graph and do not replace compiler checks.

## Update the map

```sh
export CODEGRAPH_SHELL_PYTHON="$PWD/.work/code-tools-python/bin/python"
python3 workflow/codegraph/code_tools.py update
```

One command collects CodeGraph symbols/relationships, structured resource links,
parse-only Bash evidence, JNI/UniFFI boundary candidates and literal Flutter
platform channels. Outputs live in ignored `.work/codegraph/`; the source checkout
is never executed by these graph/resource/shell collectors. CodeGraph runs on a
private source mirror. Missing Bash dependencies stay explicit coverage gaps.

Default inventory: existing tracked and unignored untracked source files reported
by Git, filtered to the supported extensions. Excludes `.git`, `.work`,
`.dart_tool`, `node_modules`, `build`, `target`, `vendor`, `third_party`, and
`__pycache__` directories. The saved `scope` lists inputs and exclusions. Tests and
tracked generated sources are included. A deleted tracked file is ignored.

For a limited region (replaces, rather than merges with, the saved map):

```sh
python3 workflow/codegraph/code_tools.py update --paths \
  lib/models/theme_settings.dart lib/theme/mornye_theme.dart rust_backend/Cargo.toml
```

Resources currently cover JSON, ARB, XML and TOML. YAML and Gradle/CMake semantics
are unsupported. Native matching uses only simple enclosing JVM owners and literal
JNI names/macros; nested/overloaded/dynamic cases remain unresolved. Channel records
are literal construction candidates, not proven connected handlers or runtime calls.
No generic Dart-to-Rust call is invented from matching names.

## Ask about a file or symbol

```sh
python3 workflow/codegraph/code_tools.py query --file lib/theme/mornye_theme.dart
python3 workflow/codegraph/code_tools.py query --symbol MornyeTheme --limit 30
```

Returns symbols, incoming/outgoing relationships, resource/native/channel evidence,
coverage and totals before truncation. Matching symbol names are exact (simple or
qualified); ambiguous matches remain separate. Definitions carry file/line locations.
The saved map is a snapshot: **run update after edits**. No fingerprint or hidden
freshness gate exists. Low coverage or absent references never proves dead code.

Optional language-server verification of the same indexed snapshot:

```sh
python3 workflow/codegraph/code_tools.py serena symbols --file lib/theme/mornye_theme.dart
python3 workflow/codegraph/code_tools.py serena definition --file lib/theme/mornye_theme.dart --symbol MornyeTheme
python3 workflow/codegraph/code_tools.py serena references --file lib/theme/mornye_theme.dart --symbol MornyeTheme
```

Only those read operations are exposed. Serena uses a read-only project on the
private mirror, with dashboard/GUI disabled and an overall timeout (default 120s).
Its configuration/caches/logs default to `.work/serena/home`; `SERENA_HOME` can point
to another isolated home containing language-server settings. Do not run a graph
update and a Serena query concurrently on the same mirror. Partial snapshots can
miss dependencies; snapshot paths in Serena results must be mapped to the source
checkout. Files/toolchains absent from the snapshot produce limited semantic evidence.

## Low-level adapter and tests

The original `codegraph_adapter.py version` and `extract --request FILE --out FILE`
remain available. Requests contain `root` (absolute), `files` (relative source paths)
and optional `auxiliary` files. Default cache: `.work/codegraph/`; `CODEGRAPH_CACHE`
can override it with an absolute path. CLI discovery checks local `node_modules`,
then `.work/tools/node_modules`, then PATH; `CODEGRAPH_COMMAND` overrides with a
JSON argv list. CLI telemetry is disabled. Relationships retain extracted/inferred
labels; ABI signatures and runtime behavior are never claimed verified.

```sh
CODEGRAPH_SHELL_PYTHON="$PWD/.work/code-tools-python/bin/python" \
  python3 -m unittest discover -s workflow/codegraph -p 'test_*.py' -v
```

Tests include real CodeGraph parsing/incremental synchronization, resource/Bash
regressions, JNI false positives, input containment, navigation and the Serena read
allowlist. Missing real tools produce explicit skips. Real Serena/LSP smoke checks
are separate because SDK/server availability varies by machine.

## Dead code, optimization and simplification scans

The default audit combines code, test quality and coverage gaps in one report,
without workflow tasks, automatic fixes or test execution:

```sh
python3 workflow/codegraph/code_tools.py audit
python3 workflow/codegraph/code_tools.py audit --lang dart
```

Specific scans remain available:

```sh
python3 workflow/codegraph/code_tools.py audit --kind code --lang dart
python3 workflow/codegraph/code_tools.py audit --kind dead-code --lang dart rust
python3 workflow/codegraph/code_tools.py audit --kind optimization --lang dart
python3 workflow/codegraph/code_tools.py audit --kind simplifier --lang dart
```

Default `--kind all` combines all five categories. `--kind code` selects the three
code categories; `--kind tests` selects test quality; `--kind gaps` selects coverage
hints. With no scope flags the CLI scans the checkout. Choose `--lang`, repeat
`--path` for file/folder scopes, or use `--base REV` for changed files. `--all`
explicitly selects the checkout scope; it is distinct from `--kind all`.
Filters intersect. Reports and full runner logs go under ignored
`.work/audit/<kind>/report.md` and `report.json`; another scan of the same kind
replaces those files. Do not run the same kind concurrently in one checkout.
Standalone equivalent: `python3 workflow/codegraph/audit.py` with the same options.

Dart uses `dart analyze --format=machine`. Rust uses explicit `cargo +<channel>`
from `rust_backend` (the channel is read from `rust_backend/rust-toolchain.toml`),
with `--workspace --all-targets --locked`; dead-code scans
use `check`, while combined/optimization/simplifier scans use `clippy` with perf,
complexity and style lint groups. Python uses `ruff check --output-format=json`
and shell uses `shellcheck --format=json1` on the selected files; both report
`UNAVAILABLE` until installed, and their unclassified checks stay gaps. Compiler
runners may analyze more files than
the selected scope; reported candidates are filtered to selected files. These
runners may write build caches and Cargo may execute crate build scripts; they
do not fix application sources. Activate the repository's Flutter/Rust toolchains
before running. No APK is built by this command.

`audit-config.json` contains the curated diagnostic categories and protected
paths. Generated/vendor code is protected. Source-only code kinds exclude test
paths; the combined default includes them and Rust inline tests. Suggestions
are **review candidates**, not safe automatic deletions or measured speedups.
Dart suggestions depend on enabled analyzer lints: the allowlists name lints this
project enables (`flutter_lints` plus `analysis_options.yaml`), so a disabled lint
never appears. Kotlin/Java use shared Android Lint; C++ uses clang-tidy with actual compilation
commands. These provide configuration-specific diagnostics, not exhaustive unused
public-symbol/reachability detection.

Exit status: `0` means no selected diagnostics or heuristic findings were emitted; `1` means candidates were found; `2` means incomplete coverage,
no matching files, or invalid invocation. Findings do not make an incomplete scan
complete. Compilation errors or nonzero commands without usable diagnostics never
produce a clean result. A clean scan does not establish that no dead code remains.


## Agent instructions

[Code navigation](../../.agents/skills/code-navigation/SKILL.md) describes graph
and Serena use; [audit](../../.agents/skills/audit/SKILL.md) covers report-only
scans and source verification. [Code-auditor](../../.agents/reviewers/code-auditor.md)
provides optional read-only review guidance. Root AGENTS.md links these explicitly;
automatic skill discovery depends on the agent. None restores planning/tasks,
review gates or automatic commits/pushes. Written prompts alone are not independent
reviews, and findings never authorize speculative deletion.


### Test quality and coverage inputs

Test inspection checks literal Dart, Rust inline/integration, Kotlin and Java test
bodies for missing recognized assertions and exact-body duplicates. These are
review hints: helper assertions, expected exceptions, fixtures and parameters may
change the conclusion. Custom/parameterized/multiline syntax and C++ test bodies
are not fully supported and produce partial-inspection notices. Tests are never
automatically deleted or rewritten.

Gap inspection searches the discovered test corpus beyond the selected source
scope for direct name references, then reads existing `coverage/lcov.info` for
Dart and `rust_backend/target/lcov.info` for Rust. Paths/production suggestions are
in `audit-config.json`. No tests or coverage jobs run automatically. Missing,
malformed or older coverage produces INCOMPLETE/PARTIAL evidence. Repeated LCOV
records aggregate hits; a run's exclusions and source revision are not verified.
Indirect tests and assertions must be inspected manually before adding tests or
claiming a behavioral gap. Other languages have explicit missing coverage inputs.


### Android JVM and C++ runners

```sh
python3 workflow/codegraph/code_tools.py audit --lang kotlin java cpp
python3 workflow/codegraph/code_tools.py audit --kind code --lang kotlin java --path android/app/src/main
python3 workflow/codegraph/code_tools.py audit --kind optimization --lang cpp --path android/app/src/main/cpp
```

Activate the project's JDK 25, Flutter, Android SDK and NDK environment first.
Kotlin/Java share one `:app:lintDebug` Gradle invocation, using a checked-in init
script to configure all warnings, enable test-source checking and write fresh
absolute XML output in memory. The tracked `android/gradlew` is used when present,
otherwise the `gradle` on PATH (this repository tracks only the wrapper
properties); none present is `UNAVAILABLE`, never a clean result. Gradle Kotlin
DSL (`.kts`) is not analyzed. The effective test-source setting is logged and
verified; missing/false confirmation makes coverage INCOMPLETE. App
Gradle sources are unchanged. A private per-run report path prevents stale-report
reuse; logs stream to `.work/audit/<kind>/android.log`. First use can compile Rust,
Flutter and Android dependencies. No assemble/APK task, test execution or auto-fix
is requested. Missing wrapper/toolchain, Gradle failure or Lint internal errors are
INCOMPLETE. Ordinary Lint code findings are candidates, not analyzer crashes.

The default variant/source roots in audit-config.json match this repository's
Debug main/debug/test/androidTest/rust wrapper sources. Other source sets and
Gradle `.kts` scripts are uncovered rather than silently cleared. Both JVM language
configurations must agree to share a run. Existing suppressions/baselines can limit
emitted diagnostics. Lint is useful for Android API/performance/simplification
patterns; it does not prove that arbitrary unused public Kotlin/Java declarations
are dead. Raw XML/logs retain diagnostics outside the curated categories; tool
metadata counts those as `unclassified_diagnostics`.

C++ default discovery selects the most recent existing Android Debug arm64
`compile_commands.json` from the configured `.cxx` locations. Other variants/ABIs
are unverified. To select another real configuration or executable:

```sh
export AUDIT_CPP_COMPILATION_DATABASE=/absolute/path/to/compile_commands.json
export AUDIT_CLANG_TIDY=/absolute/path/to/clang-tidy
```

A database directory is accepted too; alternate filenames are rejected because
clang-tidy reads the canonical filename. No database is fabricated or configured
automatically. `clang-tidy` comes from the override, PATH or activated NDK; there
is no runtime download. Selected translation units use the database's actual flags;
missing compile commands (including standalone headers/test targets) are explicit
PARTIAL/INCOMPLETE coverage. Headers are inspected as included, without claiming
that every header was reached. Stale build directories, absent generated headers,
compiler errors and timeouts stay incomplete. Only diagnostics in selected files
are classified; no fixes or native linkage changes are applied.
