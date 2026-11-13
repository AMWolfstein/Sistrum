# Validation of standalone navigation tools

Validated on `new-workflow`, based on `261626f3`, using existing local tools.
Application sources, dependencies and GitHub Actions were unchanged.

- `python3 -m unittest discover -s tools/codegraph -p 'test_*.py' -v`:
  **33 passed, 0 failed, 0 skipped**, with CodeGraph 1.6.2 installed and
  `CODEGRAPH_SHELL_PYTHON` selecting the pinned Bash-parser environment.
- Full `code_tools.py update` and subsequent incremental update succeeded.
  Final source extraction: 1,776 indexed sources, 36,573 symbols and 127,937
  relationships; CodeGraph reported no partial/unindexed source files in that scope.
  These counts include tests and tooling, not exclusively live application code.
- Supplementary coverage: 97 structured-resource files and 5 Bash scripts indexed.
  Native inventory retained 18 JNI binding candidates and 25 unresolved generated
  UniFFI surfaces. The 262 channel construction records include test fixtures;
  they are not 262 verified runtime channels.
- File/symbol navigation exercised against `MornyeTheme` and
  `rust_backend/Cargo.toml`, including incoming relationships and resource symbols.
- Real Serena 2.1.0 / MCP 2.2.0 smoke checks succeeded for Dart symbol overview
  (`ThemeSettings`), definition (`MornyeTheme`) and references (`MornyeTheme`).
  Used the existing isolated Serena home with cached Dart 3.13.7. Fresh SDK
  provisioning and semantic support for Rust/Kotlin/Java/C++ were not tested in
  this change. A partial two-file snapshot was used for the first reference smoke;
  definition and overview were also checked on the full source snapshot.
- `git diff --check` and Python compilation passed. No Android APK was built;
  application behavior/device testing was outside this tooling-only change.

The saved map is local and ignored. Updating code does not automatically refresh
it. Structural coverage and inferred links do not prove runtime reachability,
correct ABI linkage or absence of dead code. A self-review was performed; no
independent agent review was run for this change.

## Restored code scans

- Full standalone suite after restoring scans: **40 passed, 0 failed, 0 skipped**.
- Real combined Dart scan of `lib/models/theme_settings.dart`: PASS, no selected
  diagnostics. This was a single-file smoke check, not a repository-wide audit.
- Real analyzer positive-control fixture, isolated under ignored `.work`:
  `unused_local_variable` classified as dead-code, `unnecessary_new` as simplifier,
  and `prefer_const_constructors` as optimization; overall status FINDINGS.
- Tests verify generated/vendor/test exclusions, scope containment, category
  separation, compiler-error handling, unavailable coverage, JSON parsing,
  report-only behavior and Rust's explicit 1.99.1/locked Clippy command.
- Rust runner was tested with mocked process output; no live Cargo audit or
  Kotlin/Java/C++ compiler audit was run in this restoration. Cross-language
  bindings are retained as evidence to investigate, not deletion authorization.

## Combined test-quality and coverage-gap audit

- Standalone suite: **50 passed, 0 failed, 0 skipped**. Added coverage for combined
  dispatch, test-only scans, Rust inline/JUnit expected-exception handling,
  comment/string false positives, duplicate-body hints, source/test scope
  separation, unsupported syntax, missing/stale/malformed LCOV and repeated-record
  hit aggregation.
- Real combined CLI smoke used an isolated Dart fixture and supplied LCOV:
  FINDINGS, with 1 dead-code, 1 optimization, 1 simplifier, 1 test-quality and 2
  coverage-gap candidates in one report. The fixture's tests were inspected, not
  executed. No application tests or APK builds were run for this tooling change.
- No live full-repository audit, Cargo run or new coverage generation was performed.
  Missing coverage and partially recognized test syntax remain explicit limitations;
  existing LCOV never establishes current full behavioral coverage by itself.

## Kotlin/Java/C++ audit integration

- New native adapter and dispatch tests bring the suite to 64 passing tests.
  Tests cover fresh/malformed/internal-error Lint XML, containment, shared JVM
  invocation, non-variant source gaps, compilation-database validation, clang
  failures and wildcard categories. Review corrected the alternate-database-name
  case before the adapter commit.
- Real Android Lint on this project: Debug variant, JDK 25 / SDK 37, successful
  Gradle completion and fresh XML with 396 raw diagnostics. Java and Kotlin share
  that one analysis; absence of Java-specific diagnostics is not proof of deadness.
- Real NDK clang-tidy 21.0.0 used Android Debug arm64 compilation commands for both
  application translation units, completed without compiler/tool errors, and
  emitted 50 raw diagnostics. No native/source fixes were applied.
- Initial combined audit of android/app/src (before the test-source review correction): Java 6, Kotlin 142, C++ 4 selected files;
  INCOMPLETE, with 121 candidates (62 simplifier, 59 gap hints). JVM execution RAN;
  C++ was PARTIAL for flac_pcm_source.h and the host-only test lacking compile
  commands. Existing test-syntax limits and missing Kotlin/C++ LCOV remained gaps.
- No APK/device/all-ABI verification, application test execution, speculative
  deletion, native linker edits or application runtime dependencies were added.

T002 review correction: test-source roots are now analyzed with explicit
checkTestSources=true, and the effective setting must be confirmed in the Gradle
log. Missing/false confirmation yields INCOMPLETE; a regression covers that case.
The corrected live run confirmed test-source checks for both JVM languages and
produced 146 candidates (87 simplifier, 59 gap hints), still INCOMPLETE for the
known compilation/coverage/test-syntax gaps. Java had 36 selected diagnostics
(outside the current category allowlists), Kotlin had 282 (87 classified), and C++
had zero selected-file diagnostics. The 50 raw clang diagnostics came from included
dependency headers and were correctly excluded from selected application findings.
Full suite after this correction: 64 passed, 0 failed/skipped.

Independent reviews: T001 passed round 2 after database-name correction; T002
passed round 2 after effective test-source checking; T003 cumulative review passed
round 1 with no further fixes. Final map refresh succeeded (1,782 indexed sources,
36,693 symbols, 128,178 relationships). This map coverage is not a native runtime
reachability guarantee. Cumulative JSON/links/whitespace and application-source
boundary checks passed. Actual classified diagnostic details remain in ignored
.work/audit/all/; the full combined result is intentionally INCOMPLETE as explained.
