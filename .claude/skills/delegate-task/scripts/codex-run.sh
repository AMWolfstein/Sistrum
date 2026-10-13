#!/usr/bin/env bash
# Run one brief through Codex (`codex exec`) and write DIR/result.json in the same shape the
# opencode-delegate relay uses (status, sessionId, model, finalMessage, touchedFiles, ...), so
# /verify-task reads both the same way. Called by dispatch.sh; do not call it directly.
#
# Usage: codex-run.sh --brief FILE --model ID --out-dir DIR [--effort LEVEL]   (fresh run)
#        codex-run.sh --brief FILE --session UUID --out-dir DIR [--model ID]   (follow-up)
#
# Sandbox: workspace-write (the repo, plus Gradle/Kotlin/Android caches so builds and tests run),
# network on (Gradle), approvals never. Codex reads AGENTS.md only; CLAUDE.md is not a fallback
# project doc, and nothing under .claude/ is loaded, so the coder cannot re-delegate.
set -euo pipefail

brief="" model="" session="" out="" effort="medium"
while [ $# -gt 0 ]; do
  case "$1" in
    --brief) brief="$2"; shift 2 ;;
    --model) model="$2"; shift 2 ;;
    --session) session="$2"; shift 2 ;;
    --out-dir) out="$2"; shift 2 ;;
    --effort) effort="$2"; shift 2 ;;
    *) echo "codex-run: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -f "$brief" ] || { echo "codex-run: --brief FILE required" >&2; exit 2; }
[ -n "$out" ] || { echo "codex-run: --out-dir DIR required" >&2; exit 2; }
if [ -z "$session" ] && [ -z "$model" ]; then
  echo "codex-run: a fresh run needs --model (see Models in CLAUDE.md)" >&2; exit 2
fi

repo=$(git rev-parse --show-toplevel)
mkdir -p "$out"
cp "$brief" "$out/brief.txt"
events="$out/events.jsonl" final="$out/final.txt" stderr_log="$out/stderr.txt"
started=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)

roots="[\"$HOME/.gradle\", \"$HOME/.android\", \"$HOME/.local/share/kotlin\", \"$HOME/.konan\", \"$HOME/.m2\"]"
cfg=(
  -c 'sandbox_mode="workspace-write"'
  -c "sandbox_workspace_write.writable_roots=$roots"
  -c 'sandbox_workspace_write.network_access=true'
  -c 'approval_policy="never"'
  -c 'project_doc_fallback_filenames=[]'
  -c "model_reasoning_effort=\"$effort\""
)
[ -n "$model" ] && cfg+=(-m "$model")

set +e
if [ -n "$session" ]; then
  (cd "$repo" && timeout 2h codex exec resume "${cfg[@]}" --json -o "$final" "$session" - \
    <"$brief" >"$events" 2>"$stderr_log")
else
  (cd "$repo" && timeout 2h codex exec "${cfg[@]}" -C "$repo" --json -o "$final" - \
    <"$brief" >"$events" 2>"$stderr_log")
fi
code=$?
set -e

finished=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
touched=$(cd "$repo" && git status --short)
python3 - "$out/result.json" "$events" "$final" "$stderr_log" "$code" "$model" "$session" \
  "$started" "$finished" "$repo" "$out/brief.txt" "$touched" "$effort" <<'PY'
import json, sys
(res, events, final, stderr_log, code, model, session, started, finished, repo, brief,
 touched, effort) = sys.argv[1:]
code = int(code)
thread = session or None
error = None
for line in open(events, errors="replace"):
    try:
        e = json.loads(line)
    except ValueError:
        continue
    if e.get("type") == "thread.started" and e.get("thread_id"):
        thread = e["thread_id"]
    if e.get("type") in ("error", "turn.failed"):
        error = json.dumps(e)[:2000]
try:
    msg = open(final, errors="replace").read()
except OSError:
    msg = ""
tail = open(stderr_log, errors="replace").read().splitlines()[-20:]
status = "completed" if code == 0 and not error else ("timeout" if code == 124 else "failed")
json.dump({
    "schema": "delegate-relay.result.v1", "tool": "codex", "workdir": repo, "model": model or None,
    "reasoningEffort": effort, "resumed": bool(session), "startedAt": started, "finishedAt": finished,
    "briefPath": brief, "eventsPath": events, "finalPath": final, "status": status,
    "exitCode": code, "sessionId": thread, "finalMessage": msg,
    "touchedFiles": touched.splitlines(), "stderrTail": tail, "error": error,
}, open(res, "w"), indent=2)
print(f"codex-run: {status} (exit {code}), session {thread}, result {res}")
PY
