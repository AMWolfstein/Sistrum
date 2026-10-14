#!/usr/bin/env bash
# Autonomous milestone runner (owner, 2026-10-14; spec 002 onward): Codex implements, verifies, commits and pushes
# every task of a milestone, one FRESH Codex session per task (no context carried over), with no orchestrator turn
# in between. Rules for the coder: AGENTS.md "Autonomous mode".
#
# Usage: milestone-run.sh --feature specs/<feature> --tasks 'T001 T002 ...' [--out DIR]
# Per task: [HIGH-RISK] in its tasks.md line -> gpt-6.1-sol high, else medium; a BLOCKED/failed task is retried once
# with gpt-6-astra high (CLAUDE.md "Models" fallback). Stops at the first task still not DONE, or when a task
# reports DONE without a new pushed commit. Exit 0 = all DONE; 1 = stopped (reason printed and in HANDOFF).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
feature="" tasks="" out="${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/milestones"
while [ $# -gt 0 ]; do
  case "$1" in
    --feature) feature="$2"; shift 2 ;;
    --tasks) tasks="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    *) echo "milestone-run: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -f "$feature/tasks.md" ] && [ -n "$tasks" ] || { echo "milestone-run: need --feature (with tasks.md) and --tasks" >&2; exit 2; }
branch=$(git branch --show-current)
[ "$branch" != main ] || { echo "milestone-run: refusing to run on main" >&2; exit 2; }
dispatch=.claude/skills/delegate-task/scripts/dispatch.sh

brief() { # $1 task id
  cat <<EOF
MODE: AUTONOMOUS
ROLE: You are the IMPLEMENTER (coder). Follow AGENTS.md "Autonomous mode" exactly. Do not start other agents,
      \`codex\`, \`opencode\` or \`relay.mjs\`; do not write brief files.
TASK: $1 (the line for $1 in $feature/tasks.md)
FEATURE DOCS: $feature/spec.md, $feature/plan.md, $feature/tasks.md, $feature/HANDOFF.md, $feature/DECISIONS.md
              (read HANDOFF "Current status" and the $1 line first; then only the sections and files $1 needs)
MILESTONE TASKS (context only; do ONLY $1 in this session): $tasks
BRANCH: $branch (commit and push here only; never main)
MUST NOT: work on any other task; change ADRs, architecture or spec 001's shipped behaviour (stop BLOCKED instead);
          touch the owner's daily apps; fake or skip results (NOT RUN with reason).
END: one line "RESULT: DONE <sha>" or "RESULT: BLOCKED <reason>".
EOF
}

for t in $tasks; do
  line=$(grep -m1 -E "^- \[[ xX]\] $t\b" "$feature/tasks.md") || { echo "STOP: $t not in tasks.md"; exit 1; }
  case "$line" in "- [x]"*|"- [X]"*) echo "SKIP $t (already done)"; continue ;; esac
  effort=medium; case "$line" in *"[HIGH-RISK]"*) effort=high ;; esac
  done_ok=0
  for attempt in "gpt-6.1-sol $effort" "gpt-6-astra high"; do
    set -- $attempt; model=$1; eff=$2
    d="$out/$t/$model"; mkdir -p "$d"; brief "$t" > "$d/brief.md"
    before=$(git rev-parse HEAD)
    echo "=== $t $model/$eff $(date +%H:%M)"
    bash "$dispatch" --brief "$d/brief.md" --model "$model" --effort "$eff" --out-dir "$d/run" > "$d/dispatch.log" 2>&1
    res=$(grep -o 'RESULT: [A-Z]*.*' "$d/run/final.txt" 2>/dev/null | tail -1)
    after=$(git rev-parse HEAD)
    echo "$t: ${res:-no RESULT line} (HEAD $before -> $after)"
    if [[ "$res" == "RESULT: DONE"* ]] && [ "$after" != "$before" ] \
       && [ -z "$(git log --oneline "@{u}..HEAD" 2>/dev/null)" ]; then done_ok=1; break; fi
    [ "$after" != "$before" ] && { echo "STOP: $t moved HEAD without a DONE+pushed result; inspect $d"; exit 1; }
  done
  [ $done_ok = 1 ] || { echo "STOP: $t not done after fallback; see $out/$t and $feature/HANDOFF.md"; exit 1; }
done
echo "MILESTONE DONE: $tasks"
