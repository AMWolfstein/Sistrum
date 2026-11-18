# M001-TOR004 — Full code and test audit, then verified fixes

Owner extra of M001, run **after M001-TOR003 and before M001-T007**, as **one TOR, one
reviewed commit**. Planning only: nothing here is authorized until T006 and TOR003 are done.

Owner request (Egyptian Arabic, paraphrased): open a TOR after TOR003 that runs a full audit
of the code and a full audit of the test files using the workflow. Fix everything that is
confirmed. For anything not confirmed, verify first, then fix.

## Method (the repository `audit` skill, `.agents/skills/audit/SKILL.md`)

1. First action: `python3 workflow/codegraph/code_tools.py update`; refresh after source changes.
2. Run the diagnostics over the whole remaining checkout, not a narrowed scope:
   `python3 workflow/codegraph/code_tools.py audit --kind all` with `--lang dart`, `rust`,
   `kotlin`/`java` (Android Lint), `cpp` (clang-tidy with the real compilation database),
   `python`/`shell` (ruff/shellcheck, installed in this environment). Save each
   `.work/audit/<kind>/report.json` and `report.md` to the task log evidence before re-running
   (same-kind scans overwrite). An unavailable tool or compiler failure is a recorded limitation,
   never a clean scan.
3. Test audit: `--kind tests` and `--kind gaps` over every file in `test/` and
   `integration_test/` (and workflow/script tests). Look for tests that cannot fail, duplicate
   tests, assertions weakened during M001-T005/TOR003 (counts fell, for example
   `music_player_runtime_test` 118 to 85, `home_shelves_test` 8 to 3, `source_contracts_test`
   9 to 1), skipped or environment-dependent tests, and local behaviour left without a test
   (known: `library_album_artwork.dart` lost its test file in T005).
4. Classify every finding as the skill defines:
   - **CONFIRMED**: evidence establishes it in the relevant configuration (callers, imports,
     registrations, manifest components, Method/EventChannels, JNI/FFI/UniFFI, generated
     bindings, Cargo features, Gradle/CMake targets, ARM64 release variant).
   - **PROBABLE**: suggestive, verification incomplete.
   - **UNCERTAIN**: dynamic/generated/native boundary or missing tooling. Retain and name the
     missing evidence.
5. **Fix rule (owner):** fix every CONFIRMED finding. For each PROBABLE or UNCERTAIN finding,
   first run the verification that would settle it (trace from entry points, run the
   specific test, build the variant, read the generated binding), record the result, then fix
   it only if it became CONFIRMED; otherwise leave the code and record why. A tool diagnostic
   alone never authorizes deleting an externally or dynamically reachable declaration.
6. Fixes follow Ponytail: the smallest complete change; no behaviour, audio, data or
   error-handling cut for line count. Weak or missing tests are repaired by adding or
   strengthening assertions, not by deleting tests, unless the subject is gone.

## Known candidates to verify first (from M001-T005 and its review)

- Dead plumbing: Kotlin `prepareRuntimeState`/`setRuntimeState`, `initExtensionSystem` master key
  and name, `saveOnlineLyrics`/`OnlineLyricsSaver` naming, `.gitignore` stale entry.
- Legacy `network://`/`http(s)` guards in library SQL and `PlayableMedia.isNetwork`; Track online
  JSON fields (`deezerId`, `ServiceAvailability`, `headerVideoUrl`); album-artwork provider matching
  (`library_album_artwork.dart`, `album_artwork_match.dart`).
- `file_access.dart` `http(s)` open-with-OS branch; the `legacy://` availability sentinel.
- Pruning-bug class found in T005 (stale list indices, removed `&&`/`||` guards, unconditional
  blocks): sweep `git log -p` of T005 and TOR003 for them.
- Tests that fail only because the environment is root/offline/SQLite (45 baseline failures):
  classify each as environment or real, and fix the real ones.

## Execution and acceptance

One reserved ID, one commit, one independent review (up to 3 rounds). Parallel Haiku workers by
disjoint areas (Dart lib, Dart tests, Kotlin/Android, Rust, scripts/workflow); a coordinator
merges reports, runs the comparisons and fixes shared regressions.

- Audit reports for every selected kind and language are saved; each finding has a class and
  evidence; every CONFIRMED finding is fixed; every PROBABLE/UNCERTAIN finding is either verified
  and fixed or recorded as retained with the missing evidence.
- `flutter analyze --no-pub`: only the known baseline info; `flutter test` in chunks compared with the
  TOR003 final run: no new failures, and the baseline failures are classified; `cargo clippy` and
  `cargo test` pass; `cargo tree` still shows no network crates; `:app:compileReleaseKotlin`;
  workflow and script tests pass.
- Task log: counts of findings per class, fixes, retained items with their evidence, limits
  (no device, tools unavailable), and the test-count changes with reasons.
