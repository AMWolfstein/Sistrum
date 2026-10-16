#!/usr/bin/env bash
# Run several milestones back to back with no orchestrator in between (owner, 2026-10-16: Claude's weekly quota may run
# out before 004 ends; Codex must be able to continue alone). Each milestone goes through milestone-auto.sh (tasks,
# Codex reviews, fix task, gate + phone check, phone-log triage, Haiku final check). A milestone whose last task is
# already ticked in tasks.md is skipped, so the same command restarts the chain after any stop.
#
# Usage: chain.sh --feature specs/<f> --plan <file> --base <feature start sha> [--out DIR]
#   --base: task ids repeat across specs (002 and 004 both have T001), so commits are looked up only after it.
#   plan file: one milestone per line, `M2|T009 T010 ... T020|reviewer reviewer ...`; `#` starts a comment.
# Stops at the first milestone that stops (BLOCKED task, memory, failed commit); the reason is in its log and HANDOFF.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
feature="" plan="" base="" out="${XDG_STATE_HOME:-$HOME/.local/state}/sistrum-delegate/milestones"
while [ $# -gt 0 ]; do
  case "$1" in
    --feature) feature="$2"; shift 2 ;;
    --plan) plan="$2"; shift 2 ;;
    --base) base="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    *) echo "chain: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -f "$feature/tasks.md" ] && [ -f "$plan" ] && git cat-file -e "${base:-none}^{commit}" 2>/dev/null || { echo "chain: need --feature (with tasks.md), --plan FILE and --base SHA" >&2; exit 2; }
logdir="$(dirname "$out")"
while IFS='|' read -r m tasks reviewers; do
  m="${m%%#*}"; m="$(echo "$m" | xargs)"; [ -n "$m" ] || continue
  last="${tasks##* }"
  if grep -qE "^- \[[xX]\] $last( |\$)" "$feature/tasks.md"; then echo "SKIP milestone $m ($last done)"; continue; fi
  # Keep the review range whole on a restart: first commit whose message names the milestone's first task.
  first_task="$(echo "$tasks" | xargs | cut -d' ' -f1)"
  from="$(git log --reverse --format=%H -E --grep="^$first_task\$" "$base..HEAD" | head -1)"
  echo "=== chain milestone $m $(date '+%a %H:%M')${from:+ (from ${from:0:8})}"
  bash .claude/skills/delegate-task/scripts/milestone-auto.sh --feature "$feature" --milestone "$m" --tasks "$tasks" \
    --reviewers "$reviewers" --out "$out" ${from:+--from "$from"} >> "$logdir/$(basename "$out")-$m-auto.log" 2>&1
  rc=$?
  tail -3 "$logdir/$(basename "$out")-$m-auto.log"
  [ $rc = 0 ] || { echo "STOP: chain stopped in $m (exit $rc); log $logdir/$(basename "$out")-$m-auto.log"; exit 1; }
done < "$plan"
echo "CHAIN DONE"
