# Portable repository workflow

This folder contains the executable helpers, analysis tools, Ponytail provenance,
and installable Spec Kit/agent defaults. Copy the **tracked `workflow/` folder** to
another Git repository; do not copy this project's task registry, history, specs,
quick-task records, `.work/` environments or ignored `node_modules`.

All agents use the same repository instructions. No CLAUDE.md, global agent config,
marketplace plugin or host-specific hooks are needed. These are instructions and
helpers, not an executable sandbox forcing an agent to obey.

## Install or transfer

From the target Git repository root, with Python 3.10+ and Git installed:

```sh
python3 workflow/work.py setup
python3 workflow/work.py doctor
python3 workflow/work.py status
```

Node/npm must be available for CodeGraph provisioning. Optional Serena needs
Python 3.11+: `python3 workflow/work.py setup --with-serena`. No app/runtime
dependencies, OS packages, global agent configuration or APKs are installed.

Setup:

- Adds the ignored local `.work/` cache rule if necessary.
- Installs **missing** shared instructions, skills, Spec Kit templates/scripts and
  the workflow identity GitHub check from `workflow/assets/`. Existing files and
  project customizations are preserved. Review old custom instructions for compatibility.
- Creates root AGENTS.md pointing to this guide, or appends that entrypoint once
  if an existing AGENTS.md has no workflow reference. Its existing instructions survive.
- Creates TASK_HISTORY.md when absent. With valid local registered tasks it
  reconstructs their completion list; a fresh project gets an empty list and registry.
- **Never replaces or rewrites an existing TASK_HISTORY.md**, even on repeated or
  offline setup. Unknown existing history/legacy task records without matching
  registry are retained and reported INCOMPLETE for explicit reconciliation.
- Installs pinned CodeGraph/parser packages locally, reusing healthy installations.
  Explicit environment overrides still win; dependencies/cache files stay ignored.

`setup --offline` still prepares local support/history but never downloads missing
packages. A new transferred copy can therefore report INCOMPLETE until dependencies
are installed. Missing assets means the folder copy is incomplete, not permission
to import task data from another project.

For transfer from Git, export only tracked files, for example:

```sh
git archive HEAD workflow | tar -x -C /path/to/target-repository
```

The target must already be a Git repository. Do not overwrite a customized target
workflow without reviewing its diff. `workflow/assets/` is the portable default
source; root `.agents/` and `.specify/` are installed/project-specific configuration.
When updating shared workflow instructions, update the corresponding asset too.
Setup is create-only, not a blind upgrade mechanism. Project task data is deliberately
outside this distribution; copying only this folder does not import Mornye tasks.

## Entry points and readiness

```sh
python3 workflow/work.py status
python3 workflow/work.py doctor
python3 workflow/work.py doctor --full
python3 workflow/work.py setup --with-serena
python3 workflow/work.py validate
```

`status` reports branch/commit, changed paths, reserved/completed tasks, next M/TOR
numbers and registry/history consistency. `doctor` checks Git/root/ignored paths,
Node/npm, CodeGraph 1.6.2 and real pinned Bash/TOML parsing. `--with-serena` makes
optional semantic packages required. Core PASS does not mean an application build
or device test passed. `--full` also checks app-tool availability
(Dart, Rust, JDK, Android, clang-tidy, ruff, shellcheck), not execution.

Analysis defaults currently target this project's Flutter/Dart, Rust, Android
JDK/SDK/NDK, Kotlin/Java, C/C++, Python and Bash sources. Toolchain versions are
read from `rust_backend/rust-toolchain.toml`, `android/app/build.gradle.kts` and
`.fvmrc`; **adapt `codegraph/audit-config.json`,
project constitution and platform probes for another project's actual architecture.**
No SDK/JDK/Rust installation or all-language coverage is inferred from copying files.
A new project with different languages/build variants needs its own analysis adapters.

Exit 0 means successful helper operation; INCOMPLETE/error returns 2. Setup may
prepare history/support successfully while missing packages/toolchains remain.
Logs are ignored under `.work/workflow-setup.log`. Managed Python prefixes are
verified before pip; no fake local symlink may redirect installation into system Python.

## Rules for every modification: Ponytail

Before writing any source, tests, config, scripts or docs, read/apply
[the core Ponytail skill](../.agents/skills/ponytail/SKILL.md) in full mode. Read the
affected code/callers/config/tests, reuse existing components before adding code,
and make the smallest complete change. Never cut validation, error/data-loss
handling, security, accessibility or relevant audio/hardware guarantees to save lines.

Use [Ponytail review](../.agents/skills/ponytail-review/SKILL.md) inside the existing
review round; it creates no extra loop. Non-trivial logic needs meaningful targeted
checks; trivial docs need link/template/diff checks, not artificial tests/APKs.
Current owner instructions override defaults; respond in their language.

## Choose the path

Read [project policy](../.specify/project-policy.md) and
[constitution](../.specify/memory/constitution.md).

- Small isolated low-risk modification: [quick-task skill](../.agents/skills/quick-task/SKILL.md),
  one TOR record, no spec/plan or separate final task.
- Substantial/high-risk change or explicitly requested planning: Spec Kit specify →
  plan → tasks. One commit contains all finished planning artifacts. Planning-only
  requests stop there and never authorize implementation.
- Read-only questions/audits/navigation and Git-only fetch/push do not create task records.
- If small work grows or affects playback/native/data contracts/migrations, move
  to full planning; never relabel high-risk work to avoid the plan.

Skills are ordinary instructions in `.agents/skills/<name>/SKILL.md`; any agent
can read them directly. Slash/dollar command names are optional host aliases.
For planned work read `speckit-specify`, `speckit-plan`, `speckit-tasks`, then
`speckit-implement` only when implementation is authorized. Clarify/analyze/checklist
are optional, not an additional approval ceremony.

## Map-first planning and tasks

```sh
python3 workflow/codegraph/code_tools.py update
python3 workflow/codegraph/code_tools.py query --file path/to/source
python3 workflow/codegraph/code_tools.py query --symbol SymbolName
```

Planning sessions start with update/scope inspection **before repository
investigation or technical plan/task drafting**. Query relevant symbols/files and
then read source/tests/build/native registrations. Record evidence/limitations in
plan.md; reuse the map while source is unchanged during that planning session.
Missing coverage can be manually verified and documented, never silently guessed.

The **first action of every task** (ordinary, nested/standalone TOR and final) is
map refresh. There is no dedicated map task/commit. Inspect scope/coverage and stop
that task on failed refresh. Map/cache stays ignored; evidence belongs to its task
record. A missing edge or a passing compile never proves dead code is unreachable.

## IDs, task records and root history

Never hand-assign IDs/counters. Use the actual returned ID/path, not example numbers:

```sh
# Feature directory is the ordinary Spec Kit directory, not a mission-ID allocator:
python3 workflow/work.py mission new --title "Feature name" --path specs/001-feature
python3 workflow/work.py task reserve --mission M001 --title "First outcome"
python3 workflow/work.py task reserve --mission M001 --title "Cumulative review" --final
# Owner extra in an active feature:
python3 workflow/work.py task reserve --mission M001 --tor --title "Extra request"
# Independent small change:
python3 workflow/work.py task reserve --title "Small request"
```

Missions use M###; ordinary tasks M###-T###. **TOR has one shared numeric pool**:
standalone TOR001 forbids any mission M###-TOR001. The next extra could be
M###-TOR002; the next standalone TOR003. Reserved/unfinished numbers are not reused.
Mission/source folder numbering is independent. Completed final tasks close missions.

[The local registry](../.specify/task-registry.json) stores reservations. Fill the
allocated record with request/baseline/paths/acceptance/checks/map/review evidence:
standalone records are `.specify/quick-tasks/TOR###.md`; feature extras stay with the
authorized feature tasks/log before its final task. A stale pointer/completed plan
does not make an unrelated request part of that feature. Resume an unfinished task's
ID/review count rather than allocating a new number to evade a failure.

## Execution, reviews and commits

For every authorized task: map → scoped implementation → checks → independent
read-only review → fixes/revalidation/review if needed. Follow
[task-reviewer](../.agents/reviewers/task-reviewer.md). Maximum **three** review
results per task; if the third is still NEEDS_FIXES/BLOCKED, stop/report remaining
issues and uncommitted state. No round 4, reset, fabricated PASS or self-review
substitution. Missing essential review/tooling is an explicit blocker.

After actual checks and independent PASS:

```sh
python3 workflow/work.py task complete ACTUAL_ID --reviewer reviewer-name --round 1 --checks "Actual results"
python3 workflow/work.py validate
```

This marks its record/checkbox complete and updates root
[TASK_HISTORY.md](../TASK_HISTORY.md) with **completed full IDs and names only**.
Detailed evidence stays in the record. Completion is a recorded declaration, not
proof a reviewer actually ran; never invent evidence. Include code/tests, registry,
history, record/log and checkbox in **one task commit**, with no separate creation,
map, WIP, fix, review or completion commit. Preserve unrelated edits.

Full features end with one cumulative [FINAL] task reviewing every feature task/
extra against spec/plan, with full feasible checks and the same bounded loop. All
final fixes/evidence are one final-task commit. One standalone TOR needs no separate
final task. Upstream converge may inspect, not create unbounded review/task cycles.
No APK unless requested; no automatic merge, force push or unrelated history rewrite.

## Combined audit and optional semantic verification

```sh
python3 workflow/codegraph/code_tools.py audit
python3 workflow/codegraph/code_tools.py audit --lang dart
python3 workflow/codegraph/code_tools.py audit --kind tests --path test
```

Default audit combines dead-code, optimization, simplifier, test-quality and
coverage-gap hints. It never fixes sources or executes app tests automatically.
Dart analyzer, explicit Rust Cargo/Clippy, shared Kotlin/Java Android Lint, real
C++ clang-tidy, Python ruff and Bash shellcheck configuration are documented in
[analysis README](codegraph/README.md). Missing compile commands, LCOV, toolchains,
unsupported syntax/configurations remain INCOMPLETE/PARTIAL, not clean results.
Read [audit skill](../.agents/skills/audit/SKILL.md) before interpreting suggestions;
verify runtime/native references before deletion and measure optimization claims.

Optional `code_tools.py serena symbols|definition|references --file ...` reads the
private indexed mirror. `setup --with-serena` installs packages, not language-server
SDKs. Only tested languages/configurations may be claimed verified. Never update
the same mirror while querying it. See [navigation skill](../.agents/skills/code-navigation/SKILL.md).

## Integrity, collaboration and recovery

Fetch/reconcile latest registry before reservation/publishing. Local atomic locks
serialize helpers in **one checkout**, not disconnected clones or separate worktrees.
They can still choose the same number from an old Git snapshot. Validate against
the actual latest target commit before publishing:

```sh
python3 workflow/work.py validate --base ACTUAL_COMMIT_SHA
```

The installed Workflow identity GitHub check validates JSON/schema/counters,
canonical task records, exact completed history and immutable base reservations.
Make it a required branch-protection check to block merges; settings are not changed
by setup. Reconcile conflicting unmerged allocations with fresh IDs while preserving
target history; never force-push around the checks. No remote allocator or adversarial
sandbox is claimed, and remote CI is not deemed passed merely by local validation.

Setup creates missing history but preserves existing bytes/mtime. Normal verified
task completion deliberately appends/regenerates its canonical completion list.
Unknown/mismatched old history or task data is **not** reset: inspect/back up and
reconcile deliberately. `init`/`init --migrate` refuse existing registries and now
also refuse overwriting a nonmatching existing history. Do not copy another repo's
registry/history/specs into a new project. No unrecorded past task is invented.

`history` explicitly regenerates the projection from a valid local registry when
reconciliation is intended; it is not invoked by setup on an existing history file.
Reservations are persisted before auxiliary record writes; interrupted writes are
reported by validation and recovered from the reservation/evidence without reusing
IDs. Remove stale `.work/task-registry.lock` only after confirming no helper is active.
Do not run setup/allocations concurrently during provisioning. Do not run concurrent
same-kind audits or map updates/Serena queries against one output/mirror.

## Maintaining this folder and verification

Keep portable defaults in `workflow/assets/` aligned with shared instruction changes.
The unit tests fail when the installed `.agents/` instructions or
`workflow-integrity.yml` differ from `workflow/assets/`; project-data and policy
files are excluded from that check.
Project-specific root files survive setup; review upgrades instead of force-copying
over custom files. Package manifests/requirements are pinned; third-party provenance
and licenses are retained under codegraph/ponytail and installed Spec Kit metadata.
Package provenance is not a source-freshness gate. No old work.py state machine,
additional ship gate, provider-specific configuration or telemetry/session collector
is installed.

```sh
python3 -m unittest discover -s workflow -p 'test_*.py' -v
python3 -m unittest discover -s workflow/codegraph -p 'test_*.py' -v
python3 workflow/work.py validate
```

Tests include local task allocation/base preservation, installation safety,
create-only history/support, repeat/concurrent setup, and analysis regressions.
Native/app/device/other-ABI behavior is not verified by installing workflow files.
