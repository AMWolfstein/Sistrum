# Codex quota handling for the milestone runners (sourced; owner 2026-10-15: "auto resume like Claude Code").
#   - before each Codex step: 5-hour window >= 85% -> sleep until it resets; weekly window used up -> sleep until it
#     resets. No stop at a weekly percentage (owner 2026-10-15).
#   - a step that dies on "usage limit" mid-run waits for the reset and resumes the SAME Codex session (keeps its
#     context and any half-done edits), at most 6 times.
# Usage numbers come from the newest Codex session log that has them (~/.codex/sessions/**/rollout-*.jsonl).
# Only works while the laptop stays on: a shutdown kills the sleep (restart the runner by hand then).

codex_usage() { # prints "<5h %> <5h reset epoch> <weekly %> <weekly reset epoch>" or nothing
  python3 -I - <<'PY'
import glob, json, os
files = sorted(glob.glob(os.path.expanduser('~/.codex/sessions/**/rollout-*.jsonl'), recursive=True),
               key=os.path.getmtime, reverse=True)[:15]
for path in files:
    last = None
    for line in open(path, errors='replace'):
        if '"primary":{' not in line:
            continue
        try:
            obj = json.loads(line)
        except ValueError:
            continue
        stack = [obj]
        while stack:
            o = stack.pop()
            if isinstance(o, dict):
                if isinstance(o.get('primary'), dict) and isinstance(o.get('secondary'), dict):
                    last = o
                stack.extend(o.values())
            elif isinstance(o, list):
                stack.extend(o)
    if last:
        p, s = last['primary'], last['secondary']
        print(p.get('used_percent', 0), p.get('resets_at', 0), s.get('used_percent', 0), s.get('resets_at', 0))
        break
PY
}

sleep_until() { # $1 epoch, $2 label
  local now wait; now=$(date +%s); wait=$(( $1 - now + 120 ))
  [ "$wait" -gt 0 ] || return 0
  echo "QUOTA: $2; waiting until $(date -d "@$(( $1 + 120 ))" '+%a %H:%M') ($(date +%H:%M))"
  sleep "$wait"
}

quota_wait() { # $1 = "hit" after a usage-limit error: wait even if the numbers look fine (they lag)
  local p5 r5 pw rw now waited=0
  read -r p5 r5 pw rw <<< "$(codex_usage)"
  now=$(date +%s)
  if [ -n "${p5:-}" ]; then
    p5=${p5%.*}; pw=${pw%.*}
    if [ "$rw" -gt "$now" ] && [ "$pw" -ge 100 ]; then sleep_until "$rw" "weekly limit used up"; waited=1
    elif [ "$r5" -gt "$now" ] && { [ "$p5" -ge 85 ] || [ "${1:-}" = hit ]; }; then
      sleep_until "$r5" "5-hour window at ${p5}%"; waited=1
    fi
  fi
  if [ "${1:-}" = hit ] && [ $waited = 0 ]; then
    echo "QUOTA: usage limit hit, reset time unknown; waiting 30 min ($(date +%H:%M))"; sleep 1800
  fi
  return 0
}

limit_hit() { # $1 result.json
  [ -f "$1" ] && grep -q 'hit your usage limit' "$1"
}

# codex_step DIR BRIEF MODEL EFFORT: fresh run into DIR/run (dispatch log DIR/dispatch.log); on a usage-limit stop,
# wait and resume the same session; the last run's final.txt is copied to DIR/run/final.txt for the caller.
codex_step() {
  local d="$1" brief="$2" model="$3" effort="$4" cur sid n=0
  local dispatch_sh; dispatch_sh="$(dirname "${BASH_SOURCE[0]}")/dispatch.sh"
  quota_wait
  bash "$dispatch_sh" --brief "$brief" --model "$model" --effort "$effort" --out-dir "$d/run" > "$d/dispatch.log" 2>&1
  cur="$d/run"
  while limit_hit "$cur/result.json" && [ $n -lt 6 ]; do
    n=$((n + 1))
    sid=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1])).get("sessionId") or "")' "$cur/result.json")
    [ -n "$sid" ] || { echo "QUOTA: usage limit hit but no session id in $cur/result.json"; break; }
    quota_wait hit
    echo "QUOTA: resuming session $sid ($n/6) $(date +%H:%M)"
    cat > "$d/resume-$n.md" <<EOF
Your previous turn stopped because the Codex usage limit was reached. The limit has reset. Continue the same task
from where you stopped, following the same instructions as before (check git status first: some edits may already
be in place). End exactly as the original instructions say.
EOF
    bash "$dispatch_sh" --brief "$d/resume-$n.md" --session "$sid" --model "$model" --effort "$effort" \
      --out-dir "$d/run-resume-$n" >> "$d/dispatch.log" 2>&1
    cur="$d/run-resume-$n"
  done
  [ "$cur" = "$d/run" ] || cp -f "$cur/final.txt" "$d/run/final.txt" 2>/dev/null || true
}
