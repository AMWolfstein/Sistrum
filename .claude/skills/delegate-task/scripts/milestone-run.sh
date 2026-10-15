#!/usr/bin/env bash
# Autonomous milestone runner (owner, 2026-10-14; spec 002 onward): Codex implements, verifies, commits and pushes
# every task of a milestone, one FRESH Codex session per task (no context carried over), with no orchestrator turn
# in between. Rules for the coder: AGENTS.md "Autonomous mode".
#
# Usage: milestone-run.sh --feature specs/<feature> --tasks 'T001 T002 ...' [--out DIR] [--effort low|medium]
# Per task: [HIGH-RISK] in its tasks.md line -> gpt-6.1-sol high, else medium (or --effort, which never lowers a
# [HIGH-RISK] task; owner 2026-10-15: gate/phone-check tasks run at low). No Astra fallback (owner, 2026-10-14):
# a BLOCKED/failed task stops the run and Claude finishes it (as with T009). Restartable: an uncommitted .codex-commit/ is committed first. Stops at the first task still not DONE, or when a task
# reports DONE without a new pushed commit. Exit 0 = all DONE; 1 = stopped (reason printed and in HANDOFF).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
feature="" tasks="" force_effort="" out="${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/milestones"
while [ $# -gt 0 ]; do
  case "$1" in
    --feature) feature="$2"; shift 2 ;;
    --tasks) tasks="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    --effort) force_effort="$2"; shift 2 ;;
    *) echo "milestone-run: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -f "$feature/tasks.md" ] && [ -n "$tasks" ] || { echo "milestone-run: need --feature (with tasks.md) and --tasks" >&2; exit 2; }
branch=$(git branch --show-current)
[ "$branch" != main ] || { echo "milestone-run: refusing to run on main" >&2; exit 2; }
dispatch=.claude/skills/delegate-task/scripts/dispatch.sh
# Codex quota: waits for resets and resumes a step stopped by the usage limit (owner 2026-10-15).
source .claude/skills/delegate-task/scripts/quota.sh

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
END: one line "RESULT: READY" (commit prepared in .codex-commit/) or "RESULT: BLOCKED <reason>".
EOF
}

# Commit what the coder prepared (its sandbox can't write Git metadata): exactly the listed paths.
commit_prepared() { # $1 task id
  local f=.codex-commit/files.txt m=.codex-commit/message.txt
  [ -s "$f" ] && [ -s "$m" ] || { echo "STOP: $1 READY without .codex-commit/files.txt+message.txt"; return 1; }
  grep -q "$1" "$m" || { echo "STOP: $1 commit message lacks the task id"; return 1; }
  python3 .claude/hooks/graph_refresh.py auto | tail -1 | grep -q changed \
    && { grep -qx 'graphify-out/graph.json' "$f" || printf '%s\n' graphify-out/graph.json graphify-out/.graphify_analysis.json >> "$f"; }
  mapfile -t paths < <(grep -v '^\s*$' "$f" | sort -u)
  # The loop must not rewrite its own rules (loop-design-check red line): rules files are orchestrator-only.
  bad=$(printf '%s\n' "${paths[@]}" | grep -E '^(AGENTS\.md|CLAUDE\.md|\.claude/|\.specify/memory/)' || true)
  [ -z "$bad" ] || { echo "STOP: $1 lists rules files (orchestrator-only):"; echo "$bad"; return 1; }
  git add -A -- "${paths[@]}" || { echo "STOP: $1 staging failed"; return 1; }
  left=$( { git diff --name-only -- '*.kt' '*.kts'; git ls-files --others --exclude-standard -- '*.kt' '*.kts'; } | sort -u)
  [ -z "$left" ] || { echo "STOP: $1 left Kotlin changes outside its file list:"; echo "$left"; git reset -q; return 1; }
  # Existing tests changed by the task are logged for the milestone review (gaming-the-gate check).
  edited=$(git diff --cached --name-only --diff-filter=MDR -- '*/src/test/*' '*/src/androidTest/*' '*/src/commonTest/*')
  [ -z "$edited" ] || { echo "NOTE: $1 changed existing tests (review at milestone end):"; echo "$edited" | sed 's/^/  /'; }
  git commit -q -F "$m" || { echo "STOP: $1 commit failed"; git reset -q; return 1; }
  git push -q || { echo "STOP: $1 push failed (commit $(git rev-parse --short HEAD) is local)"; return 1; }
  rm -rf .codex-commit
  echo "$1: committed and pushed $(git rev-parse --short HEAD)"
}

# Resume: a .codex-commit/ left by a stopped runner (coder finished, nobody committed) is committed first.
if [ -d .codex-commit ]; then
  t=$(grep -o -m1 -E 'T[0-9]{3}[a-z]?' .codex-commit/message.txt 2>/dev/null | head -1)
  [ -n "$t" ] || { echo "STOP: .codex-commit/ without a task id in message.txt; inspect"; exit 1; }
  echo "RESUME: committing $t prepared by an earlier run"
  commit_prepared "$t" || exit 1
fi

for t in $tasks; do
  line=$(grep -m1 -E "^- \[[ xX]\] $t( |\$)" "$feature/tasks.md") || { echo "STOP: $t not in tasks.md"; exit 1; }
  case "$line" in "- [x]"*|"- [X]"*) echo "SKIP $t (already done)"; continue ;; esac
  effort=${force_effort:-medium}; case "$line" in *"[HIGH-RISK]"*) effort=high ;; esac
  done_ok=0
  for attempt in "gpt-6.1-sol $effort"; do
    set -- $attempt; model=$1; eff=$2
    d="$out/$t/$model"; mkdir -p "$d"; brief "$t" > "$d/brief.md"
    before=$(git rev-parse HEAD)
    echo "=== $t $model/$eff $(date +%H:%M)"
    codex_step "$d" "$d/brief.md" "$model" "$eff"
    res=$(grep -o 'RESULT: [A-Z]*.*' "$d/run/final.txt" 2>/dev/null | tail -1)
    echo "$t: ${res:-no RESULT line}"
    [ "$(git rev-parse HEAD)" = "$before" ] || { echo "STOP: $t moved HEAD itself; inspect $d"; exit 1; }
    if [[ "$res" == "RESULT: READY"* ]]; then
      commit_prepared "$t" && { done_ok=1; break; }
      exit 1
    fi
    [ -d .codex-commit ] && { echo "STOP: $t BLOCKED but left .codex-commit/; inspect"; exit 1; }
  done
  [ $done_ok = 1 ] || { echo "STOP: $t not done (no Astra fallback; Claude or the owner takes over); see $out/$t and $feature/HANDOFF.md"; exit 1; }
done
echo "MILESTONE DONE: $tasks"
