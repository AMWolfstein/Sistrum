#!/usr/bin/env bash
# Final gate for a delegated task: scope check, unit tests, build, optional instrumented classes,
# graph refresh (only for source changes in the graph scope). Prints only summaries and failures; full logs stay in the log directory it prints.
#
# Usage: verify.sh --files '<path> <path> ...' [--tests '<gradle --tests filter>']... [--lint]
#                  [--instrumented <FQCN>[,<FQCN>...]] [--expect-fail <FQCN>[,<FQCN>...]]
#   --files         REQUIRED: the task's file list (the brief's RELEVANT FILES plus the test files it
#                   names), repo-relative, space-separated. Any modified, deleted or new Kotlin file
#                   outside this list makes the gate refuse to run (exit 3).
#   --tests         restrict the unit-test run (repeatable); default: the full unit suites
#   --lint          also run :androidApp:lintDevDebug and print the error count
#   --instrumented  device tests to run one class at a time, only if a device is connected
#   --expect-fail   [TESTS-FIRST] tasks: androidApp host-test classes that must compile and in which EVERY test
#                   fails on an assertion (AssertionError, AssertionFailedError, ComparisonFailure); a pass, a skip,
#                   or any other exception (crash, TODO(), IllegalStateException, timeout) fails the gate. All
#                   other tests in the run must pass. Prints an "Expected-fail:" line for the commit message.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

files="" files_given=0 filters=() lint=0 instrumented="" expect_fail=""
while [ $# -gt 0 ]; do
  case "$1" in
    --files) files="$2"; files_given=1; shift 2 ;;
    --tests) filters+=(--tests "$2"); shift 2 ;;
    --lint) lint=1; shift ;;
    --instrumented) instrumented="$2"; shift 2 ;;
    --expect-fail) expect_fail="$2"; shift 2 ;;
    *) echo "verify: unknown argument $1" >&2; exit 2 ;;
  esac
done

# Scope check: refuse to verify while Kotlin files outside the task's list are changed.
if [ "$files_given" = 0 ]; then
  echo "verify: REFUSED — pass the task's file list with --files '<path> ...'" >&2
  exit 3
fi
read -r -a allowed <<< "$files"
changed_kotlin=$( { git diff --name-only --no-renames HEAD -- '*.kt' '*.kts'
                    git ls-files --others --exclude-standard -- '*.kt' '*.kts'; } | sort -u )
outside=()
while IFS= read -r path; do
  [ -z "$path" ] && continue
  in_list=0
  for a in "${allowed[@]}"; do [ "$path" = "$a" ] && in_list=1 && break; done
  [ "$in_list" = 0 ] && outside+=("$path")
done <<< "$changed_kotlin"
if [ "${#outside[@]}" -gt 0 ]; then
  echo "verify: REFUSED — Kotlin files changed outside the task's file list:" >&2
  printf '  %s\n' "${outside[@]}" >&2
  echo "Review them: revert them, or send a delta brief. Do not run the gate until only task files change." >&2
  exit 3
fi
echo "scope          PASS     only task files changed (${#allowed[@]} listed)"

logs=$(mktemp -d "${TMPDIR:-/tmp}/verify-task.XXXXXX")
overall=PASS
report() { printf '%-14s %-8s %s\n' "$1" "$2" "$3"; [ "$2" = FAIL ] && overall=FAIL; return 0; }

# Unit tests
if [ -z "$expect_fail" ]; then
  if ./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest "${filters[@]}" --continue \
      > "$logs/unit.log" 2>&1; then
    report "unit tests" PASS "${filters[*]:-full suites}"
  else
    report "unit tests" FAIL "see $logs/unit.log"
    grep -E 'FAILED|^e: |What went wrong' -A2 "$logs/unit.log" | head -40
  fi
else
  # Expected-fail mode: the Gradle run is expected to fail, so judge it from the JUnit XML instead of the exit code.
  # sharedLogic holds no expected-fail classes: it must pass outright (skipped when --tests narrows the run).
  if [ "${#filters[@]}" -eq 0 ]; then
    if ./gradlew :sharedLogic:testAndroidHostTest > "$logs/unit-shared.log" 2>&1; then
      report "unit shared" PASS ":sharedLogic:testAndroidHostTest"
    else
      report "unit shared" FAIL "see $logs/unit-shared.log"
      grep -E 'FAILED|^e: |What went wrong' -A2 "$logs/unit-shared.log" | head -20
    fi
  fi
  app_results=androidApp/build/test-results/testDevDebugUnitTest
  IFS=, read -r -a expect_classes <<< "$expect_fail"
  if [ "${#filters[@]}" -gt 0 ]; then
    for cls in "${expect_classes[@]}"; do filters+=(--tests "$cls"); done
  fi
  rm -rf "$app_results"   # no stale XML: missing results mean "did not compile or run"
  ./gradlew :androidApp:testDevDebugUnitTest "${filters[@]}" --continue > "$logs/unit.log" 2>&1
  if python3 - "$expect_fail" "$app_results" > "$logs/expect-fail.txt" 2>&1 <<'PY'
import glob, os, sys
import xml.etree.ElementTree as ET

expected = [c.strip() for c in sys.argv[1].split(",") if c.strip()]
# Assertion failures about missing behaviour. Everything else (errors, crashes, TODO(), timeouts) is rejected.
allowed = {
    "java.lang.AssertionError",
    "junit.framework.AssertionFailedError",
    "junit.framework.ComparisonFailure",
    "org.junit.ComparisonFailure",
    "org.junit.internal.ArrayComparisonFailure",
    "org.opentest4j.AssertionFailedError",
}
cases = {}  # class -> list of (name, outcome, detail)
for results in sys.argv[2:]:
    for path in glob.glob(os.path.join(results, "TEST-*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            cls, name = case.get("classname"), case.get("name")
            failure, error, skipped = case.find("failure"), case.find("error"), case.find("skipped")
            if error is not None:
                outcome, detail = "error", error.get("type") or "?"
            elif failure is not None:
                kind = failure.get("type") or "?"
                outcome = "assertion" if kind in allowed else "exception"
                detail = f"{kind}: {(failure.get('message') or '').splitlines()[0][:140] if failure.get('message') else ''}"
            elif skipped is not None:
                outcome, detail = "skipped", ""
            else:
                outcome, detail = "passed", ""
            cases.setdefault(cls, []).append((name, outcome, detail))

problems = []
for cls in expected:
    if not cases.get(cls):
        problems.append(f"{cls}: no results (did not compile, has no tests, or was not run)")
        continue
    for name, outcome, detail in cases[cls]:
        if outcome != "assertion":
            problems.append(f"{cls}.{name}: expected an assertion failure, got {outcome} {detail}".rstrip())
for cls, items in sorted(cases.items()):
    if cls in expected:
        continue
    for name, outcome, detail in items:
        if outcome in ("error", "assertion", "exception"):
            problems.append(f"{cls}.{name}: not in --expect-fail but failed ({detail})")

total = sum(len(cases.get(c, [])) for c in expected)
if problems:
    print("\n".join(problems))
    sys.exit(1)
print(f"{total} test(s) in {len(expected)} class(es) fail on assertions; all other tests pass")
PY
  then
    report "unit tests" PASS "expected-fail: $(tail -1 "$logs/expect-fail.txt")"
    echo "Expected-fail: ${expect_fail//,/, }"
  else
    report "unit tests" FAIL "expected-fail check (see $logs/expect-fail.txt, $logs/unit.log)"
    head -40 "$logs/expect-fail.txt"
    grep -E '^e: |What went wrong' -A2 "$logs/unit.log" | head -20
  fi
fi

# Build
if ./gradlew -q :androidApp:assembleDevDebug > "$logs/build.log" 2>&1; then
  report "build" PASS ":androidApp:assembleDevDebug"
else
  report "build" FAIL "see $logs/build.log"
  grep -E '^e: |What went wrong' -A3 "$logs/build.log" | head -40
fi

# Lint (optional)
if [ "$lint" = 1 ]; then
  ./gradlew :androidApp:lintDevDebug --continue > "$logs/lint.log" 2>&1
  report "lint" INFO "$(grep -m1 -o 'Lint found [0-9]* errors' "$logs/lint.log" || echo 'no summary; see log')"
fi

# Instrumented tests (optional, device only)
if [ -n "$instrumented" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}}"
  adb="$(command -v adb || echo "$sdk/platform-tools/adb")"
  if ! "$adb" get-state >/dev/null 2>&1; then
    report "instrumented" "NOT RUN" "no device connected"
  else
    ./gradlew -q :androidApp:assembleDevQa :androidApp:assembleDevQaAndroidTest > "$logs/androidtest-build.log" 2>&1
    app_apk=androidApp/build/outputs/apk/dev/qa/androidApp-dev-qa.apk
    test_apk=androidApp/build/outputs/apk/androidTest/dev/qa/androidApp-dev-qa-androidTest.apk
    aapt2="$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
    app_id="$("$aapt2" dump packagename "$app_apk" 2>/dev/null)"
    test_id="$("$aapt2" dump packagename "$test_apk" 2>/dev/null)"
    # The owner's daily app. Never install over it, instrument it or force-stop it (CLAUDE.md).
    daily_id=me.misa198.airmedy.dev
    if [ -z "$app_id" ] || [ -z "$test_id" ]; then
      report "instrumented" BLOCKED "could not read the APK application IDs (aapt2: ${aapt2:-not found})"
      classes=()
    elif [ "$app_id" != "$daily_id.qa" ] || [ "$test_id" != "$daily_id.qa.test" ]; then
      # Allowlist: only the .qa test build (ADR-007) may be installed or instrumented.
      report "instrumented" BLOCKED "APK IDs are $app_id / $test_id, not the .qa test build; refusing to install"
      classes=()
    else
      # -g: grant the .qa build's runtime permissions at install (shell grants are blocked on some OEM builds).
      "$adb" install -r -g "$app_apk" > /dev/null
      "$adb" install -r -t "$test_apk" > /dev/null
      "$adb" shell svc power stayon usb
      runner="$test_id/androidx.test.runner.AndroidJUnitRunner"
      IFS=, read -r -a classes <<< "$instrumented"
    fi
    for cls in "${classes[@]}"; do
      "$adb" shell input keyevent KEYCODE_WAKEUP
      out=$("$adb" shell am instrument -w -e class "$cls" "$runner" 2>&1)
      echo "$out" > "$logs/instr-${cls##*.}.log"
      if grep -q '^OK (' <<< "$out"; then
        report "instrumented" PASS "$cls $(grep -o 'OK ([0-9]* tests*)' <<< "$out")"
      else
        report "instrumented" FAIL "$cls $(grep -o 'Tests run: [0-9]*,  Failures: [0-9]*' <<< "$out")"
      fi
    done
  fi
fi

# Graph refresh (CLAUDE.md "Graph updates"): only when source code in the graph scope changed; a refresh
# that only rewrites built_at_commit is reset to HEAD (.claude/hooks/graph_refresh.py).
if command -v graphify >/dev/null; then
  if python3 .claude/hooks/graph_refresh.py auto > "$logs/graphify.log" 2>&1; then
    report "graph" PASS "$(tail -1 "$logs/graphify.log" | sed 's/^graph: //')"
  else
    report "graph" FAIL "see $logs/graphify.log"
  fi
else
  report "graph" "NOT RUN" "graphify not installed"
fi

echo "logs: $logs"
echo "GATE: $overall"
[ "$overall" = PASS ]
