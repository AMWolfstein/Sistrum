#!/usr/bin/env bash
# Dispatch a brief to OpenCode through the opencode-delegate relay, the only way this repo
# delegates code. Every run (fresh, --resume-last, --session) gets OPENCODE_DISABLE_CLAUDE_CODE=1,
# so the coder never loads CLAUDE.md or .claude/skills and cannot re-delegate.
#
# Usage: dispatch.sh --brief FILE --model ID --out-dir DIR            (fresh run)
#        dispatch.sh --brief FILE --resume-last|--session ID --out-dir DIR  (follow-up)
# Extra arguments are passed to relay.mjs unchanged. The relay writes DIR/result.json.
set -euo pipefail

relay="${OPENCODE_RELAY:-$HOME/.claude/skills/opencode-delegate/scripts/relay.mjs}"
if [ ! -f "$relay" ]; then
  relay=$(find "$HOME" -path '*opencode-delegate/scripts/relay.mjs' -print -quit 2>/dev/null || true)
fi
if [ -z "$relay" ] || [ ! -f "$relay" ]; then
  echo "dispatch: relay.mjs not found; install the opencode-delegate skill or set OPENCODE_RELAY" >&2
  exit 127
fi

model="" resume=0 prev=""
for arg in "$@"; do
  [ "$prev" = "--model" ] && model="$arg"
  case "$arg" in --resume-last|--session) resume=1 ;; esac
  prev="$arg"
done
case "$model" in
  opencode/*-free|opencode-go/glm-5.3)
    echo "dispatch: model '$model' is not allowed (see Models in CLAUDE.md)" >&2; exit 2 ;;
esac
if [ -z "$model" ] && [ "$resume" = 0 ]; then
  echo "dispatch: a fresh run needs --model (see Models in CLAUDE.md)" >&2; exit 2
fi

repo=$(git rev-parse --show-toplevel)
exec env OPENCODE_DISABLE_CLAUDE_CODE=1 node "$relay" --cd "$repo" --timeout 2h "$@"
