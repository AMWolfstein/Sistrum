#!/usr/bin/env bash
# Whole milestone without the orchestrator (owner, 2026-10-15: "hand M2 to Codex ... you will not be involved"):
#   1. tasks except the last (phone check)       -> milestone-run.sh (fresh Codex session per task)
#   2. milestone review                          -> one fresh Codex REVIEW session per reviewer checklist
#   3. confirmed CRITICAL/HIGH                   -> task <last>-fix (fresh Codex session, AGENTS.md step 10)
#   4. last task (full gate + phone check)       -> milestone-run.sh
#   5. phone-log triage                          -> fresh Codex REVIEW session; CRITICAL/HIGH -> <last>-fix2
#   6. final cross-check                         -> Claude Haiku, read-only, reports only (owner, 2026-10-15)
# Any stop (BLOCKED task, failed commit, memory) ends the run; the reason is printed and in HANDOFF. Nobody takes over:
# the owner decides. Usage:
#   milestone-auto.sh --feature specs/<f> --milestone M2 --tasks 'T014 ... T021' --reviewers 'silent-failure-hunter ...'
#   On a restart after the body tasks were committed, pass --from <first milestone commit> so the review range stays whole.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
feature="" milestone="" tasks="" reviewers="" from="" out="${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/milestones"
while [ $# -gt 0 ]; do
  case "$1" in
    --feature) feature="$2"; shift 2 ;;
    --milestone) milestone="$2"; shift 2 ;;
    --tasks) tasks="$2"; shift 2 ;;
    --reviewers) reviewers="$2"; shift 2 ;;
    --from) from="$2"; shift 2 ;;
    *) echo "milestone-auto: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -f "$feature/tasks.md" ] && [ -n "$milestone" ] && [ -n "$tasks" ] && [ -n "$reviewers" ] \
  || { echo "milestone-auto: need --feature --milestone --tasks --reviewers" >&2; exit 2; }
scripts=.claude/skills/delegate-task/scripts
read -r -a ids <<< "$tasks"
last="${ids[-1]}"
body=("${ids[@]:0:${#ids[@]}-1}")
reports="$feature/review/$milestone"
mkdir -p "$reports"

memory_ok() {
  ./gradlew --stop >/dev/null 2>&1; pkill -f '[K]otlinCompileDaemon'; sleep 15
  for wait in 1 2 3 4; do
    avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
    [ "$avail" -ge 2048 ] && return 0
    [ "$wait" = 4 ] && break
    echo "memory ${avail} MB < 2048; waiting 20 min ($wait/3)"; sleep 1200
  done
  echo "STOP: memory below 2 GB after 3 waits"; return 1
}

review() { # $1 reviewer, $2 range, $3 report path, $4 extra context
  local agent=".claude/agents/$1.md" d="$out/$milestone-review/$1"
  [ -f "$agent" ] || { echo "STOP: no reviewer $1"; return 1; }
  mkdir -p "$d"
  {
    echo "MODE: REVIEW"
    echo "ROLE: You are a REVIEWER in a fresh session. Follow AGENTS.md \"Review mode\". Do not edit code or tests,"
    echo "      do not run Gradle or adb, do not start other agents."
    echo "RANGE: $2   (feature $feature, milestone $milestone; tasks: $tasks)"
    echo "REPORT FILE: $3   (write only this file)"
    [ -n "$4" ] && echo "CONTEXT: $4"
    echo "CHECKLIST ($1):"
    awk 'BEGIN{n=0} /^---$/{n++; next} n>=2' "$agent"
  } > "$d/brief.md"
  echo "=== review $1 $(date +%H:%M)"
  bash "$scripts/dispatch.sh" --brief "$d/brief.md" --model gpt-6.1-sol --effort medium --out-dir "$d/run" > "$d/dispatch.log" 2>&1
  res=$(grep -o 'RESULT: [A-Z]*.*' "$d/run/final.txt" 2>/dev/null | tail -1)
  echo "review $1: ${res:-no RESULT line}"
  [ -s "$3" ] || { echo "STOP: review $1 wrote no report"; return 1; }
  [[ "$res" == "RESULT: FINDINGS"* ]] && return 10
  return 0
}

commit_docs() { # $1 message
  [ -n "$(git status --porcelain -- "$reports")" ] || return 0
  git add -- "$reports" && git commit -q -m "$1" -m "Co-authored-by: Codex <noreply@openai.com>" && git push -q \
    || { echo "STOP: committing review reports failed"; return 1; }
}

add_fix_task() { # $1 id, $2 description
  grep -qE "^- \[[ xX]\] $1( |\$)" "$feature/tasks.md" && return 0
  python3 - "$feature/tasks.md" "$last" "$1" "$2" <<'EOF'
import sys
path, last, new, text = sys.argv[1:]
lines = open(path).read().split('\n')
i = next(n for n, l in enumerate(lines) if l.startswith(f'- [ ] {last} ') or l.startswith(f'- [x] {last} '))
lines.insert(i, f'- [ ] {new} {text}')
open(path, 'w').write('\n'.join(lines))
EOF
}

start=$(git rev-parse HEAD)
# 1. body tasks
if [ ${#body[@]} -gt 0 ]; then
  memory_ok || exit 1
  bash "$scripts/milestone-run.sh" --feature "$feature" --tasks "${body[*]}" || exit 1
fi
first=${from:-$(git log --format=%H --reverse "$start..HEAD" | head -1)}
range="${first:-HEAD}^..HEAD"

# 2. review (skipped on restart when the reports exist)
findings=0
for r in $reviewers; do
  f="$reports/$r.md"
  if [ -s "$f" ]; then echo "SKIP review $r (report exists)"; grep -q '^RESULT: FINDINGS' "$f" && findings=1; continue; fi
  review "$r" "$range" "$f" ""; rc=$?
  [ $rc = 10 ] && findings=1
  [ $rc = 1 ] && exit 1
done
commit_docs "docs($(basename "$feature")): $milestone review reports" || exit 1

# 3. fix task for confirmed CRITICAL/HIGH
if [ $findings = 1 ]; then
  add_fix_task "$last-fix" "[HIGH-RISK] $milestone review fixes: read every report in \`$reports/\` (AGENTS.md step 10); fix confirmed CRITICAL/HIGH findings test-first, reject the rest with file:line reasons; quick gate."
  memory_ok || exit 1
  bash "$scripts/milestone-run.sh" --feature "$feature" --tasks "$last-fix" || exit 1
fi

# 4. last task: full gate + phone check
memory_ok || exit 1
bash "$scripts/milestone-run.sh" --feature "$feature" --tasks "$last" || exit 1

# 5. phone-log triage on the gate logs named in HANDOFF
logs=$(grep -o '/tmp/verify-task\.[A-Za-z0-9]*' "$feature/HANDOFF.md" | head -1)
triage="$reports/phone-log-triage.md"
if [ -n "$logs" ] && [ -d "$logs" ] && [ ! -s "$triage" ]; then
  review phone-log-triage "$range" "$triage" "gate logs directory: $logs (logcat-*.log, instr-*.log)"; rc=$?
  [ $rc = 1 ] && exit 1
  commit_docs "docs($(basename "$feature")): $milestone phone-log triage" || exit 1
  if [ $rc = 10 ]; then
    add_fix_task "$last-fix2" "[HIGH-RISK] $milestone phone-log fixes: read \`$triage\` (AGENTS.md step 10); fix confirmed CRITICAL/HIGH; rerun the full gate + phone check of $last."
    memory_ok || exit 1
    bash "$scripts/milestone-run.sh" --feature "$feature" --tasks "$last-fix2" || exit 1
  fi
elif [ ! -s "$triage" ]; then
  echo "NOTE: no gate logs directory found in HANDOFF; phone-log triage NOT RUN"
fi

# 6. final cross-check by Claude Haiku (read-only; writes only its report through stdout)
final="$reports/FINAL-CHECK.md"
if command -v claude >/dev/null; then
  echo "=== final check (Haiku) $(date +%H:%M)"
  commits=$(git log --format='%h %s' "$range")
  claude -p --model haiku --allowedTools "Read,Grep,Glob" \
    "Read-only final check of milestone $milestone in $feature. Commits in the milestone:
$commits
Read every report in $reports/, the \
$feature/HANDOFF.md milestone report and the task log. List: (1) any review finding marked CRITICAL/HIGH that has no \
fix commit and no 'rejected:' line with a reason; (2) any task ticked in tasks.md without its commit, or a commit without \
its task; (3) any PASS claimed without a recorded run; (4) any test that was weakened (the runner's NOTE lines name \
changed tests). End with one line: FINAL: OK or FINAL: ATTENTION <n>. Do not edit files." > "$final" 2>&1 \
    || echo "NOTE: final check failed to run; see $final"
  commit_docs "docs($(basename "$feature")): $milestone final check" || exit 1
  tail -1 "$final"
fi
echo "MILESTONE $milestone DONE"
