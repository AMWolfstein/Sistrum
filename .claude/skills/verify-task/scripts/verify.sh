#!/usr/bin/env bash
# Final gate for a delegated task: scope check, unit tests, build, optional instrumented classes,
# graph refresh. Prints only summaries and failures; full logs stay in the log directory it prints.
#
# Usage: verify.sh --files '<path> <path> ...' [--tests '<gradle --tests filter>']... [--lint]
#                  [--instrumented <FQCN>[,<FQCN>...]]
#   --files         REQUIRED: the task's file list (the brief's RELEVANT FILES plus the test files it
#                   names), repo-relative, space-separated. Any modified, deleted or new Kotlin file
#                   outside this list makes the gate refuse to run (exit 3).
#   --tests         restrict the unit-test run (repeatable); default: the full unit suites
#   --lint          also run :androidApp:lintDevDebug and print the error count
#   --instrumented  device tests to run one class at a time, only if a device is connected
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

files="" files_given=0 filters=() lint=0 instrumented=""
while [ $# -gt 0 ]; do
  case "$1" in
    --files) files="$2"; files_given=1; shift 2 ;;
    --tests) filters+=(--tests "$2"); shift 2 ;;
    --lint) lint=1; shift ;;
    --instrumented) instrumented="$2"; shift 2 ;;
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
if ./gradlew :androidApp:testDevDebugUnitTest :sharedLogic:testAndroidHostTest "${filters[@]}" --continue \
    > "$logs/unit.log" 2>&1; then
  report "unit tests" PASS "${filters[*]:-full suites}"
else
  report "unit tests" FAIL "see $logs/unit.log"
  grep -E 'FAILED|^e: |What went wrong' -A2 "$logs/unit.log" | head -40
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
    ./gradlew -q :androidApp:assembleDevDebugAndroidTest > "$logs/androidtest-build.log" 2>&1
    "$adb" install -r androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk > /dev/null
    "$adb" install -r -t androidApp/build/outputs/apk/androidTest/dev/debug/androidApp-dev-debug-androidTest.apk > /dev/null
    "$adb" shell svc power stayon usb
    runner=me.misa198.airmedy.dev.test/androidx.test.runner.AndroidJUnitRunner
    IFS=, read -r -a classes <<< "$instrumented"
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

# Graph refresh (CLAUDE.md "Graph updates")
if command -v graphify >/dev/null; then
  if graphify extract androidApp/src/main --code-only --out . > "$logs/graphify.log" 2>&1; then
    report "graph" PASS "$(grep -o '[0-9]* nodes, [0-9]* edges' "$logs/graphify.log" | tail -1)"
  else
    report "graph" FAIL "see $logs/graphify.log"
  fi
else
  report "graph" "NOT RUN" "graphify not installed"
fi

echo "logs: $logs"
echo "GATE: $overall"
[ "$overall" = PASS ]
